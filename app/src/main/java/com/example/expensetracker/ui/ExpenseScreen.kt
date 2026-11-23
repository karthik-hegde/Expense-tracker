package com.example.expensetracker.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.ExpenseCategory
import com.example.expensetracker.model.ExpenseSortOrder
import com.example.expensetracker.model.TrackerMode
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
private val indianLocale = Locale.forLanguageTag("en-IN")

private val rupeeFormatThreadLocal = ThreadLocal.withInitial {
    val formatter = NumberFormat.getCurrencyInstance(indianLocale)
    if (formatter is DecimalFormat) {
        val sym = formatter.decimalFormatSymbols
        sym.currencySymbol = "₹"
        formatter.decimalFormatSymbols = sym
    }
    formatter
}

fun formatRupees(amount: Double): String {
    return rupeeFormatThreadLocal.get()?.format(amount) ?: String.format(Locale.forLanguageTag("en-IN"), "₹%.2f", amount)
}

val IncomeGreen = Color(0xFF2E7D32)
val IncomeContainerGreen = Color(0xFFE8F5E9)
val IncomeOnContainerGreen = Color(0xFF1B5E20)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    viewModel: ExpenseViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenChart: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val activeCategories = if (uiState.trackerMode == TrackerMode.EXPENSES) categories else incomeCategories

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var expenseForInfoDialog by remember { mutableStateOf<Expense?>(null) }
    var showAddChoiceSheet by remember { mutableStateOf(false) }

    // Scroll to the first entry whenever sort order or tracker mode changes
    LaunchedEffect(uiState.sortOrder, uiState.trackerMode) {
        if (uiState.monthlyExpenses.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(uiState.syncMessage) {
        uiState.syncMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSyncMessage()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.autoPopulateFromSms()
        } else {
            Toast.makeText(context, "SMS permission is required to auto-read bank messages", Toast.LENGTH_SHORT).show()
        }
    }

    val requestSmsSync: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.autoPopulateFromSms()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        AnimatedContent(
                            targetState = uiState.trackerMode,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "TitleModeText"
                        ) { mode ->
                            Text(
                                text = if (mode == TrackerMode.EXPENSES) "Expense Tracker" else "Income Tracker",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (mode == TrackerMode.EXPENSES)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    IncomeOnContainerGreen
                            )
                        }
                        Text(
                            text = if (uiState.trackerMode == TrackerMode.EXPENSES)
                                "Monthly Budget & Expense Manager"
                            else
                                "Monthly Income & Earnings Manager",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenChart
                    ) {
                        Icon(
                            imageVector = AppIcons.PieChart,
                            contentDescription = "Analytics Chart",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.monthlyExpenses.isNotEmpty()) {
                        IconButton(
                            onClick = { showDeleteAllConfirmation = true }
                        ) {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = if (uiState.trackerMode == TrackerMode.EXPENSES) "Delete all expenses" else "Delete all income entries",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings
                    ) {
                        Icon(
                            imageVector = AppIcons.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddChoiceSheet = true },
                containerColor = if (uiState.trackerMode == TrackerMode.EXPENSES)
                    MaterialTheme.colorScheme.primary
                else
                    IncomeGreen,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = AppIcons.Add,
                    contentDescription = if (uiState.trackerMode == TrackerMode.EXPENSES) "Add Expense" else "Add Income"
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Month navigation banner
            MonthNavigator(
                selectedMonth = uiState.selectedMonth,
                onPreviousMonth = { viewModel.previousMonth() },
                onNextMonth = { viewModel.nextMonth() },
                onJumpToToday = { viewModel.jumpToCurrentMonth() }
            )

            // Monthly Summary Card
            MonthlySummaryCard(
                mode = uiState.trackerMode,
                selectedMonth = uiState.selectedMonth,
                totalAmount = uiState.totalMonthlyAmount,
                itemCount = uiState.monthlyExpenses.size,
                categoryTotals = uiState.categoryTotals,
                totalExpenseAmount = uiState.totalMonthlyExpenseAmount,
                totalIncomeAmount = uiState.totalMonthlyIncomeAmount,
                netMonthlySavings = uiState.netMonthlySavings,
                onClick = { viewModel.toggleTrackerMode() }
            )

            // Category Filter and Sort Bar
            FilterAndSortBar(
                selectedFilter = uiState.selectedCategoryFilter,
                onSelectFilter = { category -> viewModel.setCategoryFilter(category) },
                currentSortOrder = uiState.sortOrder,
                onSelectSortOrder = { order -> viewModel.setSortOrder(order) },
                categories = activeCategories
            )

            // Items list or empty state
            if (uiState.monthlyExpenses.isEmpty() && uiState.deletedMonthlyExpenses.isEmpty()) {
                EmptyExpensesView(
                    mode = uiState.trackerMode,
                    selectedMonth = uiState.selectedMonth,
                    isFiltered = uiState.selectedCategoryFilter != null,
                    isSyncing = uiState.isSyncingSms,
                    onAddClick = { viewModel.openAddExpenseDialog() },
                    onAutoPopulateClick = requestSmsSync,
                    onTrySampleSmsClick = { viewModel.populateSampleSms() }
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (uiState.monthlyExpenses.isNotEmpty()) {
                        items(
                            items = uiState.monthlyExpenses,
                            key = { it.id },
                            contentType = { "item" }
                        ) { item ->
                            ExpenseItem(
                                expense = item,
                                onDeleteClick = { expenseToDelete = item },
                                onIconClick = { expenseForInfoDialog = item }
                            )
                        }
                    }

                    // Deleted items section at the end of the list
                    if (uiState.deletedMonthlyExpenses.isNotEmpty()) {
                        item(key = "deleted_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (uiState.trackerMode == TrackerMode.EXPENSES)
                                        "Deleted Expenses (${uiState.deletedMonthlyExpenses.size})"
                                    else
                                        "Deleted Income Entries (${uiState.deletedMonthlyExpenses.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (uiState.deletedMonthlyExpenses.size > 1) {
                                    TextButton(
                                        onClick = { viewModel.restoreAllExpenses() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Restore All", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        items(
                            items = uiState.deletedMonthlyExpenses,
                            key = { "deleted_${it.id}" },
                            contentType = { "deleted_item" }
                        ) { item ->
                            DeletedExpenseItem(
                                expense = item,
                                onRestoreClick = { viewModel.restoreExpense(item.id) },
                                onIconClick = { expenseForInfoDialog = item }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddChoiceSheet) {
        AddExpenseChoiceBottomSheet(
            mode = uiState.trackerMode,
            onDismiss = { showAddChoiceSheet = false },
            onEnterManually = {
                showAddChoiceSheet = false
                viewModel.openAddExpenseDialog()
            },
            onScanSms = {
                showAddChoiceSheet = false
                requestSmsSync()
            }
        )
    }

    if (uiState.isAddExpenseDialogOpen) {
        AddExpenseBottomSheet(
            mode = uiState.trackerMode,
            initialMonth = uiState.selectedMonth,
            categories = activeCategories,
            onDismiss = { viewModel.closeAddExpenseDialog() },
            onAddExpense = { amount, remark, category, date, source ->
                viewModel.addExpense(amount, remark, category, date, source)
            }
        )
    }

    if (showDeleteAllConfirmation) {
        var onlyImported by remember { mutableStateOf(true) }
        var permanently by remember { mutableStateOf(false) }
        val isExpense = uiState.trackerMode == TrackerMode.EXPENSES
        val singularLabel = if (isExpense) "expense" else "income"
        val pluralLabel = if (isExpense) "expenses" else "income entries"

        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmation = false },
            title = { Text(if (isExpense) "Delete Expenses" else "Delete Income Entries") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (permanently) {
                            if (onlyImported) "Are you sure you want to permanently delete all imported $pluralLabel? They cannot be restored."
                            else "Are you sure you want to permanently delete ALL $pluralLabel? They cannot be restored."
                        } else {
                            if (onlyImported) "Delete imported $pluralLabel? They will be moved to the deleted list at the bottom, where you can restore them at any time."
                            else "Delete all $pluralLabel? They will be moved to the deleted list at the bottom, where you can restore them at any time."
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onlyImported = !onlyImported }
                    ) {
                        Checkbox(
                            checked = onlyImported,
                            onCheckedChange = { onlyImported = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Delete only imported $pluralLabel", style = MaterialTheme.typography.bodyMedium)
                            Text("Preserves manual entries", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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
                            Text("Cannot be restored later", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllExpenses(onlyImported, permanently)
                        showDeleteAllConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Individual item delete confirmation dialog
    expenseToDelete?.let { item ->
        val isExpense = item.type == TransactionType.EXPENSE
        val itemLabel = if (isExpense) "expense" else "income entry"
        var permanently by remember { mutableStateOf(false) }

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
                            Text("If unchecked, it will be moved to the deleted list at the bottom.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExpense(item.id, permanently)
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
                            color = if (isExpense) MaterialTheme.colorScheme.primary else IncomeGreen,
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
                            fontWeight = FontWeight.SemiBold
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
                            viewModel.updateExpenseCategory(currentItem.id, selectedCategory)
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
fun MonthNavigator(
    selectedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCurrentMonth = selectedMonth == YearMonth.now()

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
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = AppIcons.ArrowLeft,
                    contentDescription = "Previous Month"
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = selectedMonth.format(monthFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!isCurrentMonth) {
                    Text(
                        text = "Jump to Current Month",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onJumpToToday() }
                    )
                }
            }

            IconButton(onClick = onNextMonth) {
                Icon(
                    imageVector = AppIcons.ArrowRight,
                    contentDescription = "Next Month"
                )
            }
        }
    }
}

@Composable
fun MonthlySummaryCard(
    mode: TrackerMode,
    selectedMonth: YearMonth,
    totalAmount: Double,
    itemCount: Int,
    categoryTotals: Map<ExpenseCategory, Double>,
    totalExpenseAmount: Double = 0.0,
    totalIncomeAmount: Double = 0.0,
    netMonthlySavings: Double = 0.0,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isExpense = mode == TrackerMode.EXPENSES

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpense)
                MaterialTheme.colorScheme.primaryContainer
            else
                IncomeContainerGreen
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpense)
                        "Total Spent (${selectedMonth.format(monthFormatter)})"
                    else
                        "Total Income (${selectedMonth.format(monthFormatter)})",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isExpense)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else
                        IncomeOnContainerGreen.copy(alpha = 0.85f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isExpense)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else
                        IncomeGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$itemCount ${if (itemCount == 1) "entry" else "entries"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isExpense)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            IncomeOnContainerGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (isExpense) formatRupees(totalAmount) else "+${formatRupees(totalAmount)}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isExpense)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        IncomeOnContainerGreen
                )

                // Net Savings Chip
                if (totalIncomeAmount > 0.0 || totalExpenseAmount > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (netMonthlySavings >= 0)
                            Color(0xFF81C784).copy(alpha = 0.3f)
                        else
                            Color(0xFFE57373).copy(alpha = 0.3f),
                        border = BorderStroke(
                            1.dp,
                            if (netMonthlySavings >= 0) Color(0xFF2E7D32).copy(alpha = 0.4f) else Color(0xFFC62828).copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = if (netMonthlySavings >= 0)
                                "Net: +${formatRupees(netMonthlySavings)}"
                            else
                                "Net: -${formatRupees(-netMonthlySavings)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (netMonthlySavings >= 0) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Mini category breakdown badges if any entries exist
            if (categoryTotals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoryTotals.entries.sortedByDescending { it.value }.forEach { (category, amount) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = category.emoji, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = category.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isExpense) formatRupees(amount) else "+${formatRupees(amount)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExpense) MaterialTheme.colorScheme.primary else IncomeGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterAndSortBar(
    selectedFilter: ExpenseCategory?,
    onSelectFilter: (ExpenseCategory?) -> Unit,
    currentSortOrder: ExpenseSortOrder,
    onSelectSortOrder: (ExpenseSortOrder) -> Unit,
    categories: List<ExpenseCategory> = ExpenseCategory.DEFAULT_CATEGORIES,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sort Option Dropdown Chip
        Box {
            FilterChip(
                selected = currentSortOrder != ExpenseSortOrder.DATE_DESC,
                onClick = { showSortMenu = true },
                leadingIcon = {
                    Icon(
                        imageVector = AppIcons.Sort,
                        contentDescription = "Sort Options",
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Sort: ${currentSortOrder.shortLabel}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                ExpenseSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = order.label,
                                fontWeight = if (order == currentSortOrder) FontWeight.Bold else FontWeight.Normal,
                                color = if (order == currentSortOrder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onSelectSortOrder(order)
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        FilterChip(
            selected = selectedFilter == null,
            onClick = { onSelectFilter(null) },
            label = { Text("All") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        categories.forEach { category ->
            FilterChip(
                selected = selectedFilter == category,
                onClick = { onSelectFilter(category) },
                label = { Text("${category.emoji} ${category.displayName}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
fun ExpenseItem(
    expense: Expense,
    onDeleteClick: () -> Unit,
    onIconClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpense = expense.type == TransactionType.EXPENSE

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon/Emoji in colored circle (clickable to view details and change category)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(expense.category.color.copy(alpha = 0.25f))
                    .clickable { onIconClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = expense.category.emoji,
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Remark / Category / Date
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = expense.remark.ifBlank { expense.category.displayName },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

            // Amount
            Text(
                text = if (isExpense) "-${formatRupees(expense.amount)}" else "+${formatRupees(expense.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isExpense) MaterialTheme.colorScheme.error else IncomeGreen
            )

            // Delete action
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

@Composable
fun DeletedExpenseItem(
    expense: Expense,
    onRestoreClick: () -> Unit,
    onIconClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isExpense = expense.type == TransactionType.EXPENSE

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
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
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(expense.category.color.copy(alpha = 0.12f))
                    .then(if (onIconClick != null) Modifier.clickable { onIconClick() } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = expense.category.emoji,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = expense.remark.ifBlank { expense.category.displayName },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = TextDecoration.LineThrough
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = expense.source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = expense.date.format(dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                val reason = expense.effectiveIgnoreReason
                if (!reason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "🚫 $reason",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = if (isExpense) "-${formatRupees(expense.amount)}" else "+${formatRupees(expense.amount)}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = TextDecoration.LineThrough
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onRestoreClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Restore,
                    contentDescription = "Restore",
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Restore", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun EmptyExpensesView(
    mode: TrackerMode,
    selectedMonth: YearMonth,
    isFiltered: Boolean,
    isSyncing: Boolean,
    onAddClick: () -> Unit,
    onAutoPopulateClick: () -> Unit,
    onTrySampleSmsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpense = mode == TrackerMode.EXPENSES

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isFiltered) "🔍" else if (isExpense) "📊" else "💰",
            fontSize = 54.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isFiltered)
                "No ${if (isExpense) "expenses" else "income entries"} found for this category"
            else
                "No ${if (isExpense) "expenses" else "income"} in ${selectedMonth.format(monthFormatter)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isFiltered)
                "Try switching categories or add a new entry."
            else if (isExpense)
                "Keep track of your spending by recording daily expenses or scanning your bank debit SMS."
            else
                "Keep track of your earnings by recording salary, freelance, or scanning your bank credit SMS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isExpense) MaterialTheme.colorScheme.primary else IncomeGreen
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(AppIcons.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isExpense) "Add Expense" else "Add Income")
            }

            OutlinedButton(
                onClick = onAutoPopulateClick,
                enabled = !isSyncing,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(AppIcons.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan SMS")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onTrySampleSmsClick) {
            Text(
                text = if (isExpense) "Load Sample Bank SMS" else "Load Sample Credit SMS",
                style = MaterialTheme.typography.labelSmall,
                color = if (isExpense) MaterialTheme.colorScheme.primary else IncomeGreen
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseChoiceBottomSheet(
    mode: TrackerMode,
    onDismiss: () -> Unit,
    onEnterManually: () -> Unit,
    onScanSms: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isExpense = mode == TrackerMode.EXPENSES

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isExpense) "Add Expense" else "Add Income",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Option 1: Enter Manually
            Surface(
                onClick = onEnterManually,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isExpense) MaterialTheme.colorScheme.primaryContainer else IncomeContainerGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Add,
                            contentDescription = null,
                            tint = if (isExpense) MaterialTheme.colorScheme.onPrimaryContainer else IncomeGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enter manually",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isExpense)
                                "Add an expense with amount, category & date"
                            else
                                "Add income with amount, source & date",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = AppIcons.ArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Option 2: Scan SMS
            Surface(
                onClick = onScanSms,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Sms,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Scan SMS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isExpense)
                                "Auto-detect bank debit and card spend messages"
                            else
                                "Auto-detect salary, bank credit & refund messages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = AppIcons.ArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseBottomSheet(
    mode: TrackerMode = TrackerMode.EXPENSES,
    initialMonth: YearMonth,
    onDismiss: () -> Unit,
    categories: List<ExpenseCategory> = ExpenseCategory.DEFAULT_CATEGORIES,
    onAddExpense: (amount: Double, remark: String, category: ExpenseCategory, date: LocalDate, source: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val isExpense = mode == TrackerMode.EXPENSES

    var amountText by remember { mutableStateOf("") }
    var remarkText by remember { mutableStateOf("") }
    var selectedCategory by remember(categories) {
        mutableStateOf(categories.firstOrNull() ?: if (isExpense) ExpenseCategory.FOOD else ExpenseCategory.SALARY)
    }
    var selectedSource by remember { mutableStateOf(if (isExpense) "UPI" else "Bank") }

    var selectedDate by remember {
        val today = LocalDate.now()
        val defaultDate = if (initialMonth == YearMonth.from(today)) today else initialMonth.atDay(1)
        mutableStateOf(defaultDate)
    }

    var amountError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (isExpense) "Add New Expense" else "Add New Income",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isExpense) MaterialTheme.colorScheme.onSurface else IncomeOnContainerGreen
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Amount Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
                        amountText = input
                        amountError = null
                    }
                },
                label = { Text(if (isExpense) "Expense Amount" else "Income Amount") },
                placeholder = { Text("0.00") },
                prefix = {
                    Text(
                        text = "₹ ",
                        fontWeight = FontWeight.Bold,
                        color = if (isExpense) MaterialTheme.colorScheme.primary else IncomeGreen
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountError != null,
                supportingText = {
                    amountError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Remark / Source Payer Input
            OutlinedTextField(
                value = remarkText,
                onValueChange = { remarkText = it },
                label = { Text(if (isExpense) "Remark / Description" else "Source / Payer / Description") },
                placeholder = {
                    Text(if (isExpense) "e.g. Groceries, Team dinner, Uber" else "e.g. Monthly Salary, Freelance project, Dividend")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category Selection
            Text(
                text = "Select Category",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = selectedCategory == category
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) {
                            category.color.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, if (isExpense) MaterialTheme.colorScheme.primary else IncomeGreen)
                        } else null,
                        modifier = Modifier
                            .clickable { selectedCategory = category }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(text = category.emoji, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Payment Source Selection
            Text(
                text = if (isExpense) "Payment Source" else "Received Through",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            val sources = if (isExpense) listOf("UPI", "Card", "Cash", "Bank") else listOf("Bank", "UPI", "Cash", "Cheque", "Card")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                sources.forEach { src ->
                    FilterChip(
                        selected = selectedSource == src,
                        onClick = { selectedSource = src },
                        label = { Text(src) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (isExpense) MaterialTheme.colorScheme.primary else IncomeGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date Picker
            Text(
                text = "Date",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                        },
                        selectedDate.year,
                        selectedDate.monthValue - 1,
                        selectedDate.dayOfMonth
                    ).show()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = AppIcons.Calendar, contentDescription = "Pick Date")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedDate.format(dateFormatter),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull()
                        if (amount == null || amount <= 0.0) {
                            amountError = "Please enter a valid amount greater than 0"
                        } else {
                            onAddExpense(
                                amount,
                                remarkText,
                                selectedCategory,
                                selectedDate,
                                selectedSource
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isExpense) MaterialTheme.colorScheme.primary else IncomeGreen
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isExpense) "Save Expense" else "Save Income")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MonthNavigatorPreview() {
    ExpenseTrackerTheme {
        MonthNavigator(
            selectedMonth = YearMonth.now(),
            onPreviousMonth = {},
            onNextMonth = {},
            onJumpToToday = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MonthlySummaryCardPreview() {
    ExpenseTrackerTheme {
        MonthlySummaryCard(
            mode = TrackerMode.EXPENSES,
            selectedMonth = YearMonth.now(),
            totalAmount = 8450.00,
            itemCount = 8,
            categoryTotals = mapOf(
                ExpenseCategory.FOOD to 3450.00,
                ExpenseCategory.TRANSPORT to 1200.00,
                ExpenseCategory.SHOPPING to 2300.00,
                ExpenseCategory.BILLS to 1500.00
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ExpenseItemPreview() {
    ExpenseTrackerTheme {
        ExpenseItem(
            expense = Expense(
                amount = 450.00,
                remark = "Team lunch at cafe",
                category = ExpenseCategory.FOOD,
                date = LocalDate.now()
            ),
            onDeleteClick = {},
            onIconClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
fun FullExpenseScreenPreview() {
    ExpenseTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                MonthlySummaryCard(
                    mode = TrackerMode.EXPENSES,
                    selectedMonth = YearMonth.of(2026, 11),
                    totalAmount = 14850.00,
                    itemCount = 4,
                    categoryTotals = mapOf(
                        ExpenseCategory.FOOD to 4850.00,
                        ExpenseCategory.SHOPPING to 6500.00,
                        ExpenseCategory.TRANSPORT to 2000.00,
                        ExpenseCategory.BILLS to 1500.00
                    ),
                    totalExpenseAmount = 14850.00,
                    totalIncomeAmount = 75000.00,
                    netMonthlySavings = 60150.00,
                    onClick = {}
                )

                FilterAndSortBar(
                    selectedFilter = null,
                    onSelectFilter = {},
                    currentSortOrder = ExpenseSortOrder.DATE_DESC,
                    onSelectSortOrder = {},
                    categories = ExpenseCategory.DEFAULT_CATEGORIES
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpenseItem(
                        expense = Expense(
                            amount = 1362.03,
                            remark = "AMAZON PAY IN E",
                            category = ExpenseCategory.SHOPPING,
                            date = LocalDate.of(2026, 11, 25),
                            source = "Card"
                        ),
                        onDeleteClick = {},
                        onIconClick = {}
                    )

                    ExpenseItem(
                        expense = Expense(
                            amount = 955.65,
                            remark = "Hindustan Petroleum Co",
                            category = ExpenseCategory.TRANSPORT,
                            date = LocalDate.of(2026, 11, 24),
                            source = "Regalia Card"
                        ),
                        onDeleteClick = {},
                        onIconClick = {}
                    )

                    Text(
                        text = "Deleted Expenses & Exclusions (2)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )

                    DeletedExpenseItem(
                        expense = Expense(
                            amount = 25000.00,
                            remark = "House Rent",
                            category = ExpenseCategory.BILLS,
                            date = LocalDate.of(2026, 11, 1),
                            source = "Bank",
                            isDeleted = true,
                            ignoreReason = "Matches exclusion filter: \"House Rent\""
                        ),
                        onRestoreClick = {}
                    )

                    DeletedExpenseItem(
                        expense = Expense(
                            amount = 91500.00,
                            remark = "NACH-MUT-ZERODHA BROKING",
                            category = ExpenseCategory.INVESTMENTS,
                            date = LocalDate.of(2026, 11, 17),
                            source = "Bank",
                            isDeleted = true,
                            ignoreReason = "Excluded by SMS filter: Auto-debit / Mandate (nach)"
                        ),
                        onRestoreClick = {}
                    )
                }
            }
        }
    }
}
