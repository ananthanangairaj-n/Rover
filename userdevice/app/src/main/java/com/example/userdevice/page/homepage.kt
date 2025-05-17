package com.example.userdevice

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.example.userdevice.databinding.ActivityHomepageBinding


import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable


class homepage : AppCompatActivity() {
    private lateinit var binding: ActivityHomepageBinding

    val supabase = createSupabaseClient(
        supabaseUrl = "https://wyjubbeetkxrzveusyvu.supabase.co", // Replace with your Supabase project URL
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Ind5anViYmVldGt4cnp2ZXVzeXZ1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDE1OTQyNzUsImV4cCI6MjA1NzE3MDI3NX0.wxpOKGx_RWLYeBKhXUffvxcnPUrXxxOqFh6MrJdzFAg" // Replace with your Supabase API key
    ) {
        install(Postgrest)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomepageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userid = intent.getIntExtra("user_id",0)

        binding.connectButton.setOnClickListener {
            val connect = binding.connectInput.text.toString().trim()
            if (connect.isNotEmpty()) {
                checkRoverExists(connect,userid)
            } else {
                Toast.makeText(this, "Enter a connection ID", Toast.LENGTH_SHORT).show()
            }
        }

        binding.listRoverButton.setOnClickListener{
            val intent = Intent(this, listrover::class.java)
            intent.putExtra("user_id", userid)
            startActivity(intent)
        }
    }
    private fun checkRoverExists(connect: String,userid: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val existingRovers = supabase.from("rovertable")
                    .select {
                        filter {
                            eq("string", connect) // Replace with your actual column name
                        }
                    }
                    .decodeList<Rover>() // Ensure you have a matching data class

                runOnUiThread {
                    if (existingRovers.isNotEmpty()) {
                        val roverId = existingRovers[0].roverid
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                // Check if the combination already exists in userrovertable
                                val existingEntries = supabase.from("userrovertable")
                                    .select {
                                        filter {
                                            if (roverId != null) {
                                                eq("userid",userid)
                                                eq("roverid", roverId)
                                            }
                                        }
                                    }
                                    .decodeList<UserRover>()



                                if (existingEntries.isEmpty()) {
                                    // Insert only if it doesn't exist
                                    val user = UserRover(userid, roverId)
                                    supabase.from("userrovertable").insert(user)
                                    runOnUiThread {
                                        Toast.makeText(this@homepage, "Rover linked successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    runOnUiThread {
                                        Toast.makeText(this@homepage, "User is already linked to this rover.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    Toast.makeText(this@homepage, "Error inserting: ${e.message}", Toast.LENGTH_SHORT).show()
                                    println("${e.message}")
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this@homepage, "Incorrect connect", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    println("${e.message}")
                    Toast.makeText(this@homepage, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Serializable
data class Rover(
    val rovername: String,
    val roverpassword: String,
    val roverid: Int? = null,  // Make userid optional
    val string: String? = null,
    val heartbeat: String?= null
)

@Serializable
data class UserRover(
    val userid: Int ? = null,
    val roverid: Int ? = null
)