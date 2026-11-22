package com.shop.veggieledger

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityPurchaseBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class PurchaseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPurchaseBinding
    private val db = FirebaseFirestore.getInstance()

    private val supplierMap = HashMap<String, String>()
    private val supplierNames = ArrayList<String>()
    private var selectedSupplierId: String = ""
    private var selectedSupplierName: String = ""

    private var selectedItemName: String = "Onion"
    private val cartItems = JSONArray()
    private var grandTotal = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurchaseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Purchase Entry"

        fetchNextBillNumber()
        updateLotIdPreview()

        val payModes = arrayListOf("Credit", "Cash", "GPay / UPI", "Bank Transfer")
        binding.spPayMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, payModes)

        // Supplier name select / type pannum pothu Stock Lot ID auto-update aagum
        binding.actvSupplier.setOnItemClickListener { _, _, _, _ ->
            updateLotIdPreview()
        }

        val liveIdWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateLotIdPreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        binding.actvSupplier.addTextChangedListener(liveIdWatcher)
        binding.etBags.addTextChangedListener(liveIdWatcher)

        // RadioGroup listener for Item Selection
        binding.rgPurchaseItem.setOnCheckedChangeListener { _, checkedId ->
            selectedItemName = when (checkedId) {
                R.id.rbPurGarlic -> "Garlic"
                R.id.rbPurCoconut -> "Coconut"
                else -> "Onion"
            }
            binding.layoutOnionFields.visibility = if (selectedItemName == "Onion") View.VISIBLE else View.GONE
            updateLotIdPreview()
        }

        loadSuppliers()

        binding.btnAddItem.setOnClickListener {
            val netKgs = binding.etNetKgs.text.toString().toDoubleOrNull() ?: 0.0
            val ratePerKg = binding.etRatePerKg.text.toString().toDoubleOrNull() ?: 0.0

            val isOnion = selectedItemName == "Onion"
            val bags = if (isOnion) (binding.etBags.text.toString().toIntOrNull() ?: 0) else 0
            val bagRate = if (isOnion) (binding.etBagRate.text.toString().toDoubleOrNull() ?: 10.0) else 0.0

            if (netKgs <= 0.0 || ratePerKg <= 0.0) {
                Toast.makeText(this, "Enter valid Net Kgs and Rate/Kg", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val lineTotal = (netKgs * ratePerKg) + (bags * bagRate)

            val row = JSONObject()
            row.put("itemName", selectedItemName)
            row.put("bags", bags)
            row.put("bagRate", bagRate)
            row.put("netKgs", netKgs)
            row.put("ratePerKg", ratePerKg)
            row.put("lineTotal", lineTotal)

            cartItems.put(row)
            refreshCartDisplay()

            binding.etBags.setText("")
            binding.etNetKgs.setText("")
            binding.etRatePerKg.setText("")
            updateLotIdPreview()
        }

        binding.btnSavePurchase.setOnClickListener {
            val typedSupplier = binding.actvSupplier.text.toString().trim()
            if (typedSupplier.isEmpty() || !supplierMap.containsKey(typedSupplier)) {
                Toast.makeText(this, "Please search and select a valid Supplier", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (cartItems.length() == 0) {
                Toast.makeText(this, "Add at least one item to bill", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            selectedSupplierName = typedSupplier
            selectedSupplierId = supplierMap[typedSupplier] ?: ""
            updateLotIdPreview()

            val billNo = binding.etBillNo.text.toString().trim()
            val batchId = binding.tvGeneratedBatchId.text.toString().trim()
            val payMode = binding.spPayMode.selectedItem.toString()
            val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

            val itemsForDb = ArrayList<Any>()
            for (i in 0 until cartItems.length()) {
                val row = cartItems.getJSONObject(i)
                val map = HashMap<String, Any>()
                map["itemName"] = row.getString("itemName")
                map["bags"] = row.getInt("bags")
                map["bagRate"] = row.getDouble("bagRate")
                map["netKgs"] = row.getDouble("netKgs")
                map["ratePerKg"] = row.getDouble("ratePerKg")
                map["lineTotal"] = row.getDouble("lineTotal")
                itemsForDb.add(map)
            }

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
                "items" to itemsForDb,
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

    private fun fetchNextBillNumber() {
        db.collection("purchases").get().addOnSuccessListener { snap ->
            val nextNum = snap.size() + 1
            binding.etBillNo.setText(String.format("PUR-%03d", nextNum))
        }
    }

    private fun updateLotIdPreview() {
        val supplierName = binding.actvSupplier.text.toString()
        val cleanName = supplierName.replace("[^A-Za-z]".toRegex(), "").uppercase(Locale.getDefault())
        val prefix = when {
            cleanName.length >= 3 -> cleanName.substring(0, 3)
            cleanName.isNotEmpty() -> cleanName.padEnd(3, 'X')
            else -> "SUP"
        }
        val dayOnly = SimpleDateFormat("dd", Locale.getDefault()).format(Date())

        // Cart-la irukkara bags + ippo type pannirukkara bags total
        var totalBags = 0
        for (i in 0 until cartItems.length()) {
            totalBags += cartItems.getJSONObject(i).getInt("bags")
        }
        val currentTypingBags = binding.etBags.text.toString().toIntOrNull() ?: 0
        totalBags += currentTypingBags

        // Format: First 3 letters of Supplier + Day + Total Bags (e.g., MUR-22-50)
        binding.tvGeneratedBatchId.text = "$prefix-$dayOnly-$totalBags"
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
        binding.tvAddedItemsList.text = if (sb.isEmpty()) "No items added yet." else sb.toString().trim()
        binding.tvGrandTotal.text = "Grand Total: Rs. %.2f".format(grandTotal)
    }

    private fun updateStockAndSupplierBalance(batchId: String, payMode: String) {
        for (i in 0 until cartItems.length()) {
            val row = cartItems.getJSONObject(i)
            val itemName = row.getString("itemName")
            val bags = row.getInt("bags")
            val kgs = row.getDouble("netKgs")
            val rate = row.getDouble("ratePerKg")

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

        Toast.makeText(this, "Purchase Saved ($batchId) & Stock Added!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
