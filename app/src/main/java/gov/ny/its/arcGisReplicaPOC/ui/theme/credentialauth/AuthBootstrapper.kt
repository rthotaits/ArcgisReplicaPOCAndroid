package gov.ny.its.arcGisReplicaPOC.ui.theme.credentialauth

import android.util.Log
import com.arcgismaps.ArcGISEnvironment
import gov.ny.its.arcGisReplicaPOC.ui.theme.oauth2.OAuthInitializer

enum class AuthMode {
    TOKEN_CREDENTIAL,
    OAUTH
}
object AuthBootstrapper {
    private const val TAG = "ArcGIS_BOOT"

    suspend fun initialize(mode: AuthMode) {
        Log.d(TAG, "Initializing auth mode=$mode")

        ArcGISEnvironment.authenticationManager.arcGISAuthenticationChallengeHandler = null

        when (mode) {
            AuthMode.TOKEN_CREDENTIAL -> {
                TokenInitializer.initialize()
                Log.d(TAG, "Token auth initialized")
            }

            AuthMode.OAUTH -> {
                OAuthInitializer.enableOAuth()
                Log.d(TAG, "OAuth auth initialized")
            }
        }
    }
}