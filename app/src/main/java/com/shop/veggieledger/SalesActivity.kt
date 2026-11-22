package com.shop.veggieledger

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivitySalesBinding
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class SalesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalesBinding
    private val db = FirebaseFirestore.getInstance()

    private val customerMap = HashMap<String, String>()
    private val customerNames = ArrayList<String>()
    private var selectedCustomerId: String = ""
    private var selectedCustomerName: String = ""

    private val itemNames = arrayListOf("Onion", "Garlic", "Coconut")
    private val matchingBatches = ArrayList<JSONObject>()
    private val batchDisplayList = ArrayList<String>()

    private val cartItems = ArrayList<JSONObject>()
    private var grandTotal = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Sales Entry"

        generateAutoSalesBillNo()

        val payModes = arrayListOf("Credit", "Cash", "GPay / UPI", "Bank Transfer")
        binding.spSalesPayMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, payModes)

        loadCustomers()
        loadItems()

        binding.spSalesItem.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                val item = binding.spSalesItem.selectedItem?.toString() ?: ""
                binding.layoutSalesOnion.visibility = if (item.equals("Onion", ignoreCase = true)) View.VISIBLE else View.GONE
                loadBatchesForItem(item)
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.spSupplierBatch.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (pos in matchingBatches.indices) {
                    val b = matchingBatches[pos]
                    val bags = b.getLong("availableBags")
                    val kgs = b.getDouble("availableKgs")
                    binding.tvLiveStockBanner.text = "Available Stock: $bags Bags | %.2f Kgs".format(kgs)
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.btnAddSalesItem.setOnClickListener {
            val itemName = binding.spSalesItem.selectedItem?.toString() ?: return@setOnClickListener
            val batchIdx = binding.spSupplierBatch.selectedItemPosition
            if (batchIdx !in matchingBatches.indices) {
                Toast.makeText(this, "No stock lot selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedBatch = matchingBatches[batchIdx]
            val batchDocId = selectedBatch.getString("docId")
            val batchCode = selectedBatch.getString("batchId")
            val availKgs = selectedBatch.getDouble("availableKgs")

            val netKgs = binding.etSalesNetKgs.text.toString().toDoubleOrNull() ?: 0.0
            val ratePerKg = binding.etSalesRatePerKg.text.toString().toDoubleOrNull() ?: 0.0
            val isOnion = itemName.equals("Onion", ignoreCase = true)
            val bags = if (isOnion) (binding.etSalesBags.text.toString().toIntOrNull() ?: 0) else 0
            val bagRate = if (isOnion) (binding.etSalesBagRate.text.toString().toDoubleOrNull() ?: 10.0) else 0.0

            if (netKgs <= 0.0 || ratePerKg <= 0.0) {
                Toast.makeText(this, "Enter valid Net Kgs and Sales Rate", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (netKgs > availKgs) {
                Toast.makeText(this, "Insufficient stock! Available: $availKgs Kgs", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val lineTotal = (netKgs * ratePerKg) + (bags * bagRate)

            val row = JSONObject()
            row.put("itemName", itemName)
            row.put("batchDocId", batchDocId)
            row.put("batchCode", batchCode)
            row.put("bags", bags)
            row.put("bagRate", bagRate)
            row.put("netKgs", netKgs)
            row.put("ratePerKg", ratePerKg)
            row.put("lineTotal", lineTotal)

            cartItems.add(row)
            refreshCartDisplay()

            binding.etSalesBags.setText("")
            binding.etSalesNetKgs.setText("")
            binding.etSalesRatePerKg.setText("")
        }

        binding.btnSaveSalesBill.setOnClickListener {
            val typedCustomer = binding.actvCustomer.text.toString().trim()
            if (typedCustomer.isEmpty() || !customerMap.containsKey(typedCustomer)) {
                Toast.makeText(this, "Please search and select a valid Customer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (cartItems.isEmpty()) {
                Toast.makeText(this, "Add at least one item to sales bill", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            selectedCustomerName = typedCustomer
            selectedCustomerId = customerMap[typedCustomer] ?: ""
            val billNo = binding.etSalesBillNo.text.toString().trim()
            val payMode = binding.spSalesPayMode.selectedItem.toString()
            val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

            val itemsForDb = ArrayList<HashMap<String, Any>>()
            for (row in cartItems) {
                val map = HashMap<String, Any>()
                map["itemName"] = row.getString("itemName")
                map["batchDocId"] = row.getString("batchDocId")
                map["batchCode"] = row.getString("batchCode")
                map["bags"] = row.getInt("bags")
                map["bagRate"] = row.getDouble("bagRate")
                map["netKgs"] = row.getDouble("netKgs")
                map["ratePerKg"] = row.getDouble("ratePerKg")
                map["lineTotal"] = row.getDouble("lineTotal")
                itemsForDb.add(map)
            }

            val salesId = db.collection("sales").document().id
            val salesData = hashMapOf(
                "id" to salesId,
                "billNo" to billNo,
                "customerId" to selectedCustomerId,
                "customerName" to selectedCustomerName,
                "payMode" to payMode,
                "date" to today,
                "timestamp" to System.currentTimeMillis(),
                "items" to itemsForDb,
                "grandTotal" to grandTotal
            )

            db.collection("sales").document(salesId).set(salesData)
                .addOnSuccessListener {
                    deductStockAndUpdateCustomer(payMode)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to save sales bill", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun generateAutoSalesBillNo() {
        db.collection("sales").get().addOnSuccessListener { snap ->
            val nextNum = snap.size() + 1
            binding.etSalesBillNo.setText(String.format("SAL-%03d", nextNum))
        }
    }

    private fun loadCustomers() {
        db.collection("customers").get().addOnSuccessListener { snap ->
            customerMap.clear()
            customerNames.clear()
            for (doc in snap) {
                val id = doc.id
                val name = doc.getString("name") ?: continue
                customerMap[name] = id
                customerNames.add(name)
            }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customerNames)
            binding.actvCustomer.setAdapter(adapter)
            binding.actvCustomer.setText("")
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
            binding.spSalesItem.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, itemNames)
        }
    }

    private fun loadBatchesForItem(itemName: String) {
        db.collection("stock_batches")
            .whereEqualTo("itemName", itemName)
            .get()
            .addOnSuccessListener { snap ->
                matchingBatches.clear()
                batchDisplayList.clear()
                for (doc in snap) {
                    val availKgs = doc.getDouble("availableKgs") ?: 0.0
                    if (availKgs > 0) {
                        val bId = doc.getString("batchId") ?: doc.id
                        val supName = doc.getString("supplierName") ?: ""
                        val availBags = doc.getLong("availableBags") ?: 0L
                        val obj = JSONObject()
                        obj.put("docId", doc.id)
                        obj.put("batchId", bId)
                        obj.put("availableBags", availBags)
                        obj.put("availableKgs", availKgs)
                        matchingBatches.add(obj)
                        batchDisplayList.add("$bId ($supName) - %.1f Kgs".format(availKgs))
                    }
                }
                if (batchDisplayList.isEmpty()) {
                    batchDisplayList.add("No Stock Available")
                    binding.tvLiveStockBanner.text = "Available Stock: 0 Bags | 0.0 Kgs"
                }
                binding.spSupplierBatch.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, batchDisplayList)
            }
    }

    private fun refreshCartDisplay() {
        grandTotal = 0.0
        val sb = StringBuilder()
        for (idx in cartItems.indices) {
            val row = cartItems[idx]
            val name = row.getString("itemName")
            val bags = row.getInt("bags")
            val kgs = row.getDouble("netKgs")
            val rate = row.getDouble("ratePerKg")
            val total = row.getDouble("lineTotal")
            grandTotal += total

            val bagInfo = if (bags > 0) " ($bags Bags)" else ""
            sb.append("${idx + 1}. $name$bagInfo: $kgs Kgs x Rs.$rate = Rs.%.2f\n".format(total))
        }
        binding.tvSalesCartList.text = if (sb.isEmpty()) "No items added to bill yet." else sb.toString().trim()
        binding.tvSalesGrandTotal.text = "Grand Total: Rs. %.2f".format(grandTotal)
    }

    private fun deductStockAndUpdateCustomer(payMode: String) {
        for (row in cartItems) {
            val docId = row.getString("batchDocId")
            val soldBags = row.getInt("bags")
            val soldKgs = row.getDouble("netKgs")

            val batchRef = db.collection("stock_batches").document(docId)
            db.runTransaction { tr ->
                val snap = tr.get(batchRef)
                val currBags = snap.getLong("availableBags") ?: 0L
                val currKgs = snap.getDouble("availableKgs") ?: 0.0
                tr.update(batchRef, mapOf(
                    "availableBags" to (currBags - soldBags).coerceAtLeast(0L),
                    "availableKgs" to (currKgs - soldKgs).coerceAtLeast(0.0)
                ))
            }
        }

        if (payMode.equals("Credit", ignoreCase = true) && selectedCustomerId.isNotEmpty()) {
            val custRef = db.collection("customers").document(selectedCustomerId)
            db.runTransaction { tr ->
                val snap = tr.get(custRef)
                val curr = snap.getDouble("currentBalance") ?: 0.0
                tr.update(custRef, "currentBalance", curr + grandTotal)
            }
        }

        Toast.makeText(this, "Sales Bill Saved & Stock Updated!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
