package com.example.roverdevice

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.roverdevice.databinding.ActivityHomepageBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.UUID

class homepage : AppCompatActivity() {
    private lateinit var binding: ActivityHomepageBinding
    private val handler = Handler(Looper.getMainLooper())

    private val supabase = createSupabaseClient(
        supabaseUrl = "https://wyjubbeetkxrzveusyvu.supabase.co", // Replace with your Supabase project URL
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Ind5anViYmVldGt4cnp2ZXVzeXZ1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDE1OTQyNzUsImV4cCI6MjA1NzE3MDI3NX0.wxpOKGx_RWLYeBKhXUffvxcnPUrXxxOqFh6MrJdzFAg" // Replace with your Supabase API key
    ) {
        install(Postgrest)
    }

    private var roverName: String? = null
    private var roverId: Int = 0
    private var roverString: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomepageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roverName = intent.getStringExtra("rover_name")
        roverId = intent.getIntExtra("rover_id", 0)
        roverString = intent.getStringExtra("rover_string")
        val db = Firebase.firestore
        Constants.isIntiatedNow = true
        Constants.isCallEnded = true
        startHeartbeatUpdate()
        startGPSService()

        binding.getButton.setOnClickListener {
            if (roverString.isNullOrEmpty()) {
                val newRandomString = UUID.randomUUID().toString().substring(0, 10)
                updateRoverString(newRandomString)
                roverString = newRandomString
            }
            binding.stringTextView.text = roverString ?: ""
        }

        binding.camButton.setOnClickListener {

            val docRef = roverName?.let { it1 -> db.collection("calls").document(it1) }
            if (docRef != null) {
                docRef.get().addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Delete existing document before starting new call
                            docRef.delete().addOnSuccessListener {
                                Log.d("Tag", "Existing rover removed")
                                roverName?.let { it1 -> launchCall(it1) }
                            }.addOnFailureListener { e ->
                                Log.e("Tag", "Failed to delete existing rover", e)
                            }

                    } else {
                        roverName?.let { it1 -> launchCall(it1) }
                    }
                }.addOnFailureListener { e ->
                    Log.e("Tag", "Error checking document", e)
                }
            }
        }
    }

    private fun updateRoverString(newString: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                supabase.from("rovertable").update(mapOf("string" to newString)) {
                    filter { eq("roverid", roverId) }
                }
                runOnUiThread {
                    binding.stringTextView.text = newString
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@homepage, "Error updating string: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startHeartbeatUpdate() {
        val updateTask = object : Runnable {
            override fun run() {
                updateHeartbeat()
                handler.postDelayed(this, 30000) // 30-second interval
            }
        }
        handler.post(updateTask)
    }

    private fun updateHeartbeat() {
        val timestamp = Clock.System.now().toString() // Correct ISO 8601 format
        CoroutineScope(Dispatchers.IO).launch {
            try {
                roverName?.let { name ->
                    supabase.from("rovertable")
                        .update(mapOf("heartbeat" to timestamp)) {
                            filter { eq("rovername", name) }
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startGPSService() {
        val serviceIntent = Intent(this, getLocation::class.java).apply {
            putExtra("rover_name", roverName)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null) // Stop updates when activity is destroyed

        // Stop the GPS service
        val serviceIntent = Intent(this, getLocation::class.java)
        stopService(serviceIntent)
    }

    private fun launchCall(roverName: String) {
        val intent = Intent(this@homepage, RTCActivity::class.java)
        intent.putExtra("meetingID", roverName)
        intent.putExtra("isJoin", false)
        intent.putExtra("rover_name", roverName)
        intent.putExtra("rover_id", roverId)
        intent.putExtra("rover_string",roverString)
        startActivity(intent)
    }
}
