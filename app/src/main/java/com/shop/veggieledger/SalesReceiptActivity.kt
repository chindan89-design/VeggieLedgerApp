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
import com.shop.veggieledger.databinding.ActivitySalesReceiptBinding
import java.text.SimpleDateFormat
import java.util.*

class SalesReceiptActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalesReceiptBinding
    private val db = FirebaseFirestore.getInstance()

    private val customerList = ArrayList<CustomerItem>()
    private val pendingBills = ArrayList<PendingBill>()

    private var selectedCustomerId: String = ""
    private var selectedCustomerName: String = ""
    private var currentCustomerBalance: Double = 0.0
    private var selectedDateStr: String = ""
    private var targetSpecificBillDocId: String = ""

    data class CustomerItem(val id: String, val name: String, val balance: Double)

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

        val cal = Calendar.getInstance()
        selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        binding.etRecDate.setText(selectedDateStr)
        binding.etRecDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                binding.etRecDate.setText(selectedDateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val modes = arrayListOf("Cash", "GPay / UPI", "Bank Transfer", "Cheque")
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

            binding.btnSaveReceipt.isEnabled = false
            allocateAmountToBills()

            val allocationsForDb = ArrayList>()
            for (b in pendingBills) {
                if (b.allocatedNow > 0.0) {
                    allocationsForDb.add(
                        hashMapOf(
                            "billDocId" to b.docId,
                            "billNo" to b.billNo,
                            "billDate" to b.date,
                            "allocatedAmount" to b.allocatedNow,
                            "remainingBalance" to (b.pendingAmount - b.allocatedNow).coerceAtLeast(0.0)
                        )
                    )
                }
            }

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

                    val custRef = db.collection("customers").document(selectedCustomerId)
                    db.runTransaction { tr ->
                        val snap = tr.get(custRef)
                        val curr = snap.getDouble("currentBalance") ?: 0.0
                        tr.update(custRef, "currentBalance", (curr - amount).coerceAtLeast(0.0))
                    }.addOnCompleteListener {
                        Toast.makeText(this, "Receipt Saved & Bill-wise Adjusted!", Toast.LENGTH_SHORT).show()
                        resetForm()
                    }
                }
                .addOnFailureListener {
                    binding.btnSaveReceipt.isEnabled = true
                    Toast.makeText(this, "Failed to save receipt", Toast.LENGTH_SHORT).show()
                }
        }
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
        val displayNames = ArrayList<String>(filtered.map { "${it.name}  (Bal: Rs. %.2f)".format(it.balance) })

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
                        displayNames.add("${item.name}  (Bal: Rs. %.2f)".format(item.balance))
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
            currentCustomerBalance = chosen.balance
            targetSpecificBillDocId = ""
            binding.etSearchCustomer.setText(chosen.name)
            binding.tvCustOutstanding.text = "Total Outstanding Balance: Rs. %.2f".format(chosen.balance)
            loadPendingBillsForCustomer(chosen.id)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadPendingBillsForCustomer(custId: String) {
        pendingBills.clear()
        refreshBillWiseGrid()

        db.collection("sales")
            .whereEqualTo("customerId", custId)
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
            tv.text = if (selectedCustomerId.isEmpty()) "Select a customer to view bill-wise pending details." else "No pending credit bills found (Opening Balance only)."
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
            for (doc in snap) {
                val name = doc.getString("name") ?: continue
                val bal = doc.getDouble("currentBalance") ?: 0.0
                customerList.add(CustomerItem(doc.id, name, bal))
            }
            customerList.sortBy { it.name.lowercase(Locale.getDefault()) }
        }
    }

    private fun resetForm() {
        selectedCustomerId = ""
        selectedCustomerName = ""
        currentCustomerBalance = 0.0
        targetSpecificBillDocId = ""
        pendingBills.clear()
        refreshBillWiseGrid()

        binding.etSearchCustomer.setText("")
        binding.tvCustOutstanding.text = "Total Outstanding Balance: Rs. 0.00"
        binding.etRecAmount.setText("")
        binding.etRecRemarks.setText("")
        binding.spRecMode.setSelection(0)
        binding.btnSaveReceipt.isEnabled = true
        fetchNextReceiptNo()
        loadCustomers()
    }
}
