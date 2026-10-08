package com.continueo.autologin.automation

import com.continueo.autologin.models.Credentials

/**
 * ContinueO portal-specific automation adapter.
 *
 * Generates JavaScript code to interact with the portal's DOM elements.
 * All selectors are based on actual inspection of the portal at:
 * https://student.bmsit.ac.in/parents/index.php
 *
 * IMPORTANT: Day select values have a trailing space (e.g., "01 " not "01").
 * Month values do NOT have trailing spaces.
 * The hidden "passwd" field must be set to "yyyy-mm-dd" format.
 * The portal's check() function does this, but we also set it directly for safety.
 */
class ContineoAdapter {

    /**
     * Generates JS to fill Page 1 (Login page):
     * - Sets USN in #username input
     * - Selects day in #dd (with trailing space in value)
     * - Selects month in #mm
     * - Selects year in #yyyy
     * - Calls putdate() to trigger the portal's own DOB composition
     * - Sets #passwd hidden field directly as fallback
     */
    fun fillLoginPage(credentials: Credentials): String {
        val dayWithSpace = credentials.dobDay + ContineoSelectors.DAY_VALUE_SUFFIX
        val passwd = "${credentials.dobYear}-${credentials.dobMonth}-${credentials.dobDay}"

        return """
            (function() {
                try {
                    var usnInput = document.getElementById('${ContineoSelectors.USERNAME_INPUT_ID}');
                    if (!usnInput) return JSON.stringify({success: false, error: 'USN input not found'});
                    usnInput.value = '${escapeJs(credentials.usn)}';
                    usnInput.dispatchEvent(new Event('input', {bubbles: true}));

                    var ddSelect = document.getElementById('${ContineoSelectors.DAY_SELECT_ID}');
                    if (!ddSelect) return JSON.stringify({success: false, error: 'Day selector not found'});
                    ddSelect.value = '${escapeJs(dayWithSpace)}';
                    ddSelect.dispatchEvent(new Event('change', {bubbles: true}));

                    var mmSelect = document.getElementById('${ContineoSelectors.MONTH_SELECT_ID}');
                    if (!mmSelect) return JSON.stringify({success: false, error: 'Month selector not found'});
                    mmSelect.value = '${escapeJs(credentials.dobMonth)}';
                    mmSelect.dispatchEvent(new Event('change', {bubbles: true}));

                    var yyyySelect = document.getElementById('${ContineoSelectors.YEAR_SELECT_ID}');
                    if (!yyyySelect) return JSON.stringify({success: false, error: 'Year selector not found'});
                    yyyySelect.value = '${escapeJs(credentials.dobYear)}';
                    yyyySelect.dispatchEvent(new Event('change', {bubbles: true}));

                    // Call portal's putdate() function to populate passwd field
                    if (typeof putdate === 'function') {
                        putdate();
                    }

                    // Also set passwd directly as fallback
                    var passwdInput = document.getElementById('${ContineoSelectors.PASSWORD_HIDDEN_ID}');
                    if (passwdInput) {
                        passwdInput.value = '${escapeJs(passwd)}';
                    }

                    // Verify values were set
                    var verifyUsn = document.getElementById('${ContineoSelectors.USERNAME_INPUT_ID}').value;
                    var verifyDd = document.getElementById('${ContineoSelectors.DAY_SELECT_ID}').value;
                    var verifyMm = document.getElementById('${ContineoSelectors.MONTH_SELECT_ID}').value;
                    var verifyYyyy = document.getElementById('${ContineoSelectors.YEAR_SELECT_ID}').value;

                    if (!verifyUsn || !verifyDd || !verifyMm || !verifyYyyy) {
                        return JSON.stringify({success: false, error: 'Fields not populated correctly'});
                    }

                    return JSON.stringify({success: true});
                } catch(e) {
                    return JSON.stringify({success: false, error: e.toString()});
                }
            })();
        """.trimIndent()
    }

    /**
     * Generates JS to submit Page 1.
     * Calls the portal's check() function first (which composes the password),
     * then submits the form.
     */
    fun submitLoginForm(): String {
        return """
            (function() {
                try {
                    // Call the portal's check() function to finalize passwd field
                    if (typeof check === 'function') {
                        var result = check();
                        if (result === false) {
                            return JSON.stringify({success: false, error: 'Portal check() returned false'});
                        }
                    }

                    // Try clicking the submit button first (preserves onClick handlers)
                    var btn = document.querySelector('${ContineoSelectors.LOGIN_BUTTON_SELECTOR}');
                    if (btn) {
                        btn.click();
                        return JSON.stringify({success: true});
                    }

                    // Fallback: submit the form directly
                    var form = document.getElementById('${ContineoSelectors.FORM_ID}');
                    if (form) {
                        form.submit();
                        return JSON.stringify({success: true});
                    }

                    return JSON.stringify({success: false, error: 'No submit button or form found'});
                } catch(e) {
                    return JSON.stringify({success: false, error: e.toString()});
                }
            })();
        """.trimIndent()
    }

    /**
     * Generates JS to detect if the current page is the login page (Page 1).
     * Checks for the presence of #username input and #dd select.
     */
    fun detectLoginPage(): String {
        return """
            (function() {
                var hasUsername = document.getElementById('${ContineoSelectors.USERNAME_INPUT_ID}') !== null;
                var hasDaySelect = document.getElementById('${ContineoSelectors.DAY_SELECT_ID}') !== null;
                return (hasUsername && hasDaySelect).toString();
            })();
        """.trimIndent()
    }

    /**
     * Generates JS to detect if the current page is the ID Card page (Page 2).
     * Uses multiple detection strategies:
     * 1. Check page text for "ID Card" or "Enter the ID Card Number"
     * 2. Look for input fields by common selectors
     */
    fun detectIdCardPage(): String {
        return """
            (function() {
                try {
                    var bodyText = document.body ? document.body.innerText : '';
                    var textIndicator = bodyText.indexOf('ID Card') !== -1 || 
                                        bodyText.indexOf('${ContineoSelectors.ID_CARD_PAGE_INDICATOR_TEXT}') !== -1;
                    
                    var hasInput1 = document.querySelector("${ContineoSelectors.ID_CARD_INPUT_FALLBACK_1}") !== null;
                    var hasInput2 = document.querySelector("${ContineoSelectors.ID_CARD_INPUT_FALLBACK_2}") !== null;
                    
                    // Also check if login page elements are absent (we're past Page 1)
                    var noLoginPage = document.getElementById('${ContineoSelectors.USERNAME_INPUT_ID}') === null;
                    
                    return ((textIndicator || hasInput1 || hasInput2) && noLoginPage).toString();
                } catch(e) {
                    return 'false';
                }
            })();
        """.trimIndent()
    }

    /**
     * Generates JS to find and fill the ID Card Number input on Page 2.
     * Tries multiple selectors to find the input field.
     */
    fun fillIdCardPage(idCardNumber: String): String {
        return """
            (function() {
                try {
                    var input = null;
                    
                    // Strategy 1: Find by placeholder/name containing 'card' or 'id'
                    input = document.querySelector("${ContineoSelectors.ID_CARD_INPUT_FALLBACK_1}");
                    
                    // Strategy 2: Find by name attribute
                    if (!input) input = document.querySelector("${ContineoSelectors.ID_CARD_INPUT_FALLBACK_2}");
                    
                    // Strategy 3: Find text input fields that are visible
                    if (!input) {
                        var inputs = document.querySelectorAll('input[type="text"], input:not([type])');
                        for (var i = 0; i < inputs.length; i++) {
                            var inp = inputs[i];
                            if (inp.offsetParent !== null && inp.type !== 'hidden') {
                                input = inp;
                                break;
                            }
                        }
                    }
                    
                    if (input) {
                        input.value = '${escapeJs(idCardNumber)}';
                        input.dispatchEvent(new Event('input', {bubbles: true}));
                        input.dispatchEvent(new Event('change', {bubbles: true}));
                        
                        if (input.value === '${escapeJs(idCardNumber)}') {
                            return JSON.stringify({success: true});
                        } else {
                            return JSON.stringify({success: false, error: 'Value not set correctly'});
                        }
                    }
                    return JSON.stringify({success: false, error: 'ID Card input not found'});
                } catch(e) {
                    return JSON.stringify({success: false, error: e.toString()});
                }
            })();
        """.trimIndent()
    }

    /**
     * Generates JS to submit the ID Card form on Page 2.
     */
    fun submitIdCardForm(): String {
        return """
            (function() {
                try {
                    // Try submit button first
                    var btn = document.querySelector("${ContineoSelectors.ID_CARD_SUBMIT_BUTTON}");
                    if (btn) {
                        btn.click();
                        return JSON.stringify({success: true});
                    }
                    
                    // Fallback: find the form containing the ID card input and submit it
                    var input = document.querySelector("${ContineoSelectors.ID_CARD_INPUT_FALLBACK_1}") || 
                                document.querySelector("${ContineoSelectors.ID_CARD_INPUT_FALLBACK_2}");
                    if (input && input.form) {
                        input.form.submit();
                        return JSON.stringify({success: true});
                    }
                    
                    // Last resort: find any form on the page and submit
                    var forms = document.forms;
                    if (forms.length > 0) {
                        forms[0].submit();
                        return JSON.stringify({success: true});
                    }
                    
                    return JSON.stringify({success: false, error: 'Submit button or form not found'});
                } catch(e) {
                    return JSON.stringify({success: false, error: e.toString()});
                }
            })();
        """.trimIndent()
    }

    /**
     * Generates JS to detect if we've successfully passed both login pages.
     * Returns true if neither the login page nor the ID card page is detected.
     */
    fun detectSuccessPage(): String {
        return """
            (function() {
                try {
                    var hasLoginPage = document.getElementById('${ContineoSelectors.USERNAME_INPUT_ID}') !== null;
                    var bodyText = document.body ? document.body.innerText : '';
                    var hasIdCardPage = bodyText.indexOf('${ContineoSelectors.ID_CARD_PAGE_INDICATOR_TEXT}') !== -1;
                    return (!hasLoginPage && !hasIdCardPage).toString();
                } catch(e) {
                    return 'false';
                }
            })();
        """.trimIndent()
    }

    /**
     * Escapes a string for safe inclusion in JavaScript string literals.
     */
    private fun escapeJs(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
