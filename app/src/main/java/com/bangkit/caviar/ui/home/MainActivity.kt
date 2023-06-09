package com.bangkit.caviar.ui.home

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bangkit.caviar.NetworkConfig
import com.bangkit.caviar.R
import com.bangkit.caviar.databinding.ActivityMainBinding
import com.bangkit.caviar.model.NearbyTrafficLightResponse
import android.annotation.SuppressLint
import android.app.Activity.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.View.OnClickListener
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bangkit.caviar.dialog.DialogResult
import com.bangkit.caviar.ui.detection.DetectionActivity
import com.bangkit.caviar.ui.login.LoginActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.Bearing
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Expected
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.TimeFormat
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.replay.MapboxReplayer
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.tripprogress.api.MapboxTripProgressApi
import com.mapbox.navigation.ui.tripprogress.model.*
import com.mapbox.navigation.ui.voice.api.MapboxSpeechApi
import com.mapbox.navigation.ui.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.ui.voice.model.SpeechAnnouncement
import com.mapbox.navigation.ui.voice.model.SpeechError
import com.mapbox.navigation.ui.voice.model.SpeechValue
import com.mapbox.navigation.ui.voice.model.SpeechVolume
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private var percentDistanceTraveledThreshold: Double = 97.0
    private var distanceRemainingThresholdInMeters = 30
    private var arrivalNotificationHasDisplayed = false

    private companion object {
        private const val BUTTON_ANIMATION_DURATION = 1500L
    }

    private val mapboxReplayer = MapboxReplayer()
    private val replayProgressObserver = ReplayProgressObserver(mapboxReplayer)
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var textToSpeech: TextToSpeech

    private lateinit var maneuverApi : MapboxManeuverApi
    private lateinit var tripProgressApi: MapboxTripProgressApi
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private val routeArrowApi: MapboxRouteArrowApi = MapboxRouteArrowApi()
    private lateinit var routeArrowView: MapboxRouteArrowView
    private var isVoiceInstructionsMuted = false
        set(value) {
            field = value
            if (value) {
                binding.soundButton.muteAndExtend(BUTTON_ANIMATION_DURATION)
                voiceInstructionsPlayer.volume(SpeechVolume(0f))
            } else {
                binding.soundButton.unmuteAndExtend(BUTTON_ANIMATION_DURATION)
                voiceInstructionsPlayer.volume(SpeechVolume(1f))
            }
        }
    private lateinit var speechApi: MapboxSpeechApi
    private lateinit var voiceInstructionsPlayer: MapboxVoiceInstructionsPlayer
    private val voiceInstructionsObserver = VoiceInstructionsObserver { voiceInstructions ->
        speechApi.generate(voiceInstructions, speechCallback)
    }

    private val speechCallback =
        MapboxNavigationConsumer<Expected<SpeechError, SpeechValue>> { expected ->
            expected.fold(
                { error ->
                    voiceInstructionsPlayer.play(
                        error.fallback,
                        voiceInstructionsPlayerCallback
                    )
                },
                { value ->
                    voiceInstructionsPlayer.play(
                        value.announcement,
                        voiceInstructionsPlayerCallback
                    )
                }
            )
        }
    private val voiceInstructionsPlayerCallback =
        MapboxNavigationConsumer<SpeechAnnouncement> { value ->
            speechApi.clean(value)
        }
    private val navigationLocationProvider = NavigationLocationProvider()
    private val locationObserver = object : LocationObserver {
        var firstLocationUpdateReceived = false

        override fun onNewRawLocation(rawLocation: android.location.Location) {
            //
        }

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation

            navigationLocationProvider.changePosition(
                location = enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )
            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()
            if (!firstLocationUpdateReceived) {
                firstLocationUpdateReceived = true
                navigationCamera.requestNavigationCameraToOverview(
                    stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
                        .maxDuration(0)
                        .build()
                )
            }
        }
    }

    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        viewportDataSource.onRouteProgressChanged(routeProgress)
        viewportDataSource.evaluate()
        val style = binding.mapView.getMapboxMap().getStyle()
        if (style != null) {
            val maneuverArrowResult = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
            routeArrowView.renderManeuverUpdate(style, maneuverArrowResult)
        }
        val maneuvers = maneuverApi.getManeuvers(routeProgress)
        maneuvers.fold(
            { error ->
                Toast.makeText(
                    this@MainActivity,
                    error.errorMessage,
                    Toast.LENGTH_SHORT
                ).show()
            },
            {
                binding.maneuverView.visibility = View.VISIBLE
                binding.maneuverView.renderManeuvers(maneuvers)
            }
        )
        binding.tripProgressView.render(
            tripProgressApi.getTripProgress(routeProgress)
        )
        val totalDistance = routeProgress.distanceTraveled + routeProgress.distanceRemaining
        val percentDistanceTraveled = (routeProgress.distanceTraveled / totalDistance) * 100
        if (
            percentDistanceTraveled >= percentDistanceTraveledThreshold &&
            routeProgress.distanceRemaining <= distanceRemainingThresholdInMeters &&
            !arrivalNotificationHasDisplayed
        ) {
            arrivalNotificationHasDisplayed = true
            showMessageWithTextToSpeech(this, "Kamu telah tiba di lokasi!", textToSpeech)
            GlobalScope.launch {
                delay(1000)
                withContext(Dispatchers.Main) {
                    showMessageWithTextToSpeech(
                        this@MainActivity,
                        "Apakah kamu ingin melanjutkan ke halaman penyeberangan?",
                        textToSpeech
                    )
                }
            }
            val alertDialog = AlertDialog.Builder(this)
                .setTitle("Konfirmasi")
                .setMessage("Apakah kamu ingin melanjutkan ke halaman penyeberangan?")
                .setPositiveButton("Ya") { dialog, _ ->
                    val intent = Intent(this, DetectionActivity::class.java)
                    startActivity(intent)
                    finish()
                    dialog.dismiss()
                }
                .setNegativeButton("Tidak") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            alertDialog.show()
        }
    }
    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            routeLineApi.setNavigationRoutes(
                routeUpdateResult.navigationRoutes
            ) { value ->
                binding.mapView.getMapboxMap().getStyle()?.apply {
                    routeLineView.renderRouteDrawData(this, value)
                }
            }
            viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
            viewportDataSource.evaluate()
        } else {
            val style = binding.mapView.getMapboxMap().getStyle()
            if (style != null) {
                routeLineApi.clearRouteLine { value ->
                    routeLineView.renderClearRouteLineValue(
                        style,
                        value
                    )
                }
                routeArrowView.render(style, routeArrowApi.clearArrows())
            }
            viewportDataSource.clearRouteData()
            viewportDataSource.evaluate()
        }
    }

    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            @SuppressLint("MissingPermission")
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.registerRoutesObserver(routesObserver)
                mapboxNavigation.registerLocationObserver(locationObserver)
                mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
                mapboxNavigation.registerRouteProgressObserver(replayProgressObserver)
                mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
                mapboxNavigation.startTripSession()
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.unregisterRoutesObserver(routesObserver)
                mapboxNavigation.unregisterLocationObserver(locationObserver)
                mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
                mapboxNavigation.unregisterRouteProgressObserver(replayProgressObserver)
                mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
            }
        },
        onInitialize = this::initNavigation
    )
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private var destinationValue = Point.fromLngLat(107.1359, -6.8272)
    private lateinit var originValue: Point

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Home"

        auth = Firebase.auth
        val gso = GoogleSignInOptions
            .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val dialogResult:DialogResult = DialogResult(this@MainActivity)
                dialogResult.setTitle("Keluar Aplikasi")
                dialogResult.setImage(R.drawable.exit)
                dialogResult.setMessage("Apakah anda yakin ingin keluar aplikasi?")
                dialogResult.setPositiveButton("Ya", onClickListener = {
                    finish()
                })
                dialogResult.setNegativeButton("Tidak", onClickListener = {
                    dialogResult.dismiss()
                })
                dialogResult.show()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
        textToSpeech = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                textToSpeech.language = Locale.getDefault()
            }
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        viewportDataSource = MapboxNavigationViewportDataSource(binding.mapView.getMapboxMap())
        navigationCamera = NavigationCamera(
            binding.mapView.getMapboxMap(),
            binding.mapView.camera,
            viewportDataSource
        )
        binding.mapView.camera.addCameraAnimationsLifecycleListener(
            NavigationBasicGesturesHandler(navigationCamera)
        )
        navigationCamera.registerNavigationCameraStateChangeObserver { navigationCameraState ->
            when (navigationCameraState) {
                NavigationCameraState.TRANSITION_TO_FOLLOWING,
                NavigationCameraState.FOLLOWING -> binding.recenter.visibility = View.INVISIBLE
                NavigationCameraState.TRANSITION_TO_OVERVIEW,
                NavigationCameraState.OVERVIEW,
                NavigationCameraState.IDLE -> binding.recenter.visibility = View.VISIBLE
            }
        }
        val overviewPadding = ViewPaddingUtils.getOverviewPadding(this.resources)
        val followingPadding = ViewPaddingUtils.getFollowingPadding(this.resources)

        viewportDataSource.overviewPadding = overviewPadding
        viewportDataSource.followingPadding = followingPadding

        val distanceFormatterOptions = DistanceFormatterOptions.Builder(this).build()
        maneuverApi = MapboxManeuverApi(
            MapboxDistanceFormatter(distanceFormatterOptions)
        )
        tripProgressApi = MapboxTripProgressApi(
            TripProgressUpdateFormatter.Builder(this)
                .distanceRemainingFormatter(
                    DistanceRemainingFormatter(distanceFormatterOptions)
                )
                .timeRemainingFormatter(
                    TimeRemainingFormatter(this)
                )
                .percentRouteTraveledFormatter(
                    PercentDistanceTraveledFormatter()
                )
                .estimatedTimeToArrivalFormatter(
                    EstimatedTimeToArrivalFormatter(this, TimeFormat.NONE_SPECIFIED)
                )
                .build()
        )

        speechApi = MapboxSpeechApi(
            this,
            getString(R.string.mapbox_access_token),
            "id-ID"
        )
        voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(
            this,
            getString(R.string.mapbox_access_token),
            "id-ID"
        )
        val mapboxRouteLineOptions = MapboxRouteLineOptions.Builder(this)
            .withRouteLineBelowLayerId("road-label-navigation")
            .build()
        routeLineApi = MapboxRouteLineApi(mapboxRouteLineOptions)
        routeLineView = MapboxRouteLineView(mapboxRouteLineOptions)
        val routeArrowOptions = RouteArrowOptions.Builder(this).build()
        routeArrowView = MapboxRouteArrowView(routeArrowOptions)
        binding.mapView.getMapboxMap().loadStyleUri(Style.TRAFFIC_DAY) {
            binding.mapView.gestures.addOnMapLongClickListener { point ->
                findRoute(point)
                true
            }
        }
        setupUI()

    }

    val GPS_REQUEST_CODE = 1001
    private fun gpsDisabledDialog() {
        val dialogResult = DialogResult(this)
        dialogResult.setTitle("GPS tidak aktif")
        dialogResult.setMessage("Pastikan GPS anda aktif dan coba lagi")
        dialogResult.setImage(R.drawable.map)
        dialogResult.setPositiveButton("Aktifkan", onClickListener = {
            startActivityForResult(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),GPS_REQUEST_CODE)
            dialogResult.dismiss()
        })
        dialogResult.setNegativeButton("Tutup", onClickListener = {
            finish()
        })
        dialogResult.show()
    }

    private fun noConnectionDialog() {
        val dialogResult = DialogResult(this)
        dialogResult.setTitle("Tidak ada koneksi")
        dialogResult.setMessage("Pastikan anda terhubung dengan internet dan coba lagi")
        dialogResult.setImage(R.drawable.error_connection)
        dialogResult.setNegativeButton("Tutup", onClickListener = {
            dialogResult.dismiss()
        })
        dialogResult.show()
    }

    private fun serverErrorDialog(){
        val dialogResult = DialogResult(this)
        dialogResult.setTitle("Server Error")
        dialogResult.setMessage("Terjadi kesalahan pada server, silahkan coba lagi")
        dialogResult.setImage(R.drawable.error_connection)
        dialogResult.setNegativeButton("Tutup", onClickListener = {
            dialogResult.dismiss()
        })
        dialogResult.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode==GPS_REQUEST_CODE){
            Handler(Looper.getMainLooper()).postDelayed({
                initNavigation()
            }, 2000)
        }
    }

    fun getNearestTrafficLight() {

        binding.btnSearchTraffic.isEnabled = false
        if (!::originValue.isInitialized) {
            return
        }
        auth = Firebase.auth
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        firebaseUser.getIdToken(true).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = "Bearer "+task.result?.token
                val radius = 1000.0
                val lat = originValue.latitude()
                val long = originValue.longitude()
                NetworkConfig(token).getService().getNearestCrossing(lat, long, radius,token).enqueue(
                    object : Callback<NearbyTrafficLightResponse> {
                        override fun onResponse(
                            call: Call<NearbyTrafficLightResponse>,
                            response: Response<NearbyTrafficLightResponse>
                        ) {
                            if (response.isSuccessful) {
                                val data: NearbyTrafficLightResponse? = response.body()
                                if (data != null) {
                                    // Mengupdate nilai destinationValue berdasarkan data yang diterima
                                    val nearestTrafficLight = data.data
                                    if (nearestTrafficLight != null) {
                                        val destination = Point.fromLngLat(
                                            nearestTrafficLight.longitude,
                                            nearestTrafficLight.latitude
                                        )
                                        destinationValue = destination
                                        findRoute(destination)
                                    }
                                }
                            }else{
                                serverErrorDialog()
                                binding.btnSearchTraffic.isEnabled = true
                            }

                        }

                        override fun onFailure(call: Call<NearbyTrafficLightResponse>, t: Throwable) {
                            noConnectionDialog()
                            binding.btnSearchTraffic.isEnabled = true
                        }
                    }
                )
            } else {
                // Handle kesalahan jika gagal mendapatkan token
                noConnectionDialog()
                binding.btnSearchTraffic.isEnabled = true
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sign_out_menu -> {
                signOut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun signOut() {
        val dialogResult:DialogResult = DialogResult(this@MainActivity)
        dialogResult.setTitle("Logout")
        dialogResult.setImage(R.drawable.logout)
        dialogResult.setMessage("Apakah anda yakin ingin logout?")
        dialogResult.setPositiveButton("Ya", onClickListener = {
            auth.signOut()
            googleSignInClient.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        })
        dialogResult.setNegativeButton("Tidak", onClickListener = {
            dialogResult.dismiss()
        })
        dialogResult.show()

        
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initNavigation()
            } else {
                showMessageWithTextToSpeech(this, "Izin lokasi ditolak", textToSpeech)
            }
        }
    }

    private fun setupUI() {
        binding.btnSearchTraffic.setOnClickListener {
            getNearestTrafficLight()

        }

        binding.btnFabCamera.setOnClickListener {
            startActivity(Intent(this, DetectionActivity::class.java))
            finish()
        }

        binding.stop.setOnClickListener {
            clearRouteAndStopNavigation()
        }
        binding.recenter.setOnClickListener {
            navigationCamera.requestNavigationCameraToFollowing()
            binding.routeOverview.showTextAndExtend(BUTTON_ANIMATION_DURATION)
        }
        binding.routeOverview.setOnClickListener {
            navigationCamera.requestNavigationCameraToOverview()
            binding.recenter.showTextAndExtend(BUTTON_ANIMATION_DURATION)
        }
        binding.soundButton.setOnClickListener {
            isVoiceInstructionsMuted = !isVoiceInstructionsMuted
        }
        binding.soundButton.unmute()
    }


    private fun initNavigation() {
        MapboxNavigationApp.setup(
            NavigationOptions.Builder(this)
                .accessToken(getString(R.string.mapbox_access_token))
                .build()
        )
        binding.mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            this.locationPuck = LocationPuck2D(
                bearingImage = ContextCompat.getDrawable(
                    this@MainActivity,
                    R.drawable.navigation
                )
            )
            enabled = true
        }

        replayOriginLocation()
    }

    private fun replayOriginLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                mapboxReplayer.pushEvents(
                    listOf(
                        ReplayRouteMapper.mapToUpdateLocation(
                            Date().time.toDouble(),
                            Point.fromLngLat(location.longitude, location.latitude)
                        )
                    )
                )
                originValue = Point.fromLngLat(location.longitude, location.latitude)
                mapboxReplayer.playFirstLocation()
                mapboxReplayer.playbackSpeed(3.0)
            } else {
                gpsDisabledDialog()
                showMessageWithTextToSpeech(
                    this,
                    "Tidak dapat memperoleh lokasi pengguna",
                    textToSpeech
                )
            }
        }
    }


    private fun findRoute(destination: Point) {

        showMessageWithTextToSpeech(this, "Mohon Tunggu", textToSpeech)

        if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: android.location.Location? ->
                if (location != null) {
                    val originPoint = Point.fromLngLat(location.longitude, location.latitude)
                    mapboxNavigation.requestRoutes(
                        RouteOptions.builder()
                            .applyDefaultNavigationOptions()
                            .applyLanguageAndVoiceUnitOptions(this)
                            .profile(DirectionsCriteria.PROFILE_WALKING)
                            .coordinatesList(listOf(originPoint, destination))
                            .bearingsList(
                                listOf(
                                    Bearing.builder()
                                        .angle(location.bearing.toDouble())
                                        .degrees(45.0)
                                        .build(),
                                    null
                                )
                            )
                            .layersList(listOf(mapboxNavigation.getZLevel(), null))
                            .build(),
                        object : NavigationRouterCallback {
                            override fun onCanceled(
                                routeOptions: RouteOptions,
                                routerOrigin: RouterOrigin
                            ) {
                                // no impl
                            }

                            override fun onFailure(
                                reasons: List<RouterFailure>,
                                routeOptions: RouteOptions
                            ) {
                                //
                            }

                            override fun onRoutesReady(
                                routes: List<NavigationRoute>,
                                routerOrigin: RouterOrigin
                            ) {

                                setRouteAndStartNavigation(routes)
                                binding.btnSearchTraffic.isEnabled = true
                            }
                        }
                    )

                } else {
                    gpsDisabledDialog()
                    showMessageWithTextToSpeech(
                        this,
                        "Tidak dapat memperoleh lokasi pengguna",
                        textToSpeech
                    )
                    binding.btnSearchTraffic.isEnabled = true
                }
            }
    }

    private fun setRouteAndStartNavigation(routes: List<NavigationRoute>) {

        showMessageWithTextToSpeech(this, "Lokasi ditemukan!", textToSpeech)

        mapboxNavigation.setNavigationRoutes(routes)
        binding.soundButton.visibility = View.VISIBLE
        binding.routeOverview.visibility = View.VISIBLE
        binding.tripProgressCard.visibility = View.VISIBLE
        binding.btnSearchTraffic.visibility = View.INVISIBLE
        binding.btnFabCamera.visibility = View.INVISIBLE

        navigationCamera.requestNavigationCameraToOverview()
    }

    private fun clearRouteAndStopNavigation() {
        mapboxNavigation.setNavigationRoutes(listOf())
        binding.soundButton.visibility = View.INVISIBLE
        binding.maneuverView.visibility = View.INVISIBLE
        binding.routeOverview.visibility = View.INVISIBLE
        binding.tripProgressCard.visibility = View.INVISIBLE
        binding.btnSearchTraffic.visibility = View.VISIBLE
        binding.btnFabCamera.visibility = View.VISIBLE
    }

    fun showMessageWithTextToSpeech(context: Context, message: String, textToSpeech: TextToSpeech) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapboxReplayer.finish()
        if (::maneuverApi.isInitialized)
            maneuverApi.cancel()

        if (::routeLineApi.isInitialized)
            routeLineApi.cancel()
        if (::routeLineView.isInitialized)
            routeLineView.cancel()
        if(::speechApi.isInitialized)
            speechApi.cancel()
        if(::voiceInstructionsPlayer.isInitialized)
            voiceInstructionsPlayer.shutdown()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}