package com.thiruvenicoconuts.dailyledgerapp // உங்க package name-ஐ இங்க போடுங்க

import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

// 1. Report-க்கான Data Model
data class CustomerOutstandingRow(
    val customerName: String,
    val group: String,
    val openingBalance: Double,
    val todaySales: Double,
    val todayIncome: Double,
    val todayDiscount: Double,
    val closingBalance: Double
)

class CustomerOutstandingReportActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var selectedDateStr: String = "" // Format: "yyyy-MM-dd"
    private var reportList = mutableListOf<CustomerOutstandingRow>()
    private var webViewForPrint: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // உங்க XML layout name-ஐ இங்க குடுங்க
        setContentView(R.layout.activity_customer_outstanding)

        // Default-ஆ இன்னைக்கு தேதியை எடுத்தல்
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        selectedDateStr = intent.getStringExtra("SELECTED_DATE") ?: sdf.format(Date())

        val tvDate = findViewById<TextView>(R.id.tvReportDate)
        val btnPrint = findViewById<Button>(R.id.btnPrintOutstanding)

        tvDate.text = "Date: $selectedDateStr"

        // Data-வை load பண்ணி calculate செய்தல்
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
        // 1. முதலில் எல்லா Customers-ஐயும் எடுக்கிறோம்
        db.collection("customers").get().addOnSuccessListener { customerDocs ->
            // 2. அடுத்து எல்லா Sales-ஐயும் எடுக்கிறோம்
            db.collection("sales").get().addOnSuccessListener { salesDocs ->
                // 3. அடுத்து எல்லா Receipts (Income & Discount)-ஐயும் எடுக்கிறோம்
                db.collection("receipts").get().addOnSuccessListener { receiptDocs ->

                    reportList.clear()

                    for (custDoc in customerDocs) {
                        val custId = custDoc.id
                        val custName = custDoc.getString("name") ?: ""
                        val group = custDoc.getString("group") ?: ""
                        // கஸ்டமர் ஆரம்ப கணக்கு (Initial OpBal)
                        val initialOpBal = custDoc.getDouble("opbal") ?: 0.0

                        var prevSales = 0.0
                        var todaySales = 0.0

                        var prevIncome = 0.0
                        var prevDiscount = 0.0
                        var todayIncome = 0.0
                        var todayDiscount = 0.0

                        // --- SALES கணக்கீடு (நேத்து வரை vs இன்னைக்கு) ---
                        for (saleDoc in salesDocs) {
                            val sCustName = saleDoc.getString("customerName") ?: ""
                            val sDate = normalizeDate(saleDoc.getString("date") ?: "")
                            val sAmount = saleDoc.getDouble("amount") ?: 0.0

                            if (sCustName.equals(custName, ignoreCase = true)) {
                                if (sDate < targetDate) {
                                    // நேத்து மற்றும் அதுக்கு முன்னாடி நடந்த Sales
                                    prevSales += sAmount
                                } else if (sDate == targetDate) {
                                    // சரியா இன்னைக்கு நடந்த Sales மட்டும்
                                    todaySales += sAmount
                                }
                            }
                        }

                        // --- INCOME & DISCOUNT கணக்கீடு (நேத்து வரை vs இன்னைக்கு) ---
                        for (recDoc in receiptDocs) {
                            val rCustName = recDoc.getString("customerName") ?: ""
                            val rDate = normalizeDate(recDoc.getString("date") ?: "")
                            val rAmount = recDoc.getDouble("amount") ?: 0.0 // வரவு (Income)
                            val rDiscount = recDoc.getDouble("discount") ?: 0.0 // தள்ளுபடி (Discount)

                            if (rCustName.equals(custName, ignoreCase = true)) {
                                if (rDate < targetDate) {
                                    // நேத்து வரைக்கும் வந்த வரவு & தள்ளுபடி
                                    prevIncome += rAmount
                                    prevDiscount += rDiscount
                                } else if (rDate == targetDate) {
                                    // சரியா இன்னைக்கு வந்த வரவு & தள்ளுபடி மட்டும்
                                    todayIncome += rAmount
                                    todayDiscount += rDiscount
                                }
                            }
                        }

                        // --- சரியான OPENING மற்றும் CLOSING கணக்கீடு ---
                        // நேத்து closing-தான் இன்னைக்கு opening!
                        val todayOpening = initialOpBal + prevSales - (prevIncome + prevDiscount)
                        val todayClosing = todayOpening + todaySales - (todayIncome + todayDiscount)

                        // Balance அல்லது இன்னைக்கு transaction இருந்தால் மட்டும் list-ல் சேர்க்கவும்
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

                    // பெயர் வாரியாக வரிசைப்படுத்துதல் (Alphabetical Sort)
                    reportList.sortBy { it.customerName }

                    // இங்க உங்க RecyclerView adapter-ஐ update பண்ணிக்கோங்க:
                    // adapter.notifyDataSetChanged()
                    Toast.makeText(this, "Loaded ${reportList.size} customers", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Database-ல் தேதி "dd-MM-yyyy" அல்லது "dd/MM/yyyy" என்று இருந்தாலும்,
     * அதை "yyyy-MM-dd" ஆக மாற்றும் helper function (அப்போதுதான் < மற்றும் == சரியாக வேலை செய்யும்).
     */
    private fun normalizeDate(dateStr: String): String {
        if (dateStr.isEmpty()) return ""
        return try {
            if (dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                dateStr // ஏற்கனவே yyyy-MM-dd format-ல் உள்ளது
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

    /**
     * A4 Portrait Print / PDF Export Function
     */
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
