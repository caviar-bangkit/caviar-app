/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tensorflow.lite.examples.objectdetection.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.Manifest
import androidx.core.app.ActivityCompat
import com.bangkit.caviar.dialog.DialogResult
import com.bangkit.caviar.ui.home.MainActivity
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.bangkit.caviar.R
import com.bangkit.caviar.databinding.FragmentCameraBinding
import com.bangkit.caviar.detector.TrafficLightDetector
import com.google.android.gms.location.*
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper
import org.tensorflow.lite.task.vision.detector.Detection
import java.util.Locale
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build

class CameraFragment : Fragment(), ObjectDetectorHelper.DetectorListener, LocationListener {

    private val TAG = "ObjectDetection"

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var bitmapBuffer: Bitmap
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var textToSpeech: TextToSpeech
    private lateinit var trafficLightDetector: TrafficLightDetector

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var previousLocation: Location? = null
    private var isConfirmationDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        checkLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(R.id.permissions_fragment)
        }
    }


    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()

    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status != TextToSpeech.ERROR) {
                textToSpeech.language = Locale.getDefault()
            }
        }

        isConfirmationDialogShown = false

        trafficLightDetector = TrafficLightDetector(requireContext(),textToSpeech)
        trafficLightDetector.resetState()
        trafficLightDetector.setOnCrossWalkDetectedListener(object :TrafficLightDetector.TrafficLightDetectorCallback{
            override fun onCrossWalkDetected() {
                fragmentCameraBinding.redetect.visibility = View.VISIBLE
                trafficLightDetector.searchCrossWalk()
            }
            override fun onTrafficLightStateChange(state: TrafficLightDetector.TrafficLightState) {
                when(state){
                    TrafficLightDetector.TrafficLightState.RED -> {
                        fragmentCameraBinding.statusText.text ="Silahkan menyebrang"
                    }
                    TrafficLightDetector.TrafficLightState.GREEN -> {
                        fragmentCameraBinding.statusText.text ="Silahkan menunggu"
                    }
                    TrafficLightDetector.TrafficLightState.YELLOW -> {
                        fragmentCameraBinding.statusText.text ="Silahkan bersiap-siap"
                    }
                    TrafficLightDetector.TrafficLightState.CROSSWALK -> {
                        fragmentCameraBinding.statusText.text ="Arahkan kamera ke lampu lalu lintas"
                    }
                    else -> {
                        fragmentCameraBinding.statusText.text ="Arahkan kamera ke zebra cross"
                    }
                }
            }
        })
        objectDetectorHelper = ObjectDetectorHelper(
            context = requireContext(),
            objectDetectorListener = this)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }


        fragmentCameraBinding
        // Attach listeners to UI control widgets
        initObjectDetection()
        setupUI()
        view.clearFocus()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request location permission
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_CODE
            )
        } else {
            // Permission already granted
            startLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 500
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            null
        )
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val currentLocation = locationResult.lastLocation
            // Handle location update and check for movement or crossing here
            if (currentLocation != null) {
                checkMovement(currentLocation)
            }
        }
    }

    private fun checkMovement(currentLocation: Location) {
        if (previousLocation != null) {
            val distance = currentLocation.distanceTo(previousLocation!!)
            if (distance >= 3 && !isConfirmationDialogShown) {
                isConfirmationDialogShown = true
                showConfirmationDialog()
            }
        }
        previousLocation = currentLocation
    }

    private fun showConfirmationDialog() {
        speakText("Apakah Anda sudah selesai menyeberang?")

        val dialog = DialogResult(requireContext())
        dialog.setTitle("Konfirmasi")
        dialog.setImage(R.drawable.crossing)
        dialog.setMessage("Apakah Anda sudah selesai menyeberang?")

        dialog.setPositiveButton("Ya") {
            // User has finished crossing, proceed to the next activity
            Toast.makeText(requireContext(), "Kembali ke halaman utama", Toast.LENGTH_SHORT).show()
            speakText("Kembali ke halaman utama")
            dialog.dismiss()

            // Pindah ke activity lain di sini
            val intent = Intent(requireContext(), MainActivity::class.java)
            startActivity(intent)
        }

        dialog.setNegativeButton("Tidak") {
            // User hasn't finished crossing, do nothing
            Toast.makeText(requireContext(), "Lanjut Menyeberang", Toast.LENGTH_SHORT).show()
            speakText("Lanjut Menyeberang")
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun speakText(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    override fun onLocationChanged(location: Location) {
        checkMovement(location)
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }



    private fun initObjectDetection() {
        objectDetectorHelper.threshold = 0.75f
        objectDetectorHelper.maxResults = 3
        objectDetectorHelper.numThreads = 3
        objectDetectorHelper.currentDelegate = ObjectDetectorHelper.DELEGATE_CPU
        updateControlsUi()
    }


    private fun updateControlsUi() {

        // Needs to be cleared instead of reinitialized because the GPU
        // delegate needs to be initialized on the thread using it when applicable
        objectDetectorHelper.clearObjectDetector()
        fragmentCameraBinding.overlay.clear()
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector - makes assumption that we're only using the back camera
        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview =
            Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        if (!::bitmapBuffer.isInitialized) {
                            // The image rotation and RGB image buffer are initialized only once
                            // the analyzer has started running
                            bitmapBuffer = Bitmap.createBitmap(
                                image.width,
                                image.height,
                                Bitmap.Config.ARGB_8888
                            )
                        }

                        detectObjects(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectObjects(image: ImageProxy) {
        // Copy out RGB bits to the shared bitmap buffer
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

        val imageRotation = image.imageInfo.rotationDegrees
        // Pass Bitmap and rotation to the object detector helper for processing and detection
        objectDetectorHelper.detect(bitmapBuffer, imageRotation)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after objects have been detected. Extracts original image height/width
    // to scale and place bounding boxes properly through OverlayView
    override fun onResults(
        results: MutableList<Detection>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        activity?.runOnUiThread {

            // Pass necessary information to OverlayView for drawing on the canvas
            if(_fragmentCameraBinding != null){
                fragmentCameraBinding.overlay.setResults(
                    results ?: LinkedList<Detection>(),
                    imageHeight,
                    imageWidth
                )
                if (results != null) {
                    trafficLightDetector.updateObjectDetected(results)
                }
                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }


    fun setupUI(){
//        handler 300 ms
        Handler(Looper.getMainLooper()).postDelayed({
            trafficLightDetector.searchCrossWalk()
        }, 300)
        fragmentCameraBinding.redetect.visibility = View.INVISIBLE
        fragmentCameraBinding.statusText.text ="Arahkan kamera ke zebra cross"
        fragmentCameraBinding.closeButton.setOnClickListener {
            handleBackPressed()
        }
        fragmentCameraBinding.redetect.setOnClickListener(View.OnClickListener {
            trafficLightDetector.resetState()
            fragmentCameraBinding.redetect.visibility = View.INVISIBLE
        });
    }



    fun handleBackPressed() {
        requireActivity().onBackPressed()
    }
}
