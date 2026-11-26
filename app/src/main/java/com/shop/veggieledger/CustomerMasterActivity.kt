package com.shop.veggieledger

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityCustomerMasterBinding
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class CustomerMasterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerMasterBinding
    private val db = FirebaseFirestore.getInstance()

    private lateinit var spCustItem: Spinner
    private lateinit var itemAdapter: ArrayAdapter<String>
    private val itemNames = arrayListOf("Coconut", "Onion", "Garlic")

    private lateinit var etSearchCustomer: EditText
    private lateinit var tvCustomerCount: TextView
    private lateinit var llFullCustomerList: LinearLayout

    private val groups = arrayListOf("Group A", "Group B", "Group C", "Group D")
    private val allCustomersJson = JSONArray()

    private var editingCustomerId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerMasterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Customer Master"

        injectItemDropdownField()
        setupFullListWithSearchAndEdit()

        val groupAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, groups)
        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spCustGroup.adapter = groupAdapter

        binding.btnSaveCustomer.setOnClickListener {
            saveOrUpdateCustomer()
        }

        binding.btnCancel.setOnClickListener {
            resetForm()
        }

        loadItemsFromDb()
        loadCustomersFromDb()
    }

    private fun injectItemDropdownField() {
        val parent = binding.etCustArea.parent as? ViewGroup ?: return
        val insertIndex = parent.indexOfChild(binding.etCustArea) + 1

        val itemBox = LinearLayout(this)
        itemBox.orientation = LinearLayout.VERTICAL
        val boxLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        boxLp.setMargins(0, 0, 0, 12)
        itemBox.layoutParams = boxLp

        val lblItem = TextView(this)
        lblItem.text = "Item Name (Product) *"
        lblItem.textSize = 13f
        lblItem.setTypeface(null, Typeface.BOLD)
        lblItem.setTextColor(Color.parseColor("#1B5E20"))

        spCustItem = Spinner(this)
        spCustItem.setBackgroundResource(android.R.drawable.btn_dropdown)
        val spLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (48 * resources.displayMetrics.density).toInt()
        )
        spCustItem.layoutParams = spLp

        itemAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, itemNames)
        itemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spCustItem.adapter = itemAdapter

        itemBox.addView(lblItem)
        itemBox.addView(spCustItem)
        parent.addView(itemBox, insertIndex)
    }

    /**
     * Replaces the fixed-height ListView with a Search Box + Full Expandable Customer List
     * featuring dedicated EDIT and DEL buttons for every customer.
     */
    private fun setupFullListWithSearchAndEdit() {
        val parent = binding.lvCustomers.parent as? ViewGroup ?: return
        val listIndex = parent.indexOfChild(binding.lvCustomers)
        binding.lvCustomers.visibility = View.GONE

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        etSearchCustomer = EditText(this)
        etSearchCustomer.hint = "Search by Name, Phone, Group or Item..."
        etSearchCustomer.inputType = InputType.TYPE_CLASS_TEXT
        etSearchCustomer.setBackgroundResource(android.R.drawable.edit_text)
        etSearchCustomer.setPadding(24, 20, 24, 20)
        val searchLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        searchLp.setMargins(0, 4, 0, 8)
        etSearchCustomer.layoutParams = searchLp

        etSearchCustomer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderFilteredCustomerList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        container.addView(etSearchCustomer)

        tvCustomerCount = TextView(this)
        tvCustomerCount.text = "Loading customers..."
        tvCustomerCount.textSize = 12.5f
        tvCustomerCount.setTypeface(null, Typeface.BOLD)
        tvCustomerCount.setTextColor(Color.parseColor("#1565C0"))
        tvCustomerCount.setPadding(4, 0, 4, 8)
        container.addView(tvCustomerCount)

        llFullCustomerList = LinearLayout(this)
        llFullCustomerList.orientation = LinearLayout.VERTICAL
        llFullCustomerList.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        container.addView(llFullCustomerList)

        parent.addView(container, listIndex)
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
                val currentSelection = spCustItem.selectedItem?.toString() ?: ""
                itemNames.clear()
                itemNames.addAll(fetched)
                itemAdapter.notifyDataSetChanged()
                val idx = itemNames.indexOfFirst { it.equals(currentSelection, ignoreCase = true) }
                if (idx >= 0) spCustItem.setSelection(idx)
            }
        }
    }

    private fun saveOrUpdateCustomer() {
        val name = binding.etCustName.text.toString().trim()
        val phone = binding.etCustPhone.text.toString().trim()
        val group = binding.spCustGroup.selectedItem?.toString() ?: "Group A"
        val itemName = spCustItem.selectedItem?.toString() ?: "Coconut"
        val opBal = binding.etCustOpBal.text.toString().toDoubleOrNull() ?: 0.0
        val area = binding.etCustArea.text.toString().trim()
        val address = binding.etCustAddress.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter Customer Name", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSaveCustomer.isEnabled = false

        if (editingCustomerId.isNotEmpty()) {
            val updateMap = hashMapOf<String, Any>(
                "name" to name,
                "contact" to phone,
                "group" to group,
                "itemName" to itemName,
                "mappedItem" to itemName,
                "opbal" to opBal,
                "openingBalance" to opBal,
                "currentBalance" to opBal,
                "area" to area,
                "address" to address
            )
            db.collection("customers").document(editingCustomerId).update(updateMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Customer Updated!", Toast.LENGTH_SHORT).show()
                    resetForm()
                    loadCustomersFromDb()
                }
                .addOnFailureListener {
                    binding.btnSaveCustomer.isEnabled = true
                    Toast.makeText(this, "Failed to update customer", Toast.LENGTH_SHORT).show()
                }
        } else {
            val docId = db.collection("customers").document().id
            val dataMap = hashMapOf<String, Any>(
                "id" to docId,
                "name" to name,
                "contact" to phone,
                "group" to group,
                "itemName" to itemName,
                "mappedItem" to itemName,
                "opbal" to opBal,
                "openingBalance" to opBal,
                "currentBalance" to opBal,
                "area" to area,
                "address" to address
            )
            db.collection("customers").document(docId).set(dataMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Customer Saved!", Toast.LENGTH_SHORT).show()
                    resetForm()
                    loadCustomersFromDb()
                }
                .addOnFailureListener {
                    binding.btnSaveCustomer.isEnabled = true
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
                obj.put("group", doc.getString("group") ?: "Group A")
                obj.put("itemName", doc.getString("itemName") ?: doc.getString("mappedItem") ?: "Coconut")
                val op = doc.getDouble("opbal") ?: doc.getDouble("openingBalance") ?: 0.0
                obj.put("opbal", op)
                obj.put("currentBalance", doc.getDouble("currentBalance") ?: op)
                obj.put("area", doc.getString("area") ?: "")
                obj.put("address", doc.getString("address") ?: "")
                allCustomersJson.put(obj)
            }

            renderFilteredCustomerList(etSearchCustomer.text.toString())
        }
    }

    private fun renderFilteredCustomerList(query: String) {
        llFullCustomerList.removeAllViews()
        val q = query.trim().lowercase(Locale.getDefault())
        var shownCount = 0

        for (i in 0 until allCustomersJson.length()) {
            val obj = allCustomersJson.getJSONObject(i)
            val docId = obj.optString("id", "")
            val name = obj.optString("name", "")
            val phone = obj.optString("contact", "")
            val group = obj.optString("group", "Group A")
            val itemName = obj.optString("itemName", "Coconut")
            val currBal = obj.optDouble("currentBalance", 0.0)
            val area = obj.optString("area", "")

            if (q.isNotEmpty()) {
                val matchName = name.lowercase(Locale.getDefault()).contains(q)
                val matchPhone = phone.lowercase(Locale.getDefault()).contains(q)
                val matchGroup = group.lowercase(Locale.getDefault()).contains(q)
                val matchItem = itemName.lowercase(Locale.getDefault()).contains(q)
                val matchArea = area.lowercase(Locale.getDefault()).contains(q)
                if (!matchName && !matchPhone && !matchGroup && !matchItem && !matchArea) {
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
            infoTv.text = String.format(
                Locale.getDefault(),
                "%d. %s%s\nItem: %s  |  %s  |  Bal: Rs. %.2f",
                shownCount,
                name,
                phPart,
                itemName,
                group,
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
                loadCustomerIntoForm(obj)
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
                        .setTitle("Delete Customer")
                        .setMessage("Are you sure you want to delete $name?")
                        .setPositiveButton("DELETE") { _, _ ->
                            db.collection("customers").document(docId).delete()
                                .addOnSuccessListener {
                                    if (editingCustomerId == docId) resetForm()
                                    Toast.makeText(this, "Customer Deleted!", Toast.LENGTH_SHORT).show()
                                    loadCustomersFromDb()
                                }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }

            card.addView(infoTv)
            card.addView(editBtn)
            card.addView(delBtn)
            llFullCustomerList.addView(card)
        }

        tvCustomerCount.text = "Showing $shownCount of ${allCustomersJson.length()} Customers"

        if (shownCount == 0) {
            val emptyTv = TextView(this)
            emptyTv.text = "No matching customers found."
            emptyTv.setTextColor(Color.GRAY)
            emptyTv.setPadding(16, 16, 16, 16)
            llFullCustomerList.addView(emptyTv)
        }
    }

    private fun loadCustomerIntoForm(obj: JSONObject) {
        editingCustomerId = obj.optString("id", "")
        binding.etCustName.setText(obj.optString("name", ""))
        binding.etCustPhone.setText(obj.optString("contact", ""))
        binding.etCustArea.setText(obj.optString("area", ""))
        binding.etCustAddress.setText(obj.optString("address", ""))

        val opBal = obj.optDouble("opbal", 0.0)
        binding.etCustOpBal.setText(String.format(Locale.getDefault(), "%.2f", opBal))

        val grp = obj.optString("group", "Group A")
        val idx = groups.indexOf(grp)
        if (idx >= 0) binding.spCustGroup.setSelection(idx)

        val iName = obj.optString("itemName", "Coconut")
        val itemIdx = itemNames.indexOfFirst { it.equals(iName, ignoreCase = true) }
        if (itemIdx >= 0) spCustItem.setSelection(itemIdx)

        binding.btnSaveCustomer.text = "UPDATE DETAILS"

        // Scroll smoothly to top form so user can edit immediately
        val rootScroll = binding.root as? ScrollView
        rootScroll?.smoothScrollTo(0, 0)

        Toast.makeText(this, "Editing ${obj.optString("name", "")}", Toast.LENGTH_SHORT).show()
    }

    private fun resetForm() {
        editingCustomerId = ""
        binding.etCustName.setText("")
        binding.etCustPhone.setText("")
        binding.etCustArea.setText("")
        binding.etCustAddress.setText("")
        binding.etCustOpBal.setText("")
        binding.spCustGroup.setSelection(0)
        if (itemNames.isNotEmpty()) spCustItem.setSelection(0)
        binding.btnSaveCustomer.text = "SUBMIT DETAILS"
        binding.btnSaveCustomer.isEnabled = true
    }
}
