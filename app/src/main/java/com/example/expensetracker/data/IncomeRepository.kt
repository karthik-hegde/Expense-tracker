package com.example.expensetracker.data

import android.content.Context
import com.example.expensetracker.model.CustomCardSource
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.ExpenseCategory
import com.example.expensetracker.model.TransactionType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

interface IncomeRepository {
    fun getIncomes(): Flow<List<Expense>>
    suspend fun addIncome(income: Expense)
    suspend fun addIncomes(incomes: List<Expense>): Int
    suspend fun updateIncomeCategory(id: String, newCategory: ExpenseCategory)
    suspend fun deleteIncome(id: String, permanently: Boolean = false)
    suspend fun deleteAllIncomes(onlyImported: Boolean = true, permanently: Boolean = false)
    suspend fun restoreIncome(id: String)
    suspend fun restoreAllIncomes()
    suspend fun purgeAllDeletedIncomes()
    suspend fun reapplySettings(
        categories: List<ExpenseCategory>,
        excludedKeywords: List<String>,
        customCardSources: List<CustomCardSource> = emptyList()
    ): Int
}

class FileIncomeRepository(
    context: Context? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val baseDirectory: File = context?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
) : IncomeRepository {

    private val file: File
        get() = File(baseDirectory, "incomes.json")

    private val categoryOverridesFile: File
        get() = File(baseDirectory, "income_category_overrides.json")

    private val categoryOverrides = java.util.concurrent.ConcurrentHashMap<String, ExpenseCategory>()

    private val _incomesFlow: MutableStateFlow<List<Expense>>

    init {
        categoryOverrides.putAll(loadCategoryOverridesFromFile())
        val initialList = if (!file.exists()) {
            val seedData = createDefaultIncomes()
            saveIncomesToFile(seedData)
            seedData
        } else {
            loadIncomesFromFile()
        }
        for (item in initialList) {
            if (item.isCategoryUserModified) {
                indexCategoryOverride(item, item.category)
            }
        }
        _incomesFlow = MutableStateFlow(initialList)
    }

    suspend fun initialize() {
        withContext(ioDispatcher) {
            categoryOverrides.putAll(loadCategoryOverridesFromFile())
            val loaded = if (!file.exists()) {
                val seedData = createDefaultIncomes()
                saveIncomesToFile(seedData)
                seedData
            } else {
                loadIncomesFromFile()
            }
            for (item in loaded) {
                if (item.isCategoryUserModified) {
                    indexCategoryOverride(item, item.category)
                }
            }
            _incomesFlow.value = loaded
        }
    }

    private fun indexCategoryOverride(income: Expense, category: ExpenseCategory) {
        if (!income.smsId.isNullOrBlank()) {
            categoryOverrides[income.smsId] = category
        }
        if (!income.originalSms.isNullOrBlank()) {
            categoryOverrides[income.originalSms.trim()] = category
        }
        val sig = "${income.date}_${(income.amount * 100).toLong()}_${income.remark.trim().lowercase()}"
        categoryOverrides[sig] = category
    }

    override fun getIncomes(): Flow<List<Expense>> = _incomesFlow.asStateFlow()

    override suspend fun addIncome(income: Expense) {
        withContext(ioDispatcher) {
            val ensureType = if (income.type == TransactionType.INCOME) income else income.copy(type = TransactionType.INCOME)
            val updated = _incomesFlow.value + ensureType
            _incomesFlow.value = updated
            saveIncomesToFile(updated)
        }
    }

    override suspend fun updateIncomeCategory(id: String, newCategory: ExpenseCategory) {
        withContext(ioDispatcher) {
            val current = _incomesFlow.value
            val target = current.find { it.id == id }
            if (target != null) {
                indexCategoryOverride(target, newCategory)
                saveCategoryOverridesToFile()
            }
            val updated = current.map {
                if (it.id == id) {
                    it.copy(category = newCategory, isCategoryUserModified = true)
                } else {
                    it
                }
            }
            _incomesFlow.value = updated
            saveIncomesToFile(updated)
        }
    }

    override suspend fun addIncomes(incomes: List<Expense>): Int {
        return withContext(ioDispatcher) {
            val current = _incomesFlow.value

            val distinctIncoming = incomes.distinctBy { incoming ->
                incoming.smsId ?: (incoming.originalSms ?: "${incoming.date}_${incoming.amount}_${incoming.remark.lowercase()}")
            }

            val existingSmsIds = HashSet<String>(current.size)
            val existingOriginalSms = HashSet<String>(current.size)
            val existingSignatures = HashSet<String>(current.size)
            for (item in current) {
                if (!item.smsId.isNullOrBlank()) existingSmsIds.add(item.smsId)
                if (!item.originalSms.isNullOrBlank()) existingOriginalSms.add(item.originalSms)
                existingSignatures.add("${item.date}_${(item.amount * 100).toLong()}_${item.remark.trim().lowercase()}")
            }

            val mappedIncoming = distinctIncoming.map { incoming ->
                val typed = if (incoming.type == TransactionType.INCOME) incoming else incoming.copy(type = TransactionType.INCOME)
                val sig = "${typed.date}_${(typed.amount * 100).toLong()}_${typed.remark.trim().lowercase()}"
                val override = (!typed.smsId.isNullOrBlank() && categoryOverrides.containsKey(typed.smsId)) ||
                    (!typed.originalSms.isNullOrBlank() && categoryOverrides.containsKey(typed.originalSms.trim())) ||
                    categoryOverrides.containsKey(sig)

                if (override) {
                    val cat = (if (!typed.smsId.isNullOrBlank()) categoryOverrides[typed.smsId] else null)
                        ?: (if (!typed.originalSms.isNullOrBlank()) categoryOverrides[typed.originalSms.trim()] else null)
                        ?: categoryOverrides[sig]

                    if (cat != null) {
                        typed.copy(category = cat, isCategoryUserModified = true)
                    } else {
                        typed
                    }
                } else {
                    typed
                }
            }

            val newUniqueIncomes = mappedIncoming.filterNot { newInc ->
                (!newInc.smsId.isNullOrBlank() && existingSmsIds.contains(newInc.smsId)) ||
                (!newInc.originalSms.isNullOrBlank() && existingOriginalSms.contains(newInc.originalSms)) ||
                existingSignatures.contains("${newInc.date}_${(newInc.amount * 100).toLong()}_${newInc.remark.trim().lowercase()}")
            }

            if (newUniqueIncomes.isNotEmpty()) {
                val updated = current + newUniqueIncomes
                _incomesFlow.value = updated
                saveIncomesToFile(updated)
            }
            newUniqueIncomes.size
        }
    }

    override suspend fun deleteIncome(id: String, permanently: Boolean) {
        withContext(ioDispatcher) {
            val current = _incomesFlow.value
            val updated = if (permanently) {
                current.filterNot { it.id == id }
            } else {
                current.map { if (it.id == id) it.copy(isDeleted = true) else it }
            }
            _incomesFlow.value = updated
            saveIncomesToFile(updated)
        }
    }

    override suspend fun restoreIncome(id: String) {
        withContext(ioDispatcher) {
            val updated = _incomesFlow.value.map {
                if (it.id == id) it.copy(isDeleted = false) else it
            }
            _incomesFlow.value = updated
            saveIncomesToFile(updated)
        }
    }

    override suspend fun deleteAllIncomes(onlyImported: Boolean, permanently: Boolean) {
        withContext(ioDispatcher) {
            val current = _incomesFlow.value
            val updated = if (permanently) {
                if (onlyImported) {
                    current.filterNot { it.isImported }
                } else {
                    emptyList()
                }
            } else {
                if (onlyImported) {
                    current.map { if (it.isImported) it.copy(isDeleted = true) else it }
                } else {
                    current.map { it.copy(isDeleted = true) }
                }
            }
            _incomesFlow.value = updated
            saveIncomesToFile(updated)
        }
    }

    override suspend fun restoreAllIncomes() {
        withContext(ioDispatcher) {
            val updated = _incomesFlow.value.map { it.copy(isDeleted = false) }
            _incomesFlow.value = updated
            saveIncomesToFile(updated)
        }
    }

    override suspend fun purgeAllDeletedIncomes() {
        withContext(ioDispatcher) {
            val current = _incomesFlow.value
            val updated = current.filterNot { it.isDeleted }
            _incomesFlow.value = updated
            saveIncomesToFile(updated)
        }
    }

    override suspend fun reapplySettings(
        categories: List<ExpenseCategory>,
        excludedKeywords: List<String>,
        customCardSources: List<CustomCardSource>
    ): Int {
        return withContext(ioDispatcher) {
            val current = _incomesFlow.value
            var changedCount = 0

            val updatedList = current.map { income ->
                val reason = if (income.originalSms != null) SmsIncomeParser.getIgnoreReason(income.originalSms, excludedKeywords) else null
                val isIgnored = reason != null
                val newIsDeleted = income.isDeleted || isIgnored
                val newIgnoreReason = income.ignoreReason ?: reason

                val (resolvedCategory, resolvedSource) = if (income.originalSms != null) {
                    val cat = if (income.isCategoryUserModified) {
                        categories.find { it.id == income.category.id || it.name.equals(income.category.name, ignoreCase = true) }
                            ?: income.category
                    } else {
                        SmsIncomeParser.determineCategoryFromTags(
                            body = income.originalSms,
                            remark = income.remark,
                            availableCategories = categories
                        )
                    }
                    val src = SmsIncomeParser.extractSource(income.originalSms, customCardSources)
                    Pair(cat, src)
                } else {
                    val existingCat = categories.find { it.id == income.category.id || it.name.equals(income.category.name, ignoreCase = true) }
                    val cat = if (income.isCategoryUserModified) {
                        existingCat ?: income.category
                    } else {
                        existingCat ?: SmsIncomeParser.determineCategoryFromTags(
                            body = income.remark,
                            remark = income.remark,
                            availableCategories = categories
                        )
                    }
                    Pair(cat, income.source)
                }

                if (resolvedCategory != income.category || resolvedSource != income.source || newIsDeleted != income.isDeleted || newIgnoreReason != income.ignoreReason) {
                    changedCount++
                    income.copy(category = resolvedCategory, source = resolvedSource, isDeleted = newIsDeleted, ignoreReason = newIgnoreReason)
                } else {
                    income
                }
            }

            if (changedCount > 0 || updatedList.size != current.size) {
                _incomesFlow.value = updatedList
                saveIncomesToFile(updatedList)
            }
            changedCount
        }
    }

    private fun loadCategoryOverridesFromFile(): Map<String, ExpenseCategory> {
        return try {
            if (!categoryOverridesFile.exists()) return emptyMap()
            val content = categoryOverridesFile.readText()
            if (content.isBlank()) return emptyMap()
            val jsonObject = JSONObject(content)
            val result = mutableMapOf<String, ExpenseCategory>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val catObj = jsonObject.getJSONObject(key)
                val tagsList = mutableListOf<String>()
                val tagsArr = catObj.optJSONArray("tags")
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) {
                        val tagStr = tagsArr.optString(t)
                        if (tagStr.isNotBlank()) tagsList.add(tagStr)
                    }
                }
                val cat = ExpenseCategory(
                    id = catObj.optString("id", java.util.UUID.randomUUID().toString()),
                    name = catObj.optString("name", "OTHER"),
                    displayName = catObj.optString("displayName", catObj.optString("name", "Other")),
                    emoji = catObj.optString("emoji", "🏷️"),
                    colorHex = catObj.optString("colorHex", "#90A4AE"),
                    tags = tagsList
                )
                result[key] = cat
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveCategoryOverridesToFile() {
        try {
            val jsonObject = JSONObject()
            for ((key, cat) in categoryOverrides) {
                val catObj = JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("displayName", cat.displayName)
                    put("emoji", cat.emoji)
                    put("colorHex", cat.colorHex)
                    val tagsArr = JSONArray()
                    cat.tags.forEach { tagsArr.put(it) }
                    put("tags", tagsArr)
                }
                jsonObject.put(key, catObj)
            }
            safeWriteFile(categoryOverridesFile, jsonObject.toString(2))
        } catch (_: Exception) {
            // Ignore write failures
        }
    }

    private fun loadIncomesFromFile(): List<Expense> {
        return try {
            if (!file.exists()) return emptyList()
            val content = file.readText()
            if (content.isBlank()) return emptyList()

            val jsonArray = JSONArray(content)
            val list = mutableListOf<Expense>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val smsId = if (obj.has("smsId") && !obj.isNull("smsId")) obj.getString("smsId") else null
                val originalSms = if (obj.has("originalSms") && !obj.isNull("originalSms")) obj.getString("originalSms") else null
                val source = if (obj.has("source") && !obj.isNull("source")) obj.getString("source") else "Bank"
                val isDeleted = obj.optBoolean("isDeleted", false)
                val isCategoryUserModified = obj.optBoolean("isCategoryUserModified", false)

                val category = if (obj.has("categoryDetails") && !obj.isNull("categoryDetails")) {
                    val cObj = obj.getJSONObject("categoryDetails")
                    val tagsList = mutableListOf<String>()
                    val tagsArr = cObj.optJSONArray("tags")
                    if (tagsArr != null) {
                        for (t in 0 until tagsArr.length()) {
                            val tagStr = tagsArr.optString(t)
                            if (tagStr.isNotBlank()) tagsList.add(tagStr)
                        }
                    }
                    ExpenseCategory(
                        id = cObj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = cObj.optString("name", "OTHER"),
                        displayName = cObj.optString("displayName", cObj.optString("name", "Other")),
                        emoji = cObj.optString("emoji", "🏷️"),
                        colorHex = cObj.optString("colorHex", "#90A4AE"),
                        tags = tagsList
                    )
                } else {
                    ExpenseCategory.fromIncomeString(obj.getString("category"))
                }

                list.add(
                    Expense(
                        id = obj.getString("id"),
                        amount = obj.getDouble("amount"),
                        remark = obj.getString("remark"),
                        category = category,
                        date = LocalDate.parse(obj.getString("date")),
                        source = source,
                        smsId = smsId,
                        originalSms = originalSms,
                        isDeleted = isDeleted,
                        isCategoryUserModified = isCategoryUserModified,
                        type = TransactionType.INCOME
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveIncomesToFile(incomes: List<Expense>) {
        try {
            val jsonArray = JSONArray()
            for (income in incomes) {
                val catObj = JSONObject().apply {
                    put("id", income.category.id)
                    put("name", income.category.name)
                    put("displayName", income.category.displayName)
                    put("emoji", income.category.emoji)
                    put("colorHex", income.category.colorHex)
                    val tagsArr = JSONArray()
                    income.category.tags.forEach { tagsArr.put(it) }
                    put("tags", tagsArr)
                }
                val obj = JSONObject().apply {
                    put("id", income.id)
                    put("amount", income.amount)
                    put("remark", income.remark)
                    put("category", income.category.name)
                    put("categoryDetails", catObj)
                    put("date", income.date.toString())
                    put("source", income.source)
                    put("smsId", income.smsId ?: JSONObject.NULL)
                    put("originalSms", income.originalSms ?: JSONObject.NULL)
                    put("isDeleted", income.isDeleted)
                    put("isCategoryUserModified", income.isCategoryUserModified)
                    put("type", "INCOME")
                }
                jsonArray.put(obj)
            }
            safeWriteFile(file, jsonArray.toString(2))
        } catch (_: Exception) {
            // Log or ignore file write failures
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

    private fun createDefaultIncomes(): List<Expense> {
        val today = LocalDate.now()
        return listOf(
            Expense(
                amount = 75000.00,
                remark = "Monthly Salary",
                category = ExpenseCategory.SALARY,
                date = today.withDayOfMonth(1),
                source = "Bank",
                type = TransactionType.INCOME
            ),
            Expense(
                amount = 12000.00,
                remark = "Freelance UI Design project",
                category = ExpenseCategory.FREELANCE,
                date = today.minusDays(4),
                source = "Bank",
                type = TransactionType.INCOME
            ),
            Expense(
                amount = 1850.00,
                remark = "Quarterly stock dividend",
                category = ExpenseCategory.INVESTMENTS,
                date = today.minusDays(8),
                source = "Bank",
                type = TransactionType.INCOME
            ),
            Expense(
                amount = 450.00,
                remark = "E-commerce order refund",
                category = ExpenseCategory.REFUNDS,
                date = today.minusDays(12),
                source = "UPI",
                type = TransactionType.INCOME
            )
        )
    }
}
