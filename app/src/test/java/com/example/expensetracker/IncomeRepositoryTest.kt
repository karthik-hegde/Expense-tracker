package com.example.expensetracker

import com.example.expensetracker.data.FileCategoryRepository
import com.example.expensetracker.data.FileIncomeRepository
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.ExpenseCategory
import com.example.expensetracker.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class IncomeRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testIncomePersistenceAcrossInstances() = runBlocking {
        val testDir = tempFolder.newFolder("income_test")

        val repo1 = FileIncomeRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        val initialIncomes = repo1.getIncomes().first()
        assertTrue("Seed data should be populated on first run", initialIncomes.isNotEmpty())
        assertTrue("All items must be INCOME type", initialIncomes.all { it.type == TransactionType.INCOME })

        val newIncome = Expense(
            amount = 50000.0,
            remark = "Consulting retainer",
            category = ExpenseCategory.FREELANCE,
            date = LocalDate.of(2026, 10, 15),
            source = "UPI",
            type = TransactionType.INCOME
        )
        repo1.addIncome(newIncome)

        val repo2 = FileIncomeRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        val reloadedIncomes = repo2.getIncomes().first()

        val savedIncome = reloadedIncomes.find { it.id == newIncome.id }
        assertNotNull(savedIncome)
        assertEquals(50000.0, savedIncome!!.amount, 0.001)
        assertEquals("Consulting retainer", savedIncome.remark)
        assertEquals(TransactionType.INCOME, savedIncome.type)
        assertEquals(ExpenseCategory.FREELANCE.id, savedIncome.category.id)
    }

    @Test
    fun testSoftDeleteAndRestoreIncome() = runBlocking {
        val testDir = tempFolder.newFolder("income_delete_test")
        val repo = FileIncomeRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)

        val newIncome = Expense(
            amount = 12000.0,
            remark = "Side project",
            category = ExpenseCategory.FREELANCE,
            date = LocalDate.of(2026, 10, 10),
            source = "Bank",
            type = TransactionType.INCOME
        )
        repo.addIncome(newIncome)

        // Soft delete
        repo.deleteIncome(newIncome.id)
        val deletedState = repo.getIncomes().first().find { it.id == newIncome.id }
        assertNotNull(deletedState)
        assertTrue(deletedState!!.isDeleted)

        // Restore
        repo.restoreIncome(newIncome.id)
        val restoredState = repo.getIncomes().first().find { it.id == newIncome.id }
        assertNotNull(restoredState)
        assertFalse(restoredState!!.isDeleted)
    }

    @Test
    fun testUpdateIncomeCategoryAndPersistence() = runBlocking {
        val testDir = tempFolder.newFolder("income_overrides_test")
        val repo = FileIncomeRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)

        val income = Expense(
            amount = 85000.0,
            remark = "Tech Mahindra Oct Salary",
            category = ExpenseCategory.UNKNOWN_INCOME,
            date = LocalDate.of(2026, 10, 30),
            source = "Bank",
            type = TransactionType.INCOME
        )
        repo.addIncome(income)

        // Update category to SALARY
        repo.updateIncomeCategory(income.id, ExpenseCategory.SALARY)

        val repo2 = FileIncomeRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        val reloaded = repo2.getIncomes().first().find { it.id == income.id }
        assertNotNull(reloaded)
        assertEquals(ExpenseCategory.SALARY.id, reloaded!!.category.id)
        assertTrue(reloaded.isCategoryUserModified)
    }

    @Test
    fun testIncomeCategoryManagement() = runBlocking {
        val testDir = tempFolder.newFolder("income_cat_test")
        val catRepo = FileCategoryRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)

        val defaultIncomeCats = catRepo.getIncomeCategories().first()
        assertTrue(defaultIncomeCats.isNotEmpty())
        assertTrue(defaultIncomeCats.any { it.id == ExpenseCategory.SALARY.id })

        // Add a new income category
        val addResult = catRepo.addIncomeCategory(
            name = "Crypto Staking",
            emoji = "🪙",
            colorHex = "#F59E0B"
        )
        assertTrue(addResult.isSuccess)
        val createdCat = addResult.getOrThrow()

        val updatedCats = catRepo.getIncomeCategories().first()
        assertTrue(updatedCats.any { it.id == createdCat.id })

        // Add tag to the category
        val tagResult = catRepo.addTagToIncomeCategory(createdCat.id, "binance")
        assertTrue(tagResult.isSuccess)
        val catWithTag = catRepo.getIncomeCategories().first().find { it.id == createdCat.id }
        assertTrue(catWithTag!!.tags.contains("binance"))

        // Remove tag
        val removeResult = catRepo.removeTagFromIncomeCategory(createdCat.id, "binance")
        assertTrue(removeResult.isSuccess)
        val catWithoutTag = catRepo.getIncomeCategories().first().find { it.id == createdCat.id }
        assertFalse(catWithoutTag!!.tags.contains("binance"))

        // Delete custom category
        val deleteResult = catRepo.deleteIncomeCategory(createdCat.id)
        assertTrue(deleteResult.isSuccess)
        assertFalse(catRepo.getIncomeCategories().first().any { it.id == createdCat.id })
    }
}
