package com.example.expensetracker.data

import android.content.Context
import android.provider.Telephony
import com.example.expensetracker.model.CustomCardSource
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.ExpenseCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.ZoneId

class SmsReader(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun readExpensesForMonth(
        month: YearMonth,
        customExcludedTexts: List<String> = emptyList(),
        availableCategories: List<ExpenseCategory> = ExpenseCategory.DEFAULT_CATEGORIES,
        customCardSources: List<CustomCardSource> = emptyList()
    ): List<Expense> = withContext(ioDispatcher) {
        val expenses = mutableListOf<Expense>()

        val startMillis = month.atDay(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val endMillis = month.plusMonths(1)
            .atDay(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.ADDRESS
        )

        val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?"
        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val id = if (idIndex != -1) it.getString(idIndex) else ""
                    val body = if (bodyIndex != -1) it.getString(bodyIndex) else ""
                    val dateMillis = if (dateIndex != -1) it.getLong(dateIndex) else null

                    if (body.isNotBlank()) {
                        val parsed = SmsExpenseParser.parseSms(
                            smsId = id,
                            body = body,
                            smsTimestampMillis = dateMillis,
                            customExcludedTexts = customExcludedTexts,
                            availableCategories = availableCategories,
                            customCardSources = customCardSources
                        )
                        if (parsed != null) {
                            expenses.add(parsed)
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted
        } catch (_: Exception) {
            // Other query failure
        }

        expenses
    }

    // Helper for testing or emulator environments with sample banking SMS
    fun getSampleSmsExpenses(
        month: YearMonth,
        customExcludedTexts: List<String> = emptyList(),
        availableCategories: List<ExpenseCategory> = ExpenseCategory.DEFAULT_CATEGORIES,
        customCardSources: List<CustomCardSource> = emptyList()
    ): List<Expense> {
        val samples = listOf(
            "Sent Rs.153.00 from Kotak Bank A/c X8272 to JEETHENDRA KUMAR on 27-10-26. UPI Ref 080014864877. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud",
            "Spent Rs.955.65 On HDFC Bank Card 7224 At Hindustan Petroleum Co On 2026-10-28:07:55:58.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 7224 to 7308080808",
            "INR 1,362.03 spent using ICICI Bank Card XX5004 on 25-Oct-26 on AMAZON PAY IN E. Avl Limit: INR 8,53,657.39. If not you, call 1800 2662/SMS BLOCK 5004 to 9215676766.",
            "INR 91,500.00 is debited from your Account XXXXXX8272 on 17/10/2026 towards NACH-MUT-ZERODHA BROKING LTD Kotak Bank"
        )

        return samples.mapIndexedNotNull { index, sms ->
            SmsExpenseParser.parseSms(
                smsId = "sample_sms_$index",
                body = sms,
                customExcludedTexts = customExcludedTexts,
                availableCategories = availableCategories,
                customCardSources = customCardSources
            )
        }.filter { it.yearMonth == month }
    }

    suspend fun readIncomesForMonth(
        month: YearMonth,
        customExcludedTexts: List<String> = emptyList(),
        availableCategories: List<ExpenseCategory> = ExpenseCategory.DEFAULT_INCOME_CATEGORIES,
        customCardSources: List<CustomCardSource> = emptyList()
    ): List<Expense> = withContext(ioDispatcher) {
        val incomes = mutableListOf<Expense>()

        val startMillis = month.atDay(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val endMillis = month.plusMonths(1)
            .atDay(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.ADDRESS
        )

        val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?"
        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val id = if (idIndex != -1) it.getString(idIndex) else ""
                    val body = if (bodyIndex != -1) it.getString(bodyIndex) else ""
                    val dateMillis = if (dateIndex != -1) it.getLong(dateIndex) else null

                    if (body.isNotBlank()) {
                        val parsed = SmsIncomeParser.parseSms(
                            smsId = id,
                            body = body,
                            smsTimestampMillis = dateMillis,
                            customExcludedTexts = customExcludedTexts,
                            availableCategories = availableCategories,
                            customCardSources = customCardSources
                        )
                        if (parsed != null) {
                            incomes.add(parsed)
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted
        } catch (_: Exception) {
            // Other query failure
        }

        incomes
    }

    suspend fun readExpensesForYear(
        year: Int,
        customExcludedTexts: List<String> = emptyList(),
        availableCategories: List<ExpenseCategory> = ExpenseCategory.DEFAULT_CATEGORIES,
        customCardSources: List<CustomCardSource> = emptyList()
    ): List<Expense> = withContext(ioDispatcher) {
        val expenses = mutableListOf<Expense>()

        val startMillis = java.time.Year.of(year).atDay(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val endMillis = java.time.Year.of(year + 1).atDay(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.ADDRESS
        )

        val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?"
        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val id = if (idIndex != -1) it.getString(idIndex) else ""
                    val body = if (bodyIndex != -1) it.getString(bodyIndex) else ""
                    val dateMillis = if (dateIndex != -1) it.getLong(dateIndex) else null

                    if (body.isNotBlank()) {
                        val parsed = SmsExpenseParser.parseSms(
                            smsId = id,
                            body = body,
                            smsTimestampMillis = dateMillis,
                            customExcludedTexts = customExcludedTexts,
                            availableCategories = availableCategories,
                            customCardSources = customCardSources
                        )
                        if (parsed != null) {
                            expenses.add(parsed)
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }

        expenses
    }

    suspend fun readIncomesForYear(
        year: Int,
        customExcludedTexts: List<String> = emptyList(),
        availableCategories: List<ExpenseCategory> = ExpenseCategory.DEFAULT_INCOME_CATEGORIES,
        customCardSources: List<CustomCardSource> = emptyList()
    ): List<Expense> = withContext(ioDispatcher) {
        val incomes = mutableListOf<Expense>()

        val startMillis = java.time.Year.of(year).atDay(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val endMillis = java.time.Year.of(year + 1).atDay(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.ADDRESS
        )

        val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?"
        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val id = if (idIndex != -1) it.getString(idIndex) else ""
                    val body = if (bodyIndex != -1) it.getString(bodyIndex) else ""
                    val dateMillis = if (dateIndex != -1) it.getLong(dateIndex) else null

                    if (body.isNotBlank()) {
                        val parsed = SmsIncomeParser.parseSms(
                            smsId = id,
                            body = body,
                            smsTimestampMillis = dateMillis,
                            customExcludedTexts = customExcludedTexts,
                            availableCategories = availableCategories,
                            customCardSources = customCardSources
                        )
                        if (parsed != null) {
                            incomes.add(parsed)
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }

        incomes
    }

    fun getSampleSmsIncomes(
        month: YearMonth,
        customExcludedTexts: List<String> = emptyList(),
        availableCategories: List<ExpenseCategory> = ExpenseCategory.DEFAULT_INCOME_CATEGORIES,
        customCardSources: List<CustomCardSource> = emptyList()
    ): List<Expense> {
        val y = month.year.toString().takeLast(2)
        val m = month.monthValue.toString().padStart(2, '0')
        val monthShort = month.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

        val samples = listOf(
            "INR 75,000.00 credited to your A/c X8272 on 30-$monthShort-$y towards Salary from TECH CORP PVT LTD. Avl Bal: INR 1,20,450.00.",
            "A/c *1234 credited with Rs 12,500.00 on 28-$m-$y by UPI/3294829101/FREELANCE CLIENT. UPI Ref 3294829101.",
            "Your a/c no. XXXXX1234 is credited by Rs. 3,450.00 on 25-$m-$y towards Dividend from ZERODHA BROKING LTD.",
            "Refund of Rs.450.00 has been credited to your ICICI Bank Card XX5004 on 22-$monthShort-$y from SWIGGY. Ref No 892182."
        )

        return samples.mapIndexedNotNull { index, sms ->
            SmsIncomeParser.parseSms(
                smsId = "sample_income_sms_$index",
                body = sms,
                customExcludedTexts = customExcludedTexts,
                availableCategories = availableCategories,
                customCardSources = customCardSources
            )
        }.filter { it.yearMonth == month }
    }
}
