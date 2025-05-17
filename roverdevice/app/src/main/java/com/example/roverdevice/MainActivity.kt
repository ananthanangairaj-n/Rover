//package com.example.roverdevice
//
//import android.content.Intent
//import android.os.Bundle
//import android.util.Log
//import androidx.appcompat.app.AppCompatActivity
//import com.example.roverdevice.databinding.ActivityMainBinding
//import com.google.firebase.Firebase
//import com.google.firebase.firestore.firestore
//
//
//class MainActivity : AppCompatActivity() {
//    private lateinit var binding: ActivityMainBinding
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//        val db = Firebase.firestore
//        Constants.isIntiatedNow = true
//        Constants.isCallEnded = true
//
//        // Initialize WebRTCManager
//        val roverName = intent.getStringExtra("rover_name") ?: "Unknown"
//        if (roverName.isEmpty()) {
//            Log.e("Tag", "Empty name")
//        }
//
//        val docRef = db.collection("calls").document(roverName)
//        docRef.get().addOnSuccessListener { document ->
//            if (document.exists()) {
//                // Delete existing document before starting new call
//                docRef.delete().addOnSuccessListener {
//                    Log.d("Tag", "Existing rover removed")
//                    launchCall(roverName)
//                }.addOnFailureListener { e ->
//                    Log.e("Tag", "Failed to delete existing rover", e)
//                }
//            } else {
//                launchCall(roverName)
//            }
//        }.addOnFailureListener { e ->
//            Log.e("Tag", "Error checking document", e)
//        }
//
//    }
//
//    private fun launchCall(roverName: String) {
//        val intent = Intent(this@MainActivity, RTCActivity::class.java)
//        intent.putExtra("meetingID", roverName)
//        intent.putExtra("isJoin", false)
//        startActivity(intent)
//    }
//}
