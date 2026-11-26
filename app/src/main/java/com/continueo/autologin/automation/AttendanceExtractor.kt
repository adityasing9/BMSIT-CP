package com.continueo.autologin.automation

import com.continueo.autologin.models.AttendanceSnapshot
import com.continueo.autologin.models.SubjectAttendance
import org.json.JSONObject

object AttendanceExtractor {

    private const val MAX_SUBJECTS = 10

    /**
     * Multi-strategy JavaScript extractor for Contineo portal attendance dashboards:
     * 1. window.Highcharts.charts (Contineo UIkit bar/column charts)
     * 2. Rendered Highcharts SVG DOM labels (.highcharts-container)
     * 3. Chart.js instances
     * 4. Attendance HTML tables & UIkit progress elements
     *
     * Marks/CIE tables and unrelated numeric cells are ignored.
     */
    val EXTRACT_SCRIPT = """
        (function() {
            try {
                // Skip login and ID-card pages cleanly
                if (document.getElementById('username') || document.getElementById('dd')) {
                    return JSON.stringify({found: false, reason: 'login_page'});
                }
                var bodyText = document.body ? (document.body.innerText || '') : '';
                if (bodyText.indexOf('Enter the ID Card Number') !== -1) {
                    return JSON.stringify({found: false, reason: 'id_card_page'});
                }

                var subjects = [];
                var seen = {};
                var overallPct = null;

                function cleanText(s) {
                    return (s || '').replace(/\s+/g, ' ').trim();
                }

                function isOverallLabel(label) {
                    var l = cleanText(label).toLowerCase();
                    return l === 'overall' || l === 'total' || l === 'average' || l === 'overall attendance' || l === 'aggregate';
                }

                function looksLikeNoise(label) {
                    var l = cleanText(label).toLowerCase();
                    if (!l || l.length < 2 || l.length > 60) return true;
                    if (/^\d+(\.\d+)?%?$/.test(l)) return true;
                    if (l.indexOf('cie') !== -1 || l.indexOf('marks') !== -1 || l.indexOf('sgpa') !== -1 || l.indexOf('cgpa') !== -1) return true;
                    if (l === 'sl' || l === 'sl.no' || l === 'subject' || l === 'course' || l === 'attendance' || l === 'percentage') return true;
                    return false;
                }

                function parsePercent(raw) {
                    if (raw === null || raw === undefined) return null;
                    if (typeof raw === 'number' && !isNaN(raw)) {
                        var n = Math.round(raw);
                        return (n >= 0 && n <= 100) ? n : null;
                    }
                    var str = String(raw).trim();
                    var m = str.match(/(\d{1,3}(?:\.\d+)?)\s*%/);
                    if (m) {
                        var val = Math.round(parseFloat(m[1]));
                        return (val >= 0 && val <= 100) ? val : null;
                    }
                    if (/^\d{1,3}(\.\d+)?$/.test(str)) {
                        var plain = Math.round(parseFloat(str));
                        return (plain >= 0 && plain <= 100) ? plain : null;
                    }
                    return null;
                }

                function addSubject(name, pct, detail) {
                    var clean = cleanText(name);
                    if (pct === null || pct === undefined || pct < 0 || pct > 100) return;
                    if (isOverallLabel(clean)) {
                        if (overallPct === null) overallPct = pct;
                        return;
                    }
                    if (looksLikeNoise(clean)) return;
                    var key = clean.toLowerCase();
                    if (seen[key]) return;
                    seen[key] = true;
                    subjects.push({
                        name: clean,
                        percentage: pct,
                        detail: cleanText(detail || '')
                    });
                }

                // Strategy 1: window.Highcharts.charts
                if (window.Highcharts && Array.isArray(window.Highcharts.charts)) {
                    for (var c = 0; c < window.Highcharts.charts.length; c++) {
                        var chart = window.Highcharts.charts[c];
                        if (!chart || !chart.series || !chart.series.length) continue;

                        var titleText = '';
                        try {
                            titleText = ((chart.title && chart.title.textStr) ||
                                (chart.options && chart.options.title && chart.options.title.text) ||
                                (chart.renderTo && chart.renderTo.id) || '').toLowerCase();
                        } catch(e) {}

                        var parentHeading = '';
                        try {
                            var container = chart.renderTo;
                            if (container && container.parentElement) {
                                parentHeading = (container.parentElement.innerText || '').substring(0, 120).toLowerCase();
                            }
                        } catch(e) {}

                        var contextStr = titleText + ' ' + parentHeading;
                        if (contextStr.indexOf('cie') !== -1 || contextStr.indexOf('marks') !== -1 || contextStr.indexOf('internal') !== -1) {
                            continue;
                        }

                        var categories = [];
                        try {
                            if (chart.xAxis && chart.xAxis[0] && Array.isArray(chart.xAxis[0].categories)) {
                                categories = chart.xAxis[0].categories;
                            }
                        } catch(e) {}

                        var series = chart.series[0];
                        if (series && Array.isArray(series.data) && series.data.length > 0) {
                            var isAttendChart = contextStr.indexOf('attend') !== -1 ||
                                (chart.renderTo && (chart.renderTo.id || '').toLowerCase().indexOf('attend') !== -1);

                            // If title unclear, inspect whether values look like 0..100 percentages with course-like categories
                            var validCount = 0;
                            for (var p = 0; p < series.data.length; p++) {
                                var pt = series.data[p];
                                var yVal = (pt && typeof pt.y === 'number') ? pt.y : null;
                                if (yVal !== null && yVal >= 0 && yVal <= 100) validCount++;
                            }

                            if (isAttendChart || (validCount === series.data.length && categories.length >= 2 && series.data.length <= 15)) {
                                for (var p = 0; p < series.data.length; p++) {
                                    var pt = series.data[p];
                                    if (!pt) continue;
                                    var label = pt.name || pt.category || categories[p] || '';
                                    var pct = parsePercent(pt.y);
                                    if (label && pct !== null) {
                                        addSubject(label, pct, '');
                                    }
                                }
                            }
                        }
                    }
                }

                // Strategy 2: Rendered Highcharts SVG DOM fallback
                if (subjects.length === 0) {
                    var hcContainers = document.querySelectorAll('.highcharts-container, svg.highcharts-root');
                    for (var h = 0; h < hcContainers.length; h++) {
                        var root = hcContainers[h];
                        var surroundText = '';
                        try {
                            var box = root.closest ? root.closest('div, section, article, .uk-card, .card') : root.parentElement;
                            if (box) surroundText = (box.innerText || '').substring(0, 160).toLowerCase();
                        } catch(e) {}
                        if (surroundText.indexOf('cie') !== -1 || surroundText.indexOf('marks') !== -1) continue;
                        if (surroundText.indexOf('attend') === -1 && hcContainers.length > 1) continue;

                        var xLabels = root.querySelectorAll('.highcharts-xaxis-labels text');
                        var dLabels = root.querySelectorAll('.highcharts-data-labels text');
                        if (xLabels.length > 0 && xLabels.length === dLabels.length) {
                            for (var i = 0; i < xLabels.length; i++) {
                                var subName = cleanText(xLabels[i].textContent);
                                var pct = parsePercent(cleanText(dLabels[i].textContent));
                                if (subName && pct !== null) {
                                    addSubject(subName, pct, '');
                                }
                            }
                        }
                    }
                }

                // Strategy 3: Chart.js fallback
                if (subjects.length === 0 && window.Chart && window.Chart.instances) {
                    var instances = Array.isArray(window.Chart.instances)
                        ? window.Chart.instances
                        : Object.keys(window.Chart.instances).map(function(k) { return window.Chart.instances[k]; });
                    for (var j = 0; j < instances.length; j++) {
                        var ch = instances[j];
                        if (!ch || !ch.data || !ch.data.datasets || !ch.data.datasets.length) continue;
                        var labels = ch.data.labels || [];
                        var ds = ch.data.datasets[0];
                        var dsLabel = (ds.label || '').toLowerCase();
                        if (dsLabel.indexOf('cie') !== -1 || dsLabel.indexOf('marks') !== -1) continue;
                        if (dsLabel.indexOf('attend') !== -1 || (labels.length >= 2 && Array.isArray(ds.data))) {
                            for (var idx = 0; idx < labels.length; idx++) {
                                var pct = parsePercent(ds.data[idx]);
                                if (labels[idx] && pct !== null) {
                                    addSubject(String(labels[idx]), pct, '');
                                }
                            }
                        }
                    }
                }

                // Strategy 4: HTML Tables related to Attendance
                if (subjects.length === 0) {
                    var tables = document.querySelectorAll('table');
                    for (var t = 0; t < tables.length; t++) {
                        var table = tables[t];
                        var tableText = cleanText(table.innerText).toLowerCase();
                        if (!tableText) continue;
                        if (tableText.indexOf('cie') !== -1 || tableText.indexOf('marks') !== -1 || tableText.indexOf('fee') !== -1) continue;

                        var rows = table.querySelectorAll('tr');
                        var headerText = rows.length > 0 ? cleanText(rows[0].innerText).toLowerCase() : '';
                        var hasPercentCells = (table.innerText || '').indexOf('%') !== -1;
                        if (headerText.indexOf('attend') === -1 && tableText.indexOf('attend') === -1 && !hasPercentCells) {
                            continue;
                        }

                        var attendCol = -1;
                        var nameCol = -1;
                        if (rows.length > 0) {
                            var ths = rows[0].querySelectorAll('th, td');
                            for (var hi = 0; hi < ths.length; hi++) {
                                var hTxt = cleanText(ths[hi].innerText).toLowerCase();
                                if (hTxt.indexOf('attend') !== -1 || hTxt.indexOf('%') !== -1) attendCol = hi;
                                if (nameCol === -1 && (hTxt.indexOf('subject') !== -1 || hTxt.indexOf('course') !== -1 || hTxt.indexOf('code') !== -1 || hTxt.indexOf('title') !== -1)) {
                                    nameCol = hi;
                                }
                            }
                        }

                        for (var r = 0; r < rows.length; r++) {
                            var cells = rows[r].querySelectorAll('td');
                            if (cells.length < 2) continue;

                            var candName = '';
                            var candPct = null;
                            var candDetail = '';

                            if (nameCol >= 0 && nameCol < cells.length) {
                                candName = cleanText(cells[nameCol].innerText);
                            }
                            if (attendCol >= 0 && attendCol < cells.length) {
                                candPct = parsePercent(cleanText(cells[attendCol].innerText));
                            }

                            if (!candName || candPct === null) {
                                for (var ci = 0; ci < cells.length; ci++) {
                                    var cTxt = cleanText(cells[ci].innerText);
                                    if (!cTxt) continue;
                                    var mFraction = cTxt.match(/^(\d{1,3})\s*\/\s*(\d{1,3})$/);
                                    if (mFraction && parseInt(mFraction[2], 10) > 0) {
                                        candDetail = cTxt;
                                        if (candPct === null) {
                                            candPct = Math.round((parseInt(mFraction[1], 10) * 100) / parseInt(mFraction[2], 10));
                                        }
                                        continue;
                                    }
                                    if (cTxt.indexOf('%') !== -1 && candPct === null) {
                                        candPct = parsePercent(cTxt);
                                        continue;
                                    }
                                    if (!candName && !/^\d+$/.test(cTxt) && cTxt.length >= 2) {
                                        candName = cTxt;
                                    }
                                }
                            }

                            if (candName && candPct !== null) {
                                addSubject(candName, candPct, candDetail);
                            }
                        }
                    }
                }

                // Strategy 5: UIkit/HTML progress bars or attendance cards
                if (subjects.length === 0) {
                    var progressEls = document.querySelectorAll('progress, .uk-progress, [role="progressbar"]');
                    for (var k = 0; k < progressEls.length; k++) {
                        var bar = progressEls[k];
                        var pct = null;
                        if (bar.tagName === 'PROGRESS' && bar.max > 0) {
                            pct = Math.round((bar.value * 100) / bar.max);
                        } else if (bar.getAttribute('aria-valuenow')) {
                            pct = parsePercent(bar.getAttribute('aria-valuenow'));
                        } else if (bar.style && bar.style.width && bar.style.width.indexOf('%') !== -1) {
                            pct = parsePercent(bar.style.width);
                        }
                        if (pct === null) continue;
                        var card = bar.closest ? bar.closest('div, li, tr, section') : bar.parentElement;
                        var cardTxt = card ? cleanText(card.innerText) : '';
                        if (!cardTxt) continue;
                        var title = cleanText(cardTxt.replace(/\d{1,3}(?:\.\d+)?\s*%/g, ''));
                        if (title) {
                            addSubject(title, pct, '');
                        }
                    }
                }

                if (subjects.length === 0 && overallPct === null) {
                    return JSON.stringify({found: false, reason: 'no_attendance_found'});
                }

                return JSON.stringify({
                    found: true,
                    overallPercentage: overallPct,
                    subjects: subjects.slice(0, $MAX_SUBJECTS)
                });
            } catch (e) {
                return JSON.stringify({found: false, error: e.toString()});
            }
        })();
    """.trimIndent()

    fun parseExtractedJson(
        rawJsResult: String,
        accountId: String,
        accountName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): AttendanceSnapshot? {
        if (rawJsResult.isBlank() || rawJsResult == "null") return null
        return try {
            val cleanJson = unquoteEvaluateJavascriptResult(rawJsResult)
            val obj = JSONObject(cleanJson)
            if (!obj.optBoolean("found", false)) return null

            val overall = if (obj.has("overallPercentage") && !obj.isNull("overallPercentage")) {
                obj.optInt("overallPercentage").coerceIn(0, 100)
            } else {
                null
            }

            val subjects = mutableListOf<SubjectAttendance>()
            val arr = obj.optJSONArray("subjects")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val name = item.optString("name", "").trim()
                    if (name.isBlank()) continue
                    val pct = item.optInt("percentage", -1)
                    if (pct !in 0..100) continue
                    val detail = item.optString("detail", "").trim()
                    subjects.add(SubjectAttendance(name = name, percentage = pct, detail = detail))
                }
            }

            val snapshot = AttendanceSnapshot(
                accountId = accountId,
                accountName = accountName,
                overallPercentage = overall,
                subjects = subjects.take(MAX_SUBJECTS),
                updatedAtMillis = nowMillis
            )
            if (snapshot.hasData) snapshot else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * WebView.evaluateJavascript wraps returned strings in quotes with escaped inner quotes/backslashes.
     */
    internal fun unquoteEvaluateJavascriptResult(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        return trimmed
    }
}
