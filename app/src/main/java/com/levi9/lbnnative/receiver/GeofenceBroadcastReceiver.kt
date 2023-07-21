package com.levi9.lbnnative.receiver

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER
import com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.levi9.lbnnative.R
import java.util.Random

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.e("===", "Triggered!")
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent != null && geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e("===", "Error on triggering event: $errorMessage")
            return
        }

        //#################################################
        // 1. get transition type
        //#################################################
        val geofenceTransition = geofencingEvent?.geofenceTransition

        //#################################################
        // 2. take into account transitions of interest
        //#################################################
        if (geofenceTransition == GEOFENCE_TRANSITION_ENTER
            || geofenceTransition == GEOFENCE_TRANSITION_EXIT
        ) {

            //#################################################
            // 3. get triggered geofence list
            //#################################################
            val triggeringGeofenceList = geofencingEvent.triggeringGeofences

            // Send notification and log the transition details.
            if (geofenceTransition == GEOFENCE_TRANSITION_ENTER) {
                sendNotification("enter", context)
            } else {
                sendNotification("exit", context)
            }
        }
    }

    private fun sendNotification(notificationDetails: String, context: Context) {
        val notificationIntent = Intent(Intent.ACTION_VIEW)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_MUTABLE
        )
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        builder.setColor(Notification.COLOR_DEFAULT)
            .setContentTitle(notificationDetails)
            .setContentText("Click notification to remove")
            .setContentIntent(pendingIntent)
            .setDefaults(Notification.DEFAULT_SOUND)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
        val notificationManager = NotificationManagerCompat.from(context)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("===", "Permission not granted")
            return
        }
        Log.e("===", "Permission granted")
        notificationManager.notify(Random().nextInt(), builder.build())
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "Lbn notifications"
        const val NOTIFICATION_CHANNEL_DESCRIPTION = "Displays lbn notifications"
    }
}
