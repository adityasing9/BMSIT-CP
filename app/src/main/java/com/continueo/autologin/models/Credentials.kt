package com.continueo.autologin.models

data class Credentials(
    val usn: String,
    val dobDay: String,
    val dobMonth: String,
    val dobYear: String,
    val idCardNumber: String
) {
    fun isValid(): Boolean {
        return usn.isNotBlank() &&
                dobDay.isNotBlank() &&
                dobMonth.isNotBlank() &&
                dobYear.isNotBlank() &&
                idCardNumber.isNotBlank()
    }

    fun maskedSummary(): String {
        val maskedUsn = if (usn.length > 4) {
            "${usn.take(2)}***${usn.takeLast(2)}"
        } else {
            "***"
        }
        val maskedId = if (idCardNumber.length > 4) {
            "${idCardNumber.take(2)}***${idCardNumber.takeLast(2)}"
        } else {
            "***"
        }
        return "USN: $maskedUsn | ID: $maskedId"
    }
}
