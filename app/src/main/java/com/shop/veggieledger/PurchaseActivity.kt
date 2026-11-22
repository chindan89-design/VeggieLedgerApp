package com.shop.veggieledger

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityPurchaseBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor

class PurchaseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPurchaseBinding
    private val db = FirebaseFirestore.getInstance()

    private val allSuppliers = JSONArray()
    private val filteredSupplierMap = HashMap<String, String>()
    private val filteredSupplierNames = ArrayList<String>()

    private var selectedSupplierId: String = ""
    private var selectedSupplierName: String = ""
    private var selectedDateStr: String = ""
    private var selectedDayStr: String = ""

    private val itemNames = arrayListOf("Onion", "Garlic", "Coconut")
    private var selectedItemName: String = "Onion"
    private val cartItems = JSONArray()
    private var grandTotal = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurchaseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Purchase Entry"

        // 1. Date Picker (First)
        val cal = Calendar.getInstance()
        updateSelectedDate(cal)
        binding.etPurchaseDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                updateSelectedDate(cal)
                updateLotIdPreview()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // 2. Item Dropdown (Second)
        val defaultItemAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, itemNames)
        defaultItemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spItem.adapter = defaultItemAdapter

        binding.spItem.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedItemName = binding.spItem.selectedItem?.toString() ?: "Onion"
                onItemChanged(selectedItemName)
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        fetchNextBillNumber()

        val payModes = arrayListOf("Credit", "Cash", "GPay / UPI", "Bank Transfer")
        val payAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, payModes)
        payAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spPayMode.adapter = payAdapter

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

        binding.etNetKgs.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (selectedItemName.equals("Coconut", ignoreCase = true)) {
                    calculateCoconutKudumi()
                    updateCoconutNetWeightPreview()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val netWeightWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (selectedItemName.equals("Coconut", ignoreCase = true)) {
                    updateCoconutNetWeightPreview()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etKudumi.addTextChangedListener(netWeightWatcher)
        binding.etThadukki.addTextChangedListener(netWeightWatcher)

        loadItemsFromDb()
        loadAllSuppliers()
        refreshCartGrid()

        binding.btnAddItem.setOnClickListener {
            val purchaseKgs = binding.etNetKgs.text.toString().toDoubleOrNull() ?: 0.0
            val ratePerKg = binding.etRatePerKg.text.toString().toDoubleOrNull() ?: 0.0

            val isOnion = selectedItemName.equals("Onion", ignoreCase = true)
            val isCoconut = selectedItemName.equals("Coconut", ignoreCase = true)

            val bags = if (isOnion) (binding.etBags.text.toString().toIntOrNull() ?: 0) else 0
            val bagRate = if (isOnion) (binding.etBagRate.text.toString().toDoubleOrNull() ?: 10.0) else 0.0

            val kudumi = if (isCoconut) (binding.etKudumi.text.toString().toDoubleOrNull() ?: 0.0) else 0.0
            val thadukki = if (isCoconut) (binding.etThadukki.text.toString().toDoubleOrNull() ?: 0.0) else 0.0

            if (purchaseKgs <= 0.0 || ratePerKg <= 0.0) {
                Toast.makeText(this, "Enter valid Purchase Kgs and Rate/Kg", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val netKgs = (purchaseKgs - kudumi - thadukki).coerceAtLeast(0.0)
            val lineTotal = (netKgs * ratePerKg) + (bags * bagRate)

            val row = JSONObject()
            row.put("itemName", selectedItemName)
            row.put("bags", bags)
            row.put("bagRate", bagRate)
            row.put("purchaseKgs", purchaseKgs)
            row.put("kudumi", kudumi)
            row.put("thadukki", thadukki)
            row.put("netKgs", netKgs)
            row.put("ratePerKg", ratePerKg)
            row.put("lineTotal", lineTotal)

            cartItems.put(row)
            refreshCartGrid()

            binding.etBags.setText("")
            binding.etNetKgs.setText("")
            binding.etRatePerKg.setText("")
            binding.etKudumi.setText("")
            binding.etThadukki.setText("")
            binding.tvCoconutNetWeight.text = "Net Weight: 0.00 Kgs"
            updateLotIdPreview()
        }

        binding.btnSavePurchase.setOnClickListener {
            val typedSupplier = binding.actvSupplier.text.toString().trim()
            if (typedSupplier.isEmpty() || !filteredSupplierMap.containsKey(typedSupplier)) {
                Toast.makeText(this, "Please select a valid Supplier for $selectedItemName", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (cartItems.length() == 0) {
                Toast.makeText(this, "Add at least one item to bill", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            selectedSupplierName = typedSupplier
            selectedSupplierId = filteredSupplierMap[typedSupplier] ?: ""
            updateLotIdPreview()

            val billNo = binding.etBillNo.text.toString().trim()
            val batchId = binding.tvGeneratedBatchId.text.toString().trim()
            val payMode = binding.spPayMode.selectedItem.toString()

            val itemsForDb = ArrayList<Any>()
            for (i in 0 until cartItems.length()) {
                val row = cartItems.getJSONObject(i)
                val map = HashMap<String, Any>()
                map["itemName"] = row.getString("itemName")
                map["bags"] = row.getInt("bags")
                map["bagRate"] = row.getDouble("bagRate")
                map["purchaseKgs"] = row.getDouble("purchaseKgs")
                map["kudumi"] = row.getDouble("kudumi")
                map["thadukki"] = row.getDouble("thadukki")
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
                "date" to selectedDateStr,
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

    private fun updateSelectedDate(cal: Calendar) {
        selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        selectedDayStr = SimpleDateFormat("dd", Locale.getDefault()).format(cal.time)
        binding.etPurchaseDate.setText(selectedDateStr)
    }

    private fun onItemChanged(itemName: String) {
        val isOnion = itemName.equals("Onion", ignoreCase = true)
        val isCoconut = itemName.equals("Coconut", ignoreCase = true)

        binding.layoutOnionFields.visibility = if (isOnion) View.VISIBLE else View.GONE

        if (isCoconut) {
            binding.layoutCoconutFields.visibility = View.VISIBLE
            calculateCoconutKudumi()
            updateCoconutNetWeightPreview()
        } else {
            binding.layoutCoconutFields.visibility = View.GONE
            binding.etKudumi.setText("")
            binding.etThadukki.setText("")
            binding.tvCoconutNetWeight.text = "Net Weight: 0.00 Kgs"
        }

        filterSuppliersForItem(itemName)
        updateLotIdPreview()
    }

    private fun filterSuppliersForItem(itemName: String) {
        filteredSupplierMap.clear()
        filteredSupplierNames.clear()

        for (i in 0 until allSuppliers.length()) {
            val obj = allSuppliers.getJSONObject(i)
            val id = obj.getString("id")
            val name = obj.getString("name")
            val mappedItem = obj.optString("mappedItem", "")

            if (mappedItem.equals(itemName, ignoreCase = true) || mappedItem.isEmpty()) {
                filteredSupplierMap[name] = id
                filteredSupplierNames.add(name)
            }
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, filteredSupplierNames)
        binding.actvSupplier.setAdapter(adapter)
        binding.actvSupplier.setText("")
    }

    private fun calculateCoconutKudumi() {
        val purKgs = binding.etNetKgs.text.toString().toDoubleOrNull() ?: 0.0
        if (purKgs > 0.0) {
            val rawThreePercent = purKgs * 0.03
            val wholePart = floor(rawThreePercent).toInt()
            val decimalPart = rawThreePercent - wholePart
            val finalKudumi = if (decimalPart >= 0.5) (wholePart + 1) else wholePart
            binding.etKudumi.setText(finalKudumi.toString())
        } else {
            binding.etKudumi.setText("")
        }
    }

    private fun updateCoconutNetWeightPreview() {
        val purKgs = binding.etNetKgs.text.toString().toDoubleOrNull() ?: 0.0
        val kudumi = binding.etKudumi.text.toString().toDoubleOrNull() ?: 0.0
        val thadukki = binding.etThadukki.text.toString().toDoubleOrNull() ?: 0.0
        val netKgs = (purKgs - kudumi - thadukki).coerceAtLeast(0.0)
        binding.tvCoconutNetWeight.text = "Net Weight: %.2f Kgs".format(netKgs)
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

        var totalBags = 0
        for (i in 0 until cartItems.length()) {
            totalBags += cartItems.getJSONObject(i).getInt("bags")
        }
        val currentTypingBags = if (selectedItemName.equals("Onion", ignoreCase = true)) {
            (binding.etBags.text.toString().toIntOrNull() ?: 0)
        } else 0
        totalBags += currentTypingBags

        binding.tvGeneratedBatchId.text = "$prefix-$selectedDayStr-$totalBags"
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
            binding.spItem.adapter = adapter
        }
    }

    private fun loadAllSuppliers() {
        db.collection("suppliers").get().addOnSuccessListener { snap ->
            while (allSuppliers.length() > 0) {
                allSuppliers.remove(0)
            }
            for (doc in snap) {
                val name = doc.getString("name") ?: continue
                val obj = JSONObject()
                obj.put("id", doc.id)
                obj.put("name", name)
                obj.put("mappedItem", doc.getString("mappedItem") ?: "")
                allSuppliers.put(obj)
            }
            filterSuppliersForItem(selectedItemName)
        }
    }

    
    private fun makeCell(text: String, alignRight: Boolean = false, bold: Boolean = false, colorHex: String = "#000000"): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.parseColor(colorHex))
        tv.textSize = 12f
        tv.setPadding(16, 14, 16, 14)
        tv.gravity = if (alignRight) Gravity.END else Gravity.START
        if (bold) tv.setTypeface(null, Typeface.BOLD)
        return tv
    }

    private fun 12spToPx(): Float = 12f

    private fun refreshCartGrid() {
        // Keep header row (index 0), remove all previous data rows
        val childCount = binding.tlPurchaseGrid.childCount
        if (childCount > 1) {
            binding.tlPurchaseGrid.removeViews(1, childCount - 1)
        }

        grandTotal = 0.0

        if (cartItems.length() == 0) {
            val emptyRow = TableRow(this)
            emptyRow.setBackgroundColor(Color.WHITE)
            val emptyTv = TextView(this)
            emptyTv.text = "No items added yet."
            emptyTv.setTextColor(Color.GRAY)
            emptyTv.setPadding(20, 20, 20, 20)
            val params = TableRow.LayoutParams()
            params.span = 8
            emptyTv.layoutParams = params
            emptyRow.addView(emptyTv)
            binding.tlPurchaseGrid.addView(emptyRow)
            binding.tvGrandTotal.text = "Grand Total: Rs. 0.00"
            return
        }

        for (idx in 0 until cartItems.length()) {
            val row = cartItems.getJSONObject(idx)
            val name = row.getString("itemName")
            val bags = row.getInt("bags")
            val purKgs = row.getDouble("purchaseKgs")
            val kudumi = row.getDouble("kudumi")
            val thadukki = row.getDouble("thadukki")
            val netKgs = row.getDouble("netKgs")
            val rate = row.getDouble("ratePerKg")
            val total = row.getDouble("lineTotal")
            grandTotal += total

            val tableRow = TableRow(this)
            tableRow.setBackgroundColor(if (idx % 2 == 0) Color.WHITE else Color.parseColor("#F1F8FF"))

            val dedText = if (name.equals("Coconut", ignoreCase = true)) {
                "%.0f+%.0f".format(kudumi, thadukki)
            } else "-"

            tableRow.addView(makeCell(name, alignRight = false, bold = true))
            tableRow.addView(makeCell(if (bags > 0) bags.toString() else "-", alignRight = true))
            tableRow.addView(makeCell("%.1f".format(purKgs), alignRight = true))
            tableRow.addView(makeCell(dedText, alignRight = true))
            tableRow.addView(makeCell("%.2f".format(netKgs), alignRight = true, bold = true, colorHex = "#1B5E20"))
            tableRow.addView(makeCell("%.2f".format(rate), alignRight = true))
            tableRow.addView(makeCell("%.2f".format(total), alignRight = true, bold = true, colorHex = "#1565C0"))

            val delBtn = TextView(this)
            delBtn.text = "X"
            delBtn.setTextColor(Color.RED)
            delBtn.setTypeface(null, Typeface.BOLD)
            delBtn.gravity = Gravity.CENTER
            delBtn.setPadding(18, 14, 18, 14)
            delBtn.setOnClickListener {
                cartItems.remove(idx)
                refreshCartGrid()
                updateLotIdPreview()
            }
            tableRow.addView(delBtn)

            binding.tlPurchaseGrid.addView(tableRow)
        }

        binding.tvGrandTotal.text = "Grand Total: Rs. %.2f".format(grandTotal)
    }

    private fun updateStockAndSupplierBalance(batchId: String, payMode: String) {
        for (i in 0 until cartItems.length()) {
            val row = cartItems.getJSONObject(i)
            val itemName = row.getString("itemName")
            val bags = row.getInt("bags")
            val netKgs = row.getDouble("netKgs")
            val rate = row.getDouble("ratePerKg")

            val batchDoc = db.collection("stock_batches").document()
            val batchData = hashMapOf(
                "batchId" to batchId,
                "supplierId" to selectedSupplierId,
                "supplierName" to selectedSupplierName,
                "itemName" to itemName,
                "availableBags" to bags,
                "availableKgs" to netKgs,
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
