package com.shop.veggieledger

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.Gravity
import android.webkit.WebView
import android.webkit.WebViewClient
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
import kotlin.math.abs

class IncomeExpenseActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var etDate: EditText
    private lateinit var spType: Spinner
    private lateinit var spItemList: Spinner
    private lateinit var btnAddNewItem: Button
    private lateinit var spPayMode: Spinner
    private lateinit var etDesc: EditText
    private lateinit var etAmount: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancelEdit: Button

    private lateinit var tvCashSummary: TextView
    private lateinit var tvBankSummary: TextView
    private lateinit var tvNetSummary: TextView
    private lateinit var tableLayout: TableLayout
    private lateinit var printWebView: WebView

    private var selectedDateStr: String = ""
    private var editingDocId: String = ""
    private val dayEntries = JSONArray()

    private val entryTypes = arrayListOf("EXPENSE (-)", "INCOME (+)")
    private val itemNamesList = arrayListOf<String>()
    private lateinit var itemAdapter: ArrayAdapter<String>
    private val payModes = arrayListOf("Cash", "GPay / UPI")

    private val defaultItems = listOf(
        "Shop Expenses - Labour / Coolie",
        "Shop Expenses - Tea & Food",
        "Shop Expenses - Packing / Rope",
        "Shop Expenses - Rent / EB",
        "Vehicle Expenses - Diesel / Auto",
        "Other Expenses",
        "Other Income"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Income & Expenses"

        val cal = Calendar.getInstance()
        selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)

        buildUi(cal)
        loadDropdownItemsFromCloud()
        loadEntriesForDate()
    }

    private fun fmtVal(value: Double): String {
        return if (abs(value) < 0.005) "-" else String.format(Locale.getDefault(), "%.2f", value)
    }

    private fun buildUi(cal: Calendar) {
        val rootScroll = ScrollView(this)
        rootScroll.setBackgroundColor(Color.parseColor("#ECEFF1"))
        rootScroll.setPadding(16, 16, 16, 16)

        val mainCol = LinearLayout(this)
        mainCol.orientation = LinearLayout.VERTICAL
        rootScroll.addView(mainCol)

        // 1. TOP SUMMARY DASHBOARD CARD
        val summaryCard = LinearLayout(this)
        summaryCard.orientation = LinearLayout.VERTICAL
        summaryCard.setBackgroundColor(Color.WHITE)
        summaryCard.setPadding(18, 16, 18, 16)
        val sumLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        sumLp.setMargins(0, 0, 0, 12)
        summaryCard.layoutParams = sumLp

        val tvDashTitle = TextView(this)
        tvDashTitle.text = "DAILY INCOME & EXPENSE TALLY"
        tvDashTitle.textSize = 16f
        tvDashTitle.setTypeface(null, Typeface.BOLD)
        tvDashTitle.setTextColor(Color.parseColor("#1B5E20"))
        tvDashTitle.gravity = Gravity.CENTER
        tvDashTitle.setPadding(0, 0, 0, 10)
        summaryCard.addView(tvDashTitle)

        tvCashSummary = TextView(this)
        tvCashSummary.text = "Cash In: Rs. 0.00   |   Cash Out: Rs. 0.00"
        tvCashSummary.textSize = 13.5f
        tvCashSummary.setTypeface(null, Typeface.BOLD)
        tvCashSummary.setTextColor(Color.parseColor("#2E7D32"))
        tvCashSummary.setPadding(8, 6, 8, 6)
        summaryCard.addView(tvCashSummary)

        tvBankSummary = TextView(this)
        tvBankSummary.text = "GPay/Bank In: Rs. 0.00   |   GPay/Bank Out: Rs. 0.00"
        tvBankSummary.textSize = 13.5f
        tvBankSummary.setTypeface(null, Typeface.BOLD)
        tvBankSummary.setTextColor(Color.parseColor("#1565C0"))
        tvBankSummary.setPadding(8, 4, 8, 8)
        summaryCard.addView(tvBankSummary)

        tvNetSummary = TextView(this)
        tvNetSummary.text = "Net Cash: Rs. 0.00   |   Overall Net: Rs. 0.00"
        tvNetSummary.textSize = 15f
        tvNetSummary.setTypeface(null, Typeface.BOLD)
        tvNetSummary.setTextColor(Color.parseColor("#D84315"))
        tvNetSummary.setBackgroundColor(Color.parseColor("#FFF9C4"))
        tvNetSummary.setPadding(12, 10, 12, 10)
        summaryCard.addView(tvNetSummary)

        mainCol.addView(summaryCard)

        // 2. ENTRY FORM CARD
        val formCard = LinearLayout(this)
        formCard.orientation = LinearLayout.VERTICAL
        formCard.setBackgroundColor(Color.WHITE)
        formCard.setPadding(18, 18, 18, 18)
        val formLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        formLp.setMargins(0, 0, 0, 14)
        formCard.layoutParams = formLp

        // Row 1: Date & Entry Type
        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.weightSum = 2f
        row1.setPadding(0, 0, 0, 10)

        val colDate = LinearLayout(this)
        colDate.orientation = LinearLayout.VERTICAL
        colDate.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val lblDate = TextView(this)
        lblDate.text = "1. Date *"
        lblDate.textSize = 12f
        lblDate.setTypeface(null, Typeface.BOLD)
        etDate = EditText(this)
        etDate.setText(selectedDateStr)
        etDate.isFocusable = false
        etDate.isClickable = true
        etDate.setBackgroundResource(android.R.drawable.edit_text)
        etDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                etDate.setText(selectedDateStr)
                loadEntriesForDate()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        colDate.addView(lblDate)
        colDate.addView(etDate)

        val colType = LinearLayout(this)
        colType.orientation = LinearLayout.VERTICAL
        colType.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val lblType = TextView(this)
        lblType.text = "2. Entry Type *"
        lblType.textSize = 12f
        lblType.setTypeface(null, Typeface.BOLD)
        spType = Spinner(this)
        spType.setBackgroundResource(android.R.drawable.btn_dropdown)
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, entryTypes)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spType.adapter = typeAdapter
        colType.addView(lblType)
        colType.addView(spType)

        row1.addView(colDate)
        row1.addView(colType)
        formCard.addView(row1)

        // Row 2: Select Item Drop-Down + "+ ADD NEW" Button
        val lblItem = TextView(this)
        lblItem.text = "3. Select Expense / Income Item *"
        lblItem.textSize = 12f
        lblItem.setTypeface(null, Typeface.BOLD)
        lblItem.setTextColor(Color.parseColor("#1B5E20"))
        formCard.addView(lblItem)

        val rowItem = LinearLayout(this)
        rowItem.orientation = LinearLayout.HORIZONTAL
        rowItem.weightSum = 3f
        rowItem.setPadding(0, 2, 0, 10)

        spItemList = Spinner(this)
        spItemList.setBackgroundResource(android.R.drawable.btn_dropdown)
        val lpSpin = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.05f)
        lpSpin.setMargins(0, 0, 6, 0)
        spItemList.layoutParams = lpSpin

        itemNamesList.clear()
        itemNamesList.addAll(defaultItems)
        itemAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, itemNamesList)
        itemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spItemList.adapter = itemAdapter

        btnAddNewItem = Button(this)
        btnAddNewItem.text = "+ ADD NEW"
        btnAddNewItem.textSize = 12f
        btnAddNewItem.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1565C0"))
        btnAddNewItem.setTextColor(Color.WHITE)
        btnAddNewItem.setTypeface(null, Typeface.BOLD)
        val lpAddBtn = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.95f)
        btnAddNewItem.layoutParams = lpAddBtn
        btnAddNewItem.setOnClickListener { showAddNewItemDialog() }

        rowItem.addView(spItemList)
        rowItem.addView(btnAddNewItem)
        formCard.addView(rowItem)

        // Row 3: Mode & Amount
        val row3 = LinearLayout(this)
        row3.orientation = LinearLayout.HORIZONTAL
        row3.weightSum = 2f
        row3.setPadding(0, 0, 0, 8)

        val colMode = LinearLayout(this)
        colMode.orientation = LinearLayout.VERTICAL
        colMode.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val lblMode = TextView(this)
        lblMode.text = "4. Payment Mode *"
        lblMode.textSize = 12f
        lblMode.setTypeface(null, Typeface.BOLD)
        spPayMode = Spinner(this)
        spPayMode.setBackgroundResource(android.R.drawable.btn_dropdown)
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, payModes)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spPayMode.adapter = modeAdapter
        colMode.addView(lblMode)
        colMode.addView(spPayMode)

        val colAmt = LinearLayout(this)
        colAmt.orientation = LinearLayout.VERTICAL
        colAmt.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val lblAmt = TextView(this)
        lblAmt.text = "5. Amount (Rs) *"
        lblAmt.textSize = 12f
        lblAmt.setTypeface(null, Typeface.BOLD)
        etAmount = EditText(this)
        etAmount.hint = "0.00"
        etAmount.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        etAmount.setTypeface(null, Typeface.BOLD)
        etAmount.setBackgroundResource(android.R.drawable.edit_text)
        colAmt.addView(lblAmt)
        colAmt.addView(etAmount)

        row3.addView(colMode)
        row3.addView(colAmt)
        formCard.addView(row3)

        // Row 4: Optional Notes
        val lblDesc = TextView(this)
        lblDesc.text = "6. Notes / Remarks (Optional)"
        lblDesc.textSize = 12f
        lblDesc.setTypeface(null, Typeface.BOLD)
        etDesc = EditText(this)
        etDesc.hint = "Enter details if needed..."
        etDesc.setBackgroundResource(android.R.drawable.edit_text)
        val lpDesc = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lpDesc.setMargins(0, 0, 0, 12)
        etDesc.layoutParams = lpDesc
        formCard.addView(lblDesc)
        formCard.addView(etDesc)

        // Buttons Row: SAVE | CLEAR | PRINT A4 PDF
        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.weightSum = 3f

        btnSave = Button(this)
        btnSave.text = "SAVE ENTRY"
        btnSave.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B5E20"))
        btnSave.setTextColor(Color.WHITE)
        btnSave.setTypeface(null, Typeface.BOLD)
        val lpSave = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)
        lpSave.setMargins(0, 0, 4, 0)
        btnSave.layoutParams = lpSave
        btnSave.setOnClickListener { saveOrUpdateEntry() }

        btnCancelEdit = Button(this)
        btnCancelEdit.text = "CLEAR"
        btnCancelEdit.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        btnCancelEdit.setTextColor(Color.WHITE)
        btnCancelEdit.setTypeface(null, Typeface.BOLD)
        val lpCancel = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)
        lpCancel.setMargins(4, 0, 4, 0)
        btnCancelEdit.layoutParams = lpCancel
        btnCancelEdit.setOnClickListener { resetEntryForm() }

        val btnPrint = Button(this)
        btnPrint.text = "A4 PDF"
        btnPrint.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
        btnPrint.setTextColor(Color.WHITE)
        btnPrint.setTypeface(null, Typeface.BOLD)
        val lpPrint = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
        lpPrint.setMargins(4, 0, 0, 0)
        btnPrint.layoutParams = lpPrint
        btnPrint.setOnClickListener { printDailyA4Sheet() }

        btnRow.addView(btnSave)
        btnRow.addView(btnCancelEdit)
        btnRow.addView(btnPrint)
        formCard.addView(btnRow)

        mainCol.addView(formCard)

        // 3. DAY LEDGER TABLE CARD
        val listCard = LinearLayout(this)
        listCard.orientation = LinearLayout.VERTICAL
        listCard.setBackgroundColor(Color.WHITE)
        listCard.setPadding(16, 16, 16, 16)

        val tvListHdr = TextView(this)
        tvListHdr.text = "ENTRIES FOR SELECTED DATE (EDIT / DELETE)"
        tvListHdr.textSize = 14f
        tvListHdr.setTypeface(null, Typeface.BOLD)
        tvListHdr.setTextColor(Color.BLACK)
        tvListHdr.setPadding(0, 0, 0, 10)
        listCard.addView(tvListHdr)

        val hScroll = HorizontalScrollView(this)
        tableLayout = TableLayout(this)
        tableLayout.setBackgroundColor(Color.BLACK)
        tableLayout.setPadding(1, 1, 1, 1)
        hScroll.addView(tableLayout)
        listCard.addView(hScroll)

        mainCol.addView(listCard)
        setContentView(rootScroll)
    }

    /**
     * Loads saved custom expense/income items from Firestore ("expense_heads")
     * and merges them with default items in the dropdown.
     */
    private fun loadDropdownItemsFromCloud(selectItemAfterLoad: String = "") {
        db.collection("expense_heads").get().addOnSuccessListener { snap ->
            val combinedSet = LinkedHashSet<String>()
            combinedSet.addAll(defaultItems)

            for (doc in snap) {
                val itemName = doc.getString("name")?.trim() ?: ""
                if (itemName.isNotEmpty()) {
                    combinedSet.add(itemName)
                }
            }

            itemNamesList.clear()
            itemNamesList.addAll(combinedSet)
            itemAdapter.notifyDataSetChanged()

            if (selectItemAfterLoad.isNotEmpty()) {
                val idx = itemNamesList.indexOfFirst { it.equals(selectItemAfterLoad, ignoreCase = true) }
                if (idx >= 0) spItemList.setSelection(idx)
            }
        }
    }

    /**
     * Dialog triggered by "+ ADD NEW" button to add a new Expense/Income item
     * permanently into the dropdown list and Firestore.
     */
    private fun showAddNewItemDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(40, 24, 40, 12)

        val lblGroup = TextView(this)
        lblGroup.text = "Group Type (for Daily Closing Tally)"
        lblGroup.textSize = 12f
        lblGroup.setTypeface(null, Typeface.BOLD)
        box.addView(lblGroup)

        val spGroup = Spinner(this)
        val groups = listOf("Shop Expenses", "Vehicle Expenses", "Other Expenses", "Other Income")
        val grpAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, groups)
        grpAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spGroup.adapter = grpAdapter
        box.addView(spGroup)

        val lblName = TextView(this)
        lblName.text = "New Item Name *"
        lblName.textSize = 12f
        lblName.setTypeface(null, Typeface.BOLD)
        lblName.setPadding(0, 16, 0, 4)
        box.addView(lblName)

        val etNewName = EditText(this)
        etNewName.hint = "e.g. Lorry Freight, Diesel, Market Fee"
        box.addView(etNewName)

        AlertDialog.Builder(this)
            .setTitle("Add New Expense / Income Item")
            .setView(box)
            .setPositiveButton("ADD TO LIST") { _, _ ->
                val rawName = etNewName.text.toString().trim()
                if (rawName.isEmpty()) {
                    Toast.makeText(this, "Enter item name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val grp = spGroup.selectedItem?.toString() ?: "Other Expenses"
                val formattedName = if (rawName.contains("-")) rawName else "$grp - $rawName"

                // Immediately add to local dropdown and select it
                if (!itemNamesList.any { it.equals(formattedName, ignoreCase = true) }) {
                    itemNamesList.add(formattedName)
                    itemAdapter.notifyDataSetChanged()
                }
                val newIdx = itemNamesList.indexOfFirst { it.equals(formattedName, ignoreCase = true) }
                if (newIdx >= 0) spItemList.setSelection(newIdx)

                // If "Other Income" selected, switch Entry Type to INCOME (+) automatically
                if (grp.equals("Other Income", ignoreCase = true)) {
                    spType.setSelection(1)
                } else {
                    spType.setSelection(0)
                }

                // Save to Firestore for future sessions
                val docId = formattedName.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9]"), "_")
                val itemDoc = hashMapOf(
                    "name" to formattedName,
                    "group" to grp,
                    "createdAt" to System.currentTimeMillis()
                )
                db.collection("expense_heads").document(docId).set(itemDoc)
                Toast.makeText(this, "Added: $formattedName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveOrUpdateEntry() {
        val typeStr = spType.selectedItem?.toString() ?: "EXPENSE (-)"
        val isIncome = typeStr.startsWith("INCOME", ignoreCase = true)
        val selectedItemName = spItemList.selectedItem?.toString() ?: "Other Expenses"
        val mode = spPayMode.selectedItem?.toString() ?: "Cash"
        val desc = etDesc.text.toString().trim()
        val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0

        if (amount <= 0.0) {
            Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        btnSave.isEnabled = false
        val docId = if (editingDocId.isNotEmpty()) editingDocId else db.collection("expenses").document().id

        val data = hashMapOf<String, Any>(
            "id" to docId,
            "date" to selectedDateStr,
            "type" to if (isIncome) "Income" else "Expense",
            "category" to selectedItemName,
            "payMode" to mode,
            "description" to desc,
            "amount" to amount,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("expenses").document(docId).set(data)
            .addOnSuccessListener {
                val msg = if (editingDocId.isNotEmpty()) "Entry Updated!" else "Entry Saved!"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                resetEntryForm()
                loadEntriesForDate()
            }
            .addOnFailureListener {
                btnSave.isEnabled = true
                Toast.makeText(this, "Failed to save entry", Toast.LENGTH_SHORT).show()
            }
    }

    private fun resetEntryForm() {
        editingDocId = ""
        etDesc.setText("")
        etAmount.setText("")
        btnSave.text = "SAVE ENTRY"
        btnSave.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B5E20"))
        btnSave.isEnabled = true
    }

    private fun makeCell(text: String, alignRight: Boolean = false, bold: Boolean = false, bgColor: Int = Color.WHITE, textColor: Int = Color.BLACK): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(textColor)
        tv.setBackgroundColor(bgColor)
        tv.textSize = 13.5f
        tv.setPadding(14, 14, 14, 14)
        tv.gravity = if (alignRight) Gravity.END else Gravity.START
        if (bold) tv.setTypeface(null, Typeface.BOLD)
        val lp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.MATCH_PARENT)
        lp.setMargins(1, 1, 1, 1)
        tv.layoutParams = lp
        return tv
    }

    private fun loadEntriesForDate() {
        db.collection("expenses")
            .whereEqualTo("date", selectedDateStr)
            .get()
            .addOnSuccessListener { snap ->
                while (dayEntries.length() > 0) dayEntries.remove(0)

                var cashIn = 0.0
                var cashOut = 0.0
                var bankIn = 0.0
                var bankOut = 0.0

                val docs = snap.documents.sortedBy { d -> d.getLong("timestamp") ?: 0L }

                for (doc in docs) {
                    val type = doc.getString("type") ?: "Expense"
                    val isInc = type.equals("Income", ignoreCase = true)
                    val cat = doc.getString("category") ?: "Other Expenses"
                    val mode = doc.getString("payMode") ?: "Cash"
                    val desc = doc.getString("description") ?: ""
                    val amt = doc.getDouble("amount") ?: 0.0
                    val isCash = mode.equals("Cash", ignoreCase = true)

                    if (isInc) {
                        if (isCash) cashIn += amt else bankIn += amt
                    } else {
                        if (isCash) cashOut += amt else bankOut += amt
                    }

                    val obj = JSONObject()
                    obj.put("id", doc.id)
                    obj.put("type", type)
                    obj.put("category", cat)
                    obj.put("payMode", mode)
                    obj.put("description", desc)
                    obj.put("amount", amt)
                    dayEntries.put(obj)
                }

                val netCash = cashIn - cashOut
                val netTotal = (cashIn + bankIn) - (cashOut + bankOut)

                tvCashSummary.text = String.format(
                    Locale.getDefault(),
                    "Cash In: Rs. %s   |   Cash Out: Rs. %s",
                    fmtVal(cashIn), fmtVal(cashOut)
                )
                tvBankSummary.text = String.format(
                    Locale.getDefault(),
                    "GPay/Bank In: Rs. %s   |   GPay/Bank Out: Rs. %s",
                    fmtVal(bankIn), fmtVal(bankOut)
                )
                tvNetSummary.text = String.format(
                    Locale.getDefault(),
                    "Net Cash: Rs. %s   |   Overall Net: Rs. %s",
                    fmtVal(netCash), fmtVal(netTotal)
                )

                renderEntriesTable(cashIn + bankIn, cashOut + bankOut)
            }
    }

    private fun renderEntriesTable(totalIncAll: Double, totalExpAll: Double) {
        tableLayout.removeAllViews()

        val hdrRow = TableRow(this)
        hdrRow.setBackgroundColor(Color.BLACK)
        val hdrBg = Color.parseColor("#1B5E20")
        hdrRow.addView(makeCell("ITEM / PARTICULARS", bold = true, bgColor = hdrBg, textColor = Color.WHITE))
        hdrRow.addView(makeCell("MODE", bold = true, bgColor = hdrBg, textColor = Color.WHITE))
        hdrRow.addView(makeCell("INCOME (+)", alignRight = true, bold = true, bgColor = hdrBg, textColor = Color.WHITE))
        hdrRow.addView(makeCell("EXPENSE (-)", alignRight = true, bold = true, bgColor = hdrBg, textColor = Color.WHITE))
        hdrRow.addView(makeCell("EDIT", bold = true, bgColor = hdrBg, textColor = Color.WHITE))
        hdrRow.addView(makeCell("DEL", bold = true, bgColor = hdrBg, textColor = Color.WHITE))
        tableLayout.addView(hdrRow)

        if (dayEntries.length() == 0) {
            val emptyRow = TableRow(this)
            emptyRow.setBackgroundColor(Color.BLACK)
            val tv = makeCell("No income or expense entries for $selectedDateStr")
            val lp = TableRow.LayoutParams()
            lp.span = 6
            lp.setMargins(1, 1, 1, 1)
            tv.layoutParams = lp
            emptyRow.addView(tv)
            tableLayout.addView(emptyRow)
            return
        }

        for (i in 0 until dayEntries.length()) {
            val obj = dayEntries.getJSONObject(i)
            val docId = obj.getString("id")
            val type = obj.getString("type")
            val isInc = type.equals("Income", ignoreCase = true)
            val cat = obj.getString("category")
            val mode = obj.getString("payMode")
            val desc = obj.getString("description")
            val amt = obj.getDouble("amount")

            val label = if (desc.isNotEmpty()) "$cat\n($desc)" else cat
            val rowBg = if (i % 2 == 0) Color.WHITE else Color.parseColor("#F9FBE7")

            val tr = TableRow(this)
            tr.setBackgroundColor(Color.BLACK)
            tr.addView(makeCell(label, bold = true, bgColor = rowBg))
            tr.addView(makeCell(mode, bgColor = rowBg))
            tr.addView(makeCell(if (isInc) fmtVal(amt) else "-", alignRight = true, bold = isInc, bgColor = rowBg, textColor = Color.parseColor("#1B5E20")))
            tr.addView(makeCell(if (!isInc) fmtVal(amt) else "-", alignRight = true, bold = !isInc, bgColor = rowBg, textColor = Color.parseColor("#C62828")))

            val editTv = makeCell("EDIT", bold = true, bgColor = Color.parseColor("#E3F2FD"), textColor = Color.parseColor("#0D47A1"))
            editTv.setOnClickListener {
                editingDocId = docId
                spType.setSelection(if (isInc) 1 else 0)

                if (!itemNamesList.any { it.equals(cat, ignoreCase = true) }) {
                    itemNamesList.add(cat)
                    itemAdapter.notifyDataSetChanged()
                }
                val itemIdx = itemNamesList.indexOfFirst { it.equals(cat, ignoreCase = true) }
                if (itemIdx >= 0) spItemList.setSelection(itemIdx)

                val modeIdx = payModes.indexOfFirst { it.equals(mode, ignoreCase = true) }
                if (modeIdx >= 0) spPayMode.setSelection(modeIdx)
                etDesc.setText(desc)
                etAmount.setText(String.format(Locale.getDefault(), "%.2f", amt))

                btnSave.text = "UPDATE ENTRY"
                btnSave.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
                Toast.makeText(this, "Editing entry...", Toast.LENGTH_SHORT).show()
            }
            tr.addView(editTv)

            val delTv = makeCell("DEL", bold = true, bgColor = Color.parseColor("#FFEBEE"), textColor = Color.parseColor("#B71C1C"))
            delTv.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete Entry")
                    .setMessage("Delete $cat (Rs. ${fmtVal(amt)})?")
                    .setPositiveButton("DELETE") { _, _ ->
                        db.collection("expenses").document(docId).delete()
                            .addOnSuccessListener {
                                if (editingDocId == docId) resetEntryForm()
                                Toast.makeText(this, "Deleted!", Toast.LENGTH_SHORT).show()
                                loadEntriesForDate()
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            tr.addView(delTv)

            tableLayout.addView(tr)
        }

        // Total Row
        val totRow = TableRow(this)
        totRow.setBackgroundColor(Color.BLACK)
        val totBg = Color.parseColor("#C8E6C9")
        totRow.addView(makeCell("TOTAL", bold = true, bgColor = totBg))
        totRow.addView(makeCell("", bold = true, bgColor = totBg))
        totRow.addView(makeCell(fmtVal(totalIncAll), alignRight = true, bold = true, bgColor = totBg))
        totRow.addView(makeCell(fmtVal(totalExpAll), alignRight = true, bold = true, bgColor = totBg))
        totRow.addView(makeCell("", bgColor = totBg))
        totRow.addView(makeCell("", bgColor = totBg))
        tableLayout.addView(totRow)
    }

    private fun printDailyA4Sheet() {
        val rowsHtml = StringBuilder()
        var totInc = 0.0
        var totExp = 0.0

        for (i in 0 until dayEntries.length()) {
            val obj = dayEntries.getJSONObject(i)
            val isInc = obj.getString("type").equals("Income", ignoreCase = true)
            val cat = obj.getString("category")
            val mode = obj.getString("payMode")
            val desc = obj.getString("description")
            val amt = obj.getDouble("amount")

            if (isInc) totInc += amt else totExp += amt
            val particulars = if (desc.isNotEmpty()) "$cat - $desc" else cat

            rowsHtml.append(
                "<tr><td class='center'>${i + 1}</td><td class='left'>$particulars</td><td class='center'>$mode</td><td>${if (isInc) fmtVal(amt) else "-"}</td><td>${if (!isInc) fmtVal(amt) else "-"}</td></tr>"
            )
        }

        val netBal = totInc - totExp
        rowsHtml.append(
            "<tr class='total'><td colspan='3' class='left'>TOTAL</td><td>${fmtVal(totInc)}</td><td>${fmtVal(totExp)}</td></tr>"
        )
        rowsHtml.append(
            "<tr class='net'><td colspan='4' class='left'>NET BALANCE (INCOME - EXPENSE)</td><td>Rs. ${fmtVal(netBal)}</td></tr>"
        )

        val htmlDoc = """
            <html>
            <head>
            <style>
                * { box-sizing: border-box; }
                @page { size: A4 portrait; margin: 6mm; }
                body { font-family: Arial, sans-serif; color: #000; margin: 0; padding: 0; }
                .center { text-align: center; }
                .left { text-align: left !important; }
                h1 { font-size: 21px; margin: 0; }
                h2 { font-size: 13px; margin: 2px 0 4px 0; }
                h3 { font-size: 16px; margin: 4px 0 8px 0; text-decoration: underline; }
                .meta { font-size: 13.5px; font-weight: bold; margin-bottom: 6px; }
                table.grid { width: 100%; border-collapse: collapse; font-size: 13px; }
                table.grid th, table.grid td { border: 1px solid #000; padding: 6px; text-align: right; }
                table.grid th { background-color: #E0E0E0; text-align: center; font-weight: bold; }
                tr.total td { font-weight: bold; background-color: #FFFDE7; border-top: 2px solid #000; }
                tr.net td { font-weight: bold; background-color: #E8F5E9; border-top: 2px solid #000; border-bottom: 2px solid #000; font-size: 14px; }
            </style>
            </head>
            <body>
                <div class="center">
                    <h1>THIRUVENI COCONUTS</h1>
                    <h2>SHOP NO B98, PERIYAR VEGETABLE MARKET, KOYAMBEDU</h2>
                    <h3>DAILY INCOME &amp; EXPENSE STATEMENT</h3>
                </div>
                <div class="meta">DATE: $selectedDateStr</div>
                <table class="grid">
                    <thead>
                        <tr>
                            <th style="width:8%;">S.NO</th>
                            <th style="width:46%;">PARTICULARS</th>
                            <th style="width:14%;">MODE</th>
                            <th style="width:16%;">INCOME (+)</th>
                            <th style="width:16%;">EXPENSE (-)</th>
                        </tr>
                    </thead>
                    <tbody>$rowsHtml</tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        printWebView = WebView(this)
        printWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = printWebView.createPrintDocumentAdapter("Income_Expense_$selectedDateStr")
                val builder = PrintAttributes.Builder()
                builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                builder.setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                printManager.print("Income Expense A4", printAdapter, builder.build())
            }
        }
        printWebView.loadDataWithBaseURL(null, htmlDoc, "text/HTML", "UTF-8", null)
        Toast.makeText(this, "Preparing Daily Income & Expense A4 PDF...", Toast.LENGTH_SHORT).show()
    }
}
