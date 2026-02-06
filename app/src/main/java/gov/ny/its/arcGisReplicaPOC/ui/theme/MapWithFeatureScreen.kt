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
import com.arcgismaps.LoadStatus
import com.arcgismaps.data.ServiceFeatureTable
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.MobileMapPackage
import com.arcgismaps.mapping.layers.FeatureLayer
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
        try {
            val internalFile = File(context.filesDir, mmpkFileName)

            if (!internalFile.exists()) {
                    context.assets.open(mmpkFileName).use {it.copyTo(internalFile.outputStream())}
                }


            // Load the Mobile Map Package
            val mmpk = MobileMapPackage(internalFile.absolutePath)
            mmpk.load().getOrThrow()

            val localMap = mmpk.maps.first()
            localMap.load().getOrThrow()

            // Set the map to the UI
            map = localMap

            isLoadingRemoteLayers = true

            //  API call - getting layer URLs from GeoHub
            val remoteLayerUrls = listOf(
                "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/Facility_FeaturesQA/FeatureServer/0"
            )

            remoteLayerUrls.forEach { url ->
                val serviceTable = ServiceFeatureTable(url)
                val featureLayer = FeatureLayer.createWithFeatureTable(serviceTable)

                // Add to the existing local map
                localMap.operationalLayers.add(featureLayer)


                // Optional: load the layer to ensure it's valid
                featureLayer.load().onSuccess {
                    Log.d("ArcGIS", "Successfully loaded: ${featureLayer.name}")
                }.onFailure { error ->
                    Log.e("ArcGIS", "Failed to load layer: ${error.message}")
                }

            }
        } catch (e: Exception) {
            Log.e("ArcGIS", "Error: ${e.message}")
        } finally {
            isLoadingRemoteLayers = false
        }
    }

    /*featureLayer.addDoneLoadingListener {
        if (featureLayer.loadStatus.value == LoadStatus.Loaded) {
            // Successfully loaded! Log the specific name from the service
            Log.d("ArcGIS", "Loaded Feature Layer: ${featureLayer.name}")
        } else {
            val error = featureLayer.loadError?.message ?: "Unknown error"
            Log.e("ArcGIS", "Failed to load layer at $url: $error")
        }
    }
}
} catch (e: Exception) {
    Log.e("ArcGIS", "Error in setup: ${e.message}")
} finally {
    isLoadingRemoteLayers = false
}
*/
    Box(Modifier.fillMaxSize()) {
        map?.let {
            MapView(modifier = Modifier.fillMaxSize(), arcGISMap = it)
        } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))

        if (isLoadingRemoteLayers) {
            Text("Syncing live layers...",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 50.dp))
        }
    }
}