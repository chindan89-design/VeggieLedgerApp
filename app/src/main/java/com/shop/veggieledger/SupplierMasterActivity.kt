package com.shop.veggieledger

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivitySupplierMasterBinding

class SupplierMasterActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySupplierMasterBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupplierMasterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSaveSupplier.setOnClickListener {
            val name = binding.etSupName.text.toString().trim()
            val code = binding.etSupCode.text.toString().trim().uppercase()

            if (name.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "Name and Short Code required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val id = db.collection("suppliers").document().id
            val opBal = binding.etSupOpBal.text.toString().toDoubleOrNull() ?: 0.0

            val supplier = Supplier(
                id = id,
                name = name,
                shortCode = code,
                place = binding.etSupPlace.text.toString().trim(),
                phone = binding.etSupPhone.text.toString().trim(),
                openingBalance = opBal,
                currentBalance = opBal
            )

            db.collection("suppliers").document(id).set(supplier)
                .addOnSuccessListener {
                    Toast.makeText(this, "Supplier Saved ($code)!", Toast.LENGTH_SHORT).show()
                    finish()
                }
        }
    }
}
