package com.shop.veggieledger

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Xml
import android.view.Gravity
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
import com.google.firebase.firestore.SetOptions
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

    private val customerList = arrayListOf<CustomerItem>()
    private val itemNames = arrayListOf("Coconut", "Onion", "Garlic")
    private lateinit var itemAdapter: ArrayAdapter<String>
    private val billItemsArray = JSONArray()

    private var selectedDateStr: String = ""
    private var selectedCustomerId: String = ""
    private var selectedCustomerName: String = ""
    private var selectedCustomerBalance: Double = 0.0
    private var grandTotalBill: Double = 0.0

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

        binding.btnImportSalesCsv.setOnClickListener {
            pickSalesFileLauncher.launch("*/*")
        }

        val cal = Calendar.getInstance()
        selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
        binding.etSalesDate.setText(selectedDateStr)

        binding.etSalesDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.time)
                binding.etSalesDate.setText(selectedDateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Pay Mode Spinner
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, payModes)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spSalesPayMode.adapter = modeAdapter

        // Set Default Item Spinner Adapter immediately so it is always selectable
        itemAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, itemNames)
        itemAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spSalesItem.adapter = itemAdapter

        fetchNextBillNo()
        loadCustomers()
        loadItems()

        binding.etSalesCustomer.setOnClickListener {
            showCustomerSearchDialog()
        }

        val lineCalcWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val kgs = binding.etSalesKgs.text.toString().toDoubleOrNull() ?: 0.0
                val rate = binding.etSalesRate.text.toString().toDoubleOrNull() ?: 0.0
                val bagChg = binding.etSalesBagCharge.text.toString().toDoubleOrNull() ?: 0.0
                val lineTot = (kgs * rate) + bagChg
                binding.tvLineAmountPreview.text = String.format(Locale.getDefault(), "Item Amt: Rs. %.2f", lineTot)
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etSalesKgs.addTextChangedListener(lineCalcWatcher)
        binding.etSalesRate.addTextChangedListener(lineCalcWatcher)
        binding.etSalesBagCharge.addTextChangedListener(lineCalcWatcher)

        binding.btnAddSalesItem.setOnClickListener {
            val itemName = binding.spSalesItem.selectedItem?.toString() ?: "Coconut"
            val bags = binding.etSalesBags.text.toString().toIntOrNull() ?: 0
            val kgs = binding.etSalesKgs.text.toString().toDoubleOrNull() ?: 0.0
            val rate = binding.etSalesRate.text.toString().toDoubleOrNull() ?: 0.0
            val bagChg = binding.etSalesBagCharge.text.toString().toDoubleOrNull() ?: 0.0

            if (kgs <= 0.0 && bags <= 0) {
                Toast.makeText(this, "Enter valid Bags or Net Kgs", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (rate <= 0.0) {
                Toast.makeText(this, "Enter valid Rate", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val baseAmt = kgs * rate
            val rowTotal = baseAmt + bagChg

            val rowObj = JSONObject()
            rowObj.put("itemName", itemName)
            rowObj.put("bags", bags)
            rowObj.put("netKgs", kgs)
            rowObj.put("rate", rate)
            rowObj.put("amount", baseAmt)
            rowObj.put("bagCharge", bagChg)
            rowObj.put("rowTotal", rowTotal)

            billItemsArray.put(rowObj)
            refreshBillItemsGrid()

            binding.etSalesBags.setText("")
            binding.etSalesKgs.setText("")
            binding.etSalesRate.setText("")
            binding.etSalesBagCharge.setText("")
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

            binding.btnSaveSalesBill.isEnabled = false

            val itemsForDb = arrayListOf<Any>()
            var totalBagsAll = 0
            var totalKgsAll = 0.0
            var totalBagChargesAll = 0.0
            var firstItemName = ""

            for (i in 0 until billItemsArray.length()) {
                val obj = billItemsArray.getJSONObject(i)
                val iName = obj.getString("itemName")
                val bgs = obj.getInt("bags")
                val kgs = obj.getDouble("netKgs")
                val rt = obj.getDouble("rate")
                val amt = obj.getDouble("amount")
                val bChg = obj.getDouble("bagCharge")

                if (i == 0) firstItemName = iName
                totalBagsAll += bgs
                totalKgsAll += kgs
                totalBagChargesAll += bChg

                val itemMap = hashMapOf<String, Any>(
                    "itemName" to iName,
                    "bags" to bgs,
                    "netKgs" to kgs,
                    "rate" to rt,
                    "amount" to amt,
                    "bagCharge" to bChg
                )
                itemsForDb.add(itemMap)

                deductStockFifo(iName, bgs, kgs)
            }

            val isCredit = payMode.equals("Credit", ignoreCase = true)
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
                "grandTotal" to grandTotalBill,
                "paidAmount" to if (isCredit) 0.0 else grandTotalBill,
                "balanceAmount" to if (isCredit) grandTotalBill else 0.0,
                "paymentStatus" to if (isCredit) "Unpaid" else "Paid",
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("sales").document(saleId).set(saleData)
                .addOnSuccessListener {
                    if (isCredit && selectedCustomerId.isNotEmpty()) {
                        val newCustBal = selectedCustomerBalance + grandTotalBill
                        db.collection("customers").document(selectedCustomerId)
                            .set(hashMapOf<String, Any>("currentBalance" to newCustBal), SetOptions.merge())
                    }
                    Toast.makeText(this, "Sales Bill $billNo Saved!", Toast.LENGTH_SHORT).show()
                    resetForm()
                }
                .addOnFailureListener {
                    binding.btnSaveSalesBill.isEnabled = true
                    Toast.makeText(this, "Failed to save sales bill", Toast.LENGTH_SHORT).show()
                }
        }

        refreshBillItemsGrid()
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
                            "bagCharge" to bagCharge
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
            val rowTot = obj.getDouble("rowTotal")
            grandTotalBill += rowTot

            val tr = TableRow(this)
            tr.setBackgroundColor(if (i % 2 == 0) Color.WHITE else Color.parseColor("#F1F8E9"))

            tr.addView(makeCell(iName, bold = true))
            tr.addView(makeCell("$bgs", alignRight = true))
            tr.addView(makeCell(String.format(Locale.getDefault(), "%.2f", kgs), alignRight = true))
            tr.addView(makeCell(String.format(Locale.getDefault(), "%.2f", rt), alignRight = true))
            tr.addView(makeCell(String.format(Locale.getDefault(), "%.2f", bChg), alignRight = true))
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
            for (doc in snap) {
                val name = doc.getString("name") ?: continue
                val bal = doc.getDouble("currentBalance") ?: 0.0
                customerList.add(CustomerItem(doc.id, name, bal))
            }
            customerList.sortBy { c -> c.name.lowercase(Locale.getDefault()) }
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
        binding.btnSaveSalesBill.isEnabled = true
        fetchNextBillNo()
        loadCustomers()
    }
}
