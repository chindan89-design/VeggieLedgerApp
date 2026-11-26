package com.shop.veggieledger

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Xml
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shop.veggieledger.databinding.ActivitySalesBinding
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream

class SalesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalesBinding
    private val db = FirebaseFirestore.getInstance()

    private lateinit var weighCoolieContainer: LinearLayout
    private lateinit var etSalesWeighCoolie: EditText

    private val customerList = arrayListOf<CustomerItem>()
    private val editFilterCustomerList = arrayListOf<String>()
    private val itemNames = arrayListOf("Coconut", "Onion", "Garlic")
    private lateinit var itemAdapter: ArrayAdapter<String>
    private val billItemsArray = JSONArray()

    private var selectedDateStr: String = ""
    private var editFilterDateStr: String = ""
    private var selectedCustomerId: String = ""
    private var selectedCustomerName: String = ""
    private var selectedCustomerBalance: Double = 0.0
    private var grandTotalBill: Double = 0.0

    // Edit Mode State
    private var isEditMode: Boolean = false
    private var editingSaleDocId: String = ""
    private var oldSaleCustomerId: String = ""
    private var oldSalePayMode: String = "Credit"
    private var oldSaleGrandTotal: Double = 0.0
    private var oldSalePaidAmount: Double = 0.0
    private val oldSaleItemsJson = JSONArray()

    private val payModes = arrayListOf("Credit", "Cash", "GPay / UPI")

    data class CustomerItem(val id: String, val name: String, val balance: Double)

    private val pickSalesFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            importSalesFromFileUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Sales Entry"

        injectWeighCoolieFieldForOnion()

        binding.btnModeNewSales.setOnClickListener { switchToNewMode() }
        binding.btnModeEditSales.setOnClickListener { switchToEditMode() }

        binding.btnImportSalesCsv.setOnClickListener {
            pickSalesFileLauncher.launch("*/*")
        }

        val cal = Calendar.getInstance()
        selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        editFilterDateStr = selectedDateStr
        binding.etSalesDate.setText(selectedDateStr)
        binding.etEditSalesFilterDate.setText(editFilterDateStr)

        binding.etSalesDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                binding.etSalesDate.setText(selectedDateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val editCal = Calendar.getInstance()
        binding.etEditSalesFilterDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                editCal.set(y, m, d)
                editFilterDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(editCal.time)
                binding.etEditSalesFilterDate.setText(editFilterDateStr)
                loadSalesForEditFilter()
            }, editCal.get(Calendar.YEAR), editCal.get(Calendar.MONTH), editCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.spEditSalesFilterCustomer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                if (isEditMode) loadSalesForEditFilter()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, payModes)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spSalesPayMode.adapter = modeAdapter

        itemAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, itemNames)
        itemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spSalesItem.adapter = itemAdapter

        binding.spSalesItem.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                val chosenItem = binding.spSalesItem.selectedItem?.toString() ?: "Coconut"
                onSalesItemChanged(chosenItem)
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        fetchNextBillNo()
        loadCustomers()
        loadItems()

        binding.etSalesCustomer.setOnClickListener {
            showCustomerSearchDialog()
        }

        // Auto-fill Bag Charge = Bags * 10 for Onion
        binding.etSalesBags.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val itemName = binding.spSalesItem.selectedItem?.toString() ?: "Coconut"
                if (itemName.equals("Onion", ignoreCase = true)) {
                    val bags = s?.toString()?.toIntOrNull() ?: 0
                    val autoBagChg = bags * 10.0
                    binding.etSalesBagCharge.setText(
                        if (bags > 0) String.format(Locale.getDefault(), "%.2f", autoBagChg) else ""
                    )
                }
                updateLineAmountPreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val lineCalcWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateLineAmountPreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etSalesKgs.addTextChangedListener(lineCalcWatcher)
        binding.etSalesRate.addTextChangedListener(lineCalcWatcher)
        binding.etSalesBagCharge.addTextChangedListener(lineCalcWatcher)
        etSalesWeighCoolie.addTextChangedListener(lineCalcWatcher)

        binding.btnAddSalesItem.setOnClickListener {
            val itemName = binding.spSalesItem.selectedItem?.toString() ?: "Coconut"
            val isCoconut = itemName.equals("Coconut", ignoreCase = true)
            val isOnion = itemName.equals("Onion", ignoreCase = true)

            val bags = if (isCoconut) 0 else (binding.etSalesBags.text.toString().toIntOrNull() ?: 0)
            val kgs = binding.etSalesKgs.text.toString().toDoubleOrNull() ?: 0.0
            val rate = binding.etSalesRate.text.toString().toDoubleOrNull() ?: 0.0

            val bagChg = when {
                isCoconut -> 0.0
                isOnion -> {
                    val typedBag = binding.etSalesBagCharge.text.toString().toDoubleOrNull()
                    typedBag ?: (bags * 10.0)
                }
                else -> binding.etSalesBagCharge.text.toString().toDoubleOrNull() ?: 0.0
            }

            val weighCoolie = if (isOnion) {
                etSalesWeighCoolie.text.toString().toDoubleOrNull() ?: 0.0
            } else {
                0.0
            }

            if (kgs <= 0.0 && bags <= 0) {
                Toast.makeText(this, "Enter valid Bags or Net Kgs", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (rate <= 0.0) {
                Toast.makeText(this, "Enter valid Rate", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val baseAmt = kgs * rate
            val rowTotal = baseAmt + bagChg + weighCoolie

            val rowObj = JSONObject()
            rowObj.put("itemName", itemName)
            rowObj.put("bags", bags)
            rowObj.put("netKgs", kgs)
            rowObj.put("rate", rate)
            rowObj.put("amount", baseAmt)
            rowObj.put("bagCharge", bagChg)
            rowObj.put("weighCoolie", weighCoolie)
            rowObj.put("rowTotal", rowTotal)

            billItemsArray.put(rowObj)
            refreshBillItemsGrid()

            binding.etSalesBags.setText("")
            binding.etSalesKgs.setText("")
            binding.etSalesRate.setText("")
            binding.etSalesBagCharge.setText("")
            etSalesWeighCoolie.setText("")
            binding.tvLineAmountPreview.text = "Item Amt: Rs. 0.00"
        }

        binding.btnSaveSalesBill.setOnClickListener {
            val billNo = binding.etSalesBillNo.text.toString().trim()
            val payMode = binding.spSalesPayMode.selectedItem?.toString() ?: "Credit"

            if (selectedCustomerId.isEmpty() && selectedCustomerName.isEmpty()) {
                Toast.makeText(this, "Please select a Customer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (billItemsArray.length() == 0) {
                Toast.makeText(this, "Add at least one item to the bill", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isEditMode && editingSaleDocId.isEmpty()) {
                Toast.makeText(this, "Please select a bill above to edit", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSaveSalesBill.isEnabled = false

            val itemsForDb = arrayListOf<Any>()
            var totalBagsAll = 0
            var totalKgsAll = 0.0
            var totalBagChargesAll = 0.0
            var totalWeighCoolieAll = 0.0
            var firstItemName = ""

            for (i in 0 until billItemsArray.length()) {
                val obj = billItemsArray.getJSONObject(i)
                val iName = obj.getString("itemName")
                val bgs = obj.getInt("bags")
                val kgs = obj.getDouble("netKgs")
                val rt = obj.getDouble("rate")
                val amt = obj.getDouble("amount")
                val bChg = obj.getDouble("bagCharge")
                val wCoolie = obj.optDouble("weighCoolie", 0.0)

                if (i == 0) firstItemName = iName
                totalBagsAll += bgs
                totalKgsAll += kgs
                totalBagChargesAll += bChg
                totalWeighCoolieAll += wCoolie

                val itemMap = hashMapOf<String, Any>(
                    "itemName" to iName,
                    "bags" to bgs,
                    "netKgs" to kgs,
                    "rate" to rt,
                    "amount" to amt,
                    "bagCharge" to bChg,
                    "weighCoolie" to wCoolie
                )
                itemsForDb.add(itemMap)
            }

            val isCredit = payMode.equals("Credit", ignoreCase = true)

            if (isEditMode && editingSaleDocId.isNotEmpty()) {
                for (i in 0 until oldSaleItemsJson.length()) {
                    val oldObj = oldSaleItemsJson.getJSONObject(i)
                    restoreStockFifo(
                        oldObj.optString("itemName", ""),
                        oldObj.optInt("bags", 0),
                        oldObj.optDouble("netKgs", 0.0)
                    )
                }

                for (i in 0 until billItemsArray.length()) {
                    val obj = billItemsArray.getJSONObject(i)
                    deductStockFifo(obj.getString("itemName"), obj.getInt("bags"), obj.getDouble("netKgs"))
                }

                if (oldSalePayMode.equals("Credit", ignoreCase = true) && oldSaleCustomerId.isNotEmpty()) {
                    val oldUnpaid = (oldSaleGrandTotal - oldSalePaidAmount).coerceAtLeast(0.0)
                    adjustCustomerBalance(oldSaleCustomerId, -oldUnpaid)
                }
                if (isCredit && selectedCustomerId.isNotEmpty()) {
                    val newUnpaid = (grandTotalBill - oldSalePaidAmount).coerceAtLeast(0.0)
                    adjustCustomerBalance(selectedCustomerId, newUnpaid)
                }

                val newPaid = if (isCredit) oldSalePaidAmount else grandTotalBill
                val newBal = if (isCredit) (grandTotalBill - oldSalePaidAmount).coerceAtLeast(0.0) else 0.0
                val status = when {
                    newBal <= 0.01 -> "Paid"
                    newPaid > 0.01 -> "Partial"
                    else -> "Unpaid"
                }

                val updateData = hashMapOf<String, Any>(
                    "billNo" to billNo,
                    "date" to selectedDateStr,
                    "customerId" to selectedCustomerId,
                    "customerName" to selectedCustomerName,
                    "payMode" to payMode,
                    "items" to itemsForDb,
                    "itemName" to firstItemName,
                    "bags" to totalBagsAll,
                    "netKgs" to totalKgsAll,
                    "totalBagCharges" to totalBagChargesAll,
                    "totalWeighCoolie" to totalWeighCoolieAll,
                    "grandTotal" to grandTotalBill,
                    "paidAmount" to newPaid,
                    "balanceAmount" to newBal,
                    "paymentStatus" to status
                )

                db.collection("sales").document(editingSaleDocId).update(updateData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Sales Bill $billNo Updated!", Toast.LENGTH_SHORT).show()
                        editingSaleDocId = ""
                        while (oldSaleItemsJson.length() > 0) oldSaleItemsJson.remove(0)
                        resetForm()
                        loadSalesForEditFilter()
                    }
                    .addOnFailureListener {
                        binding.btnSaveSalesBill.isEnabled = true
                        Toast.makeText(this, "Failed to update bill", Toast.LENGTH_SHORT).show()
                    }
            } else {
                for (i in 0 until billItemsArray.length()) {
                    val obj = billItemsArray.getJSONObject(i)
                    deductStockFifo(obj.getString("itemName"), obj.getInt("bags"), obj.getDouble("netKgs"))
                }

                val saleId = db.collection("sales").document().id
                val saleData = hashMapOf<String, Any>(
                    "id" to saleId,
                    "billNo" to billNo,
                    "date" to selectedDateStr,
                    "customerId" to selectedCustomerId,
                    "customerName" to selectedCustomerName,
                    "payMode" to payMode,
                    "items" to itemsForDb,
                    "itemName" to firstItemName,
                    "bags" to totalBagsAll,
                    "netKgs" to totalKgsAll,
                    "totalBagCharges" to totalBagChargesAll,
                    "totalWeighCoolie" to totalWeighCoolieAll,
                    "grandTotal" to grandTotalBill,
                    "paidAmount" to if (isCredit) 0.0 else grandTotalBill,
                    "balanceAmount" to if (isCredit) grandTotalBill else 0.0,
                    "paymentStatus" to if (isCredit) "Unpaid" else "Paid",
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("sales").document(saleId).set(saleData)
                    .addOnSuccessListener {
                        if (isCredit && selectedCustomerId.isNotEmpty()) {
                            adjustCustomerBalance(selectedCustomerId, grandTotalBill)
                        }
                        Toast.makeText(this, "Sales Bill $billNo Saved!", Toast.LENGTH_SHORT).show()
                        resetForm()
                    }
                    .addOnFailureListener {
                        binding.btnSaveSalesBill.isEnabled = true
                        Toast.makeText(this, "Failed to save sales bill", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        onSalesItemChanged(binding.spSalesItem.selectedItem?.toString() ?: "Coconut")
        refreshBillItemsGrid()
    }

    /**
     * Dynamically injects the "Weigh Coolie (Rs)" field right after Bag Charge,
     * shown ONLY when Onion is selected.
     */
    private fun injectWeighCoolieFieldForOnion() {
        val parent = binding.etSalesBagCharge.parent as? ViewGroup ?: return
        val insertIdx = parent.indexOfChild(binding.etSalesBagCharge) + 1

        weighCoolieContainer = LinearLayout(this)
        weighCoolieContainer.orientation = LinearLayout.VERTICAL
        weighCoolieContainer.visibility = View.GONE
        val boxLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        boxLp.setMargins(0, 4, 0, 8)
        weighCoolieContainer.layoutParams = boxLp

        val lblCoolie = TextView(this)
        lblCoolie.text = "Weigh Coolie (Onion) (Rs)"
        lblCoolie.textSize = 12.5f
        lblCoolie.setTypeface(null, Typeface.BOLD)
        lblCoolie.setTextColor(Color.parseColor("#E65100"))

        etSalesWeighCoolie = EditText(this)
        etSalesWeighCoolie.hint = "Enter Weigh Coolie (Rs)"
        etSalesWeighCoolie.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        etSalesWeighCoolie.setBackgroundResource(android.R.drawable.edit_text)
        etSalesWeighCoolie.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        weighCoolieContainer.addView(lblCoolie)
        weighCoolieContainer.addView(etSalesWeighCoolie)
        parent.addView(weighCoolieContainer, insertIdx)
    }

    /**
     * Item visibility rules:
     * - Coconut: NO Bags, NO Bag Charge, NO Weigh Coolie
     * - Garlic: Bags + Bag Charge ONLY (NO Weigh Coolie)
     * - Onion: Bags (Rs. 10/bag) + Bag Charge + Weigh Coolie
     */
    private fun onSalesItemChanged(itemName: String) {
        val isCoconut = itemName.equals("Coconut", ignoreCase = true)
        val isOnion = itemName.equals("Onion", ignoreCase = true)

        if (isCoconut) {
            binding.etSalesBags.visibility = View.GONE
            binding.etSalesBagCharge.visibility = View.GONE
            weighCoolieContainer.visibility = View.GONE
            binding.etSalesBags.setText("")
            binding.etSalesBagCharge.setText("")
            etSalesWeighCoolie.setText("")
        } else if (isOnion) {
            binding.etSalesBags.visibility = View.VISIBLE
            binding.etSalesBagCharge.visibility = View.VISIBLE
            weighCoolieContainer.visibility = View.VISIBLE
            val bags = binding.etSalesBags.text.toString().toIntOrNull() ?: 0
            if (bags > 0) {
                binding.etSalesBagCharge.setText(String.format(Locale.getDefault(), "%.2f", bags * 10.0))
            }
        } else {
            // Garlic & other bagged items
            binding.etSalesBags.visibility = View.VISIBLE
            binding.etSalesBagCharge.visibility = View.VISIBLE
            weighCoolieContainer.visibility = View.GONE
            etSalesWeighCoolie.setText("")
        }

        updateLineAmountPreview()
    }

    private fun updateLineAmountPreview() {
        val itemName = binding.spSalesItem.selectedItem?.toString() ?: "Coconut"
        val isCoconut = itemName.equals("Coconut", ignoreCase = true)
        val isOnion = itemName.equals("Onion", ignoreCase = true)

        val kgs = binding.etSalesKgs.text.toString().toDoubleOrNull() ?: 0.0
        val rate = binding.etSalesRate.text.toString().toDoubleOrNull() ?: 0.0
        val bagChg = if (isCoconut) 0.0 else (binding.etSalesBagCharge.text.toString().toDoubleOrNull() ?: 0.0)
        val weighCoolie = if (isOnion) (etSalesWeighCoolie.text.toString().toDoubleOrNull() ?: 0.0) else 0.0

        val lineTot = (kgs * rate) + bagChg + weighCoolie
        binding.tvLineAmountPreview.text = String.format(Locale.getDefault(), "Item Amt: Rs. %.2f", lineTot)
    }

    private fun switchToNewMode() {
        isEditMode = false
        editingSaleDocId = ""
        while (oldSaleItemsJson.length() > 0) oldSaleItemsJson.remove(0)
        binding.layoutEditSalesPanel.visibility = View.GONE
        binding.btnImportSalesCsv.visibility = View.VISIBLE
        binding.btnModeNewSales.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B5E20"))
        binding.btnModeEditSales.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        binding.tvSalesHeader.text = "SALES ENTRY & BILLING"
        binding.btnSaveSalesBill.text = "SAVE SALES BILL"
        binding.btnSaveSalesBill.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B5E20"))
        resetForm()
    }

    private fun switchToEditMode() {
        isEditMode = true
        editingSaleDocId = ""
        while (oldSaleItemsJson.length() > 0) oldSaleItemsJson.remove(0)
        binding.layoutEditSalesPanel.visibility = View.VISIBLE
        binding.btnImportSalesCsv.visibility = View.GONE
        binding.btnModeEditSales.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
        binding.btnModeNewSales.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
        binding.tvSalesHeader.text = "EDIT / DELETE SALES BILL"
        binding.btnSaveSalesBill.text = "UPDATE SALES BILL"
        binding.btnSaveSalesBill.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E65100"))
        resetForm()
        loadSalesForEditFilter()
    }

    private fun loadSalesForEditFilter() {
        val selectedFilterCust = binding.spEditSalesFilterCustomer.selectedItem?.toString() ?: "All Customers"
        binding.llMatchingSalesList.removeAllViews()

        val loadingTv = TextView(this)
        loadingTv.text = "Loading sales bills..."
        loadingTv.setTextColor(Color.DKGRAY)
        loadingTv.setPadding(12, 12, 12, 12)
        binding.llMatchingSalesList.addView(loadingTv)

        db.collection("sales")
            .whereEqualTo("date", editFilterDateStr)
            .get()
            .addOnSuccessListener { snap ->
                binding.llMatchingSalesList.removeAllViews()
                var matchCount = 0

                for (doc in snap) {
                    val cName = doc.getString("customerName") ?: ""
                    if (selectedFilterCust != "All Customers" && !cName.equals(selectedFilterCust, ignoreCase = true)) {
                        continue
                    }

                    matchCount++
                    val bNo = doc.getString("billNo") ?: doc.id
                    val tot = doc.getDouble("grandTotal") ?: 0.0
                    val pMode = doc.getString("payMode") ?: "Credit"

                    val rowCard = LinearLayout(this)
                    rowCard.orientation = LinearLayout.HORIZONTAL
                    rowCard.gravity = Gravity.CENTER_VERTICAL
                    rowCard.setBackgroundColor(Color.WHITE)
                    rowCard.setPadding(16, 14, 16, 14)
                    val cardLp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    cardLp.setMargins(0, 0, 0, 10)
                    rowCard.layoutParams = cardLp

                    val infoTv = TextView(this)
                    infoTv.text = String.format(Locale.getDefault(), "%s | %s\n%s | Rs. %.2f", bNo, cName, pMode, tot)
                    infoTv.setTextColor(Color.parseColor("#1B5E20"))
                    infoTv.setTypeface(null, Typeface.BOLD)
                    infoTv.textSize = 13f
                    infoTv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val editBtn = TextView(this)
                    editBtn.text = "EDIT"
                    editBtn.setTextColor(Color.WHITE)
                    editBtn.setBackgroundColor(Color.parseColor("#1565C0"))
                    editBtn.setTypeface(null, Typeface.BOLD)
                    editBtn.textSize = 12f
                    editBtn.setPadding(24, 14, 24, 14)
                    val editLp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    editLp.setMargins(8, 0, 8, 0)
                    editBtn.layoutParams = editLp
                    editBtn.setOnClickListener {
                        populateFormFromSaleDoc(doc.id, doc.data)
                    }

                    val delBtn = TextView(this)
                    delBtn.text = "DEL"
                    delBtn.setTextColor(Color.WHITE)
                    delBtn.setBackgroundColor(Color.parseColor("#C62828"))
                    delBtn.setTypeface(null, Typeface.BOLD)
                    delBtn.textSize = 12f
                    delBtn.setPadding(24, 14, 24, 14)
                    delBtn.setOnClickListener {
                        confirmAndDeleteSale(doc.id, bNo, doc.data)
                    }

                    rowCard.addView(infoTv)
                    rowCard.addView(editBtn)
                    rowCard.addView(delBtn)
                    binding.llMatchingSalesList.addView(rowCard)
                }

                if (matchCount == 0) {
                    val noTv = TextView(this)
                    noTv.text = "No sales bills found for $editFilterDateStr ($selectedFilterCust)."
                    noTv.setTextColor(Color.RED)
                    noTv.setPadding(12, 14, 12, 14)
                    binding.llMatchingSalesList.addView(noTv)
                }
            }
            .addOnFailureListener {
                binding.llMatchingSalesList.removeAllViews()
                Toast.makeText(this, "Error loading bills", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmAndDeleteSale(docId: String, bNo: String, data: Map<String, Any>) {
        AlertDialog.Builder(this)
            .setTitle("Delete Sales Bill")
            .setMessage("Are you sure you want to delete $bNo? Stock and Customer balance will be restored.")
            .setPositiveButton("DELETE") { _, _ ->
                deleteSaleAndRestore(docId, data)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSaleAndRestore(docId: String, data: Map<String, Any>) {
        val custId = data["customerId"]?.toString() ?: ""
        val pMode = data["payMode"]?.toString() ?: "Credit"
        val grandTot = (data["grandTotal"] as? Number)?.toDouble() ?: 0.0
        val paidAmt = (data["paidAmount"] as? Number)?.toDouble() ?: 0.0
        val rawItems = data["items"] as? List<*>

        db.collection("sales").document(docId).delete()
            .addOnSuccessListener {
                if (rawItems != null) {
                    for (entry in rawItems) {
                        val m = entry as? Map<*, *> ?: continue
                        val iName = m["itemName"]?.toString() ?: ""
                        val bgs = (m["bags"] as? Number)?.toInt() ?: 0
                        val kgs = (m["netKgs"] as? Number)?.toDouble() ?: 0.0
                        restoreStockFifo(iName, bgs, kgs)
                    }
                } else {
                    val iName = data["itemName"]?.toString() ?: ""
                    val bgs = (data["bags"] as? Number)?.toInt() ?: 0
                    val kgs = (data["netKgs"] as? Number)?.toDouble() ?: 0.0
                    restoreStockFifo(iName, bgs, kgs)
                }

                if (pMode.equals("Credit", ignoreCase = true) && custId.isNotEmpty()) {
                    val unpaidRev = (grandTot - paidAmt).coerceAtLeast(0.0)
                    adjustCustomerBalance(custId, -unpaidRev)
                }

                if (editingSaleDocId == docId) {
                    editingSaleDocId = ""
                    resetForm()
                }
                Toast.makeText(this, "Sales Bill Deleted & Stock Restored!", Toast.LENGTH_SHORT).show()
                loadSalesForEditFilter()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete bill", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateFormFromSaleDoc(docId: String, data: Map<String, Any>) {
        editingSaleDocId = docId
        oldSaleCustomerId = data["customerId"]?.toString() ?: ""
        oldSalePayMode = data["payMode"]?.toString() ?: "Credit"
        oldSaleGrandTotal = (data["grandTotal"] as? Number)?.toDouble() ?: 0.0
        oldSalePaidAmount = (data["paidAmount"] as? Number)?.toDouble() ?: 0.0

        val bNo = data["billNo"]?.toString() ?: ""
        val cName = data["customerName"]?.toString() ?: ""
        val bDate = data["date"]?.toString() ?: editFilterDateStr

        selectedDateStr = bDate
        selectedCustomerId = oldSaleCustomerId
        selectedCustomerName = cName

        val foundCust = customerList.find { c -> c.id == oldSaleCustomerId || c.name.equals(cName, ignoreCase = true) }
        selectedCustomerBalance = foundCust?.balance ?: 0.0
        if (selectedCustomerId.isEmpty() && foundCust != null) {
            selectedCustomerId = foundCust.id
        }

        binding.etSalesDate.setText(bDate)
        binding.etSalesBillNo.setText(bNo)
        binding.etSalesCustomer.setText(cName)
        binding.tvSalesCustBalance.text = String.format(Locale.getDefault(), "Previous Balance: Rs. %.2f", selectedCustomerBalance)

        val modeIdx = payModes.indexOfFirst { m -> m.equals(oldSalePayMode, ignoreCase = true) }
        if (modeIdx >= 0) binding.spSalesPayMode.setSelection(modeIdx)

        while (billItemsArray.length() > 0) billItemsArray.remove(0)
        while (oldSaleItemsJson.length() > 0) oldSaleItemsJson.remove(0)

        val rawItems = data["items"] as? List<*>
        if (rawItems != null && rawItems.isNotEmpty()) {
            for (entry in rawItems) {
                val m = entry as? Map<*, *> ?: continue
                val iName = m["itemName"]?.toString() ?: "Coconut"
                val bgs = (m["bags"] as? Number)?.toInt() ?: 0
                val kgs = (m["netKgs"] as? Number)?.toDouble() ?: 0.0
                val rt = (m["rate"] as? Number)?.toDouble() ?: 0.0
                val amt = (m["amount"] as? Number)?.toDouble() ?: (kgs * rt)
                val bChg = (m["bagCharge"] as? Number)?.toDouble() ?: 0.0
                val wCoolie = (m["weighCoolie"] as? Number)?.toDouble() ?: 0.0

                val obj = JSONObject()
                obj.put("itemName", iName)
                obj.put("bags", bgs)
                obj.put("netKgs", kgs)
                obj.put("rate", rt)
                obj.put("amount", amt)
                obj.put("bagCharge", bChg)
                obj.put("weighCoolie", wCoolie)
                obj.put("rowTotal", amt + bChg + wCoolie)
                billItemsArray.put(obj)

                val oldCopy = JSONObject(obj.toString())
                oldSaleItemsJson.put(oldCopy)
            }
        } else {
            val iName = data["itemName"]?.toString() ?: "Coconut"
            val bgs = (data["bags"] as? Number)?.toInt() ?: 0
            val kgs = (data["netKgs"] as? Number)?.toDouble() ?: 0.0
            val tot = oldSaleGrandTotal
            val rt = if (kgs > 0.0) tot / kgs else 0.0

            val obj = JSONObject()
            obj.put("itemName", iName)
            obj.put("bags", bgs)
            obj.put("netKgs", kgs)
            obj.put("rate", rt)
            obj.put("amount", tot)
            obj.put("bagCharge", 0.0)
            obj.put("weighCoolie", 0.0)
            obj.put("rowTotal", tot)
            billItemsArray.put(obj)
            oldSaleItemsJson.put(JSONObject(obj.toString()))
        }

        refreshBillItemsGrid()
        Toast.makeText(this, "Loaded $bNo for editing", Toast.LENGTH_SHORT).show()
    }

    private fun adjustCustomerBalance(custId: String, delta: Double) {
        val custRef = db.collection("customers").document(custId)
        db.runTransaction { tr ->
            val snap = tr.get(custRef)
            if (snap.exists()) {
                val curr = snap.getDouble("currentBalance") ?: 0.0
                tr.update(custRef, "currentBalance", (curr + delta).coerceAtLeast(0.0))
            }
        }
    }

    private fun restoreStockFifo(itemName: String, bagsToAdd: Int, kgsToAdd: Double) {
        if (itemName.isEmpty() || (bagsToAdd <= 0 && kgsToAdd <= 0.0)) return
        db.collection("stock_batches").get().addOnSuccessListener { snap ->
            val targetDoc = snap.documents.filter { doc ->
                (doc.getString("itemName") ?: "").equals(itemName, ignoreCase = true)
            }.maxByOrNull { doc -> doc.getLong("timestamp") ?: 0L }

            if (targetDoc != null) {
                val availBags = (targetDoc.getLong("availableBags") ?: 0L).toInt()
                val availKgs = targetDoc.getDouble("availableKgs") ?: 0.0
                db.collection("stock_batches").document(targetDoc.id).update(
                    hashMapOf<String, Any>(
                        "availableBags" to (availBags + bagsToAdd),
                        "availableKgs" to (availKgs + kgsToAdd)
                    )
                )
            }
        }
    }

    private fun deductStockFifo(itemName: String, bagsToDeduct: Int, kgsToDeduct: Double) {
        db.collection("stock_batches").get().addOnSuccessListener { snap ->
            var remBags = bagsToDeduct
            var remKgs = kgsToDeduct

            val matchingDocs = snap.documents.filter { doc ->
                (doc.getString("itemName") ?: "").equals(itemName, ignoreCase = true)
            }.sortedBy { doc -> doc.getLong("timestamp") ?: 0L }

            for (doc in matchingDocs) {
                if (remBags <= 0 && remKgs <= 0.001) break
                val availBags = (doc.getLong("availableBags") ?: 0L).toInt()
                val availKgs = doc.getDouble("availableKgs") ?: 0.0

                if (availBags <= 0 && availKgs <= 0.001) continue

                val dedBags = minOf(remBags, availBags)
                val dedKgs = minOf(remKgs, availKgs)

                val newBags = (availBags - dedBags).coerceAtLeast(0)
                val newKgs = (availKgs - dedKgs).coerceAtLeast(0.0)

                remBags -= dedBags
                remKgs -= dedKgs

                db.collection("stock_batches").document(doc.id).update(
                    hashMapOf<String, Any>(
                        "availableBags" to newBags,
                        "availableKgs" to newKgs
                    )
                )
            }
        }
    }

    private fun importSalesFromFileUri(uri: Uri) {
        try {
            val rawBytes = contentResolver.openInputStream(uri)?.readBytes()
            if (rawBytes == null || rawBytes.isEmpty()) {
                Toast.makeText(this, "Selected file is empty!", Toast.LENGTH_SHORT).show()
                return
            }

            val isXlsx = rawBytes.size >= 2 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte()
            val parsedRows: List<List<String>> = if (isXlsx) {
                parseXlsxRows(rawBytes)
            } else {
                parseCsvRows(rawBytes)
            }

            if (parsedRows.size <= 1) {
                Toast.makeText(this, "No data rows found in file!", Toast.LENGTH_LONG).show()
                return
            }

            Toast.makeText(this, "Reading ${parsedRows.size - 1} rows...", Toast.LENGTH_SHORT).show()

            db.collection("customers").get().addOnSuccessListener { custSnap ->
                db.collection("stock_batches").get().addOnSuccessListener { stockSnap ->

                    val custIdMap = HashMap<String, String>()
                    val custBalMap = HashMap<String, Double>()
                    for (doc in custSnap) {
                        val cName = (doc.getString("name") ?: "").trim().lowercase(Locale.getDefault())
                        if (cName.isNotEmpty()) {
                            custIdMap[cName] = doc.id
                            custBalMap[doc.id] = doc.getDouble("currentBalance") ?: 0.0
                        }
                    }

                    val stockDocIdMap = HashMap<String, String>()
                    val stockAvailBagsMap = HashMap<String, Int>()
                    val stockAvailKgsMap = HashMap<String, Double>()

                    for (doc in stockSnap) {
                        val dId = doc.id
                        val bId = (doc.getString("batchId") ?: doc.getString("lotId") ?: doc.getString("stockId") ?: "").trim().lowercase(Locale.getDefault())
                        stockDocIdMap[dId.lowercase(Locale.getDefault())] = dId
                        if (bId.isNotEmpty()) stockDocIdMap[bId] = dId

                        stockAvailBagsMap[dId] = (doc.getLong("availableBags") ?: 0L).toInt()
                        stockAvailKgsMap[dId] = doc.getDouble("availableKgs") ?: 0.0
                    }

                    val batch = db.batch()
                    var importedCount = 0
                    val addedCreditPerCust = HashMap<String, Double>()
                    val fifoFallbackList = arrayListOf<Triple<String, Int, Double>>()
                    val modifiedStockIds = HashSet<String>()

                    val headerRow = parsedRows[0].map { h -> h.trim().lowercase(Locale.getDefault()) }
                    val hasStockCol = headerRow.any { h -> h.contains("stock") || h.contains("lot") || h.contains("batch") }

                    for (i in 1 until parsedRows.size) {
                        val cols = parsedRows[i]
                        if (cols.size < 6) continue

                        val dateStr = cols.getOrElse(0) { selectedDateStr }.trim().ifEmpty { selectedDateStr }
                        val billNo = cols.getOrElse(1) { "SAL-IMP-$i" }.trim()
                        val custName = cols.getOrElse(2) { "" }.trim()
                        val payMode = cols.getOrElse(3) { "Credit" }.trim().ifEmpty { "Credit" }
                        val itemName = cols.getOrElse(4) { "Coconut" }.trim()

                        val stockIdInput: String
                        val bags: Int
                        val netKgs: Double
                        val rate: Double
                        val amount: Double
                        val bagCharge: Double
                        val grandTotal: Double

                        if (hasStockCol) {
                            stockIdInput = cols.getOrElse(5) { "" }.trim()
                            bags = cleanNum(cols.getOrElse(6) { "0" }).toInt()
                            netKgs = cleanNum(cols.getOrElse(7) { "0" })
                            rate = cleanNum(cols.getOrElse(8) { "0" })
                            amount = cleanNum(cols.getOrElse(9) { "0" }).let { if (it > 0.0) it else netKgs * rate }
                            bagCharge = cleanNum(cols.getOrElse(10) { "0" })
                            grandTotal = cleanNum(cols.getOrElse(11) { "0" }).let { if (it > 0.0) it else amount + bagCharge }
                        } else {
                            stockIdInput = ""
                            bags = cleanNum(cols.getOrElse(5) { "0" }).toInt()
                            netKgs = cleanNum(cols.getOrElse(6) { "0" })
                            rate = cleanNum(cols.getOrElse(7) { "0" })
                            amount = cleanNum(cols.getOrElse(8) { "0" }).let { if (it > 0.0) it else netKgs * rate }
                            bagCharge = cleanNum(cols.getOrElse(9) { "0" })
                            grandTotal = cleanNum(cols.getOrElse(10) { "0" }).let { if (it > 0.0) it else amount + bagCharge }
                        }

                        if (custName.isEmpty() || (grandTotal <= 0.0 && netKgs <= 0.0 && bags <= 0)) continue

                        val custKey = custName.lowercase(Locale.getDefault())
                        val custId = custIdMap[custKey] ?: ""

                        val itemEntry = hashMapOf<String, Any>(
                            "itemName" to itemName,
                            "stockId" to stockIdInput,
                            "bags" to bags,
                            "netKgs" to netKgs,
                            "rate" to rate,
                            "amount" to amount,
                            "bagCharge" to bagCharge,
                            "weighCoolie" to 0.0
                        )

                        val isCredit = payMode.equals("Credit", ignoreCase = true)
                        val docId = db.collection("sales").document().id
                        val saleData = hashMapOf<String, Any>(
                            "id" to docId,
                            "date" to dateStr,
                            "billNo" to billNo,
                            "customerId" to custId,
                            "customerName" to custName,
                            "payMode" to payMode,
                            "items" to arrayListOf(itemEntry),
                            "itemName" to itemName,
                            "stockId" to stockIdInput,
                            "bags" to bags,
                            "netKgs" to netKgs,
                            "totalBagCharges" to bagCharge,
                            "totalWeighCoolie" to 0.0,
                            "grandTotal" to grandTotal,
                            "paidAmount" to if (isCredit) 0.0 else grandTotal,
                            "balanceAmount" to if (isCredit) grandTotal else 0.0,
                            "paymentStatus" to if (isCredit) "Unpaid" else "Paid",
                            "timestamp" to (System.currentTimeMillis() + i)
                        )

                        batch.set(db.collection("sales").document(docId), saleData)
                        importedCount++

                        if (isCredit && custId.isNotEmpty()) {
                            addedCreditPerCust[custId] = (addedCreditPerCust[custId] ?: 0.0) + grandTotal
                        }

                        val matchedStockDocId = stockDocIdMap[stockIdInput.lowercase(Locale.getDefault())]
                        if (matchedStockDocId != null) {
                            val currB = stockAvailBagsMap[matchedStockDocId] ?: 0
                            val currK = stockAvailKgsMap[matchedStockDocId] ?: 0.0
                            stockAvailBagsMap[matchedStockDocId] = (currB - bags).coerceAtLeast(0)
                            stockAvailKgsMap[matchedStockDocId] = (currK - netKgs).coerceAtLeast(0.0)
                            modifiedStockIds.add(matchedStockDocId)
                        } else if (itemName.isNotEmpty()) {
                            fifoFallbackList.add(Triple(itemName, bags, netKgs))
                        }
                    }

                    if (importedCount == 0) {
                        Toast.makeText(this, "0 valid rows found! Check column headers.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    for (sDocId in modifiedStockIds) {
                        val newBags = stockAvailBagsMap[sDocId] ?: 0
                        val newKgs = stockAvailKgsMap[sDocId] ?: 0.0
                        batch.update(
                            db.collection("stock_batches").document(sDocId),
                            hashMapOf<String, Any>(
                                "availableBags" to newBags,
                                "availableKgs" to newKgs
                            )
                        )
                    }

                    for ((cId, addAmt) in addedCreditPerCust) {
                        val newBal = (custBalMap[cId] ?: 0.0) + addAmt
                        batch.update(db.collection("customers").document(cId), "currentBalance", newBal)
                    }

                    batch.commit()
                        .addOnSuccessListener {
                            for (fb in fifoFallbackList) {
                                deductStockFifo(fb.first, fb.second, fb.third)
                            }
                            Toast.makeText(this, "SUCCESS! Imported $importedCount Sales Bills!", Toast.LENGTH_LONG).show()
                            fetchNextBillNo()
                            loadCustomers()
                        }
                        .addOnFailureListener { err ->
                            Toast.makeText(this, "DB Save Error: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Import Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun cleanNum(raw: String): Double {
        return raw.replace(",", "").replace("Rs.", "", ignoreCase = true).trim().toDoubleOrNull() ?: 0.0
    }

    private fun parseCsvRows(bytes: ByteArray): List<List<String>> {
        val result = arrayListOf<List<String>>()
        val reader = BufferedReader(InputStreamReader(ByteArrayInputStream(bytes)))
        reader.forEachLine { line ->
            if (line.trim().isNotEmpty()) {
                val tokens = arrayListOf<String>()
                val sb = StringBuilder()
                var inQuotes = false
                for (ch in line) {
                    when {
                        ch == '"' -> inQuotes = !inQuotes
                        ch == ',' && !inQuotes -> {
                            tokens.add(sb.toString().trim())
                            sb.setLength(0)
                        }
                        else -> sb.append(ch)
                    }
                }
                tokens.add(sb.toString().trim())
                result.add(tokens)
            }
        }
        return result
    }

    private fun parseXlsxRows(bytes: ByteArray): List<List<String>> {
        val sharedStrings = arrayListOf<String>()
        var sheetXmlBytes: ByteArray? = null

        val zis = ZipInputStream(ByteArrayInputStream(bytes))
        var entry = zis.nextEntry
        while (entry != null) {
            val name = entry.name
            if (name == "xl/sharedStrings.xml") {
                val ssBytes = zis.readBytes()
                val parser = Xml.newPullParser()
                parser.setInput(ByteArrayInputStream(ssBytes), "UTF-8")
                var event = parser.eventType
                val currentStr = StringBuilder()
                var inSi = false
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "si") {
                        inSi = true
                        currentStr.setLength(0)
                    } else if (event == XmlPullParser.START_TAG && parser.name == "t" && inSi) {
                        currentStr.append(parser.nextText())
                    } else if (event == XmlPullParser.END_TAG && parser.name == "si") {
                        sharedStrings.add(currentStr.toString())
                        inSi = false
                    }
                    event = parser.next()
                }
            } else if (name == "xl/worksheets/sheet1.xml") {
                sheetXmlBytes = zis.readBytes()
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()

        val rows = arrayListOf<List<String>>()
        if (sheetXmlBytes == null) return rows

        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(sheetXmlBytes), "UTF-8")
        var event = parser.eventType
        var currentRow = ArrayList<String>()
        var cellType = ""
        var colIndex = 0

        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "row" -> {
                        currentRow = ArrayList()
                        colIndex = 0
                    }
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        val ref = parser.getAttributeValue(null, "r") ?: ""
                        val targetCol = colLetterToIndex(ref)
                        while (currentRow.size < targetCol) {
                            currentRow.add("")
                        }
                        colIndex = targetCol
                    }
                    "v" -> {
                        val rawVal = parser.nextText()
                        val finalVal = if (cellType == "s") {
                            val idx = rawVal.toIntOrNull() ?: -1
                            if (idx in sharedStrings.indices) sharedStrings[idx] else rawVal
                        } else {
                            rawVal
                        }
                        if (colIndex == currentRow.size) currentRow.add(finalVal)
                        else if (colIndex < currentRow.size) currentRow[colIndex] = finalVal
                    }
                    "t" -> {
                        val inlineText = parser.nextText()
                        if (colIndex == currentRow.size) currentRow.add(inlineText)
                        else if (colIndex < currentRow.size) currentRow[colIndex] = inlineText
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "row") {
                if (currentRow.isNotEmpty()) {
                    rows.add(currentRow)
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun colLetterToIndex(cellRef: String): Int {
        var col = 0
        for (ch in cellRef) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else {
                break
            }
        }
        return if (col > 0) col - 1 else 0
    }

    private fun showCustomerSearchDialog() {
        if (customerList.isEmpty()) {
            Toast.makeText(this, "Loading customers...", Toast.LENGTH_SHORT).show()
            loadCustomers()
            return
        }

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(32, 24, 32, 16)

        val searchInput = EditText(this)
        searchInput.hint = "Type customer name..."
        searchInput.setSingleLine(true)
        container.addView(searchInput)

        val listView = ListView(this)
        val filtered = ArrayList(customerList)
        val names = arrayListOf<String>()
        for (c in filtered) names.add(c.name)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        listView.adapter = adapter
        container.addView(listView)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Customer")
            .setView(container)
            .setNegativeButton("Close", null)
            .create()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: ""
                filtered.clear()
                names.clear()
                for (c in customerList) {
                    if (c.name.lowercase(Locale.getDefault()).contains(q)) {
                        filtered.add(c)
                        names.add(c.name)
                    }
                }
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        listView.setOnItemClickListener { _, _, pos, _ ->
            val chosen = filtered[pos]
            selectedCustomerId = chosen.id
            selectedCustomerName = chosen.name
            selectedCustomerBalance = chosen.balance
            binding.etSalesCustomer.setText(chosen.name)
            binding.tvSalesCustBalance.text = String.format(Locale.getDefault(), "Previous Balance: Rs. %.2f", chosen.balance)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun makeCell(text: String, alignRight: Boolean = false, bold: Boolean = false): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.BLACK)
        tv.textSize = 12f
        tv.setPadding(14, 14, 14, 14)
        tv.gravity = if (alignRight) Gravity.END else Gravity.START
        if (bold) tv.setTypeface(null, Typeface.BOLD)
        return tv
    }

    private fun refreshBillItemsGrid() {
        val count = binding.tlSalesItemsGrid.childCount
        if (count > 1) {
            binding.tlSalesItemsGrid.removeViews(1, count - 1)
        }

        grandTotalBill = 0.0

        if (billItemsArray.length() == 0) {
            val emptyRow = TableRow(this)
            emptyRow.setBackgroundColor(Color.WHITE)
            val tv = TextView(this)
            tv.text = "No items added to bill yet."
            tv.setTextColor(Color.GRAY)
            tv.setPadding(16, 16, 16, 16)
            val lp = TableRow.LayoutParams()
            lp.span = 7
            tv.layoutParams = lp
            emptyRow.addView(tv)
            binding.tlSalesItemsGrid.addView(emptyRow)
            binding.tvSalesGrandTotal.text = "Grand Total: Rs. 0.00"
            return
        }

        for (i in 0 until billItemsArray.length()) {
            val obj = billItemsArray.getJSONObject(i)
            val iName = obj.getString("itemName")
            val bgs = obj.getInt("bags")
            val kgs = obj.getDouble("netKgs")
            val rt = obj.getDouble("rate")
            val bChg = obj.getDouble("bagCharge")
            val wCoolie = obj.optDouble("weighCoolie", 0.0)
            val rowTot = obj.getDouble("rowTotal")
            grandTotalBill += rowTot

            val tr = TableRow(this)
            tr.setBackgroundColor(if (i % 2 == 0) Color.WHITE else Color.parseColor("#F1F8E9"))

            val bagDisplay = if (iName.equals("Coconut", ignoreCase = true) || bgs <= 0) "-" else "$bgs"
            val extraCharges = bChg + wCoolie
            val extraDisplay = if (wCoolie > 0.0) {
                String.format(Locale.getDefault(), "%.0f+%.0f", bChg, wCoolie)
            } else {
                String.format(Locale.getDefault(), "%.2f", extraCharges)
            }

            tr.addView(makeCell(iName, bold = true))
            tr.addView(makeCell(bagDisplay, alignRight = true))
            tr.addView(makeCell(String.format(Locale.getDefault(), "%.2f", kgs), alignRight = true))
            tr.addView(makeCell(String.format(Locale.getDefault(), "%.2f", rt), alignRight = true))
            tr.addView(makeCell(extraDisplay, alignRight = true))
            tr.addView(makeCell(String.format(Locale.getDefault(), "%.2f", rowTot), alignRight = true, bold = true))

            val delBtn = TextView(this)
            delBtn.text = "X"
            delBtn.setTextColor(Color.RED)
            delBtn.setTypeface(null, Typeface.BOLD)
            delBtn.gravity = Gravity.CENTER
            delBtn.setPadding(16, 14, 16, 14)
            delBtn.setOnClickListener {
                billItemsArray.remove(i)
                refreshBillItemsGrid()
            }
            tr.addView(delBtn)

            binding.tlSalesItemsGrid.addView(tr)
        }

        binding.tvSalesGrandTotal.text = String.format(Locale.getDefault(), "Grand Total: Rs. %.2f", grandTotalBill)
    }

    private fun fetchNextBillNo() {
        db.collection("sales").get().addOnSuccessListener { snap ->
            binding.etSalesBillNo.setText(String.format(Locale.getDefault(), "SAL-%03d", snap.size() + 1))
        }
    }

    private fun loadCustomers() {
        db.collection("customers").get().addOnSuccessListener { snap ->
            customerList.clear()
            editFilterCustomerList.clear()
            editFilterCustomerList.add("All Customers")

            for (doc in snap) {
                val name = doc.getString("name") ?: continue
                val bal = doc.getDouble("currentBalance") ?: 0.0
                customerList.add(CustomerItem(doc.id, name, bal))
                editFilterCustomerList.add(name)
            }
            customerList.sortBy { c -> c.name.lowercase(Locale.getDefault()) }

            val filterAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, editFilterCustomerList)
            filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spEditSalesFilterCustomer.adapter = filterAdapter
        }
    }

    private fun loadItems() {
        db.collection("items").get().addOnSuccessListener { snap ->
            val fetched = arrayListOf<String>()
            for (doc in snap) {
                val n = (doc.getString("name") ?: doc.getString("itemName") ?: "").trim()
                if (n.isNotEmpty() && !fetched.contains(n)) {
                    fetched.add(n)
                }
            }
            if (fetched.isNotEmpty()) {
                fetched.sort()
                itemNames.clear()
                itemNames.addAll(fetched)
                itemAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun resetForm() {
        selectedCustomerId = ""
        selectedCustomerName = ""
        selectedCustomerBalance = 0.0
        while (billItemsArray.length() > 0) billItemsArray.remove(0)
        refreshBillItemsGrid()

        binding.etSalesCustomer.setText("")
        binding.tvSalesCustBalance.text = "Previous Balance: Rs. 0.00"
        binding.etSalesBags.setText("")
        binding.etSalesKgs.setText("")
        binding.etSalesRate.setText("")
        binding.etSalesBagCharge.setText("")
        etSalesWeighCoolie.setText("")
        binding.btnSaveSalesBill.isEnabled = true
        if (!isEditMode) fetchNextBillNo() else binding.etSalesBillNo.setText("")
        loadCustomers()
    }
}
