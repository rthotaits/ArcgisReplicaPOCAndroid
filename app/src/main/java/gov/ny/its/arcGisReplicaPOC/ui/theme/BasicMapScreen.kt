package gov.ny.its.arcGisReplicaPOC.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.PortalItem
import com.arcgismaps.mapping.Viewpoint
import com.arcgismaps.portal.Portal
import com.arcgismaps.toolkit.geoviewcompose.MapView
import gov.ny.its.arcGisReplicaPOC.ui.theme.credentialauth.AuthMode

@Composable
fun BasicMapScreen(
    authMode: AuthMode = AuthMode.OAUTH,
    webMapItemId: String = "2d2acb267d854c45a1a61d76412c678b"
) {
    var map by remember { mutableStateOf<ArcGISMap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Viewpoint(latitude, longitude, scale)
    val initialViewpoint = remember {
        Viewpoint(
            latitude = 42.7128,
            longitude = -76.006,
            scale = 3_207_051.6769789294
        )
    }

    LaunchedEffect(webMapItemId) {
        try {
            val portal = Portal("https://nysgeohub-dev.ny.gov/ext/", Portal.Connection.Authenticated)
            val portalItem = PortalItem(portal, webMapItemId)
            val enterpriseMap = ArcGISMap(portalItem)

            // This single load() call will trigger the Authenticator
            // if the Portal or Item is private.
            enterpriseMap.load().getOrThrow()

            map = enterpriseMap
        } catch (e: Exception) {
            error = e.message
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            error != null -> Text("Failed to load map: $error")
            map == null -> CircularProgressIndicator()
            else -> MapView(
                modifier = Modifier.fillMaxSize(),
                arcGISMap = map!!
            )
        }
    }
}
