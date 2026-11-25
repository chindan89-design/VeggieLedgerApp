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
import android.text.InputType
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
import android.widget.RadioButton
import android.widget.RadioGroup
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

class ExpenseIncomeActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    // Tab Controls
    private lateinit var btnTabExpense: Button
    private lateinit var btnTabIncome: Button
    private lateinit var layoutExpenseTab: LinearLayout
    private lateinit var layoutIncomeTab: LinearLayout

    // --- EXPENSE TAB CONTROLS ---
    private lateinit var etExpDate: EditText
    private lateinit var spExpCategory: Spinner
    private lateinit var spExpItem: Spinner
    private lateinit var etExpDesc: EditText
    private lateinit var etExpAmount: EditText
    private lateinit var rgExpMode: RadioGroup
    private lateinit var rbExpCash: RadioButton
    private lateinit var rbExpBank: RadioButton
    private lateinit var btnExpSave: Button
    private lateinit var btnExpClear: Button
    private lateinit var tvExpTotalBanner: TextView
    private lateinit var tblExpenses: TableLayout

    // --- INCOME TAB CONTROLS ---
    private lateinit var etIncDate: EditText
    private lateinit var spIncItem: Spinner
    private lateinit var etIncDesc: EditText
    private lateinit var etIncAmount: EditText
    private lateinit var rgIncMode: RadioGroup
    private lateinit var rbIncCash: RadioButton
    private lateinit var rbIncBank: RadioButton
    private lateinit var btnIncSave: Button
    private lateinit var btnIncClear: Button
    private lateinit var tvIncTotalBanner: TextView
    private lateinit var tblIncomes: TableLayout

    private lateinit var printWebView: WebView

    private var expDateStr: String = ""
    private var incDateStr: String = ""

    private var editingExpId: String = ""
    private var editingIncId: String = ""

    private val expCategories = arrayListOf("Other Expenses", "Shop Expenses", "Vehicle Expenses")

    // Empty initially except the single "+ ADD NEW EXPENSES" option at the bottom
    private val ADD_NEW_EXP_LABEL = "+ ADD NEW EXPENSES"
    private val expDropdownList = arrayListOf(ADD_NEW_EXP_LABEL)
    private lateinit var expItemAdapter: ArrayAdapter<String>

    // Empty initially except the single "+ ADD NEW INCOME" option at the bottom
    private val ADD_NEW_INC_LABEL = "+ ADD NEW INCOME"
    private val incDropdownList = arrayListOf(ADD_NEW_INC_LABEL)
    private lateinit var incItemAdapter: ArrayAdapter<String>

    private val expEntries = JSONArray()
    private val incEntries = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Expenses & Income"

        val cal = Calendar.getInstance()
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        expDateStr = today
        incDateStr = today

        buildTabbedUi()
        loadCustomDropdownHeads()
        loadExpensesForDate()
        loadIncomesForDate()
    }

    private fun fmtVal(value: Double): String {
        return if (abs(value) < 0.005) "-" else String.format(Locale.getDefault(), "%.2f", value)
    }

    private fun buildTabbedUi() {
        val rootScroll = ScrollView(this)
        rootScroll.setBackgroundColor(Color.parseColor("#ECEFF1"))
        rootScroll.setPadding(14, 14, 14, 14)

        val mainCol = LinearLayout(this)
        mainCol.orientation = LinearLayout.VERTICAL
        rootScroll.addView(mainCol)

        // TOP TAB BAR (EXPENSES | INCOME)
        val tabBar = LinearLayout(this)
        tabBar.orientation = LinearLayout.HORIZONTAL
        tabBar.weightSum = 2f
        val tabLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        tabLp.setMargins(0, 0, 0, 12)
        tabBar.layoutParams = tabLp

        btnTabExpense = Button(this)
        btnTabExpense.text = "EXPENSES (-)"
        btnTabExpense.setTypeface(null, Typeface.BOLD)
        btnTabExpense.textSize = 14f
        val lpT1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lpT1.setMargins(0, 0, 4, 0)
        btnTabExpense.layoutParams = lpT1

        btnTabIncome = Button(this)
        btnTabIncome.text = "INCOME (+)"
        btnTabIncome.setTypeface(null, Typeface.BOLD)
        btnTabIncome.textSize = 14f
        val lpT2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lpT2.setMargins(4, 0, 0, 0)
        btnTabIncome.layoutParams = lpT2

        btnTabExpense.setOnClickListener { switchTab(showExpense = true) }
        btnTabIncome.setOnClickListener { switchTab(showExpense = false) }

        tabBar.addView(btnTabExpense)
        tabBar.addView(btnTabIncome)
        mainCol.addView(tabBar)

        // Build both tab containers
        layoutExpenseTab = buildExpenseTabView()
        layoutIncomeTab = buildIncomeTabView()

        mainCol.addView(layoutExpenseTab)
        mainCol.addView(layoutIncomeTab)

        switchTab(showExpense = true)
        setContentView(rootScroll)
    }

    private fun switchTab(showExpense: Boolean) {
        if (showExpense) {
            layoutExpenseTab.visibility = View.VISIBLE
            layoutIncomeTab.visibility = View.GONE
            btnTabExpense.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#C62828"))
            btnTabExpense.setTextColor(Color.WHITE)
            btnTabIncome.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#CFD8DC"))
            btnTabIncome.setTextColor(Color.BLACK)
        } else {
            layoutExpenseTab.visibility = View.GONE
            layoutIncomeTab.visibility = View.VISIBLE
            btnTabIncome.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B5E20"))
            btnTabIncome.setTextColor(Color.WHITE)
            btnTabExpense.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#CFD8DC"))
            btnTabExpense.setTextColor(Color.BLACK)
        }
    }

    // =========================================================================
    // 1. EXPENSES TAB UI
    // =========================================================================
    private fun buildExpenseTabView(): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundColor(Color.WHITE)
        card.setPadding(18, 18, 18, 18)
        val cLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cLp.setMargins(0, 0, 0, 12)
        card.layoutParams = cLp

        val hdr = TextView(this)
        hdr.text = "ADD / EDIT EXPENSES"
        hdr.textSize = 15f
        hdr.setTypeface(null, Typeface.BOLD)
        hdr.setTextColor(Color.parseColor("#C62828"))
        hdr.gravity = Gravity.CENTER
        hdr.setPadding(0, 0, 0, 10)
        card.addView(hdr)

        // 1. Date & 2. Category (Other Expenses, Shop Expenses, Vehicle Expenses)
        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.weightSum = 2f
        row1.setPadding(0, 0, 0, 10)

        val colDate = LinearLayout(this)
        colDate.orientation = LinearLayout.VERTICAL
        colDate.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
        val lblDate = TextView(this)
        lblDate.text = "1. Date *"
        lblDate.textSize = 12f
        lblDate.setTypeface(null, Typeface.BOLD)
        etExpDate = EditText(this)
        etExpDate.setText(expDateStr)
        etExpDate.isFocusable = false
        etExpDate.isClickable = true
        etExpDate.setBackgroundResource(android.R.drawable.edit_text)
        etExpDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                expDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                etExpDate.setText(expDateStr)
                loadExpensesForDate()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        colDate.addView(lblDate)
        colDate.addView(etExpDate)

        val colCat = LinearLayout(this)
        colCat.orientation = LinearLayout.VERTICAL
        colCat.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f)
        val lblCat = TextView(this)
        lblCat.text = "2. Category *"
        lblCat.textSize = 12f
        lblCat.setTypeface(null, Typeface.BOLD)
        spExpCategory = Spinner(this)
        spExpCategory.setBackgroundResource(android.R.drawable.btn_dropdown)
        val catAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, expCategories)
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spExpCategory.adapter = catAdapter
        colCat.addView(lblCat)
        colCat.addView(spExpCategory)

        row1.addView(colDate)
        row1.addView(colCat)
        card.addView(row1)

        // 3. Expenses Dropdown Box (Starts empty with ONLY "+ ADD NEW EXPENSES" at bottom)
        val lblExpSpin = TextView(this)
        lblExpSpin.text = "3. Expenses *"
        lblExpSpin.textSize = 12f
        lblExpSpin.setTypeface(null, Typeface.BOLD)
        lblExpSpin.setTextColor(Color.parseColor("#C62828"))
        card.addView(lblExpSpin)

        spExpItem = Spinner(this)
        spExpItem.setBackgroundResource(android.R.drawable.btn_dropdown)
        expItemAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, expDropdownList)
        expItemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spExpItem.adapter = expItemAdapter
        val spLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        spLp.setMargins(0, 2, 0, 10)
        spExpItem.layoutParams = spLp

        spExpItem.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val chosen = expDropdownList.getOrNull(pos) ?: ""
                if (chosen == ADD_NEW_EXP_LABEL) {
                    showAddNewPopup(isExpense = true)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        card.addView(spExpItem)

        // 4. Description (e.g., 5 Tea, 10 Tea, 4 Members Tiffin)
        val lblDesc = TextView(this)
        lblDesc.text = "4. Description (e.g. 5 Tea, 10 Tea, 4 Members Tiffin)"
        lblDesc.textSize = 12f
        lblDesc.setTypeface(null, Typeface.BOLD)
        etExpDesc = EditText(this)
        etExpDesc.hint = "Enter count / notes..."
        etExpDesc.setBackgroundResource(android.R.drawable.edit_text)
        val descLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        descLp.setMargins(0, 2, 0, 10)
        etExpDesc.layoutParams = descLp
        card.addView(lblDesc)
        card.addView(etExpDesc)

        // 5. Expenses Amount + Radio Buttons (Cash / Bank) right next to it
        val lblAmt = TextView(this)
        lblAmt.text = "5. Expenses Amount & Mode *"
        lblAmt.textSize = 12f
        lblAmt.setTypeface(null, Typeface.BOLD)
        card.addView(lblAmt)

        val amtModeRow = LinearLayout(this)
        amtModeRow.orientation = LinearLayout.HORIZONTAL
        amtModeRow.gravity = Gravity.CENTER_VERTICAL
        amtModeRow.weightSum = 2f
        amtModeRow.setPadding(0, 2, 0, 12)

        etExpAmount = EditText(this)
        etExpAmount.hint = "0.00"
        etExpAmount.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        etExpAmount.setTypeface(null, Typeface.BOLD)
        etExpAmount.textSize = 16f
        etExpAmount.setBackgroundResource(android.R.drawable.edit_text)
        val lpAmt = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.05f)
        lpAmt.setMargins(0, 0, 8, 0)
        etExpAmount.layoutParams = lpAmt

        rgExpMode = RadioGroup(this)
        rgExpMode.orientation = RadioGroup.HORIZONTAL
        rgExpMode.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.95f)

        rbExpCash = RadioButton(this)
        rbExpCash.text = "Cash"
        rbExpCash.setTypeface(null, Typeface.BOLD)
        rbExpCash.isChecked = true

        rbExpBank = RadioButton(this)
        rbExpBank.text = "Bank"
        rbExpBank.setTypeface(null, Typeface.BOLD)

        rgExpMode.addView(rbExpCash)
        rgExpMode.addView(rbExpBank)

        amtModeRow.addView(etExpAmount)
        amtModeRow.addView(rgExpMode)
        card.addView(amtModeRow)

        // 6. SAVE | CLEAR | A4 PDF Buttons
        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.weightSum = 3f

        btnExpSave = Button(this)
        btnExpSave.text = "SAVE"
        btnExpSave.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#C62828"))
        btnExpSave.setTextColor(Color.WHITE)
        btnExpSave.setTypeface(null, Typeface.BOLD)
        val lpS = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)
        lpS.setMargins(0, 0, 4, 0)
        btnExpSave.layoutParams = lpS
        btnExpSave.setOnClickListener { saveExpenseEntry() }

        btnExpClear = Button(this)
        btnExpClear.text = "CLEAR"
        btnExpClear.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        btnExpClear.setTextColor(Color.WHITE)
        btnExpClear.setTypeface(null, Typeface.BOLD)
        val lpC = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)
        lpC.setMargins(4, 0, 4, 0)
        btnExpClear.layoutParams = lpC
        btnExpClear.setOnClickListener { resetExpForm() }

        val btnExpPrint = Button(this)
        btnExpPrint.text = "A4 PDF"
        btnExpPrint.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
        btnExpPrint.setTextColor(Color.WHITE)
        btnExpPrint.setTypeface(null, Typeface.BOLD)
        val lpP = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
        lpP.setMargins(4, 0, 0, 0)
        btnExpPrint.layoutParams = lpP
        btnExpPrint.setOnClickListener { printA4Sheet(isExpense = true) }

        btnRow.addView(btnExpSave)
        btnRow.addView(btnExpClear)
        btnRow.addView(btnExpPrint)
        card.addView(btnRow)
        col.addView(card)

        // Summary Banner
        tvExpTotalBanner = TextView(this)
        tvExpTotalBanner.text = "Total Expenses: Rs. 0.00"
        tvExpTotalBanner.setBackgroundColor(Color.parseColor("#FFEBEE"))
        tvExpTotalBanner.setTextColor(Color.parseColor("#B71C1C"))
        tvExpTotalBanner.setTypeface(null, Typeface.BOLD)
        tvExpTotalBanner.textSize = 14f
        tvExpTotalBanner.setPadding(16, 12, 16, 12)
        val bLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        bLp.setMargins(0, 0, 0, 10)
        tvExpTotalBanner.layoutParams = bLp
        col.addView(tvExpTotalBanner)

        // Grid View (Table) with Edit & Delete
        val gridCard = LinearLayout(this)
        gridCard.orientation = LinearLayout.VERTICAL
        gridCard.setBackgroundColor(Color.WHITE)
        gridCard.setPadding(14, 14, 14, 14)

        val hScroll = HorizontalScrollView(this)
        tblExpenses = TableLayout(this)
        tblExpenses.setBackgroundColor(Color.BLACK)
        tblExpenses.setPadding(1, 1, 1, 1)
        hScroll.addView(tblExpenses)
        gridCard.addView(hScroll)
        col.addView(gridCard)

        return col
    }

    // =========================================================================
    // 2. INCOME TAB UI
    // =========================================================================
    private fun buildIncomeTabView(): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundColor(Color.WHITE)
        card.setPadding(18, 18, 18, 18)
        val cLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cLp.setMargins(0, 0, 0, 12)
        card.layoutParams = cLp

        val hdr = TextView(this)
        hdr.text = "ADD / EDIT OTHER INCOME"
        hdr.textSize = 15f
        hdr.setTypeface(null, Typeface.BOLD)
        hdr.setTextColor(Color.parseColor("#1B5E20"))
        hdr.gravity = Gravity.CENTER
        hdr.setPadding(0, 0, 0, 10)
        card.addView(hdr)

        // 1. Date
        val lblDate = TextView(this)
        lblDate.text = "1. Date *"
        lblDate.textSize = 12f
        lblDate.setTypeface(null, Typeface.BOLD)
        etIncDate = EditText(this)
        etIncDate.setText(incDateStr)
        etIncDate.isFocusable = false
        etIncDate.isClickable = true
        etIncDate.setBackgroundResource(android.R.drawable.edit_text)
        etIncDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                incDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                etIncDate.setText(incDateStr)
                loadIncomesForDate()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        val dLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        dLp.setMargins(0, 2, 0, 10)
        etIncDate.layoutParams = dLp
        card.addView(lblDate)
        card.addView(etIncDate)

        // 2. Income Dropdown Box (Starts empty with ONLY "+ ADD NEW INCOME" at bottom)
        val lblIncSpin = TextView(this)
        lblIncSpin.text = "2. Income Particulars *"
        lblIncSpin.textSize = 12f
        lblIncSpin.setTypeface(null, Typeface.BOLD)
        lblIncSpin.setTextColor(Color.parseColor("#1B5E20"))
        card.addView(lblIncSpin)

        spIncItem = Spinner(this)
        spIncItem.setBackgroundResource(android.R.drawable.btn_dropdown)
        incItemAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, incDropdownList)
        incItemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spIncItem.adapter = incItemAdapter
        val spLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        spLp.setMargins(0, 2, 0, 10)
        spIncItem.layoutParams = spLp

        spIncItem.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val chosen = incDropdownList.getOrNull(pos) ?: ""
                if (chosen == ADD_NEW_INC_LABEL) {
                    showAddNewPopup(isExpense = false)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        card.addView(spIncItem)

        // 3. Description
        val lblDesc = TextView(this)
        lblDesc.text = "3. Description / Notes"
        lblDesc.textSize = 12f
        lblDesc.setTypeface(null, Typeface.BOLD)
        etIncDesc = EditText(this)
        etIncDesc.hint = "Enter details..."
        etIncDesc.setBackgroundResource(android.R.drawable.edit_text)
        val descLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        descLp.setMargins(0, 2, 0, 10)
        etIncDesc.layoutParams = descLp
        card.addView(lblDesc)
        card.addView(etIncDesc)

        // 4. Income Amount + Radio Buttons (Cash / Bank)
        val lblAmt = TextView(this)
        lblAmt.text = "4. Income Amount & Mode *"
        lblAmt.textSize = 12f
        lblAmt.setTypeface(null, Typeface.BOLD)
        card.addView(lblAmt)

        val amtModeRow = LinearLayout(this)
        amtModeRow.orientation = LinearLayout.HORIZONTAL
        amtModeRow.gravity = Gravity.CENTER_VERTICAL
        amtModeRow.weightSum = 2f
        amtModeRow.setPadding(0, 2, 0, 12)

        etIncAmount = EditText(this)
        etIncAmount.hint = "0.00"
        etIncAmount.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        etIncAmount.setTypeface(null, Typeface.BOLD)
        etIncAmount.textSize = 16f
        etIncAmount.setBackgroundResource(android.R.drawable.edit_text)
        val lpAmt = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.05f)
        lpAmt.setMargins(0, 0, 8, 0)
        etIncAmount.layoutParams = lpAmt

        rgIncMode = RadioGroup(this)
        rgIncMode.orientation = RadioGroup.HORIZONTAL
        rgIncMode.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.95f)

        rbIncCash = RadioButton(this)
        rbIncCash.text = "Cash"
        rbIncCash.setTypeface(null, Typeface.BOLD)
        rbIncCash.isChecked = true

        rbIncBank = RadioButton(this)
        rbIncBank.text = "Bank"
        rbIncBank.setTypeface(null, Typeface.BOLD)

        rgIncMode.addView(rbIncCash)
        rgIncMode.addView(rbIncBank)

        amtModeRow.addView(etIncAmount)
        amtModeRow.addView(rgIncMode)
        card.addView(amtModeRow)

        // 5. SAVE | CLEAR | A4 PDF Buttons
        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.weightSum = 3f

        btnIncSave = Button(this)
        btnIncSave.text = "SAVE"
        btnIncSave.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B5E20"))
        btnIncSave.setTextColor(Color.WHITE)
        btnIncSave.setTypeface(null, Typeface.BOLD)
        val lpS = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)
        lpS.setMargins(0, 0, 4, 0)
        btnIncSave.layoutParams = lpS
        btnIncSave.setOnClickListener { saveIncomeEntry() }

        btnIncClear = Button(this)
        btnIncClear.text = "CLEAR"
        btnIncClear.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        btnIncClear.setTextColor(Color.WHITE)
        btnIncClear.setTypeface(null, Typeface.BOLD)
        val lpC = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)
        lpC.setMargins(4, 0, 4, 0)
        btnIncClear.layoutParams = lpC
        btnIncClear.setOnClickListener { resetIncForm() }

        val btnIncPrint = Button(this)
        btnIncPrint.text = "A4 PDF"
        btnIncPrint.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
        btnIncPrint.setTextColor(Color.WHITE)
        btnIncPrint.setTypeface(null, Typeface.BOLD)
        val lpP = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
        lpP.setMargins(4, 0, 0, 0)
        btnIncPrint.layoutParams = lpP
        btnIncPrint.setOnClickListener { printA4Sheet(isExpense = false) }

        btnRow.addView(btnIncSave)
        btnRow.addView(btnIncClear)
        btnRow.addView(btnIncPrint)
        card.addView(btnRow)
        col.addView(card)

        // Summary Banner
        tvIncTotalBanner = TextView(this)
        tvIncTotalBanner.text = "Total Other Income: Rs. 0.00"
        tvIncTotalBanner.setBackgroundColor(Color.parseColor("#E8F5E9"))
        tvIncTotalBanner.setTextColor(Color.parseColor("#1B5E20"))
        tvIncTotalBanner.setTypeface(null, Typeface.BOLD)
        tvIncTotalBanner.textSize = 14f
        tvIncTotalBanner.setPadding(16, 12, 16, 12)
        val bLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        bLp.setMargins(0, 0, 0, 10)
        tvIncTotalBanner.layoutParams = bLp
        col.addView(tvIncTotalBanner)

        // Grid View (Table) with Edit & Delete
        val gridCard = LinearLayout(this)
        gridCard.orientation = LinearLayout.VERTICAL
        gridCard.setBackgroundColor(Color.WHITE)
        gridCard.setPadding(14, 14, 14, 14)

        val hScroll = HorizontalScrollView(this)
        tblIncomes = TableLayout(this)
        tblIncomes.setBackgroundColor(Color.BLACK)
        tblIncomes.setPadding(1, 1, 1, 1)
        hScroll.addView(tblIncomes)
        gridCard.addView(hScroll)
        col.addView(gridCard)

        return col
    }

    // =========================================================================
    // DROPDOWN MANAGEMENT ("+ ADD NEW EXPENSES" / "+ ADD NEW INCOME")
    // =========================================================================
    private fun loadCustomDropdownHeads() {
        db.collection("expense_heads").get().addOnSuccessListener { snap ->
            val savedExp = LinkedHashSet<String>()
            val savedInc = LinkedHashSet<String>()

            for (doc in snap) {
                val name = doc.getString("name")?.trim() ?: continue
                val type = doc.getString("type") ?: "Expense"
                if (name.isEmpty()) continue
                if (type.equals("Income", ignoreCase = true)) {
                    savedInc.add(name)
                } else {
                    savedExp.add(name)
                }
            }

            expDropdownList.clear()
            expDropdownList.addAll(savedExp)
            expDropdownList.add(ADD_NEW_EXP_LABEL) // Always at the bottom
            expItemAdapter.notifyDataSetChanged()
            if (savedExp.isNotEmpty()) spExpItem.setSelection(0)

            incDropdownList.clear()
            incDropdownList.addAll(savedInc)
            incDropdownList.add(ADD_NEW_INC_LABEL) // Always at the bottom
            incItemAdapter.notifyDataSetChanged()
            if (savedInc.isNotEmpty()) spIncItem.setSelection(0)
        }
    }

    private fun showAddNewPopup(isExpense: Boolean) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(40, 24, 40, 12)

        val etNewName = EditText(this)
        etNewName.hint = if (isExpense) "e.g. Tea, Tiffin, Coolie, Diesel" else "e.g. Commission, Empty Bag Sale"
        box.addView(etNewName)

        AlertDialog.Builder(this)
            .setTitle(if (isExpense) "Add New Expense Item" else "Add New Income Item")
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("ADD") { _, _ ->
                val newName = etNewName.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Enter a valid name", Toast.LENGTH_SHORT).show()
                    if (isExpense && expDropdownList.size > 1) spExpItem.setSelection(0)
                    if (!isExpense && incDropdownList.size > 1) spIncItem.setSelection(0)
                    return@setPositiveButton
                }

                if (isExpense) {
                    if (!expDropdownList.any { it.equals(newName, ignoreCase = true) }) {
                        // Insert right before "+ ADD NEW EXPENSES"
                        expDropdownList.add(expDropdownList.size - 1, newName)
                        expItemAdapter.notifyDataSetChanged()
                    }
                    val idx = expDropdownList.indexOfFirst { it.equals(newName, ignoreCase = true) }
                    if (idx >= 0) spExpItem.setSelection(idx)
                } else {
                    if (!incDropdownList.any { it.equals(newName, ignoreCase = true) }) {
                        // Insert right before "+ ADD NEW INCOME"
                        incDropdownList.add(incDropdownList.size - 1, newName)
                        incItemAdapter.notifyDataSetChanged()
                    }
                    val idx = incDropdownList.indexOfFirst { it.equals(newName, ignoreCase = true) }
                    if (idx >= 0) spIncItem.setSelection(idx)
                }

                val prefix = if (isExpense) "exp_" else "inc_"
                val docId = prefix + newName.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9]"), "_")
                val docMap = hashMapOf(
                    "name" to newName,
                    "type" to if (isExpense) "Expense" else "Income",
                    "createdAt" to System.currentTimeMillis()
                )
                db.collection("expense_heads").document(docId).set(docMap)
                Toast.makeText(this, "Added: $newName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel") { _, _ ->
                if (isExpense && expDropdownList.size > 1) spExpItem.setSelection(0)
                if (!isExpense && incDropdownList.size > 1) spIncItem.setSelection(0)
            }
            .show()
    }

    // =========================================================================
    // SAVE & LOAD EXPENSES
    // =========================================================================
    private fun saveExpenseEntry() {
        val category = spExpCategory.selectedItem?.toString() ?: "Other Expenses"
        val itemName = spExpItem.selectedItem?.toString() ?: ""
        if (itemName.isEmpty() || itemName == ADD_NEW_EXP_LABEL) {
            Toast.makeText(this, "Please select or add an Expense item", Toast.LENGTH_SHORT).show()
            showAddNewPopup(isExpense = true)
            return
        }

        val desc = etExpDesc.text.toString().trim()
        val amount = etExpAmount.text.toString().toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) {
            Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        val payMode = if (rbExpBank.isChecked) "Bank" else "Cash"
        btnExpSave.isEnabled = false

        val docId = if (editingExpId.isNotEmpty()) editingExpId else db.collection("expenses").document().id
        val data = hashMapOf<String, Any>(
            "id" to docId,
            "date" to expDateStr,
            "type" to "Expense",
            "category" to category,
            "itemName" to itemName,
            "description" to desc,
            "amount" to amount,
            "payMode" to payMode,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("expenses").document(docId).set(data)
            .addOnSuccessListener {
                Toast.makeText(this, if (editingExpId.isNotEmpty()) "Expense Updated!" else "Expense Saved!", Toast.LENGTH_SHORT).show()
                resetExpForm()
                loadExpensesForDate()
            }
            .addOnFailureListener {
                btnExpSave.isEnabled = true
                Toast.makeText(this, "Failed to save expense", Toast.LENGTH_SHORT).show()
            }
    }

    private fun resetExpForm() {
        editingExpId = ""
        etExpDesc.setText("")
        etExpAmount.setText("")
        rbExpCash.isChecked = true
        btnExpSave.text = "SAVE"
        btnExpSave.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#C62828"))
        btnExpSave.isEnabled = true
    }

    private fun loadExpensesForDate() {
        db.collection("expenses")
            .whereEqualTo("date", expDateStr)
            .get()
            .addOnSuccessListener { snap ->
                while (expEntries.length() > 0) expEntries.remove(0)
                var cashTot = 0.0
                var bankTot = 0.0

                val docs = snap.documents.sortedBy { d -> d.getLong("timestamp") ?: 0L }
                for (doc in docs) {
                    val type = doc.getString("type") ?: "Expense"
                    if (!type.equals("Expense", ignoreCase = true)) continue

                    val cat = doc.getString("category") ?: "Other Expenses"
                    val item = doc.getString("itemName") ?: cat
                    val desc = doc.getString("description") ?: ""
                    val amt = doc.getDouble("amount") ?: 0.0
                    val mode = doc.getString("payMode") ?: "Cash"

                    if (mode.equals("Bank", ignoreCase = true)) bankTot += amt else cashTot += amt

                    val obj = JSONObject()
                    obj.put("id", doc.id)
                    obj.put("category", cat)
                    obj.put("itemName", item)
                    obj.put("description", desc)
                    obj.put("amount", amt)
                    obj.put("payMode", mode)
                    expEntries.put(obj)
                }

                tvExpTotalBanner.text = String.format(
                    Locale.getDefault(),
                    "Date: %s  |  Cash: Rs. %s  |  Bank: Rs. %s  |  Total: Rs. %s",
                    expDateStr, fmtVal(cashTot), fmtVal(bankTot), fmtVal(cashTot + bankTot)
                )
                renderExpenseGrid(cashTot + bankTot)
            }
    }

    private fun renderExpenseGrid(totalAmt: Double) {
        tblExpenses.removeAllViews()

        val hdr = TableRow(this)
        hdr.setBackgroundColor(Color.BLACK)
        val bg = Color.parseColor("#C62828")
        hdr.addView(makeCell("CATEGORY", bold = true, bgColor = bg, textColor = Color.WHITE))
        hdr.addView(makeCell("EXPENSES", bold = true, bgColor = bg, textColor = Color.WHITE))
        hdr.addView(makeCell("DESCRIPTION", bold = true, bgColor = bg, textColor = Color.WHITE))
        hdr.addView(makeCell("MODE", bold = true, bgColor = bg, textColor = Color.WHITE))
        hdr.addView(makeCell("AMOUNT", alignRight = true, bold = true, bgColor = bg, textColor = Color.WHITE))
        hdr.addView(makeCell("EDIT", bold = true, bgColor = bg, textColor = Color.WHITE))
        hdr.addView(makeCell("DEL", bold = true, bgColor = bg, textColor = Color.WHITE))
        tblExpenses.addView(hdr)

        if (expEntries.length() == 0) {
            val empty = TableRow(this)
            empty.setBackgroundColor(Color.BLACK)
            val tv = makeCell("No expenses added for $expDateStr")
            val lp = TableRow.LayoutParams()
            lp.span = 7
            lp.setMargins(1, 1, 1, 1)
            tv.layoutParams = lp
            empty.addView(tv)
            tblExpenses.addView(empty)
            return
        }

        for (i in 0 until expEntries.length()) {
            val obj = expEntries.getJSONObject(i)
            val docId = obj.getString("id")
            val cat = obj.getString("category")
            val item = obj.getString("itemName")
            val desc = obj.getString("description")
            val mode = obj.getString("payMode")
            val amt = obj.getDouble("amount")
            val rBg = if (i % 2 == 0) Color.WHITE else Color.parseColor("#FFF8E1")

            val tr = TableRow(this)
            tr.setBackgroundColor(Color.BLACK)
            tr.addView(makeCell(cat, bgColor = rBg))
            tr.addView(makeCell(item, bold = true, bgColor = rBg))
            tr.addView(makeCell(if (desc.isEmpty()) "-" else desc, bgColor = rBg))
            tr.addView(makeCell(mode, bgColor = rBg))
            tr.addView(makeCell(fmtVal(amt), alignRight = true, bold = true, bgColor = rBg))

            val editTv = makeCell("EDIT", bold = true, bgColor = Color.parseColor("#E3F2FD"), textColor = Color.parseColor("#0D47A1"))
            editTv.setOnClickListener {
                editingExpId = docId
                val cIdx = expCategories.indexOfFirst { it.equals(cat, ignoreCase = true) }
                if (cIdx >= 0) spExpCategory.setSelection(cIdx)

                if (!expDropdownList.any { it.equals(item, ignoreCase = true) }) {
                    expDropdownList.add(expDropdownList.size - 1, item)
                    expItemAdapter.notifyDataSetChanged()
                }
                val iIdx = expDropdownList.indexOfFirst { it.equals(item, ignoreCase = true) }
                if (iIdx >= 0) spExpItem.setSelection(iIdx)

                etExpDesc.setText(desc)
                etExpAmount.setText(String.format(Locale.getDefault(), "%.2f", amt))
                if (mode.equals("Bank", ignoreCase = true)) rbExpBank.isChecked = true else rbExpCash.isChecked = true

                btnExpSave.text = "UPDATE"
                btnExpSave.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
                Toast.makeText(this, "Editing expense...", Toast.LENGTH_SHORT).show()
            }
            tr.addView(editTv)

            val delTv = makeCell("DEL", bold = true, bgColor = Color.parseColor("#FFEBEE"), textColor = Color.parseColor("#B71C1C"))
            delTv.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete Expense")
                    .setMessage("Delete $item (Rs. ${fmtVal(amt)})?")
                    .setPositiveButton("DELETE") { _, _ ->
                        db.collection("expenses").document(docId).delete()
                            .addOnSuccessListener {
                                if (editingExpId == docId) resetExpForm()
                                Toast.makeText(this, "Deleted!", Toast.LENGTH_SHORT).show()
                                loadExpensesForDate()
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            tr.addView(delTv)

            tblExpenses.addView(tr)
        }

        val totRow = TableRow(this)
        totRow.setBackgroundColor(Color.BLACK)
        val tBg = Color.parseColor("#FFCDD2")
        totRow.addView(makeCell("TOTAL", bold = true, bgColor = tBg))
        totRow.addView(makeCell("", bgColor = tBg))
        totRow.addView(makeCell("", bgColor = tBg))
        totRow.addView(makeCell("", bgColor = tBg))
        totRow.addView(makeCell(fmtVal(totalAmt), alignRight = true, bold = true, bgColor = tBg))
        totRow.addView(makeCell("", bgColor = tBg))
        totRow.addView(makeCell("", bgColor = tBg))
        tblExpenses.addView(totRow)
    }

    // =========================================================================
    // SAVE & LOAD INCOME
    // =========================================================================
    private fun saveIncomeEntry() {
        val itemName = spIncItem.selectedItem?.toString() ?: ""
        if (itemName.isEmpty() || itemName == ADD_NEW_INC_LABEL) {
            Toast.makeText(this, "Please select or add an Income item", Toast.LENGTH_SHORT).show()
            showAddNewPopup(isExpense = false)
            return
        }

        val desc = etIncDesc.text.toString().trim()
        val amount = etIncAmount.text.toString().toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) {
            Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        val payMode = if (rbIncBank.isChecked) "Bank" else "Cash"
        btnIncSave.isEnabled = false

        val docId = if (editingIncId.isNotEmpty()) editingIncId else db.collection("expenses").document().id
        val data = hashMapOf<String, Any>(
            "id" to docId,
            "date" to incDateStr,
            "type" to "Income",
            "category" to "Other Income",
            "itemName" to itemName,
            "description" to desc,
            "amount" to amount,
            "payMode" to payMode,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("expenses").document(docId).set(data)
            .addOnSuccessListener {
                Toast.makeText(this, if (editingIncId.isNotEmpty()) "Income Updated!" else "Income Saved!", Toast.LENGTH_SHORT).show()
                resetIncForm()
                loadIncomesForDate()
            }
            .addOnFailureListener {
                btnIncSave.isEnabled = true
                Toast.makeText(this, "Failed to save income", Toast.LENGTH_SHORT).show()
            }
    }

    private fun resetIncForm() {
        editingIncId = ""
        etIncDesc.setText("")
        etIncAmount.setText("")
        rbIncCash.isChecked = true
        btnIncSave.text = "SAVE"
        btnIncSave.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B5E20"))
        btnIncSave.isEnabled = true
    }

    private fun loadIncomesForDate() {
        db.collection("expenses")
            .whereEqualTo("date", incDateStr)
            .get()
            .addOnSuccessListener { snap ->
                while (incEntries.length() > 0) incEntries.remove(0)
                var cashTot = 0.0
                var bankTot = 0.0

                val docs = snap.documents.sortedBy { d -> d.getLong("timestamp") ?: 0L }
                for (doc in docs) {
                    val type = doc.getString("type") ?: "Expense"
                    if (!type.equals("Income", ignoreCase = true)) continue

                    val item = doc.getString("itemName") ?: doc.getString("category") ?: "Income"
                    val desc = doc.getString("description") ?: ""
                    val amt = doc.getDouble("amount") ?: 0.0
                    val mode = doc.getString("payMode") ?: "Cash"

                    if (mode.equals("Bank", ignoreCase = true)) bankTot += amt else cashTot += amt

                    val obj = JSONObject()
                    obj.put("id", doc.id)
                    obj.put("itemName", item)
                    obj.put("description", desc)
                    obj.put("amount", amt)
                    obj.put("payMode", mode)
                    incEntries.put(obj)
                }

                tvIncTotalBanner.text = String.format(
                    Locale.getDefault(),
                    "Date: %s  |  Cash: Rs. %s  |  Bank: Rs. %s  |  Total: Rs. %s",
                    incDateStr, fmtVal(cashTot), fmtVal(bankTot), fmtVal(cashTot + bankTot)
                )
                renderIncomeGrid(cashTot + bankTot)
            }
    }

    private fun renderIncomeGrid(totalAmt: Double) {
        tblIncomes.removeAllViews()

        val hdr = TableRow(this)
        hdr.setBackgroundColor(Color.BLACK)
        val bg = Color.parseColor("#1B5E20")
        hdr.addView(makeCell("INCOME ITEM", bold = true, bgColor = bg, textColor = Color.WHITE))
        hdr.addView(makeCell("DESCRIPTION", bold = true, bgColor = bg, textColor = Color.WHITE))
        hdr.addView(makeCell("MODE", bold = true, bgColor = bg, textColor = Color.WHITE))
        hdr.addView(makeCell("AMOUNT", alignRight = true, bold = true, bgColor = bg, textColor = Color.WHITE))
        hdr.addView(makeCell("EDIT", bold = true, bgColor = bg, textColor = Color.WHITE))
        hdr.addView(makeCell("DEL", bold = true, bgColor = bg, textColor = Color.WHITE))
        tblIncomes.addView(hdr)

        if (incEntries.length() == 0) {
            val empty = TableRow(this)
            empty.setBackgroundColor(Color.BLACK)
            val tv = makeCell("No income entries added for $incDateStr")
            val lp = TableRow.LayoutParams()
            lp.span = 6
            lp.setMargins(1, 1, 1, 1)
            tv.layoutParams = lp
            empty.addView(tv)
            tblIncomes.addView(empty)
            return
        }

        for (i in 0 until incEntries.length()) {
            val obj = incEntries.getJSONObject(i)
            val docId = obj.getString("id")
            val item = obj.getString("itemName")
            val desc = obj.getString("description")
            val mode = obj.getString("payMode")
            val amt = obj.getDouble("amount")
            val rBg = if (i % 2 == 0) Color.WHITE else Color.parseColor("#F1F8E9")

            val tr = TableRow(this)
            tr.setBackgroundColor(Color.BLACK)
            tr.addView(makeCell(item, bold = true, bgColor = rBg))
            tr.addView(makeCell(if (desc.isEmpty()) "-" else desc, bgColor = rBg))
            tr.addView(makeCell(mode, bgColor = rBg))
            tr.addView(makeCell(fmtVal(amt), alignRight = true, bold = true, bgColor = rBg))

            val editTv = makeCell("EDIT", bold = true, bgColor = Color.parseColor("#E3F2FD"), textColor = Color.parseColor("#0D47A1"))
            editTv.setOnClickListener {
                editingIncId = docId
                if (!incDropdownList.any { it.equals(item, ignoreCase = true) }) {
                    incDropdownList.add(incDropdownList.size - 1, item)
                    incItemAdapter.notifyDataSetChanged()
                }
                val iIdx = incDropdownList.indexOfFirst { it.equals(item, ignoreCase = true) }
                if (iIdx >= 0) spIncItem.setSelection(iIdx)

                etIncDesc.setText(desc)
                etIncAmount.setText(String.format(Locale.getDefault(), "%.2f", amt))
                if (mode.equals("Bank", ignoreCase = true)) rbIncBank.isChecked = true else rbIncCash.isChecked = true

                btnIncSave.text = "UPDATE"
                btnIncSave.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
                Toast.makeText(this, "Editing income...", Toast.LENGTH_SHORT).show()
            }
            tr.addView(editTv)

            val delTv = makeCell("DEL", bold = true, bgColor = Color.parseColor("#FFEBEE"), textColor = Color.parseColor("#B71C1C"))
            delTv.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete Income")
                    .setMessage("Delete $item (Rs. ${fmtVal(amt)})?")
                    .setPositiveButton("DELETE") { _, _ ->
                        db.collection("expenses").document(docId).delete()
                            .addOnSuccessListener {
                                if (editingIncId == docId) resetIncForm()
                                Toast.makeText(this, "Deleted!", Toast.LENGTH_SHORT).show()
                                loadIncomesForDate()
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            tr.addView(delTv)

            tblIncomes.addView(tr)
        }

        val totRow = TableRow(this)
        totRow.setBackgroundColor(Color.BLACK)
        val tBg = Color.parseColor("#C8E6C9")
        totRow.addView(makeCell("TOTAL", bold = true, bgColor = tBg))
        totRow.addView(makeCell("", bgColor = tBg))
        totRow.addView(makeCell("", bgColor = tBg))
        totRow.addView(makeCell(fmtVal(totalAmt), alignRight = true, bold = true, bgColor = tBg))
        totRow.addView(makeCell("", bgColor = tBg))
        totRow.addView(makeCell("", bgColor = tBg))
        tblIncomes.addView(totRow)
    }

    private fun makeCell(
        text: String,
        alignRight: Boolean = false,
        bold: Boolean = false,
        bgColor: Int = Color.WHITE,
        textColor: Int = Color.BLACK
    ): TextView {
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

    private fun printA4Sheet(isExpense: Boolean) {
        val list = if (isExpense) expEntries else incEntries
        val dateStr = if (isExpense) expDateStr else incDateStr
        val title = if (isExpense) "DAILY EXPENSES STATEMENT" else "DAILY OTHER INCOME STATEMENT"
        val rowsHtml = StringBuilder()
        var total = 0.0

        for (i in 0 until list.length()) {
            val obj = list.getJSONObject(i)
            val cat = obj.optString("category", "")
            val item = obj.optString("itemName", "")
            val desc = obj.optString("description", "")
            val mode = obj.optString("payMode", "Cash")
            val amt = obj.optDouble("amount", 0.0)
            total += amt

            if (isExpense) {
                rowsHtml.append("<tr><td class='center'>${i + 1}</td><td class='left'>$cat</td><td class='left'>$item</td><td class='left'>$desc</td><td class='center'>$mode</td><td>${fmtVal(amt)}</td></tr>")
            } else {
                rowsHtml.append("<tr><td class='center'>${i + 1}</td><td class='left'>$item</td><td class='left'>$desc</td><td class='center'>$mode</td><td>${fmtVal(amt)}</td></tr>")
            }
        }

        val colSpan = if (isExpense) 5 else 4
        rowsHtml.append("<tr class='total'><td colspan='$colSpan' class='left'>TOTAL</td><td>Rs. ${fmtVal(total)}</td></tr>")

        val tableHead = if (isExpense) {
            "<tr><th style='width:7%;'>S.NO</th><th style='width:22%;'>CATEGORY</th><th style='width:22%;'>EXPENSES</th><th style='width:24%;'>DESCRIPTION</th><th style='width:10%;'>MODE</th><th style='width:15%;'>AMOUNT</th></tr>"
        } else {
            "<tr><th style='width:8%;'>S.NO</th><th style='width:30%;'>INCOME ITEM</th><th style='width:34%;'>DESCRIPTION</th><th style='width:12%;'>MODE</th><th style='width:16%;'>AMOUNT</th></tr>"
        }

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
                tr.total td { font-weight: bold; background-color: #FFFDE7; border-top: 2px solid #000; border-bottom: 2px solid #000; font-size: 14px; }
            </style>
            </head>
            <body>
                <div class="center">
                    <h1>THIRUVENI COCONUTS</h1>
                    <h2>SHOP NO B98, PERIYAR VEGETABLE MARKET, KOYAMBEDU</h2>
                    <h3>$title</h3>
                </div>
                <div class="meta">DATE: $dateStr</div>
                <table class="grid">
                    <thead>$tableHead</thead>
                    <tbody>$rowsHtml</tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        printWebView = WebView(this)
        printWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = printWebView.createPrintDocumentAdapter("${title.replace(" ", "_")}_$dateStr")
                val builder = PrintAttributes.Builder()
                builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                builder.setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                printManager.print("$title A4", printAdapter, builder.build())
            }
        }
        printWebView.loadDataWithBaseURL(null, htmlDoc, "text/HTML", "UTF-8", null)
        Toast.makeText(this, "Preparing A4 PDF...", Toast.LENGTH_SHORT).show()
    }
}
