package com.example.weather

import android.app.Activity
import android.os.Bundle
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
import android.widget.ImageView
import com.bumptech.glide.Glide

import com.squareup.picasso.Picasso


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
class MainActivity : AppCompatActivity() {
    private lateinit var textViewWeather: TextView
    private val editTextLocation: EditText? = null
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
            //Varible for the inputted location
            val region: String = findViewById<TextView>(R.id.cityNameInput).text.toString().trim()
            if (!region.isEmpty()){
                fetchWeather(region)
            }


        }



        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
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
