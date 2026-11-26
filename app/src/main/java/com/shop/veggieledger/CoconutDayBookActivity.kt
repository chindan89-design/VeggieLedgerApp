package com.shop.veggieledger

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class CoconutDayBookActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var etDayBookDate: EditText
    private lateinit var tvSummaryStrip: TextView
    private lateinit var contentContainer: LinearLayout
    private lateinit var printWebView: WebView

    private var selectedDateStr: String = ""
    private var shareText: String = ""
    private var printableHtml: String = ""

    // Cached calculated values for direct A4 PDF generation
    private var lastOpBal: Double = 0.0
    private var lastTodayCollection: Double = 0.0
    private var lastTotalAvailable: Double = 0.0
    private var lastTotalExpense: Double = 0.0
    private var lastClosingBal: Double = 0.0
    private val lastCollectionTree = LinkedHashMap<String, ArrayList<TreeEntry>>()
    private val lastExpenseTree = LinkedHashMap<String, ArrayList<TreeEntry>>()

    data class TreeEntry(val label: String, val subInfo: String, val amount: Double)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Coconut Day Book"

        buildUi()

        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        selectedDateStr = sdf.format(cal.time)
        etDayBookDate.setText(selectedDateStr)

        etDayBookDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = sdf.format(cal.time)
                etDayBookDate.setText(selectedDateStr)
                loadCoconutDayBook()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        loadCoconutDayBook()
    }

    private fun fmt(v: Double): String {
        return if (abs(v) < 0.005) "0.00" else String.format(Locale.getDefault(), "%.2f", v)
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

    private fun buildUi() {
        val rootScroll = ScrollView(this)
        rootScroll.setBackgroundColor(Color.parseColor("#ECEFF1"))
        rootScroll.setPadding(16, 16, 16, 16)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        rootScroll.addView(root)

        val topCard = LinearLayout(this)
        topCard.orientation = LinearLayout.VERTICAL
        topCard.setBackgroundColor(Color.WHITE)
        topCard.setPadding(18, 16, 18, 16)
        val topLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        topLp.setMargins(0, 0, 0, 12)
        topCard.layoutParams = topLp

        val title = TextView(this)
        title.text = "COCONUT DAY BOOK"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.parseColor("#1B5E20"))
        title.gravity = Gravity.CENTER
        title.setPadding(0, 0, 0, 10)
        topCard.addView(title)

        val lblDate = TextView(this)
        lblDate.text = "Select Date"
        lblDate.textSize = 12f
        lblDate.setTypeface(null, Typeface.BOLD)
        lblDate.setTextColor(Color.parseColor("#1B5E20"))
        topCard.addView(lblDate)

        etDayBookDate = EditText(this)
        etDayBookDate.isFocusable = false
        etDayBookDate.isClickable = true
        etDayBookDate.setBackgroundResource(android.R.drawable.edit_text)
        topCard.addView(etDayBookDate)

        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.weightSum = 3f
        btnRow.setPadding(0, 8, 0, 0)

        val btnRefresh = Button(this)
        btnRefresh.text = "VIEW"
        btnRefresh.setBackgroundColor(Color.parseColor("#1B5E20"))
        btnRefresh.setTextColor(Color.WHITE)
        btnRefresh.setTypeface(null, Typeface.BOLD)
        btnRefresh.textSize = 12f
        val lp1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)
        lp1.setMargins(0, 0, 4, 0)
        btnRefresh.layoutParams = lp1
        btnRefresh.setOnClickListener { loadCoconutDayBook() }

        val btnSharePdf = Button(this)
        btnSharePdf.text = "SHARE PDF"
        btnSharePdf.setBackgroundColor(Color.parseColor("#25D366"))
        btnSharePdf.setTextColor(Color.WHITE)
        btnSharePdf.setTypeface(null, Typeface.BOLD)
        btnSharePdf.textSize = 12f
        val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
        lp2.setMargins(4, 0, 4, 0)
        btnSharePdf.layoutParams = lp2
        btnSharePdf.setOnClickListener { generateAndShareDayBookPdf() }

        val btnPrint = Button(this)
        btnPrint.text = "PRINT"
        btnPrint.setBackgroundColor(Color.parseColor("#E65100"))
        btnPrint.setTextColor(Color.WHITE)
        btnPrint.setTypeface(null, Typeface.BOLD)
        btnPrint.textSize = 12f
        val lp3 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        lp3.setMargins(4, 0, 0, 0)
        btnPrint.layoutParams = lp3
        btnPrint.setOnClickListener { printDayBookPdf() }

        btnRow.addView(btnRefresh)
        btnRow.addView(btnSharePdf)
        btnRow.addView(btnPrint)
        topCard.addView(btnRow)
        root.addView(topCard)

        tvSummaryStrip = TextView(this)
        tvSummaryStrip.text = "Loading Coconut Day Book..."
        tvSummaryStrip.setBackgroundColor(Color.parseColor("#FFF9C4"))
        tvSummaryStrip.setTextColor(Color.parseColor("#D84315"))
        tvSummaryStrip.setTypeface(null, Typeface.BOLD)
        tvSummaryStrip.textSize = 13.5f
        tvSummaryStrip.setPadding(16, 12, 16, 12)
        val sumLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        sumLp.setMargins(0, 0, 0, 12)
        tvSummaryStrip.layoutParams = sumLp
        root.addView(tvSummaryStrip)

        contentContainer = LinearLayout(this)
        contentContainer.orientation = LinearLayout.VERTICAL
        root.addView(contentContainer)

        setContentView(rootScroll)
    }

    private fun isCoconutSaleDoc(docData: Map<String, Any>): Boolean {
        val rawItems = docData["items"] as? List<*>
        if (rawItems != null && rawItems.isNotEmpty()) {
            for (entry in rawItems) {
                val m = entry as? Map<*, *> ?: continue
                val iName = (m["itemName"]?.toString() ?: "").trim()
                if (iName.equals("Coconut", ignoreCase = true)) return true
            }
        }
        val singleItem = (docData["itemName"]?.toString() ?: "").trim()
        return singleItem.equals("Coconut", ignoreCase = true)
    }

    private fun getCoconutAmountFromSaleDoc(docData: Map<String, Any>): Double {
        val rawItems = docData["items"] as? List<*>
        if (rawItems != null && rawItems.isNotEmpty()) {
            var sum = 0.0
            for (entry in rawItems) {
                val m = entry as? Map<*, *> ?: continue
                val iName = (m["itemName"]?.toString() ?: "").trim()
                if (iName.equals("Coconut", ignoreCase = true)) {
                    val amt = (m["amount"] as? Number)?.toDouble() ?: 0.0
                    val bChg = (m["bagCharge"] as? Number)?.toDouble() ?: 0.0
                    val wCol = (m["weighCoolie"] as? Number)?.toDouble() ?: 0.0
                    sum += (amt + bChg + wCol)
                }
            }
            if (sum > 0.0) return sum
        }
        val singleItem = (docData["itemName"]?.toString() ?: "").trim()
        if (singleItem.equals("Coconut", ignoreCase = true)) {
            return (docData["grandTotal"] as? Number)?.toDouble() ?: 0.0
        }
        return 0.0
    }

    private fun loadCoconutDayBook() {
        contentContainer.removeAllViews()
        tvSummaryStrip.text = "Calculating Coconut Day Book for $selectedDateStr..."
        val targetYmd = dateToYmd(selectedDateStr)

        db.collection("sales").get().addOnSuccessListener { salSnap ->
            db.collection("receipts").get().addOnSuccessListener { recSnap ->
                db.collection("expenses").get().addOnSuccessListener { expSnap ->

                    val coconutBillNos = HashSet<String>()
                    val coconutCustomerIds = HashSet<String>()
                    val coconutCustomerNames = HashSet<String>()

                    for (sDoc in salSnap) {
                        if (isCoconutSaleDoc(sDoc.data)) {
                            val bNo = (sDoc.getString("billNo") ?: "").trim().lowercase(Locale.getDefault())
                            if (bNo.isNotEmpty()) coconutBillNos.add(bNo)
                            coconutBillNos.add(sDoc.id.lowercase(Locale.getDefault()))

                            val cid = (sDoc.getString("customerId") ?: "").trim()
                            val cname = (sDoc.getString("customerName") ?: "").trim().lowercase(Locale.getDefault())
                            if (cid.isNotEmpty()) coconutCustomerIds.add(cid)
                            if (cname.isNotEmpty()) coconutCustomerNames.add(cname)
                        }
                    }

                    var prevCashIn = 0.0
                    var prevCashOut = 0.0

                    val collectionTree = LinkedHashMap<String, ArrayList<TreeEntry>>()
                    collectionTree["Cash Sales"] = arrayListOf()
                    collectionTree["Credit Bill Received by Cash"] = arrayListOf()
                    collectionTree["Other Incomes"] = arrayListOf()

                    // 1. Cash Sales (Coconut)
                    for (sDoc in salSnap) {
                        val cocoAmt = getCoconutAmountFromSaleDoc(sDoc.data)
                        if (cocoAmt <= 0.0) continue

                        val dYmd = dateToYmd(sDoc.getString("date") ?: "")
                        val mode = (sDoc.getString("payMode") ?: "Credit").trim()
                        if (!mode.equals("Cash", ignoreCase = true)) continue

                        val cName = sDoc.getString("customerName") ?: "Customer"
                        val bNo = sDoc.getString("billNo") ?: sDoc.id

                        if (dYmd in 1 until targetYmd) {
                            prevCashIn += cocoAmt
                        } else if (dYmd == targetYmd) {
                            collectionTree["Cash Sales"]?.add(TreeEntry("$cName ($bNo)", "Cash", cocoAmt))
                        }
                    }

                    // 2. Credit Bill Received by Cash
                    for (rDoc in recSnap) {
                        val dYmd = dateToYmd(rDoc.getString("date") ?: "")
                        val amt = rDoc.getDouble("amount") ?: 0.0
                        if (amt <= 0.0) continue

                        val mode = (rDoc.getString("payMode") ?: "Cash").trim()
                        if (!mode.equals("Cash", ignoreCase = true)) continue

                        val cid = (rDoc.getString("customerId") ?: "").trim()
                        val cname = (rDoc.getString("customerName") ?: "").trim()
                        val rNo = rDoc.getString("receiptNo") ?: rDoc.id

                        var matchesCoconut = (cid.isNotEmpty() && coconutCustomerIds.contains(cid)) ||
                                (cname.isNotEmpty() && coconutCustomerNames.contains(cname.lowercase(Locale.getDefault())))

                        val rawAllocs = rDoc.get("billAllocations") as? List<*>
                        if (rawAllocs != null) {
                            for (a in rawAllocs) {
                                val m = a as? Map<*, *> ?: continue
                                val bId = (m["billDocId"]?.toString() ?: "").lowercase(Locale.getDefault())
                                val bNo = (m["billNo"]?.toString() ?: "").lowercase(Locale.getDefault())
                                if (coconutBillNos.contains(bId) || coconutBillNos.contains(bNo)) {
                                    matchesCoconut = true
                                }
                            }
                        }

                        if (!matchesCoconut && coconutCustomerIds.isNotEmpty()) continue

                        if (dYmd in 1 until targetYmd) {
                            prevCashIn += amt
                        } else if (dYmd == targetYmd) {
                            collectionTree["Credit Bill Received by Cash"]?.add(TreeEntry("$cname ($rNo)", "Cash", amt))
                        }
                    }

                    // 3. Other Incomes & 3 Expense Categories (Shop, Other, Vehicle)
                    val expenseTree = LinkedHashMap<String, ArrayList<TreeEntry>>()
                    expenseTree["Shop Expenses"] = arrayListOf()
                    expenseTree["Other Expenses"] = arrayListOf()
                    expenseTree["Vehicle Expenses"] = arrayListOf()

                    for (eDoc in expSnap) {
                        val dYmd = dateToYmd(eDoc.getString("date") ?: "")
                        val type = (eDoc.getString("type") ?: "Expense").trim()
                        val cat = (eDoc.getString("category") ?: "Other Expenses").trim()
                        val desc = (eDoc.getString("description")
                            ?: eDoc.getString("particulars")
                            ?: eDoc.getString("remarks")
                            ?: cat).trim().ifEmpty { cat }
                        val amt = eDoc.getDouble("amount") ?: 0.0
                        val mode = (eDoc.getString("payMode") ?: "Cash").trim()
                        if (amt <= 0.0) continue

                        if (type.equals("Income", ignoreCase = true)) {
                            if (dYmd in 1 until targetYmd) {
                                prevCashIn += amt
                            } else if (dYmd == targetYmd) {
                                collectionTree["Other Incomes"]?.add(TreeEntry(desc, "$cat | $mode", amt))
                            }
                        } else {
                            if (dYmd in 1 until targetYmd) {
                                prevCashOut += amt
                            } else if (dYmd == targetYmd) {
                                val targetCat = when {
                                    cat.equals("Shop Expenses", ignoreCase = true) -> "Shop Expenses"
                                    cat.equals("Vehicle Expenses", ignoreCase = true) -> "Vehicle Expenses"
                                    else -> "Other Expenses"
                                }
                                expenseTree[targetCat]?.add(TreeEntry(desc, mode, amt))
                            }
                        }
                    }

                    val openingBalance = (prevCashIn - prevCashOut).coerceAtLeast(0.0)

                    var todayCollectionTotal = 0.0
                    for ((_, list) in collectionTree) {
                        for (item in list) todayCollectionTotal += item.amount
                    }

                    var todayExpenseTotal = 0.0
                    for ((_, list) in expenseTree) {
                        for (item in list) todayExpenseTotal += item.amount
                    }

                    val totalAvailable = openingBalance + todayCollectionTotal
                    val closingBalance = totalAvailable - todayExpenseTotal

                    // Cache for PDF generator
                    lastOpBal = openingBalance
                    lastTodayCollection = todayCollectionTotal
                    lastTotalAvailable = totalAvailable
                    lastTotalExpense = todayExpenseTotal
                    lastClosingBal = closingBalance
                    lastCollectionTree.clear()
                    lastCollectionTree.putAll(collectionTree)
                    lastExpenseTree.clear()
                    lastExpenseTree.putAll(expenseTree)

                    tvSummaryStrip.text = String.format(
                        Locale.getDefault(),
                        "Op.Bal: Rs. %s  |  Collection: Rs. %s  |  Exp: Rs. %s  |  Cl.Bal: Rs. %s",
                        fmt(openingBalance), fmt(todayCollectionTotal), fmt(todayExpenseTotal), fmt(closingBalance)
                    )

                    renderDayBookCards(
                        openingBalance,
                        collectionTree,
                        todayCollectionTotal,
                        totalAvailable,
                        expenseTree,
                        todayExpenseTotal,
                        closingBalance
                    )
                }
            }
        }
    }

    /**
     * Generates a clean A4 PDF file directly in Downloads/VeggieReports and opens
     * WhatsApp / Share sheet to send the PDF immediately.
     */
    private fun generateAndShareDayBookPdf() {
        if (lastCollectionTree.isEmpty() && lastExpenseTree.isEmpty()) {
            Toast.makeText(this, "Please wait for Day Book data to load", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfDoc = PdfDocument()
            val pageWidth = 595 // A4 width in PostScript points (72 dpi)
            val pageHeight = 842 // A4 height
            val margin = 32f
            var pageNum = 1

            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            var page = pdfDoc.startPage(pageInfo)
            var canvas: Canvas = page.canvas

            val boldType = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val regType = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            val pShop = Paint().apply {
                color = Color.BLACK
                textSize = 18f
                typeface = boldType
                textAlign = Paint.Align.CENTER
            }
            val pAddr = Paint().apply {
                color = Color.DKGRAY
                textSize = 10.5f
                typeface = boldType
                textAlign = Paint.Align.CENTER
            }
            val pDocTitle = Paint().apply {
                color = Color.parseColor("#1B5E20")
                textSize = 13.5f
                typeface = boldType
                textAlign = Paint.Align.CENTER
            }
            val pSecHdr = Paint().apply {
                color = Color.WHITE
                textSize = 11f
                typeface = boldType
                textAlign = Paint.Align.LEFT
            }
            val pCatLeft = Paint().apply {
                color = Color.BLACK
                textSize = 10.5f
                typeface = boldType
                textAlign = Paint.Align.LEFT
            }
            val pCatRight = Paint().apply {
                color = Color.BLACK
                textSize = 10.5f
                typeface = boldType
                textAlign = Paint.Align.RIGHT
            }
            val pChildLeft = Paint().apply {
                color = Color.DKGRAY
                textSize = 9.8f
                typeface = regType
                textAlign = Paint.Align.LEFT
            }
            val pChildRight = Paint().apply {
                color = Color.BLACK
                textSize = 9.8f
                typeface = regType
                textAlign = Paint.Align.RIGHT
            }
            val pFill = Paint().apply { style = Paint.Style.FILL }
            val pBorder = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }

            fun newPageIfNeeded(currentY: Float, neededHeight: Float): Float {
                if (currentY + neededHeight > pageHeight - margin) {
                    pdfDoc.finishPage(page)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    page = pdfDoc.startPage(pageInfo)
                    canvas = page.canvas
                    return margin + 18f
                }
                return currentY
            }

            val cx = pageWidth / 2f
            var y = 40f

            // Header
            canvas.drawText("THIRUVENI COCONUTS", cx, y, pShop)
            y += 16f
            canvas.drawText("SHOP NO B98, PERIYAR VEGETABLE MARKET, KOYAMBEDU, CHENNAI - 107", cx, y, pAddr)
            y += 20f
            canvas.drawText("COCONUT DAY BOOK  |  DATE: $selectedDateStr", cx, y, pDocTitle)
            y += 14f

            // 1. Opening Balance Box
            pFill.color = Color.parseColor("#E3F2FD")
            canvas.drawRect(margin, y, pageWidth - margin, y + 24f, pFill)
            canvas.drawRect(margin, y, pageWidth - margin, y + 24f, pBorder)
            pCatLeft.color = Color.parseColor("#0D47A1")
            pCatRight.color = Color.parseColor("#0D47A1")
            canvas.drawText("1. OPENING BALANCE (OP. BAL)", margin + 8f, y + 16f, pCatLeft)
            canvas.drawText("Rs. ${fmt(lastOpBal)}", pageWidth - margin - 8f, y + 16f, pCatRight)
            y += 32f

            // Helper to draw a TreeView section in PDF
            fun drawPdfTreeSection(
                secTitle: String,
                hdrColorHex: String,
                subBgHex: String,
                tree: LinkedHashMap<String, ArrayList<TreeEntry>>,
                subTotalLbl: String?,
                subTotalVal: Double,
                totalLbl: String,
                totalVal: Double
            ) {
                y = newPageIfNeeded(y, 44f)
                pFill.color = Color.parseColor(hdrColorHex)
                canvas.drawRect(margin, y, pageWidth - margin, y + 22f, pFill)
                canvas.drawRect(margin, y, pageWidth - margin, y + 22f, pBorder)
                canvas.drawText(secTitle, margin + 8f, y + 15f, pSecHdr)
                y += 22f

                for ((catName, children) in tree) {
                    y = newPageIfNeeded(y, 22f)
                    var catSum = 0.0
                    for (c in children) catSum += c.amount

                    pFill.color = Color.parseColor(subBgHex)
                    canvas.drawRect(margin, y, pageWidth - margin, y + 20f, pFill)
                    canvas.drawRect(margin, y, pageWidth - margin, y + 20f, pBorder)
                    pCatLeft.color = Color.parseColor(hdrColorHex)
                    pCatRight.color = Color.BLACK

                    val arrow = if (children.isNotEmpty()) "▼" else "•"
                    canvas.drawText("$arrow  $catName (${children.size})", margin + 8f, y + 14f, pCatLeft)
                    canvas.drawText("Rs. ${fmt(catSum)}", pageWidth - margin - 8f, y + 14f, pCatRight)
                    y += 20f

                    for (idx in children.indices) {
                        y = newPageIfNeeded(y, 18f)
                        val item = children[idx]
                        val branch = if (idx == children.size - 1) "└── " else "├── "
                        canvas.drawRect(margin, y, pageWidth - margin, y + 18f, pBorder)
                        canvas.drawText("$branch${item.label} (${item.subInfo})", margin + 22f, y + 13f, pChildLeft)
                        canvas.drawText(fmt(item.amount), pageWidth - margin - 8f, y + 13f, pChildRight)
                        y += 18f
                    }
                }

                if (subTotalLbl != null) {
                    y = newPageIfNeeded(y, 20f)
                    pFill.color = Color.parseColor("#E8F5E9")
                    canvas.drawRect(margin, y, pageWidth - margin, y + 20f, pFill)
                    canvas.drawRect(margin, y, pageWidth - margin, y + 20f, pBorder)
                    pCatLeft.color = Color.BLACK
                    pCatRight.color = Color.parseColor("#1B5E20")
                    canvas.drawText(subTotalLbl, margin + 8f, y + 14f, pCatLeft)
                    canvas.drawText("Rs. ${fmt(subTotalVal)}", pageWidth - margin - 8f, y + 14f, pCatRight)
                    y += 20f
                }

                y = newPageIfNeeded(y, 22f)
                pFill.color = Color.parseColor("#FFF9C4")
                canvas.drawRect(margin, y, pageWidth - margin, y + 22f, pFill)
                canvas.drawRect(margin, y, pageWidth - margin, y + 22f, pBorder)
                pCatLeft.color = Color.BLACK
                pCatRight.color = Color.parseColor(hdrColorHex)
                canvas.drawText(totalLbl, margin + 8f, y + 15f, pCatLeft)
                canvas.drawText("Rs. ${fmt(totalVal)}", pageWidth - margin - 8f, y + 15f, pCatRight)
                y += 30f
            }

            // 2. Incomes / Collection
            drawPdfTreeSection(
                secTitle = "2. INCOMES / COLLECTION",
                hdrColorHex = "#1B5E20",
                subBgHex = "#F1F8E9",
                tree = lastCollectionTree,
                subTotalLbl = "Today Collection Total",
                subTotalVal = lastTodayCollection,
                totalLbl = "Total (Op.Bal + Collection)",
                totalVal = lastTotalAvailable
            )

            // 3. Expenses (Category-wise TreeView)
            drawPdfTreeSection(
                secTitle = "3. EXPENSES (CATEGORY-WISE TREE VIEW)",
                hdrColorHex = "#C62828",
                subBgHex = "#FFEBEE",
                tree = lastExpenseTree,
                subTotalLbl = null,
                subTotalVal = 0.0,
                totalLbl = "Total Expenses (-)",
                totalVal = lastTotalExpense
            )

            // 4. Closing Balance Box
            y = newPageIfNeeded(y, 28f)
            val clHex = if (lastClosingBal >= 0.0) "#1B5E20" else "#C62828"
            val clBgHex = if (lastClosingBal >= 0.0) "#C8E6C9" else "#FFCDD2"
            pFill.color = Color.parseColor(clBgHex)
            canvas.drawRect(margin, y, pageWidth - margin, y + 26f, pFill)
            canvas.drawRect(margin, y, pageWidth - margin, y + 26f, pBorder)
            pCatLeft.color = Color.parseColor(clHex)
            pCatRight.color = Color.parseColor(clHex)
            canvas.drawText("4. CLOSING BALANCE", margin + 8f, y + 17f, pCatLeft)
            canvas.drawText("Rs. ${fmt(lastClosingBal)}", pageWidth - margin - 8f, y + 17f, pCatRight)

            pdfDoc.finishPage(page)

            // Save PDF via MediaStore so no FileProvider XML is needed
            val safeDate = selectedDateStr.replace("[^0-9A-Za-z_-]".toRegex(), "-")
            val fileName = "Coconut_DayBook_${safeDate}_${System.currentTimeMillis()}.pdf"

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VeggieReports")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = contentResolver
            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val pdfUri = resolver.insert(collectionUri, values)
            if (pdfUri == null) {
                pdfDoc.close()
                Toast.makeText(this, "Unable to create PDF file", Toast.LENGTH_SHORT).show()
                return
            }

            resolver.openOutputStream(pdfUri)?.use { out ->
                pdfDoc.writeTo(out)
                out.flush()
            }
            pdfDoc.close()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(pdfUri, values, null, null)
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                putExtra(Intent.EXTRA_SUBJECT, "Coconut Day Book - $selectedDateStr")
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                shareIntent.setPackage("com.whatsapp")
                startActivity(shareIntent)
            } catch (e: Exception) {
                try {
                    shareIntent.setPackage("com.whatsapp.w4b")
                    startActivity(shareIntent)
                } catch (ex: Exception) {
                    shareIntent.setPackage(null)
                    startActivity(Intent.createChooser(shareIntent, "Send Coconut Day Book PDF"))
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "PDF Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderDayBookCards(
        opBal: Double,
        collectionTree: LinkedHashMap<String, ArrayList<TreeEntry>>,
        todayCollection: Double,
        totalWithOpBal: Double,
        expenseTree: LinkedHashMap<String, ArrayList<TreeEntry>>,
        totalExpense: Double,
        closingBal: Double
    ) {
        contentContainer.removeAllViews()
        val shareSb = StringBuilder("COCONUT DAY BOOK - $selectedDateStr\n")
        shareSb.append("Opening Balance : Rs. ${fmt(opBal)}\n\n")

        val htmlSb = StringBuilder()

        // 1. OPENING BALANCE
        contentContainer.addView(
            makeSingleBannerCard("1. OPENING BALANCE (OP. BAL)", "Rs. ${fmt(opBal)}", "#1565C0", "#E3F2FD")
        )
        htmlSb.append("<tr class='op'><td colspan='2'>1. OPENING BALANCE (OP. BAL)</td><td class='r'>${fmt(opBal)}</td></tr>")

        // 2. INCOMES / COLLECTION (TREE VIEW)
        contentContainer.addView(
            makeTreeSectionCard(
                sectionTitle = "2. INCOMES / COLLECTION",
                headerBgHex = "#1B5E20",
                branchBgHex = "#F1F8E9",
                treeData = collectionTree,
                subTotalLabel = "Today Collection Total",
                subTotalValue = todayCollection,
                grandLabel = "Total (Op.Bal + Collection)",
                grandValue = totalWithOpBal,
                showZeroNodes = true,
                shareSb = shareSb,
                htmlSb = htmlSb
            )
        )

        // 3. EXPENSES (CATEGORY-WISE TREE VIEW: Shop, Other, Vehicle)
        contentContainer.addView(
            makeTreeSectionCard(
                sectionTitle = "3. EXPENSES (CATEGORY-WISE TREE VIEW)",
                headerBgHex = "#C62828",
                branchBgHex = "#FFEBEE",
                treeData = expenseTree,
                subTotalLabel = null,
                subTotalValue = 0.0,
                grandLabel = "Total Expenses (-)",
                grandValue = totalExpense,
                showZeroNodes = true,
                shareSb = shareSb,
                htmlSb = htmlSb
            )
        )

        // 4. CLOSING BALANCE
        val clColor = if (closingBal >= 0.0) "#1B5E20" else "#C62828"
        val clBg = if (closingBal >= 0.0) "#C8E6C9" else "#FFCDD2"
        contentContainer.addView(
            makeSingleBannerCard("4. CLOSING BALANCE", "Rs. ${fmt(closingBal)}", clColor, clBg)
        )

        shareSb.append("\n------------------------------\n")
        shareSb.append("Opening Balance  : Rs. ${fmt(opBal)}\n")
        shareSb.append("Today Collection : Rs. ${fmt(todayCollection)}\n")
        shareSb.append("Total Available  : Rs. ${fmt(totalWithOpBal)}\n")
        shareSb.append("Total Expenses   : Rs. ${fmt(totalExpense)}\n")
        shareSb.append("CLOSING BALANCE  : Rs. ${fmt(closingBal)}\n")
        shareText = shareSb.toString()

        htmlSb.append("<tr class='cl'><td colspan='2'>4. CLOSING BALANCE</td><td class='r'>${fmt(closingBal)}</td></tr>")

        printableHtml = """
            <html>
            <head>
            <style>
                @page { size: A4 portrait; margin: 8mm; }
                body { font-family: Arial, sans-serif; color: #000; font-size: 12px; }
                h1 { text-align: center; font-size: 19px; margin: 0; }
                h2 { text-align: center; font-size: 12px; margin: 2px 0 4px 0; }
                h3 { text-align: center; font-size: 15px; margin: 4px 0 10px 0; text-decoration: underline; }
                table { width: 100%; border-collapse: collapse; }
                th, td { border: 1px solid #000; padding: 6px; }
                th { background: #E0E0E0; text-align: left; }
                td.r { text-align: right; font-weight: bold; }
                tr.op td { background: #E3F2FD; font-weight: bold; font-size: 13px; }
                tr.cat td { background: #F5F5F5; font-weight: bold; }
                tr.child td.indent { padding-left: 24px; }
                tr.tot td { background: #FFF9C4; font-weight: bold; }
                tr.cl td { background: #C8E6C9; font-weight: bold; font-size: 14px; }
            </style>
            </head>
            <body>
                <h1>THIRUVENI COCONUTS</h1>
                <h2>SHOP NO B98, PERIYAR VEGETABLE MARKET, KOYAMBEDU</h2>
                <h3>COCONUT DAY BOOK  |  DATE: $selectedDateStr</h3>
                <table>
                    <thead>
                        <tr>
                            <th>PARTICULARS / CATEGORY</th>
                            <th>DETAILS / MODE</th>
                            <th style="text-align:right;">AMOUNT (Rs)</th>
                        </tr>
                    </thead>
                    <tbody>
                        $htmlSb
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()
    }

    private fun makeSingleBannerCard(label: String, value: String, textColorHex: String, bgHex: String): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setBackgroundColor(Color.parseColor(bgHex))
        card.setPadding(20, 18, 20, 18)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, 0, 12)
        card.layoutParams = lp

        val tvL = TextView(this)
        tvL.text = label
        tvL.textSize = 14.5f
        tvL.setTypeface(null, Typeface.BOLD)
        tvL.setTextColor(Color.parseColor(textColorHex))
        tvL.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val tvR = TextView(this)
        tvR.text = value
        tvR.textSize = 16.5f
        tvR.setTypeface(null, Typeface.BOLD)
        tvR.setTextColor(Color.parseColor(textColorHex))
        tvR.gravity = Gravity.END

        card.addView(tvL)
        card.addView(tvR)
        return card
    }

    private fun makeTreeSectionCard(
        sectionTitle: String,
        headerBgHex: String,
        branchBgHex: String,
        treeData: LinkedHashMap<String, ArrayList<TreeEntry>>,
        subTotalLabel: String?,
        subTotalValue: Double,
        grandLabel: String,
        grandValue: Double,
        showZeroNodes: Boolean,
        shareSb: StringBuilder,
        htmlSb: StringBuilder
    ): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundColor(Color.WHITE)
        card.setPadding(14, 14, 14, 14)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, 0, 12)
        card.layoutParams = lp

        val tvSec = TextView(this)
        tvSec.text = sectionTitle
        tvSec.textSize = 14f
        tvSec.setTypeface(null, Typeface.BOLD)
        tvSec.setTextColor(Color.WHITE)
        tvSec.setBackgroundColor(Color.parseColor(headerBgHex))
        tvSec.setPadding(14, 12, 14, 12)
        card.addView(tvSec)

        shareSb.append("=== $sectionTitle ===\n")

        for ((categoryName, children) in treeData) {
            if (!showZeroNodes && children.isEmpty()) continue

            var catSum = 0.0
            for (c in children) catSum += c.amount

            val parentRow = LinearLayout(this)
            parentRow.orientation = LinearLayout.HORIZONTAL
            parentRow.gravity = Gravity.CENTER_VERTICAL
            parentRow.setBackgroundColor(Color.parseColor(branchBgHex))
            parentRow.setPadding(14, 14, 14, 14)
            val pLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            pLp.setMargins(0, 8, 0, 2)
            parentRow.layoutParams = pLp

            val arrowChar = if (children.isNotEmpty()) "▼" else "•"
            val tvNodeTitle = TextView(this)
            tvNodeTitle.text = "$arrowChar  $categoryName (${children.size})"
            tvNodeTitle.textSize = 13.5f
            tvNodeTitle.setTypeface(null, Typeface.BOLD)
            tvNodeTitle.setTextColor(Color.parseColor(headerBgHex))
            tvNodeTitle.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val tvNodeTotal = TextView(this)
            tvNodeTotal.text = "Rs. ${fmt(catSum)}"
            tvNodeTotal.textSize = 13.5f
            tvNodeTotal.setTypeface(null, Typeface.BOLD)
            tvNodeTotal.setTextColor(Color.BLACK)
            tvNodeTotal.gravity = Gravity.END

            parentRow.addView(tvNodeTitle)
            parentRow.addView(tvNodeTotal)
            card.addView(parentRow)

            shareSb.append("• $categoryName : Rs. ${fmt(catSum)}\n")
            htmlSb.append("<tr class='cat'><td>$arrowChar $categoryName (${children.size})</td><td>Category Total</td><td class='r'>${fmt(catSum)}</td></tr>")

            if (children.isNotEmpty()) {
                val childrenBox = LinearLayout(this)
                childrenBox.orientation = LinearLayout.VERTICAL
                childrenBox.setPadding(24, 4, 8, 6)
                childrenBox.visibility = View.VISIBLE

                for (idx in children.indices) {
                    val entry = children[idx]
                    val isLastChild = (idx == children.size - 1)
                    val branchPrefix = if (isLastChild) "└── " else "├── "

                    val childRow = LinearLayout(this)
                    childRow.orientation = LinearLayout.HORIZONTAL
                    childRow.gravity = Gravity.CENTER_VERTICAL
                    childRow.setPadding(6, 8, 6, 8)

                    val tvChildLabel = TextView(this)
                    tvChildLabel.text = "$branchPrefix${entry.label} (${entry.subInfo})"
                    tvChildLabel.textSize = 12.5f
                    tvChildLabel.setTextColor(Color.DKGRAY)
                    tvChildLabel.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val tvChildAmt = TextView(this)
                    tvChildAmt.text = fmt(entry.amount)
                    tvChildAmt.textSize = 12.5f
                    tvChildAmt.setTypeface(null, Typeface.BOLD)
                    tvChildAmt.setTextColor(Color.BLACK)
                    tvChildAmt.gravity = Gravity.END

                    childRow.addView(tvChildLabel)
                    childRow.addView(tvChildAmt)
                    childrenBox.addView(childRow)

                    shareSb.append("   $branchPrefix${entry.label} : Rs. ${fmt(entry.amount)}\n")
                    htmlSb.append("<tr class='child'><td class='indent'>$branchPrefix${entry.label}</td><td>${entry.subInfo}</td><td class='r'>${fmt(entry.amount)}</td></tr>")
                }

                parentRow.setOnClickListener {
                    if (childrenBox.visibility == View.VISIBLE) {
                        childrenBox.visibility = View.GONE
                        tvNodeTitle.text = "▶  $categoryName (${children.size})"
                    } else {
                        childrenBox.visibility = View.VISIBLE
                        tvNodeTitle.text = "▼  $categoryName (${children.size})"
                    }
                }

                card.addView(childrenBox)
            }
        }

        if (subTotalLabel != null) {
            card.addView(makeSummaryStripRow(subTotalLabel, "Rs. ${fmt(subTotalValue)}", "#E8F5E9", "#1B5E20"))
            htmlSb.append("<tr class='tot'><td colspan='2'>$subTotalLabel</td><td class='r'>${fmt(subTotalValue)}</td></tr>")
        }

        card.addView(makeSummaryStripRow(grandLabel, "Rs. ${fmt(grandValue)}", "#FFF9C4", headerBgHex))
        shareSb.append("$grandLabel : Rs. ${fmt(grandValue)}\n\n")
        htmlSb.append("<tr class='tot'><td colspan='2'>$grandLabel</td><td class='r'>${fmt(grandValue)}</td></tr>")

        return card
    }

    private fun makeSummaryStripRow(label: String, value: String, bgHex: String, textHex: String): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setBackgroundColor(Color.parseColor(bgHex))
        row.setPadding(14, 12, 14, 12)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 6, 0, 0)
        row.layoutParams = lp

        val tvL = TextView(this)
        tvL.text = label
        tvL.textSize = 13.5f
        tvL.setTypeface(null, Typeface.BOLD)
        tvL.setTextColor(Color.BLACK)
        tvL.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val tvR = TextView(this)
        tvR.text = value
        tvR.textSize = 14f
        tvR.setTypeface(null, Typeface.BOLD)
        tvR.setTextColor(Color.parseColor(textHex))
        tvR.gravity = Gravity.END

        row.addView(tvL)
        row.addView(tvR)
        return row
    }

    private fun printDayBookPdf() {
        if (printableHtml.isEmpty()) {
            Toast.makeText(this, "Please wait for Day Book to load", Toast.LENGTH_SHORT).show()
            return
        }
        printWebView = WebView(this)
        printWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = printWebView.createPrintDocumentAdapter("Coconut_DayBook_$selectedDateStr")
                val builder = PrintAttributes.Builder()
                builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                builder.setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                printManager.print("Coconut Day Book", printAdapter, builder.build())
            }
        }
        printWebView.loadDataWithBaseURL(null, printableHtml, "text/HTML", "UTF-8", null)
        Toast.makeText(this, "Preparing A4 PDF...", Toast.LENGTH_SHORT).show()
    }
}
