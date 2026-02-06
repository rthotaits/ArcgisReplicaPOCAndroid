package gov.ny.its.arcGisReplicaPOC.ui.theme.node

import android.content.Context
import android.util.Log
import com.arcgismaps.data.ArcGISFeature
import com.arcgismaps.data.Geodatabase
import com.arcgismaps.data.QueryParameters
import com.arcgismaps.geometry.Geometry
import com.arcgismaps.geometry.GeometryEngine
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.MobileMapPackage
import com.arcgismaps.mapping.Viewpoint
import com.arcgismaps.mapping.layers.FeatureLayer
import kotlinx.coroutines.withTimeout
import com.arcgismaps.mapping.symbology.Renderer
import gov.ny.its.arcGisReplicaPOC.ui.theme.UniqueParkViewPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class OfflineMapResult(
    val map: ArcGISMap,
    val parkViewpoints: List<UniqueParkViewPoint>
)

suspend fun loadOfflineMapAndLayers(
    context: Context,
    mmpkFileName: String,
    gdbFileNames: List<String>,
    hideLayersIfNameContains: List<String> = listOf("hunting", "parkboundries"),
    tag: String = "OFFLINE_LOAD"
): OfflineMapResult = withContext(Dispatchers.IO) {

    // Ensure MMPK exists in filesDir
    val mmpkFile = File(context.filesDir, mmpkFileName)
    if (!mmpkFile.exists()) {
        Log.d(tag, "Copying $mmpkFileName from assets")
        context.assets.open(mmpkFileName).use { it.copyTo(mmpkFile.outputStream()) }
    }

    // Load MMPK + base map
    val mmpk = MobileMapPackage(mmpkFile.absolutePath)
    mmpk.load().getOrThrow()

    val localMap = mmpk.maps.firstOrNull()
        ?: error("No maps found inside $mmpkFileName")

    localMap.load().getOrThrow()

    // Load GDBs and add layers
    var parkViewpoints: List<UniqueParkViewPoint> = emptyList()

    gdbFileNames.forEach { gdbName ->
        val gdbFile = File(context.filesDir, gdbName)
        if (!gdbFile.exists()) {
            Log.d(tag, "Copying $gdbName from assets")
            context.assets.open(gdbName).use { it.copyTo(gdbFile.outputStream()) }
        }

        val geodatabase = Geodatabase(gdbFile.absolutePath)
        geodatabase.load().getOrThrow()

        geodatabase.featureTables.forEach { table ->

            // Build park viewpoints once from the parkBoundries gdb
            if (gdbName.contains("parkboundries", ignoreCase = true)) {
                val params = QueryParameters().apply {
                    whereClause = "Category not in ('COE' , 'Other', 'Conservation Easement', ' ')"
                }

                val result = table.queryFeatures(params).getOrThrow()
                val features = result.toList().mapNotNull { it as? ArcGISFeature }

                val grouped = features.groupBy { it.attributes["label"]?.toString().orEmpty() }

                parkViewpoints = grouped.entries.mapNotNull { entry ->
                    val name = entry.key
                    if (name.isBlank()) return@mapNotNull null

                    val geometries: List<Geometry> = entry.value.mapNotNull { it.geometry }
                    if (geometries.isEmpty()) return@mapNotNull null

                    val merged: Geometry? = geometries.drop(1).fold(geometries.first()) { acc, g ->
                        GeometryEngine.union(acc, g) ?: acc
                    }

                    val extent = merged?.extent ?: return@mapNotNull null
                    UniqueParkViewPoint(name, Viewpoint(extent))
                }.sortedBy { it.name }

                Log.d(tag, "Computed park viewpoints: ${parkViewpoints.size}")
            }

            if (gdbName.contains("trailsQA", ignoreCase = true)) {
                val blaze1Layer = FeatureLayer.createWithFeatureTable(table)
                blaze1Layer.renderer = TrailRendererHelper.create("Blaze", true)
                localMap.operationalLayers.add(blaze1Layer)

                val blaze2Layer = blaze1Layer.clone()

                blaze2Layer.definitionExpression = "Blaze_2 IS NOT NULL AND Blaze_2 != '' AND Blaze_2 != ' '"
                blaze2Layer.renderer = TrailRendererHelper.create("Blaze_2", false)

                localMap.operationalLayers.add(blaze2Layer)

                blaze1Layer.load().getOrThrow()
                blaze2Layer.load().getOrThrow()

                return@forEach
            }

            //  Create layer
            val featureLayer = FeatureLayer.createWithFeatureTable(table)

            // Hide certain layers by default
            val shouldHide = hideLayersIfNameContains.any { key ->
                gdbName.contains(key, ignoreCase = true) || table.tableName.contains(key, ignoreCase = true)
            }
            if (shouldHide) featureLayer.isVisible = false

            localMap.operationalLayers.add(featureLayer)
            featureLayer.load().getOrThrow()
        }
    }

    OfflineMapResult(map = localMap, parkViewpoints = parkViewpoints)
}
