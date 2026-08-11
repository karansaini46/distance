package com.karan.distancewidget.data

import android.location.Location
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object FirebaseHelper {

    private val db = Firebase.database.reference

    /**
     * Push this user's coords to Firebase.
     * Returns true on success, false on any error.
     */
    suspend fun updateMyLocation(userId: String, lat: Double, lng: Double): Boolean {
        return try {
            db.child("users").child(userId).setValue(
                mapOf("lat" to lat, "lng" to lng, "ts" to System.currentTimeMillis())
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetch a user's last known location from Firebase.
     * @param forceServer When true, bypasses Firebase's offline cache to get
     *                    a guaranteed server-fresh read (used during widget tap refresh).
     */
    suspend fun getLocation(userId: String, forceServer: Boolean = false): LocationData? {
        return try {
            val ref = db.child("users").child(userId)
            ref.keepSynced(true)

            val snap = if (forceServer) {
                // addListenerForSingleValueEvent + onCancelled gives a
                // guaranteed server round-trip when the device is online.
                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    ref.addListenerForSingleValueEvent(object :
                        com.google.firebase.database.ValueEventListener {
                        override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                            cont.resume(snapshot, null)
                        }
                        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                            cont.resume(null, null)
                        }
                    })
                } ?: return null
            } else {
                ref.get().await()
            }

            val lat = snap.child("lat").getValue(Double::class.java) ?: return null
            val lng = snap.child("lng").getValue(Double::class.java) ?: return null
            val ts  = snap.child("ts").getValue(Long::class.java)   ?: 0L
            LocationData(lat, lng, ts)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Haversine distance — returns kilometers, rounded to 1 decimal.
     */
    fun distanceKm(a: LocationData, b: LocationData): Double {
        val results = FloatArray(1)
        Location.distanceBetween(a.lat, a.lng, b.lat, b.lng, results)
        return (Math.round((results[0] / 1000.0) * 10.0) / 10.0)
    }

    /**
     * Human-readable distance string.
     * Under 1 km → show meters. Over that → show km.
     */
    fun formatDistance(km: Double): String = when {
        km < 1.0  -> "${(km * 1000).toInt()} m apart"
        km < 10   -> "$km km apart"
        else      -> "${km.toInt()} km apart"
    }

    /**
     * Returns true if the timestamp is older than 2 hours.
     * Used to show "last seen Xh ago" instead of "live ♡" in the widget.
     */
    fun isStale(ts: Long): Boolean =
        (System.currentTimeMillis() - ts) > (2 * 60 * 60 * 1000L)

    /**
     * Relative time string: "3m ago", "2h ago", "1d ago"
     */
    fun timeAgo(ts: Long): String {
        val diff    = System.currentTimeMillis() - ts
        val minutes = diff / 60_000
        return when {
            minutes < 1    -> "just now"
            minutes < 60   -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            else           -> "${minutes / 1440}d ago"
        }
    }
}
