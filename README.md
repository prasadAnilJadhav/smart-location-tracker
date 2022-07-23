
# Smart Location Tracker for Android

Tracks location only when device is moving, saving battery without compromising reliability






## Deployment

Step 1. Add the JitPack repository to your build file


```bash
	allprojects {
		repositories {
			maven { url 'https://jitpack.io' }
		}
	}
```

Step 2. Add the dependency

```bash
	dependencies {
	        implementation 'com.github.prasadAnilJadhav:smart-location-tracker:1.0.0'
	}
```

Step 3. (Only if you want to use Geofencing) Add the dependency

```bash
	dependencies {
            implementation 'com.google.android.gms:play-services-location:20.0.0'
	}
```











## Features

    1. Track location only when device is moving
    2. Track location in the traditional way (Continous tracking)
    3. Geofencing
    4. Fetch a single location
    5. Show permission popups for required permissions
    6. Show notifications for No Internet/No GPS (Need to pass your own notifications to display)

## How to use

#### Inititalize the library
        Define variable - 
        lateinit var smartLocationTracker: SmartLocationTracker



        Initialize variables - 
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            createNotificationChanel()
        }
        var notification = getForegroundNotification()
        smartLocationTracker = SmartLocationTracker().init(this@MainActivity, notification)!!
        addOptionalNotifications()

#### Check permissions in onResume

        override fun onResume() {
           super.onResume()
           smartLocationTracker.requestPermissions()
        }


#### Override onRequestPermissionsResult in Order to request background location permission in Android 12+

        override fun onRequestPermissionsResult(
           requestCode: Int,
           permissions: Array<out String>,
           grantResults: IntArray
        ) {
           super.onRequestPermissionsResult(requestCode, permissions, grantResults)
           smartLocationTracker.receivedPermissions()
        }


#### Request for Current Location

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

#### Start location updates 

        if(trackOnlyWhenDeviceMoves)
        {
            trackOnlyWhenMoving = true
        }else if (trackContinously)
        {
            trackOnlyWhenMoving = false
        }

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

#### Stop location updates

        smartLocationTracker.stopLocationTracking(true)

#### Add geofence

        smartLocationTracker.addGeofence("key_1", 35.292050, 76.202514, 1000, object :
            onGeofenceTriggerCallback {
            override fun onGeofenceTriggered(event: GeofencingEvent) {
                for (geofence in event.triggeringGeofences!!) {
                    var id = geofence.requestId
                    Log.e("latLongObjectx1", "triggered - " + id.toString())
                }

            }
        })





## Known issues

Some chinese devices will kill the Location Tracking Service when entering Doze mode
## Work in progress

Currently requires activity running in order to initialize and start the service, Working on moving this to a seperate class



[![MIT License](https://img.shields.io/apm/l/atomic-design-ui.svg?)](https://github.com/tterb/atomic-design-ui/blob/master/LICENSEs)
[![GPLv3 License](https://img.shields.io/badge/License-GPL%20v3-yellow.svg)](https://opensource.org/licenses/)
[![AGPL License](https://img.shields.io/badge/license-AGPL-blue.svg)](http://www.gnu.org/licenses/agpl-3.0)

