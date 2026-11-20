package com.shop.veggieledger

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityCustomerMasterBinding

class CustomerMasterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerMasterBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerMasterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSaveCustomer.setOnClickListener {
            val name = binding.etCustName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Customer Name required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val id = db.collection("customers").document().id
            val opBal = binding.etCustOpBal.text.toString().toDoubleOrNull() ?: 0.0

            val customer = Customer(
                id = id,
                name = name,
                shopName = binding.etCustShop.text.toString().trim(),
                phone = binding.etCustPhone.text.toString().trim(),
                area = binding.etCustArea.text.toString().trim(),
                openingBalance = opBal,
                currentBalance = opBal
            )

            db.collection("customers").document(id).set(customer)
                .addOnSuccessListener {
                    Toast.makeText(this, "Customer Saved!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
