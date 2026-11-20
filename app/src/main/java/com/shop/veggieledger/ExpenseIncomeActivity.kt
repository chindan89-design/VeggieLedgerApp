package com.shop.veggieledger

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityEntryCommonBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpenseIncomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEntryCommonBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEntryCommonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvEntryTitle.text = "EXPENSES & EXTRA INCOME"
        binding.tvCurrentBalInfo.visibility = View.GONE
        binding.etEntryCategoryOrRef.visibility = View.VISIBLE
        binding.etEntryCategoryOrRef.hint = "Category (Coolie, Rent, Tea, Commission)"

        binding.spEntryPartyOrType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("EXPENSE", "INCOME"))
        binding.spEntryMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("CASH", "UPI", "BANK"))

        binding.btnSaveEntry.setOnClickListener {
            val amt = binding.etEntryAmount.text.toString().toDoubleOrNull() ?: 0.0
            val cat = binding.etEntryCategoryOrRef.text.toString().trim()
            if (amt <= 0 || cat.isEmpty()) {
                Toast.makeText(this, "Enter Category and Amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val id = db.collection("expenses_income").document().id
            val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

            val record = ExpenseIncomeEntry(
                id = id,
                date = dateStr,
                entryType = binding.spEntryPartyOrType.selectedItem.toString(),
                category = cat,
                amount = amt,
                notes = binding.etEntryNotes.text.toString().trim()
            )

            db.collection("expenses_income").document(id).set(record).addOnSuccessListener {
                Toast.makeText(this, "Saved Successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
