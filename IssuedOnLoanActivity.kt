package com.example.homeaccountingapp

import android.app.Application
import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.homeaccountingapp.ui.theme.HomeAccountingAppTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.*

class IssuedOnLoanActivity : ComponentActivity() {
    private val loanViewModel: LoanViewModel by viewModels { LoanViewModelFactory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeAccountingAppTheme {
                IssuedOnLoanScreen(viewModel = loanViewModel)
            }
        }
    }
}

@Composable
fun IssuedOnLoanScreen(viewModel: LoanViewModel) {
    var showAddLoanDialog by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<LoanTransaction?>(null) }
    val transactions by viewModel.transactions.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background_app),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Red transparent gradient at the top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp) // Adjust the height as necessary
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFDC143C).copy(alpha = 0.7f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Видано в борг",
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White),
                modifier = Modifier.padding(30.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(transactions) { loanTransaction ->
                    LoanTransactionRow(loanTransaction, viewModel, onEdit = { transactionToEdit = it })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Загальна сума: ${transactions.sumOf { it.amount }}",
                style = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color.White),
                modifier = Modifier.align(Alignment.Start) // Align text to the start (left)
            )
        }

        FloatingActionButton(
            onClick = { showAddLoanDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFFDC143C)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Transaction", tint = Color.White)
        }

        if (showAddLoanDialog || transactionToEdit != null) {
            AddOrEditLoanTransactionDialog(
                onDismiss = {
                    showAddLoanDialog = false
                    transactionToEdit = null
                },
                onSave = { newTransaction ->
                    if (transactionToEdit != null) {
                        viewModel.updateLoanTransaction(newTransaction)
                    } else {
                        viewModel.addLoanTransaction(newTransaction)
                    }
                    transactionToEdit = null
                    showAddLoanDialog = false
                },
                transactionToEdit = transactionToEdit
            )
        }
    }
}

@Composable
fun LoanTransactionRow(loanTransaction: LoanTransaction, viewModel: LoanViewModel, onEdit: (LoanTransaction) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFDC143C).copy(alpha = 0.7f), Color(0xFFDC143C).copy(alpha = 0.1f))
                )
            )
            .clickable {
                // Handle click, if needed
            }
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "Сума: ${loanTransaction.amount}",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Ім'я: ${loanTransaction.borrowerName}",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Дата видачі: ${loanTransaction.issueDate}",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, color = Color.LightGray),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Дата погашення: ${loanTransaction.dueDate}",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, color = Color.LightGray),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Коментар: ${loanTransaction.comment}",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, color = Color.LightGray),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Row(modifier = Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { onEdit(loanTransaction) }) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
            }
            IconButton(onClick = { viewModel.removeLoanTransaction(loanTransaction) }) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrEditLoanTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (LoanTransaction) -> Unit,
    transactionToEdit: LoanTransaction? = null
) {
    var amount by remember { mutableStateOf(transactionToEdit?.amount?.toString() ?: "") }
    var borrowerName by remember { mutableStateOf(transactionToEdit?.borrowerName ?: "") }
    var issueDate by remember { mutableStateOf(transactionToEdit?.issueDate ?: getCurrentDateForLoan()) }
    var dueDate by remember { mutableStateOf(transactionToEdit?.dueDate ?: getCurrentDateForLoan()) }
    var comment by remember { mutableStateOf(transactionToEdit?.comment ?: "") }
    var showIssueDatePicker by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }

    if (showIssueDatePicker) {
        LoanDatePickerDialog(
            onDateSelected = { selectedDate ->
                issueDate = selectedDate
                showIssueDatePicker = false
            }
        )
    }

    if (showDueDatePicker) {
        LoanDatePickerDialog(
            onDateSelected = { selectedDate ->
                dueDate = selectedDate
                showDueDatePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (transactionToEdit != null) "Редагування транзакції" else "Додавання нової транзакції", style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold)) },
        text = {
            Column {
                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Сума", style = TextStyle(color = Color.White)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold),
                    colors = TextFieldDefaults.textFieldColors(
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                        containerColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Дата видачі в борг",
                    style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedButton(
                    onClick = { showIssueDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(text = issueDate, style = TextStyle(color = Color.White))
                }
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Дата погашення",
                    style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedButton(
                    onClick = { showDueDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(text = dueDate, style = TextStyle(color = Color.White))
                }
                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = borrowerName,
                    onValueChange = { borrowerName = it },
                    label = { Text("Ім'я боржника", style = TextStyle(color = Color.White)) },
                    textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold),
                    colors = TextFieldDefaults.textFieldColors(
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                        containerColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Коментар", style = TextStyle(color = Color.White)) },
                    textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold),
                    colors = TextFieldDefaults.textFieldColors(
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountValue = amount.toDoubleOrNull()
                    if (amountValue != null) {
                        onSave(LoanTransaction(amountValue, borrowerName, issueDate, dueDate, comment))
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC143C)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Зберегти", style = MaterialTheme.typography.bodyLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати", color = Color.White)
            }
        },
        containerColor = Color.DarkGray
    )
}

@Composable
fun LoanDatePickerDialog(onDateSelected: (String) -> Unit) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val datePickerDialog = DatePickerDialog(
        context,
        { _, selectedYear, selectedMonth, selectedDay ->
            val formattedDate = "$selectedDay/${selectedMonth + 1}/$selectedYear"
            onDateSelected(formattedDate)
        },
        year, month, day
    )
    LaunchedEffect(Unit) {
        datePickerDialog.show()
    }
}

fun getCurrentDateForLoan(): String {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return formatter.format(java.util.Date())
}

class LoanViewModel(application: Application) : AndroidViewModel(application) {
    private val _transactions = MutableStateFlow<List<LoanTransaction>>(emptyList())
    val transactions: StateFlow<List<LoanTransaction>> = _transactions

    init {
        loadTransactions(application)
    }

    private fun loadTransactions(context: Context) {
        val sharedPreferences = context.getSharedPreferences("LoanPrefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val transactionsJson = sharedPreferences.getString("LoanTransactions", "[]")
        val type = object : TypeToken<List<LoanTransaction>>() {}.type
        val loadedTransactions: List<LoanTransaction> = gson.fromJson(transactionsJson, type)
        _transactions.update { loadedTransactions }
    }

    private fun saveTransactions(context: Context) {
        val sharedPreferences = context.getSharedPreferences("LoanPrefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val transactionsJson = gson.toJson(_transactions.value)
        sharedPreferences.edit().putString("LoanTransactions", transactionsJson).apply()
    }

    fun addLoanTransaction(transaction: LoanTransaction) {
        _transactions.update { currentList ->
            currentList + transaction
        }
        saveTransactions(getApplication())
    }

    fun updateLoanTransaction(transaction: LoanTransaction) {
        _transactions.update { currentList ->
            currentList.map { if (it.id == transaction.id) transaction else it }
        }
        saveTransactions(getApplication())
    }

    fun removeLoanTransaction(transaction: LoanTransaction) {
        _transactions.update { currentList ->
            currentList - transaction
        }
        saveTransactions(getApplication())
    }
}

class LoanViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoanViewModel::class.java)) {
            return LoanViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class LoanTransaction(
    val amount: Double,
    val borrowerName: String,
    val issueDate: String,
    val dueDate: String,
    val comment: String,
    val id: UUID = UUID.randomUUID()
)