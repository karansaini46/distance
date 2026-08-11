package com.karan.distancewidget.data

import android.content.Context
import android.net.Uri
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.karan.distancewidget.data.db.ChatDatabase
import com.karan.distancewidget.data.db.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ChatRepository(private val context: Context) {
    private val db = ChatDatabase.getDatabase(context)
    private val messageDao = db.messageDao()
    private val firebaseDb = FirebaseDatabase.getInstance().reference

    fun getLocalMessages(): Flow<List<MessageEntity>> {
        return messageDao.getAllMessages()
    }

    suspend fun sendMessage(myId: String, partnerId: String, text: String, isPhoto: Boolean = false, photoUrl: String? = null) {
        val convId = getConversationId(myId, partnerId)
        val msgRef = firebaseDb.child("chats").child(convId).push()
        val msgId = msgRef.key ?: return

        val timestamp = System.currentTimeMillis()
        val message = MessageEntity(
            id = msgId,
            senderId = myId,
            text = text,
            timestamp = timestamp,
            isPhoto = isPhoto,
            photoUrl = photoUrl
        )

        // Save locally first for immediate UI update
        messageDao.insertMessage(message)

        // Send to Firebase
        msgRef.setValue(message).await()
    }
    
    suspend fun sendPhotoMessage(myId: String, partnerId: String, uri: Uri) {
        // Upload photo first using StorageHelper
        // Since StorageHelper uses Realtime DB for base64 (which is not ideal for large chats),
        // we can still use the same approach or just store it as a message.
        // Actually, StorageHelper.uploadPhoto overwrites images in a fixed 10-slot array.
        // For a chat app, storing unlimited base64 images in RTDB will break it quickly due to size limits.
        // I will implement a simpler base64 upload just for this message.
        val base64 = StorageHelper.encodeToBase64(context, uri)
        if (base64 != null) {
            sendMessage(myId, partnerId, text = "📸 Photo", isPhoto = true, photoUrl = base64)
        }
    }

    fun startListeningForMessages(myId: String, partnerId: String) {
        val convId = getConversationId(myId, partnerId)
        firebaseDb.child("chats").child(convId).orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = mutableListOf<MessageEntity>()
                    for (child in snapshot.children) {
                        try {
                            val id = child.child("id").getValue(String::class.java) ?: continue
                            val senderId = child.child("senderId").getValue(String::class.java) ?: ""
                            val text = child.child("text").getValue(String::class.java) ?: ""
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                            val isPhoto = child.child("isPhoto").getValue(Boolean::class.java) ?: false
                            val photoUrl = child.child("photoUrl").getValue(String::class.java)

                            messages.add(
                                MessageEntity(
                                    id = id,
                                    senderId = senderId,
                                    text = text,
                                    timestamp = timestamp,
                                    isPhoto = isPhoto,
                                    photoUrl = photoUrl
                                )
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    // Bulk insert to Room
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        if (messages.isNotEmpty()) {
                            messageDao.insertMessages(messages)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun getConversationId(id1: String, id2: String): String {
        return if (id1 < id2) "${id1}_$id2" else "${id2}_$id1"
    }
}
