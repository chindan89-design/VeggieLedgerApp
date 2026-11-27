package com.shop.veggieledger

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
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

class SupplierStatementActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var etFromDate: EditText
    private lateinit var etToDate: EditText
    private lateinit var actvSupplierName: AutoCompleteTextView
    private lateinit var btnSubmit: Button
    private lateinit var btnExportPdf: Button
    private lateinit var tlStatementGrid: TableLayout

    private val supplierOpeningMap = HashMap<String, Double>()
    private val supplierNamesList = ArrayList<String>()
    private lateinit var supplierAdapter: ArrayAdapter<String>

    private var fromDateStr: String = ""
    private var toDateStr: String = ""
    private val currentStatementRows = arrayListOf<StatementRow>()

    data class StatementRow(
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
        val closingBal: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Supplier Statement"

        setContentView(buildMinimalLayout())

        val cal = Calendar.getInstance()
        toDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, -30)
        fromDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)

        etFromDate.setText(fromDateStr)
        etToDate.setText(toDateStr)

        etFromDate.setOnClickListener {
            showDatePicker(etFromDate) { picked -> fromDateStr = picked }
        }
        etToDate.setOnClickListener {
            showDatePicker(etToDate) { picked -> toDateStr = picked }
        }

        supplierAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, supplierNamesList)
        actvSupplierName.setAdapter(supplierAdapter)
        actvSupplierName.threshold = 1
        actvSupplierName.setOnClickListener { actvSupplierName.showDropDown() }

        renderEmptyGrid()
        loadSuppliers()

        btnSubmit.setOnClickListener {
            generateStatementReport()
        }

        btnExportPdf.setOnClickListener {
            exportStatementToPdf()
        }
    }

    private fun buildMinimalLayout(): ScrollView {
        val scrollView = ScrollView(this).apply {
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

        etFromDate = EditText(this).apply {
            hint = "From Date"
            isFocusable = false
            isClickable = true
            setBackgroundResource(android.R.drawable.edit_text)
            setPadding(16, 12, 16, 12)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
        }

        etToDate = EditText(this).apply {
            hint = "To Date"
            isFocusable = false
            isClickable = true
            setBackgroundResource(android.R.drawable.edit_text)
            setPadding(16, 12, 16, 12)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
        }

        dateRow.addView(etFromDate)
        dateRow.addView(etToDate)
        rootCol.addView(dateRow)

        actvSupplierName = AutoCompleteTextView(this).apply {
            hint = "Supplier Name"
            setBackgroundResource(android.R.drawable.edit_text)
            setPadding(16, 12, 16, 12)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 14)
            }
        }
        rootCol.addView(actvSupplierName)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, 16)
        }

        btnSubmit = Button(this).apply {
            text = "SUBMIT"
            setBackgroundColor(Color.parseColor("#1B5E20"))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
        }

        btnExportPdf = Button(this).apply {
            text = "EXPORT TO PDF"
            setBackgroundColor(Color.parseColor("#1565C0"))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
        }

        btnRow.addView(btnSubmit)
        btnRow.addView(btnExportPdf)
        rootCol.addView(btnRow)

        val hScroll = HorizontalScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tlStatementGrid = TableLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CFD8DC"))
            setPadding(2, 2, 2, 2)
        }
        hScroll.addView(tlStatementGrid)
        rootCol.addView(hScroll)

        scrollView.addView(rootCol)
        return scrollView
    }

    private fun showDatePicker(targetEt: EditText, onPicked: (String) -> Unit) {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        try {
            val parsed = sdf.parse(targetEt.text.toString().trim())
            if (parsed != null) cal.time = parsed
        } catch (_: Exception) {
        }

        DatePickerDialog(
            this,
            { _, y, m, d ->
                cal.set(y, m, d)
                val formatted = sdf.format(cal.time)
                targetEt.setText(formatted)
                onPicked(formatted)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadSuppliers() {
        db.collection("suppliers").get().addOnSuccessListener { snap ->
            supplierOpeningMap.clear()
            supplierNamesList.clear()
            for (doc in snap) {
                val name = (doc.getString("name") ?: "").trim()
                if (name.isEmpty()) continue
                val opBal = doc.getDouble("openingBalance")
                    ?: doc.getDouble("opBal")
                    ?: 0.0
                supplierOpeningMap[name.lowercase(Locale.getDefault())] = opBal
                if (supplierNamesList.none { it.equals(name, ignoreCase = true) }) {
                    supplierNamesList.add(name)
                }
            }
            supplierNamesList.sortBy { it.lowercase(Locale.getDefault()) }
            supplierAdapter.notifyDataSetChanged()
        }
    }

    private fun generateStatementReport() {
        val targetSupplier = actvSupplierName.text.toString().trim()
        if (targetSupplier.isEmpty()) {
            Toast.makeText(this, "Enter or select Supplier Name", Toast.LENGTH_SHORT).show()
            return
        }

        val fromYmd = dateToYmdNumber(fromDateStr)
        val toYmd = dateToYmdNumber(toDateStr)
        val baseOpBal = supplierOpeningMap[targetSupplier.lowercase(Locale.getDefault())] ?: 0.0

        btnSubmit.isEnabled = false
        val purTask = db.collection("purchases").get()
        val supPayTask = db.collection("supplier_payments").get()
        val payTask = db.collection("payments").get()

        Tasks.whenAllComplete(purTask, supPayTask, payTask)
            .addOnSuccessListener {
                btnSubmit.isEnabled = true

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

                val rows = buildStatementRows(
                    baseOpeningBalance = baseOpBal,
                    purchaseDocs = purchaseDocs,
                    paymentDocs = paymentDocs,
                    supplierName = targetSupplier,
                    fromYmd = fromYmd,
                    toYmd = toYmd
                )
                currentStatementRows.clear()
                currentStatementRows.addAll(rows)
                renderStatementGrid(rows)
            }
            .addOnFailureListener {
                btnSubmit.isEnabled = true
                Toast.makeText(this, "Failed to load statement", Toast.LENGTH_SHORT).show()
            }
    }

    private fun buildDateSortKey(rawDate: String, typeOrder: Int, fallbackMillis: Long = 0L): Long {
        val ymd = dateToYmdNumber(rawDate)
        val safeYmd = if (ymd != Long.MIN_VALUE) ymd else 0L
        val safeType = typeOrder.coerceIn(0, 9).toLong()
        val tail = ((fallbackMillis / 1000L) % 1_000_000L).coerceAtLeast(0L)
        return (safeYmd * 10_000_000L) + (safeType * 1_000_000L) + tail
    }

    private fun dateToYmdNumber(rawDate: String): Long {
        val clean = rawDate.trim()
        if (clean.isEmpty()) return Long.MIN_VALUE
        val formats = listOf(
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        )
        for (fmt in formats) {
            try {
                val dt = fmt.parse(clean)
                if (dt != null) {
                    return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(dt).toLong()
                }
            } catch (_: Exception) {
            }
        }
        return Long.MIN_VALUE
    }

    private fun buildStatementRows(
        baseOpeningBalance: Double,
        purchaseDocs: List<DocumentSnapshot>,
        paymentDocs: List<DocumentSnapshot>,
        supplierName: String,
        fromYmd: Long,
        toYmd: Long
    ): List<StatementRow> {
        val rawRows = arrayListOf<StatementRow>()

        for (doc in purchaseDocs) {
            val sup = (doc.getString("supplierName") ?: "").trim()
            if (!sup.equals(supplierName, ignoreCase = true)) continue

            val dateStr = (doc.getString("date") ?: "").trim()
            val ts = doc.getLong("timestamp") ?: 0L
            val ymd = dateToYmdNumber(dateStr)
            val sortKey = buildDateSortKey(dateStr, typeOrder = 1, fallbackMillis = ts)

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

                    rawRows.add(
                        StatementRow(
                            sortKey = sortKey,
                            ymdDate = ymd,
                            dateStr = dateStr,
                            opBal = 0.0,
                            qty = qtyVal,
                            suit = suitVal,
                            kudumi = kudumiVal,
                            netQty = netQtyVal,
                            rate = rateVal,
                            amount = amtVal,
                            payment = 0.0,
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

                rawRows.add(
                    StatementRow(
                        sortKey = sortKey,
                        ymdDate = ymd,
                        dateStr = dateStr,
                        opBal = 0.0,
                        qty = qtyVal,
                        suit = suitVal,
                        kudumi = kudumiVal,
                        netQty = netQtyVal,
                        rate = rateVal,
                        amount = amtVal,
                        payment = 0.0,
                        closingBal = 0.0
                    )
                )
            }
        }

        for (doc in paymentDocs) {
            val sup = (doc.getString("supplierName")
                ?: doc.getString("partyName")
                ?: doc.getString("name")
                ?: "").trim()
            if (!sup.equals(supplierName, ignoreCase = true)) continue

            val dateStr = (doc.getString("date") ?: "").trim()
            val ts = doc.getLong("timestamp") ?: 0L
            val ymd = dateToYmdNumber(dateStr)
            val sortKey = buildDateSortKey(dateStr, typeOrder = 2, fallbackMillis = ts)

            val paidAmt = (doc.getDouble("amount")
                ?: doc.getDouble("paidAmount")
                ?: doc.getDouble("payment")
                ?: 0.0)

            rawRows.add(
                StatementRow(
                    sortKey = sortKey,
                    ymdDate = ymd,
                    dateStr = dateStr,
                    opBal = 0.0,
                    qty = 0.0,
                    suit = 0.0,
                    kudumi = 0.0,
                    netQty = 0.0,
                    rate = 0.0,
                    amount = 0.0,
                    payment = paidAmt,
                    closingBal = 0.0
                )
            )
        }

        val sortedAll = rawRows.sortedBy { it.sortKey }
        var runningOpBal = baseOpeningBalance
        val result = arrayListOf<StatementRow>()

        for (r in sortedAll) {
            val isBeforeFrom = fromYmd != Long.MIN_VALUE && r.ymdDate != Long.MIN_VALUE && r.ymdDate < fromYmd
            val isAfterTo = toYmd != Long.MAX_VALUE && r.ymdDate != Long.MAX_VALUE && r.ymdDate > toYmd

            val rowOp = runningOpBal
            val rowClosing = (rowOp + r.amount - r.payment).coerceAtLeast(0.0)

            if (isBeforeFrom) {
                runningOpBal = rowClosing
                continue
            }
            if (isAfterTo) {
                break
            }

            result.add(r.copy(opBal = rowOp, closingBal = rowClosing))
            runningOpBal = rowClosing
        }

        return result
    }

    private fun renderEmptyGrid() {
        tlStatementGrid.removeAllViews()
        val headers = listOf("Date", "Op.Bal", "Qty", "Suit", "Kudumi", "NetQty", "Rate", "Amount", "Payment", "Closing Bal")
        val headerRow = TableRow(this).apply { setBackgroundColor(Color.parseColor("#1B5E20")) }
        for (h in headers) {
            val tv = TextView(this).apply {
                text = h
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                textSize = 12f
                setPadding(12, 10, 12, 10)
                gravity = if (h == "Date") Gravity.START else Gravity.END
            }
            headerRow.addView(tv)
        }
        tlStatementGrid.addView(headerRow)
    }

    private fun renderStatementGrid(rows: List<StatementRow>) {
        renderEmptyGrid()

        var sumQty = 0.0
        var sumSuit = 0.0
        var sumKudumi = 0.0
        var sumNetQty = 0.0
        var sumAmount = 0.0
        var sumPayment = 0.0
        var finalClosing = 0.0

        rows.forEachIndexed { idx, r ->
            sumQty += r.qty
            sumSuit += r.suit
            sumKudumi += r.kudumi
            sumNetQty += r.netQty
            sumAmount += r.amount
            sumPayment += r.payment
            finalClosing = r.closingBal

            val tr = TableRow(this).apply {
                setBackgroundColor(if (idx % 2 == 0) Color.WHITE else Color.parseColor("#F1F8E9"))
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
                "%.2f".format(Locale.getDefault(), r.closingBal)
            )

            vals.forEachIndexed { cIdx, txt ->
                val tv = TextView(this).apply {
                    text = txt
                    setTextColor(
                        when (cIdx) {
                            8 -> Color.parseColor("#1B5E20")
                            9 -> Color.parseColor("#C62828")
                            else -> Color.BLACK
                        }
                    )
                    textSize = 12f
                    setPadding(12, 10, 12, 10)
                    gravity = if (cIdx == 0) Gravity.START else Gravity.END
                    if (cIdx == 0 || cIdx == 5 || cIdx == 7 || cIdx == 9) setTypeface(null, Typeface.BOLD)
                }
                tr.addView(tv)
            }
            tlStatementGrid.addView(tr)
        }

        val totalRow = TableRow(this).apply { setBackgroundColor(Color.parseColor("#FFF9C4")) }
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
            "%.2f".format(Locale.getDefault(), finalClosing)
        )
        totalVals.forEachIndexed { cIdx, txt ->
            val tv = TextView(this).apply {
                text = txt
                setTextColor(Color.BLACK)
                setTypeface(null, Typeface.BOLD)
                textSize = 12f
                setPadding(12, 12, 12, 12)
                gravity = if (cIdx == 0) Gravity.START else Gravity.END
            }
            totalRow.addView(tv)
        }
        tlStatementGrid.addView(totalRow)
    }

    private fun exportStatementToPdf() {
        if (currentStatementRows.isEmpty()) {
            Toast.makeText(this, "Tap SUBMIT first to generate report", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val supplierName = actvSupplierName.text.toString().trim()
            val pdfDoc = PdfDocument()
            val pageWidth = 842
            val pageHeight = 595
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val headPaint = Paint().apply {
                color = Color.BLACK
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint().apply {
                color = Color.BLACK
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            var y = 36f
            canvas.drawText("SUPPLIER STATEMENT : $supplierName ($fromDateStr to $toDateStr)", 24f, y, titlePaint)
            y += 28f

            val colsX = floatArrayOf(24f, 105f, 185f, 255f, 325f, 400f, 475f, 555f, 640f, 725f)
            val headers = listOf("Date", "Op.Bal", "Qty", "Suit", "Kudumi", "NetQty", "Rate", "Amount", "Payment", "Closing Bal")
            headers.forEachIndexed { i, h -> canvas.drawText(h, colsX[i], y, headPaint) }

            y += 20f
            for (r in currentStatementRows) {
                if (y > pageHeight - 40f) break
                val rowVals = listOf(
                    r.dateStr,
                    "%.2f".format(Locale.getDefault(), r.opBal),
                    "%.2f".format(Locale.getDefault(), r.qty),
                    "%.2f".format(Locale.getDefault(), r.suit),
                    "%.2f".format(Locale.getDefault(), r.kudumi),
                    "%.2f".format(Locale.getDefault(), r.netQty),
                    "%.2f".format(Locale.getDefault(), r.rate),
                    "%.2f".format(Locale.getDefault(), r.amount),
                    "%.2f".format(Locale.getDefault(), r.payment),
                    "%.2f".format(Locale.getDefault(), r.closingBal)
                )
                rowVals.forEachIndexed { i, v -> canvas.drawText(v, colsX[i], y, bodyPaint) }
                y += 18f
            }

            pdfDoc.finishPage(page)

            val safeName = supplierName.replace("[^A-Za-z0-9_-]".toRegex(), "_")
            val fileName = "SupplierStatement_${safeName}_${System.currentTimeMillis()}.pdf"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/VeggieReports")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }

            val resolver = contentResolver
            val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out -> pdfDoc.writeTo(out) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                pdfDoc.close()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Export Supplier Statement PDF"))
            } else {
                pdfDoc.close()
                Toast.makeText(this, "Unable to create PDF", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "PDF Export Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
