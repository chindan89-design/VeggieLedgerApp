package com.shop.veggieledger

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Xml
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TableLayout
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
import kotlin.math.abs

class SalesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalesBinding
    private val db = FirebaseFirestore.getInstance()

    private lateinit var spSalesStockBatch: Spinner
    private lateinit var tvSelectedBatchAvail: TextView
    private lateinit var etSalesWeighCoolie: EditText
    private lateinit var btnSendWhatsappBill: Button

    // TableLayout rows for dynamic visibility (Non-Coconut fields & Cash Payer)
    private lateinit var rowBags: TableRow
    private lateinit var rowBagRate: TableRow
    private lateinit var rowWeighCoolie: TableRow
    private lateinit var rowCashPayer: TableRow

    // 6. Pay Mode (Single-line Cash | GPay | Credit)
    private lateinit var etSplitCash: EditText
    private lateinit var etSplitGpay: EditText
    private lateinit var etSplitCredit: EditText
    private lateinit var etCashPayerName: EditText

    private val allStockBatchesJson = JSONArray()
    private val filteredBatchesJson = JSONArray()
    private val batchDisplayList = arrayListOf<String>()
    private lateinit var batchSpinnerAdapter: ArrayAdapter<String>

    private val customerList = arrayListOf<CustomerItem>()
    private val editFilterCustomerList = arrayListOf<String>()
    private val itemNames = arrayListOf("Coconut", "Onion", "Garlic")
    private lateinit var itemAdapter: ArrayAdapter<String>
    private val billItemsArray = JSONArray()

    private var selectedDateStr: String = ""
    private var editFilterDateStr: String = ""
    private var selectedCustomerId: String = ""
    private var selectedCustomerName: String = ""
    private var selectedCustomerPhone: String = ""
    private var selectedCustomerBalance: Double = 0.0
    private var grandTotalBill: Double = 0.0

    // Edit Mode State
    private var isEditMode: Boolean = false
    private var editingSaleDocId: String = ""
    private var oldSaleCustomerId: String = ""
    private var oldSaleCreditBalance: Double = 0.0
    private val oldSaleItemsJson = JSONArray()

    data class CustomerItem(val id: String, val name: String, val phone: String, val balance: Double)

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

        buildAllInputsAsTableLayout()
        injectWhatsappButtonAfterSave()

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

        spSalesStockBatch.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                updateSelectedBatchAvailabilityBanner()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        fetchNextBillNo()
        loadCustomers()
        loadItems()
        loadAvailableStockBatches()

        binding.etSalesCustomer.setOnClickListener {
            showCustomerSearchDialog()
        }

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

            val batchPos = spSalesStockBatch.selectedItemPosition
            if (filteredBatchesJson.length() == 0 || batchPos < 0 || batchPos >= filteredBatchesJson.length()) {
                Toast.makeText(this, "No available stock batch for $itemName! Add Purchase first.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val selectedBatchObj = filteredBatchesJson.getJSONObject(batchPos)
            val stockDocId = selectedBatchObj.optString("docId", "")
            val batchId = selectedBatchObj.optString("batchId", "")
            val supplierName = selectedBatchObj.optString("supplierName", "Unknown Supplier")
            val availBags = selectedBatchObj.optInt("availableBags", 0)
            val availKgs = selectedBatchObj.optDouble("availableKgs", 0.0)

            var alreadyUsedBags = 0
            var alreadyUsedKgs = 0.0
            for (k in 0 until billItemsArray.length()) {
                val existing = billItemsArray.getJSONObject(k)
                if (existing.optString("stockDocId", "") == stockDocId) {
                    alreadyUsedBags += existing.optInt("bags", 0)
                    alreadyUsedKgs += existing.optDouble("netKgs", 0.0)
                }
            }

            val remainingBags = (availBags - alreadyUsedBags).coerceAtLeast(0)
            val remainingKgs = (availKgs - alreadyUsedKgs).coerceAtLeast(0.0)

            val bags = if (isCoconut) 0 else (binding.etSalesBags.text.toString().toIntOrNull() ?: 0)
            val kgs = binding.etSalesKgs.text.toString().toDoubleOrNull() ?: 0.0
            val rate = binding.etSalesRate.text.toString().toDoubleOrNull() ?: 0.0

            if (kgs <= 0.0 && bags <= 0) {
                Toast.makeText(this, "Enter valid Bags or Net Kgs", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (rate <= 0.0) {
                Toast.makeText(this, "Enter valid Rate", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (kgs > remainingKgs + 0.01) {
                Toast.makeText(
                    this,
                    String.format(Locale.getDefault(), "Insufficient Stock! %s (%s) only has %.2f Kgs left.", supplierName, batchId, remainingKgs),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            if (!isCoconut && bags > remainingBags) {
                Toast.makeText(
                    this,
                    "Insufficient Bags! $supplierName ($batchId) only has $remainingBags Bags left.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val bagChg = when {
                isCoconut -> 0.0
                isOnion -> {
                    val typedBag = binding.etSalesBagCharge.text.toString().toDoubleOrNull()
                    typedBag ?: (bags * 10.0)
                }
                else -> binding.etSalesBagCharge.text.toString().toDoubleOrNull() ?: 0.0
            }

            val weighCoolie = if (isCoconut) {
                0.0
            } else {
                etSalesWeighCoolie.text.toString().toDoubleOrNull() ?: 0.0
            }

            val baseAmt = kgs * rate
            val rowTotal = baseAmt + bagChg + weighCoolie

            val rowObj = JSONObject()
            rowObj.put("itemName", itemName)
            rowObj.put("stockDocId", stockDocId)
            rowObj.put("batchId", batchId)
            rowObj.put("supplierName", supplierName)
            rowObj.put("bags", bags)
            rowObj.put("netKgs", kgs)
            rowObj.put("rate", rate)
            rowObj.put("amount", baseAmt)
            rowObj.put("bagCharge", bagChg)
            rowObj.put("weighCoolie", weighCoolie)
            rowObj.put("rowTotal", rowTotal)

            billItemsArray.put(rowObj)
            refreshBillItemsGrid()
            updateSelectedBatchAvailabilityBanner()

            binding.etSalesBags.setText("")
            binding.etSalesKgs.setText("")
            binding.etSalesRate.setText("")
            binding.etSalesBagCharge.setText("")
            etSalesWeighCoolie.setText("")
            binding.tvLineAmountPreview.text = "Rs. 0.00"
        }

        binding.btnSaveSalesBill.setOnClickListener {
            val billNo = binding.etSalesBillNo.text.toString().trim()

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

            val cashPaid = etSplitCash.text.toString().toDoubleOrNull() ?: 0.0
            val gpayPaid = etSplitGpay.text.toString().toDoubleOrNull() ?: 0.0
            val cashPayerName = if (cashPaid > 0.0) etCashPayerName.text.toString().trim() else ""
            val totalPaid = cashPaid + gpayPaid

            if (totalPaid > grandTotalBill + 0.01) {
                Toast.makeText(
                    this,
                    String.format(Locale.getDefault(), "Cash + GPay (%.2f) cannot exceed Bill Total (%.2f)!", totalPaid, grandTotalBill),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val creditBal = (grandTotalBill - totalPaid).coerceAtLeast(0.0)
            val isCreditSale = creditBal > 0.005
            val computedPayMode = if (isCreditSale) "Credit" else "Cash"
            val status = if (isCreditSale) {
                if (totalPaid > 0.005) "Partial" else "Unpaid"
            } else {
                "Paid"
            }

            binding.btnSaveSalesBill.isEnabled = false

            val billBitmap = generateBillReceiptBitmap()
            val targetPhone = selectedCustomerPhone
            val targetCustName = selectedCustomerName

            val itemsForDb = arrayListOf<Any>()
            var totalBagsAll = 0
            var totalKgsAll = 0.0
            var totalBagChargesAll = 0.0
            var totalWeighCoolieAll = 0.0
            var firstItemName = ""
            var firstBatchId = ""
            var firstSupplierName = ""

            for (i in 0 until billItemsArray.length()) {
                val obj = billItemsArray.getJSONObject(i)
                val iName = obj.getString("itemName")
                val sDocId = obj.optString("stockDocId", "")
                val bId = obj.optString("batchId", "")
                val sName = obj.optString("supplierName", "")
                val bgs = obj.getInt("bags")
                val kgs = obj.getDouble("netKgs")
                val rt = obj.getDouble("rate")
                val amt = obj.getDouble("amount")
                val bChg = obj.getDouble("bagCharge")
                val wCoolie = obj.optDouble("weighCoolie", 0.0)

                if (i == 0) {
                    firstItemName = iName
                    firstBatchId = bId
                    firstSupplierName = sName
                }
                totalBagsAll += bgs
                totalKgsAll += kgs
                totalBagChargesAll += bChg
                totalWeighCoolieAll += wCoolie

                val itemMap = hashMapOf<String, Any>(
                    "itemName" to iName,
                    "stockDocId" to sDocId,
                    "stockId" to bId,
                    "batchId" to bId,
                    "supplierName" to sName,
                    "bags" to bgs,
                    "netKgs" to kgs,
                    "rate" to rt,
                    "amount" to amt,
                    "bagCharge" to bChg,
                    "weighCoolie" to wCoolie
                )
                itemsForDb.add(itemMap)
            }

            if (isEditMode && editingSaleDocId.isNotEmpty()) {
                for (i in 0 until oldSaleItemsJson.length()) {
                    val oldObj = oldSaleItemsJson.getJSONObject(i)
                    restoreStockToSpecificBatch(
                        oldObj.optString("stockDocId", ""),
                        oldObj.optString("batchId", ""),
                        oldObj.optString("itemName", ""),
                        oldObj.optInt("bags", 0),
                        oldObj.optDouble("netKgs", 0.0)
                    )
                }

                for (i in 0 until billItemsArray.length()) {
                    val obj = billItemsArray.getJSONObject(i)
                    deductStockFromSpecificBatch(
                        obj.optString("stockDocId", ""),
                        obj.optString("batchId", ""),
                        obj.getString("itemName"),
                        obj.getInt("bags"),
                        obj.getDouble("netKgs")
                    )
                }

                if (oldSaleCustomerId.isNotEmpty() && oldSaleCreditBalance > 0.0) {
                    adjustCustomerBalance(oldSaleCustomerId, -oldSaleCreditBalance)
                }
                if (selectedCustomerId.isNotEmpty() && creditBal > 0.0) {
                    adjustCustomerBalance(selectedCustomerId, creditBal)
                }

                val updateData = hashMapOf<String, Any>(
                    "billNo" to billNo,
                    "date" to selectedDateStr,
                    "customerId" to selectedCustomerId,
                    "customerName" to selectedCustomerName,
                    "payMode" to computedPayMode,
                    "cash" to cashPaid,
                    "gpay" to gpayPaid,
                    "credit" to creditBal,
                    "cashPersonName" to cashPayerName,
                    "cashAmount" to cashPaid,
                    "gpayAmount" to gpayPaid,
                    "items" to itemsForDb,
                    "itemName" to firstItemName,
                    "batchId" to firstBatchId,
                    "supplierName" to firstSupplierName,
                    "bags" to totalBagsAll,
                    "netKgs" to totalKgsAll,
                    "totalBagCharges" to totalBagChargesAll,
                    "totalWeighCoolie" to totalWeighCoolieAll,
                    "grandTotal" to grandTotalBill,
                    "paidAmount" to totalPaid,
                    "balanceAmount" to creditBal,
                    "paymentStatus" to status
                )

                db.collection("sales").document(editingSaleDocId).update(updateData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Sales Bill $billNo Updated ($computedPayMode Sales)!", Toast.LENGTH_SHORT).show()
                        editingSaleDocId = ""
                        oldSaleCreditBalance = 0.0
                        while (oldSaleItemsJson.length() > 0) oldSaleItemsJson.remove(0)
                        resetForm()
                        loadSalesForEditFilter()
                        promptWhatsappJpgAfterSave(billNo, targetCustName, targetPhone, billBitmap)
                    }
                    .addOnFailureListener {
                        binding.btnSaveSalesBill.isEnabled = true
                        Toast.makeText(this, "Failed to update bill", Toast.LENGTH_SHORT).show()
                    }
            } else {
                for (i in 0 until billItemsArray.length()) {
                    val obj = billItemsArray.getJSONObject(i)
                    deductStockFromSpecificBatch(
                        obj.optString("stockDocId", ""),
                        obj.optString("batchId", ""),
                        obj.getString("itemName"),
                        obj.getInt("bags"),
                        obj.getDouble("netKgs")
                    )
                }

                val saleId = db.collection("sales").document().id
                val saleData = hashMapOf<String, Any>(
                    "id" to saleId,
                    "billNo" to billNo,
                    "date" to selectedDateStr,
                    "customerId" to selectedCustomerId,
                    "customerName" to selectedCustomerName,
                    "payMode" to computedPayMode,
                    "cash" to cashPaid,
                    "gpay" to gpayPaid,
                    "credit" to creditBal,
                    "cashPersonName" to cashPayerName,
                    "cashAmount" to cashPaid,
                    "gpayAmount" to gpayPaid,
                    "items" to itemsForDb,
                    "itemName" to firstItemName,
                    "batchId" to firstBatchId,
                    "supplierName" to firstSupplierName,
                    "bags" to totalBagsAll,
                    "netKgs" to totalKgsAll,
                    "totalBagCharges" to totalBagChargesAll,
                    "totalWeighCoolie" to totalWeighCoolieAll,
                    "grandTotal" to grandTotalBill,
                    "paidAmount" to totalPaid,
                    "balanceAmount" to creditBal,
                    "paymentStatus" to status,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("sales").document(saleId).set(saleData)
                    .addOnSuccessListener {
                        if (selectedCustomerId.isNotEmpty() && creditBal > 0.0) {
                            adjustCustomerBalance(selectedCustomerId, creditBal)
                        }
                        Toast.makeText(this, "Saved as $computedPayMode Sales ($billNo)!", Toast.LENGTH_SHORT).show()
                        resetForm()
                        promptWhatsappJpgAfterSave(billNo, targetCustName, targetPhone, billBitmap)
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
     * Converts the entire Sales Entry form into a clean bordered TableLayout
     * in exact order:
     * 1. Bill Date (Bill No hidden)
     * 2. Select Item
     * 3. Select Stock
     * 4. Select Name (+ Previous Balance)
     * 5. Coconut -> Kgs, Rate, Amount | Other than Coconut -> Bags, Kgs, Rate, Bag Rate, Weigh Coolie, Total Amount
     * 6. Pay Mode (Cash | GPay | Credit in single line + Cash Given By)
     * Followed by + ADD ITEM TO BILL button right below the table.
     */
    private fun buildAllInputsAsTableLayout() {
        val addBtnParent = binding.btnAddSalesItem.parent as? ViewGroup ?: return

        binding.etSalesBillNo.visibility = View.GONE
        binding.spSalesPayMode.visibility = View.GONE

        val oldDateParent = binding.etSalesDate.parent as? ViewGroup
        val oldBillParent = binding.etSalesBillNo.parent as? ViewGroup
        val oldCustParent = binding.etSalesCustomer.parent as? ViewGroup
        val oldPayParent = binding.spSalesPayMode.parent as? ViewGroup

        fun detachView(v: View) {
            (v.parent as? ViewGroup)?.removeView(v)
            v.visibility = View.VISIBLE
        }

        detachView(binding.etSalesDate)
        detachView(binding.spSalesItem)
        detachView(binding.etSalesCustomer)
        detachView(binding.tvSalesCustBalance)
        detachView(binding.etSalesBags)
        detachView(binding.etSalesKgs)
        detachView(binding.etSalesRate)
        detachView(binding.etSalesBagCharge)
        detachView(binding.tvLineAmountPreview)
        detachView(binding.btnAddSalesItem)

        listOfNotNull(oldDateParent, oldBillParent, oldCustParent, oldPayParent).forEach { p ->
            if (p != addBtnParent && p.id != binding.root.id) {
                p.visibility = View.GONE
                val gp = p.parent as? ViewGroup
                if (gp != null && gp != addBtnParent && gp.id != binding.root.id) {
                    gp.visibility = View.GONE
                }
            }
        }

        for (i in 0 until addBtnParent.childCount) {
            val child = addBtnParent.getChildAt(i)
            val hasModeBtn = (child as? ViewGroup)?.findViewById<View>(binding.btnModeNewSales.id) != null
            if (!hasModeBtn &&
                child.id != binding.tvSalesHeader.id &&
                child.id != binding.layoutEditSalesPanel.id &&
                child.id != binding.btnImportSalesCsv.id &&
                child.id != binding.tlSalesItemsGrid.id &&
                child.id != binding.tvSalesGrandTotal.id &&
                child.id != binding.btnSaveSalesBill.id
            ) {
                child.visibility = View.GONE
            }
        }

        spSalesStockBatch = Spinner(this)
        spSalesStockBatch.setBackgroundResource(android.R.drawable.btn_dropdown)
        batchSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, batchDisplayList)
        batchSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spSalesStockBatch.adapter = batchSpinnerAdapter

        tvSelectedBatchAvail = TextView(this)
        tvSelectedBatchAvail.text = "Loading available stock..."
        tvSelectedBatchAvail.textSize = 12f
        tvSelectedBatchAvail.setTypeface(null, Typeface.BOLD)
        tvSelectedBatchAvail.setTextColor(Color.parseColor("#1B5E20"))

        etSalesWeighCoolie = EditText(this)
        etSalesWeighCoolie.hint = "0.00"
        etSalesWeighCoolie.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        etSalesWeighCoolie.setBackgroundResource(android.R.drawable.edit_text)

        fun buildInlinePayBox(hintLabel: String, readOnly: Boolean): EditText {
            val et = EditText(this)
            et.hint = hintLabel
            et.textSize = 13f
            et.setTypeface(null, Typeface.BOLD)
            et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            et.setBackgroundResource(android.R.drawable.edit_text)
            et.setSingleLine(true)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(2, 0, 2, 0)
            et.layoutParams = lp
            if (readOnly) {
                et.isFocusable = false
                et.isClickable = false
                et.setTextColor(Color.parseColor("#D84315"))
                et.setText("Cr: 0.00")
            }
            return et
        }

        etSplitCash = buildInlinePayBox("Cash", false)
        etSplitGpay = buildInlinePayBox("GPay", false)
        etSplitCredit = buildInlinePayBox("Credit", true)

        etCashPayerName = EditText(this)
        etCashPayerName.hint = "Enter cash payer name..."
        etCashPayerName.textSize = 13f
        etCashPayerName.setSingleLine(true)
        etCashPayerName.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        etCashPayerName.setBackgroundResource(android.R.drawable.edit_text)

        val splitWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                recalculateSplitCredit()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        etSplitCash.addTextChangedListener(splitWatcher)
        etSplitGpay.addTextChangedListener(splitWatcher)

        val formTable = TableLayout(this)
        formTable.setBackgroundColor(Color.parseColor("#BDBDBD"))
        formTable.setPadding(1, 1, 1, 1)
        formTable.setColumnStretchable(1, true)
        val tableLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        tableLp.setMargins(0, 6, 0, 10)
        formTable.layoutParams = tableLp

        fun addFormRow(labelText: String, inputView: View, labelBgHex: String = "#F1F8E9"): TableRow {
            val tr = TableRow(this)
            tr.setBackgroundColor(Color.parseColor("#BDBDBD"))

            val lbl = TextView(this)
            lbl.text = labelText
            lbl.textSize = 13f
            lbl.setTypeface(null, Typeface.BOLD)
            lbl.setTextColor(Color.parseColor("#1B5E20"))
            lbl.setBackgroundColor(Color.parseColor(labelBgHex))
            lbl.gravity = Gravity.CENTER_VERTICAL
            lbl.setPadding(14, 14, 14, 14)
            val lLp = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.MATCH_PARENT)
            lLp.setMargins(1, 1, 1, 1)
            lbl.layoutParams = lLp

            val rightCell = LinearLayout(this)
            rightCell.orientation = LinearLayout.VERTICAL
            rightCell.setBackgroundColor(Color.WHITE)
            rightCell.gravity = Gravity.CENTER_VERTICAL
            rightCell.setPadding(10, 6, 10, 6)
            val rLp = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 1f)
            rLp.setMargins(1, 1, 1, 1)
            rightCell.layoutParams = rLp

            inputView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rightCell.addView(inputView)

            tr.addView(lbl)
            tr.addView(rightCell)
            formTable.addView(tr)
            return tr
        }

        // 1. Bill Date
        addFormRow("1. Bill Date", binding.etSalesDate)

        // 2. Select Item
        addFormRow("2. Select Item", binding.spSalesItem)

        // 3. Select Stock
        val stockCellBox = LinearLayout(this)
        stockCellBox.orientation = LinearLayout.VERTICAL
        spSalesStockBatch.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (42 * resources.displayMetrics.density).toInt()
        )
        tvSelectedBatchAvail.setPadding(4, 2, 4, 2)
        stockCellBox.addView(spSalesStockBatch)
        stockCellBox.addView(tvSelectedBatchAvail)
        addFormRow("3. Select Stock", stockCellBox)

        // 4. Select Name
        val custCellBox = LinearLayout(this)
        custCellBox.orientation = LinearLayout.VERTICAL
        binding.etSalesCustomer.hint = "Tap to select customer..."
        binding.etSalesCustomer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        binding.tvSalesCustBalance.textSize = 12f
        binding.tvSalesCustBalance.setPadding(4, 2, 4, 2)
        custCellBox.addView(binding.etSalesCustomer)
        custCellBox.addView(binding.tvSalesCustBalance)
        addFormRow("4. Select Name", custCellBox)

        // 5A. Bags (Non-Coconut only)
        binding.etSalesBags.hint = "Enter Bags"
        rowBags = addFormRow("Bags", binding.etSalesBags, "#FFFDE7")

        // 5B. Net Kgs
        binding.etSalesKgs.hint = "Enter Kgs"
        addFormRow("Kgs", binding.etSalesKgs)

        // 5C. Rate
        binding.etSalesRate.hint = "Enter Rate"
        addFormRow("Rate (Rs)", binding.etSalesRate)

        // 5D. Bag Rate (Non-Coconut only)
        binding.etSalesBagCharge.hint = "Enter Bag Rate"
        rowBagRate = addFormRow("Bag Rate (Rs)", binding.etSalesBagCharge, "#FFFDE7")

        // 5E. Weigh Coolie (Non-Coconut only)
        rowWeighCoolie = addFormRow("Weigh Coolie", etSalesWeighCoolie, "#FFFDE7")

        // 5F. Amount Preview
        binding.tvLineAmountPreview.textSize = 15f
        binding.tvLineAmountPreview.setTypeface(null, Typeface.BOLD)
        binding.tvLineAmountPreview.setTextColor(Color.parseColor("#D84315"))
        binding.tvLineAmountPreview.setPadding(6, 10, 6, 10)
        addFormRow("Amount", binding.tvLineAmountPreview, "#FFF9C4")

        // 6. Pay Mode (Single line Cash | GPay | Credit)
        val paySingleLine = LinearLayout(this)
        paySingleLine.orientation = LinearLayout.HORIZONTAL
        paySingleLine.weightSum = 3f
        paySingleLine.addView(etSplitCash)
        paySingleLine.addView(etSplitGpay)
        paySingleLine.addView(etSplitCredit)
        addFormRow("6. Pay Mode", paySingleLine, "#E3F2FD")

        // 6B. Cash Given By (Visible only when Cash > 0)
        rowCashPayer = addFormRow("Cash Given By", etCashPayerName, "#E3F2FD")
        rowCashPayer.visibility = View.GONE

        val gridIdx = addBtnParent.indexOfChild(binding.tlSalesItemsGrid)
        val tablePos = if (gridIdx >= 0) gridIdx else addBtnParent.childCount
        addBtnParent.addView(formTable, tablePos)

        val addBtnLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addBtnLp.setMargins(0, 6, 0, 12)
        binding.btnAddSalesItem.layoutParams = addBtnLp
        addBtnParent.addView(binding.btnAddSalesItem, tablePos + 1)
    }

    private fun recalculateSplitCredit() {
        val cashVal = etSplitCash.text.toString().toDoubleOrNull() ?: 0.0
        val gpayVal = etSplitGpay.text.toString().toDoubleOrNull() ?: 0.0
        val paidSum = cashVal + gpayVal

        val liveLineAmt = run {
            val itemName = binding.spSalesItem.selectedItem?.toString() ?: "Coconut"
            val isCoconut = itemName.equals("Coconut", ignoreCase = true)
            val kgs = binding.etSalesKgs.text.toString().toDoubleOrNull() ?: 0.0
            val rate = binding.etSalesRate.text.toString().toDoubleOrNull() ?: 0.0
            val bagChg = if (isCoconut) 0.0 else (binding.etSalesBagCharge.text.toString().toDoubleOrNull() ?: 0.0)
            val wCoolie = if (isCoconut) 0.0 else (etSalesWeighCoolie.text.toString().toDoubleOrNull() ?: 0.0)
            (kgs * rate) + bagChg + wCoolie
        }

        val effectiveTarget = if (grandTotalBill > 0.0) grandTotalBill else liveLineAmt
        val creditBal = (effectiveTarget - paidSum).coerceAtLeast(0.0)

        rowCashPayer.visibility = if (cashVal > 0.005) View.VISIBLE else View.GONE
        etSplitCredit.setText(String.format(Locale.getDefault(), "Cr: %.2f", creditBal))
    }

    private fun injectWhatsappButtonAfterSave() {
        val parent = binding.btnSaveSalesBill.parent as? ViewGroup ?: return
        val insertIdx = parent.indexOfChild(binding.btnSaveSalesBill) + 1

        btnSendWhatsappBill = Button(this)
        btnSendWhatsappBill.text = "SEND JPG BILL (3-INCH THERMAL / WHATSAPP)"
        btnSendWhatsappBill.setBackgroundColor(Color.parseColor("#25D366"))
        btnSendWhatsappBill.setTextColor(Color.WHITE)
        btnSendWhatsappBill.setTypeface(null, Typeface.BOLD)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 8, 0, 12)
        btnSendWhatsappBill.layoutParams = lp

        btnSendWhatsappBill.setOnClickListener {
            if (selectedCustomerName.isEmpty()) {
                Toast.makeText(this, "Please select a Customer first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (billItemsArray.length() == 0) {
                Toast.makeText(this, "Add at least one item to send JPG bill", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val bmp = generateBillReceiptBitmap()
            val billNo = binding.etSalesBillNo.text.toString().trim().ifEmpty { "BILL" }
            shareBitmapJpgToWhatsapp(bmp, billNo, selectedCustomerPhone)
        }

        parent.addView(btnSendWhatsappBill, insertIdx)
    }

    private fun generateBillReceiptBitmap(): Bitmap {
        val width = 576
        val pad = 18f
        val itemCount = billItemsArray.length().coerceAtLeast(1)

        val minPortraitHeight = 860
        val estimatedHeight = 610 + (itemCount * 38)
        val height = maxOf(minPortraitHeight, estimatedHeight)

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val boldType = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val regType = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val italicBold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        val italicReg = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)

        val pTitle = Paint().apply {
            color = Color.BLACK
            textSize = 31f
            typeface = boldType
            textAlign = Paint.Align.CENTER
        }
        val pSub = Paint().apply {
            color = Color.BLACK
            textSize = 17.5f
            typeface = boldType
            textAlign = Paint.Align.CENTER
        }
        val pPhoneLeft = Paint().apply {
            color = Color.BLACK
            textSize = 18.5f
            typeface = boldType
            textAlign = Paint.Align.LEFT
        }
        val pPhoneRight = Paint().apply {
            color = Color.BLACK
            textSize = 18.5f
            typeface = boldType
            textAlign = Paint.Align.RIGHT
        }
        val pBodyBold = Paint().apply {
            color = Color.BLACK
            textSize = 17.5f
            typeface = boldType
            textAlign = Paint.Align.LEFT
        }
        val pBodyReg = Paint().apply {
            color = Color.BLACK
            textSize = 17f
            typeface = regType
            textAlign = Paint.Align.LEFT
        }
        val pBodyRightReg = Paint().apply {
            color = Color.BLACK
            textSize = 17f
            typeface = regType
            textAlign = Paint.Align.RIGHT
        }
        val pBodyRightBold = Paint().apply {
            color = Color.BLACK
            textSize = 17.5f
            typeface = boldType
            textAlign = Paint.Align.RIGHT
        }
        val pDash = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(8f, 5f), 0f)
        }
        val pSolid = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        val cx = width / 2f
        var y = 42f

        canvas.drawText("THIRUVENI COCONUTS", cx, y, pTitle)
        y += 25f
        canvas.drawText("Shop No B98, Periyar Vegetable Market", cx, y, pSub)
        y += 22f
        canvas.drawText("Koyambedu , Chennai - 107", cx, y, pSub)
        y += 28f
        canvas.drawText("9841666651", pad, y, pPhoneLeft)
        canvas.drawText("9840457613", width - pad, y, pPhoneRight)

        y += 14f
        canvas.drawLine(pad, y, width - pad, y, pDash)

        y += 28f
        val custDisplay = selectedCustomerName.ifEmpty { "Cash Customer" }.take(22)
        canvas.drawText("Name : $custDisplay", pad, y, pBodyBold)
        canvas.drawText("Date : $selectedDateStr", width - pad, y, pBodyRightBold)
        canvas.drawLine(pad + 60f, y + 5f, 345f, y + 5f, pSolid)
        canvas.drawLine(width - 145f, y + 5f, width - pad, y + 5f, pSolid)

        val cashPaid = etSplitCash.text.toString().toDoubleOrNull() ?: 0.0
        val payerName = if (cashPaid > 0.0) etCashPayerName.text.toString().trim() else ""
        if (payerName.isNotEmpty()) {
            y += 24f
            canvas.drawText("Cash By : $payerName", pad, y, pBodyReg)
        }

        y += 16f
        canvas.drawLine(pad, y, width - pad, y, pDash)

        y += 26f
        val colItemX = pad
        val colQtyX = 335f
        val colRateX = 435f
        val colAmtX = width - pad

        canvas.drawText("Item", colItemX, y, pBodyBold)
        canvas.drawText("Qty (Kgs)", colQtyX, y, pBodyRightBold)
        canvas.drawText("Rate", colRateX, y, pBodyRightBold)
        canvas.drawText("Amount", colAmtX, y, pBodyRightBold)

        y += 12f
        canvas.drawLine(pad, y, width - pad, y, pDash)

        var calcBillTotal = 0.0
        for (i in 0 until billItemsArray.length()) {
            y += 30f
            val obj = billItemsArray.getJSONObject(i)
            val iName = obj.getString("itemName")
            val bgs = obj.getInt("bags")
            val kgs = obj.getDouble("netKgs")
            val rt = obj.getDouble("rate")
            val rowTot = obj.getDouble("rowTotal")
            calcBillTotal += rowTot

            val label = if (!iName.equals("Coconut", ignoreCase = true) && bgs > 0) {
                "$iName ($bgs Bg)"
            } else {
                iName
            }
            val effectiveRate = if (kgs > 0.0) (rowTot / kgs) else rt

            canvas.drawText(label.take(20), colItemX, y, pBodyReg)
            canvas.drawText(String.format(Locale.getDefault(), "%.2f", kgs), colQtyX, y, pBodyRightReg)
            canvas.drawText(String.format(Locale.getDefault(), "%.2f", effectiveRate), colRateX, y, pBodyRightReg)
            canvas.drawText(String.format(Locale.getDefault(), "%.2f", rowTot), colAmtX, y, pBodyRightReg)
        }

        y += 22f
        canvas.drawLine(pad, y, width - pad, y, pDash)

        val gpayPaid = etSplitGpay.text.toString().toDoubleOrNull() ?: 0.0
        val receivedAmt = cashPaid + gpayPaid
        val opBal = selectedCustomerBalance
        val docTotal = opBal + calcBillTotal
        val discountAmt = 0.0
        val closingBal = (docTotal - receivedAmt - discountAmt).coerceAtLeast(0.0)

        val labelX = 375f
        val colonX = 392f
        val valX = width - pad

        val pLabelBold = Paint().apply {
            color = Color.BLACK
            textSize = 17.5f
            typeface = boldType
            textAlign = Paint.Align.RIGHT
        }
        val pLabelReg = Paint().apply {
            color = Color.BLACK
            textSize = 17f
            typeface = regType
            textAlign = Paint.Align.RIGHT
        }

        y += 28f
        canvas.drawText("Total", labelX, y, pLabelBold)
        canvas.drawText(":", colonX, y, pLabelBold)
        canvas.drawText(String.format(Locale.getDefault(), "Rs. %,.2f", calcBillTotal), valX, y, pBodyRightBold)

        y += 26f
        canvas.drawText("Op. Bal", labelX, y, pLabelReg)
        canvas.drawText(":", colonX, y, pLabelReg)
        canvas.drawText(String.format(Locale.getDefault(), "Rs. %,.2f", opBal), valX, y, pBodyRightReg)

        y += 10f
        canvas.drawLine(210f, y, width - pad, y, pSolid)

        y += 26f
        canvas.drawText("Doc Total", labelX, y, pLabelBold)
        canvas.drawText(":", colonX, y, pLabelBold)
        canvas.drawText(String.format(Locale.getDefault(), "Rs. %,.2f", docTotal), valX, y, pBodyRightBold)

        y += 14f
        canvas.drawLine(pad, y, width - pad, y, pDash)

        y += 26f
        val recBreakdown = if (cashPaid > 0.0 && gpayPaid > 0.0) {
            "Rec (C:${cashPaid.toInt()}+G:${gpayPaid.toInt()})"
        } else if (gpayPaid > 0.0) {
            "Received (GPay)"
        } else {
            "Received Amount"
        }
        canvas.drawText(recBreakdown, labelX, y, pLabelReg)
        canvas.drawText(":", colonX, y, pLabelReg)
        canvas.drawText(String.format(Locale.getDefault(), "Rs. %,.2f", receivedAmt), valX, y, pBodyRightReg)

        y += 25f
        canvas.drawText("Discount", labelX, y, pLabelReg)
        canvas.drawText(":", colonX, y, pLabelReg)
        canvas.drawText(String.format(Locale.getDefault(), "Rs. %,.2f", discountAmt), valX, y, pBodyRightReg)

        y += 10f
        canvas.drawLine(pad, y, width - pad, y, pSolid)

        y += 28f
        canvas.drawText("Closing Bal", labelX, y, pLabelBold)
        canvas.drawText(":", colonX, y, pLabelBold)
        canvas.drawText(String.format(Locale.getDefault(), "Rs. %,.2f", closingBal), valX, y, pBodyRightBold)

        y += 14f
        canvas.drawLine(pad, y, width - pad, y, pDash)

        y += 30f
        val pGpay = Paint().apply {
            color = Color.BLACK
            textSize = 20.5f
            typeface = boldType
            textAlign = Paint.Align.CENTER
        }
        val pAcc = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            typeface = boldType
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("GPay No : 9841666651", cx, y, pGpay)
        y += 25f
        canvas.drawText("A/c No : ATE TOMATO WHOLESALE MERCHANT", cx, y, pAcc)

        y += 36f
        val pThank = Paint().apply {
            color = Color.BLACK
            textSize = 25f
            typeface = italicBold
            textAlign = Paint.Align.CENTER
        }
        val pVisit = Paint().apply {
            color = Color.BLACK
            textSize = 20f
            typeface = italicReg
            textAlign = Paint.Align.CENTER
        }
        canvas.drawLine(pad, y - 8f, cx - 98f, y - 8f, pSolid)
        canvas.drawText("Thank You !", cx, y, pThank)
        canvas.drawLine(cx + 98f, y - 8f, width - pad, y - 8f, pSolid)

        y += 25f
        canvas.drawText("Visit Again...", cx, y, pVisit)

        val finalHeight = (y + 30f).toInt().coerceAtLeast(840).coerceAtMost(height)
        return Bitmap.createBitmap(bmp, 0, 0, width, finalHeight)
    }

    private fun promptWhatsappJpgAfterSave(billNo: String, custName: String, phone: String, billBitmap: Bitmap) {
        AlertDialog.Builder(this)
            .setTitle("Bill $billNo Saved")
            .setMessage("Send 3-inch JPG bill receipt to $custName on WhatsApp / Printer now?")
            .setPositiveButton("SEND JPG BILL") { _, _ ->
                shareBitmapJpgToWhatsapp(billBitmap, billNo, phone)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun shareBitmapJpgToWhatsapp(bitmap: Bitmap, billNo: String, rawPhone: String) {
        try {
            val safeBill = billNo.replace("[^A-Za-z0-9_-]".toRegex(), "_")
            val fileName = "Bill_${safeBill}_${System.currentTimeMillis()}.jpg"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VeggieBills")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = contentResolver
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (imageUri == null) {
                Toast.makeText(this, "Unable to create JPG file", Toast.LENGTH_SHORT).show()
                return
            }

            resolver.openOutputStream(imageUri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, values, null, null)
            }

            val digitsOnly = rawPhone.replace("[^0-9]".toRegex(), "")
            val formattedPhone = when {
                digitsOnly.length == 10 -> "91$digitsOnly"
                digitsOnly.length == 11 && digitsOnly.startsWith("0") -> "91${digitsOnly.substring(1)}"
                digitsOnly.length >= 12 -> digitsOnly
                else -> ""
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                if (formattedPhone.isNotEmpty()) {
                    putExtra("jid", "$formattedPhone@s.whatsapp.net")
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                shareIntent.setPackage("com.whatsapp")
                startActivity(shareIntent)
            } catch (e: Exception) {
                try {
                    shareIntent.setPackage("com.whatsapp.w4b")
                    startActivity(shareIntent)
                } catch (ex: Exception) {
                    shareIntent.setPackage(null)
                    startActivity(Intent.createChooser(shareIntent, "Send / Print 3-Inch Bill JPG"))
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing JPG bill: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadAvailableStockBatches() {
        db.collection("stock_batches").get().addOnSuccessListener { snap ->
            while (allStockBatchesJson.length() > 0) allStockBatchesJson.remove(0)

            val sortedDocs = snap.documents.sortedBy { doc -> doc.getLong("timestamp") ?: 0L }
            for (doc in sortedDocs) {
                val iName = (doc.getString("itemName") ?: "").trim()
                val availBags = (doc.getLong("availableBags") ?: 0L).toInt()
                val availKgs = doc.getDouble("availableKgs") ?: 0.0

                if (iName.isNotEmpty() && (availKgs > 0.001 || availBags > 0)) {
                    val obj = JSONObject()
                    obj.put("docId", doc.id)
                    obj.put("batchId", doc.getString("batchId") ?: doc.id.take(6))
                    obj.put("supplierName", doc.getString("supplierName") ?: "Unknown Supplier")
                    obj.put("itemName", iName)
                    obj.put("availableBags", availBags)
                    obj.put("availableKgs", availKgs)
                    obj.put("purchaseRate", doc.getDouble("purchaseRate") ?: doc.getDouble("ratePerKg") ?: 0.0)
                    allStockBatchesJson.put(obj)
                }
            }

            val currentItem = binding.spSalesItem.selectedItem?.toString() ?: "Coconut"
            filterBatchesForSelectedItem(currentItem)
        }
    }

    private fun filterBatchesForSelectedItem(itemName: String) {
        while (filteredBatchesJson.length() > 0) filteredBatchesJson.remove(0)
        batchDisplayList.clear()

        val isCoconut = itemName.equals("Coconut", ignoreCase = true)

        for (i in 0 until allStockBatchesJson.length()) {
            val obj = allStockBatchesJson.getJSONObject(i)
            val bItem = obj.optString("itemName", "")
            if (!bItem.equals(itemName, ignoreCase = true)) continue

            val sup = obj.optString("supplierName", "Unknown")
            val bId = obj.optString("batchId", "")
            val bags = obj.optInt("availableBags", 0)
            val kgs = obj.optDouble("availableKgs", 0.0)

            filteredBatchesJson.put(obj)
            val label = if (isCoconut) {
                String.format(Locale.getDefault(), "%s [%s] — %.2f Kgs", sup, bId, kgs)
            } else {
                String.format(Locale.getDefault(), "%s [%s] — %d Bags | %.2f Kgs", sup, bId, bags, kgs)
            }
            batchDisplayList.add(label)
        }

        if (batchDisplayList.isEmpty()) {
            batchDisplayList.add("No $itemName stock available")
        }

        batchSpinnerAdapter.notifyDataSetChanged()
        spSalesStockBatch.setSelection(0)
        updateSelectedBatchAvailabilityBanner()
    }

    private fun updateSelectedBatchAvailabilityBanner() {
        val itemName = binding.spSalesItem.selectedItem?.toString() ?: "Coconut"
        val isCoconut = itemName.equals("Coconut", ignoreCase = true)
        val pos = spSalesStockBatch.selectedItemPosition

        if (filteredBatchesJson.length() == 0 || pos < 0 || pos >= filteredBatchesJson.length()) {
            tvSelectedBatchAvail.text = "Available Stock: 0.00 Kgs (Purchase entry required)"
            tvSelectedBatchAvail.setTextColor(Color.RED)
            return
        }

        val obj = filteredBatchesJson.getJSONObject(pos)
        val stockDocId = obj.optString("docId", "")
        val sup = obj.optString("supplierName", "")
        val bId = obj.optString("batchId", "")
        val availBags = obj.optInt("availableBags", 0)
        val availKgs = obj.optDouble("availableKgs", 0.0)
        val purRate = obj.optDouble("purchaseRate", 0.0)

        var usedBags = 0
        var usedKgs = 0.0
        for (k in 0 until billItemsArray.length()) {
            val row = billItemsArray.getJSONObject(k)
            if (row.optString("stockDocId", "") == stockDocId) {
                usedBags += row.optInt("bags", 0)
                usedKgs += row.optDouble("netKgs", 0.0)
            }
        }

        val remBags = (availBags - usedBags).coerceAtLeast(0)
        val remKgs = (availKgs - usedKgs).coerceAtLeast(0.0)

        tvSelectedBatchAvail.setTextColor(Color.parseColor("#1B5E20"))
        val rateHint = if (purRate > 0.0) String.format(Locale.getDefault(), " | Pur.Rate: %.2f", purRate) else ""
        tvSelectedBatchAvail.text = if (isCoconut) {
            String.format(Locale.getDefault(), "Supplier: %s (%s) | Avail: %.2f Kgs%s", sup, bId, remKgs, rateHint)
        } else {
            String.format(Locale.getDefault(), "Supplier: %s (%s) | Avail: %d Bags / %.2f Kgs%s", sup, bId, remBags, remKgs, rateHint)
        }
    }

    private fun onSalesItemChanged(itemName: String) {
        val isCoconut = itemName.equals("Coconut", ignoreCase = true)
        val isOnion = itemName.equals("Onion", ignoreCase = true)

        filterBatchesForSelectedItem(itemName)

        if (isCoconut) {
            rowBags.visibility = View.GONE
            rowBagRate.visibility = View.GONE
            rowWeighCoolie.visibility = View.GONE
            binding.etSalesBags.setText("")
            binding.etSalesBagCharge.setText("")
            etSalesWeighCoolie.setText("")
        } else {
            rowBags.visibility = View.VISIBLE
            rowBagRate.visibility = View.VISIBLE
            rowWeighCoolie.visibility = View.VISIBLE
            if (isOnion) {
                val bags = binding.etSalesBags.text.toString().toIntOrNull() ?: 0
                if (bags > 0) {
                    binding.etSalesBagCharge.setText(String.format(Locale.getDefault(), "%.2f", bags * 10.0))
                }
            }
        }

        updateLineAmountPreview()
    }

    private fun updateLineAmountPreview() {
        val itemName = binding.spSalesItem.selectedItem?.toString() ?: "Coconut"
        val isCoconut = itemName.equals("Coconut", ignoreCase = true)

        val kgs = binding.etSalesKgs.text.toString().toDoubleOrNull() ?: 0.0
        val rate = binding.etSalesRate.text.toString().toDoubleOrNull() ?: 0.0
        val bagChg = if (isCoconut) 0.0 else (binding.etSalesBagCharge.text.toString().toDoubleOrNull() ?: 0.0)
        val weighCoolie = if (isCoconut) 0.0 else (etSalesWeighCoolie.text.toString().toDoubleOrNull() ?: 0.0)

        val lineTot = (kgs * rate) + bagChg + weighCoolie
        binding.tvLineAmountPreview.text = String.format(Locale.getDefault(), "Rs. %.2f", lineTot)
        recalculateSplitCredit()
    }

    private fun switchToNewMode() {
        isEditMode = false
        editingSaleDocId = ""
        oldSaleCreditBalance = 0.0
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
        oldSaleCreditBalance = 0.0
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
            .setMessage("Are you sure you want to delete $bNo? Stock will be returned to its Supplier batch and Customer balance will be restored.")
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
        val storedBal = (data["credit"] as? Number)?.toDouble()
            ?: (data["balanceAmount"] as? Number)?.toDouble()
            ?: (if (pMode.equals("Credit", ignoreCase = true)) (grandTot - paidAmt).coerceAtLeast(0.0) else 0.0)
        val rawItems = data["items"] as? List<*>

        db.collection("sales").document(docId).delete()
            .addOnSuccessListener {
                if (rawItems != null) {
                    for (entry in rawItems) {
                        val m = entry as? Map<*, *> ?: continue
                        val sDocId = m["stockDocId"]?.toString() ?: ""
                        val bId = m["batchId"]?.toString() ?: m["stockId"]?.toString() ?: ""
                        val iName = m["itemName"]?.toString() ?: ""
                        val bgs = (m["bags"] as? Number)?.toInt() ?: 0
                        val kgs = (m["netKgs"] as? Number)?.toDouble() ?: 0.0
                        restoreStockToSpecificBatch(sDocId, bId, iName, bgs, kgs)
                    }
                } else {
                    val bId = data["batchId"]?.toString() ?: data["stockId"]?.toString() ?: ""
                    val iName = data["itemName"]?.toString() ?: ""
                    val bgs = (data["bags"] as? Number)?.toInt() ?: 0
                    val kgs = (data["netKgs"] as? Number)?.toDouble() ?: 0.0
                    restoreStockToSpecificBatch("", bId, iName, bgs, kgs)
                }

                if (custId.isNotEmpty() && storedBal > 0.0) {
                    adjustCustomerBalance(custId, -storedBal)
                }

                if (editingSaleDocId == docId) {
                    editingSaleDocId = ""
                    resetForm()
                }
                Toast.makeText(this, "Sales Bill Deleted & Supplier Stock Restored!", Toast.LENGTH_SHORT).show()
                loadAvailableStockBatches()
                loadSalesForEditFilter()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete bill", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateFormFromSaleDoc(docId: String, data: Map<String, Any>) {
        editingSaleDocId = docId
        oldSaleCustomerId = data["customerId"]?.toString() ?: ""
        val oldPayMode = data["payMode"]?.toString() ?: "Credit"
        val oldGrandTotal = (data["grandTotal"] as? Number)?.toDouble() ?: 0.0
        val oldPaidAmount = (data["paidAmount"] as? Number)?.toDouble() ?: 0.0

        val savedCash = (data["cash"] as? Number)?.toDouble()
            ?: (data["cashAmount"] as? Number)?.toDouble()
            ?: (if (oldPayMode.equals("Cash", ignoreCase = true)) oldPaidAmount else 0.0)
        val savedGpay = (data["gpay"] as? Number)?.toDouble()
            ?: (data["gpayAmount"] as? Number)?.toDouble()
            ?: (if (oldPayMode.contains("GPay", ignoreCase = true) || oldPayMode.contains("UPI", ignoreCase = true)) oldPaidAmount else 0.0)
        val savedPayerName = data["cashPersonName"]?.toString() ?: ""

        oldSaleCreditBalance = (data["credit"] as? Number)?.toDouble()
            ?: (data["balanceAmount"] as? Number)?.toDouble()
            ?: (if (oldPayMode.equals("Credit", ignoreCase = true)) (oldGrandTotal - oldPaidAmount).coerceAtLeast(0.0) else 0.0)

        val bNo = data["billNo"]?.toString() ?: ""
        val cName = data["customerName"]?.toString() ?: ""
        val bDate = data["date"]?.toString() ?: editFilterDateStr

        selectedDateStr = bDate
        selectedCustomerId = oldSaleCustomerId
        selectedCustomerName = cName

        val foundCust = customerList.find { c -> c.id == oldSaleCustomerId || c.name.equals(cName, ignoreCase = true) }
        selectedCustomerBalance = foundCust?.balance ?: 0.0
        selectedCustomerPhone = foundCust?.phone ?: ""
        if (selectedCustomerId.isEmpty() && foundCust != null) {
            selectedCustomerId = foundCust.id
        }

        binding.etSalesDate.setText(bDate)
        binding.etSalesBillNo.setText(bNo)
        binding.etSalesCustomer.setText(cName)
        binding.tvSalesCustBalance.text = String.format(Locale.getDefault(), "Previous Balance: Rs. %.2f", selectedCustomerBalance)

        etSplitCash.setText(if (savedCash > 0.0) String.format(Locale.getDefault(), "%.2f", savedCash) else "")
        etSplitGpay.setText(if (savedGpay > 0.0) String.format(Locale.getDefault(), "%.2f", savedGpay) else "")
        etCashPayerName.setText(savedPayerName)

        while (billItemsArray.length() > 0) billItemsArray.remove(0)
        while (oldSaleItemsJson.length() > 0) oldSaleItemsJson.remove(0)

        val rawItems = data["items"] as? List<*>
        if (rawItems != null && rawItems.isNotEmpty()) {
            for (entry in rawItems) {
                val m = entry as? Map<*, *> ?: continue
                val iName = m["itemName"]?.toString() ?: "Coconut"
                val sDocId = m["stockDocId"]?.toString() ?: ""
                val bId = m["batchId"]?.toString() ?: m["stockId"]?.toString() ?: ""
                val sName = m["supplierName"]?.toString() ?: ""
                val bgs = (m["bags"] as? Number)?.toInt() ?: 0
                val kgs = (m["netKgs"] as? Number)?.toDouble() ?: 0.0
                val rt = (m["rate"] as? Number)?.toDouble() ?: 0.0
                val amt = (m["amount"] as? Number)?.toDouble() ?: (kgs * rt)
                val bChg = (m["bagCharge"] as? Number)?.toDouble() ?: 0.0
                val wCoolie = (m["weighCoolie"] as? Number)?.toDouble() ?: 0.0

                val obj = JSONObject()
                obj.put("itemName", iName)
                obj.put("stockDocId", sDocId)
                obj.put("batchId", bId)
                obj.put("supplierName", sName)
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
            val bId = data["batchId"]?.toString() ?: data["stockId"]?.toString() ?: ""
            val sName = data["supplierName"]?.toString() ?: ""
            val bgs = (data["bags"] as? Number)?.toInt() ?: 0
            val kgs = (data["netKgs"] as? Number)?.toDouble() ?: 0.0
            val rt = if (kgs > 0.0) oldGrandTotal / kgs else 0.0

            val obj = JSONObject()
            obj.put("itemName", iName)
            obj.put("stockDocId", "")
            obj.put("batchId", bId)
            obj.put("supplierName", sName)
            obj.put("bags", bgs)
            obj.put("netKgs", kgs)
            obj.put("rate", rt)
            obj.put("amount", oldGrandTotal)
            obj.put("bagCharge", 0.0)
            obj.put("weighCoolie", 0.0)
            obj.put("rowTotal", oldGrandTotal)
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

    private fun deductStockFromSpecificBatch(
        stockDocId: String,
        batchId: String,
        itemName: String,
        bagsToDeduct: Int,
        kgsToDeduct: Double
    ) {
        if (stockDocId.isNotEmpty()) {
            val batchRef = db.collection("stock_batches").document(stockDocId)
            db.runTransaction { tr ->
                val snap = tr.get(batchRef)
                if (snap.exists()) {
                    val availBags = (snap.getLong("availableBags") ?: 0L).toInt()
                    val availKgs = snap.getDouble("availableKgs") ?: 0.0
                    val newBags = (availBags - bagsToDeduct).coerceAtLeast(0)
                    val newKgs = (availKgs - kgsToDeduct).coerceAtLeast(0.0)
                    tr.update(
                        batchRef,
                        hashMapOf<String, Any>(
                            "availableBags" to newBags,
                            "availableKgs" to newKgs
                        )
                    )
                }
            }.addOnCompleteListener {
                loadAvailableStockBatches()
            }
            return
        }

        deductStockFifo(itemName, bagsToDeduct, kgsToDeduct)
    }

    private fun restoreStockToSpecificBatch(
        stockDocId: String,
        batchId: String,
        itemName: String,
        bagsToAdd: Int,
        kgsToAdd: Double
    ) {
        if (bagsToAdd <= 0 && kgsToAdd <= 0.0) return

        if (stockDocId.isNotEmpty()) {
            val batchRef = db.collection("stock_batches").document(stockDocId)
            db.runTransaction { tr ->
                val snap = tr.get(batchRef)
                if (snap.exists()) {
                    val availBags = (snap.getLong("availableBags") ?: 0L).toInt()
                    val availKgs = snap.getDouble("availableKgs") ?: 0.0
                    tr.update(
                        batchRef,
                        hashMapOf<String, Any>(
                            "availableBags" to (availBags + bagsToAdd),
                            "availableKgs" to (availKgs + kgsToAdd)
                        )
                    )
                }
            }.addOnCompleteListener {
                loadAvailableStockBatches()
            }
            return
        }

        db.collection("stock_batches").get().addOnSuccessListener { snap ->
            val byBatch = if (batchId.isNotEmpty()) {
                snap.documents.firstOrNull { d ->
                    (d.getString("batchId") ?: "").equals(batchId, ignoreCase = true)
                }
            } else null

            val targetDoc = byBatch ?: snap.documents.filter { doc ->
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
                ).addOnCompleteListener {
                    loadAvailableStockBatches()
                }
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
            loadAvailableStockBatches()
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
                        val isGpay = payMode.contains("GPay", ignoreCase = true) || payMode.contains("UPI", ignoreCase = true)
                        val cashAmt = if (!isCredit && !isGpay) grandTotal else 0.0
                        val gpayAmt = if (isGpay) grandTotal else 0.0
                        val creditAmt = if (isCredit) grandTotal else 0.0
                        val finalMode = if (isCredit) "Credit" else "Cash"

                        val docId = db.collection("sales").document().id
                        val saleData = hashMapOf<String, Any>(
                            "id" to docId,
                            "date" to dateStr,
                            "billNo" to billNo,
                            "customerId" to custId,
                            "customerName" to custName,
                            "payMode" to finalMode,
                            "cash" to cashAmt,
                            "gpay" to gpayAmt,
                            "credit" to creditAmt,
                            "cashPersonName" to "",
                            "cashAmount" to cashAmt,
                            "gpayAmount" to gpayAmt,
                            "items" to arrayListOf(itemEntry),
                            "itemName" to itemName,
                            "stockId" to stockIdInput,
                            "bags" to bags,
                            "netKgs" to netKgs,
                            "totalBagCharges" to bagCharge,
                            "totalWeighCoolie" to 0.0,
                            "grandTotal" to grandTotal,
                            "paidAmount" to (cashAmt + gpayAmt),
                            "balanceAmount" to creditAmt,
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
                            loadAvailableStockBatches()
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
            selectedCustomerPhone = chosen.phone
            selectedCustomerBalance = chosen.balance
            binding.etSalesCustomer.setText(chosen.name)
            val phInfo = if (chosen.phone.isNotEmpty()) "  |  Ph: ${chosen.phone}" else ""
            binding.tvSalesCustBalance.text = String.format(
                Locale.getDefault(),
                "Previous Balance: Rs. %.2f%s",
                chosen.balance,
                phInfo
            )
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
            recalculateSplitCredit()
            return
        }

        for (i in 0 until billItemsArray.length()) {
            val obj = billItemsArray.getJSONObject(i)
            val iName = obj.getString("itemName")
            val supName = obj.optString("supplierName", "")
            val bgs = obj.getInt("bags")
            val kgs = obj.getDouble("netKgs")
            val rt = obj.getDouble("rate")
            val bChg = obj.getDouble("bagCharge")
            val wCoolie = obj.optDouble("weighCoolie", 0.0)
            val rowTot = obj.getDouble("rowTotal")
            grandTotalBill += rowTot

            val tr = TableRow(this)
            tr.setBackgroundColor(if (i % 2 == 0) Color.WHITE else Color.parseColor("#F1F8E9"))

            val itemWithSup = if (supName.isNotEmpty()) "$iName ($supName)" else iName
            val bagDisplay = if (iName.equals("Coconut", ignoreCase = true) || bgs <= 0) "-" else "$bgs"
            val extraCharges = bChg + wCoolie
            val extraDisplay = if (wCoolie > 0.0) {
                String.format(Locale.getDefault(), "%.0f+%.0f", bChg, wCoolie)
            } else {
                String.format(Locale.getDefault(), "%.2f", extraCharges)
            }

            tr.addView(makeCell(itemWithSup, bold = true))
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
                updateSelectedBatchAvailabilityBanner()
            }
            tr.addView(delBtn)

            binding.tlSalesItemsGrid.addView(tr)
        }

        binding.tvSalesGrandTotal.text = String.format(Locale.getDefault(), "Grand Total: Rs. %.2f", grandTotalBill)
        recalculateSplitCredit()
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
                val phone = doc.getString("contact") ?: doc.getString("phone") ?: ""
                val bal = doc.getDouble("currentBalance") ?: 0.0
                customerList.add(CustomerItem(doc.id, name, phone, bal))
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
        selectedCustomerPhone = ""
        selectedCustomerBalance = 0.0
        etSplitCash.setText("")
        etSplitGpay.setText("")
        etSplitCredit.setText("Cr: 0.00")
        etCashPayerName.setText("")
        rowCashPayer.visibility = View.GONE
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
        loadAvailableStockBatches()
    }
}
