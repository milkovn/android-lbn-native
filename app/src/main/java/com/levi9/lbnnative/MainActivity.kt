package com.levi9.lbnnative

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.OnTokenCanceledListener
import com.levi9.lbnnative.receiver.GeofenceBroadcastReceiver
import com.levi9.lbnnative.receiver.GeofenceBroadcastReceiver.Companion.NOTIFICATION_CHANNEL_ID
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private var geofenceList = mutableListOf<Geofence>()

    private lateinit var geofencingClient: GeofencingClient

    private lateinit var geofencingRequest: GeofencingRequest

    private lateinit var pendingIntent: PendingIntent

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()

        //#################################################
        // 1. fetching geofence client from the OS
        //#################################################
        geofencingClient = LocationServices.getGeofencingClient(this)

        //#################################################
        // 2. creating geofence list
        //#################################################
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

        //#################################################
        // 3. creating geofencing request
        //#################################################
        geofencingRequest = retrieveGeofencingRequest()

        //#################################################
        // 4. creating pending intent with BroadcastReceiver
        //#################################################
        pendingIntent = getGeofencePendingIntent()

        //#################################################
        // 5. final step, add geofence list to geofencing client
        //#################################################
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("===", "Permission not granted")
            return
        }
        geofencingClient.addGeofences(geofencingRequest, pendingIntent).run {
            addOnSuccessListener {
                Log.e("===", "Geofence list registered successfully")
            }
            addOnFailureListener {
                Log.e("===", "Failed to add geofence list")
            }
        }
    }

    private fun retrieveGeofencingRequest(): GeofencingRequest {
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofenceList)
        }.build()
    }

    private fun getGeofencePendingIntent(): PendingIntent {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = NOTIFICATION_CHANNEL_ID
            val descriptionText = "displays lbn notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
