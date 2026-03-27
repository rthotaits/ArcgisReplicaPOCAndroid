package gov.ny.its.arcGisReplicaPOC.ui.theme

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arcgismaps.data.QueryParameters
import com.arcgismaps.data.ServiceFeatureTable
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.MobileMapPackage
import com.arcgismaps.mapping.layers.FeatureLayer
import com.arcgismaps.mapping.symbology.PictureMarkerSymbol
import com.arcgismaps.mapping.symbology.SimpleRenderer
import com.arcgismaps.toolkit.geoviewcompose.MapView
import gov.ny.its.arcGisReplicaPOC.ui.theme.credentialauth.AuthMode
import java.io.File

@Composable
fun MapWithFeatureScreen(
    authMode: AuthMode = AuthMode.TOKEN_CREDENTIAL,
    mmpkFileName: String = "nys_offline.mmpk"
) {
    val context = LocalContext.current
    var map by remember { mutableStateOf<ArcGISMap?>(null) }
    var isLoadingRemoteLayers by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoadingRemoteLayers = true
        try {
            val internalFile = File(context.filesDir, mmpkFileName)
            if (!internalFile.exists()) {
                context.assets.open(mmpkFileName).use { it.copyTo(internalFile.outputStream()) }
            }

            // Load MMPK + map
            val mmpk = MobileMapPackage(internalFile.absolutePath)
            mmpk.load().getOrThrow()

            val localMap = mmpk.maps.first()
            localMap.load().getOrThrow()

            map = localMap


//            val layerUrl =
//                "https://services.arcgis.com/1xFZPtKn1wKC6POA/arcgis/rest/services/ParkEntrance/FeatureServer/0"
            val layerUrl = "https://services.arcgis.com/1xFZPtKn1wKC6POA/arcgis/rest/services/NY_State_Park_Facilities/FeatureServer/0"

            Log.d("ArcGIS", "Adding remote layer: $layerUrl")

            val serviceTable = ServiceFeatureTable(layerUrl)
            serviceTable.load().getOrThrow()

            Log.d("Arcgis", "ServiceFeatureTable loaded")
            Log.d("Arcgis", " - tableName: ${serviceTable.tableName}")
            Log.d("Arcgis", " - objectIdField: ${serviceTable.objectIdField}")
            Log.d("Arcgis", " - geometryType: ${serviceTable.geometryType}")
            Log.d("Arcgis", " - hasAttachments: ${serviceTable.hasAttachments}")

            // Log fields
            serviceTable.fields.forEach { field ->
                Log.d(
                    "Arcgis",
                    "Field: name=${field.name}, alias=${field.alias}, type=${field.fieldType}, nullable=${field.nullable}"
                )
            }

            val featureLayer = FeatureLayer.createWithFeatureTable(serviceTable).apply {
                name = serviceTable.tableName.ifBlank { "Facility" }
            }
            localMap.operationalLayers.add(featureLayer)

            featureLayer.load().getOrThrow()

            Log.d("Arcgis", "FeatureLayer loaded")
            Log.d("Arcgis", " - name: ${featureLayer.name}")
            Log.d("Arcgis", " - minScale: ${featureLayer.minScale}")
            Log.d("Arcgis", " - maxScale: ${featureLayer.maxScale}")
            Log.d("Arcgis", " - renderer: ${featureLayer.renderer}")
            val renderer = featureLayer.renderer
            Log.d("Arcgis", "Renderer class: ${renderer}")
            if (renderer is SimpleRenderer) {
                val symbol = renderer.symbol
                Log.d("Arcgis", "Symbol class: ${symbol}")
            }

            if (renderer is SimpleRenderer) {
                val symbol = renderer.symbol

                if (symbol is PictureMarkerSymbol) {
                    Log.d("Arcgis", "PictureMarkerSymbol detected")
                    Log.d("Arcgis", " - width: ${symbol.width}")
                    Log.d("Arcgis", " - height: ${symbol.height}")
                    Log.d("Arcgis", " - angle: ${symbol.angle}")
                    Log.d("Arcgis", " - image data size: ${symbol.image?.isVisible}")
                    Log.d("Arcgis", " - uri: ${symbol.url}")
                }
            }

            // Query
            val query = QueryParameters().apply {
                whereClause = "1=1"
                resultOffset = 10
            }

            val result = serviceTable.queryFeatures(query).getOrThrow()

            Log.d("ArcGIS", "Query returned ${result.count()} features (showing up to 10)")
            result.forEachIndexed { index, feature ->
                val attrs = feature.attributes.entries.joinToString(
                    prefix = "{", postfix = "}"
                ) { (k, v) -> "$k=$v" }

              //  Log.d("ArcGIS", "Feature[$index] attrs=$attrs")
              //  Log.d("ArcGIS", "Feature[$index] geometry=${feature.geometry}")
            }

        } catch (e: Exception) {
            Log.e("ArcGIS", "Error loading map/layer: ${e.message}", e)
        } finally {
            isLoadingRemoteLayers = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        map?.let {
            MapView(
                modifier = Modifier.fillMaxSize(),
                arcGISMap = it
            )
        } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))

        if (isLoadingRemoteLayers) {
            Text(
                "Syncing live layers...",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 50.dp)
            )
        }
    }
}
