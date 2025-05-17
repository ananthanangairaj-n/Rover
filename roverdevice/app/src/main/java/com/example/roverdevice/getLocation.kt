package com.example.roverdevice

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.*
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.database.*
import org.osmdroid.util.GeoPoint
import java.net.URL
import kotlin.math.*

class getLocation : Service(), SensorEventListener {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var database: DatabaseReference
    private var roverName: String? = null
    private val markerList = mutableListOf<GeoPoint>()
    private var currentMarkerIndex = 0

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var azimuth = 0f

    private var isManualEnabled = false

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())

        database = FirebaseDatabase.getInstance().reference
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)

        startLocationUpdates()

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        roverName = intent?.getStringExtra("rover_name") ?: "default_rover"
        checkModeAndMarkerUpload()
        fetchMarkersFromFirebase {
            currentMarkerIndex = 0
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "location_service"
        val channel = NotificationChannel(
            channelId,
            "Rover GPS Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Rover GPS Tracking")
            .setContentText("Updating GPS coordinates...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 500
            fastestInterval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    val lat = location.latitude
                    val lng = location.longitude
                    updateCoordinatesInFirebase(lat, lng)
                    if (markerList.isNotEmpty() && !isManualEnabled) {
                        navigateToNextMarker(GeoPoint(lat, lng))
                    }
                    if(isManualEnabled){
                        val modeRef = roverName?.let { database.child(it) }
                        modeRef?.child("movement")?.addValueEventListener(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val dir = snapshot.getValue(String::class.java)
                                if (dir != null) {
                                    sendCommandToESP(dir)
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {
                                Log.e("FirebaseData", "Failed to read dir", error.toException())
                            }
                        })
                    }
                }
            }
        }

        if (checkLocationPermissions()) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun checkLocationPermissions(): Boolean = true

    private fun updateCoordinatesInFirebase(lat: Double, lng: Double) {
        val locationData = mapOf(
            "latitude" to lat,
            "longitude" to lng,
            "timestamp" to System.currentTimeMillis()
        )
        roverName?.let { name ->
            database.child(name).child("location").setValue(locationData)
                .addOnSuccessListener {
                    Log.d("Firebase", "GPS Updated: $lat, $lng for $name")
                }
                .addOnFailureListener {
                    Log.e("Firebase", "Failed to update GPS: ${it.message}")
                }
        }
    }

    private fun checkModeAndMarkerUpload() {

        val modeRef = roverName?.let { database.child(it) }
        modeRef?.child("mode")?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val mode = snapshot.getValue(String::class.java)
                isManualEnabled = mode == "manual"
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseData", "Failed to read mode", error.toException())
            }
        })

        modeRef?.child("MarkerUpload")?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val shouldUpdate = snapshot.getValue(Boolean::class.java) == true
                if (shouldUpdate) {
                    modeRef.child("MarkerUpload").setValue(false)
                    fetchMarkersFromFirebase {
                        currentMarkerIndex = 0
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseData", "Failed to read MarkerUpload", error.toException())
            }
        })
    }

    private fun fetchMarkersFromFirebase(onMarkersLoaded: () -> Unit) {
        database.child(roverName!!).child("markers")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    markerList.clear()
                    for (marker in snapshot.children) {
                        val lat = marker.child("latitude").getValue(Double::class.java) ?: 0.0
                        val lng = marker.child("longitude").getValue(Double::class.java) ?: 0.0
                        markerList.add(GeoPoint(lat, lng))
                    }
                    if (markerList.isNotEmpty()) {
                        Toast.makeText(this@getLocation, "Markers loaded: ${markerList.size}", Toast.LENGTH_SHORT).show()
                        onMarkersLoaded()
                    } else {
                        Toast.makeText(this@getLocation, "No markers found.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Failed to load markers: ${error.message}")
                }
            })
    }

    private fun navigateToNextMarker(currentLocation: GeoPoint) {
        if (currentMarkerIndex >= markerList.size) {
            Toast.makeText(this, "Reached the final marker!", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        val target = markerList[currentMarkerIndex]
        val distance = currentLocation.distanceToAsDouble(target)

        if (distance < 5.0) {
            Toast.makeText(this, "Reached Marker ${currentMarkerIndex + 1}", Toast.LENGTH_SHORT).show()
            sendCommandToESP("stop")
            currentMarkerIndex++
            return
        }

        val bearing = calculateBearing(currentLocation, target)
        val direction = determineDirection(bearing)

        Toast.makeText(this, "Move: $direction", Toast.LENGTH_SHORT).show()
        sendCommandToESP(direction)
        Log.d("Navigation", "Moving $direction")

//        roverName?.let {
//            database.child("movement").child(it).setValue(direction)
//        }
    }

    private fun calculateBearing(start: GeoPoint, end: GeoPoint): Double {
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val lat2 = Math.toRadians(end.latitude)
        val lon2 = Math.toRadians(end.longitude)

        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    private fun determineDirection(bearing: Double): String {
        val diff = (bearing - azimuth + 360) % 360
        return when {
            diff < 45.0 || diff > 315.0 -> "front"
            diff in 45.0..135.0 -> "right"
            diff in 135.0..225.0 -> "back"
            diff in 225.0..315.0 -> "left"
            else -> "Unknown"
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> System.arraycopy(event.values, 0, gravity, 0, event.values.size)
            Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(event.values, 0, geomagnetic, 0, event.values.size)
        }

        val R = FloatArray(9)
        val I = FloatArray(9)
        if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(R, orientation)
            azimuth = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360) % 360
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null


    fun sendCommandToESP(direction: String) {
        val ip = "http://192.168.192.184" // Replace with ESP's IP
        val url = "$ip/move?dir=$direction"

        Thread {
            try {
                val result = URL(url).readText()
                Log.d("ESP", "Response: $result")
            } catch (e: Exception) {
                Log.e("ESP", "Error: ${e.message}")
            }
        }.start()
    }

}
