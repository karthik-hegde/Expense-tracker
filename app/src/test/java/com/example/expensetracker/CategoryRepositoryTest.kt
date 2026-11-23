package com.example.expensetracker

import com.example.expensetracker.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRepositoryTest {

    @Test
    fun testTagUniquenessValidation() {
        val cat1 = ExpenseCategory(
            id = "cat_1",
            name = "FOOD",
            displayName = "Food & Dining",
            tags = listOf("swiggy", "zomato")
        )
        val cat2 = ExpenseCategory(
            id = "cat_2",
            name = "SHOPPING",
            displayName = "Shopping",
            tags = listOf("amazon", "flipkart")
        )

        val categories = listOf(cat1, cat2)

        // Attempting to add "swiggy" to Shopping should be rejected
        val newTag = "SWIGGY"
        val cleanTag = newTag.trim().lowercase()

        val conflicting = categories.find { it.id != cat2.id && it.tags.any { t -> t.equals(cleanTag, ignoreCase = true) } }
        assertTrue("Conflict should be detected with Food & Dining", conflicting != null)
        assertEquals(cat1.id, conflicting?.id)

        // Adding unique tag "myntra" should be permitted
        val uniqueTag = "myntra"
        val noConflict = categories.find { it.id != cat2.id && it.tags.any { t -> t.equals(uniqueTag, ignoreCase = true) } }
        assertFalse("Unique tag should have no conflict", noConflict != null)
    }

    @Test
    fun testCategoryCreationDefaults() {
        val cat = ExpenseCategory(
            name = "PETS",
            displayName = "Pets",
            emoji = "🐾",
            colorHex = "#4DB6AC",
            tags = listOf("vet", "pet food")
        )

        assertEquals("Pets", cat.displayName)
        assertEquals("🐾", cat.emoji)
        assertEquals(2, cat.tags.size)
        assertTrue(cat.tags.contains("vet"))
    }

    @Test
    fun testReapplySettingsReclassifiesExistingSmsExpense() {
        val originalSms = "Sent Rs.153.00 from Kotak Bank A/c X8272 to JEETHENDRA KUMAR on 27-10-26. UPI Ref 080014864877."
        val initialExpense = com.example.expensetracker.data.SmsExpenseParser.parseSms("sms_1", originalSms)!!
        assertEquals(ExpenseCategory.UNKNOWN, initialExpense.category)

        // User now adds tag "jeethendra" to a custom category
        val personalCategory = ExpenseCategory(
            id = "cat_personal",
            name = "PERSONAL",
            displayName = "Personal Transfers",
            tags = listOf("jeethendra")
        )
        val updatedCategories = ExpenseCategory.DEFAULT_CATEGORIES + personalCategory

        val reclassifiedCategory = com.example.expensetracker.data.SmsExpenseParser.determineCategoryFromTags(
            body = initialExpense.originalSms!!,
            remark = initialExpense.remark,
            availableCategories = updatedCategories
        )
        assertEquals(personalCategory.id, reclassifiedCategory.id)
        assertEquals("Personal Transfers", reclassifiedCategory.displayName)
    }

    @Test
    fun testReapplySettingsExcludesNewlyExcludedExpense() {
        val originalSms = "Sent Rs.25000.00 from HDFC Bank to House Rent on 01-10-26."
        val initialExpense = com.example.expensetracker.data.SmsExpenseParser.parseSms("sms_rent", originalSms)!!
        assertFalse(com.example.expensetracker.data.SmsExpenseParser.isIgnoredMessage(initialExpense.originalSms!!, emptyList()))

        // User now adds "House Rent" to exclusion keywords
        val updatedExclusions = listOf("House Rent")
        assertTrue(com.example.expensetracker.data.SmsExpenseParser.isIgnoredMessage(initialExpense.originalSms, updatedExclusions))
    }
}
