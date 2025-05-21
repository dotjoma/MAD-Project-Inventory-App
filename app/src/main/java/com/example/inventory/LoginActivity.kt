package com.example.inventory

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
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

class LoginActivity : AppCompatActivity() {
    private lateinit var btnRegister: TextView
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var usernameInputLayout: TextInputLayout
    private lateinit var passwordInputLayout: TextInputLayout

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
        setContentView(R.layout.activity_login)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        usernameInputLayout = findViewById(R.id.emailInputLayout)
        passwordInputLayout = findViewById(R.id.passwordInputLayout)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnRegister = findViewById(R.id.btnSignup)

        // Add login button click listener
        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            Login()
        }
    }

    private fun setupClickListeners() {
        btnRegister.setOnClickListener { 
            startActivity(Intent(this, RegisterActivity::class.java)) 
        }

        findViewById<TextView>(R.id.forgotPasswordText).setOnClickListener {
            Toast.makeText(this, "Forgot password functionality coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    @OptIn(UnstableApi::class) private fun Login() {
        val username = etUsername.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString()?.trim() ?: ""

        // Reset errors
        usernameInputLayout.error = null
        passwordInputLayout.error = null

        // Validate inputs
        var isValid = true

        if (username.isEmpty()) {
            usernameInputLayout.error = "Username is required"
            isValid = false
        }

        if (password.isEmpty()) {
            passwordInputLayout.error = "Password is required"
            isValid = false
        }

        if (!isValid) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = loginUser(username, password)

                withContext(Dispatchers.Main) {
                    if (user != null) {
                        saveLoginState(this@LoginActivity, user)

                        Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                        Log.d("Login", "Login successful for $username")

                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    } else {
                        Log.d("LOGIN", "Login failed. Please check your credentials.")
                        Toast.makeText(this@LoginActivity, "Login failed. Please check your credentials.", Toast.LENGTH_SHORT).show()
                        passwordInputLayout.error = "Invalid username or password"
                    }
                }
            } catch (e: Exception) {
                Log.d("LOGIN", "Failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "An error occurred: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @OptIn(UnstableApi::class) suspend fun loginUser(username: String, password: String): User? {
        return try {
            val response = supabase.postgrest["users"]
                .select {
                    filter {
                        eq("username", username)
                    }
                }
                .decodeList<User>()

            Log.d("LOGIN_RESPONSE", "Response size: ${response.size}")

            if (response.isNotEmpty()) {
                val user = response.first()

                if (user.password == password) {
                    Log.d("LOGIN_STATUS", "Password match successful")
                    user
                } else {
                    Log.d("LOGIN_STATUS", "Password match failed")
                    null
                }
            } else {
                Log.d("LOGIN_STATUS", "No user found with username: $username")
                null
            }
        } catch (e: Exception) {
            Log.d("LOGIN_USER", "Login error: ${e.message}")
            null
        }
    }

    fun saveLoginState(context: Context, user: User) {
        val sharedPref = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isLoggedIn", true)
            putString("username", user.username)
            user.id?.let { putInt("userId", it) }
            apply()
        }
    }
}