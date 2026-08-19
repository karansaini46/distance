package com.karan.distancewidget

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.karan.distancewidget.data.Prefs
import com.karan.distancewidget.ui.StoryActivity

class DistanceApp : Application(), Application.ActivityLifecycleCallbacks {
    
    private var activeActivities = 0
    private val isAppInBackground: Boolean
        get() = activeActivities == 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        
        // Guard against double-init crash (hot reload, instant run, etc.)
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        try {
            Firebase.database.setPersistenceEnabled(true)
            Firebase.database.setPersistenceCacheSizeBytes(5 * 1024 * 1024L)
        } catch (_: Exception) {
            // Already called — safe to ignore
        }
        
        createNotificationChannel()
        setupMessageListener()
        
        if (Prefs.isSetup(this)) {
            setupCommentsListener()
            
            val myId = Prefs.getUserId(this)
            if (myId != null) {
                com.karan.distancewidget.data.FirebaseHelper.listenForPings(myId) {
                    // Partner pinged us — get fresh GPS immediately without opening the app
                    val data = androidx.work.workDataOf("force_fresh" to true)
                    val req = androidx.work.OneTimeWorkRequestBuilder<com.karan.distancewidget.worker.LocationWorker>()
                        .setInputData(data)
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                    androidx.work.WorkManager.getInstance(this).enqueue(req)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val name = "Messages"
        val descriptionText = "Notifications for new messages"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel("messages_channel", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun setupMessageListener() {
        val myId = Prefs.getUserId(this) ?: return
        val partnerId = Prefs.getPartnerId(this) ?: return
        val convId = if (myId < partnerId) "${myId}_$partnerId" else "${partnerId}_$myId"
        
        val dbRef = Firebase.database.reference.child("chats").child(convId)
        dbRef.limitToLast(1).addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (!isAppInBackground) return
                
                try {
                    val senderId = snapshot.child("senderId").getValue(String::class.java)
                    if (senderId == null || senderId == myId) return
                    
                    val text = snapshot.child("text").getValue(String::class.java) ?: ""
                    val isPhoto = snapshot.child("isPhoto").getValue(Boolean::class.java) ?: false
                    
                    val contentText = if (isPhoto) "📸 Photo" else text
                    val partnerInitial = Prefs.getPartnerInitial(this@DistanceApp)
                    val partnerName = partnerId.replaceFirstChar { it.uppercase() }
                    
                    showNotification(partnerName, contentText, partnerInitial)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showNotification(title: String, content: String, initial: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_chat", true)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = createCircleBitmap(initial, 150)

        val builder = NotificationCompat.Builder(this, "messages_channel")
            .setSmallIcon(R.drawable.ic_launcher) // Using app icon as small icon
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun setupCommentsListener() {
        val myId = Prefs.getUserId(this) ?: return
        val partnerId = Prefs.getPartnerId(this) ?: return

        val commentsRef = Firebase.database.reference.child("comments").child("${partnerId}_to_${myId}")
        commentsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(photoSnapshot: DataSnapshot, previousChildName: String?) {
                val photoIndex = photoSnapshot.key ?: return
                var firstLoad = true

                photoSnapshot.ref.limitToLast(1).addChildEventListener(object : ChildEventListener {
                    override fun onChildAdded(commentSnap: DataSnapshot, previousChildName: String?) {
                        if (firstLoad) {
                            firstLoad = false
                            return
                        }

                        if (!isAppInBackground) return

                        try {
                            val text = commentSnap.child("text").getValue(String::class.java) ?: return
                            val partnerInitial = Prefs.getPartnerInitial(this@DistanceApp)
                            
                            showCommentNotification(photoIndex, text, partnerInitial)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                    override fun onChildRemoved(snapshot: DataSnapshot) {}
                    override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                    override fun onCancelled(error: DatabaseError) {}
                })
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showCommentNotification(photoIndex: String, text: String, initial: String) {
        val intent = Intent(this, StoryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, photoIndex.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (text.length > 80) text.substring(0, 77) + "..." else text
        
        val builder = NotificationCompat.Builder(this, "messages_channel")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("💬 New comment from $initial")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setGroup("comments_group")
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(photoIndex.hashCode(), builder.build())
    }

    private fun createCircleBitmap(text: String, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val bgPaint = Paint().apply {
            color = Color.parseColor("#E11D48") // AccentRose as default
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = size / 2f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, bgPaint)
        
        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(text, xPos, yPos, textPaint)
        
        return bitmap
    }

    // ActivityLifecycleCallbacks
    override fun onActivityStarted(activity: Activity) { activeActivities++ }
    override fun onActivityStopped(activity: Activity) { activeActivities-- }
    
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
