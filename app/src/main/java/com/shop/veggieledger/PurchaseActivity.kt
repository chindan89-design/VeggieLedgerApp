package com.shop.veggieledger

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
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

    private lateinit var nosContainerLayout: LinearLayout
    private lateinit var etTotalNos: EditText
    private lateinit var etEditableBatchId: EditText

    private val allSuppliers = JSONArray()
    private val filteredSupplierMap = HashMap<String, String>()
    private val filteredSupplierNames = ArrayList<String>()
    private val editFilterSupplierList = ArrayList<String>()

    private var selectedSupplierId: String = ""
    private var selectedSupplierName: String = ""
    private var selectedDateStr: String = ""
    private var selectedDayStr: String = ""
    private var editFilterDateStr: String = ""
    private var dailyPurchaseIndex: Int = 0

    private val itemNames = arrayListOf("Onion", "Garlic", "Coconut")
    private var selectedItemName: String = "Onion"
    private val cartItems = JSONArray()
    private var grandTotal = 0.0

    private var isEditMode: Boolean = false
    private var editingPurchaseDocId: String = ""
    private var oldSupplierId: String = ""
    private var oldPayMode: String = ""
    private var oldGrandTotal: Double = 0.0
    private var oldBatchId: String = ""
    private val oldCartItemsJson = JSONArray()

    private var suppressKudumiAutoCalc: Boolean = false

    private val payModes = arrayListOf("Credit", "Cash", "GPay / UPI", "Bank Transfer")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurchaseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Purchase Entry"

        injectEditableBatchIdBox()
        injectTotalNosFieldBeforeTotalKgs()

        binding.btnModeNewPurchase.setOnClickListener { switchToNewMode() }
        binding.btnModeEditPurchase.setOnClickListener { switchToEditMode() }

        val cal = Calendar.getInstance()
        updateSelectedDate(cal)
        editFilterDateStr = selectedDateStr
        binding.etEditPurFilterDate.setText(editFilterDateStr)

        binding.etPurchaseDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                updateSelectedDate(cal)
                fetchNextBillNumber()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val editCal = Calendar.getInstance()
        binding.etEditPurFilterDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                editCal.set(y, m, d)
                editFilterDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(editCal.time)
                binding.etEditPurFilterDate.setText(editFilterDateStr)
                loadPurchasesForEditFilter()
            }, editCal.get(Calendar.YEAR), editCal.get(Calendar.MONTH), editCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.spEditPurFilterSupplier.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (isEditMode) loadPurchasesForEditFilter()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

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

        val payAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, payModes)
        payAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spPayMode.adapter = payAdapter

        binding.actvSupplier.setOnItemClickListener { _, _, _, _ -> updateLotIdPreview() }

        val liveIdWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateLotIdPreview() }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.actvSupplier.addTextChangedListener(liveIdWatcher)
        binding.etBags.addTextChangedListener(liveIdWatcher)
        etTotalNos.addTextChangedListener(liveIdWatcher)

        binding.etNetKgs.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (selectedItemName.equals("Coconut", ignoreCase = true) && !suppressKudumiAutoCalc) {
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

            val totalNos = if (isCoconut) (etTotalNos.text.toString().toIntOrNull() ?: 0) else 0
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
            row.put("nos", totalNos)
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

            etTotalNos.setText("")
            binding.etBags.setText("")
            binding.etNetKgs.setText("")
            binding.etRatePerKg.setText("")
            binding.etKudumi.setText("")
            binding.etThadukki.setText("")
            binding.tvCoconutNetWeight.text = "Net Weight: 0.00 Kgs"
            binding.btnAddItem.text = "+ ADD ITEM TO PURCHASE BILL"
            updateLotIdPreview()
        }

        binding.btnSavePurchase.setOnClickListener {
            val typedSupplier = binding.actvSupplier.text.toString().trim()
            if (typedSupplier.isEmpty() || !filteredSupplierMap.containsKey(typedSupplier)) {
                Toast.makeText(this, "Please select a valid Supplier", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (cartItems.length() == 0) {
                Toast.makeText(this, "Add at least one item to bill", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isEditMode && editingPurchaseDocId.isEmpty()) {
                Toast.makeText(this, "Please select a purchase bill above to edit", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            selectedSupplierName = typedSupplier
            selectedSupplierId = filteredSupplierMap[typedSupplier] ?: ""
            updateLotIdPreview()

            val billNo = binding.etBillNo.text.toString().trim()
            val batchId = if (isEditMode && editingPurchaseDocId.isNotEmpty()) {
                etEditableBatchId.text.toString().trim().uppercase(Locale.getDefault()).ifEmpty {
                    binding.tvGeneratedBatchId.text.toString().trim()
                }
            } else {
                binding.tvGeneratedBatchId.text.toString().trim()
            }
            val payMode = binding.spPayMode.selectedItem.toString()

            val itemsForDb = ArrayList<Any>()
            for (i in 0 until cartItems.length()) {
                val row = cartItems.getJSONObject(i)
                val map = HashMap<String, Any>()
                map["itemName"] = row.getString("itemName")
                map["nos"] = row.optInt("nos", 0)
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

            binding.btnSavePurchase.isEnabled = false

            if (isEditMode && editingPurchaseDocId.isNotEmpty()) {
                val updateData = hashMapOf<String, Any>(
                    "billNo" to billNo,
                    "batchId" to batchId,
                    "supplierId" to selectedSupplierId,
                    "supplierName" to selectedSupplierName,
                    "payMode" to payMode,
                    "date" to selectedDateStr,
                    "items" to itemsForDb,
                    "grandTotal" to grandTotal
                )
                db.collection("purchases").document(editingPurchaseDocId).update(updateData)
                    .addOnSuccessListener {
                        reconcileStockAndSupplierOnEdit(editingPurchaseDocId, batchId, payMode)
                    }
                    .addOnFailureListener {
                        binding.btnSavePurchase.isEnabled = true
                        Toast.makeText(this, "Failed to update purchase", Toast.LENGTH_SHORT).show()
                    }
            } else {
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
                        createStockBatchesAndBalance(purchaseId, batchId, payMode)
                    }
                    .addOnFailureListener {
                        binding.btnSavePurchase.isEnabled = true
                        Toast.makeText(this, "Failed to save purchase", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    /**
     * Adds an editable Stock / Lot ID box right below tvGeneratedBatchId so
     * existing saved purchases can have their Stock ID modified cleanly in Edit Mode.
     */
    private fun injectEditableBatchIdBox() {
        val parent = binding.tvGeneratedBatchId.parent as? ViewGroup ?: return
        val insertIdx = parent.indexOfChild(binding.tvGeneratedBatchId) + 1

        etEditableBatchId = EditText(this)
        etEditableBatchId.hint = "Edit Stock / Lot ID (e.g. MUR-26-0)"
        etEditableBatchId.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        etEditableBatchId.setBackgroundResource(android.R.drawable.edit_text)
        etEditableBatchId.setTextColor(Color.parseColor("#E65100"))
        etEditableBatchId.setTypeface(null, Typeface.BOLD)
        etEditableBatchId.visibility = View.GONE
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 4, 0, 8)
        etEditableBatchId.layoutParams = lp

        etEditableBatchId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isEditMode && editingPurchaseDocId.isNotEmpty()) {
                    binding.tvGeneratedBatchId.text = s?.toString()?.trim()?.uppercase(Locale.getDefault()) ?: ""
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        parent.addView(etEditableBatchId, insertIdx)
    }

    private fun injectTotalNosFieldBeforeTotalKgs() {
        val parent = binding.etNetKgs.parent as? ViewGroup ?: return
        var insertIndex = parent.indexOfChild(binding.etNetKgs)
        if (insertIndex > 0 && parent.getChildAt(insertIndex - 1) is TextView) {
            insertIndex -= 1
        }

        nosContainerLayout = LinearLayout(this)
        nosContainerLayout.orientation = LinearLayout.VERTICAL
        nosContainerLayout.visibility = View.GONE
        val boxLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        boxLp.setMargins(0, 0, 0, 8)
        nosContainerLayout.layoutParams = boxLp

        val lblNos = TextView(this)
        lblNos.text = "Total Nos (Coconut)"
        lblNos.textSize = 12f
        lblNos.setTypeface(null, Typeface.BOLD)
        lblNos.setTextColor(Color.parseColor("#1B5E20"))

        etTotalNos = EditText(this)
        etTotalNos.hint = "Enter Total Nos"
        etTotalNos.inputType = InputType.TYPE_CLASS_NUMBER
        etTotalNos.setBackgroundResource(android.R.drawable.edit_text)
        etTotalNos.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        nosContainerLayout.addView(lblNos)
        nosContainerLayout.addView(etTotalNos)
        parent.addView(nosContainerLayout, insertIndex)
    }

    private fun switchToNewMode() {
        isEditMode = false
        editingPurchaseDocId = ""
        oldBatchId = ""
        clearJsonArray(oldCartItemsJson)
        binding.layoutEditPurchasePanel.visibility = View.GONE
        etEditableBatchId.visibility = View.GONE
        binding.tvGeneratedBatchId.visibility = View.VISIBLE
        binding.btnModeNewPurchase.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1565C0"))
        binding.btnModeEditPurchase.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        binding.tvPurchaseHeader.text = "PURCHASE BILL ENTRY FORM"
        binding.btnSavePurchase.text = "SUBMIT PURCHASE & CREATE STOCK"
        binding.btnSavePurchase.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1565C0"))
        resetFormForFreshEntry()
    }

    private fun switchToEditMode() {
        isEditMode = true
        editingPurchaseDocId = ""
        oldBatchId = ""
        clearJsonArray(oldCartItemsJson)
        binding.layoutEditPurchasePanel.visibility = View.VISIBLE
        etEditableBatchId.visibility = View.VISIBLE
        binding.btnModeEditPurchase.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
        binding.btnModeNewPurchase.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        binding.tvPurchaseHeader.text = "EDIT / DELETE PURCHASE BILL"
        binding.btnSavePurchase.text = "UPDATE PURCHASE BILL"
        binding.btnSavePurchase.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
        resetFormForFreshEntry()
        loadPurchasesForEditFilter()
    }

    private fun loadPurchasesForEditFilter() {
        val selectedFilterSup = binding.spEditPurFilterSupplier.selectedItem?.toString() ?: "All Suppliers"
        binding.llMatchingPurBillsList.removeAllViews()

        val loadingTv = TextView(this)
        loadingTv.text = "Loading purchase bills..."
        loadingTv.setTextColor(Color.DKGRAY)
        loadingTv.setPadding(12, 12, 12, 12)
        binding.llMatchingPurBillsList.addView(loadingTv)

        db.collection("purchases")
            .whereEqualTo("date", editFilterDateStr)
            .get()
            .addOnSuccessListener { snap ->
                binding.llMatchingPurBillsList.removeAllViews()
                var matchCount = 0

                for (doc in snap) {
                    val supName = doc.getString("supplierName") ?: ""
                    if (selectedFilterSup != "All Suppliers" && !supName.equals(selectedFilterSup, ignoreCase = true)) {
                        continue
                    }

                    matchCount++
                    val billNo = doc.getString("billNo") ?: doc.id
                    val lotId = doc.getString("batchId") ?: ""
                    val total = doc.getDouble("grandTotal") ?: 0.0
                    val pMode = doc.getString("payMode") ?: "Credit"

                    val rowCard = LinearLayout(this)
                    rowCard.orientation = LinearLayout.HORIZONTAL
                    rowCard.gravity = Gravity.CENTER_VERTICAL
                    rowCard.setBackgroundColor(Color.WHITE)
                    rowCard.setPadding(16, 14, 16, 14)
                    val cardLp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    cardLp.setMargins(0, 0, 0, 10)
                    rowCard.layoutParams = cardLp

                    val infoTv = TextView(this)
                    infoTv.text = "$billNo | Stock ID: $lotId\n$supName | $pMode | Rs. %.2f".format(total)
                    infoTv.setTextColor(Color.parseColor("#1565C0"))
                    infoTv.setTypeface(null, Typeface.BOLD)
                    infoTv.textSize = 13f
                    val infoLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    infoTv.layoutParams = infoLp

                    val editBtn = TextView(this)
                    editBtn.text = "EDIT"
                    editBtn.setTextColor(Color.WHITE)
                    editBtn.setBackgroundColor(Color.parseColor("#1565C0"))
                    editBtn.setTypeface(null, Typeface.BOLD)
                    editBtn.textSize = 12f
                    editBtn.setPadding(24, 14, 24, 14)
                    val editLp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    editLp.setMargins(8, 0, 8, 0)
                    editBtn.layoutParams = editLp
                    editBtn.setOnClickListener {
                        populateFormFromPurchaseDoc(doc.id, doc.data)
                    }

                    val delBtn = TextView(this)
                    delBtn.text = "DEL"
                    delBtn.setTextColor(Color.WHITE)
                    delBtn.setBackgroundColor(Color.parseColor("#C62828"))
                    delBtn.setTypeface(null, Typeface.BOLD)
                    delBtn.textSize = 12f
                    delBtn.setPadding(24, 14, 24, 14)
                    delBtn.setOnClickListener {
                        confirmAndDeletePurchaseBill(doc.id, billNo, doc.data)
                    }

                    rowCard.addView(infoTv)
                    rowCard.addView(editBtn)
                    rowCard.addView(delBtn)
                    binding.llMatchingPurBillsList.addView(rowCard)
                }

                if (matchCount == 0) {
                    val noTv = TextView(this)
                    noTv.text = "No purchase bills found for $editFilterDateStr ($selectedFilterSup)."
                    noTv.setTextColor(Color.RED)
                    noTv.setPadding(12, 14, 12, 14)
                    binding.llMatchingPurBillsList.addView(noTv)
                }
            }
            .addOnFailureListener {
                binding.llMatchingPurBillsList.removeAllViews()
                Toast.makeText(this, "Error loading purchases", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmAndDeletePurchaseBill(docId: String, billNo: String, data: Map<String, Any>) {
        AlertDialog.Builder(this)
            .setTitle("Delete Purchase Bill")
            .setMessage("Are you sure you want to delete $billNo? Associated stock lot and Supplier balance will be adjusted.")
            .setPositiveButton("DELETE") { _, _ ->
                deletePurchaseBillAndRestore(docId, data)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePurchaseBillAndRestore(docId: String, data: Map<String, Any>) {
        val supId = data["supplierId"]?.toString() ?: ""
        val pMode = data["payMode"]?.toString() ?: "Credit"
        val totalAmt = (data["grandTotal"] as? Number)?.toDouble() ?: 0.0
        val lotId = data["batchId"]?.toString() ?: ""

        db.collection("purchases").document(docId).delete()
            .addOnSuccessListener {
                db.collection("stock_batches").whereEqualTo("purchaseId", docId).get()
                    .addOnSuccessListener { bSnap ->
                        if (!bSnap.isEmpty) {
                            for (bDoc in bSnap) bDoc.reference.delete()
                        } else if (lotId.isNotEmpty()) {
                            db.collection("stock_batches").whereEqualTo("batchId", lotId).get()
                                .addOnSuccessListener { fallbackSnap ->
                                    for (fDoc in fallbackSnap) fDoc.reference.delete()
                                }
                        }
                    }

                if (pMode.equals("Credit", ignoreCase = true) && supId.isNotEmpty()) {
                    val supRef = db.collection("suppliers").document(supId)
                    db.runTransaction { tr ->
                        val snap = tr.get(supRef)
                        if (snap.exists()) {
                            val curr = snap.getDouble("currentBalance") ?: 0.0
                            tr.update(supRef, "currentBalance", (curr - totalAmt).coerceAtLeast(0.0))
                        }
                    }
                }

                if (editingPurchaseDocId == docId) {
                    editingPurchaseDocId = ""
                    resetFormForFreshEntry()
                }
                Toast.makeText(this, "Purchase Bill Deleted!", Toast.LENGTH_SHORT).show()
                loadPurchasesForEditFilter()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete purchase", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateFormFromPurchaseDoc(docId: String, data: Map<String, Any>) {
        editingPurchaseDocId = docId
        oldSupplierId = data["supplierId"]?.toString() ?: ""
        oldPayMode = data["payMode"]?.toString() ?: "Credit"
        oldGrandTotal = (data["grandTotal"] as? Number)?.toDouble() ?: 0.0
        oldBatchId = data["batchId"]?.toString() ?: ""

        val bNo = data["billNo"]?.toString() ?: ""
        val sName = data["supplierName"]?.toString() ?: ""
        val bDate = data["date"]?.toString() ?: editFilterDateStr

        selectedDateStr = bDate
        if (bDate.length >= 2) selectedDayStr = bDate.substring(0, 2)
        binding.etPurchaseDate.setText(bDate)
        binding.etBillNo.setText(bNo)
        binding.tvGeneratedBatchId.text = oldBatchId
        etEditableBatchId.setText(oldBatchId)

        val payIdx = payModes.indexOfFirst { it.equals(oldPayMode, ignoreCase = true) }
        if (payIdx >= 0) binding.spPayMode.setSelection(payIdx)

        clearJsonArray(cartItems)
        clearJsonArray(oldCartItemsJson)
        val rawItems = data["items"] as? List<*>
        var firstItemName = "Onion"
        if (rawItems != null) {
            for (idx in rawItems.indices) {
                val map = rawItems[idx] as? Map<*, *> ?: continue
                val iName = map["itemName"]?.toString() ?: "Onion"
                if (idx == 0) firstItemName = iName
                val obj = JSONObject()
                obj.put("itemName", iName)
                obj.put("nos", (map["nos"] as? Number)?.toInt() ?: 0)
                obj.put("bags", (map["bags"] as? Number)?.toInt() ?: 0)
                obj.put("bagRate", (map["bagRate"] as? Number)?.toDouble() ?: 0.0)
                obj.put("purchaseKgs", (map["purchaseKgs"] as? Number)?.toDouble() ?: 0.0)
                obj.put("kudumi", (map["kudumi"] as? Number)?.toDouble() ?: 0.0)
                obj.put("thadukki", (map["thadukki"] as? Number)?.toDouble() ?: 0.0)
                obj.put("netKgs", (map["netKgs"] as? Number)?.toDouble() ?: 0.0)
                obj.put("ratePerKg", (map["ratePerKg"] as? Number)?.toDouble() ?: 0.0)
                obj.put("lineTotal", (map["lineTotal"] as? Number)?.toDouble() ?: 0.0)
                cartItems.put(obj)
                oldCartItemsJson.put(JSONObject(obj.toString()))
            }
        }

        val itemIdx = itemNames.indexOfFirst { it.equals(firstItemName, ignoreCase = true) }
        if (itemIdx >= 0) binding.spItem.setSelection(itemIdx)

        if (oldSupplierId.isNotEmpty() && sName.isNotEmpty()) {
            filteredSupplierMap[sName] = oldSupplierId
        }
        binding.actvSupplier.setText(sName, false)

        refreshCartGrid()
        Toast.makeText(this, "Loaded $bNo (Stock ID: $oldBatchId) for editing", Toast.LENGTH_SHORT).show()
    }

    private fun loadGridRowIntoFormForEdit(index: Int) {
        if (index < 0 || index >= cartItems.length()) return
        val row = cartItems.getJSONObject(index)
        val iName = row.getString("itemName")
        val nos = row.optInt("nos", 0)
        val bags = row.getInt("bags")
        val bagRate = row.getDouble("bagRate")
        val purKgs = row.getDouble("purchaseKgs")
        val kudumi = row.getDouble("kudumi")
        val thadukki = row.getDouble("thadukki")
        val rate = row.getDouble("ratePerKg")

        suppressKudumiAutoCalc = true

        val itemIdx = itemNames.indexOfFirst { it.equals(iName, ignoreCase = true) }
        if (itemIdx >= 0) {
            binding.spItem.setSelection(itemIdx)
            selectedItemName = itemNames[itemIdx]
        }

        val isOnion = iName.equals("Onion", ignoreCase = true)
        val isCoconut = iName.equals("Coconut", ignoreCase = true)

        binding.layoutOnionFields.visibility = if (isOnion) View.VISIBLE else View.GONE
        binding.layoutCoconutFields.visibility = if (isCoconut) View.VISIBLE else View.GONE
        nosContainerLayout.visibility = if (isCoconut) View.VISIBLE else View.GONE

        if (isCoconut) {
            etTotalNos.setText(if (nos > 0) nos.toString() else "")
        } else {
            etTotalNos.setText("")
        }

        if (isOnion) {
            binding.etBags.setText(if (bags > 0) bags.toString() else "")
            binding.etBagRate.setText(bagRate.toString())
        }

        binding.etNetKgs.setText(purKgs.toString())
        binding.etRatePerKg.setText(rate.toString())

        if (isCoconut) {
            binding.etKudumi.setText(kudumi.toString())
            binding.etThadukki.setText(thadukki.toString())
            updateCoconutNetWeightPreview()
        }

        suppressKudumiAutoCalc = false

        cartItems.remove(index)
        refreshCartGrid()
        updateLotIdPreview()

        binding.btnAddItem.text = "UPDATE ITEM IN BILL"
        Toast.makeText(this, "Editing $iName — update values & tap UPDATE ITEM IN BILL", Toast.LENGTH_SHORT).show()
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
        nosContainerLayout.visibility = if (isCoconut) View.VISIBLE else View.GONE

        if (isCoconut) {
            binding.layoutCoconutFields.visibility = View.VISIBLE
            if (!suppressKudumiAutoCalc) {
                calculateCoconutKudumi()
            }
            updateCoconutNetWeightPreview()
        } else {
            binding.layoutCoconutFields.visibility = View.GONE
            if (!suppressKudumiAutoCalc) {
                etTotalNos.setText("")
                binding.etKudumi.setText("")
                binding.etThadukki.setText("")
                binding.tvCoconutNetWeight.text = "Net Weight: 0.00 Kgs"
            }
        }

        val currentSup = binding.actvSupplier.text.toString().trim()
        filterSuppliersForItem(itemName, preserveName = if (editingPurchaseDocId.isNotEmpty() || suppressKudumiAutoCalc) currentSup else "")
        updateLotIdPreview()
    }

    private fun filterSuppliersForItem(itemName: String, preserveName: String = "") {
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
        binding.actvSupplier.setText(preserveName, false)
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
            if (!isEditMode) {
                binding.etBillNo.setText(String.format(Locale.getDefault(), "PUR-%03d", nextNum))
            }

            var sameDayCount = 0
            for (doc in snap) {
                if ((doc.getString("date") ?: "").trim() == selectedDateStr) {
                    sameDayCount++
                }
            }
            dailyPurchaseIndex = sameDayCount
            updateLotIdPreview()
        }
    }

    private fun updateLotIdPreview() {
        if (isEditMode && editingPurchaseDocId.isNotEmpty()) {
            return
        }
        val supplierName = binding.actvSupplier.text.toString()
        val cleanName = supplierName.replace("[^A-Za-z]".toRegex(), "").uppercase(Locale.getDefault())
        val prefix = when {
            cleanName.length >= 3 -> cleanName.substring(0, 3)
            cleanName.isNotEmpty() -> cleanName.padEnd(3, 'X')
            else -> "SUP"
        }

        val dayOnly = selectedDayStr.ifEmpty {
            selectedDateStr.substringBefore("-").padStart(2, '0')
        }
        val indexStr = dailyPurchaseIndex.toString()

        binding.tvGeneratedBatchId.text = "$prefix-$dayOnly-$indexStr"
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
            clearJsonArray(allSuppliers)
            editFilterSupplierList.clear()
            editFilterSupplierList.add("All Suppliers")

            for (doc in snap) {
                val name = doc.getString("name") ?: continue
                val obj = JSONObject()
                obj.put("id", doc.id)
                obj.put("name", name)
                obj.put("mappedItem", doc.getString("mappedItem") ?: "")
                allSuppliers.put(obj)
                editFilterSupplierList.add(name)
            }

            filterSuppliersForItem(selectedItemName)

            val filterAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, editFilterSupplierList)
            filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spEditPurFilterSupplier.adapter = filterAdapter
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
            val nos = row.optInt("nos", 0)
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

            val countText = when {
                name.equals("Coconut", ignoreCase = true) && nos > 0 -> "$nos Nos"
                bags > 0 -> "$bags Bags"
                nos > 0 -> "$nos Nos"
                else -> "-"
            }

            tableRow.addView(makeCell(name, alignRight = false, bold = true))
            tableRow.addView(makeCell(countText, alignRight = true, bold = (nos > 0)))
            tableRow.addView(makeCell("%.1f".format(purKgs), alignRight = true))
            tableRow.addView(makeCell(dedText, alignRight = true))
            tableRow.addView(makeCell("%.2f".format(netKgs), alignRight = true, bold = true, colorHex = "#1B5E20"))
            tableRow.addView(makeCell("%.2f".format(rate), alignRight = true))
            tableRow.addView(makeCell("%.2f".format(total), alignRight = true, bold = true, colorHex = "#1565C0"))

            val actionLayout = LinearLayout(this)
            actionLayout.orientation = LinearLayout.HORIZONTAL
            actionLayout.gravity = Gravity.CENTER

            val editCellBtn = TextView(this)
            editCellBtn.text = "EDIT"
            editCellBtn.setTextColor(Color.parseColor("#1565C0"))
            editCellBtn.setTypeface(null, Typeface.BOLD)
            editCellBtn.textSize = 11f
            editCellBtn.setPadding(12, 14, 12, 14)
            editCellBtn.setOnClickListener {
                loadGridRowIntoFormForEdit(idx)
            }

            val delBtn = TextView(this)
            delBtn.text = "X"
            delBtn.setTextColor(Color.RED)
            delBtn.setTypeface(null, Typeface.BOLD)
            delBtn.textSize = 12f
            delBtn.setPadding(14, 14, 14, 14)
            delBtn.setOnClickListener {
                cartItems.remove(idx)
                refreshCartGrid()
                updateLotIdPreview()
            }

            actionLayout.addView(editCellBtn)
            actionLayout.addView(delBtn)
            tableRow.addView(actionLayout)

            binding.tlPurchaseGrid.addView(tableRow)
        }

        binding.tvGrandTotal.text = "Grand Total: Rs. %.2f".format(grandTotal)
    }

    private fun clearJsonArray(arr: JSONArray) {
        while (arr.length() > 0) arr.remove(0)
    }

    private fun resetFormForFreshEntry() {
        clearJsonArray(cartItems)
        grandTotal = 0.0
        selectedSupplierId = ""
        selectedSupplierName = ""

        binding.actvSupplier.setText("")
        etEditableBatchId.setText("")
        etTotalNos.setText("")
        binding.etBags.setText("")
        binding.etBagRate.setText("10")
        binding.etNetKgs.setText("")
        binding.etRatePerKg.setText("")
        binding.etKudumi.setText("")
        binding.etThadukki.setText("")
        binding.tvCoconutNetWeight.text = "Net Weight: 0.00 Kgs"
        binding.spPayMode.setSelection(0)
        binding.btnAddItem.text = "+ ADD ITEM TO PURCHASE BILL"

        refreshCartGrid()
        if (!isEditMode) fetchNextBillNumber() else {
            binding.etBillNo.setText("")
            binding.tvGeneratedBatchId.text = ""
        }
        updateLotIdPreview()

        binding.btnSavePurchase.isEnabled = true
    }

    private fun createStockBatchesAndBalance(purchaseId: String, batchId: String, payMode: String) {
        for (i in 0 until cartItems.length()) {
            val row = cartItems.getJSONObject(i)
            val itemName = row.getString("itemName")
            val nos = row.optInt("nos", 0)
            val bags = row.getInt("bags")
            val netKgs = row.getDouble("netKgs")
            val rate = row.getDouble("ratePerKg")

            val batchDoc = db.collection("stock_batches").document()
            val batchData = hashMapOf(
                "purchaseId" to purchaseId,
                "batchId" to batchId,
                "supplierId" to selectedSupplierId,
                "supplierName" to selectedSupplierName,
                "itemName" to itemName,
                "availableBags" to if (bags > 0) bags else nos,
                "availableNos" to nos,
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
        resetFormForFreshEntry()
    }

    /**
     * Updates Stock ID (batchId), supplier info, and net quantity deltas in stock_batches
     * while preserving any quantity already sold from this batch.
     */
    private fun reconcileStockAndSupplierOnEdit(purchaseId: String, newBatchId: String, newPayMode: String) {
        db.collection("stock_batches").whereEqualTo("purchaseId", purchaseId).get()
            .addOnSuccessListener { bSnap ->
                if (!bSnap.isEmpty) {
                    val existingDocs = bSnap.documents
                    for (i in 0 until cartItems.length()) {
                        val row = cartItems.getJSONObject(i)
                        val newItemName = row.getString("itemName")
                        val newNos = row.optInt("nos", 0)
                        val newBags = row.getInt("bags")
                        val newEffectiveBags = if (newBags > 0) newBags else newNos
                        val newNetKgs = row.getDouble("netKgs")
                        val newRate = row.getDouble("ratePerKg")

                        if (i < existingDocs.size) {
                            val bDoc = existingDocs[i]
                            val curAvailBags = (bDoc.getLong("availableBags") ?: 0L).toInt()
                            val curAvailNos = (bDoc.getLong("availableNos") ?: 0L).toInt()
                            val curAvailKgs = bDoc.getDouble("availableKgs") ?: 0.0

                            var oldEffectiveBags = 0
                            var oldNos = 0
                            var oldNetKgs = 0.0
                            if (i < oldCartItemsJson.length()) {
                                val oldRow = oldCartItemsJson.getJSONObject(i)
                                oldNos = oldRow.optInt("nos", 0)
                                val ob = oldRow.optInt("bags", 0)
                                oldEffectiveBags = if (ob > 0) ob else oldNos
                                oldNetKgs = oldRow.optDouble("netKgs", 0.0)
                            }

                            val updatedAvailBags = (curAvailBags + (newEffectiveBags - oldEffectiveBags)).coerceAtLeast(0)
                            val updatedAvailNos = (curAvailNos + (newNos - oldNos)).coerceAtLeast(0)
                            val updatedAvailKgs = (curAvailKgs + (newNetKgs - oldNetKgs)).coerceAtLeast(0.0)

                            bDoc.reference.update(
                                hashMapOf<String, Any>(
                                    "batchId" to newBatchId,
                                    "supplierId" to selectedSupplierId,
                                    "supplierName" to selectedSupplierName,
                                    "itemName" to newItemName,
                                    "availableBags" to updatedAvailBags,
                                    "availableNos" to updatedAvailNos,
                                    "availableKgs" to updatedAvailKgs,
                                    "purchaseRate" to newRate
                                )
                            )
                        } else {
                            val batchDoc = db.collection("stock_batches").document()
                            batchDoc.set(
                                hashMapOf(
                                    "purchaseId" to purchaseId,
                                    "batchId" to newBatchId,
                                    "supplierId" to selectedSupplierId,
                                    "supplierName" to selectedSupplierName,
                                    "itemName" to newItemName,
                                    "availableBags" to newEffectiveBags,
                                    "availableNos" to newNos,
                                    "availableKgs" to newNetKgs,
                                    "purchaseRate" to newRate,
                                    "timestamp" to System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    if (existingDocs.size > cartItems.length()) {
                        for (j in cartItems.length() until existingDocs.size) {
                            existingDocs[j].reference.delete()
                        }
                    }
                } else if (oldBatchId.isNotEmpty()) {
                    db.collection("stock_batches").whereEqualTo("batchId", oldBatchId).get()
                        .addOnSuccessListener { fallbackSnap ->
                            for (fDoc in fallbackSnap) {
                                fDoc.reference.update(
                                    hashMapOf<String, Any>(
                                        "batchId" to newBatchId,
                                        "supplierId" to selectedSupplierId,
                                        "supplierName" to selectedSupplierName
                                    )
                                )
                            }
                        }
                }
            }

        if (oldPayMode.equals("Credit", ignoreCase = true) && oldSupplierId.isNotEmpty()) {
            val oldSupRef = db.collection("suppliers").document(oldSupplierId)
            db.runTransaction { tr ->
                val snap = tr.get(oldSupRef)
                if (snap.exists()) {
                    val curr = snap.getDouble("currentBalance") ?: 0.0
                    val afterRev = (curr - oldGrandTotal).coerceAtLeast(0.0)
                    val finalBal = if (newPayMode.equals("Credit", ignoreCase = true) && selectedSupplierId == oldSupplierId) {
                        afterRev + grandTotal
                    } else {
                        afterRev
                    }
                    tr.update(oldSupRef, "currentBalance", finalBal)
                }
            }
        }

        if (newPayMode.equals("Credit", ignoreCase = true) && selectedSupplierId.isNotEmpty() &&
            !(oldPayMode.equals("Credit", ignoreCase = true) && selectedSupplierId == oldSupplierId)) {
            val newSupRef = db.collection("suppliers").document(selectedSupplierId)
            db.runTransaction { tr ->
                val snap = tr.get(newSupRef)
                if (snap.exists()) {
                    val curr = snap.getDouble("currentBalance") ?: 0.0
                    tr.update(newSupRef, "currentBalance", curr + grandTotal)
                }
            }
        }

        Toast.makeText(this, "Purchase Bill & Stock ID ($newBatchId) Updated!", Toast.LENGTH_SHORT).show()
        editingPurchaseDocId = ""
        oldBatchId = ""
        clearJsonArray(oldCartItemsJson)
        resetFormForFreshEntry()
        loadPurchasesForEditFilter()
    }
}
