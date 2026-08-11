package com.karan.distancewidget.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val isPhoto: Boolean = false,
    val photoUrl: String? = null
)
