package com.vibefinancer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val TYPES = listOf("expense", "income", "transfer")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxnFormScreen(
    repo: FinancerRepository,
    accounts: List<DetailedAccountDto>,
    initialAccountId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var accountId by remember { mutableStateOf(initialAccountId ?: accounts.firstOrNull()?.id ?: "") }
    var typeIndex by remember { mutableStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    var toAccountId by remember { mutableStateOf(accounts.firstOrNull { it.id != accountId }?.id ?: "") }
    var exchangeRate by remember { mutableStateOf("") }
    var dateTime by remember { mutableStateOf(LocalDateTime.now()) }
    var busy by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val type = TYPES[typeIndex]
    val isTransfer = type == "transfer"

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
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccountDropdown("Account", accounts, accountId, onPick = {
                accountId = it
                if (toAccountId == it) toAccountId = accounts.firstOrNull { a -> a.id != it }?.id ?: ""
            })

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TYPES.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = typeIndex == i,
                        onClick = { typeIndex = i },
                        shape = SegmentedButtonDefaults.itemShape(i, TYPES.size)
                    ) { Text(t.replaceFirstChar { c -> c.uppercase() }) }
                }
            }

            OutlinedTextField(
                value = amount, onValueChange = { amount = it },
                label = { Text("Amount") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = category, onValueChange = { category = it },
                label = { Text("Category (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = payee, onValueChange = { payee = it },
                label = { Text("Payee (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (isTransfer) {
                AccountDropdown(
                    "To account",
                    accounts.filter { it.id != accountId },
                    toAccountId,
                    onPick = { toAccountId = it }
                )
                OutlinedTextField(
                    value = exchangeRate, onValueChange = { exchangeRate = it },
                    label = { Text("Exchange rate (only if currencies differ)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showDate = true }) {
                    Text(dateTime.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
                }
                TextButton(onClick = { showTime = true }) {
                    Text(dateTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val amt = amount.replace(',', '.').toDoubleOrNull()
                    if (accountId.isBlank()) {
                        scope.launch { snackbar.showSnackbar("Pick an account") }; return@Button
                    }
                    if (amt == null || amt <= 0) {
                        scope.launch { snackbar.showSnackbar("Enter a valid amount") }; return@Button
                    }
                    if (isTransfer && toAccountId.isBlank() &&
                        accounts.none { it.id != accountId }
                    ) {
                        scope.launch { snackbar.showSnackbar("Pick a destination account") }; return@Button
                    }
                    busy = true
                    scope.launch {
                        val createdAt = dateTime.toInstant(ZoneOffset.systemDefault().rules.getOffset(Instant.now())).toString()
                        val res = repo.createTransaction(
                            CreateTransactionRequest(
                                accountId = accountId,
                                type = type,
                                amount = amt,
                                name = name.ifBlank { null },
                                description = null,
                                category = category.ifBlank { null },
                                payee = payee.ifBlank { null },
                                toAccountId = toAccountId.ifBlank {
                                    accounts.firstOrNull { it.id != accountId }?.id
                                }.takeIf { isTransfer },
                                exchangeRate = exchangeRate.replace(',', '.').toDoubleOrNull()?.takeIf { isTransfer },
                                createdAt = createdAt
                            )
                        )
                        busy = false
                        when (res) {
                            is ApiResult.Ok -> onSaved()
                            is ApiResult.Err -> snackbar.showSnackbar(res.message)
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("Save")
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
            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
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
