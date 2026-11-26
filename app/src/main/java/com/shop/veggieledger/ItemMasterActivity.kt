package com.shop.veggieledger

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class ItemMasterActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var rootScroll: ScrollView
    private lateinit var etItemName: EditText
    private lateinit var spItemUnit: Spinner
    private lateinit var etDefaultRate: EditText
    private lateinit var btnSaveItem: Button
    private lateinit var btnClearItem: Button
    private lateinit var etSearchItem: EditText
    private lateinit var tvItemCount: TextView
    private lateinit var llFullItemList: LinearLayout

    private val units = arrayListOf("Kgs", "Bags", "Nos")
    private val allItemsJson = JSONArray()
    private var editingItemId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Item Master"

        buildUiProgrammatically()
        loadItemsFromDb()
    }

    private fun buildUiProgrammatically() {
        rootScroll = ScrollView(this)
        rootScroll.setBackgroundColor(Color.parseColor("#F5F7FA"))
        rootScroll.setPadding(20, 20, 20, 20)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        rootScroll.addView(root)

        val title = TextView(this)
        title.text = "ITEM REGISTRATION & MASTER"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.parseColor("#1B5E20"))
        title.gravity = Gravity.CENTER
        title.setPadding(0, 0, 0, 14)
        root.addView(title)

        // Form Card
        val formCard = LinearLayout(this)
        formCard.orientation = LinearLayout.VERTICAL
        formCard.setBackgroundColor(Color.WHITE)
        formCard.setPadding(20, 20, 20, 20)
        val formLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        formLp.setMargins(0, 0, 0, 16)
        formCard.layoutParams = formLp

        formCard.addView(makeLabel("Item Name *"))
        etItemName = EditText(this)
        etItemName.hint = "e.g. Coconut, Onion, Garlic"
        etItemName.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        etItemName.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etItemName)

        formCard.addView(makeLabel("Unit Type *"))
        spItemUnit = Spinner(this)
        spItemUnit.setBackgroundResource(android.R.drawable.btn_dropdown)
        val spLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (48 * resources.displayMetrics.density).toInt()
        )
        spLp.setMargins(0, 0, 0, 8)
        spItemUnit.layoutParams = spLp
        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, units)
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spItemUnit.adapter = unitAdapter
        formCard.addView(spItemUnit)

        formCard.addView(makeLabel("Default Rate (Rs)"))
        etDefaultRate = EditText(this)
        etDefaultRate.hint = "0.00"
        etDefaultRate.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        etDefaultRate.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etDefaultRate)

        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.weightSum = 2f
        btnRow.setPadding(0, 12, 0, 0)

        btnClearItem = Button(this)
        btnClearItem.text = "RESET / CLEAR"
        btnClearItem.setBackgroundColor(Color.parseColor("#D32F2F"))
        btnClearItem.setTextColor(Color.WHITE)
        btnClearItem.setTypeface(null, Typeface.BOLD)
        val lpClear = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
        lpClear.setMargins(0, 0, 6, 0)
        btnClearItem.layoutParams = lpClear
        btnClearItem.setOnClickListener { resetForm() }

        btnSaveItem = Button(this)
        btnSaveItem.text = "SUBMIT ITEM"
        btnSaveItem.setBackgroundColor(Color.parseColor("#1B5E20"))
        btnSaveItem.setTextColor(Color.WHITE)
        btnSaveItem.setTypeface(null, Typeface.BOLD)
        val lpSave = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f)
        lpSave.setMargins(6, 0, 0, 0)
        btnSaveItem.layoutParams = lpSave
        btnSaveItem.setOnClickListener { saveOrUpdateItem() }

        btnRow.addView(btnClearItem)
        btnRow.addView(btnSaveItem)
        formCard.addView(btnRow)
        root.addView(formCard)

        // Search Box
        etSearchItem = EditText(this)
        etSearchItem.hint = "Search Item by Name or Unit..."
        etSearchItem.inputType = InputType.TYPE_CLASS_TEXT
        etSearchItem.setBackgroundResource(android.R.drawable.edit_text)
        etSearchItem.setPadding(24, 20, 24, 20)
        val searchLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        searchLp.setMargins(0, 0, 0, 8)
        etSearchItem.layoutParams = searchLp
        etSearchItem.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderFilteredItemList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        root.addView(etSearchItem)

        tvItemCount = TextView(this)
        tvItemCount.text = "Loading items..."
        tvItemCount.textSize = 13f
        tvItemCount.setTypeface(null, Typeface.BOLD)
        tvItemCount.setTextColor(Color.parseColor("#1B5E20"))
        tvItemCount.setPadding(4, 0, 4, 8)
        root.addView(tvItemCount)

        llFullItemList = LinearLayout(this)
        llFullItemList.orientation = LinearLayout.VERTICAL
        root.addView(llFullItemList)

        setContentView(rootScroll)
    }

    private fun makeLabel(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 12.5f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setTextColor(Color.parseColor("#1B5E20"))
        tv.setPadding(2, 6, 0, 2)
        return tv
    }

    private fun saveOrUpdateItem() {
        val name = etItemName.text.toString().trim()
        val unit = spItemUnit.selectedItem?.toString() ?: "Kgs"
        val rate = etDefaultRate.text.toString().toDoubleOrNull() ?: 0.0

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter Item Name", Toast.LENGTH_SHORT).show()
            return
        }

        btnSaveItem.isEnabled = false

        if (editingItemId.isNotEmpty()) {
            val updateMap = hashMapOf<String, Any>(
                "name" to name,
                "itemName" to name,
                "unit" to unit,
                "defaultRate" to rate
            )
            db.collection("items").document(editingItemId).update(updateMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Item Updated!", Toast.LENGTH_SHORT).show()
                    resetForm()
                    loadItemsFromDb()
                }
                .addOnFailureListener {
                    btnSaveItem.isEnabled = true
                    Toast.makeText(this, "Failed to update item", Toast.LENGTH_SHORT).show()
                }
        } else {
            val docId = db.collection("items").document().id
            val dataMap = hashMapOf<String, Any>(
                "id" to docId,
                "name" to name,
                "itemName" to name,
                "unit" to unit,
                "defaultRate" to rate
            )
            db.collection("items").document(docId).set(dataMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Item Saved!", Toast.LENGTH_SHORT).show()
                    resetForm()
                    loadItemsFromDb()
                }
                .addOnFailureListener {
                    btnSaveItem.isEnabled = true
                    Toast.makeText(this, "Failed to save item", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadItemsFromDb() {
        db.collection("items").get().addOnSuccessListener { snap ->
            while (allItemsJson.length() > 0) allItemsJson.remove(0)

            val sortedDocs = snap.documents.sortedBy { doc ->
                (doc.getString("name") ?: doc.getString("itemName") ?: "").lowercase(Locale.getDefault())
            }

            for (doc in sortedDocs) {
                val obj = JSONObject()
                obj.put("id", doc.id)
                obj.put("name", doc.getString("name") ?: doc.getString("itemName") ?: "")
                obj.put("unit", doc.getString("unit") ?: "Kgs")
                obj.put("defaultRate", doc.getDouble("defaultRate") ?: 0.0)
                allItemsJson.put(obj)
            }

            renderFilteredItemList(etSearchItem.text.toString())
        }
    }

    private fun renderFilteredItemList(query: String) {
        llFullItemList.removeAllViews()
        val q = query.trim().lowercase(Locale.getDefault())
        var shownCount = 0

        for (i in 0 until allItemsJson.length()) {
            val obj = allItemsJson.getJSONObject(i)
            val docId = obj.optString("id", "")
            val name = obj.optString("name", "")
            val unit = obj.optString("unit", "Kgs")
            val rate = obj.optDouble("defaultRate", 0.0)

            if (q.isNotEmpty()) {
                val matchName = name.lowercase(Locale.getDefault()).contains(q)
                val matchUnit = unit.lowercase(Locale.getDefault()).contains(q)
                if (!matchName && !matchUnit) {
                    continue
                }
            }

            shownCount++

            val card = LinearLayout(this)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.setBackgroundColor(Color.WHITE)
            card.setPadding(20, 16, 16, 16)
            val cardLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cardLp.setMargins(0, 0, 0, 8)
            card.layoutParams = cardLp

            val infoTv = TextView(this)
            val rateText = if (rate > 0.0) String.format(Locale.getDefault(), "  |  Rate: Rs. %.2f", rate) else ""
            infoTv.text = String.format(
                Locale.getDefault(),
                "%d. %s\nUnit: %s%s",
                shownCount,
                name,
                unit,
                rateText
            )
            infoTv.setTextColor(Color.BLACK)
            infoTv.setTypeface(null, Typeface.BOLD)
            infoTv.textSize = 13.5f
            infoTv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val editBtn = TextView(this)
            editBtn.text = "EDIT"
            editBtn.setTextColor(Color.WHITE)
            editBtn.setBackgroundColor(Color.parseColor("#1565C0"))
            editBtn.setTypeface(null, Typeface.BOLD)
            editBtn.textSize = 12f
            editBtn.setPadding(22, 14, 22, 14)
            val editLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            editLp.setMargins(8, 0, 6, 0)
            editBtn.layoutParams = editLp
            editBtn.setOnClickListener {
                loadItemIntoForm(obj)
            }

            val delBtn = TextView(this)
            delBtn.text = "DEL"
            delBtn.setTextColor(Color.WHITE)
            delBtn.setBackgroundColor(Color.parseColor("#C62828"))
            delBtn.setTypeface(null, Typeface.BOLD)
            delBtn.textSize = 12f
            delBtn.setPadding(18, 14, 18, 14)
            delBtn.setOnClickListener {
                if (docId.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Delete Item")
                        .setMessage("Are you sure you want to delete $name?")
                        .setPositiveButton("DELETE") { _, _ ->
                            db.collection("items").document(docId).delete()
                                .addOnSuccessListener {
                                    if (editingItemId == docId) resetForm()
                                    Toast.makeText(this, "Item Deleted!", Toast.LENGTH_SHORT).show()
                                    loadItemsFromDb()
                                }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }

            card.addView(infoTv)
            card.addView(editBtn)
            card.addView(delBtn)
            llFullItemList.addView(card)
        }

        tvItemCount.text = "Showing $shownCount of ${allItemsJson.length()} Items"

        if (shownCount == 0) {
            val emptyTv = TextView(this)
            emptyTv.text = "No matching items found."
            emptyTv.setTextColor(Color.GRAY)
            emptyTv.setPadding(16, 16, 16, 16)
            llFullItemList.addView(emptyTv)
        }
    }

    private fun loadItemIntoForm(obj: JSONObject) {
        editingItemId = obj.optString("id", "")
        etItemName.setText(obj.optString("name", ""))

        val rate = obj.optDouble("defaultRate", 0.0)
        etDefaultRate.setText(if (rate > 0.0) String.format(Locale.getDefault(), "%.2f", rate) else "")

        val unit = obj.optString("unit", "Kgs")
        val idx = units.indexOfFirst { it.equals(unit, ignoreCase = true) }
        if (idx >= 0) spItemUnit.setSelection(idx)

        btnSaveItem.text = "UPDATE ITEM"
        rootScroll.smoothScrollTo(0, 0)
        Toast.makeText(this, "Editing ${obj.optString("name", "")}", Toast.LENGTH_SHORT).show()
    }

    private fun resetForm() {
        editingItemId = ""
        etItemName.setText("")
        etDefaultRate.setText("")
        spItemUnit.setSelection(0)
        btnSaveItem.text = "SUBMIT ITEM"
        btnSaveItem.isEnabled = true
    }
}
