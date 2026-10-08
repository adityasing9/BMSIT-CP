package com.continueo.autologin.automation

object ContineoSelectors {
    const val PORTAL_URL = "https://student.bmsit.ac.in/parents/index.php"

    // Page 1 (Login Page) Selectors
    const val FORM_ID = "login-form"
    const val USERNAME_INPUT_ID = "username"
    const val DAY_SELECT_ID = "dd"
    const val MONTH_SELECT_ID = "mm"
    const val YEAR_SELECT_ID = "yyyy"
    const val PASSWORD_HIDDEN_ID = "passwd"
    const val LOGIN_BUTTON_SELECTOR = "input[type=\"submit\"][value=\"Login\"]"
    
    // Day values have a trailing space (e.g., "01 ", "15 ")
    const val DAY_VALUE_SUFFIX = " "

    // Page 2 (ID Card Page) Selectors / Patterns
    const val ID_CARD_INPUT_FALLBACK_1 = "input[placeholder*='id' i], input[placeholder*='card' i]"
    const val ID_CARD_INPUT_FALLBACK_2 = "input[name*='id' i], input[name*='card' i]"
    const val ID_CARD_SUBMIT_BUTTON = "input[type='submit'], button[type='submit']"
    
    // Page Detection Indicators
    const val LOGIN_PAGE_INDICATOR_ID = USERNAME_INPUT_ID
    const val ID_CARD_PAGE_INDICATOR_TEXT = "Enter the ID Card Number"
}
