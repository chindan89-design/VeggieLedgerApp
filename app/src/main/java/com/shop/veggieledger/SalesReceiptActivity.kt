package com.shop.veggieledger

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityEntryCommonBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SalesReceiptActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEntryCommonBinding
    private val db = FirebaseFirestore.getInstance()
    private val customers = mutableListOf<Customer>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEntryCommonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvEntryTitle.text = "SALES RECEIPT (COLLECTION)"
        binding.spEntryMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("CASH", "UPI", "BANK"))

        db.collection("customers").get().addOnSuccessListener { snap ->
            customers.clear()
            val names = mutableListOf<String>()
            for (doc in snap) {
                val c = doc.toObject(Customer::class.java)
                customers.add(c)
                names.add(c.name)
            }
            binding.spEntryPartyOrType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        }

        binding.spEntryPartyOrType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (customers.isNotEmpty()) {
                    binding.tvCurrentBalInfo.text = "Customer Pending Balance: Rs. %.2f".format(customers[pos].currentBalance)
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.btnSaveEntry.setOnClickListener {
            if (customers.isEmpty()) return@setOnClickListener
            val amt = binding.etEntryAmount.text.toString().toDoubleOrNull() ?: 0.0
            if (amt <= 0) {
                Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val cust = customers[binding.spEntryPartyOrType.selectedItemPosition]
            val id = db.collection("payments").document().id
            val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

            val entry = PaymentEntry(
                entryId = id,
                date = dateStr,
                type = "SALES_RECEIPT",
                partyId = cust.id,
                partyName = cust.name,
                amount = amt,
                mode = binding.spEntryMode.selectedItem.toString(),
                notes = binding.etEntryNotes.text.toString().trim()
            )

            val batch = db.batch()
            batch.set(db.collection("payments").document(id), entry)
            batch.update(db.collection("customers").document(cust.id), "currentBalance", FieldValue.increment(-amt))

            batch.commit().addOnSuccessListener {
                Toast.makeText(this, "Receipt Saved & Balance Updated!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
