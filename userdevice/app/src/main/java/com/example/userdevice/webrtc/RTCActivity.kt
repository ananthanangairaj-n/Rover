package com.example.userdevice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import com.example.userdevice.databinding.ActivityDetailsBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.webrtc.*
import java.io.ByteArrayOutputStream
import com.example.userdevice.Constants2.LABELS_PATH
import com.example.userdevice.Constants2.MODEL_PATH
import java.util.concurrent.Executors

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
import kotlin.math.log


@ExperimentalCoroutinesApi
class RTCActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_AUDIO_PERMISSION_REQUEST_CODE = 1
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
        private const val AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    }

    private lateinit var binding: ActivityDetailsBinding
    private lateinit var rtcClient: RTCClient
    lateinit var detector: Detector
    private lateinit var signallingClient: SignalingClient
    private var FRONT: String = "front"
    private var BACK: String = "back"
    private var LEFT: String = "left"
    private var RIGHT: String = "right"
    private var STOP: String = "stop"

    private val audioManager by lazy { RTCAudioManager.create(this) }

    val TAG = "MainActivity"

    private var meetingID: String = "test-call"
    private var isJoin = false
//    private var isMute = false
//    private var isVideoPaused = false
//    private var inSpeakerMode = true
    private var lastDetectionTime = 0L

    // Executor for running detection in background
    private val detectionExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var isDetecting = false
    private lateinit var sendCommand: Runnable

    private val sdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
        }
    }
    val handler = Handler(Looper.getMainLooper())
    private lateinit var map: MapView
    private lateinit var database: DatabaseReference
    private var marker: Marker? = null
    private val markerCoordinates = mutableListOf<GeoPoint>()  // Store marker coordinates
    private var isMarkingEnabled = false  // Toggle marking mode
    private var isManualEnabled = false
    private var roverMarker: Marker? = null
    var roverName =""
    var index = 0;

    private val locationHandler = android.os.Handler()
    private val locationRunnable = object : Runnable {
        override fun run() {
            listenForLocationUpdates(roverName)  // Force refresh logic if needed
            locationHandler.postDelayed(this, 10000) // Run every 10 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.hasExtra("meetingID"))
            meetingID = intent.getStringExtra("meetingID")!!
        if (intent.hasExtra("isJoin"))
            isJoin = intent.getBooleanExtra("isJoin", false)

        checkCameraAndAudioPermission()
        audioManager.selectAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)

        detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
        detector.setup()

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

        binding.clearButton.setOnClickListener {
            val ref = FirebaseDatabase.getInstance().getReference(roverName).child("markers")
            ref.removeValue()
                .addOnSuccessListener {
                    Log.d("Firebase", "Markers deleted")
                }
                .addOnFailureListener {
                    Log.e("Firebase", "Markers Delete failed: ${it.message}")
                }

        }

        binding.switchButton.setOnClickListener{
            isManualEnabled=!isManualEnabled
            if(isManualEnabled){
                changeMode("manual")
                binding.switchButton.text="Switch to Automatic"
            }
            else{
                changeMode("automatic")
                binding.switchButton.text="Switch to Manual"
            }
        }


        binding.left.setOnClickListener {
            manualMovement(LEFT)
        }
        binding.right.setOnClickListener {
            manualMovement(RIGHT)
        }
        binding.back.setOnClickListener {
            manualMovement(BACK)
        }
        binding.front.setOnClickListener{
            manualMovement(FRONT)
        }
        binding.stop.setOnClickListener{
            manualMovement(STOP)
        }



        locationHandler.post(locationRunnable)

    }

    private fun checkCameraAndAudioPermission() {
        if ((ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(this, AUDIO_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED)
        ) {
            requestCameraAndAudioPermission()
        } else {
            onCameraAndAudioPermissionGranted()
        }
    }

    private fun onCameraAndAudioPermissionGranted() {
        rtcClient = RTCClient(
            application,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
                    signallingClient.sendIceCandidate(p0, isJoin)
                    rtcClient.addIceCandidate(p0)
                }

                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
                    Log.e(TAG, "onAddStream: $p0")

                    runOnUiThread {
                        p0?.videoTracks?.get(0)?.addSink(binding.remoteView)
                    }
                    // 🤖 2. Add a second VideoSink for detection (runs off UI thread)

                    p0?.videoTracks?.get(0)?.addSink(object : VideoSink {
                        override fun onFrame(frame: VideoFrame?) {
                            frame?.let {
                                val bitmap = convertVideoFrameToBitmap(it)
                                val currentTime = System.currentTimeMillis()
                                if (!isDetecting && currentTime - lastDetectionTime > 500) {
                                    isDetecting=true
                                    detectionExecutor.execute {
                                        detector.detect(bitmap)
                                        isDetecting = false
                                        lastDetectionTime = currentTime
                                    }
                                }
                            }
                        }
                    })
                }
                fun convertVideoFrameToBitmap(videoFrame: VideoFrame): Bitmap {
                    val i420Buffer = videoFrame.buffer.toI420()!!
                    val width = i420Buffer.width
                    val height = i420Buffer.height
                    val yuvBytes = YuvHelper.i420ToNv21(i420Buffer)

                    val yuvImage = YuvImage(yuvBytes, ImageFormat.NV21, width, height, null)
                    val out = ByteArrayOutputStream()
                    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
                    val jpegData = out.toByteArray()

                    return BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
                }



                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                    Log.e(TAG, "onIceConnectionChange: $p0")
                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {
                    Log.e(TAG, "onIceConnectionReceivingChange: $p0")
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    Log.e(TAG, "onConnectionChange: $newState")
                }

                override fun onDataChannel(p0: DataChannel?) {
                    Log.e(TAG, "onDataChannel: $p0")
                }

                override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    Log.e(TAG, "onStandardizedIceConnectionChange: $newState")
                }

                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                    Log.e(TAG, "onAddTrack: $p0 \n $p1")
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    Log.e(TAG, "onTrack: $transceiver")
                }
            }
        )

        rtcClient.initSurfaceView(binding.remoteView)

        signallingClient = SignalingClient(meetingID, createSignallingClientListener())
        if (!isJoin)
            rtcClient.call(sdpObserver, meetingID)
    }

    private fun createSignallingClientListener() = object : SignalingClientListener {
        override fun onConnectionEstablished() {
//            binding.endCallButton.isClickable = true
        }

        override fun onOfferReceived(description: SessionDescription) {
            rtcClient.onRemoteSessionReceived(description)
            Constants.isIntiatedNow = false
            rtcClient.answer(sdpObserver, meetingID)
//            binding.remoteViewLoading.isGone = true
        }

        override fun onAnswerReceived(description: SessionDescription) {
            rtcClient.onRemoteSessionReceived(description)
            Constants.isIntiatedNow = false
//            binding.remoteViewLoading.isGone = true
        }

        override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
            rtcClient.addIceCandidate(iceCandidate)
        }

        override fun onCallEnded() {
            if (!Constants.isCallEnded) {
                Constants.isCallEnded = true
                rtcClient.endCall(meetingID)
                finish()
                startActivity(Intent(this@RTCActivity, homepage::class.java))
            }
        }
    }

    private fun requestCameraAndAudioPermission(dialogShown: Boolean = false) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_PERMISSION) &&
            ActivityCompat.shouldShowRequestPermissionRationale(this, AUDIO_PERMISSION) &&
            !dialogShown
        ) {
            showPermissionRationaleDialog()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(CAMERA_PERMISSION, AUDIO_PERMISSION),
                CAMERA_AUDIO_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera And Audio Permission Required")
            .setMessage("This app needs the camera and audio to function")
            .setPositiveButton("Grant") { dialog, _ ->
                dialog.dismiss()
                requestCameraAndAudioPermission(true)
            }
            .setNegativeButton("Deny") { dialog, _ ->
                dialog.dismiss()
                onCameraPermissionDenied()
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_AUDIO_PERMISSION_REQUEST_CODE &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            onCameraAndAudioPermissionGranted()
        } else {
            onCameraPermissionDenied()
        }
    }

    private fun onCameraPermissionDenied() {
        Toast.makeText(this, "Camera and Audio Permission Denied", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        manualMovement(STOP)
        changeMode("automatic")
        locationHandler.removeCallbacks(locationRunnable)
        signallingClient.destroy()
        detector.clear()
        super.onDestroy()
        map.onDetach()  // Properly release the map resources
    }

    fun onEmptyDetect() {
        runOnUiThread {
            binding.overlay.clear()
        }
    }


    fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            if (boundingBoxes.isNotEmpty()) {
                binding.overlay.setResults(boundingBoxes)
                binding.overlay.invalidate()
            } else {
                onEmptyDetect()  // Directly call the method to clear
            }
        }
    }



    private fun listenForLocationUpdates(roverName: String) {
        val locationRef = database.child(roverName).child("location")

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



    private fun checkReachedMarkers(lat: Double, lng: Double) {
        if (markerCoordinates.isEmpty()) return
        val currentPoint = GeoPoint(lat, lng)
        val distance = currentPoint.distanceToAsDouble(markerCoordinates[index])
        if (distance < 5.0) {  // Consider as reached if within 5 meters
            index += 1;
            if (index == markerCoordinates.size)
                Toast.makeText(this@RTCActivity, "Reached", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@RTCActivity, "Marker added: (${geoPoint.latitude}, ${geoPoint.longitude})", Toast.LENGTH_SHORT).show()
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
        database.child(roverName).child("MarkerUpload").setValue(true)
            .addOnSuccessListener {
            Toast.makeText(this, "successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to submit markers: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        database.child(roverName).child("markers").setValue(markersList)
            .addOnSuccessListener {
                Toast.makeText(this, "Markers submitted successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to submit markers: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun changeMode(mode:String){
        database.child(roverName).child("mode").setValue(mode)
            .addOnSuccessListener {
                Toast.makeText(this, "Mode Changed ${isManualEnabled}", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Mode Failed : ${it.message}", Toast.LENGTH_SHORT).show()
            }

    }

    private fun manualMovement(direction: String){
        if(isManualEnabled) {
            Toast.makeText(this, "clicked", Toast.LENGTH_SHORT).show()
            database.child(roverName).child("movement").setValue(direction)
                .addOnSuccessListener {
//                Toast.makeText(this, " submitted successfully!", Toast.LENGTH_SHORT).show()
                    Log.e("direction", "direction loaded")
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to load: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

    }


}
