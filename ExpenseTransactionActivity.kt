@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.homeaccountingapp
import android.app.Application
import android.app.DatePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import com.example.homeaccountingapp.ui.theme.HomeAccountingAppTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.text.SimpleDateFormat
import java.util.*
import com.example.homeaccountingapp.DateUtils
private const val TAG = "ExpenseActivity"

class ExpenseViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            return ExpenseViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ExpenseActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var transactionResultLauncher: ActivityResultLauncher<Intent>
    private val gson by lazy { Gson() }
    private val viewModel: ExpenseViewModel by viewModels {
        ExpenseViewModelFactory(application)
    }
    private lateinit var updateReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() called")
        sharedPreferences = getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
        loadExpensesFromSharedPreferences()

        transactionResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.loadData()  // Оновлення даних після редагування транзакцій
                sendUpdateBroadcast()
                Log.d(TAG, "Data updated after transaction edit")
            }
        }

        setContent {
            HomeAccountingAppTheme {
                ExpenseScreen(
                    viewModel = viewModel,
                    onOpenTransactionScreen = { categoryName, transactionsJson ->
                        val intent = Intent(this, ExpenseTransactionActivity::class.java).apply {
                            putExtra("categoryName", categoryName)
                            putExtra("transactionsJson", transactionsJson)
                        }
                        transactionResultLauncher.launch(intent)
                    },
                    onDeleteCategory = { category ->
                        viewModel.deleteCategory(category)
                        sendUpdateBroadcast()
                    }
                )
            }
        }

        // Встановлення функції після створення ViewModel
        viewModel.setSendUpdateBroadcast { sendUpdateBroadcast() }

        // Ініціалізація BroadcastReceiver для оновлення даних
        updateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "com.example.homeaccountingapp.UPDATE_EXPENSES" -> viewModel.loadData()
                }
            }
        }
        val filter = IntentFilter("com.example.homeaccountingapp.UPDATE_EXPENSES")
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, filter)
    }

    private fun sendUpdateBroadcast() {
        val updateIntent = Intent("com.example.homeaccountingapp.UPDATE_EXPENSES")
        LocalBroadcastManager.getInstance(this).sendBroadcast(updateIntent)
    }

    private fun loadExpensesFromSharedPreferences() {
        val sharedPreferences = getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
        val expensesJson = sharedPreferences.getString("expenses", null)
        Log.d("ExpenseActivity", "Loaded expenses JSON: $expensesJson")
        val expenseMap: Map<String, Double> = if (expensesJson != null) {
            Gson().fromJson(expensesJson, object : TypeToken<Map<String, Double>>() {}.type)
        } else {
            emptyMap()
        }
        viewModel.updateExpenses(expenseMap)  // Оновлення витрат в ViewModel
        Log.d("ExpenseActivity", "Updated expenses: $expenseMap")
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
    }
}
class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences = application.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    var categories by mutableStateOf<List<String>>(emptyList())
    private val _transactions = MutableLiveData<List<Transaction>>()
    val transactions: LiveData<List<Transaction>> get() = _transactions
    var categoryExpenses by mutableStateOf<Map<String, Double>>(emptyMap())
    var totalExpense by mutableStateOf(0.0)
    private val mainViewModel: MainViewModel = MainViewModel() // Додайте це для доступу до MainViewModel

    // Створення властивості для зберігання функції
    private var sendUpdateBroadcast: (() -> Unit)? = null

    init {
        loadData()
    }

    // Метод для встановлення функції
    fun setSendUpdateBroadcast(sendUpdateBroadcast: () -> Unit) {
        this.sendUpdateBroadcast = sendUpdateBroadcast
    }

    // Функція для завантаження даних
    fun loadData() {
        categories = loadCategories()
        _transactions.value = loadTransactions()
        updateExpenses()  // Оновлення витрат при завантаженні даних
    }

    // Публічна функція для оновлення витрат
    fun updateExpenses(expenses: Map<String, Double> = emptyMap()) {
        val currentTransactions = _transactions.value ?: emptyList()
        categoryExpenses = expenses.takeIf { it.isNotEmpty() }
            ?: categories.associateWith { category ->
                currentTransactions.filter { it.category == category }.sumOf { it.amount }
            }
        totalExpense = currentTransactions.sumOf { it.amount }
        // Оновлення витрат у MainViewModel
        mainViewModel.saveExpensesToSharedPreferences(getApplication(), categoryExpenses)
    }

    fun updateCategories(newCategories: List<String>) {
        categories = newCategories
        saveCategories(categories)
        updateExpenses()  // Оновлення витрат після зміни категорій
        sendUpdateBroadcast?.invoke() // Виклик функції для відправки broadcast
    }

    fun updateTransactions(newTransactions: List<Transaction>) {
        _transactions.value = newTransactions.map { transaction ->
            val formattedDate = DateUtils.formatDate(transaction.date, "dd/MM/yyyy", "yyyy-MM-dd")
            if (transaction.amount > 0) transaction.copy(amount = -transaction.amount, date = formattedDate)
            else transaction.copy(date = formattedDate)
        }
        saveTransactions(_transactions.value ?: emptyList())
        updateExpenses()

        val updateIntent = Intent("com.example.homeaccountingapp.UPDATE_EXPENSES")
        LocalBroadcastManager.getInstance(getApplication()).sendBroadcast(updateIntent)
    }

    fun deleteCategory(category: String) {
        categories = categories.filter { it != category }
        _transactions.value = _transactions.value?.filter { it.category != category }
        saveCategories(categories) // Збереження категорій
        saveTransactions(_transactions.value ?: emptyList()) // Збереження транзакцій
        updateExpenses()  // Оновлення витрат після видалення категорії
    }

    fun editCategory(oldCategory: String, newCategory: String) {
        categories = categories.map { if (it == oldCategory) newCategory else it }
        _transactions.value = _transactions.value?.map {
            if (it.category == oldCategory) it.copy(category = newCategory) else it
        }
        saveCategories(categories)
        saveTransactions(_transactions.value ?: emptyList())
        updateExpenses()  // Оновлення витрат після зміни категорії
    }

    private fun saveCategories(categories: List<String>) {
        Log.d(TAG, "Saving categories: $categories")  // Логування перед збереженням
        sharedPreferences.edit().putString("categories", gson.toJson(categories)).apply()
    }

    private fun saveTransactions(transactions: List<Transaction>) {
        val negativeTransactions = transactions.map { transaction ->
            if (transaction.amount > 0) transaction.copy(amount = -transaction.amount)
            else transaction
        }
        Log.d(TAG, "Saving transactions: $negativeTransactions")
        sharedPreferences.edit().putString("transactions", gson.toJson(negativeTransactions)).apply()
    }

    private fun loadCategories(): List<String> {
        val json = sharedPreferences.getString("categories", null)
        Log.d(TAG, "Loaded categories: $json")  // Логування при завантаженні
        return if (json != null) {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
        } else {
            emptyList()
        }
    }

    private fun loadTransactions(): List<Transaction> {
        val json = sharedPreferences.getString("transactions", null)
        Log.d(TAG, "Loaded transactions: $json")
        return if (json != null) {
            gson.fromJson<List<Transaction>>(json, object : TypeToken<List<Transaction>>() {}.type)
                .map { transaction ->
                    if (transaction.amount > 0) transaction.copy(amount = -transaction.amount)
                    else transaction
                }
        } else {
            emptyList()
        }
    }

    companion object {
        private const val TAG = "ExpenseViewModel"
    }
}
@Composable
fun ExpenseScreen(
    viewModel: ExpenseViewModel,
    onOpenTransactionScreen: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit
) {
    val categories = viewModel.categories
    val transactions = viewModel.transactions.observeAsState(initial = emptyList()).value
    val categoryExpenses = viewModel.categoryExpenses
    val totalExpense = viewModel.totalExpense

    var showEditCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background_app),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 50.dp)
        ) {
            Text(
                text = "Категорії витрат",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier.weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 100.dp) // Додаємо відступ знизу для тексту "Загальні витрати"
                ) {
                    items(categories) { category ->
                        CategoryRow(
                            category = category,
                            expenseAmount = categoryExpenses[category] ?: 0.0,
                            onClick = {
                                onOpenTransactionScreen(category, Gson().toJson(transactions))
                            },
                            onDelete = {
                                categoryToDelete = category
                                showDeleteConfirmationDialog = true
                            },
                            onEdit = {
                                categoryToEdit = category
                                showEditCategoryDialog = true
                            }
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showMenu = !showMenu },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFFDC143C)
        ) {
            Text("+", color = Color.White, style = MaterialTheme.typography.bodyLarge)
        }
        if (showMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showMenu = false }
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .widthIn(max = 250.dp)
                ) {
                    MenuButton(
                        text = "Додати транзакцію",
                        backgroundColors = listOf(
                            Color(0xFF8B0000).copy(alpha = 0.7f),
                            Color(0xFFDC143C).copy(alpha = 0.1f)
                        ),
                        onClick = {
                            showAddTransactionDialog = true
                            showMenu = false
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MenuButton(
                        text = "Додати категорію",
                        backgroundColors = listOf(
                            Color(0xFF00008B).copy(alpha = 0.7f),
                            Color(0xFF4682B4).copy(alpha = 0.1f)
                        ),
                        onClick = {
                            showAddCategoryDialog = true
                            showMenu = false
                        }
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(30.dp)
        ) {
            Text(
                text = "Загальні витрати: ",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "${totalExpense.formatAmount(2)} грн", // Форматування суми
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
    if (showDeleteConfirmationDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))  // Темний прозорий фон
                .clickable { showDeleteConfirmationDialog = false }  // Закриваємо діалог при кліку поза ним
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xFF1A1A1A).copy(alpha = 0.9f), shape = RoundedCornerShape(12.dp))  // Темний прозорий фон діалогу
                    .padding(16.dp)
                    .widthIn(max = 300.dp)  // Зменшення ширини меню
            ) {
                Text(
                    text = "Ви впевнені, що хочете видалити цю категорію?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,  // Білий текст
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            showDeleteConfirmationDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4682B4))  // Синій колір кнопки
                    ) {
                        Text("Ні", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            categoryToDelete?.let {
                                viewModel.deleteCategory(it)  // Перевірка, що categoryToDelete не null
                            }
                            showDeleteConfirmationDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000))  // Червоний колір кнопки
                    ) {
                        Text("Так", color = Color.White)
                    }
                }
            }
        }
    }
    if (showAddTransactionDialog) {
        AddTransactionDialog(
            categories = categories,
            onDismiss = { showAddTransactionDialog = false },
            onSave = { transaction ->
                val updatedTransactions = transactions.toMutableList() // Створюємо змінний список з поточних транзакцій
                updatedTransactions.add(transaction) // Додаємо нову транзакцію
                viewModel.updateTransactions(updatedTransactions) // Оновлюємо транзакції у ViewModel
                showAddTransactionDialog = false
            }
        )
    }
    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onSave = { newCategory ->
                viewModel.updateCategories(categories + newCategory)
                showAddCategoryDialog = false
            }
        )
    }
    if (showEditCategoryDialog) {
        categoryToEdit?.let { oldCategory ->
            EditCategoryDialog(
                oldCategoryName = oldCategory,
                onDismiss = { showEditCategoryDialog = false },
                onSave = { newCategory ->
                    viewModel.editCategory(oldCategory, newCategory)
                    showEditCategoryDialog = false
                }
            )
        }
    }
}
@Composable
fun CategoryRow(
    category: String,
    expenseAmount: Double,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF2E2E2E).copy(alpha = 1f), // Темний колір зліва без прозорості
                        Color(0xFF2E2E2E).copy(alpha = 0f)  // Повністю прозорий колір справа
                    )
                ),
                shape = MaterialTheme.shapes.medium
            )
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), // Використовуємо bodyLarge для заголовка
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${expenseAmount.formatAmount(2)} грн", // Форматування суми
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.padding(end = 8.dp)
        )
        Row {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Category",
                    tint = Color.White
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Category",
                    tint = Color.White
                )
            }
        }
    }
}

fun Double.formatAmount(digits: Int): String {
    return "%.${digits}f".format(this)
}


@Composable
fun EditCategoryDialog(
    oldCategoryName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var newCategoryName by remember { mutableStateOf(oldCategoryName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Редагувати категорію",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Нова назва категорії", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newCategoryName.isNotBlank()) {
                        onSave(newCategoryName.trim())
                        onDismiss()
                    }
                }
            ) {
                Text("Зберегти", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрити", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF2B2B2B)
    )
}

@Composable
fun MenuButton(text: String, backgroundColors: List<Color>, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .height(60.dp)
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(colors = backgroundColors)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
    }
}
@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Додати категорію",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Назва категорії", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        onSave(categoryName.trim())
                        onDismiss()
                    }
                }
            ) {
                Text("Зберегти", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрити", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF2B2B2B)
    )
}
@Composable
fun AddTransactionDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    val today = remember {
        val calendar = Calendar.getInstance()
        "${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.YEAR)}"
    }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    var comment by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current

    if (showDatePicker) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                date = "$dayOfMonth/${month + 1}/$year"
                showDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)) // Більш прозорий чорний фон
            .clickable(enabled = true, onClick = {})
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.7f), // Чорний фон з більшою прозорістю
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Додати витрату",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Red // Червоний заголовок
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { newValue ->
                    if (newValue.all { it.isDigit() || it == '.' }) {
                        amount = newValue
                    }
                },
                label = { Text("Сума", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray,
                    containerColor = Color.Black.copy(alpha = 0.9f), // Менш прозорий чорний фон для поля вводу
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold), // Білий і жирний текст
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Decimal
                )
            )
            Button(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF616161))
            ) {
                Text(
                    text = "Дата: $date",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            ExposedDropdownMenuBox(
                expanded = isDropdownExpanded,
                onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    label = { Text("Категорія") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded)
                    },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        containerColor = Color.Black.copy(alpha = 0.9f) // Менш прозорий чорний фон для поля вводу
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
                ExposedDropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false },
                    modifier = Modifier.background(Color(0xFF2B2B2B))
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = category,
                                    color = Color.White
                                )
                            },
                            onClick = {
                                selectedCategory = category
                                isDropdownExpanded = false
                            },
                            modifier = Modifier.background(Color(0xFF2B2B2B))
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Додати категорію",
                                color = Color.Yellow,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        onClick = {
                            val intent = Intent(context, ExpenseActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.background(Color(0xFF444444))
                    )
                }
            }
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Коментар (необов'язково)", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray,
                    containerColor = Color.Black.copy(alpha = 0.9f), // Менш прозорий чорний фон для поля вводу
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.6f)
                    )
                ) {
                    Text("Закрити", color = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        val amountValue = amount.toDoubleOrNull()
                        if (amountValue != null && selectedCategory.isNotBlank() && date.isNotBlank()) {
                            onSave(
                                Transaction(
                                    category = selectedCategory,
                                    amount = amountValue,
                                    date = date,
                                    comments = comment.takeIf { it.isNotBlank() }
                                )
                            )
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Green.copy(alpha = 0.6f)
                    )
                ) {
                    Text("Зберегти", color = Color.White)
                }
            }
        }
    }
}
data class Transaction(
    val id: String = UUID.randomUUID().toString(), // Додаємо унікальний ідентифікатор
    val category: String,
    val amount: Double,
    val date: String,
    val comments: String? = null
)
