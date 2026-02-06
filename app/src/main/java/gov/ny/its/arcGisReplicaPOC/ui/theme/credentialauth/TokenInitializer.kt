package gov.ny.its.arcGisReplicaPOC.ui.theme.credentialauth

import android.util.Log
import com.arcgismaps.ArcGISEnvironment
import com.arcgismaps.httpcore.authentication.TokenCredential

object TokenInitializer {

    private const val TAG = "ArcGIS_INIT"
    private const val TOKEN_URL = "https://nysgeohub-dev.ny.gov/ext/"

    suspend fun initialize() {
       // Log.d(TAG, "token initialization")

        val credential = TokenCredential.Companion.create(
            url = TOKEN_URL,
            username = "mobile.user",
            password = "WxNvamM5nORRZoa6W1uO",
            tokenExpirationInterval = 5
        ).getOrElse { e ->
            Log.e(TAG, "TokenCredential.create failed", e)
            throw e
        }
        ArcGISEnvironment.authenticationManager.arcGISCredentialStore.add(credential)
    }
}