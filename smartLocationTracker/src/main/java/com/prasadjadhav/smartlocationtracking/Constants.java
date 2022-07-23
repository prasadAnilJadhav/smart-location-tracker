package com.prasadjadhav.smartlocationtracking;

public class Constants {
    public static String BROADCAST_DETECTED_ACTIVITY = "BROADCAST_DETECTED_ACTIVITY";
    public static final int TIMEOUT_ONE_MINUTE = 60 * 1000;
    public static final int DETECTION_INTERVAL_IN_MILLISECONDS = (30 * 1000);
    public static final String CHANNEL_DEFAULT_NOTIFICATIONS = "CHANNEL_DEFAULT_NOTIFICATIONS";
    public static Long GEOFENCE_EXPIRATION_IN_MILLISECONDS = Long.valueOf(23 * 3600000);//24 hours/
    public static int GEOFENCE_LOITERING_DELAY = 1 *  3000;

    public static final byte MIN_CONFIDENCE = 70;
}
