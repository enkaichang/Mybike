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
import android.graphics.drawable.Icon
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.History
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "MyBikeDebug"


private val Context.dataStore by preferencesDataStore(name = "favorite_prefs")

class FavoriteManager(private val context: Context) {
    private val FAVORITES_KEY = stringSetPreferencesKey("favorite_stations")

    // 取得所有最愛站點的 sno
    val favoriteStations: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[FAVORITES_KEY] ?: emptySet()
        }

    // 切換最愛狀態 (如果存在就移除，不存在就加入)
    suspend fun toggleFavorite(sno: String) {
        context.dataStore.edit { preferences ->
            val currentFavorites = preferences[FAVORITES_KEY] ?: emptySet()
            val newFavorites = currentFavorites.toMutableSet()
            if (newFavorites.contains(sno)) { //是否包含
                newFavorites.remove(sno)
            } else {
                newFavorites.add(sno)
            }
            preferences[FAVORITES_KEY] = newFavorites
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
                val productList = apiService.getAllStations()
                if (!productList.isNotEmpty()) {
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
    return try {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://tcgbusfs.blob.core.windows.net/dotapp/youbike/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(YouBikeApiService::class.java)

        val productList = apiService.getAllStations()

        if (productList.isNotEmpty()) {
            val sample = productList[0]
        }

        
        productList
    } catch (e: Exception) {
        null
    }
}

object BikeColors {
    const val DANGER = "#BA1A1A"  // 0-2台：危急紅（一眼看出沒車）
    const val NORMAL = "#DC865A"  // 3-9台：中性灰綠（冷靜的過渡色）
    const val PLENTY = "#006D3A"  // 10+台：活力綠（大膽放心地去借）
}

data class StationTrend(
    val sno: String,
    val total: Int,
    val data: List<TrendPoint>
)

data class TrendPoint(
    @SerializedName("rent") val availableRent: Double,
    @SerializedName("return") val availableReturn: Double
)

fun getCurrentTimeIndex(): Int {
    val calender = Calendar.getInstance()
    val hour = calender.get(Calendar.HOUR_OF_DAY)
    val minute = calender.get(Calendar.MINUTE)
    return hour * 6 + (minute / 10)
}

fun getTodayDay(): Int {
    val calender = Calendar.getInstance()
    return calender.get(Calendar.DAY_OF_WEEK)
}

enum class PredictionResult { INCREASING, DECREASING, STABLE, UNKNOWN }
fun getPrediction(sno: String, allData: Map<String, StationTrend>): List<PredictionResult> {
    val unknownReturn = mutableListOf(PredictionResult.UNKNOWN, PredictionResult.UNKNOWN)
    val stationAllTrend = allData[sno] ?: return unknownReturn
    val dataList = stationAllTrend.data

    val total = stationAllTrend.total

    val currentIndex = getCurrentTimeIndex()
    val futureIndex = (currentIndex + 3) % 144

    // 防呆：確保 index 不會越界
    if (currentIndex >= dataList.size || futureIndex >= dataList.size) return unknownReturn

    val currentRentAvg = dataList[currentIndex].availableRent*total
    val futureRentAvg = dataList[futureIndex].availableRent*total
    val Rentdiff = futureRentAvg - currentRentAvg

    val currentReturnAvg = dataList[currentIndex].availableReturn*total
    val futureReturnAvg = dataList[futureIndex].availableReturn*total
    val Returndiff = futureReturnAvg - currentReturnAvg


    val rentResult  = when {
        Rentdiff >= 3.0 -> PredictionResult.INCREASING
        Rentdiff <= -3.0 -> PredictionResult.DECREASING
        else -> PredictionResult.STABLE
    }
    val returnResult  = when {
        Returndiff >= 3.0 -> PredictionResult.INCREASING
        Returndiff <= -3.0 -> PredictionResult.DECREASING
        else -> PredictionResult.STABLE
    }
     return mutableListOf(rentResult, returnResult)
}

suspend fun loadPredictionData(context: Context): Map<String, StationTrend>? = withContext(
    Dispatchers.IO) {
    try {
        val day = if (getTodayDay() == 1) 7 else getTodayDay() - 1
        val jsonString = context.assets.open("stations_avg_data_day_${day}.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<StationTrend>>() {}.type
        val list: List<StationTrend> = Gson().fromJson(jsonString, type)
        list.associateBy { it.sno }
    } catch (e: Exception) {
        Log.e("YouBikeData", "解析失敗: ${e.message}")
        null
    }
}

fun makePrediction(selectedStation: YouBikeStation?, avgData: Map<String, StationTrend>?): List<PredictionResult> {
    return if (selectedStation != null && avgData != null) {
        getPrediction(selectedStation.id, avgData)
    } else {
        mutableListOf(PredictionResult.UNKNOWN, PredictionResult.UNKNOWN)
    }
}

@Preview(showBackground = true)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var state by remember {mutableStateOf(0)}
    var allStations by remember { mutableStateOf<List<YouBikeStation>>(emptyList()) }

    // 在進入 MainScreen 時抓取資料
    LaunchedEffect(Unit) {
        val data = fetchYouBikeData()
        if (data != null) {
            allStations = data
        }
    }
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
            0 -> FavoriteScreen(modifier = Modifier.padding(innerPadding), allStations = allStations)
            1 -> MapScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteScreen(
    modifier: Modifier = Modifier,
    allStations: List<YouBikeStation>,
    is_Preview: Boolean = false // 新增：判斷是否為預覽模式
) {
    val context = LocalContext.current

    var avgData by remember { mutableStateOf<Map<String, StationTrend>?>(null) }
    LaunchedEffect(Unit) {
        if (!is_Preview) {
            // ✨ 2. 載入資料
            avgData = loadPredictionData(context)
        }
    }

    // 在預覽模式下使用假的 Manager
    val favoriteManager = remember {
        if (is_Preview) null else FavoriteManager(context)
    }

    val scope = rememberCoroutineScope()

    // 1. 取得使用者存下的 sno 集合 (如果是預覽，直接設定假資料)
    val favoriteIds by if (is_Preview) {
        remember { mutableStateOf(setOf("500101001", "500101002")) }
    } else {
        favoriteManager!!.favoriteStations.collectAsState(initial = emptySet())
    }

    // 2. 過濾出屬於最愛的站點
    val favoriteList = remember(favoriteIds, allStations) {
        allStations.filter { it.id in favoriteIds }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background // 使用預設背景色
    ) { innerPadding ->

        // --- A. 空狀態設計 ---
        if (favoriteList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "尚無最愛站點",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "在地圖上點擊星星即可加入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        } else {
            // --- B. 列表狀態設計 ---
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp), // 全部的外邊距
                verticalArrangement = Arrangement.spacedBy(12.dp) // 項目之間的間距
            ) {
                // items(...) 必須放在 LazyColumn 內
                items(
                    items = favoriteList,
                    key = { station -> station.id } // 增加 Key 可以讓滑動刪除動畫更順暢
                ) { station ->

                    // 計算預測
                    val prediction = makePrediction(station, avgData)

                    // 3. 實作 Swipe-to-Dismiss 邏輯
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                // 當滑動超過門檻時執行
                                if (!is_Preview) {
                                    scope.launch {
                                        favoriteManager?.toggleFavorite(station.id)
                                    }
                                }
                                true // 允許刪除
                            } else {
                                false // 拒絕刪除 (例如向右滑)
                            }
                        }
                    )

                    // 4. SwipeToDismissBox 組件
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false, // 禁用從左向右滑
                        backgroundContent = {
                            // 滑動時出現的背景 (通常是紅色垃圾桶)
                            DismissBackground(dismissState)
                        }
                    ) {
                        // 真正的卡片內容
                        FavoriteStationCard(
                            station = station,
                            predictionRent = prediction[0],
                            predictionReturn = prediction[1]
                        )
                    }
                }
            }
        }
    }
}

// --- 子組件 A: 卡片內容 ---
@Composable
fun FavoriteStationCard(
    station: YouBikeStation,
    predictionRent: PredictionResult,
    predictionReturn: PredictionResult
) {
    // 根據車輛數決定主題顏色
    val statusColor = getQuantityColorForUI(station.availableBikes)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(24.dp), // M3 偏好大圓角
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer, // Background color
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer       // Default color for text/icons inside
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .height(IntrinsicSize.Min), // 使 Row 內部元件高度一致
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- 左側：狀態膠囊 (Status Capsule) ---
            Column(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .background(
                        color = statusColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "可借",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = station.availableBikes.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor
                    )
                    // 趨勢圖標
                    if (predictionRent != PredictionResult.UNKNOWN && predictionRent != PredictionResult.STABLE) {
                        Icon(
                            imageVector = if (predictionRent == PredictionResult.INCREASING)
                                Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // --- 中間：站點詳細資訊 ---
            Column(
                modifier = Modifier.weight(0.7f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 行政區標籤
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = station.district,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Text(
                    text = station.stationName.replace("YouBike2.0_", ""),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = station.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // 次要數據：可還車位
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "可還車位 ${station.availableReturns}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (predictionReturn != PredictionResult.UNKNOWN && predictionReturn != PredictionResult.STABLE) {
                        Icon(
                            imageVector = if (predictionReturn == PredictionResult.INCREASING)
                                Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- 子組件 B: 滑動背景 (垃圾桶) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DismissBackground(dismissState: androidx.compose.material3.SwipeToDismissBoxState) {

    val alignment = Alignment.CenterEnd
    val icon = Icons.Default.Delete

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "移除最愛",
            tint = MaterialTheme.colorScheme.onError
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val favoriteManager = remember { FavoriteManager(context) }

    // 透過 collectAsState 將 Flow 轉為 Compose 狀態
    val favorites by favoriteManager.favoriteStations.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope() // 用於執行掛起函數

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

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    // Bottom Sheet state
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedStation by remember { mutableStateOf<YouBikeStation?>(null) }
    val sheetState = rememberModalBottomSheetState()

    var avgData by remember { mutableStateOf<Map<String, StationTrend>?>(null) }

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
        } else {
            Log.w(TAG, "MapScreen: Failed to fetch stations or received null")
        }

        avgData = loadPredictionData(context)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    onCreate(null)
                    getMapAsync { map ->
                        // Set a more detailed style
                        mapInstance = map // 取得地圖實體
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
                            val target = LatLng(lastLoc.latitude, lastLoc.longitude)
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 15.0), 2000)
                        }

                        // Update or add YouBike layers when data arrives
                        geoJsonData?.let { data ->
                            val source = style.getSourceAs<GeoJsonSource>("youbike-source")
                            if (source != null) {
                                source.setGeoJson(data)
                            } else {
                                addYouBikeLayers(style, data)
                            }
                        }

                        // Add click listener for station selection
                        map.addOnMapClickListener { latLng ->
                            val screenPoint = map.projection.toScreenLocation(latLng)
                            val pointF = PointF(screenPoint.x.toFloat(), screenPoint.y.toFloat())
                            
                            // Query all youbike layers
                            val layers = arrayOf(
                                "youbike-rent-large", "youbike-return-large",
                                "youbike-rent-medium", "youbike-return-medium",
                                "youbike-rent-small", "youbike-return-small"
                            )
                            
                            val features = map.queryRenderedFeatures(pointF, *layers)
                            if (features.isNotEmpty()) {
                                val properties = features[0].properties()
                                properties?.let {
                                    val station = Gson().fromJson(it, YouBikeStation::class.java)
                                    selectedStation = station
                                    showBottomSheet = true
                                    
                                    // Auto-center on station
                                    val targetPos = LatLng(station.latitude, station.longitude)
                                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(targetPos, 16.0), 1000)
                                }
                            }
                            true
                        }
                    }
                }
            }
        )

        FloatingActionButton(
            onClick = {
                mapInstance?.let { map ->
                    val lastLoc = map.locationComponent.lastKnownLocation
                    if (lastLoc != null) {
                        val target = LatLng(lastLoc.latitude, lastLoc.longitude)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 15.0), 2000)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.Map, contentDescription = "返回我的位置", tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }


        val prediction = remember(selectedStation, avgData) {
            makePrediction(selectedStation, avgData)
        }

        // Details Bottom Sheet
        if (showBottomSheet && selectedStation != null) {
            val isFavorite = favorites.contains(selectedStation!!.id)
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant) },
                containerColor = MaterialTheme.colorScheme.surface,

            ) {
                StationDetailContent(selectedStation!!,
                    prediction[0],
                    prediction[1],
                    isFavorite,
                    onFavoriteClick = {
                        scope.launch {
                            favoriteManager.toggleFavorite(selectedStation!!.id)
                        }
                    })
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
        availableBikes = 6,
        availableReturns = 18,
        latitude = 25.02605,
        longitude = 121.5436,
        act = "1"
    )

    MybikeTheme {
        // 為了讓 Preview 看起來更像 BottomSheet 的寬度
        Surface(modifier = Modifier.fillMaxWidth()) {
            StationDetailContent(station = mockStation, prediction_rent = PredictionResult.INCREASING, prediction_return = PredictionResult.DECREASING,true, {})
        }
    }
}

@Composable
fun StationDetailContent(station: YouBikeStation, prediction_rent: PredictionResult, prediction_return: PredictionResult,isFavorite: Boolean,  onFavoriteClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {

        // Station Name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ){
            Text(
                text = station.stationName.replace("YouBike2.0_", ""),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(48.dp)

            ) {
                Log.d(TAG, isFavorite.toString())
                Icon(
                    imageVector = if(isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline ,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

        }
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
                color = getQuantityColorForUI(station.availableBikes),
                prediction = prediction_rent
            )
            StatItem(
                icon = Icons.Filled.ElectricBike,
                label = "可還車位",
                value = station.availableReturns.toString(),
                color = getQuantityColorForUI(station.availableReturns),
                prediction = prediction_return
            )
            StatItem(
                icon = Icons.Default.Update,
                label = "總車位數",
                value = station.totalBikes.toString(),
                color = MaterialTheme.colorScheme.secondary,
                prediction = PredictionResult.UNKNOWN
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
fun StatItem(icon: ImageVector, label: String, value: String, color: androidx.compose.ui.graphics.Color, prediction:PredictionResult) {
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
        Row(verticalAlignment = Alignment.CenterVertically){
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            if (prediction != PredictionResult.UNKNOWN && prediction != PredictionResult.STABLE) {
                Icon(
                    if (prediction == PredictionResult.INCREASING) Icons.Filled.TrendingUp  else Icons.Filled.TrendingDown,
                    contentDescription = null,
                    tint = color
                )

            }

        }
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
    return androidx.compose.ui.graphics.Color(Color.parseColor(hex))
}

private fun addYouBikeLayers(style: Style, data: String) {

    if (style.getSource("youbike-source") == null) {
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