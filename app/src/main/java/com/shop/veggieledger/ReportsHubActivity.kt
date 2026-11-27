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
                            suit = 0
