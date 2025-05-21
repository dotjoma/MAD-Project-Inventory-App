package com.example.inventory

import kotlinx.serialization.Serializable

@Serializable
data class Item(
    val id: Int? = null,
    val name: String,
    val quantity: Int,
    val price: Double,
    val user_id: Int? = null,
    val hasPendingReorder: Boolean = false
) {
    val isLowStock: Boolean
        get() = quantity <= 10
} 