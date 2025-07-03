package com.example.trabajointegradornativo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.app.ActivityCompat

class LocationHelper(private val context: Context) {

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    interface LocationCallback {
        fun onLocationReceived(latitude: Double, longitude: Double, address: String)
        fun onLocationError(error: String)
    }

    // Obtiene la ubicación actual del usuario y retorna la dirección mediante el callback.
    fun getCurrentLocation(callback: LocationCallback) {
        if (!hasLocationPermission()) {
            callback.onLocationError(context.getString(R.string.location_permissions_denied))
            return
        }

        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (!isGPSEnabled()) {
            callback.onLocationError(context.getString(R.string.gps_disabled_enable_settings))
            return
        }

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val latitude = location.latitude
                val longitude = location.longitude

                val address = getAddressFromLocation(latitude, longitude)

                stopLocationUpdates()

                callback.onLocationReceived(latitude, longitude, address)
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                callback.onLocationError(context.getString(R.string.location_provider_disabled))
            }

            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        }

        try {
            val lastKnownLocation =
                locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastKnownLocation != null) {
                val address =
                    getAddressFromLocation(lastKnownLocation.latitude, lastKnownLocation.longitude)
                callback.onLocationReceived(
                    lastKnownLocation.latitude,
                    lastKnownLocation.longitude,
                    address
                )
                return
            }

            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000,
                10f,
                locationListener!!
            )

        } catch (e: SecurityException) {
            callback.onLocationError(context.getString(R.string.permission_error_format, e.message))
        }
    }

    // Verifica si la app tiene permisos de ubicación.
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    // Verifica si el GPS está habilitado en el dispositivo.
    private fun isGPSEnabled(): Boolean {
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
    }

    // Convierte las coordenadas de latitud y longitud en una dirección legible.
    private fun getAddressFromLocation(latitude: Double, longitude: Double): String {
        return try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0]
                "${address.getAddressLine(0)}"
            } else {
                context.getString(R.string.lat_lng_format, latitude, longitude)
            }
        } catch (e: Exception) {
            context.getString(R.string.lat_lng_format, latitude, longitude)
        }
    }

    // Detiene las actualizaciones de ubicación y libera el listener.
    fun stopLocationUpdates() {
        locationListener?.let { listener ->
            locationManager?.removeUpdates(listener)
        }
        locationListener = null
    }
}