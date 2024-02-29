package com.levi9.lbnnative

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.levi9.lbnnative.databinding.ActivityMainBinding
import com.levi9.lbnnative.receiver.GeofenceBroadcastReceiver
import com.levi9.lbnnative.receiver.GeofenceBroadcastReceiver.Companion.NOTIFICATION_CHANNEL_DESCRIPTION
import com.levi9.lbnnative.receiver.GeofenceBroadcastReceiver.Companion.NOTIFICATION_CHANNEL_ID


class MainActivity : AppCompatActivity() {

    private var geofenceList = mutableListOf<Geofence>()

    private lateinit var geofencingClient: GeofencingClient

    private lateinit var geofencingRequest: GeofencingRequest

    private lateinit var pendingIntent: PendingIntent

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var binding: ActivityMainBinding

    private lateinit var locationRequest: LocationRequest

    private lateinit var locationCallback: LocationCallback

    private val requestFineLocationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    requestBackgroundLocationPermissionLauncher.launch(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    )
                } else {
                    startListeningForGeoFences()
                }
            }
        }

    private val requestBackgroundLocationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                startListeningForGeoFences()
            }
        }

    private val requestPostNotificationsPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                handleLocationPermission()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // in order for notifications to be sent from the app and appear to the user, we need
        // a notification channel when targeting Android 8.0 (API 26) and above
        createNotificationChannel()

        // retrieve fused location client which contains Location APIs used for location tracking
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // retrieve geofencing client, the main entry point for interacting with geofencing
        geofencingClient = LocationServices.getGeofencingClient(this)

        // create a list of tracked geofences
        // each Geofence has:
        // id,
        // circular region (lat, long, radius),
        // expiration duration,
        // loitering time (time user has to stay in geofence before receiving a DWELL event),
        // notification responsiveness (time after which user is notified when condition is met - enter, exit, dwell) (However, setting a very small responsiveness value, for example 5 seconds, doesn't necessarily mean you will get notified right after the user enters or exits a geofence: internally, the geofence might adjust the responsiveness value to save power when needed. Customer reported it as an issue)
        // transition type (enter, exit dwell)
        fillGeofenceList()

        // create geofencing request which connects initial trigger and list of tracked geofences
        geofencingRequest = retrieveGeofencingRequest()
        // create pending intent which activates broadcast receiver when trigger happens
        pendingIntent = getGeofencePendingIntent()

        // apps targeting Android 13 (API 33) or above require GRANTED notification runtime permission
        // * if notification permission condition is fulfilled, request location permission
        // * if notification and location permissions are GRANTED, we are registering for geofence event
        //   listening in method startListeningForGeoFences()
        handleNotificationsPermission()

        // for demo purposes we are listening for location updates
        handleLocationUpdates()
    }

    private fun handleLocationUpdates() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).apply {
            setMinUpdateDistanceMeters(10F)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val lat = location.latitude.toString()
                    val lon = location.longitude.toString()
                    Toast.makeText(applicationContext, "$lat - $lon", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startListeningForGeoFences() {
        geofencingClient.addGeofences(geofencingRequest, pendingIntent).run {
            addOnSuccessListener {
                Log.e("===", "Geofence list registered successfully")
            }
            addOnFailureListener {
                Log.e("===", "Failed to add geofence list")
            }
        }
    }

    private fun fillGeofenceList() {
        geofenceList.add(
            Geofence.Builder()
                .setRequestId("geofenceID1")
                .setCircularRegion(
                    45.2662652,
                    19.8453745,
                    200F
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        )
    }

    private fun retrieveGeofencingRequest(): GeofencingRequest {
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofenceList)
        }.build()
    }

    private fun getGeofencePendingIntent(): PendingIntent {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = NOTIFICATION_CHANNEL_ID
            val descriptionText = NOTIFICATION_CHANNEL_DESCRIPTION
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun handleNotificationsPermission() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) -> {
                handleLocationPermission()
            }

            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPostNotificationsPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            }
        }
    }

    private fun handleLocationPermission() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    requestBackgroundLocationPermissionLauncher.launch(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    )
                } else {
                    startListeningForGeoFences()
                }
            }

            else -> {
                requestFineLocationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
