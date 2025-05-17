package com.example.userdevice

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.userdevice.databinding.ActivityListroverBinding


import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant


class listrover : AppCompatActivity() {
    private lateinit var binding: ActivityListroverBinding

    val supabase = createSupabaseClient(
        supabaseUrl = "https://wyjubbeetkxrzveusyvu.supabase.co", // Replace with your Supabase project URL
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Ind5anViYmVldGt4cnp2ZXVzeXZ1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDE1OTQyNzUsImV4cCI6MjA1NzE3MDI3NX0.wxpOKGx_RWLYeBKhXUffvxcnPUrXxxOqFh6MrJdzFAg" // Replace with your Supabase API key
    ) {
        install(Postgrest)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListroverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val userid = intent.getIntExtra("user_id",0)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        fetchRovers(userid)
        Constants.isIntiatedNow = true
        Constants.isCallEnded = true

    }
    private fun fetchRovers(userId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) { // Keep fetching data every 5 seconds
                try {
                    val userRovers = supabase.from("userrovertable")
                        .select { filter { eq("userid", userId) } }
                        .decodeList<UserRover>()

                    val roverIds = userRovers.map { it.roverid }

                    if (roverIds.isNotEmpty()) {
                        val rovers = supabase.from("rovertable")
                            .select {
                                filter {
                                    or {
                                        roverIds.forEach { roverId ->
                                            if (roverId != null) {
                                                eq("roverid", roverId)
                                            }
                                        }
                                    }
                                }
                            }
                            .decodeList<Rover>()

                        runOnUiThread {
                            binding.recyclerView.adapter = RoverAdapter(rovers) { selectedRover ->
                                Log.d("listrover", "Selected Rover: ${selectedRover.rovername}, ${selectedRover.heartbeat}, ${selectedRover.roverpassword}")

                                val isOnline = selectedRover.heartbeat?.let {
                                    Instant.now().epochSecond - Instant.parse(it + "Z").epochSecond <= 35
                                } ?: false

                                val intent = Intent(this@listrover, RTCActivity::class.java).apply {
                                    putExtra("rover_name", selectedRover.rovername ?: "Unknown")
                                    putExtra("rover_status", if (isOnline) "Online" else "Offline")
                                    putExtra("meetingID", selectedRover.rovername)
                                    putExtra("isJoin", true)
                                    putExtra("rover_phone", selectedRover.roverpassword ?: "N/A")
                                }
                                startActivity(intent)
                            }
                        }

                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@listrover, "Er: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                kotlinx.coroutines.delay(5000) // Auto-refresh every 5 seconds
            }
        }
    }



}




