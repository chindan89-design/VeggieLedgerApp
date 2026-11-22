package com.shop.veggieledger

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TableRow
import android.widget.TextView
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
    private val editFilterCustomerList = ArrayList<String>()

    private var selectedCustomerId: String = ""
    private var selectedCustomerName: String = ""
    private var selectedDateStr: String = ""
    private var editFilterDateStr: String = ""

    private val itemNames = arrayListOf("Onion", "Garlic", "Coconut")
    private var selectedItemName: String = "Onion"
    private val matchingBatches = JSONArray()
    private val batchDisplayList = ArrayList<String>()

    private val cartItems = JSONArray()
    private var grandTotal = 0.0

    // Edit Mode State Variables
    private var isEditMode: Boolean = false
    private var editingSalesDocId: String = ""
    private var oldCustomerId: String = ""
    private var oldPayMode: String = ""
    private var oldGrandTotal: Double = 0.0
    private val oldCartItems = JSONArray()

    private val payModes = arrayListOf("Credit", "Cash", "GPay / UPI", "Bank Transfer")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Sales Entry"

        // Mode Switch Buttons
        binding.btnModeNewSales.setOnClickListener {
            switchToNewMode()
        }
        binding.btnModeEditSales.setOnClickListener {
            switchToEditMode()
        }

        // Main Sales Date Picker
        val cal = Calendar.getInstance()
        selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        editFilterDateStr = selectedDateStr
        binding.etSalesDate.setText(selectedDateStr)
        binding.etEditFilterDate.setText(editFilterDateStr)

        binding.etSalesDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                binding.etSalesDate.setText(selectedDateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Edit Filter Date Picker
        val editCal = Calendar.getInstance()
        binding.etEditFilterDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                editCal.set(y, m, d)
                editFilterDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(editCal.time)
                binding.etEditFilterDate.setText(editFilterDateStr)
                loadBillsForEditFilter()
            }, editCal.get(Calendar.YEAR), editCal.get(Calendar.MONTH), editCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Edit Filter Customer Spinner
        binding.spEditFilterCustomer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (isEditMode) {
                    loadBillsForEditFilter()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // Item Dropdown
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
        refreshCartGrid()

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

            // In edit mode, account for stock already reserved by this bill for the same batch
            var previouslyUsedKgs = 0.0
            if (editingSalesDocId.isNotEmpty()) {
                for (k in 0 until oldCartItems.length()) {
                    val oldRow = oldCartItems.getJSONObject(k)
                    if (oldRow.optString("batchDocId") == batchDocId) {
                        previouslyUsedKgs += oldRow.optDouble("netKgs", 0.0)
                    }
                }
            }

            if (netKgs > (availKgs + previouslyUsedKgs)) {
                Toast.makeText(this, "Insufficient stock! Available: %.2f Kgs".format(availKgs + previouslyUsedKgs), Toast.LENGTH_SHORT).show()
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
            refreshCartGrid()

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
            if (isEditMode && editingSalesDocId.isEmpty()) {
                Toast.makeText(this, "Please select a bill from the list above to edit", Toast.LENGTH_SHORT).show()
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

            if (isEditMode && editingSalesDocId.isNotEmpty()) {
                // UPDATE EXISTING SALES BILL
                val updateData = hashMapOf<String, Any>(
                    "billNo" to billNo,
                    "customerId" to selectedCustomerId,
                    "customerName" to selectedCustomerName,
                    "payMode" to payMode,
                    "date" to selectedDateStr,
                    "items" to itemsForDb,
                    "grandTotal" to grandTotal
                )
                db.collection("sales").document(editingSalesDocId).update(updateData)
                    .addOnSuccessListener {
                        reconcileStockAndCustomerOnEdit(payMode)
                    }
                    .addOnFailureListener {
                        binding.btnSaveSalesBill.isEnabled = true
                        Toast.makeText(this, "Failed to update sales bill", Toast.LENGTH_SHORT).show()
                    }
            } else {
                // CREATE NEW SALES BILL
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
    }

    private fun switchToNewMode() {
        isEditMode = false
        editingSalesDocId = ""
        clearJsonArray(oldCartItems)
        binding.layoutEditSalesPanel.visibility = View.GONE
        binding.btnModeNewSales.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B5E20"))
        binding.btnModeEditSales.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        binding.tvSalesHeader.text = "SALES BILL ENTRY FORM"
        binding.btnSaveSalesBill.text = "SUBMIT SALES BILL & UPDATE STOCK"
        binding.btnSaveSalesBill.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B5E20"))
        resetFormForFreshEntry()
    }

    private fun switchToEditMode() {
        isEditMode = true
        editingSalesDocId = ""
        clearJsonArray(oldCartItems)
        binding.layoutEditSalesPanel.visibility = View.VISIBLE
        binding.btnModeEditSales.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
        binding.btnModeNewSales.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        binding.tvSalesHeader.text = "EDIT EXISTING SALES BILL"
        binding.btnSaveSalesBill.text = "UPDATE SALES BILL"
        binding.btnSaveSalesBill.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
        resetFormForFreshEntry()
        loadBillsForEditFilter()
    }

    private fun loadBillsForEditFilter() {
        val selectedFilterCust = binding.spEditFilterCustomer.selectedItem?.toString() ?: "All Customers"
        binding.llMatchingBillsList.removeAllViews()

        val loadingTv = TextView(this)
        loadingTv.text = "Loading bills..."
        loadingTv.setTextColor(Color.DKGRAY)
        loadingTv.setPadding(12, 12, 12, 12)
        binding.llMatchingBillsList.addView(loadingTv)

        db.collection("sales")
            .whereEqualTo("date", editFilterDateStr)
            .get()
            .addOnSuccessListener { snap ->
                binding.llMatchingBillsList.removeAllViews()
                var matchCount = 0

                for (doc in snap) {
                    val custName = doc.getString("customerName") ?: ""
                    if (selectedFilterCust != "All Customers" && !custName.equals(selectedFilterCust, ignoreCase = true)) {
                        continue
                    }

                    matchCount++
                    val billNo = doc.getString("billNo") ?: doc.id
                    val total = doc.getDouble("grandTotal") ?: 0.0
                    val pMode = doc.getString("payMode") ?: "Credit"

                    val billCard = TextView(this)
                    billCard.text = "$billNo | $custName | $pMode | Rs. %.2f  [TAP TO EDIT]".format(total)
                    billCard.setTextColor(Color.parseColor("#1B5E20"))
                    billCard.setTypeface(null, Typeface.BOLD)
                    billCard.textSize = 13f
                    billCard.setBackgroundColor(Color.WHITE)
                    billCard.setPadding(20, 22, 20, 22)

                    val lp = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.setMargins(0, 0, 0, 10)
                    billCard.layoutParams = lp

                    billCard.setOnClickListener {
                        populateFormFromBillDocument(doc.id, doc.data)
                    }
                    binding.llMatchingBillsList.addView(billCard)
                }

                if (matchCount == 0) {
                    val noTv = TextView(this)
                    noTv.text = "No sales bills found for $editFilterDateStr ($selectedFilterCust)."
                    noTv.setTextColor(Color.RED)
                    noTv.setPadding(12, 14, 12, 14)
                    binding.llMatchingBillsList.addView(noTv)
                }
            }
            .addOnFailureListener {
                binding.llMatchingBillsList.removeAllViews()
                Toast.makeText(this, "Error loading bills", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateFormFromBillDocument(docId: String, data: Map<String, Any>) {
        editingSalesDocId = docId
        oldCustomerId = data["customerId"]?.toString() ?: ""
        oldPayMode = data["payMode"]?.toString() ?: "Credit"
        oldGrandTotal = (data["grandTotal"] as? Number)?.toDouble() ?: 0.0

        val bNo = data["billNo"]?.toString() ?: ""
        val cName = data["customerName"]?.toString() ?: ""
        val bDate = data["date"]?.toString() ?: editFilterDateStr

        selectedDateStr = bDate
        binding.etSalesDate.setText(bDate)
        binding.etSalesBillNo.setText(bNo)
        binding.actvCustomer.setText(cName, false)

        val payIdx = payModes.indexOfFirst { it.equals(oldPayMode, ignoreCase = true) }
        if (payIdx >= 0) binding.spSalesPayMode.setSelection(payIdx)

        clearJsonArray(cartItems)
        clearJsonArray(oldCartItems)

        val rawItems = data["items"] as? List<*>
        if (rawItems != null) {
            for (entry in rawItems) {
                val map = entry as? Map<*, *> ?: continue
                val obj = JSONObject()
                obj.put("itemName", map["itemName"]?.toString() ?: "Onion")
                obj.put("batchDocId", map["batchDocId"]?.toString() ?: "")
                obj.put("batchCode", map["batchCode"]?.toString() ?: "")
                obj.put("bags", (map["bags"] as? Number)?.toInt() ?: 0)
                obj.put("bagRate", (map["bagRate"] as? Number)?.toDouble() ?: 0.0)
                obj.put("netKgs", (map["netKgs"] as? Number)?.toDouble() ?: 0.0)
                obj.put("ratePerKg", (map["ratePerKg"] as? Number)?.toDouble() ?: 0.0)
                obj.put("lineTotal", (map["lineTotal"] as? Number)?.toDouble() ?: 0.0)
                cartItems.put(obj)

                // Keep a separate copy of original items for stock reconciliation
                oldCartItems.put(JSONObject(obj.toString()))
            }
        }

        refreshCartGrid()
        Toast.makeText(this, "Loaded $bNo for editing", Toast.LENGTH_SHORT).show()
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
            editFilterCustomerList.clear()
            editFilterCustomerList.add("All Customers")

            for (doc in snap) {
                val id = doc.id
                val name = doc.getString("name") ?: continue
                customerMap[name] = id
                customerNames.add(name)
                editFilterCustomerList.add(name)
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customerNames)
            binding.actvCustomer.setAdapter(adapter)
            binding.actvCustomer.setText("")

            val filterAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, editFilterCustomerList)
            filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spEditFilterCustomer.adapter = filterAdapter
        }
    }

    private fun loadBatchesForItem(itemName: String) {
        db.collection("stock_batches")
            .get()
            .addOnSuccessListener { snap ->
                clearJsonArray(matchingBatches)
                batchDisplayList.clear()
                for (doc in snap) {
                    val docItem = doc.getString("itemName") ?: ""
                    val availKgs = doc.getDouble("availableKgs") ?: 0.0
                    if (docItem.equals(itemName, ignoreCase = true) && (availKgs > 0 || isEditMode)) {
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

    private fun refreshCartGrid() {
        val childCount = binding.tlSalesGrid.childCount
        if (childCount > 1) {
            binding.tlSalesGrid.removeViews(1, childCount - 1)
        }

        grandTotal = 0.0

        if (cartItems.length() == 0) {
            val emptyRow = TableRow(this)
            emptyRow.setBackgroundColor(Color.WHITE)
            val emptyTv = TextView(this)
            emptyTv.text = "No items added to bill yet."
            emptyTv.setTextColor(Color.GRAY)
            emptyTv.setPadding(20, 20, 20, 20)
            val params = TableRow.LayoutParams()
            params.span = 7
            emptyTv.layoutParams = params
            emptyRow.addView(emptyTv)
            binding.tlSalesGrid.addView(emptyRow)
            binding.tvSalesGrandTotal.text = "Grand Total: Rs. 0.00"
            return
        }

        for (idx in 0 until cartItems.length()) {
            val row = cartItems.getJSONObject(idx)
            val name = row.getString("itemName")
            val lotCode = row.optString("batchCode", "-")
            val bags = row.getInt("bags")
            val kgs = row.getDouble("netKgs")
            val rate = row.getDouble("ratePerKg")
            val total = row.getDouble("lineTotal")
            grandTotal += total

            val tableRow = TableRow(this)
            tableRow.setBackgroundColor(if (idx % 2 == 0) Color.WHITE else Color.parseColor("#F1F8E9"))

            tableRow.addView(makeCell(name, alignRight = false, bold = true))
            tableRow.addView(makeCell(lotCode, alignRight = false))
            tableRow.addView(makeCell(if (bags > 0) bags.toString() else "-", alignRight = true))
            tableRow.addView(makeCell("%.2f".format(kgs), alignRight = true, bold = true))
            tableRow.addView(makeCell("%.2f".format(rate), alignRight = true))
            tableRow.addView(makeCell("%.2f".format(total), alignRight = true, bold = true, colorHex = "#1B5E20"))

            val delBtn = TextView(this)
            delBtn.text = "X"
            delBtn.setTextColor(Color.RED)
            delBtn.setTypeface(null, Typeface.BOLD)
            delBtn.gravity = Gravity.CENTER
            delBtn.setPadding(18, 14, 18, 14)
            delBtn.setOnClickListener {
                cartItems.remove(idx)
                refreshCartGrid()
            }
            tableRow.addView(delBtn)

            binding.tlSalesGrid.addView(tableRow)
        }

        binding.tvSalesGrandTotal.text = "Grand Total: Rs. %.2f".format(grandTotal)
    }

    private fun clearJsonArray(arr: JSONArray) {
        while (arr.length() > 0) {
            arr.remove(0)
        }
    }

    private fun resetFormForFreshEntry() {
        clearJsonArray(cartItems)
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

        refreshCartGrid()
        if (!isEditMode) {
            generateAutoSalesBillNo()
        } else {
            binding.etSalesBillNo.setText("")
        }
        loadBatchesForItem(selectedItemName)

        binding.btnSaveSalesBill.isEnabled = true
        if (!isEditMode) binding.actvCustomer.requestFocus()
    }

    private fun deductStockAndUpdateCustomer(payMode: String) {
        for (i in 0 until cartItems.length()) {
            val row = cartItems.getJSONObject(i)
            val docId = row.getString("batchDocId")
            val soldBags = row.getInt("bags")
            val soldKgs = row.getDouble("netKgs")

            if (docId.isNotEmpty()) {
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

    private fun reconcileStockAndCustomerOnEdit(newPayMode: String) {
        // Calculate net stock delta per batchDocId: (oldSold - newSold)
        val bagDeltaMap = HashMap<String, Long>()
        val kgDeltaMap = HashMap<String, Double>()

        for (i in 0 until oldCartItems.length()) {
            val oldRow = oldCartItems.getJSONObject(i)
            val bId = oldRow.optString("batchDocId", "")
            if (bId.isNotEmpty()) {
                bagDeltaMap[bId] = (bagDeltaMap[bId] ?: 0L) + oldRow.optLong("bags", 0L)
                kgDeltaMap[bId] = (kgDeltaMap[bId] ?: 0.0) + oldRow.optDouble("netKgs", 0.0)
            }
        }

        for (i in 0 until cartItems.length()) {
            val newRow = cartItems.getJSONObject(i)
            val bId = newRow.optString("batchDocId", "")
            if (bId.isNotEmpty()) {
                bagDeltaMap[bId] = (bagDeltaMap[bId] ?: 0L) - newRow.optLong("bags", 0L)
                kgDeltaMap[bId] = (kgDeltaMap[bId] ?: 0.0) - newRow.optDouble("netKgs", 0.0)
            }
        }

        for ((bId, kgDelta) in kgDeltaMap) {
            val bagDelta = bagDeltaMap[bId] ?: 0L
            if (kgDelta != 0.0 || bagDelta != 0L) {
                val batchRef = db.collection("stock_batches").document(bId)
                db.runTransaction { tr ->
                    val snap = tr.get(batchRef)
                    val currBags = snap.getLong("availableBags") ?: 0L
                    val currKgs = snap.getDouble("availableKgs") ?: 0.0
                    tr.update(batchRef, mapOf(
                        "availableBags" to (currBags + bagDelta).coerceAtLeast(0L),
                        "availableKgs" to (currKgs + kgDelta).coerceAtLeast(0.0)
                    ))
                }
            }
        }

        // Reverse old customer credit if applicable, then apply new customer credit
        if (oldPayMode.equals("Credit", ignoreCase = true) && oldCustomerId.isNotEmpty()) {
            val oldCustRef = db.collection("customers").document(oldCustomerId)
            db.runTransaction { tr ->
                val snap = tr.get(oldCustRef)
                val curr = snap.getDouble("currentBalance") ?: 0.0
                val afterReverse = (curr - oldGrandTotal).coerceAtLeast(0.0)
                val finalBal = if (newPayMode.equals("Credit", ignoreCase = true) && selectedCustomerId == oldCustomerId) {
                    afterReverse + grandTotal
                } else {
                    afterReverse
                }
                tr.update(oldCustRef, "currentBalance", finalBal)
            }
        }

        if (newPayMode.equals("Credit", ignoreCase = true) && selectedCustomerId.isNotEmpty() &&
            !(oldPayMode.equals("Credit", ignoreCase = true) && selectedCustomerId == oldCustomerId)) {
            val newCustRef = db.collection("customers").document(selectedCustomerId)
            db.runTransaction { tr ->
                val snap = tr.get(newCustRef)
                val curr = snap.getDouble("currentBalance") ?: 0.0
                tr.update(newCustRef, "currentBalance", curr + grandTotal)
            }
        }

        Toast.makeText(this, "Sales Bill Updated Successfully!", Toast.LENGTH_SHORT).show()
        editingSalesDocId = ""
        clearJsonArray(oldCartItems)
        resetFormForFreshEntry()
        loadBillsForEditFilter()
    }
}
