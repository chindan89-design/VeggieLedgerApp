package com.shop.veggieledger

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
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

    private val groups = arrayListOf("Group A", "Group B", "Group C", "Group D")
    private val customerDisplayList = arrayListOf<String>()
    private val customerDocsJson = JSONArray()
    private lateinit var listAdapter: ArrayAdapter<String>

    private var editingCustomerId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerMasterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Customer Master"

        injectItemDropdownField()

        val groupAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, groups)
        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spCustGroup.adapter = groupAdapter

        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, customerDisplayList)
        binding.lvCustomers.adapter = listAdapter

        binding.btnSaveCustomer.setOnClickListener {
            saveOrUpdateCustomer()
        }

        binding.btnCancel.setOnClickListener {
            resetForm()
        }

        binding.lvCustomers.setOnItemClickListener { _, _, position, _ ->
            if (position >= 0 && position < customerDocsJson.length()) {
                loadCustomerIntoForm(customerDocsJson.getJSONObject(position))
            }
        }

        binding.lvCustomers.setOnItemLongClickListener { _, _, position, _ ->
            if (position >= 0 && position < customerDocsJson.length()) {
                val obj = customerDocsJson.getJSONObject(position)
                val docId = obj.optString("id", "")
                val cName = obj.optString("name", "")
                if (docId.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Delete Customer")
                        .setMessage("Are you sure you want to delete $cName?")
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
            true
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
            customerDisplayList.clear()
            while (customerDocsJson.length() > 0) customerDocsJson.remove(0)

            val sortedDocs = snap.documents.sortedBy { doc ->
                (doc.getString("name") ?: "").lowercase(Locale.getDefault())
            }

            for (doc in sortedDocs) {
                val name = doc.getString("name") ?: ""
                val phone = doc.getString("contact") ?: ""
                val group = doc.getString("group") ?: "Group A"
                val itemName = doc.getString("itemName") ?: doc.getString("mappedItem") ?: "Coconut"
                val opBal = doc.getDouble("opbal") ?: doc.getDouble("openingBalance") ?: 0.0
                val currBal = doc.getDouble("currentBalance") ?: opBal
                val area = doc.getString("area") ?: ""
                val address = doc.getString("address") ?: ""

                val obj = JSONObject()
                obj.put("id", doc.id)
                obj.put("name", name)
                obj.put("contact", phone)
                obj.put("group", group)
                obj.put("itemName", itemName)
                obj.put("opbal", opBal)
                obj.put("currentBalance", currBal)
                obj.put("area", area)
                obj.put("address", address)
                customerDocsJson.put(obj)

                val phPart = if (phone.isNotEmpty()) " | $phone" else ""
                customerDisplayList.add(
                    String.format(Locale.getDefault(), "%s%s [%s - %s]\nBal: Rs. %.2f", name, phPart, itemName, group, currBal)
                )
            }
            listAdapter.notifyDataSetChanged()
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
