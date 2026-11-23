package com.example.expensetracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.model.ExpenseCategory
import com.example.expensetracker.model.TrackerMode
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val mode = uiState.trackerMode

    val excludedKeywords by viewModel.excludedKeywords.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val customCards by viewModel.customCards.collectAsState()

    var newKeywordInput by remember { mutableStateOf("") }

    // Custom Card Names State
    var cardDigitsInput by remember { mutableStateOf("") }
    var cardNameInput by remember { mutableStateOf("") }
    var cardErrorMessage by remember { mutableStateOf<String?>(null) }

    // Category & Tag Management State
    val currentCategories = if (mode == TrackerMode.EXPENSES) categories else incomeCategories

    var selectedCategoryId by remember(currentCategories, mode) {
        mutableStateOf(currentCategories.firstOrNull()?.id ?: "")
    }
    val selectedCategory = currentCategories.find { it.id == selectedCategoryId } ?: currentCategories.firstOrNull()

    var newTagInput by remember { mutableStateOf("") }
    var tagErrorMessage by remember { mutableStateOf<String?>(null) }

    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<ExpenseCategory?>(null) }

    val context = LocalContext.current

    // Collapsed/Expanded states for each settings option (all collapsed by default)
    var isCategoriesExpanded by remember { mutableStateOf(false) }
    var isCardsExpanded by remember { mutableStateOf(false) }
    var isExclusionsExpanded by remember { mutableStateOf(false) }
    var isBackupExpanded by remember { mutableStateOf(false) }
    var isYearScanExpanded by remember { mutableStateOf(false) }

    val currentYear = remember { java.time.Year.now().value }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.autoPopulateForFullYear(currentYear)
        } else {
            Toast.makeText(context, "SMS permission is required to read bank messages", Toast.LENGTH_SHORT).show()
        }
    }

    val requestFullYearSmsScan: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.autoPopulateForFullYear(currentYear)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    var backupStatusMessage by remember { mutableStateOf<String?>(null) }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importExpensesFromUri(context, uri) { result ->
                result.onSuccess { addedCount ->
                    val typeName = if (mode == TrackerMode.EXPENSES) "expense" else "income entry"
                    val typeNamePlural = if (mode == TrackerMode.EXPENSES) "expenses" else "income entries"
                    val msg = if (addedCount > 0) {
                        "Successfully imported $addedCount new ${if (addedCount == 1) typeName else typeNamePlural}!"
                    } else {
                        "File processed: All $typeNamePlural were already in your tracker (0 new added)."
                    }
                    backupStatusMessage = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }.onFailure { ex ->
                    val errorMsg = "Import failed: ${ex.message ?: "Invalid file"}"
                    backupStatusMessage = errorMsg
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = AppIcons.ArrowLeft,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ==========================================
            // Section 1: Categories & SMS Tags Management
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (isCategoriesExpanded) 180f else 0f,
                        label = "categories_chevron"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCategoriesExpanded = !isCategoriesExpanded }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = "🏷️", fontSize = 20.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Categories & SMS Tags",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${categories.size} categories configured",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = AppIcons.ChevronDown,
                            contentDescription = if (isCategoriesExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(chevronRotation)
                        )
                    }

                    AnimatedVisibility(visible = isCategoriesExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                                    Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (mode == TrackerMode.EXPENSES)
                                        "Create custom expense categories and assign SMS tags for auto-classification."
                                    else
                                        "Create custom income categories and assign SMS tags for auto-classification.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { showCreateCategoryDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (mode == TrackerMode.EXPENSES)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            Color(0xFF2E7D32)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Add,
                                        contentDescription = "Add Category",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New", fontSize = 13.sp)
                                }
                            }

                            // Horizontal list of Categories
                            Text(
                                text = "Select Category to Manage Tags (${currentCategories.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                currentCategories.forEach { category ->
                                    val isSelected = category.id == selectedCategory?.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedCategoryId = category.id
                                            tagErrorMessage = null
                                            newTagInput = ""
                                        },
                                        label = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("${category.emoji} ${category.displayName}")
                                                if (category.tags.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                                                        else MaterialTheme.colorScheme.surfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = "${category.tags.size}",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = category.color.copy(alpha = 0.85f),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            // Details Card for Selected Category
                            if (selectedCategory != null) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(1.dp, selectedCategory.color.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = selectedCategory.color.copy(alpha = 0.2f),
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(text = selectedCategory.emoji, fontSize = 20.sp)
                                                    }
                                                }
                                                Column {
                                                    Text(
                                                        text = selectedCategory.displayName,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${selectedCategory.tags.size} SMS tag(s)",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            // Delete Category Button (Cannot delete unknown or if only 1 category remains)
                                            val canDelete = !selectedCategory.id.equals("unknown", ignoreCase = true) &&
                                                !selectedCategory.id.equals("unknown_income", ignoreCase = true) &&
                                                currentCategories.size > 1
                                            if (canDelete) {
                                                IconButton(
                                                    onClick = { categoryToDelete = selectedCategory },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = AppIcons.Delete,
                                                        contentDescription = "Delete Category",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = "SMS messages containing these tags will automatically be categorized under '${selectedCategory.displayName}'. Each tag must be unique across all categories.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        // Input to Add New Tag to This Category
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = newTagInput,
                                                onValueChange = {
                                                    newTagInput = it
                                                    tagErrorMessage = null
                                                },
                                                placeholder = {
                                                    Text(if (mode == TrackerMode.EXPENSES) "e.g. swiggy, uber, bescom" else "e.g. payroll, dividend, client")
                                                },
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp),
                                                isError = tagErrorMessage != null,
                                                modifier = Modifier.weight(1f)
                                            )

                                            Button(
                                                onClick = {
                                                    if (newTagInput.isNotBlank()) {
                                                        if (mode == TrackerMode.EXPENSES) {
                                                            viewModel.addTagToCategory(selectedCategory.id, newTagInput) { result ->
                                                                result.onSuccess {
                                                                    newTagInput = ""
                                                                    tagErrorMessage = null
                                                                }.onFailure { ex ->
                                                                    tagErrorMessage = ex.message ?: "Failed to add tag"
                                                                }
                                                            }
                                                        } else {
                                                            viewModel.addTagToIncomeCategory(selectedCategory.id, newTagInput) { result ->
                                                                result.onSuccess {
                                                                    newTagInput = ""
                                                                    tagErrorMessage = null
                                                                }.onFailure { ex ->
                                                                    tagErrorMessage = ex.message ?: "Failed to add tag"
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                enabled = newTagInput.isNotBlank(),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("Add Tag")
                                            }
                                        }

                                        // Error message if tag already belongs to another category
                                        if (tagErrorMessage != null) {
                                            Text(
                                                text = tagErrorMessage!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        // Existing tags list
                                        if (selectedCategory.tags.isEmpty()) {
                                            Text(
                                                text = "No tags added yet. Add merchant or keyword tags to auto-classify SMS into this category.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                selectedCategory.tags.forEach { tag ->
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = selectedCategory.color.copy(alpha = 0.15f),
                                                        border = BorderStroke(0.5.dp, selectedCategory.color.copy(alpha = 0.5f))
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 2.dp, end = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = tag,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            IconButton(
                                                                onClick = {
                                                                    if (mode == TrackerMode.EXPENSES) {
                                                                        viewModel.removeTagFromCategory(selectedCategory.id, tag)
                                                                    } else {
                                                                        viewModel.removeTagFromIncomeCategory(selectedCategory.id, tag)
                                                                    }
                                                                },
                                                                modifier = Modifier.size(20.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = AppIcons.Close,
                                                                    contentDescription = "Remove tag $tag",
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // Section 2: Custom Card Names
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (isCardsExpanded) 180f else 0f,
                        label = "cards_chevron"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCardsExpanded = !isCardsExpanded }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = "💳", fontSize = 20.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Custom Card Names",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (customCards.isEmpty()) "No custom cards configured" else "${customCards.size} custom cards configured",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = AppIcons.ChevronDown,
                            contentDescription = if (isCardsExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(chevronRotation)
                        )
                    }

                    AnimatedVisibility(visible = isCardsExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Map 4-digit card numbers to custom names (e.g. 7224 → Regalia). Bank SMS containing 'card' and matching digits will display the custom card name as their source.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Input fields for 4 digits and Card Name
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = cardDigitsInput,
                                    onValueChange = { input ->
                                        if (input.length <= 4 && input.all { it.isDigit() }) {
                                            cardDigitsInput = input
                                            cardErrorMessage = null
                                        }
                                    },
                                    label = { Text("Last 4 digits") },
                                    placeholder = { Text("7224") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.width(135.dp)
                                )

                                OutlinedTextField(
                                    value = cardNameInput,
                                    onValueChange = {
                                        cardNameInput = it
                                        cardErrorMessage = null
                                    },
                                    label = { Text("Card Name") },
                                    placeholder = { Text("e.g. Regalia") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Save Button
                            Button(
                                onClick = {
                                    if (cardDigitsInput.length != 4) {
                                        cardErrorMessage = "Please enter exactly 4 digits"
                                    } else if (cardNameInput.isBlank()) {
                                        cardErrorMessage = "Card name cannot be empty"
                                    } else {
                                        viewModel.addOrUpdateCardSource(cardDigitsInput, cardNameInput)
                                        cardDigitsInput = ""
                                        cardNameInput = ""
                                        cardErrorMessage = null
                                    }
                                },
                                enabled = cardDigitsInput.length == 4 && cardNameInput.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Save Card")
                            }

                            if (cardErrorMessage != null) {
                                Text(
                                    text = cardErrorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Configured Cards (${customCards.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (customCards.isEmpty()) {
                                Text(
                                    text = "No custom cards configured. Card expenses will display 'Card' as source.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    customCards.forEach { card ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            shadowElevation = 1.dp
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
                                            ) {
                                                Text(
                                                    text = "💳 •••• ${card.last4Digits} → ",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = card.cardName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = { viewModel.removeCardSource(card.last4Digits) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = AppIcons.Close,
                                                        contentDescription = "Remove card ${card.last4Digits}",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // Section 3: Excluded SMS Texts
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (isExclusionsExpanded) 180f else 0f,
                        label = "exclusions_chevron"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExclusionsExpanded = !isExclusionsExpanded }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = "🚫", fontSize = 20.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Excluded SMS Texts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${excludedKeywords.size} active exclusions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = AppIcons.ChevronDown,
                            contentDescription = if (isExclusionsExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(chevronRotation)
                        )
                    }

                    AnimatedVisibility(visible = isExclusionsExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Bank or credit card SMS messages containing any of these keywords will be ignored and not imported into your ${if (mode == TrackerMode.EXPENSES) "expenses" else "income entries"} (e.g. transfers, investments, specific senders).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Input Field to Add New Exclusion
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newKeywordInput,
                                    onValueChange = { newKeywordInput = it },
                                    placeholder = { Text(if (mode == TrackerMode.EXPENSES) "e.g. refund, salary" else "e.g. rent, Swiggy") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = {
                                        if (newKeywordInput.isNotBlank()) {
                                            viewModel.addExcludedKeyword(newKeywordInput, mode)
                                            newKeywordInput = ""
                                        }
                                    },
                                    enabled = newKeywordInput.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Add")
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Active Exclusions (${excludedKeywords.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Chips of currently excluded keywords
                            if (excludedKeywords.isEmpty()) {
                                Text(
                                    text = "No exclusion keywords set. All detected ${if (mode == TrackerMode.EXPENSES) "expense" else "income"} SMS will be parsed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    excludedKeywords.forEach { keyword ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            shadowElevation = 1.dp
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                                            ) {
                                                Text(
                                                    text = keyword,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = { viewModel.removeExcludedKeyword(keyword, mode) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = AppIcons.Close,
                                                        contentDescription = "Remove $keyword",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Reset to Defaults Button
                            OutlinedButton(
                                onClick = { viewModel.resetDefaultExcludedKeywords(mode) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Reset to Defaults", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // Section 4: Data Export & Import
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (isBackupExpanded) 180f else 0f,
                        label = "backup_chevron"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isBackupExpanded = !isBackupExpanded }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = "📁", fontSize = 20.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Data Export & Import",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Export or import ${if (mode == TrackerMode.EXPENSES) "expenses" else "income entries"} in JSON format",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = AppIcons.ChevronDown,
                            contentDescription = if (isBackupExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(chevronRotation)
                        )
                    }

                    AnimatedVisibility(visible = isBackupExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Export all ${if (mode == TrackerMode.EXPENSES) "expenses" else "income entries"} to a standard JSON file in your device's Downloads folder, which can be consumed by other apps. You can also import any compatible JSON ${if (mode == TrackerMode.EXPENSES) "expense" else "income"} backup.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (backupStatusMessage != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = backupStatusMessage!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { backupStatusMessage = null },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.Close,
                                                contentDescription = "Dismiss",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Export Action Card
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = AppIcons.Export,
                                                    contentDescription = "Export to Downloads",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Export ${if (mode == TrackerMode.EXPENSES) "Expenses" else "Income Entries"}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Saves JSON into Downloads folder",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.exportExpenses(context) { result ->
                                                result.onSuccess { fileName ->
                                                    val msg = "Exported to Downloads/$fileName"
                                                    backupStatusMessage = msg
                                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                }.onFailure { ex ->
                                                    val msg = "Export failed: ${ex.message ?: "Unknown error"}"
                                                    backupStatusMessage = msg
                                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Export")
                                    }
                                }
                            }

                            // Import Action Card
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = AppIcons.Import,
                                                    contentDescription = "Import from JSON",
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Import ${if (mode == TrackerMode.EXPENSES) "Expenses" else "Income Entries"}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Select JSON file from device",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            importFileLauncher.launch(
                                                arrayOf(
                                                    "application/json",
                                                    "text/plain",
                                                    "text/*",
                                                    "*/*"
                                                )
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Import")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // Section 5: Full Year SMS Rescan
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (isYearScanExpanded) 180f else 0f,
                        label = "year_scan_chevron"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isYearScanExpanded = !isYearScanExpanded }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = "📅", fontSize = 20.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Full Year SMS Scan ($currentYear)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Scan all banking SMS messages for $currentYear",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = AppIcons.ChevronDown,
                            contentDescription = if (isYearScanExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(chevronRotation)
                        )
                    }

                    AnimatedVisibility(visible = isYearScanExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Scan all bank and credit card SMS messages for the entire year $currentYear (Jan 1 – Dec 31) for both expenses and income entries. New transactions will be automatically imported and categorized.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Action Button Card
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = AppIcons.Calendar,
                                                    contentDescription = "Scan Current Year SMS",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Scan Entire $currentYear",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Processes Jan–Dec SMS messages",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = { requestFullYearSmsScan() },
                                        enabled = !uiState.isSyncingSms,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(if (uiState.isSyncingSms) "Scanning..." else "Scan $currentYear")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // Dialog: Create New Category
    // ==========================================
    if (showCreateCategoryDialog) {
        CreateCategoryDialog(
            mode = mode,
            onDismiss = { showCreateCategoryDialog = false },
            onCreate = { name, emoji, colorHex ->
                if (mode == TrackerMode.EXPENSES) {
                    viewModel.createCategory(name, emoji, colorHex) { result ->
                        result.onSuccess { newCat ->
                            selectedCategoryId = newCat.id
                            showCreateCategoryDialog = false
                        }
                    }
                } else {
                    viewModel.createIncomeCategory(name, emoji, colorHex) { result ->
                        result.onSuccess { newCat ->
                            selectedCategoryId = newCat.id
                            showCreateCategoryDialog = false
                        }
                    }
                }
            }
        )
    }

    // ==========================================
    // Dialog: Confirm Delete Category
    // ==========================================
    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete '${cat.displayName}'?") },
            text = {
                Text("Are you sure you want to delete this category? Any associated SMS tags (${cat.tags.joinToString(", ")}) will be removed. Existing entries will not be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (mode == TrackerMode.EXPENSES) {
                            viewModel.deleteCategory(cat.id) {
                                categoryToDelete = null
                            }
                        } else {
                            viewModel.deleteIncomeCategory(cat.id) {
                                categoryToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CreateCategoryDialog(
    mode: TrackerMode = TrackerMode.EXPENSES,
    onDismiss: () -> Unit,
    onCreate: (name: String, emoji: String, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf(if (mode == TrackerMode.EXPENSES) "🏷️" else "💼") }
    var selectedColorHex by remember { mutableStateOf(if (mode == TrackerMode.EXPENSES) "#90A4AE" else "#4CAF50") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val presetEmojis = listOf("💼", "💰", "📈", "🏠", "🎁", "💻", "🎉", "🏋️", "✈️", "🎮", "🐾", "☕", "🏥", "🚗", "🍔", "🛍️", "💡", "🎬", "📚", "🏷️")
    val presetColors = listOf(
        "#4CAF50", // Green
        "#00BCD4", // Cyan
        "#3F51B5", // Indigo
        "#FF9800", // Orange
        "#9C27B0", // Purple
        "#E91E63", // Pink
        "#E57373", // Coral Red
        "#64B5F6", // Sky Blue
        "#4DB6AC", // Teal
        "#90A4AE"  // Blue Grey
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (mode == TrackerMode.EXPENSES) "New Expense Category" else "New Income Category",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g. Fitness, Gifts, Rent") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Emoji Selection
                Text(
                    text = "Select Icon ($selectedEmoji)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetEmojis.forEach { emoji ->
                        val isSelected = selectedEmoji == emoji
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { selectedEmoji = emoji }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 18.sp)
                            }
                        }
                    }
                }

                // Color Selection
                Text(
                    text = "Select Color",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetColors.forEach { hex ->
                        val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                        val color = try {
                            Color(android.graphics.Color.parseColor(hex))
                        } catch (_: Exception) {
                            Color.Gray
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color, CircleShape)
                                .clickable { selectedColorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White,
                                    modifier = Modifier.size(10.dp)
                                ) {}
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "Category name cannot be empty"
                    } else {
                        onCreate(name.trim(), selectedEmoji, selectedColorHex)
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true, widthDp = 380, heightDp = 600)
@Composable
fun FullSettingsScreenPreview() {
    ExpenseTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 3: Excluded SMS Texts Preview
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Excluded SMS Keywords (2)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "SMS messages matching these keywords will be moved to exclusions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "House Rent ✖",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "SIP ✖",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Section 5: Full Year SMS Rescan Preview
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = "📅", fontSize = 18.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Full Year SMS Scan (2026)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Scan all banking SMS messages for 2026",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Scan all bank and credit card SMS messages for the entire year 2026 (Jan 1 – Dec 31) for both expenses and income entries.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {},
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Scan Entire 2026")
                        }
                    }
                }
            }
        }
    }
}
