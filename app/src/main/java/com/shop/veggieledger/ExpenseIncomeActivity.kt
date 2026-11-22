package com.shop.veggieledger

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityExpenseIncomeBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ExpenseIncomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExpenseIncomeBinding
    private val db = FirebaseFirestore.getInstance()

    private var currentType: String = "Expense"
    private var selectedDateStr: String = ""
    private val pendingGridList = JSONArray()
    private var gridTotalAmount = 0.0

    private val payModes = arrayListOf("Cash", "GPay / UPI", "Bank Transfer")

    private val mainCategories = arrayListOf(
        "Shop Expenses",
        "Vehicle Expenses",
        "Other Expenses"
    )

    private val defaultExpenseHeads = arrayListOf(
        "Water",
        "Tea / Coffee",
        "Tiffin",
        "Juice / Soup",
        "Staff Salary",
        "Vehicle Rent",
        "Weighbridge Charges",
        "Gate Entry Charges",
        "Unloading Coolie",
        "Labour Lunch Expenses"
    )

    private val dbExpenseHeadsList = arrayListOf("+ Add New Item")
    private val ADD_NEW_OPTION = "+ Add New Item"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpenseIncomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Expenses & Income"

        binding.btnTypeExpense.setOnClickListener { switchToExpenseMode() }
        binding.btnTypeIncome.setOnClickListener { switchToIncomeMode() }

        val cal = Calendar.getInstance()
        selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        binding.etExpDate.setText(selectedDateStr)

        binding.etExpDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                binding.etExpDate.setText(selectedDateStr)
                loadSavedEntriesForDate()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, payModes)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spExpPayMode.adapter = modeAdapter

        val mainCatAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mainCategories)
        mainCatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spMainCategory.adapter = mainCatAdapter

        bindExpenseHeadsSpinner()
        loadExpenseHeadsFromDatabase()

        val selectionListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                checkCustomExpenseVisibility()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spMainCategory.onItemSelectedListener = selectionListener
        binding.spAllExpenseHeads.onItemSelectedListener = selectionListener

        // Add Expense Row to Data Grid
        binding.btnAddExpenseRow.setOnClickListener {
            val chosenMainCat = binding.spMainCategory.selectedItem?.toString() ?: "Shop Expenses"
            val chosenHead = binding.spAllExpenseHeads.selectedItem?.toString() ?: ""
            val amt = binding.etExpAmount.text.toString().toDoubleOrNull() ?: 0.0
            val note = binding.etExpRemarks.text.toString().trim()

            val isNewOrOther = chosenMainCat.equals("Other Expenses", ignoreCase = true) ||
                    chosenHead == ADD_NEW_OPTION

            val finalExpName = if (isNewOrOther) {
                binding.etCustomExpenseName.text.toString().trim()
            } else {
                chosenHead
            }

            if (finalExpName.isEmpty()) {
                Toast.makeText(this, "Please enter New Expense Name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (amt <= 0.0) {
                Toast.makeText(this, "Please enter valid Amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isNewOrOther) {
                saveNewExpenseHeadToDatabase(finalExpName)
            }

            val rowObj = JSONObject()
            rowObj.put("type", "Expense")
            rowObj.put("category", chosenMainCat)
            rowObj.put("expenseName", finalExpName)
            rowObj.put("amount", amt)
            rowObj.put("remarks", note)

            pendingGridList.put(rowObj)
            refreshDataGrid()

            binding.etCustomExpenseName.setText("")
            binding.etExpAmount.setText("")
            binding.etExpRemarks.setText("")
            binding.btnAddExpenseRow.text = "+ ADD EXPENSE TO GRID"
        }

        // Add Income Row to Data Grid
        binding.btnAddIncomeRow.setOnClickListener {
            val incName = binding.etIncomeName.text.toString().trim()
            val amt = binding.etIncAmount.text.toString().toDoubleOrNull() ?: 0.0

            if (incName.isEmpty()) {
                Toast.makeText(this, "Please enter Income Name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (amt <= 0.0) {
                Toast.makeText(this, "Please enter valid Income Amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val rowObj = JSONObject()
            rowObj.put("type", "Income")
            rowObj.put("category", "Income")
            rowObj.put("expenseName", incName)
            rowObj.put("amount", amt)
            rowObj.put("remarks", "")

            pendingGridList.put(rowObj)
            refreshDataGrid()

            binding.etIncomeName.setText("")
            binding.etIncAmount.setText("")
            binding.btnAddIncomeRow.text = "+ ADD INCOME TO GRID"
        }

        // Save All Entries from Data Grid to Firestore
        binding.btnSaveAllExpInc.setOnClickListener {
            if (pendingGridList.length() == 0) {
                Toast.makeText(this, "Add at least one entry to the grid first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSaveAllExpInc.isEnabled = false
            val payMode = binding.spExpPayMode.selectedItem?.toString() ?: "Cash"
            val totalCount = pendingGridList.length()
            var savedCount = 0

            for (i in 0 until totalCount) {
                val item = pendingGridList.getJSONObject(i)
                val docId = db.collection("expenses_income").document().id
                val dataMap = hashMapOf(
                    "id" to docId,
                    "type" to item.getString("type"),
                    "date" to selectedDateStr,
                    "category" to item.getString("category"),
                    "subCategory" to item.getString("expenseName"),
                    "amount" to item.getDouble("amount"),
                    "payMode" to payMode,
                    "remarks" to item.optString("remarks", ""),
                    "timestamp" to (System.currentTimeMillis() + i)
                )

                db.collection("expenses_income").document(docId).set(dataMap)
                    .addOnSuccessListener {
                        savedCount++
                        if (savedCount == totalCount) {
                            Toast.makeText(this, "All $totalCount entries saved successfully!", Toast.LENGTH_SHORT).show()
                            clearJsonArray(pendingGridList)
                            refreshDataGrid()
                            binding.btnSaveAllExpInc.isEnabled = true
                            loadSavedEntriesForDate()
                        }
                    }
                    .addOnFailureListener {
                        binding.btnSaveAllExpInc.isEnabled = true
                        Toast.makeText(this, "Error saving some entries", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        refreshDataGrid()
        loadSavedEntriesForDate()
    }

    private fun loadExpenseHeadsFromDatabase(selectNameAfterLoad: String = "") {
        db.collection("expense_heads").get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    for (headName in defaultExpenseHeads) {
                        val docId = db.collection("expense_heads").document().id
                        db.collection("expense_heads").document(docId).set(
                            hashMapOf("id" to docId, "name" to headName)
                        )
                    }
                    dbExpenseHeadsList.clear()
                    dbExpenseHeadsList.addAll(defaultExpenseHeads)
                    dbExpenseHeadsList.sortBy { it.lowercase(Locale.getDefault()) }
                    dbExpenseHeadsList.add(ADD_NEW_OPTION)
                    bindExpenseHeadsSpinner(selectNameAfterLoad)
                } else {
                    val tempNames = arrayListOf<String>()
                    for (doc in snap) {
                        val name = doc.getString("name")?.trim() ?: ""
                        if (name.isNotEmpty() && !tempNames.any { it.equals(name, ignoreCase = true) }) {
                            tempNames.add(name)
                        }
                    }
                    tempNames.sortBy { it.lowercase(Locale.getDefault()) }
                    dbExpenseHeadsList.clear()
                    dbExpenseHeadsList.addAll(tempNames)
                    dbExpenseHeadsList.add(ADD_NEW_OPTION)
                    bindExpenseHeadsSpinner(selectNameAfterLoad)
                }
            }
            .addOnFailureListener {
                dbExpenseHeadsList.clear()
                dbExpenseHeadsList.addAll(defaultExpenseHeads)
                dbExpenseHeadsList.add(ADD_NEW_OPTION)
                bindExpenseHeadsSpinner(selectNameAfterLoad)
            }
    }

    private fun bindExpenseHeadsSpinner(selectName: String = "") {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, dbExpenseHeadsList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spAllExpenseHeads.adapter = adapter

        if (selectName.isNotEmpty()) {
            val idx = dbExpenseHeadsList.indexOfFirst { it.equals(selectName, ignoreCase = true) }
            if (idx >= 0) binding.spAllExpenseHeads.setSelection(idx)
        }
        checkCustomExpenseVisibility()
    }

    private fun saveNewExpenseHeadToDatabase(newHeadName: String) {
        val exists = dbExpenseHeadsList.any { it.equals(newHeadName, ignoreCase = true) }
        if (exists) return

        val docId = db.collection("expense_heads").document().id
        db.collection("expense_heads").document(docId)
            .set(hashMapOf("id" to docId, "name" to newHeadName))
            .addOnSuccessListener {
                Toast.makeText(this, "'$newHeadName' saved to Expense Database!", Toast.LENGTH_SHORT).show()
                loadExpenseHeadsFromDatabase(newHeadName)
            }
    }

    private fun checkCustomExpenseVisibility() {
        val chosenMain = binding.spMainCategory.selectedItem?.toString() ?: ""
        val chosenHead = binding.spAllExpenseHeads.selectedItem?.toString() ?: ""

        val showCustomBox = chosenMain.equals("Other Expenses", ignoreCase = true) ||
                chosenHead == ADD_NEW_OPTION

        binding.layoutCustomExpenseName.visibility = if (showCustomBox) View.VISIBLE else View.GONE
    }

    private fun switchToExpenseMode() {
        currentType = "Expense"
        binding.layoutExpenseCard.visibility = View.VISIBLE
        binding.layoutIncomeCard.visibility = View.GONE
        binding.btnTypeExpense.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#C62828"))
        binding.btnTypeIncome.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        binding.tvExpIncHeader.text = "EXPENSE ENTRY"
        binding.tvExpIncHeader.setTextColor(Color.parseColor("#C62828"))
        binding.btnSaveAllExpInc.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#C62828"))
    }

    private fun switchToIncomeMode() {
        currentType = "Income"
        binding.layoutExpenseCard.visibility = View.GONE
        binding.layoutIncomeCard.visibility = View.VISIBLE
        binding.btnTypeIncome.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B5E20"))
        binding.btnTypeExpense.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        binding.tvExpIncHeader.text = "INCOME ENTRY"
        binding.tvExpIncHeader.setTextColor(Color.parseColor("#1B5E20"))
        binding.btnSaveAllExpInc.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B5E20"))
    }

    private fun makeCell(text: String, alignRight: Boolean = false, bold: Boolean = false, colorHex: String = "#000000"): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.parseColor(colorHex))
        tv.textSize = 12f
        tv.setPadding(16, 14, 16, 14)
        tv.gravity = if (alignRight) Gravity.END else Gravity.START
        if (bold) tv.setTypeface(null, Typeface.BOLD)
        return tv
    }

    private fun refreshDataGrid() {
        val childCount = binding.tlExpIncGrid.childCount
        if (childCount > 1) {
            binding.tlExpIncGrid.removeViews(1, childCount - 1)
        }

        gridTotalAmount = 0.0

        if (pendingGridList.length() == 0) {
            val emptyRow = TableRow(this)
            emptyRow.setBackgroundColor(Color.WHITE)
            val emptyTv = TextView(this)
            emptyTv.text = "No entries added to grid yet."
            emptyTv.setTextColor(Color.GRAY)
            emptyTv.setPadding(18, 18, 18, 18)
            val lp = TableRow.LayoutParams()
            lp.span = 4
            emptyTv.layoutParams = lp
            emptyRow.addView(emptyTv)
            binding.tlExpIncGrid.addView(emptyRow)
            binding.tvExpIncGrandTotal.text = "Total Grid Amount: Rs. 0.00"
            return
        }

        for (idx in 0 until pendingGridList.length()) {
            val row = pendingGridList.getJSONObject(idx)
            val rowType = row.getString("type")
            val cat = row.getString("category")
            val expName = row.getString("expenseName")
            val amt = row.getDouble("amount")
            val note = row.optString("remarks", "")
            gridTotalAmount += amt

            val tr = TableRow(this)
            tr.setBackgroundColor(if (idx % 2 == 0) Color.WHITE else Color.parseColor("#F5F7FA"))

            val nameDisplay = if (note.isNotEmpty()) "$expName ($note)" else expName
            val amtColor = if (rowType.equals("Income", ignoreCase = true)) "#1B5E20" else "#C62828"

            tr.addView(makeCell(cat, bold = true))
            tr.addView(makeCell(nameDisplay))
            tr.addView(makeCell("%.2f".format(amt), alignRight = true, bold = true, colorHex = amtColor))

            val actionBox = LinearLayout(this)
            actionBox.orientation = LinearLayout.HORIZONTAL
            actionBox.gravity = Gravity.CENTER

            val editBtn = TextView(this)
            editBtn.text = "EDIT"
            editBtn.setTextColor(Color.parseColor("#1565C0"))
            editBtn.setTypeface(null, Typeface.BOLD)
            editBtn.textSize = 11f
            editBtn.setPadding(12, 14, 12, 14)
            editBtn.setOnClickListener {
                loadGridRowForEdit(idx)
            }

            val delBtn = TextView(this)
            delBtn.text = "X"
            delBtn.setTextColor(Color.RED)
            delBtn.setTypeface(null, Typeface.BOLD)
            delBtn.textSize = 12f
            delBtn.setPadding(14, 14, 14, 14)
            delBtn.setOnClickListener {
                pendingGridList.remove(idx)
                refreshDataGrid()
            }

            actionBox.addView(editBtn)
            actionBox.addView(delBtn)
            tr.addView(actionBox)

            binding.tlExpIncGrid.addView(tr)
        }

        binding.tvExpIncGrandTotal.text = "Total Grid Amount: Rs. %.2f".format(gridTotalAmount)
    }

    private fun loadGridRowForEdit(index: Int) {
        if (index < 0 || index >= pendingGridList.length()) return
        val row = pendingGridList.getJSONObject(index)
        val rowType = row.getString("type")
        val cat = row.getString("category")
        val expName = row.getString("expenseName")
        val amt = row.getDouble("amount")
        val note = row.optString("remarks", "")

        if (rowType.equals("Income", ignoreCase = true)) {
            switchToIncomeMode()
            binding.etIncomeName.setText(expName)
            binding.etIncAmount.setText("%.2f".format(amt))
            binding.btnAddIncomeRow.text = "UPDATE INCOME IN GRID"
        } else {
            switchToExpenseMode()
            val mainIdx = mainCategories.indexOfFirst { it.equals(cat, ignoreCase = true) }
            if (mainIdx >= 0) binding.spMainCategory.setSelection(mainIdx)

            val headIdx = dbExpenseHeadsList.indexOfFirst { it.equals(expName, ignoreCase = true) }
            if (headIdx >= 0 && !cat.equals("Other Expenses", ignoreCase = true)) {
                binding.spAllExpenseHeads.setSelection(headIdx)
                binding.layoutCustomExpenseName.visibility = View.GONE
            } else {
                val addNewIdx = dbExpenseHeadsList.indexOf(ADD_NEW_OPTION)
                if (addNewIdx >= 0) binding.spAllExpenseHeads.setSelection(addNewIdx)
                binding.layoutCustomExpenseName.visibility = View.VISIBLE
                binding.etCustomExpenseName.setText(expName)
            }

            binding.etExpAmount.setText("%.2f".format(amt))
            binding.etExpRemarks.setText(note)
            binding.btnAddExpenseRow.text = "UPDATE EXPENSE IN GRID"
        }

        pendingGridList.remove(index)
        refreshDataGrid()
        Toast.makeText(this, "Loaded row for editing", Toast.LENGTH_SHORT).show()
    }

    private fun clearJsonArray(arr: JSONArray) {
        while (arr.length() > 0) arr.remove(0)
    }

    private fun loadSavedEntriesForDate() {
        binding.tvSavedEntriesTitle.text = "SAVED ENTRIES FOR $selectedDateStr"
        binding.llSavedDateEntries.removeAllViews()

        db.collection("expenses_income")
            .whereEqualTo("date", selectedDateStr)
            .get()
            .addOnSuccessListener { snap ->
                binding.llSavedDateEntries.removeAllViews()
                if (snap.isEmpty) {
                    val emptyTv = TextView(this)
                    emptyTv.text = "No saved entries for $selectedDateStr."
                    emptyTv.setTextColor(Color.GRAY)
                    emptyTv.setPadding(12, 12, 12, 12)
                    binding.llSavedDateEntries.addView(emptyTv)
                    return@addOnSuccessListener
                }

                for (doc in snap) {
                    val type = doc.getString("type") ?: "Expense"
                    val cat = doc.getString("category") ?: ""
                    val subCat = doc.getString("subCategory") ?: ""
                    val amt = doc.getDouble("amount") ?: 0.0
                    val rem = doc.getString("remarks") ?: ""

                    val rowCard = LinearLayout(this)
                    rowCard.orientation = LinearLayout.HORIZONTAL
                    rowCard.gravity = Gravity.CENTER_VERTICAL
                    rowCard.setBackgroundColor(Color.WHITE)
                    rowCard.setPadding(16, 14, 16, 14)
                    val cardLp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    cardLp.setMargins(0, 0, 0, 8)
                    rowCard.layoutParams = cardLp

                    val remText = if (rem.isNotEmpty()) " ($rem)" else ""
                    val infoTv = TextView(this)
                    infoTv.text = "[$cat] $subCat$remText\nRs. %.2f".format(amt)
                    infoTv.setTextColor(
                        if (type.equals("Income", ignoreCase = true)) Color.parseColor("#1B5E20")
                        else Color.parseColor("#C62828")
                    )
                    infoTv.setTypeface(null, Typeface.BOLD)
                    infoTv.textSize = 13f
                    infoTv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val delBtn = TextView(this)
                    delBtn.text = "DEL"
                    delBtn.setTextColor(Color.WHITE)
                    delBtn.setBackgroundColor(Color.parseColor("#C62828"))
                    delBtn.setTypeface(null, Typeface.BOLD)
                    delBtn.textSize = 12f
                    delBtn.setPadding(24, 14, 24, 14)
                    delBtn.setOnClickListener {
                        AlertDialog.Builder(this)
                            .setTitle("Delete Saved Entry")
                            .setMessage("Delete $subCat (Rs. %.2f)?".format(amt))
                            .setPositiveButton("DELETE") { _, _ ->
                                db.collection("expenses_income").document(doc.id).delete()
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Deleted!", Toast.LENGTH_SHORT).show()
                                        loadSavedEntriesForDate()
                                    }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }

                    rowCard.addView(infoTv)
                    rowCard.addView(delBtn)
                    binding.llSavedDateEntries.addView(rowCard)
                }
            }
    }
}
