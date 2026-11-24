package com.shop.veggieledger

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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

data class CustomerOutstandingRow(
    val customerName: String,
    val group: String,
    val openingBalance: Double,
    val todaySales: Double,
    val todayIncome: Double,
    val todayDiscount: Double,
    val closingBalance: Double
)

class CustomerOutstandingActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var selectedDateStr: String = ""
    private var reportList = mutableListOf<CustomerOutstandingRow>()
    private var webViewForPrint: WebView? = null
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        selectedDateStr = intent.getStringExtra("SELECTED_DATE") ?: sdf.format(Date())

        // XML இல்லாமல் நேரடியாக UI உருவாக்குதல்
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.WHITE)
        }

        val tvTitle = TextView(this).apply {
            text = "THIRUVENI COCONUTS\nCustomer Outstanding"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
        }

        val tvDate = TextView(this).apply {
            text = "Report Date: $selectedDateStr"
            textSize = 16f
            setPadding(0, 24, 0, 24)
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
        }

        tvStatus = TextView(this).apply {
            text = "Loading customer records..."
            textSize = 14f
            setPadding(0, 16, 0, 32)
            gravity = Gravity.CENTER
            setTextColor(Color.BLUE)
        }

        val btnPrint = Button(this).apply {
            text = "PRINT A4 OUTSTANDING REPORT"
            textSize = 15f
            setPadding(32, 24, 32, 24)
        }

        mainLayout.addView(tvTitle)
        mainLayout.addView(tvDate)
        mainLayout.addView(tvStatus)
        mainLayout.addView(btnPrint)
        setContentView(mainLayout)

        fetchAndCalculateOutstanding(selectedDateStr)

        btnPrint.setOnClickListener {
            if (reportList.isNotEmpty()) {
                printA4Report(reportList, selectedDateStr)
            } else {
                Toast.makeText(this, "No records to print!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchAndCalculateOutstanding(targetDate: String) {
        db.collection("customers").get().addOnSuccessListener { customerDocs ->
            db.collection("sales").get().addOnSuccessListener { salesDocs ->
                db.collection("receipts").get().addOnSuccessListener { receiptDocs ->

                    reportList.clear()

                    for (custDoc in customerDocs) {
                        val custName = custDoc.getString("name") ?: ""
                        val group = custDoc.getString("group") ?: ""
                        val initialOpBal = custDoc.getDouble("opbal") ?: 0.0

                        var prevSales = 0.0
                        var todaySales = 0.0

                        var prevIncome = 0.0
                        var prevDiscount = 0.0
                        var todayIncome = 0.0
                        var todayDiscount = 0.0

                        // 1. Sales கணக்கீடு (நேத்து வரை vs இன்னைக்கு)
                        for (saleDoc in salesDocs) {
                            val sCustName = saleDoc.getString("customerName") ?: ""
                            val sDate = normalizeDate(saleDoc.getString("date") ?: "")
                            val sAmount = saleDoc.getDouble("amount") ?: 0.0

                            if (sCustName.equals(custName, ignoreCase = true)) {
                                if (sDate < targetDate) {
                                    prevSales += sAmount
                                } else if (sDate == targetDate) {
                                    todaySales += sAmount
                                }
                            }
                        }

                        // 2. Income & Discount கணக்கீடு (நேத்து வரை vs இன்னைக்கு)
                        for (recDoc in receiptDocs) {
                            val rCustName = recDoc.getString("customerName") ?: ""
                            val rDate = normalizeDate(recDoc.getString("date") ?: "")
                            val rAmount = recDoc.getDouble("amount") ?: 0.0
                            val rDiscount = recDoc.getDouble("discount") ?: 0.0

                            if (rCustName.equals(custName, ignoreCase = true)) {
                                if (rDate < targetDate) {
                                    prevIncome += rAmount
                                    prevDiscount += rDiscount
                                } else if (rDate == targetDate) {
                                    todayIncome += rAmount
                                    todayDiscount += rDiscount
                                }
                            }
                        }

                        // 3. Opening & Closing கணக்கீடு
                        val todayOpening = initialOpBal + prevSales - (prevIncome + prevDiscount)
                        val todayClosing = todayOpening + todaySales - (todayIncome + todayDiscount)

                        if (todayOpening != 0.0 || todaySales != 0.0 || todayIncome != 0.0 || todayClosing != 0.0) {
                            reportList.add(
                                CustomerOutstandingRow(
                                    customerName = custName,
                                    group = group,
                                    openingBalance = todayOpening,
                                    todaySales = todaySales,
                                    todayIncome = todayIncome,
                                    todayDiscount = todayDiscount,
                                    closingBalance = todayClosing
                                )
                            )
                        }
                    }

                    reportList.sortBy { it.customerName }
                    tvStatus.text = "Ready! ${reportList.size} customers loaded."
                    tvStatus.setTextColor(Color.parseColor("#006400"))
                }
            }
        }.addOnFailureListener {
            tvStatus.text = "Error: ${it.message}"
            tvStatus.setTextColor(Color.RED)
        }
    }

    private fun normalizeDate(dateStr: String): String {
        if (dateStr.isEmpty()) return ""
        return try {
            if (dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                dateStr
            } else {
                val cleanStr = dateStr.replace("/", "-")
                val inputFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = inputFormat.parse(cleanStr)
                if (date != null) outputFormat.format(date) else dateStr
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun printA4Report(rows: List<CustomerOutstandingRow>, reportDate: String) {
        val webView = WebView(this)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "Thiruveni_Outstanding_$reportDate"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                val builder = PrintAttributes.Builder()
                builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                printManager.print(jobName, printAdapter, builder.build())
            }
        }

        var totOp = 0.0
        var totSales = 0.0
        var totInc = 0.0
        var totDisc = 0.0
        var totClose = 0.0

        val tableRowsHtml = StringBuilder()
        rows.forEachIndexed { index, row ->
            totOp += row.openingBalance
            totSales += row.todaySales
            totInc += row.todayIncome
            totDisc += row.todayDiscount
            totClose += row.closingBalance

            tableRowsHtml.append("""
                <tr>
                    <td style="text-align:center;">${index + 1}</td>
                    <td>${row.customerName}</td>
                    <td style="text-align:right;">${String.format("%.2f", row.openingBalance)}</td>
                    <td style="text-align:right;">${String.format("%.2f", row.todaySales)}</td>
                    <td style="text-align:right;">${String.format("%.2f", row.todayIncome)}</td>
                    <td style="text-align:right;">${String.format("%.2f", row.todayDiscount)}</td>
                    <td style="text-align:right; font-weight:bold;">${String.format("%.2f", row.closingBalance)}</td>
                </tr>
            """.trimIndent())
        }

        val htmlContent = """
            <html>
            <head>
                <style>
                    body { font-family: sans-serif; font-size: 11px; margin: 15px; }
                    h2, p { text-align: center; margin: 2px 0; }
                    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                    th, td { border: 1px solid #000; padding: 5px; }
                    th { background-color: #f2f2f2; }
                    .total-row { background-color: #e6e6e6; font-weight: bold; }
                </style>
            </head>
            <body>
                <h2>THIRUVENI COCONUTS</h2>
                <p>Shop No B98, Periyar Vegetable Market, Koyambedu, Chennai</p>
                <p><strong>CUSTOMER OUTSTANDING REPORT - DATE: $reportDate</strong></p>
                <table>
                    <thead>
                        <tr>
                            <th>S.No</th>
                            <th>Customer Name</th>
                            <th>Op. Bal</th>
                            <th>Today Sales</th>
                            <th>Income</th>
                            <th>Discount</th>
                            <th>Closing Bal</th>
                        </tr>
                    </thead>
                    <tbody>
                        $tableRowsHtml
                        <tr class="total-row">
                            <td colspan="2" style="text-align:right;">TOTAL</td>
                            <td style="text-align:right;">${String.format("%.2f", totOp)}</td>
                            <td style="text-align:right;">${String.format("%.2f", totSales)}</td>
                            <td style="text-align:right;">${String.format("%.2f", totInc)}</td>
                            <td style="text-align:right;">${String.format("%.2f", totDisc)}</td>
                            <td style="text-align:right;">${String.format("%.2f", totClose)}</td>
                        </tr>
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, htmlContent, "text/HTML", "UTF-8", null)
        webViewForPrint = webView
    }
}
