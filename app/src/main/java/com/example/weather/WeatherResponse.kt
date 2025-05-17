package com.example.weather

class WeatherResponse {
    var location: Location? = null
    var current: Current? = null

    inner class Location {
        var name: String? = null
        var region: String? = null
        var country: String? = null
    }

    inner class Current {
        var temp_c: Double = 0.0
        var temp_f: Double = 0.0

        var condition: Condition? = null

        inner class Condition {
            var text: String? = null
            var icon: String? = null
        }
    }
}

