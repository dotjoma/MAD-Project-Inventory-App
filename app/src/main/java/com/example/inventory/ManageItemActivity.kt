package com.example.inventory

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ManageItemActivity : AppCompatActivity() {
    private lateinit var nameInputLayout: TextInputLayout
    private lateinit var quantityInputLayout: TextInputLayout
    private lateinit var priceInputLayout: TextInputLayout
    private lateinit var etName: TextInputEditText
    private lateinit var etQuantity: TextInputEditText
    private lateinit var etPrice: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var btnDelete: MaterialButton
    private lateinit var progressBar: ProgressBar

    private var itemId: Int? = null
    private var isEditMode = false

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
        setContentView(R.layout.activity_manage_item)

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        // Setup navigation icon click listener
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        // Initialize views
        nameInputLayout = findViewById(R.id.nameInputLayout)
        quantityInputLayout = findViewById(R.id.quantityInputLayout)
        priceInputLayout = findViewById(R.id.priceInputLayout)
        etName = findViewById(R.id.etName)
        etQuantity = findViewById(R.id.etQuantity)
        etPrice = findViewById(R.id.etPrice)
        btnSave = findViewById(R.id.btnSave)
        btnDelete = findViewById(R.id.btnDelete)
        progressBar = findViewById(R.id.progressBar)

        // Get item ID if in edit mode
        itemId = intent.getIntExtra("item_id", -1).takeIf { it != -1 }
        isEditMode = itemId != null

        // Update UI based on mode
        supportActionBar?.title = if (isEditMode) "Edit Item" else "Add Item"
        btnDelete.visibility = if (isEditMode) View.VISIBLE else View.GONE

        // Load item data if in edit mode
        if (isEditMode) {
            loadItemData()
        }

        // Setup save button
        btnSave.setOnClickListener {
            saveItem()
        }

        // Setup delete button
        btnDelete.setOnClickListener {
            showDeleteDialog()
        }
    }

    private fun showDeleteDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete this item?")
            .setPositiveButton("Delete") { _, _ ->
                deleteItem()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteItem() {
        if (itemId == null) {
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
                            eq("item_id", itemId!!)
                            eq("user_id", userId)
                        }
                    }

                // Then delete the item
                supabase.postgrest["inventory_items"]
                    .delete {
                        filter {
                            eq("id", itemId!!)
                            eq("user_id", userId)
                        }
                    }
                
                Toast.makeText(this@ManageItemActivity, "Item deleted successfully", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@ManageItemActivity, "Error deleting item: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadItemData() {
        showLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val item = fetchItem(itemId!!)
                withContext(Dispatchers.Main) {
                    item?.let { populateFields(it) }
                    showLoading(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@ManageItemActivity, "Error loading item: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private suspend fun fetchItem(id: Int): Item? {
        return supabase.postgrest["inventory_items"]
            .select {
                filter {
                    eq("id", id)
                }
            }
            .decodeSingleOrNull()
    }

    private fun populateFields(item: Item) {
        etName.setText(item.name)
        etQuantity.setText(item.quantity.toString())
        etPrice.setText(item.price.toString())
    }

    private fun saveItem() {
        showLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val item = createItemObject()
                if (isEditMode) {
                    updateItem(item)
                } else {
                    insertItem(item)
                }
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@ManageItemActivity, "Item saved successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@ManageItemActivity, "Error saving item: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createItemObject(): Item {
        return Item(
            id = itemId,
            name = etName.text?.toString()?.trim() ?: "",
            quantity = etQuantity.text?.toString()?.toInt() ?: 0,
            price = etPrice.text?.toString()?.toDouble() ?: 0.0,
            user_id = getUserId()
        )
    }

    private suspend fun insertItem(item: Item) {
        supabase.postgrest["inventory_items"]
            .insert(item)
    }

    private suspend fun updateItem(item: Item) {
        supabase.postgrest["inventory_items"]
            .update(item) {
                filter {
                    eq("id", item.id!!)
                }
            }
    }

    private fun getUserId(): Int {
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        return sharedPref.getInt("userId", -1)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSave.isEnabled = !show
    }
}