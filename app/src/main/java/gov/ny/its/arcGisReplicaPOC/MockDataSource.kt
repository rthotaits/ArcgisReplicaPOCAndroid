package gov.ny.its.arcGisReplicaPOC

import com.arcgismaps.location.Location
import com.arcgismaps.location.LocationDataSource
import com.arcgismaps.geometry.Point
import com.arcgismaps.geometry.SpatialReference

//class MockLocationDataSource(
//    private val latitude: Double,
//    private val longitude: Double
//) : LocationDataSource() {
//
//    override suspend fun onStart() {
//        val point = Point(
//            longitude,
//            latitude,
//            SpatialReference.wgs84()
//        )
//
//        val location = android.location.Location(
//            position = point,
//            horizontalAccuracy = 5.0
//        )
//
//        updateLocation(location)
//    }
//
//    override suspend fun onStop() {
//        // no-op
//    }
//}