package com.shop.veggieledger

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import java.io.File
import java.io.FileOutputStream
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
    private lateinit var spItemFilter: Spinner
    private lateinit var layoutItemBox: LinearLayout
    private lateinit var tvItemLabel: TextView
    private lateinit var tableLayout: TableLayout
    private lateinit var daybookContainer: LinearLayout
    private lateinit var hScrollTable: HorizontalScrollView

    private lateinit var minimalRootScroll: ScrollView
    private lateinit var minEtFromDate: EditText
    private lateinit var minEtToDate: EditText
    private lateinit var minSpSupplier: Spinner
    private lateinit var minBtnSubmit: Button
    private lateinit var minBtnExportPdf: Button
    private lateinit var minTableLayout: TableLayout

    private data class MinSupplierRow(
        val sortKey: Long,
        val ymdDate: Long,
        val dateStr: String,
        val opBal: Double,
        val qty: Double,
        val suit: Double,
        val kudumi: Double,
        val netQty: Double,
        val rate: Double,
        val amount: Double,
        val payment: Double,
        val less: Double,
        val closingBal: Double
    )

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

    private val minSupplierNames = arrayListOf<String>()
    private val minSupplierOpBalMap = HashMap<String, Double>()
    private lateinit var minSupplierAdapter: ArrayAdapter<String>
    private val lastMinStatementRows = arrayListOf<MinSupplierRow>()

    private var fromDateStr: String = ""
    private var toDateStr: String = ""
    private val partyNames = arrayListOf("ALL SUPPLIERS")
    private val itemNames = arrayListOf("ALL ITEMS")
    private val partyOpBalMap = HashMap<String, Double>()
    private val partyIdMap = HashMap<String, String>()
    private var shareReportText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reportType = intent.getStringExtra("REPORT_TYPE") ?: "PUR_DETAILS"
        supportActionBar?.title = getReportTitle(reportType)

        if (reportType == "PUR_STATEMENT") {
            setupMinimalSupplierStatementScreen()
            return
        }

        buildUiProgrammatically()
        setupDates()
        setupPartyAndItemDropdowns()

        when {
            isDaySalesReport() -> loadAndRenderDaySalesReport()
            isDaybookReport() -> loadAndRenderDaybook()
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

    private fun setupMinimalSupplierStatementScreen() {
        minimalRootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F5F7FA"))
            setPadding(24, 24, 24, 32)
        }

        val rootCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, 12)
        }

        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val calTo = Calendar.getInstance()
        val calFrom = Calendar.getInstance()
        fromDateStr = sdf.format(calFrom.time)
        toDateStr = sdf.format(calTo.time)

        minEtFromDate = EditText(this).apply {
            hint = "From Date"
            setText(fromDateStr)
            isFocusable = false
            isClickable = true
            setBackgroundResource(android.R.drawable.edit_text)
            setPadding(16, 12, 16, 12)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            setOnClickListener {
                DatePickerDialog(this@ReportsHubActivity, { _, y, m, d ->
                    calFrom.set(y, m, d)
                    fromDateStr = sdf.format(calFrom.time)
                    setText(fromDateStr)
                }, calFrom.get(Calendar.YEAR), calFrom.get(Calendar.MONTH), calFrom.get(Calendar.DAY_OF_MONTH)).show()
            }
        }

        minEtToDate = EditText(this).apply {
            hint = "To Date"
            setText(toDateStr)
            isFocusable = false
            isClickable = true
            setBackgroundResource(android.R.drawable.edit_text)
            setPadding(16, 12, 16, 12)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
            setOnClickListener {
                DatePickerDialog(this@ReportsHubActivity, { _, y, m, d ->
                    calTo.set(y, m, d)
                    toDateStr = sdf.format(calTo.time)
                    setText(toDateStr)
                }, calTo.get(Calendar.YEAR), calTo.get(Calendar.MONTH), calTo.get(Calendar.DAY_OF_MONTH)).show()
            }
        }

        dateRow.addView(minEtFromDate)
        dateRow.addView(minEtToDate)
        rootCol.addView(dateRow)

        minSpSupplier = Spinner(this).apply {
            setBackgroundResource(android.R.drawable.btn_dropdown)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (46 * resources.displayMetrics.density).toInt()
            ).apply {
                setMargins(0, 0, 0, 14)
            }
        }
        minSupplierAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, minSupplierNames)
        minSupplierAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        minSpSupplier.adapter = minSupplierAdapter
        rootCol.addView(minSpSupplier)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, 16)
        }

        minBtnSubmit = Button(this).apply {
            text = "SUBMIT"
            setBackgroundColor(Color.parseColor("#1B5E20"))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            setOnClickListener { generateMinimalSupplierStatement() }
        }

        minBtnExportPdf = Button(this).apply {
            text = "SHARE"
            setBackgroundColor(Color.parseColor("#1565C0"))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
            setOnClickListener { exportMinimalSupplierPdfAndImage() }
        }

        btnRow.addView(minBtnSubmit)
        btnRow.addView(minBtnExportPdf)
        rootCol.addView(btnRow)

        val hScroll = HorizontalScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        minTableLayout = TableLayout(this).apply {
            setBackgroundColor(Color.parseColor("#607D8B"))
            setPadding(3, 3, 3, 3)
        }
        hScroll.addView(minTableLayout)
        rootCol.addView(hScroll)

        minimalRootScroll.addView(rootCol)
        setContentView(minimalRootScroll)

        renderMinimalHeaderOnly()

        db.collection("suppliers").get().addOnSuccessListener { snap ->
            minSupplierNames.clear()
            minSupplierOpBalMap.clear()
            for (doc in snap) {
                val name = (doc.getString("name") ?: "").trim()
                if (name.isEmpty()) continue
                val opBal = doc.getDouble("openingBalance") ?: doc.getDouble("opBal") ?: 0.0
                minSupplierOpBalMap[name.lowercase(Locale.getDefault())] = opBal
                if (minSupplierNames.none { it.equals(name, ignoreCase = true) }) {
                    minSupplierNames.add(name)
                }
            }
            minSupplierNames.sortBy { it.lowercase(Locale.getDefault()) }
            minSupplierAdapter.notifyDataSetChanged()
        }
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

    private fun renderMinimalHeaderOnly() {
        minTableLayout.removeAllViews()
        val headers = listOf("Date", "Op.Bal", "Qty", "Suit", "Kudumi", "NetQty", "Rate", "Amount", "Payment", "Less", "Closing Bal")
        val headerRow = TableRow(this).apply {
            setBackgroundColor(Color.parseColor("#607D8B"))
        }
        for (h in headers) {
            headerRow.addView(
                makeTableCell(
                    text = h,
                    bgColorHex = "#1B5E20",
                    textColorHex = "#FFFFFF",
                    alignRight = (h != "Date"),
                    bold = true
                )
            )
        }
        minTableLayout.addView(headerRow)
    }

    private fun generateMinimalSupplierStatement() {
        val supName = minSpSupplier.selectedItem?.toString()?.trim().orEmpty()
        if (supName.isEmpty()) {
            Toast.makeText(this, "Select a Supplier Name", Toast.LENGTH_SHORT).show()
            return
        }

        val fromYmd = dateToYmd(fromDateStr)
        val toYmd = dateToYmd(toDateStr)
        val baseOpBal = minSupplierOpBalMap[supName.lowercase(Locale.getDefault())] ?: 0.0

        minBtnSubmit.isEnabled = false
        val purTask = db.collection("purchases").get()
        val supPayTask = db.collection("supplier_payments").get()
        val payTask = db.collection("payments").get()

        Tasks.whenAllComplete(purTask, supPayTask, payTask)
            .addOnSuccessListener {
                minBtnSubmit.isEnabled = true

                val purchaseDocs = if (purTask.isSuccessful) {
                    (purTask.result as? QuerySnapshot)?.documents.orEmpty()
                } else emptyList()

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

                val events = arrayListOf<MinSupplierRow>()

                for (doc in purchaseDocs) {
                    val s = (doc.getString("supplierName") ?: "").trim()
                    if (!s.equals(supName, ignoreCase = true)) continue

                    val dStr = (doc.getString("date") ?: "").trim()
                    val ts = doc.getLong("timestamp") ?: 0L
                    val ymd = dateToYmd(dStr).toLong()
                    val safeYmd = if (ymd > 0L) ymd else 0L
                    val sortKey = (safeYmd * 10_000_000L) + (1L * 1_000_000L) + ((ts / 1000L) % 1_000_000L)

                    val rawItems = doc.get("items") as? List<*>
                    if (rawItems != null && rawItems.isNotEmpty()) {
                        for (entry in rawItems) {
                            val m = entry as? Map<*, *> ?: continue
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
                            val netQtyVal = savedNet ?: (qtyVal - suitVal - kudumiVal).coerceAtLeast(0.0)
                            val rateVal = (m["ratePerKg"] as? Number)?.toDouble()
                                ?: (m["rate"] as? Number)?.toDouble()
                                ?: 0.0
                            val amtVal = (m["lineTotal"] as? Number)?.toDouble()
                                ?: (m["amount"] as? Number)?.toDouble()
                                ?: (netQtyVal * rateVal)

                            events.add(
                                MinSupplierRow(
                                    sortKey = sortKey,
                                    ymdDate = safeYmd,
                                    dateStr = dStr,
                                    opBal = 0.0,
                                    qty = qtyVal,
                                    suit = suitVal,
                                    kudumi = kudumiVal,
                                    netQty = netQtyVal,
                                    rate = rateVal,
                                    amount = amtVal,
                                    payment = 0.0,
                                    less = 0.0,
                                    closingBal = 0.0
                                )
                            )
                        }
                    } else {
                        val qtyVal = (doc.getDouble("purchaseKgs") ?: doc.getDouble("netKgs") ?: 0.0)
                        val suitVal = (doc.getDouble("suit") ?: doc.getDouble("thadukki") ?: 0.0)
                        val kudumiVal = (doc.getDouble("kudumi") ?: 0.0)
                        val netQtyVal = (doc.getDouble("netKgs") ?: (qtyVal - suitVal - kudumiVal).coerceAtLeast(0.0))
                        val rateVal = (doc.getDouble("ratePerKg") ?: doc.getDouble("rate") ?: 0.0)
                        val amtVal = (doc.getDouble("grandTotal") ?: doc.getDouble("amount") ?: (netQtyVal * rateVal))

                        events.add(
                            MinSupplierRow(
                                sortKey = sortKey,
                                ymdDate = safeYmd,
                                dateStr = dStr,
                                opBal = 0.0,
                                qty = qtyVal,
                                suit = suitVal,
                                kudumi = kudumiVal,
                                netQty = netQtyVal,
                                rate = rateVal,
                                amount = amtVal,
                                payment = 0.0,
                                less = 0.0,
                                closingBal = 0.0
                            )
                        )
                    }
                }

                for (doc in paymentDocs) {
                    val s = (doc.getString("supplierName")
                        ?: doc.getString("partyName")
                        ?: doc.getString("name")
                        ?: "").trim()
                    if (!s.equals(supName, ignoreCase = true)) continue

                    val dStr = (doc.getString("date") ?: "").trim()
                    val ts = doc.getLong("timestamp") ?: 0L
                    val ymd = dateToYmd(dStr).toLong()
                    val safeYmd = if (ymd > 0L) ymd else 0L
                    val sortKey = (safeYmd * 10_000_000L) + (2L * 1_000_000L) + ((ts / 1000L) % 1_000_000L)

                    val paidAmt = (doc.getDouble("amount")
                        ?: doc.getDouble("paidAmount")
                        ?: doc.getDouble("payment")
                        ?: 0.0)

                    val lessAmt = (doc.getDouble("discount")
                        ?: doc.getDouble("less")
                        ?: doc.getDouble("discAmount")
                        ?: doc.getDouble("deduction")
                        ?: 0.0)

                    events.add(
                        MinSupplierRow(
                            sortKey = sortKey,
                            ymdDate = safeYmd,
                            dateStr = dStr,
                            opBal = 0.0,
                            qty = 0.0,
                            suit = 0.0,
                            kudumi = 0.0,
                            netQty = 0.0,
                            rate = 0.0,
                            amount = 0.0,
                            payment = paidAmt,
                            less = lessAmt,
                            closingBal = 0.0
                        )
                    )
                }

                val sortedEvents = events.sortedBy { it.sortKey }
                var runningOp = baseOpBal
                val filteredRows = arrayListOf<MinSupplierRow>()

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

                    filteredRows.add(ev.copy(opBal = rowOp, closingBal = rowClosing))
                    runningOp = rowClosing
                }

                lastMinStatementRows.clear()
                lastMinStatementRows.addAll(filteredRows)

                renderMinimalHeaderOnly()

                var sumQty = 0.0
                var sumSuit = 0.0
                var sumKudumi = 0.0
                var sumNetQty = 0.0
                var sumAmount = 0.0
                var sumPayment = 0.0
                var sumLess = 0.0
                var lastClosing = baseOpBal

                filteredRows.forEachIndexed { idx, r ->
                    sumQty += r.qty
                    sumSuit += r.suit
                    sumKudumi += r.kudumi
                    sumNetQty += r.netQty
                    sumAmount += r.amount
                    sumPayment += r.payment
                    sumLess += r.less
                    lastClosing = r.closingBal

                    val rowBg = if (idx % 2 == 0) "#FFFFFF" else "#F1F8E9"
                    val tr = TableRow(this).apply {
                        setBackgroundColor(Color.parseColor("#607D8B"))
                    }
                    val vals = listOf(
                        r.dateStr,
                        "%.2f".format(Locale.getDefault(), r.opBal),
                        "%.2f".format(Locale.getDefault(), r.qty),
                        "%.2f".format(Locale.getDefault(), r.suit),
                        "%.2f".format(Locale.getDefault(), r.kudumi),
                        "%.2f".format(Locale.getDefault(), r.netQty),
                        "%.2f".format(Locale.getDefault(), r.rate),
                        "%.2f".format(Locale.getDefault(), r.amount),
                        "%.2f".format(Locale.getDefault(), r.payment),
                        "%.2f".format(Locale.getDefault(), r.less),
                        "%.2f".format(Locale.getDefault(), r.closingBal)
                    )
                    vals.forEachIndexed { cIdx, txt ->
                        tr.addView(
                            makeTableCell(
                                text = txt,
                                bgColorHex = rowBg,
                                textColorHex = if (cIdx == 10) "#C62828" else "#000000",
                                alignRight = (cIdx != 0),
                                bold = (cIdx == 0 || cIdx == 5 || cIdx == 7 || cIdx == 10)
                            )
                        )
                    }
                    minTableLayout.addView(tr)
                }

                val totalRow = TableRow(this).apply {
                    setBackgroundColor(Color.parseColor("#607D8B"))
                }
                val totalVals = listOf(
                    "TOTAL",
                    "",
                    "%.2f".format(Locale.getDefault(), sumQty),
                    "%.2f".format(Locale.getDefault(), sumSuit),
                    "%.2f".format(Locale.getDefault(), sumKudumi),
                    "%.2f".format(Locale.getDefault(), sumNetQty),
                    "",
                    "%.2f".format(Locale.getDefault(), sumAmount),
                    "%.2f".format(Locale.getDefault(), sumPayment),
                    "%.2f".format(Locale.getDefault(), sumLess),
                    "%.2f".format(Locale.getDefault(), lastClosing)
                )
                totalVals.forEachIndexed { cIdx, txt ->
                    totalRow.addView(
                        makeTableCell(
                            text = txt,
                            bgColorHex = "#FFF9C4",
                            textColorHex = if (cIdx == 10) "#C62828" else "#000000",
                            alignRight = (cIdx != 0),
                            bold = true
                        )
                    )
                }
                minTableLayout.addView(totalRow)
            }
            .addOnFailureListener {
                minBtnSubmit.isEnabled = true
                Toast.makeText(this, "Failed to generate report", Toast.LENGTH_SHORT).show()
            }
    }

    private fun drawMinimalSheetOnCanvas(canvas: Canvas, width: Int, height: Int) {
        canvas.drawColor(Color.WHITE)
        val supName = minSpSupplier.selectedItem?.toString()?.trim().orEmpty()

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val headerBg = Paint().apply {
            color = Color.parseColor("#1B5E20")
            style = Paint.Style.FILL
        }
        val evenBg = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val oddBg = Paint().apply {
            color = Color.parseColor("#F1F8E9")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#607D8B")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            isAntiAlias = true
        }
        val headText = Paint().apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyText = Paint().apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val leftX = 20f
        var y = 34f
        canvas.drawText("SUPPLIER STATEMENT : $supName ($fromDateStr to $toDateStr)", leftX, y, titlePaint)
        y += 22f

        val colWidths = floatArrayOf(78f, 68f, 62f, 58f, 62f, 68f, 62f, 78f, 78f, 64f, 90f)
        val colXs = FloatArray(colWidths.size)
        var rx = leftX
        for (i in colWidths.indices) {
            colXs[i] = rx
            rx += colWidths[i]
        }
        val rightX = rx
        val rowH = 22f

        val headers = listOf("Date", "Op.Bal", "Qty", "Suit", "Kudumi", "NetQty", "Rate", "Amount", "Payment", "Less", "Closing Bal")
        canvas.drawRect(Rect(leftX.toInt(), y.toInt(), rightX.toInt(), (y + rowH).toInt()), headerBg)
        headers.forEachIndexed { i, h ->
            val cL = colXs[i]
            val cR = cL + colWidths[i]
            canvas.drawRect(Rect(cL.toInt(), y.toInt(), cR.toInt(), (y + rowH).toInt()), borderPaint)
            if (i == 0) canvas.drawText(h, cL + 6f, y + 14f, headText)
            else canvas.drawText(h, cR - headText.measureText(h) - 6f, y + 14f, headText)
        }
        y += rowH

        lastMinStatementRows.forEachIndexed { idx, r ->
            if (y + rowH > height - 24f) return@forEachIndexed
            val bg = if (idx % 2 == 0) evenBg else oddBg
            canvas.drawRect(Rect(leftX.toInt(), y.toInt(), rightX.toInt(), (y + rowH).toInt()), bg)
            val vals = listOf(
                r.dateStr,
                "%.2f".format(Locale.getDefault(), r.opBal),
                "%.2f".format(Locale.getDefault(), r.qty),
                "%.2f".format(Locale.getDefault(), r.suit),
                "%.2f".format(Locale.getDefault(), r.kudumi),
                "%.2f".format(Locale.getDefault(), r.netQty),
                "%.2f".format(Locale.getDefault(), r.rate),
                "%.2f".format(Locale.getDefault(), r.amount),
                "%.2f".format(Locale.getDefault(), r.payment),
                "%.2f".format(Locale.getDefault(), r.less),
                "%.2f".format(Locale.getDefault(), r.closingBal)
            )
            vals.forEachIndexed { i, v ->
                val cL = colXs[i]
                val cR = cL + colWidths[i]
                canvas.drawRect(Rect(cL.toInt(), y.toInt(), cR.toInt(), (y + rowH).toInt()), borderPaint)
                if (i == 0) canvas.drawText(v, cL + 6f, y + 14f, bodyText)
                else canvas.drawText(v, cR - bodyText.measureText(v) - 6f, y + 14f, bodyText)
            }
            y += rowH
        }
    }

    private fun saveBitmapFile(bitmap: Bitmap, fileName: String): Uri? {
        val outDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES) ?: cacheDir
        if (!outDir.exists()) outDir.mkdirs()
        val localFile = File(outDir, fileName)
        FileOutputStream(localFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            fos.flush()
        }

        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VeggieReports")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            return uri
        }
        return Uri.fromFile(localFile)
    }

    private fun exportMinimalSupplierPdfAndImage() {
        if (lastMinStatementRows.isEmpty()) {
            Toast.makeText(this, "Tap SUBMIT first to generate report", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val stamp = System.currentTimeMillis()
            val sheetW = 842
            val sheetH = 595

            val pdfDoc = PdfDocument()
            val page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(sheetW, sheetH, 1).create())
            drawMinimalSheetOnCanvas(page.canvas, sheetW, sheetH)
            pdfDoc.finishPage(page)

            val pdfValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "SupplierStatement_$stamp.pdf")
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/VeggieReports")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }
            val resolver = contentResolver
            val pdfUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pdfValues)
            if (pdfUri != null) {
                resolver.openOutputStream(pdfUri)?.use { out -> pdfDoc.writeTo(out) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    pdfValues.clear()
                    pdfValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(pdfUri, pdfValues, null, null)
                }
            }
            pdfDoc.close()

            val bmp = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
            drawMinimalSheetOnCanvas(Canvas(bmp), sheetW, sheetH)
            val imgUri = saveBitmapFile(bmp, "SupplierStatement_$stamp.jpg")
            bmp.recycle()

            if (imgUri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, imgUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Supplier Statement Image"))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "PDF Export Error: ${e.message}", Toast.LENGTH_LONG).show()
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

        // 1. Supplier Name
        layoutPartyBox = LinearLayout(this)
        layoutPartyBox.orientation = LinearLayout.VERTICAL
        layoutPartyBox.setPadding(0, 0, 0, 8)
        tvPartyLabel = TextView(this)
        tvPartyLabel.text = "1. Supplier Name"
        tvPartyLabel.textSize = 12.5f
        tvPartyLabel.setTypeface(null, Typeface.BOLD)
        tvPartyLabel.setTextColor(Color.parseColor("#1B5E20"))
        spPartyFilter = Spinner(this)
        spPartyFilter.setBackgroundResource(android.R.drawable.btn_dropdown)
        layoutPartyBox.addView(tvPartyLabel)
        layoutPartyBox.addView(spPartyFilter)
        filterCard.addView(layoutPartyBox)

        // Item Filter
        layoutItemBox = LinearLayout(this)
        layoutItemBox.orientation = LinearLayout.VERTICAL
        layoutItemBox.setPadding(0, 0, 0, 8)
        tvItemLabel = TextView(this)
        tvItemLabel.text = "Item"
        tvItemLabel.textSize = 12.5f
        tvItemLabel.setTypeface(null, Typeface.BOLD)
        tvItemLabel.setTextColor(Color.parseColor("#1B5E20"))
        spItemFilter = Spinner(this)
        spItemFilter.setBackgroundResource(android.R.drawable.btn_dropdown)
        layoutItemBox.addView(tvItemLabel)
        layoutItemBox.addView(spItemFilter)
        filterCard.addView(layoutItemBox)

        // 2. From Date to To Date
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
        lblFrom.text = if (isDaybookReport() || isDaySalesReport()) "Date" else "2. From Date"
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
        tvSheetShopName.text = "THIRUVENI COCONUTS[span_0](start_span)"[span_0](end_span)
        tvSheetShopName.textSize = 20f
        tvSheetShopName.setTypeface(null, Typeface.BOLD)
        tvSheetShopName.setTextColor(Color.BLACK)
        tvSheetShopName.gravity = Gravity.CENTER
        sheetBox.addView(tvSheetShopName)

        tvSheetShopAddr = TextView(this)
        tvSheetShopAddr.text = "SHOP NO B98, PERIYAR VEGETABLE MARKET, KOYAMBEDU[span_1](start_span)"[span_1](end_span)
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
        tvLeftMeta.text = "SUPPLIER: ALL SUPPLIERS | ITEM: ALL ITEMS"
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

    private fun setupPartyAndItemDropdowns() {
        if (isDaybookReport() || isDaySalesReport()) {
            layoutPartyBox.visibility = View.GONE
            layoutItemBox.visibility = View.GONE
            toCol.visibility = View.GONE
            lblFrom.text = "Date"
            return
        }

        layoutPartyBox.visibility = View.VISIBLE
        layoutItemBox.visibility = View.VISIBLE
        tvPartyLabel.text = "1. Supplier Name"
        tvItemLabel.text = "Item"

        val supTask = db.collection("suppliers").get()
        val purTask = db.collection("purchases").get()
        val itemsTask = db.collection("items").get()

        Tasks.whenAllComplete(supTask, purTask, itemsTask).addOnSuccessListener {
            val uniqueSuppliers = linkedSetOf<String>()
            val uniqueItems = linkedSetOf<String>()

            if (supTask.isSuccessful) {
                for (doc in (supTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                    val n = (doc.getString("name") ?: "").trim()
                    if (n.isNotEmpty()) {
                        uniqueSuppliers.add(n)
                        val key = n.lowercase(Locale.getDefault())
                        partyOpBalMap[key] = safeDouble(doc, "openingBalance")
                        partyIdMap[key] = doc.id
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
                    val s = (doc.getString("supplierName") ?: doc.getString("partyName") ?: "").trim()
                    if (s.isNotEmpty()) uniqueSuppliers.add(s)

                    val topItem = (doc.getString("itemName") ?: doc.getString("item") ?: "").trim()
                    if (topItem.isNotEmpty()) uniqueItems.add(topItem)

                    val rawItems = doc.get("items") as? List<*>
                    if (rawItems != null) {
                        for (entry in rawItems) {
                            val m = entry as? Map<*, *> ?: continue
                            val lineItem = (m["itemName"] as? String ?: m["name"] as? String ?: m["item"] as? String ?: "").trim()
                            if (lineItem.isNotEmpty()) uniqueItems.add(lineItem)
                        }
                    }
                }
            }

            partyNames.clear()
            partyNames.add("ALL SUPPLIERS")
            partyNames.addAll(uniqueSuppliers.sortedBy { it.lowercase(Locale.getDefault()) })

            val supAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, partyNames)
            supAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spPartyFilter.adapter = supAdapter
            spPartyFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {}
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

            itemNames.clear()
            itemNames.add("ALL ITEMS")
            itemNames.addAll(uniqueItems.sortedBy { it.lowercase(Locale.getDefault()) })

            val itemAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, itemNames)
            itemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spItemFilter.adapter = itemAdapter
            spItemFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {}
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }
    }

    private fun runReport() {
        when {
            isDaySalesReport() -> loadAndRenderDaySalesReport()
            isDaybookReport() -> loadAndRenderDaybook()
            else -> loadAndRenderPurchaseGridReport()
        }
    }

    private fun loadAndRenderPurchaseGridReport() {
        val selectedSup = spPartyFilter.selectedItem?.toString()?.trim() ?: "ALL SUPPLIERS"
        val selectedItem = spItemFilter.selectedItem?.toString()?.trim() ?: "ALL ITEMS"
        val isSingleSupplier = selectedSup.isNotEmpty() && !selectedSup.equals("ALL SUPPLIERS", ignoreCase = true)
        val isSingleItem = selectedItem.isNotEmpty() && !selectedItem.equals("ALL ITEMS", ignoreCase = true)
        val fromYmd = dateToYmd(fromDateStr)
        val toYmd = dateToYmd(toDateStr)

        daybookContainer.visibility = View.GONE
        tvLeftMeta.text = "SUPPLIER: $selectedSup | ITEM: $selectedItem"
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

                    val ts = safeLong(doc, "timestamp")
                    val sortKey = (if (dYmd > 0) dYmd.toLong() else 0L) * 1_000_000L +
                        (if (ts > 0L) (ts % 1_000_000L) else pos.toLong())

                    val docItemDefault = (doc.getString("itemName") ?: doc.getString("item") ?: "COCONUT").trim()
                    val rawItems = doc.get("items") as? List<*>
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

                            rows.add(
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
                        if (isSingleItem && !docItemDefault.equals(selectedItem, ignoreCase = true)) continue

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

                        rows.add(
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

                rows.sortBy { it.sortKey }
                renderPurchaseGrid(rows, selectedSup, selectedItem, isSingleSupplier)
            }
            .addOnFailureListener { e ->
                tvSummary.text = "Error: ${e.message}"
            }
    }

    private fun renderPurchaseGrid(
        rows: List<PurchaseGridRow>,
        selectedSup: String,
        selectedItem: String,
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

        val shareSb = StringBuilder("DATEWISE PURCHASE REPORT ($selectedSup | $selectedItem)\nDate: $fromDateStr to $toDateStr\n\n")

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
