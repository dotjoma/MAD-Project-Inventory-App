package com.example.inventory

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ItemAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var fabAddItem: FloatingActionButton
    private lateinit var etSearch: TextInputEditText
    private lateinit var btnFilter: MaterialButton
    private lateinit var notificationHelper: NotificationHelper

    private var isLowStockFilterActive = false
    private var allItems: List<Item> = emptyList()

    private val supabase = createSupabaseClient(
        supabaseUrl = "https://nrvouvckdkdcjegeklrc.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5ydm91dmNrZGtkY2plZ2VrbHJjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDc3OTY4MzgsImV4cCI6MjA2MzM3MjgzOH0.5QLum1FsQliv8Wdv9Ito0b_LuLhtsMv8lCfzcH7-rjI"
    ) {
        install(Postgrest)
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
        })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize notification helper
        notificationHelper = NotificationHelper(this)

        // Request notification permission for Android 13 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))

        // Initialize views
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        fabAddItem = findViewById(R.id.fabAddItem)
        etSearch = findViewById(R.id.etSearch)
        btnFilter = findViewById(R.id.btnFilter)

        // Setup RecyclerView
        adapter = ItemAdapter(
            onItemClick = { item ->
                val intent = Intent(this, ManageItemActivity::class.java).apply {
                    putExtra("item_id", item.id)
                }
                startActivity(intent)
            },
            onReorderClick = { item -> showReorderDialog(item) },
            onCancelReorderClick = { item -> showCancelReorderDialog(item) },
            onDeleteClick = { item -> showDeleteDialog(item) }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        // Setup FAB
        fabAddItem.setOnClickListener {
            startActivity(Intent(this, ManageItemActivity::class.java))
        }

        // Setup search
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterItems()
            }
        })

        // Setup filter button
        btnFilter.setOnClickListener {
            isLowStockFilterActive = !isLowStockFilterActive
            btnFilter.text = if (isLowStockFilterActive) "All Items" else "Low Stock"
            filterItems()
        }

        // Load items
        loadItems()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun filterItems() {
        val searchQuery = etSearch.text.toString().trim().lowercase()
        val filteredItems = allItems.filter { item ->
            val matchesSearch = item.name.lowercase().contains(searchQuery)
            val matchesFilter = !isLowStockFilterActive || item.quantity <= 10
            matchesSearch && matchesFilter
        }
        adapter.updateItems(filteredItems)
    }

    private fun loadItems() {
        val sharedPref = this@MainActivity.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        val userId = sharedPref.getInt("userId", -1)

        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                
                // Get all items
                val items = supabase.postgrest["inventory_items"]
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<Item>()

                // Get all pending reorders
                val reorders = supabase.postgrest["reorders"]
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<Reorder>()

                // Check for items that are no longer low in stock and remove their reorders
                for (item in items) {
                    if (item.quantity > 10) {
                        val itemReorder = reorders.find { it.item_id == item.id }
                        if (itemReorder != null) {
                            // Delete the reorder if item is no longer low in stock
                            supabase.postgrest["reorders"]
                                .delete {
                                    filter {
                                        eq("item_id", item.id!!)
                                        eq("user_id", userId)
                                    }
                                }
                        }
                    }
                }

                // Get updated reorders after deletion
                val updatedReorders = supabase.postgrest["reorders"]
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<Reorder>()

                // Update items with reorder status and date
                allItems = items.map { item ->
                    val reorder = updatedReorders.find { it.item_id == item.id }
                    item.copy(
                        hasPendingReorder = reorder != null,
                        reorderDate = reorder?.reorder_date
                    )
                }

                filterItems()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading items: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun getUserId(): Int {
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        return sharedPref.getInt("userId", -1)
    }

    private fun logout() {
        // Clear user session
        getSharedPreferences("UserSession", MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        // Navigate to login screen
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (getUserId() != -1) {
            loadItems()
        } else {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showReorderDialog(item: Item) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reorder, null)
        val etDate = dialogView.findViewById<TextInputEditText>(R.id.etDate)
        val etNotes = dialogView.findViewById<TextInputEditText>(R.id.etNotes)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSave)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)

        // Setup date picker
        val calendar = Calendar.getInstance()
        etDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    etDate.setText(dateFormat.format(calendar.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnSave.setOnClickListener {
            val date = etDate.text.toString()
            val notes = etNotes.text.toString().trim()

            if (date.isEmpty()) {
                etDate.error = "Select a date"
                return@setOnClickListener
            }

            saveReorder(item, date, notes)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveReorder(item: Item, date: String, notes: String) {
        if (item.id == null) {
            Toast.makeText(this, "Error: Item ID is missing", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = getUserId()
        if (userId == -1) {
            Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                val reorder = Reorder(
                    item_id = item.id,
                    user_id = userId,
                    reorder_date = date,
                    notes = notes.takeIf { it.isNotEmpty() }
                )

                supabase.postgrest["reorders"]
                    .insert(reorder)

                // Refresh the items list
                loadItems()
                
                // Check if the reorder date is today using SimpleDateFormat
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                if (date == today) {
                    notificationHelper.showReorderNotification(item.name, item.id)
                }
                
                // Only check reorder dates if there are items with reorder dates
                if (allItems.any { it.hasPendingReorder }) {
                    startService(Intent(this@MainActivity, ReorderCheckService::class.java))
                }
                
                Toast.makeText(this@MainActivity, "Reorder reminder set successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error setting reorder reminder: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun showCancelReorderDialog(item: Item) {
        AlertDialog.Builder(this)
            .setTitle("Cancel Reorder")
            .setMessage("Are you sure you want to cancel the reorder for ${item.name}?")
            .setPositiveButton("Yes") { _, _ ->
                cancelReorder(item)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun cancelReorder(item: Item) {
        val userId = getUserId()
        if (userId == -1) {
            Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                supabase.postgrest["reorders"]
                    .delete {
                        filter {
                            eq("item_id", item.id!!)
                            eq("user_id", userId)
                        }
                    }
                
                // Refresh the items list
                loadItems()
                Toast.makeText(this@MainActivity, "Reorder cancelled successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error cancelling reorder: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun showDeleteDialog(item: Item) {
        AlertDialog.Builder(this)
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete ${item.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteItem(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteItem(item: Item) {
        if (item.id == null) {
            Toast.makeText(this, "Error: Item ID is missing", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = getUserId()
        if (userId == -1) {
            Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                
                // First delete any associated reorders
                supabase.postgrest["reorders"]
                    .delete {
                        filter {
                            eq("item_id", item.id)
                            eq("user_id", userId)
                        }
                    }

                // Then delete the item
                supabase.postgrest["inventory_items"]
                    .delete {
                        filter {
                            eq("id", item.id)
                            eq("user_id", userId)
                        }
                    }
                
                // Refresh the items list
                loadItems()
                Toast.makeText(this@MainActivity, "Item deleted successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error deleting item: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}