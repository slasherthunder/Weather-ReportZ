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
import androidx.appcompat.app.AlertDialog
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.Locale
import kotlin.text.get

class MainActivity : AppCompatActivity() {
    private val SMS_PERMISSION_REQUEST_CODE = 101
    private val CALL_PERMISSION_REQUEST_CODE = 102

    private lateinit var textViewWeather: TextView
    private val editTextLocation: EditText? = null
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var imageCapture: ImageCapture
    private lateinit var apiService: WeatherApiService

    private val APIKEY = "bd19138f2a75421f88535220251705"
    private var isSettingsVisible = false
    private var isCelsius = false // Default to Fahrenheit
    // Add this new function to your MainActivity class:

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

        // WEDNESDAY BUTTON - FRONT CAMERA picture
        val wednesdayButton = findViewById<ImageButton>(R.id.wednesdayButton)
        wednesdayButton.setOnClickListener {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            if (hasPermissions()) {
                takePhoto()
            } else {
                requestPermissions()
            }
        }

        // THURSDAY BUTTON - BACK CAMERA picture
        val thursdayButton = findViewById<ImageButton>(R.id.thursdayButton)
        thursdayButton.setOnClickListener {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            if (hasPermissions()) {
                takePhoto()
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

        // Request permissions
        if (hasPermissions()) {
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
    private fun showUserGuide() {
        val guideMessage = """
        Monday sends a "I am not okay" text to the authorities.
        Tuesday starts recording a video from the back camera, clicking on it again will stop the video.
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

    /* ========== SMS/MMS FUNCTIONALITY ========== */
    private fun seeIfSMSHasPermission() {
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
            val imageUri = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a3/June_odd-eyed-cat_cropped.jpg/640px-June_odd-eyed-cat_cropped.jpg".toUri()
            sendMmsWithImage("6194966341", "Whats Good", imageUri)
        }
    }

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
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

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

    /* ========== WEATHER FUNCTIONALITY ========== */
    private fun updateTemperatureDisplay(tempF: Float) {
        val tempText = findViewById<TextView>(R.id.temperatureText)
        if (isCelsius) {
            val tempC = (tempF - 32) * 5 / 9
            tempText.text = "%.1f°C".format(tempC)
        } else {
            tempText.text = "%.1f°F".format(tempF)
        }
    }

    fun fetchWeather(place: String) {
        val call = apiService.getCurrentWeather(APIKEY, place)
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

    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}