package com.prasadjadhav.smartlocationtracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.tasks.Task

class SmartLocationTracker {
    companion object {
        var smartLocationTracker: SmartLocationTracker? = null
        var task: Task<Void>? = null
        var activityContext: AppCompatActivity? = null
        private val PERMISSION_REQUEST_ACTIVITY_RECOGNITION = 45
        var mActivityRecognitionClient: ActivityRecognitionClient? = null
        var mLocationService: LocationService = LocationService()

        lateinit var mIntentService: Intent
        var useActivityRecognition = true
        lateinit var foregroundServiceNotif: Notification

        lateinit var mPendingIntent: PendingIntent
        val MY_PREFS_NAME = "prasadjadhavSmartLocation"


        fun writePref(key: String?, value: Long?, context: Context) {
            val editor = context.getSharedPreferences(
                MY_PREFS_NAME,
                Application.MODE_PRIVATE
            ).edit()
            editor.putLong(key, value!!)
            editor.apply()
        }

        fun readPref(key: String?, context: Context): Long? {
            val prefs = context.getSharedPreferences(
                MY_PREFS_NAME,
                Application.MODE_PRIVATE
            )
            return prefs.getLong(key, 12345678910L)
        }
    }

    fun init(activity: AppCompatActivity, notification: Notification): SmartLocationTracker? {
        activityContext = activity
        foregroundServiceNotif = notification
        mActivityRecognitionClient = ActivityRecognitionClient(activityContext as Context)

        mIntentService = Intent(activityContext, DetectedActivitiesIntentService::class.java)
        mPendingIntent = PendingIntent.getService(
            activityContext,
            5632,
            mIntentService!!,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        smartLocationTracker = this

//        activityContext!!.startService(Intent(activityContext, LocationService::class.java))
        mLocationService =
            LocationService().getInstance(activityContext!!, smartLocationTracker!!)!!
        return smartLocationTracker
    }


    var permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACTIVITY_RECOGNITION


    )


    fun requestPermissions() {

        if (hasContext()) {

            var result: Int
            val listPermissionsNeeded: MutableList<String> = ArrayList()
            for (p in permissions) {
                result = ContextCompat.checkSelfPermission(activityContext!!, p)
                if (result != PackageManager.PERMISSION_GRANTED) {
                    listPermissionsNeeded.add(p)
                }
            }
            if (!listPermissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(
                    activityContext!!,
                    listPermissionsNeeded.toTypedArray(),
                    5258
                )

            }


        }
    }

    private fun toast(msg: String) {
        if (hasContext() && BuildConfig.DEBUG) {
            Toast.makeText(activityContext, "Debug toast - " + msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun hasContext(): Boolean {
        if (activityContext == null) {
            throw RuntimeException("SmartLocationTracker not initialized")
        }
        return true
    }


    @SuppressLint("MissingPermission")
    fun startActivityTracking() {
        if (task != null) {
            mActivityRecognitionClient!!.removeActivityTransitionUpdates(
                mPendingIntent
            )
        }

        task = mActivityRecognitionClient!!.requestActivityUpdates(
            (30 * 1000).toLong(),
            mPendingIntent
        )

        task!!.addOnSuccessListener {
            toast("Tracking only when device is moving")
        }

        task!!.addOnFailureListener {
            Log.e("taskxx", it.message.toString())
            it.printStackTrace()
            toast("Requesting activity updates failed to start")
            toast(it.message.toString())


        }
        runMiniLocationDetector()

    }

    private fun runMiniLocationDetector(onLocationUpdateCallback: onLocationUpdateCallback? = null) {
        activityContext!!.startService(Intent(activityContext, LocationService::class.java))
        mLocationService.requestLocationUpdate(5000)
        if (onLocationUpdateCallback != null) {
            mLocationService.requestLocationUpdate(singleUpdateCallback = onLocationUpdateCallback)
        }
    }

    fun startLocationTracking(smart: Boolean) {

        useActivityRecognition = smart

        if (smart) {
            startActivityTracking()
        } else {
            startLocationTrackingTraditional()
        }
    }

    private fun startLocationTrackingTraditional() {
        activityContext!!.startService(Intent(activityContext, LocationService::class.java))
        mLocationService.requestLocationUpdate()
    }

    fun getForefroundNotif(): Notification? {
        return foregroundServiceNotif
    }

    fun stopLocationTracking(stopService: Boolean) {
        mLocationService.stopLocationUpdate()
        if (stopService) {
            activityContext!!.stopService(Intent(activityContext, LocationService::class.java))
        }
        toast("Stopped location tracking")
    }

    fun setNoInternetNotification(notif: Notification?) {
        mLocationService.setNoInternetService(notif)
    }

    fun notificationNoGPS(notif: Notification?) {
        mLocationService.notificationNoGPS(notif)
    }

    var handlderPermissionCallback = Handler()
    fun receivedPermissions() {
        handlderPermissionCallback.removeCallbacksAndMessages(null)
        handlderPermissionCallback.postDelayed({
            var result = ContextCompat.checkSelfPermission(
                activityContext!!,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
            if (result != PackageManager.PERMISSION_GRANTED) {
                val listPermissionsNeeded2: MutableList<String> = ArrayList()
                listPermissionsNeeded2.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                ActivityCompat.requestPermissions(
                    activityContext!!,
                    listPermissionsNeeded2.toTypedArray(),
                    5239
                )
            }
        }, 500)

    }

    fun onLocationUpdate(locationUpdateCallback: onLocationUpdateCallback) {
        mLocationService.setLocationUpdateCallBack(locationUpdateCallback)
    }

    fun requestSingleLocationUpdate(onLocationUpdateCallback: onLocationUpdateCallback) {
        runMiniLocationDetector(onLocationUpdateCallback)
    }


    /** Geofencing */
    var geoFenceHandler = Handler()


    fun addGeofence(
        key: String,
        lat: Double,
        lon: Double,
        geoFenceRadius: Long,
        listener: onGeofenceTriggerCallback
    ) {
        geoFenceHandler.removeCallbacksAndMessages(null)
        geoFenceHandler.postDelayed({
            mLocationService.addGeoFence(key, lat, lon, geoFenceRadius, listener)

        }, 500)
    }

    fun continueTracking() {
        startLocationTracking(useActivityRecognition)
    }

}