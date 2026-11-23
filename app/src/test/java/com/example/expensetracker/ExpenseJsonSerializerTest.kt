package com.example.expensetracker

import com.example.expensetracker.data.ExpenseJsonSerializer
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpenseJsonSerializerTest {

    @Test
    fun testSerializationAndDeserializationRoundtrip() {
        val originalList = listOf(
            Expense(
                amount = 1250.75,
                remark = "Dinner with friends",
                category = ExpenseCategory.FOOD,
                date = LocalDate.of(2026, 10, 25),
                source = "HDFC Card"
            ),
            Expense(
                amount = 450.0,
                remark = "Uber Ride",
                category = ExpenseCategory.TRANSPORT,
                date = LocalDate.of(2026, 10, 26),
                source = "UPI",
                originalSms = "Rs 450 debited for Uber",
                smsId = "sms_123"
            )
        )

        val jsonString = ExpenseJsonSerializer.serialize(originalList)
        assertTrue(jsonString.contains("Dinner with friends"))
        assertTrue(jsonString.contains("1250.75"))
        assertTrue(jsonString.contains("Uber Ride"))

        val deserialized = ExpenseJsonSerializer.deserialize(jsonString)
        assertEquals(2, deserialized.size)

        val first = deserialized[0]
        assertEquals(1250.75, first.amount, 0.001)
        assertEquals("Dinner with friends", first.remark)
        assertEquals(ExpenseCategory.FOOD.name, first.category.name)
        assertEquals(LocalDate.of(2026, 10, 25), first.date)
        assertEquals("HDFC Card", first.source)

        val second = deserialized[1]
        assertEquals(450.0, second.amount, 0.001)
        assertEquals("Uber Ride", second.remark)
        assertEquals(ExpenseCategory.TRANSPORT.name, second.category.name)
        assertEquals("sms_123", second.smsId)
        assertEquals("Rs 450 debited for Uber", second.originalSms)
    }

    @Test
    fun testDeserializeGenericOtherAppJsonArray() {
        // Sample JSON format exported by another expense app
        val externalJson = """
            [
              {
                "price": 320.50,
                "description": "Starbucks Coffee",
                "category": "FOOD",
                "date": "2026-10-28"
              },
              {
                "amount": 999.00,
                "title": "Amazon Prime",
                "category": "Entertainment",
                "created_at": "2026-10-27T10:15:30Z"
              }
            ]
        """.trimIndent()

        val parsed = ExpenseJsonSerializer.deserialize(externalJson)
        assertEquals(2, parsed.size)

        assertEquals(320.50, parsed[0].amount, 0.001)
        assertEquals("Starbucks Coffee", parsed[0].remark)
        assertEquals(ExpenseCategory.FOOD.name, parsed[0].category.name)
        assertEquals(LocalDate.of(2026, 10, 28), parsed[0].date)

        assertEquals(999.00, parsed[1].amount, 0.001)
        assertEquals("Amazon Prime", parsed[1].remark)
        assertEquals(ExpenseCategory.ENTERTAINMENT.name, parsed[1].category.name)
        assertEquals(LocalDate.of(2026, 10, 27), parsed[1].date)
    }

    @Test
    fun testDeserializeGenericRootObjectJson() {
        // Root object with "expenses" array
        val externalJson = """
            {
              "expenses": [
                {
                  "amount": "1500",
                  "note": "Electricity Bill",
                  "category": "BILLS",
                  "date": "2026-10-20"
                }
              ]
            }
        """.trimIndent()

        val parsed = ExpenseJsonSerializer.deserialize(externalJson)
        assertEquals(1, parsed.size)
        assertEquals(1500.0, parsed[0].amount, 0.001)
        assertEquals("Electricity Bill", parsed[0].remark)
        assertEquals(ExpenseCategory.BILLS.name, parsed[0].category.name)
    }

    @Test
    fun testDeserializeEmptyOrInvalidJson() {
        val emptyList1 = ExpenseJsonSerializer.deserialize("[]")
        assertTrue(emptyList1.isEmpty())

        val emptyList2 = ExpenseJsonSerializer.deserialize("""[{"amount": 0.0, "remark": "Free item"}]""")
        assertTrue(emptyList2.isEmpty())
    }
}
