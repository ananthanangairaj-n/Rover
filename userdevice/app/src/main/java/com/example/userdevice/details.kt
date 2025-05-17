package com.example.userdevice

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import com.example.userdevice.databinding.ActivityDetailsBinding
import com.google.firebase.Firebase
import com.google.firebase.database.*
import com.google.firebase.firestore.firestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

class roverDetail : AppCompatActivity() {

    private lateinit var binding: ActivityDetailsBinding
    private lateinit var map: MapView
    private lateinit var database: DatabaseReference
    private var marker: Marker? = null
    private val markerCoordinates = mutableListOf<GeoPoint>()  // Store marker coordinates
    private var isMarkingEnabled = false  // Toggle marking mode
    private var roverMarker: Marker? = null
    private lateinit var rtcClient: RTCClient
    var roverName =""
    var index = 0;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Initialize ViewBinding
        binding = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // ✅ Get Rover Details from Intent
        roverName = intent.getStringExtra("rover_name") ?: "Unknown"
        val roverStatus = intent.getStringExtra("rover_status") ?: "Offline"
        val roverPhone = intent.getStringExtra("rover_phone") ?: "N/A"

        // ✅ Display Rover Details
        binding.detailsRoverName.text = "Name: $roverName"
        binding.detailsRoverStatus.text = "Status: $roverStatus"
        binding.detailsRoverPhone.text = "Phone: $roverPhone"

        // ✅ Initialize Firebase
        database = FirebaseDatabase.getInstance().reference

        // ✅ Initialize OSM configuration
        Configuration.getInstance().load(applicationContext, getPreferences(MODE_PRIVATE))

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.controller.setZoom(18.0)
        map.setMultiTouchControls(true)


        // ✅ Start listening for Firebase location updates
        listenForLocationUpdates(roverName)

        // ✅ Toggle Marking Mode on Button Click
        binding.addMarkerButton.setOnClickListener {
            isMarkingEnabled = !isMarkingEnabled
            if (isMarkingEnabled) {
                Toast.makeText(this, "Start marking!", Toast.LENGTH_SHORT).show()
                enableMarkerPlacement()
            } else {
                Toast.makeText(this, "Marking stopped.", Toast.LENGTH_SHORT).show()
            }
        }

        // ✅ Submit button to store markers and draw line
        binding.submitButton.setOnClickListener {
            submitMarkersToFirebase(roverName)
            drawInitialToFirstLine()
        }

        val db = Firebase.firestore
        Constants.isIntiatedNow = true
        Constants.isCallEnded = true

//        if (roverName.isEmpty()) {
//            Log.e("Tag", "Empty Rover name")
//        } else {
//            val intent = Intent(this, RTCActivity::class.java)
//            intent.putExtra("meetingID", roverName)
//            intent.putExtra("isJoin", true)
//            startActivity(intent)
//        }

    }

    private fun listenForLocationUpdates(roverName: String) {
        val locationRef = database.child("movloc").child(roverName)

        locationRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val latitude = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                    val longitude = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                    updateMarker(latitude, longitude, timestamp)
                    checkReachedMarkers(latitude,longitude)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle potential errors
                error.toException().printStackTrace()
            }
        })
    }

    private fun updateMarker(lat: Double, lng: Double, timestamp: Long) {
        val newPoint = GeoPoint(lat, lng)
        runOnUiThread {
            // ✅ Remove previous marker if it exists
            roverMarker?.let { map.overlays.remove(it) }

            // ✅ Create a new marker with the latest location
            roverMarker = Marker(map).apply {
                position = newPoint
                title = "Last Updated: $timestamp"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setDefaultIcon()  // 🌟 Use default OSM marker
            }

            // ✅ Add the new marker to the map
            map.overlays.add(roverMarker)

            // ✅ Move the camera to the new location and refresh the map
            map.controller.animateTo(newPoint)
            map.invalidate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        map.onDetach()  // Properly release the map resources
        rtcClient.endCall(roverName)
        binding.remoteView.isGone = false
        Constants.isCallEnded = true
        finish()
        startActivity(Intent(this@roverDetail, homepage::class.java))
    }

    private fun checkReachedMarkers(lat: Double, lng: Double) {
        if (markerCoordinates.isEmpty()) return
        val currentPoint = GeoPoint(lat, lng)
            val distance = currentPoint.distanceToAsDouble(markerCoordinates[index])
            if (distance < 5.0) {  // Consider as reached if within 5 meters
                index += 1;
                if (index == markerCoordinates.size)
                    Toast.makeText(this@roverDetail, "Reached", Toast.LENGTH_SHORT).show()
                else
                    drawInitialToFirstLine()

            }
    }



    // 🌟 Enable tapping on map to add custom markers
    private fun enableMarkerPlacement() {
        map.overlays.add(object : Overlay() {
            override fun onSingleTapConfirmed(event: android.view.MotionEvent, mapView: MapView): Boolean {
                if (!isMarkingEnabled) return false

                val geoPoint = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                addCustomMarker(geoPoint)
                markerCoordinates.add(geoPoint)
                drawLineBetweenMarkers()  // Draw line after each marker
                Toast.makeText(this@roverDetail, "Marker added: (${geoPoint.latitude}, ${geoPoint.longitude})", Toast.LENGTH_SHORT).show()
                return true
            }
        })
    }

    // ✅ Add Custom Marker with Red Symbol
    private fun addCustomMarker(geoPoint: GeoPoint) {
        val newMarker = Marker(map)



        newMarker.position = geoPoint
        newMarker.title = "User Marker (${geoPoint.latitude}, ${geoPoint.longitude})"
        newMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.overlays.add(newMarker)
        map.invalidate()
    }

    // ✅ Draw line connecting all markers
    private fun drawLineBetweenMarkers() {
        if (markerCoordinates.size < 2) return

        val line = Polyline()
        line.setPoints(markerCoordinates)
        line.color = ContextCompat.getColor(this, android.R.color.holo_blue_dark)
        line.width = 8.0f

        map.overlays.add(line)
        map.invalidate()
    }

    // ✅ Draw black line from initial marker to the first marker
    private fun drawInitialToFirstLine() {
        if (markerCoordinates.isEmpty()) return

        val line = Polyline()
        line.setPoints(listOf(roverMarker!!.position, markerCoordinates[index]))
        line.color = ContextCompat.getColor(this, android.R.color.black)
        line.width = 8.0f

        map.overlays.add(line)
        map.invalidate()
    }

    private fun submitMarkersToFirebase(roverName: String) {
        if (markerCoordinates.isEmpty()) {
            Toast.makeText(this, "No markers to submit.", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ Create a list of marker maps
        val markersList = markerCoordinates.map { point ->
            mapOf("latitude" to point.latitude, "longitude" to point.longitude)
        }

        // ✅ Store in Firebase
        database.child("movloc").child("markers").child(roverName).setValue(markersList)
            .addOnSuccessListener {
                Toast.makeText(this, "Markers submitted successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to submit markers: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }


}
