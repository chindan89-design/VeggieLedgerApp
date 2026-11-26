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

class SupplierMasterActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var rootScroll: ScrollView
    private lateinit var etSupName: EditText
    private lateinit var etSupPhone: EditText
    private lateinit var spSupItem: Spinner
    private lateinit var etSupOpBal: EditText
    private lateinit var etSupArea: EditText
    private lateinit var etSupAddress: EditText
    private lateinit var btnSaveSupplier: Button
    private lateinit var btnClearSupplier: Button
    private lateinit var etSearchSupplier: EditText
    private lateinit var tvSupplierCount: TextView
    private lateinit var llFullSupplierList: LinearLayout

    private lateinit var itemAdapter: ArrayAdapter<String>
    private val itemNames = arrayListOf("Coconut", "Onion", "Garlic")
    private val allSuppliersJson = JSONArray()
    private var editingSupplierId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Supplier Master"

        buildUiProgrammatically()
        loadItemsFromDb()
        loadSuppliersFromDb()
    }

    private fun buildUiProgrammatically() {
        rootScroll = ScrollView(this)
        rootScroll.setBackgroundColor(Color.parseColor("#F5F7FA"))
        rootScroll.setPadding(20, 20, 20, 20)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        rootScroll.addView(root)

        val title = TextView(this)
        title.text = "SUPPLIER REGISTRATION & MASTER"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.parseColor("#1565C0"))
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

        formCard.addView(makeLabel("Supplier Name *"))
        etSupName = EditText(this)
        etSupName.hint = "Enter Supplier Name"
        etSupName.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        etSupName.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etSupName)

        formCard.addView(makeLabel("Phone Number"))
        etSupPhone = EditText(this)
        etSupPhone.hint = "10-digit phone number"
        etSupPhone.inputType = InputType.TYPE_CLASS_PHONE
        etSupPhone.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etSupPhone)

        formCard.addView(makeLabel("Supply Item (Product) *"))
        spSupItem = Spinner(this)
        spSupItem.setBackgroundResource(android.R.drawable.btn_dropdown)
        val spLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (48 * resources.displayMetrics.density).toInt()
        )
        spLp.setMargins(0, 0, 0, 8)
        spSupItem.layoutParams = spLp
        itemAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, itemNames)
        itemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spSupItem.adapter = itemAdapter
        formCard.addView(spSupItem)

        formCard.addView(makeLabel("Opening / Pending Balance (Rs)"))
        etSupOpBal = EditText(this)
        etSupOpBal.hint = "0.00"
        etSupOpBal.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        etSupOpBal.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etSupOpBal)

        formCard.addView(makeLabel("Area / Place"))
        etSupArea = EditText(this)
        etSupArea.hint = "e.g. Pollachi / Kangeyam / Nashik"
        etSupArea.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etSupArea)

        formCard.addView(makeLabel("Full Address"))
        etSupAddress = EditText(this)
        etSupAddress.hint = "Address details..."
        etSupAddress.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etSupAddress)

        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.weightSum = 2f
        btnRow.setPadding(0, 12, 0, 0)

        btnClearSupplier = Button(this)
        btnClearSupplier.text = "RESET / CLEAR"
        btnClearSupplier.setBackgroundColor(Color.parseColor("#D32F2F"))
        btnClearSupplier.setTextColor(Color.WHITE)
        btnClearSupplier.setTypeface(null, Typeface.BOLD)
        val lpClear = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
        lpClear.setMargins(0, 0, 6, 0)
        btnClearSupplier.layoutParams = lpClear
        btnClearSupplier.setOnClickListener { resetForm() }

        btnSaveSupplier = Button(this)
        btnSaveSupplier.text = "SUBMIT SUPPLIER"
        btnSaveSupplier.setBackgroundColor(Color.parseColor("#1565C0"))
        btnSaveSupplier.setTextColor(Color.WHITE)
        btnSaveSupplier.setTypeface(null, Typeface.BOLD)
        val lpSave = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f)
        lpSave.setMargins(6, 0, 0, 0)
        btnSaveSupplier.layoutParams = lpSave
        btnSaveSupplier.setOnClickListener { saveOrUpdateSupplier() }

        btnRow.addView(btnClearSupplier)
        btnRow.addView(btnSaveSupplier)
        formCard.addView(btnRow)
        root.addView(formCard)

        // Search Box
        etSearchSupplier = EditText(this)
        etSearchSupplier.hint = "Search Supplier by Name, Phone, Item or Place..."
        etSearchSupplier.inputType = InputType.TYPE_CLASS_TEXT
        etSearchSupplier.setBackgroundResource(android.R.drawable.edit_text)
        etSearchSupplier.setPadding(24, 20, 24, 20)
        val searchLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        searchLp.setMargins(0, 0, 0, 8)
        etSearchSupplier.layoutParams = searchLp
        etSearchSupplier.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderFilteredSupplierList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        root.addView(etSearchSupplier)

        tvSupplierCount = TextView(this)
        tvSupplierCount.text = "Loading suppliers..."
        tvSupplierCount.textSize = 13f
        tvSupplierCount.setTypeface(null, Typeface.BOLD)
        tvSupplierCount.setTextColor(Color.parseColor("#1565C0"))
        tvSupplierCount.setPadding(4, 0, 4, 8)
        root.addView(tvSupplierCount)

        llFullSupplierList = LinearLayout(this)
        llFullSupplierList.orientation = LinearLayout.VERTICAL
        root.addView(llFullSupplierList)

        setContentView(rootScroll)
    }

    private fun makeLabel(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 12.5f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setTextColor(Color.parseColor("#1565C0"))
        tv.setPadding(2, 6, 0, 2)
        return tv
    }

    private fun loadItemsFromDb() {
        db.collection("items").get().addOnSuccessListener { snap ->
            val fetched = arrayListOf<String>()
            for (doc in snap) {
                val n = (doc.getString("name") ?: doc.getString("itemName") ?: doc.getString("item") ?: "").trim()
                if (n.isNotEmpty() && fetched.none { it.equals(n, ignoreCase = true) }) {
                    fetched.add(n)
                }
            }
            if (fetched.isNotEmpty()) {
                val curr = spSupItem.selectedItem?.toString() ?: ""
                itemNames.clear()
                itemNames.addAll(fetched)
                itemAdapter.notifyDataSetChanged()
                val idx = itemNames.indexOfFirst { it.equals(curr, ignoreCase = true) }
                if (idx >= 0) spSupItem.setSelection(idx)
            }
        }
    }

    private fun saveOrUpdateSupplier() {
        val name = etSupName.text.toString().trim()
        val phone = etSupPhone.text.toString().trim()
        val mappedItem = spSupItem.selectedItem?.toString() ?: "Coconut"
        val opBal = etSupOpBal.text.toString().toDoubleOrNull() ?: 0.0
        val area = etSupArea.text.toString().trim()
        val address = etSupAddress.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter Supplier Name", Toast.LENGTH_SHORT).show()
            return
        }

        btnSaveSupplier.isEnabled = false

        if (editingSupplierId.isNotEmpty()) {
            val updateMap = hashMapOf<String, Any>(
                "name" to name,
                "contact" to phone,
                "mappedItem" to mappedItem,
                "itemName" to mappedItem,
                "opbal" to opBal,
                "openingBalance" to opBal,
                "currentBalance" to opBal,
                "area" to area,
                "address" to address
            )
            db.collection("suppliers").document(editingSupplierId).update(updateMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Supplier Updated!", Toast.LENGTH_SHORT).show()
                    resetForm()
                    loadSuppliersFromDb()
                }
                .addOnFailureListener {
                    btnSaveSupplier.isEnabled = true
                    Toast.makeText(this, "Failed to update supplier", Toast.LENGTH_SHORT).show()
                }
        } else {
            val docId = db.collection("suppliers").document().id
            val dataMap = hashMapOf<String, Any>(
                "id" to docId,
                "name" to name,
                "contact" to phone,
                "mappedItem" to mappedItem,
                "itemName" to mappedItem,
                "opbal" to opBal,
                "openingBalance" to opBal,
                "currentBalance" to opBal,
                "area" to area,
                "address" to address
            )
            db.collection("suppliers").document(docId).set(dataMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Supplier Saved!", Toast.LENGTH_SHORT).show()
                    resetForm()
                    loadSuppliersFromDb()
                }
                .addOnFailureListener {
                    btnSaveSupplier.isEnabled = true
                    Toast.makeText(this, "Failed to save supplier", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadSuppliersFromDb() {
        db.collection("suppliers").get().addOnSuccessListener { snap ->
            while (allSuppliersJson.length() > 0) allSuppliersJson.remove(0)

            val sortedDocs = snap.documents.sortedBy { doc ->
                (doc.getString("name") ?: "").lowercase(Locale.getDefault())
            }

            for (doc in sortedDocs) {
                val obj = JSONObject()
                obj.put("id", doc.id)
                obj.put("name", doc.getString("name") ?: "")
                obj.put("contact", doc.getString("contact") ?: "")
                obj.put("mappedItem", doc.getString("mappedItem") ?: doc.getString("itemName") ?: "Coconut")
                val op = doc.getDouble("opbal") ?: doc.getDouble("openingBalance") ?: 0.0
                obj.put("opbal", op)
                obj.put("currentBalance", doc.getDouble("currentBalance") ?: op)
                obj.put("area", doc.getString("area") ?: "")
                obj.put("address", doc.getString("address") ?: "")
                allSuppliersJson.put(obj)
            }

            renderFilteredSupplierList(etSearchSupplier.text.toString())
        }
    }

    private fun renderFilteredSupplierList(query: String) {
        llFullSupplierList.removeAllViews()
        val q = query.trim().lowercase(Locale.getDefault())
        var shownCount = 0

        for (i in 0 until allSuppliersJson.length()) {
            val obj = allSuppliersJson.getJSONObject(i)
            val docId = obj.optString("id", "")
            val name = obj.optString("name", "")
            val phone = obj.optString("contact", "")
            val itemName = obj.optString("mappedItem", "Coconut")
            val currBal = obj.optDouble("currentBalance", 0.0)
            val area = obj.optString("area", "")

            if (q.isNotEmpty()) {
                val matchName = name.lowercase(Locale.getDefault()).contains(q)
                val matchPhone = phone.lowercase(Locale.getDefault()).contains(q)
                val matchItem = itemName.lowercase(Locale.getDefault()).contains(q)
                val matchArea = area.lowercase(Locale.getDefault()).contains(q)
                if (!matchName && !matchPhone && !matchItem && !matchArea) {
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
            val phPart = if (phone.isNotEmpty()) " | Ph: $phone" else ""
            val areaPart = if (area.isNotEmpty()) " | $area" else ""
            infoTv.text = String.format(
                Locale.getDefault(),
                "%d. %s%s\nItem: %s%s  |  Due: Rs. %.2f",
                shownCount,
                name,
                phPart,
                itemName,
                areaPart,
                currBal
            )
            infoTv.setTextColor(Color.BLACK)
            infoTv.setTypeface(null, Typeface.BOLD)
            infoTv.textSize = 13f
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
                loadSupplierIntoForm(obj)
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
                        .setTitle("Delete Supplier")
                        .setMessage("Are you sure you want to delete $name?")
                        .setPositiveButton("DELETE") { _, _ ->
                            db.collection("suppliers").document(docId).delete()
                                .addOnSuccessListener {
                                    if (editingSupplierId == docId) resetForm()
                                    Toast.makeText(this, "Supplier Deleted!", Toast.LENGTH_SHORT).show()
                                    loadSuppliersFromDb()
                                }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }

            card.addView(infoTv)
            card.addView(editBtn)
            card.addView(delBtn)
            llFullSupplierList.addView(card)
        }

        tvSupplierCount.text = "Showing $shownCount of ${allSuppliersJson.length()} Suppliers"

        if (shownCount == 0) {
            val emptyTv = TextView(this)
            emptyTv.text = "No matching suppliers found."
            emptyTv.setTextColor(Color.GRAY)
            emptyTv.setPadding(16, 16, 16, 16)
            llFullSupplierList.addView(emptyTv)
        }
    }

    private fun loadSupplierIntoForm(obj: JSONObject) {
        editingSupplierId = obj.optString("id", "")
        etSupName.setText(obj.optString("name", ""))
        etSupPhone.setText(obj.optString("contact", ""))
        etSupArea.setText(obj.optString("area", ""))
        etSupAddress.setText(obj.optString("address", ""))

        val opBal = obj.optDouble("opbal", 0.0)
        etSupOpBal.setText(String.format(Locale.getDefault(), "%.2f", opBal))

        val itemName = obj.optString("mappedItem", "Coconut")
        val idx = itemNames.indexOfFirst { it.equals(itemName, ignoreCase = true) }
        if (idx >= 0) spSupItem.setSelection(idx)

        btnSaveSupplier.text = "UPDATE SUPPLIER"
        rootScroll.smoothScrollTo(0, 0)
        Toast.makeText(this, "Editing ${obj.optString("name", "")}", Toast.LENGTH_SHORT).show()
    }

    private fun resetForm() {
        editingSupplierId = ""
        etSupName.setText("")
        etSupPhone.setText("")
        etSupArea.setText("")
        etSupAddress.setText("")
        etSupOpBal.setText("")
        if (itemNames.isNotEmpty()) spSupItem.setSelection(0)
        btnSaveSupplier.text = "SUBMIT SUPPLIER"
        btnSaveSupplier.isEnabled = true
    }
}
