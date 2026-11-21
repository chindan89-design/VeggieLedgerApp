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

        // Setup Group Dropdown (Group A, B, C, D)
        val groupOptions = listOf("A", "B", "C", "D")
        val groupAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, groupOptions)
        binding.spCustGroup.adapter = groupAdapter

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, customerList)
        binding.lvCustomers.adapter = adapter

        loadCustomers()

        binding.btnSaveCustomer.setOnClickListener {
            val name = binding.etCustName.text.toString().trim()
            val phone = binding.etCustPhone.text.toString().trim()
            val groupName = binding.spCustGroup.selectedItem.toString()
            val area = binding.etCustArea.text.toString().trim()
            val address = binding.etCustAddress.text.toString().trim()
            val opBalStr = binding.etCustOpBal.text.toString().trim()
            val opBal = opBalStr.toDoubleOrNull() ?: 0.0

            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter Customer Name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val custId = db.collection("customers").document().id
            val customerData = hashMapOf(
                "id" to custId,
                "name" to name,
                "phone" to phone,
                "groupName" to groupName,
                "area" to area,
                "address" to address,
                "openingBalance" to opBal,
                "currentBalance" to opBal
            )

            db.collection("customers").document(custId).set(customerData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Customer Registered ($groupName)!", Toast.LENGTH_SHORT).show()
                    clearFields()
                    loadCustomers()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to register customer", Toast.LENGTH_SHORT).show()
                }
        }

        binding.btnCancel.setOnClickListener {
            clearFields()
        }
    }

    private fun loadCustomers() {
        db.collection("customers").get().addOnSuccessListener { snap ->
            customerList.clear()
            for (doc in snap) {
                val name = doc.getString("name") ?: ""
                val group = doc.getString("groupName") ?: "Group A"
                val area = doc.getString("area") ?: ""
                val balance = doc.getDouble("currentBalance") ?: 0.0
                val areaText = if (area.isNotEmpty()) " | $area" else ""
                customerList.add("$name [$group]$areaText - Bal: Rs.%.2f".format(balance))
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun clearFields() {
        binding.etCustName.setText("")
        binding.etCustPhone.setText("")
        binding.spCustGroup.setSelection(0)
        binding.etCustArea.setText("")
        binding.etCustAddress.setText("")
        binding.etCustOpBal.setText("")
    }
}
