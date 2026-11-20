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

class PurchasePaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEntryCommonBinding
    private val db = FirebaseFirestore.getInstance()
    private val suppliers = mutableListOf<Supplier>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEntryCommonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvEntryTitle.text = "PURCHASE PAYMENT (SUPPLIER)"
        binding.spEntryMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("CASH", "UPI", "BANK"))

        db.collection("suppliers").get().addOnSuccessListener { snap ->
            suppliers.clear()
            val names = mutableListOf<String>()
            for (doc in snap) {
                val s = doc.toObject(Supplier::class.java)
                suppliers.add(s)
                names.add("${s.name} (${s.shortCode})")
            }
            binding.spEntryPartyOrType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        }

        binding.spEntryPartyOrType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (suppliers.isNotEmpty()) {
                    binding.tvCurrentBalInfo.text = "Supplier Payable Balance: Rs. %.2f".format(suppliers[pos].currentBalance)
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.btnSaveEntry.setOnClickListener {
            if (suppliers.isEmpty()) return@setOnClickListener
            val amt = binding.etEntryAmount.text.toString().toDoubleOrNull() ?: 0.0
            if (amt <= 0) {
                Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sup = suppliers[binding.spEntryPartyOrType.selectedItemPosition]
            val id = db.collection("payments").document().id
            val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

            val entry = PaymentEntry(
                entryId = id,
                date = dateStr,
                type = "PURCHASE_PAYMENT",
                partyId = sup.id,
                partyName = sup.name,
                amount = amt,
                mode = binding.spEntryMode.selectedItem.toString(),
                notes = binding.etEntryNotes.text.toString().trim()
            )

            val batch = db.batch()
            batch.set(db.collection("payments").document(id), entry)
            batch.update(db.collection("suppliers").document(sup.id), "currentBalance", FieldValue.increment(-amt))

            batch.commit().addOnSuccessListener {
                Toast.makeText(this, "Supplier Payment Saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
