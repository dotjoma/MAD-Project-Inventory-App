package com.example.inventory

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ProfileActivity : AppCompatActivity() {
    private lateinit var tvUsername: TextView
    private lateinit var tvUserId: TextView
    private lateinit var tvTotalItems: TextView
    private lateinit var tvLowStockItems: TextView
    private lateinit var tvPendingReorders: TextView
    private lateinit var btnLogout: MaterialButton
    private lateinit var progressBar: ProgressBar

    private val supabase = createSupabaseClient(
        supabaseUrl = "https://nrvouvckdkdcjegeklrc.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5ydm91dmNrZGtkY2plZ2VrbHJjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDc3OTY4MzgsImV4cCI6MjA2MzM3MjgzOH0.5QLum1FsQliv8Wdv9Ito0b_LuLhtsMv8lCfzcH7-rjI"
    ) {
        install(Postgrest)
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        initViews()
        setupToolbar()
        loadUserData()
        setupLogoutButton()
    }

    private fun initViews() {
        tvUsername = findViewById(R.id.tvUsername)
        tvUserId = findViewById(R.id.tvUserId)
        tvTotalItems = findViewById(R.id.tvTotalItems)
        tvLowStockItems = findViewById(R.id.tvLowStockItems)
        tvPendingReorders = findViewById(R.id.tvPendingReorders)
        btnLogout = findViewById(R.id.btnLogout)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupToolbar() {
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { finish() }
        }
    }

    @OptIn(UnstableApi::class) @SuppressLint("SetTextI18n")
    private fun loadUserData() {
        showLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = getUserId()
                if (userId == -1) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Toast.makeText(this@ProfileActivity, "User not logged in", Toast.LENGTH_SHORT).show()
                        navigateToLogin()
                    }
                    return@launch
                }

                // Load user information
                val username = getSharedPreferences("UserSession", MODE_PRIVATE)
                    .getString("username", "Unknown User")

                // Load statistics
                val totalItems = fetchTotalItems(userId)
                val lowStockItems = fetchLowStockItems(userId)
                val pendingReorders = fetchPendingReorders(userId)

                withContext(Dispatchers.Main) {
                    tvUsername.text = "Username: $username"
                    tvUserId.text = "User ID: $userId"
                    tvTotalItems.text = "Total Items: $totalItems"
                    tvLowStockItems.text = "Low Stock Items: $lowStockItems"
                    tvPendingReorders.text = "Pending Reorders: $pendingReorders"
                    showLoading(false)
                }
            } catch (e: Exception) {
                Log.d("PROFILE", "Error loading profile: ${e.message}")
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@ProfileActivity, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun fetchTotalItems(userId: Int): Int {
        return supabase.postgrest["inventory_items"]
            .select {
                filter {
                    eq("user_id", userId)
                }
            }
            .decodeList<Item>()
            .size
    }

    private suspend fun fetchLowStockItems(userId: Int): Int {
        return supabase.postgrest["inventory_items"]
            .select {
                filter {
                    eq("user_id", userId)
                }
            }
            .decodeList<Item>()
            .count { it.isLowStock }
    }

    private suspend fun fetchPendingReorders(userId: Int): Int {
        return supabase.postgrest["reorders"]
            .select {
                filter {
                    eq("user_id", userId)
                }
            }
            .decodeList<Reorder>()
            .size
    }

    private fun setupLogoutButton() {
        btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun logout() {
        // Clear user session
        getSharedPreferences("UserSession", MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        // Navigate to login screen
        navigateToLogin()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun getUserId(): Int {
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        return sharedPref.getInt("userId", -1)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
} 