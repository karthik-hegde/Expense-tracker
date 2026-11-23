package com.example.expensetracker.data

import android.content.Context
import com.example.expensetracker.model.CustomCardSource
import com.example.expensetracker.model.ExpenseSortOrder
import com.example.expensetracker.model.TrackerMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface SettingsRepository {
    fun getExcludedKeywords(mode: TrackerMode = TrackerMode.EXPENSES): StateFlow<List<String>>
    suspend fun addExcludedKeyword(keyword: String, mode: TrackerMode = TrackerMode.EXPENSES): Boolean
    suspend fun removeExcludedKeyword(keyword: String, mode: TrackerMode = TrackerMode.EXPENSES)
    suspend fun resetToDefaultKeywords(mode: TrackerMode = TrackerMode.EXPENSES)

    fun getCustomCardSources(): StateFlow<List<CustomCardSource>>
    suspend fun addOrUpdateCardSource(last4Digits: String, cardName: String): Result<CustomCardSource>
    suspend fun removeCardSource(last4Digits: String): Result<Unit>

    fun getSortOrder(): StateFlow<ExpenseSortOrder>
    suspend fun setSortOrder(order: ExpenseSortOrder)
}

class FileSettingsRepository(
    context: Context? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val baseDirectory: File = context?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
) : SettingsRepository {

    private val expenseKeywordsFile: File
        get() = File(baseDirectory, "excluded_keywords.json")

    private val incomeKeywordsFile: File
        get() = File(baseDirectory, "income_excluded_keywords.json")

    private val cardsFile: File
        get() = File(baseDirectory, "custom_cards.json")

    private val preferencesFile: File
        get() = File(baseDirectory, "app_preferences.json")

    private val _expenseKeywordsFlow: MutableStateFlow<List<String>>
    private val _incomeKeywordsFlow: MutableStateFlow<List<String>>
    private val _cardsFlow: MutableStateFlow<List<CustomCardSource>>
    private val _sortOrderFlow: MutableStateFlow<ExpenseSortOrder>

    init {
        val initialExpenseKeywords = if (!expenseKeywordsFile.exists()) {
            val defaults = defaultExpenseKeywords
            saveKeywordsToFile(defaults, expenseKeywordsFile)
            defaults
        } else {
            loadKeywordsFromFile(expenseKeywordsFile)
        }
        _expenseKeywordsFlow = MutableStateFlow(initialExpenseKeywords)

        val initialIncomeKeywords = if (!incomeKeywordsFile.exists()) {
            val defaults = defaultIncomeKeywords
            saveKeywordsToFile(defaults, incomeKeywordsFile)
            defaults
        } else {
            loadKeywordsFromFile(incomeKeywordsFile)
        }
        _incomeKeywordsFlow = MutableStateFlow(initialIncomeKeywords)

        val initialCards = loadCardsFromFile()
        _cardsFlow = MutableStateFlow(initialCards)

        val initialSortOrder = loadSortOrderFromFile()
        _sortOrderFlow = MutableStateFlow(initialSortOrder)
    }

    suspend fun initialize() {
        withContext(ioDispatcher) {
            if (!expenseKeywordsFile.exists()) {
                val defaults = defaultExpenseKeywords
                saveKeywordsToFile(defaults, expenseKeywordsFile)
                _expenseKeywordsFlow.value = defaults
            } else {
                _expenseKeywordsFlow.value = loadKeywordsFromFile(expenseKeywordsFile)
            }

            if (!incomeKeywordsFile.exists()) {
                val defaults = defaultIncomeKeywords
                saveKeywordsToFile(defaults, incomeKeywordsFile)
                _incomeKeywordsFlow.value = defaults
            } else {
                _incomeKeywordsFlow.value = loadKeywordsFromFile(incomeKeywordsFile)
            }

            _cardsFlow.value = loadCardsFromFile()
            _sortOrderFlow.value = loadSortOrderFromFile()
        }
    }

    override fun getExcludedKeywords(mode: TrackerMode): StateFlow<List<String>> = 
        if (mode == TrackerMode.EXPENSES) _expenseKeywordsFlow.asStateFlow() else _incomeKeywordsFlow.asStateFlow()

    override suspend fun addExcludedKeyword(keyword: String, mode: TrackerMode): Boolean {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return false

        return withContext(ioDispatcher) {
            val flow = if (mode == TrackerMode.EXPENSES) _expenseKeywordsFlow else _incomeKeywordsFlow
            val targetFile = if (mode == TrackerMode.EXPENSES) expenseKeywordsFile else incomeKeywordsFile
            
            val current = flow.value
            if (current.any { it.equals(trimmed, ignoreCase = true) }) {
                false
            } else {
                val updated = current + trimmed
                flow.value = updated
                saveKeywordsToFile(updated, targetFile)
                true
            }
        }
    }

    override suspend fun removeExcludedKeyword(keyword: String, mode: TrackerMode) {
        withContext(ioDispatcher) {
            val flow = if (mode == TrackerMode.EXPENSES) _expenseKeywordsFlow else _incomeKeywordsFlow
            val targetFile = if (mode == TrackerMode.EXPENSES) expenseKeywordsFile else incomeKeywordsFile
            
            val updated = flow.value.filterNot { it.equals(keyword.trim(), ignoreCase = true) }
            flow.value = updated
            saveKeywordsToFile(updated, targetFile)
        }
    }

    override suspend fun resetToDefaultKeywords(mode: TrackerMode) {
        withContext(ioDispatcher) {
            val flow = if (mode == TrackerMode.EXPENSES) _expenseKeywordsFlow else _incomeKeywordsFlow
            val targetFile = if (mode == TrackerMode.EXPENSES) expenseKeywordsFile else incomeKeywordsFile
            val defaults = if (mode == TrackerMode.EXPENSES) defaultExpenseKeywords else defaultIncomeKeywords
            
            flow.value = defaults
            saveKeywordsToFile(defaults, targetFile)
        }
    }

    override fun getCustomCardSources(): StateFlow<List<CustomCardSource>> = _cardsFlow.asStateFlow()

    override suspend fun addOrUpdateCardSource(
        last4Digits: String,
        cardName: String
    ): Result<CustomCardSource> = withContext(ioDispatcher) {
        val cleanDigits = last4Digits.trim()
        if (cleanDigits.length != 4 || !cleanDigits.all { it.isDigit() }) {
            return@withContext Result.failure(IllegalArgumentException("Card digits must be exactly 4 numbers (e.g. 7224)"))
        }
        val cleanName = cardName.trim()
        if (cleanName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Card name cannot be empty"))
        }

        val current = _cardsFlow.value
        val entry = CustomCardSource(last4Digits = cleanDigits, cardName = cleanName)
        val updated = current.filterNot { it.last4Digits == cleanDigits } + entry
        _cardsFlow.value = updated
        saveCardsToFile(updated)
        Result.success(entry)
    }

    override suspend fun removeCardSource(last4Digits: String): Result<Unit> = withContext(ioDispatcher) {
        val cleanDigits = last4Digits.trim()
        val current = _cardsFlow.value
        val updated = current.filterNot { it.last4Digits == cleanDigits }
        _cardsFlow.value = updated
        saveCardsToFile(updated)
        Result.success(Unit)
    }

    override fun getSortOrder(): StateFlow<ExpenseSortOrder> = _sortOrderFlow.asStateFlow()

    override suspend fun setSortOrder(order: ExpenseSortOrder) {
        withContext(ioDispatcher) {
            _sortOrderFlow.value = order
            saveSortOrderToFile(order)
        }
    }

    private fun loadKeywordsFromFile(file: File): List<String> {
        return try {
            if (!file.exists()) return emptyList()
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val jsonArray = JSONArray(text)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optString(i)
                if (item.isNotBlank()) list.add(item)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveKeywordsToFile(keywords: List<String>, file: File) {
        try {
            val jsonArray = JSONArray()
            for (item in keywords) {
                jsonArray.put(item)
            }
            safeWriteFile(file, jsonArray.toString(2))
        } catch (_: Exception) {
        }
    }

    private fun loadCardsFromFile(): List<CustomCardSource> {
        return try {
            if (!cardsFile.exists()) return emptyList()
            val text = cardsFile.readText()
            if (text.isBlank()) return emptyList()
            val jsonArray = JSONArray(text)
            val list = mutableListOf<CustomCardSource>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val digits = obj.optString("last4Digits")
                val name = obj.optString("cardName")
                if (digits.isNotBlank() && name.isNotBlank()) {
                    list.add(CustomCardSource(digits, name))
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCardsToFile(cards: List<CustomCardSource>) {
        try {
            val jsonArray = JSONArray()
            for (item in cards) {
                val obj = JSONObject().apply {
                    put("last4Digits", item.last4Digits)
                    put("cardName", item.cardName)
                }
                jsonArray.put(obj)
            }
            safeWriteFile(cardsFile, jsonArray.toString(2))
        } catch (_: Exception) {
        }
    }

    private fun loadSortOrderFromFile(): ExpenseSortOrder {
        return try {
            if (!preferencesFile.exists()) return ExpenseSortOrder.DATE_DESC
            val text = preferencesFile.readText()
            if (text.isBlank()) return ExpenseSortOrder.DATE_DESC
            val obj = JSONObject(text)
            val sortOrderName = obj.optString("sortOrder", ExpenseSortOrder.DATE_DESC.name)
            ExpenseSortOrder.fromString(sortOrderName)
        } catch (_: Exception) {
            ExpenseSortOrder.DATE_DESC
        }
    }

    private fun saveSortOrderToFile(order: ExpenseSortOrder) {
        try {
            val obj = JSONObject().apply {
                put("sortOrder", order.name)
            }
            safeWriteFile(preferencesFile, obj.toString(2))
        } catch (_: Exception) {
        }
    }

    private fun safeWriteFile(targetFile: File, content: String) {
        val tempFile = File(targetFile.parentFile ?: baseDirectory, "${targetFile.name}.tmp")
        tempFile.writeText(content)
        try {
            java.nio.file.Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            try {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            } catch (_: Exception) {
                targetFile.writeText(content)
            }
        }
    }

    companion object {
        val defaultExpenseKeywords = listOf(
            "NACH",
            "mandate",
            "CRED",
            "CheQ",
            "MUT-",
            "mutual fund",
            "SIP",
            "ZERODHA",
            "groww",
            "upstox",
            "broking",
            "credited",
            "refund",
            "cashback",
            "OTP",
            "verification code",
            "one time password",
            "declined",
            "failed",
            "emi auto-debit"
        )
        val defaultIncomeKeywords = listOf(
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
            "bit.ly",
            "debited",
            "spent",
            "paid",
            "sent"
        )
    }
}
