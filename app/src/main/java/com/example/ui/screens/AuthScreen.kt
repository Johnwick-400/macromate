package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.MacroMateViewModel

@Composable
fun AuthScreen(
    viewModel: MacroMateViewModel,
    onAuthSuccess: () -> Unit,
) {
    var isSignUp by remember { mutableStateOf(value = false) }

    // Form fields
    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("") } // "Male" | "Female", sign-up only
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // Password visibility states
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val authError by viewModel.authError.collectAsState()
    var localError by remember { mutableStateOf<String?>(null) }

    val activeError = localError ?: authError

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = SlateDark)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Image/Logo
            Icon(
                imageVector = Icons.Default.RestaurantMenu,
                contentDescription = "MacroMate",
                tint = NeonEmerald,
                modifier = Modifier
                    .size(64.dp)
                    .background(LightTeal, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "MacroMate",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "AI-POWERED NUTRITIONAL CONCIERGE",
                style = MaterialTheme.typography.labelMedium,
                color = OrangeSwiggy,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Tabs / Segmented controls for Sign In vs Sign Up
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(InputBg)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (!isSignUp) NeonEmerald else Color.Transparent)
                        .clickable {
                            isSignUp = false
                            localError = null
                            viewModel.clearAuthError()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign In",
                        color = if (!isSignUp) Color.White else TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSignUp) NeonEmerald else Color.Transparent)
                        .clickable {
                            isSignUp = true
                            localError = null
                            viewModel.clearAuthError()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign Up",
                        color = if (isSignUp) Color.White else TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Main input fields container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isSignUp) "Create Account" else "Welcome Back",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Text(
                        text = if (isSignUp) "Configure your secure credentials to keep track of macros" else "Sign in to resume tracking and ordering synced Swiggy meals",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(top = 4.dp, bottom = 20.dp)
                    )

                    // Error feedback banner
                    if (activeError != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SoftRed.copy(alpha = 0.1f))
                                .border(1.dp, SoftRed.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = SoftRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeError,
                                color = SoftRed,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Field 1: Name (Sign up only)
                    AnimatedVisibility(
                        visible = isSignUp,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    name = it
                                    localError = null
                                },
                                label = { Text("Your Name") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Person,
                                        "Name Icon",
                                        tint = NeonEmerald
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("signup_name_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonEmerald,
                                    unfocusedBorderColor = SlateBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedContainerColor = InputBg.copy(alpha = 0.5f),
                                    unfocusedContainerColor = InputBg.copy(alpha = 0.5f)
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Gender", fontSize = 12.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("Male", "Female").forEach { option ->
                                    val selected = sex == option
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (selected) NeonEmerald else InputBg.copy(alpha = 0.5f))
                                            .border(1.dp, if (selected) NeonEmerald else SlateBorder, RoundedCornerShape(12.dp))
                                            .clickable {
                                                sex = option
                                                localError = null
                                            }
                                            .testTag("signup_sex_$option")
                                    ) {
                                        Text(
                                            option,
                                            color = if (selected) Color.White else TextPrimary,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Field 2: Email Address
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            localError = null
                        },
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Email,
                                "Email Icon",
                                tint = NeonEmerald
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = InputBg.copy(alpha = 0.5f),
                            unfocusedContainerColor = InputBg.copy(alpha = 0.5f)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Field 3: Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            localError = null
                        },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                "Lock Icon",
                                tint = NeonEmerald
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = TextHint
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = InputBg.copy(alpha = 0.5f),
                            unfocusedContainerColor = InputBg.copy(alpha = 0.5f)
                        )
                    )

                    // Field 4: Confirm Password (Sign up only)
                    AnimatedVisibility(
                        visible = isSignUp,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    localError = null
                                },
                                label = { Text("Confirm Password") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Lock,
                                        "Confirm Lock Icon",
                                        tint = NeonEmerald
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                        Icon(
                                            imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle confirm password visibility",
                                            tint = TextHint
                                        )
                                    }
                                },
                                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_confirm_password_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonEmerald,
                                    unfocusedBorderColor = SlateBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedContainerColor = InputBg.copy(alpha = 0.5f),
                                    unfocusedContainerColor = InputBg.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Submit Action Button
                    Button(
                        onClick = {
                            localError = null
                            if (isSignUp) {
                                if (name.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                                    localError = "Please fill in all registration fields."
                                    return@Button
                                }
                                if (password != confirmPassword) {
                                    localError = "Passwords do not match."
                                    return@Button
                                }
                                if (sex.isBlank()) {
                                    localError = "Please select your gender."
                                    return@Button
                                }
                                viewModel.signUp(name, email, password, sex) { success ->
                                    if (success) {
                                        onAuthSuccess()
                                    }
                                }
                            } else {
                                if (email.isBlank() || password.isBlank()) {
                                    localError = "Please fill in email and password."
                                    return@Button
                                }
                                viewModel.signIn(email, password) { success ->
                                    if (success) {
                                        onAuthSuccess()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_submit_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Text(
                            text = if (isSignUp) "Register Account" else "Access Portal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Proceed",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer note
            Text(
                text = "Clinical-grade metabolic scoring integrated directly under active Swiggy API structures. Multi-factor local credential encryption enabled. v1.1.2",
                style = MaterialTheme.typography.bodySmall,
                color = TextHint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
