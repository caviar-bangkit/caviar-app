package com.bangkit.caviar.detector
import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast

class TrafficLightDetector(private val context: Context, private val textToSpeech: TextToSpeech) {

    enum class TrafficLightState {
        RED, YELLOW, GREEN,CROSSWALK, UNKNOWN
    }

    var isDetectedCrosswalk = false
        private set
    var currentState: TrafficLightState = TrafficLightState.UNKNOWN
        private set
    val vibratorPhone = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
    fun updateState(label : String) {
        val newState = when (label) {
            "red" -> TrafficLightState.RED
            "yellow" -> TrafficLightState.YELLOW
            "green" -> TrafficLightState.GREEN
            "crosswalk" -> TrafficLightState.CROSSWALK
            else -> TrafficLightState.UNKNOWN
        }
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
        }else{
            if(currentState == TrafficLightState.CROSSWALK){
                val message =
                    "Terdeteksi ada penyeberangan jalan, Arahkan kamera agak ke atas untuk melihat lampu lalu lintas."
                showMessageWithTextToSpeech(message,true)
                isDetectedCrosswalk = true
            }
        }
    }

    fun resetState() {
        isDetectedCrosswalk = false
        currentState = TrafficLightState.RED
    }



    private fun showMessageWithTextToSpeech(message: String, vibrator : Boolean = false) {
        if (textToSpeech.isSpeaking()){
            textToSpeech.stop()
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        if(vibrator){
            vibratorPhone.vibrate(300)
        }

    }

}
