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
     * Uses comprehensive heuristics including:
     * - Search by input id/name/placeholder patterns
     * - Search by proximity to "Enter the ID Card Number" text
     * - Visible text inputs inside forms or containers
     * - Frame/iframe traversal
     */
    fun fillIdCardPage(idCardNumber: String): String {
        return """
            (function() {
                try {
                    function findInputInDoc(doc) {
                        if (!doc) return null;
                        
                        // Heuristic 1: Look for id or name matches
                        var explicit = doc.querySelector("input#idcard, input#id_card, input#idno, input#id_no, input#usn, input#cardno, input#card_no, input#regno, input#reg_no, input#key, input[name='idcard'], input[name='id_card'], input[name='idno'], input[name='id_no'], input[name='cardno'], input[name='card_no'], input[name='id'], input[name='card']");
                        if (explicit && explicit.type !== 'hidden') return explicit;

                        // Heuristic 2: Placeholder or aria matches
                        var selectorMatch = doc.querySelector("${ContineoSelectors.ID_CARD_INPUT_FALLBACK_1}, ${ContineoSelectors.ID_CARD_INPUT_FALLBACK_2}");
                        if (selectorMatch && selectorMatch.type !== 'hidden') return selectorMatch;

                        // Heuristic 3: Proximity to "Enter the ID Card Number" or "ID Card"
                        var allElements = doc.querySelectorAll('*');
                        for (var i = 0; i < allElements.length; i++) {
                            var el = allElements[i];
                            var text = el.innerText || el.textContent || '';
                            if (text.indexOf('Enter the ID Card Number') !== -1 || text.indexOf('ID Card') !== -1) {
                                // Search downward inside this container
                                var insideInput = el.querySelector("input[type='text'], input[type='password'], input[type='number'], input:not([type])");
                                if (insideInput && insideInput.type !== 'hidden') return insideInput;

                                // Search siblings / parent container
                                var parent = el.parentElement;
                                if (parent) {
                                    var parentInput = parent.querySelector("input[type='text'], input[type='password'], input[type='number'], input:not([type])");
                                    if (parentInput && parentInput.type !== 'hidden') return parentInput;
                                }
                            }
                        }

                        // Heuristic 4: Any input on Page 2 that isn't hidden submit/token
                        var inputs = doc.getElementsByTagName('input');
                        for (var j = 0; j < inputs.length; j++) {
                            var inp = inputs[j];
                            var t = (inp.type || 'text').toLowerCase();
                            if (t !== 'hidden' && t !== 'submit' && t !== 'button' && t !== 'image') {
                                return inp;
                            }
                        }

                        // Heuristic 5: If only one text/number input exists in any form
                        for (var f = 0; f < doc.forms.length; f++) {
                            var formInputs = doc.forms[f].querySelectorAll('input');
                            for (var fi = 0; fi < formInputs.length; fi++) {
                                var finp = formInputs[fi];
                                var ft = (finp.type || 'text').toLowerCase();
                                if (ft !== 'hidden' && ft !== 'submit' && ft !== 'button') {
                                    return finp;
                                }
                            }
                        }

                        // Heuristic 6: Check nested iframes
                        var iframes = doc.querySelectorAll('iframe, frame');
                        for (var k = 0; k < iframes.length; k++) {
                            try {
                                var frameDoc = iframes[k].contentDocument || iframes[k].contentWindow.document;
                                var res = findInputInDoc(frameDoc);
                                if (res) return res;
                            } catch(err) {}
                        }

                        return null;
                    }

                    var input = findInputInDoc(document);

                    if (input) {
                        input.focus();
                        input.value = '${escapeJs(idCardNumber)}';
                        input.setAttribute('value', '${escapeJs(idCardNumber)}');
                        
                        // Fire all standard user interaction events
                        ['input', 'change', 'blur'].forEach(function(evt) {
                            input.dispatchEvent(new Event(evt, {bubbles: true}));
                        });
                        ['keydown', 'keypress', 'keyup'].forEach(function(evt) {
                            input.dispatchEvent(new KeyboardEvent(evt, {bubbles: true, key: 'Enter'}));
                        });

                        return JSON.stringify({success: true});
                    }

                    return JSON.stringify({
                        success: false, 
                        error: 'ID Card input not found'
                    });
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
                    function findSubmitBtn(doc) {
                        if (!doc) return null;

                        // 1. Buttons with value or text "SUBMIT" / "Submit"
                        var buttons = doc.querySelectorAll("input[type='submit'], input[type='button'], button, a.btn, a.button");
                        for (var i = 0; i < buttons.length; i++) {
                            var b = buttons[i];
                            var val = (b.value || b.innerText || b.textContent || '').trim().toUpperCase();
                            if (val === 'SUBMIT' || val.indexOf('SUBMIT') !== -1 || val === 'LOGIN') {
                                return b;
                            }
                        }

                        // 2. Any submit input/button
                        var defaultBtn = doc.querySelector("${ContineoSelectors.ID_CARD_SUBMIT_BUTTON}");
                        if (defaultBtn) return defaultBtn;

                        // 3. Search in iframes
                        var iframes = doc.querySelectorAll('iframe, frame');
                        for (var k = 0; k < iframes.length; k++) {
                            try {
                                var frameDoc = iframes[k].contentDocument || iframes[k].contentWindow.document;
                                var bRes = findSubmitBtn(frameDoc);
                                if (bRes) return bRes;
                            } catch(err) {}
                        }
                        return null;
                    }

                    var btn = findSubmitBtn(document);
                    if (btn) {
                        btn.click();
                        return JSON.stringify({success: true});
                    }

                    // Fallback to form submission
                    var forms = document.forms;
                    if (forms && forms.length > 0) {
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
