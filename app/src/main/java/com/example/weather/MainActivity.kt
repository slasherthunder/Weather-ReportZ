package com.example.weather


//Image stuff
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import java.util.concurrent.Executors
import android.media.MediaScannerConnection


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

import com.squareup.picasso.Picasso


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
    private lateinit var textViewWeather: TextView
    private val editTextLocation: EditText? = null
    private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var imageCapture: ImageCapture
    private lateinit var apiService: WeatherApiService

    private val APIKEY = "bd19138f2a75421f88535220251705"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        //Storm text
        val test = findViewById<TextView>(R.id.descriptionText)

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.weatherapi.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(WeatherApiService::class.java)

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
                    textTest.text = tempature.toString() + "°F"
                    val cityTest = findViewById<TextView>(R.id.cityNameText)
                    cityTest.text = name.toString()
                    val windSpeed = findViewById<TextView>(R.id.windText)
                    windSpeed.text = mph.toString() + " mph"
                    val humidityLabel = findViewById<TextView>(R.id.humidityText)
                    humidityLabel.text = humidityValue.toString() + "%"
                    val descriptionLabel = findViewById<TextView>(R.id.descriptionText)
                    val weatherPic = findViewById<ImageView>(R.id.weatherIcon)
                    descriptionLabel.text = description.toString()
//                    Glide.with()
//                        .load(weatherPic.toString()) // URL of the image
//                        .into(weatherPic)
                    Log.d("MyTag", "https:" + pic.toString())

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

    }













