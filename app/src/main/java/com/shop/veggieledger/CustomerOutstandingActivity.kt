package com.shop.veggieledger

import android.app.DatePickerDialog
import android.content.Context
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

class CustomerOutstandingActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var etAsOnDate: EditText
    private lateinit var spCustomerFilter: Spinner
    private lateinit var tvLeftCustName: TextView
    private lateinit var tvRightDate: TextView
    private lateinit var tableLayout: TableLayout
    private lateinit var printWebView: WebView

    private var selectedDateStr: String = ""
    private val customerNames = arrayListOf("ALL CUSTOMERS")
    private val reportRows = JSONArray()

    // Adjusted to 33 rows per A4 page to accommodate +2pt font size and +1pt row padding cleanly
    private val ROWS_PER_A4_PAGE = 33

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Customer Outstanding (A4)"

        val cal = Calendar.getInstance()
        selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)

        buildA4ScreenUi(cal)
        loadOutstandingData()
    }

    private fun buildA4ScreenUi(cal: Calendar) {
        val rootScroll = ScrollView(this)
        rootScroll.setBackgroundColor(Color.parseColor("#ECEFF1"))
        rootScroll.setPadding(16, 16, 16, 16)

        val mainCol = LinearLayout(this)
        mainCol.orientation = LinearLayout.VERTICAL
        rootScroll.addView(mainCol)

        // Filter & Print Control Bar
        val controlCard = LinearLayout(this)
        controlCard.orientation = LinearLayout.VERTICAL
        controlCard.setBackgroundColor(Color.WHITE)
        controlCard.setPadding(20, 20, 20, 20)
        val ctrlLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        ctrlLp.setMargins(0, 0, 0, 16)
        controlCard.layoutParams = ctrlLp

        val filterRow = LinearLayout(this)
        filterRow.orientation = LinearLayout.HORIZONTAL
        filterRow.weightSum = 2f

        // Date Picker
        val dateCol = LinearLayout(this)
        dateCol.orientation = LinearLayout.VERTICAL
        dateCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val lblDate = TextView(this)
        lblDate.text = "As On Date"
        lblDate.textSize = 11f
        lblDate.setTypeface(null, Typeface.BOLD)
        etAsOnDate = EditText(this)
        etAsOnDate.setText(selectedDateStr)
        etAsOnDate.isFocusable = false
        etAsOnDate.isClickable = true
        etAsOnDate.setBackgroundResource(android.R.drawable.edit_text)
        etAsOnDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                etAsOnDate.setText(selectedDateStr)
                loadOutstandingData()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        dateCol.addView(lblDate)
        dateCol.addView(etAsOnDate)

        // Customer Filter Spinner
        val custCol = LinearLayout(this)
        custCol.orientation = LinearLayout.VERTICAL
        custCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val lblCust = TextView(this)
        lblCust.text = "Filter Customer"
        lblCust.textSize = 11f
        lblCust.setTypeface(null, Typeface.BOLD)
        spCustomerFilter = Spinner(this)
        spCustomerFilter.setBackgroundResource(android.R.drawable.btn_dropdown)
        custCol.addView(lblCust)
        custCol.addView(spCustomerFilter)

        filterRow.addView(dateCol)
        filterRow.addView(custCol)
        controlCard.addView(filterRow)

        val btnPrintA4 = Button(this)
        btnPrintA4.text = "PRINT / SAVE A4 PDF SHEET"
        btnPrintA4.setBackgroundColor(Color.parseColor("#1B5E20"))
        btnPrintA4.setTextColor(Color.WHITE)
        btnPrintA4.setTypeface(null, Typeface.BOLD)
        val btnLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        btnLp.setMargins(0, 12, 0, 0)
        btnPrintA4.layoutParams = btnLp
        btnPrintA4.setOnClickListener { generateAndPrintA4Pdf() }
        controlCard.addView(btnPrintA4)

        mainCol.addView(controlCard)

        // A4 SHEET PREVIEW CONTAINER
        val a4Sheet = LinearLayout(this)
        a4Sheet.orientation = LinearLayout.VERTICAL
        a4Sheet.setBackgroundColor(Color.WHITE)
        a4Sheet.setPadding(24, 28, 24, 28)

        // Center Heading 1: THIRUVENI COCONUTS
        val tvShopName = TextView(this)
        tvShopName.text = "THIRUVENI COCONUTS"
        tvShopName.textSize = 21f
        tvShopName.setTypeface(null, Typeface.BOLD)
        tvShopName.setTextColor(Color.BLACK)
        tvShopName.gravity = Gravity.CENTER
        a4Sheet.addView(tvShopName)

        // Center Heading 2: Address
        val tvShopAddr = TextView(this)
        tvShopAddr.text = "SHOP NO B98, PERIYAR VEGETABLE MARKET, KOYAMBEDU"
        tvShopAddr.textSize = 14f
        tvShopAddr.setTypeface(null, Typeface.BOLD)
        tvShopAddr.setTextColor(Color.DKGRAY)
        tvShopAddr.gravity = Gravity.CENTER
        tvShopAddr.setPadding(0, 4, 0, 10)
        a4Sheet.addView(tvShopAddr)

        // Center Heading 3: CUSTOMER OUTSTANDING
        val tvSheetTitle = TextView(this)
        tvSheetTitle.text = "CUSTOMER OUTSTANDING"
        tvSheetTitle.textSize = 17f
        tvSheetTitle.setTypeface(null, Typeface.BOLD)
        tvSheetTitle.setTextColor(Color.BLACK)
        tvSheetTitle.gravity = Gravity.CENTER
        tvSheetTitle.setPadding(0, 4, 0, 16)
        a4Sheet.addView(tvSheetTitle)

        // Left: Customer Name | Right: Date
        val infoRow = LinearLayout(this)
        infoRow.orientation = LinearLayout.HORIZONTAL
        infoRow.weightSum = 2f
        infoRow.setPadding(4, 8, 4, 12)

        tvLeftCustName = TextView(this)
        tvLeftCustName.text = "CUSTOMER NAME: ALL CUSTOMERS"
        tvLeftCustName.textSize = 14f
        tvLeftCustName.setTypeface(null, Typeface.BOLD)
        tvLeftCustName.setTextColor(Color.BLACK)
        tvLeftCustName.gravity = Gravity.START
        tvLeftCustName.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)

        tvRightDate = TextView(this)
        tvRightDate.text = "DATE: $selectedDateStr"
        tvRightDate.textSize = 14f
        tvRightDate.setTypeface(null, Typeface.BOLD)
        tvRightDate.setTextColor(Color.BLACK)
        tvRightDate.gravity = Gravity.END
        tvRightDate.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)

        infoRow.addView(tvLeftCustName)
        infoRow.addView(tvRightDate)
        a4Sheet.addView(infoRow)

        // Table Container
        val hScroll = HorizontalScrollView(this)
        tableLayout = TableLayout(this)
        tableLayout.setBackgroundColor(Color.BLACK)
        tableLayout.setPadding(1, 1, 1, 1)
        hScroll.addView(tableLayout)
        a4Sheet.addView(hScroll)

        mainCol.addView(a4Sheet)
        setContentView(rootScroll)
    }

    private fun isOnOrBeforeSelectedDate(dateStr: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val d = sdf.parse(dateStr) ?: return true
            val target = sdf.parse(selectedDateStr) ?: return true
            !d.after(target)
        } catch (e: Exception) {
            true
        }
    }

    private fun loadOutstandingData() {
        tvRightDate.text = "DATE: $selectedDateStr"

        db.collection("customers").get().addOnSuccessListener { custSnap ->
            db.collection("sales").get().addOnSuccessListener { salesSnap ->
                db.collection("receipts").get().addOnSuccessListener { recSnap ->

                    val salesMap = HashMap<String, Double>()
                    for (doc in salesSnap) {
                        val d = doc.getString("date") ?: ""
                        if (!isOnOrBeforeSelectedDate(d)) continue
                        val pMode = doc.getString("payMode") ?: "Credit"
                        if (!pMode.equals("Credit", ignoreCase = true)) continue

                        val cId = doc.getString("customerId") ?: ""
                        val cName = (doc.getString("customerName") ?: "").trim().lowercase(Locale.getDefault())
                        val tot = doc.getDouble("grandTotal") ?: 0.0

                        if (cId.isNotEmpty()) salesMap[cId] = (salesMap[cId] ?: 0.0) + tot
                        if (cName.isNotEmpty()) salesMap["NAME_$cName"] = (salesMap["NAME_$cName"] ?: 0.0) + tot
                    }

                    val incomeMap = HashMap<String, Double>()
                    val discMap = HashMap<String, Double>()
                    for (doc in recSnap) {
                        val d = doc.getString("date") ?: ""
                        if (!isOnOrBeforeSelectedDate(d)) continue

                        val cId = doc.getString("customerId") ?: ""
                        val cName = (doc.getString("customerName") ?: "").trim().lowercase(Locale.getDefault())
                        val amt = doc.getDouble("amount") ?: 0.0
                        val disc = doc.getDouble("discount") ?: 0.0

                        if (cId.isNotEmpty()) {
                            incomeMap[cId] = (incomeMap[cId] ?: 0.0) + amt
                            discMap[cId] = (discMap[cId] ?: 0.0) + disc
                        }
                        if (cName.isNotEmpty()) {
                            incomeMap["NAME_$cName"] = (incomeMap["NAME_$cName"] ?: 0.0) + amt
                            discMap["NAME_$cName"] = (discMap["NAME_$cName"] ?: 0.0) + disc
                        }
                    }

                    while (reportRows.length() > 0) reportRows.remove(0)
                    val prevSelection = spCustomerFilter.selectedItem?.toString() ?: "ALL CUSTOMERS"
                    customerNames.clear()
                    customerNames.add("ALL CUSTOMERS")

                    val sortedCusts = custSnap.documents.sortedBy { doc ->
                        (doc.getString("name") ?: "").lowercase(Locale.getDefault())
                    }

                    for (doc in sortedCusts) {
                        val name = doc.getString("name")?.trim() ?: continue
                        val phone = (doc.getString("contact")
                            ?: doc.getString("phone")
                            ?: doc.getString("mobile")
                            ?: doc.getString("contactNo")
                            ?: "").trim()
                        val cleanPhone = if (phone == "0" || phone == "-") "" else phone

                        val nameKey = "NAME_${name.lowercase(Locale.getDefault())}"
                        val opBal = doc.getDouble("opbal") ?: doc.getDouble("openingBalance") ?: 0.0
                        val salesAmt = salesMap[doc.id] ?: salesMap[nameKey] ?: 0.0
                        val incAmt = incomeMap[doc.id] ?: incomeMap[nameKey] ?: 0.0
                        val discAmt = discMap[doc.id] ?: discMap[nameKey] ?: 0.0
                        val clBal = (opBal + salesAmt - incAmt - discAmt).coerceAtLeast(0.0)

                        customerNames.add(name)

                        val obj = JSONObject()
                        obj.put("name", name)
                        obj.put("phone", cleanPhone)
                        obj.put("opBal", opBal)
                        obj.put("sales", salesAmt)
                        obj.put("income", incAmt)
                        obj.put("discount", discAmt)
                        obj.put("clBal", clBal)
                        reportRows.put(obj)
                    }

                    val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, customerNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spCustomerFilter.adapter = adapter

                    val selIdx = customerNames.indexOf(prevSelection)
                    if (selIdx >= 0) spCustomerFilter.setSelection(selIdx)

                    spCustomerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                            renderTableForSelection()
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }

                    renderTableForSelection()
                }
            }
        }
    }

    private fun makeBorderedCell(text: String, alignRight: Boolean = false, bold: Boolean = false, bgColor: Int = Color.WHITE, textColor: Int = Color.BLACK): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(textColor)
        tv.setBackgroundColor(bgColor)
        tv.textSize = 14f
        tv.setPadding(15, 16, 15, 16)
        tv.gravity = if (alignRight) Gravity.END else Gravity.START
        if (bold) tv.setTypeface(null, Typeface.BOLD)
        val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.MATCH_PARENT)
        lp.setMargins(1, 1, 1, 1)
        tv.layoutParams = lp
        return tv
    }

    private fun addSummaryRowToScreen(label: String, op: Double, sal: Double, inc: Double, disc: Double, cl: Double, bgColor: Int) {
        val row = TableRow(this)
        row.setBackgroundColor(Color.BLACK)
        row.addView(makeBorderedCell(label, bold = true, bgColor = bgColor))
        row.addView(makeBorderedCell(String.format(Locale.getDefault(), "%.2f", op), alignRight = true, bold = true, bgColor = bgColor))
        row.addView(makeBorderedCell(String.format(Locale.getDefault(), "%.2f", sal), alignRight = true, bold = true, bgColor = bgColor))
        row.addView(makeBorderedCell(String.format(Locale.getDefault(), "%.2f", inc), alignRight = true, bold = true, bgColor = bgColor))
        row.addView(makeBorderedCell(String.format(Locale.getDefault(), "%.2f", disc), alignRight = true, bold = true, bgColor = bgColor))
        row.addView(makeBorderedCell(String.format(Locale.getDefault(), "%.2f", cl), alignRight = true, bold = true, bgColor = bgColor))
        row.addView(makeBorderedCell("", bold = true, bgColor = bgColor))
        row.addView(makeBorderedCell("                    ", bold = true, bgColor = bgColor))
        tableLayout.addView(row)
    }

    private fun renderTableForSelection() {
        val chosenCust = spCustomerFilter.selectedItem?.toString() ?: "ALL CUSTOMERS"
        tvLeftCustName.text = "CUSTOMER NAME: $chosenCust"

        tableLayout.removeAllViews()

        val headerRow = TableRow(this)
        headerRow.setBackgroundColor(Color.BLACK)
        val hdrBg = Color.parseColor("#E0E0E0")

        headerRow.addView(makeBorderedCell("NAME", bold = true, bgColor = hdrBg))
        headerRow.addView(makeBorderedCell("OP.BAL", alignRight = true, bold = true, bgColor = hdrBg))
        headerRow.addView(makeBorderedCell("SALES", alignRight = true, bold = true, bgColor = hdrBg))
        headerRow.addView(makeBorderedCell("INCOME", alignRight = true, bold = true, bgColor = hdrBg))
        headerRow.addView(makeBorderedCell("DISCOUNT", alignRight = true, bold = true, bgColor = hdrBg))
        headerRow.addView(makeBorderedCell("CL.BAL", alignRight = true, bold = true, bgColor = hdrBg))
        headerRow.addView(makeBorderedCell("PH.NO", bold = true, bgColor = hdrBg))
        headerRow.addView(makeBorderedCell("PAYMENT", bold = true, bgColor = hdrBg))
        tableLayout.addView(headerRow)

        val filteredList = arrayListOf<JSONObject>()
        for (i in 0 until reportRows.length()) {
            val obj = reportRows.getJSONObject(i)
            val name = obj.getString("name")
            if (chosenCust != "ALL CUSTOMERS" && !name.equals(chosenCust, ignoreCase = true)) continue

            val op = obj.getDouble("opBal")
            val sal = obj.getDouble("sales")
            val inc = obj.getDouble("income")
            val cl = obj.getDouble("clBal")
            if (chosenCust == "ALL CUSTOMERS" && op == 0.0 && sal == 0.0 && inc == 0.0 && cl == 0.0) continue

            filteredList.add(obj)
        }

        var grandOp = 0.0
        var grandSales = 0.0
        var grandInc = 0.0
        var grandDisc = 0.0
        var grandCl = 0.0

        var pageOp = 0.0
        var pageSales = 0.0
        var pageInc = 0.0
        var pageDisc = 0.0
        var pageCl = 0.0
        var pageNo = 1

        for (idx in filteredList.indices) {
            val obj = filteredList[idx]
            val name = obj.getString("name")
            val phone = obj.optString("phone", "")
            val op = obj.getDouble("opBal")
            val sal = obj.getDouble("sales")
            val inc = obj.getDouble("income")
            val disc = obj.getDouble("discount")
            val cl = obj.getDouble("clBal")

            pageOp += op
            pageSales += sal
            pageInc += inc
            pageDisc += disc
            pageCl += cl

            grandOp += op
            grandSales += sal
            grandInc += inc
            grandDisc += disc
            grandCl += cl

            val tr = TableRow(this)
            tr.setBackgroundColor(Color.BLACK)
            tr.addView(makeBorderedCell(name, bold = true))
            tr.addView(makeBorderedCell(String.format(Locale.getDefault(), "%.2f", op), alignRight = true))
            tr.addView(makeBorderedCell(String.format(Locale.getDefault(), "%.2f", sal), alignRight = true))
            tr.addView(makeBorderedCell(String.format(Locale.getDefault(), "%.2f", inc), alignRight = true))
            tr.addView(makeBorderedCell(String.format(Locale.getDefault(), "%.2f", disc), alignRight = true))
            tr.addView(makeBorderedCell(String.format(Locale.getDefault(), "%.2f", cl), alignRight = true, bold = true))
            tr.addView(makeBorderedCell(phone))
            tr.addView(makeBorderedCell("                    "))
            tableLayout.addView(tr)

            val isEndOfPage = ((idx + 1) % ROWS_PER_A4_PAGE == 0)
            val isLastItem = (idx == filteredList.size - 1)

            if (isEndOfPage || isLastItem) {
                addSummaryRowToScreen(
                    "PAGE $pageNo TOTAL",
                    pageOp, pageSales, pageInc, pageDisc, pageCl,
                    Color.parseColor("#FFF9C4")
                )
                pageOp = 0.0
                pageSales = 0.0
                pageInc = 0.0
                pageDisc = 0.0
                pageCl = 0.0
                pageNo++
            }
        }

        addSummaryRowToScreen(
            "GRAND TOTAL",
            grandOp, grandSales, grandInc, grandDisc, grandCl,
            Color.parseColor("#C8E6C9")
        )
    }

    private fun generateAndPrintA4Pdf() {
        val chosenCust = spCustomerFilter.selectedItem?.toString() ?: "ALL CUSTOMERS"

        val filteredList = arrayListOf<JSONObject>()
        for (i in 0 until reportRows.length()) {
            val obj = reportRows.getJSONObject(i)
            val name = obj.getString("name")
            if (chosenCust != "ALL CUSTOMERS" && !name.equals(chosenCust, ignoreCase = true)) continue

            val op = obj.getDouble("opBal")
            val sal = obj.getDouble("sales")
            val inc = obj.getDouble("income")
            val cl = obj.getDouble("clBal")
            if (chosenCust == "ALL CUSTOMERS" && op == 0.0 && sal == 0.0 && inc == 0.0 && cl == 0.0) continue

            filteredList.add(obj)
        }

        val pagesHtml = StringBuilder()

        var grandOp = 0.0
        var grandSales = 0.0
        var grandInc = 0.0
        var grandDisc = 0.0
        var grandCl = 0.0

        val totalPages = if (filteredList.isEmpty()) 1 else ((filteredList.size + ROWS_PER_A4_PAGE - 1) / ROWS_PER_A4_PAGE)

        for (pageIdx in 0 until totalPages) {
            val startIdx = pageIdx * ROWS_PER_A4_PAGE
            val endIdx = minOf(startIdx + ROWS_PER_A4_PAGE, filteredList.size)

            var pageOp = 0.0
            var pageSales = 0.0
            var pageInc = 0.0
            var pageDisc = 0.0
            var pageCl = 0.0

            val pageRowsHtml = StringBuilder()

            for (i in startIdx until endIdx) {
                val obj = filteredList[i]
                val name = obj.getString("name")
                val phone = obj.optString("phone", "")
                val op = obj.getDouble("opBal")
                val sal = obj.getDouble("sales")
                val inc = obj.getDouble("income")
                val disc = obj.getDouble("discount")
                val cl = obj.getDouble("clBal")

                pageOp += op
                pageSales += sal
                pageInc += inc
                pageDisc += disc
                pageCl += cl

                grandOp += op
                grandSales += sal
                grandInc += inc
                grandDisc += disc
                grandCl += cl

                pageRowsHtml.append(
                    String.format(
                        Locale.getDefault(),
                        "<tr><td class='namecol'>%s</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td style='font-weight:bold;'>%.2f</td><td style='text-align:center;'>%s</td><td></td></tr>",
                        name, op, sal, inc, disc, cl, phone
                    )
                )
            }

            // Page Total Row at the bottom of EVERY page
            pageRowsHtml.append(
                String.format(
                    Locale.getDefault(),
                    "<tr class='pagetotal'><td class='namecol'>PAGE %d TOTAL</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td></td><td></td></tr>",
                    (pageIdx + 1), pageOp, pageSales, pageInc, pageDisc, pageCl
                )
            )

            // Grand Total Row at the bottom of the LAST page
            if (pageIdx == totalPages - 1) {
                pageRowsHtml.append(
                    String.format(
                        Locale.getDefault(),
                        "<tr class='grandtotal'><td class='namecol'>GRAND TOTAL</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td></td><td></td></tr>",
                        grandOp, grandSales, grandInc, grandDisc, grandCl
                    )
                )
            }

            val pageBreakStyle = if (pageIdx < totalPages - 1) "page-break-after: always;" else ""

            pagesHtml.append(
                """
                <div class="page-box" style="$pageBreakStyle">
                    <div class="center">
                        <h1>THIRUVENI COCONUTS</h1>
                        <h2>SHOP NO B98, PERIYAR VEGETABLE MARKET, KOYAMBEDU</h2>
                        <h3>CUSTOMER OUTSTANDING</h3>
                    </div>
                    <table class="meta">
                        <tr>
                            <td style="text-align:left;">CUSTOMER NAME: $chosenCust</td>
                            <td style="text-align:right;">DATE: $selectedDateStr  |  Page ${pageIdx + 1} of $totalPages</td>
                        </tr>
                    </table>
                    <table class="grid">
                        <colgroup>
                            <col style="width: 24%;">
                            <col style="width: 11%;">
                            <col style="width: 10%;">
                            <col style="width: 10%;">
                            <col style="width: 9%;">
                            <col style="width: 12%;">
                            <col style="width: 12%;">
                            <col style="width: 12%;">
                        </colgroup>
                        <thead>
                            <tr>
                                <th>NAME</th>
                                <th>OP.BAL</th>
                                <th>SALES</th>
                                <th>INCOME</th>
                                <th>DISCOUNT</th>
                                <th>CL.BAL</th>
                                <th>PH.NO</th>
                                <th>PAYMENT</th>
                            </tr>
                        </thead>
                        <tbody>
                            $pageRowsHtml
                        </tbody>
                    </table>
                </div>
                """.trimIndent()
            )
        }

        val htmlDoc = """
            <html>
            <head>
            <style>
                * { box-sizing: border-box; }
                @page { size: A4 portrait; margin: 5mm 5mm 5mm 5mm; }
                html, body { width: 100%; font-family: Arial, sans-serif; color: #000; margin: 0; padding: 0; }
                .page-box { width: 100%; }
                .center { text-align: center; }
                h1 { font-size: 20px; margin: 0; letter-spacing: 0.8px; }
                h2 { font-size: 13px; margin: 2px 0 4px 0; font-weight: bold; }
                h3 { font-size: 15px; margin: 3px 0 6px 0; text-decoration: underline; }
                .meta { width: 100%; margin-bottom: 5px; font-size: 12.5px; font-weight: bold; }
                .meta td { border: none; padding: 1px 0; }
                table.grid { width: 100%; table-layout: fixed; border-collapse: collapse; font-size: 11.8px; }
                table.grid th, table.grid td { border: 1px solid #000; padding: 6.5px 4px; text-align: right; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
                table.grid th { background-color: #E0E0E0; text-align: center; font-weight: bold; padding: 7px 3px; font-size: 11.5px; }
                td.namecol { text-align: left !important; font-weight: bold; }
                tr.pagetotal td { font-weight: bold; background-color: #FFFDE7; border-top: 1.5px solid #000; padding: 7px 4px; }
                tr.grandtotal td { font-weight: bold; background-color: #E8F5E9; border-top: 2px solid #000; border-bottom: 2px solid #000; font-size: 12.5px; padding: 7.5px 4px; }
            </style>
            </head>
            <body>
                $pagesHtml
            </body>
            </html>
        """.trimIndent()

        printWebView = WebView(this)
        printWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = printWebView.createPrintDocumentAdapter("Customer_Outstanding_$selectedDateStr")
                val builder = PrintAttributes.Builder()
                builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                builder.setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                printManager.print("Customer Outstanding A4", printAdapter, builder.build())
            }
        }
        printWebView.loadDataWithBaseURL(null, htmlDoc, "text/HTML", "UTF-8", null)
        Toast.makeText(this, "Preparing Full-Page A4 Sheet (+2pt Font)...", Toast.LENGTH_SHORT).show()
    }
}
