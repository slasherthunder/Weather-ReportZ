package com.example.weather

//Image stuff
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import java.util.concurrent.Executors
import android.media.MediaScannerConnection

import androidx.core.net.toUri
import android.telephony.SmsManager
import android.graphics.drawable.BitmapDrawable

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.Locale
import kotlin.text.get

class MainActivity : AppCompatActivity() {
    private val SMS_PERMISSION_REQUEST_CODE = 101

    private lateinit var textViewWeather: TextView
    private val editTextLocation: EditText? = null
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var imageCapture: ImageCapture
    private lateinit var apiService: WeatherApiService

    private val APIKEY = "bd19138f2a75421f88535220251705"
    private var isSettingsVisible = false
    private var isCelsius = false // Default to Fahrenheit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

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

        //MONDAY BUTTON SEND MESSAGE CONFIGURATION
        var mondayButton = findViewById<ImageButton>(R.id.mondayButton)
        mondayButton.setOnClickListener {
            seeIfSMSHasPermission()
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
//            //Varible for the inputted location
            val region: String = findViewById<TextView>(R.id.cityNameInput).text.toString().trim()
            if (!region.isEmpty()){
                fetchWeather(region)
            }
        }

        val captureButton = findViewById<ImageButton>(R.id.thursdayButton)
        val captureFrontButton = findViewById<ImageButton>(R.id.wednesdayButton)

        captureButton.setOnClickListener {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            if (hasPermissions()) {
//                cameraProviderFuture.get()?.unbindAll()
//                startCamera()
                takePhoto()
            } else {
                requestPermissions()
            }
        }

        captureFrontButton.setOnClickListener {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            if (hasPermissions()) {
//                cameraProviderFuture.get()?.unbindAll()
//                startCamera()
                takePhoto()
            } else {
                requestPermissions()
            }
        }
        // Request permissions
        if ( hasPermissions()) {
            startCamera()
        } else {
            requestPermissions()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
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

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_CODE_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (hasPermissions()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }
        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendSms()
            } else {
                Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCamera() {
        val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.camera_preview)
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // ImageCapture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // Select back camera

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // 1. Create content values for MediaStore
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,                      // ContentResolver
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,  // Save collection URI
            contentValues                         // ContentValues
        ).build()

        // 2. Get the content resolver and insert the entry
        val contentResolver = applicationContext.contentResolver
        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: run {
            Toast.makeText(this, "Failed to create file", Toast.LENGTH_SHORT).show()
            return
        }

        // 3. Take the picture and save to MediaStore
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@MainActivity, "Photo saved to gallery", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    private fun notifyGallery(file: File) {
        MediaScannerConnection.scanFile(
            this,
            arrayOf(file.absolutePath),
            arrayOf("image/jpeg")
        ) { _, uri ->
            Log.d(TAG, "Image scanned with URI: $uri")
        }
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
    }

    fun fetchWeather(place: String){
        val call  = apiService.getCurrentWeather(APIKEY, place)
        call.enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                if (response.isSuccessful) {
                    val weatherData = response.body()
                    // Access your data here
                    val name = weatherData?.location?.name
                    val tempature = weatherData?.current?.temp_f
                    val mph = weatherData?.current?.wind_mph
                    val humidityValue = weatherData?.current?.humidity
                    val description = weatherData?.current?.condition?.text
                    val pic = weatherData?.current?.condition?.icon

                    Log.d("FindMe", tempature.toString())
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
                    // Update your UI with the data
                } else {
                    // Handle error
                    Log.e("API", "Error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                // Handle failure
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
            val imageUri = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a3/June_odd-eyed-cat_cropped.jpg/640px-June_odd-eyed-cat_cropped.jpg".toUri()
            sendMmsWithImage("6194966341", "Whats Good", imageUri)
        }
    }

    private fun sendSms() {
        try {
            val phoneNumber = "6194966341" // Replace with recipient number
            val message = "i LOVEVEEEEEEEE children"


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

    private fun sendMmsWithImage(phoneNumber: String, message: String, imageUri: Uri) {
        try {
            sendSms()
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }

            // For newer Android versions

            smsManager.sendMultimediaMessage(
                this,
                imageUri,
                "image/*",
                null,
                null
            )

//            else {
//                // For older versions (may not work on all devices)
//                val parts = ArrayList<Uri>().apply { add(imageUri) }
//                smsManager.sendMultimediaMessage(
//                    this,
//                    phoneNumber,
//                    null,
//                    parts,
//                    message,
//                    null
//                )
//            }
            Toast.makeText(this, "MMS with image sent", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "MMS failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }





}












