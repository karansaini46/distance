package com.karan.distancewidget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object StorageHelper {

    private val db = Firebase.database.reference

    // ── UPLOAD ────────────────────────────────────────────────────────────

    /**
     * Compress and upload a photo from the given Uri.
     * Stores the new timestamp and Base64 string in Firebase DB so partner's widget knows to refresh.
     * Returns true on success.
     */
    suspend fun uploadPhoto(context: Context, uri: Uri, userId: String): Boolean {
        return try {
            // 1. Decode and compress bitmap
            val bitmap = compressBitmap(context, uri) ?: return false

            // 2. Convert to byte array (JPEG, 80% quality)
            val bytes = ByteArrayOutputStream().also { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }.toByteArray()

            // 3. Convert to Base64 string
            val base64String = Base64.encodeToString(bytes, Base64.DEFAULT)

            // 4. Upload to Firebase Realtime Database
            val updates = mapOf<String, Any>(
                "photos/${userId}_b64" to base64String,
                "photos/${userId}_ts" to System.currentTimeMillis()
            )
            db.updateChildren(updates).await()

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Read and compress bitmap from a content Uri.
     * Scales down if the image is very large before upload.
     */
    private fun compressBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original    = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Scale down to max 1080px on longest side before upload
            val maxDim = 1080
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
     * Get the partner's latest photo timestamp from Firebase DB.
     * Returns 0L if never uploaded.
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
     * Get the locally cached timestamp (last time we downloaded partner's photo).
     * Used to avoid re-downloading unchanged photos.
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
     * Download partner's photo from Firebase Realtime Database and save to internal storage.
     * Returns the local file path on success, null on failure.
     *
     * Skips download if the cached timestamp matches Firebase — avoids wasting
     * bandwidth re-downloading the same image on every 15-min worker run.
     */
    suspend fun downloadPartnerPhoto(context: Context, partnerId: String): String? {
        return try {
            val remoteTs = getPartnerTimestamp(partnerId)
            if (remoteTs == 0L) return null   // partner hasn't uploaded yet

            val cachedTs  = getCachedTimestamp(context, partnerId)
            val localFile = File(context.filesDir, "photo_$partnerId.jpg")

            // Skip download if already cached and up to date
            if (remoteTs == cachedTs && localFile.exists()) {
                return localFile.absolutePath
            }

            // Get Base64 image from Firebase DB
            val snap = db.child("photos").child("${partnerId}_b64").get().await()
            val base64String = snap.getValue(String::class.java) ?: return null

            // Decode image bytes and scale
            val bitmap = decodeAndScaleBitmap(base64String) ?: return null

            // Save to internal storage
            FileOutputStream(localFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            // Record that we now have the latest version
            saveCachedTimestamp(context, partnerId, remoteTs)

            localFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decode a bitmap from a Base64 string and scale it to widget-safe dimensions.
     * Max 400x300 — keeps RemoteViews memory well under the 1MB limit.
     */
    private fun decodeAndScaleBitmap(base64Str: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

            // Scale to widget dimensions
            val maxW = 400
            val maxH = 300
            val scale = minOf(maxW.toFloat() / bitmap.width,
                              maxH.toFloat() / bitmap.height, 1f)
            if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width  * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load the cached photo as a Bitmap for the widget.
     * Returns null if no cached photo exists.
     */
    fun loadCachedBitmap(context: Context, partnerId: String): Bitmap? {
        val file = File(context.filesDir, "photo_$partnerId.jpg")
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns how long ago the partner last sent a photo.
     * e.g. "2h ago", "just now"
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
