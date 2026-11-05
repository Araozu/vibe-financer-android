package com.vibefinancer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vibefinancer.app.data.ApiResult
import com.vibefinancer.app.data.CreateTransactionRequest
import com.vibefinancer.app.data.DetailedAccountDto
import com.vibefinancer.app.data.FinancerRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TYPES = listOf("expense", "income", "transfer")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxnFormScreen(
    repo: FinancerRepository,
    accounts: List<DetailedAccountDto>,
    initialAccountId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            TransactionForm(repo, accounts, initialAccountId, onSaved)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionForm(
    repo: FinancerRepository,
    accounts: List<DetailedAccountDto>,
    initialAccountId: String?,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var accountId by remember { mutableStateOf(initialAccountId ?: "") }
    var typeIndex by remember { mutableStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    var toAccountId by remember { mutableStateOf("") }
    var exchangeRate by remember { mutableStateOf("") }
    var dateTime by remember { mutableStateOf(LocalDateTime.now()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    LaunchedEffect(accounts) {
        if (accounts.isEmpty()) return@LaunchedEffect
        if (accounts.none { it.id == accountId }) accountId = accounts.first().id
        if (accounts.none { it.id == toAccountId } || toAccountId == accountId) {
            toAccountId = accounts.firstOrNull { it.id != accountId }?.id ?: ""
        }
    }

    val type = TYPES[typeIndex]
    val isTransfer = type == "transfer"

    fun save() {
        val amt = amount.replace(',', '.').toDoubleOrNull()
        val validation = when {
            accountId.isBlank() -> "Pick an account"
            amt == null || amt <= 0 -> "Enter a valid amount"
            isTransfer && toAccountId.isBlank() -> "Pick a destination account"
            else -> null
        }
        if (validation != null || amt == null) {
            error = validation ?: "Enter a valid amount"
            return
        }
        error = null
        busy = true
        scope.launch {
            val createdAt = dateTime.atZone(ZoneId.systemDefault()).toInstant().toString()
            val res = repo.createTransaction(
                CreateTransactionRequest(
                    accountId = accountId,
                    type = type,
                    amount = amt,
                    name = name.ifBlank { null },
                    description = null,
                    category = category.ifBlank { null },
                    payee = payee.ifBlank { null },
                    toAccountId = toAccountId.ifBlank { null }.takeIf { isTransfer },
                    exchangeRate = exchangeRate.replace(',', '.').toDoubleOrNull()?.takeIf { isTransfer },
                    createdAt = createdAt
                )
            )
            busy = false
            when (res) {
                is ApiResult.Ok -> {
                    amount = ""
                    name = ""
                    category = ""
                    payee = ""
                    exchangeRate = ""
                    dateTime = LocalDateTime.now()
                    onSaved()
                }
                is ApiResult.Err -> error = res.message
            }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TYPES.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = typeIndex == i,
                    onClick = { typeIndex = i },
                    shape = SegmentedButtonDefaults.itemShape(i, TYPES.size)
                ) { Text(t.replaceFirstChar { c -> c.uppercase() }) }
            }
        }

        AccountDropdown("Account", accounts, accountId, onPick = {
            accountId = it
            if (toAccountId == it) toAccountId = accounts.firstOrNull { a -> a.id != it }?.id ?: ""
        })
        if (isTransfer) {
            AccountDropdown(
                "To account",
                accounts.filter { it.id != accountId },
                toAccountId,
                onPick = { toAccountId = it }
            )
        }

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = payee,
                onValueChange = { payee = it },
                label = { Text("Payee") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        if (isTransfer) {
            OutlinedTextField(
                value = exchangeRate,
                onValueChange = { exchangeRate = it },
                label = { Text("Exchange rate (only if currencies differ)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { showDate = true },
                label = { Text(dateTime.format(DATE_FORMAT)) },
                leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            AssistChip(
                onClick = { showTime = true },
                label = { Text(dateTime.format(TIME_FORMAT)) },
                leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { save() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Text("Add transaction")
            }
        }
    }

    if (showDate) {
        val dp = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dp.selectedDateMillis?.let { millis ->
                        val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        dateTime = LocalDateTime.of(d, dateTime.toLocalTime())
                    }
                    showDate = false
                }) { Text("OK") }
            }
        ) { DatePicker(dp) }
    }
    if (showTime) {
        val tp = rememberTimePickerState(dateTime.hour, dateTime.minute, true)
        Dialog(onDismissRequest = { showTime = false }) {
            Surface(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimePicker(tp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            dateTime = dateTime.withHour(tp.hour).withMinute(tp.minute)
                            showTime = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    label: String,
    accounts: List<DetailedAccountDto>,
    selectedId: String,
    onPick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "Select…",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { acc ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${acc.name} · ${formatMoney(acc.currentBalance, acc.currencyCode, acc.currencySymbol)}"
                        )
                    },
                    onClick = { onPick(acc.id); expanded = false }
                )
            }
        }
    }
}
