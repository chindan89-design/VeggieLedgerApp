package com.shop.veggieledger

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
    private lateinit var daybookContainer: LinearLayout
    private lateinit var hScrollTable: HorizontalScrollView

    // Minimal Supplier Statement views (used when reportType == "PUR_STATEMENT")
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

    private val minSupplierNames = arrayListOf<String>()
    private val minSupplierOpBalMap = HashMap<String, Double>()
    private lateinit var minSupplierAdapter: ArrayAdapter<String>
    private val lastMinStatementRows = arrayListOf<MinSupplierRow>()

    private var fromDateStr: String = ""
    private var toDateStr: String = ""
    private val partyNames = arrayListOf("ALL CUSTOMERS")
    private val partyOpBalMap = HashMap<String, Double>()
    private val partyIdMap = HashMap<String, String>()
    private var shareReportText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reportType = intent.getStringExtra("REPORT_TYPE") ?: "DAILY_CLOSING"
        supportActionBar?.title = getReportTitle(reportType)

        if (isDaySalesReportType(reportType)) {
            startActivity(Intent(this, DailySalesEditActivity::class.java))
            finish()
            return
        }

        if (reportType == "PUR_STATEMENT") {
            setupMinimalSupplierStatementScreen()
            return
        }

        buildUiProgrammatically()
        setupDates()
        setupPartyDropdownIfNeeded()
        if (isDaybookReport()) {
            loadAndRenderDaybook()
        }
    }

    private fun isDaySalesReportType(type: String): Boolean {
        return type.equals("DAY_SALES_REPORT", ignoreCase = true) ||
            type.equals("DAILY_SALES_REPORT", ignoreCase = true) ||
            type.equals("DAILY_SALES_EDIT", ignoreCase = true) ||
            type.equals("SAL_DAY_REPORT", ignoreCase = true)
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
        toDateStr = sdf.format(calTo.time)
        val calFrom = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -30) }
        fromDateStr = sdf.format(calFrom.time)

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
            text = "EXPORT TO PDF"
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
            textSize = 12f
            setPadding(14, 11, 14, 11)
            gravity = if (alignRight) Gravity.END else Gravity.START
            if (bold) setTypeface(null, Typeface.BOLD)
            val lp = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.MATCH_PARENT
            )
            lp.setMargins(2, 2, 2, 2)
            layoutParams = lp
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

    private fun getReportTitle(type: String): String {
        return when (type) {
            "DAILY_CLOSING", "DAYBOOK" -> "Daybook Report"
            "DAY_SALES_REPORT", "DAILY_SALES_REPORT", "DAILY_SALES_EDIT", "SAL_DAY_REPORT" -> "Day Sales Report"
            "PUR_WISE_SALES" -> "Purchase-wise Sales Report (Lot Tracking)"
            "PUR_SUPPLIER" -> "Supplier-wise Purchase Report"
            "PUR_DETAILS" -> "Purchase Bill Details"
            "PUR_ITEMWISE" -> "Itemwise Purchase Report"
            "PUR_STATEMENT" -> "Supplier Statement"
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

        val salesMenuLabel = TextView(this).apply {
            text = "SALES REPORTS"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B5E20"))
            setPadding(0, 0, 0, 6)
        }
        filterCard.addView(salesMenuLabel)

        val btnDaySalesReport = Button(this).apply {
            text = "DAY SALES REPORT (EDIT / DELETE)"
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 10)
            }
            setOnClickListener {
                startActivity(Intent(this@ReportsHubActivity, DailySalesEditActivity::class.java))
            }
        }
        filterCard.addView(btnDaySalesReport)

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
        lblFrom.text = if (isDaybookReport()) "Date" else "2. From Date"
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
        tvSummary.text = "Tap SUBMIT to generate report"
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
        if (isDaybookReport()) {
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

    private fun setupPartyDropdownIfNeeded() {
        if (isDaybookReport()) {
            layoutPartyBox.visibility = View.GONE
            toCol.visibility = View.GONE
            lblFrom.text = "Date"
            return
        }

        layoutPartyBox.visibility = View.VISIBLE
        tvPartyLabel.text = "1. Customer Name"
        db.collection("customers").get().addOnSuccessListener { snap ->
            partyNames.clear()
            partyOpBalMap.clear()
            partyIdMap.clear()
            partyNames.add("ALL CUSTOMERS")
            for (doc in snap) {
                val n = (doc.getString("name") ?: "").trim()
                if (n.isNotEmpty()) {
                    val key = n.lowercase(Locale.getDefault())
                    partyNames.add(n)
                    partyOpBalMap[key] = safeDouble(doc, "openingBalance")
                    partyIdMap[key] = doc.id
                }
            }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, partyNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spPartyFilter.adapter = adapter
            spPartyFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {}
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }
    }

    private fun runReport() {
        if (isDaybookReport()) {
            loadAndRenderDaybook()
            return
        }

        val selectedParty = spPartyFilter.selectedItem?.toString() ?: "ALL CUSTOMERS"
        tvLeftMeta.text = "PARTY: $selectedParty"
        tvRightMeta.text = "DATE: $fromDateStr TO $toDateStr"
        tvSummary.text = "Report ready for $selectedParty ($fromDateStr to $toDateStr)"
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
