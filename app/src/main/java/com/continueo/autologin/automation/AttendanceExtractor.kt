package com.continueo.autologin.automation

import com.continueo.autologin.models.AttendanceSnapshot
import com.continueo.autologin.models.SubjectAttendance
import org.json.JSONObject

object AttendanceExtractor {

    private const val MAX_SUBJECTS = 10

    /**
     * Multi-strategy JavaScript extractor tailored for BMSIT&M Contineo portals:
     * 1. Billboard.js instances (window.bb.instance — Contineo's primary chart engine)
     * 2. Rendered Billboard.js SVG DOM (.bb, .bb-bars, .bb-texts, .bb-gauge-value, D3 __data__)
     * 3. Contineo progress cards & attendance stats (.cn-att-stat, .sc-progress, .cn-stu-progress, .cn-attendance)
     * 4. Contineo & UIkit tables (.cn-attend-list1/2, .cn-course-table, .uk-table, combined Attendance + CIE tables)
     * 5. Highcharts (JS API & SVG) & Chart.js fallbacks
     * 6. Course card + course-code regex fallback
     */
    val EXTRACT_SCRIPT = """
        (function() {
            try {
                // Collect main document and any accessible same-origin iframes
                var contexts = [];
                function addContext(doc, win) {
                    if (!doc) return;
                    contexts.push({ doc: doc, win: win });
                    try {
                        var frames = doc.querySelectorAll('iframe, frame');
                        for (var f = 0; f < frames.length; f++) {
                            try {
                                var fDoc = frames[f].contentDocument;
                                var fWin = frames[f].contentWindow;
                                if (fDoc) addContext(fDoc, fWin);
                            } catch (_) {}
                        }
                    } catch (_) {}
                }
                addContext(document, window);

                // Skip login and ID-card pages if no dashboard containers are present
                var hasDashboardMarkers = !!document.querySelector('.bb, .cn-att-stat, .cn-course-table, .cn-attend-list1, .cn-attend-list2, .sc-progress');
                if (!hasDashboardMarkers) {
                    if (document.getElementById('username') || document.getElementById('dd')) {
                        return JSON.stringify({found: false, reason: 'login_page'});
                    }
                    var mainBodyText = document.body ? (document.body.innerText || '') : '';
                    if (mainBodyText.indexOf('Enter the ID Card Number') !== -1) {
                        return JSON.stringify({found: false, reason: 'id_card_page'});
                    }
                }

                var subjects = [];
                var seen = {};
                var overallPct = null;
                var usedSource = '';

                function cleanText(s) {
                    return (s || '').replace(/\s+/g, ' ').trim();
                }

                function isOverallLabel(label) {
                    var l = cleanText(label).toLowerCase();
                    return l === 'overall' ||
                        l === 'total' ||
                        l === 'average' ||
                        l === 'avg' ||
                        l === 'overall attendance' ||
                        l === 'aggregate';
                }

                function looksLikeNoise(label) {
                    var l = cleanText(label).toLowerCase();
                    if (!l || l.length < 2 || l.length > 75) return true;
                    if (/^\d+(?:\.\d+)?%?$/.test(l)) return true;
                    if (/^\d{1,3}\s*\/\s*\d{1,3}$/.test(l)) return true;
                    if (/\b(cie|marks|sgpa|cgpa|total marks|internal|credits)\b/.test(l)) return true;
                    if (/^(sl\.?\s*no\.?|subject|course|course code|course name|attendance|percentage|present|absent|action|details?)$/.test(l)) return true;
                    if (/^(present|absent|still to be updated)$/.test(l)) return true;
                    return false;
                }

                function parsePercent(raw, allowPlainNumber) {
                    if (raw === null || raw === undefined) return null;
                    if (typeof raw === 'number' && !isNaN(raw)) {
                        var n = Math.round(raw);
                        return (n >= 0 && n <= 100) ? n : null;
                    }
                    var str = cleanText(String(raw));
                    var m = str.match(/(\d{1,3}(?:\.\d+)?)\s*%/);
                    if (m) {
                        var val = Math.round(parseFloat(m[1]));
                        return (val >= 0 && val <= 100) ? val : null;
                    }
                    if (allowPlainNumber && /^\d{1,3}(?:\.\d+)?$/.test(str)) {
                        var plain = Math.round(parseFloat(str));
                        return (plain >= 0 && plain <= 100) ? plain : null;
                    }
                    return null;
                }

                function parseFractionPercent(str) {
                    var m = cleanText(str).match(/^(\d{1,3})\s*\/\s*(\d{1,3})$/);
                    if (!m) return null;
                    var num = parseInt(m[1], 10);
                    var den = parseInt(m[2], 10);
                    if (den <= 0 || num > den) return null;
                    return Math.round((num * 100) / den);
                }

                function isCieOnlyText(str) {
                    var l = cleanText(str).toLowerCase();
                    if (!l) return false;
                    var hasAttend = /(attend|present|\batt\b)/.test(l);
                    var hasCie = /(\bcie\b|marks|internal|\bsgpa\b|\bcgpa\b|\bfee\b)/.test(l);
                    return hasCie && !hasAttend;
                }

                function hasAttendKeyword(str) {
                    return /(attend|present|\batt\b)/i.test(str || '');
                }

                function getLocalContainerHeading(el) {
                    if (!el) return '';
                    try {
                        // Prefer immediate card/panel without climbing into multi-chart wrappers like .cn-att-cie
                        var card = el.closest ? el.closest('.cn-att-stat, .uk-card, .uk-panel, .card, section') : el.parentElement;
                        if (card) {
                            var heading = card.querySelector('.uk-card-header, .uk-card-title, h1, h2, h3, h4, caption, .cn-att, .cn-cie, .cn-attendance');
                            if (heading) return cleanText(heading.innerText || heading.textContent);
                            return cleanText((card.innerText || '').substring(0, 140));
                        }
                        if (el.parentElement) {
                            return cleanText((el.parentElement.innerText || '').substring(0, 120));
                        }
                    } catch (_) {}
                    return '';
                }

                function addSubject(name, pct, detail, sourceName) {
                    var clean = cleanText(name);
                    if (pct === null || pct === undefined || pct < 0 || pct > 100) return;
                    if (isOverallLabel(clean)) {
                        if (overallPct === null) overallPct = pct;
                        if (!usedSource && sourceName) usedSource = sourceName;
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
                    if (!usedSource && sourceName) usedSource = sourceName;
                }

                var bbCount = 0;
                var bbSvgCount = 0;
                var scProgCount = 0;
                var tableCount = 0;

                for (var ctxIdx = 0; ctxIdx < contexts.length; ctxIdx++) {
                    var doc = contexts[ctxIdx].doc;
                    var win = contexts[ctxIdx].win;

                    // Strategy 1: Billboard.js live instances (win.bb.instance)
                    if (win && win.bb && Array.isArray(win.bb.instance)) {
                        bbCount += win.bb.instance.length;
                        for (var b = 0; b < win.bb.instance.length; b++) {
                            var chart = win.bb.instance[b];
                            if (!chart) continue;

                            var cats = [];
                            try {
                                if (typeof chart.categories === 'function') {
                                    cats = chart.categories() || [];
                                }
                            } catch (_) {}
                            if ((!cats || !cats.length) && chart.internal && chart.internal.config && Array.isArray(chart.internal.config.axis_x_categories)) {
                                cats = chart.internal.config.axis_x_categories;
                            }

                            var dataTargets = [];
                            try {
                                if (typeof chart.data === 'function') {
                                    dataTargets = chart.data() || [];
                                }
                            } catch (_) {}
                            if ((!dataTargets || !dataTargets.length) && chart.internal && chart.internal.data && Array.isArray(chart.internal.data.targets)) {
                                dataTargets = chart.internal.data.targets;
                            }
                            if (!dataTargets || !dataTargets.length) continue;

                            var chartEl = null;
                            try {
                                chartEl = (chart.internal && chart.internal.selectChart && chart.internal.selectChart.node && chart.internal.selectChart.node()) ||
                                    (chart.$ && chart.$.chart && chart.$.chart.node && chart.$.chart.node()) ||
                                    chart.element || null;
                            } catch (_) {}
                            var heading = getLocalContainerHeading(chartEl);
                            if (isCieOnlyText(heading)) continue;

                            var chartType = '';
                            try {
                                chartType = String(
                                    (typeof chart.config === 'function' ? chart.config('data.type') : '') ||
                                    (chart.internal && chart.internal.config && chart.internal.config.data_type) ||
                                    ''
                                ).toLowerCase();
                            } catch (_) {}

                            // Check if chart explicitly has an attendance series
                            var hasExplicitAttendSeries = false;
                            for (var dtCheck = 0; dtCheck < dataTargets.length; dtCheck++) {
                                var idChk = String((dataTargets[dtCheck] && dataTargets[dtCheck].id) || '').toLowerCase();
                                if (hasAttendKeyword(idChk)) {
                                    hasExplicitAttendSeries = true;
                                    break;
                                }
                            }

                            var hasAttendContext = hasAttendKeyword(heading) || hasExplicitAttendSeries || chartType === 'gauge';

                            for (var tIdx = 0; tIdx < dataTargets.length; tIdx++) {
                                var target = dataTargets[tIdx];
                                if (!target || !Array.isArray(target.values) || !target.values.length) continue;
                                var tId = String(target.id || '');
                                var tIdLower = tId.toLowerCase();
                                if (/(\bcie\b|marks|internal|\bia\b|sgpa|cgpa)/.test(tIdLower)) continue;
                                if (hasExplicitAttendSeries && !hasAttendKeyword(tIdLower) && dataTargets.length > 1) {
                                    continue;
                                }

                                // Single-value series without X categories:
                                // - If there is 1 target named Overall/Attendance -> overall gauge %
                                // - If there are multiple targets (Contineo multi-ring gauge per subject) -> each target.id is a subject!
                                if (target.values.length === 1 && (!cats || cats.length === 0)) {
                                    if (!hasAttendContext && !hasAttendKeyword(tIdLower)) continue;

                                    var singlePct = parsePercent(target.values[0] ? target.values[0].value : null, true);
                                    if (singlePct === null) continue;

                                    if (dataTargets.length === 1 && (isOverallLabel(tId) || hasAttendKeyword(tId))) {
                                        if (overallPct === null) {
                                            overallPct = singlePct;
                                            if (!usedSource) usedSource = 'billboard_gauge';
                                        }
                                    } else {
                                        addSubject(tId, singlePct, '', 'billboard_multi_gauge');
                                    }
                                    continue;
                                }

                                var looksValidSeries = hasAttendKeyword(tIdLower) ||
                                    hasAttendKeyword(heading) ||
                                    (cats && cats.length >= 2 && hasAttendContext);
                                if (!looksValidSeries) continue;

                                for (var vi = 0; vi < target.values.length; vi++) {
                                    var v = target.values[vi];
                                    if (!v) continue;
                                    var xIdx = (typeof v.x === 'number') ? Math.round(v.x) : vi;
                                    var lbl = (cats && cats[xIdx] !== undefined) ? cats[xIdx] : (v.name || target.id || '');
                                    var pct = parsePercent(v.value, true);
                                    if (lbl && pct !== null) {
                                        addSubject(lbl, pct, '', 'billboard_api');
                                    }
                                }
                            }
                        }
                    }

                    // Strategy 2: Rendered Billboard.js SVG DOM (.bb) & D3 bound data
                    var bbNodes = doc.querySelectorAll('.bb, svg');
                    for (var s = 0; s < bbNodes.length; s++) {
                        var bbEl = bbNodes[s];
                        var isBbNode = (bbEl.classList && bbEl.classList.contains('bb')) || !!bbEl.querySelector('.bb-bars, .bb-chart-arcs, .bb-axis-x');
                        if (!isBbNode) continue;
                        bbSvgCount++;

                        var svgHeading = getLocalContainerHeading(bbEl);
                        if (isCieOnlyText(svgHeading)) continue;

                        // Check single vs multi-arc gauge inside .bb SVG
                        var arcEls = bbEl.querySelectorAll('.bb-chart-arcs .bb-chart-arc');
                        if (arcEls.length > 1 && subjects.length === 0) {
                            for (var ai = 0; ai < arcEls.length; ai++) {
                                var arcEl = arcEls[ai];
                                var arcData = arcEl.__data__;
                                var arcId = (arcData && arcData.data && arcData.data.id) ? String(arcData.data.id) : '';
                                if (!arcId) {
                                    var clsStr = String((arcEl.className && arcEl.className.baseVal) || arcEl.getAttribute('class') || '');
                                    var mTarget = clsStr.match(/bb-target-([^\s]+)/);
                                    if (mTarget) arcId = mTarget[1];
                                }
                                var arcValEl = arcEl.querySelector ? arcEl.querySelector('.bb-gauge-value') : null;
                                var rawArcVal = arcValEl ? arcValEl.textContent : (arcData ? arcData.value : null);
                                var arcPct = parsePercent(rawArcVal, true);
                                if (arcId && arcPct !== null) {
                                    addSubject(arcId, arcPct, '', 'billboard_svg_multi_arc');
                                }
                            }
                        } else if (arcEls.length <= 1) {
                            var gaugeValEl = bbEl.querySelector('.bb-gauge-value, .bb-chart-arcs-title');
                            if (gaugeValEl && (hasAttendKeyword(svgHeading) || subjects.length === 0)) {
                                var gaugeTitleEl = bbEl.querySelector('.bb-chart-arcs-gauge-title');
                                var gTitle = gaugeTitleEl ? cleanText(gaugeTitleEl.textContent) : '';
                                if (!isCieOnlyText(gTitle)) {
                                    var gVal = parsePercent(gaugeValEl.textContent, true);
                                    if (gVal !== null && overallPct === null) {
                                        overallPct = gVal;
                                        if (!usedSource) usedSource = 'billboard_svg_gauge';
                                    }
                                }
                            }
                        }

                        if (subjects.length > 0) continue;

                        var xTickEls = bbEl.querySelectorAll('.bb-axis-x .tick text');
                        var xCats = [];
                        for (var xi = 0; xi < xTickEls.length; xi++) {
                            xCats.push(cleanText(xTickEls[xi].textContent));
                        }
                        if (xCats.length === 0) continue;

                        // Inspect D3 bound __data__ on .bb-texts text or .bb-bars .bb-bar
                        var d3Nodes = bbEl.querySelectorAll('.bb-texts text.bb-text, .bb-bars .bb-bar');
                        var d3Entries = [];
                        var d3HasAttendId = false;
                        for (var di = 0; di < d3Nodes.length; di++) {
                            var dObj = d3Nodes[di].__data__;
                            if (!dObj || typeof dObj.value !== 'number') continue;
                            var dId = String(dObj.id || '').toLowerCase();
                            if (/(\bcie\b|marks|internal|\bia\b|sgpa|cgpa)/.test(dId)) continue;
                            if (hasAttendKeyword(dId)) d3HasAttendId = true;
                            d3Entries.push(dObj);
                        }

                        if (d3Entries.length > 0) {
                            for (var de = 0; de < d3Entries.length; de++) {
                                var entry = d3Entries[de];
                                var entryId = String(entry.id || '').toLowerCase();
                                if (d3HasAttendId && !hasAttendKeyword(entryId)) continue;
                                var idx = (typeof entry.x === 'number') ? Math.round(entry.x) : entry.index;
                                if (idx >= 0 && idx < xCats.length) {
                                    var pVal = parsePercent(entry.value, true);
                                    if (pVal !== null) {
                                        addSubject(xCats[idx], pVal, '', 'billboard_d3_dom');
                                    }
                                }
                            }
                        }

                        // Pure SVG text fallback without __data__
                        if (subjects.length === 0) {
                            var textEls = bbEl.querySelectorAll('.bb-texts text');
                            if (textEls.length >= xCats.length && xCats.length > 0) {
                                // Try finding explicit Attendance target group first
                                var attendTexts = bbEl.querySelectorAll('.bb-target-Attendance text, .bb-target-attendance text');
                                var chosenTexts = (attendTexts.length === xCats.length) ? attendTexts : textEls;
                                if (chosenTexts.length === xCats.length) {
                                    for (var ti = 0; ti < xCats.length; ti++) {
                                        var tp = parsePercent(chosenTexts[ti].textContent, hasAttendKeyword(svgHeading));
                                        if (tp !== null) {
                                            addSubject(xCats[ti], tp, '', 'billboard_svg_text');
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Strategy 3: Contineo progress widgets (.cn-att-stat, .cn-stu-progress, .sc-progress, .uk-progress, .cn-attendance)
                    var attStatEls = doc.querySelectorAll('.cn-student-data2 .cn-attendance, .cn-att-stat .cn-attendance');
                    for (var as = 0; as < attStatEls.length; as++) {
                        var asPct = parsePercent(attStatEls[as].innerText || attStatEls[as].textContent, true);
                        if (asPct !== null && overallPct === null) {
                            overallPct = asPct;
                            if (!usedSource) usedSource = 'contineo_stat_overall';
                        }
                    }

                    if (subjects.length === 0) {
                        var progEls = doc.querySelectorAll('.sc-progress, .cn-stu-progress, .uk-progress, progress, [role="progressbar"]');
                        scProgCount += progEls.length;
                        for (var pe = 0; pe < progEls.length; pe++) {
                            var pEl = progEls[pe];
                            var pCardText = getLocalContainerHeading(pEl);
                            if (isCieOnlyText(pCardText)) continue;

                            var barPct = null;
                            if (pEl.tagName === 'PROGRESS' && pEl.max > 0) {
                                barPct = Math.round((pEl.value * 100) / pEl.max);
                            } else if (pEl.getAttribute && pEl.getAttribute('aria-valuenow')) {
                                barPct = parsePercent(pEl.getAttribute('aria-valuenow'), true);
                            }
                            if (barPct === null && pEl.style && pEl.style.width && pEl.style.width.indexOf('%') !== -1) {
                                barPct = parsePercent(pEl.style.width, false);
                            }
                            if (barPct === null) {
                                var innerBar = pEl.querySelector('.sc-progress-bar, .uk-progress-bar, [style*="width"]');
                                if (innerBar) {
                                    if (innerBar.style && innerBar.style.width) {
                                        barPct = parsePercent(innerBar.style.width, false);
                                    }
                                    if (barPct === null) {
                                        barPct = parsePercent(innerBar.innerText || innerBar.textContent, false);
                                    }
                                }
                            }

                            var rowCard = pEl.closest ? pEl.closest('.cn-stu-progress, .cn-course, li, tr, .uk-card > div, div') : pEl.parentElement;
                            var labelNode = (rowCard && rowCard.querySelector('.cn-course-name, .sc-progress-label, h4, h5, strong')) || null;
                            var rawRowTxt = cleanText((rowCard && (rowCard.innerText || rowCard.textContent)) || '');
                            if (barPct === null) {
                                barPct = parsePercent(rawRowTxt, false);
                            }
                            if (barPct === null) continue;

                            var rawTitle = labelNode ? cleanText(labelNode.innerText || labelNode.textContent) : rawRowTxt;
                            var subTitle = cleanText(rawTitle.replace(/\d{1,3}(?:\.\d+)?\s*%/g, '').replace(/\b\d{1,3}\s*\/\s*\d{1,3}\b/g, ''));
                            if (subTitle) {
                                addSubject(subTitle, barPct, '', 'contineo_progress');
                            }
                        }
                    }

                    // Strategy 4: Contineo & HTML Tables (handles combined Attendance + CIE course tables safely)
                    if (subjects.length === 0) {
                        var tables = doc.querySelectorAll('.cn-attend-list1, .cn-attend-list2, .cn-course-table, .cn-reg-table, .cn-present-table, table');
                        tableCount += tables.length;
                        for (var tb = 0; tb < tables.length; tb++) {
                            var table = tables[tb];
                            if (table.tagName !== 'TABLE') continue;
                            var fullTableTxt = cleanText(table.innerText || table.textContent).toLowerCase();
                            if (!fullTableTxt) continue;

                            var tableClass = String(table.className || '').toLowerCase();
                            var isKnownCourseTable = /(cn-attend|cn-course|cn-reg|cn-present|uk-table)/.test(tableClass);
                            var hasAttInTable = hasAttendKeyword(fullTableTxt) || !!table.querySelector('.cn-attendanceclr, .cn-attendance');

                            // Skip unrelated tables (fees/result/CIE-only tables without attendance)
                            if (!hasAttInTable && /(cie|marks|sgpa|cgpa|fee|amount|receipt|timetable)/.test(fullTableTxt)) {
                                continue;
                            }
                            if (!hasAttInTable && !isKnownCourseTable && fullTableTxt.indexOf('%') === -1) {
                                continue;
                            }

                            var allRows = table.querySelectorAll('tr');
                            if (allRows.length < 2) continue;

                            var attendCol = -1;
                            var nameCol = -1;
                            var codeCol = -1;

                            // Inspect header row(s)
                            var headerRows = table.querySelectorAll('thead tr');
                            var hdrRow = headerRows.length > 0 ? headerRows[headerRows.length - 1] : allRows[0];
                            if (hdrRow) {
                                var hdrCells = hdrRow.querySelectorAll('th, td');
                                for (var hc = 0; hc < hdrCells.length; hc++) {
                                    var hTxt = cleanText(hdrCells[hc].innerText || hdrCells[hc].textContent).toLowerCase();
                                    if (!hTxt) continue;
                                    var isCieCol = /(\bcie\b|marks|\bia\b|score|grade)/.test(hTxt);
                                    if (attendCol === -1 && !isCieCol && (hasAttendKeyword(hTxt) || hTxt.indexOf('%') !== -1)) {
                                        attendCol = hc;
                                    }
                                    if (codeCol === -1 && /\bcode\b/.test(hTxt)) {
                                        codeCol = hc;
                                    }
                                    if (nameCol === -1 && /(course name|subject|course|title|paper)/.test(hTxt) && !/\bcode\b/.test(hTxt)) {
                                        nameCol = hc;
                                    }
                                }
                            }
                            if (nameCol === -1 && codeCol >= 0) nameCol = codeCol;

                            for (var r = 0; r < allRows.length; r++) {
                                if (allRows[r].closest && allRows[r].closest('thead')) continue;
                                var cells = allRows[r].querySelectorAll('td');
                                if (cells.length < 2) continue;

                                var rowName = '';
                                if (nameCol >= 0 && nameCol < cells.length) {
                                    rowName = cleanText(cells[nameCol].innerText || cells[nameCol].textContent);
                                } else if (codeCol >= 0 && codeCol < cells.length) {
                                    rowName = cleanText(cells[codeCol].innerText || cells[codeCol].textContent);
                                }
                                if (looksLikeNoise(rowName) && codeCol >= 0 && codeCol < cells.length) {
                                    rowName = cleanText(cells[codeCol].innerText || cells[codeCol].textContent);
                                }

                                var rowPct = null;
                                var rowDetail = '';

                                // 1) Explicit attendance column
                                if (attendCol >= 0 && attendCol < cells.length) {
                                    var attCell = cells[attendCol];
                                    var attTxt = cleanText(attCell.innerText || attCell.textContent);
                                    rowPct = parsePercent(attTxt, true);
                                    if (rowPct === null) {
                                        rowPct = parseFractionPercent(attTxt);
                                        if (rowPct !== null) rowDetail = attTxt;
                                    }
                                }

                                // 2) Contineo .cn-attendanceclr / .cn-attendance cell badge
                                if (rowPct === null) {
                                    var attBadge = allRows[r].querySelector('.cn-attendanceclr, .cn-attendance, [href*="attend"]');
                                    if (attBadge) {
                                        var badgeTxt = cleanText(attBadge.innerText || attBadge.textContent);
                                        rowPct = parsePercent(badgeTxt, false);
                                        if (rowPct === null) {
                                            rowPct = parseFractionPercent(badgeTxt);
                                            if (rowPct !== null) rowDetail = badgeTxt;
                                        }
                                    }
                                }

                                // 3) Cell-by-cell % scan excluding explicit CIE elements
                                if (rowPct === null || !rowName) {
                                    for (var ci = 0; ci < cells.length; ci++) {
                                        var cellEl = cells[ci];
                                        if (cellEl.querySelector && cellEl.querySelector('.cn-cieclr, .cn-cie')) continue;
                                        var cTxt = cleanText(cellEl.innerText || cellEl.textContent);
                                        if (!cTxt) continue;
                                        if (!rowName && cTxt.indexOf('%') === -1 && !/^\d+(?:\.\d+)?$/.test(cTxt) && !looksLikeNoise(cTxt)) {
                                            rowName = cTxt;
                                            continue;
                                        }
                                        if (rowPct === null && cTxt.indexOf('%') !== -1) {
                                            rowPct = parsePercent(cTxt, false);
                                        }
                                    }
                                }

                                if (rowName && rowPct !== null) {
                                    addSubject(rowName, rowPct, rowDetail, 'contineo_table');
                                }
                            }
                        }
                    }

                    // Strategy 5: Highcharts JS API / SVG & Chart.js fallback
                    if (subjects.length === 0 && win && win.Highcharts && Array.isArray(win.Highcharts.charts)) {
                        for (var c = 0; c < win.Highcharts.charts.length; c++) {
                            var hChart = win.Highcharts.charts[c];
                            if (!hChart || !hChart.series || !hChart.series.length) continue;

                            var hcTitle = '';
                            try {
                                hcTitle = ((hChart.title && hChart.title.textStr) ||
                                    (hChart.options && hChart.options.title && hChart.options.title.text) || '').toLowerCase();
                            } catch (_) {}
                            var hcHeading = getLocalContainerHeading(hChart.renderTo);
                            if (isCieOnlyText(hcTitle + ' ' + hcHeading)) continue;

                            var hCats = [];
                            try {
                                if (hChart.xAxis && hChart.xAxis[0] && Array.isArray(hChart.xAxis[0].categories)) {
                                    hCats = hChart.xAxis[0].categories;
                                }
                            } catch (_) {}

                            var hSeries = hChart.series[0];
                            if (hSeries && Array.isArray(hSeries.data) && hSeries.data.length > 0) {
                                if (hasAttendKeyword(hcTitle + ' ' + hcHeading) || hCats.length >= 2) {
                                    for (var hp = 0; hp < hSeries.data.length; hp++) {
                                        var hPt = hSeries.data[hp];
                                        if (!hPt) continue;
                                        var hLbl = hPt.name || hPt.category || hCats[hp] || '';
                                        var hPct = parsePercent(hPt.y, true);
                                        if (hLbl && hPct !== null) {
                                            addSubject(hLbl, hPct, '', 'highcharts');
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (subjects.length === 0 && win && win.Chart && win.Chart.instances) {
                        var cInsts = Array.isArray(win.Chart.instances)
                            ? win.Chart.instances
                            : Object.keys(win.Chart.instances).map(function(k) { return win.Chart.instances[k]; });
                        for (var cj = 0; cj < cInsts.length; cj++) {
                            var cjs = cInsts[cj];
                            if (!cjs || !cjs.data || !cjs.data.datasets || !cjs.data.datasets.length) continue;
                            var cLabels = cjs.data.labels || [];
                            var cds = cjs.data.datasets[0];
                            if (isCieOnlyText(cds.label || '')) continue;
                            if (hasAttendKeyword(cds.label || '') || cLabels.length >= 2) {
                                for (var cl = 0; cl < cLabels.length; cl++) {
                                    var cPct = parsePercent(cds.data[cl], true);
                                    if (cLabels[cl] && cPct !== null) {
                                        addSubject(String(cLabels[cl]), cPct, '', 'chartjs');
                                    }
                                }
                            }
                        }
                    }

                    // Strategy 6: Generic individual course card (skip container cards holding charts/tables or multiple codes)
                    if (subjects.length === 0) {
                        var cardEls = doc.querySelectorAll('.cn-course, .cn-course-info, .cn-course-details, .uk-card');
                        for (var cc = 0; cc < cardEls.length; cc++) {
                            var cardNode = cardEls[cc];
                            if (cardNode.querySelector && cardNode.querySelector('.bb, svg, table, canvas')) continue;
                            var cardTxt = cleanText(cardNode.innerText || cardNode.textContent);
                            if (!cardTxt || cardTxt.length > 240 || isCieOnlyText(cardTxt)) continue;
                            var allCodes = cardTxt.match(/\b(?:\d{2}[A-Z]{2,5}\d{1,3}[A-Z]?|[A-Z]{2,6}\d{3}[A-Z]?)\b/g);
                            if (!allCodes || allCodes.length !== 1) continue;
                            var mPct = cardTxt.match(/(\d{1,3}(?:\.\d+)?)\s*%/);
                            if (mPct && hasAttendKeyword(cardTxt)) {
                                addSubject(allCodes[0], parsePercent(mPct[1], true), '', 'course_card');
                            }
                        }
                    }
                }

                if (subjects.length === 0 && overallPct === null) {
                    return JSON.stringify({
                        found: false,
                        reason: 'no_attendance_found',
                        debug: {
                            path: String(location.pathname || ''),
                            bbCount: bbCount,
                            bbSvgCount: bbSvgCount,
                            scProgCount: scProgCount,
                            tableCount: tableCount
                        }
                    });
                }

                return JSON.stringify({
                    found: true,
                    overallPercentage: overallPct,
                    subjects: subjects.slice(0, $MAX_SUBJECTS),
                    source: usedSource
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
     * WebView.evaluateJavascript wraps returned strings in quotes and escapes JSON inner characters.
     * Implemented in pure Kotlin so JVM unit tests work without Android org.json stubs.
     */
    internal fun unquoteEvaluateJavascriptResult(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            val inner = trimmed.substring(1, trimmed.length - 1)
            val sb = StringBuilder(inner.length)
            var i = 0
            while (i < inner.length) {
                val ch = inner[i]
                if (ch == '\\' && i + 1 < inner.length) {
                    val next = inner[i + 1]
                    when (next) {
                        '\\' -> { sb.append('\\'); i += 2 }
                        '"' -> { sb.append('"'); i += 2 }
                        '/' -> { sb.append('/'); i += 2 }
                        'n' -> { sb.append('\n'); i += 2 }
                        'r' -> { sb.append('\r'); i += 2 }
                        't' -> { sb.append('\t'); i += 2 }
                        'u' -> {
                            if (i + 5 < inner.length) {
                                val code = inner.substring(i + 2, i + 6).toIntOrNull(16)
                                if (code != null) {
                                    sb.append(code.toChar())
                                    i += 6
                                    continue
                                }
                            }
                            sb.append(next)
                            i += 2
                        }
                        else -> { sb.append(next); i += 2 }
                    }
                } else {
                    sb.append(ch)
                    i++
                }
            }
            return sb.toString()
        }
        return trimmed
    }
}
