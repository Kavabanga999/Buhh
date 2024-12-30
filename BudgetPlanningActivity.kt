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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlin.math.ceil

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
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.background_app),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Spacer(modifier = Modifier.height(40.dp))  // Додано відступ
                        Text(
                            text = "Планування витрат",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 20.dp)  // Змінено відступ зверху
                        )
                        BudgetPlanningScreen(viewModel)
                    }
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
    val isAddingGoal = MutableLiveData<Boolean>(false) // Для відображення/приховування меню цілі
    var currentCategory: String? = null // Поточна категорія для додавання ліміту

    var goalAmount by mutableStateOf("")
    var goalPeriod by mutableStateOf("")
    var weeklySaving by mutableStateOf("")
    var monthlySaving by mutableStateOf("")

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

    fun toggleAddingGoal() {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("GoalPrefs", Context.MODE_PRIVATE)
        if (!isAddingGoal.value!!) {
            goalAmount = sharedPreferences.getString("goal_amount", "") ?: ""
            goalPeriod = sharedPreferences.getString("goal_period", "") ?: ""
            weeklySaving = sharedPreferences.getString("weekly_saving", "") ?: ""
            monthlySaving = sharedPreferences.getString("monthly_saving", "") ?: ""
        }
        isAddingGoal.value = !(isAddingGoal.value ?: false)
    }

    fun calculateGoal() {
        val goalAmountValue = goalAmount.toDoubleOrNull() ?: 0.0
        val goalPeriodValue = goalPeriod.toIntOrNull() ?: 0
        weeklySaving = if (goalPeriodValue > 0) (goalAmountValue / (goalPeriodValue * 4)).formatBudgetAmount(2) else "0.0"
        monthlySaving = if (goalPeriodValue > 0) (goalAmountValue / goalPeriodValue).formatBudgetAmount(2) else "0.0"
    }

    fun saveGoal(context: Context) {
        calculateGoal()
        val sharedPreferences = context.getSharedPreferences("GoalPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putString("goal_amount", goalAmount)
            .putString("goal_period", goalPeriod)
            .putString("weekly_saving", weeklySaving)
            .putString("monthly_saving", monthlySaving)
            .apply()

        saveMessage.value = "Ціль збережено" // Показати повідомлення після збереження
        isAddingGoal.value = false // Приховати меню цілі після збереження
    }
}
@Composable
fun BudgetPlanningScreen(viewModel: BudgetPlanningViewModel) {
    val expenseCategories by viewModel.expenseCategories.observeAsState(emptyMap())
    val maxExpenses by viewModel.maxExpenses.observeAsState(emptyMap())
    val expenses by viewModel.expenses.observeAsState(emptyMap())
    val saveMessage by viewModel.saveMessage.observeAsState(null)
    val isAddingLimit by viewModel.isAddingLimit.observeAsState(false)
    val isAddingGoal by viewModel.isAddingGoal.observeAsState(false)
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
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(expenseCategories.toList()) { (category, _) ->
                val expense = expenses[category] ?: 0.0
                val maxExpense = maxExpenses[category] ?: 0.0
                BudgetCategoryItemWithRedBackground(
                    category = category,
                    expense = expense,
                    maxExpense = maxExpense,
                    onToggleAddingLimit = {
                        viewModel.toggleAddingLimit(category)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
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

                if (isAddingGoal) {
                    AddGoalDialog(
                        goalAmount = viewModel.goalAmount,
                        goalPeriod = viewModel.goalPeriod,
                        weeklySaving = viewModel.weeklySaving,
                        monthlySaving = viewModel.monthlySaving,
                        onGoalAmountChange = { viewModel.goalAmount = it },
                        onGoalPeriodChange = { viewModel.goalPeriod = it },
                        onDismissRequest = { viewModel.isAddingGoal.value = false },
                        onCalculateGoal = { viewModel.calculateGoal() },
                        onSaveGoal = { viewModel.saveGoal(context) }
                    )
                }
            }
        }

        Button(
            onClick = {
                viewModel.toggleAddingGoal()
            },
            modifier = Modifier
                .align(Alignment.End)
                .padding(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006400).copy(alpha = 0.4f)) // Темно-зелена прозора кнопка
        ) {
            Text("Моя ціль", color = Color.White)
        }
    }
}
@Composable
fun BudgetCategoryItemWithRedBackground(
    category: String,
    expense: Double,
    maxExpense: Double,
    onToggleAddingLimit: () -> Unit
) {
    val progress = if (maxExpense > 0) expense / maxExpense else 0.0
    val percentage = (progress * 100).toInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFD32F2F).copy(alpha = 0.7f),
                        Color(0xFFB00020).copy(alpha = 0.1f)  // Темний прозорий внизу
                    )
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp), // Збільшений розмір шрифту
                color = Color.White,
                modifier = Modifier.padding(end = 8.dp)
            )
            Button(
                onClick = onToggleAddingLimit,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Додати ліміт", color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ліміт: ${if (maxExpense > 0) maxExpense.formatBudgetAmount(2) else "не заданий"}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp), // Трохи більший розмір шрифту
                color = Color.Gray
            )
            Text(
                text = "Витрачено $percentage%",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp), // Трохи більший розмір шрифту
                color = Color.White
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
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green.copy(alpha = 0.6f)) // Темно-зелена прозора кнопка
            ) {
                Text("Зберегти", color = textColor)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.6f)) // Темно-червона прозора кнопка
            ) {
                Text("Скасувати", color = textColor)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGoalDialog(
    goalAmount: String,
    goalPeriod: String,
    weeklySaving: String,
    monthlySaving: String,
    onGoalAmountChange: (String) -> Unit,
    onGoalPeriodChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onCalculateGoal: () -> Unit,
    onSaveGoal: () -> Unit
) {
    val formattedGoalAmount = remember(goalAmount) {
        goalAmount.formatWithSpaces()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Color.Black.copy(alpha = 0.8f), // Темний прозорий фон
        title = {
            Text(text = "Моя ціль", color = Color.White)
        },
        text = {
            Column(modifier = Modifier.width(400.dp)) { // Збільшення ширини діалогового вікна
                OutlinedTextField(
                    value = formattedGoalAmount,
                    onValueChange = { value -> onGoalAmountChange(value.filter { it.isDigit() }) },
                    label = { Text("Моя ціль", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color.Gray,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White // Колір курсора
                    ),
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontWeight = FontWeight.Bold), // Жирний шрифт
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = goalPeriod,
                    onValueChange = onGoalPeriodChange,
                    label = { Text("Період (місяців)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color.Gray,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White // Колір курсора
                    ),
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontWeight = FontWeight.Bold), // Жирний шрифт
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onCalculateGoal,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.6f)), // Сіра прозора кнопка
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Розрахувати", color = Color.White, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Відкладати щотижня: $weeklySaving",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Відкладати щомісяця: $monthlySaving",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(
                modifier = Modifier
                    .padding(all = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.6f)), // Темно-червона прозора кнопка
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Відміна", color = Color.White, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSaveGoal,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green.copy(alpha = 0.6f)), // Темно-зелена прозора кнопка
                    modifier = Modifier.weight(1f)
                ) {
                    Text("OK", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    )
}

fun String.formatWithSpaces(): String {
    return this.reversed().chunked(3).joinToString(" ").reversed()
}
fun Double.formatBudgetAmount(digits: Int): String {
    return "%.${digits}f".format(this)
}
