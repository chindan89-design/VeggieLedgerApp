// selectedDate என்பது நீங்க report எடுக்குற தேதி (Format: "yyyy-MM-dd" என்று வைத்துக்கொள்வோம்)

var prevSales = 0.0
var prevIncome = 0.0
var prevDiscount = 0.0

var todaySales = 0.0
var todayIncome = 0.0
var todayDiscount = 0.0

// 1. Sales பட்டியலை பிரித்தல்
for (sale in allCustomerSales) {
    if (sale.date < selectedDate) {
        // நேத்து வரைக்கும் நடந்த Sales (Opening Balance-க்காக)
        prevSales += sale.amount
    } else if (sale.date == selectedDate) {
        // இன்னைக்கு நடந்த Sales மட்டும்
        todaySales += sale.amount
    }
}

// 2. Income & Discount பட்டியலை பிரித்தல்
for (receipt in allCustomerReceipts) {
    if (receipt.date < selectedDate) {
        // நேத்து வரைக்கும் வந்த வரவு & தள்ளுபடி
        prevIncome += receipt.amount
        prevDiscount += receipt.discount
    } else if (receipt.date == selectedDate) {
        // இன்னைக்கு வந்த வரவு & தள்ளுபடி மட்டும்
        todayIncome += receipt.amount
        todayDiscount += receipt.discount
    }
}

// 3. சரியான கணக்கீடு
val todayOpeningBalance = customer.initialOpBal + prevSales - (prevIncome + prevDiscount)
val closingOutstanding = todayOpeningBalance + todaySales - (todayIncome + todayDiscount)
