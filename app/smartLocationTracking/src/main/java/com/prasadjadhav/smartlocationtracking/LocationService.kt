package com.prasadjadhav.smartlocationtracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.os.*
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import com.prasadjadhav.smartlocationtracking.Constants.DETECTION_INTERVAL_IN_MILLISECONDS
import com.prasadjadhav.smartlocationtracking.SmartLocationTracker.Companion.readPref
import com.prasadjadhav.smartlocationtracking.SmartLocationTracker.Companion.writePref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*


class LocationService : Service() {

    companion object {
        var onLocationUpdateCallback: onLocationUpdateCallback? = null
        var onSingleLocationUpdateCallback: onLocationUpdateCallback? = null
        var hashMap: HashMap<String, LatLongObject> = HashMap<String, LatLongObject>()

        var notificationManager: NotificationManager? = null
        var notificationNoInternet: Notification? = null
        var notificationNoGPS: Notification? = null
        val notifId: Int = 659856
        var geofenceListener: onGeofenceTriggerCallback? = null
        var isShowingLocationNotif = false
        var isShowingInternetNotif = false
        lateinit var broadcastReceiver: BroadcastReceiver
        var geofenceList: ArrayList<Geofence> = ArrayList()
        var latTemp: Double = 0.0
        var lonTemp: Double = 0.0
        lateinit var geofencingClient: GeofencingClient
        private val geofencePendingIntent: PendingIntent by lazy {
            val intent = Intent(activityContext, GeofenceBroadcastReceiver::class.java)
            // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
            // addGeofences() and removeGeofences().
            PendingIntent.getBroadcast(
                activityContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }

        val METERS_IN_MILE = 1609.344

        fun metersToMiles(meters: Double): Double {
            return meters / METERS_IN_MILE
        }

        fun milesToMeters(miles: Double): Double {
            return miles * METERS_IN_MILE
        }

        private fun deg2rad(deg: Double): Double {
            return deg * Math.PI / 180.0
        }

        private fun rad2deg(rad: Double): Double {
            return rad * 180.0 / Math.PI
        }

        fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val theta = lon1 - lon2
            var dist = (Math.sin(deg2rad(lat1))
                    * Math.sin(deg2rad(lat2))
                    + (Math.cos(deg2rad(lat1))
                    * Math.cos(deg2rad(lat2))
                    * Math.cos(deg2rad(theta))))
            dist = Math.acos(dist)
            dist = rad2deg(dist)
            dist = dist * 60 * 1.1515
            var prevDistance = 0F
            try {
//                prevDistance = distanceList.get(distanceList.size - 1)
                if (dist < 1000) {
                    Log.e("transx", "Enter - " + dist)


                } else {
                    Log.e("transx", "Exit " + dist)

                }

            } catch (e: Exception) {
                Log.e("transx", "Exception")

                e.printStackTrace()
            }
//        distanceList.add(dist.toFloat())

            return milesToMeters(dist)
        }

        private fun toggleInternetNotif(showHide: Boolean) {
            try {


                if (notificationManager == null) {
                    notificationManager =
                        activityContext!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                }


                if (showHide) {
                    if (!isShowingInternetNotif) {
                        isShowingInternetNotif = true
                        writeToAFileUnhandled("Notif show - no internet" + latitudeCurrent + " - " + longitudeCurrent)

                        notificationManager!!.notify(notifId, notificationNoInternet)
                        Log.e("isShowingInternetNotif", "show")


                    }
                } else {
                    isShowingInternetNotif = false
                    notificationManager!!.cancel(notifId)

                    Log.e("isShowingInternetNotif", "hide")
//                    writeToAFileUnhandled("Notif hide - no internet" + latitudeCurrent + " - " + longitudeCurrent)

                }


            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnected
//            return true
        }

        private fun checkInternetActual(): Boolean {
            if (isNetworkAvailable(activityContext)) {
                try {
                    val urlc: HttpURLConnection =
                        URL("http://www.google.com").openConnection() as HttpURLConnection
                    urlc.setRequestProperty("User-Agent", "Test")
                    urlc.setRequestProperty("Connection", "close")
                    urlc.setConnectTimeout(1500)
                    urlc.connect()
                    return urlc.getResponseCode() === 200
                } catch (e: IOException) {
                    Log.e("internetx", "Error checking internet connection", e)
//                    e.printStackTrace()
                }
            } else {
                Log.d("internetx", "No network available!")
            }
            return false
        }

        fun hasActiveInternetConnection() {
            if (notificationNoInternet != null) {
                var coroutineScope = CoroutineScope(Dispatchers.IO)
                coroutineScope?.launch {
                    Log.d("Track", "Launch Fetch Started")
                    val result = checkInternetActual()
                    Log.d("Trackxx", result.toString())

                    launch(Dispatchers.Main) {
                        if (result) {
                            toggleInternetNotif(false)

//                       AppController.toast("Has internet")
                        } else {
                            toggleInternetNotif(true)

//                       AppController.toast("No internet")

                        }
                    }
                }
            } else {
                notificationManager!!.cancel(notifId)


            }

        }

        fun isGpsEnabled(): Boolean {
            val contentResolver = activityContext.contentResolver
            // Find out what the settings say about which providers are enabled
            //  String locationMode = "Settings.Secure.LOCATION_MODE_OFF";
            val mode = Settings.Secure.getInt(
                contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF
            )
            return if (mode != Settings.Secure.LOCATION_MODE_OFF) {
                true
                /* if (mode == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY) {
                                locationMode = "High accuracy. Uses GPS, Wi-Fi, and mobile networks to determine location";
                            } else if (mode == Settings.Secure.LOCATION_MODE_SENSORS_ONLY) {
                                locationMode = "Device only. Uses GPS to determine location";
                            } else if (mode == Settings.Secure.LOCATION_MODE_BATTERY_SAVING) {
                                locationMode = "Battery saving. Uses Wi-Fi and mobile networks to determine location";
                            }*/
            } else {
                false
            }
        }

        fun hasActiveGPSonnection() {
            if (notificationNoGPS != null) {
                var isLocationOn = isGpsEnabled()
                toggleLocationNotification(isLocationOn)
            } else {
                notificationManager!!.cancel(notifIdGPS)

            }

        }
        var firstTime = true
        var notificationTimer = object : CountDownTimer(Long.MAX_VALUE, 5000) {
            override fun onTick(p0: Long) {
                if(firstTime)
                {
                    firstTime = false
                    return
                }
                try {
                    hasActiveInternetConnection()
                } catch (e: Exception) {
                }
                try {
                    hasActiveGPSonnection()
                } catch (e: Exception) {
                }
                Log.e("serviceTimer", "Ontick")



            }

            override fun onFinish() {

            }

        }
        var debugTimer = object : CountDownTimer(Long.MAX_VALUE, 300000) {
            override fun onTick(p0: Long) {

                Log.e("debugTimer", "Ontick")



            }

            override fun onFinish() {

            }

        }
        val notifIdGPS: Int = 55245

        private fun toggleLocationNotification(locationOn: Boolean) {
            try {

                if (notificationManager == null) {
                    notificationManager =
                        activityContext!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                }
                //Notification builder


                if (!locationOn) {
                    if (!isShowingLocationNotif) {
                        isShowingLocationNotif = true
                        notificationManager!!.notify(notifIdGPS, notificationNoGPS)
                        Log.e("locationxxx", "show")
                        writeToAFileUnhandled("Notif show - no GPS" + latitudeCurrent + " - " + longitudeCurrent)

                    }
                } else {
                    isShowingLocationNotif = false
                    notificationManager!!.cancel(notifIdGPS)

                    Log.e("locationxxx", "hide")
//                    writeToAFileUnhandled("Notif hide - no GPS" + latitudeCurrent + " - " + longitudeCurrent)

                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        var locationService: LocationService? = null
        lateinit var activityContext: AppCompatActivity

        var handler = Handler()
        var latitudeCurrent: Double = 0.0
        var longitudeCurrent: Double = 0.0
        lateinit var smartLocationTracker: SmartLocationTracker
        var fusedLocationProviderClient: FusedLocationProviderClient? = null

        var mLocationCallBack = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    val location: Location? = locationResult.getLastLocation()
                    if (location != null) {
                        Log.d("LocationXService1", "location update $location")

                        if (latitudeCurrent == location.latitude && longitudeCurrent == location.longitude) {
                            Log.d("LocationXService1", "skip")

                            return@postDelayed

                        }
                        if (location.latitude > 0.0) {
                            return@postDelayed
                        }
                        latitudeCurrent = location.latitude
                        longitudeCurrent = location.longitude

                        Log.d("LocationXService", "location update $location")
                        Toast.makeText(
                            activityContext,
                            "Location update - " + latitudeCurrent + " - " + longitudeCurrent,
                            Toast.LENGTH_LONG
                        ).show()

                        var coroutineScope = CoroutineScope(Dispatchers.IO)
                        coroutineScope?.launch {
                            Log.d("Track", "Launch Fetch Started")
                            val result = checkInternetActual()
                            Log.d("Trackxx", result.toString())

                            launch(Dispatchers.Main) {
                                onLocationUpdateCallback?.onLocationUpdate(
                                    latitudeCurrent,
                                    longitudeCurrent,
                                    result,
                                    isGpsEnabled()
                                )
                                onSingleLocationUpdateCallback?.onLocationUpdate(
                                    latitudeCurrent,
                                    longitudeCurrent,
                                    result,
                                    isGpsEnabled()
                                )
                                Handler().postDelayed({
                                    if (onSingleLocationUpdateCallback != null) {
                                        onSingleLocationUpdateCallback = null
                                    }
                                }, 1000)

                            }
                        }

                    }
                }, 500)
            }
        }

        //        fun writeToAFileUnhandled(thread: Thread, e: Throwable) {
        fun writeToAFileUnhandled(finalString: String) {
            try {
                if (BuildConfig.DEBUG) {
                    writeTextFile("smartTracker", finalString)


                    /*  val trace = e.stackTrace
                      var errorIn = " No trace "
                      try {
                          errorIn = " -- Error in - " + Arrays.toString(e.cause!!.stackTrace)
                      } catch (ex: java.lang.Exception) {
  //                ex.printStackTrace();
                      }
                      val stackTrace = Log.getStackTraceString(e)
                      val finalString =
                          """ -- Name - ${e.message} -- Class - ${thread.javaClass.name} -- Method - ${trace[0].methodName} -- Line - ${trace[0].lineNumber} -- Trace - $stackTrace$errorIn

  """
                      writeTextFile("insight_exceps", finalString)*/
                }
            } catch (ex: java.lang.Exception) {
//            ex.printStackTrace();
            }
        }

        fun writeTextFile(fileName: String, data: String) {
            val hasPerm: Int = activityContext.packageManager.checkPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                activityContext.getPackageName()
            )
            if (hasPerm == PackageManager.PERMISSION_GRANTED) {
                writeFileActual(fileName, data)
            }

        }


        fun writeFileActual(fileName: String, data: String) {
            if (BuildConfig.DEBUG) {
                object : AsyncTask<Void?, Void?, Void?>() {

                    override fun doInBackground(vararg params: Void?): Void? {
                        try {
                            val currentDate =
                                SimpleDateFormat(
                                    "dd-MM-yyyy HH:mm:ss - ",
                                    Locale.getDefault()
                                ).format(
                                    Date()
                                )
                            var direct: File? = null
                            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                                // only for gingerbread and newer versions
                                direct = File(
                                    Environment.getExternalStorageDirectory()
                                        .toString() + "/smartTracker"
                                )
                            } else {
                                direct = File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                                    "smartTracker"
                                )
                            }


                            /* val direct = File(
                                 Environment.getExternalStorageDirectory()
                                     .toString() + "/smartTracker"
                             )*/
                            if (!direct.exists()) {
                                direct.mkdir() //directory is created;
                                Log.e("writeFileActualx", "mkdirs - ")
                            }
                            val gpxfile = File(direct, "$fileName.txt")
                            if (!gpxfile.exists()) {
                                gpxfile.createNewFile()
                                Log.e("writeFileActualx", "createNewFile - $fileName")
                            }
                            val writer = FileWriter(gpxfile, true)
                            writer.append(
                                """
                            $currentDate$data
                            
                            
                            """.trimIndent()
                            )
                            writer.flush()
                            writer.close()
                            Log.e("writeFileActual", "Wrote to file - $fileName - Data - $data")
                        } catch (e: java.lang.Exception) {
                            Log.e("writeFileActual", "Exception - " + e.message)
                            //e.printStackTrace();
                        }
                        return null
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    fun requestLocationUpdate(
        timeoutOneMinute: Int? = 0,
        singleUpdateCallback: onLocationUpdateCallback? = null
    ) {

        if (fusedLocationProviderClient != null) {
            stopLocationUpdate()
        }

        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(activityContext)
        val request = LocationRequest()

        request.setInterval(DETECTION_INTERVAL_IN_MILLISECONDS.toLong())
        request.setFastestInterval(DETECTION_INTERVAL_IN_MILLISECONDS.toLong())
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        val permission = ContextCompat.checkSelfPermission(
            activityContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (permission == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient!!.requestLocationUpdates(
                request,
                mLocationCallBack,
                null
            )
        }
        if (timeoutOneMinute != null && timeoutOneMinute > 0) {
            Handler().postDelayed({
                stopLocationUpdate()

            }, timeoutOneMinute.toLong())
        }
        if (singleUpdateCallback != null) {
            onSingleLocationUpdateCallback = singleUpdateCallback
        }

    }

    val FG_NOTIF_ID = 54

    private fun startServiceInForeground() {
        try {


            startForeground(FG_NOTIF_ID, smartLocationTracker.getForefroundNotif())

            notificationTimer.cancel()
            notificationTimer.start()
            debugTimer.cancel()
            debugTimer.start()

        } catch (e: Exception) {
            e.printStackTrace()
        }


    }


    fun getInstance(activity: AppCompatActivity, helper: SmartLocationTracker): LocationService? {
        smartLocationTracker = helper
        activityContext = activity
        if (locationService == null) {
            locationService = this
        }
        return locationService
    }


    var handlerActivity = Handler()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startServiceInForeground()
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                handlerActivity.removeCallbacksAndMessages(null)
                handlerActivity.postDelayed({
                    Log.e("handleUserActivity", "onReceive : ")
                    if (intent.action == Constants.BROADCAST_DETECTED_ACTIVITY) {
                        val type = intent.getIntExtra("type", -1)
                        val confidence = intent.getIntExtra("confidence", 0)
                        handleUserActivity(type, confidence)
                    }
                }, 500)
            }
        }
        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(
            broadcastReceiver,
            IntentFilter(Constants.BROADCAST_DETECTED_ACTIVITY)
        )

        return super.onStartCommand(intent, flags, startId)
    }

    fun stopLocationUpdate() {
        if (fusedLocationProviderClient != null) {

            fusedLocationProviderClient!!.removeLocationUpdates(mLocationCallBack)
        }

    }

    private fun handleUserActivity(type: Int, confidence: Int) {
        var label = "UNKNOWN"
        when (type) {
            DetectedActivity.IN_VEHICLE -> {
                label = "IN_VEHICLE"
            }
            DetectedActivity.ON_BICYCLE -> {
                label = "ON_BICYCLE"
            }
            DetectedActivity.ON_FOOT -> {
                label = "ON_FOOT"
            }
            DetectedActivity.RUNNING -> {
                label = "RUNNING"
            }
            DetectedActivity.STILL -> {
                label = "STILL"
            }
            DetectedActivity.TILTING -> {
                label = "TILTING"
            }
            DetectedActivity.WALKING -> {
                label = "WALKING"
            }
            DetectedActivity.UNKNOWN -> {
                label = "UNKNOWN"
            }
        }
        Log.e("handleUserActivity", "detected : " + label)
//        Toast.makeText(activityContext, "detected : " + label, Toast.LENGTH_SHORT).show()


        if (SmartLocationTracker.useActivityRecognition) {

            if (confidence > Constants.MIN_CONFIDENCE) {
//            showToast("User activity: $label, Confidence: $confidence")
                Log.e("handleUserActivity", "User activity: $label, Confidence: $confidence")

                if (type == DetectedActivity.STILL) {

                    stopLocationUpdate()
                } else {

                    requestLocationUpdate()
                }
            }
        } else {

            requestLocationUpdate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationTimer.cancel()
        debugTimer.cancel()
        notificationManager!!.cancel(notifId)
        notificationManager!!.cancel(notifIdGPS)
        writeToAFileUnhandled("Notif hide - onDestroy" + latitudeCurrent + " - " + longitudeCurrent)

    }

    fun setNoInternetService(notif: Notification?) {

        notificationNoInternet = notif
    }

    fun notificationNoGPS(notif: Notification?) {

        notificationNoGPS = notif
    }

    fun setLocationUpdateCallBack(locationUpdateCallback: onLocationUpdateCallback) {
        onLocationUpdateCallback = null
        onLocationUpdateCallback = locationUpdateCallback
    }

    /** Geofencing */

    private fun getGeofencingRequest(): GeofencingRequest {
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT)
            addGeofences(geofenceList)
        }.build()
    }


    @SuppressLint("MissingPermission")
    fun addGeoFence(
        key: String,
        lat: Double,
        lon: Double,
        geoFenceRadius: Long,
        listener: onGeofenceTriggerCallback
    ) {
        try {
            geofenceListener = listener
            latTemp = lat
            lonTemp = lon
            var TAG = "geofencex"

            var distance = distance(latitudeCurrent, longitudeCurrent, latTemp, lonTemp)
            Log.e(TAG, "distancex - " + distance)


            geofencingClient = LocationServices.getGeofencingClient(activityContext)
            Log.e(TAG, "Register client")
            geofencingClient?.removeGeofences(geofencePendingIntent)?.run {
                addOnSuccessListener {
                    // Geofences removed
                    // ...
                    Log.e(TAG, "removeGeofences")

                }
                addOnFailureListener {
                    // Failed to remove geofences
                    // ...
                    Log.e(TAG, "removeGeofences fail")

                }
            }
            var latLongObject = LatLongObject()
            latLongObject.lat = lat
            latLongObject.lon = lon
            latLongObject.radius = geoFenceRadius
            Log.e("latLongObjectx", "insert - " + latLongObject.toString())

            hashMap.put(key, latLongObject)
            geofenceList.add(
                Geofence.Builder()
                    // Set the request ID of the geofence. This is a string to identify this
                    // geofence.
                    .setRequestId(key)
                    .setNotificationResponsiveness(Notification.PRIORITY_HIGH)


                    // Set the circular region of this geofence.
                    .setCircularRegion(
                        lat,
                        lon,
                        geoFenceRadius.toFloat()
                    )

                    // Set the expiration duration of the geofence. This geofence gets automatically
                    // removed after this period of time.
                    .setExpirationDuration(Constants.GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                    .setLoiteringDelay(Constants.GEOFENCE_LOITERING_DELAY)

                    // Set the transition types of interest. Alerts are only generated for these
                    // transition. We track entry and exit transitions in this sample.
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)

                    // Create the geofence.
                    .build()
            )
            Log.e(TAG, "add geofence to list")
            Log.e(TAG, "addGeofences")

            geofencingClient?.addGeofences(getGeofencingRequest(), geofencePendingIntent)?.run {
                addOnSuccessListener {
                    // Geofences added
                    // ...
                    //                AppController.toast("Added geofence - " + key)
                    Log.e(TAG, "addGeofences added")
                    smartLocationTracker?.continueTracking()

                }
                addOnFailureListener {
                    // Failed to add geofences
                    // ...
                    //                AppController.toast("Adding geofence failed - " + key + " cause - " +  it.message)
                    if (it.message.toString().contains("1004")) {
//                        HomeScreen.showbackgroundLocationDialog()
                        Toast.makeText(
                            activityContext,
                            "Adding geofence failed - " + key + " cause - " + it.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    Log.e(TAG, "addGeofences failedxx")
                    it.printStackTrace()

                }
            }
            /*if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }*/
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }


    class GeofenceBroadcastReceiver : BroadcastReceiver() {
        var TAG = "geofencex"


        override fun onReceive(context: Context?, intent: Intent?) {

            try {
                val geofencingEvent = intent?.let { GeofencingEvent.fromIntent(it) }
                if (geofencingEvent != null) {

                    geofenceListener!!.onGeofenceTriggered(geofencingEvent)
                    writeToAFileUnhandled("onGeofenceTriggered")
                    Log.e(TAG, "onReceive")
                    Log.e(
                        "latLongObjectx",
                        "onReceive "
                    )
                    var distance = distance(latitudeCurrent, longitudeCurrent, latTemp, lonTemp)
                    Log.e(TAG, "xdistancex - " + distance)
                    var geoTimeout = readPref("geoTimeout", activityContext)!!.toLong()
                    if (geoTimeout == 12345678910L) {
                        writePref("geoTimeout", SystemClock.elapsedRealtime(), activityContext)
                    }
                    var mLastClickTime: Long = readPref("geoTimeout", activityContext)!!

                    if (SystemClock.elapsedRealtime() - mLastClickTime < 5000) {
                        Log.e(TAG, "skipped")

                        //                return;
                    }
                    Log.e(TAG, "proceed")

                    mLastClickTime = SystemClock.elapsedRealtime();
                    writePref("geoTimeout", mLastClickTime, activityContext)
                    if (geofencingEvent != null) {
                        if (geofencingEvent.hasError()) {
                            val errorMessage = GeofenceStatusCodes
                                .getStatusCodeString(geofencingEvent.errorCode)
                            Log.e(TAG, errorMessage)
                            return
                        }
                    }

                    // Get the transition type.
                    val geofenceTransitionDetails = geofencingEvent?.let {
                        getGeofenceTransitionDetails(
                            it

                        )
                    }

                    val geofenceTransition = geofencingEvent!!.geofenceTransition
                    if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                        Log.e(TAG, "GEOFENCE_TRANSITION_ENTER")


                    }
                    if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {


                    }
                    // Test that the reported transition was of interest.
                    if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER || geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

                        // Get the geofences that were triggered. A single event can trigger
                        // multiple geofences.
                        val triggeringGeofences = geofencingEvent.triggeringGeofences

                        // Get the transition details as a String.
                        val geofenceTransitionDetails = getGeofenceTransitionDetails(
                            geofencingEvent

                        )

                        // Send notification and log the transition details.
                        //                sendNotification(geofenceTransitionDetails)
                        //                AppController.toast("GEOFENCE_TRANSITION")
                        Log.e(
                            "latLongObjectx",
                            "triggeringGeofences " + geofencingEvent.triggeringGeofences!!.size
                        )

                        for (geofence in geofencingEvent.triggeringGeofences!!) {
                            try {
                                var id = geofence.requestId
                                var latLongObject = hashMap.getValue(id)
                                Log.e("latLongObjectx", "triggered - " + latLongObject.toString())
                                var distance = distance(
                                    latitudeCurrent,
                                    longitudeCurrent,
                                    latLongObject.lat,
                                    latLongObject.lon
                                )
                                Log.e(
                                    "latLongObject",
                                    "current " + latitudeCurrent + " - " + longitudeCurrent
                                )
                                Log.e(
                                    "latLongObject",
                                    "geofence " + latLongObject.lat + " - " + latLongObject.lon
                                )
                                Log.e("latLongObjectx", "distance " + distance)

                                if (distance > 10 && distance < 5000) {
                                    if (distance < latLongObject.radius) {
                                        Log.e("latLongObjectx", "triggeredxx - enter " + distance)

                                    } else {

                                        Log.e("latLongObjectx", "triggeredxx - exit " + distance)
                                        Log.e(TAG, "GEOFENCE_TRANSITION_EXIT")
                                        Log.e(TAG, geofenceTransitionDetails)

                                    }
                                }
                                Log.e("latLongObject", "triggered - " + latLongObject.toString())

                            } catch (e: Exception) {
                            }
                        }


                    } else {
                        //                AppController.toast("geofence_transition_invalid_type - " + geofenceTransition)

                        // Log the error.
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }


        private fun removeGeoFence() {
            geofencingClient?.removeGeofences(geofencePendingIntent)?.run {
                addOnSuccessListener {
                    // Geofences removed
                    // ...
                    Log.e(TAG, "removeGeofences")

                }
                addOnFailureListener {
                    // Failed to remove geofences
                    // ...
                    Log.e(TAG, "removeGeofences fail")

                }
            }
        }

        private fun getGeofenceTransitionDetails(event: GeofencingEvent): String {
            val transitionString: String
            val c = Calendar.getInstance()
            val df = SimpleDateFormat("mm-ss")
            val formattedDate: String = df.format(c.time)
            val geofenceTransition = event.geofenceTransition
            transitionString = if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                "IN-$formattedDate"
            } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                "OUT-$formattedDate"
            } else {
                "OTHER-$formattedDate"
            }
            val triggeringIDs: MutableList<String?>
            triggeringIDs = ArrayList()
            for (geofence in event.triggeringGeofences!!) {
                triggeringIDs.add(geofence.requestId)
            }
            return String.format("%s: %s", transitionString, TextUtils.join(", ", triggeringIDs))
        }
    }
}
