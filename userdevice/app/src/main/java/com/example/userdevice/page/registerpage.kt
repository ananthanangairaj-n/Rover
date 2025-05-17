package com.example.userdevice

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.example.userdevice.databinding.ActivityRegisterpageBinding


import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable



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

            val username = binding.usernameInput.text.toString().trim()
            val userpass = binding.userPassword.text.toString().trim()

            if (username.isNotEmpty() && userpass.isNotEmpty()) {
                insertUserData(username, userpass)
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
                val user = UserName(name, pass)
                val existingUsers = supabase.from("usertable")
                    .select {
                        filter {
                            eq("username", name)
                        }
                    }
                    .decodeList<UserName>()
                val existingPass = supabase.from("usertable")
                    .select {
                        filter {
                            eq("userpassword", pass)
                        }
                    }
                    .decodeList<UserName>()
                if (existingUsers.isNotEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@registerpage, "UserName already exists!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                if (existingPass.isNotEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@registerpage, "UserPassword already exists!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                // Insert user into database
                supabase.from("usertable").insert(user)
                runOnUiThread {
                    Toast.makeText(this@registerpage, "User registered successfully!", Toast.LENGTH_SHORT).show()
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
data class UserName(
    val username: String,
    val userpassword: String,
    val userid: Int? = null,  // Make userid optional
)