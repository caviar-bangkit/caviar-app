package com.bangkit.caviar.detector
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Toast
import org.tensorflow.lite.task.vision.detector.Detection

class TrafficLightDetector(private val context: Context, private val textToSpeech: TextToSpeech) {

    enum class TrafficLightState {
        RED, YELLOW, GREEN,CROSSWALK, UNKNOWN
    }

    var isDetectedCrosswalk = false
        private set
    var measuereCrossWalk = false
        private set
    val handler = Handler(Looper.getMainLooper())
    val delayedTime = 3500
    val runnable:Runnable = object :Runnable {
        override fun run() {
            if (measuereCrossWalk){
                if(!isDetectedCrosswalk){
                    isDetectedCrosswalk = true
                    val message = "Terdeteksi ada penyeberangan jalan, Arahkan kamera agak ke atas untuk melihat lampu lalu lintas."
                    showMessageWithTextToSpeech(message,true)
                }
            }else{
                handler.postDelayed(this, delayedTime.toLong())
            }

        }
    }

    init {
        runHandler()
    }
    var currentState: TrafficLightState = TrafficLightState.UNKNOWN
        private set
    val vibratorPhone = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
    private fun updateState(newState : TrafficLightState) {
        if (newState == currentState || (newState ==TrafficLightState.CROSSWALK && isDetectedCrosswalk)){
            return
        }
        currentState = newState
        if (isDetectedCrosswalk){
            if(currentState == TrafficLightState.RED){
                val message = "Lampu merah terdeteksi, Silakan lanjutkan menyeberang jalan."
                showMessageWithTextToSpeech(message,true)
            }else if(currentState == TrafficLightState.GREEN) {
                val message =
                    "Lampu lalu lintas masih hijau, Tunggu sebentar untuk menyeberang jalan."
                showMessageWithTextToSpeech(message,true)
            }else if(currentState == TrafficLightState.YELLOW){
                val message =
                    "Lampu lalu lintas kuning, Tunggu sebentar untuk menyeberang jalan."
                showMessageWithTextToSpeech(message)
            }
        }
    }

    fun updateObjectDetected(objectList : MutableList<Detection>) {
        measuereCrossWalk = false
//        check is null
        if (objectList.isEmpty()){
            return
        }


//        looping
        for (obj in objectList){
            val state:TrafficLightState = toState(obj.categories[0].label)
            val score = obj.categories[0].score
//            check is crosswalk and score 0.93
            if (state == TrafficLightState.CROSSWALK && score > 0.85){
                measuereCrossWalk = true
            }
            if(score > 0.83){
                updateState(state)
            }
        }

    }

   private fun toState(label : String) : TrafficLightState{
        return when (label) {
            "red" -> TrafficLightState.RED
            "yellow" -> TrafficLightState.YELLOW
            "green" -> TrafficLightState.GREEN
            "crosswalk" -> TrafficLightState.CROSSWALK
            else -> TrafficLightState.UNKNOWN
        }
    }



    fun stopHandler() {
        handler.removeCallbacks(runnable)
    }

    fun runHandler() {
        handler.postDelayed(runnable, delayedTime.toLong())
    }


    fun resetState() {
        isDetectedCrosswalk = false
        currentState = TrafficLightState.RED
    }



    private fun showMessageWithTextToSpeech(message: String, vibrator : Boolean = false) {
//        wait until text to speech finish
        while (textToSpeech.isSpeaking){
            Thread.sleep(100)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        if(vibrator){
            vibratorPhone.vibrate(300)
        }

    }

}
