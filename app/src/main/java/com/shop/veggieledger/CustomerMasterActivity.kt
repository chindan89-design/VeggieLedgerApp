package com.shop.veggieledger

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

class CustomerMasterActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var etName: EditText
    private lateinit var etContact: EditText
    private lateinit var etArea: EditText
    private lateinit var etOpBal: EditText
    private lateinit var spGroup: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnClear: Button
    private lateinit var etSearch: EditText
    private lateinit var llCustomerList: LinearLayout

    private var editingCustomerId: String = ""
    private val allCustomersJson = JSONArray()
    private val groups = arrayListOf("Group A", "Group B", "Group C", "Group D")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Customer Master"

        buildUi()
        loadCustomersFromDb()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#F5F7FA"))
        scroll.setPadding(24, 24, 24, 24)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        scroll.addView(root)

        val title = TextView(this)
        title.text = "CUSTOMER MASTER"
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

        etName = EditText(this)
        etName.hint = "Customer Name *"
        etName.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etName)

        etContact = EditText(this)
        etContact.hint = "Phone / Contact No"
        etContact.inputType = InputType.TYPE_CLASS_PHONE
        etContact.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etContact)

        etArea = EditText(this)
        etArea.hint = "Area / Market Block"
        etArea.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etArea)

        spGroup = Spinner(this)
        val grpAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, groups)
        grpAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spGroup.adapter = grpAdapter
        spGroup.setBackgroundResource(android.R.drawable.btn_dropdown)
        formCard.addView(spGroup)

        etOpBal = EditText(this)
        etOpBal.hint = "Opening / Pending Balance (Rs)"
        etOpBal.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        etOpBal.setBackgroundResource(android.R.drawable.edit_text)
        formCard.addView(etOpBal)

        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL
        btnRow.weightSum = 2f
        btnRow.setPadding(0, 8, 0, 0)

        btnSave = Button(this)
        btnSave.text = "SAVE CUSTOMER"
        btnSave.setBackgroundColor(Color.parseColor("#1B5E20"))
        btnSave.setTextColor(Color.WHITE)
        btnSave.setTypeface(null, Typeface.BOLD)
        val lpSave = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)
        lpSave.setMargins(0, 0, 6, 0)
        btnSave.layoutParams = lpSave
        btnSave.setOnClickListener { saveOrUpdateCustomer() }

        btnClear = Button(this)
        btnClear.text = "CLEAR"
        btnClear.setBackgroundColor(Color.parseColor("#78909C"))
        btnClear.setTextColor(Color.WHITE)
        btnClear.setTypeface(null, Typeface.BOLD)
        val lpClear = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
        lpClear.setMargins(6, 0, 0, 0)
        btnClear.layoutParams = lpClear
        btnClear.setOnClickListener { resetForm() }

        btnRow.addView(btnSave)
        btnRow.addView(btnClear)
        formCard.addView(btnRow)

        root.addView(formCard)

        // Search Box
        etSearch = EditText(this)
        etSearch.hint = "Search saved customers..."
        etSearch.setBackgroundResource(android.R.drawable.edit_text)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderCustomerList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        root.addView(etSearch)

        llCustomerList = LinearLayout(this)
        llCustomerList.orientation = LinearLayout.VERTICAL
        root.addView(llCustomerList)

        setContentView(scroll)
    }

    private fun saveOrUpdateCustomer() {
        val name = etName.text.toString().trim()
        val contact = etContact.text.toString().trim()
        val area = etArea.text.toString().trim()
        val grp = spGroup.selectedItem?.toString() ?: "Group A"
        val opBal = etOpBal.text.toString().toDoubleOrNull() ?: 0.0

        if (name.isEmpty()) {
            Toast.makeText(this, "Enter Customer Name", Toast.LENGTH_SHORT).show()
            return
        }

        btnSave.isEnabled = false
        if (editingCustomerId.isNotEmpty()) {
            val upd = hashMapOf<String, Any>(
                "name" to name,
                "contact" to contact,
                "area" to area,
                "group" to grp,
                "opbal" to opBal,
                "openingBalance" to opBal,
                "currentBalance" to opBal
            )
            db.collection("customers").document(editingCustomerId).update(upd)
                .addOnSuccessListener {
                    Toast.makeText(this, "Customer Updated!", Toast.LENGTH_SHORT).show()
                    resetForm()
                    loadCustomersFromDb()
                }
                .addOnFailureListener {
                    btnSave.isEnabled = true
                    Toast.makeText(this, "Failed to update customer", Toast.LENGTH_SHORT).show()
                }
        } else {
            val docId = db.collection("customers").document().id
            val data = hashMapOf<String, Any>(
                "id" to docId,
                "name" to name,
                "contact" to contact,
                "area" to area,
                "group" to grp,
                "opbal" to opBal,
                "openingBalance" to opBal,
                "currentBalance" to opBal
            )
            db.collection("customers").document(docId).set(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "Customer Saved!", Toast.LENGTH_SHORT).show()
                    resetForm()
                    loadCustomersFromDb()
                }
                .addOnFailureListener {
                    btnSave.isEnabled = true
                    Toast.makeText(this, "Failed to save customer", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loadCustomersFromDb() {
        db.collection("customers").get().addOnSuccessListener { snap ->
            while (allCustomersJson.length() > 0) allCustomersJson.remove(0)
            val sortedDocs = snap.documents.sortedBy { doc ->
                (doc.getString("name") ?: "").lowercase(Locale.getDefault())
            }
            for (doc in sortedDocs) {
                val obj = JSONObject()
                obj.put("id", doc.id)
                obj.put("name", doc.getString("name") ?: "")
                obj.put("contact", doc.getString("contact") ?: "")
                obj.put("area", doc.getString("area") ?: "")
                obj.put("group", doc.getString("group") ?: "Group A")
                obj.put("opbal", doc.getDouble("opbal") ?: doc.getDouble("openingBalance") ?: 0.0)
                obj.put("bal", doc.getDouble("currentBalance") ?: 0.0)
                allCustomersJson.put(obj)
            }
            renderCustomerList(etSearch.text.toString())
        }
    }

    private fun renderCustomerList(query: String) {
        llCustomerList.removeAllViews()
        val q = query.trim().lowercase(Locale.getDefault())

        for (i in 0 until allCustomersJson.length()) {
            val obj = allCustomersJson.getJSONObject(i)
            val id = obj.getString("id")
            val name = obj.getString("name")
            val contact = obj.getString("contact")
            val area = obj.getString("area")
            val grp = obj.getString("group")
            val op = obj.getDouble("opbal")
            val bal = obj.getDouble("bal")

            if (q.isNotEmpty() && !name.lowercase(Locale.getDefault()).contains(q) && !contact.contains(q)) {
                continue
            }

            val card = LinearLayout(this)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.setBackgroundColor(Color.WHITE)
            card.setPadding(18, 14, 18, 14)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 8)
            card.layoutParams = lp

            val phText = if (contact.isNotEmpty()) " | Ph: $contact" else ""
            val info = TextView(this)
            info.text = String.format(Locale.getDefault(), "%s%s\nBal: Rs. %.2f", name, phText, bal)
            info.setTextColor(Color.BLACK)
            info.setTypeface(null, Typeface.BOLD)
            info.textSize = 13f
            info.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val editBtn = TextView(this)
            editBtn.text = "EDIT"
            editBtn.setTextColor(Color.WHITE)
            editBtn.setBackgroundColor(Color.parseColor("#1565C0"))
            editBtn.setTypeface(null, Typeface.BOLD)
            editBtn.setPadding(22, 12, 22, 12)
            editBtn.setOnClickListener {
                editingCustomerId = id
                etName.setText(name)
                etContact.setText(contact)
                etArea.setText(area)
                etOpBal.setText(String.format(Locale.getDefault(), "%.2f", op))
                val gIdx = groups.indexOf(grp)
                if (gIdx >= 0) spGroup.setSelection(gIdx)
                btnSave.text = "UPDATE CUSTOMER"
            }

            card.addView(info)
            card.addView(editBtn)
            llCustomerList.addView(card)
        }
    }

    private fun resetForm() {
        editingCustomerId = ""
        etName.setText("")
        etContact.setText("")
        etArea.setText("")
        etOpBal.setText("")
        spGroup.setSelection(0)
        btnSave.text = "SAVE CUSTOMER"
        btnSave.isEnabled = true
    }
}
