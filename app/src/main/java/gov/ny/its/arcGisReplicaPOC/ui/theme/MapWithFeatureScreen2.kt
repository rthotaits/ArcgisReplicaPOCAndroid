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
import com.arcgismaps.arcgisservices.ArcGISFeatureServiceInfo
import com.arcgismaps.data.ArcGISFeature
import com.arcgismaps.data.ServiceFeatureTable
import com.arcgismaps.data.ServiceGeodatabase
import com.arcgismaps.location.IndoorPositioningDataOrigin
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.MobileMapPackage
import com.arcgismaps.mapping.layers.FeatureLayer
import com.arcgismaps.toolkit.geoviewcompose.MapView
import gov.ny.its.arcGisReplicaPOC.ui.theme.credentialauth.AuthMode
import java.io.File


@Composable
fun MapWithFeatureScreen2(
    authMode: AuthMode = AuthMode.TOKEN_CREDENTIAL,
    mmpkFileName: String = "nys_offline.mmpk"
) {
    val context = LocalContext.current
    var map by remember { mutableStateOf<ArcGISMap?>(null) }
    var isLoadingRemoteLayers by remember { mutableStateOf(false) }

    var isSyncing by remember { mutableStateOf(false) }
    val featureServiceUrl = "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/Facility_FeaturesQA/FeatureServer"

    LaunchedEffect(Unit) {
        try {

            val internalFile = File(context.filesDir, mmpkFileName)
            if (!internalFile.exists()) {
                context.assets.open(mmpkFileName).use { it.copyTo(internalFile.outputStream()) }
            }

            val mmpk = MobileMapPackage(internalFile.absolutePath)
            mmpk.load().getOrThrow()
            val localMap = mmpk.maps.first()
            localMap.load().getOrThrow()
            map = localMap

            isLoadingRemoteLayers = true
            val remoteServiceUrls = listOf(
                "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/Facility_FeaturesQA/FeatureServer",
                "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/TrailsQA/FeatureServer",
                "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/HuntingQA/FeatureServer",
                "https://nysgeohub-dev.ny.gov/host/rest/services/Hosted/ParkBoundriesQA/FeatureServer",
            )

            remoteServiceUrls.forEach { serviceUrl ->

                val serviceGeodatabase = ServiceGeodatabase(serviceUrl)
                serviceGeodatabase.load().getOrThrow()


                val layerInfos = serviceGeodatabase.serviceInfo?.layerInfos

                layerInfos?.forEach { layerInfo ->

                    val subLayerUrl = "$serviceUrl/${layerInfo.id}"


                    val serviceTable = ServiceFeatureTable(subLayerUrl)
                    val featureLayer = FeatureLayer.createWithFeatureTable(serviceTable)


                    localMap.operationalLayers.add(featureLayer)


                    featureLayer.load().getOrThrow()

                    Log.d("ArcGIS", "Successfully added layer: ${featureLayer.name} (ID: ${layerInfo.id})")
                }
            }
        } catch (e: Exception) {
            Log.e("ArcGIS", "Error: ${e.message}", e)
        } finally {
            isLoadingRemoteLayers = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        map?.let {
            MapView(modifier = Modifier.fillMaxSize(), arcGISMap = it)
        } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))

        if (isLoadingRemoteLayers) {
            Text(
                "Syncing live layers...",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 50.dp)
            )
        }
    }
}