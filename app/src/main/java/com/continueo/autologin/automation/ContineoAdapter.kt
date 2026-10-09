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
     * Uses an extremely aggressive search that:
     * 1. Tries all inputs, textareas, selects
     * 2. Checks shadow DOM
     * 3. On failure, dumps full DOM diagnostics for debugging
     */
    fun fillIdCardPage(idCardNumber: String): String {
        return """
            (function() {
                try {
                    // Collect ALL possible input-like elements across the entire document
                    function getAllInputs(root) {
                        var results = [];
                        if (!root) return results;
                        
                        // Standard inputs
                        var inputs = root.querySelectorAll('input, textarea, select');
                        for (var i = 0; i < inputs.length; i++) results.push(inputs[i]);
                        
                        // Also check contenteditable elements
                        var editables = root.querySelectorAll('[contenteditable="true"]');
                        for (var e = 0; e < editables.length; e++) results.push(editables[e]);
                        
                        // Check shadow DOM hosts
                        var allEls = root.querySelectorAll('*');
                        for (var s = 0; s < allEls.length; s++) {
                            if (allEls[s].shadowRoot) {
                                var shadowInputs = getAllInputs(allEls[s].shadowRoot);
                                for (var si = 0; si < shadowInputs.length; si++) results.push(shadowInputs[si]);
                            }
                        }
                        
                        // Check iframes
                        var iframes = root.querySelectorAll('iframe, frame');
                        for (var f = 0; f < iframes.length; f++) {
                            try {
                                var frameDoc = iframes[f].contentDocument || iframes[f].contentWindow.document;
                                var frameInputs = getAllInputs(frameDoc);
                                for (var fi = 0; fi < frameInputs.length; fi++) results.push(frameInputs[fi]);
                            } catch(err) {}
                        }
                        
                        return results;
                    }
                    
                    var allInputs = getAllInputs(document);
                    
                    // Build diagnostic info
                    var diag = [];
                    for (var d = 0; d < allInputs.length; d++) {
                        var el = allInputs[d];
                        diag.push({
                            tag: el.tagName,
                            type: el.type || '',
                            id: el.id || '',
                            name: el.name || '',
                            placeholder: el.placeholder || '',
                            className: (el.className || '').toString().substring(0, 60),
                            visible: el.offsetParent !== null || el.offsetWidth > 0 || el.offsetHeight > 0,
                            display: window.getComputedStyle ? window.getComputedStyle(el).display : ''
                        });
                    }
                    
                    // Strategy 1: By explicit id/name matching card/id patterns
                    var found = null;
                    for (var i = 0; i < allInputs.length; i++) {
                        var inp = allInputs[i];
                        if (inp.tagName === 'INPUT' || inp.tagName === 'TEXTAREA') {
                            var t = (inp.type || 'text').toLowerCase();
                            if (t === 'hidden' || t === 'submit' || t === 'button' || t === 'image' || t === 'checkbox' || t === 'radio') continue;
                            var idNamePlc = ((inp.id || '') + ' ' + (inp.name || '') + ' ' + (inp.placeholder || '')).toLowerCase();
                            if (idNamePlc.indexOf('card') !== -1 || idNamePlc.indexOf('idno') !== -1 || idNamePlc.indexOf('id_no') !== -1 || idNamePlc.indexOf('regno') !== -1) {
                                found = inp;
                                break;
                            }
                        }
                    }
                    
                    // Strategy 2: Any visible text/number/tel input that isn't username/password/date related
                    if (!found) {
                        for (var i = 0; i < allInputs.length; i++) {
                            var inp = allInputs[i];
                            if (inp.tagName !== 'INPUT' && inp.tagName !== 'TEXTAREA') continue;
                            var t = (inp.type || 'text').toLowerCase();
                            if (t === 'hidden' || t === 'submit' || t === 'button' || t === 'image' || t === 'checkbox' || t === 'radio') continue;
                            // Skip login page fields if they somehow exist
                            if (inp.id === 'username' || inp.id === 'passwd' || inp.id === 'recaptcha-token') continue;
                            if (inp.name === 'username' || inp.name === 'passwd' || inp.name === 'token' || inp.name === 'action') continue;
                            found = inp;
                            break;
                        }
                    }
                    
                    // Strategy 3: First textarea
                    if (!found) {
                        for (var i = 0; i < allInputs.length; i++) {
                            if (allInputs[i].tagName === 'TEXTAREA') {
                                found = allInputs[i];
                                break;
                            }
                        }
                    }
                    
                    // Strategy 4: Contenteditable element
                    if (!found) {
                        for (var i = 0; i < allInputs.length; i++) {
                            if (allInputs[i].getAttribute && allInputs[i].getAttribute('contenteditable') === 'true') {
                                found = allInputs[i];
                                break;
                            }
                        }
                    }

                    if (found) {
                        found.focus();
                        // Use native setter to bypass React/Angular controlled components
                        var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                        if (nativeInputValueSetter && nativeInputValueSetter.set) {
                            nativeInputValueSetter.set.call(found, '${escapeJs(idCardNumber)}');
                        } else {
                            found.value = '${escapeJs(idCardNumber)}';
                        }
                        found.setAttribute('value', '${escapeJs(idCardNumber)}');
                        
                        // Fire all standard user interaction events
                        ['input', 'change', 'blur'].forEach(function(evt) {
                            found.dispatchEvent(new Event(evt, {bubbles: true}));
                        });
                        ['keydown', 'keypress', 'keyup'].forEach(function(evt) {
                            found.dispatchEvent(new KeyboardEvent(evt, {bubbles: true, key: '0'}));
                        });

                        return JSON.stringify({success: true, method: found.tagName + '#' + found.id + '.' + found.name});
                    }

                    // No input found — dump diagnostics
                    var bodySnippet = document.body ? document.body.innerHTML.substring(0, 1500) : 'NO BODY';
                    return JSON.stringify({
                        success: false, 
                        error: 'ID Card input not found',
                        inputCount: allInputs.length,
                        inputs: diag.slice(0, 20),
                        url: window.location.href,
                        bodyText: (document.body ? document.body.innerText : '').substring(0, 500),
                        bodyHtml: bodySnippet
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
