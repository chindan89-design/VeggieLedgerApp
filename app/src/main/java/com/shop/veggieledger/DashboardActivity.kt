package com.shop.veggieledger

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
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

    private var selectedDateStr: String = ""

    companion object {
        private const val GRP_MASTERS = 100
        private const val GRP_ENTRIES = 200
        private const val GRP_PUR_REP = 300
        private const val GRP_SAL_REP = 400
        private const val GRP_OTH_REP = 500

        private const val ID_SUPPLIER = 101
        private const val ID_CUSTOMER = 102
        private const val ID_ITEMS = 103

        private const val ID_PURCHASE = 201
        private const val ID_SALES = 202
        private const val ID_PAYMENT = 203
        private const val ID_RECEIPT = 204
        private const val ID_INCOME_EXPENSE = 205

        private const val ID_PUR_DATEWISE = 301
        private const val ID_PUR_STATEMENT = 302
        private const val ID_PUR_OUTSTANDING = 303

        private const val ID_SAL_DAYSALES = 401
        private const val ID_SAL_SUPPLIER_WISE = 402
        private const val ID_SAL_STATEMENT = 403
        private const val ID_SAL_OUTSTANDING = 404

        private const val ID_OTH_COCONUT_DAYBOOK = 501
        private const val ID_OTH_PROFIT_LOSS = 502
        private const val ID_OTH_DAYBOOK = 503
    }

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        populateFullMenu(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return handleMenuAction(item.itemId) || super.onOptionsItemSelected(item)
    }

    private fun populateFullMenu(menu: Menu) {
        menu.clear()

        val mastersSub: SubMenu = menu.addSubMenu(0, 1, 1, "MASTERS")
        mastersSub.add(GRP_MASTERS, ID_SUPPLIER, 1, "SUPPLIER")
        mastersSub.add(GRP_MASTERS, ID_CUSTOMER, 2, "CUSTOMER")
        mastersSub.add(GRP_MASTERS, ID_ITEMS, 3, "ITEMS")

        val entriesSub: SubMenu = menu.addSubMenu(0, 2, 2, "ENTRIES")
        entriesSub.add(GRP_ENTRIES, ID_PURCHASE, 1, "PURCHASE")
        entriesSub.add(GRP_ENTRIES, ID_SALES, 2, "SALES")
        entriesSub.add(GRP_ENTRIES, ID_PAYMENT, 3, "PAYMENT")
        entriesSub.add(GRP_ENTRIES, ID_RECEIPT, 4, "RECEIPT")
        entriesSub.add(GRP_ENTRIES, ID_INCOME_EXPENSE, 5, "INCOME AND EXPENSES")

        val reportSub: SubMenu = menu.addSubMenu(0, 3, 3, "REPORT")

        val purReportSub: SubMenu = reportSub.addSubMenu(0, 31, 1, "PURCHASE REPORT")
        purReportSub.add(GRP_PUR_REP, ID_PUR_DATEWISE, 1, "DATEWISE REPORT")
        purReportSub.add(GRP_PUR_REP, ID_PUR_STATEMENT, 2, "SUPPLIER STATEMENT")
        purReportSub.add(GRP_PUR_REP, ID_PUR_OUTSTANDING, 3, "SUPPLIER OUTSTANDING")

        val salReportSub: SubMenu = reportSub.addSubMenu(0, 32, 2, "SALES REPORT")
        salReportSub.add(GRP_SAL_REP, ID_SAL_DAYSALES, 1, "DAYSALES REPORT")
        salReportSub.add(GRP_SAL_REP, ID_SAL_SUPPLIER_WISE, 2, "SUPPLIER WISE SALES REPORT")
        salReportSub.add(GRP_SAL_REP, ID_SAL_STATEMENT, 3, "CUSTOMER STATEMENT")
        salReportSub.add(GRP_SAL_REP, ID_SAL_OUTSTANDING, 4, "CUSTOMER OUTSTANDING")

        val othReportSub: SubMenu = reportSub.addSubMenu(0, 33, 3, "OTHER REPORT")
        othReportSub.add(GRP_OTH_REP, ID_OTH_COCONUT_DAYBOOK, 1, "COCONUT DAY BOOK")
        othReportSub.add(GRP_OTH_REP, ID_OTH_PROFIT_LOSS, 2, "PROFIT AND LOSS")
        othReportSub.add(GRP_OTH_REP, ID_OTH_DAYBOOK, 3, "DAYBOOK REPORTS")
    }

    private fun showMenuPopup(anchor: View) {
        val popup = PopupMenu(this, anchor)
        populateFullMenu(popup.menu)
        popup.setOnMenuItemClickListener { item ->
            handleMenuAction(item.itemId)
        }
        popup.show()
    }

    private fun handleMenuAction(itemId: Int): Boolean {
        when (itemId) {
            // MASTERS
            ID_SUPPLIER -> openScreenSmart(
                listOf("com.shop.veggieledger.SupplierMasterActivity"),
                "SUPPLIER"
            )
            ID_CUSTOMER -> openScreenSmart(
                listOf("com.shop.veggieledger.CustomerMasterActivity"),
                "CUSTOMER"
            )
            ID_ITEMS -> openScreenSmart(
                listOf("com.shop.veggieledger.ItemMasterActivity"),
                "ITEMS"
            )

            // ENTRIES
            ID_PURCHASE -> openScreenSmart(
                listOf("com.shop.veggieledger.PurchaseActivity"),
                "PURCHASE"
            )
            ID_SALES -> openScreenSmart(
                listOf("com.shop.veggieledger.SalesActivity"),
                "SALES"
            )
            ID_PAYMENT -> openScreenSmart(
                listOf("com.shop.veggieledger.PurchasePaymentActivity"),
                "PAYMENT"
            )
            ID_RECEIPT -> openScreenSmart(
                listOf("com.shop.veggieledger.SalesReceiptActivity"),
                "RECEIPT"
            )
            ID_INCOME_EXPENSE -> openScreenSmart(
                listOf("com.shop.veggieledger.ExpenseIncomeActivity"),
                "INCOME AND EXPENSES"
            )

            // PURCHASE REPORT
            ID_PUR_DATEWISE -> openReport("PUR_DETAILS")
            ID_PUR_STATEMENT -> openReport("PUR_STATEMENT")
            ID_PUR_OUTSTANDING -> openScreenSmart(
                listOf("com.shop.veggieledger.SupplierOutstandingActivity", "com.shop.veggieledger.ReportsHubActivity"),
                "SUPPLIER OUTSTANDING",
                "PUR_OUTSTANDING"
            )

            // SALES REPORT
            ID_SAL_DAYSALES -> openScreenSmart(
                listOf("com.shop.veggieledger.DailySalesEditActivity", "com.shop.veggieledger.ReportsHubActivity"),
                "DAYSALES REPORT",
                "DAY_SALES_REPORT"
            )
            ID_SAL_SUPPLIER_WISE -> openReport("PUR_WISE_SALES")
            ID_SAL_STATEMENT -> openReport("SAL_STATEMENT")
            ID_SAL_OUTSTANDING -> openScreenSmart(
                listOf("com.shop.veggieledger.CustomerOutstandingActivity", "com.shop.veggieledger.ReportsHubActivity"),
                "CUSTOMER OUTSTANDING",
                "SAL_OUTSTANDING"
            )

            // OTHER REPORT
            ID_OTH_COCONUT_DAYBOOK -> openScreenSmart(
                listOf("com.shop.veggieledger.CoconutDayBookActivity", "com.shop.veggieledger.ReportsHubActivity"),
                "COCONUT DAY BOOK",
                "COCONUT_DAYBOOK"
            )
            ID_OTH_PROFIT_LOSS -> openScreenSmart(
                listOf("com.shop.veggieledger.ProfitLossActivity", "com.shop.veggieledger.ReportsHubActivity"),
                "PROFIT AND LOSS",
                "PROFIT_LOSS"
            )
            ID_OTH_DAYBOOK -> openScreenSmart(
                listOf("com.shop.veggieledger.DailyClosingActivity", "com.shop.veggieledger.ReportsHubActivity"),
                "DAYBOOK REPORTS",
                "DAILY_CLOSING"
            )

            else -> return false
        }
        return true
    }

    private fun openReport(reportType: String) {
        startActivity(
            Intent(this, ReportsHubActivity::class.java).apply {
                putExtra("REPORT_TYPE", reportType)
            }
        )
    }

    private fun openScreenSmart(
        candidates: List<String>,
        fallbackLabel: String,
        reportTypeIfMissing: String? = null
    ) {
        for (clsName in candidates) {
            try {
                val clazz = Class.forName(clsName)
                startActivity(
                    Intent(this, clazz).apply {
                        if (reportTypeIfMissing != null) {
                            putExtra("REPORT_TYPE", reportTypeIfMissing)
                        }
                    }
                )
                return
            } catch (_: ClassNotFoundException) {
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening $fallbackLabel: ${e.message}", Toast.LENGTH_SHORT).show()
                return
            }
        }
        if (reportTypeIfMissing != null) {
            openReport(reportTypeIfMissing)
        } else {
            Toast.makeText(this, "$fallbackLabel screen not found", Toast.LENGTH_LONG).show()
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

        val btnMenu = Button(this).apply {
            text = "MENU ▾"
            setBackgroundColor(Color.parseColor("#1B5E20"))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            textSize = 12f
            setOnClickListener { showMenuPopup(it) }
        }

        topHeader.addView(tvBrand)
        topHeader.addView(btnMenu)
        root.addView(topHeader)

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
                openScreenSmart(listOf("com.shop.veggieledger.SalesActivity"), "SALES")
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
                openScreenSmart(listOf("com.shop.veggieledger.PurchaseActivity"), "PURCHASE")
            }
        }

        quickRow.addView(btnAddSales)
        quickRow.addView(btnAddPurchase)
        root.addView(quickRow)

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
