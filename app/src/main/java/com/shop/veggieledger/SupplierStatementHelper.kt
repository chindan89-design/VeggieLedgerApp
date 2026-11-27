package com.shop.veggieledger

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
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

class SupplierStatementActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var etFromDate: EditText
    private lateinit var etToDate: EditText
    private lateinit var spSupplierFilter: Spinner
    private lateinit var tvSummaryBanner: TextView
    private lateinit var tlStatementGrid: TableLayout

    private val supplierList = arrayListOf<SupplierInfo>()
    private val supplierSpinnerNames = arrayListOf("All Suppliers")
    private lateinit var supplierAdapter: ArrayAdapter<String>

    private var fromDateStr: String = ""
    private var toDateStr: String = ""
    private var isUpdatingSpinner: Boolean = false

    data class SupplierInfo(
        val id: String,
        val name: String,
        val openingBalance: Double
    )

    data class StatementRow(
        val sortKey: Long,
        val dateStr: String,
        val supplierName: String,
        val opBal: Double,
        val purchaseLabel: String,
        val qty: Double,
        val suit: Double,
        val kudumi: Double,
        val rate: Double,
        val amount: Double,
        val payment: Double,
        val discount: Double,
        val outstanding: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Supplier Statement"

        val rootView = buildScreenLayout()
        setContentView(rootView)

        val cal = Calendar.getInstance()
        toDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, -30)
        fromDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)

        etFromDate.setText(fromDateStr)
        etToDate.setText(toDateStr)

        etFromDate.setOnClickListener {
            showDatePicker(etFromDate) { picked ->
                fromDateStr = picked
                loadSupplierStatement()
            }
        }

        etToDate.setOnClickListener {
            showDatePicker(etToDate) { picked ->
                toDateStr = picked
                loadSupplierStatement()
            }
        }

        supplierAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, supplierSpinnerNames)
        supplierAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spSupplierFilter.adapter = supplierAdapter

        spSupplierFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (!isUpdatingSpinner) {
                    loadSupplierStatement()
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        loadSuppliersThenStatement()
    }

    private fun buildScreenLayout(): ScrollView {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F5F7FA"))
            setPadding(24, 24, 24, 32)
        }

        val mainCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleTv = TextView(this).apply {
            text = "SUPPLIER STATEMENT (DATE SORTED)"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B5E20"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        mainCol.addView(titleTv)

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
        mainCol.addView(dateRow)

        val lblSup = TextView(this).apply {
            text = "Select Supplier"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B5E20"))
            setPadding(4, 0, 0, 4)
        }
        mainCol.addView(lblSup)

        spSupplierFilter = Spinner(this).apply {
            setBackgroundResource(android.R.drawable.btn_dropdown)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (46 * resources.displayMetrics.density).toInt()
            ).apply {
                setMargins(0, 0, 0, 12)
            }
        }
        mainCol.addView(spSupplierFilter)

        val btnRefresh = Button(this).apply {
            text = "REFRESH STATEMENT"
            setBackgroundColor(Color.parseColor("#1B5E20"))
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 14)
            }
            setOnClickListener { loadSupplierStatement() }
        }
        mainCol.addView(btnRefresh)

        tvSummaryBanner = TextView(this).apply {
            text = "Total Outstanding: Rs. 0.00"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#C62828"))
            setBackgroundColor(Color.parseColor("#FFF9C4"))
            setPadding(16, 14, 16, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 14)
            }
        }
        mainCol.addView(tvSummaryBanner)

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
        mainCol.addView(hScroll)

        scrollView.addView(mainCol)
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

    private fun loadSuppliersThenStatement() {
        db.collection("suppliers").get().addOnSuccessListener { snap ->
            supplierList.clear()
            isUpdatingSpinner = true
            supplierSpinnerNames.clear()
            supplierSpinnerNames.add("All Suppliers")

            for (doc in snap) {
                val name = (doc.getString("name") ?: "").trim()
                if (name.isEmpty()) continue
                val opBal = doc.getDouble("openingBalance")
                    ?: doc.getDouble("opBal")
                    ?: 0.0
                supplierList.add(SupplierInfo(doc.id, name, opBal))
                if (supplierSpinnerNames.none { it.equals(name, ignoreCase = true) }) {
                    supplierSpinnerNames.add(name)
                }
            }

            supplierAdapter.notifyDataSetChanged()
            isUpdatingSpinner = false
            loadSupplierStatement()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load suppliers", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSupplierStatement() {
        val selectedSupplier = spSupplierFilter.selectedItem?.toString() ?: "All Suppliers"

        val purchasesTask = db.collection("purchases").get()
        val supplierPaymentsTask = db.collection("supplier_payments").get()
        val paymentsTask = db.collection("payments").get()

        Tasks.whenAllComplete(purchasesTask, supplierPaymentsTask, paymentsTask)
            .addOnSuccessListener {
                val purchaseDocs = if (purchasesTask.isSuccessful) {
                    (purchasesTask.result as? QuerySnapshot)?.documents.orEmpty()
                } else emptyList()

                val combinedPaymentDocs = arrayListOf<DocumentSnapshot>()
                if (supplierPaymentsTask.isSuccessful) {
                    combinedPaymentDocs.addAll((supplierPaymentsTask.result as? QuerySnapshot)?.documents.orEmpty())
                }
                if (paymentsTask.isSuccessful) {
                    for (doc in (paymentsTask.result as? QuerySnapshot)?.documents.orEmpty()) {
                        val partyType = (doc.getString("partyType") ?: doc.getString("type") ?: "").trim()
                        val hasSupplierField = !(doc.getString("supplierName") ?: "").isNullOrBlank()
                        if (partyType.equals("supplier", ignoreCase = true) || hasSupplierField) {
                            combinedPaymentDocs.add(doc)
                        }
                    }
                }

                val fromKey = dateToYmdNumber(fromDateStr)
                val toKey = dateToYmdNumber(toDateStr)

                val allRowsSorted = if (selectedSupplier == "All Suppliers") {
                    val groupedRows = arrayListOf<StatementRow>()
                    for (sup in supplierList) {
                        groupedRows.addAll(
                            buildSortedStatementRows(
                                supplierOpeningBalance = sup.openingBalance,
                                purchaseDocs = purchaseDocs,
                                paymentDocs = combinedPaymentDocs,
                                selectedSupplierFilter = sup.name
                            )
                        )
                    }
                    groupedRows.sortedBy { it.sortKey }
                } else {
                    val matchedSup = supplierList.firstOrNull { it.name.equals(selectedSupplier, ignoreCase = true) }
                    buildSortedStatementRows(
                        supplierOpeningBalance = matchedSup?.openingBalance ?: 0.0,
                        purchaseDocs = purchaseDocs,
                        paymentDocs = combinedPaymentDocs,
                        selectedSupplierFilter = selectedSupplier
                    )
                }

                val filteredByDate = allRowsSorted.filter { row ->
                    val rowYmd = row.sortKey / 1_000_000L
                    (fromKey == Long.MIN_VALUE || rowYmd >= fromKey) &&
                        (toKey == Long.MAX_VALUE || rowYmd <= toKey)
                }

                val latestOut = renderStatementTable(tlStatementGrid, filteredByDate)
                tvSummaryBanner.text = String.format(
                    Locale.getDefault(),
                    "%s | Closing Outstanding: Rs. %.2f",
                    selectedSupplier,
                    latestOut
                )
            }
    }

    private fun parseDateSortKey(rawDate: String, fallbackMillis: Long = 0L): Long {
        val clean = rawDate.trim()
        val formats = listOf(
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        )
        for (fmt in formats) {
            try {
                val dt = fmt.parse(clean)
                if (dt != null) {
                    val ymd = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(dt).toLong()
                    return (ymd * 1_000_000L) + ((fallbackMillis / 1000L) % 1_000_000L)
                }
            } catch (_: Exception) {
            }
        }
        return fallbackMillis
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

    private fun buildSortedStatementRows(
        supplierOpeningBalance: Double,
        purchaseDocs: List<DocumentSnapshot>,
        paymentDocs: List<DocumentSnapshot>,
        selectedSupplierFilter: String
    ): List<StatementRow> {
        val rawEvents = arrayListOf<StatementRow>()

        for (doc in purchaseDocs) {
            val supName = (doc.getString("supplierName") ?: "").trim()
            if (supName.isEmpty()) continue
            if (selectedSupplierFilter != "All Suppliers" &&
                !supName.equals(selectedSupplierFilter, ignoreCase = true)
            ) continue

            val dateStr = (doc.getString("date") ?: "").trim()
            val ts = doc.getLong("timestamp") ?: 0L
            val sortKey = parseDateSortKey(dateStr, ts)
            val billNo = (doc.getString("billNo") ?: "").trim()
            val batchId = (doc.getString("batchId") ?: "").trim()
            val purTag = when {
                billNo.isNotEmpty() && batchId.isNotEmpty() -> "$billNo ($batchId)"
                billNo.isNotEmpty() -> billNo
                batchId.isNotEmpty() -> batchId
                else -> "Purchase"
            }

            val rawItems = doc.get("items") as? List<*>
            if (rawItems != null && rawItems.isNotEmpty()) {
                for (entry in rawItems) {
                    val m = entry as? Map<*, *> ?: continue
                    val itemName = m["itemName"]?.toString()?.trim() ?: "Item"
                    val nos = (m["nos"] as? Number)?.toDouble() ?: 0.0
                    val bags = (m["bags"] as? Number)?.toDouble() ?: 0.0
                    val purKgs = (m["purchaseKgs"] as? Number)?.toDouble() ?: 0.0
                    val netKgs = (m["netKgs"] as? Number)?.toDouble() ?: 0.0

                    val qtyVal = when {
                        purKgs > 0.0 -> purKgs
                        netKgs > 0.0 -> netKgs
                        bags > 0.0 -> bags
                        else -> nos
                    }

                    val suitVal = (m["suit"] as? Number)?.toDouble()
                        ?: (m["thadukki"] as? Number)?.toDouble()
                        ?: 0.0
                    val kudumiVal = (m["kudumi"] as? Number)?.toDouble() ?: 0.0

                    val rateVal = (m["ratePerKg"] as? Number)?.toDouble()
                        ?: (m["rate"] as? Number)?.toDouble()
                        ?: 0.0

                    val computedNet = if (purKgs > 0.0) (purKgs - suitVal - kudumiVal).coerceAtLeast(0.0) else qtyVal
                    val lineAmt = (m["lineTotal"] as? Number)?.toDouble()
                        ?: (m["amount"] as? Number)?.toDouble()
                        ?: (computedNet * rateVal)

                    rawEvents.add(
                        StatementRow(
                            sortKey = sortKey,
                            dateStr = dateStr,
                            supplierName = supName,
                            opBal = 0.0,
                            purchaseLabel = "$purTag - $itemName",
                            qty = qtyVal,
                            suit = suitVal,
                            kudumi = kudumiVal,
                            rate = rateVal,
                            amount = lineAmt,
                            payment = 0.0,
                            discount = 0.0,
                            outstanding = 0.0
                        )
                    )
                }
            } else {
                val purKgs = (doc.getDouble("purchaseKgs") ?: doc.getDouble("netKgs") ?: 0.0)
                val suitVal = (doc.getDouble("suit") ?: doc.getDouble("thadukki") ?: 0.0)
                val kudumiVal = (doc.getDouble("kudumi") ?: 0.0)
                val rateVal = (doc.getDouble("ratePerKg") ?: doc.getDouble("rate") ?: 0.0)
                val amtVal = (doc.getDouble("grandTotal") ?: doc.getDouble("amount") ?: (purKgs * rateVal))

                rawEvents.add(
                    StatementRow(
                        sortKey = sortKey,
                        dateStr = dateStr,
                        supplierName = supName,
                        opBal = 0.0,
                        purchaseLabel = purTag,
                        qty = purKgs,
                        suit = suitVal,
                        kudumi = kudumiVal,
                        rate = rateVal,
                        amount = amtVal,
                        payment = 0.0,
                        discount = 0.0,
                        outstanding = 0.0
                    )
                )
            }
        }

        for (doc in paymentDocs) {
            val supName = (doc.getString("supplierName")
                ?: doc.getString("partyName")
                ?: doc.getString("name")
                ?: "").trim()
            if (supName.isEmpty()) continue
            if (selectedSupplierFilter != "All Suppliers" &&
                !supName.equals(selectedSupplierFilter, ignoreCase = true)
            ) continue

            val dateStr = (doc.getString("date") ?: "").trim()
            val ts = doc.getLong("timestamp") ?: 0L
            val sortKey = parseDateSortKey(dateStr, ts)

            val paidAmt = (doc.getDouble("amount")
                ?: doc.getDouble("paidAmount")
                ?: doc.getDouble("payment")
                ?: 0.0)
            val discAmt = (doc.getDouble("discount")
                ?: doc.getDouble("discAmount")
                ?: doc.getDouble("deduction")
                ?: 0.0)

            val refNo = (doc.getString("receiptNo")
                ?: doc.getString("voucherNo")
                ?: doc.getString("payMode")
                ?: "Payment").trim()

            rawEvents.add(
                StatementRow(
                    sortKey = sortKey,
                    dateStr = dateStr,
                    supplierName = supName,
                    opBal = 0.0,
                    purchaseLabel = refNo,
                    qty = 0.0,
                    suit = 0.0,
                    kudumi = 0.0,
                    rate = 0.0,
                    amount = 0.0,
                    payment = paidAmt,
                    discount = discAmt,
                    outstanding = 0.0
                )
            )
        }

        val sortedEvents = rawEvents.sortedBy { row -> row.sortKey }

        var runningBalance = supplierOpeningBalance
        val finalRows = ArrayList<StatementRow>(sortedEvents.size)

        for (ev in sortedEvents) {
            val rowOpBal = runningBalance
            val rowClosingOut = (rowOpBal + ev.amount - ev.payment - ev.discount).coerceAtLeast(0.0)
            finalRows.add(
                ev.copy(
                    opBal = rowOpBal,
                    outstanding = rowClosingOut
                )
            )
            runningBalance = rowClosingOut
        }

        return finalRows
    }

    private fun renderStatementTable(tableLayout: TableLayout, rows: List<StatementRow>): Double {
        tableLayout.removeAllViews()

        val headers = listOf(
            "Date",
            "Name",
            "Op.Bal",
            "Purchase",
            "Qty",
            "Suit",
            "Kudumi",
            "Rate",
            "Amount",
            "Payment",
            "Discount",
            "Outstanding"
        )

        val headerRow = TableRow(tableLayout.context)
        headerRow.setBackgroundColor(Color.parseColor("#1B5E20"))
        for (h in headers) {
            val tv = TextView(tableLayout.context)
            tv.text = h
            tv.setTextColor(Color.WHITE)
            tv.setTypeface(null, Typeface.BOLD)
            tv.textSize = 12f
            tv.setPadding(12, 10, 12, 10)
            tv.gravity = if (h == "Date" || h == "Name" || h == "Purchase") Gravity.START else Gravity.END
            headerRow.addView(tv)
        }
        tableLayout.addView(headerRow)

        var totalPurchaseAmt = 0.0
        var totalPaidAmt = 0.0
        var totalDiscAmt = 0.0
        var latestOutstanding = 0.0

        rows.forEachIndexed { idx, r ->
            totalPurchaseAmt += r.amount
            totalPaidAmt += r.payment
            totalDiscAmt += r.discount
            latestOutstanding = r.outstanding

            val tr = TableRow(tableLayout.context)
            tr.setBackgroundColor(if (idx % 2 == 0) Color.WHITE else Color.parseColor("#F1F8E9"))

            val cells = listOf(
                r.dateStr,
                r.supplierName,
                "%.2f".format(Locale.getDefault(), r.opBal),
                r.purchaseLabel,
                if (r.qty > 0.0) "%.2f".format(Locale.getDefault(), r.qty) else "-",
                if (r.suit > 0.0) "%.2f".format(Locale.getDefault(), r.suit) else "-",
                if (r.kudumi > 0.0) "%.2f".format(Locale.getDefault(), r.kudumi) else "-",
                if (r.rate > 0.0) "%.2f".format(Locale.getDefault(), r.rate) else "-",
                if (r.amount > 0.0) "%.2f".format(Locale.getDefault(), r.amount) else "-",
                if (r.payment > 0.0) "%.2f".format(Locale.getDefault(), r.payment) else "-",
                if (r.discount > 0.0) "%.2f".format(Locale.getDefault(), r.discount) else "-",
                "%.2f".format(Locale.getDefault(), r.outstanding)
            )

            cells.forEachIndexed { cIdx, txt ->
                val tv = TextView(tableLayout.context)
                tv.text = txt
                tv.setTextColor(
                    when (cIdx) {
                        11 -> Color.parseColor("#C62828")
                        9 -> Color.parseColor("#1B5E20")
                        10 -> Color.parseColor("#1565C0")
                        else -> Color.BLACK
                    }
                )
                tv.textSize = 12f
                tv.setPadding(12, 10, 12, 10)
                tv.gravity = if (cIdx == 0 || cIdx == 1 || cIdx == 3) Gravity.START else Gravity.END
                if (cIdx == 0 || cIdx == 1 || cIdx == 8 || cIdx == 11) {
                    tv.setTypeface(null, Typeface.BOLD)
                }
                tr.addView(tv)
            }
            tableLayout.addView(tr)
        }

        val summaryRow = TableRow(tableLayout.context)
        summaryRow.setBackgroundColor(Color.parseColor("#FFF9C4"))
        val summaryCells = listOf(
            "TOTAL",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "%.2f".format(Locale.getDefault(), totalPurchaseAmt),
            "%.2f".format(Locale.getDefault(), totalPaidAmt),
            "%.2f".format(Locale.getDefault(), totalDiscAmt),
            "%.2f".format(Locale.getDefault(), latestOutstanding)
        )
        summaryCells.forEachIndexed { cIdx, txt ->
            val tv = TextView(tableLayout.context)
            tv.text = txt
            tv.setTextColor(Color.parseColor("#000000"))
            tv.setTypeface(null, Typeface.BOLD)
            tv.textSize = 12f
            tv.setPadding(12, 12, 12, 12)
            tv.gravity = if (cIdx == 0 || cIdx == 1 || cIdx == 3) Gravity.START else Gravity.END
            summaryRow.addView(tv)
        }
        tableLayout.addView(summaryRow)

        return latestOutstanding
    }
}
