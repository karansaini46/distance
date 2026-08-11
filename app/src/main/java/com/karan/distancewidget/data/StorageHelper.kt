package com.karan.distancewidget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object StorageHelper {

    private val db = Firebase.database.reference

    private const val MAX_PHOTOS = 10

    // ── UPLOAD (Base64 → Realtime Database) ───────────────────────────────

    /**
     * Upload multiple photos as Base64 to Firebase Realtime Database.
     * Each photo stored at: photos/{userId}/images/{index}
     * Metadata at: photos/{userId}_count and photos/{userId}_ts
     *
     * Photos are compressed to 1800px max, JPEG 95% — highest practical
     * quality for free-tier RTDB (each photo ~500KB–1.2MB Base64).
     *
     * Returns true if ALL uploads succeed.
     */
    suspend fun uploadMultiplePhotos(
        context: Context,
        uris: List<Uri>,
        userId: String
    ): Boolean {
        if (uris.isEmpty()) return false
        val photos = uris.take(MAX_PHOTOS)

        return try {
            val updates = mutableMapOf<String, Any>()

            for ((index, uri) in photos.withIndex()) {
                val bitmap = compressBitmap(context, uri) ?: continue
                val bytes = ByteArrayOutputStream().also { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }.toByteArray()
                val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)

                updates["photos/$userId/images/$index"] = base64
            }

            // Remove old photos beyond new count
            val newCount = photos.size
            for (i in newCount until MAX_PHOTOS) {
                updates["photos/$userId/images/$i"] = "" // clear old slots
            }

            // Metadata
            updates["photos/${userId}_count"] = newCount
            updates["photos/${userId}_ts"]    = System.currentTimeMillis()

            db.updateChildren(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Upload a single photo (convenience wrapper).
     */
    suspend fun uploadPhoto(context: Context, uri: Uri, userId: String): Boolean {
        return uploadMultiplePhotos(context, listOf(uri), userId)
    }

    /**
     * Read and compress bitmap from a content Uri.
     * Max 1800px on longest side, JPEG 95% — best quality for free RTDB.
     */
    private fun compressBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original    = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val maxDim = 1800
            val scale  = minOf(maxDim.toFloat() / original.width,
                               maxDim.toFloat() / original.height, 1f)
            if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width  * scale).toInt(),
                    (original.height * scale).toInt(),
                    true
                )
            } else original
        } catch (e: Exception) {
            null
        }
    }

    // ── DOWNLOAD + CACHE ──────────────────────────────────────────────────

    /**
     * Get the partner's photo count and timestamp from RTDB.
     */
    suspend fun getPartnerPhotoMeta(partnerId: String): Pair<Int, Long> {
        return try {
            val countSnap = db.child("photos").child("${partnerId}_count").get().await()
            val tsSnap    = db.child("photos").child("${partnerId}_ts").get().await()
            val count = countSnap.getValue(Int::class.java) ?: 0
            val ts    = tsSnap.getValue(Long::class.java) ?: 0L
            Pair(count, ts)
        } catch (e: Exception) {
            Pair(0, 0L)
        }
    }

    /**
     * Get the partner's latest photo timestamp.
     */
    suspend fun getPartnerTimestamp(partnerId: String): Long {
        return try {
            val snap = db.child("photos").child("${partnerId}_ts").get().await()
            snap.getValue(Long::class.java) ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get the locally cached timestamp.
     */
    fun getCachedTimestamp(context: Context, partnerId: String): Long {
        return context.getSharedPreferences("photo_cache", Context.MODE_PRIVATE)
            .getLong("ts_$partnerId", 0L)
    }

    private fun saveCachedTimestamp(context: Context, partnerId: String, ts: Long) {
        context.getSharedPreferences("photo_cache", Context.MODE_PRIVATE)
            .edit().putLong("ts_$partnerId", ts).apply()
    }

    /**
     * Get the cached photo count.
     */
    fun getCachedPhotoCount(context: Context, partnerId: String): Int {
        return context.getSharedPreferences("photo_cache", Context.MODE_PRIVATE)
            .getInt("count_$partnerId", 0)
    }

    private fun saveCachedPhotoCount(context: Context, partnerId: String, count: Int) {
        context.getSharedPreferences("photo_cache", Context.MODE_PRIVATE)
            .edit().putInt("count_$partnerId", count).apply()
    }

    /**
     * Download ALL partner photos from RTDB (Base64 → local JPEG files).
     * Skips if cached timestamp matches remote.
     * Returns number of photos downloaded, 0 if none, -1 on error.
     */
    suspend fun downloadAllPartnerPhotos(context: Context, partnerId: String): Int {
        return try {
            val (remoteCount, remoteTs) = getPartnerPhotoMeta(partnerId)
            if (remoteCount == 0 || remoteTs == 0L) return 0

            val cachedTs = getCachedTimestamp(context, partnerId)
            if (remoteTs == cachedTs) {
                return getCachedPhotoCount(context, partnerId)
            }

            var downloaded = 0
            for (i in 0 until remoteCount) {
                try {
                    val snap = db.child("photos").child(partnerId)
                        .child("images").child("$i").get().await()
                    val base64 = snap.getValue(String::class.java)
                    if (base64.isNullOrEmpty()) continue

                    val bytes  = Base64.decode(base64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: continue

                    // Scale for widget (max 800px)
                    val scaled = scaleForWidget(bitmap)

                    val file = File(context.filesDir, "photo_${partnerId}_$i.jpg")
                    FileOutputStream(file).use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    downloaded++
                } catch (_: Exception) {
                    // Skip this photo
                }
            }

            // Remove old cached photos beyond new count
            for (i in remoteCount until MAX_PHOTOS) {
                val old = File(context.filesDir, "photo_${partnerId}_$i.jpg")
                if (old.exists()) old.delete()
            }

            saveCachedTimestamp(context, partnerId, remoteTs)
            saveCachedPhotoCount(context, partnerId, downloaded)

            downloaded
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Legacy single-photo download (backward compat).
     */
    suspend fun downloadPartnerPhoto(context: Context, partnerId: String): String? {
        val count = downloadAllPartnerPhotos(context, partnerId)
        if (count <= 0) return null
        val file = File(context.filesDir, "photo_${partnerId}_0.jpg")
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * Scale bitmap for widget display.
     * Max 800x800 — keeps RemoteViews memory well under 1MB limit.
     */
    private fun scaleForWidget(bitmap: Bitmap): Bitmap {
        val maxW = 800
        val maxH = 800
        val scale = minOf(maxW.toFloat() / bitmap.width,
                          maxH.toFloat() / bitmap.height, 1f)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width  * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else bitmap
    }

    /**
     * Load a specific cached photo by index.
     */
    fun loadCachedBitmap(context: Context, partnerId: String, index: Int = 0): Bitmap? {
        val file = File(context.filesDir, "photo_${partnerId}_$index.jpg")
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns how long ago the partner last sent a photo.
     */
    fun photoTimeAgo(ts: Long): String {
        if (ts == 0L) return "no photo yet"
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
