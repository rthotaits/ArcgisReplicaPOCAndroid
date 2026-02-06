package gov.ny.its.arcGisReplicaPOC.ui.theme.oauth2

import android.util.Log
import com.arcgismaps.ArcGISEnvironment
import com.arcgismaps.httpcore.authentication.OAuthUserConfiguration
import com.arcgismaps.toolkit.authentication.AuthenticatorState

object OAuthInitializer {

    private const val TAG = "ArcGIS_OAUTH"
    private const val PORTAL_URL = "https://nysgeohub-dev.ny.gov/ext/"
    private const val CLIENT_ID = "4BeJLWhyoOewfv8x"

    private const val REDIRECT_URL = "my-ags-app://auth"

    val authenticator: AuthenticatorState by lazy {
        val oAuthConfiguration = OAuthUserConfiguration(
            portalUrl = PORTAL_URL,
            clientId = CLIENT_ID,
            redirectUrl = REDIRECT_URL
        )
        AuthenticatorState().apply {
            oAuthUserConfiguration = oAuthConfiguration
        }
    }

    suspend fun enableOAuth() {
        Log.d(TAG, "Enabling OAuth challenge handler")
        ArcGISEnvironment.authenticationManager.arcGISAuthenticationChallengeHandler = authenticator

        runCatching {
            ArcGISEnvironment.authenticationManager.arcGISCredentialStore
            Log.d(TAG, "Persistent credential storage enabled")
        }.onFailure {
            Log.w(TAG, "Persistent credential storage failed: ${it.message}")
        }
    }
}