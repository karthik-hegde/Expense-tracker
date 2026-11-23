package com.example.expensetracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.ExpenseCategory
import com.example.expensetracker.model.TrackerMode
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class ChartPeriod {
    MONTHLY,
    ANNUAL
}

enum class ChartMode {
    EXPENSES,
    INCOME,
    NET
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()) }
    val uiState by viewModel.uiState.collectAsState()
    val allExpensesList by viewModel.allExpenses.collectAsState()
    val allIncomesList by viewModel.allIncomes.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()

    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var expenseForInfoDialog by remember { mutableStateOf<Expense?>(null) }

    var chartMode by remember {
        mutableStateOf(
            if (uiState.trackerMode == TrackerMode.EXPENSES) ChartMode.EXPENSES else ChartMode.INCOME
        )
    }
    var selectedPeriod by remember { mutableStateOf(ChartPeriod.MONTHLY) }
    var selectedMonthCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var selectedAnnualCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var selectedYear by remember { mutableIntStateOf(uiState.selectedMonth.year) }

    val activeDataset = remember(chartMode, allExpensesList, allIncomesList) {
        when (chartMode) {
            ChartMode.EXPENSES -> allExpensesList
            ChartMode.INCOME -> allIncomesList
            ChartMode.NET -> allExpensesList + allIncomesList
        }
    }

    // Monthly data
    var activeMonthlyExpenses by remember { mutableStateOf(emptyList<Expense>()) }
    var totalMonthlyAmount by remember { mutableDoubleStateOf(0.0) }
    var monthlyCategoryTotals by remember { mutableStateOf(emptyList<Map.Entry<ExpenseCategory, Double>>()) }

    val totalMonthlyExpense = remember(activeMonthlyExpenses) {
        activeMonthlyExpenses.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }
    val totalMonthlyIncome = remember(activeMonthlyExpenses) {
        activeMonthlyExpenses.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }
    val netMonthlySavings = remember(totalMonthlyIncome, totalMonthlyExpense) {
        totalMonthlyIncome - totalMonthlyExpense
    }

    LaunchedEffect(activeDataset, uiState.selectedMonth, chartMode) {
        withContext(Dispatchers.Default) {
            val expenses = activeDataset.filter { (!it.isDeleted) && (it.yearMonth == uiState.selectedMonth) }
                .sortedByDescending { it.amount }
            val total = expenses.sumOf { it.amount }
            val catTotals = expenses.groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
                .filter { it.value > 0 }
                .entries
                .sortedByDescending { it.value }
            
            activeMonthlyExpenses = expenses
            totalMonthlyAmount = total
            monthlyCategoryTotals = catTotals
        }
    }

    // Annual data combining all months for the selected year
    var activeYearExpenses by remember { mutableStateOf(emptyList<Expense>()) }
    var totalYearAmount by remember { mutableDoubleStateOf(0.0) }
    var yearCategoryTotals by remember { mutableStateOf(emptyList<Map.Entry<ExpenseCategory, Double>>()) }
    var monthlyAmounts by remember { mutableStateOf(emptyList<Triple<Month, Double, Int>>()) }
    var activeMonthsCount by remember { mutableIntStateOf(1) }
    var peakMonth by remember { mutableStateOf<Triple<Month, Double, Int>?>(null) }

    val totalYearExpense = remember(activeYearExpenses) {
        activeYearExpenses.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }
    val totalYearIncome = remember(activeYearExpenses) {
        activeYearExpenses.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }
    val totalYearNet = remember(totalYearIncome, totalYearExpense) {
        totalYearIncome - totalYearExpense
    }

    LaunchedEffect(activeDataset, selectedYear, chartMode) {
        withContext(Dispatchers.Default) {
            val expenses = activeDataset.filter { (!it.isDeleted) && (it.date.year == selectedYear) }
                .sortedByDescending { it.date }
            val total = expenses.sumOf { it.amount }
            val catTotals = expenses.groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
                .filter { it.value > 0 }
                .entries
                .sortedByDescending { it.value }
            val mAmounts = Month.entries.map { month ->
                val monthExpenses = expenses.filter { it.date.month == month }
                val monthVal = if (chartMode == ChartMode.NET) {
                    val inc = monthExpenses.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                    val exp = monthExpenses.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                    inc - exp
                } else {
                    monthExpenses.sumOf { it.amount }
                }
                Triple(month, monthVal, monthExpenses.size)
            }
            
            activeYearExpenses = expenses
            totalYearAmount = total
            yearCategoryTotals = catTotals
            monthlyAmounts = mAmounts
            activeMonthsCount = mAmounts.count { kotlin.math.abs(it.second) > 0 }.coerceAtLeast(1)
            peakMonth = mAmounts.maxByOrNull { it.second }
        }
    }
    
    val monthlyAverage = if (activeYearExpenses.isNotEmpty()) totalYearAmount / activeMonthsCount else 0.0

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (chartMode) {
                                ChartMode.EXPENSES -> if (selectedPeriod == ChartPeriod.MONTHLY) "Expense Analytics" else "Annual Expense Report"
                                ChartMode.INCOME -> if (selectedPeriod == ChartPeriod.MONTHLY) "Income Analytics" else "Annual Income Report"
                                ChartMode.NET -> if (selectedPeriod == ChartPeriod.MONTHLY) "Net Financial Analytics" else "Annual Net Expense & Income Report"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedPeriod == ChartPeriod.MONTHLY) {
                                "${uiState.selectedMonth.format(monthFormatter)} breakdown"
                            } else {
                                "$selectedYear full year overview"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = AppIcons.ArrowLeft,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Mode Selector: Expenses vs Income vs Net
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = chartMode == ChartMode.EXPENSES,
                            onClick = {
                                chartMode = ChartMode.EXPENSES
                                selectedMonthCategory = null
                                selectedAnnualCategory = null
                            },
                            label = { Text("Expenses") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        FilterChip(
                            selected = chartMode == ChartMode.INCOME,
                            onClick = {
                                chartMode = ChartMode.INCOME
                                selectedMonthCategory = null
                                selectedAnnualCategory = null
                            },
                            label = { Text("Income") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2E7D32),
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = chartMode == ChartMode.NET,
                            onClick = {
                                chartMode = ChartMode.NET
                                selectedMonthCategory = null
                                selectedAnnualCategory = null
                            },
                            label = { Text("Net Report") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1565C0),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Period Toggle Tabs: Monthly vs Annual
            PrimaryTabRow(
                selectedTabIndex = selectedPeriod.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                Tab(
                    selected = selectedPeriod == ChartPeriod.MONTHLY,
                    onClick = {
                        selectedPeriod = ChartPeriod.MONTHLY
                        selectedMonthCategory = null
                    },
                    text = {
                        Text(
                            text = "Monthly Report",
                            fontWeight = if (selectedPeriod == ChartPeriod.MONTHLY) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
                Tab(
                    selected = selectedPeriod == ChartPeriod.ANNUAL,
                    onClick = {
                        selectedPeriod = ChartPeriod.ANNUAL
                        selectedAnnualCategory = null
                    },
                    text = {
                        Text(
                            text = "Annual Report",
                            fontWeight = if (selectedPeriod == ChartPeriod.ANNUAL) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }

            when (selectedPeriod) {
                ChartPeriod.MONTHLY -> {
                    MonthNavigator(
                        selectedMonth = uiState.selectedMonth,
                        onPreviousMonth = {
                            selectedMonthCategory = null
                            viewModel.previousMonth()
                        },
                        onNextMonth = {
                            selectedMonthCategory = null
                            viewModel.nextMonth()
                        },
                        onJumpToToday = {
                            selectedMonthCategory = null
                            viewModel.jumpToCurrentMonth()
                        }
                    )

                    if (monthlyCategoryTotals.isEmpty() || totalMonthlyAmount <= 0.0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(text = "📊", fontSize = 48.sp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No Expenses in ${uiState.selectedMonth.format(monthFormatter)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Add expenses manually or scan banking SMS to see the monthly category breakdown.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        val displayedMonthlyExpenses = remember(activeMonthlyExpenses, selectedMonthCategory) {
                            if (selectedMonthCategory != null) {
                                activeMonthlyExpenses.filter { it.category == selectedMonthCategory }
                            } else {
                                activeMonthlyExpenses
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (chartMode == ChartMode.NET) {
                                item(key = "monthly_net_summary_card") {
                                    NetMonthlySummaryCard(
                                        selectedMonth = uiState.selectedMonth,
                                        totalIncome = totalMonthlyIncome,
                                        totalExpense = totalMonthlyExpense,
                                        netSavings = netMonthlySavings,
                                        itemCount = activeMonthlyExpenses.size
                                    )
                                }
                            }

                            // Monthly Pie Chart Card
                            item(key = "monthly_pie_chart_card") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Monthly Category Distribution",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Tap any slice or category to filter expenses",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        ClassicPieChart(
                                            categoryTotals = monthlyCategoryTotals,
                                            totalAmount = totalMonthlyAmount,
                                            selectedCategory = selectedMonthCategory,
                                            onSelectCategory = { clickedCat ->
                                                selectedMonthCategory = if (selectedMonthCategory == clickedCat) null else clickedCat
                                            },
                                            modifier = Modifier.size(280.dp)
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        val highlighted = selectedMonthCategory
                                        if (highlighted != null) {
                                            val catAmount = monthlyCategoryTotals.find { it.key == highlighted }?.value ?: 0.0
                                            val catPercent = (catAmount / totalMonthlyAmount) * 100
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = highlighted.color.copy(alpha = 0.2f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, highlighted.color)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = highlighted.emoji, fontSize = 20.sp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = highlighted.displayName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "${formatRupees(catAmount)} (${String.format(Locale.getDefault(), "%.1f", catPercent)}%)",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = "Clear",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.clickable { selectedMonthCategory = null }
                                                    )
                                                }
                                            }
                                        } else {
                                            val topCategoryEntry = monthlyCategoryTotals.firstOrNull()
                                            if (topCategoryEntry != null) {
                                                val topCat = topCategoryEntry.key
                                                val topPercent = (topCategoryEntry.value / totalMonthlyAmount) * 100
                                                Text(
                                                    text = "Largest spend: ${topCat.emoji} ${topCat.displayName} (${String.format(Locale.getDefault(), "%.1f", topPercent)}%)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Monthly Breakdown Header
                            item(key = "monthly_breakdown_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Category Breakup (${monthlyCategoryTotals.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Total: ${formatRupees(totalMonthlyAmount)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Monthly Category Breakup items
                            itemsIndexed(
                                items = monthlyCategoryTotals,
                                key = { index, item -> "month_cat_${item.key.id}_$index" }
                            ) { _, (category, amount) ->
                                val percent = if (totalMonthlyAmount > 0) (amount / totalMonthlyAmount) * 100 else 0.0
                                val count = activeMonthlyExpenses.count { it.category == category }
                                val isSelected = selectedMonthCategory == category

                                CategoryBreakupItem(
                                    category = category,
                                    amount = amount,
                                    percent = percent,
                                    expenseCount = count,
                                    isSelected = isSelected,
                                    onClick = {
                                        selectedMonthCategory = if (selectedMonthCategory == category) null else category
                                    }
                                )
                            }

                            // Monthly Expenses Section Header
                            item(key = "monthly_expenses_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedMonthCategory != null) {
                                            "${selectedMonthCategory!!.displayName} Expenses (${displayedMonthlyExpenses.size})"
                                        } else {
                                            "Monthly Expenses (${displayedMonthlyExpenses.size})"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (selectedMonthCategory != null) {
                                        TextButton(onClick = { selectedMonthCategory = null }) {
                                            Text("Show All")
                                        }
                                    }
                                }
                            }

                            // Monthly Expenses List
                            itemsIndexed(
                                items = displayedMonthlyExpenses,
                                key = { index, expense -> "month_exp_${expense.id}_$index" }
                            ) { _, expense ->
                                ChartExpenseItem(
                                    expense = expense,
                                    onIconClick = { expenseForInfoDialog = expense },
                                    onDeleteClick = { expenseToDelete = expense }
                                )
                            }
                        }
                    }
                }

                ChartPeriod.ANNUAL -> {
                    val currentYear = remember { Year.now().value }

                    YearNavigator(
                        selectedYear = selectedYear,
                        onPreviousYear = {
                            selectedAnnualCategory = null
                            selectedYear--
                        },
                        onNextYear = {
                            selectedAnnualCategory = null
                            selectedYear++
                        },
                        onJumpToCurrentYear = {
                            selectedAnnualCategory = null
                            selectedYear = currentYear
                        }
                    )

                    if (yearCategoryTotals.isEmpty() || totalYearAmount <= 0.0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(text = "📅", fontSize = 48.sp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No Expenses in $selectedYear",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No transactions found for the year $selectedYear. Transactions from any month of this year will automatically show up in this annual report.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        val displayedYearExpenses = remember(activeYearExpenses, selectedAnnualCategory) {
                            if (selectedAnnualCategory != null) {
                                activeYearExpenses.filter { it.category == selectedAnnualCategory }
                            } else {
                                activeYearExpenses
                            }
                        }

                        val peakMonthName = peakMonth?.first?.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) ?: "N/A"
                        val peakMonthAmount = peakMonth?.second ?: 0.0

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Annual Summary KPI Card
                            item(key = "annual_summary_card") {
                                AnnualSummaryCard(
                                    selectedYear = selectedYear,
                                    totalAmount = totalYearAmount,
                                    transactionCount = activeYearExpenses.size,
                                    monthlyAverage = monthlyAverage,
                                    peakMonthName = peakMonthName,
                                    peakMonthAmount = peakMonthAmount,
                                    chartMode = chartMode,
                                    totalIncome = totalYearIncome,
                                    totalExpense = totalYearExpense
                                )
                            }

                            // Annual Pie Chart Card
                            item(key = "annual_pie_chart_card") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "$selectedYear Category Distribution",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Combined across all 12 months • Tap to filter",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        ClassicPieChart(
                                            categoryTotals = yearCategoryTotals,
                                            totalAmount = totalYearAmount,
                                            selectedCategory = selectedAnnualCategory,
                                            onSelectCategory = { clickedCat ->
                                                selectedAnnualCategory = if (selectedAnnualCategory == clickedCat) null else clickedCat
                                            },
                                            modifier = Modifier.size(280.dp)
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        val highlighted = selectedAnnualCategory
                                        if (highlighted != null) {
                                            val catAmount = yearCategoryTotals.find { it.key == highlighted }?.value ?: 0.0
                                            val catPercent = (catAmount / totalYearAmount) * 100
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = highlighted.color.copy(alpha = 0.2f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, highlighted.color)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = highlighted.emoji, fontSize = 20.sp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = highlighted.displayName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "${formatRupees(catAmount)} (${String.format(Locale.getDefault(), "%.1f", catPercent)}%)",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = "Clear",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.clickable { selectedAnnualCategory = null }
                                                    )
                                                }
                                            }
                                        } else {
                                            val topCategoryEntry = yearCategoryTotals.firstOrNull()
                                            if (topCategoryEntry != null) {
                                                val topCat = topCategoryEntry.key
                                                val topPercent = (topCategoryEntry.value / totalYearAmount) * 100
                                                Text(
                                                    text = "Annual Top: ${topCat.emoji} ${topCat.displayName} (${String.format(Locale.getDefault(), "%.1f", topPercent)}%)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Annual Monthly Spending Trend Bar Chart Card
                            item(key = "annual_trend_chart_card") {
                                AnnualMonthlyBarChart(
                                    monthlyAmounts = monthlyAmounts,
                                    chartMode = chartMode
                                )
                            }

                            // Annual Category Breakup Header
                            item(key = "annual_breakdown_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Annual Category Breakup (${yearCategoryTotals.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Total: ${formatRupees(totalYearAmount)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Annual Category Breakup items
                            itemsIndexed(
                                items = yearCategoryTotals,
                                key = { index, item -> "year_cat_${item.key.id}_$index" }
                            ) { _, (category, amount) ->
                                val percent = if (totalYearAmount > 0) (amount / totalYearAmount) * 100 else 0.0
                                val count = activeYearExpenses.count { it.category == category }
                                val isSelected = selectedAnnualCategory == category

                                CategoryBreakupItem(
                                    category = category,
                                    amount = amount,
                                    percent = percent,
                                    expenseCount = count,
                                    isSelected = isSelected,
                                    onClick = {
                                        selectedAnnualCategory = if (selectedAnnualCategory == category) null else category
                                    }
                                )
                            }

                            // Annual Expenses Section Header
                            item(key = "annual_expenses_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedAnnualCategory != null) {
                                            "${selectedAnnualCategory!!.displayName} Expenses ($selectedYear) (${displayedYearExpenses.size})"
                                        } else {
                                            "All $selectedYear Expenses (${displayedYearExpenses.size})"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (selectedAnnualCategory != null) {
                                        TextButton(onClick = { selectedAnnualCategory = null }) {
                                            Text("Show All")
                                        }
                                    }
                                }
                            }

                            // Annual Expenses List
                            itemsIndexed(
                                items = displayedYearExpenses,
                                key = { index, expense -> "year_exp_${expense.id}_$index" }
                            ) { _, expense ->
                                ChartExpenseItem(
                                    expense = expense,
                                    onIconClick = { expenseForInfoDialog = expense },
                                    onDeleteClick = { expenseToDelete = expense }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    expenseToDelete?.let { item ->
        var permanently by remember { mutableStateOf(false) }
        val isExpense = item.type == TransactionType.EXPENSE

        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text(if (isExpense) "Delete Expense" else "Delete Income Entry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to delete '${item.remark.ifBlank { item.category.displayName }}' (${formatRupees(item.amount)})?")
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { permanently = !permanently }
                    ) {
                        Checkbox(
                            checked = permanently,
                            onCheckedChange = { permanently = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Delete permanently", style = MaterialTheme.typography.bodyMedium)
                            Text("If unchecked, it will be moved to the deleted list.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExpense(item.id, permanently, type = item.type)
                        expenseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Item Detail and Category Selection Dialog
    expenseForInfoDialog?.let { item ->
        var selectedCategory by remember(item) { mutableStateOf(item.category) }
        val isExpense = item.type == TransactionType.EXPENSE
        val activeCategories = if (isExpense) categories else incomeCategories

        AlertDialog(
            onDismissRequest = { expenseForInfoDialog = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(selectedCategory.color.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = selectedCategory.emoji, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isExpense) "Expense Details" else "Income Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header with Amount and Remark
                    Column {
                        Text(
                            text = if (isExpense) "-${formatRupees(item.amount)}" else "+${formatRupees(item.amount)}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (isExpense) MaterialTheme.colorScheme.primary else Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.remark.ifBlank { selectedCategory.displayName },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Metadata Row (Source and Date)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Source",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = item.source,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = item.date.format(dateFormatter),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Source SMS preview if available
                    val sms = item.originalSms
                    if (!sms.isNullOrBlank()) {
                        Text(
                            text = "Original SMS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = sms,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Exclusion Reason if applicable
                    val reason = item.effectiveIgnoreReason
                    if (!reason.isNullOrBlank()) {
                        Text(
                            text = "Exclusion Reason",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🚫 $reason",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Category selection section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (item.isCategoryUserModified || selectedCategory != item.category) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Customized",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Tap a category below to change:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeCategories.forEach { cat ->
                            val isSelected = selectedCategory.id == cat.id || selectedCategory.name.equals(cat.name, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) {
                                    cat.color.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                                border = if (isSelected) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else null,
                                modifier = Modifier.clickable {
                                    selectedCategory = cat
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(text = cat.emoji, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = cat.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val hasChanged = selectedCategory.id != item.category.id &&
                    !selectedCategory.name.equals(item.category.name, ignoreCase = true)
                TextButton(
                    onClick = {
                        val currentItem = item
                        if (hasChanged) {
                            viewModel.updateExpenseCategory(currentItem.id, selectedCategory, type = currentItem.type)
                        }
                        expenseForInfoDialog = null
                    }
                ) {
                    Text(if (hasChanged) "Save Category" else "Close")
                }
            },
            dismissButton = {
                val hasChanged = selectedCategory.id != item.category.id &&
                    !selectedCategory.name.equals(item.category.name, ignoreCase = true)
                if (hasChanged) {
                    TextButton(onClick = { expenseForInfoDialog = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
fun YearNavigator(
    selectedYear: Int,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onJumpToCurrentYear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentYear = remember { Year.now().value }
    val isCurrentYear = selectedYear == currentYear

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousYear) {
                Icon(
                    imageVector = AppIcons.ArrowLeft,
                    contentDescription = "Previous Year"
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Year $selectedYear",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!isCurrentYear) {
                    Text(
                        text = "Jump to Current Year",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onJumpToCurrentYear() }
                    )
                }
            }

            IconButton(onClick = onNextYear) {
                Icon(
                    imageVector = AppIcons.ArrowRight,
                    contentDescription = "Next Year"
                )
            }
        }
    }
}

@Composable
fun NetMonthlySummaryCard(
    selectedMonth: YearMonth,
    totalIncome: Double,
    totalExpense: Double,
    netSavings: Double,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val isPositive = netSavings >= 0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPositive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Net Balance (${selectedMonth.format(monthFormatter)})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPositive) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPositive) Color(0xFF81C784).copy(alpha = 0.3f) else Color(0xFFE57373).copy(alpha = 0.3f)
                ) {
                    Text(
                        text = "$itemCount entries",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPositive) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = if (isPositive) "+${formatRupees(netSavings)}" else "-${formatRupees(-netSavings)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Total Income",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "+${formatRupees(totalIncome)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Total Expenses",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "-${formatRupees(totalExpense)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnnualSummaryCard(
    selectedYear: Int,
    totalAmount: Double,
    transactionCount: Int,
    monthlyAverage: Double,
    peakMonthName: String,
    peakMonthAmount: Double,
    chartMode: ChartMode = ChartMode.EXPENSES,
    totalIncome: Double = 0.0,
    totalExpense: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val isNet = chartMode == ChartMode.NET
    val netBalance = totalIncome - totalExpense
    val isNetPositive = netBalance >= 0

    val containerColor = when {
        isNet && isNetPositive -> Color(0xFFE8F5E9)
        isNet && !isNetPositive -> Color(0xFFFFEBEE)
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val textColor = when {
        isNet && isNetPositive -> Color(0xFF1B5E20)
        isNet && !isNetPositive -> Color(0xFFB71C1C)
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isNet) "$selectedYear Net Balance" else "$selectedYear Total Spending",
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor.copy(alpha = 0.8f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = textColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$transactionCount entries",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = if (isNet) {
                    if (isNetPositive) "+${formatRupees(netBalance)}" else "-${formatRupees(-netBalance)}"
                } else {
                    formatRupees(totalAmount)
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            if (isNet) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Total Income",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "+${formatRupees(totalIncome)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Total Expenses",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "-${formatRupees(totalExpense)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Monthly Avg",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatRupees(monthlyAverage),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Peak ($peakMonthName)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatRupees(peakMonthAmount),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnnualMonthlyBarChart(
    monthlyAmounts: List<Triple<Month, Double, Int>>,
    chartMode: ChartMode = ChartMode.EXPENSES,
    modifier: Modifier = Modifier
) {
    val isNet = chartMode == ChartMode.NET
    val maxAmount = remember(monthlyAmounts, isNet) {
        if (isNet) {
            (monthlyAmounts.maxOfOrNull { kotlin.math.abs(it.second) } ?: 1.0).coerceAtLeast(1.0)
        } else {
            (monthlyAmounts.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = if (isNet) "Monthly Net Trend (Income vs Expenses)" else "Monthly Spending Trend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isNet) "Jan - Dec monthly net savings or deficit" else "Jan - Dec monthly expense breakdown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                monthlyAmounts.forEach { (month, amount, _) ->
                    val absAmount = kotlin.math.abs(amount)
                    val rawRatio = if (maxAmount > 0) (absAmount / maxAmount).toFloat() else 0f
                    val ratio = if (rawRatio.isNaN()) 0f else rawRatio.coerceIn(0f, 1f)
                    val monthShortName = month.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())

                    val barColor = when {
                        isNet && amount > 0 -> Color(0xFF2E7D32)
                        isNet && amount < 0 -> MaterialTheme.colorScheme.error
                        !isNet && amount > 0 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (absAmount > 0) {
                            val textLabel = if (isNet) {
                                if (amount > 0) "+${formatCompactRupees(amount)}" else "-${formatCompactRupees(absAmount)}"
                            } else {
                                formatCompactRupees(amount)
                            }
                            Text(
                                text = textLabel,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isNet && amount > 0) Color(0xFF2E7D32) else if (isNet && amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(
                                    if (absAmount > 0) (85.dp * ratio).coerceAtLeast(6.dp)
                                    else 3.dp
                                )
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = monthShortName,
                            fontSize = 11.sp,
                            fontWeight = if (absAmount > 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (absAmount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

fun formatCompactRupees(amount: Double): String {
    return when {
        amount <= 0 -> "-"
        amount < 1000 -> amount.roundToInt().toString()
        amount < 100000 -> String.format(Locale.getDefault(), "%.0fk", amount / 1000.0)
        else -> String.format(Locale.getDefault(), "%.1fL", amount / 100000.0)
    }
}

@Composable
fun ChartExpenseItem(
    expense: Expense,
    onIconClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(expense.category.color.copy(alpha = 0.25f))
                    .clickable { onIconClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = expense.category.emoji, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.remark.ifBlank { expense.category.displayName },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = expense.source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = expense.date.format(dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val isExpense = expense.type == TransactionType.EXPENSE
            Text(
                text = if (isExpense) "-${formatRupees(expense.amount)}" else "+${formatRupees(expense.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isExpense) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
            )

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Delete,
                    contentDescription = if (isExpense) "Delete Expense" else "Delete Income",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun FullNetReportPreview() {
    ExpenseTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                NetMonthlySummaryCard(
                    selectedMonth = YearMonth.of(2026, 11),
                    totalIncome = 75000.00,
                    totalExpense = 14850.00,
                    netSavings = 60150.00,
                    itemCount = 6
                )

                AnnualMonthlyBarChart(
                    monthlyAmounts = listOf(
                        Triple(java.time.Month.JANUARY, 45000.0, 10),
                        Triple(java.time.Month.FEBRUARY, 52000.0, 12),
                        Triple(java.time.Month.MARCH, -8000.0, 15),
                        Triple(java.time.Month.APRIL, 38000.0, 8),
                        Triple(java.time.Month.MAY, 61000.0, 14),
                        Triple(java.time.Month.JUNE, 49000.0, 11),
                        Triple(java.time.Month.JULY, -12000.0, 16),
                        Triple(java.time.Month.AUGUST, 55000.0, 13),
                        Triple(java.time.Month.SEPTEMBER, 67000.0, 9),
                        Triple(java.time.Month.OCTOBER, 42000.0, 11),
                        Triple(java.time.Month.NOVEMBER, 60150.0, 6),
                        Triple(java.time.Month.DECEMBER, 0.0, 0)
                    ),
                    chartMode = ChartMode.NET
                )

                ChartExpenseItem(
                    expense = Expense(
                        amount = 75000.00,
                        remark = "TECH CORP PVT LTD - Salary",
                        category = ExpenseCategory.SALARY,
                        date = LocalDate.of(2026, 11, 30),
                        source = "Bank",
                        type = TransactionType.INCOME
                    )
                )

                ChartExpenseItem(
                    expense = Expense(
                        amount = 1362.03,
                        remark = "AMAZON PAY IN E",
                        category = ExpenseCategory.SHOPPING,
                        date = LocalDate.of(2026, 11, 25),
                        source = "Card",
                        type = TransactionType.EXPENSE
                    )
                )
            }
        }
    }
}

@Composable
fun ClassicPieChart(
    categoryTotals: List<Map.Entry<ExpenseCategory, Double>>,
    totalAmount: Double,
    selectedCategory: ExpenseCategory?,
    onSelectCategory: (ExpenseCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val isDark = isSystemInDarkTheme()
    val outlineColor = if (isDark) Color(0xFFE0E0E0) else Color(0xFF2C2C2C)
    val labelTextColor = MaterialTheme.colorScheme.onSurface

    // Map slice angles for tap detection
    val sliceAngles = remember(categoryTotals, totalAmount) {
        var start = -90f
        categoryTotals.map { entry ->
            val sweep = ((entry.value / totalAmount) * 360f).toFloat()
            val slice = SliceAngle(entry.key, start, sweep)
            start += sweep
            slice
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(sliceAngles) {
                    detectTapGestures { tapOffset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = tapOffset.x - center.x
                        val dy = tapOffset.y - center.y
                        val distance = sqrt((dx * dx) + (dy * dy))
                        val radius = (kotlin.math.min(size.width, size.height) / 2f) - 34.dp.toPx()

                        if (distance <= radius + 12.dp.toPx()) {
                            // Calculate angle in degrees [0, 360)
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            if (angle < -90f) {
                                angle += 360f
                            }

                            val matchedSlice = sliceAngles.find { slice ->
                                angle >= slice.startAngle && angle < (slice.startAngle + slice.sweepAngle)
                            }
                            matchedSlice?.category?.let { onSelectCategory(it) }
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.minDimension / 2f) - 34.dp.toPx()
            val borderWidth = 1.5.dp.toPx()

            // 1. Draw solid filled pie slices with boundary lines
            var currentAngle = -90f
            for (entry in categoryTotals) {
                val category = entry.key
                val amount = entry.value
                val sweep = ((amount / totalAmount) * 360f).toFloat()
                val isSelected = selectedCategory == category

                val midAngle = currentAngle + sweep / 2f
                val midRad = Math.toRadians(midAngle.toDouble())

                // If selected, gently explode the slice outwards
                val explodeDist = if (isSelected) 8.dp.toPx() else 0f
                val sliceCenter = Offset(
                    center.x + (explodeDist * cos(midRad)).toFloat(),
                    center.y + (explodeDist * sin(midRad)).toFloat()
                )
                val sliceTopLeft = Offset(sliceCenter.x - radius, sliceCenter.y - radius)
                val sliceSize = Size(radius * 2f, radius * 2f)

                // Fill slice solid
                drawArc(
                    color = category.color,
                    startAngle = currentAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = sliceTopLeft,
                    size = sliceSize
                )

                // Radial divider lines from center to outer edge
                val startRad = Math.toRadians(currentAngle.toDouble())
                val endRad = Math.toRadians((currentAngle + sweep).toDouble())

                val startLineEnd = Offset(
                    sliceCenter.x + (radius * cos(startRad)).toFloat(),
                    sliceCenter.y + (radius * sin(startRad)).toFloat()
                )
                val endLineEnd = Offset(
                    sliceCenter.x + (radius * cos(endRad)).toFloat(),
                    sliceCenter.y + (radius * sin(endRad)).toFloat()
                )

                drawLine(
                    color = outlineColor,
                    start = sliceCenter,
                    end = startLineEnd,
                    strokeWidth = borderWidth
                )
                drawLine(
                    color = outlineColor,
                    start = sliceCenter,
                    end = endLineEnd,
                    strokeWidth = borderWidth
                )

                // Outer perimeter arc of the slice
                drawArc(
                    color = outlineColor,
                    startAngle = currentAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = sliceTopLeft,
                    size = sliceSize,
                    style = Stroke(width = borderWidth)
                )

                currentAngle += sweep
            }

            // 2. Draw outer percentage labels around the perimeter
            currentAngle = -90f
            for (entry in categoryTotals) {
                val amount = entry.value
                val sweep = ((amount / totalAmount) * 360f).toFloat()
                val percent = (amount / totalAmount) * 100

                val midAngle = currentAngle + sweep / 2f
                val midRad = Math.toRadians(midAngle.toDouble())

                if (percent >= 1.0) {
                    val labelRadius = radius + 18.dp.toPx()
                    val labelX = center.x + (labelRadius * cos(midRad)).toFloat()
                    val labelY = center.y + (labelRadius * sin(midRad)).toFloat()

                    val percentText = if (percent >= 9.5 || percent % 1.0 == 0.0) {
                        "${percent.roundToInt()}%"
                    } else {
                        String.format(Locale.getDefault(), "%.1f%%", percent)
                    }

                    val textLayout = textMeasurer.measure(
                        text = percentText,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = labelTextColor
                        )
                    )

                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(
                            labelX - textLayout.size.width / 2f,
                            labelY - textLayout.size.height / 2f
                        )
                    )
                }

                currentAngle += sweep
            }
        }
    }
}

private data class SliceAngle(
    val category: ExpenseCategory,
    val startAngle: Float,
    val sweepAngle: Float
)

@Composable
fun CategoryBreakupItem(
    category: ExpenseCategory,
    amount: Double,
    percent: Double,
    expenseCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                category.color.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, category.color)
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category icon/emoji
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(category.color.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = category.emoji, fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name & transaction count
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$expenseCount ${if (expenseCount == 1) "transaction" else "transactions"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Amount & Percentage Pill
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatRupees(amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val safePercent = if (percent.isNaN() || percent < 0) 0.0 else percent
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = category.color.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = "${String.format(Locale.getDefault(), "%.1f", safePercent)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Percentage Progress Bar
            val progressVal = (percent / 100f).toFloat()
            val safeProgress = if (progressVal.isNaN()) 0f else progressVal.coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = safeProgress,
                animationSpec = tween(durationMillis = 500),
                label = "progress_anim"
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = category.color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ClassicPieChartPreview() {
    com.example.expensetracker.ui.theme.ExpenseTrackerTheme {
        val sampleMap = listOf(
            java.util.AbstractMap.SimpleEntry(ExpenseCategory.SHOPPING, 4000.0), // 40%
            java.util.AbstractMap.SimpleEntry(ExpenseCategory.BILLS, 3000.0),    // 30%
            java.util.AbstractMap.SimpleEntry(ExpenseCategory.FOOD, 2000.0),     // 20%
            java.util.AbstractMap.SimpleEntry(ExpenseCategory.TRANSPORT, 1000.0) // 10%
        )
        Box(modifier = Modifier.padding(16.dp)) {
            ClassicPieChart(
                categoryTotals = sampleMap,
                totalAmount = 10000.0,
                selectedCategory = null,
                onSelectCategory = {},
                modifier = Modifier.size(280.dp)
            )
        }
    }
}
