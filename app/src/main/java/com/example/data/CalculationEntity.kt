package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calculation_history")
data class CalculationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val queryText: String,
    val spokenResponse: String,
    val calculationSteps: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
