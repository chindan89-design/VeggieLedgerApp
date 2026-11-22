package com.shop.veggieledger

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivitySalesBinding
import org.json.JSONArray
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
    private var selectedDateStr: String = ""

    private val itemNames = arrayListOf("Onion", "Garlic", "Coconut")
    private var selectedItemName: String = "Onion"
    private val matchingBatches = JSONArray()
    private val batchDisplayList = ArrayList<String>()

    private val cartItems = JSONArray()
    private var grandTotal = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Sales Entry"

        // 1. Setup Date Picker
        val cal = Calendar.getInstance()
        selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        binding.etSalesDate.setText(selectedDateStr)
        binding.etSalesDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                binding.etSalesDate.setText(selectedDateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // 2. Setup Item Dropdown
        val defaultItemAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, itemNames)
        defaultItemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spSalesItem.adapter = defaultItemAdapter

        binding.spSalesItem.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedItemName = binding.spSalesItem.selectedItem?.toString() ?: "Onion"
                binding.layoutSalesOnion.visibility = if (selectedItemName.equals("Onion", ignoreCase = true)) View.VISIBLE else View.GONE
                loadBatchesForItem(selectedItemName)
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        generateAutoSalesBillNo()

        val payModes = arrayListOf("Credit", "Cash", "GPay / UPI", "Bank Transfer")
        val payAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, payModes)
        payAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spSalesPayMode.adapter = payAdapter

        binding.spSupplierBatch.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (pos >= 0 && pos < matchingBatches.length()) {
                    val b = matchingBatches.getJSONObject(pos)
                    val bags = b.getLong("availableBags")
                    val kgs = b.getDouble("availableKgs")
                    binding.tvLiveStockBanner.text = "Available Stock: $bags Bags | %.2f Kgs".format(kgs)
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        loadItemsFromDb()
        loadCustomers()
        loadBatchesForItem(selectedItemName)

        binding.btnAddSalesItem.setOnClickListener {
            val batchIdx = binding.spSupplierBatch.selectedItemPosition
            if (batchIdx < 0 || batchIdx >= matchingBatches.length()) {
                Toast.makeText(this, "No stock lot selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedBatch = matchingBatches.getJSONObject(batchIdx)
            val batchDocId = selectedBatch.getString("docId")
            val batchCode = selectedBatch.getString("batchId")
            val availKgs = selectedBatch.getDouble("availableKgs")

            val netKgs = binding.etSalesNetKgs.text.toString().toDoubleOrNull() ?: 0.0
            val ratePerKg = binding.etSalesRatePerKg.text.toString().toDoubleOrNull() ?: 0.0
            val isOnion = selectedItemName.equals("Onion", ignoreCase = true)
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
            row.put("itemName", selectedItemName)
            row.put("batchDocId", batchDocId)
            row.put("batchCode", batchCode)
            row.put("bags", bags)
            row.put("bagRate", bagRate)
            row.put("netKgs", netKgs)
            row.put("ratePerKg", ratePerKg)
            row.put("lineTotal", lineTotal)

            cartItems.put(row)
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
            if (cartItems.length() == 0) {
                Toast.makeText(this, "Add at least one item to sales bill", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            selectedCustomerName = typedCustomer
            selectedCustomerId = customerMap[typedCustomer] ?: ""
            val billNo = binding.etSalesBillNo.text.toString().trim()
            val payMode = binding.spSalesPayMode.selectedItem.toString()

            val itemsForDb = ArrayList<Any>()
            for (i in 0 until cartItems.length()) {
                val row = cartItems.getJSONObject(i)
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

            binding.btnSaveSalesBill.isEnabled = false

            val salesId = db.collection("sales").document().id
            val salesData = hashMapOf(
                "id" to salesId,
                "billNo" to billNo,
                "customerId" to selectedCustomerId,
                "customerName" to selectedCustomerName,
                "payMode" to payMode,
                "date" to selectedDateStr,
                "timestamp" to System.currentTimeMillis(),
                "items" to itemsForDb,
                "grandTotal" to grandTotal
            )

            db.collection("sales").document(salesId).set(salesData)
                .addOnSuccessListener {
                    deductStockAndUpdateCustomer(payMode)
                }
                .addOnFailureListener {
                    binding.btnSaveSalesBill.isEnabled = true
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
            binding.spSalesItem.adapter = adapter
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

    private fun loadBatchesForItem(itemName: String) {
        db.collection("stock_batches")
            .get()
            .addOnSuccessListener { snap ->
                while (matchingBatches.length() > 0) {
                    matchingBatches.remove(0)
                }
                batchDisplayList.clear()
                for (doc in snap) {
                    val docItem = doc.getString("itemName") ?: ""
                    val availKgs = doc.getDouble("availableKgs") ?: 0.0
                    if (docItem.equals(itemName, ignoreCase = true) && availKgs > 0) {
                        val bId = doc.getString("batchId") ?: doc.id
                        val supName = doc.getString("supplierName") ?: ""
                        val availBags = doc.getLong("availableBags") ?: 0L
                        val obj = JSONObject()
                        obj.put("docId", doc.id)
                        obj.put("batchId", bId)
                        obj.put("availableBags", availBags)
                        obj.put("availableKgs", availKgs)
                        matchingBatches.put(obj)
                        batchDisplayList.add("$bId ($supName) - %.1f Kgs".format(availKgs))
                    }
                }
                if (batchDisplayList.isEmpty()) {
                    batchDisplayList.add("No Stock Available")
                    binding.tvLiveStockBanner.text = "Available Stock: 0 Bags | 0.0 Kgs"
                } else {
                    val first = matchingBatches.getJSONObject(0)
                    binding.tvLiveStockBanner.text = "Available Stock: ${first.getLong("availableBags")} Bags | %.2f Kgs".format(first.getDouble("availableKgs"))
                }
                val batchAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, batchDisplayList)
                batchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spSupplierBatch.adapter = batchAdapter
            }
    }

    private fun refreshCartDisplay() {
        grandTotal = 0.0
        val sb = StringBuilder()
        for (idx in 0 until cartItems.length()) {
            val row = cartItems.getJSONObject(idx)
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

    private fun resetFormForFreshEntry() {
        while (cartItems.length() > 0) {
            cartItems.remove(0)
        }
        grandTotal = 0.0
        selectedCustomerId = ""
        selectedCustomerName = ""

        binding.actvCustomer.setText("")
        binding.etSalesBags.setText("")
        binding.etSalesBagRate.setText("10")
        binding.etSalesNetKgs.setText("")
        binding.etSalesRatePerKg.setText("")
        binding.spSalesPayMode.setSelection(0)
        binding.spSalesItem.setSelection(0)

        refreshCartDisplay()
        generateAutoSalesBillNo()
        loadBatchesForItem(selectedItemName)

        binding.btnSaveSalesBill.isEnabled = true
        binding.actvCustomer.requestFocus()
    }

    private fun deductStockAndUpdateCustomer(payMode: String) {
        for (i in 0 until cartItems.length()) {
            val row = cartItems.getJSONObject(i)
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

        Toast.makeText(this, "Sales Bill Saved! Ready for next entry.", Toast.LENGTH_SHORT).show()
        resetFormForFreshEntry()
    }
}
