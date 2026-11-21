package com.shop.veggieledger

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityCustomerMasterBinding

class CustomerMasterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerMasterBinding
    private val db = FirebaseFirestore.getInstance()
    private val customerList = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerMasterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Customer Registration"

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, customerList)
        binding.lvCustomers.adapter = adapter

        // Customers பட்டியலை Firestore-லிருந்து லோட் செய்தல்
        loadCustomers()

        // SUBMIT DETAILS பொத்தான் கிளிக் செய்யும் போது
        binding.btnSaveCustomer.setOnClickListener {
            val name = binding.etCustName.text.toString().trim()
            val phone = binding.etCustPhone.text.toString().trim()
            val area = binding.etCustArea.text.toString().trim()
            val address = binding.etCustAddress.text.toString().trim()
            val opBalStr = binding.etCustOpBal.text.toString().trim()
            val opBal = opBalStr.toDoubleOrNull() ?: 0.0

            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter Customer Name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // வாடிக்கையாளர் புதிய ID
            val custId = db.collection("customers").document().id
            val customerData = hashMapOf(
                "id" to custId,
                "name" to name,
                "phone" to phone,
                "area" to area,
                "address" to address,
                "openingBalance" to opBal,
                "currentBalance" to opBal
            )

            db.collection("customers").document(custId).set(customerData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Customer Registered Successfully!", Toast.LENGTH_SHORT).show()
                    clearFields()
                    loadCustomers()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to register customer", Toast.LENGTH_SHORT).show()
                }
        }

        // RESET / CLEAR பொத்தான் கிளிக் செய்யும் போது
        binding.btnCancel.setOnClickListener {
            clearFields()
        }
    }

    private fun loadCustomers() {
        db.collection("customers").get().addOnSuccessListener { snap ->
            customerList.clear()
            for (doc in snap) {
                val name = doc.getString("name") ?: ""
                val area = doc.getString("area") ?: ""
                val balance = doc.getDouble("currentBalance") ?: 0.0
                val displayStr = if (area.isNotEmpty()) {
                    "$name ($area) - Bal: Rs.$balance"
                } else {
                    "$name - Bal: Rs.$balance"
                }
                customerList.add(displayStr)
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun clearFields() {
        binding.etCustName.setText("")
        binding.etCustPhone.setText("")
        binding.etCustArea.setText("")
        binding.etCustAddress.setText("")
        binding.etCustOpBal.setText("")
    }
}
