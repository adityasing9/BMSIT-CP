package com.continueo.autologin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.continueo.autologin.models.Credentials
import com.continueo.autologin.security.CredentialManager
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onNavigateToHome: () -> Unit,
    existingCredentials: Credentials? = null
) {
    val context = LocalContext.current
    val credentialManager = remember { CredentialManager(context) }

    var usn by remember { mutableStateOf(existingCredentials?.usn ?: "") }
    var idNumber by remember { mutableStateOf(existingCredentials?.idCardNumber ?: "") }

    var day by remember { mutableStateOf(existingCredentials?.dobDay ?: "") }
    var month by remember { mutableStateOf(existingCredentials?.dobMonth ?: "") }
    var year by remember { mutableStateOf(existingCredentials?.dobYear ?: "") }

    var expandedDay by remember { mutableStateOf(false) }
    var expandedMonth by remember { mutableStateOf(false) }
    var expandedYear by remember { mutableStateOf(false) }

    val days = (1..31).map { it.toString().padStart(2, '0') }
    val months = listOf(
        "Jan" to "01", "Feb" to "02", "Mar" to "03", "Apr" to "04",
        "May" to "05", "Jun" to "06", "Jul" to "07", "Aug" to "08",
        "Sep" to "09", "Oct" to "10", "Nov" to "11", "Dec" to "12"
    )
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val years = (currentYear downTo 1955).map { it.toString() }

    var usnError by remember { mutableStateOf(false) }
    var dayError by remember { mutableStateOf(false) }
    var monthError by remember { mutableStateOf(false) }
    var yearError by remember { mutableStateOf(false) }
    var idError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "ContinueO",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Auto Login",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // USN field
        OutlinedTextField(
            value = usn,
            onValueChange = {
                usn = it
                usnError = false
            },
            label = { Text("USN") },
            placeholder = { Text("Enter your USN") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = usnError,
            supportingText = if (usnError) {
                { Text("USN is required") }
            } else null
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Date of Birth",
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Day Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedDay,
                onExpandedChange = { expandedDay = !expandedDay },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = day,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Day") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDay) },
                    modifier = Modifier.menuAnchor(),
                    isError = dayError
                )
                ExposedDropdownMenu(
                    expanded = expandedDay,
                    onDismissRequest = { expandedDay = false }
                ) {
                    days.forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d) },
                            onClick = {
                                day = d
                                dayError = false
                                expandedDay = false
                            }
                        )
                    }
                }
            }

            // Month Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedMonth,
                onExpandedChange = { expandedMonth = !expandedMonth },
                modifier = Modifier.weight(1.2f)
            ) {
                OutlinedTextField(
                    value = months.find { it.second == month }?.first ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Month") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMonth) },
                    modifier = Modifier.menuAnchor(),
                    isError = monthError
                )
                ExposedDropdownMenu(
                    expanded = expandedMonth,
                    onDismissRequest = { expandedMonth = false }
                ) {
                    months.forEach { (mName, mValue) ->
                        DropdownMenuItem(
                            text = { Text(mName) },
                            onClick = {
                                month = mValue
                                monthError = false
                                expandedMonth = false
                            }
                        )
                    }
                }
            }

            // Year Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedYear,
                onExpandedChange = { expandedYear = !expandedYear },
                modifier = Modifier.weight(1.1f)
            ) {
                OutlinedTextField(
                    value = year,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Year") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedYear) },
                    modifier = Modifier.menuAnchor(),
                    isError = yearError
                )
                ExposedDropdownMenu(
                    expanded = expandedYear,
                    onDismissRequest = { expandedYear = false }
                ) {
                    years.forEach { y ->
                        DropdownMenuItem(
                            text = { Text(y) },
                            onClick = {
                                year = y
                                yearError = false
                                expandedYear = false
                            }
                        )
                    }
                }
            }
        }

        if (dayError || monthError || yearError) {
            Text(
                text = "All date fields are required",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(top = 4.dp, start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ID Card Number field
        OutlinedTextField(
            value = idNumber,
            onValueChange = {
                idNumber = it
                idError = false
            },
            label = { Text("ID Card Number") },
            placeholder = { Text("Enter your ID Card Number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = idError,
            supportingText = if (idError) {
                { Text("ID Card Number is required") }
            } else null
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                var hasError = false
                if (usn.isBlank()) { usnError = true; hasError = true }
                if (day.isBlank()) { dayError = true; hasError = true }
                if (month.isBlank()) { monthError = true; hasError = true }
                if (year.isBlank()) { yearError = true; hasError = true }
                if (idNumber.isBlank()) { idError = true; hasError = true }

                if (!hasError) {
                    val credentials = Credentials(
                        usn = usn.trim(),
                        dobDay = day,
                        dobMonth = month,
                        dobYear = year,
                        idCardNumber = idNumber.trim()
                    )
                    credentialManager.saveCredentials(credentials)
                    onNavigateToHome()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Save Credentials", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your credentials are stored only on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
