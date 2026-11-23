package com.example.expensetracker

import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExpenseModelTest {

    @Test
    fun testExpenseCreationAndYearMonth() {
        val date = LocalDate.of(2026, 10, 29)
        val expense = Expense(
            amount = 45.50,
            remark = "Grocery shopping",
            category = ExpenseCategory.FOOD,
            date = date
        )

        assertEquals(45.50, expense.amount, 0.001)
        assertEquals("Grocery shopping", expense.remark)
        assertEquals(ExpenseCategory.FOOD, expense.category)
        assertEquals(YearMonth.of(2026, 10), expense.yearMonth)
        assertNotNull(expense.id)
    }

    @Test
    fun testCategoryFromStringFallback() {
        assertEquals(ExpenseCategory.FOOD, ExpenseCategory.fromString("FOOD"))
        assertEquals(ExpenseCategory.TRANSPORT, ExpenseCategory.fromString("transport"))
        assertEquals(ExpenseCategory.OTHER, ExpenseCategory.fromString("UNKNOWN_XYZ"))
    }

    @Test
    fun testRupeeFormatting() {
        val formatted = com.example.expensetracker.ui.formatRupees(1250.50)
        org.junit.Assert.assertTrue(formatted.contains("₹") || formatted.contains("1,250.50"))
    }

    @Test
    fun testSortByAmountDescending() {
        val exp1 = Expense(amount = 100.0, remark = "Item 1", category = ExpenseCategory.FOOD, date = LocalDate.of(2026, 10, 20))
        val exp2 = Expense(amount = 500.0, remark = "Item 2", category = ExpenseCategory.FOOD, date = LocalDate.of(2026, 10, 21))
        val exp3 = Expense(amount = 250.0, remark = "Item 3", category = ExpenseCategory.FOOD, date = LocalDate.of(2026, 10, 22))

        val list = listOf(exp1, exp2, exp3)
        val sortedByAmount = list.sortedByDescending { it.amount }

        assertEquals(500.0, sortedByAmount[0].amount, 0.001)
        assertEquals(250.0, sortedByAmount[1].amount, 0.001)
        assertEquals(100.0, sortedByAmount[2].amount, 0.001)
    }

    @Test
    fun testSortByAmountAscending() {
        val exp1 = Expense(amount = 300.0, remark = "Item 1", category = ExpenseCategory.FOOD)
        val exp2 = Expense(amount = 50.0, remark = "Item 2", category = ExpenseCategory.FOOD)

        val list = listOf(exp1, exp2)
        val sorted = list.sortedBy { it.amount }

        assertEquals(50.0, sorted[0].amount, 0.001)
        assertEquals(300.0, sorted[1].amount, 0.001)
    }

    @Test
    fun testAnnualExpensesAggregationAcrossMonths() {
        val expJan = Expense(amount = 1000.0, remark = "Jan Bills", category = ExpenseCategory.BILLS, date = LocalDate.of(2026, 1, 15))
        val expFeb = Expense(amount = 2500.0, remark = "Feb Shopping", category = ExpenseCategory.SHOPPING, date = LocalDate.of(2026, 2, 20))
        val expOct = Expense(amount = 1500.0, remark = "Oct Food", category = ExpenseCategory.FOOD, date = LocalDate.of(2026, 10, 5))
        val expOtherYear = Expense(amount = 9999.0, remark = "2025 Item", category = ExpenseCategory.ENTERTAINMENT, date = LocalDate.of(2025, 12, 31))
        val expDeleted = Expense(amount = 500.0, remark = "Deleted", category = ExpenseCategory.FOOD, date = LocalDate.of(2026, 6, 1), isDeleted = true)

        val allExpenses = listOf(expJan, expFeb, expOct, expOtherYear, expDeleted)
        val activeYearExpenses = allExpenses.filter { !it.isDeleted && it.date.year == 2026 }

        assertEquals(3, activeYearExpenses.size)
        val totalYearAmount = activeYearExpenses.sumOf { it.amount }
        assertEquals(5000.0, totalYearAmount, 0.001)

        val categoryTotals = activeYearExpenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        assertEquals(1000.0, categoryTotals[ExpenseCategory.BILLS] ?: 0.0, 0.001)
        assertEquals(2500.0, categoryTotals[ExpenseCategory.SHOPPING] ?: 0.0, 0.001)
        assertEquals(1500.0, categoryTotals[ExpenseCategory.FOOD] ?: 0.0, 0.001)
    }

    @Test
    fun testCompactRupeeFormatting() {
        assertEquals("-", com.example.expensetracker.ui.formatCompactRupees(0.0))
        assertEquals("500", com.example.expensetracker.ui.formatCompactRupees(500.0))
        assertEquals("5k", com.example.expensetracker.ui.formatCompactRupees(5000.0))
        assertEquals("1.5L", com.example.expensetracker.ui.formatCompactRupees(150000.0))
    }
}
