package com.shop.veggieledger

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityPurchasePaymentBinding
import java.text.SimpleDateFormat
import java.util.*

class PurchasePaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPurchasePaymentBinding
    private val db = FirebaseFirestore.getInstance()

    private val supplierList = ArrayList<SupplierItem>()
    private val pendingBills = ArrayList<PendingPurBill>()

    private var selectedSupplierId: String = ""
    private var selectedSupplierName: String = ""
    private var selectedDateStr: String = ""
    private var targetSpecificBillDocId: String = ""

    data class SupplierItem(val id: String, val name: String, val balance: Double)

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

        val cal = Calendar.getInstance()
        selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        binding.etPayDate.setText(selectedDateStr)
        binding.etPayDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                binding.etPayDate.setText(selectedDateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val modes = arrayListOf("Cash", "GPay / UPI", "Bank Transfer", "Cheque")
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

                    val supRef = db.collection("suppliers").document(selectedSupplierId)
                    db.runTransaction { tr ->
                        val snap = tr.get(supRef)
                        val curr = snap.getDouble("currentBalance") ?: 0.0
                        tr.update(supRef, "currentBalance", (curr - amount).coerceAtLeast(0.0))
                    }.addOnCompleteListener {
                        Toast.makeText(this, "Payment Saved & Bill-wise Adjusted!", Toast.LENGTH_SHORT).show()
                        resetForm()
                    }
                }
                .addOnFailureListener {
                    binding.btnSavePayment.isEnabled = true
                    Toast.makeText(this, "Failed to save payment", Toast.LENGTH_SHORT).show()
                }
        }
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
            displayNames.add("${s.name}  (Due: Rs. %.2f)".format(s.balance))
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
                        displayNames.add("${item.name}  (Due: Rs. %.2f)".format(item.balance))
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
            targetSpecificBillDocId = ""
            binding.etSearchSupplier.setText(chosen.name)
            binding.tvSupPayable.text = "Total Payable Balance: Rs. %.2f".format(chosen.balance)
            loadPendingBillsForSupplier(chosen.id)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadPendingBillsForSupplier(supId: String) {
        pendingBills.clear()
        refreshBillWiseGrid()

        db.collection("purchases")
            .whereEqualTo("supplierId", supId)
            .get()
            .addOnSuccessListener { snap ->
                pendingBills.clear()
                for (doc in snap) {
                    val pMode = doc.getString("payMode") ?: "Credit"
                    if (!pMode.equals("Credit", ignoreCase = true)) continue

                    val total = doc.getDouble("grandTotal") ?: 0.0
                    val paid = doc.getDouble("paidAmount") ?: 0.0
                    val bal = doc.getDouble("balanceAmount") ?: (total - paid)

                    if (bal > 0.01) {
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
            tv.text = if (selectedSupplierId.isEmpty()) "Select a supplier to view bill-wise pending details." else "No pending credit purchase bills found (Opening Balance only)."
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
            for (doc in snap) {
                val name = doc.getString("name") ?: continue
                val bal = doc.getDouble("currentBalance") ?: 0.0
                supplierList.add(SupplierItem(doc.id, name, bal))
            }
            supplierList.sortBy { it.name.lowercase(Locale.getDefault()) }
        }
    }

    private fun resetForm() {
        selectedSupplierId = ""
        selectedSupplierName = ""
        targetSpecificBillDocId = ""
        pendingBills.clear()
        refreshBillWiseGrid()

        binding.etSearchSupplier.setText("")
        binding.tvSupPayable.text = "Total Payable Balance: Rs. 0.00"
        binding.etPayAmount.setText("")
        binding.etPayRemarks.setText("")
        binding.spPayMode.setSelection(0)
        binding.btnSavePayment.isEnabled = true
        fetchNextPaymentNo()
        loadSuppliers()
    }
}
