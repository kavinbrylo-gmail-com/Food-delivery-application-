package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class GpsFix(
  val lat: Double,
  val lng: Double,
  val streetHint: String?,
  val cityHint: String?,
)

object LocationHelper {

  fun hasLocationPermission(context: Context): Boolean {
    val fine =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  @SuppressLint("MissingPermission")
  suspend fun fetchCurrentGps(context: Context): Result<GpsFix> {
    if (!hasLocationPermission(context)) {
      return Result.failure(SecurityException("Location permission not granted"))
    }
    val client = LocationServices.getFusedLocationProviderClient(context)
    val tokenSource = CancellationTokenSource()

    val location =
      suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { tokenSource.cancel() }
        client
          .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
          .addOnSuccessListener { loc ->
            if (loc != null) {
              cont.resume(loc)
            } else {
              client.lastLocation
                .addOnSuccessListener { last -> cont.resume(last) }
                .addOnFailureListener { cont.resume(null) }
            }
          }
          .addOnFailureListener {
            client.lastLocation
              .addOnSuccessListener { last -> cont.resume(last) }
              .addOnFailureListener { cont.resume(null) }
          }
      }

    if (location == null) {
      return Result.failure(IllegalStateException("Unable to acquire GPS fix. Check device location services."))
    }

    val (street, city) = reverseGeocode(context, location.latitude, location.longitude)
    return Result.success(
      GpsFix(
        lat = location.latitude,
        lng = location.longitude,
        streetHint = street,
        cityHint = city,
      )
    )
  }

  private suspend fun reverseGeocode(
    context: Context,
    lat: Double,
    lng: Double,
  ): Pair<String?, String?> =
    withContext(Dispatchers.IO) {
      if (!Geocoder.isPresent()) return@withContext null to null
      try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          suspendCancellableCoroutine { cont ->
            geocoder.getFromLocation(lat, lng, 1) { addresses -> cont.resume(addresses) }
          }
        } else {
          geocoder.getFromLocation(lat, lng, 1)
        }
        val first = list?.firstOrNull() ?: return@withContext null to null
        val street = listOfNotNull(first.subThoroughfare, first.thoroughfare).joinToString(" ").ifBlank {
          first.featureName
        }
        val city = listOfNotNull(first.locality, first.adminArea).joinToString(", ").ifBlank { null }
        street to city
      } catch (_: Exception) {
        null to null
      }
    }
}
