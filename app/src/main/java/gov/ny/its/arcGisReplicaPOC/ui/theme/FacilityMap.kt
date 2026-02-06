package gov.ny.its.arcGisReplicaPOC.ui.theme

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arcgismaps.tasks.geodatabase.SyncDirection
import com.arcgismaps.LoadStatus
import com.arcgismaps.arcgisservices.ArcGISFeatureServiceInfo
import com.arcgismaps.data.ArcGISFeature
import com.arcgismaps.data.Geodatabase
import com.arcgismaps.data.QueryParameters
import com.arcgismaps.data.ServiceFeatureTable
import com.arcgismaps.data.ServiceGeodatabase
import com.arcgismaps.data.StatisticDefinition
import com.arcgismaps.geometry.Geometry
import com.arcgismaps.geometry.GeometryEngine
import com.arcgismaps.data.FeatureQueryResult
import com.arcgismaps.location.IndoorPositioningDataOrigin
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.MobileMapPackage
import com.arcgismaps.mapping.Viewpoint
import com.arcgismaps.mapping.layers.FeatureLayer
import com.arcgismaps.tasks.geodatabase.GeodatabaseSyncTask
import com.arcgismaps.toolkit.geoviewcompose.MapView
import com.arcgismaps.toolkit.geoviewcompose.MapViewProxy
import gov.ny.its.arcGisReplicaPOC.ui.theme.credentialauth.AuthMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Holds the unique park name and viewpint
//data class UniqueParkViewPoint(val name: String, val viewpoint: Viewpoint)

// 1. load offline map 2. Load local geo databases (parks boundaries, trails, facilities, huntiing 3. Builds featureLayers from the geodatabases 4. computes unique park-level viewpoints by merging park boundaries
//5. supports identity on tap adn full geodatabase sync
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacilityMap(
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
    val mapViewProxy = remember { MapViewProxy() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // Required for async identification
    var map by remember { mutableStateOf<ArcGISMap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    var selectedPark by remember { mutableStateOf<UniqueParkViewPoint?>(null) }
    var parksExpanded by remember { mutableStateOf(false) }

    var facilityCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var lastFacilityQueried by remember { mutableStateOf<String?>(null) }

    //list of unique park viewpoints
    var uniqueParkViewPoints by remember { mutableStateOf<List<UniqueParkViewPoint>>(emptyList()) }

    //corute scope to run once - loads offline map, loads all geodatabases, create feature layer, compute park boundaries
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {

                //Load offline map
                val mmpkFile = File(context.filesDir, mmpkFileName)
                if (!mmpkFile.exists()) {
                    context.assets.open(mmpkFileName).use { it.copyTo(mmpkFile.outputStream()) }
                }
                val mmpk = MobileMapPackage(mmpkFile.absolutePath)
                mmpk.load().getOrThrow()
                val localMap = mmpk.maps.first()
                localMap.load().getOrThrow()

                //load GeoDatabases
                gdbFileNames.forEachIndexed { index, gdbName ->
                    val gdbFile = File(context.filesDir, gdbName)
                    if (!gdbFile.exists()) {
                        //copy geodatabase on first run
                        context.assets.open(gdbName).use { it.copyTo(gdbFile.outputStream()) }
                    }

                    //loads each geodatabases
                    val geodatabase = Geodatabase(gdbFile.absolutePath)
                    geodatabase.load().getOrThrow()

                    // process each feature table
                    geodatabase.featureTables.forEach { table ->
                        //park boundaries -filter categories, group feature by park lable, merge all geometries for park, create viewpoint per park
                        if(gdbName.contains("parkBoundries", ignoreCase = true)){
                            val params = QueryParameters().apply {
                                whereClause = "Category not in ('COE' , 'Other', 'Conservation Easement')"
                            }
                            val result = table.queryFeatures(params).getOrThrow()
                            //val features = result.toList()
                            val features: List<ArcGISFeature> = result.toList().mapNotNull { it as? ArcGISFeature }
                            Log.d("Arcgis", "Queried ${features.size} park boundary features")

                            // Group features by "label" attribute
                           // val grouped = features.groupBy { it.attributes["label"]?.toString() ?: "" }

                            val grouped: Map<String, List<ArcGISFeature>> =
                                features.groupBy { it.attributes["label"]?.toString().orEmpty() }

                            // Merge geometries and create viewpoints
                           // val tempViewPoints = grouped.mapNotNull { (name, items) ->
                                //val geometries = items.mapNotNull { it.geometry }
                               // val tempViewPoint = grouped.mapNotNull { (name, items) ->
                            val tempViewPoints: List<UniqueParkViewPoint> =
                                grouped.entries.mapNotNull { entry ->
                                    val name: String = entry.key
                                    val items: List<ArcGISFeature> = entry.value

                                // val geometries: List<com.arcgismaps.geometry.Geometry> = items.mapNotNull { it.geometry }
                                val geometries: List<Geometry> = items.mapNotNull { it.geometry }
                                if (geometries.isEmpty()) return@mapNotNull null

                                val merged: Geometry? = geometries.drop(1).fold(geometries.first()) { acc, g ->
                                        GeometryEngine.union(acc, g) ?: acc
                                    }
                                    val extent = merged?.extent ?: return@mapNotNull null
                                    UniqueParkViewPoint(name, Viewpoint(extent))
//                                merged?.extent?.let { extent ->
//                                    UniqueParkViewPoint(name, Viewpoint(extent))
//                                }
                            }.sortedBy { it.name }

                                /*if (geometries.isNotEmpty()) {
                                    // GeometryEngine.union merges multiple parts into one
                                    //val merged = GeometryEngine.union(geometries)
                                    val merged = com.arcgismaps.geometry.GeometryEngine.union(geometries)
                                    merged?.let {
                                        UniqueParkViewPoint(name, Viewpoint(it.extent))
                                    }
                                } else null*/
                           // }.sortedBy { it.name }

                            withContext(Dispatchers.Main) {
                                uniqueParkViewPoints = tempViewPoints
                                Log.d("ArcGIS", "unique view points ${uniqueParkViewPoints.size}")
                                uniqueParkViewPoints.forEachIndexed { index, vp ->
                                    Log.d(
                                        "unique parkviewpoints",
                                        "$index -> ${vp.name} | Extent=${vp.viewpoint.targetGeometry?.extent}"
                                    )
                                }
                            }
                        }
                        val featureLayer = FeatureLayer.createWithFeatureTable(table)


                        if (gdbName.contains("hunting", ignoreCase = true) ||
                            gdbName.contains("parkBoundries", ignoreCase = true)) {
                            featureLayer.isVisible = false
                        }

                        localMap.operationalLayers.add(featureLayer)
                      //  featureLayer.refreshInterval = 1000

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

        //park dropdown
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .fillMaxWidth()
                .zIndex(2f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
        ) {
            ExposedDropdownMenuBox(
                expanded = parksExpanded,
                onExpandedChange = {parksExpanded = !parksExpanded}
            ) {
                TextField(
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    readOnly =  true,
                    value = selectedPark?.name ?: "",
                    onValueChange = {},
                    label = { Text("Select Park")},
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = parksExpanded)
                    },
                    colors = ExposedDropdownMenuDefaults.textFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = parksExpanded,
                    onDismissRequest = {parksExpanded = false}
                ) {
                    uniqueParkViewPoints.forEach { park ->
                        DropdownMenuItem(
                            text = {Text(park.name)},
                            onClick = {
                                selectedPark = park
                                parksExpanded = false

                                scope.launch {
                                    map?.operationalLayers?.forEach { layer ->
                                        val fl = layer as? FeatureLayer ?: return@forEach
                                        if(fl.name.contains("parkboundaries", ignoreCase = true)){
                                            fl.isVisible = true
                                        }
                                    }
                                    mapViewProxy.setViewpoint(park.viewpoint)
                                }
                            }
                        )
                    }
                }
            }
        }
        //map display
        map?.let { arcGISMap ->
            MapView(
                modifier = Modifier.fillMaxSize(),
                arcGISMap = arcGISMap,

                mapViewProxy = mapViewProxy,
                onSingleTapConfirmed = { tapEvent ->
                    scope.launch {
                        try {

                            arcGISMap.operationalLayers.forEach { layer ->
                                (layer as? FeatureLayer)?.clearSelection()
                            }

                            // Identify tapped features accross operational layers
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

                                    if (layer != null && layerName.contains("facility", ignoreCase = true)) {

                                        val facilityFilterName =
                                            (firstElement.attributes["Facility"] as? String)
                                                ?: (firstElement.attributes["Facility_Name"] as? String)

                                        if (!facilityFilterName.isNullOrBlank()) {
                                            lastFacilityQueried = facilityFilterName

                                            val counts = getFeatureCountsBySubAssetClientSide(
                                                featureLayer = layer,
                                                facilityName = facilityFilterName
                                            )

                                            facilityCounts = counts

                                            Log.d("ArcGIS_STATS", "Counts for facility=$facilityFilterName")
                                            counts.toList().sortedByDescending { it.second }.forEach { (subAsset, cnt) ->
                                                Log.d("ArcGIS_STATS", "  $subAsset -> $cnt")
                                            }
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

        // sync all databases
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

                        val gdbFile = File(context.filesDir, "facility_featuresQA.geodatabase")
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
                .zIndex(2f)
        ) {
            Text(if (isSyncing) "Syncing..." else "Sync All Databases")
        }
    }
}

private suspend fun getFeatureCountsBySubAssetClientSide(
    featureLayer: FeatureLayer,
    facilityName: String
): Map<String, Int> {

    val table = featureLayer.featureTable

    val safeFacilityName = facilityName.replace("'", "''")

    val qp = QueryParameters().apply {
        whereClause = "Facility = '$safeFacilityName'"
    }

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