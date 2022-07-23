package com.prasadjadhav.smartlocationtracking

interface onLocationUpdateCallback {

    fun onLocationUpdate(lat:Double,lon : Double,hasInternet : Boolean,hasGPS : Boolean)
}