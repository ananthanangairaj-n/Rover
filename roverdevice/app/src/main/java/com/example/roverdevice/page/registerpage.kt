package com.example.roverdevice

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.example.roverdevice.databinding.ActivityRegisterpageBinding
import com.google.firebase.Timestamp


import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


class registerpage : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterpageBinding

    val supabase = createSupabaseClient(
        supabaseUrl = "https://wyjubbeetkxrzveusyvu.supabase.co", // Replace with your Supabase project URL
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Ind5anViYmVldGt4cnp2ZXVzeXZ1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDE1OTQyNzUsImV4cCI6MjA1NzE3MDI3NX0.wxpOKGx_RWLYeBKhXUffvxcnPUrXxxOqFh6MrJdzFAg" // Replace with your Supabase API key
    ) {
        install(Postgrest)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterpageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.signupButton.setOnClickListener {

            val rovername = binding.rovnameInput.text.toString().trim()
            val roverpass = binding.rovpasswordInput.text.toString().trim()

            if (rovername.isNotEmpty() && roverpass.isNotEmpty()) {
                insertUserData(rovername, roverpass)
            } else {
                Toast.makeText(this, "Please enter your username & password", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        binding.loginButton.setOnClickListener{
            val intent = Intent(this, loginpage::class.java)
            startActivity(intent)
        }


    }

    private fun insertUserData(name: String, pass: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rover = RoverName(name, pass)
                val existingUsers = supabase.from("rovertable")
                    .select {
                        filter {
                            eq("rovername", name)
                        }
                    }
                    .decodeList<RoverName>()
                val existingPass = supabase.from("rovertable")
                    .select {
                        filter {
                            eq("roverpassword", pass)
                        }
                    }
                    .decodeList<RoverName>()
                if (existingUsers.isNotEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@registerpage, "Rovername already exists!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                if (existingPass.isNotEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@registerpage, "Roverpassword already exists!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                // Insert user into database
                supabase.from("rovertable").insert(rover)
                runOnUiThread {
                    Toast.makeText(this@registerpage, "Rover registered successfully!", Toast.LENGTH_SHORT).show()
                }
            }
            catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@registerpage, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    println("${e.message}")
                }
            }
        }
    }
}

@Serializable
data class RoverName(
    val rovername: String,
    val roverpassword: String,
    val roverid: Int? = null,  // Make userid optional
    val string: String? = null,
    val heartbeat: String ?= null
)