package com.example.expensetracker.data

import com.example.expensetracker.model.CustomCardSource
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.ExpenseCategory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale
import java.util.regex.Pattern

object SmsExpenseParser {

    // Keywords that indicate mandates, investments, credits, OTPs, or non-expense messages
    private val IGNORE_PATTERNS = listOf(
        "nach",
        "mandate",
        "mut-",
        "mutual fund",
        "sip",
        "zerodha",
        "groww",
        "upstox",
        "broking",
        "credited",
        "refund",
        "cashback",
        "otp",
        "verification code",
        "one time password",
        "declined",
        "failed",
        "unsuccessful",
        "request to pay",
        "collect request",
        "emi auto-debit"
    )

    // Regex to match debit / spend amounts (e.g. "Sent Rs.153.00", "Spent Rs.955.65", "INR 1,362.03 spent", "debited by INR 500")
    // Captures group(1) or group(2) as the amount
    private val DEBIT_PATTERNS = listOf(
        // Sent Rs.153.00 or Sent INR 153.00
        Pattern.compile("""(?:sent|spent|paid|debited\s*(?:by|with)?)\s*(?:rs\.?|inr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE),
        // INR 1,362.03 spent or Rs. 150 debited or INR 91,500.00 is debited
        Pattern.compile("""(?:rs\.?|inr)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:is|has\s*been)?\s*(?:spent|debited|paid|transferred)""", Pattern.CASE_INSENSITIVE),
        // Fallback: general "Rs. 150 debited" or "debited for Rs 150"
        Pattern.compile("""debited\s*for\s*(?:rs\.?|inr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE)
    )

    // Regex to extract merchant or recipient name
    private val MERCHANT_PATTERNS = listOf(
        // "to JEETHENDRA KUMAR on 27-10-26" or "to JEETHENDRA KUMAR."
        Pattern.compile("""\bto\s+([A-Za-z0-9\s&.\-_]+?)(?=\s+(?:on\b|\.|\bupi\b|\bref\b|not done|not you|$))""", Pattern.CASE_INSENSITIVE),
        // "At Hindustan Petroleum Co On 2026-10-28"
        Pattern.compile("""\bat\s+([A-Za-z0-9\s&.\-_]+?)(?=\s+(?:on\b|\.|\bavl\b|\bref\b|not you|not done|$))""", Pattern.CASE_INSENSITIVE),
        // "on AMAZON PAY IN E. Avl Limit" (second "on <merchant>" after "on <date>")
        Pattern.compile("""\bon\s+([A-Za-z0-9\s&.\-_]{3,}?)(?=\.|\bavl\b|if not|\bref\b|$)""", Pattern.CASE_INSENSITIVE)
    )

    // Supported date formats in SMS bodies
    private val DATE_PATTERNS = listOf(
        // 2026-10-28 or 2026-10-28:07:55:58
        Regex("""\b(\d{4}-\d{2}-\d{2})(?::\d{2}:\d{2}:\d{2})?\b"""),
        // 27-10-26 or 27-10-2026 or 27/10/2026 or 27/10/26
        Regex("""\b(\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\b"""),
        // 25-Oct-26 or 25-Oct-2026 or 25 Oct 2026
        Regex("""\b(\d{1,2}[-\s][A-Za-z]{3}[-\s]\d{2,4})\b""")
    )

    private val IGNORE_REGEXES = listOf(
        Regex("""\bcred\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcheq\b""", RegexOption.IGNORE_CASE)
    )

    fun getIgnoreReason(body: String, customExcludedTexts: List<String> = emptyList()): String? {
        val lower = body.lowercase(Locale.ROOT)
        for (excluded in customExcludedTexts) {
            val trimmed = excluded.trim()
            if (trimmed.isNotBlank() && lower.contains(trimmed.lowercase(Locale.ROOT))) {
                return "Matches exclusion filter: \"$trimmed\""
            }
        }

        if (IGNORE_REGEXES.any { it.containsMatchIn(body) } || lower.contains("cred") || lower.contains("cheq")) {
            val kw = if (lower.contains("cheq")) "CheQ" else "CRED"
            return "Excluded by SMS filter: Credit card bill payment ($kw)"
        }
        if (lower.contains("nach") || lower.contains("mandate") || lower.contains("emi auto-debit")) {
            val kw = when {
                lower.contains("nach") -> "nach"
                lower.contains("mandate") -> "mandate"
                else -> "emi auto-debit"
            }
            return "Excluded by SMS filter: Auto-debit / Mandate ($kw)"
        }
        if (lower.contains("zerodha") || lower.contains("sip") || lower.contains("groww") || lower.contains("upstox") || lower.contains("mutual fund") || lower.contains("mut-") || lower.contains("broking")) {
            val kw = when {
                lower.contains("zerodha") -> "zerodha"
                lower.contains("sip") -> "sip"
                lower.contains("groww") -> "groww"
                lower.contains("upstox") -> "upstox"
                lower.contains("mutual fund") -> "mutual fund"
                lower.contains("broking") -> "broking"
                else -> "mut-"
            }
            return "Excluded by SMS filter: Investment / Demat ($kw)"
        }
        if (lower.contains("credited") || lower.contains("refund") || lower.contains("cashback")) {
            val kw = when {
                lower.contains("credited") -> "credited"
                lower.contains("refund") -> "refund"
                else -> "cashback"
            }
            return "Excluded by SMS filter: Credit / Refund ($kw)"
        }
        if (lower.contains("otp") || lower.contains("verification code") || lower.contains("one time password")) {
            return "Excluded by SMS filter: OTP verification"
        }
        if (lower.contains("declined") || lower.contains("failed") || lower.contains("unsuccessful")) {
            return "Excluded by SMS filter: Failed transaction alert"
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
        availableCategories: List<ExpenseCategory> = ExpenseCategory.DEFAULT_CATEGORIES,
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
                lower.contains("collect request")

        if (isNonTransaction) {
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

        // 3. Extract Remark / Merchant
        val remark = extractRemark(body)

        // 4. Auto-detect category from tags
        val category = determineCategoryFromTags(body, remark, availableCategories)

        // 5. Extract Source (UPI, Custom Card Name, Card, Bank)
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
            if (category.id.equals("unknown", ignoreCase = true)) continue
            for (tag in category.tags) {
                val cleanTag = tag.trim().lowercase(Locale.ROOT)
                if (cleanTag.isNotBlank()) {
                    if (lowerRemark.contains(cleanTag) || lowerBody.contains(cleanTag)) {
                        return category
                    }
                }
            }
        }
        return availableCategories.find { it.id.equals("unknown", ignoreCase = true) } ?: ExpenseCategory.UNKNOWN
    }

    private fun extractAmount(body: String): Double? {
        for (pattern in DEBIT_PATTERNS) {
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
        for (pattern in MERCHANT_PATTERNS) {
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

        // Fallback: Check if bank name is present (e.g. Kotak Bank, HDFC Bank, ICICI Bank)
        val bankMatcher = Pattern.compile("""\b(Kotak|HDFC|ICICI|SBI|Axis|PNB|Bank\s+of\s+Baroda)\b""", Pattern.CASE_INSENSITIVE).matcher(body)
        return if (bankMatcher.find()) {
            "${bankMatcher.group(1)} Expense"
        } else {
            "Auto-detected Expense"
        }
    }

    private fun cleanRemark(text: String): String {
        var cleaned = text.trim()
        // Strip trailing punctuation or prefixes like "card XX5004 on"
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
        val currentCentury = (currentYear / 100) * 100 // 2000

        // Format: yyyy-MM-dd
        if (dateStr.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) {
            return try {
                LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {
                null
            }
        }

        // Format: dd-MM-yy or dd/MM/yy or dd-MM-yyyy or dd/MM/yyyy
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

        // Format: dd-MMM-yy or dd-MMM-yyyy (e.g. 25-Oct-26)
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
