package com.cropora.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_history",
    indices = [Index(value = ["result_id"], unique = true)]
)
data class ScanRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "result_id")
    val resultId: String,
    @ColumnInfo(name = "model_label")
    val modelLabel: String,
    @ColumnInfo(name = "disease")
    val disease: String,
    @ColumnInfo(name = "confidence")
    val confidence: Float,
    @ColumnInfo(name = "uncertain")
    val uncertain: Boolean,
    @ColumnInfo(name = "guidance_available")
    val guidanceAvailable: Boolean,
    @ColumnInfo(name = "symptoms")
    val symptoms: String,
    @ColumnInfo(name = "treatment")
    val treatment: String,
    @ColumnInfo(name = "prevention")
    val prevention: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)
