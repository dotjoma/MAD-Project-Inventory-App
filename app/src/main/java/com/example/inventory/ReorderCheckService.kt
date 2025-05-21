package com.example.inventory

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ReorderCheckService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var notificationHelper: NotificationHelper

    private val supabase = createSupabaseClient(
        supabaseUrl = "https://nrvouvckdkdcjegeklrc.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5ydm91dmNrZGtkY2plZ2VrbHJjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDc3OTY4MzgsImV4cCI6MjA2MzM3MjgzOH0.5QLum1FsQliv8Wdv9Ito0b_LuLhtsMv8lCfzcH7-rjI"
    ) {
        install(Postgrest)
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
        })
    }
    
    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            while (true) {
                checkReorderDates()
                delay(300000)
            }
        }
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun checkReorderDates() {
        try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
            
            // Get all reorders due today
            val reorders = supabase.postgrest["reorders"]
                .select {
                    filter {
                        eq("reorder_date", today)
                    }
                }
                .decodeList<Reorder>()

            // For each reorder, get the item details and show notification
            for (reorder in reorders) {
                val item = supabase.postgrest["inventory_items"]
                    .select {
                        filter {
                            eq("id", reorder.item_id)
                        }
                    }
                    .decodeSingle<Item>()

                notificationHelper.showReorderNotification(item.name, item.id!!)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 