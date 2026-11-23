package com.example.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.expensetracker.data.FileCategoryRepository
import com.example.expensetracker.data.FileExpenseRepository
import com.example.expensetracker.data.FileIncomeRepository
import com.example.expensetracker.data.FileSettingsRepository
import com.example.expensetracker.data.SmsReader
import com.example.expensetracker.ui.ChartScreen
import com.example.expensetracker.ui.ExpenseScreen
import com.example.expensetracker.ui.ExpenseViewModel
import com.example.expensetracker.ui.ExpenseViewModelFactory
import com.example.expensetracker.ui.SettingsScreen
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme

enum class AppScreen {
    EXPENSES,
    SETTINGS,
    CHART,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = FileExpenseRepository(applicationContext)
        val smsReader = SmsReader(applicationContext)
        val settingsRepository = FileSettingsRepository(applicationContext)
        val categoryRepository = FileCategoryRepository(applicationContext)
        val incomeRepository = FileIncomeRepository(applicationContext)

        val viewModel = ViewModelProvider(
            this,
            ExpenseViewModelFactory(repository, smsReader, settingsRepository, categoryRepository, incomeRepository),
        )[ExpenseViewModel::class.java]

        setContent {
            ExpenseTrackerTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.EXPENSES) }

                BackHandler(enabled = currentScreen != AppScreen.EXPENSES) {
                    currentScreen = AppScreen.EXPENSES
                }

                when (currentScreen) {
                    AppScreen.EXPENSES -> {
                        ExpenseScreen(
                            viewModel = viewModel,
                            onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                            onOpenChart = { currentScreen = AppScreen.CHART },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    AppScreen.SETTINGS -> {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentScreen = AppScreen.EXPENSES },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    AppScreen.CHART -> {
                        ChartScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentScreen = AppScreen.EXPENSES },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
