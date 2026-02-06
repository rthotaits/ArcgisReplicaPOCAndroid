package gov.ny.its.arcGisReplicaPOC.ui.theme

import android.util.Log
import android.Manifest.permission
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arcgismaps.tasks.geodatabase.SyncDirection
import com.arcgismaps.LoadStatus
import com.arcgismaps.arcgisservices.ArcGISFeatureServiceInfo
import com.arcgismaps.data.ArcGISFeature
import com.arcgismaps.data.Geodatabase
import com.arcgismaps.data.ServiceFeatureTable
import com.arcgismaps.data.ServiceGeodatabase
import com.arcgismaps.geometry.GeometryType
import com.arcgismaps.geometry.PointBuilder
import com.arcgismaps.geometry.SpatialReference
import com.arcgismaps.location.IndoorPositioningDataOrigin
import com.arcgismaps.location.Location
import com.arcgismaps.location.LocationDisplayAutoPanMode
import com.arcgismaps.location.SimulatedLocationDataSource
import com.arcgismaps.location.SimulationParameters
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.MobileMapPackage
import com.arcgismaps.mapping.layers.FeatureLayer
import com.arcgismaps.tasks.geodatabase.GeodatabaseSyncTask
import com.arcgismaps.toolkit.geoviewcompose.MapView
import com.arcgismaps.toolkit.geoviewcompose.rememberLocationDisplay
import gov.ny.its.arcGisReplicaPOC.ui.theme.credentialauth.AuthMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.jar.Manifest


@Composable
fun MapWithFeatureScreen3(
    authMode: AuthMode = AuthMode.TOKEN_CREDENTIAL,
    mmpkFileName: String = "nys_offline.mmpk",
    gdbFileNames: List<String> = listOf(
        "parkBoundriesQA.geodatabase",
        "trailsQA.geodatabase",
        "facility_featuresQA1.geodatabase",
        "huntingQA.geodatabase"
    )
) {

//state variables
    val mapViewProxy = remember { com.arcgismaps.toolkit.geoviewcompose.MapViewProxy() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // for async identification
    var map by remember { mutableStateOf<ArcGISMap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    var hasLocationPermission by remember { mutableStateOf(false) }

    val locationDisplay = rememberLocationDisplay()

    val simulatedSource = remember {
        val mockPoint = com.arcgismaps.geometry.Point(-78.782489, 41.999191, SpatialReference.wgs84())
        val params = SimulationParameters(
            startTime = Instant.now(),
            velocity = 1.0,
            horizontalAccuracy = 5.0
        )

        SimulatedLocationDataSource().apply {
            setLocationsWithPolyline(
                com.arcgismaps.geometry.Polyline(listOf(mockPoint)),
                params
            )
        }
    }

//    LaunchedEffect(Unit) {
//        locationDisplay.setAutoPanMode(com.arcgismaps.location.LocationDisplayAutoPanMode.Recenter)
//    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission =
            (result[permission.ACCESS_FINE_LOCATION] == true) ||
                    (result[permission.ACCESS_COARSE_LOCATION] == true)
    }


    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {

                locationDisplay.dataSource = simulatedSource

                locationDisplay.dataSource.start()
                locationDisplay.setAutoPanMode(LocationDisplayAutoPanMode.Recenter)

                Log.d("Arcgis", "Mock Started")
            } catch (e: Exception) {
                Log.e("Arcgis", "Error starting mock: ${e.message}")
            }
        }
    }

    LaunchedEffect(Unit) {

        locationPermissionLauncher.launch(
            arrayOf(
                permission.ACCESS_FINE_LOCATION,
                permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                isLoading = true


                val mmpkFile = File(context.filesDir, mmpkFileName)
                if (!mmpkFile.exists()) {
                    context.assets.open(mmpkFileName).use { it.copyTo(mmpkFile.outputStream()) }
                }
                val mmpk = MobileMapPackage(mmpkFile.absolutePath)
                mmpk.load().getOrThrow()
                val localMap = mmpk.maps.first()
                localMap.load().getOrThrow()

                gdbFileNames.forEachIndexed { index, gdbName ->
                    val gdbFile = File(context.filesDir, gdbName)
                    if (!gdbFile.exists()) {
                        context.assets.open(gdbName).use { it.copyTo(gdbFile.outputStream()) }
                    }

                    val geodatabase = com.arcgismaps.data.Geodatabase(gdbFile.absolutePath)
                    geodatabase.load().getOrThrow()

                    geodatabase.featureTables.forEach { table ->
                        val featureLayer = FeatureLayer.createWithFeatureTable(table)


                        if (gdbName.contains("hunting", ignoreCase = true) ||
                            gdbName.contains("parkBoundries", ignoreCase = true)) {
                            featureLayer.isVisible = false
                        }

                        localMap.operationalLayers.add(featureLayer)
                        featureLayer.refreshInterval = 1000

                        featureLayer.load().getOrThrow()
                    }
                }

                withContext(Dispatchers.Main) { map = localMap }
            } catch (e: Exception) {
                Log.e("ArcGIS", "Error loading: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        map?.let { arcGISMap ->
            MapView(
                modifier = Modifier.fillMaxSize(),
                arcGISMap = arcGISMap,
                locationDisplay = locationDisplay,

                mapViewProxy = mapViewProxy,
                onSingleTapConfirmed = { tapEvent ->
                    scope.launch {
                        try {

                            arcGISMap.operationalLayers.forEach { layer ->
                                (layer as? FeatureLayer)?.clearSelection()
                            }

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


                                        val trailName = firstElement.attributes["Trail_Name"] ?: "Unknown Trail"
                                        Log.d("arcgis", "clicked: $trailName in $layerName")

                                        val facilityName = firstElement.attributes["Facility_Name"] ?: "N/A"
                                        Log.d("arcgis facility" ,"clicked: $facilityName | $layerName")

                                        Log.d("ArcGIS_Identify", "Trail: $trailName | Layer: $layerName | Facility: $facilityName")

                                        firstElement.attributes.forEach { (key, value) ->
                                            Log.d("arcgis", "   $key: $value")
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

        Button(
            onClick = {
                scope.launch {
                    Log.d("Arcgis", "Sync button clicked")
                    try {
                        isSyncing = true
//                        val featureServiceURL =
//                            "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/Facility_FeaturesQA/FeatureServer"
                        val syncMap = mapOf(
                            "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/ParkBoundriesQA/FeatureServer" to "parkBoundriesQA.geodatabase",
                            "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/TrailsQA/FeatureServer" to "trailsQA.geodatabase",
                            "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/Facility_FeaturesQA/FeatureServer" to "facility_featuresQA1.geodatabase",
                            "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/HuntingQA/FeatureServer" to "huntingQA.geodatabase",
                        )
                        syncMap.forEach { (serviceUrl, filename) ->
                            Log.d("Arcgis", "Syncing: $filename")
                            val syncTask = GeodatabaseSyncTask(serviceUrl)
                            val gdbFile = File(context.filesDir, filename)
                            val geodatabase = Geodatabase(gdbFile.absolutePath)

                            syncTask.load().getOrThrow()
                            geodatabase.load().getOrThrow()

                            val params = syncTask.createDefaultSyncGeodatabaseParameters(geodatabase).getOrThrow()
                            params.geodatabaseSyncDirection = SyncDirection.Download

                            val syncJob = syncTask.createSyncGeodatabaseJob(params, geodatabase)
                            syncJob.start()

                            val result = syncJob.result().getOrThrow()
                            Log.d("Arcgis", "synced successfully $filename: ${result.size} elements")
                        }
                        Log.d("Arcgis", "All databases synced successfully")
                      //  val syncTask = GeodatabaseSyncTask(featureServiceURL)
                       // syncTask.load().getOrThrow()

                        // val params = syncTask.createDefaultSyncGeodatabaseParameters(geodatabase)
                        //  .getOrThrow()

                        //  params.geodatabaseSyncDirection = com.arcgismaps.tasks.geodatabase.SyncDirection.Download

                        //params.geodatabaseSyncDirection = GeodatabaseSyncDirection.Download
                        // val syncJob = syncTask.createSyncGeodatabaseJob(params, geodatabase)
                        // val syncJob = syncTask.syncGeodatabase(params, geodatabase)
                        //  syncJob.start()

                        //  val syncResult = syncJob.result().getOrThrow()
                        //  Log.d("arcgis", "Sync is successfull: $syncResult")

                        val gdbFile = File(context.filesDir, "facility_featuresQA1.geodatabase")
                        val geodatabase = Geodatabase(gdbFile.absolutePath)
                        geodatabase.load().getOrThrow()



                    } catch (e: Exception) {
                        Log.e("arcgis error block", "failed job; ${e.message}")

                    } finally {
                        isSyncing = false
                    }
                }
            },
            enabled = !isSyncing,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            if (isSyncing) {
                Text("Syncing...")
            }
            Text("Sync All Databases")
        }
    }
}