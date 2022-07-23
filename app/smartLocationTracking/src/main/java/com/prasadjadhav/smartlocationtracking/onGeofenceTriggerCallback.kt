package com.prasadjadhav.smartlocationtracking

import com.google.android.gms.location.GeofencingEvent

interface onGeofenceTriggerCallback {

    fun onGeofenceTriggered(event: GeofencingEvent)
}