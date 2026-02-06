//package gov.ny.its.arcGisReplicaPOC.ui.theme.node
//
//import android.Manifest.permission
//import android.util.Log
//import androidx.activity.compose.rememberLauncherForActivityResult
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.navigationBarsPadding
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.statusBarsPadding
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.filled.ArrowBack
//import androidx.compose.material3.Button
//import androidx.compose.material3.CircularProgressIndicator
//import androidx.compose.material3.ExperimentalMaterial3Api
//import androidx.compose.material3.Icon
//import androidx.compose.material3.IconButton
//import androidx.compose.material3.Text
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.zIndex
//import com.arcgismaps.tasks.geodatabase.SyncDirection
//import com.arcgismaps.data.ArcGISFeature
//import com.arcgismaps.data.Geodatabase
//import com.arcgismaps.data.QueryParameters
//import com.arcgismaps.geometry.Geometry
//import com.arcgismaps.geometry.GeometryEngine
//import com.arcgismaps.data.FeatureQueryResult
//import com.arcgismaps.geometry.SpatialReference
//import com.arcgismaps.location.LocationDisplayAutoPanMode
//import com.arcgismaps.location.SimulatedLocationDataSource
//import com.arcgismaps.location.SimulationParameters
//import com.arcgismaps.mapping.ArcGISMap
//import com.arcgismaps.mapping.MobileMapPackage
//import com.arcgismaps.mapping.Viewpoint
//import com.arcgismaps.mapping.layers.FeatureLayer
//import com.arcgismaps.mapping.symbology.SimpleFillSymbol
//import com.arcgismaps.mapping.symbology.SimpleFillSymbolStyle
//import com.arcgismaps.mapping.symbology.SimpleLineSymbol
//import com.arcgismaps.mapping.symbology.SimpleLineSymbolStyle
//import com.arcgismaps.mapping.view.Graphic
//import com.arcgismaps.mapping.view.GraphicsOverlay
//import com.arcgismaps.tasks.geodatabase.GeodatabaseSyncTask
//import com.arcgismaps.toolkit.geoviewcompose.MapView
//import com.arcgismaps.toolkit.geoviewcompose.MapViewProxy
//import com.arcgismaps.toolkit.geoviewcompose.rememberLocationDisplay
//import gov.ny.its.arcGisReplicaPOC.ui.theme.UniqueParkViewPoint
//import gov.ny.its.arcGisReplicaPOC.ui.theme.credentialauth.AuthMode
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import java.io.File
//import java.time.Instant
//
//// Holds the unique park name and viewpint
////data class UniqueParkViewPoint(val name: String, val viewpoint: Viewpoint)
//
//// 1. load offline map 2. Load local geo databases (parks boundaries, trails, facilities, huntiing 3. Builds featureLayers from the geodatabases 4. computes unique park-level viewpoints by merging park boundaries
////5. supports identity on tap adn full geodatabase sync
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun ParkMap2(
//    authMode: AuthMode = AuthMode.TOKEN_CREDENTIAL,
//    mmpkFileName: String = "nys_offline.mmpk",
//    gdbFileNames: List<String> = listOf(
//        "parkBoundriesQA.geodatabase",
//        "trailsQA.geodatabase",
//        "facility_featuresQA.geodatabase",
//        "huntingQA.geodatabase"
//    ),
//    selectedParkName: String? = null,
//    onBack: () -> Unit
//) {
//
////state variables
//    val mapViewProxy = remember { MapViewProxy() }
//    val context = LocalContext.current
//    val scope = rememberCoroutineScope() // Required for async identification
//    var map by remember { mutableStateOf<ArcGISMap?>(null) }
//    var isLoading by remember { mutableStateOf(false) }
//    var isSyncing by remember { mutableStateOf(false) }
//
//    var selectedPark by remember { mutableStateOf<UniqueParkViewPoint?>(null) }
//    var parksExpanded by remember { mutableStateOf(false) }
//
//    var facilityCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
//    var lastFacilityQueried by remember { mutableStateOf<String?>(null) }
//
//    //list of unique park viewpoints
//    var uniqueParkViewPoints by remember { mutableStateOf<List<UniqueParkViewPoint>>(emptyList()) }
//
//    //location permission
//    var hasLocationPermission by remember { mutableStateOf(false) }
//
//    //Initialize standard locationDisplay
//    val locationDisplay = rememberLocationDisplay()
//
//    val alleganymock = remember { com.arcgismaps.geometry.Point(-78.782489, 41.999191, SpatialReference.wgs84()) }
//
//    val bufferOverlay = remember { GraphicsOverlay() }
//
//    //mock location
//    val simulatedSource = remember {
//        val mockPoint = com.arcgismaps.geometry.Point(-78.782489, 41.999191, SpatialReference.wgs84())
//        val params = SimulationParameters(
//            startTime = Instant.now(),
//            velocity = 1.0,
//            horizontalAccuracy = 5.0
//        )
//
//        SimulatedLocationDataSource().apply {
//            setLocationsWithPolyline(
//                com.arcgismaps.geometry.Polyline(listOf(mockPoint)),
//                params
//            )
//        }
//    }
//
//
//    val locationPermissionLauncher = rememberLauncherForActivityResult(
//        ActivityResultContracts.RequestMultiplePermissions()
//    ) { result ->
//        hasLocationPermission =
//            (result[permission.ACCESS_FINE_LOCATION] == true) ||
//                    (result[permission.ACCESS_COARSE_LOCATION] == true)
//    }
//
//
//    LaunchedEffect(hasLocationPermission) {
//        if (hasLocationPermission) {
//            try {
//
//                locationDisplay.dataSource = simulatedSource
//
//                locationDisplay.dataSource.start()
//                locationDisplay.setAutoPanMode(LocationDisplayAutoPanMode.Recenter)
//
//                Log.d("Arcgis", "Mock Started")
//            } catch (e: Exception) {
//                Log.e("Arcgis", "Error starting mock: ${e.message}")
//            }
//        }
//    }
//
//    LaunchedEffect(Unit) {
//
//        locationPermissionLauncher.launch(
//            arrayOf(
//                permission.ACCESS_FINE_LOCATION,
//                permission.ACCESS_COARSE_LOCATION
//            )
//        )
//    }
//
//
//
//// to load the parks list
//    LaunchedEffect(selectedParkName, map, uniqueParkViewPoints) {
//        val target = selectedParkName ?: return@LaunchedEffect
//        val currentMap = map ?: return@LaunchedEffect
//        if (uniqueParkViewPoints.isEmpty()) return@LaunchedEffect
//
//        val match = uniqueParkViewPoints.firstOrNull { it.name.equals(target, ignoreCase = true) }
//            ?: return@LaunchedEffect
//
//        // show park boundaries layer so you can see outlines
//        currentMap.operationalLayers.forEach { lyr ->
//            val fl = lyr as? FeatureLayer ?: return@forEach
//            if (fl.name.contains("parkbound", ignoreCase = true)) {
//                fl.isVisible = true
//            }
//        }
//
//        mapViewProxy.setViewpoint(match.viewpoint)
//    }
//
//
//    //corute scope to run once - loads offline map, loads all geodatabases, create feature layer, compute park boundaries
//    LaunchedEffect(Unit) {
//        withContext(Dispatchers.IO) {
//            try {
//
//                //Load offline map
//                val mmpkFile = File(context.filesDir, mmpkFileName)
//                if (!mmpkFile.exists()) {
//                    context.assets.open(mmpkFileName).use { it.copyTo(mmpkFile.outputStream()) }
//                }
//                val mmpk = MobileMapPackage(mmpkFile.absolutePath)
//                mmpk.load().getOrThrow()
//                val localMap = mmpk.maps.first()
//                localMap.load().getOrThrow()
//
//                //load GeoDatabases
//                gdbFileNames.forEachIndexed { index, gdbName ->
//                    val gdbFile = File(context.filesDir, gdbName)
//                    if (!gdbFile.exists()) {
//                        //copy geodatabase on first run
//                        context.assets.open(gdbName).use { it.copyTo(gdbFile.outputStream()) }
//                    }
//
//                    //loads each geodatabases
//                    val geodatabase = Geodatabase(gdbFile.absolutePath)
//                    geodatabase.load().getOrThrow()
//
//                    // process each feature table
//                    geodatabase.featureTables.forEach { table ->
//                        //park boundaries -filter categories, group feature by park lable, merge all geometries for park, create viewpoint per park
//                        if(gdbName.contains("parkBoundries", ignoreCase = true)){
//                            val params = QueryParameters().apply {
//                                whereClause = "Category not in ('COE' , 'Other', 'Conservation Easement')"
//                            }
//                            val result = table.queryFeatures(params).getOrThrow()
//                            //val features = result.toList()
//                            val features: List<ArcGISFeature> = result.toList().mapNotNull { it as? ArcGISFeature }
//                            Log.d("Arcgis", "Queried ${features.size} park boundary features")
//
//                            // Group features by "label" attribute
//                            // val grouped = features.groupBy { it.attributes["label"]?.toString() ?: "" }
//
//                            val grouped: Map<String, List<ArcGISFeature>> =
//                                features.groupBy { it.attributes["label"]?.toString().orEmpty() }
//
//                            // Merge geometries and create viewpoints
//                            // val tempViewPoints = grouped.mapNotNull { (name, items) ->
//                            //val geometries = items.mapNotNull { it.geometry }
//                            // val tempViewPoint = grouped.mapNotNull { (name, items) ->
//                            val tempViewPoints: List<UniqueParkViewPoint> =
//                                grouped.entries.mapNotNull { entry ->
//                                    val name: String = entry.key
//                                    val items: List<ArcGISFeature> = entry.value
//
//                                    // val geometries: List<com.arcgismaps.geometry.Geometry> = items.mapNotNull { it.geometry }
//                                    val geometries: List<Geometry> = items.mapNotNull { it.geometry }
//                                    if (geometries.isEmpty()) return@mapNotNull null
//
//                                    val merged: Geometry? = geometries.drop(1).fold(geometries.first()) { acc, g ->
//                                        GeometryEngine.union(acc, g) ?: acc
//                                    }
//                                    val extent = merged?.extent ?: return@mapNotNull null
//                                    UniqueParkViewPoint(name, Viewpoint(extent))
////                                merged?.extent?.let { extent ->
////                                    UniqueParkViewPoint(name, Viewpoint(extent))
////                                }
//                                }.sortedBy { it.name }
//
//                            /*if (geometries.isNotEmpty()) {
//                                // GeometryEngine.union merges multiple parts into one
//                                //val merged = GeometryEngine.union(geometries)
//                                val merged = com.arcgismaps.geometry.GeometryEngine.union(geometries)
//                                merged?.let {
//                                    UniqueParkViewPoint(name, Viewpoint(it.extent))
//                                }
//                            } else null*/
//                            // }.sortedBy { it.name }
//
//                            withContext(Dispatchers.Main) {
//                                uniqueParkViewPoints = tempViewPoints
//
//                                Log.d("ArcGIS", "unique view points ${uniqueParkViewPoints.size}")
//                                uniqueParkViewPoints.forEachIndexed { index, vp ->
//                                    Log.d(
//                                        "unique parkviewpoints",
//                                        "$index -> ${vp.name} | Extent=${vp.viewpoint.targetGeometry?.extent}"
//                                    )
//                                }
//                            }
//                        }
//                        val featureLayer = FeatureLayer.createWithFeatureTable(table)
//
//
//                        if (gdbName.contains("hunting", ignoreCase = true) ||
//                            gdbName.contains("parkBoundries", ignoreCase = true)) {
//                            featureLayer.isVisible = false
//                        }
//
//                        localMap.operationalLayers.add(featureLayer)
//
//                        featureLayer.load().getOrThrow()
//                    }
//                }
//
//                withContext(Dispatchers.Main) { map = localMap }
//            } catch (e: Exception) {
//                Log.e("ArcGIS", "Error loading: ${e.message}")
//            } finally {
//                isLoading = false
//            }
//        }
//    }
//
//
//    Box(Modifier.fillMaxSize()) {
//        //map display
//        map?.let { arcGISMap ->
//            MapView(
//                modifier = Modifier.fillMaxSize(),
//                arcGISMap = arcGISMap,
//                locationDisplay = locationDisplay,
//
//                mapViewProxy = mapViewProxy,
//                onSingleTapConfirmed = { tapEvent ->
//                    scope.launch {
//                        try {
//
//                            arcGISMap.operationalLayers.forEach { layer ->
//                                (layer as? FeatureLayer)?.clearSelection()
//                            }
//
//
//                            //trigger the nearest Aminity search
////                            val facilityLayer = arcGISMap.operationalLayers.firstOrNull {
////                                it.name.contains("Facility", ignoreCase = true)
////                            }as? FeatureLayer
//
////                            facilityLayer?.featureTable?.let {
////                                table ->
////                                runNearestAmenityQuery(
////                                    scope = scope,
////                                    alleganymock = alleganymock,
////                                    selectedParkListItem = selectedParkName ?: "Allegany",
////                                    facilityTable = table,
////                                    onResultFound = { response ->
////                                        Log.d("arcgis result", response)
////                                    }
////                                )
////                            }
//
//
//                            fun findNearbyAmenities() {
//                                val currentPos = locationDisplay.location.value?.position ?: return
//
//                                scope.launch {
//                                    // Show the blue circle on map
//                                    showBufferArea(currentPos, bufferOverlay)
//
//                                    // Find all facility tables in the map
//                                    map?.operationalLayers?.filterIsInstance<FeatureLayer>()?.forEach { layer ->
//                                        if (layer.name.contains("facility", ignoreCase = true)) {
//                                            val amenities = queryAmenitiesInRange(currentPos, layer.featureTable!!)
//
//                                            // Select them visually on the map
//                                            layer.selectFeatures(amenities)
//
//                                            Log.d("ArcGIS", "Found ${amenities.size} amenities nearby in ${layer.name}")
//                                            amenities.forEach {
//                                                Log.d("ArcGIS", " - ${it.attributes["Sub_Asset"]}")
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//
//                            // Identify tapped features accross operational layers
//                            val results = mapViewProxy.identifyLayers(
//                                screenCoordinate = tapEvent.screenCoordinate,
//                                tolerance = 12.dp,
//                                returnPopupsOnly = false
//                            ).getOrThrow()
//
//                            results.forEach { result ->
//
//                                val layer = result.layerContent as? FeatureLayer
//                                val layerName = layer?.name ?: ""
//
//                                val firstElement = result.geoElements.firstOrNull() as? ArcGISFeature
//
//                                if (firstElement != null) {
//
//                                    if (!layerName.contains("parkboundriesqa_parkboundries", ignoreCase = true)) {
//
//
//                                        layer?.selectFeature(firstElement)
//
//
//                                        val trailName = firstElement.attributes["Trail_Name"] ?: "Unknown Trail"
//                                        Log.d("arcgis", "clicked: $trailName in $layerName")
//
//                                        val facilityName = firstElement.attributes["Facility_Name"] ?: "N/A"
//                                        Log.d("arcgis facility" ,"clicked: $facilityName | $layerName")
//
//                                        Log.d("ArcGIS_Identify", "Trail: $trailName | Layer: $layerName | Facility: $facilityName")
//
//                                        firstElement.attributes.forEach { (key, value) ->
//                                            Log.d("arcgis", "   $key: $value")
//                                        }
//                                    }
//
//                                    if (layer != null && layerName.contains("facility", ignoreCase = true)) {
//
//                                        val facilityFilterName =
//                                            (firstElement.attributes["Facility"] as? String)
//                                                ?: (firstElement.attributes["Facility_Name"] as? String)
//
//                                        if (!facilityFilterName.isNullOrBlank()) {
//                                            lastFacilityQueried = facilityFilterName
//
//                                            val counts = getFeatureCountsBySubAssetClientSide(
//                                                featureLayer = layer,
//                                                facilityName = facilityFilterName
//                                            )
//
//                                            facilityCounts = counts
//
//                                            Log.d("ArcGIS_STATS", "Counts for facility=$facilityFilterName")
//                                            counts.toList().sortedByDescending { it.second }.forEach { (subAsset, cnt) ->
//                                                Log.d("ArcGIS_STATS", "  $subAsset -> $cnt")
//                                            }
//                                        }
//                                    }
//
//
//
//                                }
//                            }
//                        } catch (e: Exception) {
//                            Log.e("ArcGIS_Identify", "Identify failed: ${e.message}")
//                        }
//                    }
//                }
//            )
//        } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))
//
//        IconButton(
//            onClick = onBack,
//            modifier = Modifier
//                .align (Alignment.TopStart)
//                .statusBarsPadding()
//                .padding(20.dp)
//                .zIndex(10f)
//        ) {
//            Icon(
//                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
//                contentDescription = "Back",
//                tint = Color.Black
//            )
//        }
//
//        // sync all databases
//        Button(
//            onClick = {
//                scope.launch {
//                    Log.d("Arcgis", "Sync button clicked")
//                    try {
//                        isSyncing = true
////                        val featureServiceURL =
////                            "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/Facility_FeaturesQA/FeatureServer"
//                        val syncMap = mapOf(
//                            "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/ParkBoundriesQA/FeatureServer" to "parkBoundriesQA.geodatabase",
//                            "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/TrailsQA/FeatureServer" to "trailsQA.geodatabase",
//                            "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/Facility_FeaturesQA/FeatureServer" to "facility_featuresQA.geodatabase",
//                            "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/HuntingQA/FeatureServer" to "huntingQA.geodatabase",
//                        )
//                        syncMap.forEach { (serviceUrl, filename) ->
//                            Log.d("Arcgis", "Syncing: $filename")
//                            val syncTask = GeodatabaseSyncTask(serviceUrl)
//                            val gdbFile = File(context.filesDir, filename)
//                            val geodatabase = Geodatabase(gdbFile.absolutePath)
//
//                            syncTask.load().getOrThrow()
//                            geodatabase.load().getOrThrow()
//
//                            val params = syncTask.createDefaultSyncGeodatabaseParameters(geodatabase).getOrThrow()
//                            params.geodatabaseSyncDirection = SyncDirection.Download
//
//                            val syncJob = syncTask.createSyncGeodatabaseJob(params, geodatabase)
//                            syncJob.start()
//
//                            val result = syncJob.result().getOrThrow()
//                            Log.d("Arcgis", "synced successfully $filename: ${result.size} elements")
//                        }
//                        Log.d("Arcgis", "All databases synced successfully")
//
//                        val gdbFile = File(context.filesDir, "facility_featuresQA.geodatabase")
//                        val geodatabase = Geodatabase(gdbFile.absolutePath)
//                        geodatabase.load().getOrThrow()
//
//
//
//                    } catch (e: Exception) {
//                        Log.e("arcgis error block", "failed job; ${e.message}")
//
//                    } finally {
//                        isSyncing = false
//                    }
//                }
//            },
//            enabled = !isSyncing,
//            modifier = Modifier
//                .align(Alignment.BottomCenter)
//                .padding(16.dp)
//                .navigationBarsPadding()
//                .zIndex(2f)
//        ) {
//            Text(if (isSyncing) "Syncing..." else "Sync All Databases")
//        }
//    }
//}
//
//private suspend fun getFeatureCountsBySubAssetClientSide(
//    featureLayer: FeatureLayer,
//    facilityName: String
//): Map<String, Int> {
//
//    val table = featureLayer.featureTable
//    val safeFacilityName = facilityName.replace("'", "''")
//
//    val qp = QueryParameters().apply {
//        whereClause = "Facility = '$safeFacilityName'"
//    }
//
//    val result: FeatureQueryResult = table?.queryFeatures(qp)!!.getOrThrow()
//
//    val counts = mutableMapOf<String, Int>()
//    for (geoElement in result) {
//        val feature = geoElement as? ArcGISFeature ?: continue
//        val subAsset = feature.attributes["Sub_Asset"]?.toString()?.trim().orEmpty()
//        if (subAsset.isBlank()) continue
//        counts[subAsset] = (counts[subAsset] ?: 0) + 1
//    }
//    return counts
//}
//
////Finds the nearest feature in a list to a specific point.
//
//fun findNearestFeature(fromFeatures: List<ArcGISFeature>, toLocation: com.arcgismaps.geometry.Point): ArcGISFeature? {
//    if (fromFeatures.isEmpty()) return null
//    return fromFeatures.minByOrNull { feature ->
//        val geom = feature.geometry ?: return@minByOrNull Double.MAX_VALUE
//        com.arcgismaps.geometry.GeometryEngine.distanceOrNull(geom, toLocation)!!
//    }
//}
//
///**
// * Calculates distance between a point and a feature's geometry.
// * Note: Returns miles (assuming input is in meters/Web Mercator, we convert).
// */
//fun getDistanceInMiles(fromLocation: com.arcgismaps.geometry.Point, toFeature: ArcGISFeature): Double {
//    val geometry = toFeature.geometry ?: return 0.0
//    // distance is in meters if using Web Mercator/MMPK
//    val distanceMeters = GeometryEngine.distanceOrNull(geometry, fromLocation)
//    return distanceMeters?.times(0.000621371) ?: 0.0 // Convert meters to miles
//
//
//}
//
//private suspend fun queryAmenitiesInRange(
//    currentLocation: com.arcgismaps.geometry.Point,
//    facilityTable: com.arcgismaps.data.FeatureTable,
//    bufferDistanceMeters: Double = 500.0
//): List<ArcGISFeature> {
//    // 1. Ensure the point is in a projected coordinate system (meters) for accurate buffering
//    val mercatorPoint = GeometryEngine.projectOrNull(currentLocation, SpatialReference.webMercator())
//            as com.arcgismaps.geometry.Point
//
//    // 2. Create the 500m buffer
//    val queryBuffer = GeometryEngine.bufferOrNull(mercatorPoint, bufferDistanceMeters)
//
//    // 3. Setup Spatial Query
//    val params = QueryParameters().apply {
//        geometry = queryBuffer
//        spatialRelationship = com.arcgismaps.data.SpatialRelationship.Intersects
//    }
//
//    // 4. Execute and return results
//    val result = facilityTable.queryFeatures(params).getOrThrow()
//    return result.toList().mapNotNull { it as? ArcGISFeature }
//}
//
//fun showBufferArea(
//    center: com.arcgismaps.geometry.Point,
//    overlay: GraphicsOverlay,
//    distance: Double = 500.0
//) {
//    val mercatorPoint = GeometryEngine.projectOrNull(center, SpatialReference.webMercator())
//            as com.arcgismaps.geometry.Point
//    val circle = GeometryEngine.bufferOrNull(mercatorPoint, distance)
//
//    val fillSymbol = SimpleFillSymbol(
//        style = SimpleFillSymbolStyle.Solid,
//        color = Color(0x330000FF), // Very light blue
//        outline = SimpleLineSymbol(SimpleLineSymbolStyle.Solid, Color.Blue, 1f)
//    )
//
//    overlay.graphics.clear()
//    overlay.graphics.add(Graphic(circle, fillSymbol))
//}
//
////to find one nearest feature
////private fun runNearestAmenityQuery(
////    scope: CoroutineScope,
////    alleganymock: com.arcgismaps.geometry.Point,
////    selectedParkListItem: String,
////    facilityTable: com.arcgismaps.data.FeatureTable,
////    onResultFound: (String) -> Unit
////) {
////    scope.launch {
////        try {
////            // Buffer must be in meters if map is Web Mercator
////            val queryBuffer = GeometryEngine.bufferOrNull(alleganymock, 500.0)
////
////            val params = QueryParameters().apply {
////                val safeName = selectedParkListItem.replace("'", "''")
////                whereClause = "Facility = '$safeName'"
////                geometry = queryBuffer
////                spatialRelationship = com.arcgismaps.data.SpatialRelationship.Intersects
////                maxFeatures = 10
////            }
////
////            val result = facilityTable.queryFeatures(params).getOrThrow()
////            val featureList = result.toList().mapNotNull { it as? ArcGISFeature }
////
////            if (featureList.isNotEmpty()) {
////                val nearest = findNearestFeature(featureList, alleganymock)
////                nearest?.let { feat ->
////                    val subAsset = feat.attributes["Sub_Asset"]?.toString() ?: "Unknown"
////                    val nameAttr = feat.attributes["Name"]?.toString() ?: "Unknown"
////
////                    val distMiles = getDistanceInMiles(alleganymock, feat)
////                    val distFeet = (distMiles * 5280).toInt()
////
////                    val response = "$distFeet feet from current location. Named $nameAttr, a $subAsset"
////                    onResultFound(response)
////                }
////            }
////        } catch (e: Exception) {
////            Log.e("ArcGIS_Query", "Query failed: ${e.message}")
////        }
////    }
////}
//
