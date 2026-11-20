package com.shop.veggieledger

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityItemMasterBinding

class ItemMasterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityItemMasterBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityItemMasterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val unitTypes = listOf("KG", "BAG_KG")
        binding.spUnitType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, unitTypes)

        binding.btnLoadDefaults.setOnClickListener {
            val defaults = listOf(
                ItemMaster("item_onion", "Onion", "BAG_KG", 10.0),
                ItemMaster("item_coconut", "Coconut", "KG", 0.0),
                ItemMaster("item_garlic", "Garlic", "KG", 0.0)
            )
            defaults.forEach { item ->
                db.collection("items").document(item.id).set(item)
            }
            Toast.makeText(this, "Onion, Coconut & Garlic Added!", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveItem.setOnClickListener {
            val name = binding.etItemName.text.toString().trim()
            if (name.isEmpty()) return@setOnClickListener

            val id = db.collection("items").document().id
            val unit = binding.spUnitType.selectedItem.toString()
            val bagRate = binding.etBagCharge.text.toString().toDoubleOrNull() ?: 0.0

            val item = ItemMaster(id, name, unit, bagRate)
            db.collection("items").document(id).set(item).addOnSuccessListener {
                Toast.makeText(this, "Item Saved!", Toast.LENGTH_SHORT).show()
                binding.etItemName.text.clear()
            }
        }
    }
}
