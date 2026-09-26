package com.example.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import com.example.data.model.ResolvedAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Current device location → street address, using only platform APIs (LocationManager +
 * Geocoder) so no Play Services dependency is needed. Callers must hold a location permission.
 * Every entry point fails closed to null, matching the rest of the app's optional-feature design.
 */
object DeviceLocationClient {
    private const val TAG = "DeviceLocationClient"
    private const val FIX_TIMEOUT_MS = 15_000L

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        val provider = candidates.firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return lastKnown(lm)

        return try {
            withTimeoutOrNull(FIX_TIMEOUT_MS) { freshFix(context, lm, provider) } ?: lastKnown(lm)
        } catch (e: Exception) {
            Log.w(TAG, "Location fix failed", e)
            lastKnown(lm)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(context: Context, lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } else {
                // All four callbacks implemented: pre-API-29 LocationListener has no default methods.
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? =
        lm.allProviders
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): ResolvedAddress? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(FIX_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }
                            override fun onError(errorMessage: String?) {
                                Log.w(TAG, "Geocoder error: $errorMessage")
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed", e)
            null
        }
        return address?.let { toResolvedAddress(it, latitude, longitude) }
    }

    /** Maps Android's Address onto create_address's required fields; blanks stay blank for the user to fill in. */
    internal fun toResolvedAddress(a: Address, latitude: Double, longitude: Double): ResolvedAddress {
        val street = listOfNotNull(a.subThoroughfare, a.thoroughfare)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { a.featureName.orEmpty() }
        val full = (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it) }.joinToString(", ")
        return ResolvedAddress(
            fullAddress = full.ifBlank { listOf(street, a.subLocality, a.locality, a.postalCode).filterNot { it.isNullOrBlank() }.joinToString(", ") },
            addressLine = street.ifBlank { a.subLocality.orEmpty() },
            addressLine2 = "",
            locality = a.subLocality.orEmpty(),
            city = a.locality ?: a.subAdminArea.orEmpty(),
            postalCode = a.postalCode.orEmpty(),
            latitude = latitude,
            longitude = longitude
        )
    }
}
