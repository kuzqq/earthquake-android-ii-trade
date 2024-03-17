package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.ToggleButton
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myapplication.R
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.ScatterData
import com.github.mikephil.charting.data.ScatterDataSet
import com.github.mikephil.charting.utils.EntryXComparator
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.Instant.*
import java.time.ZoneOffset
import java.util.Collections

class SecondActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var spinnerTimePeriod: Spinner
    private lateinit var spinnerCoordinates: Spinner
    private lateinit var spinnerRadius: Spinner
    private lateinit var spinnerMagnitude: Spinner
    private lateinit var buttonMain: Button
    private lateinit var buttonLoadData: Button
    private lateinit var toggleNotifications: ToggleButton
    private lateinit var scatterChart: ScatterChart
    private var scatterDataSet = ArrayList<Entry>()
    private lateinit var tvEarthquakeInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        // Initialization
        spinnerTimePeriod = findViewById(R.id.spinner_time_period)
        spinnerCoordinates = findViewById(R.id.spinner_coordinates)
        spinnerRadius = findViewById(R.id.spinner_radius)
        spinnerMagnitude = findViewById(R.id.spinner_magnitude2)
        buttonMain = findViewById(R.id.button_main)
        buttonLoadData = findViewById(R.id.button_load_data)
        toggleNotifications = findViewById(R.id.toggle_notifications)
        scatterChart = findViewById(R.id.scatter_chart)
        scatterChart.axisLeft.setAxisMaximum(10f)
        scatterChart.axisRight.setAxisMaximum(10f)
        tvEarthquakeInfo = findViewById(R.id.tv_earthquake_info)
        LocalBroadcastManager.getInstance(this).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val earthquakeInfo = intent.getStringExtra("earthquake_info")
                tvEarthquakeInfo.text = earthquakeInfo

            }
        }, IntentFilter("NewEarthquake"))

        // Button to go back to MainActivity
        buttonMain.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        toggleNotifications.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE).edit()
            if (isChecked) {
                // Save notification settings
                val minMagnitude = spinnerMagnitude.selectedItem.toString().toFloat()
                val coordinates = spinnerCoordinates.selectedItem.toString().split(",")
                val latitude = coordinates[0].trim().toFloat()
                val longitude = coordinates[1].trim().toFloat()
                val radius = spinnerRadius.selectedItem.toString().toFloat()
                prefs.putFloat("minMagnitude", minMagnitude)
                prefs.putFloat("latitude", latitude)
                prefs.putFloat("longitude", longitude)
                prefs.putFloat("radius", radius)
                prefs.apply()

                // Start service
                val intent = Intent(this, EarthquakeService::class.java)
                startService(intent)
            } else {
                // Stop service
                val intent = Intent(this, EarthquakeService::class.java)
                stopService(intent)
            }
        }

        buttonLoadData.setOnClickListener {
            println("Button clicked!")

            scatterDataSet.clear()  // Clearing the data set before loading new data

            val timePeriod = spinnerTimePeriod.selectedItem.toString().toLong() * 24 * 60 * 60
            val coordinates = spinnerCoordinates.selectedItem.toString().split(",")
            val latitude = coordinates[0]
            val longitude = coordinates[1]
            val radius = spinnerRadius.selectedItem.toString()

            val currentTimestamp = now()
            val pastTimestamp = currentTimestamp.minusSeconds(timePeriod)

            val requestUrl = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson" +
                    "&starttime=${pastTimestamp}&endtime=${currentTimestamp}" +
                    "&latitude=$latitude&longitude=$longitude&maxradiuskm=$radius"

            println("Request URL: $requestUrl")

            val request = Request.Builder()
                .url(requestUrl)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseData = response.body?.string()
                    println(responseData)
                    val jsonResponse = JSONObject(responseData)
                    val features = jsonResponse.getJSONArray("features")
                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)
                        val properties = feature.getJSONObject("properties")
                        val magnitude = properties.getDouble("mag")
                        val time = properties.getLong("time")
                        val place = properties.getString("place")
                        val earthquakeInfo = "Magnitude: $magnitude, Place: $place, Time: ${
                            ofEpochMilli(time).atZone(
                                ZoneOffset.UTC
                            )
                        }\n"
                        scatterDataSet.add(Entry(time.toFloat(), magnitude.toFloat()))
                        runOnUiThread {
                            tvEarthquakeInfo.append(earthquakeInfo)
                        }
                    }

                    // Sort entries by x-value
                    Collections.sort(scatterDataSet, EntryXComparator())

                    runOnUiThread {
                        val scatterData = ScatterData(ScatterDataSet(scatterDataSet, "Earthquakes"))
                        scatterChart.data = scatterData
                        scatterChart.invalidate() // refresh
                    }
                }
            })
        }
    }
}