package com.levi9.lbnnative

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import com.levi9.lbnnative.databinding.ActivityMainBinding
import com.levi9.lbnnative.receiver.GeofenceBroadcastReceiver
import com.levi9.lbnnative.receiver.GeofenceBroadcastReceiver.Companion.NOTIFICATION_CHANNEL_DESCRIPTION
import com.levi9.lbnnative.receiver.GeofenceBroadcastReceiver.Companion.NOTIFICATION_CHANNEL_ID
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private var geofenceList = mutableListOf<Geofence>()

    private lateinit var geofencingClient: GeofencingClient

    private lateinit var geofencingRequest: GeofencingRequest

    private lateinit var pendingIntent: PendingIntent

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var binding: ActivityMainBinding

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

        createNotificationChannel()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)

        fillGeofenceList()

        geofencingRequest = retrieveGeofencingRequest()
        pendingIntent = getGeofencePendingIntent()

        handleNotificationsPermission()
    }

    @SuppressLint("MissingPermission")
    private fun startListeningForGeoFences() {
        geofencingClient.addGeofences(geofencingRequest, pendingIntent).run {
            addOnSuccessListener {
                Log.e("===", "Geofence list registered successfully")
                setElementClickListener()
            }
            addOnFailureListener {
                Log.e("===", "Failed to add geofence list")
            }
        }
    }

    private fun fillGeofenceList() {
        geofenceList.add(
            Geofence.Builder()
                .setRequestId(UUID.randomUUID().toString())
                .setCircularRegion(
                    45.2569469,
                    19.844466,
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

    @SuppressLint("MissingPermission")
    private fun setElementClickListener() {
        binding.tvGetLocation.setOnClickListener {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                object : CancellationToken() {
                    override fun onCanceledRequested(p0: OnTokenCanceledListener) =
                        CancellationTokenSource().token

                    override fun isCancellationRequested() = false
                })
                .addOnSuccessListener { location: Location? ->
                    if (location == null)
                        Toast.makeText(
                            this@MainActivity,
                            "Cannot get location.",
                            Toast.LENGTH_SHORT
                        ).show()
                    else {
                        Toast.makeText(this@MainActivity, "Fetched location.", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
        }
    }
}
