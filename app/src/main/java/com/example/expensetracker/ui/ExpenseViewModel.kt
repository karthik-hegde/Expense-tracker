package com.example.expensetracker.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.CategoryRepository
import com.example.expensetracker.data.ExpenseJsonSerializer
import com.example.expensetracker.data.ExpenseRepository
import com.example.expensetracker.data.FileCategoryRepository
import com.example.expensetracker.data.FileExpenseRepository
import com.example.expensetracker.data.FileIncomeRepository
import com.example.expensetracker.data.FileSettingsRepository
import com.example.expensetracker.data.IncomeRepository
import com.example.expensetracker.data.SettingsRepository
import com.example.expensetracker.data.SmsReader
import com.example.expensetracker.model.CustomCardSource
import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.ExpenseCategory
import com.example.expensetracker.model.ExpenseSortOrder
import com.example.expensetracker.model.TrackerMode
import com.example.expensetracker.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

private val amountDescComparator = compareByDescending<Expense> { it.amount }.thenByDescending { it.date }
private val amountAscComparator = compareBy<Expense> { it.amount }.thenByDescending { it.date }
private val dateDescComparator = compareByDescending<Expense> { it.date }.thenByDescending { it.amount }
private val dateAscComparator = compareBy<Expense> { it.date }.thenByDescending { it.amount }

data class ExpenseUiState(
    val trackerMode: TrackerMode = TrackerMode.EXPENSES,
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedCategoryFilter: ExpenseCategory? = null,
    val sortOrder: ExpenseSortOrder = ExpenseSortOrder.DATE_DESC,
    val monthlyExpenses: List<Expense> = emptyList(), // Current mode's active items for display
    val deletedMonthlyExpenses: List<Expense> = emptyList(), // Current mode's deleted items
    val totalMonthlyAmount: Double = 0.0, // Current mode's total
    val totalMonthlyExpenseAmount: Double = 0.0,
    val totalMonthlyIncomeAmount: Double = 0.0,
    val netMonthlySavings: Double = 0.0, // totalMonthlyIncome - totalMonthlyExpense
    val categoryTotals: Map<ExpenseCategory, Double> = emptyMap(),
    val isAddExpenseDialogOpen: Boolean = false,
    val isSyncingSms: Boolean = false,
    val syncMessage: String? = null
)

class ExpenseViewModel(
    private val repository: ExpenseRepository,
    private val smsReader: SmsReader? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val categoryRepository: CategoryRepository? = null,
    private val incomeRepository: IncomeRepository? = null
) : ViewModel() {

    private val _trackerMode = MutableStateFlow(TrackerMode.EXPENSES)
    val trackerMode: StateFlow<TrackerMode> = _trackerMode.asStateFlow()

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    private val _selectedCategoryFilter = MutableStateFlow<ExpenseCategory?>(null)
    private val _sortOrder = MutableStateFlow(
        settingsRepository?.getSortOrder()?.value ?: ExpenseSortOrder.DATE_DESC
    )
    private val _isAddExpenseDialogOpen = MutableStateFlow(false)
    private val _isSyncingSms = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)

    val expenseExcludedKeywords: StateFlow<List<String>> = settingsRepository?.getExcludedKeywords(TrackerMode.EXPENSES)
        ?: MutableStateFlow(FileSettingsRepository.defaultExpenseKeywords)
        
    val incomeExcludedKeywords: StateFlow<List<String>> = settingsRepository?.getExcludedKeywords(TrackerMode.INCOME)
        ?: MutableStateFlow(FileSettingsRepository.defaultIncomeKeywords)
        
    val excludedKeywords: StateFlow<List<String>> = combine(_trackerMode, expenseExcludedKeywords, incomeExcludedKeywords) { mode, exp, inc ->
        if (mode == TrackerMode.EXPENSES) exp else inc
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val categories: StateFlow<List<ExpenseCategory>> = categoryRepository?.getCategories()
        ?: MutableStateFlow(ExpenseCategory.DEFAULT_CATEGORIES)

    val incomeCategories: StateFlow<List<ExpenseCategory>> = categoryRepository?.getIncomeCategories()
        ?: MutableStateFlow(ExpenseCategory.DEFAULT_INCOME_CATEGORIES)

    val customCards: StateFlow<List<CustomCardSource>> = settingsRepository?.getCustomCardSources()
        ?: MutableStateFlow(emptyList())

    val allExpenses: StateFlow<List<Expense>> = repository.getExpenses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allIncomes: StateFlow<List<Expense>> = (incomeRepository?.getIncomes() ?: MutableStateFlow(emptyList()))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        if (repository is FileExpenseRepository) {
            viewModelScope.launch {
                repository.initialize()
            }
        }
        if (incomeRepository is FileIncomeRepository) {
            viewModelScope.launch {
                incomeRepository.initialize()
            }
        }
        if (settingsRepository is FileSettingsRepository) {
            viewModelScope.launch {
                settingsRepository.initialize()
            }
        }
        if (categoryRepository is FileCategoryRepository) {
            viewModelScope.launch {
                categoryRepository.initialize()
            }
        }
        if (settingsRepository != null) {
            viewModelScope.launch {
                settingsRepository.getSortOrder().collect { order ->
                    _sortOrder.value = order
                }
            }
        }
    }

    val uiState: StateFlow<ExpenseUiState> = combine(
        repository.getExpenses(),
        incomeRepository?.getIncomes() ?: MutableStateFlow<List<Expense>>(emptyList()),
        _trackerMode,
        _selectedMonth,
        _selectedCategoryFilter,
        _sortOrder,
        _isAddExpenseDialogOpen,
        _isSyncingSms,
        _syncMessage
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val allExpenses = args[0] as List<Expense>
        @Suppress("UNCHECKED_CAST")
        val allIncomes = args[1] as List<Expense>
        val mode = args[2] as TrackerMode
        val month = args[3] as YearMonth
        val filter = args[4] as? ExpenseCategory
        val sortOrder = args[5] as ExpenseSortOrder
        val isDialogOpen = args[6] as Boolean
        val isSyncing = args[7] as Boolean
        val syncMsg = args[8] as String?

        // Calculate Expense metrics for month (optimized single-pass)
        val activeMonthExpenses = ArrayList<Expense>()
        val deletedMonthExpenses = ArrayList<Expense>()
        var totalMonthlyExpenseAmount = 0.0

        for (expense in allExpenses) {
            if (expense.yearMonth == month && expense.type == TransactionType.EXPENSE) {
                if (expense.isDeleted) {
                    deletedMonthExpenses.add(expense)
                } else {
                    activeMonthExpenses.add(expense)
                    totalMonthlyExpenseAmount += expense.amount
                }
            }
        }

        // Calculate Income metrics for month (optimized single-pass)
        val activeMonthIncomes = ArrayList<Expense>()
        val deletedMonthIncomes = ArrayList<Expense>()
        var totalMonthlyIncomeAmount = 0.0

        for (income in allIncomes) {
            if (income.yearMonth == month && income.type == TransactionType.INCOME) {
                if (income.isDeleted) {
                    deletedMonthIncomes.add(income)
                } else {
                    activeMonthIncomes.add(income)
                    totalMonthlyIncomeAmount += income.amount
                }
            }
        }

        val netMonthlySavings = totalMonthlyIncomeAmount - totalMonthlyExpenseAmount

        // Choose items and totals based on active mode
        val activeItems = if (mode == TrackerMode.EXPENSES) activeMonthExpenses else activeMonthIncomes
        val deletedItems = if (mode == TrackerMode.EXPENSES) deletedMonthExpenses else deletedMonthIncomes
        val totalAmount = if (mode == TrackerMode.EXPENSES) totalMonthlyExpenseAmount else totalMonthlyIncomeAmount

        val categoryTotals = LinkedHashMap<ExpenseCategory, Double>()
        for (item in activeItems) {
            categoryTotals[item.category] = (categoryTotals[item.category] ?: 0.0) + item.amount
        }

        val filteredItems = if (filter != null) {
            activeItems.filter { it.category == filter }
        } else {
            activeItems
        }

        val filteredDeleted = if (filter != null) {
            deletedItems.filter { it.category == filter }
        } else {
            deletedItems
        }

        val comparator = when (sortOrder) {
            ExpenseSortOrder.AMOUNT_DESC -> amountDescComparator
            ExpenseSortOrder.AMOUNT_ASC -> amountAscComparator
            ExpenseSortOrder.DATE_DESC -> dateDescComparator
            ExpenseSortOrder.DATE_ASC -> dateAscComparator
        }
        val sortedItems = filteredItems.sortedWith(comparator)

        ExpenseUiState(
            trackerMode = mode,
            selectedMonth = month,
            selectedCategoryFilter = filter,
            sortOrder = sortOrder,
            monthlyExpenses = sortedItems,
            deletedMonthlyExpenses = filteredDeleted,
            totalMonthlyAmount = totalAmount,
            totalMonthlyExpenseAmount = totalMonthlyExpenseAmount,
            totalMonthlyIncomeAmount = totalMonthlyIncomeAmount,
            netMonthlySavings = netMonthlySavings,
            categoryTotals = categoryTotals,
            isAddExpenseDialogOpen = isDialogOpen,
            isSyncingSms = isSyncing,
            syncMessage = syncMsg
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExpenseUiState()
    )

    fun toggleTrackerMode() {
        _trackerMode.value = if (_trackerMode.value == TrackerMode.EXPENSES) {
            TrackerMode.INCOME
        } else {
            TrackerMode.EXPENSES
        }
        _selectedCategoryFilter.value = null
    }

    fun setTrackerMode(mode: TrackerMode) {
        _trackerMode.value = mode
        _selectedCategoryFilter.value = null
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun jumpToCurrentMonth() {
        _selectedMonth.value = YearMonth.now()
    }

    fun setCategoryFilter(category: ExpenseCategory?) {
        _selectedCategoryFilter.value = if (_selectedCategoryFilter.value == category) null else category
    }

    fun setSortOrder(order: ExpenseSortOrder) {
        _sortOrder.value = order
        viewModelScope.launch {
            settingsRepository?.setSortOrder(order)
        }
    }

    fun openAddExpenseDialog() {
        _isAddExpenseDialogOpen.value = true
    }

    fun closeAddExpenseDialog() {
        _isAddExpenseDialogOpen.value = false
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun autoPopulateFromSms() {
        if (smsReader == null) {
            _syncMessage.value = "SMS service is not available."
            return
        }

        viewModelScope.launch {
            _isSyncingSms.value = true
            try {
                val currentMonth = _selectedMonth.value
                val expenseExcluded = expenseExcludedKeywords.value
                val incomeExcluded = incomeExcludedKeywords.value
                val currentCards = customCards.value

                // 1. Scan Expenses
                val availableExpenseCats = categories.value
                val smsExpenses = smsReader.readExpensesForMonth(currentMonth, expenseExcluded, availableExpenseCats, currentCards)
                val addedExpenseCount = repository.addExpenses(smsExpenses)

                // 2. Scan Income
                val addedIncomeCount = if (incomeRepository != null) {
                    val availableIncomeCats = incomeCategories.value
                    val smsIncomes = smsReader.readIncomesForMonth(currentMonth, incomeExcluded, availableIncomeCats, currentCards)
                    incomeRepository.addIncomes(smsIncomes)
                } else {
                    0
                }

                val totalAdded = addedExpenseCount + addedIncomeCount
                val monthName = currentMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }

                _syncMessage.value = when {
                    totalAdded > 0 -> "Found and added $addedExpenseCount expense(s) & $addedIncomeCount income entry(ies) from SMS."
                    smsExpenses.isEmpty() -> "No banking SMS found for $monthName ${currentMonth.year}."
                    else -> "No new entries found. All SMS entries are already up to date."
                }
            } catch (e: Exception) {
                _syncMessage.value = "Failed to read SMS: ${e.message}"
            } finally {
                _isSyncingSms.value = false
            }
        }
    }

    fun populateSampleSms() {
        if (smsReader == null) return
        viewModelScope.launch {
            _isSyncingSms.value = true
            try {
                val currentMonth = _selectedMonth.value
                val expenseExcluded = expenseExcludedKeywords.value
                val incomeExcluded = incomeExcludedKeywords.value
                val currentCards = customCards.value

                val availableExpenseCats = categories.value
                val sampleExpenses = smsReader.getSampleSmsExpenses(currentMonth, expenseExcluded, availableExpenseCats, currentCards)
                val addedExpenseCount = repository.addExpenses(sampleExpenses)

                val addedIncomeCount = if (incomeRepository != null) {
                    val availableIncomeCats = incomeCategories.value
                    val sampleIncomes = smsReader.getSampleSmsIncomes(currentMonth, incomeExcluded, availableIncomeCats, currentCards)
                    incomeRepository.addIncomes(sampleIncomes)
                } else {
                    0
                }

                val totalAdded = addedExpenseCount + addedIncomeCount
                _syncMessage.value = if (totalAdded > 0) {
                    "Populated $addedExpenseCount sample expense(s) & $addedIncomeCount sample income entry(ies)."
                } else {
                    "Sample entries are already populated."
                }
            } catch (e: Exception) {
                _syncMessage.value = "Failed to load sample SMS: ${e.message}"
            } finally {
                _isSyncingSms.value = false
            }
        }
    }

    fun autoPopulateForFullYear(year: Int = java.time.Year.now().value) {
        if (smsReader == null) {
            _syncMessage.value = "SMS service is not available."
            return
        }

        viewModelScope.launch {
            _isSyncingSms.value = true
            try {
                val expenseExcluded = expenseExcludedKeywords.value
                val incomeExcluded = incomeExcludedKeywords.value
                val currentCards = customCards.value

                val availableExpenseCats = categories.value
                val smsExpenses = smsReader.readExpensesForYear(year, expenseExcluded, availableExpenseCats, currentCards)
                val addedExpenseCount = repository.addExpenses(smsExpenses)

                val addedIncomeCount = if (incomeRepository != null) {
                    val availableIncomeCats = incomeCategories.value
                    val smsIncomes = smsReader.readIncomesForYear(year, incomeExcluded, availableIncomeCats, currentCards)
                    incomeRepository.addIncomes(smsIncomes)
                } else {
                    0
                }

                val totalAdded = addedExpenseCount + addedIncomeCount

                _syncMessage.value = when {
                    totalAdded > 0 -> "Scanned full year $year: added $addedExpenseCount expense(s) & $addedIncomeCount income entry(ies)."
                    smsExpenses.isEmpty() -> "No banking SMS found for full year $year."
                    else -> "No new entries found for $year. All SMS entries are up to date."
                }
            } catch (e: Exception) {
                _syncMessage.value = "Failed to read full year SMS: ${e.message}"
            } finally {
                _isSyncingSms.value = false
            }
        }
    }

    fun addExcludedKeyword(keyword: String, mode: TrackerMode) {
        viewModelScope.launch {
            val added = settingsRepository?.addExcludedKeyword(keyword, mode) ?: false
            if (added) {
                _syncMessage.value = "Added \"${keyword.trim()}\" to SMS exclusion list."
                reapplySettingsInternal()
            } else {
                _syncMessage.value = "Keyword is empty or already in the exclusion list."
            }
        }
    }

    fun removeExcludedKeyword(keyword: String, mode: TrackerMode) {
        viewModelScope.launch {
            settingsRepository?.removeExcludedKeyword(keyword, mode)
            _syncMessage.value = "Removed \"${keyword.trim()}\" from exclusion list."
            reapplySettingsInternal()
        }
    }

    fun resetDefaultExcludedKeywords(mode: TrackerMode) {
        viewModelScope.launch {
            settingsRepository?.resetToDefaultKeywords(mode)
            _syncMessage.value = "Reset exclusions to default."
            reapplySettingsInternal()
        }
    }

    // Expense Categories
    fun createCategory(
        name: String,
        emoji: String,
        colorHex: String,
        onResult: (Result<ExpenseCategory>) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (categoryRepository == null) {
                val err = Result.failure<ExpenseCategory>(IllegalStateException("Category repository not available"))
                onResult(err)
                return@launch
            }
            val result = categoryRepository.addCategory(name, emoji, colorHex)
            result.onSuccess {
                _syncMessage.value = "Created expense category '${it.displayName}'."
                reapplySettingsInternal()
            }
            onResult(result)
        }
    }

    fun deleteCategory(
        categoryId: String,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (categoryRepository == null) {
                val err = Result.failure<Unit>(IllegalStateException("Category repository not available"))
                onResult(err)
                return@launch
            }
            val result = categoryRepository.deleteCategory(categoryId)
            result.onSuccess {
                _syncMessage.value = "Category deleted."
                reapplySettingsInternal()
            }.onFailure {
                _syncMessage.value = it.message ?: "Failed to delete category."
            }
            onResult(result)
        }
    }

    fun addTagToCategory(
        categoryId: String,
        tag: String,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (categoryRepository == null) {
                val err = Result.failure<Unit>(IllegalStateException("Category repository not available"))
                onResult(err)
                return@launch
            }
            val result = categoryRepository.addTagToCategory(categoryId, tag)
            result.onSuccess {
                _syncMessage.value = "Tag added to category."
                reapplySettingsInternal()
            }
            onResult(result)
        }
    }

    fun removeTagFromCategory(
        categoryId: String,
        tag: String,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (categoryRepository == null) {
                val err = Result.failure<Unit>(IllegalStateException("Category repository not available"))
                onResult(err)
                return@launch
            }
            val result = categoryRepository.removeTagFromCategory(categoryId, tag)
            result.onSuccess {
                reapplySettingsInternal()
            }
            onResult(result)
        }
    }

    // Income Categories
    fun createIncomeCategory(
        name: String,
        emoji: String,
        colorHex: String,
        onResult: (Result<ExpenseCategory>) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (categoryRepository == null) {
                val err = Result.failure<ExpenseCategory>(IllegalStateException("Category repository not available"))
                onResult(err)
                return@launch
            }
            val result = categoryRepository.addIncomeCategory(name, emoji, colorHex)
            result.onSuccess {
                _syncMessage.value = "Created income category '${it.displayName}'."
                reapplySettingsInternal()
            }
            onResult(result)
        }
    }

    fun deleteIncomeCategory(
        categoryId: String,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (categoryRepository == null) {
                val err = Result.failure<Unit>(IllegalStateException("Category repository not available"))
                onResult(err)
                return@launch
            }
            val result = categoryRepository.deleteIncomeCategory(categoryId)
            result.onSuccess {
                _syncMessage.value = "Income category deleted."
                reapplySettingsInternal()
            }.onFailure {
                _syncMessage.value = it.message ?: "Failed to delete income category."
            }
            onResult(result)
        }
    }

    fun addTagToIncomeCategory(
        categoryId: String,
        tag: String,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (categoryRepository == null) {
                val err = Result.failure<Unit>(IllegalStateException("Category repository not available"))
                onResult(err)
                return@launch
            }
            val result = categoryRepository.addTagToIncomeCategory(categoryId, tag)
            result.onSuccess {
                _syncMessage.value = "Tag added to income category."
                reapplySettingsInternal()
            }
            onResult(result)
        }
    }

    fun removeTagFromIncomeCategory(
        categoryId: String,
        tag: String,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (categoryRepository == null) {
                val err = Result.failure<Unit>(IllegalStateException("Category repository not available"))
                onResult(err)
                return@launch
            }
            val result = categoryRepository.removeTagFromIncomeCategory(categoryId, tag)
            result.onSuccess {
                reapplySettingsInternal()
            }
            onResult(result)
        }
    }

    fun addOrUpdateCardSource(
        last4Digits: String,
        cardName: String,
        onResult: (Result<CustomCardSource>) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (settingsRepository == null) {
                val err = Result.failure<CustomCardSource>(IllegalStateException("Settings repository not available"))
                onResult(err)
                return@launch
            }
            val result = settingsRepository.addOrUpdateCardSource(last4Digits, cardName)
            result.onSuccess {
                _syncMessage.value = "Saved card '${it.cardName}' (•••• ${it.last4Digits})."
                reapplySettingsInternal()
            }
            onResult(result)
        }
    }

    fun removeCardSource(
        last4Digits: String,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (settingsRepository == null) {
                val err = Result.failure<Unit>(IllegalStateException("Settings repository not available"))
                onResult(err)
                return@launch
            }
            val result = settingsRepository.removeCardSource(last4Digits)
            result.onSuccess {
                _syncMessage.value = "Removed card mapping."
                reapplySettingsInternal()
            }
            onResult(result)
        }
    }

    fun reapplySettings() {
        viewModelScope.launch {
            reapplySettingsInternal()
        }
    }

    private suspend fun reapplySettingsInternal() {
        val currentCats = categories.value
        val currentIncomeCats = incomeCategories.value
        val expenseExclusions = expenseExcludedKeywords.value
        val incomeExclusions = incomeExcludedKeywords.value
        val currentCards = customCards.value
        repository.reapplySettings(currentCats, expenseExclusions, currentCards)
        incomeRepository?.reapplySettings(currentIncomeCats, incomeExclusions, currentCards)
    }

    fun addExpense(
        amount: Double,
        remark: String,
        category: ExpenseCategory,
        date: LocalDate,
        source: String = "Manual",
        type: TransactionType = if (_trackerMode.value == TrackerMode.EXPENSES) TransactionType.EXPENSE else TransactionType.INCOME
    ) {
        viewModelScope.launch {
            val item = Expense(
                amount = amount,
                remark = remark.trim(),
                category = category,
                date = date,
                source = source,
                type = type
            )
            if (type == TransactionType.EXPENSE) {
                repository.addExpense(item)
            } else {
                incomeRepository?.addIncome(item)
            }
            _selectedMonth.value = YearMonth.from(date)
            _isAddExpenseDialogOpen.value = false
        }
    }

    fun updateExpenseCategory(id: String, newCategory: ExpenseCategory, type: TransactionType? = null) {
        val isExpense = type?.let { it == TransactionType.EXPENSE } ?: (_trackerMode.value == TrackerMode.EXPENSES)
        viewModelScope.launch {
            if (isExpense) {
                repository.updateExpenseCategory(id, newCategory)
            } else {
                incomeRepository?.updateIncomeCategory(id, newCategory)
            }
            _syncMessage.value = "Updated category to '${newCategory.displayName}'."
        }
    }

    fun deleteExpense(id: String, permanently: Boolean = false, type: TransactionType? = null) {
        val isExpense = type?.let { it == TransactionType.EXPENSE } ?: (_trackerMode.value == TrackerMode.EXPENSES)
        viewModelScope.launch {
            if (isExpense) {
                repository.deleteExpense(id, permanently)
                _syncMessage.value = if (permanently) "Expense permanently deleted." else "Expense moved to deleted."
            } else {
                incomeRepository?.deleteIncome(id, permanently)
                _syncMessage.value = if (permanently) "Income permanently deleted." else "Income moved to deleted."
            }
        }
    }

    fun restoreExpense(id: String) {
        val mode = _trackerMode.value
        viewModelScope.launch {
            if (mode == TrackerMode.EXPENSES) {
                repository.restoreExpense(id)
                _syncMessage.value = "Expense restored."
            } else {
                incomeRepository?.restoreIncome(id)
                _syncMessage.value = "Income restored."
            }
        }
    }

    fun deleteAllExpenses(onlyImported: Boolean = true, permanently: Boolean = false) {
        val mode = _trackerMode.value
        viewModelScope.launch {
            if (mode == TrackerMode.EXPENSES) {
                repository.deleteAllExpenses(onlyImported, permanently)
                _syncMessage.value = when {
                    onlyImported && permanently -> "Imported expenses permanently deleted."
                    onlyImported && !permanently -> "Imported expenses moved to deleted (manual entries kept)."
                    !onlyImported && permanently -> "All expenses permanently deleted."
                    else -> "All expenses moved to deleted."
                }
            } else {
                incomeRepository?.deleteAllIncomes(onlyImported, permanently)
                _syncMessage.value = when {
                    onlyImported && permanently -> "Imported income entries permanently deleted."
                    onlyImported && !permanently -> "Imported income entries moved to deleted (manual entries kept)."
                    !onlyImported && permanently -> "All income entries permanently deleted."
                    else -> "All income entries moved to deleted."
                }
            }
        }
    }

    fun restoreAllExpenses() {
        val mode = _trackerMode.value
        viewModelScope.launch {
            if (mode == TrackerMode.EXPENSES) {
                repository.restoreAllExpenses()
                _syncMessage.value = "All deleted expenses restored."
            } else {
                incomeRepository?.restoreAllIncomes()
                _syncMessage.value = "All deleted income entries restored."
            }
        }
    }

    fun purgeAllDeletedExpenses() {
        val mode = _trackerMode.value
        viewModelScope.launch {
            if (mode == TrackerMode.EXPENSES) {
                repository.purgeAllDeletedExpenses()
                _syncMessage.value = "Deleted expenses permanently cleared."
            } else {
                incomeRepository?.purgeAllDeletedIncomes()
                _syncMessage.value = "Deleted income entries permanently cleared."
            }
        }
    }

    fun exportExpenses(context: Context, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mode = _trackerMode.value
                val items = if (mode == TrackerMode.EXPENSES) allExpenses.value else allIncomes.value
                val activeItems = items.filter { !it.isDeleted }
                val toExport = if (activeItems.isNotEmpty()) activeItems else items
                if (toExport.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(Result.failure(IllegalStateException("No ${if (mode == TrackerMode.EXPENSES) "expenses" else "income"} found to export.")))
                    }
                    return@launch
                }
                val (fileName, _) = ExpenseJsonSerializer.exportToDownloads(context, toExport)
                withContext(Dispatchers.Main) {
                    onResult(Result.success(fileName))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(Result.failure(e))
                }
            }
        }
    }

    fun importExpensesFromUri(context: Context, uri: Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Could not open selected file.")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val mode = _trackerMode.value
                val currentCategories = if (mode == TrackerMode.EXPENSES) categories.value else incomeCategories.value
                val parsed = ExpenseJsonSerializer.deserialize(jsonString, currentCategories)
                if (parsed.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(Result.failure(IllegalStateException("No valid items found in JSON.")))
                    }
                    return@launch
                }
                val expenseItems = parsed.filter { it.type == TransactionType.EXPENSE }
                val incomeItems = parsed.filter { it.type == TransactionType.INCOME }

                var addedCount = 0
                if (expenseItems.isNotEmpty()) {
                    addedCount += repository.addExpenses(expenseItems)
                }
                if (incomeItems.isNotEmpty() && incomeRepository != null) {
                    addedCount += incomeRepository.addIncomes(incomeItems)
                } else if (incomeItems.isNotEmpty() && mode == TrackerMode.INCOME) {
                    // If no explicit incomeRepository, fallback
                    addedCount += repository.addExpenses(incomeItems)
                }
                withContext(Dispatchers.Main) {
                    onResult(Result.success(addedCount))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(Result.failure(e))
                }
            }
        }
    }
}

class ExpenseViewModelFactory(
    private val repository: ExpenseRepository,
    private val smsReader: SmsReader? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val categoryRepository: CategoryRepository? = null,
    private val incomeRepository: IncomeRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            return ExpenseViewModel(repository, smsReader, settingsRepository, categoryRepository, incomeRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
