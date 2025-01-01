package com.example.homeaccountingapp

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.homeaccountingapp.ui.theme.HomeAccountingAppTheme
import java.text.SimpleDateFormat
import java.util.*

class AllTransactionExpenseActivity : ComponentActivity() {
    private val viewModel: ExpenseViewModel by viewModels { ExpenseViewModelFactory(application) }
    private lateinit var updateReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeAccountingAppTheme {
                AllTransactionExpenseScreen(viewModel = viewModel)
            }
        }

        // Ініціалізація BroadcastReceiver для оновлення даних
        updateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.example.homeaccountingapp.UPDATE_EXPENSES") {
                    viewModel.loadData()
                }
            }
        }
        val filter = IntentFilter("com.example.homeaccountingapp.UPDATE_EXPENSES")
        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
    }
}

@Composable
fun AllTransactionExpenseScreen(viewModel: ExpenseViewModel) {
    val expenseTransactions by viewModel.transactions.observeAsState(emptyList())
    val todayDate = getCurrentDateAllExpense()
    val todayTransactions = expenseTransactions.filter { it.date == todayDate }
    val pastTransactions = expenseTransactions.filter { it.date != todayDate }
    val totalTodayExpense = todayTransactions.sumOf { it.amount }

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
                text = "Всі транзакції витрат",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(24.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 100.dp)
            ) {
                if (todayTransactions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Сьогоднішні транзакції",
                            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(todayTransactions) { transaction ->
                        AllExpenseTransactionItem(
                            transaction = transaction,
                            onClick = {
                                // Додатковий функціонал при натисканні на транзакцію
                            }
                        )
                    }
                    item {
                        Text(
                            text = "Всього сьогоднішніх витрат: ${totalTodayExpense.formatAmount(2)} грн",
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, color = Color.White),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                if (pastTransactions.isNotEmpty()) {
                    items(pastTransactions) { transaction ->
                        AllExpenseTransactionItem(
                            transaction = transaction,
                            onClick = {
                                // Додатковий функціонал при натисканні на транзакцію
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AllExpenseTransactionItem(
    transaction: Transaction,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFD32F2F).copy(alpha = 0.7f),
                        Color(0xFFB00020).copy(alpha = 0.1f)  // Dark transparent at the bottom
                    )
                )
            )
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFD32F2F).copy(alpha = 0.7f),
                        Color(0xFFB00020).copy(alpha = 0.1f)  // Almost fully transparent on the right
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(16.dp) // Inner padding
    ) {
        Column {
            Text(
                text = "Сума: ${if (transaction.amount < 0) "" else "-"}${transaction.amount.formatAmount(2)} грн", // Перевірка наявності мінуса
                style = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Дата: ${transaction.date}",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (!transaction.comments.isNullOrEmpty()) {
                Text(
                    text = "Коментар: ${transaction.comments}",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                )
            }
        }
    }
}

// Функція для отримання поточної дати у форматі "yyyy-MM-dd"
fun getCurrentDateAllExpense(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return formatter.format(Date())
}
