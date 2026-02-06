package gov.ny.its.arcGisReplicaPOC

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arcgismaps.toolkit.authentication.Authenticator
import gov.ny.its.arcGisReplicaPOC.ui.theme.credentialauth.AuthBootstrapper
import gov.ny.its.arcGisReplicaPOC.ui.theme.credentialauth.AuthMode
import gov.ny.its.arcGisReplicaPOC.ui.theme.node.AppNavRoot
import gov.ny.its.arcGisReplicaPOC.ui.theme.oauth2.OAuthInitializer

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AppRoot(authMode = AuthMode.TOKEN_CREDENTIAL)
            }
        }
    }
}

@Composable
fun AppRoot(authMode: AuthMode) {
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authMode) {
        ready = false
        error = null
        runCatching {
            AuthBootstrapper.initialize(authMode)
        }.onSuccess {
            ready = true
        }.onFailure {
            error = it.message ?: it.toString()
        }
    }

    Authenticator(authenticatorState = OAuthInitializer.authenticator)

    Box(Modifier.fillMaxSize()
        ) {
        when {
            error != null -> Text("Auth failed: $error", modifier = Modifier.padding(16.dp))
            !ready -> CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
          //  else -> MapWithFeatureScreen3(authMode)
            else -> AppNavRoot()
        }
    }
}