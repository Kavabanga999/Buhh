package com.example.homeaccountingapp

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.homeaccountingapp.ui.theme.HomeAccountingAppTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BudgetPlanningActivity : ComponentActivity() {
    private val viewModel: BudgetPlanningViewModel by viewModels()

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if ("com.example.homeaccountingapp.UPDATE_EXPENSES" == intent.action) {
                viewModel.loadExpensesFromMainActivity(context)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeAccountingAppTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.background_app),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    BudgetPlanningScreen(viewModel)
                }
            }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            updateReceiver, IntentFilter("com.example.homeaccountingapp.UPDATE_EXPENSES")
        )

        viewModel.loadExpenseCategories(this)
        viewModel.loadMaxExpenses(this)
        viewModel.loadExpensesFromMainActivity(this)  // Завантаження початкових витрат
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
    }
}

class BudgetPlanningViewModel(application: Application) : AndroidViewModel(application) {
    val expenseCategories = MutableLiveData<Map<String, Double>>(emptyMap())
    val maxExpenses = MutableLiveData<Map<String, Double>>(emptyMap())
    val expenses = MutableLiveData<Map<String, Double>>(emptyMap())
    val saveMessage = MutableLiveData<String?>(null) // Для повідомлення про збереження
    val isAddingLimit = MutableLiveData<Boolean>(false) // Для відображення/приховування поля вводу
    var currentCategory: String? = null // Поточна категорія для додавання ліміту

    fun loadExpenseCategories(context: Context) {
        val sharedPreferences = context.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("categories", "[]")
        val type = object : TypeToken<List<String>>() {}.type
        val categories: List<String> = gson.fromJson(json, type)

        val categoriesMap = categories.associateWith { 0.0 }
        expenseCategories.value = categoriesMap
    }

    fun loadMaxExpenses(context: Context) {
        val sharedPreferences = context.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("max_expenses", "{}")
        val type = object : TypeToken<Map<String, Double>>() {}.type
        val maxExpensesMap: Map<String, Double> = gson.fromJson(json, type)
        maxExpenses.value = maxExpensesMap
    }

    fun loadExpensesFromMainActivity(context: Context) {
        val sharedPreferences = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
        val expensesJson = sharedPreferences.getString("expenses", null)
        val expenseMap: Map<String, Double> = if (expensesJson != null) {
            Gson().fromJson(expensesJson, object : TypeToken<Map<String, Double>>() {}.type)
        } else {
            emptyMap()
        }
        expenses.value = expenseMap
    }

    fun updateMaxExpense(context: Context, category: String, maxExpense: Double) {
        val currentMaxExpenses = maxExpenses.value ?: emptyMap()
        val updatedMaxExpenses = currentMaxExpenses.toMutableMap()
        updatedMaxExpenses[category] = maxExpense
        maxExpenses.value = updatedMaxExpenses

        saveMaxExpenses(context, updatedMaxExpenses)
        saveMessage.value = "Ліміт збережено" // Показати повідомлення після збереження
        isAddingLimit.value = false // Приховати поле вводу після збереження
    }

    private fun saveMaxExpenses(context: Context, maxExpenses: Map<String, Double>) {
        val sharedPreferences = context.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = gson.toJson(maxExpenses)
        sharedPreferences.edit().putString("max_expenses", json).apply()
    }

    fun toggleAddingLimit(category: String) {
        currentCategory = category
        isAddingLimit.value = !(isAddingLimit.value ?: false)
    }
}

@Composable
fun BudgetPlanningScreen(viewModel: BudgetPlanningViewModel) {
    val expenseCategories by viewModel.expenseCategories.observeAsState(emptyMap())
    val maxExpenses by viewModel.maxExpenses.observeAsState(emptyMap())
    val expenses by viewModel.expenses.observeAsState(emptyMap())
    val saveMessage by viewModel.saveMessage.observeAsState(null)
    val isAddingLimit by viewModel.isAddingLimit.observeAsState(false)
    val context = LocalContext.current

    saveMessage?.let {
        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        viewModel.saveMessage.value = null // Скинути повідомлення після показу
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Планування бюджету",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(24.dp))

        expenseCategories.forEach { (category, _) ->
            val expense = expenses[category] ?: 0.0
            val maxExpense = maxExpenses[category] ?: 0.0
            BudgetCategoryItem(
                category = category,
                expense = expense,
                maxExpense = maxExpense,
                onToggleAddingLimit = {
                    viewModel.toggleAddingLimit(category)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isAddingLimit) {
            AddLimitDialog(
                category = viewModel.currentCategory ?: "",
                onDismissRequest = { viewModel.isAddingLimit.value = false },
                onSaveMaxExpense = { maxExpense ->
                    viewModel.currentCategory?.let { category ->
                        viewModel.updateMaxExpense(context, category, maxExpense)
                    }
                }
            )
        }
    }
}

@Composable
fun BudgetCategoryItem(
    category: String,
    expense: Double,
    maxExpense: Double,
    onToggleAddingLimit: () -> Unit
) {
    val progress = if (maxExpense > 0) expense / maxExpense else 0.0
    val percentage = (progress * 100).toInt()
    val backgroundColor = Color.Black.copy(alpha = 0.3f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, shape = RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Button(
                onClick = onToggleAddingLimit,
                modifier = Modifier.padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
            ) {
                Text("Додати ліміт", color = Color.White)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (maxExpense > 0) {
                Text(
                    text = "Ліміт: ${maxExpense.formatBudgetAmount(2)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = "Витрачено $percentage%",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLimitDialog(
    category: String,
    onDismissRequest: () -> Unit,
    onSaveMaxExpense: (Double) -> Unit,
    textColor: Color = Color.White // Параметр для кольору тексту
) {
    var limitValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Color.Black.copy(alpha = 0.8f), // Темний прозорий фон
        title = {
            Text(text = "Додати ліміт для $category", color = textColor)
        },
        text = {
            OutlinedTextField(
                value = limitValue,
                onValueChange = { value -> limitValue = value },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.Gray,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = textColor // Колір курсора
                ),
                textStyle = LocalTextStyle.current.copy(color = textColor, fontWeight = FontWeight.Bold), // Жирний шрифт
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f)) // Темний прозорий фон для поля вводу
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val maxExpenseValue = limitValue.toDoubleOrNull() ?: 0.0
                    onSaveMaxExpense(maxExpenseValue)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green) // Зелена кнопка
            ) {
                Text("Зберегти", color = textColor)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red) // Червона кнопка
            ) {
                Text("Скасувати", color = textColor)
            }
        }
    )
}
fun Double.formatBudgetAmount(digits: Int): String {
    return "%.${digits}f".format(this)
}
