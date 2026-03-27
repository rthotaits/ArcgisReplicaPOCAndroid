package gov.ny.its.arcGisReplicaPOC.ui.theme.credentialauth

import android.util.Log
import com.arcgismaps.ArcGISEnvironment
import com.arcgismaps.httpcore.authentication.TokenCredential

object TokenInitializer {

    private const val TAG = "ArcGIS_INIT"
    private const val TOKEN_URL = "https://nysparks.maps.arcgis.com/"

    suspend fun initialize() {
       // Log.d(TAG, "token initialization")

        val credential = TokenCredential.Companion.create(
            url = TOKEN_URL,
            username = "NYS.Parks.Explorer",
            password = "gD!Duz).8ff&VDV",
            tokenExpirationInterval = 5
        ).getOrElse { e ->
            Log.e(TAG, "TokenCredential.create failed", e)
            throw e
        }
        ArcGISEnvironment.authenticationManager.arcGISCredentialStore.add(credential)
    }
}