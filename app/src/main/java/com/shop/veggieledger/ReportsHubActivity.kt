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
import com.google.firebase.firestore.DocumentSnapshot
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
    private lateinit var toCol: LinearLayout
    private lateinit var lblFrom: TextView
    private lateinit var spPartyFilter: Spinner
    private lateinit var layoutPartyBox: LinearLayout
    private lateinit var tvPartyLabel: TextView
    private lateinit var tableLayout: TableLayout
    private lateinit var closingCardsContainer: LinearLayout
    private lateinit var hScrollTable: HorizontalScrollView
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

    private fun safeDouble(doc: DocumentSnapshot, field: String): Double {
        val raw = doc.get(field) ?: return 0.0
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.trim().toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun getSaleSplitAmounts(doc: DocumentSnapshot): Triple<Double, Double, Double> {
        val tot = safeDouble(doc, "grandTotal")
        val mode = (doc.getString("payMode") ?: "Credit").trim()
        val hasSplitFields = doc.contains("cash") || doc.contains("gpay") || doc.contains("credit") ||
                doc.contains("cashAmount") || doc.contains("gpayAmount")

        return if (hasSplitFields) {
            val cAmt = if (doc.contains("cash")) safeDouble(doc, "cash") else safeDouble(doc, "cashAmount")
            val gAmt = if (doc.contains("gpay")) safeDouble(doc, "gpay") else safeDouble(doc, "gpayAmount")
            val crAmt = if (doc.contains("credit")) {
                safeDouble(doc, "credit")
            } else if (doc.contains("balanceAmount")) {
                safeDouble(doc, "balanceAmount")
            } else {
                (tot - cAmt - gAmt).coerceAtLeast(0.0)
            }
            Triple(cAmt, gAmt, crAmt)
        } else {
            when {
                mode.equals("Cash", ignoreCase = true) -> Triple(tot, 0.0, 0.0)
                mode.contains("GPay", ignoreCase = true) ||
                        mode.contains("UPI", ignoreCase = true) ||
                        mode.contains("Bank", ignoreCase = true) -> Triple(0.0, tot, 0.0)
                else -> Triple(0.0, 0.0, tot)
            }
        }
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

    private fun ordinalLabel(index: Int): String {
        val n = index + 1
        return when {
            n % 100 in 11..13 -> "${n}th"
            n % 10 == 1 -> "${n}st"
            n % 10 == 2 -> "${n}nd"
            n % 10 == 3 -> "${n}rd"
            else -> "${n}th"
        }
    }

    private fun getReportTitle(type: String): String {
        return when (type) {
            "DAILY_CLOSING" -> "Daily Account Closing (11 Steps)"
            "PUR_WISE_SALES" -> "Purchase-wise Sales Report (Lot Tracking)"
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
        rootScroll.setPadding(14, 14, 14, 14)

        val mainCol = LinearLayout(this)
        mainCol.orientation = LinearLayout.VERTICAL
        rootScroll.addView(mainCol)

        val quickBtnRow = LinearLayout(this)
        quickBtnRow.orientation = LinearLayout.HORIZONTAL
        quickBtnRow.weightSum = 2f
        val qLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        qLp.setMargins(0, 0, 0, 12)
        quickBtnRow.layoutParams = qLp

        val btnDailyClosing = Button(this)
        btnDailyClosing.text = "DAILY CLOSING (11 STEPS)"
        btnDailyClosing.setBackgroundColor(Color.parseColor("#E65100"))
        btnDailyClosing.setTextColor(Color.WHITE)
        btnDailyClosing.setTypeface(null, Typeface.BOLD)
        btnDailyClosing.textSize = 11.5f
        val dcLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        dcLp.setMargins(0, 0, 4, 0)
        btnDailyClosing.layoutParams = dcLp
        btnDailyClosing.setOnClickListener {
            reportType = "DAILY_CLOSING"
            supportActionBar?.title = getReportTitle(reportType)
            tvTitle.text = "DAILY ACCOUNT CLOSING (11 STEPS)"
            layoutPartyBox.visibility = View.GONE
            toCol.visibility = View.GONE
            lblFrom.text = "Closing Date"
            etFromDate.setText(toDateStr)
            fromDateStr = toDateStr
            runReport()
        }

        val btnPurWiseSales = Button(this)
        btnPurWiseSales.text = "PURCHASE-WISE SALES"
        btnPurWiseSales.setBackgroundColor(Color.parseColor("#1565C0"))
        btnPurWiseSales.setTextColor(Color.WHITE)
        btnPurWiseSales.setTypeface(null, Typeface.BOLD)
        btnPurWiseSales.textSize = 11.5f
        val pwsLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        pwsLp.setMargins(4, 0, 0, 0)
        btnPurWiseSales.layoutParams = pwsLp
        btnPurWiseSales.setOnClickListener {
            reportType = "PUR_WISE_SALES"
            supportActionBar?.title = getReportTitle(reportType)
            tvTitle.text = "PURCHASE-WISE SALES REPORT"
            toCol.visibility = View.VISIBLE
            lblFrom.text = "2. From Date"
            setupPartyDropdownIfNeeded()
        }

        quickBtnRow.addView(btnDailyClosing)
        quickBtnRow.addView(btnPurWiseSales)
        mainCol.addView(quickBtnRow)

        val filterCard = LinearLayout(this)
        filterCard.orientation = LinearLayout.VERTICAL
        filterCard.setBackgroundColor(Color.WHITE)
        filterCard.setPadding(16, 16, 16, 16)
        val fcLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        fcLp.setMargins(0, 0, 0, 12)
        filterCard.layoutParams = fcLp

        tvTitle = TextView(this)
        tvTitle.text = getReportTitle(reportType).uppercase(Locale.getDefault())
        tvTitle.textSize = 15.5f
        tvTitle.setTypeface(null, Typeface.BOLD)
        tvTitle.setTextColor(Color.parseColor("#1B5E20"))
        tvTitle.gravity = Gravity.CENTER
        tvTitle.setPadding(0, 0, 0, 10)
        filterCard.addView(tvTitle)

        layoutPartyBox = LinearLayout(this)
        layoutPartyBox.orientation = LinearLayout.VERTICAL
        layoutPartyBox.setPadding(0, 0, 0, 8)
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
        lblFrom = TextView(this)
        lblFrom.text = "2. From Date"
        lblFrom.textSize = 12f
        lblFrom.setTypeface(null, Typeface.BOLD)
        etFromDate = EditText(this)
        etFromDate.isFocusable = false
        etFromDate.isClickable = true
        etFromDate.setBackgroundResource(android.R.drawable.edit_text)
        fromCol.addView(lblFrom)
        fromCol.addView(etFromDate)

        toCol = LinearLayout(this)
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
        tvSummary.setPadding(16, 12, 16, 12)
        val sumLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        sumLp.setMargins(0, 0, 0, 12)
        tvSummary.layoutParams = sumLp
        mainCol.addView(tvSummary)

        closingCardsContainer = LinearLayout(this)
        closingCardsContainer.orientation = LinearLayout.VERTICAL
        closingCardsContainer.visibility = View.GONE
        mainCol.addView(closingCardsContainer)

        val sheetBox = LinearLayout(this)
        sheetBox.orientation = LinearLayout.VERTICAL
        sheetBox.setBackgroundColor(Color.WHITE)
        sheetBox.setPadding(18, 20, 18, 20)

        tvSheetShopName = TextView(this)
        tvSheetShopName.text = "THIRUVENI COCONUTS"
        tvSheetShopName.textSize = 20f
        tvSheetShopName.setTypeface(null, Typeface.BOLD)
        tvSheetShopName.setTextColor(Color.BLACK)
        tvSheetShopName.gravity = Gravity.CENTER
        sheetBox.addView(tvSheetShopName)

        tvSheetShopAddr = TextView(this)
        tvSheetShopAddr.text = "SHOP NO B98, PERIYAR VEGETABLE MARKET, KOYAMBEDU"
        tvSheetShopAddr.textSize = 12.5f
        tvSheetShopAddr.setTypeface(null, Typeface.BOLD)
        tvSheetShopAddr.setTextColor(Color.DKGRAY)
        tvSheetShopAddr.gravity = Gravity.CENTER
        tvSheetShopAddr.setPadding(0, 2, 0, 6)
        sheetBox.addView(tvSheetShopAddr)

        tvSheetSubTitle = TextView(this)
        tvSheetSubTitle.text = getReportTitle(reportType).uppercase(Locale.getDefault())
        tvSheetSubTitle.textSize = 15.5f
        tvSheetSubTitle.setTypeface(null, Typeface.BOLD)
        tvSheetSubTitle.setTextColor(Color.BLACK)
        tvSheetSubTitle.gravity = Gravity.CENTER
        tvSheetSubTitle.setPadding(0, 2, 0, 10)
        sheetBox.addView(tvSheetSubTitle)

        val metaRow = LinearLayout(this)
        metaRow.orientation = LinearLayout.HORIZONTAL
        metaRow.weightSum = 2f
        metaRow.setPadding(4, 4, 4, 8)

        tvLeftMeta = TextView(this)
        tvLeftMeta.text = "CUSTOMER NAME: ALL CUSTOMERS"
        tvLeftMeta.textSize = 12.5f
        tvLeftMeta.setTypeface(null, Typeface.BOLD)
        tvLeftMeta.setTextColor(Color.BLACK)
        tvLeftMeta.gravity = Gravity.START
        tvLeftMeta.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)

        tvRightMeta = TextView(this)
        tvRightMeta.text = "DATE: $fromDateStr TO $toDateStr"
        tvRightMeta.textSize = 12.5f
        tvRightMeta.setTypeface(null, Typeface.BOLD)
        tvRightMeta.setTextColor(Color.BLACK)
        tvRightMeta.gravity = Gravity.END
        tvRightMeta.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)

        metaRow.addView(tvLeftMeta)
        metaRow.addView(tvRightMeta)
        sheetBox.addView(metaRow)

        hScrollTable = HorizontalScrollView(this)
        hScrollTable.setBackgroundColor(Color.WHITE)
        tableLayout = TableLayout(this)
        tableLayout.setBackgroundColor(Color.BLACK)
        tableLayout.setPadding(1, 1, 1, 1)
        hScrollTable.addView(tableLayout)
        sheetBox.addView(hScrollTable)

        mainCol.addView(sheetBox)
        setContentView(rootScroll)
    }

    private fun setupDates() {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val calTo = Calendar.getInstance()
        toDateStr = sdf.format(calTo.time)

        val calFrom = Calendar.getInstance()
        if (reportType == "DAILY_CLOSING") {
            fromDateStr = toDateStr
        } else {
            calFrom.set(Calendar.DAY_OF_MONTH, 1)
            fromDateStr = sdf.format(calFrom.time)
        }

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
        if (reportType == "DAILY_CLOSING") {
            layoutPartyBox.visibility = View.GONE
            toCol.visibility = View.GONE
            lblFrom.text = "Closing Date"
            runReport()
            return
        }

        if (reportType == "PUR_WISE_SALES") {
            layoutPartyBox.visibility = View.VISIBLE
            tvPartyLabel.text = "1. Select Purchase Stock ID / Lot"
            db.collection("purchases").get().addOnSuccessListener { snap ->
                partyNames.clear()
                partyOpBalMap.clear()
                partyIdMap.clear()
                partyNames.add("ALL PURCHASE LOTS")

                val sortedDocs = snap.documents.sortedByDescending { doc ->
                    doc.getLong("timestamp") ?: 0L
                }
                for (doc in sortedDocs) {
                    val bId = (doc.getString("batchId") ?: "").trim()
                    val sup = (doc.getString("supplierName") ?: "Unknown").trim()
                    val dt = (doc.getString("date") ?: "").trim()
                    if (bId.isNotEmpty()) {
                        val label = "$bId | $sup ($dt)"
                        if (!partyNames.contains(label)) {
                            partyNames.add(label)
                            partyIdMap[label] = bId
                        }
                    }
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
            return
        }

        val isPurchase = reportType.startsWith("PUR_")
        val isItemwise = reportType.endsWith("ITEMWISE")
        val isSupplierStatement = (reportType == "PUR_STATEMENT")

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
            if (!isSupplierStatement) {
                partyNames.add(if (isPurchase) "ALL SUPPLIERS" else "ALL CUSTOMERS")
            }

            val sortedDocs = snap.documents.sortedBy { doc ->
                (doc.getString("name") ?: "").lowercase(Locale.getDefault())
            }
            for (doc in sortedDocs) {
                val name = doc.getString("name")?.trim() ?: continue
                val opBal = safeDouble(doc, "opbal").let { if (it != 0.0) it else safeDouble(doc, "openingBalance") }
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

    private fun addCustomerSectionBannerRow(bannerText: String, colSpanCount: Int) {
        val tr = TableRow(this)
        tr.setBackgroundColor(Color.BLACK)
        val tv = TextView(this)
        tv.text = bannerText
        tv.setTextColor(Color.parseColor("#0D47A1"))
        tv.setBackgroundColor(Color.parseColor("#E3F2FD"))
        tv.textSize = 14f
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

        if (reportType == "DAILY_CLOSING") {
            closingCardsContainer.visibility = View.VISIBLE
            (hScrollTable.parent as View).visibility = View.GONE
            loadDailyClosing11Steps(fromDateStr)
            return
        } else {
            closingCardsContainer.visibility = View.GONE
            (hScrollTable.parent as View).visibility = View.VISIBLE
        }

        when (reportType) {
            "PUR_WISE_SALES" -> loadPurchaseWiseSalesReport(selectedParty)
            "SAL_CUSTOMER" -> loadGroupedCustomerSalesReport(selectedParty, "CUSTOMER SALES REPORT")
            "SAL_STATEMENT" -> loadGroupedCustomerSalesReport(selectedParty, "CUSTOMER LEDGER STATEMENT")
            "PUR_SUPPLIER" -> loadPartySummaryReport("purchases", "supplierName", selectedParty, "#1565C0")
            "PUR_DETAILS" -> loadBillDetailsReport("purchases", "supplierName", selectedParty, "#1565C0")
            "PUR_ITEMWISE" -> loadItemwiseReport("purchases", "#1565C0")
            "PUR_STATEMENT" -> loadLedgerStatement("purchases", "payments", "supplierName", selectedParty, "#1565C0")
            "SAL_DETAILS" -> loadBillDetailsReport("sales", "customerName", selectedParty, "#1B5E20")
            "SAL_ITEMWISE" -> loadItemwiseReport("sales", "#1B5E20")
        }
    }

    private fun loadPurchaseWiseSalesReport(selectedLotFilter: String) {
        val isAllLots = selectedLotFilter.equals("ALL PURCHASE LOTS", ignoreCase = true)
        val filterBatchId = if (isAllLots) "" else (partyIdMap[selectedLotFilter] ?: selectedLotFilter.substringBefore("|").trim())

        tvLeftMeta.text = "PURCHASE LOT: ${if (isAllLots) "ALL LOTS" else filterBatchId}"
        tvSheetSubTitle.text = "PURCHASE-WISE SALES REPORT (CUSTOMER DISTRIBUTION)"

        val headers = listOf("SALE DATE", "BILL NO", "CUSTOMER NAME", "BAGS", "NET KGS", "RATE", "AMOUNT", "MODE")
        addHeaderRow(headers, "#1565C0")

        db.collection("purchases").get().addOnSuccessListener { purSnap ->
            db.collection("sales").get().addOnSuccessListener { salSnap ->
                db.collection("stock_batches").get().addOnSuccessListener { stockSnap ->

                    val availKgsByBatch = HashMap<String, Double>()
                    for (stDoc in stockSnap) {
                        val bId = (stDoc.getString("batchId") ?: "").trim().uppercase(Locale.getDefault())
                        if (bId.isNotEmpty()) {
                            availKgsByBatch[bId] = (availKgsByBatch[bId] ?: 0.0) + safeDouble(stDoc, "availableKgs")
                        }
                    }

                    val salesByBatch = HashMap<String, ArrayList<JSONObject>>()
                    val sortedSales = salSnap.documents.sortedBy { it.getLong("timestamp") ?: 0L }

                    for (sDoc in sortedSales) {
                        val sDate = sDoc.getString("date") ?: ""
                        val bNo = sDoc.getString("billNo") ?: sDoc.id
                        val cName = sDoc.getString("customerName") ?: "Unknown Customer"
                        val pMode = sDoc.getString("payMode") ?: "Credit"
                        val docBatchId = (sDoc.getString("batchId") ?: sDoc.getString("stockId") ?: "").trim().uppercase(Locale.getDefault())
                        val docSupName = (sDoc.getString("supplierName") ?: "").trim()

                        val itemsList = sDoc.get("items") as? List<*>
                        if (itemsList != null && itemsList.isNotEmpty()) {
                            for (raw in itemsList) {
                                val m = raw as? Map<*, *> ?: continue
                                val iName = m["itemName"]?.toString() ?: ""
                                val itemBatch = (m["batchId"]?.toString() ?: m["stockId"]?.toString() ?: docBatchId).trim().uppercase(Locale.getDefault())
                                val itemSup = (m["supplierName"]?.toString() ?: docSupName).trim()
                                val bags = (m["bags"] as? Number)?.toInt() ?: 0
                                val kgs = (m["netKgs"] as? Number)?.toDouble() ?: (m["kgs"] as? Number)?.toDouble() ?: 0.0
                                val rate = (m["rate"] as? Number)?.toDouble() ?: 0.0
                                val amt = ((m["amount"] as? Number)?.toDouble() ?: 0.0) +
                                        ((m["bagCharge"] as? Number)?.toDouble() ?: 0.0) +
                                        ((m["weighCoolie"] as? Number)?.toDouble() ?: 0.0)

                                val key = if (itemBatch.isNotEmpty()) itemBatch else "SUP_${itemSup.uppercase(Locale.getDefault())}_${iName.uppercase(Locale.getDefault())}"
                                val list = salesByBatch.getOrPut(key) { arrayListOf() }
                                val obj = JSONObject()
                                obj.put("date", sDate)
                                obj.put("billNo", bNo)
                                obj.put("customerName", cName)
                                obj.put("bags", bags)
                                obj.put("netKgs", kgs)
                                obj.put("rate", rate)
                                obj.put("amount", amt)
                                obj.put("payMode", pMode)
                                list.add(obj)
                            }
                        } else {
                            val iName = sDoc.getString("itemName") ?: ""
                            val bags = (sDoc.getLong("bags") ?: 0L).toInt()
                            val kgs = safeDouble(sDoc, "netKgs")
                            val tot = safeDouble(sDoc, "grandTotal")
                            val rate = if (kgs > 0.0) tot / kgs else 0.0

                            val key = if (docBatchId.isNotEmpty()) docBatchId else "SUP_${docSupName.uppercase(Locale.getDefault())}_${iName.uppercase(Locale.getDefault())}"
                            val list = salesByBatch.getOrPut(key) { arrayListOf() }
                            val obj = JSONObject()
                            obj.put("date", sDate)
                            obj.put("billNo", bNo)
                            obj.put("customerName", cName)
                            obj.put("bags", bags)
                            obj.put("netKgs", kgs)
                            obj.put("rate", rate)
                            obj.put("amount", tot)
                            obj.put("payMode", pMode)
                            list.add(obj)
                        }
                    }

                    val sortedPurchases = purSnap.documents.sortedByDescending { it.getLong("timestamp") ?: 0L }

                    var grandPurKgs = 0.0
                    var grandSoldKgs = 0.0
                    var grandSoldAmt = 0.0
                    var rowIdx = 0

                    val htmlSections = StringBuilder()
                    val shareSb = StringBuilder("PURCHASE-WISE SALES REPORT ($fromDateStr TO $toDateStr)\n\n")

                    for (pDoc in sortedPurchases) {
                        val pDate = pDoc.getString("date") ?: ""
                        val bId = (pDoc.getString("batchId") ?: "").trim().uppercase(Locale.getDefault())
                        val supName = pDoc.getString("supplierName") ?: "Unknown Supplier"
                        val purBillNo = pDoc.getString("billNo") ?: ""

                        if (!isAllLots) {
                            if (!bId.equals(filterBatchId, ignoreCase = true)) continue
                        } else {
                            if (!isDateInRange(pDate)) continue
                        }

                        var purItemName = ""
                        var purTotalBags = 0
                        var purTotalKgs = 0.0
                        var purAvgRate = 0.0

                        val pItems = pDoc.get("items") as? List<*>
                        if (pItems != null && pItems.isNotEmpty()) {
                            for (raw in pItems) {
                                val m = raw as? Map<*, *> ?: continue
                                if (purItemName.isEmpty()) purItemName = m["itemName"]?.toString() ?: ""
                                purTotalBags += (m["bags"] as? Number)?.toInt() ?: 0
                                purTotalKgs += (m["netKgs"] as? Number)?.toDouble() ?: 0.0
                                purAvgRate = (m["ratePerKg"] as? Number)?.toDouble() ?: (m["rate"] as? Number)?.toDouble() ?: 0.0
                            }
                        }

                        val fallbackKey = "SUP_${supName.uppercase(Locale.getDefault())}_${purItemName.uppercase(Locale.getDefault())}"
                        val matchedSales = salesByBatch[bId] ?: salesByBatch[fallbackKey] ?: arrayListOf()

                        var lotSoldBags = 0
                        var lotSoldKgs = 0.0
                        var lotSoldAmt = 0.0
                        for (sObj in matchedSales) {
                            lotSoldBags += sObj.optInt("bags", 0)
                            lotSoldKgs += sObj.optDouble("netKgs", 0.0)
                            lotSoldAmt += sObj.optDouble("amount", 0.0)
                        }

                        val balKgs = availKgsByBatch[bId] ?: (purTotalKgs - lotSoldKgs).coerceAtLeast(0.0)
                        val isCoconut = purItemName.equals("Coconut", ignoreCase = true)

                        val purQtyText = if (isCoconut || purTotalBags <= 0) {
                            "${fmtVal(purTotalKgs)} Kgs"
                        } else {
                            "$purTotalBags Bags / ${fmtVal(purTotalKgs)} Kgs"
                        }

                        val bannerTitle = "LOT: $bId ($pDate) | Supplier: $supName | Item: $purItemName | Pur: $purQtyText @ Rs.${fmtVal(purAvgRate)} | Sold: ${fmtVal(lotSoldKgs)} Kgs | Bal: ${fmtVal(balKgs)} Kgs"
                        addCustomerSectionBannerRow(bannerTitle, 8)

                        val secHtml = StringBuilder()
                        secHtml.append("<tr class='custbanner'><td colspan='8'>$bannerTitle</td></tr>")
                        shareSb.append("=== $bannerTitle ===\n")

                        grandPurKgs += purTotalKgs
                        grandSoldKgs += lotSoldKgs
                        grandSoldAmt += lotSoldAmt

                        if (matchedSales.isEmpty()) {
                            addDataRow(
                                listOf("-", purBillNo, "No sales recorded from this lot yet", "-", "-", "-", "-", "-"),
                                rowIdx++,
                                rightAlignFromIndex = 3
                            )
                            secHtml.append("<tr><td class='center'>-</td><td class='center'>$purBillNo</td><td class='left'>No sales recorded from this lot yet</td><td>-</td><td>-</td><td>-</td><td>-</td><td>-</td></tr>")
                            shareSb.append("No sales recorded yet.\n\n")
                        } else {
                            for (sObj in matchedSales) {
                                val sDate = sObj.optString("date", "")
                                val sBill = sObj.optString("billNo", "")
                                val cName = sObj.optString("customerName", "")
                                val sBags = sObj.optInt("bags", 0)
                                val sKgs = sObj.optDouble("netKgs", 0.0)
                                val sRate = sObj.optDouble("rate", 0.0)
                                val sAmt = sObj.optDouble("amount", 0.0)
                                val sMode = sObj.optString("payMode", "")

                                val bagStr = if (isCoconut || sBags <= 0) "-" else "$sBags"

                                addDataRow(
                                    listOf(sDate, sBill, cName, bagStr, fmtVal(sKgs), fmtVal(sRate), fmtVal(sAmt), sMode),
                                    rowIdx++,
                                    rightAlignFromIndex = 3
                                )
                                secHtml.append("<tr><td class='center'>$sDate</td><td class='center'>$sBill</td><td class='left'>$cName</td><td>$bagStr</td><td>${fmtVal(sKgs)}</td><td>${fmtVal(sRate)}</td><td class='bold'>${fmtVal(sAmt)}</td><td class='center'>$sMode</td></tr>")
                                shareSb.append("$sDate | $sBill | $cName | ${fmtVal(sKgs)} Kgs @ ${fmtVal(sRate)} = Rs. ${fmtVal(sAmt)} ($sMode)\n")
                            }

                            val subBagStr = if (isCoconut || lotSoldBags <= 0) "-" else "$lotSoldBags"
                            addDataRow(
                                listOf("LOT TOTAL", "-", "Customers: ${matchedSales.size}", subBagStr, fmtVal(lotSoldKgs), "-", fmtVal(lotSoldAmt), "Bal: ${fmtVal(balKgs)} Kg"),
                                rowIdx++,
                                rightAlignFromIndex = 3,
                                isSubTotalRow = true
                            )
                            secHtml.append("<tr class='subtotal'><td class='left'>LOT TOTAL</td><td>-</td><td class='left'>Customers: ${matchedSales.size}</td><td>$subBagStr</td><td>${fmtVal(lotSoldKgs)}</td><td>-</td><td>${fmtVal(lotSoldAmt)}</td><td class='center'>Bal: ${fmtVal(balKgs)}</td></tr>")
                            shareSb.append("LOT SOLD: ${fmtVal(lotSoldKgs)} Kgs | Rs. ${fmtVal(lotSoldAmt)} | BAL STOCK: ${fmtVal(balKgs)} Kgs\n\n")
                        }

                        htmlSections.append("<tbody>$secHtml</tbody>")
                    }

                    addDataRow(
                        listOf("GRAND TOTAL", "-", "Pur: ${fmtVal(grandPurKgs)} Kg", "-", fmtVal(grandSoldKgs), "-", fmtVal(grandSoldAmt), "-"),
                        rowIdx,
                        rightAlignFromIndex = 3,
                        isTotalRow = true
                    )
                    htmlSections.append("<tbody><tr class='total'><td class='left'>GRAND TOTAL</td><td>-</td><td class='left'>Pur: ${fmtVal(grandPurKgs)} Kg</td><td>-</td><td>${fmtVal(grandSoldKgs)}</td><td>-</td><td>${fmtVal(grandSoldAmt)}</td><td>-</td></tr></tbody>")

                    tvSummary.text = String.format(
                        Locale.getDefault(),
                        "Purchased: %s Kgs  |  Sold: %s Kgs  |  Sales Value: Rs. %s",
                        fmtVal(grandPurKgs), fmtVal(grandSoldKgs), fmtVal(grandSoldAmt)
                    )
                    shareReportText = shareSb.toString()

                    lastPrintableHtml = """
                        <html>
                        <head>
                        <style>
                            * { box-sizing: border-box; }
                            @page { size: A4 portrait; margin: 5mm; }
                            body { font-family: Arial, sans-serif; color: #000; margin: 0; padding: 0; }
                            .center { text-align: center !important; }
                            .left { text-align: left !important; }
                            .bold { font-weight: bold; }
                            h1 { font-size: 20px; margin: 0; }
                            h2 { font-size: 12px; margin: 2px 0 4px 0; }
                            h3 { font-size: 15px; margin: 3px 0 6px 0; text-decoration: underline; }
                            .meta { width: 100%; margin-bottom: 6px; font-size: 12.5px; font-weight: bold; }
                            .meta td { border: none; padding: 2px 0; }
                            table.grid { width: 100%; border-collapse: collapse; font-size: 11.5px; }
                            table.grid thead { display: table-header-group; }
                            table.grid tbody { page-break-inside: avoid; }
                            table.grid th, table.grid td { border: 1px solid #000; padding: 5px 4px; text-align: right; }
                            table.grid th { background-color: #E0E0E0; text-align: center; font-weight: bold; }
                            tr.custbanner td { text-align: left !important; font-weight: bold; background-color: #E3F2FD; color: #0D47A1; font-size: 12px; padding: 6px; }
                            tr.subtotal td { font-weight: bold; background-color: #FFFDE7; border-top: 1.5px solid #000; }
                            tr.total td { font-weight: bold; background-color: #E8F5E9; border-top: 2px solid #000; border-bottom: 2px solid #000; font-size: 12px; }
                        </style>
                        </head>
                        <body>
                            <div class="center">
                                <h1>THIRUVENI COCONUTS</h1>
                                <h2>SHOP NO B98, PERIYAR VEGETABLE MARKET, KOYAMBEDU</h2>
                                <h3>PURCHASE-WISE SALES REPORT</h3>
                            </div>
                            <table class="meta">
                                <tr>
                                    <td style="text-align:left;">LOT FILTER: ${if (isAllLots) "ALL PURCHASE LOTS" else filterBatchId}</td>
                                    <td style="text-align:right;">DATE: $fromDateStr TO $toDateStr</td>
                                </tr>
                            </table>
                            <table class="grid">
                                <thead>
                                    <tr>
                                        <th>SALE DATE</th>
                                        <th>BILL NO</th>
                                        <th>CUSTOMER NAME</th>
                                        <th>BAGS</th>
                                        <th>NET KGS</th>
                                        <th>RATE</th>
                                        <th>AMOUNT</th>
                                        <th>MODE</th>
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

    private fun addClosingTwoColCard(title: String, rows: List<Pair<String, String>>, highlightLast: Boolean = false) {
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
            val isLast = (i == rows.size - 1) && highlightLast
            val isSubHeader = rows[i].second == "SECTION_HEADER"
            val bg = when {
                isSubHeader -> Color.parseColor("#E3F2FD")
                isLast -> Color.parseColor("#FFF9C4")
                i % 2 == 0 -> Color.WHITE
                else -> Color.parseColor("#F9FBE7")
            }

            val tr = TableRow(this)
            tr.setBackgroundColor(Color.BLACK)

            if (isSubHeader) {
                val tvHdr = TextView(this)
                tvHdr.text = rows[i].first
                tvHdr.setTextColor(Color.parseColor("#0D47A1"))
                tvHdr.setBackgroundColor(bg)
                tvHdr.textSize = 13.5f
                tvHdr.setTypeface(null, Typeface.BOLD)
                tvHdr.setPadding(12, 10, 12, 10)
                val lpSpan = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
                lpSpan.span = 2
                lpSpan.setMargins(1, 1, 1, 1)
                tvHdr.layoutParams = lpSpan
                tr.addView(tvHdr)
            } else {
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
            }
            tbl.addView(tr)
        }

        card.addView(tbl)
        closingCardsContainer.addView(card)
    }

    private fun addClosingThreeColCreditRec(cashAmt: Double, gpayAmt: Double) {
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

        val hdr = TableRow(this)
        hdr.setBackgroundColor(Color.BLACK)
        hdr.addView(makeGridCell("PARTICULARS", false, true, Color.parseColor("#E0E0E0"), 1.2f))
        hdr.addView(makeGridCell("CASH", true, true, Color.parseColor("#E0E0E0"), 0.9f))
        hdr.addView(makeGridCell("GPAY", true, true, Color.parseColor("#E0E0E0"), 0.9f))
        tbl.addView(hdr)

        val valRow = TableRow(this)
        valRow.setBackgroundColor(Color.BLACK)
        valRow.addView(makeGridCell("Credit Bill Received", false, true, Color.parseColor("#FFF9C4"), 1.2f))
        valRow.addView(makeGridCell(fmtVal(cashAmt), true, true, Color.parseColor("#FFF9C4"), 0.9f))
        valRow.addView(makeGridCell(fmtVal(gpayAmt), true, true, Color.parseColor("#FFF9C4"), 0.9f))
        tbl.addView(valRow)

        card.addView(tbl)
        closingCardsContainer.addView(card)
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

    private fun loadDailyClosing11Steps(targetDateStr: String) {
        closingCardsContainer.removeAllViews()
        tvSummary.text = "Calculating 11-Step Closing Report for $targetDateStr..."
        val targetYmd = dateToYmd(targetDateStr)

        db.collection("purchases").get().addOnSuccessListener { purSnap ->
            db.collection("suppliers").get().addOnSuccessListener { supSnap ->
                db.collection("payments").get().addOnSuccessListener { paySnap ->
                    db.collection("sales").get().addOnSuccessListener { salSnap ->
                        db.collection("receipts").get().addOnSuccessListener { recSnap ->
                            db.collection("expenses").get().addOnSuccessListener { expSnap ->
                                db.collection("customers").get().addOnSuccessListener { custSnap ->
                                    db.collection("stock_batches").get().addOnSuccessListener { stockSnap ->

                                        val purchaseLineRows = arrayListOf<Pair<String, String>>()
                                        val purHtmlRows = StringBuilder()

                                        var totalPurAll = 0.0
                                        var todayPurchaseTot = 0.0
                                        var purCounter = 0

                                        val sortedPurDocs = purSnap.documents.sortedBy { it.getLong("timestamp") ?: 0L }
                                        for (doc in sortedPurDocs) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            val tot = safeDouble(doc, "grandTotal")
                                            if (dYmd in 1..targetYmd) totalPurAll += tot
                                            if (dYmd != targetYmd) continue

                                            val supName = doc.getString("supplierName") ?: "Unknown Supplier"
                                            val itemsList = doc.get("items") as? List<*>

                                            if (itemsList != null && itemsList.isNotEmpty()) {
                                                for (rawItem in itemsList) {
                                                    val m = rawItem as? Map<*, *> ?: continue
                                                    val nos = (m["nos"] as? Number)?.toDouble()
                                                        ?: (m["bags"] as? Number)?.toDouble()
                                                        ?: (m["qtyNos"] as? Number)?.toDouble()
                                                        ?: 0.0
                                                    val totalKgs = (m["purchaseKgs"] as? Number)?.toDouble()
                                                        ?: (m["grossKgs"] as? Number)?.toDouble()
                                                        ?: (m["totalKgs"] as? Number)?.toDouble()
                                                        ?: (m["kgs"] as? Number)?.toDouble()
                                                        ?: (m["netKgs"] as? Number)?.toDouble()
                                                        ?: 0.0
                                                    val kudumi = (m["kudumi"] as? Number)?.toDouble()
                                                        ?: (m["kudumiKgs"] as? Number)?.toDouble()
                                                        ?: 0.0
                                                    val thadukki = (m["thadukki"] as? Number)?.toDouble() ?: 0.0
                                                    val netKgs = (m["netKgs"] as? Number)?.toDouble()
                                                        ?: (totalKgs - kudumi - thadukki).coerceAtLeast(0.0)
                                                    val rate = (m["ratePerKg"] as? Number)?.toDouble()
                                                        ?: (m["rate"] as? Number)?.toDouble()
                                                        ?: 0.0
                                                    val lineAmt = (m["lineTotal"] as? Number)?.toDouble()
                                                        ?: (m["amount"] as? Number)?.toDouble()
                                                        ?: (netKgs * rate)

                                                    val label = "${ordinalLabel(purCounter)} Purchase - $supName"
                                                    purchaseLineRows.add(label to "SECTION_HEADER")
                                                    purchaseLineRows.add("Today Purchase Nos & Kgs" to "${fmtQty(nos)} Nos  |  ${fmtVal(totalKgs)} Kgs")
                                                    purchaseLineRows.add("Kudumi + Thadukki" to "${fmtVal(kudumi)} + ${fmtVal(thadukki)} = ${fmtVal(kudumi + thadukki)} Kgs")
                                                    purchaseLineRows.add("Net Purchase Kgs" to "${fmtVal(netKgs)} Kgs")
                                                    purchaseLineRows.add("Net Kgs x Rate = Amount (${fmtVal(netKgs)} x ${fmtVal(rate)})" to fmtVal(lineAmt))

                                                    purHtmlRows.append("<tr class='subhdr'><td colspan='2'>$label</td></tr>")
                                                    purHtmlRows.append("<tr><td>Today Purchase Nos &amp; Kgs</td><td class='r'>${fmtQty(nos)} Nos | ${fmtVal(totalKgs)} Kgs</td></tr>")
                                                    purHtmlRows.append("<tr><td>Kudumi + Thadukki</td><td class='r'>${fmtVal(kudumi)} + ${fmtVal(thadukki)} = ${fmtVal(kudumi + thadukki)} Kgs</td></tr>")
                                                    purHtmlRows.append("<tr><td>Net Purchase Kgs</td><td class='r'>${fmtVal(netKgs)} Kgs</td></tr>")
                                                    purHtmlRows.append("<tr><td>Net Kgs x Rate = Amount (${fmtVal(netKgs)} x ${fmtVal(rate)})</td><td class='r'>${fmtVal(lineAmt)}</td></tr>")

                                                    todayPurchaseTot += lineAmt
                                                    purCounter++
                                                }
                                            }
                                        }

                                        if (purchaseLineRows.isEmpty()) {
                                            purchaseLineRows.add("Today Purchase" to "No purchase entries")
                                            purHtmlRows.append("<tr><td>Today Purchase</td><td class='r'>-</td></tr>")
                                        }
                                        purchaseLineRows.add("Today Total Purchase Amount" to fmtVal(todayPurchaseTot))
                                        purHtmlRows.append("<tr class='hi'><td>Today Total Purchase Amount</td><td class='r'>${fmtVal(todayPurchaseTot)}</td></tr>")

                                        var supMasterOp = 0.0
                                        for (doc in supSnap) {
                                            supMasterOp += safeDouble(doc, "opbal").let { if (it != 0.0) it else safeDouble(doc, "openingBalance") }
                                        }
                                        var totalSupPaid = 0.0
                                        for (doc in paySnap) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            if (dYmd in 1..targetYmd) {
                                                totalSupPaid += safeDouble(doc, "amount") + safeDouble(doc, "discount")
                                            }
                                        }
                                        for (doc in purSnap) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            if (dYmd in 1..targetYmd && (doc.getString("payMode") ?: "").equals("Cash", ignoreCase = true)) {
                                                totalSupPaid += safeDouble(doc, "grandTotal")
                                            }
                                        }
                                        val supplierClosingOut = (supMasterOp + totalPurAll - totalSupPaid).coerceAtLeast(0.0)

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
                                                val amt = safeDouble(doc, "amount")
                                                val disc = safeDouble(doc, "discount")
                                                todayCustDiscount += disc
                                                if (mode.equals("Cash", ignoreCase = true)) creditRecCash += amt
                                                else creditRecGpay += amt
                                            }
                                        }

                                        var cashSales = 0.0
                                        var gpaySales = 0.0
                                        var creditSales = 0.0
                                        for (doc in salSnap) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            if (dYmd == targetYmd) {
                                                val (cAmt, gAmt, crAmt) = getSaleSplitAmounts(doc)
                                                cashSales += cAmt
                                                gpaySales += gAmt
                                                creditSales += crAmt
                                            }
                                        }
                                        val todaySalesTotal = cashSales + gpaySales + creditSales
                                        val cashReceivedTotal = cashSales + creditRecCash

                                        var otherExp = 0.0
                                        var shopExp = 0.0
                                        var vehicleExp = 0.0
                                        var otherCashIncome = 0.0

                                        for (doc in expSnap) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            if (dYmd != targetYmd) continue
                                            val type = doc.getString("type") ?: "Expense"
                                            val cat = (doc.getString("category") ?: "Other Expenses").trim()
                                            val amt = safeDouble(doc, "amount")
                                            val mode = (doc.getString("payMode") ?: "Cash").trim()

                                            if (type.equals("Income", ignoreCase = true)) {
                                                if (!mode.equals("Bank", ignoreCase = true)) otherCashIncome += amt
                                            } else {
                                                when {
                                                    cat.equals("Shop Expenses", ignoreCase = true) -> shopExp += amt
                                                    cat.equals("Vehicle Expenses", ignoreCase = true) -> vehicleExp += amt
                                                    else -> otherExp += amt
                                                }
                                            }
                                        }
                                        val totalExpenses = otherExp + shopExp + vehicleExp
                                        val collectionTotal = cashSales + creditRecCash + otherCashIncome
                                        val todayClosingBalance = collectionTotal - totalExpenses

                                        val stockMap = HashMap<String, Pair<Int, Double>>()
                                        for (doc in stockSnap) {
                                            val iName = doc.getString("itemName") ?: continue
                                            val bgs = (doc.getLong("availableBags") ?: 0L).toInt()
                                            val kgs = safeDouble(doc, "availableKgs")
                                            val curr = stockMap[iName] ?: Pair(0, 0.0)
                                            stockMap[iName] = Pair(curr.first + bgs, curr.second + kgs)
                                        }

                                        val incomeDetailsTotal = cashSales + gpaySales + creditRecCash + creditRecGpay

                                        var custMasterOp = 0.0
                                        for (doc in custSnap) {
                                            custMasterOp += safeDouble(doc, "opbal").let { if (it != 0.0) it else safeDouble(doc, "openingBalance") }
                                        }
                                        var prevSalesAll = 0.0
                                        var prevIncAll = 0.0
                                        var prevDiscAll = 0.0

                                        for (doc in salSnap) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            val tot = safeDouble(doc, "grandTotal")
                                            val (cAmt, gAmt, _) = getSaleSplitAmounts(doc)
                                            val directPaid = cAmt + gAmt
                                            val bNo = (doc.getString("billNo") ?: "").trim().lowercase(Locale.getDefault())
                                            val linked = (bNo.isNotEmpty() && receiptLinkedBills.contains(bNo)) || receiptLinkedBills.contains(doc.id.lowercase(Locale.getDefault()))
                                            val unlinkedDirect = if (!linked) directPaid else 0.0

                                            if (dYmd in 1 until targetYmd) {
                                                prevSalesAll += tot
                                                prevIncAll += unlinkedDirect
                                            }
                                        }
                                        for (doc in recSnap) {
                                            val dYmd = dateToYmd(doc.getString("date") ?: "")
                                            if (dYmd in 1 until targetYmd) {
                                                prevIncAll += safeDouble(doc, "amount")
                                                prevDiscAll += safeDouble(doc, "discount")
                                            }
                                        }
                                        val vertOpOut = (custMasterOp + prevSalesAll - prevIncAll - prevDiscAll).coerceAtLeast(0.0)
                                        val vertClosingOut = (vertOpOut + todaySalesTotal - incomeDetailsTotal - todayCustDiscount).coerceAtLeast(0.0)

                                        val netProfitLoss = todaySalesTotal - todayPurchaseTot - totalExpenses - todayCustDiscount

                                        tvSummary.text = String.format(
                                            Locale.getDefault(),
                                            "Collection: Rs. %s  |  Expenses: Rs. %s  |  Closing Bal: Rs. %s",
                                            fmtVal(collectionTotal), fmtVal(totalExpenses), fmtVal(todayClosingBalance)
                                        )

                                        closingCardsContainer.removeAllViews()

                                        addClosingTwoColCard("1. PURCHASE DETAILS (LINE BY LINE)", purchaseLineRows, highlightLast = true)
                                        addClosingTwoColCard("2. SUPPLIER OUTSTANDING", listOf("Supplier Closing Outstanding" to fmtVal(supplierClosingOut)), highlightLast = true)
                                        addClosingThreeColCreditRec(creditRecCash, creditRecGpay)
                                        addClosingTwoColCard(
                                            "4. TODAY SALES & 5. CASH RECEIVED",
                                            listOf(
                                                "Cash Sales" to fmtVal(cashSales),
                                                "GPay Sales" to fmtVal(gpaySales),
                                                "Credit Sales" to fmtVal(creditSales),
                                                "4. Today Total Sales" to fmtVal(todaySalesTotal),
                                                "5. Cash Received (Cash Sales + Credit Bill via Cash)" to fmtVal(cashReceivedTotal)
                                            ),
                                            highlightLast = true
                                        )
                                        addClosingTwoColCard(
                                            "6. EXPENSES & 7. COLLECTION CLOSING BALANCE",
                                            listOf(
                                                "6A. Other Expenses" to fmtVal(otherExp),
                                                "6B. Shop Expenses" to fmtVal(shopExp),
                                                "6C. Vehicle Expenses" to fmtVal(vehicleExp),
                                                "Total Expenses" to fmtVal(totalExpenses),
                                                "7. Collection Total (Cash Sales + Credit Cash + Other Inc)" to fmtVal(collectionTotal),
                                                "TODAY CLOSING BALANCE (Collection - Expenses)" to fmtVal(todayClosingBalance)
                                            ),
                                            highlightLast = true
                                        )

                                        val stockRows = arrayListOf<Pair<String, String>>()
                                        for ((item, pair) in stockMap.toSortedMap()) {
                                            if (item.equals("Coconut", ignoreCase = true)) {
                                                stockRows.add(item to "${fmtVal(pair.second)} Kgs")
                                            } else {
                                                stockRows.add(item to "${pair.first} Bags  |  ${fmtVal(pair.second)} Kgs")
                                            }
                                        }
                                        if (stockRows.isEmpty()) stockRows.add("Stock" to "No active batches")
                                        addClosingTwoColCard("8. STOCK INQUIRY", stockRows)

                                        addClosingTwoColCard(
                                            "9. INCOME DETAILS",
                                            listOf(
                                                "Cash Sales" to fmtVal(cashSales),
                                                "GPay Sales" to fmtVal(gpaySales),
                                                "Credit Bill Received via Cash" to fmtVal(creditRecCash),
                                                "Credit Bill Received via GPay" to fmtVal(creditRecGpay),
                                                "Total Income Details" to fmtVal(incomeDetailsTotal)
                                            ),
                                            highlightLast = true
                                        )
                                        addClosingTwoColCard(
                                            "10. CUSTOMER OUTSTANDING (VERTICAL)",
                                            listOf(
                                                "Opening Outstanding" to fmtVal(vertOpOut),
                                                "(+) Today Sales" to fmtVal(todaySalesTotal),
                                                "(-) Today Income" to fmtVal(incomeDetailsTotal),
                                                "(-) Today Discount" to fmtVal(todayCustDiscount),
                                                "Closing Outstanding" to fmtVal(vertClosingOut)
                                            ),
                                            highlightLast = true
                                        )
                                        addClosingTwoColCard(
                                            "11. PROFIT AND LOSS",
                                            listOf(
                                                "Today Sales" to fmtVal(todaySalesTotal),
                                                "(-) Today Purchase" to fmtVal(todayPurchaseTot),
                                                "(-) Expenses & Discount" to fmtVal(totalExpenses + todayCustDiscount),
                                                "Net Profit / (Loss)" to fmtVal(netProfitLoss)
                                            ),
                                            highlightLast = true
                                        )

                                        val stockHtml = StringBuilder()
                                        for (s in stockRows) stockHtml.append("<tr><td>${s.first}</td><td class='r'>${s.second}</td></tr>")

                                        lastPrintableHtml = """
                                            <html>
                                            <head>
                                            <style>
                                                * { box-sizing: border-box; }
                                                @page { size: A4 portrait; margin: 4mm; }
                                                body { font-family: Arial, sans-serif; color: #000; margin: 0; padding: 0; font-size: 10.5px; }
                                                .center { text-align: center; }
                                                h1 { font-size: 18px; margin: 0; }
                                                h2 { font-size: 11.5px; margin: 1px 0 3px 0; }
                                                h3 { font-size: 13.5px; margin: 2px 0 5px 0; text-decoration: underline; }
                                                .cols { display: flex; justify-content: space-between; gap: 8px; }
                                                .col { width: 49.5%; }
                                                table { width: 100%; border-collapse: collapse; margin-bottom: 6px; }
                                                th, td { border: 1px solid #000; padding: 3px 5px; }
                                                th { background: #E0E0E0; text-align: left; font-size: 10.5px; }
                                                td.r { text-align: right; font-weight: bold; }
                                                tr.subhdr td { background: #E3F2FD; color: #0D47A1; font-weight: bold; }
                                                tr.hi td { background: #FFF9C4; font-weight: bold; }
                                            </style>
                                            </head>
                                            <body>
                                                <div class="center">
                                                    <h1>THIRUVENI COCONUTS</h1>
                                                    <h2>SHOP NO B98, PERIYAR VEGETABLE MARKET, KOYAMBEDU</h2>
                                                    <h3>DAILY ACCOUNT CLOSING REPORT  |  DATE: $targetDateStr</h3>
                                                </div>
                                                <div class="cols">
                                                    <div class="col">
                                                        <table>
                                                            <tr><th colspan="2">1. PURCHASE DETAILS (LINE BY LINE)</th></tr>
                                                            $purHtmlRows
                                                        </table>
                                                        <table>
                                                            <tr><th colspan="2">2. SUPPLIER OUTSTANDING</th></tr>
                                                            <tr class="hi"><td>Supplier Closing Outstanding</td><td class="r">${fmtVal(supplierClosingOut)}</td></tr>
                                                        </table>
                                                        <table>
                                                            <tr><th colspan="3">3. CREDIT BILL RECEIVED</th></tr>
                                                            <tr><th>Particulars</th><th style="text-align:right;">CASH</th><th style="text-align:right;">GPAY</th></tr>
                                                            <tr class="hi"><td>Credit Bill Received</td><td class="r">${fmtVal(creditRecCash)}</td><td class="r">${fmtVal(creditRecGpay)}</td></tr>
                                                        </table>
                                                        <table>
                                                            <tr><th colspan="2">4. TODAY SALES &amp; 5. CASH RECEIVED</th></tr>
                                                            <tr><td>Cash Sales</td><td class="r">${fmtVal(cashSales)}</td></tr>
                                                            <tr><td>GPay Sales</td><td class="r">${fmtVal(gpaySales)}</td></tr>
                                                            <tr><td>Credit Sales</td><td class="r">${fmtVal(creditSales)}</td></tr>
                                                            <tr class="hi"><td>4. Today Total Sales</td><td class="r">${fmtVal(todaySalesTotal)}</td></tr>
                                                            <tr class="hi"><td>5. Cash Received (Cash Sales + Credit Cash)</td><td class="r">${fmtVal(cashReceivedTotal)}</td></tr>
                                                        </table>
                                                    </div>
                                                    <div class="col">
                                                        <table>
                                                            <tr><th colspan="2">6. EXPENSES &amp; 7. COLLECTION CLOSING</th></tr>
                                                            <tr><td>6A. Other Expenses</td><td class="r">${fmtVal(otherExp)}</td></tr>
                                                            <tr><td>6B. Shop Expenses</td><td class="r">${fmtVal(shopExp)}</td></tr>
                                                            <tr><td>6C. Vehicle Expenses</td><td class="r">${fmtVal(vehicleExp)}</td></tr>
                                                            <tr class="hi"><td>Total Expenses</td><td class="r">${fmtVal(totalExpenses)}</td></tr>
                                                            <tr><td>7. Collection Total</td><td class="r">${fmtVal(collectionTotal)}</td></tr>
                                                            <tr class="hi"><td>TODAY CLOSING BALANCE</td><td class="r">${fmtVal(todayClosingBalance)}</td></tr>
                                                        </table>
                                                        <table>
                                                            <tr><th colspan="2">8. STOCK INQUIRY</th></tr>
                                                            $stockHtml
                                                        </table>
                                                        <table>
                                                            <tr><th colspan="2">9. INCOME DETAILS</th></tr>
                                                            <tr><td>Cash Sales</td><td class="r">${fmtVal(cashSales)}</td></tr>
                                                            <tr><td>GPay Sales</td><td class="r">${fmtVal(gpaySales)}</td></tr>
                                                            <tr><td>Credit Bill Received via Cash</td><td class="r">${fmtVal(creditRecCash)}</td></tr>
                                                            <tr><td>Credit Bill Received via GPay</td><td class="r">${fmtVal(creditRecGpay)}</td></tr>
                                                            <tr class="hi"><td>Total Income Details</td><td class="r">${fmtVal(incomeDetailsTotal)}</td></tr>
                                                        </table>
                                                        <table>
                                                            <tr><th colspan="2">10. CUSTOMER OUTSTANDING (VERTICAL)</th></tr>
                                                            <tr><td>Opening Outstanding</td><td class="r">${fmtVal(vertOpOut)}</td></tr>
                                                            <tr><td>(+) Today Sales</td><td class="r">${fmtVal(todaySalesTotal)}</td></tr>
                                                            <tr><td>(-) Today Income</td><td class="r">${fmtVal(incomeDetailsTotal)}</td></tr>
                                                            <tr><td>(-) Today Discount</td><td class="r">${fmtVal(todayCustDiscount)}</td></tr>
                                                            <tr class="hi"><td>Closing Outstanding</td><td class="r">${fmtVal(vertClosingOut)}</td></tr>
                                                        </table>
                                                        <table>
                                                            <tr><th colspan="2">11. PROFIT AND LOSS</th></tr>
                                                            <tr><td>Today Sales</td><td class="r">${fmtVal(todaySalesTotal)}</td></tr>
                                                            <tr><td>(-) Today Purchase</td><td class="r">${fmtVal(todayPurchaseTot)}</td></tr>
                                                            <tr><td>(-) Expenses &amp; Discount</td><td class="r">${fmtVal(totalExpenses + todayCustDiscount)}</td></tr>
                                                            <tr class="hi"><td>Net Profit / (Loss)</td><td class="r">${fmtVal(netProfitLoss)}</td></tr>
                                                        </table>
                                                    </div>
                                                </div>
                                            </body>
                                            </html>
                                        """.trimIndent()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadGroupedCustomerSalesReport(selectedCust: String, reportHeading: String) {
        val isAll = selectedCust.equals("ALL CUSTOMERS", ignoreCase = true) || selectedCust.startsWith("All")
        tvLeftMeta.text = "CUSTOMER NAME: $selectedCust"
        tvSheetSubTitle.text = reportHeading

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
                        val baseOp = safeDouble(doc, "opbal").let { if (it != 0.0) it else safeDouble(doc, "openingBalance") }
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
                    val shareSb = StringBuilder("$reportHeading ($fromDateStr TO $toDateStr)\nFilter: $selectedCust\n\n")

                    for ((custId, custName, masterOpBal) in targetCustomers) {
                        val custNameLower = custName.lowercase(Locale.getDefault())
                        var effectiveOpBal = masterOpBal

                        val txList = arrayListOf<JSONObject>()

                        for (sDoc in salesSnap) {
                            val sCid = sDoc.getString("customerId") ?: ""
                            val sCname = (sDoc.getString("customerName") ?: "").trim().lowercase(Locale.getDefault())
                            if (sCid != custId && sCname != custNameLower) continue

                            val dStr = sDoc.getString("date") ?: ""
                            val gTot = safeDouble(sDoc, "grandTotal")
                            val (cAmt, gAmt, crAmt) = getSaleSplitAmounts(sDoc)
                            val paidDirect = cAmt + gAmt
                            val ts = sDoc.getLong("timestamp") ?: 0L

                            if (isDateBeforeFrom(dStr)) {
                                effectiveOpBal += crAmt
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
                                    val lineAmt = ((m["amount"] as? Number)?.toDouble() ?: 0.0) +
                                            ((m["bagCharge"] as? Number)?.toDouble() ?: 0.0) +
                                            ((m["weighCoolie"] as? Number)?.toDouble() ?: 0.0)
                                    val finalAmt = if (lineAmt > 0.0) lineAmt else gTot
                                    val ratio = if (gTot > 0.0) (finalAmt / gTot).coerceIn(0.0, 1.0) else 1.0

                                    val tx = JSONObject()
                                    tx.put("date", dStr)
                                    tx.put("qty", qtyVal)
                                    tx.put("rate", rtVal)
                                    tx.put("amount", finalAmt)
                                    tx.put("income", paidDirect * ratio)
                                    tx.put("discount", 0.0)
                                    tx.put("ts", ts + k)
                                    txList.add(tx)
                                }
                            } else {
                                val kgs = safeDouble(sDoc, "netKgs").let { if (it > 0.0) it else (sDoc.getLong("bags") ?: 0L).toDouble() }
                                val rtVal = if (kgs > 0.0) gTot / kgs else 0.0
                                val tx = JSONObject()
                                tx.put("date", dStr)
                                tx.put("qty", kgs)
                                tx.put("rate", rtVal)
                                tx.put("amount", gTot)
                                tx.put("income", paidDirect)
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
                            val incAmt = safeDouble(rDoc, "amount")
                            val discAmt = safeDouble(rDoc, "discount")
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

                        addCustomerSectionBannerRow("NAME: $custName", 8)
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
                                <h3>$reportHeading</h3>
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

                val tot = safeDouble(doc, "grandTotal")
                val pMode = doc.getString("payMode") ?: "Credit"
                val paid = if (pMode.equals("Cash", ignoreCase = true)) tot else safeDouble(doc, "paidAmount")
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
                val tot = safeDouble(doc, "grandTotal")
                val paid = safeDouble(doc, "paidAmount")
                val bal = if (doc.contains("credit")) safeDouble(doc, "credit")
                else if (mode.equals("Cash", ignoreCase = true)) 0.0
                else (doc.getDouble("balanceAmount") ?: (tot - paid))

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
                    val kgs = safeDouble(doc, "netKgs").let { if (it > 0.0) it else safeDouble(doc, "kgs") }
                    val amt = safeDouble(doc, "grandTotal")

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
                    val tot = safeDouble(doc, "grandTotal")
                    val billNo = doc.getString("billNo") ?: doc.id
                    val ts = doc.getLong("timestamp") ?: 0L

                    val paidAmt = if (billCol == "sales") {
                        val (cAmt, gAmt, _) = getSaleSplitAmounts(doc)
                        cAmt + gAmt
                    } else {
                        if (mode.equals("Cash", ignoreCase = true)) tot else 0.0
                    }

                    val obj = JSONObject()
                    obj.put("date", date)
                    obj.put("ref", billNo)
                    obj.put("desc", "Bill ($mode)")
                    obj.put("debit", tot)
                    obj.put("credit", paidAmt)
                    obj.put("ts", ts)
                    entriesList.put(obj)
                }

                for (doc in vouSnap) {
                    val p = doc.getString(partyField) ?: ""
                    if (!p.equals(partyName, ignoreCase = true)) continue
                    val date = doc.getString("date") ?: ""
                    if (!isDateInRange(date)) continue

                    val amt = safeDouble(doc, "amount")
                    val disc = safeDouble(doc, "discount")
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
            Toast.makeText(this, "A4 PDF print is ready for this report", Toast.LENGTH_SHORT).show()
            return
        }
        printWebView = WebView(this)
        printWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = printWebView.createPrintDocumentAdapter("Report_${fromDateStr}_to_${toDateStr}")
                val builder = PrintAttributes.Builder()
                builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                builder.setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                printManager.print("Veggie Ledger A4", printAdapter, builder.build())
            }
        }
        printWebView.loadDataWithBaseURL(null, lastPrintableHtml, "text/HTML", "UTF-8", null)
        Toast.makeText(this, "Preparing A4 PDF...", Toast.LENGTH_SHORT).show()
    }
}
