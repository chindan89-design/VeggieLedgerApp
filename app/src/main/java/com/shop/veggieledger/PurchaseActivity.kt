package com.shop.veggieledger

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityPurchaseBinding
import java.text.SimpleDateFormat
import java.util.*

class PurchaseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPurchaseBinding
    private val db = FirebaseFirestore.getInstance()

    private val supplierMap = HashMap<String, String>()
    private val supplierNames = ArrayList<String>()
    private var selectedSupplierId: String = ""
    private var selectedSupplierName: String = ""

    private val itemNames = arrayListOf("Onion", "Garlic", "Coconut")
    private val cartItems = ArrayList>()
    private var grandTotal = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurchaseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Purchase Entry"

        generateAutoBillAndBatch()

        val payModes = arrayListOf("Credit", "Cash", "GPay / UPI", "Bank Transfer")
        binding.spPayMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, payModes)

        loadSuppliers()
        loadItems()

        binding.spItem.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                val item = binding.spItem.selectedItem?.toString() ?: ""
                binding.layoutOnionFields.visibility = if (item.equals("Onion", ignoreCase = true)) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.btnAddItem.setOnClickListener {
            val itemName = binding.spItem.selectedItem?.toString() ?: return@setOnClickListener
            val netKgs = binding.etNetKgs.text.toString().toDoubleOrNull() ?: 0.0
            val ratePerKg = binding.etRatePerKg.text.toString().toDoubleOrNull() ?: 0.0

            val isOnion = itemName.equals("Onion", ignoreCase = true)
            val bags = if (isOnion) (binding.etBags.text.toString().toIntOrNull() ?: 0) else 0
            val bagRate = if (isOnion) (binding.etBagRate.text.toString().toDoubleOrNull() ?: 10.0) else 0.0

            if (netKgs <= 0.0 || ratePerKg <= 0.0) {
                Toast.makeText(this, "Enter valid Net Kgs and Rate/Kg", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val lineTotal = (netKgs * ratePerKg) + (bags * bagRate)

            val row = HashMap<String, Any>()
            row["itemName"] = itemName
            row["bags"] = bags
            row["bagRate"] = bagRate
            row["netKgs"] = netKgs
            row["ratePerKg"] = ratePerKg
            row["lineTotal"] = lineTotal

            cartItems.add(row)
            refreshCartDisplay()

            binding.etBags.setText("")
            binding.etNetKgs.setText("")
            binding.etRatePerKg.setText("")
        }

        binding.btnSavePurchase.setOnClickListener {
            val typedSupplier = binding.actvSupplier.text.toString().trim()
            if (typedSupplier.isEmpty() || !supplierMap.containsKey(typedSupplier)) {
                Toast.makeText(this, "Please search and select a valid Supplier", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (cartItems.isEmpty()) {
                Toast.makeText(this, "Add at least one item to bill", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            selectedSupplierName = typedSupplier
            selectedSupplierId = supplierMap[typedSupplier] ?: ""
            val billNo = binding.etBillNo.text.toString().trim()
            val batchId = binding.tvGeneratedBatchId.text.toString().trim()
            val payMode = binding.spPayMode.selectedItem.toString()
            val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

            val purchaseId = db.collection("purchases").document().id
            val purchaseData = hashMapOf(
                "id" to purchaseId,
                "billNo" to billNo,
                "batchId" to batchId,
                "supplierId" to selectedSupplierId,
                "supplierName" to selectedSupplierName,
                "payMode" to payMode,
                "date" to today,
                "timestamp" to System.currentTimeMillis(),
                "items" to cartItems,
                "grandTotal" to grandTotal
            )

            db.collection("purchases").document(purchaseId).set(purchaseData)
                .addOnSuccessListener {
                    updateStockAndSupplierBalance(batchId, payMode)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to save purchase", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun generateAutoBillAndBatch() {
        db.collection("purchases").get().addOnSuccessListener { snap ->
            val nextNum = snap.size() + 1
            binding.etBillNo.setText(String.format("PUR-%03d", nextNum))
        }
        val shortTime = SimpleDateFormat("ddMM-HHmm", Locale.getDefault()).format(Date())
        binding.tvGeneratedBatchId.text = "LOT-$shortTime"
    }

    private fun loadSuppliers() {
        db.collection("suppliers").get().addOnSuccessListener { snap ->
            supplierMap.clear()
            supplierNames.clear()
            for (doc in snap) {
                val id = doc.id
                val name = doc.getString("name") ?: continue
                supplierMap[name] = id
                supplierNames.add(name)
            }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, supplierNames)
            binding.actvSupplier.setAdapter(adapter)
            binding.actvSupplier.setText("")
        }
    }

    private fun loadItems() {
        db.collection("items").get().addOnSuccessListener { snap ->
            if (!snap.isEmpty) {
                itemNames.clear()
                for (doc in snap) {
                    doc.getString("name")?.let { itemNames.add(it) }
                }
            }
            binding.spItem.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, itemNames)
        }
    }

    private fun refreshCartDisplay() {
        grandTotal = 0.0
        val sb = StringBuilder()
        for (idx in cartItems.indices) {
            val row = cartItems[idx]
            val name = row["itemName"] as String
            val bags = row["bags"] as Int
            val kgs = row["netKgs"] as Double
            val rate = row["ratePerKg"] as Double
            val total = row["lineTotal"] as Double
            grandTotal += total

            val bagInfo = if (bags > 0) " ($bags Bags)" else ""
            sb.append("${idx + 1}. $name$bagInfo: $kgs Kgs x Rs.$rate = Rs.%.2f\n".format(total))
        }
        binding.tvAddedItemsList.text = if (sb.isEmpty()) "No items added yet." else sb.toString().trim()
        binding.tvGrandTotal.text = "Grand Total: Rs. %.2f".format(grandTotal)
    }

    private fun updateStockAndSupplierBalance(batchId: String, payMode: String) {
        for (row in cartItems) {
            val itemName = row["itemName"] as String
            val bags = row["bags"] as Int
            val kgs = row["netKgs"] as Double
            val rate = row["ratePerKg"] as Double

            val batchDoc = db.collection("stock_batches").document()
            val batchData = hashMapOf(
                "batchId" to batchId,
                "supplierId" to selectedSupplierId,
                "supplierName" to selectedSupplierName,
                "itemName" to itemName,
                "availableBags" to bags,
                "availableKgs" to kgs,
                "purchaseRate" to rate,
                "timestamp" to System.currentTimeMillis()
            )
            batchDoc.set(batchData)
        }

        if (payMode.equals("Credit", ignoreCase = true) && selectedSupplierId.isNotEmpty()) {
            val supRef = db.collection("suppliers").document(selectedSupplierId)
            db.runTransaction { tr ->
                val snap = tr.get(supRef)
                val curr = snap.getDouble("currentBalance") ?: 0.0
                tr.update(supRef, "currentBalance", curr + grandTotal)
            }
        }

        Toast.makeText(this, "Purchase Saved & Stock Added!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
