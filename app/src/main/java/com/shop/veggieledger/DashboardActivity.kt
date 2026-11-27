package com.shop.veggieledger

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class DashboardActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var etDashDate: EditText
    private lateinit var tvLiveStock: TextView
    private lateinit var tvPurchaseSum: TextView
    private lateinit var tvSalesSum: TextView
    private lateinit var tvReceiptSum: TextView
    private lateinit var tvPaymentSum: TextView
    private lateinit var tvIncomeSum: TextView
    private lateinit var tvExpenseSum: TextView
    private lateinit var tvCollectableSum: TextView
    private lateinit var tvPayableSum: TextView
    private lateinit var btnSalesReportsMenu: Button

    private var selectedDateStr: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Mandi (ADMIN)"

        setContentView(buildDashboardUi())
        setupDatePicker()
        loadDashboardSummary()
    }

    override fun onResume() {
        super.onResume()
        if (selectedDateStr.isNotEmpty()) {
            loadDashboardSummary()
        }
    }

    private fun buildDashboardUi(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F6F9"))
            setPadding(18, 18, 18, 32)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Header + Sales Reports Menu Trigger
        val topHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }

        val tvBrand = TextView(this).apply {
            text = "Veggie Mandi"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B5E20"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnSalesReportsMenu = Button(this).apply {
            text = "SALES REPORTS ▾"
            setBackgroundColor(Color.parseColor("#1B5E20"))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            textSize = 12f
            setOnClickListener { showSalesReportsMenu(it) }
        }

        topHeader.addView(tvBrand)
        topHeader.addView(btnSalesReportsMenu)
        root.addView(topHeader)

        // Quick Action Buttons (+ SALES, + PURCHASE)
        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, 14)
        }

        val btnAddSales = Button(this).apply {
            text = "+ SALES"
            setBackgroundColor(Color.parseColor("#1B5E20"))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            setOnClickListener {
                startActivity(
                    Intent(this@DashboardActivity, ReportsHubActivity::class.java).apply {
                        putExtra("REPORT_TYPE", "DAY_SALES_REPORT")
                    }
                )
            }
        }

        val btnAddPurchase = Button(this).apply {
            text = "+ PURCHASE"
            setBackgroundColor(Color.parseColor("#1565C0"))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
            setOnClickListener {
                startActivity(
                    Intent(this@DashboardActivity, ReportsHubActivity::class.java).apply {
                        putExtra("REPORT_TYPE", "PUR_STATEMENT")
                    }
                )
            }
        }

        quickRow.addView(btnAddSales)
        quickRow.addView(btnAddPurchase)
        root.addView(quickRow)

        // Date Filter Row
        val dateCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 14)
            }
        }

        val tvDateLbl = TextView(this).apply {
            text = "Date:"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B5E20"))
            setPadding(0, 0, 12, 0)
        }

        etDashDate = EditText(this).apply {
            isFocusable = false
            isClickable = true
            setBackgroundResource(android.R.drawable.edit_text)
            setPadding(16, 10, 16, 10)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        dateCard.addView(tvDateLbl)
        dateCard.addView(etDashDate)
        root.addView(dateCard)

        // 1. Live Available Stock Card
        val stockCard = makeSectionCard("1. LIVE AVAILABLE STOCK")
        tvLiveStock = TextView(this).apply {
            text = "Coconut : 0.00 Kgs"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B5E20"))
            setBackgroundColor(Color.parseColor("#E8F5E9"))
            setPadding(16, 12, 16, 12)
        }
        stockCard.addView(tvLiveStock)
        root.addView(stockCard)

        // 2. Summary for Selected Date
        val sumCard = makeSectionCard("2. SUMMARY FOR SELECTED DATE")
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, 8)
        }
        tvPurchaseSum = makeMetricBox("Purchase", "#E3F2FD", "#1565C0", true)
        tvSalesSum = makeMetricBox("Sales", "#E8F5E9", "#1B5E20", false)
        row1.addView(tvPurchaseSum)
        row1.addView(tvSalesSum)
        sumCard.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, 8)
        }
        tvReceiptSum = makeMetricBox("Receipt", "#F1F8E9", "#2E7D32", true)
        tvPaymentSum = makeMetricBox("Payment", "#E0F7FA", "#00695C", false)
        row2.addView(tvReceiptSum)
        row2.addView(tvPaymentSum)
        sumCard.addView(row2)

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        tvIncomeSum = makeMetricBox("Income", "#E8F5E9", "#1B5E20", true)
        tvExpenseSum = makeMetricBox("Expenses", "#FFEBEE", "#C62828", false)
        row3.addView(tvIncomeSum)
        row3.addView(tvExpenseSum)
        sumCard.addView(row3)

        root.addView(sumCard)

        // 3. Overall Balance Summary
        val balCard = makeSectionCard("3. OVERALL BALANCE SUMMARY")
        val balRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        tvCollectableSum = makeMetricBox("Total Collectable", "#FFF3E0", "#E65100", true)
        tvPayableSum = makeMetricBox("Total Payable", "#E3F2FD", "#1565C0", false)
        balRow.addView(tvCollectableSum)
        balRow.addView(tvPayableSum)
        balCard.addView(balRow)
        root.addView(balCard)

        scroll.addView(root)
        return scroll
    }

    private fun makeSectionCard(titleText: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16, 14, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 14)
            }
            addView(
                TextView(this@DashboardActivity).apply {
                    text = titleText
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#1B5E20"))
                    setPadding(0, 0, 0, 10)
                }
            )
        }
    }

    private fun makeMetricBox(label: String, bgHex: String, colorHex: String, leftMargin: Boolean): TextView {
        return TextView(this).apply {
            text = "$label\nRs. 0.00"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(colorHex))
            setBackgroundColor(Color.parseColor(bgHex))
            setPadding(14, 14, 14, 14)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (leftMargin) setMargins(0, 0, 6, 0) else setMargins(6, 0, 0, 0)
            }
        }
    }

    private fun showSalesReportsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Customer Sales Report")
        popup.menu.add(0, 2, 1, "Purchase-wise Sales Report")
        popup.menu.add(0, 3, 2, "Sales Bill Details")
        popup.menu.add(0, 4, 3, "Itemwise Sales")
        popup.menu.add(0, 5, 4, "Customer Statement")
        popup.menu.add(0, 6, 5, "Customer Outstanding")
        popup.menu.add(0, 7, 6, "Day Sales Report")

        popup.setOnMenuItemClickListener { item ->
            val typeCode = when (item.itemId) {
                1 -> "SAL_CUSTOMER"
                2 -> "PUR_WISE_SALES"
                3 -> "SAL_DETAILS"
                4 -> "SAL_ITEMWISE"
                5 -> "SAL_STATEMENT"
                6 -> "SAL_OUTSTANDING"
                7 -> "DAY_SALES_REPORT"
                else -> return@setOnMenuItemClickListener false
            }
            startActivity(
                Intent(this, ReportsHubActivity::class.java).apply {
                    putExtra("REPORT_TYPE", typeCode)
                }
            )
            true
        }
        popup.show()
    }

    private fun setupDatePicker() {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()
        selectedDateStr = sdf.format(cal.time)
        etDashDate.setText(selectedDateStr)

        etDashDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    cal.set(y, m, d)
                    selectedDateStr = sdf.format(cal.time)
                    etDashDate.setText(selectedDateStr)
                    loadDashboardSummary()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun loadDashboardSummary() {
        val targetYmd = dateToYmd(selectedDateStr)

        val stockTask = db.collection("stock_batches").get()
        val purTask = db.collection("purchases").get()
        val salTask = db.collection("sales").get()
        val recTask = db.collection("receipts").get()
        val payTask = db.collection("payments").get()
        val expTask = db.collection("expenses").get()
        val custTask = db.collection("customers").get()
        val supTask = db.collection("suppliers").get()

        Tasks.whenAllComplete(stockTask, purTask, salTask, recTask, payTask, expTask, custTask, supTask)
            .addOnSuccessListener {
                var availableKgs = 0.0
                if (stockTask.isSuccessful) {
                    for (doc in (stockTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                        val rem = safeDouble(doc, "remainingKgs").let {
                            if (it != 0.0) it else safeDouble(doc, "availableKgs")
                        }
                        availableKgs += rem
                    }
                }

                var dayPur = 0.0
                var allPur = 0.0
                if (purTask.isSuccessful) {
                    for (doc in (purTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                        val amt = safeDouble(doc, "grandTotal").let {
                            if (it > 0.0) it else safeDouble(doc, "amount")
                        }
                        allPur += amt
                        val dYmd = dateToYmd((doc.getString("date") ?: "").trim())
                        if (targetYmd != -1 && dYmd == targetYmd) dayPur += amt
                    }
                }

                var daySal = 0.0
                var allSal = 0.0
                if (salTask.isSuccessful) {
                    for (doc in (salTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                        val amt = safeDouble(doc, "grandTotal").let {
                            if (it > 0.0) it else safeDouble(doc, "amount")
                        }
                        allSal += amt
                        val dYmd = dateToYmd((doc.getString("date") ?: "").trim())
                        if (targetYmd != -1 && dYmd == targetYmd) daySal += amt
                    }
                }

                var dayRec = 0.0
                var allRec = 0.0
                if (recTask.isSuccessful) {
                    for (doc in (recTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                        val amt = safeDouble(doc, "amount")
                        allRec += amt
                        val dYmd = dateToYmd((doc.getString("date") ?: "").trim())
                        if (targetYmd != -1 && dYmd == targetYmd) dayRec += amt
                    }
                }

                var dayPay = 0.0
                var allPay = 0.0
                if (payTask.isSuccessful) {
                    for (doc in (payTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                        val amt = safeDouble(doc, "amount")
                        allPay += amt
                        val dYmd = dateToYmd((doc.getString("date") ?: "").trim())
                        if (targetYmd != -1 && dYmd == targetYmd) dayPay += amt
                    }
                }

                var dayInc = 0.0
                var dayExp = 0.0
                if (expTask.isSuccessful) {
                    for (doc in (expTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                        val amt = safeDouble(doc, "amount")
                        val type = (doc.getString("type") ?: "Expense").trim()
                        val dYmd = dateToYmd((doc.getString("date") ?: "").trim())
                        if (targetYmd != -1 && dYmd == targetYmd) {
                            if (type.equals("Income", ignoreCase = true)) dayInc += amt else dayExp += amt
                        }
                    }
                }

                var custOp = 0.0
                if (custTask.isSuccessful) {
                    for (doc in (custTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                        custOp += safeDouble(doc, "openingBalance")
                    }
                }

                var supOp = 0.0
                if (supTask.isSuccessful) {
                    for (doc in (supTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                        supOp += safeDouble(doc, "openingBalance").let {
                            if (it != 0.0) it else safeDouble(doc, "opBal")
                        }
                    }
                }

                val collectable = (custOp + allSal - allRec).coerceAtLeast(0.0)
                val payable = (supOp + allPur - allPay).coerceAtLeast(0.0)

                tvLiveStock.text = "Coconut : %.2f Kgs".format(Locale.getDefault(), availableKgs)
                tvPurchaseSum.text = "Purchase\nRs. %.2f".format(Locale.getDefault(), dayPur)
                tvSalesSum.text = "Sales\nRs. %.2f".format(Locale.getDefault(), daySal)
                tvReceiptSum.text = "Receipt\nRs. %.2f".format(Locale.getDefault(), dayRec)
                tvPaymentSum.text = "Payment\nRs. %.2f".format(Locale.getDefault(), dayPay)
                tvIncomeSum.text = "Income\nRs. %.2f".format(Locale.getDefault(), dayInc)
                tvExpenseSum.text = "Expenses\nRs. %.2f".format(Locale.getDefault(), dayExp)
                tvCollectableSum.text = "Total Collectable\nRs. %.2f".format(Locale.getDefault(), collectable)
                tvPayableSum.text = "Total Payable\nRs. %.2f".format(Locale.getDefault(), payable)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load dashboard", Toast.LENGTH_SHORT).show()
            }
    }

    private fun safeDouble(doc: DocumentSnapshot, field: String): Double {
        val raw = doc.get(field) ?: return 0.0
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.trim().toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun dateToYmd(dateStr: String): Int {
        val clean = dateStr.trim().substringBefore(" ")
        val parts = clean.split("-", "/", ".")
        if (parts.size != 3) return -1
        val p0 = parts[0].trim().toIntOrNull() ?: return -1
        val p1 = parts[1].trim().toIntOrNull() ?: return -1
        val p2 = parts[2].trim().toIntOrNull() ?: return -1
        return if (p0 > 1000) {
            (p0 * 10000) + (p1 * 100) + p2
        } else {
            val yearVal = if (p2 < 100) 2000 + p2 else p2
            (yearVal * 10000) + (p1 * 100) + p0
        }
    }
}
