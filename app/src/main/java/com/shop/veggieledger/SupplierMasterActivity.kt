package com.shop.veggieledger

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivitySupplierMasterBinding

class SupplierMasterActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySupplierMasterBinding
    private val db = FirebaseFirestore.getInstance()
    private val itemNames = arrayListOf("Onion", "Garlic", "Coconut")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupplierMasterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Supplier Master"

        // Auto Supplier Code
        db.collection("suppliers").get().addOnSuccessListener { snap ->
            val nextNum = snap.size() + 1
            binding.etSupCode.setText(String.format("SUP-%03d", nextNum))
        }

        // Load Item Dropdown
        val defaultAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, itemNames)
        defaultAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spSupItem.adapter = defaultAdapter

        loadItemsFromDb()

        binding.btnSaveSupplier.setOnClickListener {
            val name = binding.etSupName.text.toString().trim()
            val code = binding.etSupCode.text.toString().trim()
            val mappedItem = binding.spSupItem.selectedItem?.toString() ?: "Onion"
            val phone = binding.etSupPhone.text.toString().trim()
            val place = binding.etSupPlace.text.toString().trim()
            val opBal = binding.etSupOpBal.text.toString().toDoubleOrNull() ?: 0.0

            if (name.isEmpty()) {
                Toast.makeText(this, "Enter Supplier Name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val supId = db.collection("suppliers").document().id
            val supData = hashMapOf(
                "id" to supId,
                "code" to code,
                "name" to name,
                "mappedItem" to mappedItem,
                "phone" to phone,
                "contact" to phone,
                "place" to place,
                "openingBalance" to opBal,
                "currentBalance" to opBal,
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("suppliers").document(supId).set(supData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Supplier ($name - $mappedItem) Saved!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to save supplier", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadItemsFromDb() {
        db.collection("items").get().addOnSuccessListener { snap ->
            for (doc in snap) {
                val n = doc.getString("name") ?: doc.getString("itemName") ?: doc.getString("item")
                if (!n.isNullOrBlank() && itemNames.none { it.equals(n, ignoreCase = true) }) {
                    itemNames.add(n)
                }
            }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, itemNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spSupItem.adapter = adapter
        }
    }
}
