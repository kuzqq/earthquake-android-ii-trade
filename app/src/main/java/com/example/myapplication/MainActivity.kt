package com.example.myapplication
import android.util.Log

import java.util.Locale
import android.annotation.SuppressLint
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.osmdroid.views.overlay.Marker
import java.util.concurrent.TimeUnit
import com.fasterxml.jackson.databind.DeserializationFeature
import android.widget.EditText
import com.github.mikephil.charting.charts.CandleStickChart
import com.github.mikephil.charting.data.CandleData
import com.github.mikephil.charting.data.CandleEntry
import com.github.mikephil.charting.data.CandleDataSet
import android.graphics.Color
import android.graphics.Paint
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.annotation.JsonProperty
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import org.joda.time.format.DateTimeFormat
import kotlinx.coroutines.delay
import android.widget.TextView
import android.content.Intent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class MainActivity : AppCompatActivity() {
    private lateinit var priceChangeTextView: TextView
    private lateinit var map: MapView
    private lateinit var companyAdapter: CompanyAdapter // <-- Declare companyAdapter here
    private var selectedDateTime: String? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ctx = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        setContentView(R.layout.activity_main)


        val buttonSecond: Button = findViewById(R.id.button_second)
        buttonSecond.setOnClickListener {
            val intent = Intent(this, SecondActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
        val recyclerView: RecyclerView = findViewById(R.id.companies_recycler_view)
        companyAdapter = CompanyAdapter() // <-- Initialize companyAdapter here
        recyclerView.layoutManager = LinearLayoutManager(this)
        companyAdapter = CompanyAdapter(listOf()) // <-- Pass an empty list or some initial data
        recyclerView.adapter = companyAdapter

        priceChangeTextView = findViewById(R.id.price_change_text_view)

        // Инициализация карты
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        // Центр карты
        val mapController = map.controller
        mapController.setZoom(5.0)
        val startPoint = GeoPoint(48.8583, 2.2944) // Париж
        mapController.setCenter(startPoint)

        // Показать мое местоположение
        val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        myLocationOverlay.enableMyLocation()
        map.overlays.add(myLocationOverlay)

        // Инициализация Spinner
        val spinner: Spinner = findViewById(R.id.spinner)
        // Создаем адаптер ArrayAdapter с массивом строк, полученных из ресурсов строк.
        // Макет android.R.layout.simple_spinner_item - это стандартный макет, предоставляемый Android,
        // который вы можете использовать с ArrayAdapter.
        val adapter: ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(this,
            R.array.period_array, android.R.layout.simple_spinner_item)
        // Задайте макет для отображения списка выбора в выпадающем списке
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // Примените адаптер к Spinner
        spinner.adapter = adapter

        val button: Button = findViewById(R.id.button)
        button.setOnClickListener { v: View ->
            val period = spinner.selectedItem.toString()
            loadEarthquakeData(period, 0.0) // Здесь вы устанавливаете магнитуду на 0, вы можете выбрать другое значение

            val magnitudeSpinner: Spinner = findViewById(R.id.magnitude_spinner)
            val magnitudeAdapter: ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(this, R.array.magnitude_array, android.R.layout.simple_spinner_item)
            magnitudeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            magnitudeSpinner.adapter = magnitudeAdapter
            button.setOnClickListener { v: View ->
                val period = spinner.selectedItem.toString()
                val magnitude = magnitudeSpinner.selectedItem.toString().toDouble()
                // вызовите функцию API с выбранным периодом и магнитудой
                loadEarthquakeData(period, magnitude)
            }

        }
        val stockEditText: EditText = findViewById(R.id.stockEditText)
        stockEditText.setText("BLK") // устанавливаем значение по умолчанию

        val loadStockButton: Button = findViewById(R.id.loadStockButton)
        loadStockButton.setOnClickListener { v: View ->
            val stockSymbol = stockEditText.text.toString()
            loadStockData(stockSymbol)
        }
    }

    private fun loadEarthquakeData(period: String, minMagnitude: Double) {
        val end = Calendar.getInstance().time
        val start = Calendar.getInstance().apply {
            // Конвертируем выбранный период в число и вычитаем это количество дней
            add(Calendar.DAY_OF_MONTH, -period.toInt())
        }.time

        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val url = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&starttime=${format.format(start)}&endtime=${format.format(end)}&minmagnitude=$minMagnitude"

        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            // Проверка успешности ответа
            if (response.isSuccessful) {
                val data = response.body?.string()
                println("Response data: $data")  // Вывод данных в консоль

                if (data != null) {
                    val objectMapper = jacksonObjectMapper()
                    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    val earthquakes: EarthquakeResponse = objectMapper.readValue(data)

                    // Обновление карты должно происходить в основном потоке
                    runOnUiThread {
                        map.overlays.clear()

                        for (earthquake in earthquakes.features) {
                            val timestamp = earthquake.properties.time
                            val date = Date(timestamp)
                            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date)

                            val marker = Marker(map)
                            val latitude = earthquake.geometry.coordinates[1]
                            val longitude = earthquake.geometry.coordinates[0]

                            if (latitude in -85.05112877980658..85.05112877980658 && longitude in -180.0..180.0) {
                                marker.position = GeoPoint(latitude, longitude)
                                marker.title = "${earthquake.properties.mag} - ${earthquake.properties.place} - Time: $formattedDate"
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                marker.setOnMarkerClickListener { marker, mapView ->
                                    // Записываем дату и время выбранного маркера
                                    selectedDateTime = marker.title.split(" - Time: ")[1]

                                    // Показываем информационное окно маркера
                                    marker.showInfoWindow()

                                    // Выводим координаты маркера в консоль
                                    println("Маркер нажат: Широта = ${marker.position.latitude}, Долгота = ${marker.position.longitude}")

                                    // Формируем URL с параметрами
                                    val latitude = marker.position.latitude
                                    val longitude = marker.position.longitude
                                    val maxDistance = 1500
                                    val url = "http://10.0.2.2:5000/get_companies?latitude=$latitude&longitude=$longitude&max_distance=$maxDistance"

                                    // Создаем клиент и запрос
                                    val client = OkHttpClient.Builder()
                                        .readTimeout(60, TimeUnit.SECONDS)
                                        .connectTimeout(60, TimeUnit.SECONDS)
                                        .build()
                                    val request = Request.Builder().url(url).build()

                                    // Запускаем сопрограмму для выполнения запроса
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            val response = client.newCall(request).execute()

                                            // Проверяем успешность ответа
                                            if (response.isSuccessful) {
                                                val data = response.body?.string()

                                                // Парсим JSON ответ
                                                val objectMapper = jacksonObjectMapper()
                                                val companies: List<Map<String, String>> = objectMapper.readValue(data.orEmpty())

                                                // Обновляем UI в главном потоке
                                                runOnUiThread {
                                                    companyAdapter.updateData(companies)
                                                    // Здесь можно отобразить данные на экране, используя RecyclerView или иным способом
                                                    // Например:
                                                    // myRecyclerViewAdapter.updateData(companies)
                                                }
                                            } else {
                                                println("Response failed: ${response.message}")
                                            }
                                        } catch (e: Exception) {
                                            println("Ошибка при выполнении сетевого запроса: ${e.message}")
                                        }
                                    }

                                    true
                                }

                                map.overlays.add(marker)
                            }
                        }

                        map.invalidate()

                    }
                } else {
                    println("Response body is null")
                }
            } else {
                println("Response failed: ${response.message}")
            }
        }
    }




    fun loadStockData(stockSymbol: String) {
        CoroutineScope(Dispatchers.IO).launch {
            // Замените эту строку на URL вашего сервера
            priceChangeTextView.text = ""
            val url = "http://92.118.149.17:5000/stock/$stockSymbol?period=30d"

            val client = OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val data = response.body?.string()

                if (data != null) {
                    val objectMapper = ObjectMapper()
                    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

                    // Десериализация данных в объект HistoricalData
                    val historicalData: HistoricalData = objectMapper.readValue(data)

                    // Теперь вы можете обратиться к полям historicalData, например:
                    println("Historical data: ${historicalData.Close}")

                    val entries = ArrayList<CandleEntry>()
                    val xAxisLabels = ArrayList<String>() // Список дат для шкалы XAxis

                    for ((index, closeEntry) in historicalData.Close.entries.toList().withIndex()) {
                        val date = closeEntry.key
                        val close = closeEntry.value
                        val open = historicalData.Open[date]
                        val high = historicalData.High[date]
                        val low = historicalData.Low[date]

                        if (open != null && high != null && low != null) {
                            entries.add(CandleEntry(index.toFloat(), high.toFloat(), low.toFloat(), open.toFloat(), close.toFloat()))
                            xAxisLabels.add(date) // Добавить дату в список для шкалы XAxis
                        }
                    }

                    runOnUiThread {
                        val dataSet = CandleDataSet(entries, "Stock Price")

                        dataSet.setDecreasingColor(Color.RED)
                        dataSet.setIncreasingColor(Color.GREEN)
                        dataSet.setDecreasingPaintStyle(Paint.Style.FILL)
                        dataSet.setIncreasingPaintStyle(Paint.Style.STROKE)

                        val chart = findViewById<CandleStickChart>(R.id.chart)
                        chart.data = CandleData(dataSet)

                        // Настройка шкалы XAxis с использованием IndexAxisValueFormatter и списка дат
                        val xAxis = chart.xAxis
                        xAxis.valueFormatter = IndexAxisValueFormatter(xAxisLabels)
                        xAxis.position = XAxis.XAxisPosition.BOTTOM
                        xAxis.setDrawGridLines(false)
                        xAxis.granularity = 20f
                        xAxis.setAvoidFirstLastClipping(true)
                        xAxis.labelCount = xAxisLabels.size

                        // Искать ближайшую дату к заданной
                        val fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ssZ")
                        val targetDateTime = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").parseDateTime(selectedDateTime ?: "2023-05-15 14:56:45")
                        var closestDateIndex = -1
                        var smallestDifference = Long.MAX_VALUE

                        for ((index, dateTime) in xAxisLabels.withIndex()) {
                            val instant = fmt.parseDateTime(dateTime)
                            val difference = kotlin.math.abs(targetDateTime.millis - instant.millis)
                            if (difference < smallestDifference) {
                                smallestDifference = difference
                                closestDateIndex = index
                            }
                        }

                        if (closestDateIndex != -1) {
                            chart.highlightValue(closestDateIndex.toFloat(), 0, false)
                            val highlightedCandle = dataSet.getEntryForIndex(closestDateIndex)
                            val highlightedCandleAveragePrice = (highlightedCandle.open + highlightedCandle.close) / 2
                            Log.d("HighlightedCandle", "Open: ${highlightedCandle.open}, High: ${highlightedCandle.high}, Low: ${highlightedCandle.low}, Close: ${highlightedCandle.close}, Average: $highlightedCandleAveragePrice")

                            val additionalIndices = listOf(3, 6, 10, 20)
                            for (additionalIndex in additionalIndices) {
                                val nextIndex = closestDateIndex + additionalIndex
                                if (nextIndex < dataSet.entryCount) {
                                    val nextCandle = dataSet.getEntryForIndex(nextIndex)
                                    val nextCandleAveragePrice = (nextCandle.open + nextCandle.close) / 2
                                    val priceChangePercentage = ((nextCandle.close - highlightedCandle.close) / highlightedCandle.close) * 100

                                    runOnUiThread {
                                        priceChangeTextView.append("\nPrice Change ($additionalIndex candles): ${"%.2f".format(priceChangePercentage)}%")
                                    }


                                    Log.d("NextCandle-$additionalIndex", "Open: ${nextCandle.open}, High: ${nextCandle.high}, Low: ${nextCandle.low}, Close: ${nextCandle.close}, Average: $nextCandleAveragePrice, Price Change: $priceChangePercentage%")
                                } else {
                                    Log.d("NextCandle-$additionalIndex", "Нет данных через $additionalIndex столбцов справа")
                                }
                            }
                        }





                        chart.invalidate()
                    }
                }
            }
        }
    }







    // Это предположительный класс, опирающийся на предположение о том, как выглядят ваши данные


    data class HistoricalData(
        @JsonProperty("Close")
        val Close: Map<String, Double>,
        @JsonProperty("Dividends")
        val Dividends: Map<String, Double>,
        @JsonProperty("High")
        val High: Map<String, Double>,
        @JsonProperty("Low")
        val Low: Map<String, Double>,
        @JsonProperty("Open")
        val Open: Map<String, Double>,
        @JsonProperty("Stock Splits")
        val StockSplits: Map<String, Double>,
        @JsonProperty("Volume")
        val Volume: Map<String, Double>
    ) {
        constructor() : this(mapOf(), mapOf(), mapOf(), mapOf(), mapOf(), mapOf(), mapOf())
    }





    public override fun onResume() {
        super.onResume()
        map.onResume()
    }

    public override fun onPause() {
        super.onPause()
        map.onPause()
    }
    data class EarthquakeResponse(
        val features: List<EarthquakeFeature>
    )

    data class EarthquakeFeature(
        val properties: EarthquakeProperties,
        val geometry: EarthquakeGeometry
    )

    data class EarthquakeProperties(
        val mag: Double,
        val place: String?,
        val time: Long
    )

    data class EarthquakeGeometry(
        val coordinates: List<Double>
    )


}
