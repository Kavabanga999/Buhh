package com.example.homeaccountingapp

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
                viewModel.loadExpenses(context)
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
        viewModel.loadExpenses(this)  // Завантаження початкових витрат
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

    fun loadExpenses(context: Context) {
        val sharedPreferences = context.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("expenses", "{}")
        val type = object : TypeToken<Map<String, Double>>() {}.type
        val expensesMap: Map<String, Double> = gson.fromJson(json, type)
        expenses.value = expensesMap
    }

    fun updateMaxExpense(context: Context, category: String, maxExpense: Double) {
        val currentMaxExpenses = maxExpenses.value ?: emptyMap()
        val updatedMaxExpenses = currentMaxExpenses.toMutableMap()
        updatedMaxExpenses[category] = maxExpense
        maxExpenses.value = updatedMaxExpenses

        saveMaxExpenses(context, updatedMaxExpenses)
    }

    private fun saveMaxExpenses(context: Context, maxExpenses: Map<String, Double>) {
        val sharedPreferences = context.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = gson.toJson(maxExpenses)
        sharedPreferences.edit().putString("max_expenses", json).apply()
    }
}

@Composable
fun BudgetPlanningScreen(viewModel: BudgetPlanningViewModel) {
    val expenseCategories by viewModel.expenseCategories.observeAsState(emptyMap())
    val maxExpenses by viewModel.maxExpenses.observeAsState(emptyMap())
    val expenses by viewModel.expenses.observeAsState(emptyMap())
    val context = LocalContext.current

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
            BudgetCategoryItem(
                context = context,
                category = category,
                expense = expense,
                maxExpense = maxExpenses[category] ?: 0.0,
                onMaxExpenseChange = { maxExpense ->
                    viewModel.updateMaxExpense(context, category, maxExpense)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun BudgetCategoryItem(
    context: Context,
    category: String,
    expense: Double,
    maxExpense: Double,
    onMaxExpenseChange: (Double) -> Unit
) {
    val progress = if (maxExpense > 0) expense / maxExpense else 0.0
    val percentage = (progress * 100).toInt()
    val color = Brush.horizontalGradient(
        colors = listOf(
            Color.Green,
            Color.Yellow,
            Color.Red
        ),
        startX = 0.0f,
        endX = 1.0f
    )

    Column {
        Text(
            text = category,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color.Gray)
        ) {
            LinearProgressIndicator(
                progress = progress.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = maxExpense.toString(),
            onValueChange = { value ->
                val maxExpenseValue = value.toDoubleOrNull() ?: 0.0
                onMaxExpenseChange(maxExpenseValue)
            },
            label = { Text("Максимальна сума витрат", color = Color.White) },
            textStyle = LocalTextStyle.current.copy(color = Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
