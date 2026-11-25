package com.shop.veggieledger

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.Gravity
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class DailyClosingActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var etClosingDate: EditText
    private lateinit var containerLayout: LinearLayout
    private lateinit var printWebView: WebView

    private var selectedDateStr: String = ""
    private var lastA4Html: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Daily Account Closing Report"

        val cal = Calendar.getInstance()
        selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)

        buildBaseUi(cal)
        loadAllSectionsData()
    }

    private fun fmt(v: Double): String {
        return if (abs(v) < 0.005) "-" else String.format(Locale.getDefault(), "%.2f", v)
    }

    private fun dateToYmd(dateStr: String): Int {
        val clean = dateStr.trim().substringBefore(" ")
        val parts = clean.split("-", "/", ".")
        if (parts.size != 3) return -1
        val p0 = parts[0].trim().toIntOrNull() ?: return -1
        val p1 = parts[1].trim().toIntOrNull() ?: return -1
        val p2 = parts[2].trim().toIntOrNull() ?: return -1
        return if (p0 > 1000) (p0 * 10000) + (p1 * 100) + p2
        else ((if (p2 < 100) 2000 + p2 else p2) * 10000) + (p1 * 100) + p0
    }

    private fun buildBaseUi(cal: Calendar) {
        val rootScroll = ScrollView(this)
        rootScroll.setBackgroundColor(Color.parseColor("#ECEFF1"))
        rootScroll.setPadding(14, 14, 14, 14)

        val mainCol = LinearLayout(this)
        mainCol.orientation = LinearLayout.VERTICAL
        rootScroll.addView(mainCol)

        // Top Control Bar
        val topCard = LinearLayout(this)
        topCard.orientation = LinearLayout.VERTICAL
        topCard.setBackgroundColor(Color.WHITE)
        topCard.setPadding(16, 16, 16, 16)
        val topLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        topLp.setMargins(0, 0, 0, 12)
        topCard.layoutParams = topLp

        val tvHeading = TextView(this)
        tvHeading.text = "THIRUVENI COCONUTS - DAILY CLOSING REPORT"
        tvHeading.textSize = 15.5f
        tvHeading.setTypeface(null, Typeface.BOLD)
        tvHeading.setTextColor(Color.parseColor("#1B5E20"))
        tvHeading.gravity = Gravity.CENTER
        tvHeading.setPadding(0, 0, 0, 10)
        topCard.addView(tvHeading)

        val rowCtrl = LinearLayout(this)
        rowCtrl.orientation = LinearLayout.HORIZONTAL
        rowCtrl.weightSum = 2f

        etClosingDate = EditText(this)
        etClosingDate.setText(selectedDateStr)
        etClosingDate.isFocusable = false
        etClosingDate.isClickable = true
        etClosingDate.setTypeface(null, Typeface.BOLD)
        etClosingDate.setBackgroundResource(android.R.drawable.edit_text)
        val lpDate = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lpDate.setMargins(0, 0, 6, 0)
        etClosingDate.layoutParams = lpDate
        etClosingDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                etClosingDate.setText(selectedDateStr)
                loadAllSectionsData()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val btnPrint = Button(this)
        btnPrint.text = "PRINT A4 REPORT"
        btnPrint.setBackgroundColor(Color.parseColor("#E65100"))
        btnPrint.setTextColor(Color.WHITE)
        btnPrint.setTypeface(null, Typeface.BOLD)
        val lpBtn = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lpBtn.setMargins(6, 0, 0, 0)
        btnPrint.layoutParams = lpBtn
        btnPrint.setOnClickListener { printA4ClosingSheet() }

        rowCtrl.addView(etClosingDate)
        rowCtrl.addView(btnPrint)
        topCard.addView(rowCtrl)
        mainCol.addView(topCard)

        containerLayout = LinearLayout(this)
        containerLayout.orientation = LinearLayout.VERTICAL
        mainCol.addView(containerLayout)

        setContentView(rootScroll)
    }

    private fun addTwoColCard(title: String, rows: List<Pair<String, String>>, highlightLastRow: Boolean = false) {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundColor(Color.WHITE)
        card.setPadding(14, 12, 14, 12)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, 0, 10)
        card.layoutParams = lp

        val tvSec = TextView(this)
        tvSec.text = title
        tvSec.textSize = 13.5f
        tvSec.setTypeface(null, Typeface.BOLD)
        tvSec.setTextColor(Color.WHITE)
        tvSec.setBackgroundColor(Color.parseColor("#1B5E20"))
        tvSec.setPadding(12, 9, 12, 9)
        card.addView(tvSec)

        val tbl = TableLayout(this)
        tbl.setBackgroundColor(Color.BLACK)
        tbl.setPadding(1, 1, 1, 1)
        tbl.isStretchAllColumns = true

        for (i in rows.indices) {
            val isLast = (i == rows.size - 1) && highlightLastRow
            val bg = if (isLast) Color.parseColor("#FFF9C4") else if (i % 2 == 0) Color.WHITE else Color.parseColor("#F9FBE7")

            val tr = TableRow(this)
            tr.setBackgroundColor(Color.BLACK)

            val tvL = TextView(this)
            tvL.text = rows[i].first
            tvL.setTextColor(Color.BLACK)
            tvL.setBackgroundColor(bg)
            tvL.textSize = 13.5f
            tvL.setPadding(12, 10, 12, 10)
            if (isLast) tvL.setTypeface(null, Typeface.BOLD)
            val lp1 = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 1.3f)
            lp1.setMargins(1, 1, 1, 1)
            tvL.layoutParams = lp1

            val tvR = TextView(this)
            tvR.text = rows[i].second
            tvR.setTextColor(if (isLast) Color.parseColor("#D84315") else Color.BLACK)
            tvR.setBackgroundColor(bg)
            tvR.textSize = 13.5f
            tvR.gravity = Gravity.END
            tvR.setPadding(12, 10, 12, 10)
            tvR.setTypeface(null, Typeface.BOLD)
            val lp2 = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 0.7f)
            lp2.setMargins(1, 1, 1, 1)
            tvR.layoutParams = lp2

            tr.addView(tvL)
            tr.addView(tvR)
            tbl.addView(tr)
        }

        card.addView(tbl)
        containerLayout.addView(card)
    }

    /**
     * Special 3-Column Card for "3. Credit Bill Received" (Particulars | CASH | GPAY)
     */
    private fun addThreeColCreditRecCard(cashAmt: Double, gpayAmt: Double) {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundColor(Color.WHITE)
        card.setPadding(14, 12, 14, 12)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, 0, 10)
        card.layoutParams = lp

        val tvSec = TextView(this)
        tvSec.text = "3. CREDIT BILL RECEIVED (CASH & GPAY)"
        tvSec.textSize = 13.5f
        tvSec.setTypeface(null, Typeface.BOLD)
        tvSec.setTextColor(Color.WHITE)
        tvSec.setBackgroundColor(Color.parseColor("#1B5E20"))
        tvSec.setPadding(12, 9, 12, 9)
        card.addView(tvSec)

        val tbl = TableLayout(this)
        tbl.setBackgroundColor(Color.BLACK)
        tbl.setPadding(1, 1, 1, 1)
        tbl.isStretchAllColumns = true

        // Header Row
        val hdr = TableRow(this)
        hdr.setBackgroundColor(Color.BLACK)
        val h1 = makeGridCell("PARTICULARS", false, true, Color.parseColor("#E0E0E0"), 1.2f)
        val h2 = makeGridCell("CASH", true, true, Color.parseColor("#E0E0E0"), 0.9f)
        val h3 = makeGridCell("GPAY", true, true, Color.parseColor("#E0E0E0"), 0.9f)
        hdr.addView(h1)
        hdr.addView(h2)
        hdr.addView(h3)
        tbl.addView(hdr)

        // Value Row
        val valRow = TableRow(this)
        valRow.setBackgroundColor(Color.BLACK)
        val v1 = makeGridCell("Credit Bill Received", false, true, Color.parseColor("#FFF9C4"), 1.2f)
        val v2 = makeGridCell(fmt(cashAmt), true, true, Color.parseColor("#FFF9C4"), 0.9f)
        val v3 = makeGridCell(fmt(gpayAmt), true, true, Color.parseColor("#FFF9C4"), 0.9f)
        valRow.addView(v1)
        valRow.addView(v2)
        valRow.addView(v3)
        tbl.addView(valRow)

        card.addView(tbl)
        containerLayout.addView(card)
    }

    private fun makeGridCell(text: String, right: Boolean, bold: Boolean, bg: Int, weight: Float): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.BLACK)
        tv.setBackgroundColor(bg)
        tv.textSize = 13.5f
        tv.setPadding(10, 10, 10, 10)
        tv.gravity = if (right) Gravity.END else Gravity.START
        if (bold) tv.setTypeface(null, Typeface.BOLD)
        val lp = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, weight)
        lp.setMargins(1, 1, 1, 1)
        tv.layoutParams = lp
        return tv
    }

    private fun loadAllSectionsData() {
        containerLayout.removeAllViews()
        val loadingTv = TextView(this)
        loadingTv.text = "Generating Daily Closing Report for $selectedDateStr..."
        loadingTv.setPadding(16, 16, 16, 16)
        containerLayout.addView(loadingTv)

        val targetYmd = dateToYmd(selectedDateStr)

        db.collection("purchases").get().addOnSuccessListener { purSnap ->
            db.collection("suppliers").get().addOnSuccessListener { supSnap ->
                db.collection("payments").get().addOnSuccessListener { paySnap ->
                    db.collection("sales").get().addOnSuccessListener { salSnap ->
                        db.collection("receipts").get().addOnSuccessListener { recSnap ->
                            db.collection("expenses").get().addOnSuccessListener { expSnap ->
                                db.collection("customers").get().addOnSuccessListener { custSnap ->
                                    db.collection("stock_batches").get().addOnSuccessListener { stockSnap ->

                                        // 1. PURCHASE DETAILS (Today)
                                        var purCash = 0.0
                                        var purCredit = 0.0
                                        var totalPurAll = 0.0
                                        for (doc in purSnap) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            val tot = doc.getDouble("grandTotal") ?: 0.0
                                            if (dYmd in 1..targetYmd) totalPurAll += tot
                                            if (dYmd == targetYmd) {
                                                val mode = (doc.getString("payMode") ?: "Credit").trim()
                                                if (mode.equals("Cash", ignoreCase = true)) purCash += tot else purCredit += tot
                                            }
                                        }
                                        val todayPurchaseTot = purCash + purCredit

                                        // 2. SUPPLIER OUTSTANDING
                                        var supMasterOp = 0.0
                                        for (doc in supSnap) {
                                            supMasterOp += (doc.getDouble("opbal") ?: doc.getDouble("openingBalance") ?: 0.0)
                                        }
                                        var totalSupPaid = 0.0
                                        for (doc in paySnap) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            if (dYmd in 1..targetYmd) {
                                                totalSupPaid += (doc.getDouble("amount") ?: 0.0) + (doc.getDouble("discount") ?: 0.0)
                                            }
                                        }
                                        for (doc in purSnap) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            if (dYmd in 1..targetYmd && (doc.getString("payMode") ?: "").equals("Cash", ignoreCase = true)) {
                                                totalSupPaid += (doc.getDouble("grandTotal") ?: 0.0)
                                            }
                                        }
                                        val supplierClosingOut = (supMasterOp + totalPurAll - totalSupPaid).coerceAtLeast(0.0)

                                        // 3. CREDIT BILL RECEIVED (Cash & GPay 2 columns)
                                        var creditRecCash = 0.0
                                        var creditRecGpay = 0.0
                                        var todayCustDiscount = 0.0
                                        val receiptLinkedBills = HashSet<String>()

                                        for (doc in recSnap) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            val linked = (doc.getString("billNo") ?: doc.getString("saleId") ?: "").trim().lowercase(Locale.getDefault())
                                            if (linked.isNotEmpty()) receiptLinkedBills.add(linked)

                                            if (dYmd == targetYmd) {
                                                val mode = (doc.getString("payMode") ?: "Cash").trim()
                                                val amt = doc.getDouble("amount") ?: 0.0
                                                val disc = doc.getDouble("discount") ?: 0.0
                                                todayCustDiscount += disc
                                                if (mode.equals("Cash", ignoreCase = true)) creditRecCash += amt
                                                else creditRecGpay += amt
                                            }
                                        }

                                        // 4. TODAY SALES (Cash Sales, GPay Sales, Credit Sales)
                                        var cashSales = 0.0
                                        var gpaySales = 0.0
                                        var creditSales = 0.0
                                        for (doc in salSnap) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            if (dYmd == targetYmd) {
                                                val mode = (doc.getString("payMode") ?: "Credit").trim()
                                                val tot = doc.getDouble("grandTotal") ?: 0.0
                                                when {
                                                    mode.equals("Cash", ignoreCase = true) -> cashSales += tot
                                                    mode.contains("GPay", ignoreCase = true) || mode.contains("UPI", ignoreCase = true) || mode.contains("Bank", ignoreCase = true) -> gpaySales += tot
                                                    else -> creditSales += tot
                                                }
                                            }
                                        }
                                        val todaySalesTotal = cashSales + gpaySales + creditSales

                                        // 5. CASH RECEIVED (Cash Sales + Credit Bill Received via Cash)
                                        val cashReceivedTotal = cashSales + creditRecCash

                                        // 6. EXPENSES (A. Other, B. Shop, C. Vehicle) & Other Income
                                        var otherExp = 0.0
                                        var shopExp = 0.0
                                        var vehicleExp = 0.0
                                        var otherCashIncome = 0.0
                                        var otherBankIncome = 0.0

                                        for (doc in expSnap) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            if (dYmd != targetYmd) continue
                                            val type = doc.getString("type") ?: "Expense"
                                            val cat = (doc.getString("category") ?: "Other Expenses").trim()
                                            val amt = doc.getDouble("amount") ?: 0.0
                                            val mode = (doc.getString("payMode") ?: "Cash").trim()

                                            if (type.equals("Income", ignoreCase = true)) {
                                                if (mode.equals("Bank", ignoreCase = true)) otherBankIncome += amt
                                                else otherCashIncome += amt
                                            } else {
                                                when {
                                                    cat.equals("Shop Expenses", ignoreCase = true) -> shopExp += amt
                                                    cat.equals("Vehicle Expenses", ignoreCase = true) -> vehicleExp += amt
                                                    else -> otherExp += amt
                                                }
                                            }
                                        }
                                        val totalExpenses = otherExp + shopExp + vehicleExp

                                        // 7. COLLECTION & TODAY CLOSING BALANCE
                                        // Collection = Cash Sales + Credit Bill via Cash + Other Cash Income
                                        val collectionTotal = cashSales + creditRecCash + otherCashIncome
                                        val todayClosingBalance = collectionTotal - totalExpenses

                                        // 8. STOCK INQUIRY
                                        val stockMap = HashMap<String, Pair<Int, Double>>()
                                        for (doc in stockSnap) {
                                            val iName = doc.getString("itemName") ?: continue
                                            val bgs = (doc.getLong("availableBags") ?: 0L).toInt()
                                            val kgs = doc.getDouble("availableKgs") ?: 0.0
                                            val curr = stockMap[iName] ?: Pair(0, 0.0)
                                            stockMap[iName] = Pair(curr.first + bgs, curr.second + kgs)
                                        }

                                        // 9. INCOME DETAILS (Cash Sales, GPay Sales, Credit Bill via Cash, Credit Bill via GPay)
                                        val incomeDetailsTotal = cashSales + gpaySales + creditRecCash + creditRecGpay

                                        // 10. CUSTOMER OUTSTANDING (Vertically: Op, Sales, Income, Discount, Closing)
                                        var custMasterOp = 0.0
                                        for (doc in custSnap) {
                                            custMasterOp += (doc.getDouble("opbal") ?: doc.getDouble("openingBalance") ?: 0.0)
                                        }
                                        var prevSalesAll = 0.0
                                        var prevIncAll = 0.0
                                        var prevDiscAll = 0.0

                                        for (doc in salSnap) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            val tot = doc.getDouble("grandTotal") ?: 0.0
                                            val isCred = (doc.getString("payMode") ?: "Credit").equals("Credit", ignoreCase = true)
                                            val bNo = (doc.getString("billNo") ?: "").trim().lowercase(Locale.getDefault())
                                            val linked = (bNo.isNotEmpty() && receiptLinkedBills.contains(bNo)) || receiptLinkedBills.contains(doc.id.lowercase(Locale.getDefault()))
                                            val unlinkedCash = if (!isCred && !linked) tot else 0.0

                                            if (dYmd in 1 until targetYmd) {
                                                prevSalesAll += tot
                                                prevIncAll += unlinkedCash
                                            }
                                        }
                                        for (doc in recSnap) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            if (dYmd in 1 until targetYmd) {
                                                prevIncAll += (doc.getDouble("amount") ?: 0.0)
                                                prevDiscAll += (doc.getDouble("discount") ?: 0.0)
                                            }
                                        }
                                        val vertOpOut = (custMasterOp + prevSalesAll - prevIncAll - prevDiscAll).coerceAtLeast(0.0)
                                        val vertClosingOut = (vertOpOut + todaySalesTotal - incomeDetailsTotal - todayCustDiscount).coerceAtLeast(0.0)

                                        // 11. PROFIT & LOSS
                                        val netProfitLoss = todaySalesTotal - todayPurchaseTot - totalExpenses - todayCustDiscount

                                        // RENDER ALL 11 SECTIONS
                                        containerLayout.removeAllViews()

                                        addTwoColCard(
                                            "1. PURCHASE DETAILS & 2. SUPPLIER OUTSTANDING",
                                            listOf(
                                                "Cash Purchase" to fmt(purCash),
                                                "Credit Purchase" to fmt(purCredit),
                                                "Today Total Purchase" to fmt(todayPurchaseTot),
                                                "2. Supplier Closing Outstanding" to fmt(supplierClosingOut)
                                            ),
                                            highlightLastRow = true
                                        )

                                        addThreeColCreditRecCard(creditRecCash, creditRecGpay)

                                        addTwoColCard(
                                            "4. TODAY SALES & 5. CASH RECEIVED",
                                            listOf(
                                                "Cash Sales" to fmt(cashSales),
                                                "GPay Sales" to fmt(gpaySales),
                                                "Credit Sales" to fmt(creditSales),
                                                "4. Today Total Sales" to fmt(todaySalesTotal),
                                                "5. Cash Received (Cash Sales + Credit Bill via Cash)" to fmt(cashReceivedTotal)
                                            ),
                                            highlightLastRow = true
                                        )

                                        addTwoColCard(
                                            "6. EXPENSES & 7. COLLECTION CLOSING BALANCE",
                                            listOf(
                                                "6A. Other Expenses" to fmt(otherExp),
                                                "6B. Shop Expenses" to fmt(shopExp),
                                                "6C. Vehicle Expenses" to fmt(vehicleExp),
                                                "Total Expenses" to fmt(totalExpenses),
                                                "7. Collection Total (Cash Sales + Credit Cash + Other Inc)" to fmt(collectionTotal),
                                                "TODAY CLOSING BALANCE (Collection - Expenses)" to fmt(todayClosingBalance)
                                            ),
                                            highlightLastRow = true
                                        )

                                        val stockRows = arrayListOf<Pair<String, String>>()
                                        for ((item, pair) in stockMap.toSortedMap()) {
                                            stockRows.add(item to "${pair.first} Bags  |  ${fmt(pair.second)} Kgs")
                                        }
                                        if (stockRows.isEmpty()) stockRows.add("Stock" to "No active batches")
                                        addTwoColCard("8. STOCK INQUIRY", stockRows)

                                        addTwoColCard(
                                            "9. INCOME DETAILS",
                                            listOf(
                                                "Cash Sales" to fmt(cashSales),
                                                "GPay Sales" to fmt(gpaySales),
                                                "Credit Bill Received via Cash" to fmt(creditRecCash),
                                                "Credit Bill Received via GPay" to fmt(creditRecGpay),
                                                "Total Income Details" to fmt(incomeDetailsTotal)
                                            ),
                                            highlightLastRow = true
                                        )

                                        addTwoColCard(
                                            "10. CUSTOMER OUTSTANDING (VERTICAL)",
                                            listOf(
                                                "Opening Outstanding" to fmt(vertOpOut),
                                                "(+) Today Sales" to fmt(todaySalesTotal),
                                                "(-) Today Income" to fmt(incomeDetailsTotal),
                                                "(-) Today Discount" to fmt(todayCustDiscount),
                                                "Closing Outstanding" to fmt(vertClosingOut)
                                            ),
                                            highlightLastRow = true
                                        )

                                        addTwoColCard(
                                            "11. PROFIT AND LOSS",
                                            listOf(
                                                "Today Sales" to fmt(todaySalesTotal),
                                                "(-) Today Purchase" to fmt(todayPurchaseTot),
                                                "(-) Total Expenses & Discount" to fmt(totalExpenses + todayCustDiscount),
                                                "Net Profit / (Loss)" to fmt(netProfitLoss)
                                            ),
                                            highlightLastRow = true
                                        )

                                        buildA4ClosingHtml(
                                            purCash, purCredit, todayPurchaseTot, supplierClosingOut,
                                            creditRecCash, creditRecGpay,
                                            cashSales, gpaySales, creditSales, todaySalesTotal, cashReceivedTotal,
                                            otherExp, shopExp, vehicleExp, totalExpenses,
                                            otherCashIncome, collectionTotal, todayClosingBalance,
                                            stockRows, incomeDetailsTotal,
                                            vertOpOut, incomeDetailsTotal, todayCustDiscount, vertClosingOut,
                                            netProfitLoss
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildA4ClosingHtml(
        purCash: Double, purCredit: Double, purTot: Double, supOut: Double,
        recCash: Double, recGpay: Double,
        salCash: Double, salGpay: Double, salCredit: Double, salTot: Double, cashRecTot: Double,
        expOther: Double, expShop: Double, expVeh: Double, expTot: Double,
        otherInc: Double, collTot: Double, closingCash: Double,
        stockRows: List<Pair<String, String>>, incTot: Double,
        opOut: Double, todayIncOut: Double, todayDiscOut: Double, clOut: Double,
        netPl: Double
    ) {
        val stockHtml = StringBuilder()
        for (s in stockRows) {
            stockHtml.append("<tr><td>${s.first}</td><td class='r'>${s.second}</td></tr>")
        }

        lastA4Html = """
            <html>
            <head>
            <style>
                * { box-sizing: border-box; }
                @page { size: A4 portrait; margin: 4mm; }
                body { font-family: Arial, sans-serif; color: #000; margin: 0; padding: 0; font-size: 11px; }
                .center { text-align: center; }
                h1 { font-size: 18px; margin: 0; }
                h2 { font-size: 11.5px; margin: 1px 0 3px 0; }
                h3 { font-size: 13.5px; margin: 2px 0 5px 0; text-decoration: underline; }
                .cols { display: flex; justify-content: space-between; gap: 8px; }
                .col { width: 49.5%; }
                table { width: 100%; border-collapse: collapse; margin-bottom: 6px; }
                th, td { border: 1px solid #000; padding: 3.5px 5px; }
                th { background: #E0E0E0; text-align: left; font-size: 11px; }
                td.r { text-align: right; font-weight: bold; }
                tr.hi td { background: #FFF9C4; font-weight: bold; }
            </style>
            </head>
            <body>
                <div class="center">
                    <h1>THIRUVENI COCONUTS</h1>
                    <h2>SHOP NO B98, PERIYAR VEGETABLE MARKET, KOYAMBEDU</h2>
                    <h3>DAILY ACCOUNT CLOSING REPORT  |  DATE: $selectedDateStr</h3>
                </div>
                <div class="cols">
                    <div class="col">
                        <table>
                            <tr><th colspan="2">1. PURCHASE DETAILS &amp; 2. SUPPLIER OUTSTANDING</th></tr>
                            <tr><td>Cash Purchase</td><td class="r">${fmt(purCash)}</td></tr>
                            <tr><td>Credit Purchase</td><td class="r">${fmt(purCredit)}</td></tr>
                            <tr><td>Today Total Purchase</td><td class="r">${fmt(purTot)}</td></tr>
                            <tr class="hi"><td>2. Supplier Closing Outstanding</td><td class="r">${fmt(supOut)}</td></tr>
                        </table>

                        <table>
                            <tr><th colspan="3">3. CREDIT BILL RECEIVED</th></tr>
                            <tr><th>Particulars</th><th style="text-align:right;">CASH</th><th style="text-align:right;">GPAY</th></tr>
                            <tr class="hi"><td>Credit Bill Received</td><td class="r">${fmt(recCash)}</td><td class="r">${fmt(recGpay)}</td></tr>
                        </table>

                        <table>
                            <tr><th colspan="2">4. TODAY SALES &amp; 5. CASH RECEIVED</th></tr>
                            <tr><td>Cash Sales</td><td class="r">${fmt(salCash)}</td></tr>
                            <tr><td>GPay Sales</td><td class="r">${fmt(salGpay)}</td></tr>
                            <tr><td>Credit Sales</td><td class="r">${fmt(salCredit)}</td></tr>
                            <tr class="hi"><td>4. Today Total Sales</td><td class="r">${fmt(salTot)}</td></tr>
                            <tr class="hi"><td>5. Cash Received (Cash Sales + Credit Cash)</td><td class="r">${fmt(cashRecTot)}</td></tr>
                        </table>

                        <table>
                            <tr><th colspan="2">6. EXPENSES &amp; 7. COLLECTION CLOSING</th></tr>
                            <tr><td>6A. Other Expenses</td><td class="r">${fmt(expOther)}</td></tr>
                            <tr><td>6B. Shop Expenses</td><td class="r">${fmt(expShop)}</td></tr>
                            <tr><td>6C. Vehicle Expenses</td><td class="r">${fmt(expVeh)}</td></tr>
                            <tr class="hi"><td>Total Expenses</td><td class="r">${fmt(expTot)}</td></tr>
                            <tr><td>7. Collection (Cash Sales + Credit Cash + Other Inc ${fmt(otherInc)})</td><td class="r">${fmt(collTot)}</td></tr>
                            <tr class="hi"><td>TODAY CLOSING BALANCE (Collection - Exp)</td><td class="r">${fmt(closingCash)}</td></tr>
                        </table>
                    </div>

                    <div class="col">
                        <table>
                            <tr><th colspan="2">8. STOCK INQUIRY</th></tr>
                            $stockHtml
                        </table>

                        <table>
                            <tr><th colspan="2">9. INCOME DETAILS</th></tr>
                            <tr><td>Cash Sales</td><td class="r">${fmt(salCash)}</td></tr>
                            <tr><td>GPay Sales</td><td class="r">${fmt(salGpay)}</td></tr>
                            <tr><td>Credit Bill Received via Cash</td><td class="r">${fmt(recCash)}</td></tr>
                            <tr><td>Credit Bill Received via GPay</td><td class="r">${fmt(recGpay)}</td></tr>
                            <tr class="hi"><td>Total Income Details</td><td class="r">${fmt(incTot)}</td></tr>
                        </table>

                        <table>
                            <tr><th colspan="2">10. CUSTOMER OUTSTANDING (VERTICAL)</th></tr>
                            <tr><td>Opening Outstanding</td><td class="r">${fmt(opOut)}</td></tr>
                            <tr><td>(+) Today Sales</td><td class="r">${fmt(salTot)}</td></tr>
                            <tr><td>(-) Today Income</td><td class="r">${fmt(todayIncOut)}</td></tr>
                            <tr><td>(-) Today Discount</td><td class="r">${fmt(todayDiscOut)}</td></tr>
                            <tr class="hi"><td>Closing Outstanding</td><td class="r">${fmt(clOut)}</td></tr>
                        </table>

                        <table>
                            <tr><th colspan="2">11. PROFIT AND LOSS</th></tr>
                            <tr><td>Today Sales</td><td class="r">${fmt(salTot)}</td></tr>
                            <tr><td>(-) Today Purchase</td><td class="r">${fmt(purTot)}</td></tr>
                            <tr><td>(-) Expenses &amp; Discount</td><td class="r">${fmt(expTot + todayDiscOut)}</td></tr>
                            <tr class="hi"><td>Net Profit / (Loss)</td><td class="r">${fmt(netPl)}</td></tr>
                        </table>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun printA4ClosingSheet() {
        if (lastA4Html.isEmpty()) return
        printWebView = WebView(this)
        printWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = printWebView.createPrintDocumentAdapter("Daily_Closing_Report_$selectedDateStr")
                val builder = PrintAttributes.Builder()
                builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                builder.setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                printManager.print("Daily Closing Report A4", printAdapter, builder.build())
            }
        }
        printWebView.loadDataWithBaseURL(null, lastA4Html, "text/HTML", "UTF-8", null)
        Toast.makeText(this, "Preparing Daily Closing Report A4...", Toast.LENGTH_SHORT).show()
    }
}
