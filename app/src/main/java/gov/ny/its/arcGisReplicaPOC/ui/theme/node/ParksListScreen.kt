package gov.ny.its.arcGisReplicaPOC.ui.theme.node

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gov.ny.its.arcGisReplicaPOC.ui.theme.UniqueParkViewPoint
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppNavRoot() {
    LoadParksOnce { parks ->
        AppNavRoot(parks = parks)
    }
}

/**
 * first screen (parks list).
 */
@Composable
fun ParksListScreen(
    parks: List<UniqueParkViewPoint>,
    onParkClick: (UniqueParkViewPoint) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Count of Parks: ${parks.size}",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(parks, key = { it.name }) { park ->
                    Log.d("count of parks", "parkscount: ${parks.size}")
                    Log.d("list of parks####", "parks list: $park.name")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable { onParkClick(park) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = park.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Loads park viewpoints once (progress + error handling).
 */
@Composable
fun LoadParksOnce(
    mmpkFileName: String = "nys_offline.mmpk",
    parkGdbFileName: String = "boundaryQA.geodatabase",
    content: @Composable (parks: List<UniqueParkViewPoint>) -> Unit
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var parks by remember { mutableStateOf<List<UniqueParkViewPoint>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        withContext(Dispatchers.Main) { error = null }

        runCatching {
            loadOfflineMapAndLayers(
                context = context,
                mmpkFileName = mmpkFileName,
                gdbFileNames = listOf(parkGdbFileName)
            )
        }.onSuccess { result ->
            parks = result.parkViewpoints
            loading = false
        }.onFailure { e ->
            error = e.message ?: "Unknown error"
            loading = false
        }
    }


    Box(Modifier.fillMaxSize()) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            error != null -> Text(
                text = "Failed to load parks: $error",
                modifier = Modifier.align(Alignment.Center).padding(16.dp)
            )
            else -> content(parks)
        }
        Log.d("parks list display", "parks list${parks.size}")
    }
}