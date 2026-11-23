package com.example.expensetracker

import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class GenerateScreenshotsTest {

    private val width = 760
    private val height = 1560

    @Test
    fun generateAllScreenshots() {
        val outputDir = File("../docs/screenshots").apply { mkdirs() }

        generateExpenseScreen(File(outputDir, "expense_screen.png"))
        generateIncomeScreen(File(outputDir, "income_screen.png"))
        generateNetReportScreen(File(outputDir, "net_report.png"))
        generateMonthlyChartScreen(File(outputDir, "monthly_chart.png"))
        generateItemDetailsDialogScreen(File(outputDir, "item_details_dialog.png"))
        generateSettingsScreen(File(outputDir, "settings_screen.png"))

        assertTrue(File(outputDir, "expense_screen.png").exists())
        assertTrue(File(outputDir, "income_screen.png").exists())
        assertTrue(File(outputDir, "net_report.png").exists())
        assertTrue(File(outputDir, "monthly_chart.png").exists())
        assertTrue(File(outputDir, "item_details_dialog.png").exists())
        assertTrue(File(outputDir, "settings_screen.png").exists())
    }

    private fun createCanvas(): Pair<BufferedImage, Graphics2D> {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // Outer Dark Border / Background
        g.color = Color(15, 23, 42)
        g.fillRect(0, 0, width, height)

        // Device Outer Frame
        g.color = Color(30, 41, 59)
        g.fillRoundRect(10, 10, width - 20, height - 20, 72, 72)
        g.color = Color(51, 65, 85)
        g.stroke = BasicStroke(6f)
        g.drawRoundRect(10, 10, width - 20, height - 20, 72, 72)

        // Screen Body Canvas
        g.color = Color(248, 250, 252)
        g.fillRoundRect(24, 24, width - 48, height - 48, 60, 60)

        // Status Bar Notch
        g.color = Color(15, 23, 42)
        g.fillRoundRect(width / 2 - 100, 36, 200, 36, 18, 18)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.color = Color(51, 65, 85)
        g.drawString("9:41", 64, 62)

        return Pair(image, g)
    }

    private fun drawNavBar(g: Graphics2D, activeTab: Int) {
        val y = 1360
        g.color = Color(30, 41, 59)
        g.fillRoundRect(48, y, width - 96, 104, 32, 32)

        val labels = listOf("💳 Tracker", "📊 Analytics", "⚙️ Settings")
        for (i in labels.indices) {
            val x = 90 + i * 210
            if (i == activeTab) {
                g.color = Color(56, 189, 248)
                g.font = Font("SansSerif", Font.BOLD, 24)
            } else {
                g.color = Color(148, 163, 184)
                g.font = Font("SansSerif", Font.PLAIN, 24)
            }
            g.drawString(labels[i], x, y + 62)
        }
    }

    private fun generateExpenseScreen(file: File) {
        val (image, g) = createCanvas()

        // Top App Bar
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 40)
        g.drawString("Expense Tracker", 48, 136)
        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 22)
        g.drawString("Monthly Budget & Expense Manager", 48, 168)

        // Analytics Icon
        g.color = Color(241, 245, 249)
        g.fillOval(640, 100, 64, 64)
        g.color = Color(37, 99, 235)
        g.fillArc(648, 108, 48, 48, 90, -180)
        g.color = Color(59, 130, 246)
        g.fillArc(648, 108, 48, 48, 90, 180)

        // Month Navigator
        g.color = Color(241, 245, 249)
        g.fillRoundRect(48, 192, width - 96, 72, 20, 20)
        g.color = Color(37, 99, 235)
        g.font = Font("SansSerif", Font.BOLD, 28)
        g.drawString("‹", 80, 238)
        g.drawString("›", 660, 238)
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 26)
        g.drawString("November 2026", 260, 238)

        // Main Summary Card
        g.color = Color(209, 227, 255)
        g.fillRoundRect(48, 284, width - 96, 272, 32, 32)
        g.color = Color(30, 58, 138)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("Total Spent (November 2026)", 80, 336)

        // Entries Badge
        g.color = Color(30, 64, 175, 30)
        g.fillRoundRect(520, 308, 132, 40, 16, 16)
        g.color = Color(30, 58, 138)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("4 entries", 540, 336)

        // Amount
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 56)
        g.drawString("₹14,850.00", 80, 410)

        // Net Savings Badge
        g.color = Color(220, 252, 231)
        g.fillRoundRect(420, 370, 232, 48, 16, 16)
        g.color = Color(134, 239, 172)
        g.stroke = BasicStroke(2f)
        g.drawRoundRect(420, 370, 232, 48, 16, 16)
        g.color = Color(22, 101, 52)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("Net: +₹60,150.00", 438, 402)

        // Category Pills
        g.color = Color(255, 255, 255, 230)
        g.fillRoundRect(80, 460, 270, 64, 16, 16)
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("🛍️ Shopping  ₹6,500", 96, 500)

        g.fillRoundRect(370, 460, 280, 64, 16, 16)
        g.drawString("🍔 Food  ₹4,850", 386, 500)

        // Filter Bar
        g.color = Color.WHITE
        g.fillRoundRect(48, 580, 260, 64, 20, 20)
        g.color = Color(203, 213, 225)
        g.stroke = BasicStroke(2f)
        g.drawRoundRect(48, 580, 260, 64, 20, 20)
        g.color = Color(51, 65, 85)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("≡ Sort: Latest Date", 72, 620)

        g.color = Color(30, 41, 59)
        g.fillRoundRect(324, 580, 80, 64, 20, 20)
        g.color = Color.WHITE
        g.drawString("All", 348, 620)

        g.fillRoundRect(420, 580, 240, 64, 20, 20)
        g.color = Color(203, 213, 225)
        g.drawRoundRect(420, 580, 240, 64, 20, 20)
        g.color = Color(51, 65, 85)
        g.drawString("🍔 Food & Dining", 436, 620)

        // Active Transactions
        drawTransactionRow(g, 668, "🛍️", "AMAZON PAY IN E", "Card • Nov 25, 2026", "-₹1,362.03", Color(220, 38, 38), Color(252, 231, 243))
        drawTransactionRow(g, 808, "🚗", "Hindustan Petroleum Co", "Regalia Card • Nov 24, 2026", "-₹955.65", Color(220, 38, 38), Color(224, 242, 254))

        // Exclusions Section Header
        g.color = Color(71, 85, 105)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("Deleted Expenses & Exclusions (2)", 48, 980)

        // Excluded Items
        drawExcludedRow(g, 1000, "💡", "House Rent", "Bank • Nov 01, 2026", "-₹25,000.00", "🚫 Matches exclusion filter: \"House Rent\"", Color(254, 243, 199))
        drawExcludedRow(g, 1160, "📈", "NACH-MUT-ZERODHA BROKING", "Bank • Nov 17, 2026", "-₹91,500.00", "🚫 Excluded by SMS filter: Auto-debit (nach)", Color(220, 252, 231))

        drawNavBar(g, 0)
        ImageIO.write(image, "PNG", file)
    }

    private fun generateIncomeScreen(file: File) {
        val (image, g) = createCanvas()

        // Top App Bar
        g.color = Color(22, 101, 52)
        g.font = Font("SansSerif", Font.BOLD, 40)
        g.drawString("Income Tracker", 48, 136)
        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 22)
        g.drawString("Monthly Income & Earnings Manager", 48, 168)

        // Analytics Icon
        g.color = Color(241, 245, 249)
        g.fillOval(640, 100, 64, 64)
        g.color = Color(46, 125, 50)
        g.fillArc(648, 108, 48, 48, 90, -180)
        g.color = Color(74, 222, 128)
        g.fillArc(648, 108, 48, 48, 90, 180)

        // Month Navigator
        g.color = Color(241, 245, 249)
        g.fillRoundRect(48, 192, width - 96, 72, 20, 20)
        g.color = Color(22, 101, 52)
        g.font = Font("SansSerif", Font.BOLD, 28)
        g.drawString("‹", 80, 238)
        g.drawString("›", 660, 238)
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 26)
        g.drawString("November 2026", 260, 238)

        // Main Summary Card (Income Green)
        g.color = Color(220, 252, 231)
        g.fillRoundRect(48, 284, width - 96, 272, 32, 32)
        g.color = Color(22, 101, 52)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("Total Income (November 2026)", 80, 336)

        // Entries Badge
        g.color = Color(22, 101, 52, 30)
        g.fillRoundRect(520, 308, 132, 40, 16, 16)
        g.color = Color(22, 101, 52)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("3 entries", 540, 336)

        // Amount
        g.color = Color(20, 83, 45)
        g.font = Font("SansSerif", Font.BOLD, 56)
        g.drawString("+₹85,000.00", 80, 410)

        // Net Savings Badge
        g.color = Color.WHITE
        g.fillRoundRect(420, 370, 232, 48, 16, 16)
        g.color = Color(134, 239, 172)
        g.stroke = BasicStroke(2f)
        g.drawRoundRect(420, 370, 232, 48, 16, 16)
        g.color = Color(21, 128, 61)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("Net: +₹70,150.00", 438, 402)

        // Category Pills
        g.color = Color(255, 255, 255, 230)
        g.fillRoundRect(80, 460, 270, 64, 16, 16)
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("💼 Salary  ₹75,000", 96, 500)

        g.fillRoundRect(370, 460, 280, 64, 16, 16)
        g.drawString("💻 Freelance  ₹10,000", 386, 500)

        // Active Income Transactions
        drawTransactionRow(g, 668, "💼", "TECH CORP PVT LTD - Salary", "Bank • Nov 30, 2026", "+₹75,000.00", Color(22, 101, 52), Color(220, 252, 231))
        drawTransactionRow(g, 808, "💻", "FREELANCE CLIENT - Design", "UPI • Nov 28, 2026", "+₹10,000.00", Color(22, 101, 52), Color(224, 242, 254))

        // Exclusions Header
        g.color = Color(71, 85, 105)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("Deleted Income Entries & Exclusions (1)", 48, 980)

        drawExcludedRow(g, 1000, "📈", "ZERODHA BROKING LTD", "Bank • Nov 25, 2026", "+₹3,450.00", "🚫 Matches exclusion filter: \"Dividend\"", Color(254, 243, 199))

        drawNavBar(g, 0)
        ImageIO.write(image, "PNG", file)
    }

    private fun generateNetReportScreen(file: File) {
        val (image, g) = createCanvas()

        // Top App Bar
        g.color = Color(37, 99, 235)
        g.font = Font("SansSerif", Font.BOLD, 36)
        g.drawString("←", 48, 136)
        g.color = Color(15, 23, 42)
        g.drawString("Net Financial Analytics", 96, 136)
        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 22)
        g.drawString("November 2026 breakdown", 96, 168)

        // Mode Chips
        g.color = Color(241, 245, 249)
        g.fillRoundRect(48, 192, width - 96, 76, 24, 24)
        g.color = Color.WHITE
        g.fillRoundRect(60, 200, 180, 60, 16, 16)
        g.color = Color(71, 85, 105)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("Expenses", 96, 238)

        g.color = Color.WHITE
        g.fillRoundRect(250, 200, 160, 60, 16, 16)
        g.color = Color(71, 85, 105)
        g.drawString("Income", 286, 238)

        g.color = Color(21, 101, 192)
        g.fillRoundRect(420, 200, 276, 60, 16, 16)
        g.color = Color.WHITE
        g.drawString("Net Report", 480, 238)

        // Net Summary Card
        g.color = Color(232, 245, 233)
        g.fillRoundRect(48, 288, width - 96, 248, 32, 32)
        g.color = Color(27, 94, 32)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("Net Balance (November 2026)", 80, 336)

        g.font = Font("SansSerif", Font.BOLD, 52)
        g.drawString("+₹60,150.00", 80, 400)

        // Income & Expense Sub-Cards
        g.color = Color.WHITE
        g.fillRoundRect(80, 430, 280, 76, 20, 20)
        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 20)
        g.drawString("Total Income", 96, 460)
        g.color = Color(46, 125, 50)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("+₹75,000.00", 96, 492)

        g.color = Color.WHITE
        g.fillRoundRect(380, 430, 280, 76, 20, 20)
        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 20)
        g.drawString("Total Expenses", 396, 460)
        g.color = Color(220, 38, 38)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("-₹14,850.00", 396, 492)

        // Monthly Net Trend Bar Chart
        g.color = Color(241, 245, 249)
        g.fillRoundRect(48, 560, width - 96, 370, 32, 32)
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 26)
        g.drawString("Monthly Net Trend (Income vs Expenses)", 80, 604)
        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 20)
        g.drawString("Jan - Dec monthly net savings or deficit", 80, 632)

        // Bars
        val months = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
        val values = listOf(45, 52, -8, 38, 61, 49, -12, 55, 67, 42, 60, 0)

        for (i in months.indices) {
            val bx = 80 + i * 50
            val valNet = values[i]
            val barH = Math.abs(valNet) * 2

            if (valNet > 0) {
                g.color = Color(46, 125, 50)
                g.fillRoundRect(bx, 840 - barH, 24, barH, 6, 6)
                g.font = Font("SansSerif", Font.BOLD, 14)
                g.drawString("+$valNet", bx - 4, 830 - barH)
            } else if (valNet < 0) {
                g.color = Color(220, 38, 38)
                g.fillRoundRect(bx, 840, 24, barH, 6, 6)
                g.font = Font("SansSerif", Font.BOLD, 14)
                g.drawString("$valNet", bx - 4, 860 + barH)
            } else {
                g.color = Color(203, 213, 225)
                g.fillRect(bx, 838, 24, 6)
            }

            g.color = Color(15, 23, 42)
            g.font = Font("SansSerif", Font.BOLD, 20)
            g.drawString(months[i], bx + 6, 880)
        }

        // Transactions List in Net Report
        g.color = Color(71, 85, 105)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("Transactions in Net Report (6)", 48, 970)

        drawTransactionRow(g, 990, "💼", "TECH CORP PVT LTD - Salary", "Bank • 30 Nov 2026", "+₹75,000.00", Color(46, 125, 50), Color(220, 252, 231))
        drawTransactionRow(g, 1130, "🛍️", "AMAZON PAY IN E", "Card • 25 Nov 2026", "-₹1,362.03", Color(220, 38, 38), Color(252, 231, 243))

        drawNavBar(g, 1)
        ImageIO.write(image, "PNG", file)
    }

    private fun generateMonthlyChartScreen(file: File) {
        val (image, g) = createCanvas()

        // Top App Bar
        g.color = Color(37, 99, 235)
        g.font = Font("SansSerif", Font.BOLD, 36)
        g.drawString("←", 48, 136)
        g.color = Color(15, 23, 42)
        g.drawString("Expense Analytics", 96, 136)
        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 22)
        g.drawString("November 2026 breakdown", 96, 168)

        // Pie Chart Card
        g.color = Color(241, 245, 249)
        g.fillRoundRect(48, 200, width - 96, 480, 32, 32)
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 26)
        g.drawString("Monthly Category Distribution", 80, 244)

        // Donut / Pie Chart Slices
        val cx = 380
        val cy = 400
        val r = 130

        g.color = Color(236, 72, 153)
        g.fill(Arc2D.Float((cx - r).toFloat(), (cy - r).toFloat(), (r * 2).toFloat(), (r * 2).toFloat(), 90f, -158f, Arc2D.PIE))

        g.color = Color(245, 158, 11)
        g.fill(Arc2D.Float((cx - r).toFloat(), (cy - r).toFloat(), (r * 2).toFloat(), (r * 2).toFloat(), -68f, -118f, Arc2D.PIE))

        g.color = Color(56, 189, 248)
        g.fill(Arc2D.Float((cx - r).toFloat(), (cy - r).toFloat(), (r * 2).toFloat(), (r * 2).toFloat(), -186f, -48f, Arc2D.PIE))

        g.color = Color(139, 92, 246)
        g.fill(Arc2D.Float((cx - r).toFloat(), (cy - r).toFloat(), (r * 2).toFloat(), (r * 2).toFloat(), -234f, -36f, Arc2D.PIE))

        // Donut Inner Hole
        g.color = Color(241, 245, 249)
        g.fillOval(cx - 56, cy - 56, 112, 112)
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("₹14.8k", cx - 32, cy + 8)

        // Selected Category Highlight Badge
        g.color = Color(252, 231, 243)
        g.fillRoundRect(80, 580, 600, 60, 20, 20)
        g.color = Color(236, 72, 153)
        g.stroke = BasicStroke(2f)
        g.drawRoundRect(80, 580, 600, 60, 20, 20)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("🛍️ Shopping — ₹6,500.00 (43.8%)", 100, 618)

        // Category Breakup Items
        g.color = Color(71, 85, 105)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("Category Breakup (4)", 48, 715)
        g.color = Color(37, 99, 235)
        g.drawString("Total: ₹14,850.00", 480, 715)

        drawCategoryProgressRow(g, 735, "🛍️", "Shopping", "2 transactions", "₹6,500.00", "43.8%", 0.44f, Color(236, 72, 153), Color(252, 231, 243))
        drawCategoryProgressRow(g, 885, "🍔", "Food & Dining", "1 transaction", "₹4,850.00", "32.7%", 0.33f, Color(245, 158, 11), Color(254, 243, 199))
        drawCategoryProgressRow(g, 1035, "🚗", "Transport", "1 transaction", "₹2,000.00", "13.5%", 0.14f, Color(56, 189, 248), Color(224, 242, 254))

        drawNavBar(g, 1)
        ImageIO.write(image, "PNG", file)
    }

    private fun generateItemDetailsDialogScreen(file: File) {
        val (image, g) = createCanvas()

        // Background Screen (Dimmed)
        g.color = Color(0, 0, 0, 140)
        g.fillRect(24, 24, width - 48, height - 48)

        // Centered Dialog
        val dx = 56
        val dy = 240
        val dw = width - 112
        val dh = 1040

        g.color = Color.WHITE
        g.fillRoundRect(dx, dy, dw, dh, 48, 48)
        g.color = Color(203, 213, 225)
        g.stroke = BasicStroke(3f)
        g.drawRoundRect(dx, dy, dw, dh, 48, 48)

        // Dialog Header
        g.color = Color(254, 243, 199)
        g.fillOval(dx + 40, dy + 40, 80, 80)
        g.font = Font("SansSerif", Font.PLAIN, 36)
        g.drawString("🍔", dx + 60, dy + 92)

        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 36)
        g.drawString("Expense Details", dx + 140, dy + 92)

        // Amount & Remark
        g.color = Color(37, 99, 235)
        g.font = Font("SansSerif", Font.BOLD, 48)
        g.drawString("-₹4,850.00", dx + 40, dy + 200)

        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 28)
        g.drawString("Swiggy Gourmet Order", dx + 40, dy + 248)

        // Source & Date Cards
        g.color = Color(248, 250, 252)
        g.fillRoundRect(dx + 40, dy + 280, 260, 80, 16, 16)
        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 20)
        g.drawString("Source", dx + 60, dy + 312)
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("UPI (Kotak)", dx + 60, dy + 344)

        g.color = Color(248, 250, 252)
        g.fillRoundRect(dx + 320, dy + 280, 280, 80, 16, 16)
        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 20)
        g.drawString("Date", dx + 340, dy + 312)
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("24 Nov 2026", dx + 340, dy + 344)

        // Original SMS Box
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("Original Bank SMS", dx + 40, dy + 410)
        g.color = Color(241, 245, 249)
        g.fillRoundRect(dx + 40, dy + 424, dw - 80, 100, 16, 16)
        g.color = Color(71, 85, 105)
        g.font = Font("SansSerif", Font.PLAIN, 20)
        g.drawString("Sent Rs.4850.00 from Kotak Bank A/c X8272", dx + 60, dy + 462)
        g.drawString("to SWIGGY on 24-11-26. UPI Ref 080014864877.", dx + 60, dy + 498)

        // Exact Exclusion Reason Box
        g.color = Color(220, 38, 38)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("Exclusion Reason", dx + 40, dy + 570)

        g.color = Color(254, 242, 242)
        g.fillRoundRect(dx + 40, dy + 584, dw - 80, 76, 16, 16)
        g.color = Color(252, 165, 165)
        g.drawRoundRect(dx + 40, dy + 584, dw - 80, 76, 16, 16)
        g.color = Color(153, 27, 27)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("🚫 Matches exclusion filter: \"Swiggy\"", dx + 60, dy + 632)

        // Category Customization Chips
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("Category", dx + 40, dy + 700)
        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 20)
        g.drawString("Tap a category below to change:", dx + 40, dy + 730)

        // Chips
        g.color = Color(254, 243, 199)
        g.fillRoundRect(dx + 40, dy + 750, 240, 60, 16, 16)
        g.color = Color(37, 99, 235)
        g.drawRoundRect(dx + 40, dy + 750, 240, 60, 16, 16)
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("🍔 Food & Dining", dx + 60, dy + 788)

        g.color = Color(241, 245, 249)
        g.fillRoundRect(dx + 300, dy + 750, 180, 60, 16, 16)
        g.color = Color(51, 65, 85)
        g.drawString("🛍️ Shopping", dx + 320, dy + 788)

        // Action Buttons
        g.color = Color(37, 99, 235)
        g.fillRoundRect(dx + 360, dy + 940, 220, 68, 16, 16)
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("Save Category", dx + 386, dy + 982)

        ImageIO.write(image, "PNG", file)
    }

    private fun generateSettingsScreen(file: File) {
        val (image, g) = createCanvas()

        // Top App Bar
        g.color = Color(37, 99, 235)
        g.font = Font("SansSerif", Font.BOLD, 36)
        g.drawString("←", 48, 136)
        g.color = Color(15, 23, 42)
        g.drawString("Settings", 96, 136)

        // Section Cards
        drawSettingsSectionCard(g, 180, "🏷️", "Categories & SMS Tags", "9 categories configured")
        drawSettingsSectionCard(g, 320, "💳", "Custom Card Names", "e.g. 7224 → Regalia Card")

        // Excluded Keywords Card
        g.color = Color(241, 245, 249)
        g.fillRoundRect(48, 460, width - 96, 180, 28, 28)
        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 26)
        g.drawString("Excluded SMS Keywords (2)", 80, 506)
        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 20)
        g.drawString("SMS matching these keywords will be moved to exclusions.", 80, 538)

        g.color = Color(209, 227, 255)
        g.fillRoundRect(80, 560, 200, 52, 14, 14)
        g.color = Color(30, 58, 138)
        g.font = Font("SansSerif", Font.BOLD, 22)
        g.drawString("House Rent ✖", 100, 594)

        g.fillRoundRect(300, 560, 110, 52, 14, 14)
        g.drawString("SIP ✖", 320, 594)

        // Full Year SMS Scan Card (Highlighted)
        g.color = Color(239, 246, 255)
        g.fillRoundRect(48, 660, width - 96, 460, 32, 32)
        g.color = Color(191, 219, 254)
        g.stroke = BasicStroke(3f)
        g.drawRoundRect(48, 660, width - 96, 460, 32, 32)

        g.color = Color(219, 234, 254)
        g.fillOval(80, 700, 72, 72)
        g.font = Font("SansSerif", Font.PLAIN, 32)
        g.drawString("📅", 100, 748)

        g.color = Color(30, 58, 138)
        g.font = Font("SansSerif", Font.BOLD, 30)
        g.drawString("Full Year SMS Scan (2026)", 170, 730)
        g.color = Color(59, 130, 246)
        g.font = Font("SansSerif", Font.PLAIN, 22)
        g.drawString("Scan all banking SMS messages for 2026", 170, 762)

        g.color = Color(71, 85, 105)
        g.font = Font("SansSerif", Font.PLAIN, 22)
        g.drawString("Scan all bank and credit card SMS messages for the", 80, 830)
        g.drawString("entire year 2026 (Jan 1 – Dec 31) for both expenses", 80, 862)
        g.drawString("and income entries. New transactions will be added.", 80, 894)

        // Scan Action Box
        g.color = Color.WHITE
        g.fillRoundRect(80, 930, width - 160, 150, 24, 24)
        g.color = Color(203, 213, 225)
        g.stroke = BasicStroke(2f)
        g.drawRoundRect(80, 930, width - 160, 150, 24, 24)

        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 26)
        g.drawString("Scan Entire 2026", 110, 980)
        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 20)
        g.drawString("Processes Jan–Dec SMS messages", 110, 1012)

        g.color = Color(37, 99, 235)
        g.fillRoundRect(440, 960, 200, 76, 20, 20)
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString("Scan 2026", 480, 1006)

        drawNavBar(g, 2)
        ImageIO.write(image, "PNG", file)
    }

    private fun drawTransactionRow(g: Graphics2D, y: Int, icon: String, title: String, sub: String, amt: String, amtColor: Color, bgCircle: Color) {
        g.color = Color(241, 245, 249)
        g.fillRoundRect(48, y, width - 96, 120, 24, 24)

        g.color = bgCircle
        g.fillOval(72, y + 20, 80, 80)
        g.font = Font("SansSerif", Font.PLAIN, 32)
        g.drawString(icon, 94, y + 70)

        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 26)
        g.drawString(title, 170, y + 50)

        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 20)
        g.drawString(sub, 170, y + 86)

        g.color = amtColor
        g.font = Font("SansSerif", Font.BOLD, 26)
        g.drawString(amt, 460, y + 70)
    }

    private fun drawExcludedRow(g: Graphics2D, y: Int, icon: String, title: String, sub: String, amt: String, reason: String, bgCircle: Color) {
        g.color = Color(248, 250, 252)
        g.fillRoundRect(48, y, width - 96, 140, 24, 24)
        g.color = Color(226, 232, 240)
        g.stroke = BasicStroke(2f)
        g.drawRoundRect(48, y, width - 96, 140, 24, 24)

        g.color = bgCircle
        g.fillOval(72, y + 20, 80, 80)
        g.font = Font("SansSerif", Font.PLAIN, 32)
        g.drawString(icon, 94, y + 70)

        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString(title, 170, y + 46)

        g.font = Font("SansSerif", Font.PLAIN, 20)
        g.drawString(sub, 170, y + 78)

        g.color = Color(220, 38, 38)
        g.font = Font("SansSerif", Font.BOLD, 20)
        g.drawString(reason, 170, y + 112)

        g.color = Color(148, 163, 184)
        g.font = Font("SansSerif", Font.BOLD, 24)
        g.drawString(amt, 440, y + 50)

        g.color = Color.WHITE
        g.fillRoundRect(550, 70 + y, 120, 48, 12, 12)
        g.color = Color(37, 99, 235)
        g.drawRoundRect(550, 70 + y, 120, 48, 12, 12)
        g.font = Font("SansSerif", Font.BOLD, 20)
        g.drawString("Restore", 572, 102 + y)
    }

    private fun drawCategoryProgressRow(g: Graphics2D, y: Int, icon: String, name: String, count: String, amt: String, pct: String, progress: Float, color: Color, bgCircle: Color) {
        g.color = Color(241, 245, 249)
        g.fillRoundRect(48, y, width - 96, 130, 24, 24)

        g.color = bgCircle
        g.fillOval(72, y + 20, 72, 72)
        g.font = Font("SansSerif", Font.PLAIN, 30)
        g.drawString(icon, 92, y + 64)

        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 26)
        g.drawString(name, 160, y + 48)

        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 20)
        g.drawString(count, 160, y + 80)

        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 26)
        g.drawString(amt, 480, y + 48)

        g.color = color
        g.fillRoundRect(520, y + 62, 100, 36, 10, 10)
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.BOLD, 20)
        g.drawString(pct, 542, y + 88)

        // Progress bar
        g.color = Color(226, 232, 240)
        g.fillRoundRect(160, y + 98, 480, 12, 6, 6)
        g.color = color
        g.fillRoundRect(160, y + 98, (480 * progress).toInt(), 12, 6, 6)
    }

    private fun drawSettingsSectionCard(g: Graphics2D, y: Int, icon: String, title: String, sub: String) {
        g.color = Color(241, 245, 249)
        g.fillRoundRect(48, y, width - 96, 120, 28, 28)

        g.color = Color(226, 232, 240)
        g.fillOval(80, y + 20, 80, 80)
        g.font = Font("SansSerif", Font.PLAIN, 32)
        g.drawString(icon, 102, y + 70)

        g.color = Color(15, 23, 42)
        g.font = Font("SansSerif", Font.BOLD, 28)
        g.drawString(title, 180, y + 54)

        g.color = Color(100, 116, 139)
        g.font = Font("SansSerif", Font.PLAIN, 22)
        g.drawString(sub, 180, y + 90)
    }
}
