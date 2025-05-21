package com.example.inventory

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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

class RegisterActivity : AppCompatActivity() {
    private lateinit var etPassword: TextInputEditText
    private lateinit var tvLengthRequirement: TextView
    private lateinit var tvSpecialCharRequirement: TextView
    private lateinit var tvUpperCaseRequirement: TextView
    private lateinit var tvLowerCaseRequirement: TextView
    private lateinit var btnLogin: TextView
    private lateinit var etUsername: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var usernameInputLayout: TextInputLayout
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var confirmPasswordInputLayout: TextInputLayout

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
        setContentView(R.layout.activity_register)

        initObj()

        // Add password validation
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePassword(s.toString())
            }
        })

        btnLogin = findViewById(R.id.btnSignin)
        btnLogin.setOnClickListener { finish() }

        // Add register button click listener
        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            Register()
        }
    }

    private fun initObj() {
        // Initialize views
        usernameInputLayout = findViewById(R.id.usernameInputLayout)
        passwordInputLayout = findViewById(R.id.passwordInputLayout)
        confirmPasswordInputLayout = findViewById(R.id.confirmPasswordInputLayout)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        tvLengthRequirement = findViewById(R.id.tvLengthRequirement)
        tvSpecialCharRequirement = findViewById(R.id.tvSpecialCharRequirement)
        tvUpperCaseRequirement = findViewById(R.id.tvUpperCaseRequirement)
        tvLowerCaseRequirement = findViewById(R.id.tvLowerCaseRequirement)
    }

    private fun isPasswordValid(password: String): Boolean {
        return password.length >= 8 &&
                password.any { !it.isLetterOrDigit() } &&
                password.any { it.isUpperCase() } &&
                password.any { it.isLowerCase() }
    }

    @OptIn(UnstableApi::class) private fun Register() {
        val username = etUsername.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString()?.trim() ?: ""
        val confirmPassword = etConfirmPassword.text?.toString()?.trim() ?: ""

        // Reset errors
        usernameInputLayout.error = null
        passwordInputLayout.error = null
        confirmPasswordInputLayout.error = null

        var isValid = true

        // Validate username
        if (username.isEmpty()) {
            usernameInputLayout.error = "Username is required"
            isValid = false
        } else if (username.length < 4) {
            usernameInputLayout.error = "Username must be at least 4 characters"
            isValid = false
        }

        // Validate password
        if (password.isEmpty()) {
            passwordInputLayout.error = "Password is required"
            isValid = false
        } else if (!isPasswordValid(password)) {
            passwordInputLayout.error = "Password doesn't meet requirements"
            isValid = false
        }

        // Validate password confirmation
        if (confirmPassword.isEmpty()) {
            confirmPasswordInputLayout.error = "Please confirm your password"
            isValid = false
        } else if (password != confirmPassword) {
            confirmPasswordInputLayout.error = "Passwords don't match"
            isValid = false
        }

        if (isValid) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val isTaken = isUsernameTaken(username)
                    if (isTaken) {
                        Log.d("USERNAME_RESPONSE", "Username is taken.")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@RegisterActivity, "Username already taken", Toast.LENGTH_SHORT).show()
                            usernameInputLayout.error = "Choose another one"
                            etUsername.requestFocus()
                        }
                        return@launch
                    }

                    val isInserted = insertUser(username, password)
                    withContext(Dispatchers.Main) {
                        if (isInserted) {
                            Toast.makeText(this@RegisterActivity, "Registration successful!", Toast.LENGTH_LONG).show()
                            Log.d("REGISTRATION", "Registration successful for $username")
                            finish()
                        } else {
                            Toast.makeText(this@RegisterActivity, "Registration failed. Please try again.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.d("REGISTRATION", "Failed: ${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RegisterActivity, "Unexpected error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    @OptIn(UnstableApi::class) suspend fun isUsernameTaken(username: String): Boolean {
        return try {
            val response = supabase.postgrest["users"]
                .select {
                    filter {
                        eq("username", username)
                    }
                }
                .decodeList<User>()

            response.isNotEmpty()
        } catch (e: Exception) {
            Log.d("USERNAME_CHECK", "Error checking username: ${e.message}")
            false
        }
    }

    @OptIn(UnstableApi::class) suspend fun insertUser(username: String, password: String): Boolean {
        return try {
            val userData = mapOf(
                "username" to username,
                "password" to password
            )

            supabase.postgrest["users"]
                .insert(userData)

            true
        } catch (e: Exception) {
            Log.d("INSERT_USER", "Error inserting user: ${e.message}")
            false
        }
    }

    private fun validatePassword(password: String) {
        // Check length requirement (8 characters)
        val hasMinLength = password.length >= 8
        updateRequirementStatus(tvLengthRequirement, hasMinLength)

        // Check special character requirement
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }
        updateRequirementStatus(tvSpecialCharRequirement, hasSpecialChar)

        // Check uppercase requirement
        val hasUpperCase = password.any { it.isUpperCase() }
        updateRequirementStatus(tvUpperCaseRequirement, hasUpperCase)

        // Check lowercase requirement
        val hasLowerCase = password.any { it.isLowerCase() }
        updateRequirementStatus(tvLowerCaseRequirement, hasLowerCase)
    }

    private fun updateRequirementStatus(textView: TextView, isMet: Boolean) {
        textView.setTextColor(if (isMet) Color.parseColor("#4CAF50") else Color.parseColor("#757575"))
    }
}