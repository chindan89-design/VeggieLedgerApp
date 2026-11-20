package com.shop.veggieledger

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val user = binding.etUsername.text.toString().trim().lowercase()
            val pin = binding.etPin.text.toString().trim()

            if (user.isEmpty() || pin.length < 4) {
                Toast.makeText(this, "Enter valid username and 4-digit PIN", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Quick default login + Firebase user check
            if ((user == "admin" && pin == "1234") || (user == "staff" && pin == "1111")) {
                openDashboard(user.uppercase())
            } else {
                db.collection("users")
                    .whereEqualTo("username", user)
                    .whereEqualTo("pin", pin)
                    .get()
                    .addOnSuccessListener { docs ->
                        if (!docs.isEmpty) {
                            val role = docs.documents[0].getString("role") ?: "STAFF"
                            openDashboard(role)
                        } else {
                            Toast.makeText(this, "Invalid PIN!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Login Error: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun openDashboard(role: String) {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.putExtra("USER_ROLE", role)
        startActivity(intent)
        finish()
    }
}
