package gov.ny.its.arcGisReplicaPOC.ui.theme.node

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gov.ny.its.arcGisReplicaPOC.ui.theme.UniqueParkViewPoint
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.arcgismaps.data.ArcGISFeature
import com.arcgismaps.data.Geodatabase
import com.arcgismaps.data.QueryParameters
import com.arcgismaps.geometry.Geometry
import com.arcgismaps.geometry.GeometryEngine
import com.arcgismaps.mapping.MobileMapPackage
import com.arcgismaps.mapping.Viewpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AppNavRoot() {
    LoadParksOnce { parks ->
        AppNavRoot(parks = parks)
    }
}

/**
 * Your first screen (parks list).
 */
@Composable
fun ParksListScreen(
    parks: List<UniqueParkViewPoint>,
    onParkClick: (UniqueParkViewPoint) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(parks, key = { it.name }) { park ->
                Log.d("count of parks", "parkscount: ${parks.size}")
                Log.d("list of parks####", "parks list: $park.name")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable { onParkClick(park) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        text = park.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Loads park viewpoints once (progress + error handling).
 */
@Composable
fun LoadParksOnce(
    mmpkFileName: String = "nys_offline.mmpk",
    parkGdbFileName: String = "parkBoundriesQA.geodatabase",
    content: @Composable (parks: List<UniqueParkViewPoint>) -> Unit
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var parks by remember { mutableStateOf<List<UniqueParkViewPoint>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

//    LaunchedEffect(Unit) {
//        withContext(Dispatchers.IO) {
//            try {
//                Log.d("PARKS_LOAD", " Checking for MMPK and GDB files in internal storage")
//                // ensure mmpk exists (some workflows require it even if we only use gdb here)
//                val mmpkFile = File(context.filesDir, mmpkFileName)
//                if (!mmpkFile.exists()) {
//                    Log.d("PARKS_LOAD", "-> Copying $mmpkFileName from assets...")
//                    context.assets.open(mmpkFileName).use { it.copyTo(mmpkFile.outputStream()) }
//                }
//                MobileMapPackage(mmpkFile.absolutePath).load().getOrThrow()
//
//                // ensure park gdb exists
//                val gdbFile = File(context.filesDir, parkGdbFileName)
//                if (!gdbFile.exists()) {
//                    Log.d("PARKS_LOAD", "-> Copying $parkGdbFileName from assets...")
//                    context.assets.open(parkGdbFileName).use { it.copyTo(gdbFile.outputStream()) }
//                }
//
//                //establish database connection - laod geodatabase
//                Log.d("PARKS_LOAD", "opening Geodatabase connection")
//                val gdb = Geodatabase(gdbFile.absolutePath)
//                gdb.load().getOrThrow()
//
//                //grab the park boundaries
//                val table = gdb.featureTables.firstOrNull()
//                    ?: error("No feature tables in $parkGdbFileName")
//                Log.d("results table","results: ${table.tableName}")
//
//                val params = QueryParameters().apply {
//                    whereClause = "Category not in ('COE' , 'Other', 'Conservation Easement', ' ')"
//                }
//                Log.d("PARKS_LOAD", "Executing Query: ${params.whereClause}")
//
//                val result = table.queryFeatures(params).getOrThrow()
//                val features: List<ArcGISFeature> = result.toList().mapNotNull { it as? ArcGISFeature }
//                Log.d("features result#####", "Query complete. Found ${features.count()} valid feature records.")
//
//                val grouped = features.groupBy { val it1 = it
//                   it1.attributes["label"]?.toString().orEmpty() }
//                Log.d("results", "Grouped into ${grouped.size} unique Park Labels")
//
//                Log.d("PARKS_LOAD", "calculating Viewpoints (Merging Geometries)")
//                val temp = grouped.entries.mapNotNull { entry ->
//                    val name = entry.key
//                    if (name.isBlank()) return@mapNotNull null
//                    val geometries: List<Geometry> = entry.value.mapNotNull { it.geometry }
//                    if (geometries.isEmpty()) return@mapNotNull null
//
//                    //merge all polygon chunks in to one boundary
//                    val merged: Geometry? = geometries.drop(1).fold(geometries.first()) { acc, g ->
//                        GeometryEngine.union(acc, g) ?: acc
//                    }
//
//                    val extent = merged?.extent ?: return@mapNotNull null
//
//                    UniqueParkViewPoint(name, Viewpoint(extent))
//                }.sortedBy { it.name }
//
//                //push to UI
//                withContext(Dispatchers.Main) {
//                    Log.d("PARKS_LOAD", "Loading complete. Pushing ${temp.size} parks to UI list.")
//                    parks = temp
//                    error = null
//                    loading = false
//                }
//            } catch (e: Exception) {
//                Log.e("PARKS_LOAD", "Failed", e)
//                withContext(Dispatchers.Main) {
//                    error = e.message ?: "Unknown error"
//                    loading = false
//                }
//            }
//        }
//    }

    LaunchedEffect(Unit) {
        loading = true
        withContext(Dispatchers.Main) { error = null }

        runCatching {
            loadOfflineMapAndLayers(
                context = context,
                mmpkFileName = mmpkFileName,
                gdbFileNames = listOf(parkGdbFileName)
            )
        }.onSuccess { result ->
            parks = result.parkViewpoints
            loading = false
        }.onFailure { e ->
            error = e.message ?: "Unknown error"
            loading = false
        }
    }


    Box(Modifier.fillMaxSize()) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            error != null -> Text(
                text = "Failed to load parks: $error",
                modifier = Modifier.align(Alignment.Center).padding(16.dp)
            )
            else -> content(parks)
        }
        Log.d("parks list display", "parks list${parks.size}")
    }
}