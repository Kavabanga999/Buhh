package com.example.homeaccountingapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.homeaccountingapp.ui.theme.HomeAccountingAppTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.nio.file.Files.list
import java.text.SimpleDateFormat
import java.util.*
import com.example.homeaccountingapp.DateUtils
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var updateReceiver: BroadcastReceiver

    override fun onResume() {
        super.onResume()
        viewModel.loadExpensesFromSharedPreferences(this)
        viewModel.loadIncomesFromSharedPreferences(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.SplashTheme)
        super.onCreate(savedInstanceState)

        setContent {
            HomeAccountingAppTheme {
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    SplashScreen {
                        showSplash = false
                    }
                } else {
                    MainScreen(
                        onNavigateToIncomes = {
                            val intent = Intent(this@MainActivity, IncomeActivity::class.java)
                            startActivity(intent)
                        },
                        onNavigateToExpenses = {
                            val intent = Intent(this@MainActivity, ExpenseActivity::class.java)
                            startActivity(intent)
                        },
                        onNavigateToIssuedOnLoan = {
                            val intent = Intent(this@MainActivity, IssuedOnLoanActivity::class.java)
                            startActivity(intent)
                        },
                        onNavigateToBorrowed = {
                            val intent = Intent(this@MainActivity, BorrowedActivity::class.java)
                            startActivity(intent)
                        },
                        onNavigateToAllTransactionIncome = {
                            val intent = Intent(this@MainActivity, AllTransactionIncomeActivity::class.java)
                            startActivity(intent)
                        },
                        onNavigateToAllTransactionExpense = {
                            val intent = Intent(this@MainActivity, AllTransactionExpenseActivity::class.java)
                            startActivity(intent)
                        },
                        onNavigateToBudgetPlanning = {
                            val intent = Intent(this@MainActivity, BudgetPlanningActivity::class.java)
                            startActivity(intent)
                        },
                        viewModel = viewModel
                    )
                }
            }
        }

        updateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.example.homeaccountingapp.UPDATE_EXPENSES" ||
                    intent.action == "com.example.homeaccountingapp.UPDATE_INCOME") {
                    viewModel.refreshExpenses(context)
                    viewModel.refreshIncomes(context)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction("com.example.homeaccountingapp.UPDATE_EXPENSES")
            addAction("com.example.homeaccountingapp.UPDATE_INCOME")
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
    }
}
// Функція Splash Screen
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    // Затримка перед переходом на головний екран
    LaunchedEffect(Unit) {
        delay(2000) // 2 секунди
        onTimeout()
    }

    // Центрування та повноекранне зображення
    Box(
        modifier = Modifier
            .fillMaxSize() // Займає весь екран
            .background(Color.Black), // Колір фону (якщо картинка не покриває весь екран)
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_image), // Ваше зображення
            contentDescription = "Splash Image",
            modifier = Modifier.fillMaxSize(), // Заповнити весь екран
            contentScale = ContentScale.Crop // Розтягнути зображення, щоб заповнило екран
        )
    }
}

class MainViewModel : ViewModel() {
    private val _expenses = MutableLiveData<Map<String, Double>>()
    val expenses: LiveData<Map<String, Double>> = _expenses
    private val _incomes = MutableLiveData<Map<String, Double>>()
    val incomes: LiveData<Map<String, Double>> = _incomes

    fun loadExpensesFromSharedPreferences(context: Context) {
        val sharedPreferences = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
        val expensesJson = sharedPreferences.getString("expenses", null)
        val expenseMap: Map<String, Double> = if (expensesJson != null) {
            Gson().fromJson(expensesJson, object : TypeToken<Map<String, Double>>() {}.type)
        } else {
            emptyMap()
        }
        _expenses.value = expenseMap
    }

    fun saveExpensesToSharedPreferences(context: Context, expenses: Map<String, Double>) {
        val sharedPreferences = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val expensesJson = Gson().toJson(expenses)
        editor.putString("expenses", expensesJson)
        editor.apply()
        _expenses.value = expenses // Негайне оновлення LiveData
    }

    fun loadIncomesFromSharedPreferences(context: Context) {
        val sharedPreferences = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
        val incomesJson = sharedPreferences.getString("incomes", null)
        val incomeMap: Map<String, Double> = if (incomesJson != null) {
            Gson().fromJson(incomesJson, object : TypeToken<Map<String, Double>>() {}.type)
        } else {
            emptyMap()
        }
        _incomes.value = incomeMap
    }

    fun saveIncomesToSharedPreferences(context: Context, incomes: Map<String, Double>) {
        val sharedPreferences = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val incomesJson = Gson().toJson(incomes)
        editor.putString("incomes", incomesJson)
        editor.apply()
        _incomes.value = incomes // Негайне оновлення LiveData
    }

    // Відредагуємо функцію saveExpenseTransaction
    fun saveExpenseTransaction(context: Context, transaction: Transaction) {
        val sharedPreferences = context.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
        val gson = Gson()

        val existingTransactionsJson = sharedPreferences.getString("transactions", "[]") ?: "[]"
        val type = object : TypeToken<List<Transaction>>() {}.type
        val existingTransactions: MutableList<Transaction> = gson.fromJson(existingTransactionsJson, type)

        val formattedDate = DateUtils.formatDate(transaction.date, "dd/MM/yyyy", "yyyy-MM-dd")
        existingTransactions.add(transaction.copy(amount = -transaction.amount, date = formattedDate))
        val updatedJson = gson.toJson(existingTransactions)

        sharedPreferences.edit().putString("transactions", updatedJson).apply()

        val updatedExpenses = calculateExpenses(existingTransactions)
        _expenses.value = updatedExpenses
        saveExpensesToSharedPreferences(context, updatedExpenses)

        val updateIntent = Intent("com.example.homeaccountingapp.UPDATE_EXPENSES")
        LocalBroadcastManager.getInstance(context).sendBroadcast(updateIntent)
    }

    fun saveIncomeTransaction(context: Context, transaction: IncomeTransaction) {
        val sharedPreferences = context.getSharedPreferences("IncomePrefs", Context.MODE_PRIVATE)
        val gson = Gson()

        val existingTransactionsJson = sharedPreferences.getString("IncomeTransactions", "[]") ?: "[]"
        val type = object : TypeToken<List<IncomeTransaction>>() {}.type
        val existingTransactions: MutableList<IncomeTransaction> = gson.fromJson(existingTransactionsJson, type)

        val formattedDate = DateUtils.formatDate(transaction.date, "dd/MM/yyyy", "yyyy-MM-dd")
        existingTransactions.add(transaction.copy(date = formattedDate))
        val updatedJson = gson.toJson(existingTransactions)

        sharedPreferences.edit().putString("IncomeTransactions", updatedJson).apply()

        val updatedIncomes = calculateIncomes(existingTransactions)
        _incomes.value = updatedIncomes
        saveIncomesToSharedPreferences(context, updatedIncomes)

        val updateIntent = Intent("com.example.homeaccountingapp.UPDATE_INCOME")
        LocalBroadcastManager.getInstance(context).sendBroadcast(updateIntent)
    }
    // Допоміжний метод для перерахунку витрат за категоріями
    private fun calculateExpenses(transactions: List<Transaction>): Map<String, Double> {
        return transactions.groupBy { it.category }.mapValues { (_, transactions) ->
            transactions.sumOf { it.amount }
        }
    }

    // Допоміжний метод для перерахунку доходів за категоріями
    private fun calculateIncomes(transactions: List<IncomeTransaction>): Map<String, Double> {
        return transactions.groupBy { it.category }.mapValues { (_, transactions) ->
            transactions.sumOf { it.amount }
        }
    }

    fun refreshExpenses(context: Context) {
        val sharedPreferences = context.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val transactionsJson = sharedPreferences.getString("transactions", "[]") ?: "[]"
        val type = object : TypeToken<List<Transaction>>() {}.type
        val transactions: List<Transaction> = gson.fromJson(transactionsJson, type)

        // Перерахунок витрат
        val updatedExpenses = calculateExpenses(transactions)
        _expenses.value = updatedExpenses
    }

    fun refreshIncomes(context: Context) {
        val sharedPreferences = context.getSharedPreferences("IncomePrefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val transactionsJson = sharedPreferences.getString("IncomeTransactions", "[]") ?: "[]"
        val type = object : TypeToken<List<IncomeTransaction>>() {}.type
        val transactions: List<IncomeTransaction> = gson.fromJson(transactionsJson, type)

        // Перерахунок доходів
        val updatedIncomes = calculateIncomes(transactions)
        _incomes.value = updatedIncomes
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToIncomes: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToIssuedOnLoan: () -> Unit,
    onNavigateToBorrowed: () -> Unit,
    onNavigateToAllTransactionIncome: () -> Unit,
    onNavigateToAllTransactionExpense: () -> Unit,
    onNavigateToBudgetPlanning: () -> Unit, // Додано
    viewModel: MainViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showExpenses by remember { mutableStateOf(false) }
    var showIncomes by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAddExpenseTransactionDialog by remember { mutableStateOf(false) }
    var showAddIncomeTransactionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showExpenses = false
        showIncomes = false
    }

    val expenses = viewModel.expenses.observeAsState(initial = emptyMap()).value
    val incomes = viewModel.incomes.observeAsState(initial = emptyMap()).value

    val totalExpenses = expenses.values.sum()
    val totalIncomes = incomes.values.sum()
    val balance = totalIncomes + totalExpenses

    val showWarning = balance < 0
    val showSuccess = balance > 0

    var showMessage by remember { mutableStateOf(false) }

    LaunchedEffect(balance) {
        showMessage = showWarning || showSuccess
        delay(5000)
        showMessage = false
    }

    val context = LocalContext.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                onNavigateToIncomes = { scope.launch { drawerState.close(); onNavigateToIncomes() } },
                onNavigateToExpenses = { scope.launch { drawerState.close(); onNavigateToExpenses() } },
                onNavigateToIssuedOnLoan = { scope.launch { drawerState.close(); onNavigateToIssuedOnLoan() } },
                onNavigateToBorrowed = { scope.launch { drawerState.close(); onNavigateToBorrowed() } },
                onNavigateToAllTransactionIncome = { scope.launch { drawerState.close(); onNavigateToAllTransactionIncome() } },
                onNavigateToAllTransactionExpense = { scope.launch { drawerState.close(); onNavigateToAllTransactionExpense() } },
                onNavigateToBudgetPlanning = { scope.launch { drawerState.close(); onNavigateToBudgetPlanning() } } // Додано
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Домашня бухгалтерія", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Меню",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
                )
            },
            content = { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .backgroundWithImage(
                            painter = painterResource(id = R.drawable.background_app),
                            contentScale = ContentScale.Crop
                        )
                        .padding(innerPadding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ExpandableButtonWithAmount(
                                text = "Загальні доходи",
                                amount = totalIncomes,
                                gradientColors = listOf(
                                    Color(0xFF006400),
                                    Color(0x200032CD32)
                                ),
                                isExpanded = showIncomes,
                                onClick = { showIncomes = !showIncomes }
                            )
                            AnimatedVisibility(visible = showIncomes) {
                                IncomeList(incomes = incomes)
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            ExpandableButtonWithAmount(
                                text = "Загальні витрати",
                                amount = totalExpenses,
                                gradientColors = listOf(
                                    Color(0xFF8B0000),
                                    Color(0x20B22222)
                                ),
                                isExpanded = showExpenses,
                                onClick = { showExpenses = !showExpenses }
                            )
                            AnimatedVisibility(visible = showExpenses) {
                                ExpensesList(expenses = expenses)
                            }

                            // Діаграми доходів та витрат
                            Spacer(modifier = Modifier.height(32.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Діаграма доходів
                                IncomeExpenseChart(
                                    incomes = incomes,
                                    expenses = expenses,
                                    totalIncomes = totalIncomes,
                                    totalExpenses = totalExpenses
                                )
                            }
                        }

                        // Відображення залишку
                        val formattedBalance = "%,.2f".format(balance).replace(",", " ")
                        Spacer(modifier = Modifier.height(8.dp)) // Додаємо відступ
                        Column(
                            modifier = Modifier
                                .fillMaxWidth() // Займає всю ширину
                                .padding(start = 16.dp)
                                .wrapContentWidth(Alignment.Start) // Вирівнювання по лівому краю
                        ) {
                            Text(
                                text = "Залишок:",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 27.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "$formattedBalance грн",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 27.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // Повідомлення
                    AnimatedVisibility(
                        visible = showMessage,
                        enter = slideInVertically(
                            initialOffsetY = { fullHeight -> fullHeight }
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { fullHeight -> fullHeight }
                        ),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp) // Підняти повідомлення трохи вище
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .background(
                                    if (showWarning) Color(0x80B22222) else Color(0x8000B22A),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Text(
                                text = if (showWarning) "Вам потрібно менше витрачати" else "Ви на вірному шляху",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp)
                            .offset(y = (-16).dp) // Підняти кнопки вище
                    ) {
                        FloatingActionButton(
                            onClick = { showAddIncomeTransactionDialog = true },
                            containerColor = Color(0xFF228B22),
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Text("+", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        }

                        FloatingActionButton(
                            onClick = { showAddExpenseTransactionDialog = true },
                            containerColor = Color(0xFFDC143C)
                        ) {
                            Text("+", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    if (showAddExpenseTransactionDialog) {
                        AddTransactionDialog(
                            categories = expenses.keys.toList(),
                            onDismiss = { showAddExpenseTransactionDialog = false },
                            onSave = { transaction ->
                                viewModel.saveExpenseTransaction(context, transaction) // Передаємо поточний Context
                                viewModel.refreshExpenses(context) // Форсоване оновлення витрат
                                showAddExpenseTransactionDialog = false
                            }
                        )
                    }

                    if (showAddIncomeTransactionDialog) {
                        IncomeAddIncomeTransactionDialog(
                            categories = incomes.keys.toList(),
                            onDismiss = { showAddIncomeTransactionDialog = false },
                            onSave = { incomeTransaction ->
                                viewModel.saveIncomeTransaction(context, incomeTransaction) // Передаємо поточний Context
                                viewModel.refreshIncomes(context) // Форсоване оновлення доходів
                                showAddIncomeTransactionDialog = false
                            }
                        )
                    }
                }
            }
        )
    }
}
@Composable
fun IncomeExpenseChart(
    incomes: Map<String, Double>,
    expenses: Map<String, Double>,
    totalIncomes: Double,
    totalExpenses: Double,
    chartSize: Dp = 150.dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Генерація кольорів для доходів і витрат
        val incomeColors = generateColors(incomes.size.takeIf { it > 0 } ?: 1)
        val expenseColors = generateColors(expenses.size.takeIf { it > 0 } ?: 1, isExpense = true)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Діаграма доходів
            DonutChart(
                values = incomes.values.toList(),
                maxAmount = totalIncomes,
                chartSize = chartSize,
                colors = incomeColors,
                strokeWidth = 100f,
                chartLabel = "Доходи", // Назва для діаграми доходів
                emptyChartColor = Color(0x8032CD32).copy(alpha = 0.5f) // Напівпрозорий зелений для доходів
            )
            Spacer(modifier = Modifier.width(24.dp))
            // Діаграма витрат
            DonutChart(
                values = expenses.values.toList(),
                maxAmount = totalExpenses,
                chartSize = chartSize,
                colors = expenseColors,
                strokeWidth = 100f,
                chartLabel = "Витрати", // Назва для діаграми витрат
                emptyChartColor = Color(0x80B22222).copy(alpha = 0.5f) // Напівпрозорий червоний для витрат
            )
        }

        // Легенда для доходів і витрат
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Легенда для доходів
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                incomes.keys.forEachIndexed { index, category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(color = incomeColors[index], shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = category,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Легенда для витрат
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .padding(start = 35.dp, end = 35.dp)
                    .weight(1f)
            ) {
                expenses.keys.forEachIndexed { index, category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(color = expenseColors[index], shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = category,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DonutChart(
    values: List<Double>,
    maxAmount: Double,
    chartSize: Dp,
    colors: List<Color>,
    strokeWidth: Float,
    chartLabel: String, // Назва діаграми
    emptyChartColor: Color // Колір для пустої діаграми
) {
    Canvas(modifier = Modifier.size(chartSize)) {
        val chartRadius = size.minDimension / 2f
        val innerRadius = chartRadius - strokeWidth * 1.5f // Зменшення розміру внутрішнього кола
        var currentAngle = 0f

        if (values.isEmpty() || maxAmount == 0.0) {
            // Якщо немає категорій або значення порожні, малюємо повний фон діаграми
            drawArc(
                color = emptyChartColor,
                startAngle = 0f,
                sweepAngle = 360f, // Повна діаграма
                useCenter = false,
                topLeft = Offset(0f, 0f),
                size = Size(chartRadius * 2, chartRadius * 2),
                style = Stroke(width = strokeWidth)
            )
        } else if (values.size == 1) {
            // Якщо є тільки одна категорія, заповнюємо діаграму повністю цією категорією
            drawArc(
                color = colors[0],
                startAngle = 0f,
                sweepAngle = 360f, // Повна діаграма
                useCenter = false,
                topLeft = Offset(0f, 0f),
                size = Size(chartRadius * 2, chartRadius * 2),
                style = Stroke(width = strokeWidth)
            )
        } else {
            // Малюємо сегменти діаграми для кількох категорій
            values.forEachIndexed { index, value ->
                val sweepAngle = (value / maxAmount * 360).toFloat() // Перетворюємо на Float
                val color = colors[index % colors.size]

                drawArc(
                    color = color,
                    startAngle = currentAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(0f, 0f),
                    size = Size(chartRadius * 2, chartRadius * 2),
                    style = Stroke(width = strokeWidth) // Ширина кільця
                )
                currentAngle += sweepAngle // Перетворення не потрібне, вже Float
            }
        }

        // Додаємо порожнє коло в центр діаграми
        drawCircle(
            color = Color.Transparent,
            radius = innerRadius, // Використовуємо звужений innerRadius
            style = Stroke(width = 1f) // Порожнє коло
        )

        // Додаємо текст у центр діаграми
        drawContext.canvas.nativeCanvas.apply {
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE // Білий текст
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = (chartRadius / 4) // Розмір тексту, залежить від розміру діаграми
                isFakeBoldText = true
            }

            drawText(
                chartLabel,
                size.width / 2, // Центр по горизонталі
                size.height / 2 + (textPaint.textSize / 4), // Центр по вертикалі, з урахуванням висоти тексту
                textPaint
            )
        }
    }
}


fun generateColors(size: Int, isExpense: Boolean = false): List<Color> {
    val expenseColors = listOf(
        Color(0xFFD32F2F).copy(alpha = 0.5f), // Яскраво-червоний з прозорістю 50%
        Color(0xFFFFC107).copy(alpha = 0.5f), // Яскраво-жовтий з прозорістю 50%
        Color(0xFF4CAF50).copy(alpha = 0.5f), // Яскраво-зелений з прозорістю 50%
        Color(0xFF2196F3).copy(alpha = 0.5f), // Яскраво-синій з прозорістю 50%
        Color(0xFFFF5722).copy(alpha = 0.5f), // Яскраво-оранжевий з прозорістю 50%
        Color(0xFF9C27B0).copy(alpha = 0.5f), // Яскраво-фіолетовий з прозорістю 50%
        Color(0xFFE91E63).copy(alpha = 0.5f), // Яскраво-рожевий з прозорістю 50%
        Color(0xFF00BCD4).copy(alpha = 0.5f), // Яскраво-бірюзовий з прозорістю 50%
        Color(0xFF673AB7).copy(alpha = 0.5f)  // Насичено-фіолетовий з прозорістю 50%
    )

    val incomeColors = listOf(
        Color(0xFF1E88E5).copy(alpha = 0.5f), // Синій з прозорістю 50%
        Color(0xFF43A047).copy(alpha = 0.5f), // Зелений з прозорістю 50%
        Color(0xFFF4511E).copy(alpha = 0.5f), // Помаранчевий з прозорістю 50%
        Color(0xFFFB8C00).copy(alpha = 0.5f), // Жовтогарячий з прозорістю 50%
        Color(0xFF8E24AA).copy(alpha = 0.5f), // Фіолетовий з прозорістю 50%
        Color(0xFF26C6DA).copy(alpha = 0.5f)  // Бірюзовий з прозорістю 50%
    )

    // Вибираємо кольори залежно від типу
    val baseColors = if (isExpense) expenseColors else incomeColors

    // Генеруємо кольори з урахуванням кількості
    return List(size) { index -> baseColors[index % baseColors.size] }
}
// Функція форматування чисел із пробілами між тисячами
@Composable
fun ExpandableButtonWithAmount(
    text: String,
    amount: Double,
    gradientColors: List<Color>,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            gradientColors[0],  // Непрозорий колір зліва
            gradientColors[1]   // Прозорий колір справа
        )
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(gradient)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Bold, // Жирний текст
                fontSize = 18.sp // Розмір шрифту для тексту
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${"%.2f".format(amount)} грн", // Форматування суми
                    color = Color.White,
                    fontWeight = FontWeight.Bold, // Жирний текст
                    fontSize = 18.sp // Розмір шрифту для суми
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    }
}
@Composable
fun IncomeList(incomes: Map<String, Double>) {
    LazyColumn {
        items(incomes.toList()) { (category, amount) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 8.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0x99000000), Color(0x66000000)),
                            start = Offset(0f, 0f),
                            end = Offset(1f, 0f) // Горизонтальний градієнт
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                    )
                    Text(
                        text = "${"%.2f".format(amount)} грн", // Форматування суми
                        style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                    )
                }
            }
        }
    }
}

@Composable
fun ExpensesList(expenses: Map<String, Double>) {
    LazyColumn {
        items(expenses.toList()) { (category, amount) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 8.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0x99000000), Color(0x66000000)),
                            start = Offset(0f, 0f),
                            end = Offset(1f, 0f) // Горизонтальний градієнт
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                    )
                    Text(
                        text = "${"%.2f".format(amount)} грн", // Форматування суми
                        style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                    )
                }
            }
        }
    }
}
@Composable
fun CategoryItem(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    gradientColors: List<Color>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = gradientColors,
                    startX = 0f,
                    endX = Float.POSITIVE_INFINITY
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            icon() // Іконка зліва
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White
                )
            )
        }
    }
}
@Composable
fun DrawerContent(
    onNavigateToIncomes: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToIssuedOnLoan: () -> Unit,
    onNavigateToBorrowed: () -> Unit,
    onNavigateToAllTransactionIncome: () -> Unit,
    onNavigateToAllTransactionExpense: () -> Unit,
    onNavigateToBudgetPlanning: () -> Unit // Додано
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val iconSize = when {
        screenWidthDp < 360.dp -> 20.dp  // Small screens
        screenWidthDp < 600.dp -> 24.dp  // Normal screens
        else -> 28.dp  // Large screens
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.8f)
            .background(Color(0xFF1E1E1E).copy(alpha = 0.9f))
            .padding(16.dp)
    ) {
        Text(
            text = "Меню",
            style = MaterialTheme.typography.titleLarge.copy(color = Color.White)
        )
        Spacer(modifier = Modifier.height(40.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            CategoryItem(
                text = "Доходи",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_income),
                        contentDescription = "Іконка доходів",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToIncomes,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryItem(
                text = "Витрати",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_expense),
                        contentDescription = "Іконка витрат",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToExpenses,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryItem(
                text = "Видано в борг",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_loan_issued),
                        contentDescription = "Іконка виданих боргів",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToIssuedOnLoan,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryItem(
                text = "Отримано в борг",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_loan_borrowed),
                        contentDescription = "Іконка отриманих боргів",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToBorrowed,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryItem(
                text = "Всі транзакції доходів",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_all_income_transactions),
                        contentDescription = "Іконка всіх транзакцій доходів",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToAllTransactionIncome,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryItem(
                text = "Всі транзакції витрат",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_all_expense_transactions),
                        contentDescription = "Іконка всіх транзакцій витрат",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToAllTransactionExpense,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Додано новий пункт меню
            CategoryItem(
                text = "Планування бюджету",
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_budget_planning),
                        contentDescription = "Іконка планування бюджету",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                },
                onClick = onNavigateToBudgetPlanning,
                gradientColors = listOf(
                    Color(0xFF000000).copy(alpha = 0.7f),
                    Color(0xFF2E2E2E).copy(alpha = 0.7f)
                )
            )
        }
    }
}
private fun Modifier.backgroundWithImage(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentScale: ContentScale
): Modifier {
    return this.then(
        Modifier.paint(
            painter = painter,
            contentScale = contentScale
        )
    )
}
