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

class SupplierOutstandingActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var etFromDate: EditText
    private lateinit var etToDate: EditText
    private lateinit var spSupplierName: Spinner
    private lateinit var btnSubmit: Button
    private lateinit var btnExportPdf: Button
    private lateinit var tlOutstandingGrid: TableLayout

    private val supplierNames = arrayListOf<String>()
    private val supplierOpBalMap = HashMap<String, Double>()
    private lateinit var supplierAdapter: ArrayAdapter<String>

    private var fromDateStr: String = ""
    private var toDateStr: String = ""

    data class SupplierOutstandingRow(
        val supplierName: String,
        val opBalance: Double,
        val purchase: Double,
        val payment: Double,
        val less: Double,
        val outstanding: Double
    )

    private val currentRows = arrayListOf<SupplierOutstandingRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Supplier Outstanding"

        setContentView(buildScreenLayout())
        setupDates()
        renderTableHeaderOnly()
        loadSuppliers()

        btnSubmit.setOnClickListener {
            generateSupplierOutstanding()
        }

        btnExportPdf.setOnClickListener {
            exportSupplierOutstandingPdf()
        }
    }

    private fun buildScreenLayout(): ScrollView {
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

        spSupplierName = Spinner(this).apply {
            setBackgroundResource(android.R.drawable.btn_dropdown)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (46 * resources.displayMetrics.density).toInt()
            ).apply {
                setMargins(0, 0, 0, 14)
            }
        }
        supplierAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, supplierNames)
        supplierAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spSupplierName.adapter = supplierAdapter
        rootCol.addView(spSupplierName)

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

        tlOutstandingGrid = TableLayout(this).apply {
            setBackgroundColor(Color.parseColor("#607D8B"))
            setPadding(3, 3, 3, 3)
        }
        hScroll.addView(tlOutstandingGrid)
        rootCol.addView(hScroll)

        scrollView.addView(rootCol)
        return scrollView
    }

    private fun setupDates() {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val calTo = Calendar.getInstance()
        toDateStr = sdf.format(calTo.time)

        val calFrom = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
        fromDateStr = sdf.format(calFrom.time)

        etFromDate.setText(fromDateStr)
        etToDate.setText(toDateStr)

        etFromDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    calFrom.set(y, m, d)
                    fromDateStr = sdf.format(calFrom.time)
                    etFromDate.setText(fromDateStr)
                },
                calFrom.get(Calendar.YEAR),
                calFrom.get(Calendar.MONTH),
                calFrom.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        etToDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    calTo.set(y, m, d)
                    toDateStr = sdf.format(calTo.time)
                    etToDate.setText(toDateStr)
                },
                calTo.get(Calendar.YEAR),
                calTo.get(Calendar.MONTH),
                calTo.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun loadSuppliers() {
        db.collection("suppliers").get().addOnSuccessListener { snap ->
            supplierNames.clear()
            supplierOpBalMap.clear()
            supplierNames.add("ALL SUPPLIERS")

            for (doc in snap) {
                val name = (doc.getString("name") ?: "").trim()
                if (name.isEmpty()) continue
                val opBal = safeDouble(doc, "openingBalance").let {
                    if (it != 0.0) it else safeDouble(doc, "opBal")
                }
                supplierOpBalMap[name.lowercase(Locale.getDefault())] = opBal
                if (supplierNames.none { it.equals(name, ignoreCase = true) }) {
                    supplierNames.add(name)
                }
            }
            supplierAdapter.notifyDataSetChanged()
        }
    }

    private fun makeCell(
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
            setPadding(16, 12, 16, 12)
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

    private fun renderTableHeaderOnly() {
        tlOutstandingGrid.removeAllViews()
        val headers = listOf(
            "Supplier Name",
            "Op Balance",
            "Purchase",
            "Payment",
            "Less",
            "Outstanding"
        )
        val headerRow = TableRow(this).apply {
            setBackgroundColor(Color.parseColor("#607D8B"))
        }
        for (h in headers) {
            headerRow.addView(
                makeCell(
                    text = h,
                    bgColorHex = "#1B5E20",
                    textColorHex = "#FFFFFF",
                    alignRight = (h != "Supplier Name"),
                    bold = true
                )
            )
        }
        tlOutstandingGrid.addView(headerRow)
    }

    private fun generateSupplierOutstanding() {
        val selectedSup = spSupplierName.selectedItem?.toString()?.trim().orEmpty()
        if (selectedSup.isEmpty()) {
            Toast.makeText(this, "Select a Supplier Name", Toast.LENGTH_SHORT).show()
            return
        }

        val fromYmd = dateToYmd(fromDateStr)
        val toYmd = dateToYmd(toDateStr)

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

                val targetNames = if (selectedSup.equals("ALL SUPPLIERS", ignoreCase = true)) {
                    supplierNames.filter { !it.equals("ALL SUPPLIERS", ignoreCase = true) }
                } else {
                    listOf(selectedSup)
                }

                val rows = arrayListOf<SupplierOutstandingRow>()

                for (supName in targetNames) {
                    val baseOp = supplierOpBalMap[supName.lowercase(Locale.getDefault())] ?: 0.0
                    var beforePur = 0.0
                    var beforePay = 0.0
                    var beforeLess = 0.0
                    var periodPur = 0.0
                    var periodPay = 0.0
                    var periodLess = 0.0

                    for (doc in purchaseDocs) {
                        val s = (doc.getString("supplierName") ?: "").trim()
                        if (!s.equals(supName, ignoreCase = true)) continue

                        val ymd = dateToYmd((doc.getString("date") ?: "").trim())
                        val amt = extractPurchaseAmount(doc)

                        if (fromYmd != -1 && ymd != -1 && ymd < fromYmd) {
                            beforePur += amt
                        } else if ((fromYmd == -1 || ymd == -1 || ymd >= fromYmd) &&
                            (toYmd == -1 || ymd == -1 || ymd <= toYmd)
                        ) {
                            periodPur += amt
                        }
                    }

                    for (doc in paymentDocs) {
                        val s = (doc.getString("supplierName")
                            ?: doc.getString("partyName")
                            ?: doc.getString("name")
                            ?: "").trim()
                        if (!s.equals(supName, ignoreCase = true)) continue

                        val ymd = dateToYmd((doc.getString("date") ?: "").trim())
                        val paid = safeDouble(doc, "amount").let {
                            if (it > 0.0) it else safeDouble(doc, "paidAmount").let { p ->
                                if (p > 0.0) p else safeDouble(doc, "payment")
                            }
                        }
                        val lessAmt = extractLessAmount(doc)

                        if (fromYmd != -1 && ymd != -1 && ymd < fromYmd) {
                            beforePay += paid
                            beforeLess += lessAmt
                        } else if ((fromYmd == -1 || ymd == -1 || ymd >= fromYmd) &&
                            (toYmd == -1 || ymd == -1 || ymd <= toYmd)
                        ) {
                            periodPay += paid
                            periodLess += lessAmt
                        }
                    }

                    val opBal = (baseOp + beforePur - beforePay - beforeLess).coerceAtLeast(0.0)
                    val outAmt = (opBal + periodPur - periodPay - periodLess).coerceAtLeast(0.0)

                    rows.add(
                        SupplierOutstandingRow(
                            supplierName = supName,
                            opBalance = opBal,
                            purchase = periodPur,
                            payment = periodPay,
                            less = periodLess,
                            outstanding = outAmt
                        )
                    )
                }

                currentRows.clear()
                currentRows.addAll(rows)
                renderTable(rows)
            }
            .addOnFailureListener {
                btnSubmit.isEnabled = true
                Toast.makeText(this, "Failed to load supplier outstanding", Toast.LENGTH_SHORT).show()
            }
    }

    private fun renderTable(rows: List<SupplierOutstandingRow>) {
        renderTableHeaderOnly()

        var totOp = 0.0
        var totPur = 0.0
        var totPay = 0.0
        var totLess = 0.0
        var totOut = 0.0

        rows.forEachIndexed { idx, r ->
            totOp += r.opBalance
            totPur += r.purchase
            totPay += r.payment
            totLess += r.less
            totOut += r.outstanding

            val rowBg = if (idx % 2 == 0) "#FFFFFF" else "#F1F8E9"
            val tr = TableRow(this).apply {
                setBackgroundColor(Color.parseColor("#607D8B"))
            }

            val vals = listOf(
                r.supplierName,
                "%.2f".format(Locale.getDefault(), r.opBalance),
                "%.2f".format(Locale.getDefault(), r.purchase),
                "%.2f".format(Locale.getDefault(), r.payment),
                "%.2f".format(Locale.getDefault(), r.less),
                "%.2f".format(Locale.getDefault(), r.outstanding)
            )

            vals.forEachIndexed { cIdx, txt ->
                tr.addView(
                    makeCell(
                        text = txt,
                        bgColorHex = rowBg,
                        textColorHex = if (cIdx == 5) "#C62828" else "#000000",
                        alignRight = (cIdx != 0),
                        bold = (cIdx == 0 || cIdx == 5)
                    )
                )
            }
            tlOutstandingGrid.addView(tr)
        }

        val totalRow = TableRow(this).apply {
            setBackgroundColor(Color.parseColor("#607D8B"))
        }
        val totalVals = listOf(
            "TOTAL",
            "%.2f".format(Locale.getDefault(), totOp),
            "%.2f".format(Locale.getDefault(), totPur),
            "%.2f".format(Locale.getDefault(), totPay),
            "%.2f".format(Locale.getDefault(), totLess),
            "%.2f".format(Locale.getDefault(), totOut)
        )
        totalVals.forEachIndexed { cIdx, txt ->
            totalRow.addView(
                makeCell(
                    text = txt,
                    bgColorHex = "#FFF9C4",
                    textColorHex = if (cIdx == 5) "#C62828" else "#000000",
                    alignRight = (cIdx != 0),
                    bold = true
                )
            )
        }
        tlOutstandingGrid.addView(totalRow)
    }

    private fun exportSupplierOutstandingPdf() {
        if (currentRows.isEmpty()) {
            Toast.makeText(this, "Tap SUBMIT first to generate report", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfDoc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val headPaint = Paint().apply {
                color = Color.BLACK
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint().apply {
                color = Color.BLACK
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            var y = 38f
            canvas.drawText("SUPPLIER OUTSTANDING ($fromDateStr to $toDateStr)", 24f, y, titlePaint)
            y += 30f

            val colsX = floatArrayOf(24f, 220f, 340f, 460f, 580f, 695f)
            val headers = listOf(
                "Supplier Name",
                "Op Balance",
                "Purchase",
                "Payment",
                "Less",
                "Outstanding"
            )
            headers.forEachIndexed { i, h -> canvas.drawText(h, colsX[i], y, headPaint) }

            y += 22f
            for (r in currentRows) {
                if (y > 550f) break
                val rowVals = listOf(
                    r.supplierName,
                    "%.2f".format(Locale.getDefault(), r.opBalance),
                    "%.2f".format(Locale.getDefault(), r.purchase),
                    "%.2f".format(Locale.getDefault(), r.payment),
                    "%.2f".format(Locale.getDefault(), r.less),
                    "%.2f".format(Locale.getDefault(), r.outstanding)
                )
                rowVals.forEachIndexed { i, v -> canvas.drawText(v, colsX[i], y, bodyPaint) }
                y += 20f
            }

            pdfDoc.finishPage(page)

            val fileName = "SupplierOutstanding_${System.currentTimeMillis()}.pdf"
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
                startActivity(Intent.createChooser(shareIntent, "Export Supplier Outstanding PDF"))
            } else {
                pdfDoc.close()
                Toast.makeText(this, "Unable to create PDF", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "PDF Export Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun extractLessAmount(doc: DocumentSnapshot): Double {
        val candidates = listOf("less", "discount", "discAmount", "deduction", "lessAmount")
        for (field in candidates) {
            val v = safeDouble(doc, field)
            if (v != 0.0) return v
        }
        return 0.0
    }

    private fun extractPurchaseAmount(doc: DocumentSnapshot): Double {
        val grandTot = safeDouble(doc, "grandTotal")
        if (grandTot > 0.0) return grandTot
        val directAmt = safeDouble(doc, "amount")
        if (directAmt > 0.0) return directAmt

        val rawItems = doc.get("items") as? List<*> ?: return 0.0
        var sum = 0.0
        for (entry in rawItems) {
            val m = entry as? Map<*, *> ?: continue
            val lineTot = (m["lineTotal"] as? Number)?.toDouble() ?: (m["amount"] as? Number)?.toDouble()
            if (lineTot != null) {
                sum += lineTot
            } else {
                val kgs = (m["netKgs"] as? Number)?.toDouble() ?: (m["purchaseKgs"] as? Number)?.toDouble() ?: 0.0
                val rate = (m["ratePerKg"] as? Number)?.toDouble() ?: (m["rate"] as? Number)?.toDouble() ?: 0.0
                val bags = (m["bags"] as? Number)?.toDouble() ?: 0.0
                val bagRate = (m["bagRate"] as? Number)?.toDouble() ?: 0.0
                sum += (kgs * rate) + (bags * bagRate)
            }
        }
        return sum
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
        return if (p0 > 1000) (p0 * 10000) + (p1 * 100) + p2
        else ((if (p2 < 100) 2000 + p2 else p2) * 10000) + (p1 * 100) + p0
    }
}
