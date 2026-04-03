# **🚲 MyBike \- 台北市 YouBike 即時地圖**

MyBike 是一款基於 **Jetpack Compose** 與 **MapLibre** 開發的 Android 應用程式。旨在提供台北市 YouBike 2.0 站點的即時租借與還車資訊，透過直觀的顏色編碼與地圖分層顯示技術，優化使用者的通勤體驗。
資料由 <a href="https://data.taipei/dataset/detail?id=c6bc8aed-557d-41d5-bfb1-8da24f78f2fb">YouBike2.0臺北市公共自行車即時資訊</a> 取得

## **🌟 核心功能**

* **即時數據串接**：串接台北市政府公開資料 API，獲取最新 YouBike 2.0 站點狀態。  
* **動態地圖分層 (LOD)**：  
  * 根據地圖縮放等級（Zoom Level）自動過濾站點。  
  * 大站（車位 \>= 60）：全縮放等級顯示。  
  * 中站（車位 25-60）：Zoom 13 以上顯示。  
  * 小站（車位 \< 25）：Zoom 15 以上顯示。  
* **直觀顏色與樣式**：  
  * **雙圓圈指示**：左圓代表「可借車輛」，右圓代表「可還車位」。  
  * **顏色狀態**：🔴 危急 (0-2)、🟠 普通 (3-9)、🟢 充足 (10+)。  
* **站點詳細資訊 (Bottom Sheet)**：點擊站點可查看地址、中文名稱、英文名稱、精確數量及最後更新時間。  
* **定位追蹤**：支援系統定位權限請求，並自動將視角導航至使用者當前位置。

## **🛠 技術棧**

* **UI 框架**：Jetpack Compose (Material 3\)  
* **地圖引擎**：MapLibre Native SDK for Android  
* **網路請求**：Retrofit 2 & OkHttp  
* **資料解析**：Gson (搭配自定義 GeoJSON 轉換器)  
* **非同步處理**：Kotlin Coroutines & Flow  
* **地圖樣式**：使用 OpenFreeMap 提供之樣式。

## **📸 畫面預覽**

1. **地圖主畫面**：整合了 AndroidView 與 Compose，提供流暢的縮放與點擊體驗。  
2. **站點詳情**：使用 ModalBottomSheet 顯示由 StatItem 組成的視覺化數據面板。  
3. **定位功能**：整合 LocationComponent 實現即時追蹤。

<img width="1224" height="2570" alt="image" src="https://github.com/user-attachments/assets/99333f94-87b3-46b2-8b9a-c2e16c444ef0" />
