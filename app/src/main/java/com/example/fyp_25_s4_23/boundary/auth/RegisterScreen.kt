package com.example.fyp_25_s4_23.boundary.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.entity.domain.valueobjects.UserRole

@Composable
fun RegisterScreen(
    isBusy: Boolean,
    message: String?,
    onRegister: (String, String, String, UserRole) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.REGISTERED) }
    var localError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Create Account")

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        Text(text = "Role", modifier = Modifier.padding(top = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = role == UserRole.REGISTERED, onClick = { role = UserRole.REGISTERED })
            Text(text = "Registered user", modifier = Modifier.padding(end = 16.dp))
            RadioButton(selected = role == UserRole.ADMIN, onClick = { role = UserRole.ADMIN })
            Text(text = "Admin")
        }

        val resolvedMessage = message ?: localError
        if (resolvedMessage != null) {
            Text(text = resolvedMessage, modifier = Modifier.padding(top = 8.dp))
        }

        Button(
            onClick = {
                if (password == confirmPassword) {
                    localError = null
                    onRegister(username, password, displayName, role)
                } else {
                    localError = "Passwords do not match"
                }
            },
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Text("Create account")
        }

        TextButton(onClick = onNavigateToLogin, modifier = Modifier.padding(top = 8.dp)) {
            Text("Already registered? Sign in")
        }
    }
}
