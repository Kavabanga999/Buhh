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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.homeaccountingapp.ui.theme.HomeAccountingAppTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AllTransactionIncomeActivity : ComponentActivity() {
    private val viewModel: IncomeViewModel by viewModels { IncomeViewModelFactory(application) }
    private lateinit var updateReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeAccountingAppTheme {
                AllTransactionIncomeScreen(viewModel = viewModel)
            }
        }

        // Ініціалізація BroadcastReceiver для оновлення даних
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

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
    }
}

@Composable
fun AllTransactionIncomeScreen(viewModel: IncomeViewModel) {
    val incomeTransactions = viewModel.IncomeTransactions
    val todayDate = getCurrentDateALLIncome()
    val todayTransactions = incomeTransactions.filter { it.date == todayDate }
    val pastTransactions = incomeTransactions.filter { it.date != todayDate }
    val totalTodayIncome = todayTransactions.sumOf { it.amount }

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
                text = "Всі транзакції доходів",
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
                        AllIncomeTransactionItem(
                            transaction = transaction,
                            onClick = {
                                // Додатковий функціонал при натисканні на транзакцію
                            }
                        )
                    }
                    item {
                        Text(
                            text = "Всього сьогоднішніх доходів: $totalTodayIncome грн",
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, color = Color.White),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                if (pastTransactions.isNotEmpty()) {
                    items(pastTransactions) { transaction ->
                        AllIncomeTransactionItem(
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
fun AllIncomeTransactionItem(
    transaction: IncomeTransaction,
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
                        Color(0xFF006400).copy(alpha = 0.7f),
                        Color(0xFF228B22).copy(alpha = 0.1f)  // Dark transparent at the bottom
                    )
                )
            )
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF006400).copy(alpha = 0.7f),
                        Color(0xFF228B22).copy(alpha = 0.1f)  // Almost fully transparent on the right
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(16.dp) // Inner padding
    ) {
        Column {
            Text(
                text = "Сума: ${transaction.amount} грн",
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
fun getCurrentDateALLIncome(): String {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return formatter.format(java.util.Date())
}