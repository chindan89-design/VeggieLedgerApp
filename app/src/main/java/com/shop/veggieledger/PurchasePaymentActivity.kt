package com.shop.veggieledger

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.shop.veggieledger.databinding.ActivityPurchasePaymentBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class PurchasePaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPurchasePaymentBinding
    private val db = FirebaseFirestore.getInstance()

    private val supplierList = ArrayList<SupplierItem>()
    private val editFilterSupplierList = ArrayList<String>()
    private val pendingBills = ArrayList<PendingPurBill>()

    private var selectedSupplierId: String = ""
    private var selectedSupplierName: String = ""
    private var selectedSupplierOpBal: Double = 0.0
    private var calculatedTotalPayable: Double = 0.0
    private var selectedDateStr: String = ""
    private var editFilterDateStr: String = ""
    private var targetSpecificBillDocId: String = ""

    // Edit Mode State
    private var isEditMode: Boolean = false
    private var editingPaymentDocId: String = ""
    private var oldPaymentSupplierId: String = ""
    private var oldPaymentAmount: Double = 0.0
    private val oldAllocationsJson = JSONArray()

    private val modes = arrayListOf("Cash", "GPay / UPI", "Bank Transfer", "Cheque")

    data class SupplierItem(val id: String, val name: String, val balance: Double, val opBal: Double)

    data class PendingPurBill(
        val docId: String,
        val billNo: String,
        val lotId: String,
        val date: String,
        val totalAmount: Double,
        val alreadyPaid: Double,
        val pendingAmount: Double,
        val timestamp: Long,
        var allocatedNow: Double = 0.0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurchasePaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Purchase Payment"

        binding.btnModeNewPay.setOnClickListener { switchToNewMode() }
        binding.btnModeEditPay.setOnClickListener { switchToEditMode() }

        val cal = Calendar.getInstance()
        selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        editFilterDateStr = selectedDateStr
        binding.etPayDate.setText(selectedDateStr)
        binding.etEditPayFilterDate.setText(editFilterDateStr)

        binding.etPayDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                binding.etPayDate.setText(selectedDateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val editCal = Calendar.getInstance()
        binding.etEditPayFilterDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                editCal.set(y, m, d)
                editFilterDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(editCal.time)
                binding.etEditPayFilterDate.setText(editFilterDateStr)
                loadPaymentsForEditFilter()
            }, editCal.get(Calendar.YEAR), editCal.get(Calendar.MONTH), editCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.spEditPayFilterSupplier.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (isEditMode) loadPaymentsForEditFilter()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spPayMode.adapter = modeAdapter

        fetchNextPaymentNo()
        loadSuppliers()
        refreshBillWiseGrid()

        binding.etSearchSupplier.setOnClickListener {
            showSupplierSearchDialog()
        }

        binding.etPayAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                allocateAmountToBills()
                refreshBillWiseGrid()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSavePayment.setOnClickListener {
            val amount = binding.etPayAmount.text.toString().toDoubleOrNull() ?: 0.0
            val payNo = binding.etPayNo.text.toString().trim()
            val mode = binding.spPayMode.selectedItem?.toString() ?: "Cash"
            val remarks = binding.etPayRemarks.text.toString().trim()

            if (selectedSupplierId.isEmpty()) {
                Toast.makeText(this, "Please select a Supplier", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (amount <= 0.0) {
                Toast.makeText(this, "Enter valid Payment Amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isEditMode && editingPaymentDocId.isEmpty()) {
                Toast.makeText(this, "Please select a payment voucher above to edit", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSavePayment.isEnabled = false
            allocateAmountToBills()

            val allocationsForDb = ArrayList<Any>()
            for (b in pendingBills) {
                if (b.allocatedNow > 0.0) {
                    val allocMap = HashMap<String, Any>()
                    allocMap["billDocId"] = b.docId
                    allocMap["billNo"] = b.billNo
                    allocMap["lotId"] = b.lotId
                    allocMap["billDate"] = b.date
                    allocMap["allocatedAmount"] = b.allocatedNow
                    allocMap["remainingBalance"] = (b.pendingAmount - b.allocatedNow).coerceAtLeast(0.0)
                    allocationsForDb.add(allocMap)
                }
            }

            if (isEditMode && editingPaymentDocId.isNotEmpty()) {
                val updateData = hashMapOf<String, Any>(
                    "paymentNo" to payNo,
                    "date" to selectedDateStr,
                    "supplierId" to selectedSupplierId,
                    "supplierName" to selectedSupplierName,
                    "amount" to amount,
                    "payMode" to mode,
                    "remarks" to remarks,
                    "billAllocations" to allocationsForDb
                )
                db.collection("payments").document(editingPaymentDocId).update(updateData)
                    .addOnSuccessListener {
                        applyPaymentBillUpdates(amount)
                    }
                    .addOnFailureListener {
                        binding.btnSavePayment.isEnabled = true
                        Toast.makeText(this, "Failed to update payment", Toast.LENGTH_SHORT).show()
                    }
            } else {
                val payId = db.collection("payments").document().id
                val payData = hashMapOf(
                    "id" to payId,
                    "paymentNo" to payNo,
                    "date" to selectedDateStr,
                    "supplierId" to selectedSupplierId,
                    "supplierName" to selectedSupplierName,
                    "amount" to amount,
                    "payMode" to mode,
                    "remarks" to remarks,
                    "billAllocations" to allocationsForDb,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("payments").document(payId).set(payData)
                    .addOnSuccessListener {
                        applyPaymentBillUpdates(amount)
                    }
                    .addOnFailureListener {
                        binding.btnSavePayment.isEnabled = true
                        Toast.makeText(this, "Failed to save payment", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun clearJsonArray(arr: JSONArray) {
        while (arr.length() > 0) arr.remove(0)
    }

    private fun applyPaymentBillUpdates(amount: Double) {
        for (b in pendingBills) {
            if (b.allocatedNow > 0.0) {
                val newPaid = b.alreadyPaid + b.allocatedNow
                val newBal = (b.totalAmount - newPaid).coerceAtLeast(0.0)
                val status = if (newBal <= 0.01) "Paid" else "Partial"
                db.collection("purchases").document(b.docId).update(
                    mapOf(
                        "paidAmount" to newPaid,
                        "balanceAmount" to newBal,
                        "paymentStatus" to status
                    )
                )
            }
        }

        val updatedBalance = (calculatedTotalPayable - amount).coerceAtLeast(0.0)
        db.collection("suppliers").document(selectedSupplierId)
            .set(mapOf("currentBalance" to updatedBalance), SetOptions.merge())
            .addOnCompleteListener {
                val msg = if (isEditMode) "Payment Updated Successfully!" else "Payment Saved & Bill-wise Adjusted!"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                editingPaymentDocId = ""
                clearJsonArray(oldAllocationsJson)
                resetForm()
                if (isEditMode) loadPaymentsForEditFilter()
            }
    }

    private fun switchToNewMode() {
        isEditMode = false
        editingPaymentDocId = ""
        clearJsonArray(oldAllocationsJson)
        binding.layoutEditPayPanel.visibility = View.GONE
        binding.btnModeNewPay.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1565C0"))
        binding.btnModeEditPay.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        binding.tvPayHeader.text = "PURCHASE PAYMENT (BILL-WISE)"
        binding.btnSavePayment.text = "SAVE PAYMENT & UPDATE BILLS"
        binding.btnSavePayment.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1565C0"))
        resetForm()
    }

    private fun switchToEditMode() {
        isEditMode = true
        editingPaymentDocId = ""
        clearJsonArray(oldAllocationsJson)
        binding.layoutEditPayPanel.visibility = View.VISIBLE
        binding.btnModeEditPay.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
        binding.btnModeNewPay.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        binding.tvPayHeader.text = "EDIT / DELETE PURCHASE PAYMENT"
        binding.btnSavePayment.text = "UPDATE PAYMENT"
        binding.btnSavePayment.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
        resetForm()
        loadPaymentsForEditFilter()
    }

    private fun loadPaymentsForEditFilter() {
        val selectedFilterSup = binding.spEditPayFilterSupplier.selectedItem?.toString() ?: "All Suppliers"
        binding.llMatchingPayList.removeAllViews()

        val loadingTv = TextView(this)
        loadingTv.text = "Loading payments..."
        loadingTv.setTextColor(Color.DKGRAY)
        loadingTv.setPadding(12, 12, 12, 12)
        binding.llMatchingPayList.addView(loadingTv)

        db.collection("payments")
            .whereEqualTo("date", editFilterDateStr)
            .get()
            .addOnSuccessListener { snap ->
                binding.llMatchingPayList.removeAllViews()
                var matchCount = 0

                for (doc in snap) {
                    val sName = doc.getString("supplierName") ?: ""
                    if (selectedFilterSup != "All Suppliers" && !sName.equals(selectedFilterSup, ignoreCase = true)) {
                        continue
                    }

                    matchCount++
                    val pNo = doc.getString("paymentNo") ?: doc.id
                    val amt = doc.getDouble("amount") ?: 0.0
                    val pMode = doc.getString("payMode") ?: "Cash"

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
                    infoTv.text = "$pNo | $sName\n$pMode | Rs. %.2f".format(amt)
                    infoTv.setTextColor(Color.parseColor("#1565C0"))
                    infoTv.setTypeface(null, Typeface.BOLD)
                    infoTv.textSize = 13f
                    infoTv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

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
                        populateFormFromPaymentDoc(doc.id, doc.data)
                    }

                    val delBtn = TextView(this)
                    delBtn.text = "DEL"
                    delBtn.setTextColor(Color.WHITE)
                    delBtn.setBackgroundColor(Color.parseColor("#C62828"))
                    delBtn.setTypeface(null, Typeface.BOLD)
                    delBtn.textSize = 12f
                    delBtn.setPadding(24, 14, 24, 14)
                    delBtn.setOnClickListener {
                        confirmAndDeletePayment(doc.id, pNo, doc.data)
                    }

                    rowCard.addView(infoTv)
                    rowCard.addView(editBtn)
                    rowCard.addView(delBtn)
                    binding.llMatchingPayList.addView(rowCard)
                }

                if (matchCount == 0) {
                    val noTv = TextView(this)
                    noTv.text = "No payments found for $editFilterDateStr ($selectedFilterSup)."
                    noTv.setTextColor(Color.RED)
                    noTv.setPadding(12, 14, 12, 14)
                    binding.llMatchingPayList.addView(noTv)
                }
            }
            .addOnFailureListener {
                binding.llMatchingPayList.removeAllViews()
                Toast.makeText(this, "Error loading payments", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmAndDeletePayment(docId: String, pNo: String, data: Map<String, Any>) {
        AlertDialog.Builder(this)
            .setTitle("Delete Payment")
            .setMessage("Are you sure you want to delete $pNo? Purchase bill balances and Supplier payable will be restored.")
            .setPositiveButton("DELETE") { _, _ ->
                deletePaymentAndRestore(docId, data)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePaymentAndRestore(docId: String, data: Map<String, Any>) {
        val supId = data["supplierId"]?.toString() ?: ""
        val amt = (data["amount"] as? Number)?.toDouble() ?: 0.0
        val rawAllocs = data["billAllocations"] as? List<*>

        db.collection("payments").document(docId).delete()
            .addOnSuccessListener {
                if (rawAllocs != null) {
                    for (entry in rawAllocs) {
                        val map = entry as? Map<*, *> ?: continue
                        val bDocId = map["billDocId"]?.toString() ?: ""
                        val allocAmt = (map["allocatedAmount"] as? Number)?.toDouble() ?: 0.0
                        if (bDocId.isNotEmpty() && allocAmt > 0.0) {
                            val purRef = db.collection("purchases").document(bDocId)
                            db.runTransaction { tr ->
                                val snap = tr.get(purRef)
                                if (snap.exists()) {
                                    val total = snap.getDouble("grandTotal") ?: 0.0
                                    val currPaid = snap.getDouble("paidAmount") ?: 0.0
                                    val restoredPaid = (currPaid - allocAmt).coerceAtLeast(0.0)
                                    val restoredBal = (total - restoredPaid).coerceAtLeast(0.0)
                                    val status = if (restoredPaid <= 0.01) "Unpaid" else "Partial"
                                    tr.update(purRef, mapOf(
                                        "paidAmount" to restoredPaid,
                                        "balanceAmount" to restoredBal,
                                        "paymentStatus" to status
                                    ))
                                }
                            }
                        }
                    }
                }

                if (supId.isNotEmpty()) {
                    val supRef = db.collection("suppliers").document(supId)
                    db.runTransaction { tr ->
                        val snap = tr.get(supRef)
                        if (snap.exists()) {
                            val curr = snap.getDouble("currentBalance") ?: 0.0
                            tr.update(supRef, "currentBalance", curr + amt)
                        }
                    }
                }

                if (editingPaymentDocId == docId) {
                    editingPaymentDocId = ""
                    resetForm()
                }
                Toast.makeText(this, "Payment Deleted & Balances Restored!", Toast.LENGTH_SHORT).show()
                loadPaymentsForEditFilter()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete payment", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateFormFromPaymentDoc(docId: String, data: Map<String, Any>) {
        editingPaymentDocId = docId
        oldPaymentSupplierId = data["supplierId"]?.toString() ?: ""
        oldPaymentAmount = (data["amount"] as? Number)?.toDouble() ?: 0.0

        val pNo = data["paymentNo"]?.toString() ?: ""
        val sName = data["supplierName"]?.toString() ?: ""
        val pDate = data["date"]?.toString() ?: editFilterDateStr
        val pMode = data["payMode"]?.toString() ?: "Cash"
        val remarks = data["remarks"]?.toString() ?: ""

        selectedDateStr = pDate
        selectedSupplierId = oldPaymentSupplierId
        selectedSupplierName = sName
        targetSpecificBillDocId = ""

        val foundSup = supplierList.find { it.id == oldPaymentSupplierId }
        selectedSupplierOpBal = foundSup?.opBal ?: 0.0

        binding.etPayDate.setText(pDate)
        binding.etPayNo.setText(pNo)
        binding.etSearchSupplier.setText(sName)
        binding.etPayRemarks.setText(remarks)

        val modeIdx = modes.indexOfFirst { it.equals(pMode, ignoreCase = true) }
        if (modeIdx >= 0) binding.spPayMode.setSelection(modeIdx)

        clearJsonArray(oldAllocationsJson)
        val rawAllocs = data["billAllocations"] as? List<*>
        if (rawAllocs != null) {
            for (entry in rawAllocs) {
                val map = entry as? Map<*, *> ?: continue
                val obj = JSONObject()
                obj.put("billDocId", map["billDocId"]?.toString() ?: "")
                obj.put("allocatedAmount", (map["allocatedAmount"] as? Number)?.toDouble() ?: 0.0)
                oldAllocationsJson.put(obj)
            }
        }

        for (i in 0 until oldAllocationsJson.length()) {
            val obj = oldAllocationsJson.getJSONObject(i)
            val bDocId = obj.optString("billDocId", "")
            val prevAlloc = obj.optDouble("allocatedAmount", 0.0)
            if (bDocId.isNotEmpty() && prevAlloc > 0.0) {
                val purRef = db.collection("purchases").document(bDocId)
                db.runTransaction { tr ->
                    val snap = tr.get(purRef)
                    if (snap.exists()) {
                        val total = snap.getDouble("grandTotal") ?: 0.0
                        val currPaid = snap.getDouble("paidAmount") ?: 0.0
                        val revPaid = (currPaid - prevAlloc).coerceAtLeast(0.0)
                        val revBal = (total - revPaid).coerceAtLeast(0.0)
                        tr.update(purRef, mapOf("paidAmount" to revPaid, "balanceAmount" to revBal))
                    }
                }
            }
        }

        binding.etPayAmount.setText("%.2f".format(oldPaymentAmount))
        loadPendingBillsForSupplier(selectedSupplierId, selectedSupplierName, selectedSupplierOpBal)
        Toast.makeText(this, "Loaded $pNo for editing", Toast.LENGTH_SHORT).show()
    }

    private fun showSupplierSearchDialog() {
        if (supplierList.isEmpty()) {
            Toast.makeText(this, "Loading suppliers, please wait...", Toast.LENGTH_SHORT).show()
            loadSuppliers()
            return
        }

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(32, 24, 32, 16)

        val searchInput = EditText(this)
        searchInput.hint = "Type supplier name to filter..."
        searchInput.setSingleLine(true)
        container.addView(searchInput)

        val listView = ListView(this)
        val filtered = ArrayList<SupplierItem>(supplierList)
        val displayNames = ArrayList<String>()
        for (s in filtered) {
            displayNames.add(s.name)
        }

        val listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayNames)
        listView.adapter = listAdapter
        container.addView(listView)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Supplier")
            .setView(container)
            .setNegativeButton("Close", null)
            .create()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: ""
                filtered.clear()
                displayNames.clear()
                for (item in supplierList) {
                    if (item.name.lowercase(Locale.getDefault()).contains(query)) {
                        filtered.add(item)
                        displayNames.add(item.name)
                    }
                }
                listAdapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val chosen = filtered[position]
            selectedSupplierId = chosen.id
            selectedSupplierName = chosen.name
            selectedSupplierOpBal = chosen.opBal
            targetSpecificBillDocId = ""
            binding.etSearchSupplier.setText(chosen.name)
            binding.tvSupPayable.text = "Calculating Payable..."
            loadPendingBillsForSupplier(chosen.id, chosen.name, chosen.opBal)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadPendingBillsForSupplier(supId: String, supName: String, opBal: Double) {
        pendingBills.clear()
        refreshBillWiseGrid()

        db.collection("purchases")
            .get()
            .addOnSuccessListener { snap ->
                pendingBills.clear()
                var totalBillsPending = 0.0

                for (doc in snap) {
                    val docSupId = doc.getString("supplierId") ?: ""
                    val docSupName = doc.getString("supplierName") ?: ""
                    if (docSupId != supId && !docSupName.equals(supName, ignoreCase = true)) continue

                    val pMode = doc.getString("payMode") ?: "Credit"
                    if (!pMode.equals("Credit", ignoreCase = true)) continue

                    val total = doc.getDouble("grandTotal") ?: 0.0
                    val paid = doc.getDouble("paidAmount") ?: 0.0
                    val bal = doc.getDouble("balanceAmount") ?: (total - paid)

                    if (bal > 0.01) {
                        totalBillsPending += bal
                        pendingBills.add(
                            PendingPurBill(
                                docId = doc.id,
                                billNo = doc.getString("billNo") ?: doc.id,
                                lotId = doc.getString("batchId") ?: "-",
                                date = doc.getString("date") ?: "-",
                                totalAmount = total,
                                alreadyPaid = paid,
                                pendingAmount = bal,
                                timestamp = doc.getLong("timestamp") ?: 0L
                            )
                        )
                    }
                }

                calculatedTotalPayable = totalBillsPending + opBal
                binding.tvSupPayable.text = "Total Payable Balance: Rs. %.2f".format(calculatedTotalPayable)

                db.collection("suppliers").document(supId)
                    .set(mapOf("currentBalance" to calculatedTotalPayable), SetOptions.merge())

                pendingBills.sortBy { it.timestamp }
                allocateAmountToBills()
                refreshBillWiseGrid()
            }
    }

    private fun allocateAmountToBills() {
        var remaining = binding.etPayAmount.text.toString().toDoubleOrNull() ?: 0.0
        for (b in pendingBills) b.allocatedNow = 0.0

        if (targetSpecificBillDocId.isNotEmpty()) {
            val target = pendingBills.find { it.docId == targetSpecificBillDocId }
            if (target != null && remaining > 0.0) {
                val alloc = minOf(remaining, target.pendingAmount)
                target.allocatedNow = alloc
                remaining -= alloc
            }
        }

        for (b in pendingBills) {
            if (remaining <= 0.0) break
            if (b.docId == targetSpecificBillDocId) continue
            val alloc = minOf(remaining, b.pendingAmount)
            b.allocatedNow = alloc
            remaining -= alloc
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

    private fun refreshBillWiseGrid() {
        val childCount = binding.tlPaymentBills.childCount
        if (childCount > 1) {
            binding.tlPaymentBills.removeViews(1, childCount - 1)
        }

        if (pendingBills.isEmpty()) {
            val emptyRow = TableRow(this)
            emptyRow.setBackgroundColor(Color.WHITE)
            val tv = TextView(this)
            tv.text = if (selectedSupplierId.isEmpty()) "Select a supplier to view bill-wise pending details." else "No pending credit purchase bills found."
            tv.setTextColor(Color.GRAY)
            tv.setPadding(18, 18, 18, 18)
            val lp = TableRow.LayoutParams()
            lp.span = 7
            tv.layoutParams = lp
            emptyRow.addView(tv)
            binding.tlPaymentBills.addView(emptyRow)
            return
        }

        for (idx in pendingBills.indices) {
            val b = pendingBills[idx]
            val newBal = (b.pendingAmount - b.allocatedNow).coerceAtLeast(0.0)

            val tr = TableRow(this)
            val isTargeted = (b.docId == targetSpecificBillDocId)
            tr.setBackgroundColor(
                when {
                    isTargeted -> Color.parseColor("#FFF9C4")
                    idx % 2 == 0 -> Color.WHITE
                    else -> Color.parseColor("#F1F8FF")
                }
            )

            tr.addView(makeCell(b.billNo, bold = true))
            tr.addView(makeCell(b.lotId))
            tr.addView(makeCell(b.date))
            tr.addView(makeCell("%.2f".format(b.totalAmount), alignRight = true))
            tr.addView(makeCell("%.2f".format(b.pendingAmount), alignRight = true, bold = true, colorHex = "#D84315"))
            tr.addView(makeCell("%.2f".format(b.allocatedNow), alignRight = true, bold = true, colorHex = "#1B5E20"))
            tr.addView(makeCell("%.2f".format(newBal), alignRight = true, bold = true, colorHex = "#1565C0"))

            tr.setOnClickListener {
                targetSpecificBillDocId = b.docId
                binding.etPayAmount.setText("%.2f".format(b.pendingAmount))
                Toast.makeText(this, "Selected ${b.billNo} (Rs. %.2f)".format(b.pendingAmount), Toast.LENGTH_SHORT).show()
            }

            binding.tlPaymentBills.addView(tr)
        }
    }

    private fun fetchNextPaymentNo() {
        db.collection("payments").get().addOnSuccessListener { snap ->
            binding.etPayNo.setText(String.format("PAY-%03d", snap.size() + 1))
        }
    }

    private fun loadSuppliers() {
        db.collection("suppliers").get().addOnSuccessListener { snap ->
            supplierList.clear()
            editFilterSupplierList.clear()
            editFilterSupplierList.add("All Suppliers")

            for (doc in snap) {
                val name = doc.getString("name") ?: continue
                val bal = doc.getDouble("currentBalance") ?: 0.0
                val opBal = doc.getDouble("opbal") ?: doc.getDouble("openingBalance") ?: 0.0
                supplierList.add(SupplierItem(doc.id, name, bal, opBal))
                editFilterSupplierList.add(name)
            }
            supplierList.sortBy { it.name.lowercase(Locale.getDefault()) }

            val filterAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, editFilterSupplierList)
            filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spEditPayFilterSupplier.adapter = filterAdapter
        }
    }

    private fun resetForm() {
        selectedSupplierId = ""
        selectedSupplierName = ""
        selectedSupplierOpBal = 0.0
        calculatedTotalPayable = 0.0
        targetSpecificBillDocId = ""
        pendingBills.clear()
        refreshBillWiseGrid()

        binding.etSearchSupplier.setText("")
        binding.tvSupPayable.text = "Total Payable Balance: Rs. 0.00"
        binding.etPayAmount.setText("")
        binding.etPayRemarks.setText("")
        binding.spPayMode.setSelection(0)
        binding.btnSavePayment.isEnabled = true
        if (!isEditMode) fetchNextPaymentNo() else binding.etPayNo.setText("")
        loadSuppliers()
    }
}
