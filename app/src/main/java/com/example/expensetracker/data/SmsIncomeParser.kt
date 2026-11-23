package com.example.expensetracker.data

import com.example.expensetracker.model.CustomCardSource
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.ExpenseCategory
import com.example.expensetracker.model.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale
import java.util.regex.Pattern

object SmsIncomeParser {

    // Keywords that indicate non-income messages: OTPs, failures, payment requests, or pure debits
    private val IGNORE_PATTERNS = listOf(
        "otp",
        "verification code",
        "one time password",
        "declined",
        "failed",
        "unsuccessful",
        "request to pay",
        "collect request",
        "emi auto-debit",
        "mandate",
        "nach-mut-",
        "statement",
        "balance is",
        "bill is due",
        "due date",
        "reminder",
        "pre-approved",
        "personal loan",
        "apply now",
        "bit.ly"
    )

    // Regex to match credit / income amounts
    private val CREDIT_PATTERNS = listOf(
        // credited by Rs. 50,000.00 / credited with INR 12500 / credited for Rs 500 / received Rs. 2000 / deposited Rs 1000
        Pattern.compile("""(?:credited\s*(?:with|by|to\s*your\s*a/c|for)?|received|deposited)\s*(?:rs\.?|inr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
        // INR 75,000.00 credited or Rs. 1,200.00 is credited / received / deposited
        Pattern.compile("""(?:rs\.?|inr)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:is|has\s*been)?\s*(?:credited|deposited|received)""", Pattern.CASE_INSENSITIVE),
        // Refund of Rs.450.00 or Cashback of INR 50
        Pattern.compile("""(?:refund|cashback|salary)\s*(?:of)?\s*(?:rs\.?|inr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
        // A/c *1234 credited ... Rs 5000
        Pattern.compile("""(?:credited|deposited)\s*to\s*(?:your\s*)?(?:a/c|account)[^.\n]*?(?:rs\.?|inr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE)
    )

    // Payer / Sender / Purpose extraction
    private val PAYER_PATTERNS = listOf(
        // "from TECH CORP PVT LTD on 30-Oct-26" or "from SWIGGY. Ref No"
        Pattern.compile("""\bfrom\s+([A-Za-z0-9\s&.\-_/]+?)(?=\s+on\b|\.|\s+avl\b|\s+ref\b|\s+for\b|if not|not done|not you|$)""", Pattern.CASE_INSENSITIVE),
        // "towards Salary from TECH CORP" or "towards Dividend"
        Pattern.compile("""\btowards\s+([A-Za-z0-9\s&.\-_/]+?)(?=\s+from\b|\s+on\b|\.|\s+avl\b|\s+ref\b|$)""", Pattern.CASE_INSENSITIVE),
        // "by UPI/3294829101/FREELANCE CLIENT" or "by a/c linked to VPA user@upi"
        Pattern.compile("""\bby\s+(?:upi\/[0-9]+\/|a\/c\s+linked\s+to\s+vpa\s+)?([A-Za-z0-9\s&.\-_/@]+?)(?=\s+on\b|\.|\s+upi\b|\s+ref\b|\s+info\b|\s+bal\b|if not|$)""", Pattern.CASE_INSENSITIVE),
        // "for Amazon Pay return" or "for flat rent"
        Pattern.compile("""\bfor\s+([A-Za-z0-9\s&.\-_/]+?)(?=\s+on\b|\.|\s+avl\b|\s+ref\b|\s+upi\b|if not|not done|not you|$)""", Pattern.CASE_INSENSITIVE),
        // "Info: NEFT-TECH-CORP-SALARY"
        Pattern.compile("""\binfo:\s*([A-Za-z0-9\s&.\-_/]+?)(?=\.|\bref\b|$)""", Pattern.CASE_INSENSITIVE)
    )

    // Supported date formats in SMS bodies
    private val DATE_PATTERNS = listOf(
        Regex("""\b(\d{4}-\d{2}-\d{2})(?::\d{2}:\d{2}:\d{2})?\b"""),
        Regex("""\b(\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\b"""),
        Regex("""\b(\d{1,2}[-\s][A-Za-z]{3}[-\s]\d{2,4})\b""")
    )

    fun getIgnoreReason(body: String, customExcludedTexts: List<String> = emptyList()): String? {
        val lower = body.lowercase(Locale.ROOT)
        for (excluded in customExcludedTexts) {
            val trimmed = excluded.trim()
            if (trimmed.isNotBlank() && lower.contains(trimmed.lowercase(Locale.ROOT))) {
                return "Matches exclusion filter: \"$trimmed\""
            }
        }

        val hasDebit = Regex("""\b(?:debited|spent|paid|sent)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val hasCredit = Regex("""\b(?:credited|received|refund|cashback|salary|deposited)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        if (hasDebit && !hasCredit) {
            return "Excluded by SMS filter: Debit/Expense transaction (Not an income)"
        }

        if (lower.contains("otp") || lower.contains("verification code") || lower.contains("one time password")) {
            return "Excluded by SMS filter: OTP verification"
        }
        if (lower.contains("declined") || lower.contains("failed") || lower.contains("unsuccessful")) {
            return "Excluded by SMS filter: Failed transaction alert"
        }
        if (lower.contains("nach-mut-") || lower.contains("mandate") || lower.contains("emi auto-debit")) {
            val kw = when {
                lower.contains("nach-mut-") -> "nach"
                lower.contains("mandate") -> "mandate"
                else -> "emi auto-debit"
            }
            return "Excluded by SMS filter: Auto-debit / Mandate ($kw)"
        }
        if (lower.contains("statement") || lower.contains("balance is") || lower.contains("bill is due") || lower.contains("due date") || lower.contains("reminder") || lower.contains("pre-approved") || lower.contains("personal loan") || lower.contains("apply now") || lower.contains("bit.ly")) {
            return "Excluded by SMS filter: Statement / Notice"
        }

        for (pattern in IGNORE_PATTERNS) {
            if (lower.contains(pattern)) {
                return "Excluded by SMS filter: \"$pattern\""
            }
        }

        return null
    }

    fun isIgnoredMessage(body: String, customExcludedTexts: List<String> = emptyList()): Boolean {
        return getIgnoreReason(body, customExcludedTexts) != null
    }

    fun parseSms(
        smsId: String,
        body: String,
        smsTimestampMillis: Long? = null,
        customExcludedTexts: List<String> = emptyList(),
        availableCategories: List<ExpenseCategory> = ExpenseCategory.DEFAULT_INCOME_CATEGORIES,
        customCardSources: List<CustomCardSource> = emptyList()
    ): Expense? {
        if (body.isBlank()) {
            return null
        }

        val lower = body.lowercase(Locale.ROOT)
        val isNonTransaction = lower.contains("otp") ||
                lower.contains("verification code") ||
                lower.contains("one time password") ||
                lower.contains("declined") ||
                lower.contains("failed") ||
                lower.contains("unsuccessful") ||
                lower.contains("request to pay") ||
                lower.contains("collect request") ||
                lower.contains("statement") ||
                lower.contains("balance is") ||
                lower.contains("bill is due") ||
                lower.contains("due date") ||
                lower.contains("reminder") ||
                lower.contains("pre-approved") ||
                lower.contains("personal loan") ||
                lower.contains("apply now") ||
                lower.contains("bit.ly")

        if (isNonTransaction) {
            return null
        }

        val hasDebit = Regex("""\b(?:debited|spent|paid|sent)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val hasCredit = Regex("""\b(?:credited|received|refund|cashback|salary|deposited)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        if (hasDebit && !hasCredit) {
            return null
        }

        // 1. Extract Amount
        val amount = extractAmount(body) ?: return null

        // 2. Extract Date (try body first, fallback to SMS delivery timestamp)
        val date = extractDate(body) ?: smsTimestampMillis?.let {
            java.time.Instant.ofEpochMilli(it)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
        } ?: LocalDate.now()

        // 3. Extract Remark / Payer / Source
        val remark = extractRemark(body)

        // 4. Auto-detect category from tags
        val category = determineCategoryFromTags(body, remark, availableCategories)

        // 5. Extract Source (Bank, UPI, Card)
        val source = extractSource(body, customCardSources)

        val reason = getIgnoreReason(body, customExcludedTexts)
        val isIgnored = reason != null

        return Expense(
            amount = amount,
            remark = remark,
            category = category,
            date = date,
            source = source,
            smsId = smsId,
            originalSms = body.trim(),
            isDeleted = isIgnored,
            type = TransactionType.INCOME,
            ignoreReason = reason
        )
    }

    fun extractSource(
        body: String,
        customCardSources: List<CustomCardSource> = emptyList()
    ): String {
        val hasCard = Regex("""\b(?:card|credit\s*card|debit\s*card)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val hasUpi = Regex("""\b(?:upi|vpa)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)

        if (hasCard) {
            for (custom in customCardSources) {
                val digits = custom.last4Digits.trim()
                if (digits.length == 4 && digits.all { it.isDigit() }) {
                    val pattern = Regex("""\b(?:xx|x|\*)*$digits\b""", RegexOption.IGNORE_CASE)
                    if (pattern.containsMatchIn(body) || body.contains(digits)) {
                        return custom.cardName
                    }
                }
            }
            return "Card"
        }

        return when {
            hasUpi -> "UPI"
            Regex("""\b(?:netbanking|net\s*banking|neft|rtgs|imps)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "Bank"
            Regex("""\b(?:a/c|account)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "Bank"
            else -> "Bank"
        }
    }

    fun determineCategoryFromTags(
        body: String,
        remark: String,
        availableCategories: List<ExpenseCategory>
    ): ExpenseCategory {
        val lowerBody = body.lowercase(Locale.ROOT)
        val lowerRemark = remark.lowercase(Locale.ROOT)

        for (category in availableCategories) {
            if (category.id.equals("unknown", ignoreCase = true) || category.id.equals("unknown_income", ignoreCase = true)) continue
            for (tag in category.tags) {
                val cleanTag = tag.trim().lowercase(Locale.ROOT)
                if (cleanTag.isNotBlank()) {
                    val matches = if (cleanTag.length <= 3) {
                        Regex("""\b${Regex.escape(cleanTag)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerRemark) ||
                        Regex("""\b${Regex.escape(cleanTag)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerBody)
                    } else {
                        lowerRemark.contains(cleanTag) || lowerBody.contains(cleanTag)
                    }
                    if (matches) {
                        return category
                    }
                }
            }
        }
        return availableCategories.find {
            it.id.equals("unknown_income", ignoreCase = true) || it.id.equals("unknown", ignoreCase = true)
        } ?: ExpenseCategory.UNKNOWN_INCOME
    }

    private fun extractAmount(body: String): Double? {
        for (pattern in CREDIT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val rawNumber = matcher.group(1)?.replace(",", "")?.trim()
                val parsed = rawNumber?.toDoubleOrNull()
                if (parsed != null && parsed > 0.0) {
                    return parsed
                }
            }
        }
        return null
    }

    private fun extractRemark(body: String): String {
        for (pattern in PAYER_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim()
                if (!candidate.isNullOrBlank() &&
                    !candidate.equals("your", ignoreCase = true) &&
                    !candidate.equals("a/c", ignoreCase = true) &&
                    candidate.length > 2
                ) {
                    return cleanRemark(candidate)
                }
            }
        }

        // Fallback: Check bank name
        val bankMatcher = Pattern.compile("""\b(Kotak|HDFC|ICICI|SBI|Axis|PNB|Bank\s+of\s+Baroda)\b""", Pattern.CASE_INSENSITIVE).matcher(body)
        return if (bankMatcher.find()) {
            "${bankMatcher.group(1)} Credit"
        } else {
            "Auto-detected Income"
        }
    }

    private fun cleanRemark(text: String): String {
        var cleaned = text.trim()
        if (cleaned.startsWith("card", ignoreCase = true)) {
            val idx = cleaned.indexOf("on", ignoreCase = true)
            if (idx != -1 && idx + 2 < cleaned.length) {
                cleaned = cleaned.substring(idx + 2).trim()
            }
        }
        return cleaned.replace(Regex("""[.,;:]+$"""), "").trim()
    }

    private fun extractDate(body: String): LocalDate? {
        for (regex in DATE_PATTERNS) {
            val match = regex.find(body)
            if (match != null) {
                val dateStr = match.groupValues[1]
                parseDateString(dateStr)?.let { return it }
            }
        }
        return null
    }

    private fun parseDateString(dateStr: String): LocalDate? {
        val currentYear = LocalDate.now().year
        val currentCentury = (currentYear / 100) * 100

        if (dateStr.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) {
            return try {
                LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {
                null
            }
        }

        val separator = if (dateStr.contains("-")) "-" else if (dateStr.contains("/")) "/" else null
        if (separator != null) {
            val parts = dateStr.split(separator)
            if (parts.size == 3) {
                val day = parts[0].toIntOrNull()
                val month = parts[1].toIntOrNull()
                var year = parts[2].toIntOrNull()
                if (day != null && month != null && year != null) {
                    if (year < 100) {
                        year += currentCentury
                    }
                    return try {
                        LocalDate.of(year, month, day)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }

        return try {
            val formatter = DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("d-MMM-")
                .appendValueReduced(ChronoField.YEAR, 2, 4, 2000)
                .toFormatter(Locale.ENGLISH)
            LocalDate.parse(dateStr.replace(" ", "-"), formatter)
        } catch (_: Exception) {
            null
        }
    }
}
