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
import com.shop.veggieledger.databinding.ActivitySalesBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SalesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalesBinding
    private val db = FirebaseFirestore.getInstance()

    private val customerList = mutableListOf<Customer>()
    private val itemList = mutableListOf<ItemMaster>()
    private val activeBatches = mutableListOf<StockBatch>()
    private val salesCart = mutableListOf<BillItem>()

    private var selectedBatch: StockBatch? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val payModes = listOf("CREDIT", "CASH", "UPI")
        binding.spSalesPayMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, payModes)

        loadCustomers()
        loadItems()
        setupListeners()
    }

    private fun loadCustomers() {
        db.collection("customers").get().addOnSuccessListener { snap ->
            customerList.clear()
            val names = mutableListOf<String>()
            for (doc in snap) {
                val c = doc.toObject(Customer::class.java)
                customerList.add(c)
                names.add("${c.name} (Bal: ₹${c.currentBalance.toInt()})")
            }
            binding.spCustomer.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
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
            binding.spSalesItem.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        }
    }

    private fun loadBatchesForItem(itemName: String) {
        db.collection("stock_batches")
            .whereEqualTo("itemName", itemName)
            .whereEqualTo("status", "ACTIVE")
            .get()
            .addOnSuccessListener { snap ->
                activeBatches.clear()
                val batchLabels = mutableListOf<String>()
                for (doc in snap) {
                    val b = doc.toObject(StockBatch::class.java)
                    if (b.availableKgs > 0 || b.availableBags > 0) {
                        activeBatches.add(b)
                        batchLabels.add("${b.batchCode} (${b.supplierName})")
                    }
                }

                if (activeBatches.isEmpty()) {
                    selectedBatch = null
                    binding.spSupplierBatch.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf("No Active Stock Available")
                    )
                    binding.tvLiveStockBanner.text = "Stock Alert: 0 Stock available for $itemName!"
                } else {
                    binding.spSupplierBatch.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        batchLabels
                    )
                }
            }
    }

    private fun updateLiveStockBanner(batch: StockBatch) {
        selectedBatch = batch
        val isOnion = batch.itemName.equals("Onion", ignoreCase = true)
        binding.tvLiveStockBanner.text = if (isOnion) {
            "LIVE STOCK [${batch.batchCode}]:  ${batch.availableBags} Bags  |  ${batch.availableKgs} Kgs\n(Purchase Cost: ₹${batch.purchaseRate}/kg)"
        } else {
            "LIVE STOCK [${batch.batchCode}]:  ${batch.availableKgs} Kgs Available\n(Purchase Cost: ₹${batch.purchaseRate}/kg)"
        }
    }

    private fun setupListeners() {
        // When Item changes -> toggle Onion fields & load Supplier Stock Batches
        binding.spSalesItem.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (itemList.isNotEmpty()) {
                    val item = itemList[pos]
                    val isOnion = item.unitType == "BAG_KG" || item.itemName.equals("Onion", true)
                    binding.layoutSalesOnion.visibility = if (isOnion) View.VISIBLE else View.GONE
                    loadBatchesForItem(item.itemName)
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // When Supplier Stock ID changes -> automatically display Bags & Kgs stock
        binding.spSupplierBatch.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (activeBatches.isNotEmpty() && pos < activeBatches.size) {
                    updateLiveStockBanner(activeBatches[pos])
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // Manual Bag Weights auto-sum (e.g., "45, 49, 60" -> 154 Kgs & 3 Bags)
        binding.etSalesManualWeights.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val weights = s.toString().split(",", " ", "+")
                    .mapNotNull { it.trim().toDoubleOrNull() }
                if (weights.isNotEmpty()) {
                    binding.etSalesNetKgs.setText(weights.sum().toString())
                    if (binding.etSalesBags.text.isEmpty()) {
                        binding.etSalesBags.setText(weights.size.toString())
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Add Item to Multi-Item Sales Cart
        binding.btnAddSalesItem.setOnClickListener {
            val batch = selectedBatch
            if (batch == null) {
                Toast.makeText(this, "Select a valid Supplier Stock ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isOnion = binding.layoutSalesOnion.visibility == View.VISIBLE
            val bags = if (isOnion) (binding.etSalesBags.text.toString().toIntOrNull() ?: 0) else 0
            val bagWeightsText = if (isOnion) binding.etSalesManualWeights.text.toString().trim() else ""
            val netKgs = binding.etSalesNetKgs.text.toString().toDoubleOrNull() ?: 0.0
            val rate = binding.etSalesRatePerKg.text.toString().toDoubleOrNull() ?: 0.0
            val bagRate = if (isOnion) (binding.etSalesBagRate.text.toString().toDoubleOrNull() ?: 10.0) else 0.0

            if (netKgs <= 0 || rate <= 0) {
                Toast.makeText(this, "Enter valid Net Kgs and Sales Rate", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Stock Over-sell Safety Warning
            if (isOnion && bags > batch.availableBags) {
                Toast.makeText(this, "Warning: Only ${batch.availableBags} Bags available in ${batch.batchCode}!", Toast.LENGTH_LONG).show()
            } else if (netKgs > batch.availableKgs) {
                Toast.makeText(this, "Warning: Only ${batch.availableKgs} Kgs available in ${batch.batchCode}!", Toast.LENGTH_LONG).show()
            }

            val totalBagCharge = bags * bagRate
            val itemAmount = netKgs * rate
            val lineTotal = itemAmount + totalBagCharge

            val lineItem = BillItem(
                itemName = batch.itemName,
                batchCode = batch.batchCode,
                supplierName = batch.supplierName,
                bags = bags,
                bagWeightsText = bagWeightsText,
                netKgs = netKgs,
                ratePerKg = rate,
                bagChargeRate = bagRate,
                totalBagCharge = totalBagCharge,
                itemAmount = itemAmount,
                lineTotal = lineTotal
            )

            salesCart.add(lineItem)
            updateSalesCartUI()
            clearSalesItemInputs()
        }

        binding.btnSaveSalesBill.setOnClickListener {
            saveSalesBill()
        }
    }

    private fun updateSalesCartUI() {
        val sb = StringBuilder()
        var grandTotal = 0.0
        salesCart.forEachIndexed { idx, it ->
            sb.append("${idx + 1}. ${it.itemName} [ID: ${it.batchCode} - ${it.supplierName}]\n")
            if (it.bags > 0) {
                sb.append("   Bags: ${it.bags} (${it.bagWeightsText}) | Bag Fee: ₹${it.totalBagCharge}\n")
            }
            sb.append("   ${it.netKgs} Kg × ₹${it.ratePerKg} = ₹${it.lineTotal}\n\n")
            grandTotal += it.lineTotal
        }
        binding.tvSalesCartList.text = if (sb.isEmpty()) "No items added to bill yet." else sb.toString()
        binding.tvSalesGrandTotal.text = "Grand Total: Rs. %.2f".format(grandTotal)
    }

    private fun clearSalesItemInputs() {
        binding.etSalesBags.text.clear()
        binding.etSalesManualWeights.text.clear()
        binding.etSalesNetKgs.text.clear()
        binding.etSalesRatePerKg.text.clear()
    }

    private fun saveSalesBill() {
        if (salesCart.isEmpty() || customerList.isEmpty()) {
            Toast.makeText(this, "Select Customer and add at least 1 item", Toast.LENGTH_SHORT).show()
            return
        }

        val cust = customerList[binding.spCustomer.selectedItemPosition]
        val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val billId = db.collection("transactions").document().id
        val payMode = binding.spSalesPayMode.selectedItem.toString()

        val totalBags = salesCart.sumOf { it.bags }
        val totalKgs = salesCart.sumOf { it.netKgs }
        val totalBagCharges = salesCart.sumOf { it.totalBagCharge }
        val grandTotal = salesCart.sumOf { it.lineTotal }

        val tx = BillTransaction(
            billId = billId,
            billNo = binding.etSalesBillNo.text.toString().ifEmpty { "SAL-${System.currentTimeMillis() % 10000}" },
            date = dateStr,
            type = "SALES",
            partyId = cust.id,
            partyName = cust.name,
            paymentMode = payMode,
            totalBags = totalBags,
            totalKgs = totalKgs,
            totalBagCharges = totalBagCharges,
            grandTotal = grandTotal,
            items = salesCart
        )

        val batch = db.batch()
        batch.set(db.collection("transactions").document(billId), tx)

        // Auto-deduct Bags and Kgs from each selected Supplier Stock Batch
        for (item in salesCart) {
            val stockRef = db.collection("stock_batches").document(item.batchCode)
            if (item.bags > 0) {
                batch.update(stockRef, "availableBags", FieldValue.increment(-item.bags.toLong()))
            }
            batch.update(stockRef, "availableKgs", FieldValue.increment(-item.netKgs))
        }

        // Update Customer Outstanding Balance if Credit
        if (payMode == "CREDIT") {
            batch.update(db.collection("customers").document(cust.id), "currentBalance", FieldValue.increment(grandTotal))
        }

        batch.commit().addOnSuccessListener {
            Toast.makeText(this, "Sales Bill Saved & Stock Deducted!", Toast.LENGTH_LONG).show()
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
