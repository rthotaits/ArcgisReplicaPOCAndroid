package gov.ny.its.arcGisReplicaPOC.ui.theme.node

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import gov.ny.its.arcGisReplicaPOC.ui.theme.UniqueParkViewPoint

@Composable
fun AppNavRoot(parks: List<UniqueParkViewPoint>) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "parks") {
        composable("parks") {
            ParksListScreen(
                parks = parks,
                onParkClick = { park ->
                    val encoded = Uri.encode(park.name)
                    navController.navigate("map/$encoded")
                }
            )
        }

        composable(
            route = "map/{parkName}",
            arguments = listOf(navArgument("parkName") { type = NavType.StringType })
        ) { backStackEntry ->
            val parkName = backStackEntry.arguments?.getString("parkName")
            ParkMap(selectedParkName = parkName,
                onBack = {navController.popBackStack()})
        }
    }
}