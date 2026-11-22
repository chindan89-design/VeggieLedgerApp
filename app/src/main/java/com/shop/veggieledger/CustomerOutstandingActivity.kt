package com.shop.veggieledger

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityReportSheetBinding

class CustomerOutstandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportSheetBinding
    private val db = FirebaseFirestore.getInstance()
    private val allCustomers = mutableListOf<Customer>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportSheetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvReportTitle.text = "CUSTOMER OUTSTANDING SHEET"

        val groups = listOf("ALL GROUPS", "A", "B", "C", "D")
        binding.spReportGroupFilter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, groups)
        binding.spReportGroupFilter.visibility = View.VISIBLE
        binding.tvReportTotal.visibility = View.VISIBLE
        binding.lvReportSheet.visibility = View.VISIBLE
        binding.tvReportSummary.visibility = View.GONE
        binding.btnShareReport.visibility = View.GONE

        binding.spReportGroupFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                filterByGroup()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        

        loadCustomers()
    }

    private fun loadCustomers() {
        db.collection("customers").get().addOnSuccessListener { snap ->
            allCustomers.clear()
            for (doc in snap) {
                allCustomers.add(doc.toObject(Customer::class.java))
            }
            filterByGroup()
        }
    }

    private fun filterByGroup() {
        val selectedGroup = binding.spReportGroupFilter.selectedItem.toString()
        val filtered = if (selectedGroup == "ALL GROUPS") {
            allCustomers
        } else {
            allCustomers.filter { it.groupName == selectedGroup }
        }

        val totalDue = filtered.sumOf { it.currentBalance }
        binding.tvReportTotal.text = "Total Pending ($selectedGroup): Rs. %.2f".format(totalDue)

        val displayList = filtered.mapIndexed { idx, c ->
            "${idx + 1}. ${c.name} [${c.groupName}]\n    Area: ${c.area.ifEmpty { "-" }} | Pending: Rs. %.2f".format(c.currentBalance)
        }
        binding.lvReportSheet.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
    }
}
