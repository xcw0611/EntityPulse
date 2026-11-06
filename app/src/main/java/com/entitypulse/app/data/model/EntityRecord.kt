package com.entitypulse.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entities")
data class EntityRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val category: String = "默认",
    val isVip: Boolean = false,
    val isNotifyEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
