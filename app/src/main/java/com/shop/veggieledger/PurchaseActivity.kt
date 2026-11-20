package com.shop.veggieledger

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivityPurchaseBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PurchaseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPurchaseBinding
    private val db = FirebaseFirestore.getInstance()

    private val supplierList = mutableListOf<Supplier>()
    private val itemList = mutableListOf<ItemMaster>()
    private val cartItems = mutableListOf<BillItem>()
    private var currentBatchCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurchaseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val payModes = listOf("CREDIT", "CASH", "UPI")
        binding.spPayMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, payModes)

        loadSuppliers()
        loadItems()
        setupListeners()
    }

    private fun loadSuppliers() {
        db.collection("suppliers").get().addOnSuccessListener { snap ->
            supplierList.clear()
            val names = mutableListOf<String>()
            for (doc in snap) {
                val s = doc.toObject(Supplier::class.java)
                supplierList.add(s)
                names.add("${s.name} (${s.shortCode})")
            }
            binding.spSupplier.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        }
    }

    private fun loadItems() {
        db.collection("items").get().addOnSuccessListener { snap ->
            itemList.clear()
            val names = mutableListOf<String>()
            for (doc in snap) {
                val item = doc.toObject(ItemMaster::class.java)
                itemList.add(item)
                names.add(item.itemName)
            }
            binding.spItem.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        }
    }

    private fun generateBatchCode() {
        if (supplierList.isEmpty()) return
        val sup = supplierList[binding.spSupplier.selectedItemPosition]
        val dateStr = SimpleDateFormat("ddMM", Locale.getDefault()).format(Date())
        val rand = (100..999).random()
        currentBatchCode = "${sup.shortCode}-$dateStr-$rand"
        binding.tvGeneratedBatchId.text = "Stock ID: $currentBatchCode"
    }

    private fun setupListeners() {
        binding.spSupplier.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                generateBatchCode()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.spItem.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (itemList.isNotEmpty()) {
                    val selected = itemList[pos]
                    binding.layoutOnionFields.visibility =
                        if (selected.unitType == "BAG_KG" || selected.itemName.equals("Onion", true)) View.VISIBLE else View.GONE
                    generateBatchCode()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // Auto-sum manual bag weights (e.g. "45, 49, 60" -> 154)
        binding.etManualBagWeights.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val raw = s.toString()
                val weights = raw.split(",", " ", "+")
                    .mapNotNull { it.trim().toDoubleOrNull() }
                if (weights.isNotEmpty()) {
                    binding.etNetKgs.setText(weights.sum().toString())
                    if (binding.etBags.text.isEmpty()) {
                        binding.etBags.setText(weights.size.toString())
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnAddItem.setOnClickListener {
            if (supplierList.isEmpty() || itemList.isEmpty()) return@setOnClickListener

            val sup = supplierList[binding.spSupplier.selectedItemPosition]
            val item = itemList[binding.spItem.selectedItemPosition]
            val isOnion = binding.layoutOnionFields.visibility == View.VISIBLE

            val bags = if (isOnion) (binding.etBags.text.toString().toIntOrNull() ?: 0) else 0
            val bagWeightsText = if (isOnion) binding.etManualBagWeights.text.toString().trim() else ""
            val netKgs = binding.etNetKgs.text.toString().toDoubleOrNull() ?: 0.0
            val rate = binding.etRatePerKg.text.toString().toDoubleOrNull() ?: 0.0
            val bagChargeRate = if (isOnion) (binding.etBagRate.text.toString().toDoubleOrNull() ?: 10.0) else 0.0

            if (netKgs <= 0 || rate <= 0) {
                Toast.makeText(this, "Enter valid Kgs and Rate", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalBagCharge = bags * bagChargeRate
            val itemAmount = netKgs * rate
            val lineTotal = itemAmount + totalBagCharge

            val billItem = BillItem(
                itemName = item.itemName,
                batchCode = currentBatchCode,
                supplierName = sup.name,
                bags = bags,
                bagWeightsText = bagWeightsText,
                netKgs = netKgs,
                ratePerKg = rate,
                bagChargeRate = bagChargeRate,
                totalBagCharge = totalBagCharge,
                itemAmount = itemAmount,
                lineTotal = lineTotal
            )

            cartItems.add(billItem)
            updateCartUI()
            clearItemInputs()
            generateBatchCode()
        }

        binding.btnSavePurchase.setOnClickListener {
            savePurchaseBill()
        }
    }

    private fun updateCartUI() {
        val sb = StringBuilder()
        var grandTotal = 0.0
        cartItems.forEachIndexed { idx, it ->
            sb.append("${idx + 1}. ${it.itemName} [${it.batchCode}]\n")
            if (it.bags > 0) {
                sb.append("   Bags: ${it.bags} (${it.bagWeightsText}) | Bag Fee: ₹${it.totalBagCharge}\n")
            }
            sb.append("   ${it.netKgs} Kg × ₹${it.ratePerKg} = ₹${it.lineTotal}\n\n")
            grandTotal += it.lineTotal
        }
        binding.tvAddedItemsList.text = if (sb.isEmpty()) "No items added yet." else sb.toString()
        binding.tvGrandTotal.text = "Grand Total: Rs. %.2f".format(grandTotal)
    }

    private fun clearItemInputs() {
        binding.etBags.text.clear()
        binding.etManualBagWeights.text.clear()
        binding.etNetKgs.text.clear()
        binding.etRatePerKg.text.clear()
    }

    private fun savePurchaseBill() {
        if (cartItems.isEmpty() || supplierList.isEmpty()) {
            Toast.makeText(this, "Add at least 1 item", Toast.LENGTH_SHORT).show()
            return
        }

        val sup = supplierList[binding.spSupplier.selectedItemPosition]
        val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val billId = db.collection("transactions").document().id
        val payMode = binding.spPayMode.selectedItem.toString()

        val totalBags = cartItems.sumOf { it.bags }
        val totalKgs = cartItems.sumOf { it.netKgs }
        val totalBagCharges = cartItems.sumOf { it.totalBagCharge }
        val grandTotal = cartItems.sumOf { it.lineTotal }

        val tx = BillTransaction(
            billId = billId,
            billNo = binding.etBillNo.text.toString().ifEmpty { "PUR-${System.currentTimeMillis() % 10000}" },
            date = dateStr,
            type = "PURCHASE",
            partyId = sup.id,
            partyName = sup.name,
            paymentMode = payMode,
            totalBags = totalBags,
            totalKgs = totalKgs,
            totalBagCharges = totalBagCharges,
            grandTotal = grandTotal,
            items = cartItems
        )

        val batch = db.batch()
        batch.set(db.collection("transactions").document(billId), tx)

        // Create individual StockBatch documents for each item so Sales can pick Supplier ID
        for (item in cartItems) {
            val stockBatch = StockBatch(
                batchCode = item.batchCode,
                date = dateStr,
                supplierId = sup.id,
                supplierName = sup.name,
                itemName = item.itemName,
                purchasedBags = item.bags,
                availableBags = item.bags,
                purchasedKgs = item.netKgs,
                availableKgs = item.netKgs,
                purchaseRate = item.ratePerKg,
                status = "ACTIVE"
            )
            batch.set(db.collection("stock_batches").document(item.batchCode), stockBatch)
        }

        // Update Supplier Balance if Credit
        if (payMode == "CREDIT") {
            batch.update(db.collection("suppliers").document(sup.id), "currentBalance", FieldValue.increment(grandTotal))
        }

        batch.commit().addOnSuccessListener {
            Toast.makeText(this, "Purchase Saved & Stock IDs Created!", Toast.LENGTH_LONG).show()
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
