package com.continueo.autologin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.continueo.autologin.utils.FeedbackCategory
import com.continueo.autologin.utils.FeedbackManager
import com.continueo.autologin.utils.UpdateManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackDialog(
    onDismiss: () -> Unit,
    initialCategory: FeedbackCategory = FeedbackCategory.BUG,
    initialSubject: String = ""
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var subject by remember { mutableStateOf(initialSubject) }
    var message by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var includeDeviceInfo by remember { mutableStateOf(true) }

    var isSubmitting by remember { mutableStateOf(false) }
    var messageError by remember { mutableStateOf<String?>(null) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        icon = {
            Icon(
                imageVector = if (successMessage != null) Icons.Default.CheckCircle else Icons.Default.Feedback,
                contentDescription = null,
                tint = if (successMessage != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (successMessage != null) "Feedback Sent!" else "Feedback & Complaints",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (successMessage != null) {
                Column {
                    Text(
                        text = successMessage ?: "Thank you for helping improve BMSIT CP!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your submission has been delivered directly to the developer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FeedbackCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { if (!isSubmitting) selectedCategory = category },
                                label = { Text(category.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        enabled = !isSubmitting,
                        label = { Text("Summary (Optional)") },
                        placeholder = { Text("Short title for your feedback or issue") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = message,
                        onValueChange = {
                            message = it
                            messageError = null
                            submitError = null
                        },
                        enabled = !isSubmitting,
                        label = { Text("Details *") },
                        placeholder = { Text("Describe the bug, complaint, or suggestion...") },
                        isError = messageError != null,
                        supportingText = messageError?.let { err -> { Text(err) } },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = contact,
                        onValueChange = { contact = it },
                        enabled = !isSubmitting,
                        label = { Text("Email or Phone (Optional)") },
                        placeholder = { Text("Leave blank to stay anonymous") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = includeDeviceInfo,
                            onCheckedChange = { if (!isSubmitting) includeDeviceInfo = it },
                            enabled = !isSubmitting
                        )
                        Column {
                            Text(
                                text = "Include app & Android version",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Helps debug device-specific issues cleanly",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Your USN, DOB, and ID card number are never sent.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (submitError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = submitError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        UpdateManager.openInBrowser(context, FeedbackManager.FEEDBACK_WEB_URL)
                                    }
                                ) {
                                    Text("Open Web Form ↗")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (successMessage != null) {
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            } else {
                Button(
                    enabled = !isSubmitting,
                    onClick = {
                        if (message.isBlank()) {
                            messageError = "Please describe your feedback or issue."
                            return@Button
                        }
                        isSubmitting = true
                        submitError = null
                        coroutineScope.launch {
                            val result = FeedbackManager.submitFeedback(
                                context = context,
                                category = selectedCategory,
                                subject = subject,
                                message = message,
                                contact = contact,
                                includeDeviceInfo = includeDeviceInfo
                            )
                            isSubmitting = false
                            result.onSuccess { msg ->
                                successMessage = msg
                            }.onFailure { err ->
                                submitError = err.message ?: "Failed to submit feedback."
                            }
                        }
                    }
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sending...")
                    } else {
                        Text("Submit")
                    }
                }
            }
        },
        dismissButton = {
            if (successMessage == null) {
                TextButton(
                    enabled = !isSubmitting,
                    onClick = onDismiss
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}
