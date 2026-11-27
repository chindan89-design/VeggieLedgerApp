package com.shop.veggieledger

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class DailySalesEditActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var etSearchDate: EditText
    private lateinit var btnSearch: Button
    private lateinit var tvSummaryBar: TextView
    private lateinit var tableSales: TableLayout

    private var selectedDateStr: String = ""

    data class SaleEntryRow(
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
        val credit: Double,
        val itemName: String,
        val stockId: String,
        val bags: Double,
        val bagCharge: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Daily Sales Report (Entry Index)"

        setContentView(buildScreenUi())
        setupDatePicker()
        renderTableHeader()

        searchSalesByDate()

        btnSearch.setOnClickListener {
            searchSalesByDate()
        }
    }

    private fun buildScreenUi(): ScrollView {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F1F5F9"))
            setPadding(18, 18, 18, 28)
        }

        val rootCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
        }

        val tvTitle = TextView(this).apply {
            text = "DAILY SALES REPORT (BY ENTRY INDEX)"
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B5E20"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }
        headerCard.addView(tvTitle)

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 3f
        }

        etSearchDate = EditText(this).apply {
            hint = "Select Date"
            isFocusable = false
            isClickable = true
            setBackgroundResource(android.R.drawable.edit_text)
            setPadding(16, 12, 16, 12)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f).apply {
                setMargins(0, 0, 8, 0)
            }
        }

        btnSearch = Button(this).apply {
            text = "SEARCH"
            setBackgroundColor(Color.parseColor("#1B5E20"))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            textSize = 13.5f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
        }

        filterRow.addView(etSearchDate)
        filterRow.addView(btnSearch)
        headerCard.addView(filterRow)
        rootCol.addView(headerCard)

        tvSummaryBar = TextView(this).apply {
            text = "Loading sales..."
            setBackgroundColor(Color.parseColor("#FFF9C4"))
            setTextColor(Color.parseColor("#D84315"))
            setTypeface(null, Typeface.BOLD)
            textSize = 13f
            setPadding(14, 10, 14, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
        }
        rootCol.addView(tvSummaryBar)

        val hScroll = HorizontalScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tableSales = TableLayout(this).apply {
            setBackgroundColor(Color.parseColor("#64748B"))
            setPadding(2, 2, 2, 2)
        }
        hScroll.addView(tableSales)
        rootCol.addView(hScroll)

        rootScroll.addView(rootCol)
        return rootScroll
    }

    private fun setupDatePicker() {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()
        selectedDateStr = sdf.format(cal.time)
        etSearchDate.setText(selectedDateStr)

        etSearchDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    cal.set(y, m, d)
                    selectedDateStr = sdf.format(cal.time)
                    etSearchDate.setText(selectedDateStr)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun makeCell(
        text: String,
        bgHex: String,
        textHex: String = "#000000",
        alignRight: Boolean = false,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            this.text = text
            setBackgroundColor(Color.parseColor(bgHex))
            setTextColor(Color.parseColor(textHex))
            textSize = 12f
            setPadding(12, 10, 12, 10)
            gravity = if (alignRight) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
            if (bold) setTypeface(null, Typeface.BOLD)
            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(1, 1, 1, 1)
            }
        }
    }

    private fun makeActionButton(label: String, bgHex: String, onClick: () -> Unit): Button {
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
            setPadding(16, 8, 16, 8)
            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(2, 2, 2, 2)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun renderTableHeader() {
        tableSales.removeAllViews()
        val headers = listOf(
            "Idx",
            "Bill No",
            "Party Name",
            "Qty",
            "Rate",
            "Amount",
            "GPay",
            "Cash",
            "Credit",
            "Edit",
            "Delete"
        )
        val hRow = TableRow(this).apply {
            setBackgroundColor(Color.parseColor("#64748B"))
        }
        for (h in headers) {
            val isRight = h in listOf("Idx", "Qty", "Rate", "Amount", "GPay", "Cash", "Credit")
            hRow.addView(
                makeCell(
                    text = h,
                    bgHex = "#1B5E20",
                    textHex = "#FFFFFF",
                    alignRight = isRight,
                    bold = true
                )
            )
        }
        tableSales.addView(hRow)
    }

    private fun searchSalesByDate() {
        val targetYmd = dateToYmd(selectedDateStr)
        btnSearch.isEnabled = false
        tvSummaryBar.text = "Searching sales for $selectedDateStr..."

        db.collection("sales").get()
            .addOnSuccessListener { snap ->
                btnSearch.isEnabled = true
                val rows = arrayListOf<SaleEntryRow>()

                for ((docListPos, doc) in snap.documents.withIndex()) {
                    val dStr = (doc.getString("date") ?: "").trim()
                    val dYmd = dateToYmd(dStr)
                    if (targetYmd != -1 && dYmd != targetYmd) continue

                    rows.add(parseSaleDoc(doc, docListPos.toLong()))
                }

                // Strictly order by entryIndex (0, 1, 2, 3...) so entries appear in exact entry index order
                rows.sortWith(
                    compareBy<SaleEntryRow> { it.entryIndex }
                        .thenBy { it.docId }
                )
                renderSalesRows(rows)
            }
            .addOnFailureListener { e ->
                btnSearch.isEnabled = true
                tvSummaryBar.text = "Error: ${e.message}"
                Toast.makeText(this, "Failed to load sales", Toast.LENGTH_SHORT).show()
            }
    }

    private fun extractEntryIndex(doc: DocumentSnapshot, fallbackPos: Long): Long {
        if (doc.contains("entryIndex")) {
            val raw = doc.get("entryIndex")
            val idxVal = when (raw) {
                is Number -> raw.toLong()
                is String -> raw.trim().toLongOrNull()
                else -> null
            }
            if (idxVal != null && idxVal >= 0L) return idxVal
        }

        val altOrder = safeLong(doc, "sequenceNo").let {
            if (it > 0L) it else safeLong(doc, "entryOrder").let { eo ->
                if (eo > 0L) eo else safeLong(doc, "rowIndex")
            }
        }
        if (altOrder > 0L) return altOrder

        val ts = safeLong(doc, "timestamp")
        if (ts > 0L) return ts

        return 9_000_000_000_000L + fallbackPos
    }

    // Helper to save a new sale entry with sequential entryIndex (0, 1, 2...) for the given date
    fun saveNewSaleWithIndex(saleMap: MutableMap<String, Any>, onDone: (Boolean) -> Unit = {}) {
        val dateVal = (saleMap["date"] as? String)?.trim().orEmpty()
        val targetYmd = dateToYmd(dateVal)

        db.collection("sales").get()
            .addOnSuccessListener { snap ->
                var maxIdx = -1L
                for (doc in snap.documents) {
                    val dYmd = dateToYmd((doc.getString("date") ?: "").trim())
                    if (targetYmd != -1 && dYmd == targetYmd) {
                        val existingIdx = safeLong(doc, "entryIndex")
                        if (doc.contains("entryIndex") && existingIdx > maxIdx) {
                            maxIdx = existingIdx
                        }
                    }
                }
                val nextIndex = maxIdx + 1L
                saleMap["entryIndex"] = nextIndex
                if (!saleMap.containsKey("timestamp")) {
                    saleMap["timestamp"] = System.currentTimeMillis()
                }

                db.collection("sales").add(saleMap)
                    .addOnSuccessListener { onDone(true) }
                    .addOnFailureListener { onDone(false) }
            }
            .addOnFailureListener {
                saleMap["entryIndex"] = System.currentTimeMillis()
                db.collection("sales").add(saleMap)
                    .addOnSuccessListener { onDone(true) }
                    .addOnFailureListener { onDone(false) }
            }
    }

    private fun parseSaleDoc(doc: DocumentSnapshot, fallbackPos: Long): SaleEntryRow {
        val idxVal = extractEntryIndex(doc, fallbackPos)
        val billNo = (doc.getString("billNo") ?: doc.id).trim()
        val dateStr = (doc.getString("date") ?: selectedDateStr).trim()
        val partyName = (doc.getString("customerName") ?: doc.getString("partyName") ?: "CASH").trim()
        val itemName = (doc.getString("itemName") ?: "COCONUT").trim()
        val stockId = (doc.getString("stockId") ?: "sto-25-0").trim()
        val bags = safeDouble(doc, "bags")
        val bagCharge = safeDouble(doc, "bagCharge")

        var qty = safeDouble(doc, "netKgs").let {
            if (it > 0.0) it else safeDouble(doc, "quantity").let { q ->
                if (q > 0.0) q else safeDouble(doc, "qty")
            }
        }
        var rate = safeDouble(doc, "rate").let {
            if (it > 0.0) it else safeDouble(doc, "ratePerKg")
        }
        var amount = safeDouble(doc, "grandTotal").let {
            if (it > 0.0) it else safeDouble(doc, "amount")
        }

        val rawItems = doc.get("items") as? List<*>
        if ((qty <= 0.0 || rate <= 0.0) && rawItems != null && rawItems.isNotEmpty()) {
            val first = rawItems[0] as? Map<*, *>
            if (first != null) {
                if (qty <= 0.0) {
                    qty = (first["netKgs"] as? Number)?.toDouble()
                        ?: (first["qty"] as? Number)?.toDouble()
                        ?: 0.0
                }
                if (rate <= 0.0) {
                    rate = (first["rate"] as? Number)?.toDouble()
                        ?: (first["ratePerKg"] as? Number)?.toDouble()
                        ?: 0.0
                }
                if (amount <= 0.0) {
                    amount = (first["amount"] as? Number)?.toDouble()
                        ?: (first["lineTotal"] as? Number)?.toDouble()
                        ?: (qty * rate)
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
                (amount - cashAmt - gpayAmt).coerceAtLeast(0.0)
            }
        } else {
            when {
                mode.equals("Cash", ignoreCase = true) -> {
                    cashAmt = amount; gpayAmt = 0.0; creditAmt = 0.0
                }
                mode.contains("GPay", ignoreCase = true) || mode.contains("UPI", ignoreCase = true) -> {
                    cashAmt = 0.0; gpayAmt = amount; creditAmt = 0.0
                }
                else -> {
                    cashAmt = 0.0; gpayAmt = 0.0; creditAmt = amount
                }
            }
        }

        return SaleEntryRow(
            docId = doc.id,
            entryIndex = idxVal,
            billNo = billNo,
            date = dateStr,
            partyName = partyName,
            qty = qty,
            rate = rate,
            amount = amount,
            gpay = gpayAmt,
            cash = cashAmt,
            credit = creditAmt,
            itemName = itemName,
            stockId = stockId,
            bags = bags,
            bagCharge = bagCharge
        )
    }

    private fun renderSalesRows(rows: List<SaleEntryRow>) {
        renderTableHeader()

        var totQty = 0.0
        var totAmt = 0.0
        var totGpay = 0.0
        var totCash = 0.0
        var totCredit = 0.0

        rows.forEachIndexed { displayPos, r ->
            totQty += r.qty
            totAmt += r.amount
            totGpay += r.gpay
            totCash += r.cash
            totCredit += r.credit

            val bg = if (displayPos % 2 == 0) "#FFFFFF" else "#F1F8E9"
            val tr = TableRow(this).apply {
                setBackgroundColor(Color.parseColor("#64748B"))
            }

            val indexLabel = if (r.entryIndex < 9_000_000_000_000L && r.entryIndex < 10_000_000L) {
                "${r.entryIndex}"
            } else {
                "$displayPos"
            }

            tr.addView(makeCell(indexLabel, bg, "#334155", alignRight = true, bold = true))
            tr.addView(makeCell(r.billNo, bg, "#1B5E20", alignRight = false, bold = true))
            tr.addView(makeCell(r.partyName, bg, "#000000", alignRight = false, bold = true))
            tr.addView(makeCell(fmtQty(r.qty), bg, "#000000", alignRight = true, bold = false))
            tr.addView(makeCell(fmtAmt(r.rate), bg, "#000000", alignRight = true, bold = false))
            tr.addView(makeCell(fmtAmt(r.amount), bg, "#000000", alignRight = true, bold = true))
            tr.addView(makeCell(fmtAmt(r.gpay), bg, "#1565C0", alignRight = true, bold = false))
            tr.addView(makeCell(fmtAmt(r.cash), bg, "#2E7D32", alignRight = true, bold = false))
            tr.addView(makeCell(fmtAmt(r.credit), bg, "#C62828", alignRight = true, bold = false))

            tr.addView(makeActionButton("EDIT", "#1565C0") {
                showEditDialog(r)
            })
            tr.addView(makeActionButton("DELETE", "#C62828") {
                confirmDeleteSale(r)
            })

            tableSales.addView(tr)
        }

        val totRow = TableRow(this).apply {
            setBackgroundColor(Color.parseColor("#64748B"))
        }
        totRow.addView(makeCell("", "#FFF9C4", "#000000", alignRight = true, bold = true))
        totRow.addView(makeCell("TOTAL", "#FFF9C4", "#000000", alignRight = false, bold = true))
        totRow.addView(makeCell("${rows.size} Bills", "#FFF9C4", "#000000", alignRight = false, bold = true))
        totRow.addView(makeCell(fmtQty(totQty), "#FFF9C4", "#000000", alignRight = true, bold = true))
        totRow.addView(makeCell("-", "#FFF9C4", "#000000", alignRight = true, bold = true))
        totRow.addView(makeCell(fmtAmt(totAmt), "#FFF9C4", "#000000", alignRight = true, bold = true))
        totRow.addView(makeCell(fmtAmt(totGpay), "#FFF9C4", "#1565C0", alignRight = true, bold = true))
        totRow.addView(makeCell(fmtAmt(totCash), "#FFF9C4", "#2E7D32", alignRight = true, bold = true))
        totRow.addView(makeCell(fmtAmt(totCredit), "#FFF9C4", "#C62828", alignRight = true, bold = true))
        totRow.addView(makeCell("", "#FFF9C4", "#000000", alignRight = false, bold = true))
        totRow.addView(makeCell("", "#FFF9C4", "#000000", alignRight = false, bold = true))
        tableSales.addView(totRow)

        tvSummaryBar.text = String.format(
            Locale.getDefault(),
            "Date: %s | Bills: %d | Qty: %s | Total: Rs. %s (GPay: %s | Cash: %s | Credit: %s)",
            selectedDateStr,
            rows.size,
            fmtQty(totQty),
            fmtAmt(totAmt),
            fmtAmt(totGpay),
            fmtAmt(totCash),
            fmtAmt(totCredit)
        )
    }

    private fun showEditDialog(row: SaleEntryRow) {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 12)
        }

        fun addLabeledInput(label: String, initial: String, numeric: Boolean): EditText {
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
            dialogLayout.addView(tv)
            dialogLayout.addView(et)
            return et
        }

        val etParty = addLabeledInput("Party Name", row.partyName, false)
        val etQty = addLabeledInput("Quantity (Net Kgs)", fmtQty(row.qty), true)
        val etRate = addLabeledInput("Rate", fmtAmt(row.rate), true)
        val etAmount = addLabeledInput("Amount (Auto = Qty x Rate)", fmtAmt(row.amount), true)
        val etGpay = addLabeledInput("GPay", fmtAmt(row.gpay), true)
        val etCash = addLabeledInput("Cash", fmtAmt(row.cash), true)
        val etCredit = addLabeledInput("Credit", fmtAmt(row.credit), true)

        val recalcWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = etQty.text.toString().toDoubleOrNull() ?: 0.0
                val r = etRate.text.toString().toDoubleOrNull() ?: 0.0
                val newAmt = q * r
                etAmount.setText(String.format(Locale.US, "%.2f", newAmt))
            }
        }
        etQty.addTextChangedListener(recalcWatcher)
        etRate.addTextChangedListener(recalcWatcher)

        AlertDialog.Builder(this)
            .setTitle("Edit Sale (${row.billNo})")
            .setView(dialogLayout)
            .setPositiveButton("UPDATE") { _, _ ->
                val newParty = etParty.text.toString().trim().ifEmpty { "CASH" }
                val newQty = etQty.text.toString().toDoubleOrNull() ?: 0.0
                val newRate = etRate.text.toString().toDoubleOrNull() ?: 0.0
                val newAmt = etAmount.text.toString().toDoubleOrNull() ?: (newQty * newRate)
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
                        searchSalesByDate()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun confirmDeleteSale(row: SaleEntryRow) {
        AlertDialog.Builder(this)
            .setTitle("Delete Sale Bill?")
            .setMessage("Are you sure you want to delete ${row.billNo} (${row.partyName} - Rs. ${fmtAmt(row.amount)})?")
            .setPositiveButton("DELETE") { _, _ ->
                db.collection("sales").document(row.docId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "${row.billNo} Deleted!", Toast.LENGTH_SHORT).show()
                        searchSalesByDate()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("CANCEL", null)
            .show()
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

    private fun fmtAmt(v: Double): String {
        return String.format(Locale.getDefault(), "%.2f", v)
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
        return if (p0 > 1000) (p0 * 10000) + (p1 * 100) + p2
        else ((if (p2 < 100) 2000 + p2 else p2) * 10000) + (p1 * 100) + p0
    }
}
