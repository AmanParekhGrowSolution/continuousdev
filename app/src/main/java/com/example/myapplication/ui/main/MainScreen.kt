package com.example.myapplication.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

@Composable
fun MainScreen(today: LocalDate, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = today.format(dateFormatter),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    MainScreen(today = LocalDate.now(), modifier = modifier)
}

@Preview(showBackground = true, name = "Light")
@Composable
private fun MainScreenLightPreview() {
    MyApplicationTheme(darkTheme = false) {
        MainScreen(today = LocalDate.of(2026, 4, 28))
    }
}

@Preview(showBackground = true, name = "Dark")
@Composable
private fun MainScreenDarkPreview() {
    MyApplicationTheme(darkTheme = true) {
        MainScreen(today = LocalDate.of(2026, 4, 28))
    }
}
