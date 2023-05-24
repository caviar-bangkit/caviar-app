package com.bangkit.caviar.ui.home

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import com.bangkit.caviar.Location
import com.bangkit.caviar.NetworkConfig
import com.bangkit.caviar.R
import com.bangkit.caviar.databinding.ActivityMainBinding
import com.bangkit.caviar.model.NearbyTrafficLightResponse
import com.bangkit.caviar.ui.login.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(R.string.exit_hint)
                    .setPositiveButton("Ya") { _, _ ->
                        signOut()
                    }
                    .setNegativeButton("Tidak") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
//        getNearestTrafficLight()

    }


    fun getNearestTrafficLight(lat: Double, long: Double, radius: Double) {
        NetworkConfig().getService().getNearestCrossing(lat, long, radius).enqueue(
            object : Callback<NearbyTrafficLightResponse> {
                override fun onResponse(
                    call: Call<NearbyTrafficLightResponse>,
                    response: Response<NearbyTrafficLightResponse>
                ) {
                    if (response.isSuccessful) {
                        val data: NearbyTrafficLightResponse? = response.body()
                        if (data != null) {

                        }
                    }
                }

                override fun onFailure(call: Call<NearbyTrafficLightResponse>, t: Throwable) {
                    Toast.makeText(this@MainActivity, t.message, Toast.LENGTH_SHORT).show()
                }
            }
        )
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
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}