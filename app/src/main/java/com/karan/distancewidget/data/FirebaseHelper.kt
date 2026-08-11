package com.karan.distancewidget.data

import android.location.Location
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

object FirebaseHelper {

    private val db = Firebase.database.reference

    fun pingPartnerForLocation(partnerId: String) {
        db.child("requests").child(partnerId).child("ping").setValue(System.currentTimeMillis())
    }

    fun listenForPings(myId: String, onPingReceived: () -> Unit) {
        val ref = db.child("requests").child(myId).child("ping")
        ref.addValueEventListener(object : ValueEventListener {
            private var firstCall = true
            override fun onDataChange(snapshot: DataSnapshot) {
                if (firstCall) {
                    firstCall = false
                    return
                }
                if (snapshot.exists()) {
                    onPingReceived()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

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

    /**
     * Send a comment for a specific photo index.
     */
    suspend fun sendComment(fromId: String, toId: String, photoIndex: Int, text: String): Boolean {
        return try {
            val commentId = db.child("comments").child("${toId}_to_${fromId}").child(photoIndex.toString()).push().key ?: return false
            val commentData = mapOf(
                "text" to text,
                "ts" to System.currentTimeMillis()
            )
            db.child("comments").child("${toId}_to_${fromId}").child(photoIndex.toString()).child(commentId).setValue(commentData).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Observe comments for a specific photo index.
     */
    fun getComments(fromId: String, toId: String, photoIndex: Int): Flow<List<CommentData>> = callbackFlow {
        val ref = db.child("comments").child("${fromId}_to_${toId}").child(photoIndex.toString())
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val comments = mutableListOf<CommentData>()
                for (child in snapshot.children) {
                    val text = child.child("text").getValue(String::class.java) ?: continue
                    val ts = child.child("ts").getValue(Long::class.java) ?: 0L
                    comments.add(CommentData(text, ts))
                }
                comments.sortBy { it.ts }
                trySend(comments)
            }

            override fun onCancelled(error: DatabaseError) {
                // Ignore
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}

data class CommentData(val text: String, val ts: Long)
