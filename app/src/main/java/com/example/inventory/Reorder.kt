package com.example.inventory

import kotlinx.serialization.Serializable

@Serializable
data class Reorder(
    val id: Int? = null,
    val item_id: Int,
    val user_id: Int,
    val reorder_date: String,
    val notes: String? = null
) 