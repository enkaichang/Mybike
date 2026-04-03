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
import android.graphics.PointF
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import kotlin.jvm.java

// Compose Material 3 & Icons
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.tooling.preview.Preview

private const val TAG = "MyBikeDebug"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: App starting")
        
        // Initialize MapLibre
        MapLibre.getInstance(this)
        
        enableEdgeToEdge()
        setContent {
            MybikeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }

        // 1. 初始化 Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl("https://tcgbusfs.blob.core.windows.net/dotapp/youbike/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(YouBikeApiService::class.java)

        // 2. 在 LifecycleScope 或 ViewModel 中呼叫 (測試用)
        lifecycleScope.launch {
            try {
                Log.d(TAG, "onCreate: Fetching sample station data...")
                val productList = apiService.getAllStations()
                if (productList.isNotEmpty()) {
                    Log.d(TAG, "onCreate: Sample station ID: ${productList[0].id}")
                } else {
                    Log.w(TAG, "onCreate: Fetched station list is empty!")
                }
            } catch (e: Exception) {
                Log.e(TAG, "onCreate: Error fetching sample data", e)
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
    Log.d(TAG, "fetchYouBikeData: Starting fetch from API")
    return try {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://tcgbusfs.blob.core.windows.net/dotapp/youbike/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(YouBikeApiService::class.java)

        val productList = apiService.getAllStations()
        Log.d(TAG, "fetchYouBikeData: Successfully fetched ${productList.size} stations")

        if (productList.isNotEmpty()) {
            val sample = productList[0]
            Log.d(TAG, "fetchYouBikeData: Sample station: sno=${sample.id}, name=${sample.stationName}, lat=${sample.latitude}, lng=${sample.longitude}, Qty=${sample.totalBikes}")
        }

        val geojson = convertToGeoJson(productList)
        Log.d(TAG, "fetchYouBikeData: Conversion to GeoJson complete")
        
        productList
    } catch (e: Exception) {
        Log.e(TAG, "fetchYouBikeData: Error during API call or parsing", e)
        null
    }
}

object BikeColors {
    const val DANGER = "#BA1A1A"  // 0-2台：危急紅（一眼看出沒車）
    const val NORMAL = "#DC865A"  // 3-9台：中性灰綠（冷靜的過渡色）
    const val PLENTY = "#006D3A"  // 10+台：活力綠（大膽放心地去借）
}

@Preview(showBackground = true)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var state by remember {mutableStateOf(0)}

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column() {
                TabRow(
                    selectedTabIndex = state,
                    divider = {},
                    indicator = {tabPositions ->
                        if (state < tabPositions.size) {
                            TabRowDefaults.PrimaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[state]),
                                width = 50.dp

                            )
                        }

                    }
                ){
                    Tab(
                        selected = state == 0,
                        onClick = { state = 0 },
                        icon = { Icon(Icons.Outlined.StarOutline, contentDescription = null) },
                        text = { Text("Favorite", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = state == 1,
                        onClick = { state = 1 },
                        icon = { Icon(Icons.Outlined.Map, contentDescription = null) },
                        text = { Text("Map", fontWeight = FontWeight.Bold) }
                    )
                }

            }
        }
    ) { innerPadding ->
        when (state) {
            1 -> MapScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    
    // Bottom Sheet state
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedStation by remember { mutableStateOf<YouBikeStation?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        Log.d(TAG, "MapScreen: LaunchedEffect started")
        if (!hasLocationPermission) {
            Log.d(TAG, "MapScreen: Requesting location permissions")
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
            Log.d(TAG, "MapScreen: Received ${stations.size} stations, updating geoJsonData state")
            val wrapper = convertToGeoJson(stations)
            geoJsonData = Gson().toJson(wrapper)
        } else {
            Log.w(TAG, "MapScreen: Failed to fetch stations or received null")
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    onCreate(null)
                    getMapAsync { map ->
                        // Set a more detailed style
                        map.setStyle("https://tiles.openfreemap.org/styles/positron") { style ->
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
                Log.d(TAG, "AndroidView.update: geoJsonData is ${if (geoJsonData == null) "NULL" else "AVAILABLE (${geoJsonData?.length} chars)"}")
                mapView.getMapAsync { map ->
                    map.getStyle { style ->
                        if (hasLocationPermission) {
                            enableLocationComponent(context, map, style)
                        }
                        val lastLoc = map.locationComponent.lastKnownLocation
                        if (lastLoc != null) {
                            val target = org.maplibre.android.geometry.LatLng(lastLoc.latitude, lastLoc.longitude)
                            map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(target, 15.0), 2000)
                        }

                        // Update or add YouBike layers when data arrives
                        geoJsonData?.let { data ->
                            val source = style.getSourceAs<GeoJsonSource>("youbike-source")
                            if (source != null) {
                                Log.d(TAG, "AndroidView.update: Updating existing youbike-source")
                                source.setGeoJson(data)
                            } else {
                                Log.d(TAG, "AndroidView.update: youbike-source not found, calling addYouBikeLayers")
                                addYouBikeLayers(style, data)
                            }
                        }

                        // Add click listener for station selection
                        map.addOnMapClickListener { latLng ->
                            Log.d(TAG, "Map clicked at: $latLng")
                            val screenPoint = map.projection.toScreenLocation(latLng)
                            val pointF = PointF(screenPoint.x.toFloat(), screenPoint.y.toFloat())
                            
                            // Query all youbike layers
                            val layers = arrayOf(
                                "youbike-rent-large", "youbike-return-large",
                                "youbike-rent-medium", "youbike-return-medium",
                                "youbike-rent-small", "youbike-return-small"
                            )
                            
                            val features = map.queryRenderedFeatures(pointF, *layers)
                            Log.d(TAG, "Query features at click: found ${features.size} features")
                            if (features.isNotEmpty()) {
                                val properties = features[0].properties()
                                Log.d(TAG, "Selected feature properties: $properties")
                                properties?.let {
                                    val station = Gson().fromJson(it, YouBikeStation::class.java)
                                    selectedStation = station
                                    showBottomSheet = true
                                    
                                    // Auto-center on station
                                    val targetPos = org.maplibre.android.geometry.LatLng(station.latitude, station.longitude)
                                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(targetPos, 16.0), 1000)
                                }
                            }
                            true
                        }
                    }
                }
            }
        )

        // Details Bottom Sheet
        if (showBottomSheet && selectedStation != null) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant) },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                StationDetailContent(selectedStation!!)
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StationDetailContentPreview() {
    // 建立一個假的站點資料供預覽使用
    val mockStation = YouBikeStation(
        id = "500101001",
        stationName = "YouBike2.0_捷運科技大樓站",
        district = "大安區",
        lastUpdate = "2024-03-20 12:00:00",
        address = "復興南路二段235號前",
        stationNameEn = "MRT Technology Building Sta.",
        addressEn = "No. 235, Sec. 2, Fuxing S. Rd.",
        totalBikes = 30,
        availableBikes = 12,
        availableReturns = 18,
        latitude = 25.02605,
        longitude = 121.5436,
        act = "1"
    )

    MybikeTheme {
        // 為了讓 Preview 看起來更像 BottomSheet 的寬度
        Surface(modifier = Modifier.fillMaxWidth()) {
            StationDetailContent(station = mockStation)
        }
    }
}

@Composable
fun StationDetailContent(station: YouBikeStation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        // Station Name
        Text(
            text = station.stationName.replace("YouBike2.0_", ""),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = station.stationNameEn,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Address
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = station.address,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        // Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem(
                icon = Icons.Filled.PedalBike,
                label = "可借車輛",
                value = station.availableBikes.toString(),
                color = getQuantityColorForUI(station.availableBikes)
            )
            StatItem(
                icon = Icons.Filled.ElectricBike,
                label = "可還車位",
                value = station.availableReturns.toString(),
                color = getQuantityColorForUI(station.availableReturns)
            )
            StatItem(
                icon = Icons.Default.Update,
                label = "總車位數",
                value = station.totalBikes.toString(),
                color = MaterialTheme.colorScheme.secondary
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Last Update
        Text(
            text = "${station.lastUpdate}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun StatItem(icon: ImageVector, label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun getQuantityColorForUI(quantity: Int): androidx.compose.ui.graphics.Color {
    val hex = when {
        quantity <= 2 -> BikeColors.DANGER
        quantity <= 9 -> BikeColors.NORMAL
        else -> BikeColors.PLENTY
    }
    return androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
}

private fun addYouBikeLayers(style: Style, data: String) {
    Log.d(TAG, "addYouBikeLayers called! Data length: ${data.length}")
    if (data.length > 500) {
        Log.d(TAG, "addYouBikeLayers: GeoJSON preview: ${data.substring(0, 500)}")
    }

    if (style.getSource("youbike-source") == null) {
        Log.d(TAG, "addYouBikeLayers: Source 'youbike-source' does not exist, adding it now")
        style.addSource(GeoJsonSource("youbike-source", data))

        // 根據數量產生顏色的 Expression：0=紅, 3=橘, 5=黃, 10=綠
        fun quantityColorExpression(property: String): Expression {
            return Expression.step(
                Expression.toNumber(Expression.get(property)),
                Expression.color(Color.parseColor(BikeColors.DANGER)),  // 0: 紅色 (無車/無位)
                Expression.stop(3, Expression.color(Color.parseColor(BikeColors.NORMAL))),   // 3: 橘色
                Expression.stop(10, Expression.color(Color.parseColor(BikeColors.PLENTY)))   // 10+: 綠色
            )
        }

        // 建立一組可借/可還圖層，依站點規模分層顯示
        fun addStationLayerPair(suffix: String, filter: Expression, minZoom: Float?) {
            Log.d(TAG, "addStationLayerPair: Adding layers for scale '$suffix'")
            val rentLayer = CircleLayer("youbike-rent-$suffix", "youbike-source")
            rentLayer.setProperties(
                PropertyFactory.circleColor(quantityColorExpression("available_rent_bikes")),
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(1.5f),
                PropertyFactory.circleTranslate(arrayOf(-5f, 0f))
            )
            rentLayer.setFilter(filter)
            minZoom?.let { rentLayer.setMinZoom(it) }
            style.addLayer(rentLayer)

            val returnLayer = CircleLayer("youbike-return-$suffix", "youbike-source")
            returnLayer.setProperties(
                PropertyFactory.circleColor(quantityColorExpression("available_return_bikes")),
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(1.5f),
                PropertyFactory.circleTranslate(arrayOf(5f, 0f))
            )
            returnLayer.setFilter(filter)
            minZoom?.let { returnLayer.setMinZoom(it) }
            style.addLayer(returnLayer)
        }

        // 大站 (總車位 >= 60)：所有縮放等級都顯示
        addStationLayerPair(
            "large",
            Expression.gte(
                Expression.toNumber(Expression.get("Quantity")),
                Expression.literal(60)
            ),
            null
        )

        // 中站 (25 <= 總車位 < 60)：zoom >= 13 才顯示
        addStationLayerPair(
            "medium",
            Expression.all(
                Expression.gte(
                    Expression.toNumber(Expression.get("Quantity")),
                    Expression.literal(25)
                ),
                Expression.lt(
                    Expression.toNumber(Expression.get("Quantity")),
                    Expression.literal(60)
                )
            ),
            13f
        )

        // 小站 (總車位 < 25)：zoom >= 15 才顯示
        addStationLayerPair(
            "small",
            Expression.lt(
                Expression.toNumber(Expression.get("Quantity")),
                Expression.literal(25)
            ),
            15f
        )

        Log.d(TAG, "addYouBikeLayers: Layers added successfully!")
    } else {
        Log.d(TAG, "addYouBikeLayers: Source 'youbike-source' already exists, skipping layer creation")
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