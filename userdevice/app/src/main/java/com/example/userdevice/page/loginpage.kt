package com.example.userdevice

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.userdevice.databinding.ActivityLoginpageBinding
import android.widget.Toast


import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class loginpage : AppCompatActivity() {
    private lateinit var binding: ActivityLoginpageBinding

    val supabase = createSupabaseClient(
        supabaseUrl = "https://wyjubbeetkxrzveusyvu.supabase.co", // Replace with your Supabase project URL
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Ind5anViYmVldGt4cnp2ZXVzeXZ1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDE1OTQyNzUsImV4cCI6MjA1NzE3MDI3NX0.wxpOKGx_RWLYeBKhXUffvxcnPUrXxxOqFh6MrJdzFAg" // Replace with your Supabase API key
    ) {
        install(Postgrest)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginpageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startButton.setOnClickListener {

            val username = binding.usernameInput.text.toString().trim()
            val userpass= binding.userpasswordInput.text.toString().trim()

            if (username.isNotEmpty() && userpass.isNotEmpty()) {
                insertUserData(username , userpass)
            } else {
                Toast.makeText(this, "Please enter your username & password", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        binding.registerButton.setOnClickListener{
//            Toast.makeText(this, "Clicked", Toast.LENGTH_SHORT)
//                .show()
            val intent = Intent(this, registerpage::class.java)
            startActivity(intent)
        }
    }

    private fun insertUserData(name: String, pass: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val existingUsers = supabase.from("usertable")
                    .select {
                        filter {
                            eq("username", name)
                        }
                    }
                    .decodeList<User>()


                val user = existingUsers.first() // Get the first matched user
                val userId = user.userid ?: -0// Default to -1 if null
                val existingPass = user.userpassword

                if (existingUsers.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@loginpage, "Username does not exist!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                if (!existingPass.equals(pass)) {
                    runOnUiThread {
                        Toast.makeText(this@loginpage, "Incorrect password!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                  runOnUiThread {
                    Toast.makeText(this@loginpage, "Login Successful!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@loginpage, homepage::class.java)
                    intent.putExtra("user_id", userId)
                    startActivity(intent)
                    finish() // Close login page
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@loginpage, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Serializable
data class User(
    val username: String,
    val userpassword: String,
    val userid: Int? = null,  // Make userid optional
)