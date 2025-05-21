package com.example.inventory

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Serializable
data class Item(
    val id: Int? = null,
    val name: String,
    val quantity: Int,
    val price: Double,
    val user_id: Int? = null,
    val hasPendingReorder: Boolean = false,
    val reorderDate: String? = null
) {
    val isLowStock: Boolean
        get() = quantity <= 10

    val isReorderDateReached: Boolean
        @RequiresApi(Build.VERSION_CODES.O)
        get() = reorderDate?.let { date ->
            val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            date <= today
        } ?: false
} 