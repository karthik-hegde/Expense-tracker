package com.example.expensetracker.data

import android.content.Context
import com.example.expensetracker.model.CustomCardSource
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.ExpenseCategory
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

interface ExpenseRepository {
    fun getExpenses(): Flow<List<Expense>>
    suspend fun addExpense(expense: Expense)
    suspend fun addExpenses(expenses: List<Expense>): Int
    suspend fun updateExpenseCategory(id: String, newCategory: ExpenseCategory)
    suspend fun deleteExpense(id: String, permanently: Boolean = false)
    suspend fun deleteAllExpenses(onlyImported: Boolean = true, permanently: Boolean = false)
    suspend fun restoreExpense(id: String)
    suspend fun restoreAllExpenses()
    suspend fun purgeAllDeletedExpenses()
    suspend fun reapplySettings(
        categories: List<ExpenseCategory>,
        excludedKeywords: List<String>,
        customCardSources: List<CustomCardSource> = emptyList()
    ): Int
}

class FileExpenseRepository(
    context: Context? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val baseDirectory: File = context?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
) : ExpenseRepository {

    private val file: File
        get() = File(baseDirectory, "expenses.json")

    private val categoryOverridesFile: File
        get() = File(baseDirectory, "category_overrides.json")

    private val categoryOverrides = java.util.concurrent.ConcurrentHashMap<String, ExpenseCategory>()

    private val _expensesFlow: MutableStateFlow<List<Expense>>

    init {
        categoryOverrides.putAll(loadCategoryOverridesFromFile())
        val initialList = if (!file.exists()) {
            val seedData = createDefaultExpenses()
            saveExpensesToFile(seedData)
            seedData
        } else {
            loadExpensesFromFile()
        }
        for (exp in initialList) {
            if (exp.isCategoryUserModified) {
                indexCategoryOverride(exp, exp.category)
            }
        }
        _expensesFlow = MutableStateFlow(initialList)
    }

    suspend fun initialize() {
        withContext(ioDispatcher) {
            categoryOverrides.putAll(loadCategoryOverridesFromFile())
            val loaded = if (!file.exists()) {
                val seedData = createDefaultExpenses()
                saveExpensesToFile(seedData)
                seedData
            } else {
                loadExpensesFromFile()
            }
            for (exp in loaded) {
                if (exp.isCategoryUserModified) {
                    indexCategoryOverride(exp, exp.category)
                }
            }
            _expensesFlow.value = loaded
        }
    }

    private fun indexCategoryOverride(expense: Expense, category: ExpenseCategory) {
        if (!expense.smsId.isNullOrBlank()) {
            categoryOverrides[expense.smsId] = category
        }
        if (!expense.originalSms.isNullOrBlank()) {
            categoryOverrides[expense.originalSms.trim()] = category
        }
        val sig = "${expense.date}_${(expense.amount * 100).toLong()}_${expense.remark.trim().lowercase()}"
        categoryOverrides[sig] = category
    }

    override fun getExpenses(): Flow<List<Expense>> = _expensesFlow.asStateFlow()

    override suspend fun addExpense(expense: Expense) {
        withContext(ioDispatcher) {
            val updated = _expensesFlow.value + expense
            _expensesFlow.value = updated
            saveExpensesToFile(updated)
        }
    }

    override suspend fun updateExpenseCategory(id: String, newCategory: ExpenseCategory) {
        withContext(ioDispatcher) {
            val current = _expensesFlow.value
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
            _expensesFlow.value = updated
            saveExpensesToFile(updated)
        }
    }

    override suspend fun addExpenses(expenses: List<Expense>): Int {
        return withContext(ioDispatcher) {
            val current = _expensesFlow.value

            // 1. De-duplicate within the incoming batch itself
            val distinctIncoming = expenses.distinctBy { incoming ->
                incoming.smsId ?: (incoming.originalSms ?: "${incoming.date}_${incoming.amount}_${incoming.remark.lowercase()}")
            }

            // 2. Pre-index existing items into HashSets for O(1) lookups instead of O(N*M) scans
            val existingSmsIds = HashSet<String>(current.size)
            val existingOriginalSms = HashSet<String>(current.size)
            val existingSignatures = HashSet<String>(current.size)
            for (item in current) {
                if (!item.smsId.isNullOrBlank()) existingSmsIds.add(item.smsId)
                if (!item.originalSms.isNullOrBlank()) existingOriginalSms.add(item.originalSms)
                existingSignatures.add("${item.date}_${(item.amount * 100).toLong()}_${item.remark.trim().lowercase()}")
            }

            // 3. For any incoming item, check if there's a stored user category override
            val mappedIncoming = distinctIncoming.map { incoming ->
                val sig = "${incoming.date}_${(incoming.amount * 100).toLong()}_${incoming.remark.trim().lowercase()}"
                val override = (!incoming.smsId.isNullOrBlank() && categoryOverrides.containsKey(incoming.smsId)) ||
                    (!incoming.originalSms.isNullOrBlank() && categoryOverrides.containsKey(incoming.originalSms.trim())) ||
                    categoryOverrides.containsKey(sig)

                if (override) {
                    val cat = (if (!incoming.smsId.isNullOrBlank()) categoryOverrides[incoming.smsId] else null)
                        ?: (if (!incoming.originalSms.isNullOrBlank()) categoryOverrides[incoming.originalSms.trim()] else null)
                        ?: categoryOverrides[sig]

                    if (cat != null) {
                        incoming.copy(category = cat, isCategoryUserModified = true)
                    } else {
                        incoming
                    }
                } else {
                    incoming
                }
            }

            val newUniqueExpenses = mappedIncoming.filterNot { newExp ->
                (!newExp.smsId.isNullOrBlank() && existingSmsIds.contains(newExp.smsId)) ||
                (!newExp.originalSms.isNullOrBlank() && existingOriginalSms.contains(newExp.originalSms)) ||
                existingSignatures.contains("${newExp.date}_${(newExp.amount * 100).toLong()}_${newExp.remark.trim().lowercase()}")
            }

            if (newUniqueExpenses.isNotEmpty()) {
                val updated = current + newUniqueExpenses
                _expensesFlow.value = updated
                saveExpensesToFile(updated)
            }
            newUniqueExpenses.size
        }
    }

    override suspend fun deleteExpense(id: String, permanently: Boolean) {
        withContext(ioDispatcher) {
            val current = _expensesFlow.value
            val updated = if (permanently) {
                current.filterNot { it.id == id }
            } else {
                current.map { if (it.id == id) it.copy(isDeleted = true) else it }
            }
            _expensesFlow.value = updated
            saveExpensesToFile(updated)
        }
    }

    override suspend fun restoreExpense(id: String) {
        withContext(ioDispatcher) {
            val updated = _expensesFlow.value.map {
                if (it.id == id) it.copy(isDeleted = false) else it
            }
            _expensesFlow.value = updated
            saveExpensesToFile(updated)
        }
    }

    override suspend fun deleteAllExpenses(onlyImported: Boolean, permanently: Boolean) {
        withContext(ioDispatcher) {
            val current = _expensesFlow.value
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
            _expensesFlow.value = updated
            saveExpensesToFile(updated)
        }
    }

    override suspend fun restoreAllExpenses() {
        withContext(ioDispatcher) {
            val updated = _expensesFlow.value.map { it.copy(isDeleted = false) }
            _expensesFlow.value = updated
            saveExpensesToFile(updated)
        }
    }

    override suspend fun purgeAllDeletedExpenses() {
        withContext(ioDispatcher) {
            val current = _expensesFlow.value
            val updated = current.filterNot { it.isDeleted }
            _expensesFlow.value = updated
            saveExpensesToFile(updated)
        }
    }

    override suspend fun reapplySettings(
        categories: List<ExpenseCategory>,
        excludedKeywords: List<String>,
        customCardSources: List<CustomCardSource>
    ): Int {
        return withContext(ioDispatcher) {
            val current = _expensesFlow.value
            var changedCount = 0

            val updatedList = current.map { expense ->
                val reason = if (expense.originalSms != null) SmsExpenseParser.getIgnoreReason(expense.originalSms, excludedKeywords) else null
                val isIgnored = reason != null
                val newIsDeleted = expense.isDeleted || isIgnored
                val newIgnoreReason = expense.ignoreReason ?: reason

                val (resolvedCategory, resolvedSource) = if (expense.originalSms != null) {
                    val cat = if (expense.isCategoryUserModified) {
                        categories.find { it.id == expense.category.id || it.name.equals(expense.category.name, ignoreCase = true) }
                            ?: expense.category
                    } else {
                        SmsExpenseParser.determineCategoryFromTags(
                            body = expense.originalSms,
                            remark = expense.remark,
                            availableCategories = categories
                        )
                    }
                    val src = SmsExpenseParser.extractSource(expense.originalSms, customCardSources)
                    Pair(cat, src)
                } else {
                    // Manual expense
                    val existingCat = categories.find { it.id == expense.category.id || it.name.equals(expense.category.name, ignoreCase = true) }
                    val cat = if (expense.isCategoryUserModified) {
                        existingCat ?: expense.category
                    } else {
                        existingCat ?: SmsExpenseParser.determineCategoryFromTags(
                            body = expense.remark,
                            remark = expense.remark,
                            availableCategories = categories
                        )
                    }
                    Pair(cat, expense.source)
                }

                if (resolvedCategory != expense.category || resolvedSource != expense.source || newIsDeleted != expense.isDeleted || newIgnoreReason != expense.ignoreReason) {
                    changedCount++
                    expense.copy(category = resolvedCategory, source = resolvedSource, isDeleted = newIsDeleted, ignoreReason = newIgnoreReason)
                } else {
                    expense
                }
            }

            if (changedCount > 0 || updatedList.size != current.size) {
                _expensesFlow.value = updatedList
                saveExpensesToFile(updatedList)
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

    private fun loadExpensesFromFile(): List<Expense> {
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
                val source = if (obj.has("source") && !obj.isNull("source")) obj.getString("source") else "Manual"
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
                    ExpenseCategory.fromString(obj.getString("category"))
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
                        isCategoryUserModified = isCategoryUserModified
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveExpensesToFile(expenses: List<Expense>) {
        try {
            val jsonArray = JSONArray()
            for (expense in expenses) {
                val catObj = JSONObject().apply {
                    put("id", expense.category.id)
                    put("name", expense.category.name)
                    put("displayName", expense.category.displayName)
                    put("emoji", expense.category.emoji)
                    put("colorHex", expense.category.colorHex)
                    val tagsArr = JSONArray()
                    expense.category.tags.forEach { tagsArr.put(it) }
                    put("tags", tagsArr)
                }
                val obj = JSONObject().apply {
                    put("id", expense.id)
                    put("amount", expense.amount)
                    put("remark", expense.remark)
                    put("category", expense.category.name)
                    put("categoryDetails", catObj)
                    put("date", expense.date.toString())
                    put("source", expense.source)
                    put("smsId", expense.smsId ?: JSONObject.NULL)
                    put("originalSms", expense.originalSms ?: JSONObject.NULL)
                    put("isDeleted", expense.isDeleted)
                    put("isCategoryUserModified", expense.isCategoryUserModified)
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

    private fun createDefaultExpenses(): List<Expense> {
        val today = LocalDate.now()
        return listOf(
            Expense(
                amount = 850.00,
                remark = "Weekly grocery run",
                category = ExpenseCategory.FOOD,
                date = today
            ),
            Expense(
                amount = 240.00,
                remark = "Auto / Cab to office",
                category = ExpenseCategory.TRANSPORT,
                date = today.minusDays(1)
            ),
            Expense(
                amount = 1450.00,
                remark = "Electricity & WiFi bill",
                category = ExpenseCategory.BILLS,
                date = today.minusDays(3)
            ),
            Expense(
                amount = 499.00,
                remark = "OTT streaming subscription",
                category = ExpenseCategory.ENTERTAINMENT,
                date = today.minusDays(5)
            )
        )
    }
}
