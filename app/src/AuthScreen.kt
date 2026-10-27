package com.harpreet.omniai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AuthScreen(vm: AppViewModel, onClose: () -> Unit) {
    var signUp by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    LaunchedEffect(vm.user) { if (vm.user != null) onClose() }
    LaunchedEffect(Unit) { vm.clearAuthError() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🤖 OmniAI-OS", style = MaterialTheme.typography.headlineMedium)
            ChipRow(listOf("Sign in", "Create account"), if (signUp) "Create account" else "Sign in") {
                signUp = it == "Create account"; vm.clearAuthError()
            }
            OutlinedTextField(username, { username = it }, label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (signUp) {
                OutlinedTextField(name, { name = it }, label = { Text("Display name (optional)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            OutlinedTextField(password, { password = it }, label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation())
            if (signUp) {
                OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirm password") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
            }
            vm.authError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    if (signUp) vm.signUp(username, name, password, confirm) else vm.signIn(username, password)
                },
                enabled = username.isNotBlank() && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (signUp) "Create account" else "Sign in") }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Continue as guest") }
            Text(
                "Accounts are stored only on this device: each account has its own chat history. " +
                    "There is no cloud sync and no password recovery.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
