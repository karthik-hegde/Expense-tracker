package com.example.expensetracker.data

import android.content.Context
import com.example.expensetracker.model.ExpenseCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface CategoryRepository {
    fun getCategories(): StateFlow<List<ExpenseCategory>>
    suspend fun addCategory(name: String, emoji: String, colorHex: String): Result<ExpenseCategory>
    suspend fun deleteCategory(categoryId: String): Result<Unit>
    suspend fun addTagToCategory(categoryId: String, tag: String): Result<Unit>
    suspend fun removeTagFromCategory(categoryId: String, tag: String): Result<Unit>
    suspend fun resetToDefaults()

    fun getIncomeCategories(): StateFlow<List<ExpenseCategory>>
    suspend fun addIncomeCategory(name: String, emoji: String, colorHex: String): Result<ExpenseCategory>
    suspend fun deleteIncomeCategory(categoryId: String): Result<Unit>
    suspend fun addTagToIncomeCategory(categoryId: String, tag: String): Result<Unit>
    suspend fun removeTagFromIncomeCategory(categoryId: String, tag: String): Result<Unit>
    suspend fun resetIncomeCategoriesToDefaults()
}

class FileCategoryRepository(
    context: Context? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val baseDirectory: File = context?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
) : CategoryRepository {

    private val file: File
        get() = File(baseDirectory, "categories.json")

    private val incomeFile: File
        get() = File(baseDirectory, "income_categories.json")

    private val _categoriesFlow: MutableStateFlow<List<ExpenseCategory>>
    private val _incomeCategoriesFlow: MutableStateFlow<List<ExpenseCategory>>

    init {
        val initialExpenses = if (!file.exists()) {
            val defaults = ExpenseCategory.DEFAULT_CATEGORIES
            saveCategoriesToTargetFile(file, defaults)
            defaults
        } else {
            val loaded = loadCategoriesFromTargetFile(file)
            if (loaded.isNotEmpty()) loaded else ExpenseCategory.DEFAULT_CATEGORIES
        }
        _categoriesFlow = MutableStateFlow(initialExpenses)

        val initialIncomes = if (!incomeFile.exists()) {
            val defaults = ExpenseCategory.DEFAULT_INCOME_CATEGORIES
            saveCategoriesToTargetFile(incomeFile, defaults)
            defaults
        } else {
            val loaded = loadCategoriesFromTargetFile(incomeFile)
            if (loaded.isNotEmpty()) loaded else ExpenseCategory.DEFAULT_INCOME_CATEGORIES
        }
        _incomeCategoriesFlow = MutableStateFlow(initialIncomes)
    }

    suspend fun initialize() {
        withContext(ioDispatcher) {
            if (!file.exists()) {
                val defaults = ExpenseCategory.DEFAULT_CATEGORIES
                saveCategoriesToTargetFile(file, defaults)
                _categoriesFlow.value = defaults
            } else {
                val loaded = loadCategoriesFromTargetFile(file)
                if (loaded.isNotEmpty()) {
                    _categoriesFlow.value = loaded
                }
            }

            if (!incomeFile.exists()) {
                val defaults = ExpenseCategory.DEFAULT_INCOME_CATEGORIES
                saveCategoriesToTargetFile(incomeFile, defaults)
                _incomeCategoriesFlow.value = defaults
            } else {
                val loaded = loadCategoriesFromTargetFile(incomeFile)
                if (loaded.isNotEmpty()) {
                    _incomeCategoriesFlow.value = loaded
                }
            }
        }
    }

    override fun getCategories(): StateFlow<List<ExpenseCategory>> = _categoriesFlow.asStateFlow()

    override suspend fun addCategory(
        name: String,
        emoji: String,
        colorHex: String
    ): Result<ExpenseCategory> = withContext(ioDispatcher) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Category name cannot be empty"))
        }

        val current = _categoriesFlow.value
        if (current.any { it.displayName.equals(trimmedName, ignoreCase = true) || it.name.equals(trimmedName, ignoreCase = true) }) {
            return@withContext Result.failure(IllegalArgumentException("A category with this name already exists"))
        }

        val newCategory = ExpenseCategory(
            name = trimmedName.uppercase().replace(" ", "_"),
            displayName = trimmedName,
            emoji = emoji.ifBlank { "🏷️" },
            colorHex = colorHex.ifBlank { "#90A4AE" },
            tags = emptyList()
        )

        val updated = current + newCategory
        _categoriesFlow.value = updated
        saveCategoriesToTargetFile(file, updated)
        Result.success(newCategory)
    }

    override suspend fun deleteCategory(categoryId: String): Result<Unit> = withContext(ioDispatcher) {
        val current = _categoriesFlow.value
        val toDelete = current.find { it.id == categoryId }
            ?: return@withContext Result.failure(IllegalArgumentException("Category not found"))

        if (toDelete.id.equals("unknown", ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("Cannot delete the default Unknown category"))
        }

        if (current.size <= 1) {
            return@withContext Result.failure(IllegalArgumentException("Must have at least one category"))
        }

        val updated = current.filterNot { it.id == categoryId }
        _categoriesFlow.value = updated
        saveCategoriesToTargetFile(file, updated)
        Result.success(Unit)
    }

    override suspend fun addTagToCategory(
        categoryId: String,
        tag: String
    ): Result<Unit> = withContext(ioDispatcher) {
        val cleanTag = tag.trim().lowercase()
        if (cleanTag.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Tag cannot be empty"))
        }

        val current = _categoriesFlow.value
        val targetCategory = current.find { it.id == categoryId }
            ?: return@withContext Result.failure(IllegalArgumentException("Category not found"))

        if (targetCategory.tags.any { it.equals(cleanTag, ignoreCase = true) }) {
            return@withContext Result.failure(IllegalArgumentException("Tag '$cleanTag' is already added to this category"))
        }

        val conflictingCategory = current.find { it.id != categoryId && it.tags.any { t -> t.equals(cleanTag, ignoreCase = true) } }
        if (conflictingCategory != null) {
            return@withContext Result.failure(
                IllegalArgumentException("Tag '$cleanTag' already belongs to '${conflictingCategory.displayName}'")
            )
        }

        val updated = current.map { cat ->
            if (cat.id == categoryId) {
                cat.copy(tags = cat.tags + cleanTag)
            } else {
                cat
            }
        }

        _categoriesFlow.value = updated
        saveCategoriesToTargetFile(file, updated)
        Result.success(Unit)
    }

    override suspend fun removeTagFromCategory(
        categoryId: String,
        tag: String
    ): Result<Unit> = withContext(ioDispatcher) {
        val cleanTag = tag.trim().lowercase()
        val current = _categoriesFlow.value

        val updated = current.map { cat ->
            if (cat.id == categoryId) {
                cat.copy(tags = cat.tags.filterNot { it.equals(cleanTag, ignoreCase = true) })
            } else {
                cat
            }
        }

        _categoriesFlow.value = updated
        saveCategoriesToTargetFile(file, updated)
        Result.success(Unit)
    }

    override suspend fun resetToDefaults() {
        withContext(ioDispatcher) {
            val defaults = ExpenseCategory.DEFAULT_CATEGORIES
            _categoriesFlow.value = defaults
            saveCategoriesToTargetFile(file, defaults)
        }
    }

    // Income Category implementations
    override fun getIncomeCategories(): StateFlow<List<ExpenseCategory>> = _incomeCategoriesFlow.asStateFlow()

    override suspend fun addIncomeCategory(
        name: String,
        emoji: String,
        colorHex: String
    ): Result<ExpenseCategory> = withContext(ioDispatcher) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Category name cannot be empty"))
        }

        val current = _incomeCategoriesFlow.value
        if (current.any { it.displayName.equals(trimmedName, ignoreCase = true) || it.name.equals(trimmedName, ignoreCase = true) }) {
            return@withContext Result.failure(IllegalArgumentException("An income category with this name already exists"))
        }

        val newCategory = ExpenseCategory(
            name = trimmedName.uppercase().replace(" ", "_"),
            displayName = trimmedName,
            emoji = emoji.ifBlank { "🏷️" },
            colorHex = colorHex.ifBlank { "#4CAF50" },
            tags = emptyList()
        )

        val updated = current + newCategory
        _incomeCategoriesFlow.value = updated
        saveCategoriesToTargetFile(incomeFile, updated)
        Result.success(newCategory)
    }

    override suspend fun deleteIncomeCategory(categoryId: String): Result<Unit> = withContext(ioDispatcher) {
        val current = _incomeCategoriesFlow.value
        val toDelete = current.find { it.id == categoryId }
            ?: return@withContext Result.failure(IllegalArgumentException("Income category not found"))

        if (toDelete.id.equals("unknown", ignoreCase = true) || toDelete.id.equals("unknown_income", ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("Cannot delete the default Unknown category"))
        }

        if (current.size <= 1) {
            return@withContext Result.failure(IllegalArgumentException("Must have at least one category"))
        }

        val updated = current.filterNot { it.id == categoryId }
        _incomeCategoriesFlow.value = updated
        saveCategoriesToTargetFile(incomeFile, updated)
        Result.success(Unit)
    }

    override suspend fun addTagToIncomeCategory(
        categoryId: String,
        tag: String
    ): Result<Unit> = withContext(ioDispatcher) {
        val cleanTag = tag.trim().lowercase()
        if (cleanTag.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Tag cannot be empty"))
        }

        val current = _incomeCategoriesFlow.value
        val targetCategory = current.find { it.id == categoryId }
            ?: return@withContext Result.failure(IllegalArgumentException("Income category not found"))

        if (targetCategory.tags.any { it.equals(cleanTag, ignoreCase = true) }) {
            return@withContext Result.failure(IllegalArgumentException("Tag '$cleanTag' is already added to this category"))
        }

        val conflictingCategory = current.find { it.id != categoryId && it.tags.any { t -> t.equals(cleanTag, ignoreCase = true) } }
        if (conflictingCategory != null) {
            return@withContext Result.failure(
                IllegalArgumentException("Tag '$cleanTag' already belongs to '${conflictingCategory.displayName}'")
            )
        }

        val updated = current.map { cat ->
            if (cat.id == categoryId) {
                cat.copy(tags = cat.tags + cleanTag)
            } else {
                cat
            }
        }

        _incomeCategoriesFlow.value = updated
        saveCategoriesToTargetFile(incomeFile, updated)
        Result.success(Unit)
    }

    override suspend fun removeTagFromIncomeCategory(
        categoryId: String,
        tag: String
    ): Result<Unit> = withContext(ioDispatcher) {
        val cleanTag = tag.trim().lowercase()
        val current = _incomeCategoriesFlow.value

        val updated = current.map { cat ->
            if (cat.id == categoryId) {
                cat.copy(tags = cat.tags.filterNot { it.equals(cleanTag, ignoreCase = true) })
            } else {
                cat
            }
        }

        _incomeCategoriesFlow.value = updated
        saveCategoriesToTargetFile(incomeFile, updated)
        Result.success(Unit)
    }

    override suspend fun resetIncomeCategoriesToDefaults() {
        withContext(ioDispatcher) {
            val defaults = ExpenseCategory.DEFAULT_INCOME_CATEGORIES
            _incomeCategoriesFlow.value = defaults
            saveCategoriesToTargetFile(incomeFile, defaults)
        }
    }

    private fun loadCategoriesFromTargetFile(targetFile: File): List<ExpenseCategory> {
        return try {
            if (!targetFile.exists()) return emptyList()
            val text = targetFile.readText()
            if (text.isBlank()) return emptyList()

            val jsonArray = JSONArray(text)
            val list = mutableListOf<ExpenseCategory>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val tagsArray = obj.optJSONArray("tags")
                val tags = mutableListOf<String>()
                if (tagsArray != null) {
                    for (j in 0 until tagsArray.length()) {
                        val t = tagsArray.optString(j)
                        if (t.isNotBlank()) tags.add(t)
                    }
                }

                list.add(
                    ExpenseCategory(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "OTHER"),
                        displayName = obj.optString("displayName", obj.optString("name", "Other")),
                        emoji = obj.optString("emoji", "🏷️"),
                        colorHex = obj.optString("colorHex", "#90A4AE"),
                        tags = tags
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCategoriesToTargetFile(targetFile: File, categories: List<ExpenseCategory>) {
        try {
            val jsonArray = JSONArray()
            for (cat in categories) {
                val obj = JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("displayName", cat.displayName)
                    put("emoji", cat.emoji)
                    put("colorHex", cat.colorHex)
                    val tagsArray = JSONArray()
                    cat.tags.forEach { tagsArray.put(it) }
                    put("tags", tagsArray)
                }
                jsonArray.put(obj)
            }
            safeWriteFile(targetFile, jsonArray.toString(2))
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
}
