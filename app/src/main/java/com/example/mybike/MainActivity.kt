package com.example.mybike

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.mybike.ui.theme.MybikeTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch
import com.google.gson.Gson
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.expressions.Expression
import android.graphics.Color
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import kotlin.jvm.java

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize MapLibre
        MapLibre.getInstance(this)
        
        enableEdgeToEdge()
        setContent {
            MybikeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MapScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }

        // 1. 初始化 Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl("https://tcgbusfs.blob.core.windows.net/dotapp/youbike/")
            .addConverterFactory(GsonConverterFactory.create()) // 這裡就幫你把 JSON 解析好了！
            .build()

        val apiService = retrofit.create(YouBikeApiService::class.java)

        // 2. 在 LifecycleScope 或 ViewModel 中呼叫
        lifecycleScope.launch {
            try {
                val productList = apiService.getAllStations()
                // 這裡的 productList 已經是解析好的 List<Product> 物件了
                Log.d("data",productList[0].id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

data class YouBikeStation(
    @SerializedName("sno")
    val id: String,                 // 站點編號

    @SerializedName("sna")
    val stationName: String,        // 站點名稱 (中)

    @SerializedName("sarea")
    val district: String,           // 行政區 (中)

    @SerializedName("mday")
    val lastUpdate: String,         // 資料更新時間

    @SerializedName("ar")
    val address: String,            // 地址 (中)

    @SerializedName("snaen")
    val stationNameEn: String,      // 站點名稱 (英)

    @SerializedName("aren")
    val addressEn: String,          // 地址 (英)

    @SerializedName("Quantity")
    val totalBikes: Int,            // 總車位數

    @SerializedName("available_rent_bikes")
    val availableBikes: Int,        // 可借車輛數

    @SerializedName("available_return_bikes")
    val availableReturns: Int,      // 可還車位數

    @SerializedName("latitude")
    val latitude: Double,                // 緯度

    @SerializedName("longitude")
    val longitude: Double,                // 經度

    val act: String                 // 全站禁用狀態 (0:禁用, 1:啟用)
)

// 整體的 GeoJSON 容器
data class GeoJsonWrapper(
    val type: String = "FeatureCollection",
    val features: List<GeoJsonFeature>
)
// 單一地理要素
data class GeoJsonFeature(
    val type: String = "Feature",
    val geometry: Geometry,
    val properties: YouBikeStation // 直接複用你之前的 YouBikeStation 模型
)
// 幾何資訊
data class Geometry(
    val type: String = "Point",
    val coordinates: List<Double> // [經度, 緯度]
)

fun convertToGeoJson(stations: List<YouBikeStation>): GeoJsonWrapper {
    val features = stations.map { station ->
        GeoJsonFeature(
            geometry = Geometry(
                coordinates = listOf(station.longitude, station.latitude) // 注意：GeoJSON 規定先經後緯
            ),
            properties = station
        )
    }
    return GeoJsonWrapper(features = features)
}

interface YouBikeApiService {
    @GET("v2/youbike_immediate.json") // 這裡填入實際路徑
    suspend fun getAllStations(): List<YouBikeStation>
}

// 定義一個回傳 List<YouBikeStation> 的函數
suspend fun fetchYouBikeData(): List<YouBikeStation>? {
    return try {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://tcgbusfs.blob.core.windows.net/dotapp/youbike/")
            .addConverterFactory(GsonConverterFactory.create()) // 這裡就幫你把 JSON 解析好了！
            .build()

        val apiService = retrofit.create(YouBikeApiService::class.java)

        val productList = apiService.getAllStations()

        val geojson = convertToGeoJson(productList)
        println("data: " + geojson.toString())
        
        productList


    } catch (e: Exception) {
        // 處理錯誤 (例如：沒網路、URL 錯誤)
        e.printStackTrace()
        null // 失敗時回傳 null
    }
}

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Store GeoJSON data
    var geoJsonData by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        
        // Fetch YouBike data
        val stations = fetchYouBikeData()
        if (stations != null) {
            val wrapper = convertToGeoJson(stations)
            geoJsonData = Gson().toJson(wrapper)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                onCreate(null)
                getMapAsync { map ->
                    // Set a more detailed style
                    map.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                        if (hasLocationPermission) {
                            enableLocationComponent(context, map, style)
                        }
                        
                        // Add YouBike Source and Layer if data is already available
                        geoJsonData?.let { data ->
                            addYouBikeLayers(style, data)
                        }
                    }
                }
            }
        },
        update = { mapView ->
            println("MapScreen update called, geoJsonData is null? ${geoJsonData == null}")
            mapView.getMapAsync { map ->
                map.getStyle { style ->
                    println("MapScreen getStyle callback fired! hasLocationPermission=$hasLocationPermission")
                    if (hasLocationPermission) {
                        enableLocationComponent(context, map, style)
                    }
                    
                    // Update or add YouBike layers when data arrives
                    geoJsonData?.let { data ->
                        println("MapScreen getStyle geoJsonData is present!")
                        val source = style.getSourceAs<GeoJsonSource>("youbike-source")
                        if (source != null) {
                            println("MapScreen getStyle updating existing source")
                            source.setGeoJson(data)
                        } else {
                            println("MapScreen getStyle calling addYouBikeLayers")
                            addYouBikeLayers(style, data)
                        }
                    }
                }
            }
        }
    )
}

private fun addYouBikeLayers(style: Style, data: String) {
    println("addYouBikeLayers called!")
    if (style.getSource("youbike-source") == null) {
        println("addYouBikeLayers: source did not exist, adding it now")
        style.addSource(GeoJsonSource("youbike-source", data))

        // 根據數量產生顏色的 Expression：0=紅, 3=橘, 5=黃, 10=綠
        fun quantityColorExpression(property: String): Expression {
            return Expression.step(
                Expression.toNumber(Expression.get(property)),
                Expression.color(Color.parseColor("#E53935")),  // 0: 紅色 (無車/無位)
                Expression.stop(3, Expression.color(Color.parseColor("#FB8C00"))),   // 3: 橘色
                Expression.stop(5, Expression.color(Color.parseColor("#FDD835"))),   // 5: 黃色
                Expression.stop(10, Expression.color(Color.parseColor("#43A047")))   // 10+: 綠色
            )
        }

        // 可借車數圖層 (左邊的點)
        val rentLayer = CircleLayer("youbike-rent-layer", "youbike-source")
        rentLayer.setProperties(
            PropertyFactory.circleColor(quantityColorExpression("available_rent_bikes")),
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleStrokeColor(Color.WHITE),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleTranslate(arrayOf(-5f, 0f))  // 往左偏移
        )
        style.addLayer(rentLayer)

        // 可還車數圖層 (右邊的點)
        val returnLayer = CircleLayer("youbike-return-layer", "youbike-source")
        returnLayer.setProperties(
            PropertyFactory.circleColor(quantityColorExpression("available_return_bikes")),
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleStrokeColor(Color.WHITE),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleTranslate(arrayOf(5f, 0f))   // 往右偏移
        )
        style.addLayer(returnLayer)

        println("addYouBikeLayers: rent + return layers added to style!")
    } else {
        println("addYouBikeLayers: source already exists!")
    }
}


@SuppressLint("MissingPermission")
private fun enableLocationComponent(context: Context, mapLibreMap: MapLibreMap, loadedMapStyle: Style) {
    val locationComponent = mapLibreMap.locationComponent
    val activationOptions = LocationComponentActivationOptions.builder(context, loadedMapStyle)
        .useDefaultLocationEngine(true)
        .build()

    locationComponent.activateLocationComponent(activationOptions)
    locationComponent.isLocationComponentEnabled = true
    locationComponent.cameraMode = CameraMode.TRACKING
    locationComponent.renderMode = RenderMode.COMPASS
}