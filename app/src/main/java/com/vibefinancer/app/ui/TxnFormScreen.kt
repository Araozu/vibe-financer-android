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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
            // Fresh form state per target account: warm widget taps for another
            // account must not reuse the previous account's half-filled form.
            androidx.compose.runtime.key(initialAccountId) {
                TransactionForm(repo, accounts, initialAccountId, onSaved, autofocusAmount = true)
            }
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
    modifier: Modifier = Modifier,
    autofocusAmount: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val amountFocus = remember { FocusRequester() }

    // Keyed on the deep-link account so a warm widget tap for another account
    // resets the selection instead of keeping the previous account.
    var accountId by remember(initialAccountId) { mutableStateOf(initialAccountId ?: "") }
    var typeIndex by rememberSaveable { mutableStateOf(0) }
    var amount by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var payee by rememberSaveable { mutableStateOf("") }
    var toAccountId by rememberSaveable { mutableStateOf("") }
    var exchangeRate by rememberSaveable { mutableStateOf("") }
    var dateTime by remember { mutableStateOf(LocalDateTime.now()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    // Amount is preselected on the dedicated form screen: focus it as soon as
    // the form opens so the numeric keyboard is already up for one-tap widget
    // entries. Selection covers any existing text so typing replaces it
    // outright. The inline dashboard form must NOT steal focus.
    LaunchedEffect(Unit) {
        if (autofocusAmount) {
            amountFocus.requestFocus()
            keyboard?.show()
        }
    }

    LaunchedEffect(accounts) {
        if (accounts.isEmpty()) return@LaunchedEffect
        if (accounts.none { it.id == accountId }) accountId = accounts.first().id
        if (accounts.none { it.id == toAccountId } || toAccountId == accountId) {
            toAccountId = accounts.firstOrNull { it.id != accountId }?.id ?: ""
        }
    }

    val type = TYPES[typeIndex]
    val isTransfer = type == "transfer"

    // Balance labels are memoized: NumberFormat construction per account per
    // keystroke was the main source of typing jank in this form.
    val accountLabels = remember(accounts) {
        accounts.associate { acc ->
            acc.id to "${acc.name} · ${formatMoney(acc.currentBalance, acc.currencyCode, acc.currencySymbol)}"
        }
    }
    val toAccounts = remember(accounts, accountId) {
        accounts.filter { it.id != accountId }
    }

    fun save() {
        if (busy) return
        val amt = amount.text.replace(',', '.').toDoubleOrNull()
        val validation = when {
            accountId.isBlank() -> "Pick an account"
            name.isBlank() -> "Enter a title"
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
                    name = name.trim().ifBlank { null },
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
                    amount = TextFieldValue("")
                    name = ""
                    category = ""
                    payee = ""
                    exchangeRate = ""
                    dateTime = LocalDateTime.now()
                    onSaved()
                    // Dedicated form screen: keep the fast-entry loop going
                    // with amount focused and keyboard up.
                    if (autofocusAmount) {
                        amountFocus.requestFocus()
                        keyboard?.show()
                    }
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

        AccountDropdown("Account", accounts, accountLabels, accountId, onPick = {
            accountId = it
            if (toAccountId == it) toAccountId = accounts.firstOrNull { a -> a.id != it }?.id ?: ""
        })
        if (isTransfer) {
            AccountDropdown(
                "To account",
                toAccounts,
                accountLabels,
                toAccountId,
                onPick = { toAccountId = it }
            )
        }

        AmountField(
            value = amount,
            onValueChange = { amount = it },
            focusRequester = amountFocus,
            onDone = { save() }
        )
        TitleField(
            value = name,
            onValueChange = { name = it },
            onDone = { save() }
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
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
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

/**
 * Amount entry: focused on open with the numeric keyboard up, existing text
 * selected so typing replaces it, and Done/enter submits the form.
 * A separate scope bounds each keystroke's recomposition to this field.
 */
@Composable
private fun AmountField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Amount") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused && value.text.isNotEmpty()) {
                    onValueChange(value.copy(selection = TextRange(0, value.text.length)))
                }
            }
    )
}

/** Title is required (like the web form); Done/enter submits. */
@Composable
private fun TitleField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Title") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    label: String,
    accounts: List<DetailedAccountDto>,
    labels: Map<String, String>,
    selectedId: String,
    onPick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { labels[it.id] ?: it.name } ?: "Select…",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { acc ->
                DropdownMenuItem(
                    text = { Text(labels[acc.id] ?: acc.name) },
                    onClick = { onPick(acc.id); expanded = false }
                )
            }
        }
    }
}
