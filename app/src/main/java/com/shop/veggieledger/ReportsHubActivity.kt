package com.shop.veggieledger

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
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
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
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
    private lateinit var spItemFilter: Spinner
    private lateinit var layoutItemBox: LinearLayout
    private lateinit var tvItemLabel: TextView
    private lateinit var spPartyFilter: Spinner
    private lateinit var layoutPartyBox: LinearLayout
    private lateinit var tvPartyLabel: TextView
    private lateinit var tableLayout: TableLayout
    private lateinit var daybookContainer: LinearLayout
    private lateinit var hScrollTable: HorizontalScrollView

    private data class DaySaleRow(
        val docId: String,
        val entryIndex: Long,
        val billNo: String,
        val date: String,
        val partyName: String,
        val qty: Double,
        val rate: Double,
        val amount: Double,
        val gpay: Double,
        val cash: Double,
        val credit: Double
    )

    private data class PurchaseGridRow(
        val sortKey: Long,
        val dateStr: String,
        val supplierName: String,
        val itemName: String,
        val qty: Double,
        val suit: Double,
        val kudumi: Double,
        val netKgs: Double,
        val rate: Double,
        val amount: Double
    )

    private data class SupplierStatementRow(
        val sortKey: Long,
        val ymdDate: Long,
        val dateStr: String,
        val opBal: Double,
        val qty: Double,
        val suit: Double,
        val kudumi: Double,
        val netKgs: Double,
        val rate: Double,
        val amount: Double,
        val payment: Double,
        val less: Double,
        val closingBal: Double
    )

    private data class SupplierOutstandingRow(
        val supplierName: String,
        val opBal: Double,
        val purchaseAmt: Double,
        val paymentAmt: Double,
        val lessAmt: Double,
        val closingBal: Double
    )

    private var fromDateStr: String = ""
    private var toDateStr: String = ""
    private val itemNames = arrayListOf("ALL ITEMS")
    private val partyNames = arrayListOf("ALL SUPPLIERS")
    private val allKnownSuppliers = linkedSetOf<String>()
    private val itemToSuppliersMap = HashMap<String, LinkedHashSet<String>>()
    private val partyOpBalMap = HashMap<String, Double>()
    private var shareReportText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reportType = intent.getStringExtra("REPORT_TYPE") ?: "PUR_DETAILS"
        supportActionBar?.title = getReportTitle(reportType)

        buildUiProgrammatically()
        setupDates()
        setupItemAndSupplierDropdowns()

        when {
            isDaySalesReport() -> loadAndRenderDaySalesReport()
            isDaybookReport() -> loadAndRenderDaybook()
            reportType == "PUR_STATEMENT" -> loadAndRenderSupplierStatementReport()
            reportType == "PUR_OUTSTANDING" -> loadAndRenderSupplierOutstandingReport()
            else -> loadAndRenderPurchaseGridReport()
        }
    }

    private fun isDaySalesReport(): Boolean {
        return reportType.equals("DAY_SALES_REPORT", ignoreCase = true) ||
            reportType.equals("DAILY_SALES_REPORT", ignoreCase = true) ||
            reportType.equals("DAILY_SALES_EDIT", ignoreCase = true) ||
            reportType.equals("SAL_DAY_REPORT", ignoreCase = true)
    }

    private fun isDaybookReport(): Boolean {
        return reportType.equals("DAILY_CLOSING", ignoreCase = true) ||
            reportType.equals("DAYBOOK", ignoreCase = true)
    }

    private fun makeTableCell(
        text: String,
        bgColorHex: String,
        textColorHex: String = "#000000",
        alignRight: Boolean = false,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            this.text = text
            setBackgroundColor(Color.parseColor(bgColorHex))
            setTextColor(Color.parseColor(textColorHex))
            textSize = 12.5f
            setPadding(16, 12, 16, 12)
            gravity = if (alignRight) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
            if (bold) setTypeface(null, Typeface.BOLD)
            val lp = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.MATCH_PARENT
            )
            lp.setMargins(2, 2, 2, 2)
            layoutParams = lp
        }
    }

    private fun makeSmallActionBtn(label: String, bgHex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setBackgroundColor(Color.parseColor(bgHex))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            textSize = 11f
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(14, 8, 14, 8)
            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(2, 2, 2, 2)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun safeLong(doc: DocumentSnapshot, field: String): Long {
        val raw = doc.get(field) ?: return 0L
        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull() ?: 0L
            else -> 0L
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

    private fun fmtQty(v: Double): String {
        return if (abs(v - v.toLong()) < 0.005) {
            "${v.toLong()}"
        } else {
            String.format(Locale.getDefault(), "%.2f", v)
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

    private fun getReportTitle(type: String): String {
        return when (type) {
            "DAILY_CLOSING", "DAYBOOK" -> "Daybook Report"
            "DAY_SALES_REPORT", "DAILY_SALES_REPORT", "DAILY_SALES_EDIT", "SAL_DAY_REPORT" -> "Day Sales Report"
            "PUR_WISE_SALES" -> "Purchase-wise Sales Report (Lot Tracking)"
            "PUR_DETAILS", "PUR_ITEMWISE" -> "Datewise Purchase Report"
            "PUR_STATEMENT" -> "Supplier Statement"
            "PUR_OUTSTANDING" -> "Supplier Outstanding"
            "SAL_CUSTOMER" -> "Customer Sales Report"
            "SAL_DETAILS" -> "Sales Bill Details"
            "SAL_ITEMWISE" -> "Itemwise Sales Report"
            "SAL_STATEMENT" -> "Customer Ledger Statement"
            else -> "Datewise Purchase Report"
        }
    }

    private fun buildUiProgrammatically() {
        val rootScroll = ScrollView(this)
        rootScroll.setBackgroundColor(Color.parseColor("#ECEFF1"))
        rootScroll.setPadding(14, 14, 14, 14)

        val mainCol = LinearLayout(this)
        mainCol.orientation = LinearLayout.VERTICAL
        rootScroll.addView(mainCol)

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

        // 1. Item Name (First)
        layoutItemBox = LinearLayout(this)
        layoutItemBox.orientation = LinearLayout.VERTICAL
        layoutItemBox.setPadding(0, 0, 0, 8)
        tvItemLabel = TextView(this)
        tvItemLabel.text = "1. Item Name"
        tvItemLabel.textSize = 12.5f
        tvItemLabel.setTypeface(null, Typeface.BOLD)
        tvItemLabel.setTextColor(Color.parseColor("#1B5E20"))
        spItemFilter = Spinner(this)
        spItemFilter.setBackgroundResource(android.R.drawable.btn_dropdown)
        layoutItemBox.addView(tvItemLabel)
        layoutItemBox.addView(spItemFilter)
        filterCard.addView(layoutItemBox)

        // 2. Supplier Name (Next - filtered by selected Item)
        layoutPartyBox = LinearLayout(this)
        layoutPartyBox.orientation = LinearLayout.VERTICAL
        layoutPartyBox.setPadding(0, 0, 0, 8)
        tvPartyLabel = TextView(this)
        tvPartyLabel.text = "2. Supplier Name"
        tvPartyLabel.textSize = 12.5f
        tvPartyLabel.setTypeface(null, Typeface.BOLD)
        tvPartyLabel.setTextColor(Color.parseColor("#1B5E20"))
        spPartyFilter = Spinner(this)
        spPartyFilter.setBackgroundResource(android.R.drawable.btn_dropdown)
        layoutPartyBox.addView(tvPartyLabel)
        layoutPartyBox.addView(spPartyFilter)
        filterCard.addView(layoutPartyBox)

        // 3. From Date to To Date
        val dateRow = LinearLayout(this)
        dateRow.orientation = LinearLayout.HORIZONTAL
        dateRow.weightSum = 2f
        dateRow.setPadding(0, 0, 0, 8)

        val fromCol = LinearLayout(this)
        fromCol.orientation = LinearLayout.VERTICAL
        fromCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(0, 0, 6, 0)
        }
        lblFrom = TextView(this)
        lblFrom.text = if (isDaybookReport() || isDaySalesReport()) "Date" else "3. From Date"
        lblFrom.textSize = 12.5f
        lblFrom.setTypeface(null, Typeface.BOLD)
        lblFrom.setTextColor(Color.parseColor("#1B5E20"))
        etFromDate = EditText(this)
        etFromDate.isFocusable = false
        etFromDate.isClickable = true
        etFromDate.setBackgroundResource(android.R.drawable.edit_text)
        etFromDate.textSize = 13f
        etFromDate.setTypeface(null, Typeface.BOLD)
        fromCol.addView(lblFrom)
        fromCol.addView(etFromDate)

        toCol = LinearLayout(this)
        toCol.orientation = LinearLayout.VERTICAL
        toCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(6, 0, 0, 0)
        }
        val lblTo = TextView(this)
        lblTo.text = "To Date"
        lblTo.textSize = 12.5f
        lblTo.setTypeface(null, Typeface.BOLD)
        lblTo.setTextColor(Color.parseColor("#1B5E20"))
        etToDate = EditText(this)
        etToDate.isFocusable = false
        etToDate.isClickable = true
        etToDate.setBackgroundResource(android.R.drawable.edit_text)
        etToDate.textSize = 13f
        etToDate.setTypeface(null, Typeface.BOLD)
        toCol.addView(lblTo)
        toCol.addView(etToDate)

        dateRow.addView(fromCol)
        dateRow.addView(toCol)
        filterCard.addView(dateRow)

        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.weightSum = 2f
        btnRow.setPadding(0, 4, 0, 4)

        val btnFilter = Button(this)
        btnFilter.text = "SUBMIT"
        btnFilter.setBackgroundColor(Color.parseColor("#1B5E20"))
        btnFilter.setTextColor(Color.WHITE)
        btnFilter.setTypeface(null, Typeface.BOLD)
        val lpBtn1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lpBtn1.setMargins(0, 0, 6, 0)
        btnFilter.layoutParams = lpBtn1
        btnFilter.setOnClickListener { runReport() }

        val btnShare = Button(this)
        btnShare.text = "SHARE"
        btnShare.setBackgroundColor(Color.parseColor("#1565C0"))
        btnShare.setTextColor(Color.WHITE)
        btnShare.setTypeface(null, Typeface.BOLD)
        val lpBtn3 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lpBtn3.setMargins(6, 0, 0, 0)
        btnShare.layoutParams = lpBtn3
        btnShare.setOnClickListener {
            val sendIntent = Intent(Intent.ACTION_SEND)
            sendIntent.type = "text/plain"
            sendIntent.putExtra(Intent.EXTRA_TEXT, shareReportText)
            startActivity(Intent.createChooser(sendIntent, "Share Report"))
        }

        btnRow.addView(btnFilter)
        btnRow.addView(btnShare)
        filterCard.addView(btnRow)
        mainCol.addView(filterCard)

        tvSummary = TextView(this)
        tvSummary.text = "Tap SUBMIT to view Grid"
        tvSummary.setBackgroundColor(Color.parseColor("#FFF9C4"))
        tvSummary.setTextColor(Color.parseColor("#D84315"))
        tvSummary.setTypeface(null, Typeface.BOLD)
        tvSummary.textSize = 13.5f
        tvSummary.setPadding(16, 12, 16, 12)
        val sumLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        sumLp.setMargins(0, 0, 0, 12)
        tvSummary.layoutParams = sumLp
        mainCol.addView(tvSummary)

        daybookContainer = LinearLayout(this)
        daybookContainer.orientation = LinearLayout.VERTICAL
        daybookContainer.visibility = View.GONE
        mainCol.addView(daybookContainer)

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
        tvLeftMeta.text = "ITEM: ALL ITEMS | SUPPLIER: ALL SUPPLIERS"
        tvLeftMeta.textSize = 12.5f
        tvLeftMeta.setTypeface(null, Typeface.BOLD)
        tvLeftMeta.setTextColor(Color.BLACK)
        tvLeftMeta.gravity = Gravity.START
        tvLeftMeta.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)

        tvRightMeta = TextView(this)
        tvRightMeta.text = "DATE: $fromDateStr TO $toDateStr"
        tvRightMeta.textSize = 12.5f
        tvRightMeta.setTypeface(null, Typeface.BOLD)
        tvRightMeta.setTextColor(Color.BLACK)
        tvRightMeta.gravity = Gravity.END
        tvRightMeta.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)

        metaRow.addView(tvLeftMeta)
        metaRow.addView(tvRightMeta)
        sheetBox.addView(metaRow)

        hScrollTable = HorizontalScrollView(this)
        hScrollTable.setBackgroundColor(Color.WHITE)
        tableLayout = TableLayout(this)
        tableLayout.setBackgroundColor(Color.parseColor("#607D8B"))
        tableLayout.setPadding(2, 2, 2, 2)
        hScrollTable.addView(tableLayout)
        sheetBox.addView(hScrollTable)

        mainCol.addView(sheetBox)
        setContentView(rootScroll)
    }

    private fun setupDates() {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val calTo = Calendar.getInstance()
        val calFrom = Calendar.getInstance()

        fromDateStr = sdf.format(calFrom.time)
        toDateStr = sdf.format(calTo.time)

        etFromDate.setText(fromDateStr)
        etToDate.setText(toDateStr)

        etFromDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                calFrom.set(y, m, d)
                fromDateStr = sdf.format(calFrom.time)
                etFromDate.setText(fromDateStr)
            }, calFrom.get(Calendar.YEAR), calFrom.get(Calendar.MONTH), calFrom.get(Calendar.DAY_OF_MONTH)).show()
        }

        etToDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                calTo.set(y, m, d)
                toDateStr = sdf.format(calTo.time)
                etToDate.setText(toDateStr)
            }, calTo.get(Calendar.YEAR), calTo.get(Calendar.MONTH), calTo.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun setupItemAndSupplierDropdowns() {
        if (isDaybookReport() || isDaySalesReport()) {
            layoutItemBox.visibility = View.GONE
            layoutPartyBox.visibility = View.GONE
            toCol.visibility = View.GONE
            lblFrom.text = "Date"
            return
        }

        layoutItemBox.visibility = View.VISIBLE
        layoutPartyBox.visibility = View.VISIBLE
        tvItemLabel.text = "1. Item Name"
        tvPartyLabel.text = "2. Supplier Name"

        val supTask = db.collection("suppliers").get()
        val purTask = db.collection("purchases").get()
        val itemsTask = db.collection("items").get()

        Tasks.whenAllComplete(supTask, purTask, itemsTask).addOnSuccessListener {
            allKnownSuppliers.clear()
            itemToSuppliersMap.clear()
            partyOpBalMap.clear()
            val uniqueItems = linkedSetOf<String>()

            if (supTask.isSuccessful) {
                for (doc in (supTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                    val n = (doc.getString("name") ?: "").trim()
                    if (n.isNotEmpty()) {
                        allKnownSuppliers.add(n)
                        val key = n.lowercase(Locale.getDefault())
                        val op = safeDouble(doc, "openingBalance").let {
                            if (it != 0.0) it else safeDouble(doc, "opBal")
                        }
                        partyOpBalMap[key] = op
                    }
                }
            }

            if (itemsTask.isSuccessful) {
                for (doc in (itemsTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                    val item = (doc.getString("name") ?: doc.getString("itemName") ?: "").trim()
                    if (item.isNotEmpty()) uniqueItems.add(item)
                }
            }

            if (purTask.isSuccessful) {
                for (doc in (purTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                    val sup = (doc.getString("supplierName") ?: doc.getString("partyName") ?: "").trim()
                    if (sup.isNotEmpty()) allKnownSuppliers.add(sup)

                    val docItemDefault = (doc.getString("itemName") ?: doc.getString("item") ?: "").trim()
                    val rawItems = doc.get("items") as? List<*>
                    if (rawItems != null && rawItems.isNotEmpty()) {
                        for (entry in rawItems) {
                            val m = entry as? Map<*, *> ?: continue
                            val lineItem = (m["itemName"] as? String
                                ?: m["name"] as? String
                                ?: m["item"] as? String
                                ?: docItemDefault).trim()
                            if (lineItem.isNotEmpty()) {
                                uniqueItems.add(lineItem)
                                if (sup.isNotEmpty()) {
                                    val iKey = lineItem.lowercase(Locale.getDefault())
                                    itemToSuppliersMap.getOrPut(iKey) { linkedSetOf() }.add(sup)
                                }
                            }
                        }
                    } else if (docItemDefault.isNotEmpty()) {
                        uniqueItems.add(docItemDefault)
                        if (sup.isNotEmpty()) {
                            val iKey = docItemDefault.lowercase(Locale.getDefault())
                            itemToSuppliersMap.getOrPut(iKey) { linkedSetOf() }.add(sup)
                        }
                    }
                }
            }

            itemNames.clear()
            itemNames.add("ALL ITEMS")
            itemNames.addAll(uniqueItems.sortedBy { it.lowercase(Locale.getDefault()) })

            val itemAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, itemNames)
            itemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spItemFilter.adapter = itemAdapter

            updateSuppliersForSelectedItem("ALL ITEMS")

            spItemFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    val chosenItem = itemNames.getOrNull(pos) ?: "ALL ITEMS"
                    updateSuppliersForSelectedItem(chosenItem)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    private fun updateSuppliersForSelectedItem(selectedItem: String) {
        val previousSup = spPartyFilter.selectedItem?.toString()?.trim()
        partyNames.clear()
        partyNames.add("ALL SUPPLIERS")

        val listToShow = if (selectedItem.equals("ALL ITEMS", ignoreCase = true)) {
            allKnownSuppliers
        } else {
            itemToSuppliersMap[selectedItem.lowercase(Locale.getDefault())] ?: emptySet()
        }

        partyNames.addAll(listToShow.sortedBy { it.lowercase(Locale.getDefault()) })

        val supAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, partyNames)
        supAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spPartyFilter.adapter = supAdapter

        if (!previousSup.isNullOrEmpty()) {
            val matchIdx = partyNames.indexOfFirst { it.equals(previousSup, ignoreCase = true) }
            if (matchIdx >= 0) {
                spPartyFilter.setSelection(matchIdx)
            }
        }
    }

    private fun runReport() {
        when {
            isDaySalesReport() -> loadAndRenderDaySalesReport()
            isDaybookReport() -> loadAndRenderDaybook()
            reportType == "PUR_STATEMENT" -> loadAndRenderSupplierStatementReport()
            reportType == "PUR_OUTSTANDING" -> loadAndRenderSupplierOutstandingReport()
            else -> loadAndRenderPurchaseGridReport()
        }
    }

    private fun extractPurchaseLinesForDoc(
        doc: DocumentSnapshot,
        pos: Int,
        isSingleItem: Boolean,
        selectedItem: String
    ): List<PurchaseGridRow> {
        val sup = (doc.getString("supplierName") ?: doc.getString("partyName") ?: "SUPPLIER").trim()
        val dStr = (doc.getString("date") ?: "").trim()
        val dYmd = dateToYmd(dStr)
        val ts = safeLong(doc, "timestamp")
        val sortKey = (if (dYmd > 0) dYmd.toLong() else 0L) * 1_000_000L +
            (if (ts > 0L) (ts % 1_000_000L) else pos.toLong())

        val docItemDefault = (doc.getString("itemName") ?: doc.getString("item") ?: "COCONUT").trim()
        val rawItems = doc.get("items") as? List<*>
        val out = arrayListOf<PurchaseGridRow>()

        if (rawItems != null && rawItems.isNotEmpty()) {
            for (entry in rawItems) {
                val m = entry as? Map<*, *> ?: continue
                val lineItem = (m["itemName"] as? String
                    ?: m["name"] as? String
                    ?: m["item"] as? String
                    ?: docItemDefault).trim()
                if (isSingleItem && !lineItem.equals(selectedItem, ignoreCase = true)) continue

                val purKgs = (m["purchaseKgs"] as? Number)?.toDouble() ?: 0.0
                val bags = (m["bags"] as? Number)?.toDouble() ?: 0.0
                val nos = (m["nos"] as? Number)?.toDouble() ?: 0.0
                val qtyVal = when {
                    purKgs > 0.0 -> purKgs
                    bags > 0.0 -> bags
                    else -> nos
                }
                val suitVal = (m["suit"] as? Number)?.toDouble()
                    ?: (m["thadukki"] as? Number)?.toDouble()
                    ?: 0.0
                val kudumiVal = (m["kudumi"] as? Number)?.toDouble() ?: 0.0
                val savedNet = (m["netKgs"] as? Number)?.toDouble()
                val netKgsVal = savedNet ?: (qtyVal - suitVal - kudumiVal).coerceAtLeast(0.0)
                val rateVal = (m["ratePerKg"] as? Number)?.toDouble()
                    ?: (m["rate"] as? Number)?.toDouble()
                    ?: 0.0
                val amtVal = (m["lineTotal"] as? Number)?.toDouble()
                    ?: (m["amount"] as? Number)?.toDouble()
                    ?: (netKgsVal * rateVal)

                out.add(
                    PurchaseGridRow(
                        sortKey = sortKey,
                        dateStr = dStr,
                        supplierName = sup,
                        itemName = lineItem,
                        qty = qtyVal,
                        suit = suitVal,
                        kudumi = kudumiVal,
                        netKgs = netKgsVal,
                        rate = rateVal,
                        amount = amtVal
                    )
                )
            }
        } else {
            if (!isSingleItem || docItemDefault.equals(selectedItem, ignoreCase = true)) {
                val qtyVal = safeDouble(doc, "purchaseKgs").let {
                    if (it > 0.0) it else safeDouble(doc, "netKgs").let { n ->
                        if (n > 0.0) n else safeDouble(doc, "qty")
                    }
                }
                val suitVal = safeDouble(doc, "suit").let {
                    if (it > 0.0) it else safeDouble(doc, "thadukki")
                }
                val kudumiVal = safeDouble(doc, "kudumi")
                val netKgsVal = safeDouble(doc, "netKgs").let {
                    if (it > 0.0) it else (qtyVal - suitVal - kudumiVal).coerceAtLeast(0.0)
                }
                val rateVal = safeDouble(doc, "ratePerKg").let {
                    if (it > 0.0) it else safeDouble(doc, "rate")
                }
                val amtVal = safeDouble(doc, "grandTotal").let {
                    if (it > 0.0) it else safeDouble(doc, "amount").let { a ->
                        if (a > 0.0) a else (netKgsVal * rateVal)
                    }
                }

                out.add(
                    PurchaseGridRow(
                        sortKey = sortKey,
                        dateStr = dStr,
                        supplierName = sup,
                        itemName = docItemDefault,
                        qty = qtyVal,
                        suit = suitVal,
                        kudumi = kudumiVal,
                        netKgs = netKgsVal,
                        rate = rateVal,
                        amount = amtVal
                    )
                )
            }
        }
        return out
    }

    private fun loadAndRenderPurchaseGridReport() {
        val selectedItem = spItemFilter.selectedItem?.toString()?.trim() ?: "ALL ITEMS"
        val selectedSup = spPartyFilter.selectedItem?.toString()?.trim() ?: "ALL SUPPLIERS"
        val isSingleItem = selectedItem.isNotEmpty() && !selectedItem.equals("ALL ITEMS", ignoreCase = true)
        val isSingleSupplier = selectedSup.isNotEmpty() && !selectedSup.equals("ALL SUPPLIERS", ignoreCase = true)
        val fromYmd = dateToYmd(fromDateStr)
        val toYmd = dateToYmd(toDateStr)

        daybookContainer.visibility = View.GONE
        tvLeftMeta.text = "ITEM: $selectedItem | SUPPLIER: $selectedSup"
        tvRightMeta.text = "DATE: $fromDateStr TO $toDateStr"
        tvSummary.text = "Loading..."

        db.collection("purchases").get()
            .addOnSuccessListener { snap ->
                val rows = arrayListOf<PurchaseGridRow>()

                for ((pos, doc) in snap.documents.withIndex()) {
                    val sup = (doc.getString("supplierName") ?: doc.getString("partyName") ?: "SUPPLIER").trim()
                    if (isSingleSupplier && !sup.equals(selectedSup, ignoreCase = true)) continue

                    val dStr = (doc.getString("date") ?: "").trim()
                    val dYmd = dateToYmd(dStr)
                    if (fromYmd != -1 && dYmd != -1 && dYmd < fromYmd) continue
                    if (toYmd != -1 && dYmd != -1 && dYmd > toYmd) continue

                    rows.addAll(extractPurchaseLinesForDoc(doc, pos, isSingleItem, selectedItem))
                }

                rows.sortBy { it.sortKey }
                renderPurchaseGrid(rows, selectedItem, selectedSup, isSingleSupplier)
            }
            .addOnFailureListener { e ->
                tvSummary.text = "Error: ${e.message}"
            }
    }

    private fun renderPurchaseGrid(
        rows: List<PurchaseGridRow>,
        selectedItem: String,
        selectedSup: String,
        isSingleSupplier: Boolean
    ) {
        tableLayout.removeAllViews()

        val headers = if (isSingleSupplier) {
            listOf("Date", "Quantity", "Suit", "Kudumi", "Net Kgs", "Rate", "Amount")
        } else {
            listOf("Date", "Supplier", "Quantity", "Suit", "Kudumi", "Net Kgs", "Rate", "Amount")
        }

        val hRow = TableRow(this).apply { setBackgroundColor(Color.parseColor("#607D8B")) }
        for (h in headers) {
            val isRight = h in listOf("Quantity", "Suit", "Kudumi", "Net Kgs", "Rate", "Amount")
            hRow.addView(makeTableCell(h, "#1B5E20", "#FFFFFF", isRight, true))
        }
        tableLayout.addView(hRow)

        var totQty = 0.0
        var totSuit = 0.0
        var totKudumi = 0.0
        var totNetKgs = 0.0
        var totAmt = 0.0

        val shareSb = StringBuilder("DATEWISE PURCHASE REPORT ($selectedItem | $selectedSup)\nDate: $fromDateStr to $toDateStr\n\n")

        rows.forEachIndexed { idx, r ->
            totQty += r.qty
            totSuit += r.suit
            totKudumi += r.kudumi
            totNetKgs += r.netKgs
            totAmt += r.amount

            val bg = if (idx % 2 == 0) "#FFFFFF" else "#F1F8E9"
            val tr = TableRow(this).apply { setBackgroundColor(Color.parseColor("#607D8B")) }

            tr.addView(makeTableCell(r.dateStr, bg, "#000000", false, true))
            if (!isSingleSupplier) {
                tr.addView(makeTableCell(r.supplierName, bg, "#1565C0", false, true))
            }
            tr.addView(makeTableCell(fmtQty(r.qty), bg, "#000000", true, false))
            tr.addView(makeTableCell(fmtQty(r.suit), bg, "#000000", true, false))
            tr.addView(makeTableCell(fmtQty(r.kudumi), bg, "#000000", true, false))
            tr.addView(makeTableCell(fmtQty(r.netKgs), bg, "#1B5E20", true, true))
            tr.addView(makeTableCell("%.2f".format(Locale.getDefault(), r.rate), bg, "#000000", true, false))
            tr.addView(makeTableCell("%.2f".format(Locale.getDefault(), r.amount), bg, "#000000", true, true))
            tableLayout.addView(tr)

            if (isSingleSupplier) {
                shareSb.append(
                    "${r.dateStr} | Qty: ${fmtQty(r.qty)} | Suit: ${fmtQty(r.suit)} | Kudumi: ${fmtQty(r.kudumi)} | Net: ${fmtQty(r.netKgs)} | Rate: %.2f | Amt: %.2f\n".format(
                        Locale.getDefault(), r.rate, r.amount
                    )
                )
            } else {
                shareSb.append(
                    "${r.dateStr} | ${r.supplierName} | Net: ${fmtQty(r.netKgs)} | Rate: %.2f | Amt: %.2f\n".format(
                        Locale.getDefault(), r.rate, r.amount
                    )
                )
            }
        }

        val totRow = TableRow(this).apply { setBackgroundColor(Color.parseColor("#607D8B")) }
        totRow.addView(makeTableCell("TOTAL", "#FFF9C4", "#000000", false, true))
        if (!isSingleSupplier) {
            totRow.addView(makeTableCell("", "#FFF9C4", "#000000", false, true))
        }
        totRow.addView(makeTableCell(fmtQty(totQty), "#FFF9C4", "#000000", true, true))
        totRow.addView(makeTableCell(fmtQty(totSuit), "#FFF9C4", "#000000", true, true))
        totRow.addView(makeTableCell(fmtQty(totKudumi), "#FFF9C4", "#000000", true, true))
        totRow.addView(makeTableCell(fmtQty(totNetKgs), "#FFF9C4", "#1B5E20", true, true))
        totRow.addView(makeTableCell("-", "#FFF9C4", "#000000", true, true))
        totRow.addView(makeTableCell("%.2f".format(Locale.getDefault(), totAmt), "#FFF9C4", "#C62828", true, true))
        tableLayout.addView(totRow)

        tvSummary.text = "Qty: ${fmtQty(totQty)} | Suit: ${fmtQty(totSuit)} | Kudumi: ${fmtQty(totKudumi)} | Net Kgs: ${fmtQty(totNetKgs)} | Amount: Rs. %.2f".format(
            Locale.getDefault(), totAmt
        )
        shareSb.append("\nTotal Net Kgs: ${fmtQty(totNetKgs)} | Total Amount: Rs. %.2f".format(Locale.getDefault(), totAmt))
        shareReportText = shareSb.toString()
    }

    private fun loadAndRenderSupplierStatementReport() {
        val selectedItem = spItemFilter.selectedItem?.toString()?.trim() ?: "ALL ITEMS"
        val selectedSup = spPartyFilter.selectedItem?.toString()?.trim() ?: "ALL SUPPLIERS"
        val isSingleItem = selectedItem.isNotEmpty() && !selectedItem.equals("ALL ITEMS", ignoreCase = true)
        val isSingleSupplier = selectedSup.isNotEmpty() && !selectedSup.equals("ALL SUPPLIERS", ignoreCase = true)
        val fromYmd = dateToYmd(fromDateStr)
        val toYmd = dateToYmd(toDateStr)

        daybookContainer.visibility = View.GONE
        tvLeftMeta.text = "ITEM: $selectedItem | SUPPLIER: $selectedSup"
        tvRightMeta.text = "DATE: $fromDateStr TO $toDateStr"
        tvSummary.text = "Loading Supplier Statement..."

        val purTask = db.collection("purchases").get()
        val supPayTask = db.collection("supplier_payments").get()
        val payTask = db.collection("payments").get()

        Tasks.whenAllComplete(purTask, supPayTask, payTask).addOnSuccessListener {
            val events = arrayListOf<SupplierStatementRow>()

            if (purTask.isSuccessful) {
                for ((pos, doc) in (purTask.result as? QuerySnapshot)?.documents.orEmpty().withIndex()) {
                    val sup = (doc.getString("supplierName") ?: doc.getString("partyName") ?: "SUPPLIER").trim()
                    if (isSingleSupplier && !sup.equals(selectedSup, ignoreCase = true)) continue

                    val dStr = (doc.getString("date") ?: "").trim()
                    val ymd = dateToYmd(dStr).toLong()
                    val safeYmd = if (ymd > 0L) ymd else 0L
                    val ts = safeLong(doc, "timestamp")
                    val sortKey = (safeYmd * 10_000_000L) + (1L * 1_000_000L) +
                        (if (ts > 0L) ((ts / 1000L) % 1_000_000L) else pos.toLong())

                    val lines = extractPurchaseLinesForDoc(doc, pos, isSingleItem, selectedItem)
                    for (line in lines) {
                        events.add(
                            SupplierStatementRow(
                                sortKey = sortKey,
                                ymdDate = safeYmd,
                                dateStr = dStr,
                                opBal = 0.0,
                                qty = line.qty,
                                suit = line.suit,
                                kudumi = line.kudumi,
                                netKgs = line.netKgs,
                                rate = line.rate,
                                amount = line.amount,
                                payment = 0.0,
                                less = 0.0,
                                closingBal = 0.0
                            )
                        )
                    }
                }
            }

            val paymentDocs = arrayListOf<DocumentSnapshot>()
            if (supPayTask.isSuccessful) {
                paymentDocs.addAll((supPayTask.result as? QuerySnapshot)?.documents.orEmpty())
            }
            if (payTask.isSuccessful) {
                for (doc in (payTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                    val pType = (doc.getString("partyType") ?: doc.getString("type") ?: "").trim()
                    val hasSup = !(doc.getString("supplierName") ?: "").isNullOrBlank()
                    if (pType.equals("supplier", ignoreCase = true) || hasSup) {
                        paymentDocs.add(doc)
                    }
                }
            }

            if (!isSingleItem) {
                for (doc in paymentDocs) {
                    val sup = (doc.getString("supplierName")
                        ?: doc.getString("partyName")
                        ?: doc.getString("name")
                        ?: "").trim()
                    if (isSingleSupplier && !sup.equals(selectedSup, ignoreCase = true)) continue

                    val dStr = (doc.getString("date") ?: "").trim()
                    val ymd = dateToYmd(dStr).toLong()
                    val safeYmd = if (ymd > 0L) ymd else 0L
                    val ts = safeLong(doc, "timestamp")
                    val sortKey = (safeYmd * 10_000_000L) + (2L * 1_000_000L) + ((ts / 1000L) % 1_000_000L)

                    val paidAmt = safeDouble(doc, "amount").let {
                        if (it > 0.0) it else safeDouble(doc, "paidAmount").let { p ->
                            if (p > 0.0) p else safeDouble(doc, "payment")
                        }
                    }
                    val lessAmt = safeDouble(doc, "discount").let {
                        if (it > 0.0) it else safeDouble(doc, "less").let { l ->
                            if (l > 0.0) l else safeDouble(doc, "discAmount")
                        }
                    }

                    events.add(
                        SupplierStatementRow(
                            sortKey = sortKey,
                            ymdDate = safeYmd,
                            dateStr = dStr,
                            opBal = 0.0,
                            qty = 0.0,
                            suit = 0.0,
                            kudumi = 0.0,
                            netKgs = 0.0,
                            rate = 0.0,
                            amount = 0.0,
                            payment = paidAmt,
                            less = lessAmt,
                            closingBal = 0.0
                        )
                    )
                }
            }

            val baseOpBal = if (isSingleSupplier) {
                partyOpBalMap[selectedSup.lowercase(Locale.getDefault())] ?: 0.0
            } else {
                0.0
            }

            val sortedEvents = events.sortedBy { it.sortKey }
            var runningOp = baseOpBal
            val rows = arrayListOf<SupplierStatementRow>()

            for (ev in sortedEvents) {
                val isBeforeFrom = fromYmd != -1 && ev.ymdDate > 0L && ev.ymdDate < fromYmd.toLong()
                val isAfterTo = toYmd != -1 && ev.ymdDate > 0L && ev.ymdDate > toYmd.toLong()

                val rowOp = runningOp
                val rowClosing = (rowOp + ev.amount - ev.payment - ev.less).coerceAtLeast(0.0)

                if (isBeforeFrom) {
                    runningOp = rowClosing
                    continue
                }
                if (isAfterTo) break

                rows.add(ev.copy(opBal = rowOp, closingBal = rowClosing))
                runningOp = rowClosing
            }

            tableLayout.removeAllViews()
            val headers = listOf("Date", "Op.Bal", "Qty", "Suit", "Kudumi", "Net Kgs", "Rate", "Amount", "Payment", "Less", "Closing Bal")
            val hRow = TableRow(this).apply { setBackgroundColor(Color.parseColor("#607D8B")) }
            for (h in headers) {
                hRow.addView(makeTableCell(h, "#1B5E20", "#FFFFFF", h != "Date", true))
            }
            tableLayout.addView(hRow)

            var sumQty = 0.0
            var sumSuit = 0.0
            var sumKudumi = 0.0
            var sumNetKgs = 0.0
            var sumAmt = 0.0
            var sumPay = 0.0
            var sumLess = 0.0
            var lastClosing = baseOpBal

            val shareSb = StringBuilder("SUPPLIER STATEMENT ($selectedItem | $selectedSup)\nDate: $fromDateStr to $toDateStr\n\n")

            rows.forEachIndexed { idx, r ->
                sumQty += r.qty
                sumSuit += r.suit
                sumKudumi += r.kudumi
                sumNetKgs += r.netKgs
                sumAmt += r.amount
                sumPay += r.payment
                sumLess += r.less
                lastClosing = r.closingBal

                val bg = if (idx % 2 == 0) "#FFFFFF" else "#F1F8E9"
                val tr = TableRow(this).apply { setBackgroundColor(Color.parseColor("#607D8B")) }
                val vals = listOf(
                    r.dateStr,
                    "%.2f".format(Locale.getDefault(), r.opBal),
                    fmtQty(r.qty),
                    fmtQty(r.suit),
                    fmtQty(r.kudumi),
                    fmtQty(r.netKgs),
                    "%.2f".format(Locale.getDefault(), r.rate),
                    "%.2f".format(Locale.getDefault(), r.amount),
                    "%.2f".format(Locale.getDefault(), r.payment),
                    "%.2f".format(Locale.getDefault(), r.less),
                    "%.2f".format(Locale.getDefault(), r.closingBal)
                )
                vals.forEachIndexed { cIdx, v ->
                    tr.addView(
                        makeTableCell(
                            text = v,
                            bgColorHex = bg,
                            textColorHex = if (cIdx == 10) "#C62828" else "#000000",
                            alignRight = cIdx != 0,
                            bold = cIdx in listOf(0, 5, 7, 10)
                        )
                    )
                }
                tableLayout.addView(tr)

                shareSb.append(
                    "${r.dateStr} | Net: ${fmtQty(r.netKgs)} | Amt: %.2f | Pay: %.2f | Cl: %.2f\n".format(
                        Locale.getDefault(), r.amount, r.payment, r.closingBal
                    )
                )
            }

            val totRow = TableRow(this).apply { setBackgroundColor(Color.parseColor("#607D8B")) }
            val totVals = listOf(
                "TOTAL",
                "",
                fmtQty(sumQty),
                fmtQty(sumSuit),
                fmtQty(sumKudumi),
                fmtQty(sumNetKgs),
                "-",
                "%.2f".format(Locale.getDefault(), sumAmt),
                "%.2f".format(Locale.getDefault(), sumPay),
                "%.2f".format(Locale.getDefault(), sumLess),
                "%.2f".format(Locale.getDefault(), lastClosing)
            )
            totVals.forEachIndexed { cIdx, v ->
                totRow.addView(
                    makeTableCell(
                        text = v,
                        bgColorHex = "#FFF9C4",
                        textColorHex = if (cIdx == 10) "#C62828" else "#000000",
                        alignRight = cIdx != 0,
                        bold = true
                    )
                )
            }
            tableLayout.addView(totRow)

            tvSummary.text = "Net Kgs: ${fmtQty(sumNetKgs)} | Amount: Rs. %.2f | Payment: Rs. %.2f | Closing: Rs. %.2f".format(
                Locale.getDefault(), sumAmt, sumPay, lastClosing
            )
            shareSb.append("\nClosing Balance: Rs. %.2f".format(Locale.getDefault(), lastClosing))
            shareReportText = shareSb.toString()
        }
    }

    private fun loadAndRenderSupplierOutstandingReport() {
        val selectedItem = spItemFilter.selectedItem?.toString()?.trim() ?: "ALL ITEMS"
        val selectedSup = spPartyFilter.selectedItem?.toString()?.trim() ?: "ALL SUPPLIERS"
        val isSingleItem = selectedItem.isNotEmpty() && !selectedItem.equals("ALL ITEMS", ignoreCase = true)
        val isSingleSupplier = selectedSup.isNotEmpty() && !selectedSup.equals("ALL SUPPLIERS", ignoreCase = true)
        val fromYmd = dateToYmd(fromDateStr)
        val toYmd = dateToYmd(toDateStr)

        daybookContainer.visibility = View.GONE
        tvLeftMeta.text = "ITEM: $selectedItem | SUPPLIER: $selectedSup"
        tvRightMeta.text = "DATE: $fromDateStr TO $toDateStr"
        tvSummary.text = "Loading Supplier Outstanding..."

        val purTask = db.collection("purchases").get()
        val supPayTask = db.collection("supplier_payments").get()
        val payTask = db.collection("payments").get()

        Tasks.whenAllComplete(purTask, supPayTask, payTask).addOnSuccessListener {
            val purBefore = HashMap<String, Double>()
            val purRange = HashMap<String, Double>()
            val payBefore = HashMap<String, Double>()
            val payRange = HashMap<String, Double>()
            val lessBefore = HashMap<String, Double>()
            val lessRange = HashMap<String, Double>()
            val candidateSuppliers = linkedSetOf<String>()

            if (isSingleSupplier) {
                candidateSuppliers.add(selectedSup)
            } else {
                val allowed = if (isSingleItem) {
                    itemToSuppliersMap[selectedItem.lowercase(Locale.getDefault())] ?: emptySet()
                } else {
                    allKnownSuppliers
                }
                candidateSuppliers.addAll(allowed)
            }

            if (purTask.isSuccessful) {
                for ((pos, doc) in (purTask.result as? QuerySnapshot)?.documents.orEmpty().withIndex()) {
                    val sup = (doc.getString("supplierName") ?: doc.getString("partyName") ?: "").trim()
                    if (sup.isEmpty()) continue
                    if (isSingleSupplier && !sup.equals(selectedSup, ignoreCase = true)) continue

                    val dStr = (doc.getString("date") ?: "").trim()
                    val dYmd = dateToYmd(dStr)
                    if (toYmd != -1 && dYmd != -1 && dYmd > toYmd) continue

                    val lines = extractPurchaseLinesForDoc(doc, pos, isSingleItem, selectedItem)
                    if (lines.isEmpty()) continue

                    candidateSuppliers.add(sup)
                    val amt = lines.sumOf { it.amount }
                    val key = sup.lowercase(Locale.getDefault())
                    if (fromYmd != -1 && dYmd != -1 && dYmd < fromYmd) {
                        purBefore[key] = (purBefore[key] ?: 0.0) + amt
                    } else {
                        purRange[key] = (purRange[key] ?: 0.0) + amt
                    }
                }
            }

            val paymentDocs = arrayListOf<DocumentSnapshot>()
            if (supPayTask.isSuccessful) {
                paymentDocs.addAll((supPayTask.result as? QuerySnapshot)?.documents.orEmpty())
            }
            if (payTask.isSuccessful) {
                for (doc in (payTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                    val pType = (doc.getString("partyType") ?: doc.getString("type") ?: "").trim()
                    val hasSup = !(doc.getString("supplierName") ?: "").isNullOrBlank()
                    if (pType.equals("supplier", ignoreCase = true) || hasSup) {
                        paymentDocs.add(doc)
                    }
                }
            }

            if (!isSingleItem) {
                for (doc in paymentDocs) {
                    val sup = (doc.getString("supplierName")
                        ?: doc.getString("partyName")
                        ?: doc.getString("name")
                        ?: "").trim()
                    if (sup.isEmpty()) continue
                    if (isSingleSupplier && !sup.equals(selectedSup, ignoreCase = true)) continue

                    val dStr = (doc.getString("date") ?: "").trim()
                    val dYmd = dateToYmd(dStr)
                    if (toYmd != -1 && dYmd != -1 && dYmd > toYmd) continue

                    candidateSuppliers.add(sup)
                    val paidAmt = safeDouble(doc, "amount").let {
                        if (it > 0.0) it else safeDouble(doc, "paidAmount").let { p ->
                            if (p > 0.0) p else safeDouble(doc, "payment")
                        }
                    }
                    val lessAmt = safeDouble(doc, "discount").let {
                        if (it > 0.0) it else safeDouble(doc, "less").let { l ->
                            if (l > 0.0) l else safeDouble(doc, "discAmount")
                        }
                    }
                    val key = sup.lowercase(Locale.getDefault())
                    if (fromYmd != -1 && dYmd != -1 && dYmd < fromYmd) {
                        payBefore[key] = (payBefore[key] ?: 0.0) + paidAmt
                        lessBefore[key] = (lessBefore[key] ?: 0.0) + lessAmt
                    } else {
                        payRange[key] = (payRange[key] ?: 0.0) + paidAmt
                        lessRange[key] = (lessRange[key] ?: 0.0) + lessAmt
                    }
                }
            }

            val rows = arrayListOf<SupplierOutstandingRow>()
            for (sup in candidateSuppliers.sortedBy { it.lowercase(Locale.getDefault()) }) {
                val key = sup.lowercase(Locale.getDefault())
                val baseOp = if (isSingleItem) 0.0 else (partyOpBalMap[key] ?: 0.0)
                val opBal = (baseOp + (purBefore[key] ?: 0.0) - (payBefore[key] ?: 0.0) - (lessBefore[key] ?: 0.0))
                    .coerceAtLeast(0.0)
                val pAmt = purRange[key] ?: 0.0
                val payAmt = payRange[key] ?: 0.0
                val lAmt = lessRange[key] ?: 0.0
                val clBal = (opBal + pAmt - payAmt - lAmt).coerceAtLeast(0.0)

                if (isSingleSupplier || abs(opBal) > 0.005 || abs(pAmt) > 0.005 || abs(payAmt) > 0.005 || abs(clBal) > 0.005) {
                    rows.add(
                        SupplierOutstandingRow(
                            supplierName = sup,
                            opBal = opBal,
                            purchaseAmt = pAmt,
                            paymentAmt = payAmt,
                            lessAmt = lAmt,
                            closingBal = clBal
                        )
                    )
                }
            }

            tableLayout.removeAllViews()
            val headers = listOf("Supplier Name", "Op.Bal", "Purchase", "Payment", "Less", "Outstanding")
            val hRow = TableRow(this).apply { setBackgroundColor(Color.parseColor("#607D8B")) }
            for (h in headers) {
                hRow.addView(makeTableCell(h, "#1B5E20", "#FFFFFF", h != "Supplier Name", true))
            }
            tableLayout.addView(hRow)

            var totOp = 0.0
            var totPur = 0.0
            var totPay = 0.0
            var totLess = 0.0
            var totOut = 0.0

            val shareSb = StringBuilder("SUPPLIER OUTSTANDING ($selectedItem | $selectedSup)\nDate: $fromDateStr to $toDateStr\n\n")

            rows.forEachIndexed { idx, r ->
                totOp += r.opBal
                totPur += r.purchaseAmt
                totPay += r.paymentAmt
                totLess += r.lessAmt
                totOut += r.closingBal

                val bg = if (idx % 2 == 0) "#FFFFFF" else "#F1F8E9"
                val tr = TableRow(this).apply { setBackgroundColor(Color.parseColor("#607D8B")) }
                tr.addView(makeTableCell(r.supplierName, bg, "#000000", false, true))
                tr.addView(makeTableCell("%.2f".format(Locale.getDefault(), r.opBal), bg, "#000000", true, false))
                tr.addView(makeTableCell("%.2f".format(Locale.getDefault(), r.purchaseAmt), bg, "#1B5E20", true, false))
                tr.addView(makeTableCell("%.2f".format(Locale.getDefault(), r.paymentAmt), bg, "#1565C0", true, false))
                tr.addView(makeTableCell("%.2f".format(Locale.getDefault(), r.lessAmt), bg, "#000000", true, false))
                tr.addView(makeTableCell("%.2f".format(Locale.getDefault(), r.closingBal), bg, "#C62828", true, true))
                tableLayout.addView(tr)

                shareSb.append(
                    "${r.supplierName} | Pur: %.2f | Pay: %.2f | Bal: %.2f\n".format(
                        Locale.getDefault(), r.purchaseAmt, r.paymentAmt, r.closingBal
                    )
                )
            }

            val totRow = TableRow(this).apply { setBackgroundColor(Color.parseColor("#607D8B")) }
            val totVals = listOf(
                "TOTAL",
                "%.2f".format(Locale.getDefault(), totOp),
                "%.2f".format(Locale.getDefault(), totPur),
                "%.2f".format(Locale.getDefault(), totPay),
                "%.2f".format(Locale.getDefault(), totLess),
                "%.2f".format(Locale.getDefault(), totOut)
            )
            totVals.forEachIndexed { cIdx, v ->
                totRow.addView(
                    makeTableCell(
                        text = v,
                        bgColorHex = "#FFF9C4",
                        textColorHex = if (cIdx == 5) "#C62828" else "#000000",
                        alignRight = cIdx != 0,
                        bold = true
                    )
                )
            }
            tableLayout.addView(totRow)

            tvSummary.text = "Suppliers: ${rows.size} | Purchase: Rs. %.2f | Payment: Rs. %.2f | Total Outstanding: Rs. %.2f".format(
                Locale.getDefault(), totPur, totPay, totOut
            )
            shareSb.append("\nTotal Outstanding: Rs. %.2f".format(Locale.getDefault(), totOut))
            shareReportText = shareSb.toString()
        }
    }

    private fun loadAndRenderDaySalesReport() {
        val targetYmd = dateToYmd(fromDateStr)
        daybookContainer.visibility = View.GONE
        tvLeftMeta.text = "DAY SALES REPORT"
        tvRightMeta.text = "DATE: $fromDateStr"
        tvSummary.text = "Loading sales for $fromDateStr..."

        db.collection("sales").get()
            .addOnSuccessListener { snap ->
                val rows = arrayListOf<DaySaleRow>()
                for ((pos, doc) in snap.documents.withIndex()) {
                    val dStr = (doc.getString("date") ?: "").trim()
                    val dYmd = dateToYmd(dStr)
                    if (targetYmd != -1 && dYmd != targetYmd) continue

                    val idxVal = if (doc.contains("entryIndex")) {
                        safeLong(doc, "entryIndex")
                    } else if (safeLong(doc, "sequenceNo") > 0L) {
                        safeLong(doc, "sequenceNo")
                    } else if (safeLong(doc, "timestamp") > 0L) {
                        safeLong(doc, "timestamp")
                    } else {
                        9_000_000_000_000L + pos.toLong()
                    }

                    val billNo = (doc.getString("billNo") ?: doc.id).trim()
                    val party = (doc.getString("customerName") ?: doc.getString("partyName") ?: "CASH").trim()
                    var qty = safeDouble(doc, "netKgs").let {
                        if (it > 0.0) it else safeDouble(doc, "quantity").let { q ->
                            if (q > 0.0) q else safeDouble(doc, "qty")
                        }
                    }
                    var rate = safeDouble(doc, "rate").let {
                        if (it > 0.0) it else safeDouble(doc, "ratePerKg")
                    }
                    var amt = safeDouble(doc, "grandTotal").let {
                        if (it > 0.0) it else safeDouble(doc, "amount")
                    }

                    val rawItems = doc.get("items") as? List<*>
                    if ((qty <= 0.0 || rate <= 0.0) && rawItems != null && rawItems.isNotEmpty()) {
                        val first = rawItems[0] as? Map<*, *>
                        if (first != null) {
                            if (qty <= 0.0) {
                                qty = (first["netKgs"] as? Number)?.toDouble()
                                    ?: (first["qty"] as? Number)?.toDouble() ?: 0.0
                            }
                            if (rate <= 0.0) {
                                rate = (first["rate"] as? Number)?.toDouble()
                                    ?: (first["ratePerKg"] as? Number)?.toDouble() ?: 0.0
                            }
                            if (amt <= 0.0) {
                                amt = (first["amount"] as? Number)?.toDouble()
                                    ?: (first["lineTotal"] as? Number)?.toDouble() ?: (qty * rate)
                            }
                        }
                    }

                    val mode = (doc.getString("payMode") ?: doc.getString("paymentMode") ?: "").trim()
                    val hasSplit = doc.contains("cash") || doc.contains("gpay") || doc.contains("credit") ||
                        doc.contains("cashAmount") || doc.contains("gpayAmount")

                    val cashAmt: Double
                    val gpayAmt: Double
                    val creditAmt: Double

                    if (hasSplit) {
                        cashAmt = if (doc.contains("cash")) safeDouble(doc, "cash") else safeDouble(doc, "cashAmount")
                        gpayAmt = if (doc.contains("gpay")) safeDouble(doc, "gpay") else safeDouble(doc, "gpayAmount")
                        creditAmt = if (doc.contains("credit")) {
                            safeDouble(doc, "credit")
                        } else {
                            (amt - cashAmt - gpayAmt).coerceAtLeast(0.0)
                        }
                    } else {
                        when {
                            mode.equals("Cash", ignoreCase = true) -> {
                                cashAmt = amt; gpayAmt = 0.0; creditAmt = 0.0
                            }
                            mode.contains("GPay", ignoreCase = true) || mode.contains("UPI", ignoreCase = true) -> {
                                cashAmt = 0.0; gpayAmt = amt; creditAmt = 0.0
                            }
                            else -> {
                                cashAmt = 0.0; gpayAmt = 0.0; creditAmt = amt
                            }
                        }
                    }

                    rows.add(
                        DaySaleRow(
                            docId = doc.id,
                            entryIndex = idxVal,
                            billNo = billNo,
                            date = dStr,
                            partyName = party,
                            qty = qty,
                            rate = rate,
                            amount = amt,
                            gpay = gpayAmt,
                            cash = cashAmt,
                            credit = creditAmt
                        )
                    )
                }

                rows.sortWith(compareBy<DaySaleRow> { it.entryIndex }.thenBy { it.docId })
                renderDaySalesTable(rows)
            }
            .addOnFailureListener { e ->
                tvSummary.text = "Error loading Day Sales: ${e.message}"
            }
    }

    private fun renderDaySalesTable(rows: List<DaySaleRow>) {
        tableLayout.removeAllViews()
        val headers = listOf("Idx", "Bill No", "Party Name", "Qty", "Rate", "Amount", "GPay", "Cash", "Credit", "Edit", "Delete")
        val hRow = TableRow(this).apply { setBackgroundColor(Color.parseColor("#64748B")) }
        for (h in headers) {
            val isRight = h in listOf("Idx", "Qty", "Rate", "Amount", "GPay", "Cash", "Credit")
            hRow.addView(makeTableCell(h, "#1B5E20", "#FFFFFF", isRight, true))
        }
        tableLayout.addView(hRow)

        var totQty = 0.0
        var totAmt = 0.0
        var totGpay = 0.0
        var totCash = 0.0
        var totCredit = 0.0

        val shareSb = StringBuilder("DAY SALES REPORT - $fromDateStr\n")

        rows.forEachIndexed { displayPos, r ->
            totQty += r.qty
            totAmt += r.amount
            totGpay += r.gpay
            totCash += r.cash
            totCredit += r.credit

            val idxStr = if (r.entryIndex < 9_000_000_000_000L && r.entryIndex < 10_000_000L) {
                "${r.entryIndex}"
            } else {
                "$displayPos"
            }

            val bg = if (displayPos % 2 == 0) "#FFFFFF" else "#F1F8E9"
            val tr = TableRow(this).apply { setBackgroundColor(Color.parseColor("#64748B")) }
            tr.addView(makeTableCell(idxStr, bg, "#334155", true, true))
            tr.addView(makeTableCell(r.billNo, bg, "#1B5E20", false, true))
            tr.addView(makeTableCell(r.partyName, bg, "#000000", false, true))
            tr.addView(makeTableCell(fmtQty(r.qty), bg, "#000000", true, false))
            tr.addView(makeTableCell("%.2f".format(Locale.getDefault(), r.rate), bg, "#000000", true, false))
            tr.addView(makeTableCell("%.2f".format(Locale.getDefault(), r.amount), bg, "#000000", true, true))
            tr.addView(makeTableCell("%.2f".format(Locale.getDefault(), r.gpay), bg, "#1565C0", true, false))
            tr.addView(makeTableCell("%.2f".format(Locale.getDefault(), r.cash), bg, "#2E7D32", true, false))
            tr.addView(makeTableCell("%.2f".format(Locale.getDefault(), r.credit), bg, "#C62828", true, false))

            tr.addView(makeSmallActionBtn("EDIT", "#1565C0") {
                showEditSaleDialog(r)
            })
            tr.addView(makeSmallActionBtn("DELETE", "#C62828") {
                confirmDeleteSaleRow(r)
            })
            tableLayout.addView(tr)

            shareSb.append(
                "[$idxStr] ${r.billNo} | ${r.partyName} | Qty: ${fmtQty(r.qty)} | Amt: %.2f (GPay: %.2f, Cash: %.2f, Cr: %.2f)\n".format(
                    Locale.getDefault(), r.amount, r.gpay, r.cash, r.credit
                )
            )
        }

        val totRow = TableRow(this).apply { setBackgroundColor(Color.parseColor("#64748B")) }
        totRow.addView(makeTableCell("", "#FFF9C4", "#000000", true, true))
        totRow.addView(makeTableCell("TOTAL", "#FFF9C4", "#000000", false, true))
        totRow.addView(makeTableCell("${rows.size} Bills", "#FFF9C4", "#000000", false, true))
        totRow.addView(makeTableCell(fmtQty(totQty), "#FFF9C4", "#000000", true, true))
        totRow.addView(makeTableCell("-", "#FFF9C4", "#000000", true, true))
        totRow.addView(makeTableCell("%.2f".format(Locale.getDefault(), totAmt), "#FFF9C4", "#000000", true, true))
        totRow.addView(makeTableCell("%.2f".format(Locale.getDefault(), totGpay), "#FFF9C4", "#1565C0", true, true))
        totRow.addView(makeTableCell("%.2f".format(Locale.getDefault(), totCash), "#FFF9C4", "#2E7D32", true, true))
        totRow.addView(makeTableCell("%.2f".format(Locale.getDefault(), totCredit), "#FFF9C4", "#C62828", true, true))
        totRow.addView(makeTableCell("", "#FFF9C4", "#000000", false, true))
        totRow.addView(makeTableCell("", "#FFF9C4", "#000000", false, true))
        tableLayout.addView(totRow)

        tvSummary.text = "Date: $fromDateStr | Bills: ${rows.size} | Qty: ${fmtQty(totQty)} | Total: Rs. %.2f (GPay: %.2f | Cash: %.2f | Credit: %.2f)".format(
            Locale.getDefault(), totAmt, totGpay, totCash, totCredit
        )
        shareSb.append("\nTotal Amount: Rs. %.2f | GPay: %.2f | Cash: %.2f | Credit: %.2f".format(
            Locale.getDefault(), totAmt, totGpay, totCash, totCredit
        ))
        shareReportText = shareSb.toString()
    }

    private fun showEditSaleDialog(row: DaySaleRow) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 12)
        }

        fun fieldInput(label: String, initial: String, numeric: Boolean): EditText {
            val tv = TextView(this).apply {
                text = label
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1B5E20"))
            }
            val et = EditText(this).apply {
                setText(initial)
                inputType = if (numeric) {
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                }
            }
            box.addView(tv)
            box.addView(et)
            return et
        }

        val etParty = fieldInput("Party Name", row.partyName, false)
        val etQty = fieldInput("Quantity (Net Kgs)", fmtQty(row.qty), true)
        val etRate = fieldInput("Rate", "%.2f".format(Locale.US, row.rate), true)
        val etAmt = fieldInput("Amount (Auto = Qty x Rate)", "%.2f".format(Locale.US, row.amount), true)
        val etGpay = fieldInput("GPay", "%.2f".format(Locale.US, row.gpay), true)
        val etCash = fieldInput("Cash", "%.2f".format(Locale.US, row.cash), true)
        val etCredit = fieldInput("Credit", "%.2f".format(Locale.US, row.credit), true)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = etQty.text.toString().toDoubleOrNull() ?: 0.0
                val r = etRate.text.toString().toDoubleOrNull() ?: 0.0
                etAmt.setText("%.2f".format(Locale.US, q * r))
            }
        }
        etQty.addTextChangedListener(watcher)
        etRate.addTextChangedListener(watcher)

        AlertDialog.Builder(this)
            .setTitle("Edit Sale (${row.billNo})")
            .setView(box)
            .setPositiveButton("UPDATE") { _, _ ->
                val newParty = etParty.text.toString().trim().ifEmpty { "CASH" }
                val newQty = etQty.text.toString().toDoubleOrNull() ?: 0.0
                val newRate = etRate.text.toString().toDoubleOrNull() ?: 0.0
                val newAmt = etAmt.text.toString().toDoubleOrNull() ?: (newQty * newRate)
                val newGpay = etGpay.text.toString().toDoubleOrNull() ?: 0.0
                val newCash = etCash.text.toString().toDoubleOrNull() ?: 0.0
                val newCredit = etCredit.text.toString().toDoubleOrNull() ?: 0.0

                val payMode = when {
                    newCredit > 0.0 && newCash == 0.0 && newGpay == 0.0 -> "Credit"
                    newGpay > 0.0 && newCash == 0.0 && newCredit == 0.0 -> "GPay / UPI"
                    newCash > 0.0 && newGpay == 0.0 && newCredit == 0.0 -> "Cash"
                    newCredit > 0.0 -> "Credit"
                    newGpay > 0.0 -> "GPay / UPI"
                    else -> "Cash"
                }

                val updates = hashMapOf<String, Any>(
                    "customerName" to newParty,
                    "netKgs" to newQty,
                    "quantity" to newQty,
                    "rate" to newRate,
                    "ratePerKg" to newRate,
                    "amount" to newAmt,
                    "grandTotal" to newAmt,
                    "gpay" to newGpay,
                    "gpayAmount" to newGpay,
                    "cash" to newCash,
                    "cashAmount" to newCash,
                    "credit" to newCredit,
                    "payMode" to payMode
                )

                db.collection("sales").document(row.docId)
                    .update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this, "${row.billNo} Updated!", Toast.LENGTH_SHORT).show()
                        loadAndRenderDaySalesReport()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun confirmDeleteSaleRow(row: DaySaleRow) {
        AlertDialog.Builder(this)
            .setTitle("Delete Sale Bill?")
            .setMessage("Delete ${row.billNo} (${row.partyName} - Rs. %.2f)?".format(Locale.getDefault(), row.amount))
            .setPositiveButton("DELETE") { _, _ ->
                db.collection("sales").document(row.docId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "${row.billNo} Deleted!", Toast.LENGTH_SHORT).show()
                        loadAndRenderDaySalesReport()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun loadAndRenderDaybook() {
        val targetYmd = dateToYmd(fromDateStr)
        tvLeftMeta.text = "DAYBOOK"
        tvRightMeta.text = "DATE: $fromDateStr"

        val salesTask = db.collection("sales").get()
        val expTask = db.collection("expenses").get()

        Tasks.whenAllComplete(salesTask, expTask).addOnSuccessListener {
            val salesDocs = if (salesTask.isSuccessful) {
                (salesTask.result as? QuerySnapshot)?.documents.orEmpty().filter { doc ->
                    val dYmd = dateToYmd((doc.getString("date") ?: "").trim())
                    targetYmd == -1 || dYmd == -1 || dYmd == targetYmd
                }
            } else emptyList()

            val expenseDocs = if (expTask.isSuccessful) {
                (expTask.result as? QuerySnapshot)?.documents.orEmpty().filter { doc ->
                    val dYmd = dateToYmd((doc.getString("date") ?: "").trim())
                    targetYmd == -1 || dYmd == -1 || dYmd == targetYmd
                }
            } else emptyList()

            var totalCashSales = 0.0
            for (doc in salesDocs) {
                val mode = (doc.getString("payMode") ?: "").trim()
                val cashAmt = when {
                    doc.contains("cash") -> safeDouble(doc, "cash")
                    doc.contains("cashAmount") -> safeDouble(doc, "cashAmount")
                    mode.equals("Cash", ignoreCase = true) -> safeDouble(doc, "grandTotal").let {
                        if (it > 0.0) it else safeDouble(doc, "amount")
                    }
                    else -> 0.0
                }
                totalCashSales += cashAmt
            }

            daybookContainer.visibility = View.VISIBLE
            daybookContainer.removeAllViews()

            val cashTv = TextView(this).apply {
                text = String.format(Locale.getDefault(), "Total Cash Sales : Rs. %.2f", totalCashSales)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1B5E20"))
                setBackgroundColor(Color.parseColor("#E8F5E9"))
                setPadding(16, 14, 16, 14)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 12)
                }
            }
            daybookContainer.addView(cashTv)

            val expTable = TableLayout(this).apply {
                setBackgroundColor(Color.parseColor("#607D8B"))
                setPadding(2, 2, 2, 2)
            }
            val hRow = TableRow(this).apply { setBackgroundColor(Color.parseColor("#607D8B")) }
            listOf("Account - Description", "Amount").forEachIndexed { idx, h ->
                hRow.addView(
                    makeTableCell(
                        text = h,
                        bgColorHex = "#C62828",
                        textColorHex = "#FFFFFF",
                        alignRight = (idx == 1),
                        bold = true
                    )
                )
            }
            expTable.addView(hRow)

            var totalExp = 0.0
            expenseDocs.forEachIndexed { i, doc ->
                val acc = (doc.getString("account")
                    ?: doc.getString("accountName")
                    ?: doc.getString("category")
                    ?: "Expense").trim()
                val desc = (doc.getString("description")
                    ?: doc.getString("desc")
                    ?: doc.getString("remarks")
                    ?: "").trim()
                val label = if (desc.isNotEmpty()) "$acc - $desc" else acc
                val amt = safeDouble(doc, "amount").let { if (it > 0.0) it else safeDouble(doc, "expenseAmount") }
                totalExp += amt

                val bg = if (i % 2 == 0) "#FFFFFF" else "#FFF8E1"
                val tr = TableRow(this).apply { setBackgroundColor(Color.parseColor("#607D8B")) }
                tr.addView(makeTableCell(label, bg, "#000000", alignRight = false, bold = true))
                tr.addView(
                    makeTableCell(
                        String.format(Locale.getDefault(), "%.2f", amt),
                        bg,
                        "#C62828",
                        alignRight = true,
                        bold = true
                    )
                )
                expTable.addView(tr)
            }

            val totRow = TableRow(this).apply { setBackgroundColor(Color.parseColor("#607D8B")) }
            totRow.addView(makeTableCell("Total Expenses", "#FFF9C4", "#000000", alignRight = false, bold = true))
            totRow.addView(
                makeTableCell(
                    String.format(Locale.getDefault(), "%.2f", totalExp),
                    "#FFF9C4",
                    "#C62828",
                    alignRight = true,
                    bold = true
                )
            )
            expTable.addView(totRow)

            daybookContainer.addView(expTable)

            tvSummary.text = String.format(
                Locale.getDefault(),
                "Cash Sales: Rs. %.2f | Expenses: Rs. %.2f",
                totalCashSales,
                totalExp
            )
            shareReportText = "Daybook ($fromDateStr)\nTotal Cash Sales: Rs. %.2f\nTotal Expenses: Rs. %.2f".format(
                Locale.getDefault(),
                totalCashSales,
                totalExp
            )
        }
    }
}
