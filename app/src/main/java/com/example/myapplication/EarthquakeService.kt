package com.example.myapplication

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class EarthquakeService : Service() {
    private val client = OkHttpClient()
    private val knownEarthquakes = mutableListOf<String>()
    private var isFirstRequest = true

    private val handler = Handler(Looper.getMainLooper())

    private val updateEarthquakesRunnable: Runnable = object : Runnable {
        override fun run() {
            checkForEarthquakes()
            handler.postDelayed(this, 15000) // run every 15 seconds
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread(updateEarthquakesRunnable).start()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateEarthquakesRunnable) // stop when the service is destroyed
    }

    private fun checkForEarthquakes() {
        println("Updating earthquakes data.")
        Log.d("EarthquakeService", "Service started")
        print("Service started первый круг $isFirstRequest")

        // Read settings from shared preferences
        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val minMagnitude = prefs.getFloat("minMagnitude", 0f)
        val latitude = prefs.getFloat("latitude", 0f)
        val longitude = prefs.getFloat("longitude", 0f)
        val radius = prefs.getFloat("radius", 0f)

        Log.d(
            "EarthquakeService",
            "Settings: minMagnitude=$minMagnitude, latitude=$latitude, longitude=$longitude, radius=$radius"
        )
        print("Settings: minMagnitude=$minMagnitude, latitude=$latitude, longitude=$longitude, radius=$radius")

        // Query API and check for earthquakes
        val requestUrl = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson" +
                "&minmagnitude=$minMagnitude" +
                "&latitude=$latitude&longitude=$longitude&maxradiuskm=$radius"

        Log.d("EarthquakeService", "Request URL: $requestUrl")
        print("Request URL: $requestUrl")

        val request = Request.Builder()
            .url(requestUrl)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body
                if (responseBody != null) {
                    try {
                        val responseData = responseBody.string()
                        val jsonResponse = JSONObject(responseData)
                        val features = jsonResponse.getJSONArray("features")
                        if (features.length() > 0) {
                            for (i in 0 until features.length()) {
                                val feature = features.getJSONObject(i)
                                val id = feature.getString("id") // get the id of the earthquake
                                if (id !in knownEarthquakes) {
                                    // This is a new earthquake, add to knownEarthquakes
                                    knownEarthquakes.add(id)
                                    if (!isFirstRequest) {  // Only create notification if not the first request
                                        Log.d("EarthquakeService", "New earthquake detected!")
                                        println("New earthquake detected: $feature")
                                        val intent = Intent("NewEarthquake")
                                        intent.putExtra("earthquake_info", feature.toString())
                                        LocalBroadcastManager.getInstance(this@EarthquakeService)
                                            .sendBroadcast(intent)
                                        // TODO: Add code here to create and show notification
                                    }
                                }
                            }
                        }
                        isFirstRequest = false  // Set the flag to false after the first request
                    } catch (exception: IOException) {
                        exception.printStackTrace()
                    } finally {
                        // If an error occurred during string() operation, then close the response body
                        responseBody.close()
                    }
                }
            }
        })
    }
}