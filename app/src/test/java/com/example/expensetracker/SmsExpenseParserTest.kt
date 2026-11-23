package com.example.expensetracker

import com.example.expensetracker.data.SmsExpenseParser
import com.example.expensetracker.model.CustomCardSource
import com.example.expensetracker.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SmsExpenseParserTest {

    @Test
    fun testUpiTransactionParsing() {
        val sms = "Sent Rs.153.00 from Kotak Bank A/c X8272 to JEETHENDRA KUMAR on 27-10-26. UPI Ref 080014864877. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
        val expense = SmsExpenseParser.parseSms("sms_1", sms)

        assertNotNull(expense)
        assertEquals(153.00, expense!!.amount, 0.001)
        assertEquals(LocalDate.of(2026, 10, 27), expense.date)
        assertEquals(ExpenseCategory.UNKNOWN, expense.category)
        assertEquals("UPI", expense.source)
        assertTrue(expense.remark.contains("JEETHENDRA KUMAR", ignoreCase = true))
        assertEquals(sms, expense.originalSms)
    }

    @Test
    fun testHdfcCreditCardParsing() {
        val sms = "Spent Rs.955.65 On HDFC Bank Card 7224 At Hindustan Petroleum Co On 2026-10-28:07:55:58.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 7224 to 7308080808"
        val expense = SmsExpenseParser.parseSms("sms_2", sms)

        assertNotNull(expense)
        assertEquals(955.65, expense!!.amount, 0.001)
        assertEquals(LocalDate.of(2026, 10, 28), expense.date)
        assertEquals(ExpenseCategory.TRANSPORT, expense.category)
        assertEquals("Card", expense.source)
        assertTrue(expense.remark.contains("Hindustan Petroleum Co", ignoreCase = true))
        assertEquals(sms, expense.originalSms)
    }

    @Test
    fun testIciciCreditCardParsing() {
        val sms = "INR 1,362.03 spent using ICICI Bank Card XX5004 on 25-Oct-26 on AMAZON PAY IN E. Avl Limit: INR 8,53,657.39. If not you, call 1800 2662/SMS BLOCK 5004 to 9215676766."
        val expense = SmsExpenseParser.parseSms("sms_3", sms)

        assertNotNull(expense)
        assertEquals(1362.03, expense!!.amount, 0.001)
        assertEquals(LocalDate.of(2026, 10, 25), expense.date)
        assertEquals(ExpenseCategory.SHOPPING, expense.category)
        assertEquals("Card", expense.source)
        assertTrue(expense.remark.contains("AMAZON PAY IN E", ignoreCase = true))
        assertEquals(sms, expense.originalSms)
    }

    @Test
    fun testIgnoredNachMandate() {
        val sms = "INR 91,500.00 is debited from your Account XXXXXX8272 on 17/10/2026 towards NACH-MUT-ZERODHA BROKING LTD Kotak Bank"
        val expense = SmsExpenseParser.parseSms("sms_4", sms)

        assertNotNull("NACH/Mandate transaction must be parsed as deleted expense", expense)
        assertTrue(expense!!.isDeleted)
    }

    @Test
    fun testIgnoredCreditAndOtp() {
        val creditSms = "Rs 5,000.00 credited to your Account X1234 on 20-10-26 by UPI transfer."
        assertNull(SmsExpenseParser.parseSms("sms_5", creditSms))

        val otpSms = "Your OTP for transaction of Rs 500 at Swiggy is 492011. Do not share with anyone."
        assertNull(SmsExpenseParser.parseSms("sms_6", otpSms))
    }

    @Test
    fun testDuplicateDetection() {
        val sms1 = "Sent Rs.153.00 from Kotak Bank A/c X8272 to JEETHENDRA KUMAR on 27-10-26. UPI Ref 080014864877."
        val sms2 = "Spent Rs.955.65 On HDFC Bank Card 7224 At Hindustan Petroleum Co On 2026-10-28:07:55:58."

        val expense1 = SmsExpenseParser.parseSms("sms_1", sms1)!!
        val expense2 = SmsExpenseParser.parseSms("sms_2", sms2)!!
        val duplicateExpense1 = SmsExpenseParser.parseSms("sms_1", sms1)!!

        val batch = listOf(expense1, expense2, duplicateExpense1)
        val distinct = batch.distinctBy { it.smsId ?: (it.originalSms ?: "${it.date}_${it.amount}_${it.remark.lowercase()}") }

        assertEquals(2, distinct.size)
        assertTrue(distinct.contains(expense1))
        assertTrue(distinct.contains(expense2))
    }

    @Test
    fun testIgnoredCredPayment() {
        val credSms = """
            Sent Rs.1612.00
            From HDFC Bank A/C *5569
            To CRED
            On 05/08/26
            Ref 658392425709
            Not You?
            Call 18002586161/SMS BLOCK UPI to
            7308080808
        """.trimIndent()
        val expense = SmsExpenseParser.parseSms("sms_cred", credSms)
        assertNotNull("Payments to CRED must be parsed as deleted expense", expense)
        assertTrue(expense!!.isDeleted)
    }

    @Test
    fun testIgnoredCheqPayment() {
        val cheqSms = """
            Sent Rs.45109.00
            From HDFC Bank A/C *5569
            To CheQ
            On 05/08/26
            Ref 161459417737
            Not You?
            Call 18002586161/SMS BLOCK UPI to
            7308080808
        """.trimIndent()
        val expense = SmsExpenseParser.parseSms("sms_cheq", cheqSms)
        assertNotNull("Payments to CheQ must be parsed as deleted expense", expense)
        assertTrue(expense!!.isDeleted)
    }

    @Test
    fun testCreditCardNotIgnored() {
        val ccSms = "Rs 450.00 spent on ICICI Bank Credit Card XX1234 at Starbucks on 20-10-26."
        assertNotNull(SmsExpenseParser.parseSms("sms_cc", ccSms))
    }

    @Test
    fun testCustomExcludedTexts() {
        val sms = "Sent Rs.25000.00 from HDFC Bank to House Rent on 01-10-26."
        val customExclusions = listOf("House Rent", "Landlord")

        val withoutExclusion = SmsExpenseParser.parseSms("sms_rent", sms)
        assertNotNull(withoutExclusion)

        val withExclusion = SmsExpenseParser.parseSms("sms_rent", sms, customExcludedTexts = customExclusions)
        assertNotNull(withExclusion)
        assertTrue("SMS matching custom exclusion keyword must be marked as deleted", withExclusion!!.isDeleted)
    }

    @Test
    fun testCustomCategoryTagAutoCategorization() {
        val customFitness = ExpenseCategory(
            id = "fitness_id",
            name = "FITNESS",
            displayName = "Fitness & Gym",
            emoji = "🏋️",
            colorHex = "#4DB6AC",
            tags = listOf("cult.fit", "gold's gym", "anytime fitness")
        )
        val categories = ExpenseCategory.DEFAULT_CATEGORIES + customFitness

        val cultSms = "Sent Rs.2,499.00 from Kotak Bank to CULT.FIT on 20-10-26. UPI Ref 112233."
        val expense = SmsExpenseParser.parseSms("sms_cult", cultSms, availableCategories = categories)

        assertNotNull(expense)
        assertEquals(customFitness.id, expense!!.category.id)
        assertEquals("Fitness & Gym", expense.category.displayName)
        assertEquals("UPI", expense.source)
    }

    @Test
    fun testHdfcCardBigBasketSourceParsing() {
        val sms = "Spent Rs.183.67 On HDFC Bank Card 9255 At WWWBIGBASKETCOM On 2026-10-24:12:56:20.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 9255 to 7308080808"
        val expense = SmsExpenseParser.parseSms("sms_bb", sms)

        assertNotNull(expense)
        assertEquals(183.67, expense!!.amount, 0.001)
        assertEquals(LocalDate.of(2026, 10, 24), expense.date)
        assertEquals("Card", expense.source)
        assertTrue(expense.remark.contains("WWWBIGBASKETCOM", ignoreCase = true))
    }

    @Test
    fun testCustomCardSourceMatching() {
        val sms = "Spent Rs.955.65 On HDFC Bank Card 7224 At Hindustan Petroleum Co On 2026-10-28:07:55:58.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 7224 to 7308080808"
        val customCards = listOf(CustomCardSource(last4Digits = "7224", cardName = "Regalia"))

        val expense = SmsExpenseParser.parseSms("sms_card", sms, customCardSources = customCards)
        assertNotNull(expense)
        assertEquals("Regalia", expense!!.source)
    }

    @Test
    fun testCustomCardSourceMaskedDigits() {
        val sms = "INR 1,362.03 spent using ICICI Bank Card XX5004 on 25-Oct-26 on AMAZON PAY IN E. Avl Limit: INR 8,53,657.39. If not you, call 1800 2662/SMS BLOCK 5004 to 9215676766."
        val customCards = listOf(
            CustomCardSource(last4Digits = "5004", cardName = "Amazon Pay ICICI"),
            CustomCardSource(last4Digits = "7224", cardName = "Regalia")
        )

        val expense = SmsExpenseParser.parseSms("sms_icici", sms, customCardSources = customCards)
        assertNotNull(expense)
        assertEquals("Amazon Pay ICICI", expense!!.source)
    }

    @Test
    fun testCustomCardSourceFallbackWhenUnmatched() {
        val sms = "Spent Rs.183.67 On HDFC Bank Card 9255 At WWWBIGBASKETCOM On 2026-10-24:12:56:20."
        val customCards = listOf(CustomCardSource(last4Digits = "7224", cardName = "Regalia"))

        val expense = SmsExpenseParser.parseSms("sms_card_fallback", sms, customCardSources = customCards)
        assertNotNull(expense)
        assertEquals("Card", expense!!.source)
    }
}
