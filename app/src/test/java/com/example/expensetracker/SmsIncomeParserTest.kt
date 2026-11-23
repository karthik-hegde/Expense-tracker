package com.example.expensetracker

import com.example.expensetracker.data.SmsIncomeParser
import com.example.expensetracker.model.CustomCardSource
import com.example.expensetracker.model.ExpenseCategory
import com.example.expensetracker.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SmsIncomeParserTest {

    @Test
    fun testSalaryCreditParsing() {
        val sms = "Dear Customer, salary of Rs. 85,000.00 credited to your A/c XX8272 on 30-10-2026 by Tech Mahindra Ltd. Info: SAL/OCT26. Bal: Rs.1,12,450.00"
        val income = SmsIncomeParser.parseSms("inc_1", sms)

        assertNotNull(income)
        assertEquals(85000.00, income!!.amount, 0.001)
        assertEquals(LocalDate.of(2026, 10, 30), income.date)
        assertEquals(ExpenseCategory.SALARY, income.category)
        assertEquals(TransactionType.INCOME, income.type)
        assertEquals("Bank", income.source)
        assertTrue(income.remark.contains("Tech Mahindra", ignoreCase = true))
        assertEquals(sms, income.originalSms)
    }

    @Test
    fun testUpiIncomeParsing() {
        val sms = "Rs.2,500.00 credited to a/c XX1234 on 28-10-26 by UPI/123456789/Ramesh Kumar/Payment for design consultation. UPI Ref 080014864877."
        val income = SmsIncomeParser.parseSms("inc_2", sms)

        assertNotNull(income)
        assertEquals(2500.00, income!!.amount, 0.001)
        assertEquals(LocalDate.of(2026, 10, 28), income.date)
        assertEquals(ExpenseCategory.FREELANCE, income.category)
        assertEquals(TransactionType.INCOME, income.type)
        assertEquals("UPI", income.source)
        assertTrue(income.remark.contains("Ramesh Kumar", ignoreCase = true) || income.remark.contains("design consultation", ignoreCase = true))
        assertEquals(sms, income.originalSms)
    }

    @Test
    fun testDividendInvestmentCredit() {
        val sms = "INR 1,250.00 credited to your Account XXXXXX8272 on 25-10-2026 towards dividend from TATA CONSULTANCY SERVICES. Ref DIV20261025."
        val income = SmsIncomeParser.parseSms("inc_3", sms)

        assertNotNull(income)
        assertEquals(1250.00, income!!.amount, 0.001)
        assertEquals(LocalDate.of(2026, 10, 25), income.date)
        assertEquals(ExpenseCategory.INVESTMENTS, income.category)
        assertEquals(TransactionType.INCOME, income.type)
        assertEquals("Bank", income.source)
        assertTrue(income.remark.contains("TATA CONSULTANCY", ignoreCase = true))
    }

    @Test
    fun testRefundCreditParsing() {
        val sms = "Refund of Rs 799.00 processed and credited to your ICICI Bank Card XX5004 on 22-10-2026 for Amazon Pay return."
        val income = SmsIncomeParser.parseSms("inc_4", sms)

        assertNotNull(income)
        assertEquals(799.00, income!!.amount, 0.001)
        assertEquals(LocalDate.of(2026, 10, 22), income.date)
        assertEquals(ExpenseCategory.REFUNDS, income.category)
        assertEquals(TransactionType.INCOME, income.type)
        assertEquals("Card", income.source)
        assertTrue(income.remark.contains("Amazon", ignoreCase = true))
    }

    @Test
    fun testRentalCreditParsing() {
        val sms = "Rs 18,000.00 received in your A/c XX4321 on 05-10-2026 from Suresh Patel for flat rent October. UPI Ref 9988776655."
        val income = SmsIncomeParser.parseSms("inc_5", sms)

        assertNotNull(income)
        assertEquals(18000.00, income!!.amount, 0.001)
        assertEquals(LocalDate.of(2026, 10, 5), income.date)
        assertEquals(ExpenseCategory.RENTAL, income.category)
        assertEquals(TransactionType.INCOME, income.type)
    }

    @Test
    fun testCustomCardSourceMatching() {
        val sms = "Cashback of Rs. 250.00 credited to your Card 9999 on 15-10-2026."
        val customSources = listOf(
            CustomCardSource(last4Digits = "9999", cardName = "Regalia Gold")
        )
        val income = SmsIncomeParser.parseSms("inc_6", sms, customCardSources = customSources)

        assertNotNull(income)
        assertEquals(250.00, income!!.amount, 0.001)
        assertEquals("Regalia Gold", income.source)
    }

    @Test
    fun testIgnoresDebitAndExpenseSms() {
        val debitSms1 = "Sent Rs.153.00 from Kotak Bank A/c X8272 to JEETHENDRA KUMAR on 27-10-26. UPI Ref 080014864877."
        assertNull("Debits must not be parsed as income", SmsIncomeParser.parseSms("deb_1", debitSms1))

        val debitSms2 = "Spent Rs.955.65 On HDFC Bank Card 7224 At Hindustan Petroleum Co On 2026-10-28."
        assertNull("Card spends must not be parsed as income", SmsIncomeParser.parseSms("deb_2", debitSms2))

        val debitSms3 = "INR 91,500.00 is debited from your Account XXXXXX8272 on 17/10/2026 towards NACH-MUT-ZERODHA BROKING LTD"
        assertNull("NACH debits must not be parsed as income", SmsIncomeParser.parseSms("deb_3", debitSms3))
    }

    @Test
    fun testIgnoresOtpAndSpamMessages() {
        val otpSms = "Your OTP for credit card activation is 492011. Do not share with anyone."
        assertNull(SmsIncomeParser.parseSms("otp_1", otpSms))

        val spamSms = "Get pre-approved personal loan of Rs 5,00,000 credited instantly! Apply now at bit.ly/loan"
        assertNull(SmsIncomeParser.parseSms("spam_1", spamSms))

        val billReminder = "Your credit card bill of Rs 14,200.00 is due on 05-Nov-2026. Pay now."
        assertNull(SmsIncomeParser.parseSms("bill_1", billReminder))
    }
}
