package com.example.expensetracker.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.ExpenseCategory
import com.example.expensetracker.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

object ExpenseJsonSerializer {

    /**
     * Serializes a list of expenses into a standard formatted JSON string.
     */
    fun serialize(expenses: List<Expense>): String {
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
                put("type", expense.type.name)
                put("ignoreReason", expense.ignoreReason ?: JSONObject.NULL)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    /**
     * Deserializes a JSON string into a list of Expense objects.
     * Supports our native format as well as generic expense JSON arrays from other apps.
     */
    fun deserialize(
        jsonString: String,
        availableCategories: List<ExpenseCategory> = ExpenseCategory.DEFAULT_CATEGORIES
    ): List<Expense> {
        val trimmed = jsonString.trim()
        val jsonArray = if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            when {
                root.has("expenses") -> root.getJSONArray("expenses")
                root.has("data") -> root.getJSONArray("data")
                root.has("items") -> root.getJSONArray("items")
                else -> JSONArray().apply { put(root) }
            }
        } else {
            JSONArray(trimmed)
        }

        val list = mutableListOf<Expense>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue

            val amount = parseAmount(obj)
            if (amount <= 0.0) continue

            val remark = parseRemark(obj)
            val date = parseDate(obj)
            val category = parseCategory(obj, availableCategories)
            val source = obj.optString("source", "Imported")
            val id = if (obj.has("id") && !obj.isNull("id")) obj.getString("id") else UUID.randomUUID().toString()
            val smsId = if (obj.has("smsId") && !obj.isNull("smsId")) obj.getString("smsId") else null
            val originalSms = if (obj.has("originalSms") && !obj.isNull("originalSms")) obj.getString("originalSms") else null
            val isDeleted = obj.optBoolean("isDeleted", false)
            val isCategoryUserModified = obj.optBoolean("isCategoryUserModified", false)
            val rawType = obj.optString("type", "EXPENSE")
            val type = if (rawType.equals("INCOME", ignoreCase = true)) TransactionType.INCOME else TransactionType.EXPENSE
            val ignoreReason = if (obj.has("ignoreReason") && !obj.isNull("ignoreReason")) obj.getString("ignoreReason") else null

            list.add(
                Expense(
                    id = id,
                    amount = amount,
                    remark = remark,
                    category = category,
                    date = date,
                    source = source,
                    smsId = smsId,
                    originalSms = originalSms,
                    isDeleted = isDeleted,
                    isCategoryUserModified = isCategoryUserModified,
                    type = type,
                    ignoreReason = ignoreReason
                )
            )
        }
        return list
    }

    private fun parseAmount(obj: JSONObject): Double {
        return when {
            obj.has("amount") -> {
                val num = obj.optDouble("amount", Double.NaN)
                if (num.isNaN()) obj.optString("amount").toDoubleOrNull() ?: 0.0 else num
            }
            obj.has("price") -> {
                val num = obj.optDouble("price", Double.NaN)
                if (num.isNaN()) obj.optString("price").toDoubleOrNull() ?: 0.0 else num
            }
            obj.has("cost") -> {
                val num = obj.optDouble("cost", Double.NaN)
                if (num.isNaN()) obj.optString("cost").toDoubleOrNull() ?: 0.0 else num
            }
            else -> 0.0
        }
    }

    private fun parseRemark(obj: JSONObject): String {
        return when {
            obj.has("remark") -> obj.optString("remark", "")
            obj.has("description") -> obj.optString("description", "")
            obj.has("title") -> obj.optString("title", "")
            obj.has("note") -> obj.optString("note", "")
            obj.has("merchant") -> obj.optString("merchant", "")
            else -> ""
        }
    }

    private fun parseDate(obj: JSONObject): LocalDate {
        val dateStr = when {
            obj.has("date") -> obj.optString("date", "")
            obj.has("timestamp") -> obj.optString("timestamp", "")
            obj.has("created_at") -> obj.optString("created_at", "")
            else -> ""
        }
        return try {
            if (dateStr.isNotBlank()) {
                val datePart = if (dateStr.contains("T")) dateStr.substringBefore("T") else dateStr
                LocalDate.parse(datePart)
            } else {
                LocalDate.now()
            }
        } catch (_: Exception) {
            LocalDate.now()
        }
    }

    private fun parseCategory(obj: JSONObject, availableCategories: List<ExpenseCategory>): ExpenseCategory {
        return if (obj.has("categoryDetails") && !obj.isNull("categoryDetails")) {
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
                id = cObj.optString("id", UUID.randomUUID().toString()),
                name = cObj.optString("name", "OTHER"),
                displayName = cObj.optString("displayName", cObj.optString("name", "Other")),
                emoji = cObj.optString("emoji", "🏷️"),
                colorHex = cObj.optString("colorHex", "#90A4AE"),
                tags = tagsList
            )
        } else if (obj.has("category") && !obj.isNull("category")) {
            ExpenseCategory.fromString(obj.optString("category"), availableCategories)
        } else {
            ExpenseCategory.OTHER
        }
    }

    /**
     * Exports expenses to a JSON file in the public Downloads directory.
     * Uses MediaStore.Downloads on Android 10+ (API 29+) and Environment fallback for older versions.
     */
    fun exportToDownloads(context: Context, expenses: List<Expense>): Pair<String, Uri?> {
        val jsonString = serialize(expenses)
        val timeStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.getDefault()).format(LocalDateTime.now())
        val fileName = "expenses_export_$timeStamp.json"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { os ->
                    os.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                return Pair(fileName, uri)
            }
        }

        // Fallback for API < 29
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val file = File(downloadsDir, fileName)
        file.writeText(jsonString, Charsets.UTF_8)
        return Pair(fileName, Uri.fromFile(file))
    }
}
