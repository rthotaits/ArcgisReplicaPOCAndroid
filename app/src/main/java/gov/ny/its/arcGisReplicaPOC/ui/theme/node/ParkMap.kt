package gov.ny.its.arcGisReplicaPOC.ui.theme.node

import android.Manifest.permission
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arcgismaps.Color
import com.arcgismaps.tasks.geodatabase.SyncDirection
import com.arcgismaps.data.ArcGISFeature
import com.arcgismaps.data.Geodatabase
import com.arcgismaps.data.QueryParameters
import com.arcgismaps.geometry.Geometry
import com.arcgismaps.geometry.GeometryEngine
import com.arcgismaps.data.FeatureQueryResult
import com.arcgismaps.geometry.AngularUnit
import com.arcgismaps.geometry.GeodeticCurveType
import com.arcgismaps.geometry.LinearUnit
import com.arcgismaps.geometry.SpatialReference
import com.arcgismaps.location.LocationDisplayAutoPanMode
import com.arcgismaps.location.SimulatedLocationDataSource
import com.arcgismaps.location.SimulationParameters
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.MobileMapPackage
import com.arcgismaps.mapping.Viewpoint
import com.arcgismaps.mapping.layers.FeatureLayer
import com.arcgismaps.mapping.symbology.SimpleFillSymbol
import com.arcgismaps.mapping.symbology.SimpleFillSymbolStyle
import com.arcgismaps.mapping.symbology.SimpleLineSymbol
import com.arcgismaps.mapping.symbology.SimpleLineSymbolStyle
import com.arcgismaps.mapping.view.Graphic
import com.arcgismaps.mapping.view.GraphicsOverlay
import com.arcgismaps.tasks.geodatabase.GeodatabaseSyncTask
import com.arcgismaps.toolkit.geoviewcompose.MapView
import com.arcgismaps.toolkit.geoviewcompose.MapViewProxy
import com.arcgismaps.toolkit.geoviewcompose.rememberLocationDisplay
import gov.ny.its.arcGisReplicaPOC.ui.theme.UniqueParkViewPoint
import gov.ny.its.arcGisReplicaPOC.ui.theme.credentialauth.AuthMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkMap(
    authMode: AuthMode = AuthMode.TOKEN_CREDENTIAL,
    mmpkFileName: String = "nys_offline.mmpk",
    gdbFileNames: List<String> = listOf(
        "parkBoundriesQA.geodatabase",
        "trailsQA.geodatabase",
        "facility_featuresQA1.geodatabase",
        "huntingQA.geodatabase"
    ),
    selectedParkName: String? = null,
    onBack: () -> Unit
) {
    val mapViewProxy = remember { MapViewProxy() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var map by remember { mutableStateOf<ArcGISMap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var facilityCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var lastFacilityQueried by remember { mutableStateOf<String?>(null) }
    var uniqueParkViewPoints by remember { mutableStateOf<List<UniqueParkViewPoint>>(emptyList()) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    val locationDisplay = rememberLocationDisplay()
    val bufferOverlay = remember { GraphicsOverlay() }
    val alleganymock = remember { com.arcgismaps.geometry.Point(-78.782489, 41.999191, SpatialReference.wgs84()) }
    var totalNearbyCount by remember { mutableStateOf(0) }


    fun findNearbyAmenities() {
        val currentPos = alleganymock
        scope.launch {
            showBufferArea(currentPos, bufferOverlay)

            //  Collect features from all facility layers
            val allFoundFeatures = mutableListOf<ArcGISFeature>()
            map?.operationalLayers?.forEach { Log.d("arcgis", "Available Layers: ${it.name}") }
            map?.operationalLayers?.filterIsInstance<FeatureLayer>()?.forEach { layer ->
                if (layer.name.contains("facility", ignoreCase = true)) {
                    val amenities = queryAmenitiesInRange(currentPos, layer.featureTable!!)
                    layer.selectFeatures(amenities)
                    allFoundFeatures.addAll(amenities)
                }
            }

            // Map features to their distances and filter out null geometries
            val featuresWithDistance = allFoundFeatures.mapNotNull { feature ->
                val featureGeometry = feature.geometry ?: return@mapNotNull null

                // geodeticDistance returns a result containing the distance value
                val distanceResult = GeometryEngine.distanceGeodeticOrNull(
                    point1 = currentPos,
                    point2 = featureGeometry as com.arcgismaps.geometry.Point,
                    distanceUnit = LinearUnit.feet,
                    azimuthUnit = AngularUnit.degrees,
                    curveType = GeodeticCurveType.Geodesic
                )

                // Return a Pair of the Feature and its distance value
                feature to (distanceResult?.distance ?: 0.0)
            }.sortedBy { it.second } // 3. Sort by distance (closest first)

            // Update the total count state
            totalNearbyCount = featuresWithDistance.size

            Log.d("total near by ammenities", "count${totalNearbyCount}")

            // Log the results sorted by distance
            Log.d("Arcgis", "--- Closest Amenities (Total: ${featuresWithDistance.size}) ---")
            featuresWithDistance.forEach { (feature, distance) ->
                val name = feature.attributes["Sub_Asset"] ?: feature.attributes["Name"] ?: "Unknown"
                // Formatting to 2 decimal places
                val formattedDistance = "%.2f".format(distance)
                Log.d("Arcgis", "Feature: $name is $formattedDistance feet away")
            }
        }
    }

    fun findNearbyTrails() {
        val currentPos = alleganymock
        scope.launch {
            Log.d("ArcGIS", "findNearbyTrails: Started")

            val allFoundTrails = mutableListOf<ArcGISFeature>()
            val layers = map?.operationalLayers?.filterIsInstance<FeatureLayer>() ?: emptyList()

            Log.d("ArcGIS", "Searching through ${layers.size} layers for trails")

            layers.forEach { layer ->
                // Using "transportation" or "trails" to match your log: trailsqa_transportationlocal
                if (layer.name.contains("trails", ignoreCase = true) ||
                    layer.name.contains("transportation", ignoreCase = true)) {

                    Log.d("Arcgis", "Targeting Trail Layer: ${layer.name}")
                    val trails = queryAmenitiesInRange(currentPos, layer.featureTable!!)
                    layer.selectFeatures(trails)
                    allFoundTrails.addAll(trails)
                }
            }

            if (allFoundTrails.isEmpty()) {
                Log.d("ArcGIS", "No trails found in the 500m buffer.")
                return@launch
            }

            val trailsWithDistance = allFoundTrails.mapNotNull { trail ->
                val trailGeometry = trail.geometry ?: return@mapNotNull null

                // Find nearest point on the line
                val nearestSearchResult = GeometryEngine.nearestCoordinate(trailGeometry, currentPos)
                val pointOnTrail = nearestSearchResult?.coordinate ?: return@mapNotNull null

                val geodeticResult = GeometryEngine.distanceGeodeticOrNull(
                    point1 = currentPos,
                    point2 = pointOnTrail,
                    distanceUnit = LinearUnit.feet,
                    azimuthUnit = AngularUnit.degrees,
                    curveType = GeodeticCurveType.Geodesic
                )

                val distValue = geodeticResult?.distance ?: Double.MAX_VALUE
                trail to distValue
            }.sortedBy { it.second }

            Log.d("ArcGIS", "--- Sorted Trails (Total: ${trailsWithDistance.size}) ---")
            trailsWithDistance.forEach { (trail, dist) ->
                val trailName = trail.attributes["Trail_Name"] ?: trail.attributes["Name"] ?: "Unknown Trail"
                Log.d("ArcGIS", "Trail Result: $trailName is ${"%.2f".format(dist)} ft away")
            }
        }
    }

    val simulatedSource = remember {
        val params = SimulationParameters(
            startTime = Instant.now(),
            velocity = 1.0,
            horizontalAccuracy = 5.0
        )
        SimulatedLocationDataSource().apply {
            setLocationsWithPolyline(
                com.arcgismaps.geometry.Polyline(listOf(alleganymock)),
                params
            )
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = (result[permission.ACCESS_FINE_LOCATION] == true) ||
                (result[permission.ACCESS_COARSE_LOCATION] == true)
    }

    LaunchedEffect(hasLocationPermission, map) { // Added 'map' as a key
        if (hasLocationPermission && map != null) {
            try {
                locationDisplay.dataSource = simulatedSource
                locationDisplay.dataSource.start()
                locationDisplay.setAutoPanMode(LocationDisplayAutoPanMode.Off)

                Log.d("Arcgis", "Mock Started - Map is ready, triggering searches")

                // Run these in separate coroutines so they don't block each other
                launch { findNearbyAmenities() }
                launch { findNearbyTrails() }

            } catch (e: Exception) {
                Log.e("Arcgis", "Error starting mock: ${e.message}")
            }
        }
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(permission.ACCESS_FINE_LOCATION, permission.ACCESS_COARSE_LOCATION)
        )
    }

    LaunchedEffect(selectedParkName, map, uniqueParkViewPoints) {
        val target = selectedParkName ?: return@LaunchedEffect
        val currentMap = map ?: return@LaunchedEffect
        if (uniqueParkViewPoints.isEmpty()) return@LaunchedEffect
        val match = uniqueParkViewPoints.firstOrNull { it.name.equals(target, ignoreCase = true) } ?: return@LaunchedEffect
        currentMap.operationalLayers.forEach { lyr ->
            val fl = lyr as? FeatureLayer ?: return@forEach
            if (fl.name.contains("parkbound", ignoreCase = true)) {
                fl.isVisible = true
            }
        }
        mapViewProxy.setViewpoint(match.viewpoint)
    }

    LaunchedEffect(mmpkFileName, gdbFileNames) {
        isLoading = true
        runCatching {
            loadOfflineMapAndLayers(
                context = context,
                mmpkFileName = mmpkFileName,
                gdbFileNames = gdbFileNames
            )
        }.onSuccess { result ->
            map = result.map
            uniqueParkViewPoints = result.parkViewpoints
        }.onFailure { e ->
            Log.e("Arcgis", "Error loading: ${e.message}", e)
        }
        isLoading = false
    }


    Box(Modifier.fillMaxSize()) {
        map?.let { arcGISMap ->
            MapView(
                modifier = Modifier.fillMaxSize(),
                arcGISMap = arcGISMap,
                locationDisplay = locationDisplay,
                mapViewProxy = mapViewProxy,
                graphicsOverlays = listOf(bufferOverlay),
                onSingleTapConfirmed = { tapEvent ->
                    scope.launch {
                        try {
                            arcGISMap.operationalLayers.forEach { (it as? FeatureLayer)?.clearSelection() }

                            launch {findNearbyAmenities()}
                            launch {findNearbyTrails()}

                            val results = mapViewProxy.identifyLayers(
                                screenCoordinate = tapEvent.screenCoordinate,
                                tolerance = 12.dp,
                                returnPopupsOnly = false
                            ).getOrThrow()
                            results.forEach { result ->
                                val layer = result.layerContent as? FeatureLayer
                                val layerName = layer?.name ?: ""
                                val firstElement = result.geoElements.firstOrNull() as? ArcGISFeature
                                if (firstElement != null) {
                                    if (!layerName.contains("parkboundriesqa_parkboundries", ignoreCase = true)) {
                                        layer?.selectFeature(firstElement)
                                        Log.d("arcgis", "clicked: ${firstElement.attributes["Trail_Name"]} in $layerName")
                                    }
                                    if (layer != null && layerName.contains("facility", ignoreCase = true)) {
                                        val facilityFilterName = (firstElement.attributes["Facility"] as? String) ?: (firstElement.attributes["Facility_Name"] as? String)
                                        if (!facilityFilterName.isNullOrBlank()) {
                                            lastFacilityQueried = facilityFilterName
                                            facilityCounts = getFeatureCountsBySubAssetClientSide(layer, facilityFilterName)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ArcGIS_Identify", "Identify failed: ${e.message}")
                        }
                    }
                }
            )
        } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(20.dp).zIndex(10f)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = androidx.compose.ui.graphics.Color.Black)
        }

        Button(
            onClick = {
                scope.launch {
                    try {
                        isSyncing = true
                        val syncMap = mapOf(
                            "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/ParkBoundriesQA/FeatureServer" to "parkBoundriesQA.geodatabase",
                            "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/TrailsQA/FeatureServer" to "trailsQA.geodatabase",
                            "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/Facility_FeaturesQA/FeatureServer" to "facility_featuresQA1.geodatabase",
                            "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/HuntingQA/FeatureServer" to "huntingQA.geodatabase",
                        )
                        syncMap.forEach { (serviceUrl, filename) ->
                            val syncTask = GeodatabaseSyncTask(serviceUrl)
                            val gdbFile = File(context.filesDir, filename)
                            val geodatabase = Geodatabase(gdbFile.absolutePath)
                            syncTask.load().getOrThrow()
                            geodatabase.load().getOrThrow()
                            val params = syncTask.createDefaultSyncGeodatabaseParameters(geodatabase).getOrThrow()
                            params.geodatabaseSyncDirection = SyncDirection.Download
                            val syncJob = syncTask.createSyncGeodatabaseJob(params, geodatabase)
                            syncJob.start()
                            syncJob.result().getOrThrow()
                        }
                    } catch (e: Exception) {
                        Log.e("arcgis error", "failed job; ${e.message}")
                    } finally {
                        isSyncing = false
                    }
                }
            },
            enabled = !isSyncing,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).navigationBarsPadding().zIndex(2f)
        ) {
            Text(if (isSyncing) "Syncing..." else "Sync All Databases")
        }
    }
}

private suspend fun getFeatureCountsBySubAssetClientSide(featureLayer: FeatureLayer, facilityName: String): Map<String, Int> {
    val table = featureLayer.featureTable
    val safeFacilityName = facilityName.replace("'", "''")
    val qp = QueryParameters().apply { whereClause = "Facility = '$safeFacilityName'" }
    val result: FeatureQueryResult = table?.queryFeatures(qp)!!.getOrThrow()
    val counts = mutableMapOf<String, Int>()
    for (geoElement in result) {
        val feature = geoElement as? ArcGISFeature ?: continue
        val subAsset = feature.attributes["Sub_Asset"]?.toString()?.trim().orEmpty()
        if (subAsset.isBlank()) continue
        counts[subAsset] = (counts[subAsset] ?: 0) + 1
    }
    return counts
}

fun findNearestFeature(
    fromFeatures: List<ArcGISFeature>,
    toLocation: com.arcgismaps.geometry.Point):
        ArcGISFeature? {
    if (fromFeatures.isEmpty()) return null
    return fromFeatures.minByOrNull { feature ->
        val geom = feature.geometry ?: return@minByOrNull Double.MAX_VALUE
        GeometryEngine.distanceOrNull(geom, toLocation) ?: Double.MAX_VALUE
    }
}

fun getDistanceInMiles(
    fromLocation: com.arcgismaps.geometry.Point,
    toFeature: ArcGISFeature):
        Double {
    val geometry = toFeature.geometry ?: return 0.0
    val distanceMeters = GeometryEngine.distanceOrNull(geometry, fromLocation)
    return distanceMeters?.times(0.000621371) ?: 0.0
}

private suspend fun queryAmenitiesInRange(
    currentLocation: com.arcgismaps.geometry.Point,
    facilityTable: com.arcgismaps.data.FeatureTable,
    bufferDistanceMeters: Double = 500.0):
        List<ArcGISFeature> {
    val mercatorPoint = GeometryEngine.projectOrNull(currentLocation, SpatialReference.webMercator()) as com.arcgismaps.geometry.Point
    val queryBuffer = GeometryEngine.bufferOrNull(mercatorPoint, bufferDistanceMeters)
    val params = QueryParameters().apply {
        geometry = queryBuffer
        spatialRelationship = com.arcgismaps.data.SpatialRelationship.Intersects
    }
    val result = facilityTable.queryFeatures(params).getOrThrow()
    return result.toList().mapNotNull { it as? ArcGISFeature }
}

fun showBufferArea(
    center: com.arcgismaps.geometry.Point,
    overlay: GraphicsOverlay,
    distance: Double = 500.0) {
    val mercatorPoint = GeometryEngine.projectOrNull(center, SpatialReference.webMercator()) as com.arcgismaps.geometry.Point
    val circle = GeometryEngine.bufferOrNull(mercatorPoint, distance)

    val lineSymbol = SimpleLineSymbol().apply {
        style = SimpleLineSymbolStyle.Solid
        color = Color.fromRgba(0, 0, 255, 255)
        width = 1f
    }

    val fillSymbol = SimpleFillSymbol().apply {
        style = SimpleFillSymbolStyle.Solid
        color = Color.fromRgba(0, 0, 255, 51) // 0x33 is 51 in decimal
        outline = lineSymbol
    }

    overlay.graphics.clear()
    circle?.let { overlay.graphics.add(Graphic(it, fillSymbol)) }
}