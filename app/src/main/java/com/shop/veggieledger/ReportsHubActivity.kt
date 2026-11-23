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

        // Filter Card
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

        // 1. FIRST: Customer Name Dropdown (Starts with ALL CUSTOMERS)
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

        // 2. SECOND: From Date - To Date Row
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

        // Action Buttons: SHOW REPORT | A4 PDF | SHARE
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

        // Summary Banner
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

        // A4 SHEET REPORT PREVIEW BOX
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

        // Left: Customer Name | Right: Date Range
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
        tvRightMeta.text = "DATE: $fromDateStr TO$toDateStr"
        tvRightMeta.textSize = 13f
        tvRightMeta.setTypeface(null, Typeface.BOLD)
        tvRightMeta.setTextColor(Color.BLACK)
        tvRightMeta.gravity = Gravity.END
        tvRightMeta.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)

        metaRow.addView(tvLeftMeta)
        metaRow.addView(tvRightMeta)
        sheetBox.addView(metaRow)

        // Data Table
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

            val sortedDocs = snap.documents.sortedBy { doc -> (doc.getString("name") ?: "").lowercase(Locale.getDefault()) }
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
        val to = parseDateSafe
