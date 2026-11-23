    private fun importSalesFromCsvUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines()
            reader.close()

            if (lines.size <= 1) {
                Toast.makeText(this, "File is empty or has only headers", Toast.LENGTH_SHORT).show()
                return
            }

            Toast.makeText(this, "Importing ${lines.size - 1} rows & updating stock...", Toast.LENGTH_SHORT).show()

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

                    // Map stock batches by docId, batchId, or lotId
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

                    for (i in 1 until lines.size) {
                        val line = lines[i].trim()
                        if (line.isEmpty()) continue

                        val cols = line.split(",")
                        if (cols.size < 10) continue

                        // Columns: Date, BillNo, CustomerName, PayMode, ItemName, StockId, Bags, NetKgs, Rate, Amount, BagCharge, GrandTotal
                        val dateStr = cols[0].trim()
                        val billNo = cols[1].trim()
                        val custName = cols[2].trim()
                        val payMode = cols[3].trim().ifEmpty { "Credit" }
                        val itemName = cols[4].trim()
                        val stockIdInput = cols[5].trim()
                        val bags = cols[6].trim().toIntOrNull() ?: 0
                        val netKgs = cols[7].trim().toDoubleOrNull() ?: 0.0
                        val rate = cols[8].trim().toDoubleOrNull() ?: 0.0
                        val amount = cols[9].trim().toDoubleOrNull() ?: (netKgs * rate)
                        val bagCharge = if (cols.size > 10) (cols[10].trim().toDoubleOrNull() ?: 0.0) else 0.0
                        val grandTotal = if (cols.size > 11) (cols[11].trim().toDoubleOrNull() ?: (amount + bagCharge)) else (amount + bagCharge)

                        if (custName.isEmpty() || grandTotal <= 0.0) continue

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

                        // Deduct Stock using StockId if provided, else fallback to FIFO
                        val matchedStockDocId = stockDocIdMap[stockIdInput.lowercase(Locale.getDefault())]
                        if (matchedStockDocId != null) {
                            val currB = stockAvailBagsMap[matchedStockDocId] ?: 0
                            val currK = stockAvailKgsMap[matchedStockDocId] ?: 0.0
                            stockAvailBagsMap[matchedStockDocId] = (currB - bags).coerceAtLeast(0)
                            stockAvailKgsMap[matchedStockDocId] = (currK - netKgs).coerceAtLeast(0.0)
                        } else if (itemName.isNotEmpty()) {
                            fifoFallbackList.add(Triple(itemName, bags, netKgs))
                        }
                    }

                    // Commit stock updates for matched StockIds
                    for ((sDocId, newBags) in stockAvailBagsMap) {
                        val newKgs = stockAvailKgsMap[sDocId] ?: 0.0
                        batch.update(
                            db.collection("stock_batches").document(sDocId),
                            hashMapOf<String, Any>(
                                "availableBags" to newBags,
                                "availableKgs" to newKgs
                            )
                        )
                    }

                    // Update customer balances
                    for ((cId, addAmt) in addedCreditPerCust) {
                        val newBal = (custBalMap[cId] ?: 0.0) + addAmt
                        batch.update(db.collection("customers").document(cId), "currentBalance", newBal)
                    }

                    batch.commit()
                        .addOnSuccessListener {
                            for (fb in fifoFallbackList) {
                                deductStockFifo(fb.first, fb.second, fb.third)
                            }
                            Toast.makeText(this, "Imported $importedCount Sales & Updated Stock!", Toast.LENGTH_LONG).show()
                            fetchNextBillNo()
                            loadCustomers()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Import failed! Check internet.", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "File error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
