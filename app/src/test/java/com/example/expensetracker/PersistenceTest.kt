package com.example.expensetracker

import com.example.expensetracker.data.FileCategoryRepository
import com.example.expensetracker.data.FileExpenseRepository
import com.example.expensetracker.data.FileSettingsRepository
import com.example.expensetracker.model.CustomCardSource
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.ExpenseCategory
import com.example.expensetracker.model.ExpenseSortOrder
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

class PersistenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testExpensePersistenceAcrossInstances() = runBlocking {
        val testDir = tempFolder.newFolder("expenses_test")

        // 1. Initial repository creation should seed default data
        val repo1 = FileExpenseRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        val initialExpenses = repo1.getExpenses().first()
        assertTrue("Seed data should be populated on first run", initialExpenses.isNotEmpty())

        // 2. Add an expense with a custom category and source
        val customCat = ExpenseCategory(
            id = "cat_pet",
            name = "PETS",
            displayName = "Pet Care",
            emoji = "🐾",
            colorHex = "#FF8A65",
            tags = listOf("vet", "drools")
        )
        val customExpense = Expense(
            amount = 1250.75,
            remark = "Vet vaccination & grooming",
            category = customCat,
            date = LocalDate.of(2026, 10, 25),
            source = "Regalia",
            smsId = "sms_vet_1",
            originalSms = "Spent Rs.1250.75 On HDFC Bank Card 7224 at Pet Care Clinic..."
        )
        repo1.addExpense(customExpense)

        val updated1 = repo1.getExpenses().first()
        assertTrue(updated1.any { it.id == customExpense.id })

        // 3. Create a brand new repository instance pointing to the same folder
        val repo2 = FileExpenseRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        val reloadedExpenses = repo2.getExpenses().first()

        assertEquals(updated1.size, reloadedExpenses.size)
        val reloadedCustom = reloadedExpenses.find { it.id == customExpense.id }
        assertNotNull("Custom expense should persist across repository restarts", reloadedCustom)
        assertEquals(1250.75, reloadedCustom!!.amount, 0.001)
        assertEquals("Vet vaccination & grooming", reloadedCustom.remark)
        assertEquals("Regalia", reloadedCustom.source)
        assertEquals("sms_vet_1", reloadedCustom.smsId)
        assertEquals(LocalDate.of(2026, 10, 25), reloadedCustom.date)
        // Verify custom category properties were preserved exactly
        assertEquals("cat_pet", reloadedCustom.category.id)
        assertEquals("Pet Care", reloadedCustom.category.displayName)
        assertEquals("🐾", reloadedCustom.category.emoji)
        assertEquals("#FF8A65", reloadedCustom.category.colorHex)
    }

    @Test
    fun testDeleteAllExpensesPersistsAndDoesNotRecreateSeedData() = runBlocking {
        val testDir = tempFolder.newFolder("delete_all_test")

        val repo1 = FileExpenseRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        assertTrue(repo1.getExpenses().first().isNotEmpty())

        // Delete all expenses (marks isDeleted = true)
        repo1.deleteAllExpenses(onlyImported = false)
        val allAfterDelete = repo1.getExpenses().first()
        assertTrue("All expenses should be marked as deleted", allAfterDelete.all { it.isDeleted })

        // Restart repository
        val repo2 = FileExpenseRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        val reloaded = repo2.getExpenses().first()

        // Deletion must persist
        assertTrue("Restarting app should keep all expenses marked as deleted", reloaded.all { it.isDeleted })
    }

    @Test
    fun testDeletedExpenseNotReAddedOnSmsRescanAndCanBeRestored() = runBlocking {
        val testDir = tempFolder.newFolder("sms_rescan_test")
        val repo = FileExpenseRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        repo.deleteAllExpenses()

        val sms = "Spent Rs.955.65 On HDFC Bank Card 7224 At Hindustan Petroleum Co On 2026-10-28:07:55:58."
        val smsExpense = com.example.expensetracker.data.SmsExpenseParser.parseSms("sms_fuel_1", sms)!!

        // 1. Initial SMS scan adds the expense
        val addedCount1 = repo.addExpenses(listOf(smsExpense))
        assertEquals(1, addedCount1)

        val expensesBeforeDelete = repo.getExpenses().first()
        val targetExpense = expensesBeforeDelete.find { it.smsId == "sms_fuel_1" }
        assertNotNull(targetExpense)
        assertFalse(targetExpense!!.isDeleted)

        // 2. User deletes the expense
        repo.deleteExpense(targetExpense.id)

        val expensesAfterDelete = repo.getExpenses().first()
        val deletedItem = expensesAfterDelete.find { it.id == targetExpense.id }
        assertNotNull(deletedItem)
        assertTrue("Expense should be marked as deleted", deletedItem!!.isDeleted)

        // 3. User rescans SMS messages
        val addedCount2 = repo.addExpenses(listOf(smsExpense))
        assertEquals("Rescanning SMS should NOT add the deleted expense again", 0, addedCount2)

        val expensesAfterRescan = repo.getExpenses().first()
        val itemsMatchingSms = expensesAfterRescan.filter { it.smsId == "sms_fuel_1" }
        assertEquals(1, itemsMatchingSms.size)
        assertTrue("Deleted expense must remain deleted after rescan", itemsMatchingSms[0].isDeleted)

        // 4. User restores the expense
        repo.restoreExpense(targetExpense.id)
        val expensesAfterRestore = repo.getExpenses().first()
        val restoredItem = expensesAfterRestore.find { it.id == targetExpense.id }
        assertNotNull(restoredItem)
        assertFalse("Restored expense must no longer be marked as deleted", restoredItem!!.isDeleted)
    }

    @Test
    fun testSettingsPersistenceAcrossInstances() = runBlocking {
        val testDir = tempFolder.newFolder("settings_test")

        val settings1 = FileSettingsRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)

        // Add excluded keyword
        settings1.addExcludedKeyword("Investment SIP")
        assertTrue(settings1.getExcludedKeywords().first().contains("Investment SIP"))

        // Add custom card
        settings1.addOrUpdateCardSource("7224", "Regalia Gold")
        val cards1 = settings1.getCustomCardSources().first()
        assertTrue(cards1.any { it.last4Digits == "7224" && it.cardName == "Regalia Gold" })

        // Set sort order
        settings1.setSortOrder(ExpenseSortOrder.AMOUNT_DESC)
        assertEquals(ExpenseSortOrder.AMOUNT_DESC, settings1.getSortOrder().first())

        // Create new instance pointing to same storage
        val settings2 = FileSettingsRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)

        val reloadedKeywords = settings2.getExcludedKeywords().first()
        assertTrue("Custom exclusion keyword must persist", reloadedKeywords.contains("Investment SIP"))

        val reloadedCards = settings2.getCustomCardSources().first()
        assertEquals(1, reloadedCards.size)
        assertEquals("7224", reloadedCards[0].last4Digits)
        assertEquals("Regalia Gold", reloadedCards[0].cardName)

        val reloadedSortOrder = settings2.getSortOrder().first()
        assertEquals("Sort order must persist", ExpenseSortOrder.AMOUNT_DESC, reloadedSortOrder)
    }

    @Test
    fun testCategoryPersistenceAcrossInstances() = runBlocking {
        val testDir = tempFolder.newFolder("category_test")

        val catRepo1 = FileCategoryRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        val initialCats = catRepo1.getCategories().first()
        assertTrue(initialCats.isNotEmpty())

        // Add a new custom category
        val result = catRepo1.addCategory("Gaming", "🎮", "#7E57C2")
        assertTrue(result.isSuccess)
        val newCatId = result.getOrThrow().id

        // Add tag to the category
        val tagResult = catRepo1.addTagToCategory(newCatId, "steam")
        assertTrue(tagResult.isSuccess)

        // Create new instance pointing to same storage
        val catRepo2 = FileCategoryRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        val reloadedCats = catRepo2.getCategories().first()

        val persistedCat = reloadedCats.find { it.id == newCatId }
        assertNotNull("Custom category must persist", persistedCat)
        assertEquals("Gaming", persistedCat!!.displayName)
        assertEquals("🎮", persistedCat.emoji)
        assertEquals("#7E57C2", persistedCat.colorHex)
        assertTrue("Custom tags must persist", persistedCat.tags.contains("steam"))
    }

    @Test
    fun testSettingsRepositoryDirectlyExposesStateFlowWithoutCollectionRequirement() = runBlocking {
        val testDir = tempFolder.newFolder("vm_exclusion_test")

        // Pre-save custom exclusion keyword in storage
        val settingsRepo1 = FileSettingsRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        settingsRepo1.addExcludedKeyword("JEETHENDRA")

        // Fresh repository representing new app launch
        val settingsRepo2 = FileSettingsRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)

        // Verify getExcludedKeywords() is a StateFlow whose .value is IMMEDIATELY available without collecting
        val initialExcluded = settingsRepo2.getExcludedKeywords().value
        assertTrue(
            "User-added exclusion keyword must be immediately present in getExcludedKeywords().value without collecting",
            initialExcluded.contains("JEETHENDRA")
        )
    }

    @Test
    fun testDeleteOnlyImportedExpensesPreservesManualExpenses() = runBlocking {
        val testDir = tempFolder.newFolder("delete_imported_test")
        val repo = FileExpenseRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        repo.deleteAllExpenses(onlyImported = false, permanently = true) // start empty

        val cat = repo.getExpenses().first().firstOrNull()?.category ?: ExpenseCategory.OTHER
        val manualExpense = Expense(
            amount = 500.0,
            remark = "Lunch cash",
            category = cat,
            date = LocalDate.now(),
            source = "Cash",
            smsId = null,
            originalSms = null
        )
        val importedExpense = Expense(
            amount = 1200.0,
            remark = "Swiggy order",
            category = cat,
            date = LocalDate.now(),
            source = "UPI",
            smsId = "sms_swiggy_1",
            originalSms = "Sent Rs.1200.00 to Swiggy on 27-10-26"
        )
        repo.addExpense(manualExpense)
        repo.addExpense(importedExpense)

        assertFalse("Manual expense should not be imported", manualExpense.isImported)
        assertTrue("SMS expense should be imported", importedExpense.isImported)

        // Delete with onlyImported = true, permanently = false
        repo.deleteAllExpenses(onlyImported = true, permanently = false)

        val expenses = repo.getExpenses().first()
        val manualResult = expenses.find { it.id == manualExpense.id }
        val importedResult = expenses.find { it.id == importedExpense.id }

        assertNotNull(manualResult)
        assertFalse("Manual expense must NOT be deleted when deleting only imported", manualResult!!.isDeleted)

        assertNotNull(importedResult)
        assertTrue("Imported expense MUST be marked deleted", importedResult!!.isDeleted)
    }

    @Test
    fun testDeleteImportedExpensesPermanentlyLeavesNoRestoreEntry() = runBlocking {
        val testDir = tempFolder.newFolder("delete_imported_perm_test")
        val repo = FileExpenseRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        repo.deleteAllExpenses(onlyImported = false, permanently = true) // start empty

        val cat = ExpenseCategory.OTHER
        val manualExpense = Expense(
            amount = 300.0,
            remark = "Tea stall",
            category = cat,
            date = LocalDate.now(),
            source = "Cash"
        )
        val importedExpense = Expense(
            amount = 850.0,
            remark = "Groceries",
            category = cat,
            date = LocalDate.now(),
            source = "Card",
            smsId = "sms_grocery_1",
            originalSms = "Spent Rs.850.00 on Card..."
        )
        repo.addExpense(manualExpense)
        repo.addExpense(importedExpense)

        // Delete permanently only imported
        repo.deleteAllExpenses(onlyImported = true, permanently = true)

        val expenses = repo.getExpenses().first()
        val manualResult = expenses.find { it.id == manualExpense.id }
        val importedResult = expenses.find { it.id == importedExpense.id }

        assertNotNull("Manual expense must remain intact", manualResult)
        assertFalse(manualResult!!.isDeleted)

        // Permanently deleted imported expense must not exist at all (cannot be restored)
        assertTrue("Imported expense must be permanently removed and have no restore entry", importedResult == null)
    }

    @Test
    fun testDeleteSingleExpensePermanentlyLeavesNoRestoreEntry() = runBlocking {
        val testDir = tempFolder.newFolder("delete_single_perm_test")
        val repo = FileExpenseRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        repo.deleteAllExpenses(onlyImported = false, permanently = true) // start empty

        val expense1 = Expense(
            amount = 100.0,
            remark = "Coffee",
            category = ExpenseCategory.OTHER,
            date = LocalDate.now()
        )
        val expense2 = Expense(
            amount = 200.0,
            remark = "Snacks",
            category = ExpenseCategory.OTHER,
            date = LocalDate.now()
        )
        repo.addExpense(expense1)
        repo.addExpense(expense2)

        // Delete expense1 soft (permanently = false)
        repo.deleteExpense(expense1.id, permanently = false)
        // Delete expense2 permanently (permanently = true)
        repo.deleteExpense(expense2.id, permanently = true)

        val expenses = repo.getExpenses().first()
        val exp1Result = expenses.find { it.id == expense1.id }
        val exp2Result = expenses.find { it.id == expense2.id }

        assertNotNull("Soft deleted expense must still exist in deleted state", exp1Result)
        assertTrue(exp1Result!!.isDeleted)

        assertTrue("Permanently deleted expense must be completely erased", exp2Result == null)
    }

    @Test
    fun testUserModifiedCategoryPersistsOnReimportAndSettingsReapply() = runBlocking {
        val testDir = tempFolder.newFolder("user_category_override_test")
        val repo1 = FileExpenseRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        repo1.deleteAllExpenses(onlyImported = false, permanently = true) // start empty

        val sms = "Spent Rs.450.00 On HDFC Bank Card 7224 at Swiggy on 2026-10-28."
        val smsExpense = com.example.expensetracker.data.SmsExpenseParser.parseSms("sms_swiggy_override", sms)!!
        assertEquals("Initial category parsed from Swiggy should be FOOD", ExpenseCategory.FOOD, smsExpense.category)

        // 1. Initial import
        repo1.addExpenses(listOf(smsExpense))
        val initialList = repo1.getExpenses().first()
        val importedExp = initialList.find { it.smsId == "sms_swiggy_override" }
        assertNotNull(importedExp)
        assertEquals(ExpenseCategory.FOOD, importedExp!!.category)
        assertFalse(importedExp.isCategoryUserModified)

        // 2. User manually changes the category to ENTERTAINMENT
        repo1.updateExpenseCategory(importedExp.id, ExpenseCategory.ENTERTAINMENT)
        val afterUpdate = repo1.getExpenses().first()
        val updatedExp = afterUpdate.find { it.id == importedExp.id }
        assertNotNull(updatedExp)
        assertEquals(ExpenseCategory.ENTERTAINMENT, updatedExp!!.category)
        assertTrue(updatedExp.isCategoryUserModified)

        // 3. User modifies settings or tags triggering reapplySettings
        repo1.reapplySettings(
            categories = ExpenseCategory.DEFAULT_CATEGORIES,
            excludedKeywords = emptyList(),
            customCardSources = emptyList()
        )
        val afterReapply = repo1.getExpenses().first()
        val expAfterReapply = afterReapply.find { it.id == importedExp.id }
        assertEquals(
            "reapplySettings must NOT revert user-modified category back to FOOD",
            ExpenseCategory.ENTERTAINMENT,
            expAfterReapply!!.category
        )

        // 4. Persistence across app restarts
        val repo2 = FileExpenseRepository(baseDirectory = testDir, ioDispatcher = Dispatchers.Unconfined)
        val reloadedList = repo2.getExpenses().first()
        val reloadedExp = reloadedList.find { it.smsId == "sms_swiggy_override" }
        assertNotNull(reloadedExp)
        assertEquals("Category must persist across restarts", ExpenseCategory.ENTERTAINMENT, reloadedExp!!.category)
        assertTrue(reloadedExp.isCategoryUserModified)

        // 5. User deletes imported expenses and rescans/re-imports SMS
        repo2.deleteAllExpenses(onlyImported = true, permanently = true)
        val emptyList = repo2.getExpenses().first()
        assertTrue(emptyList.isEmpty())

        // Re-parsing the raw SMS produces default FOOD category
        val freshSmsExpense = com.example.expensetracker.data.SmsExpenseParser.parseSms("sms_swiggy_override", sms)!!
        assertEquals(ExpenseCategory.FOOD, freshSmsExpense.category)

        // Re-importing into the repository should apply the user's persisted override!
        repo2.addExpenses(listOf(freshSmsExpense))
        val reimportedList = repo2.getExpenses().first()
        val reimportedExp = reimportedList.find { it.smsId == "sms_swiggy_override" }
        assertNotNull(reimportedExp)
        assertEquals(
            "Re-importing SMS must persist user-modified category ENTERTAINMENT instead of reverting to FOOD",
            ExpenseCategory.ENTERTAINMENT,
            reimportedExp!!.category
        )
        assertTrue(reimportedExp.isCategoryUserModified)
    }
}
