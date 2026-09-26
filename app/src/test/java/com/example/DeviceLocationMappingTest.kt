package com.example

import android.location.Address
import com.example.data.repository.DeviceLocationClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** Robolectric for a real android.location.Address. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceLocationMappingTest {

    @Test
    fun `maps geocoder address onto create_address fields`() {
        val a = Address(Locale.US).apply {
            subThoroughfare = "12"
            thoroughfare = "MG Road"
            subLocality = "Ashok Nagar"
            locality = "Bengaluru"
            postalCode = "560001"
            setAddressLine(0, "12 MG Road, Ashok Nagar, Bengaluru, Karnataka 560001, India")
        }

        val r = DeviceLocationClient.toResolvedAddress(a, 12.97, 77.59)

        assertEquals("12 MG Road", r.addressLine)
        assertEquals("", r.addressLine2) // flat/floor can't come from GPS — user fills it in
        assertEquals("Ashok Nagar", r.locality)
        assertEquals("Bengaluru", r.city)
        assertEquals("560001", r.postalCode)
        assertEquals("12 MG Road, Ashok Nagar, Bengaluru, Karnataka 560001, India", r.fullAddress)
        assertEquals(12.97, r.latitude, 0.0)
    }

    @Test
    fun `falls back to sub-locality and district when street and city are missing`() {
        val a = Address(Locale.US).apply {
            subLocality = "Koramangala"
            subAdminArea = "Bangalore Urban"
            postalCode = "560034"
        }

        val r = DeviceLocationClient.toResolvedAddress(a, 12.93, 77.62)

        assertEquals("Koramangala", r.addressLine)
        assertEquals("Bangalore Urban", r.city)
        assertEquals("Koramangala, 560034", r.fullAddress)
    }
}
