package com.prasadjadhav.smartlocationtrackerdemo

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.GeofencingEvent
import com.prasadjadhav.smartlocationtracking.LocationService
import com.prasadjadhav.smartlocationtracking.SmartLocationTracker
import com.prasadjadhav.smartlocationtracking.onGeofenceTriggerCallback
import com.prasadjadhav.smartlocationtracking.onLocationUpdateCallback
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), View.OnClickListener {

    lateinit var smartLocationTracker: SmartLocationTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btn_req_permissions.setOnClickListener(this)
        btn_fetch_location_single.setOnClickListener(this)
        btn_add_geofence.setOnClickListener(this)
        btn_start_location_tracking_smart.setOnClickListener(this)
        btn_start_location_tracking.setOnClickListener(this)
        btn_stop_location_tracking.setOnClickListener(this)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            createNotificationChanel()
        }
        var notification = getForegroundNotification()
        smartLocationTracker = SmartLocationTracker().init(this@MainActivity, notification)!!
        addOptionalNotifications()

    }

    override fun onResume() {
        super.onResume()
        checkIfHasAllPermissions()
    }

    private fun checkIfHasAllPermissions() {
        ll_buttons.visibility = GONE
        btn_req_permissions.visibility = VISIBLE
        var permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }

        var result: Int
        val listPermissionsNeeded: MutableList<String> = ArrayList()
        for (p in permissions) {
            result = ContextCompat.checkSelfPermission(this, p)
            if (result != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p)
            }
        }
        if (listPermissionsNeeded.isEmpty()) {
            ll_buttons.visibility = VISIBLE
            btn_req_permissions.visibility = GONE
        } else {
            btn_req_permissions.setText("Request permissions (" + listPermissionsNeeded.size + ")")
        }
    }

    private fun addOptionalNotifications() {
        var notificationNoInternet = getNotificationNoInternet()
        var notificationNoGPS = getNotificationNoGPS()
        smartLocationTracker.setNoInternetNotification(notificationNoInternet)
        smartLocationTracker.notificationNoGPS(notificationNoGPS)
    }

    override fun onClick(v: View?) {
        var viewId = v!!.id
        if (viewId == R.id.btn_req_permissions) {
            requestLocationPermissions()
        } else if (viewId == R.id.btn_fetch_location_single) {
            requestSingleLocationUpdate()
        } else if (viewId == R.id.btn_add_geofence) {
            addGeofence()
        } else if (viewId == R.id.btn_start_location_tracking_smart) {
            startLocationUpdates(true)
        } else if (viewId == R.id.btn_start_location_tracking) {
            startLocationUpdates(false)
        } else if (viewId == R.id.btn_stop_location_tracking) {
            stopLocationUpdates()
        }
    }


    private fun requestLocationPermissions() {
        smartLocationTracker.requestPermissions()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        smartLocationTracker.receivedPermissions()
    }

    private fun requestSingleLocationUpdate() {
        smartLocationTracker.requestSingleLocationUpdate(object : onLocationUpdateCallback {
            override fun onLocationUpdate(
                lat: Double,
                lon: Double,
                hasInternet: Boolean,
                hasGPS: Boolean
            ) {
                Log.d("singleLocationUpdate", "location update : " + lat)
                LocationService.writeToAFileUnhandled("Location update single- " + LocationService.latitudeCurrent + " - " + LocationService.longitudeCurrent)

            }
        })
    }


    private fun startLocationUpdates(trackOnlyWhenMoving: Boolean) {
        smartLocationTracker.startLocationTracking(trackOnlyWhenMoving)
        smartLocationTracker.onLocationUpdate(object : onLocationUpdateCallback {
            override fun onLocationUpdate(
                lat: Double,
                lon: Double,
                hasInternet: Boolean,
                hasGPS: Boolean
            ) {
                Log.d("onLocationUpdate", "location update : " + lat)
                LocationService.writeToAFileUnhandled("Location update - " + LocationService.latitudeCurrent + " - " + LocationService.longitudeCurrent)
            }
        })

    }

    private fun addGeofence() {

        smartLocationTracker.addGeofence("key_1", 19.292050, 73.202514, 1000, object :
            onGeofenceTriggerCallback {
            override fun onGeofenceTriggered(event: GeofencingEvent) {
                for (geofence in event.triggeringGeofences!!) {
                    var id = geofence.requestId
                    Log.e("latLongObjectx1", "triggered - " + id.toString())
                }

            }
        })
    }

    private fun stopLocationUpdates() {
        smartLocationTracker.stopLocationTracking(true)
    }

    val NOTIFICATION_CHANNEL_ID = "com.getlocationbackground"
    val channelName = "Smart Location tracking Service"

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChanel() {

        val chan = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        )
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(chan)


    }

    private fun getForegroundNotification(): Notification {
        val notificationBuilder =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)


        return notificationBuilder.setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentText("Tracking only when you move")
            .setContentTitle("Smart location tracking")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun getNotificationNoInternet(): Notification {
        val notificationBuilder =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        /*val intent = Intent(Intent.ACTION_MAIN)
        intent.setAction(android.provider.Settings.ACTION_SETTINGS)
        intent.setClassName("com.android.phone", "com.android.phone.NetworkSetting")
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);*/
        val intent =
            Intent(Settings.ACTION_WIRELESS_SETTINGS) //android.provider.Settings.ACTION_SETTINGS //Intent.ACTION_MAIN

        intent.setClassName(
            "com.android.settings",
            "com.android.settings.Settings\$DataUsageSummaryActivity"
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                6235,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        return notificationBuilder.setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentText("No internet")
            .setContentIntent(pendingIntent)
            .setContentTitle("Smart location tracking")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun getNotificationNoGPS(): Notification {
        val notificationBuilder =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)

        val intent =
            Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)


        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                6235,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        return notificationBuilder.setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentText("No GPS")
            .setContentIntent(pendingIntent)
            .setContentTitle("Smart location tracking")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }


}