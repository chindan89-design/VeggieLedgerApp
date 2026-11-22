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
import com.shop.veggieledger.databinding.ActivitySalesReceiptBinding
import java.text.SimpleDateFormat
import java.util.*

class SalesReceiptActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalesReceiptBinding
    private val db = FirebaseFirestore.getInstance()

    private val customerList = ArrayList<CustomerItem>()
    private val editFilterCustomerList = ArrayList<String>()
    private val pendingBills = ArrayList<PendingBill>()

    private var selectedCustomerId: String = ""
    private var selectedCustomerName: String = ""
    private var selectedCustomerOpBal: Double = 0.0
    private var calculatedTotalOutstanding: Double = 0.0
    private var selectedDateStr: String = ""
    private var editFilterDateStr: String = ""
    private var targetSpecificBillDocId: String = ""

    // Edit Mode State
    private var isEditMode: Boolean = false
    private var editingReceiptDocId: String = ""
    private var oldReceiptCustomerId: String = ""
    private var oldReceiptAmount: Double = 0.0
    private val oldAllocations = ArrayList>()

    private val modes = arrayListOf("Cash", "GPay / UPI", "Bank Transfer", "Cheque")

    data class CustomerItem(val id: String, val name: String, val balance: Double, val opBal: Double)

    data class PendingBill(
        val docId: String,
        val billNo: String,
        val date: String,
        val totalAmount: Double,
        val alreadyPaid: Double,
        val pendingAmount: Double,
        val timestamp: Long,
        var allocatedNow: Double = 0.0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesReceiptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Sales Receipt"

        binding.btnModeNewRec.setOnClickListener { switchToNewMode() }
        binding.btnModeEditRec.setOnClickListener { switchToEditMode() }

        val cal = Calendar.getInstance()
        selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        editFilterDateStr = selectedDateStr
        binding.etRecDate.setText(selectedDateStr)
        binding.etEditRecFilterDate.setText(editFilterDateStr)

        binding.etRecDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                binding.etRecDate.setText(selectedDateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val editCal = Calendar.getInstance()
        binding.etEditRecFilterDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                editCal.set(y, m, d)
                editFilterDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(editCal.time)
                binding.etEditRecFilterDate.setText(editFilterDateStr)
                loadReceiptsForEditFilter()
            }, editCal.get(Calendar.YEAR), editCal.get(Calendar.MONTH), editCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.spEditRecFilterCustomer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (isEditMode) loadReceiptsForEditFilter()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spRecMode.adapter = modeAdapter

        fetchNextReceiptNo()
        loadCustomers()
        refreshBillWiseGrid()

        binding.etSearchCustomer.setOnClickListener {
            showCustomerSearchDialog()
        }

        binding.etRecAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                allocateAmountToBills()
                refreshBillWiseGrid()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSaveReceipt.setOnClickListener {
            val amount = binding.etRecAmount.text.toString().toDoubleOrNull() ?: 0.0
            val recNo = binding.etRecNo.text.toString().trim()
            val mode = binding.spRecMode.selectedItem?.toString() ?: "Cash"
            val remarks = binding.etRecRemarks.text.toString().trim()

            if (selectedCustomerId.isEmpty()) {
                Toast.makeText(this, "Please select a Customer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (amount <= 0.0) {
                Toast.makeText(this, "Enter valid Receipt Amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isEditMode && editingReceiptDocId.isEmpty()) {
                Toast.makeText(this, "Please select a receipt above to edit", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSaveReceipt.isEnabled = false
            allocateAmountToBills()

            val allocationsForDb = ArrayList<Any>()
            for (b in pendingBills) {
                if (b.allocatedNow > 0.0) {
                    val allocMap = HashMap<String, Any>()
                    allocMap["billDocId"] = b.docId
                    allocMap["billNo"] = b.billNo
                    allocMap["billDate"] = b.date
                    allocMap["allocatedAmount"] = b.allocatedNow
                    allocMap["remainingBalance"] = (b.pendingAmount - b.allocatedNow).coerceAtLeast(0.0)
                    allocationsForDb.add(allocMap)
                }
            }

            if (isEditMode && editingReceiptDocId.isNotEmpty()) {
                val updateData = hashMapOf<String, Any>(
                    "receiptNo" to recNo,
                    "date" to selectedDateStr,
                    "customerId" to selectedCustomerId,
                    "customerName" to selectedCustomerName,
                    "amount" to amount,
                    "payMode" to mode,
                    "remarks" to remarks,
                    "billAllocations" to allocationsForDb
                )
                db.collection("receipts").document(editingReceiptDocId).update(updateData)
                    .addOnSuccessListener {
                        applyReceiptBillUpdates(amount)
                    }
                    .addOnFailureListener {
                        binding.btnSaveReceipt.isEnabled = true
                        Toast.makeText(this, "Failed to update receipt", Toast.LENGTH_SHORT).show()
                    }
            } else {
                val recId = db.collection("receipts").document().id
                val recData = hashMapOf(
                    "id" to recId,
                    "receiptNo" to recNo,
                    "date" to selectedDateStr,
                    "customerId" to selectedCustomerId,
                    "customerName" to selectedCustomerName,
                    "amount" to amount,
                    "payMode" to mode,
                    "remarks" to remarks,
                    "billAllocations" to allocationsForDb,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("receipts").document(recId).set(recData)
                    .addOnSuccessListener {
                        applyReceiptBillUpdates(amount)
                    }
                    .addOnFailureListener {
                        binding.btnSaveReceipt.isEnabled = true
                        Toast.makeText(this, "Failed to save receipt", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun applyReceiptBillUpdates(amount: Double) {
        for (b in pendingBills) {
            if (b.allocatedNow > 0.0) {
                val newPaid = b.alreadyPaid + b.allocatedNow
                val newBal = (b.totalAmount - newPaid).coerceAtLeast(0.0)
                val status = if (newBal <= 0.01) "Paid" else "Partial"
                db.collection("sales").document(b.docId).update(
                    mapOf(
                        "paidAmount" to newPaid,
                        "balanceAmount" to newBal,
                        "paymentStatus" to status
                    )
                )
            }
        }

        val updatedBalance = (calculatedTotalOutstanding - amount).coerceAtLeast(0.0)
        db.collection("customers").document(selectedCustomerId)
            .set(mapOf("currentBalance" to updatedBalance), SetOptions.merge())
            .addOnCompleteListener {
                val msg = if (isEditMode) "Receipt Updated Successfully!" else "Receipt Saved & Bill-wise Adjusted!"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                editingReceiptDocId = ""
                oldAllocations.clear()
                resetForm()
                if (isEditMode) loadReceiptsForEditFilter()
            }
    }

    private fun switchToNewMode() {
        isEditMode = false
        editingReceiptDocId = ""
        oldAllocations.clear()
        binding.layoutEditRecPanel.visibility = View.GONE
        binding.btnModeNewRec.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B5E20"))
        binding.btnModeEditRec.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        binding.tvRecHeader.text = "SALES RECEIPT (BILL-WISE)"
        binding.btnSaveReceipt.text = "SAVE RECEIPT & UPDATE BILLS"
        binding.btnSaveReceipt.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B5E20"))
        resetForm()
    }

    private fun switchToEditMode() {
        isEditMode = true
        editingReceiptDocId = ""
        oldAllocations.clear()
        binding.layoutEditRecPanel.visibility = View.VISIBLE
        binding.btnModeEditRec.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
        binding.btnModeNewRec.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        binding.tvRecHeader.text = "EDIT / DELETE SALES RECEIPT"
        binding.btnSaveReceipt.text = "UPDATE RECEIPT"
        binding.btnSaveReceipt.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
        resetForm()
        loadReceiptsForEditFilter()
    }

    private fun loadReceiptsForEditFilter() {
        val selectedFilterCust = binding.spEditRecFilterCustomer.selectedItem?.toString() ?: "All Customers"
        binding.llMatchingRecList.removeAllViews()

        val loadingTv = TextView(this)
        loadingTv.text = "Loading receipts..."
        loadingTv.setTextColor(Color.DKGRAY)
        loadingTv.setPadding(12, 12, 12, 12)
        binding.llMatchingRecList.addView(loadingTv)

        db.collection("receipts")
            .whereEqualTo("date", editFilterDateStr)
            .get()
            .addOnSuccessListener { snap ->
                binding.llMatchingRecList.removeAllViews()
                var matchCount = 0

                for (doc in snap) {
                    val cName = doc.getString("customerName") ?: ""
                    if (selectedFilterCust != "All Customers" && !cName.equals(selectedFilterCust, ignoreCase = true)) {
                        continue
                    }

                    matchCount++
                    val rNo = doc.getString("receiptNo") ?: doc.id
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
                    infoTv.text = "$rNo | $cName\n$pMode | Rs. %.2f".format(amt)
                    infoTv.setTextColor(Color.parseColor("#1B5E20"))
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
                        populateFormFromReceiptDoc(doc.id, doc.data)
                    }

                    val delBtn = TextView(this)
                    delBtn.text = "DEL"
                    delBtn.setTextColor(Color.WHITE)
                    delBtn.setBackgroundColor(Color.parseColor("#C62828"))
                    delBtn.setTypeface(null, Typeface.BOLD)
                    delBtn.textSize = 12f
                    delBtn.setPadding(24, 14, 24, 14)
                    delBtn.setOnClickListener {
                        confirmAndDeleteReceipt(doc.id, rNo, doc.data)
                    }

                    rowCard.addView(infoTv)
                    rowCard.addView(editBtn)
                    rowCard.addView(delBtn)
                    binding.llMatchingRecList.addView(rowCard)
                }

                if (matchCount == 0) {
                    val noTv = TextView(this)
                    noTv.text = "No receipts found for $editFilterDateStr ($selectedFilterCust)."
                    noTv.setTextColor(Color.RED)
                    noTv.setPadding(12, 14, 12, 14)
                    binding.llMatchingRecList.addView(noTv)
                }
            }
            .addOnFailureListener {
                binding.llMatchingRecList.removeAllViews()
                Toast.makeText(this, "Error loading receipts", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmAndDeleteReceipt(docId: String, rNo: String, data: Map<String, Any>) {
        AlertDialog.Builder(this)
            .setTitle("Delete Receipt")
            .setMessage("Are you sure you want to delete $rNo? Bill balances and Customer outstanding will be restored.")
            .setPositiveButton("DELETE") { _, _ ->
                deleteReceiptAndRestore(docId, data)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteReceiptAndRestore(docId: String, data: Map<String, Any>) {
        val custId = data["customerId"]?.toString() ?: ""
        val amt = (data["amount"] as? Number)?.toDouble() ?: 0.0
        val rawAllocs = data["billAllocations"] as? List<*>

        db.collection("receipts").document(docId).delete()
            .addOnSuccessListener {
                // Restore each bill's paidAmount & balanceAmount
                if (rawAllocs != null) {
                    for (entry in rawAllocs) {
                        val map = entry as? Map<*, *> ?: continue
                        val bDocId = map["billDocId"]?.toString() ?: ""
                        val allocAmt = (map["allocatedAmount"] as? Number)?.toDouble() ?: 0.0
                        if (bDocId.isNotEmpty() && allocAmt > 0.0) {
                            val saleRef = db.collection("sales").document(bDocId)
                            db.runTransaction { tr ->
                                val snap = tr.get(saleRef)
                                if (snap.exists()) {
                                    val total = snap.getDouble("grandTotal") ?: 0.0
                                    val currPaid = snap.getDouble("paidAmount") ?: 0.0
                                    val restoredPaid = (currPaid - allocAmt).coerceAtLeast(0.0)
                                    val restoredBal = (total - restoredPaid).coerceAtLeast(0.0)
                                    val status = if (restoredPaid <= 0.01) "Unpaid" else "Partial"
                                    tr.update(saleRef, mapOf(
                                        "paidAmount" to restoredPaid,
                                        "balanceAmount" to restoredBal,
                                        "paymentStatus" to status
                                    ))
                                }
                            }
                        }
                    }
                }

                // Restore customer currentBalance
                if (custId.isNotEmpty()) {
                    val custRef = db.collection("customers").document(custId)
                    db.runTransaction { tr ->
                        val snap = tr.get(custRef)
                        if (snap.exists()) {
                            val curr = snap.getDouble("currentBalance") ?: 0.0
                            tr.update(custRef, "currentBalance", curr + amt)
                        }
                    }
                }

                if (editingReceiptDocId == docId) {
                    editingReceiptDocId = ""
                    resetForm()
                }
                Toast.makeText(this, "Receipt Deleted & Balances Restored!", Toast.LENGTH_SHORT).show()
                loadReceiptsForEditFilter()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete receipt", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateFormFromReceiptDoc(docId: String, data: Map<String, Any>) {
        editingReceiptDocId = docId
        oldReceiptCustomerId = data["customerId"]?.toString() ?: ""
        oldReceiptAmount = (data["amount"] as? Number)?.toDouble() ?: 0.0

        val rNo = data["receiptNo"]?.toString() ?: ""
        val cName = data["customerName"]?.toString() ?: ""
        val rDate = data["date"]?.toString() ?: editFilterDateStr
        val pMode = data["payMode"]?.toString() ?: "Cash"
        val remarks = data["remarks"]?.toString() ?: ""

        selectedDateStr = rDate
        selectedCustomerId = oldReceiptCustomerId
        selectedCustomerName = cName
        targetSpecificBillDocId = ""

        val foundCust = customerList.find { it.id == oldReceiptCustomerId }
        selectedCustomerOpBal = foundCust?.opBal ?: 0.0

        binding.etRecDate.setText(rDate)
        binding.etRecNo.setText(rNo)
        binding.etSearchCustomer.setText(cName)
        binding.etRecRemarks.setText(remarks)

        val modeIdx = modes.indexOfFirst { it.equals(pMode, ignoreCase = true) }
        if (modeIdx >= 0) binding.spRecMode.setSelection(modeIdx)

        // Store old allocations so we reverse them in memory while editing
        oldAllocations.clear()
        val rawAllocs = data["billAllocations"] as? List<*>
        if (rawAllocs != null) {
            for (entry in rawAllocs) {
                val map = entry as? Map<*, *> ?: continue
                val cleanMap = HashMap<String, Any>()
                cleanMap["billDocId"] = map["billDocId"]?.toString() ?: ""
                cleanMap["allocatedAmount"] = (map["allocatedAmount"] as? Number)?.toDouble() ?: 0.0
                oldAllocations.add(cleanMap)
            }
        }

        // First reverse old allocations on sales docs so user edits from clean state
        for (alloc in oldAllocations) {
            val bDocId = alloc["billDocId"]?.toString() ?: ""
            val prevAlloc = (alloc["allocatedAmount"] as? Number)?.toDouble() ?: 0.0
            if (bDocId.isNotEmpty() && prevAlloc > 0.0) {
                val saleRef = db.collection("sales").document(bDocId)
                db.runTransaction { tr ->
                    val snap = tr.get(saleRef)
                    if (snap.exists()) {
                        val total = snap.getDouble("grandTotal") ?: 0.0
                        val currPaid = snap.getDouble("paidAmount") ?: 0.0
                        val revPaid = (currPaid - prevAlloc).coerceAtLeast(0.0)
                        val revBal = (total - revPaid).coerceAtLeast(0.0)
                        tr.update(saleRef, mapOf("paidAmount" to revPaid, "balanceAmount" to revBal))
                    }
                }
            }
        }

        binding.etRecAmount.setText("%.2f".format(oldReceiptAmount))
        loadPendingBillsForCustomer(selectedCustomerId, selectedCustomerName, selectedCustomerOpBal)
        Toast.makeText(this, "Loaded $rNo for editing", Toast.LENGTH_SHORT).show()
    }

    private fun showCustomerSearchDialog() {
        if (customerList.isEmpty()) {
            Toast.makeText(this, "Loading customers, please wait...", Toast.LENGTH_SHORT).show()
            loadCustomers()
            return
        }

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(32, 24, 32, 16)

        val searchInput = EditText(this)
        searchInput.hint = "Type customer name to filter..."
        searchInput.setSingleLine(true)
        container.addView(searchInput)

        val listView = ListView(this)
        val filtered = ArrayList<CustomerItem>(customerList)
        val displayNames = ArrayList<String>()
        for (c in filtered) {
            displayNames.add(c.name)
        }

        val listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayNames)
        listView.adapter = listAdapter
        container.addView(listView)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Customer")
            .setView(container)
            .setNegativeButton("Close", null)
            .create()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: ""
                filtered.clear()
                displayNames.clear()
                for (item in customerList) {
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
            selectedCustomerId = chosen.id
            selectedCustomerName = chosen.name
            selectedCustomerOpBal = chosen.opBal
            targetSpecificBillDocId = ""
            binding.etSearchCustomer.setText(chosen.name)
            binding.tvCustOutstanding.text = "Calculating Outstanding..."
            loadPendingBillsForCustomer(chosen.id, chosen.name, chosen.opBal)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadPendingBillsForCustomer(custId: String, custName: String, opBal: Double) {
        pendingBills.clear()
        refreshBillWiseGrid()

        db.collection("sales")
            .get()
            .addOnSuccessListener { snap ->
                pendingBills.clear()
                var totalBillsPending = 0.0

                for (doc in snap) {
                    val docCustId = doc.getString("customerId") ?: ""
                    val docCustName = doc.getString("customerName") ?: ""
                    if (docCustId != custId && !docCustName.equals(custName, ignoreCase = true)) continue

                    val pMode = doc.getString("payMode") ?: "Credit"
                    if (!pMode.equals("Credit", ignoreCase = true)) continue

                    val total = doc.getDouble("grandTotal") ?: 0.0
                    val paid = doc.getDouble("paidAmount") ?: 0.0
                    val bal = doc.getDouble("balanceAmount") ?: (total - paid)

                    if (bal > 0.01) {
                        totalBillsPending += bal
                        pendingBills.add(
                            PendingBill(
                                docId = doc.id,
                                billNo = doc.getString("billNo") ?: doc.id,
                                date = doc.getString("date") ?: "-",
                                totalAmount = total,
                                alreadyPaid = paid,
                                pendingAmount = bal,
                                timestamp = doc.getLong("timestamp") ?: 0L
                            )
                        )
                    }
                }

                calculatedTotalOutstanding = totalBillsPending + opBal
                binding.tvCustOutstanding.text = "Total Outstanding Balance: Rs. %.2f".format(calculatedTotalOutstanding)

                db.collection("customers").document(custId)
                    .set(mapOf("currentBalance" to calculatedTotalOutstanding), SetOptions.merge())

                pendingBills.sortBy { it.timestamp }
                allocateAmountToBills()
                refreshBillWiseGrid()
            }
    }

    private fun allocateAmountToBills() {
        var remaining = binding.etRecAmount.text.toString().toDoubleOrNull() ?: 0.0
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
        val childCount = binding.tlReceiptBills.childCount
        if (childCount > 1) {
            binding.tlReceiptBills.removeViews(1, childCount - 1)
        }

        if (pendingBills.isEmpty()) {
            val emptyRow = TableRow(this)
            emptyRow.setBackgroundColor(Color.WHITE)
            val tv = TextView(this)
            tv.text = if (selectedCustomerId.isEmpty()) "Select a customer to view bill-wise pending details." else "No pending credit bills found."
            tv.setTextColor(Color.GRAY)
            tv.setPadding(18, 18, 18, 18)
            val lp = TableRow.LayoutParams()
            lp.span = 6
            tv.layoutParams = lp
            emptyRow.addView(tv)
            binding.tlReceiptBills.addView(emptyRow)
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
                    else -> Color.parseColor("#F1F8E9")
                }
            )

            tr.addView(makeCell(b.billNo, bold = true))
            tr.addView(makeCell(b.date))
            tr.addView(makeCell("%.2f".format(b.totalAmount), alignRight = true))
            tr.addView(makeCell("%.2f".format(b.pendingAmount), alignRight = true, bold = true, colorHex = "#D84315"))
            tr.addView(makeCell("%.2f".format(b.allocatedNow), alignRight = true, bold = true, colorHex = "#1B5E20"))
            tr.addView(makeCell("%.2f".format(newBal), alignRight = true, bold = true, colorHex = "#1565C0"))

            tr.setOnClickListener {
                targetSpecificBillDocId = b.docId
                binding.etRecAmount.setText("%.2f".format(b.pendingAmount))
                Toast.makeText(this, "Selected ${b.billNo} (Rs. %.2f)".format(b.pendingAmount), Toast.LENGTH_SHORT).show()
            }

            binding.tlReceiptBills.addView(tr)
        }
    }

    private fun fetchNextReceiptNo() {
        db.collection("receipts").get().addOnSuccessListener { snap ->
            binding.etRecNo.setText(String.format("REC-%03d", snap.size() + 1))
        }
    }

    private fun loadCustomers() {
        db.collection("customers").get().addOnSuccessListener { snap ->
            customerList.clear()
            editFilterCustomerList.clear()
            editFilterCustomerList.add("All Customers")

            for (doc in snap) {
                val name = doc.getString("name") ?: continue
                val bal = doc.getDouble("currentBalance") ?: 0.0
                val opBal = doc.getDouble("opbal") ?: doc.getDouble("openingBalance") ?: 0.0
                customerList.add(CustomerItem(doc.id, name, bal, opBal))
                editFilterCustomerList.add(name)
            }
            customerList.sortBy { it.name.lowercase(Locale.getDefault()) }

            val filterAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, editFilterCustomerList)
            filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spEditRecFilterCustomer.adapter = filterAdapter
        }
    }

    private fun resetForm() {
        selectedCustomerId = ""
        selectedCustomerName = ""
        selectedCustomerOpBal = 0.0
        calculatedTotalOutstanding = 0.0
        targetSpecificBillDocId = ""
        pendingBills.clear()
        refreshBillWiseGrid()

        binding.etSearchCustomer.setText("")
        binding.tvCustOutstanding.text = "Total Outstanding Balance: Rs. 0.00"
        binding.etRecAmount.setText("")
        binding.etRecRemarks.setText("")
        binding.spRecMode.setSelection(0)
        binding.btnSaveReceipt.isEnabled = true
        if (!isEditMode) fetchNextReceiptNo() else binding.etRecNo.setText("")
        loadCustomers()
    }
}
