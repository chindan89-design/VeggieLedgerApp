package com.shop.veggieledger

import com.google.firebase.firestore.PropertyName

// 1. User Login Model
data class AppUser(
    val userId: String = "",
    val username: String = "",
    val pin: String = "",
    val role: String = "STAFF" // "ADMIN" or "STAFF"
)

// 2. Customer Master

    data class Customer(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val groupName: String = "A",
    val area: String = "",
    val address: String = "",
    val openingBalance: Double = 0.0,
    val currentBalance: Double = 0.0
)
    


// 3. Supplier Master
data class Supplier(
    val id: String = "",
    val name: String = "",
    val shortCode: String = "", // e.g., "RAM" for Ramesh Traders
    val place: String = "",
    val phone: String = "",
    val openingBalance: Double = 0.0,
    val currentBalance: Double = 0.0
)

// 4. Item Master (Coconut, Onion, Garlic)
data class ItemMaster(
    val id: String = "",
    val itemName: String = "", // "Onion", "Coconut", "Garlic"
    val unitType: String = "KG", // "BAG_KG" for Onion, "KG" for Coconut/Garlic
    val defaultBagCharge: Double = 10.0 // Rs.10 per bag for Onion
)

// 5. Supplier Stock Batch (Tracks live Bags & Kgs per Supplier ID)
data class StockBatch(
    val batchCode: String = "", // e.g., "RAM-2011-01"
    val date: String = "",
    val supplierId: String = "",
    val supplierName: String = "",
    val itemName: String = "",
    val purchasedBags: Int = 0,
    val availableBags: Int = 0,
    val purchasedKgs: Double = 0.0,
    val availableKgs: Double = 0.0,
    val purchaseRate: Double = 0.0,
    val status: String = "ACTIVE" // "ACTIVE" or "CLOSED"
)

// 6. Individual Line Item inside Purchase / Sales Bill (Supports Multi-item)
data class BillItem(
    val itemName: String = "",
    val batchCode: String = "",      // Selected Supplier Stock ID
    val supplierName: String = "",
    val bags: Int = 0,               // For Onion
    val bagWeightsText: String = "", // Manual entry e.g. "45, 49, 60"
    val netKgs: Double = 0.0,        // Auto-sum of manual weights or direct Kgs
    val ratePerKg: Double = 0.0,
    val bagChargeRate: Double = 10.0,// Default Rs.10
    val totalBagCharge: Double = 0.0,// bags * bagChargeRate
    val itemAmount: Double = 0.0,    // netKgs * ratePerKg
    val lineTotal: Double = 0.0      // itemAmount + totalBagCharge
)

// 7. Purchase & Sales Bill Header
data class BillTransaction(
    val billId: String = "",
    val billNo: String = "",
    val date: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "SALES", // "PURCHASE" or "SALES"
    val partyId: String = "",   // CustomerId or SupplierId
    val partyName: String = "",
    val paymentMode: String = "CREDIT", // "CASH", "CREDIT", "UPI"
    val totalBags: Int = 0,
    val totalKgs: Double = 0.0,
    val totalBagCharges: Double = 0.0,
    val grandTotal: Double = 0.0,
    val items: List<BillItem> = emptyList()
)

// 8. Sales Receipt & Purchase Payment
data class PaymentEntry(
    val entryId: String = "",
    val date: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "SALES_RECEIPT", // "SALES_RECEIPT" or "PURCHASE_PAYMENT"
    val partyId: String = "",
    val partyName: String = "",
    val amount: Double = 0.0,
    val mode: String = "CASH", // "CASH", "UPI", "BANK"
    val notes: String = ""
)

// 9. Shop Expenses & Extra Income
data class ExpenseIncomeEntry(
    val id: String = "",
    val date: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val entryType: String = "EXPENSE", // "EXPENSE" or "INCOME"
    val category: String = "", // "Coolie", "Rent", "Transport", "Tea", "Commission"
    val amount: Double = 0.0,
    val notes: String = ""
)
