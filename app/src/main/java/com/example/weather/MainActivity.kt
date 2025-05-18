package com.example.weather

// Image stuff
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import java.util.concurrent.Executors
import android.media.MediaScannerConnection

import androidx.core.net.toUri
import android.telephony.SmsManager
import android.graphics.drawable.BitmapDrawable

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.icu.text.SimpleDateFormat
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.common.util.concurrent.ListenableFuture
import android.widget.LinearLayout
import com.squareup.picasso.Picasso
import android.widget.RelativeLayout
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.Switch
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.appcompat.app.AlertDialog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import kotlin.text.get

class MainActivity : AppCompatActivity() {
    private lateinit var viewFinder: PreviewView
    private lateinit var videoCaptureButton: ImageButton
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null



    private var isRecording = false

    private val executor = Executors.newSingleThreadExecutor()
    private val SMS_PERMISSION_REQUEST_CODE = 101
    private val CALL_PERMISSION_REQUEST_CODE = 102

    private lateinit var textViewWeather: TextView
    private val editTextLocation: EditText? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private lateinit var fusedLocationClient: FusedLocationProviderClient //Location client promised varible type shit

    private lateinit var previewView: PreviewView
    private lateinit var recordButton: ImageButton

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var imageCapture: ImageCapture
    private lateinit var apiService: WeatherApiService

    private val APIKEY = "bd19138f2a75421f88535220251705"
    private var isSettingsVisible = false
    private var isCelsius = false // Default to Fahrenheit
    // Add this new function to your MainActivity class:
    private var REQUEST_CAMERA_CODE_PERMISSIONS = 10

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this) //Sets location client varible
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        viewFinder = findViewById(R.id.CAMERAAHHHH)
        videoCaptureButton = findViewById(R.id.sundayButton)
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }



        //Camera stuff
//        val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.CAMERAAHHHH)
//        val recordButton = findViewById<ImageButton>(R.id.saturdayButton)

//        recordButton.setOnClickListener {
//            if (isRecording) {
//                stopRecording()
//            } else {
//                if (checkCameraPermissions()) {
//                    Log.d("Teta", "Monkey")
//                    startRecording()
//                }else{
//                    Log.d("Teta", "Mexican")
//
//                }
//            }
//        }
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//        cameraProviderFuture.addListener({
//            cameraProvider = cameraProviderFuture.get()
//            bindCameraUseCases()
//        }, ContextCompat.getMainExecutor(this))



        // Initialize settings panel
        val settingsPanel = findViewById<LinearLayout>(R.id.settingsPanel)
        val btnCloseSettings = findViewById<Button>(R.id.btn_close_settings)
        val mainLayout = findViewById<RelativeLayout>(R.id.main)
        val tempUnitGroup = findViewById<RadioGroup>(R.id.tempUnitGroup)
        val radioCelsius = findViewById<RadioButton>(R.id.radio_celsius)
        val radioFahrenheit = findViewById<RadioButton>(R.id.radio_fahrenheit)
        val switchHumidity = findViewById<Switch>(R.id.switch_humidity)
        val switchWind = findViewById<Switch>(R.id.switch_wind)

        //Storm text
        val test = findViewById<TextView>(R.id.descriptionText)
        val btnGuide = findViewById<Button>(R.id.btn_guide)
        btnGuide.setOnClickListener {
            showUserGuide()
        }
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.weatherapi.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(WeatherApiService::class.java)

        // Settings button click listener
        val settingsButton = findViewById<ImageButton>(R.id.settingsButton)
        settingsButton.setOnClickListener {
            settingsPanel.visibility = View.VISIBLE
            mainLayout.alpha = 0.5f
            mainLayout.isClickable = false
        }

        // MONDAY BUTTON - Starts taking video using the front camera
        val mondayButton = findViewById<ImageButton>(R.id.mondayButton)
        mondayButton.setOnClickListener {
            seeIfSMSHasPermission()
        }

        // TUESDAY BUTTON - Starts taking video using the back camera
        val saturdayButton = findViewById<ImageButton>(R.id.saturdayButton)
        saturdayButton.setOnClickListener {
            makePhoneCall()
        }
//
//        // WEDNESDAY BUTTON - FRONT CAMERA picture
//        val wednesdayButton = findViewById<ImageButton>(R.id.wednesdayButton)
//        wednesdayButton.setOnClickListener {
//            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
//            if (hasPermissions()) {
//                takePhoto()
//            } else {
//                requestPermissions()
//            }
//        }

        // THURSDAY BUTTON - BACK CAMERA picture
        val thursdayButton = findViewById<ImageButton>(R.id.thursdayButton)
        thursdayButton.setOnClickListener {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            if (hasPermissions()) {
//                takePhoto()
            } else {
                requestPermissions()
            }
        }

        // Close settings button click listener
        btnCloseSettings.setOnClickListener {
            settingsPanel.visibility = View.GONE
            mainLayout.alpha = 1f
            mainLayout.isClickable = true
        }

        // Temperature unit change listener
        tempUnitGroup.setOnCheckedChangeListener { _, checkedId ->
            isCelsius = checkedId == R.id.radio_celsius
            // Update current temperature display
            val currentTempText = findViewById<TextView>(R.id.temperatureText).text.toString()
            if (currentTempText.isNotEmpty() && currentTempText != "N/A") {
                val tempValue = currentTempText.replace("°F", "").replace("°C", "").trim().toFloatOrNull()
                tempValue?.let {
                    updateTemperatureDisplay(it)
                }
            }
        }

        // Humidity switch listener
        switchHumidity.setOnCheckedChangeListener { _, isChecked ->
            findViewById<LinearLayout>(R.id.humidityLayout).visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Wind switch listener
        switchWind.setOnCheckedChangeListener { _, isChecked ->
            findViewById<LinearLayout>(R.id.windLayout).visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        //Button that changes city
        val changeButton = findViewById<Button>(R.id.fetchWeatherButton)
        //When button is clicked
        changeButton.setOnClickListener {
            val region: String = findViewById<TextView>(R.id.cityNameInput).text.toString().trim()
            if (!region.isEmpty()){
                fetchWeather(region)
            }
        }

        val locationButton = findViewById<ImageButton>(R.id.weatherIcon)
        //When button is clicked
        locationButton.setOnClickListener {
//            //Varible for the inputted location
            checkLocationPermission()
            seeIfSMSHasPermission()
            getCurrentLocation { latitude ->
                Log.d("LOCATION", "Got latitude: $latitude")
                sendSms(latitude)
                // use the latitude here
            }        }

        val captureButton = findViewById<ImageButton>(R.id.thursdayButton)
        val captureFrontButton = findViewById<ImageButton>(R.id.wednesdayButton)

        captureButton.setOnClickListener {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            if (hasPermissions()) {
//                cameraProviderFuture.get()?.unbindAll()
//                startCamera()
//                takePhoto()
//                sendMmsWithImage("6194966341", "DONKEY DONG", takePhoto())
            } else {
                requestPermissions()
            }
        }
//        val messageIMage = findViewById<ImageButton>(R.id.fridayButton)
//        messageIMage.setOnClickListener {
//            checkPermissions()
//        }



        captureFrontButton.setOnClickListener {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            if (hasPermissions()) {
//                cameraProviderFuture.get()?.unbindAll()
//                startCamera()
//                takePhoto()
            } else {
                requestPermissions()
            }
        }
        // Request permissions
        if (hasPermissions()) {
            startCamera()
        } else {
            requestPermissions()
        }


        videoCaptureButton.setOnClickListener {
            if (recording != null) {
                // Stop the current recording
                recording?.stop()
                recording = null
            } else {
                // Start new recording
                captureVideo()
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()






        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    private fun showUserGuide() {
        val guideMessage = """
        Monday sends a "I am not okay" text to the authorities.
        Sunday starts recording a video from the back camera, clicking on it again will stop the video.
        Clicking on the current weather icon sends the current location to a saved number.
        Saturday starts a call to 911

    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("User Guide")
            .setMessage(guideMessage)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
    /* ========== CALL FUNCTIONALITY ========== */
    private fun checkCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCallPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CALL_PHONE),
            CALL_PERMISSION_REQUEST_CODE
        )
    }

    private fun makePhoneCall() {
        val phoneNumber = "6194966341" // Replace with the number you want to call

        try {
            if (checkCallPermission()) {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                startActivity(callIntent)
            } else {
                requestCallPermission()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Call failed: ${e.message}", Toast.LENGTH_SHORT).show()

        }
        }
    private fun captureVideo() {
        val currentRecording = recording
        if (currentRecording != null) {
            // Currently recording - shouldn't happen as we toggle button
            return
        }

        // Create quality selector
        val qualitySelector = QualitySelector.from(
            Quality.HD,
            FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
        )

        // Create a new recording
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture?.output
            ?.prepareRecording(this, mediaStoreOutputOptions)
            ?.apply {
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            ?.start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        videoCaptureButton.isEnabled = true
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        } else {
                            recording?.close()
                            recording = null
//                            videoCaptureButton.text = "Start Recording"
                            Toast.makeText(baseContext,
                                "Video capture failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Recorder
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, videoCapture)

            } catch(exc: Exception) {
                Toast.makeText(this, "Use case binding failed", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

//    override fun onRequestPermissionsResult(
//        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == REQUEST_CODE_PERMISSIONS) {
//            if (allPermissionsGranted()) {
//                startCamera()
//            } else {
//                Toast.makeText(this,
//                    "Permissions not granted by the user.",
//                    Toast.LENGTH_SHORT).show()
//                finish()
//            }
//        }
//    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

























    private fun updateTemperatureDisplay(tempF: Float) {
        val tempText = findViewById<TextView>(R.id.temperatureText)
        if (isCelsius) {
            val tempC = (tempF - 32) * 5 / 9
            tempText.text = "%.1f°C".format(tempC)
        } else {
            tempText.text = "%.1f°F".format(tempF)
        }
    }

    /* ========== SMS/MMS FUNCTIONALITY ========== */
//    private fun seeIfSMSHasPermission() {
//        if (ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.SEND_SMS
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            ActivityCompat.requestPermissions(
//                this,
//                arrayOf(Manifest.permission.SEND_SMS),
//                SMS_PERMISSION_REQUEST_CODE
//            )
//        } else {
//            val imageUri = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a3/June_odd-eyed-cat_cropped.jpg/640px-June_odd-eyed-cat_cropped.jpg".toUri()
//            sendMmsWithImage("6194966341", "Whats Good", imageUri)
//        }
//    }

    private fun sendSms() {
        try {
            val phoneNumber = "6194966341"
            val message = "i LOVEVEEEEEEEE children"

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                this.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(
                phoneNumber,
                null,
                message,
                null,
                null
            )

            Toast.makeText(this, "SMS sent successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send SMS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMmsWithImage(phoneNumber: String, message: String, imageUri: Uri) {
        try {
            sendSms()
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }

            smsManager.sendMultimediaMessage(
                this,
                imageUri,
                "image/*",
                null,
                null
            )

            Toast.makeText(this, "MMS with image sent", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "MMS failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /* ========== CAMERA FUNCTIONALITY ========== */
    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            REQUEST_CODE_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CODE_PERMISSIONS -> {
                if (hasPermissions()) {
                    startCamera()
                } else {
                    Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
                }
            }
            SMS_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sendSms()
                } else {
                    Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            CALL_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    makePhoneCall()
                } else {
                    Toast.makeText(this, "Call permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

//    private fun startCamera() {
//        val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.camera_preview)
//        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//
//        cameraProviderFuture.addListener({
//            val cameraProvider = cameraProviderFuture.get()
//
//            // Preview
//            val preview = Preview.Builder()
//                .build()
//                .also {
//                    it.setSurfaceProvider(previewView.surfaceProvider)
//                }
//
//            // ImageCapture
//            imageCapture = ImageCapture.Builder()
//                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
//                .build()
//
//            try {
//                cameraProvider.unbindAll()
//                cameraProvider.bindToLifecycle(
//                    this,
//                    cameraSelector,
//                    preview,
//                    imageCapture
//                )
//            } catch (exc: Exception) {
//                Log.e(TAG, "Camera binding failed", exc)
//
//            }
//        }
//
//        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                sendSms("Help MEEEEE")
//            } else {
//                Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show()
//            }
//        }
//        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
////                getCurrentLocation()
//            } else {
//                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
//            }
//        }
//        if (requestCode == REQUEST_CODE_PERMISSIONS) {
//            if (allPermissionsGranted()) {
//                startCamera()
//            } else {
//                Toast.makeText(this,
//                    "Permissions not granted by the user.",
//                    Toast.LENGTH_SHORT).show()
//                finish()
//            }
//        }
////        if (requestCode == SMS_MMS_PERMISSION_REQUEST_CODE &&
////            grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
////            sendImageToNumber("6194966341") // Replace with recipient number
////        } else {
////            Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
////        }
////        when (requestCode) {
////            REQUEST_CAMERA_CODE_PERMISSIONS -> {
////                if (hasPermissions()) {
////                    // Permissions granted, bind camera use cases
////                    bindCameraUseCases()
////                } else {
////                    Toast.makeText(this, "Camera permissions denied", Toast.LENGTH_SHORT).show()
////                }
////
////            }
////            }
////            }
////        if (requestCode == REQUEST_CAMERA_CODE_PERMISSIONS &&
////            grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
////            bindCameraUseCases()
////        } else {
////            Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
////        }
//
//    }

//    private fun takePhoto() {
//    private fun startCameraPhoto() {
//        val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.camera_preview)
//        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//
//        cameraProviderFuture.addListener({
//            val cameraProvider = cameraProviderFuture.get()
//
//            // Preview
//            val preview = Preview.Builder()
//                .build()
//                .also {
//                    it.setSurfaceProvider(previewView.surfaceProvider)
//                }
//
//            // ImageCapture
//            imageCapture = ImageCapture.Builder()
//                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
//                .build()
//
//            // Select back camera
//
//            try {
//                cameraProvider.unbindAll()
//                cameraProvider.bindToLifecycle(
//                    this,
//                    cameraSelector,
//                    preview,
//                    imageCapture
//                )
//            } catch (exc: Exception) {
//                Log.e(TAG, "Camera binding failed", exc)
//            }
//        }, ContextCompat.getMainExecutor(this))
//    }

//    private fun takePhoto(): Uri {
//        // 1. Create content values for MediaStore
//        val contentValues = ContentValues().apply {
//            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
//            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
//            }
//        }
//
//        val outputOptions = ImageCapture.OutputFileOptions.Builder(
//            contentResolver,
//            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//            contentValues
//        ).build()
//        ) ?: run {
//            Toast.makeText(this, "Failed to create file", Toast.LENGTH_SHORT).show()
//            return "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a3/June_odd-eyed-cat_cropped.jpg/640px-June_odd-eyed-cat_cropped.jpg".toUri()
//        }
//
//        imageCapture.takePicture(
//            outputOptions,
//            ContextCompat.getMainExecutor(this),
//            object : ImageCapture.OnImageSavedCallback {
//                override fun onError(exc: ImageCaptureException) {
//                    Log.e(TAG, "Photo capture failed", exc)
//                }
//
//                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
//                    Toast.makeText(this@MainActivity, "Photo saved to gallery", Toast.LENGTH_SHORT).show()
//                }
//            }
//        )
//        return uri
//    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    /* ========== WEATHER FUNCTIONALITY ========== */
//    private fun updateTemperatureDisplay(tempF: Float) {
//        val tempText = findViewById<TextView>(R.id.temperatureText)
//        if (isCelsius) {
//            val tempC = (tempF - 32) * 5 / 9
//            tempText.text = "%.1f°C".format(tempC)
//        } else {
//            tempText.text = "%.1f°F".format(tempF)
//        }
//    }

//    fun fetchWeather(place: String) {
//        val call = apiService.getCurrentWeather(APIKEY, place)
////    companion object {
////        private const val TAG = "CameraXApp"
////        private const val REQUEST_CODE_PERMISSIONS = 10
////    }

    fun fetchWeather(place: String){
        val call  = apiService.getCurrentWeather(APIKEY, place)
        call.enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                if (response.isSuccessful) {
                    val weatherData = response.body()
                    val name = weatherData?.location?.name
                    val tempature = weatherData?.current?.temp_f
                    val mph = weatherData?.current?.wind_mph
                    val humidityValue = weatherData?.current?.humidity
                    val description = weatherData?.current?.condition?.text
                    val pic = weatherData?.current?.condition?.icon

                    val textTest = findViewById<TextView>(R.id.temperatureText)
                    if (tempature != null) {
                        if (isCelsius) {
                            val tempC = (tempature - 32) * 5 / 9
                            textTest.text = "%.1f°C".format(tempC)
                        } else {
                            textTest.text = "%.1f°F".format(tempature)
                        }
                    } else {
                        textTest.text = "N/A"
                    }

                    val cityTest = findViewById<TextView>(R.id.cityNameText)
                    cityTest.text = name.toString()
                    val windSpeed = findViewById<TextView>(R.id.windText)
                    windSpeed.text = mph.toString() + " mph"
                    val humidityLabel = findViewById<TextView>(R.id.humidityText)
                    humidityLabel.text = humidityValue.toString() + "%"
                    val descriptionLabel = findViewById<TextView>(R.id.descriptionText)
                    val weatherPic = findViewById<ImageView>(R.id.weatherIcon)
                    descriptionLabel.text = description.toString()

                    // Update humidity icon based on humidity value
                    val humidityIcon = findViewById<ImageView>(R.id.humidityIcon)
                    humidityValue?.let { humidity ->
                        when {
                            humidity < 20 -> humidityIcon.setImageResource(R.drawable.ic_no_humidity)
                            humidity in 20..60 -> humidityIcon.setImageResource(R.drawable.ic_half_humidity)
                            humidity > 60 -> humidityIcon.setImageResource(R.drawable.ic_full_humidity)
                        }
                    }

                    Picasso.get()
                        .load("https:" + pic.toString())
                        .into(weatherPic)
                } else {
                    Log.e("API", "Error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                Log.e("API", "Failure: ${t.message}")
            }
        })
    }









    private fun seeIfSMSHasPermission(){
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                SMS_PERMISSION_REQUEST_CODE
            )
        } else {
//            sendSms() // Permission already granted
//            val imageUri = getImageUriFromDrawable(R.drawable.ic_humidity)
            sendSms("I am not safe")
        }
    }

    private fun sendSms(message: String) {
        try {
            val phoneNumber = "6194966341" // Replace with recipient number
            val message = message


            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                this.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(
                phoneNumber,
                null, // Optional SMS service center number (use null for default)
                message,
                null, // Optional pending intent for sent status
                null  // Optional pending intent for delivery status
            )

            Toast.makeText(this, "SMS sent successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send SMS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }



    fun getImageUriFromDrawable(drawableId: Int): Uri {
        return try {
            // 1. Safely get drawable and convert to bitmap
            val drawable = ContextCompat.getDrawable(this, drawableId) ?:
            throw IllegalStateException("Drawable not found")

            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?:
            throw IllegalStateException("Drawable is not a bitmap")

            // 2. Create cache directory if needed
            val cacheDir = File(externalCacheDir?.path ?: cacheDir.path, "shared_images").apply {
                if (!exists()) mkdirs()
            }

            // 3. Create temp file
            val file = File(cacheDir, "img_${System.currentTimeMillis()}.jpg").apply {
                createNewFile()
            }

            // 4. Compress and save
            file.outputStream().use { os ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, os)) {
                    throw IOException("Failed to compress bitmap")
                }
            }

            // 5. Generate URI (with fallback)
            try {
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider", // NOTE: Changed from '.provider' to match standard
                    file
                )
            } catch (e: IllegalArgumentException) {
                Uri.fromFile(file) // Fallback for testing only (won't work on Android 7+)
            }
        } catch (e: Exception) {
            Log.e("ImageUri", "Error creating URI: ${e.message}")
            throw e // Or return a default URI if preferred
        }
    }
    // Usage:

//    private fun sendMmsWithImage(phoneNumber: String, message: String, imageUri: Uri) {
////        val imageUri = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a3/June_odd-eyed-cat_cropped.jpg/640px-June_odd-eyed-cat_cropped.jpg".toUri()
//        try {
//            sendSms(message)
//            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                getSystemService(SmsManager::class.java)
//            } else {
//                SmsManager.getDefault()
//            }
//
//            // For newer Android versions
//
//            smsManager.sendMultimediaMessage(
//                this,
//                imageUri,
//                "image/*",
//                null,
//                null
//            )
//
//            Toast.makeText(this, "MMS with image sent", Toast.LENGTH_SHORT).show()
//        } catch (e: Exception) {
//            Toast.makeText(this, "MMS failed: ${e.message}", Toast.LENGTH_SHORT).show()
//        }
//    }




    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
//                getCurrentLocation()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                // Explain why you need permission
                AlertDialog.Builder(this)
                    .setTitle("Location Permission Needed")
                    .setMessage("This app needs location permission to show your current location")
                    .setPositiveButton("OK") { _, _ ->
                        requestLocationPermission()
                    }
                    .create()
                    .show()
            }
            else -> {
                // Request permission
                requestLocationPermission()
            }
        }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }



    private fun getCurrentLocation(callback: (String) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback("error")
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val latitude = location.latitude.toString()
                    val longitude = location.longitude
                    Toast.makeText(
                        this,
                        "Lat: $latitude, Long: $longitude",
                        Toast.LENGTH_LONG
                    ).show()
                    callback("Lat: $latitude, Long: $longitude")
                } else {
                    // Optionally: call requestNewLocation() and pass callback to that too
                    callback("no location")
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Error getting location: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                callback("error")
            }
    }


    private fun requestNewLocation() {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 10000
            fastestInterval = 5000
        }

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

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    locationResult.lastLocation?.let { location ->
                        val latitude = location.latitude
                        val longitude = location.longitude
                        Toast.makeText(
                            this@MainActivity,
                            "New Location - Lat: $latitude, Long: $longitude",
                            Toast.LENGTH_LONG
                        ).show()
                        sendSms("New Location - Lat: $latitude, Long: $longitude")
                        // Remove updates after getting location
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            },
            Looper.getMainLooper()
        )
    }


    private val SMS_MMS_PERMISSION_REQUEST_CODE = 100

//    private fun checkPermissions() {
//        val permissionsNeeded = mutableListOf<String>()
//
//        // For Android 13+
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
//                != PackageManager.PERMISSION_GRANTED) {
//                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
//            }
//        } else {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
//                != PackageManager.PERMISSION_GRANTED) {
//                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
//            }
//        }
//
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
//            != PackageManager.PERMISSION_GRANTED) {
//            permissionsNeeded.add(Manifest.permission.SEND_SMS)
//        }
//
//        if (permissionsNeeded.isNotEmpty()) {
//            ActivityCompat.requestPermissions(
//                this,
//                permissionsNeeded.toTypedArray(),
//                SMS_MMS_PERMISSION_REQUEST_CODE
//            )
//        } else {
//            sendImageToNumber("6194966341") // Replace with recipient number
//        }
//    }

    private fun getLatestImageUri(): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = it.getLong(idColumn)
                return ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
            }
        }
        return null
    }

    private fun sendImageToNumber(phoneNumber: String) {
        val imageUri = getLatestImageUri() ?: run {
            Toast.makeText(this, "No images found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // For Android 5.0+
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                this.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }

            // Create temp file copy (some carriers require this)
            val tempFile = createTempImageCopy(imageUri) ?: run {
                Toast.makeText(this, "Failed to prepare image", Toast.LENGTH_SHORT).show()
                return
            }

            val sendableUri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                tempFile
            )

            // Grant permission to SMS app
            grantUriPermission(
                "com.android.mms",  // Default MMS package
                sendableUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            // Send MMS
            smsManager.sendMultimediaMessage(
                this,
                sendableUri,
                "image/jpeg",
                null,
                null
            )

            Toast.makeText(this, "MMS sending initiated", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send MMS: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("MMS", "Error sending MMS", e)
        }
    }

    private fun createTempImageCopy(uri: Uri): File? {
        return try {
            val tempFile = File.createTempFile(
                "mms_${System.currentTimeMillis()}",
                ".jpg",
                externalCacheDir
            )

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            tempFile
        } catch (e: Exception) {
            Log.e("MMS", "Failed to create temp file", e)
            null
        }
    }


//    private fun checkCameraPermissions(): Boolean {
//        val permissions = arrayOf(
//            Manifest.permission.CAMERA,
//            Manifest.permission.RECORD_AUDIO,
////            Manifest.permission.WRITE_EXTERNAL_STORAGE
//        )
//
//        if (permissions.all {
//                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
//            }) {
//            Log.d("Teta", "I AM TRUE")
//            return true
//        }
//        Log.d("Teta", "I AM FALSE")
//
//        ActivityCompat.requestPermissions(this, permissions, REQUEST_CAMERA_CODE_PERMISSIONS)
//        return false
//    }
//private fun checkCameraPermissions(): Boolean {
//    val permissions = arrayOf(
//        Manifest.permission.CAMERA,
//        Manifest.permission.RECORD_AUDIO
//    )
//
//    return permissions.all {
//        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
//    }
//}
//
//
////    private fun bindCameraUseCases() {
////        if (!hasPermissions()) {
////            requestPermissions()
////            return
////        }
////        val cameraProvider = cameraProvider ?: return
////
////        // Unbind previous use cases
////        cameraProvider.unbindAll()
////
////        // Preview
////        val preview = Preview.Builder()
////            .build()
////            .also {
////                it.setSurfaceProvider(previewView.surfaceProvider)
////            }
////
////        // Video capture
////        val recorder = Recorder.Builder()
////            .setQualitySelector(QualitySelector.from(Quality.HD))
////            .build()
////        videoCapture = VideoCapture.withOutput(recorder)
////
////        // Select back camera
////
////        try {
////            cameraProvider.bindToLifecycle(
////                this,
////                cameraSelector,
////                preview,
////                videoCapture
////            )
////        } catch (e: Exception) {
////            e.printStackTrace()
////        }
////    }
//private fun bindCameraUseCases() {
//    val cameraProvider = cameraProvider ?: return
//    previewView = findViewById<androidx.camera.view.PreviewView>(R.id.CAMERAAHHHH)
//
//    try {
//        cameraProvider.unbindAll()
//
//        // 1. First bind the camera to get CameraInfo
//        val camera = cameraProvider.bindToLifecycle(
//            this,
//            cameraSelector,
//            Preview.Builder().build().also {
//                it.setSurfaceProvider(previewView.surfaceProvider)
//            }
//        )
//
//        // 2. Now get the CameraInfo from the bound camera
//        val cameraInfo = camera.cameraInfo
//
//        // 3. Get supported qualities for this camera
//        val qualities = QualitySelector.getSupportedQualities(cameraInfo)
//
//        // 4. Unbind to reconfigure with video
//        cameraProvider.unbindAll()
//
//        // 5. Create quality selector with fallback
//        val quality = when {
//            qualities.contains(Quality.HD) -> Quality.HD
//            qualities.contains(Quality.SD) -> Quality.SD
//            else -> qualities.firstOrNull() ?: Quality.LOWEST
//        }
//
//        // 6. Build the recorder with selected quality
//        val recorder = Recorder.Builder()
//            .setQualitySelector(QualitySelector.from(quality))
//            .build()
//
//        videoCapture = VideoCapture.withOutput(recorder)
//
//        // 7. Final bind with all use cases
//        cameraProvider.bindToLifecycle(
//            this,
//            cameraSelector,
//            Preview.Builder().build().also {
//                it.setSurfaceProvider(previewView.surfaceProvider)
//            },
//            videoCapture
//        )
//
//    } catch (e: Exception) {
//        Log.e(TAG, "Use case binding failed", e)
//        Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_LONG).show()
//    }
//}
////
//lateinit var outputFile: File
//    private fun startRecording() {
//    val videoCapture = this.videoCapture ?: return
//
//    // Create a file in public Movies directory
//    outputFile = File(
//        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
//        "VID_${System.currentTimeMillis()}.mp4"
//    )
//
//    val outputOptions = FileOutputOptions.Builder(outputFile).build()
//
//        recording = videoCapture.output
//            .prepareRecording(this, outputOptions)
//            .apply {
//                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
//                    PackageManager.PERMISSION_GRANTED) {
//                    withAudioEnabled()
//                }
//            }
//            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
//                when(recordEvent) {
//                    is VideoRecordEvent.Start -> {
//                        isRecording = true
////                        recordButton.text = "Stop Recording"
//                    }
//                    is VideoRecordEvent.Finalize -> {
//                        if (!recordEvent.hasError()) {
//                            Toast.makeText(
//                                this,
//                                "Video saved: ${outputFile.path}",
//                                Toast.LENGTH_SHORT
//                            ).show()
//                        } else {
//                            outputFile.delete()
//                            Toast.makeText(
//                                this,
//                                "Error: ${recordEvent.error}",
//                                Toast.LENGTH_SHORT
//                            ).show()
//                        }
//                        isRecording = false
////                        recordButton.text = "Start Recording"
//                    }
//                }
//            }
//    }
////
//private fun stopRecording() {
//    recording?.stop()
//    recording = null
//
//    // Optional: Add a delay to ensure file is fully written
//    Handler(Looper.getMainLooper()).postDelayed({
//        scanFileToGallery(outputFile) // You'll need to track videoFile
//    }, 1000)
//}
//
//    private fun scanFileToGallery(file: File) {
//        MediaScannerConnection.scanFile(
//            this,
//            arrayOf(file.absolutePath),
//            arrayOf("video/mp4"),
//            null
//        )
//
//        // Alternative method for older APIs
//        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
//            data = Uri.fromFile(file)
//        }
//        sendBroadcast(intent)
//    }
////    companion object {
////        private const val REQUEST_CAMERA_CODE_PERMISSIONS = 10
////    }
////




}












