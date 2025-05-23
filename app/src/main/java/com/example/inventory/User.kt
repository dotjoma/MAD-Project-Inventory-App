package com.example.inventory

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int? = 0,
    val username: String,
    val password: String
)