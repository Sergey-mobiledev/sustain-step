package com.sustain.step.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history",
    indices = [Index(value = ["date"], unique = true)]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val steps: Int,
    val date: String,
    @ColumnInfo(defaultValue = "7000")
    val goal: Int = 7000,
    val task: String = "",
    val isPurchased: Boolean = true
)

