package com.shop.veggieledger

import android.app.DatePickerDialog
import android.content.Intent
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
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ReportsHubActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var reportType: String = ""

    private lateinit var tvTitle: TextView
    private lateinit var tvSummary: TextView
    private lateinit var etFromDate: EditText
    private lateinit var etToDate: EditText
    private lateinit var spPartyFilter: Spinner
    private lateinit var layoutPartyBox: LinearLayout
    private lateinit var tvPartyLabel: TextView
    private lateinit var tableLayout: TableLayout

    private var fromDateStr: String = ""
    private var toDateStr: String = ""
    private val partyNames = arrayListOf("All")
    private val partyOpBalMap = HashMap<String, Double>()
    private var shareReportText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reportType = intent.getStringExtra("REPORT_TYPE") ?: "PUR_DETAILS"
        supportActionBar?.title = getReportTitle(reportType)

        buildUiProgrammatically()
        setupDates()
        setupPartyDropdownIfNeeded()
    }

    private fun getReportTitle(type: String): String {
        return when (type) {
            "PUR_SUPPLIER" -> "Supplier-wise Purchase Report"
            "PUR_DETAILS" -> "Purchase Bill Details"
            "PUR_ITEMWISE" -> "Itemwise Purchase Report"
            "PUR_STATEMENT" -> "Supplier Ledger Statement"
            "SAL_CUSTOMER" -> "Customer-wise Sales Report"
            "SAL_DETAILS" -> "Sales Bill Details"
            "SAL_ITEMWISE" -> "Itemwise Sales Report"
            "SAL_STATEMENT" -> "Customer Ledger Statement"
            else -> "Report"
        }
    }

    private fun buildUiProgrammatically() {
        val rootScroll = ScrollView(this)
        rootScroll.setBackgroundColor(Color.parseColor("#F5F7FA"))
        rootScroll.setPadding(24, 24, 24, 24)

        val mainCol = LinearLayout(this)
        mainCol.orientation = LinearLayout.VERTICAL
        rootScroll.addView(mainCol)

        tvTitle = TextView(this)
        tvTitle.text = getReportTitle(reportType).uppercase(Locale.getDefault())
        tvTitle.textSize = 17f
        tvTitle.setTypeface(null, Typeface.BOLD)
        tvTitle.setTextColor(Color.parseColor("#1B5E20"))
        tvTitle.gravity = Gravity.CENTER
        tvTitle.setPadding(0, 0, 0, 16)
        mainCol.addView(tvTitle)

        // Date Filter Row
        val dateRow = LinearLayout(this)
        dateRow.orientation = LinearLayout.HORIZONTAL
        dateRow.weightSum = 2f

        val fromCol = LinearLayout(this)
        fromCol.orientation = LinearLayout.VERTICAL
        fromCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val lblFrom = TextView(this)
        lblFrom.text = "From Date"
        lblFrom.textSize = 11f
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
        lblTo.text = "To Date"
        lblTo.textSize = 11f
        lblTo.setTypeface(null, Typeface.BOLD)
        etToDate = EditText(this)
        etToDate.isFocusable = false
        etToDate.isClickable = true
        etToDate.setBackgroundResource(android.R.drawable.edit_text)
        toCol.addView(lblTo)
        toCol.addView(etToDate)

        dateRow.addView(fromCol)
        dateRow.addView(toCol)
        mainCol.addView(dateRow)

        // Party Dropdown Filter (Supplier / Customer)
        layoutPartyBox = LinearLayout(this)
        layoutPartyBox.orientation = LinearLayout.VERTICAL
        layoutPartyBox.setPadding(0, 10, 0, 10)
        tvPartyLabel = TextView(this)
        tvPartyLabel.text = "Select Party"
        tvPartyLabel.textSize = 12f
        tvPartyLabel.setTypeface(null, Typeface.BOLD)
        spPartyFilter = Spinner(this)
        spPartyFilter.setBackgroundResource(android.R.drawable.btn_dropdown)
        layoutPartyBox.addView(tvPartyLabel)
        layoutPartyBox.addView(spPartyFilter)
        mainCol.addView(layoutPartyBox)

        // Filter & Share Buttons Row
        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.weightSum = 2f
        btnRow.setPadding(0, 8, 0, 14)

        val btnFilter = Button(this)
        btnFilter.text = "SHOW REPORT"
        btnFilter.setBackgroundColor(Color.parseColor("#1B5E20"))
        btnFilter.setTextColor(Color.WHITE)
        val lpBtn1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lpBtn1.setMargins(0, 0, 8, 0)
        btnFilter.layoutParams = lpBtn1
        btnFilter.setOnClickListener { runReport() }

        val btnShare = Button(this)
        btnShare.text = "SHARE REPORT"
        btnShare.setBackgroundColor(Color.parseColor("#1565C0"))
        btnShare.setTextColor(Color.WHITE)
        val lpBtn2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lpBtn2.setMargins(8, 0, 0, 0)
        btnShare.layoutParams = lpBtn2
        btnShare.setOnClickListener {
            val sendIntent = Intent(Intent.ACTION_SEND)
            sendIntent.type = "text/plain"
            sendIntent.putExtra(Intent.EXTRA_TEXT, shareReportText)
            startActivity(Intent.createChooser(sendIntent, "Share Report"))
        }

        btnRow.addView(btnFilter)
        btnRow.addView(btnShare)
        mainCol.addView(btnRow)

        // Summary Banner
        tvSummary = TextView(this)
        tvSummary.text = "Loading..."
        tvSummary.setBackgroundColor(Color.parseColor("#FFF9C4"))
        tvSummary.setTextColor(Color.parseColor("#D84315"))
        tvSummary.setTypeface(null, Typeface.BOLD)
        tvSummary.textSize = 14f
        tvSummary.setPadding(20, 18, 20, 18)
        val sumLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        sumLp.setMargins(0, 0, 0, 14)
        tvSummary.layoutParams = sumLp
        mainCol.addView(tvSummary)

        // Data Table
        val hScroll = HorizontalScrollView(this)
        hScroll.setBackgroundColor(Color.WHITE)
        tableLayout = TableLayout(this)
        tableLayout.setBackgroundColor(Color.parseColor("#CFD8DC"))
        tableLayout.setPadding(1, 1, 1, 1)
        hScroll.addView(tableLayout)
        mainCol.addView(hScroll)

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
        val isPurchase = reportType.startsWith("PUR_")
        val isItemwise = reportType.endsWith("ITEMWISE")
        val isStatement = reportType.endsWith("STATEMENT")

        if (isItemwise) {
            layoutPartyBox.visibility = View.GONE
            runReport()
            return
        }

        layoutPartyBox.visibility = View.VISIBLE
        tvPartyLabel.text = if (isPurchase) "Select Supplier" else "Select Customer"
        val collectionName = if (isPurchase) "suppliers" else "customers"

        db.collection(collectionName).get().addOnSuccessListener { snap ->
            partyNames.clear()
            partyOpBalMap.clear()
            if (!isStatement) {
                partyNames.add(if (isPurchase) "All Suppliers" else "All Customers")
            }

            val sortedDocs = snap.documents.sortedBy { (it.getString("name") ?: "").lowercase(Locale.getDefault()) }
            for (doc in sortedDocs) {
                val name = doc.getString("name") ?: continue
                val opBal = doc.getDouble("opbal") ?: doc.getDouble("openingBalance") ?: 0.0
                partyNames.add(name)
                partyOpBalMap[name] = opBal
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

    private fun isDateInRange(dateStr: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val d = sdf.parse(dateStr) ?: return true
            val from = sdf.parse(fromDateStr) ?: return true
            val to = sdf.parse(toDateStr) ?: return true
            !d.before(from) && !d.after(to)
        } catch (e: Exception) {
            true
        }
    }

    private fun addHeaderRow(headers: List<String>, bgHex: String) {
        tableLayout.removeAllViews()
        val tr = TableRow(this)
        tr.setBackgroundColor(Color.parseColor(bgHex))
        for (h in headers) {
            val tv = TextView(this)
            tv.text = h
            tv.setTextColor(Color.WHITE)
            tv.setTypeface(null, Typeface.BOLD)
            tv.textSize = 12f
            tv.setPadding(18, 16, 18, 16)
            tr.addView(tv)
        }
        tableLayout.addView(tr)
    }

    private fun addDataRow(cells: List<String>, rowIndex: Int) {
        val tr = TableRow(this)
        tr.setBackgroundColor(if (rowIndex % 2 == 0) Color.WHITE else Color.parseColor("#F1F8E9"))
        for (c in cells) {
            val tv = TextView(this)
            tv.text = c
            tv.setTextColor(Color.BLACK)
            tv.textSize = 12f
            tv.setPadding(18, 14, 18, 14)
            tr.addView(tv)
        }
        tableLayout.addView(tr)
    }

    private fun runReport() {
        val selectedParty = spPartyFilter.selectedItem?.toString() ?: ""
        when (reportType) {
            "PUR_SUPPLIER" -> loadPartySummaryReport("purchases", "supplierName", selectedParty, "#1565C0")
            "PUR_DETAILS" -> loadBillDetailsReport("purchases", "supplierName", selectedParty, "#1565C0")
            "PUR_ITEMWISE" -> loadItemwiseReport("purchases", "#1565C0")
            "PUR_STATEMENT" -> loadLedgerStatement("purchases", "payments", "supplierName", selectedParty, "#1565C0")

            "SAL_CUSTOMER" -> loadPartySummaryReport("sales", "customerName", selectedParty, "#1B5E20")
            "SAL_DETAILS" -> loadBillDetailsReport("sales", "customerName", selectedParty, "#1B5E20")
            "SAL_ITEMWISE" -> loadItemwiseReport("sales", "#1B5E20")
            "SAL_STATEMENT" -> loadLedgerStatement("sales", "receipts", "customerName", selectedParty, "#1B5E20")
        }
    }

    private fun loadPartySummaryReport(col: String, partyField: String, filterParty: String, headerColor: String) {
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
                if (!filterParty.startsWith("All") && !party.equals(filterParty, ignoreCase = true)) continue

                val tot = doc.getDouble("grandTotal") ?: 0.0
                val pMode = doc.getString("payMode") ?: "Credit"
                val paid = if (pMode.equals("Cash", ignoreCase = true)) tot else (doc.getDouble("paidAmount") ?: 0.0)
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
                addDataRow(
                    listOf(
                        p,
                        "$c",
                        String.format(Locale.getDefault(), "%.2f", t),
                        String.format(Locale.getDefault(), "%.2f", pd),
                        String.format(Locale.getDefault(), "%.2f", b)
                    ),
                    idx++
                )
                sb.append("$p : Total Rs. %.2f | Pending Rs. %.2f\n".format(t, b))
            }

            tvSummary.text = String.format(Locale.getDefault(), "Total: Rs. %.2f  |  Pending: Rs. %.2f", grandTotal, grandBal)
            shareReportText = sb.toString()
        }
    }

    private fun loadBillDetailsReport(col: String, partyField: String, filterParty: String, headerColor: String) {
        addHeaderRow(listOf("Date", "Bill No", "Party Name", "Mode", "Amount", "Pending"), headerColor)
        db.collection(col).get().addOnSuccessListener { snap ->
            var grandTotal = 0.0
            var grandBal = 0.0
            val sb = StringBuilder("${tvTitle.text} ($fromDateStr to $toDateStr)\n\n")
            var idx = 0

            val docs = snap.documents.sortedBy { it.getLong("timestamp") ?: 0L }
            for (doc in docs) {
                val date = doc.getString("date") ?: ""
                if (!isDateInRange(date)) continue
                val party = doc.getString(partyField) ?: ""
                if (!filterParty.startsWith("All") && !party.equals(filterParty, ignoreCase = true)) continue

                val billNo = doc.getString("billNo") ?: doc.id
                val mode = doc.getString("payMode") ?: "Credit"
                val tot = doc.getDouble("grandTotal") ?: 0.0
                val paid = doc.getDouble("paidAmount") ?: 0.0
                val bal = if (mode.equals("Cash", ignoreCase = true)) 0.0 else (doc.getDouble("balanceAmount") ?: (tot - paid))

                grandTotal += tot
                grandBal += bal

                addDataRow(
                    listOf(
                        date,
                        billNo,
                        party,
                        mode,
                        String.format(Locale.getDefault(), "%.2f", tot),
                        String.format(Locale.getDefault(), "%.2f", bal)
                    ),
                    idx++
                )
                sb.append("$date | $billNo | $party | Rs. %.2f\n".format(tot))
            }

            tvSummary.text = String.format(Locale.getDefault(), "Bills: %d  |  Total: Rs. %.2f  |  Pending: Rs. %.2f", idx, grandTotal, grandBal)
            shareReportText = sb.toString()
        }
    }

    private fun loadItemwiseReport(col: String, headerColor: String) {
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
                        val kgs = (m["netKgs"] as? Number)?.toDouble()
                            ?: (m["kgs"] as? Number)?.toDouble()
                            ?: 0.0
                        val amt = (m["amount"] as? Number)?.toDouble() ?: 0.0

                        bagsMap[iName] = (bagsMap[iName] ?: 0) + bags
                        kgsMap[iName] = (kgsMap[iName] ?: 0.0) + kgs
                        amtMap[iName] = (amtMap[iName] ?: 0.0) + amt
                        grandAmt += amt
                    }
                } else {
                    val iName = doc.getString("itemName") ?: continue
                    val bags = (doc.getLong("bags") ?: 0L).toInt()
                    val kgs = doc.getDouble("netKgs") ?: doc.getDouble("kgs") ?: 0.0
                    val amt = doc.getDouble("grandTotal") ?: 0.0

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
                addDataRow(
                    listOf(
                        item,
                        "$b",
                        String.format(Locale.getDefault(), "%.2f", k),
                        String.format(Locale.getDefault(), "%.2f", a)
                    ),
                    idx++
                )
                sb.append("$item : $b Bags | %.2f Kgs | Rs. %.2f\n".format(k, a))
            }

            tvSummary.text = String.format(Locale.getDefault(), "Total Item Value: Rs. %.2f", grandAmt)
            shareReportText = sb.toString()
        }
    }

    private fun loadLedgerStatement(billCol: String, voucherCol: String, partyField: String, partyName: String, headerColor: String) {
        addHeaderRow(listOf("Date", "Ref No", "Particulars", "Bill (+)", "Paid/Rec (-)", "Balance"), headerColor)
        if (partyName.isEmpty()) return

        val opBal = partyOpBalMap[partyName] ?: 0.0

        db.collection(billCol).get().addOnSuccessListener { billSnap ->
            db.collection(voucherCol).get().addOnSuccessListener { vouSnap ->
                val entries = arrayListOf>()

                for (doc in billSnap) {
                    val p = doc.getString(partyField) ?: ""
                    if (!p.equals(partyName, ignoreCase = true)) continue
                    val date = doc.getString("date") ?: ""
                    if (!isDateInRange(date)) continue

                    val mode = doc.getString("payMode") ?: "Credit"
                    val tot = doc.getDouble("grandTotal") ?: 0.0
                    val billNo = doc.getString("billNo") ?: doc.id
                    val ts = doc.getLong("timestamp") ?: 0L

                    entries.add(hashMapOf<String, Any>(
                        "date" to date,
                        "ref" to billNo,
                        "desc" to "Bill ($mode)",
                        "debit" to tot,
                        "credit" to if (mode.equals("Cash", ignoreCase = true)) tot else 0.0,
                        "ts" to ts
                    ))
                }

                for (doc in vouSnap) {
                    val p = doc.getString(partyField) ?: ""
                    if (!p.equals(partyName, ignoreCase = true)) continue
                    val date = doc.getString("date") ?: ""
                    if (!isDateInRange(date)) continue

                    val amt = doc.getDouble("amount") ?: 0.0
                    val disc = doc.getDouble("discount") ?: 0.0
                    val refNo = doc.getString("receiptNo") ?: doc.getString("paymentNo") ?: doc.id
                    val mode = doc.getString("payMode") ?: "Cash"
                    val ts = doc.getLong("timestamp") ?: 0L

                    entries.add(hashMapOf<String, Any>(
                        "date" to date,
                        "ref" to refNo,
                        "desc" to "Voucher ($mode)",
                        "debit" to 0.0,
                        "credit" to (amt + disc),
                        "ts" to ts
                    ))
                }

                entries.sortBy { (it["ts"] as? Number)?.toLong() ?: 0L }

                var runningBal = opBal
                var idx = 0
                addDataRow(
                    listOf("-", "OP-BAL", "Opening Balance", "0.00", "0.00", String.format(Locale.getDefault(), "%.2f", runningBal)),
                    idx++
                )

                val sb = StringBuilder("STATEMENT: $partyName ($fromDateStr to $toDateStr)\nOpening: Rs. %.2f\n\n".format(opBal))

                for (e in entries) {
                    val dr = (e["debit"] as? Number)?.toDouble() ?: 0.0
                    val cr = (e["credit"] as? Number)?.toDouble() ?: 0.0
                    runningBal = (runningBal + dr - cr).coerceAtLeast(0.0)

                    val dStr = e["date"].toString()
                    val rStr = e["ref"].toString()
                    val descStr = e["desc"].toString()

                    addDataRow(
                        listOf(
                            dStr,
                            rStr,
                            descStr,
                            String.format(Locale.getDefault(), "%.2f", dr),
                            String.format(Locale.getDefault(), "%.2f", cr),
                            String.format(Locale.getDefault(), "%.2f", runningBal)
                        ),
                        idx++
                    )
                    sb.append("$dStr | $rStr | +%.2f | -%.2f | Bal: %.2f\n".format(dr, cr, runningBal))
                }

                tvSummary.text = String.format(Locale.getDefault(), "%s Closing Balance: Rs. %.2f", partyName, runningBal)
                shareReportText = sb.toString()
            }
        }
    }
}
