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
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.text.SimpleDateFormat
import java.util.*
import com.example.homeaccountingapp.DateUtils

private const val TAG = "IncomeActivity"

class IncomeViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(IncomeViewModel::class.java)) {
            return IncomeViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class IncomeActivity : ComponentActivity() {
    private lateinit var incomesharedPreferences: SharedPreferences
    private lateinit var IncomeTransactionResultLauncher: ActivityResultLauncher<Intent>
    private val gson by lazy { Gson() }
    private val viewModel: IncomeViewModel by viewModels {
        IncomeViewModelFactory(application)
    }
    private lateinit var updateReceiver: BroadcastReceiver
    private val standardIncomeCategories = listOf("Зарплата", "Бонуси", "Подарунки")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomesharedPreferences = getSharedPreferences("IncomePrefs", Context.MODE_PRIVATE)

        // Ініціалізація категорій при першому запуску
        if (incomesharedPreferences.getString("categories", null) == null) {
            saveCategories(standardIncomeCategories)
        }

        loadIncomesFromSharedPreferences()

        IncomeTransactionResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.loadDataIncome()
                sendUpdateBroadcast()
            }
        }

        setContent {
            HomeAccountingAppTheme {
                IncomeScreen(
                    viewModel = viewModel,
                    onOpenIncomeTransactionScreen = { categoryName, _ ->
                        val intent = Intent(this, IncomeTransactionActivity::class.java).apply {
                            putExtra("categoryName", categoryName)
                        }
                        IncomeTransactionResultLauncher.launch(intent)
                    },
                    onincomeDeleteCategory = { category ->
                        viewModel.incomeDeleteCategory(category)
                        sendUpdateBroadcast()
                    }
                )
            }
        }

        // Встановлення функції після створення ViewModel
        viewModel.setSendUpdateBroadcast { sendUpdateBroadcast() }

        updateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.example.homeaccountingapp.UPDATE_INCOME") {
                    viewModel.loadDataIncome()
                }
            }
        }
        val filter = IntentFilter("com.example.homeaccountingapp.UPDATE_INCOME")
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, filter)
    }

    private fun sendUpdateBroadcast() {
        val updateIntent = Intent("com.example.homeaccountingapp.UPDATE_INCOME")
        LocalBroadcastManager.getInstance(this).sendBroadcast(updateIntent)
    }

    private fun loadIncomesFromSharedPreferences() {
        val incomesJson = incomesharedPreferences.getString("incomes", null)
        val incomeMap: Map<String, Double> = if (incomesJson != null) {
            Gson().fromJson(incomesJson, object : TypeToken<Map<String, Double>>() {}.type)
        } else {
            emptyMap()
        }
        viewModel.updateIncomes(incomeMap)
    }

    private fun saveCategories(categories: List<String>) {
        incomesharedPreferences.edit().putString("categories", gson.toJson(categories)).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
    }

    companion object {
        private const val TAG = "IncomeActivity"
    }
}
class IncomeViewModel(application: Application) : AndroidViewModel(application) {
    private val incomesharedPreferences = application.getSharedPreferences("IncomePrefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    var categories by mutableStateOf<List<String>>(emptyList())
    var IncomeTransactions by mutableStateOf<List<IncomeTransaction>>(emptyList())
    var categoryIncomes by mutableStateOf<Map<String, Double>>(emptyMap())
    var totalIncome by mutableStateOf(0.0)
    private val mainViewModel: MainViewModel = MainViewModel() // Додайте це для доступу до MainViewModel

    // Створення властивості для зберігання функції
    private var sendUpdateBroadcast: (() -> Unit)? = null

    init {
        loadDataIncome()
    }

    // Метод для встановлення функції
    fun setSendUpdateBroadcast(sendUpdateBroadcast: () -> Unit) {
        this.sendUpdateBroadcast = sendUpdateBroadcast
    }

    // Функція для завантаження даних
    fun loadDataIncome() {
        categories = IncomeLoadCategories()
        IncomeTransactions = loadIncomeTransactions()
        updateIncomes()  // Оновлення доходів при завантаженні даних
    }

    // Публічна функція для оновлення доходів
    fun updateIncomes(Incomes: Map<String, Double> = emptyMap()) {
        categoryIncomes = Incomes.takeIf { it.isNotEmpty() }
            ?: categories.associateWith { category ->
                IncomeTransactions.filter { it.category == category }.sumOf { it.amount }
            }
        totalIncome = IncomeTransactions.sumOf { it.amount }
        // Оновлення доходів у MainViewModel
        mainViewModel.saveIncomesToSharedPreferences(getApplication(), categoryIncomes)
    }

    fun updateCategories(newCategories: List<String>) {
        categories = newCategories
        IncomeSaveCategories(categories)
        updateIncomes()  // Оновлення витрат після зміни категорій
        sendUpdateBroadcast?.invoke() // Виклик функції для відправки broadcast
    }

    fun updateIncomeTransactions(newTransactions: List<IncomeTransaction>) {
        IncomeTransactions = newTransactions.map { transaction ->
            val formattedDate = DateUtils.formatDate(transaction.date, "dd/MM/yyyy", "yyyy-MM-dd")
            transaction.copy(date = formattedDate)
        }
        saveIncomeTransactions(IncomeTransactions)
        updateIncomes()

        val updateIntent = Intent("com.example.homeaccountingapp.UPDATE_INCOME")
        LocalBroadcastManager.getInstance(getApplication()).sendBroadcast(updateIntent)
    }

    fun incomeDeleteCategory(category: String) {
        categories = categories.filter { it != category }
        IncomeTransactions = IncomeTransactions.filter { it.category != category }
        IncomeSaveCategories(categories) // Збереження категорій
        saveIncomeTransactions(IncomeTransactions) // Збереження транзакцій
        updateIncomes()  // Оновлення доходів після видалення категорії
    }

    fun IncomeEditCategory(oldCategory: String, newCategory: String) {
        categories = categories.map { if (it == oldCategory) newCategory else it }
        IncomeTransactions = IncomeTransactions.map {
            if (it.category == oldCategory) it.copy(category = newCategory) else it
        }
        IncomeSaveCategories(categories)
        saveIncomeTransactions(IncomeTransactions)
        updateIncomes()  // Оновлення доходів після зміни категорії
    }

    private fun IncomeSaveCategories(categories: List<String>) {
        Log.d(TAG, "Saving categories: $categories")  // Логування перед збереженням
        incomesharedPreferences.edit().putString("categories", gson.toJson(categories)).apply()
    }

    private fun saveIncomeTransactions(IncomeTransactions: List<IncomeTransaction>) {
        Log.d(TAG, "Saving IncomeTransactions: $IncomeTransactions")  // Логування перед збереженням
        incomesharedPreferences.edit().putString("IncomeTransactions", gson.toJson(IncomeTransactions)).apply()
    }

    private fun IncomeLoadCategories(): List<String> {
        val json = incomesharedPreferences.getString("categories", null)
        Log.d(TAG, "Loaded categories: $json")  // Логування при завантаженні
        return if (json != null) {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
        } else {
            emptyList()
        }
    }

    private fun loadIncomeTransactions(): List<IncomeTransaction> {
        val json = incomesharedPreferences.getString("IncomeTransactions", null)
        Log.d(TAG, "Loaded IncomeTransactions: $json")  // Логування при завантаженні
        return if (json != null) {
            gson.fromJson(json, object : TypeToken<List<IncomeTransaction>>() {}.type)
        } else {
            emptyList()
        }
    }

    fun loadTransactionsForCategory(categoryName: String): MutableList<IncomeTransaction> {
        return loadIncomeTransactions().filter { it.category == categoryName }.toMutableList()
    }

    fun saveTransactionsForCategory(updatedTransactions: List<IncomeTransaction>, categoryName: String) {
        val existingTransactions = loadIncomeTransactions().filter { it.category != categoryName } + updatedTransactions
        saveIncomeTransactions(existingTransactions)
    }
}
@Composable
fun IncomeScreen(
    viewModel: IncomeViewModel, // Приймаємо ViewModel
    onOpenIncomeTransactionScreen: (String, String) -> Unit,
    onincomeDeleteCategory: (String) -> Unit
) {
    val categories = viewModel.categories
    val IncomeTransactions = viewModel.IncomeTransactions
    val categoryIncomes = viewModel.categoryIncomes
    val totalIncome = viewModel.totalIncome

    var showIncomeEditCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showIncomeAddIncomeTransactionDialog by remember { mutableStateOf(false) }
    var showIncomeAddCategoryDialog by remember { mutableStateOf(false) }
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
                text = "Категорії доходів",
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
                        .padding(bottom = 100.dp) // Додаємо відступ знизу для тексту "Загальні доходи"
                ) {
                    items(categories) { category ->
                        IncomeCategoryRow(
                            category = category,
                            IncomeAmount = categoryIncomes[category] ?: 0.0,
                            onClick = {
                                onOpenIncomeTransactionScreen(category, Gson().toJson(IncomeTransactions))
                            },
                            onDelete = {
                                categoryToDelete = category
                                showDeleteConfirmationDialog = true
                            },
                            onEdit = {
                                categoryToEdit = category
                                showIncomeEditCategoryDialog = true
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
            containerColor = Color(0xFF228B22)
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
                    IncomeMenuButton(
                        text = "Додати транзакцію",
                        backgroundColors = listOf(
                            Color(0xFF006400).copy(alpha = 0.7f),
                            Color(0xFF228B22).copy(alpha = 0.1f)
                        ),
                        onClick = {
                            showIncomeAddIncomeTransactionDialog = true
                            showMenu = false
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    IncomeMenuButton(
                        text = "Додати категорію",
                        backgroundColors = listOf(
                            Color(0xFF00008B).copy(alpha = 0.7f),
                            Color(0xFF4682B4).copy(alpha = 0.1f)
                        ),
                        onClick = {
                            showIncomeAddCategoryDialog = true
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
                text = "Загальні Доходи: ",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "${totalIncome.incomeFormatAmount(2)} грн", // Додаємо "грн" вкінці
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
                                viewModel.incomeDeleteCategory(it)  // Перевірка, що categoryToDelete не null
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
    if (showIncomeAddIncomeTransactionDialog) {
        IncomeAddIncomeTransactionDialog(
            categories = categories,
            onDismiss = { showIncomeAddIncomeTransactionDialog = false },
            onSave = { IncomeTransaction ->
                viewModel.updateIncomeTransactions(IncomeTransactions + IncomeTransaction) // Передаємо транзакцію у ViewModel
                showIncomeAddIncomeTransactionDialog = false
            }
        )
    }
    if (showIncomeAddCategoryDialog) {
        IncomeAddCategoryDialog(
            onDismiss = { showIncomeAddCategoryDialog = false },
            onSave = { newCategory ->
                viewModel.updateCategories(categories + newCategory)
                showIncomeAddCategoryDialog = false
            }
        )
    }
    if (showIncomeEditCategoryDialog) {
        categoryToEdit?.let { oldCategory ->
            IncomeEditCategoryDialog(
                oldCategoryName = oldCategory,
                onDismiss = { showIncomeEditCategoryDialog = false },
                onSave = { newCategory ->
                    viewModel.IncomeEditCategory(oldCategory, newCategory)
                    showIncomeEditCategoryDialog = false
                }
            )
        }
    }
}

@Composable
fun IncomeCategoryRow(
    category: String,
    IncomeAmount: Double,
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
            text = "${IncomeAmount.incomeFormatAmount(2)} грн", // Форматування суми
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

fun Double.incomeFormatAmount(digits: Int): String {
    return "%.${digits}f".format(this)
}
@Composable
fun IncomeEditCategoryDialog(
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
fun IncomeMenuButton(text: String, backgroundColors: List<Color>, onClick: () -> Unit) {
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
fun IncomeAddCategoryDialog(
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
fun IncomeAddIncomeTransactionDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (IncomeTransaction) -> Unit
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
                text = "Додати дохід",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Green // Зелений заголовок
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
                            val intent = Intent(context, IncomeActivity::class.java)
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
                                IncomeTransaction(
                                    category = selectedCategory,
                                    amount = amountValue, // Додаємо дохід зі знаком "+"
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
data class IncomeTransaction(
    val id: String = UUID.randomUUID().toString(),
    val category: String,
    val amount: Double,
    val date: String,
    val comments: String? = null
)
