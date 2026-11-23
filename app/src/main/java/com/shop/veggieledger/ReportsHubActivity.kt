package com.shop.veggieledger

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class ReportsHubActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var reportType: String = ""

    private lateinit var tvTitle: TextView
    private lateinit var tvSheetShopName: TextView
    private lateinit var tvSheetShopAddr: TextView
    private lateinit var tvSheetSubTitle: TextView
    private lateinit var tvLeftMeta: TextView
    private lateinit var tvRightMeta: TextView
    private lateinit var tvSummary: TextView
    private lateinit var etFromDate: EditText
    private lateinit var etToDate: EditText
    private lateinit var spPartyFilter: Spinner
    private lateinit var layoutPartyBox: LinearLayout
    private lateinit var tvPartyLabel: TextView
    private lateinit var tableLayout: TableLayout
    private lateinit var printWebView: WebView

    private var fromDateStr: String = ""
    private var toDateStr: String = ""
    private val partyNames = arrayListOf("ALL CUSTOMERS")
    private val partyOpBalMap = HashMap<String, Double>()
    private val partyIdMap = HashMap<String, String>()
    private var shareReportText: String = ""
    private var lastPrintableHtml: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reportType = intent.getStringExtra("REPORT_TYPE") ?: "SAL_CUSTOMER"
        supportActionBar?.title = getReportTitle(reportType)

        buildUiProgrammatically()
        setupDates()
        setupPartyDropdownIfNeeded()
    }

    private fun fmtVal(value: Double): String {
        return if (abs(value) < 0.005) "-" else String.format(Locale.getDefault(), "%.2f", value)
    }

    private fun fmtQty(value: Double): String {
        if (abs(value) < 0.005) return "-"
        return if (abs(value - value.toLong()) < 0.005) {
            "${value.toLong()}"
        } else {
            String.format(Locale.getDefault(), "%.2f", value)
        }
    }

    private fun getReportTitle(type: String): String {
        return when (type) {
            "PUR_SUPPLIER" -> "Supplier-wise Purchase Report"
            "PUR_DETAILS" -> "Purchase Bill Details"
            "PUR_ITEMWISE" -> "Itemwise Purchase Report"
            "PUR_STATEMENT" -> "Supplier Ledger Statement"
            "SAL_CUSTOMER" -> "Customer Sales Report"
            "SAL_DETAILS" -> "Sales Bill Details"
            "SAL_ITEMWISE" -> "Itemwise Sales Report"
            "SAL_STATEMENT" -> "Customer Ledger Statement"
            else -> "Report"
        }
    }

    private fun buildUiProgrammatically() {
        val rootScroll = ScrollView(this)
        rootScroll.setBackgroundColor(Color.parseColor("#ECEFF1"))
        rootScroll.setPadding(16, 16, 16, 16)

        val mainCol = LinearLayout(this)
        mainCol.orientation = LinearLayout.VERTICAL
        rootScroll.addView(mainCol)

        val filterCard = LinearLayout(this)
        filterCard.orientation = LinearLayout.VERTICAL
        filterCard.setBackgroundColor(Color.WHITE)
        filterCard.setPadding(18, 18, 18, 18)
        val fcLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        fcLp.setMargins(0, 0, 0, 14)
        filterCard.layoutParams = fcLp

        tvTitle = TextView(this)
        tvTitle.text = getReportTitle(reportType).uppercase(Locale.getDefault())
        tvTitle.textSize = 16f
        tvTitle.setTypeface(null, Typeface.BOLD)
        tvTitle.setTextColor(Color.parseColor("#1B5E20"))
        tvTitle.gravity = Gravity.CENTER
        tvTitle.setPadding(0, 0, 0, 12)
        filterCard.addView(tvTitle)

        layoutPartyBox = LinearLayout(this)
        layoutPartyBox.orientation = LinearLayout.VERTICAL
        layoutPartyBox.setPadding(0, 0, 0, 10)
        tvPartyLabel = TextView(this)
        tvPartyLabel.text = "1. Customer Name"
        tvPartyLabel.textSize = 12f
        tvPartyLabel.setTypeface(null, Typeface.BOLD)
        tvPartyLabel.setTextColor(Color.parseColor("#1B5E20"))
        spPartyFilter = Spinner(this)
        spPartyFilter.setBackgroundResource(android.R.drawable.btn_dropdown)
        layoutPartyBox.addView(tvPartyLabel)
        layoutPartyBox.addView(spPartyFilter)
        filterCard.addView(layoutPartyBox)

        val dateRow = LinearLayout(this)
        dateRow.orientation = LinearLayout.HORIZONTAL
        dateRow.weightSum = 2f
        dateRow.setPadding(0, 0, 0, 8)

        val fromCol = LinearLayout(this)
        fromCol.orientation = LinearLayout.VERTICAL
        fromCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val lblFrom = TextView(this)
        lblFrom.text = "2. From Date"
        lblFrom.textSize = 12f
        lblFrom.setTypeface(null, Typeface.BOLD)
        etFromDate = EditText(this)
        etFromDate.isFocusable = false
        etFromDate.isClickable = true
        etFromDate.setBackgroundResource(android.R.drawable.edit_text)
        fromCol.addView(lblFrom)
        fromCol.addView(etFromDate)

        val toCol = LinearLayout(this)
        toCol.orientation = LinearLayout.VERTICAL
        toCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val lblTo = TextView(this)
        lblTo.text = "3. To Date"
        lblTo.textSize = 12f
        lblTo.setTypeface(null, Typeface.BOLD)
        etToDate = EditText(this)
        etToDate.isFocusable = false
        etToDate.isClickable = true
        etToDate.setBackgroundResource(android.R.drawable.edit_text)
        toCol.addView(lblTo)
        toCol.addView(etToDate)

        dateRow.addView(fromCol)
        dateRow.addView(toCol)
        filterCard.addView(dateRow)

        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.weightSum = 3f
        btnRow.setPadding(0, 4, 0, 4)

        val btnFilter = Button(this)
        btnFilter.text = "SHOW REPORT"
        btnFilter.setBackgroundColor(Color.parseColor("#1B5E20"))
        btnFilter.setTextColor(Color.WHITE)
        btnFilter.setTypeface(null, Typeface.BOLD)
        val lpBtn1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
        lpBtn1.setMargins(0, 0, 4, 0)
        btnFilter.layoutParams = lpBtn1
        btnFilter.setOnClickListener { runReport() }

        val btnPrint = Button(this)
        btnPrint.text = "A4 PDF"
        btnPrint.setBackgroundColor(Color.parseColor("#E65100"))
        btnPrint.setTextColor(Color.WHITE)
        btnPrint.setTypeface(null, Typeface.BOLD)
        val lpBtn2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
        lpBtn2.setMargins(4, 0, 4, 0)
        btnPrint.layoutParams = lpBtn2
        btnPrint.setOnClickListener { printCurrentReportA4() }

        val btnShare = Button(this)
        btnShare.text = "SHARE"
        btnShare.setBackgroundColor(Color.parseColor("#1565C0"))
        btnShare.setTextColor(Color.WHITE)
        btnShare.setTypeface(null, Typeface.BOLD)
        val lpBtn3 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
        lpBtn3.setMargins(4, 0, 0, 0)
        btnShare.layoutParams = lpBtn3
        btnShare.setOnClickListener {
            val sendIntent = Intent(Intent.ACTION_SEND)
            sendIntent.type = "text/plain"
            sendIntent.putExtra(Intent.EXTRA_TEXT, shareReportText)
            startActivity(Intent.createChooser(sendIntent, "Share Report"))
        }

        btnRow.addView(btnFilter)
        btnRow.addView(btnPrint)
        btnRow.addView(btnShare)
        filterCard.addView(btnRow)
        mainCol.addView(filterCard)

        tvSummary = TextView(this)
        tvSummary.text = "Loading..."
        tvSummary.setBackgroundColor(Color.parseColor("#FFF9C4"))
        tvSummary.setTextColor(Color.parseColor("#D84315"))
        tvSummary.setTypeface(null, Typeface.BOLD)
        tvSummary.textSize = 14f
        tvSummary.setPadding(18, 14, 18, 14)
        val sumLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        sumLp.setMargins(0, 0, 0, 12)
        tvSummary.layoutParams = sumLp
        mainCol.addView(tvSummary)

        val sheetBox = LinearLayout(this)
        sheetBox.orientation = LinearLayout.VERTICAL
        sheetBox.setBackgroundColor(Color.WHITE)
        sheetBox.setPadding(20, 24, 20, 24)

        tvSheetShopName = TextView(this)
        tvSheetShopName.text = "THIRUVENI COCONUTS"
        tvSheetShopName.textSize = 21f
        tvSheetShopName.setTypeface(null, Typeface.BOLD)
        tvSheetShopName.setTextColor(Color.BLACK)
        tvSheetShopName.gravity = Gravity.CENTER
        sheetBox.addView(tvSheetShopName)

        tvSheetShopAddr = TextView(this)
        tvSheetShopAddr.text = "SHOP NO B98, PERIYAR VEGETABLE MARKET, KOYAMBEDU"
        tvSheetShopAddr.textSize = 13f
        tvSheetShopAddr.setTypeface(null, Typeface.BOLD)
        tvSheetShopAddr.setTextColor(Color.DKGRAY)
        tvSheetShopAddr.gravity = Gravity.CENTER
        tvSheetShopAddr.setPadding(0, 2, 0, 8)
        sheetBox.addView(tvSheetShopAddr)

        tvSheetSubTitle = TextView(this)
        tvSheetSubTitle.text = getReportTitle(reportType).uppercase(Locale.getDefault())
        tvSheetSubTitle.textSize = 16f
        tvSheetSubTitle.setTypeface(null, Typeface.BOLD)
        tvSheetSubTitle.setTextColor(Color.BLACK)
        tvSheetSubTitle.gravity = Gravity.CENTER
        tvSheetSubTitle.setPadding(0, 2, 0, 12)
        sheetBox.addView(tvSheetSubTitle)

        val metaRow = LinearLayout(this)
        metaRow.orientation = LinearLayout.HORIZONTAL
        metaRow.weightSum = 2f
        metaRow.setPadding(4, 6, 4, 10)

        tvLeftMeta = TextView(this)
        tvLeftMeta.text = "CUSTOMER NAME: ALL CUSTOMERS"
        tvLeftMeta.textSize = 13f
        tvLeftMeta.setTypeface(null, Typeface.BOLD)
        tvLeftMeta.setTextColor(Color.BLACK)
        tvLeftMeta.gravity = Gravity.START
        tvLeftMeta.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)

        tvRightMeta = TextView(this)
        tvRightMeta.text = "DATE: $fromDateStr TO $toDateStr"
        tvRightMeta.textSize = 13f
        tvRightMeta.setTypeface(null, Typeface.BOLD)
        tvRightMeta.setTextColor(Color.BLACK)
        tvRightMeta.gravity = Gravity.END
        tvRightMeta.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)

        metaRow.addView(tvLeftMeta)
        metaRow.addView(tvRightMeta)
        sheetBox.addView(metaRow)

        val hScroll = HorizontalScrollView(this)
        hScroll.setBackgroundColor(Color.WHITE)
        tableLayout = TableLayout(this)
        tableLayout.setBackgroundColor(Color.BLACK)
        tableLayout.setPadding(1, 1, 1, 1)
        hScroll.addView(tableLayout)
        sheetBox.addView(hScroll)

        mainCol.addView(sheetBox)
        setContentView(rootScroll)
    }

    private fun setupDates() {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val calTo = Calendar.getInstance()
        toDateStr = sdf.format(calTo.time)

        val calFrom = Calendar.getInstance()
        calFrom.set(Calendar.DAY_OF_MONTH, 1)
        fromDateStr = sdf.format(calFrom.time)

        etFromDate.setText(fromDateStr)
        etToDate.setText(toDateStr)

        etFromDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                calFrom.set(y, m, d)
                fromDateStr = sdf.format(calFrom.time)
                etFromDate.setText(fromDateStr)
                runReport()
            }, calFrom.get(Calendar.YEAR), calFrom.get(Calendar.MONTH), calFrom.get(Calendar.DAY_OF_MONTH)).show()
        }

        etToDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                calTo.set(y, m, d)
                toDateStr = sdf.format(calTo.time)
                etToDate.setText(toDateStr)
                runReport()
            }, calTo.get(Calendar.YEAR), calTo.get(Calendar.MONTH), calTo.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun setupPartyDropdownIfNeeded() {
        val isPurchase = reportType.startsWith("PUR_")
        val isItemwise = reportType.endsWith("ITEMWISE")
        val isStatement = reportType.endsWith("STATEMENT")

        if (isItemwise) {
            layoutPartyBox.visibility = View.GONE
            runReport()
            return
        }

        layoutPartyBox.visibility = View.VISIBLE
        tvPartyLabel.text = if (isPurchase) "1. Supplier Name" else "1. Customer Name"
        val collectionName = if (isPurchase) "suppliers" else "customers"

        db.collection(collectionName).get().addOnSuccessListener { snap ->
            partyNames.clear()
            partyOpBalMap.clear()
            partyIdMap.clear()
            if (!isStatement) {
                partyNames.add(if (isPurchase) "ALL SUPPLIERS" else "ALL CUSTOMERS")
            }

            val sortedDocs = snap.documents.sortedBy { doc ->
                (doc.getString("name") ?: "").lowercase(Locale.getDefault())
            }
            for (doc in sortedDocs) {
                val name = doc.getString("name")?.trim() ?: continue
                val opBal = doc.getDouble("opbal") ?: doc.getDouble("openingBalance") ?: 0.0
                partyNames.add(name)
                partyOpBalMap[name] = opBal
                partyIdMap[name] = doc.id
            }

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, partyNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spPartyFilter.adapter = adapter

            spPartyFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    runReport()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            runReport()
        }
    }

    private fun parseDateSafe(dateStr: String): Date? {
        return try {
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(dateStr)
        } catch (e: Exception) {
            null
        }
    }

    private fun isDateBeforeFrom(dateStr: String): Boolean {
        val d = parseDateSafe(dateStr) ?: return false
        val from = parseDateSafe(fromDateStr) ?: return false
        return d.before(from)
    }

    private fun isDateInRange(dateStr: String): Boolean {
        val d = parseDateSafe(dateStr) ?: return true
        val from = parseDateSafe(fromDateStr) ?: return true
        val to = parseDateSafe(toDateStr) ?: return true
        return !d.before(from) && !d.after(to)
    }

    private fun makeBorderedCell(
        text: String,
        alignRight: Boolean = false,
        bold: Boolean = false,
        bgColor: Int = Color.WHITE,
        textColor: Int = Color.BLACK
    ): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(textColor)
        tv.setBackgroundColor(bgColor)
        tv.textSize = 14f
        tv.setPadding(14, 14, 14, 14)
        tv.gravity = if (alignRight) Gravity.END else Gravity.START
        if (bold) tv.setTypeface(null, Typeface.BOLD)
        val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.MATCH_PARENT)
        lp.setMargins(1, 1, 1, 1)
        tv.layoutParams = lp
        return tv
    }

    private fun addHeaderRow(headers: List<String>, bgHex: String) {
        tableLayout.removeAllViews()
        val tr = TableRow(this)
        tr.setBackgroundColor(Color.BLACK)
        val bg = Color.parseColor(bgHex)
        for (h in headers) {
            tr.addView(makeBorderedCell(h, bold = true, bgColor = bg, textColor = Color.WHITE))
        }
        tableLayout.addView(tr)
    }

    private fun addCustomerSectionBannerRow(customerName: String, colSpanCount: Int) {
        val tr = TableRow(this)
        tr.setBackgroundColor(Color.BLACK)
        val tv = TextView(this)
        tv.text = "NAME: $customerName"
        tv.setTextColor(Color.parseColor("#0D47A1"))
        tv.setBackgroundColor(Color.parseColor("#E3F2FD"))
        tv.textSize = 15f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setPadding(14, 14, 14, 14)
        tv.gravity = Gravity.START
        val lp = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
        lp.span = colSpanCount
        lp.setMargins(1, 1, 1, 1)
        tv.layoutParams = lp
        tr.addView(tv)
        tableLayout.addView(tr)
    }

    private fun addDataRow(
        cells: List<String>,
        rowIndex: Int,
        rightAlignFromIndex: Int = 1,
        isTotalRow: Boolean = false,
        isSubTotalRow: Boolean = false
    ) {
        val tr = TableRow(this)
        tr.setBackgroundColor(Color.BLACK)
        val bg = when {
            isTotalRow -> Color.parseColor("#C8E6C9")
            isSubTotalRow -> Color.parseColor("#FFFDE7")
            rowIndex % 2 == 0 -> Color.WHITE
            else -> Color.parseColor("#FAFAFA")
        }
        for (i in cells.indices) {
            tr.addView(
                makeBorderedCell(
                    cells[i],
                    alignRight = (i >= rightAlignFromIndex),
                    bold = (isTotalRow || isSubTotalRow || i == cells.size - 1),
                    bgColor = bg
                )
            )
        }
        tableLayout.addView(tr)
    }

    private fun runReport() {
        val selectedParty = spPartyFilter.selectedItem?.toString() ?: "ALL CUSTOMERS"
        tvRightMeta.text = "DATE: $fromDateStr TO $toDateStr"

        when (reportType) {
            "SAL_CUSTOMER" -> loadGroupedCustomerSalesReport(selectedParty)
            "PUR_SUPPLIER" -> loadPartySummaryReport("purchases", "supplierName", selectedParty, "#1565C0")
            "PUR_DETAILS" -> loadBillDetailsReport("purchases", "supplierName", selectedParty, "#1565C0")
            "PUR_ITEMWISE" -> loadItemwiseReport("purchases", "#1565C0")
            "PUR_STATEMENT" -> loadLedgerStatement("purchases", "payments", "supplierName", selectedParty, "#1565C0")
            "SAL_DETAILS" -> loadBillDetailsReport("sales", "customerName", selectedParty, "#1B5E20")
            "SAL_ITEMWISE" -> loadItemwiseReport("sales", "#1B5E20")
            "SAL_STATEMENT" -> loadLedgerStatement("sales", "receipts", "customerName", selectedParty, "#1B5E20")
        }
    }

    private fun loadGroupedCustomerSalesReport(selectedCust: String) {
        val isAll = selectedCust.equals("ALL CUSTOMERS", ignoreCase = true) || selectedCust.startsWith("All")
        tvLeftMeta.text = "CUSTOMER NAME: $selectedCust"

        val headers = listOf("DATE", "OP.BAL", "QTY", "RATE", "AMOUNT", "INCOME", "DISCOUNT", "CL.BAL")
        addHeaderRow(headers, "#1B5E20")

        db.collection("customers").get().addOnSuccessListener { custSnap ->
            db.collection("sales").get().addOnSuccessListener { salesSnap ->
                db.collection("receipts").get().addOnSuccessListener { recSnap ->

                    val targetCustomers = arrayListOf<Triple<String, String, Double>>()
                    val sortedCustDocs = custSnap.documents.sortedBy { doc ->
                        (doc.getString("name") ?: "").lowercase(Locale.getDefault())
                    }

                    for (doc in sortedCustDocs) {
                        val cName = doc.getString("name")?.trim() ?: continue
                        val baseOp = doc.getDouble("opbal") ?: doc.getDouble("openingBalance") ?: 0.0
                        if (isAll || cName.equals(selectedCust, ignoreCase = true)) {
                            targetCustomers.add(Triple(doc.id, cName, baseOp))
                        }
                    }

                    var grandOpTotal = 0.0
                    var grandQtyTotal = 0.0
                    var grandAmtTotal = 0.0
                    var grandIncTotal = 0.0
                    var grandDiscTotal = 0.0
                    var grandClTotal = 0.0

                    var rowIdx = 0
                    val htmlSections = StringBuilder()
                    val shareSb = StringBuilder("CUSTOMER SALES REPORT ($fromDateStr TO $toDateStr)\nFilter: $selectedCust\n\n")

                    for ((custId, custName, masterOpBal) in targetCustomers) {
                        val custNameLower = custName.lowercase(Locale.getDefault())
                        var effectiveOpBal = masterOpBal

                        val txList = arrayListOf<JSONObject>()

                        for (sDoc in salesSnap) {
                            val sCid = sDoc.getString("customerId") ?: ""
                            val sCname = (sDoc.getString("customerName") ?: "").trim().lowercase(Locale.getDefault())
                            if (sCid != custId && sCname != custNameLower) continue

                            val dStr = sDoc.getString("date") ?: ""
                            val pMode = sDoc.getString("payMode") ?: "Credit"
                            val gTot = sDoc.getDouble("grandTotal") ?: 0.0
                            val ts = sDoc.getLong("timestamp") ?: 0L

                            if (isDateBeforeFrom(dStr)) {
                                if (pMode.equals("Credit", ignoreCase = true)) {
                                    effectiveOpBal += gTot
                                }
                                continue
                            }

                            if (!isDateInRange(dStr)) continue

                            val itemsList = sDoc.get("items") as? List<*>
                            if (itemsList != null && itemsList.isNotEmpty()) {
                                for (k in itemsList.indices) {
                                    val m = itemsList[k] as? Map<*, *> ?: continue
                                    val kgs = (m["netKgs"] as? Number)?.toDouble() ?: (m["kgs"] as? Number)?.toDouble() ?: 0.0
                                    val bgs = (m["bags"] as? Number)?.toDouble() ?: 0.0
                                    val qtyVal = if (kgs > 0.0) kgs else bgs
                                    val rtVal = (m["rate"] as? Number)?.toDouble() ?: 0.0
                                    val lineAmt = ((m["amount"] as? Number)?.toDouble() ?: 0.0) + ((m["bagCharge"] as? Number)?.toDouble() ?: 0.0)
                                    val finalAmt = if (lineAmt > 0.0) lineAmt else gTot

                                    val tx = JSONObject()
                                    tx.put("date", dStr)
                                    tx.put("qty", qtyVal)
                                    tx.put("rate", rtVal)
                                    tx.put("amount", finalAmt)
                                    tx.put("income", if (pMode.equals("Cash", ignoreCase = true)) finalAmt else 0.0)
                                    tx.put("discount", 0.0)
                                    tx.put("ts", ts + k)
                                    txList.add(tx)
                                }
                            } else {
                                val kgs = sDoc.getDouble("netKgs") ?: (sDoc.getLong("bags") ?: 0L).toDouble()
                                val rtVal = if (kgs > 0.0) gTot / kgs else 0.0
                                val tx = JSONObject()
                                tx.put("date", dStr)
                                tx.put("qty", kgs)
                                tx.put("rate", rtVal)
                                tx.put("amount", gTot)
                                tx.put("income", if (pMode.equals("Cash", ignoreCase = true)) gTot else 0.0)
                                tx.put("discount", 0.0)
                                tx.put("ts", ts)
                                txList.add(tx)
                            }
                        }

                        for (rDoc in recSnap) {
                            val rCid = rDoc.getString("customerId") ?: ""
                            val rCname = (rDoc.getString("customerName") ?: "").trim().lowercase(Locale.getDefault())
                            if (rCid != custId && rCname != custNameLower) continue

                            val dStr = rDoc.getString("date") ?: ""
                            val incAmt = rDoc.getDouble("amount") ?: 0.0
                            val discAmt = rDoc.getDouble("discount") ?: 0.0
                            val ts = rDoc.getLong("timestamp") ?: 0L

                            if (isDateBeforeFrom(dStr)) {
                                effectiveOpBal -= (incAmt + discAmt)
                                continue
                            }

                            if (!isDateInRange(dStr)) continue

                            val tx = JSONObject()
                            tx.put("date", dStr)
                            tx.put("qty", 0.0)
                            tx.put("rate", 0.0)
                            tx.put("amount", 0.0)
                            tx.put("income", incAmt)
                            tx.put("discount", discAmt)
                            tx.put("ts", ts)
                            txList.add(tx)
                        }

                        effectiveOpBal = effectiveOpBal.coerceAtLeast(0.0)

                        if (isAll && effectiveOpBal == 0.0 && txList.isEmpty()) {
                            continue
                        }

                        txList.sortBy { obj -> obj.optLong("ts", 0L) }

                        addCustomerSectionBannerRow(custName, 8)
                        val custBodyHtml = StringBuilder()
                        custBodyHtml.append("<tr class='custbanner'><td colspan='8'>NAME: $custName</td></tr>")
                        shareSb.append("=== $custName ===\n")

                        grandOpTotal += effectiveOpBal
                        var runningBal = effectiveOpBal

                        var custQtySum = 0.0
                        var custAmtSum = 0.0
                        var custIncSum = 0.0
                        var custDiscSum = 0.0

                        if (txList.isEmpty()) {
                            grandClTotal += runningBal
                            addDataRow(
                                listOf(fromDateStr, fmtVal(effectiveOpBal), "-", "-", "-", "-", "-", fmtVal(runningBal)),
                                rowIdx++,
                                rightAlignFromIndex = 1
                            )
                            custBodyHtml.append("<tr><td class='center'>$fromDateStr</td><td>${fmtVal(effectiveOpBal)}</td><td>-</td><td>-</td><td>-</td><td>-</td><td>-</td><td class='bold'>${fmtVal(runningBal)}</td></tr>")
                            shareSb.append("$fromDateStr | Op: ${fmtVal(effectiveOpBal)} | Cl: ${fmtVal(runningBal)}\n\n")
                        } else {
                            for (tIdx in txList.indices) {
                                val tx = txList[tIdx]
                                val dStr = tx.optString("date", "")
                                val qty = tx.optDouble("qty", 0.0)
                                val rate = tx.optDouble("rate", 0.0)
                                val amt = tx.optDouble("amount", 0.0)
                                val inc = tx.optDouble("income", 0.0)
                                val disc = tx.optDouble("discount", 0.0)

                                val rowOp = if (tIdx == 0) effectiveOpBal else 0.0
                                runningBal = (runningBal + amt - inc - disc).coerceAtLeast(0.0)

                                custQtySum += qty
                                custAmtSum += amt
                                custIncSum += inc
                                custDiscSum += disc

                                grandQtyTotal += qty
                                grandAmtTotal += amt
                                grandIncTotal += inc
                                grandDiscTotal += disc

                                if (tIdx == txList.size - 1) {
                                    grandClTotal += runningBal
                                }

                                addDataRow(
                                    listOf(dStr, fmtVal(rowOp), fmtQty(qty), fmtVal(rate), fmtVal(amt), fmtVal(inc), fmtVal(disc), fmtVal(runningBal)),
                                    rowIdx++,
                                    rightAlignFromIndex = 1
                                )
                                custBodyHtml.append("<tr><td class='center'>$dStr</td><td>${fmtVal(rowOp)}</td><td>${fmtQty(qty)}</td><td>${fmtVal(rate)}</td><td>${fmtVal(amt)}</td><td>${fmtVal(inc)}</td><td>${fmtVal(disc)}</td><td class='bold'>${fmtVal(runningBal)}</td></tr>")
                                shareSb.append("$dStr | Qty: ${fmtQty(qty)} | Rate: ${fmtVal(rate)} | Amt: ${fmtVal(amt)} | Inc: ${fmtVal(inc)} | Bal: ${fmtVal(runningBal)}\n")
                            }

                            if (isAll) {
                                addDataRow(
                                    listOf("SUB TOTAL", fmtVal(effectiveOpBal), fmtQty(custQtySum), "-", fmtVal(custAmtSum), fmtVal(custIncSum), fmtVal(custDiscSum), fmtVal(runningBal)),
                                    rowIdx++,
                                    rightAlignFromIndex = 1,
                                    isSubTotalRow = true
                                )
                                custBodyHtml.append("<tr class='subtotal'><td class='left'>SUB TOTAL</td><td>${fmtVal(effectiveOpBal)}</td><td>${fmtQty(custQtySum)}</td><td>-</td><td>${fmtVal(custAmtSum)}</td><td>${fmtVal(custIncSum)}</td><td>${fmtVal(custDiscSum)}</td><td>${fmtVal(runningBal)}</td></tr>")
                            }
                            shareSb.append("\n")
                        }

                        htmlSections.append("<tbody>$custBodyHtml</tbody>")
                    }

                    val totalLabel = if (isAll) "GRAND TOTAL" else "TOTAL"
                    addDataRow(
                        listOf(totalLabel, fmtVal(grandOpTotal), fmtQty(grandQtyTotal), "-", fmtVal(grandAmtTotal), fmtVal(grandIncTotal), fmtVal(grandDiscTotal), fmtVal(grandClTotal)),
                        rowIdx,
                        rightAlignFromIndex = 1,
                        isTotalRow = true
                    )
                    htmlSections.append("<tbody><tr class='total'><td class='left'>$totalLabel</td><td>${fmtVal(grandOpTotal)}</td><td>${fmtQty(grandQtyTotal)}</td><td>-</td><td>${fmtVal(grandAmtTotal)}</td><td>${fmtVal(grandIncTotal)}</td><td>${fmtVal(grandDiscTotal)}</td><td>${fmtVal(grandClTotal)}</td></tr></tbody>")

                    tvSummary.text = String.format(
                        Locale.getDefault(),
                        "Sales: Rs. %s  |  Income: Rs. %s  |  Closing Bal: Rs. %s",
                        fmtVal(grandAmtTotal), fmtVal(grandIncTotal), fmtVal(grandClTotal)
                    )
                    shareReportText = shareSb.toString()

                    lastPrintableHtml = """
                        <html>
                        <head>
                        <style>
                            * { box-sizing: border-box; }
                            @page { size: A4 portrait; margin: 5mm; }
                            body { font-family: Arial, sans-serif; color: #000; margin: 0; padding: 0; }
                            .center { text-align: center; }
                            .left { text-align: left !important; }
                            .bold { font-weight: bold; }
                            h1 { font-size: 21px; margin: 0; letter-spacing: 0.8px; }
                            h2 { font-size: 13px; margin: 2px 0 4px 0; }
                            h3 { font-size: 16px; margin: 3px 0 6px 0; text-decoration: underline; }
                            .meta { width: 100%; margin-bottom: 6px; font-size: 13px; font-weight: bold; }
                            .meta td { border: none; padding: 2px 0; }
                            table.grid { width: 100%; table-layout: fixed; border-collapse: collapse; font-size: 12px; }
                            table.grid thead { display: table-header-group; }
                            table.grid tbody { page-break-inside: avoid; }
                            table.grid th, table.grid td { border: 1px solid #000; padding: 5.5px 4px; text-align: right; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
                            table.grid th { background-color: #E0E0E0; text-align: center; font-weight: bold; }
                            tr.custbanner td { text-align: left !important; font-weight: bold; background-color: #E3F2FD; color: #0D47A1; font-size: 13px; padding: 6px; }
                            tr.subtotal td { font-weight: bold; background-color: #FFFDE7; border-top: 1.5px solid #000; }
                            tr.total td { font-weight: bold; background-color: #E8F5E9; border-top: 2px solid #000; border-bottom: 2px solid #000; font-size: 12.5px; }
                        </style>
                        </head>
                        <body>
                            <div class="center">
                                <h1>THIRUVENI COCONUTS</h1>
                                <h2>SHOP NO B98, PERIYAR VEGETABLE MARKET, KOYAMBEDU</h2>
                                <h3>CUSTOMER SALES REPORT</h3>
                            </div>
                            <table class="meta">
                                <tr>
                                    <td style="text-align:left;">CUSTOMER NAME: $selectedCust</td>
                                    <td style="text-align:right;">DATE: $fromDateStr TO $toDateStr</td>
                                </tr>
                            </table>
                            <table class="grid">
                                <colgroup>
                                    <col style="width: 14%;">
                                    <col style="width: 13%;">
                                    <col style="width: 10%;">
                                    <col style="width: 10%;">
                                    <col style="width: 13%;">
                                    <col style="width: 13%;">
                                    <col style="width: 12%;">
                                    <col style="width: 15%;">
                                </colgroup>
                                <thead>
                                    <tr>
                                        <th>DATE</th>
                                        <th>OP.BAL</th>
                                        <th>QTY</th>
                                        <th>RATE</th>
                                        <th>AMOUNT</th>
                                        <th>INCOME</th>
                                        <th>DISCOUNT</th>
                                        <th>CL.BAL</th>
                                    </tr>
                                </thead>
                                $htmlSections
                            </table>
                        </body>
                        </html>
                    """.trimIndent()
                }
            }
        }
    }

    private fun loadPartySummaryReport(col: String, partyField: String, filterParty: String, headerColor: String) {
        tvLeftMeta.text = "SUPPLIER: $filterParty"
        addHeaderRow(listOf("Party Name", "Bills", "Total Amount", "Paid/Rec", "Pending"), headerColor)
        db.collection(col).get().addOnSuccessListener { snap ->
            val countMap = HashMap<String, Int>()
            val totalMap = HashMap<String, Double>()
            val paidMap = HashMap<String, Double>()
            val balMap = HashMap<String, Double>()

            var grandTotal = 0.0
            var grandBal = 0.0

            for (doc in snap) {
                val date = doc.getString("date") ?: ""
                if (!isDateInRange(date)) continue
                val party = doc.getString(partyField) ?: "Unknown"
                if (!filterParty.startsWith("ALL", ignoreCase = true) && !party.equals(filterParty, ignoreCase = true)) continue

                val tot = doc.getDouble("grandTotal") ?: 0.0
                val pMode = doc.getString("payMode") ?: "Credit"
                val paid = if (pMode.equals("Cash", ignoreCase = true)) tot else (doc.getDouble("paidAmount") ?: 0.0)
                val bal = if (pMode.equals("Cash", ignoreCase = true)) 0.0 else (doc.getDouble("balanceAmount") ?: (tot - paid))

                countMap[party] = (countMap[party] ?: 0) + 1
                totalMap[party] = (totalMap[party] ?: 0.0) + tot
                paidMap[party] = (paidMap[party] ?: 0.0) + paid
                balMap[party] = (balMap[party] ?: 0.0) + bal

                grandTotal += tot
                grandBal += bal
            }

            val sb = StringBuilder("${tvTitle.text} ($fromDateStr to $toDateStr)\n\n")
            var idx = 0
            for (p in totalMap.keys.sorted()) {
                val t = totalMap[p] ?: 0.0
                val pd = paidMap[p] ?: 0.0
                val b = balMap[p] ?: 0.0
                val c = countMap[p] ?: 0
                addDataRow(listOf(p, "$c", fmtVal(t), fmtVal(pd), fmtVal(b)), idx++)
                sb.append("$p : Total Rs. ${fmtVal(t)} | Pending Rs. ${fmtVal(b)}\n")
            }

            tvSummary.text = String.format(Locale.getDefault(), "Total: Rs. %s  |  Pending: Rs. %s", fmtVal(grandTotal), fmtVal(grandBal))
            shareReportText = sb.toString()
        }
    }

    private fun loadBillDetailsReport(col: String, partyField: String, filterParty: String, headerColor: String) {
        tvLeftMeta.text = "PARTY: $filterParty"
        addHeaderRow(listOf("Date", "Bill No", "Party Name", "Mode", "Amount", "Pending"), headerColor)
        db.collection(col).get().addOnSuccessListener { snap ->
            var grandTotal = 0.0
            var grandBal = 0.0
            val sb = StringBuilder("${tvTitle.text} ($fromDateStr to $toDateStr)\n\n")
            var idx = 0

            val docs = snap.documents.sortedBy { doc -> doc.getLong("timestamp") ?: 0L }
            for (doc in docs) {
                val date = doc.getString("date") ?: ""
                if (!isDateInRange(date)) continue
                val party = doc.getString(partyField) ?: ""
                if (!filterParty.startsWith("ALL", ignoreCase = true) && !party.equals(filterParty, ignoreCase = true)) continue

                val billNo = doc.getString("billNo") ?: doc.id
                val mode = doc.getString("payMode") ?: "Credit"
                val tot = doc.getDouble("grandTotal") ?: 0.0
                val paid = doc.getDouble("paidAmount") ?: 0.0
                val bal = if (mode.equals("Cash", ignoreCase = true)) 0.0 else (doc.getDouble("balanceAmount") ?: (tot - paid))

                grandTotal += tot
                grandBal += bal

                addDataRow(listOf(date, billNo, party, mode, fmtVal(tot), fmtVal(bal)), idx++, rightAlignFromIndex = 4)
                sb.append("$date | $billNo | $party | Rs. ${fmtVal(tot)}\n")
            }

            tvSummary.text = String.format(Locale.getDefault(), "Bills: %d  |  Total: Rs. %s  |  Pending: Rs. %s", idx, fmtVal(grandTotal), fmtVal(grandBal))
            shareReportText = sb.toString()
        }
    }

    private fun loadItemwiseReport(col: String, headerColor: String) {
        tvLeftMeta.text = "ALL ITEMS"
        addHeaderRow(listOf("Item Name", "Total Bags", "Total Kgs", "Total Amount"), headerColor)
        db.collection(col).get().addOnSuccessListener { snap ->
            val bagsMap = HashMap<String, Int>()
            val kgsMap = HashMap<String, Double>()
            val amtMap = HashMap<String, Double>()
            var grandAmt = 0.0

            for (doc in snap) {
                val date = doc.getString("date") ?: ""
                if (!isDateInRange(date)) continue

                val itemsList = doc.get("items") as? List<*>
                if (itemsList != null) {
                    for (raw in itemsList) {
                        val m = raw as? Map<*, *> ?: continue
                        val iName = m["itemName"]?.toString() ?: "Item"
                        val bags = (m["bags"] as? Number)?.toInt() ?: 0
                        val kgs = (m["netKgs"] as? Number)?.toDouble() ?: (m["kgs"] as? Number)?.toDouble() ?: 0.0
                        val amt = (m["amount"] as? Number)?.toDouble() ?: 0.0

                        bagsMap[iName] = (bagsMap[iName] ?: 0) + bags
                        kgsMap[iName] = (kgsMap[iName] ?: 0.0) + kgs
                        amtMap[iName] = (amtMap[iName] ?: 0.0) + amt
                        grandAmt += amt
                    }
                } else {
                    val iName = doc.getString("itemName") ?: continue
                    val bags = (doc.getLong("bags") ?: 0L).toInt()
                    val kgs = doc.getDouble("netKgs") ?: doc.getDouble("kgs") ?: 0.0
                    val amt = doc.getDouble("grandTotal") ?: 0.0

                    bagsMap[iName] = (bagsMap[iName] ?: 0) + bags
                    kgsMap[iName] = (kgsMap[iName] ?: 0.0) + kgs
                    amtMap[iName] = (amtMap[iName] ?: 0.0) + amt
                    grandAmt += amt
                }
            }

            val sb = StringBuilder("${tvTitle.text} ($fromDateStr to $toDateStr)\n\n")
            var idx = 0
            for (item in amtMap.keys.sorted()) {
                val b = bagsMap[item] ?: 0
                val k = kgsMap[item] ?: 0.0
                val a = amtMap[item] ?: 0.0
                addDataRow(listOf(item, "$b", fmtVal(k), fmtVal(a)), idx++)
                sb.append("$item : $b Bags | ${fmtVal(k)} Kgs | Rs. ${fmtVal(a)}\n")
            }

            tvSummary.text = String.format(Locale.getDefault(), "Total Item Value: Rs. %s", fmtVal(grandAmt))
            shareReportText = sb.toString()
        }
    }

    private fun loadLedgerStatement(billCol: String, voucherCol: String, partyField: String, partyName: String, headerColor: String) {
        tvLeftMeta.text = "PARTY: $partyName"
        addHeaderRow(listOf("Date", "Ref No", "Particulars", "Bill (+)", "Paid/Rec (-)", "Balance"), headerColor)
        if (partyName.isEmpty()) return

        val opBal = partyOpBalMap[partyName] ?: 0.0

        db.collection(billCol).get().addOnSuccessListener { billSnap ->
            db.collection(voucherCol).get().addOnSuccessListener { vouSnap ->
                val entriesList = JSONArray()

                for (doc in billSnap) {
                    val p = doc.getString(partyField) ?: ""
                    if (!p.equals(partyName, ignoreCase = true)) continue
                    val date = doc.getString("date") ?: ""
                    if (!isDateInRange(date)) continue

                    val mode = doc.getString("payMode") ?: "Credit"
                    val tot = doc.getDouble("grandTotal") ?: 0.0
                    val billNo = doc.getString("billNo") ?: doc.id
                    val ts = doc.getLong("timestamp") ?: 0L

                    val obj = JSONObject()
                    obj.put("date", date)
                    obj.put("ref", billNo)
                    obj.put("desc", "Bill ($mode)")
                    obj.put("debit", tot)
                    obj.put("credit", if (mode.equals("Cash", ignoreCase = true)) tot else 0.0)
                    obj.put("ts", ts)
                    entriesList.put(obj)
                }

                for (doc in vouSnap) {
                    val p = doc.getString(partyField) ?: ""
                    if (!p.equals(partyName, ignoreCase = true)) continue
                    val date = doc.getString("date") ?: ""
                    if (!isDateInRange(date)) continue

                    val amt = doc.getDouble("amount") ?: 0.0
                    val disc = doc.getDouble("discount") ?: 0.0
                    val refNo = doc.getString("receiptNo") ?: doc.getString("paymentNo") ?: doc.id
                    val mode = doc.getString("payMode") ?: "Cash"
                    val ts = doc.getLong("timestamp") ?: 0L

                    val obj = JSONObject()
                    obj.put("date", date)
                    obj.put("ref", refNo)
                    obj.put("desc", "Voucher ($mode)")
                    obj.put("debit", 0.0)
                    obj.put("credit", amt + disc)
                    obj.put("ts", ts)
                    entriesList.put(obj)
                }

                val sortedObjects = arrayListOf<JSONObject>()
                for (i in 0 until entriesList.length()) {
                    sortedObjects.add(entriesList.getJSONObject(i))
                }
                sortedObjects.sortBy { obj -> obj.optLong("ts", 0L) }

                var runningBal = opBal
                var idx = 0
                addDataRow(listOf("-", "OP-BAL", "Opening Balance", "-", "-", fmtVal(runningBal)), idx++, rightAlignFromIndex = 3)

                val sb = StringBuilder("STATEMENT: $partyName ($fromDateStr to $toDateStr)\nOpening: Rs. ${fmtVal(opBal)}\n\n")

                for (obj in sortedObjects) {
                    val dr = obj.optDouble("debit", 0.0)
                    val cr = obj.optDouble("credit", 0.0)
                    runningBal = (runningBal + dr - cr).coerceAtLeast(0.0)

                    val dStr = obj.optString("date", "")
                    val rStr = obj.optString("ref", "")
                    val descStr = obj.optString("desc", "")

                    addDataRow(listOf(dStr, rStr, descStr, fmtVal(dr), fmtVal(cr), fmtVal(runningBal)), idx++, rightAlignFromIndex = 3)
                    sb.append("$dStr | $rStr | +${fmtVal(dr)} | -${fmtVal(cr)} | Bal: ${fmtVal(runningBal)}\n")
                }

                tvSummary.text = String.format(Locale.getDefault(), "%s Closing Balance: Rs. %s", partyName, fmtVal(runningBal))
                shareReportText = sb.toString()
            }
        }
    }

    private fun printCurrentReportA4() {
        if (lastPrintableHtml.isEmpty()) {
            Toast.makeText(this, "A4 PDF print is ready for Customer Report", Toast.LENGTH_SHORT).show()
            return
        }
        printWebView = WebView(this)
        printWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = printWebView.createPrintDocumentAdapter("Customer_Sales_Report_${fromDateStr}_to_${toDateStr}")
                val builder = PrintAttributes.Builder()
                builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                builder.setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                printManager.print("Customer Sales Report A4", printAdapter, builder.build())
            }
        }
        printWebView.loadDataWithBaseURL(null, lastPrintableHtml, "text/HTML", "UTF-8", null)
        Toast.makeText(this, "Preparing A4 PDF...", Toast.LENGTH_SHORT).show()
    }
}
