package com.shop.veggieledger

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ReceiptEntryActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var etDate: EditText
    private lateinit var etReceiptNo: EditText
    private lateinit var spCustomer: Spinner
    private lateinit var spPayMode: Spinner
    private lateinit var etAmount: EditText
    private lateinit var etRemarks: EditText
    private lateinit var btnSave: Button

    private val customerList = arrayListOf("CASH")
    private var selectedDateStr: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Receipt Entry"
        setContentView(buildUi())
        setupDateAndDefaults()
        loadCustomers()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F6F9"))
            setPadding(20, 20, 20, 32)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(20, 20, 20, 20)
        }

        col.addView(TextView(this).apply {
            text = "RECEIPT ENTRY"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B5E20"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 14)
        })

        col.addView(label("Date"))
        etDate = EditText(this).apply {
            isFocusable = false
            isClickable = true
            setBackgroundResource(android.R.drawable.edit_text)
        }
        col.addView(etDate)

        col.addView(label("Receipt No"))
        etReceiptNo = EditText(this).apply {
            hint = "REC-001"
            setBackgroundResource(android.R.drawable.edit_text)
        }
        col.addView(etReceiptNo)

        col.addView(label("Customer Name"))
        spCustomer = Spinner(this).apply { setBackgroundResource(android.R.drawable.btn_dropdown) }
        col.addView(spCustomer)

        col.addView(label("Payment Mode"))
        spPayMode = Spinner(this).apply {
            setBackgroundResource(android.R.drawable.btn_dropdown)
            adapter = ArrayAdapter(
                this@ReceiptEntryActivity,
                android.R.layout.simple_spinner_item,
                listOf("Cash", "GPay / UPI", "Credit")
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        col.addView(spPayMode)

        col.addView(label("Amount"))
        etAmount = EditText(this).apply {
            hint = "0.00"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setBackgroundResource(android.R.drawable.edit_text)
        }
        col.addView(etAmount)

        col.addView(label("Remarks / Description"))
        etRemarks = EditText(this).apply {
            hint = "Optional"
            setBackgroundResource(android.R.drawable.edit_text)
        }
        col.addView(etRemarks)

        btnSave = Button(this).apply {
            text = "SAVE RECEIPT"
            setBackgroundColor(Color.parseColor("#1B5E20"))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 18, 0, 0) }
            setOnClickListener { saveReceipt() }
        }
        col.addView(btnSave)

        scroll.addView(col)
        return scroll
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12.5f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.parseColor("#1B5E20"))
        setPadding(0, 10, 0, 4)
    }

    private fun setupDateAndDefaults() {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()
        selectedDateStr = sdf.format(cal.time)
        etDate.setText(selectedDateStr)
        etDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = sdf.format(cal.time)
                etDate.setText(selectedDateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        etReceiptNo.setText("REC-${System.currentTimeMillis().toString().takeLast(4)}")
    }

    private fun loadCustomers() {
        db.collection("customers").get().addOnSuccessListener { snap ->
            customerList.clear()
            customerList.add("CASH")
            for (doc in snap.documents) {
                val n = (doc.getString("name") ?: "").trim()
                if (n.isNotEmpty() && customerList.none { it.equals(n, ignoreCase = true) }) {
                    customerList.add(n)
                }
            }
            spCustomer.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                customerList
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
    }

    private fun saveReceipt() {
        val amt = etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
        if (amt <= 0.0) {
            Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show()
            return
        }
        btnSave.isEnabled = false
        val recNo = etReceiptNo.text.toString().trim().ifEmpty { "REC-${System.currentTimeMillis().toString().takeLast(4)}" }
        val cust = spCustomer.selectedItem?.toString() ?: "CASH"
        val mode = spPayMode.selectedItem?.toString() ?: "Cash"
        val remarks = etRemarks.text.toString().trim()

        db.collection("receipts").get().addOnSuccessListener { snap ->
            val data = hashMapOf<String, Any>(
                "date" to selectedDateStr,
                "receiptNo" to recNo,
                "customerName" to cust,
                "payMode" to mode,
                "amount" to amt,
                "remarks" to remarks,
                "entryIndex" to snap.size().toLong(),
                "timestamp" to System.currentTimeMillis()
            )
            db.collection("receipts").add(data).addOnSuccessListener {
                Toast.makeText(this, "Receipt Saved!", Toast.LENGTH_SHORT).show()
                finish()
            }.addOnFailureListener { e ->
                btnSave.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener {
            btnSave.isEnabled = true
        }
    }
}
