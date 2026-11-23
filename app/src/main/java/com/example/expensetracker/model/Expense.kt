package com.example.expensetracker.model

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class ExpenseCategory(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val displayName: String = name,
    val emoji: String = "🏷️",
    val colorHex: String = "#90A4AE",
    val tags: List<String> = emptyList()
) {
    val color: Color by lazy {
        try {
            Color(android.graphics.Color.parseColor(colorHex))
        } catch (_: Exception) {
            Color(0xFF90A4AE)
        }
    }

    companion object {
        val FOOD = ExpenseCategory(
            id = "food",
            name = "FOOD",
            displayName = "Food & Dining",
            emoji = "🍔",
            colorHex = "#E57373",
            tags = listOf("swiggy", "zomato", "restaurant", "cafe", "mcdonald", "starbucks", "blinkit", "zepto", "instamart", "grocery")
        )

        val TRANSPORT = ExpenseCategory(
            id = "transport",
            name = "TRANSPORT",
            displayName = "Transportation",
            emoji = "🚗",
            colorHex = "#64B5F6",
            tags = listOf("uber", "ola", "rapido", "petrol", "fuel", "hindustan petroleum", "hpcl", "bpcl", "ioc", "metro", "irctc")
        )

        val SHOPPING = ExpenseCategory(
            id = "shopping",
            name = "SHOPPING",
            displayName = "Shopping",
            emoji = "🛍️",
            colorHex = "#BA68C8",
            tags = listOf("amazon", "flipkart", "myntra", "ajio", "zara", "h&m")
        )

        val BILLS = ExpenseCategory(
            id = "bills",
            name = "BILLS",
            displayName = "Bills & Utilities",
            emoji = "💡",
            colorHex = "#FFB74D",
            tags = listOf("electricity", "bescom", "airtel", "jio", "broadband", "water", "gas", "recharge")
        )

        val ENTERTAINMENT = ExpenseCategory(
            id = "entertainment",
            name = "ENTERTAINMENT",
            displayName = "Entertainment",
            emoji = "🎬",
            colorHex = "#4DB6AC",
            tags = listOf("netflix", "pvr", "bookmyshow", "hotstar", "spotify", "prime video", "inox")
        )

        val HEALTH = ExpenseCategory(
            id = "health",
            name = "HEALTH",
            displayName = "Health & Medical",
            emoji = "💊",
            colorHex = "#81C784",
            tags = listOf("pharmacy", "apollo", "1mg", "hospital", "pharmeasy", "doctor")
        )

        val EDUCATION = ExpenseCategory(
            id = "education",
            name = "EDUCATION",
            displayName = "Education",
            emoji = "📚",
            colorHex = "#7986CB",
            tags = listOf("udemy", "coursera", "book", "school", "college", "tuition")
        )

        val OTHER = ExpenseCategory(
            id = "other",
            name = "OTHER",
            displayName = "Other",
            emoji = "📦",
            colorHex = "#90A4AE",
            tags = emptyList()
        )

        val UNKNOWN = ExpenseCategory(
            id = "unknown",
            name = "UNKNOWN",
            displayName = "Unknown",
            emoji = "❓",
            colorHex = "#9E9E9E",
            tags = emptyList()
        )

        val DEFAULT_CATEGORIES: List<ExpenseCategory> = listOf(
            FOOD,
            TRANSPORT,
            SHOPPING,
            BILLS,
            ENTERTAINMENT,
            HEALTH,
            EDUCATION,
            OTHER,
            UNKNOWN
        )

        // Default Income Categories
        val SALARY = ExpenseCategory(
            id = "salary",
            name = "SALARY",
            displayName = "Salary",
            emoji = "💼",
            colorHex = "#4CAF50",
            tags = listOf("salary", "payroll", "stipend", "wages", "corp", "pvt ltd", "employer", "infosys", "tcs", "wipro", "google", "microsoft", "accenture")
        )

        val FREELANCE = ExpenseCategory(
            id = "freelance",
            name = "FREELANCE",
            displayName = "Freelance & Consulting",
            emoji = "💻",
            colorHex = "#00BCD4",
            tags = listOf("freelance", "upwork", "fiverr", "client", "consulting", "consultation", "contract", "invoice", "project", "retainer")
        )

        val INVESTMENTS = ExpenseCategory(
            id = "investments",
            name = "INVESTMENTS",
            displayName = "Investments & Dividends",
            emoji = "📈",
            colorHex = "#3F51B5",
            tags = listOf("dividend", "interest", "zerodha", "groww", "mutual fund", "stocks", "capital gains", "bonds", "yield", "fixed deposit", "recurring deposit")
        )

        val RENTAL = ExpenseCategory(
            id = "rental",
            name = "RENTAL",
            displayName = "Rental & Real Estate",
            emoji = "🏠",
            colorHex = "#FF9800",
            tags = listOf("rent", "tenant", "lease", "rental", "property", "house rent")
        )

        val REFUNDS = ExpenseCategory(
            id = "refunds",
            name = "REFUNDS",
            displayName = "Refunds & Cashback",
            emoji = "🎁",
            colorHex = "#9C27B0",
            tags = listOf("refund", "cashback", "reward", "reversal", "reimbursement", "claim", "return")
        )

        val GIFTS = ExpenseCategory(
            id = "gifts",
            name = "GIFTS",
            displayName = "Grants & Gifts",
            emoji = "🎉",
            colorHex = "#E91E63",
            tags = listOf("gift", "allowance", "pocket money", "bonus", "prize")
        )

        val OTHER_INCOME = ExpenseCategory(
            id = "other_income",
            name = "OTHER",
            displayName = "Other Income",
            emoji = "📦",
            colorHex = "#90A4AE",
            tags = emptyList()
        )

        val UNKNOWN_INCOME = ExpenseCategory(
            id = "unknown_income",
            name = "UNKNOWN",
            displayName = "Unknown",
            emoji = "❓",
            colorHex = "#9E9E9E",
            tags = emptyList()
        )

        val DEFAULT_INCOME_CATEGORIES: List<ExpenseCategory> = listOf(
            SALARY,
            FREELANCE,
            INVESTMENTS,
            RENTAL,
            REFUNDS,
            GIFTS,
            OTHER_INCOME,
            UNKNOWN_INCOME
        )

        val entries: List<ExpenseCategory>
            get() = DEFAULT_CATEGORIES

        fun fromString(name: String, available: List<ExpenseCategory> = DEFAULT_CATEGORIES): ExpenseCategory {
            return available.find {
                it.name.equals(name, ignoreCase = true) ||
                it.displayName.equals(name, ignoreCase = true) ||
                it.id.equals(name, ignoreCase = true)
            } ?: OTHER
        }

        fun fromIncomeString(name: String, available: List<ExpenseCategory> = DEFAULT_INCOME_CATEGORIES): ExpenseCategory {
            return available.find {
                it.name.equals(name, ignoreCase = true) ||
                it.displayName.equals(name, ignoreCase = true) ||
                it.id.equals(name, ignoreCase = true)
            } ?: OTHER_INCOME
        }
    }
}

enum class TransactionType {
    EXPENSE,
    INCOME
}

enum class TrackerMode(val label: String, val singularLabel: String) {
    EXPENSES("Expenses", "Expense"),
    INCOME("Income", "Income")
}

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val remark: String,
    val category: ExpenseCategory,
    val date: LocalDate = LocalDate.now(),
    val source: String = "Manual",
    val smsId: String? = null,
    val originalSms: String? = null,
    val isDeleted: Boolean = false,
    val isCategoryUserModified: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    val ignoreReason: String? = null
) {
    val yearMonth: YearMonth = YearMonth.from(date)
    val isImported: Boolean
        get() = !originalSms.isNullOrBlank() || !smsId.isNullOrBlank()

    val effectiveIgnoreReason: String?
        get() = when {
            !ignoreReason.isNullOrBlank() -> ignoreReason
            isDeleted && isImported && !originalSms.isNullOrBlank() -> {
                if (type == TransactionType.INCOME) {
                    com.example.expensetracker.data.SmsIncomeParser.getIgnoreReason(originalSms) ?: "Excluded by SMS filter"
                } else {
                    com.example.expensetracker.data.SmsExpenseParser.getIgnoreReason(originalSms) ?: "Excluded by SMS filter"
                }
            }
            isDeleted && isImported -> "Excluded by SMS filter"
            isDeleted -> "Manually deleted by user"
            else -> null
        }
}

enum class ExpenseSortOrder(val label: String, val shortLabel: String) {
    AMOUNT_DESC("Highest Amount (High to Low)", "₹ High → Low"),
    AMOUNT_ASC("Lowest Amount (Low to High)", "₹ Low → High"),
    DATE_DESC("Latest Date First", "Latest Date"),
    DATE_ASC("Oldest Date First", "Oldest Date");

    companion object {
        fun fromString(value: String): ExpenseSortOrder {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: DATE_DESC
        }
    }
}
