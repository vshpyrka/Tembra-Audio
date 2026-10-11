/* Tembra Audio performance report: hash router and inline-SVG charts over the embedded JSON.
   Views: #/ (device grid), #/device/<id>, #/device/<id>/<series>. No network, no libraries. */
(function () {
  "use strict";
  var DATA = JSON.parse(document.getElementById("tracepulse-data").textContent);
  var view = document.getElementById("view");
  var crumbs = document.getElementById("crumbs");
  var tooltip = document.getElementById("tooltip");
  var STATUS_WORD = { CONFIRMED: "Confirmed", SUSPECT: "Suspect", PRELIMINARY: "First hint", IMPROVED: "Improved", ENV_SUSPECT: "Environment-suspect" };

  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function fmt(v, unit) {
    if (v == null) return "—";
    var a = Math.abs(v);
    var s = a >= 1000 ? Math.round(v).toLocaleString("en-US") : a >= 100 ? v.toFixed(1) : a >= 10 ? v.toFixed(2) : v.toFixed(3);
    return s + (unit ? " " + unit : "");
  }
  function pct(v) { return v == null ? "—" : (v > 0 ? "+" : "") + v.toFixed(1) + " %"; }
  function device(id) { for (var i = 0; i < DATA.devices.length; i++) if (DATA.devices[i].device_id === id) return DATA.devices[i]; return null; }
  function seriesOf(dev, key) { for (var f = 0; f < dev.families.length; f++) for (var s = 0; s < dev.families[f].series.length; s++) if (dev.families[f].series[s].key === key) return dev.families[f].series[s]; return null; }
  function badges(list) { return list.map(function (b) { return '<span class="badge ' + esc(b) + '">' + esc(b) + "</span>"; }).join(" "); }

  /* ---------- charts ---------- */
  var W = 560, H = 220, PAD = { l: 52, r: 16, t: 14, b: 30 };

  function scale(points, limitLines) {
    var lo = Infinity, hi = -Infinity;
    points.forEach(function (p) { if (!p) return; lo = Math.min(lo, p[1], p[0]); hi = Math.max(hi, p[2], p[0]); });
    (limitLines || []).forEach(function (v) { lo = Math.min(lo, v); hi = Math.max(hi, v); });
    if (lo === Infinity) { lo = 0; hi = 1; }
    if (hi === lo) { hi = lo + (Math.abs(lo) || 1) * 0.1; lo = lo - (Math.abs(lo) || 1) * 0.1; }
    var span = hi - lo; lo -= span * 0.08; hi += span * 0.08;
    if (lo > 0 && lo < span * 0.5) lo = 0;
    return { lo: lo, hi: hi };
  }
  function ticks(lo, hi, n) {
    var raw = (hi - lo) / n, mag = Math.pow(10, Math.floor(Math.log10(raw))), norm = raw / mag;
    var step = (norm < 1.5 ? 1 : norm < 3.5 ? 2 : norm < 7.5 ? 5 : 10) * mag;
    var out = [], v = Math.ceil(lo / step) * step;
    for (; v <= hi + 1e-9; v += step) out.push(+v.toPrecision(8));
    return out;
  }
  function chartSvg(dev, s, big) {
    var w = big ? 980 : W, h = big ? 340 : H, pad = PAD;
    var n = dev.rounds.length, pts = s.points;
    var limitLines = [];
    s.gate.forEach(function (g) { if (g.state === "above_limit" && g.limit_pct != null && pts[g.round_index]) { limitLines.push(pts[g.round_index][0] / (1 + g.observed_pct / 100) * (1 + g.limit_pct / 100)); } });
    var sc = scale(pts, limitLines);
    var x = function (i) { return n <= 1 ? pad.l + (w - pad.l - pad.r) / 2 : pad.l + (w - pad.l - pad.r) * i / (n - 1); };
    var y = function (v) { return pad.t + (h - pad.t - pad.b) * (1 - (v - sc.lo) / (sc.hi - sc.lo)); };
    var out = ['<svg class="plot" viewBox="0 0 ' + w + " " + h + '" role="img" aria-label="' + esc(s.title) + '">'];
    var tickValues = ticks(sc.lo, sc.hi, 4);
    var integral = tickValues.every(function (t) { return Math.abs(t - Math.round(t)) < 1e-9; });
    tickValues.forEach(function (t) {
      out.push('<line class="grid-line" x1="' + pad.l + '" x2="' + (w - pad.r) + '" y1="' + y(t).toFixed(1) + '" y2="' + y(t).toFixed(1) + '"/>');
      var label = integral ? Math.round(t).toLocaleString("en-US") : fmt(t, "");
      out.push('<g class="axis"><text x="' + (pad.l - 6) + '" y="' + (y(t) + 3).toFixed(1) + '" text-anchor="end">' + esc(label) + "</text></g>");
    });
    // Rounds of one day are told apart by their time.
    var days = {}; dev.rounds.forEach(function (r) { days[r.date.slice(0, 10)] = (days[r.date.slice(0, 10)] || 0) + 1; });
    var sameDay = Object.keys(days).some(function (d) { return days[d] > 1; });
    var every = n > 12 ? Math.ceil(n / 8) : 1;
    for (var i = 0; i < n; i += every) {
      var dateLabel = sameDay ? dev.rounds[i].date.slice(5, 16) : dev.rounds[i].date.slice(5, 10);
      out.push('<g class="axis"><text x="' + x(i).toFixed(1) + '" y="' + (h - 10) + '" text-anchor="middle">' + esc(dateLabel) + "</text></g>");
    }
    dev.label_boundaries.forEach(function (b) {
      var bx = x(b.round_index) - (n > 1 ? (w - pad.l - pad.r) / (n - 1) / 2 : 0);
      out.push('<line class="boundary" x1="' + bx.toFixed(1) + '" x2="' + bx.toFixed(1) + '" y1="' + pad.t + '" y2="' + (h - pad.b) + '"/>');
      out.push('<text class="boundary-label" x="' + (bx + 4).toFixed(1) + '" y="' + (pad.t + 10) + '">' + esc(b.label) + "</text>");
    });
    dev.fingerprint_changes.forEach(function (c) {
      out.push('<line class="fingerprint" x1="' + x(c.round_index).toFixed(1) + '" x2="' + x(c.round_index).toFixed(1) + '" y1="' + pad.t + '" y2="' + (h - pad.b) + '"><title>' + esc(c.field + " " + c.before + " → " + c.after) + "</title></line>");
    });
    var band = [], line = [], back = [];
    pts.forEach(function (p, i) { if (!p) return; band.push(x(i).toFixed(1) + "," + y(p[2]).toFixed(1)); back.unshift(x(i).toFixed(1) + "," + y(p[1]).toFixed(1)); line.push(x(i).toFixed(1) + "," + y(p[0]).toFixed(1)); });
    if (band.length > 1) out.push('<polygon class="band" points="' + band.concat(back).join(" ") + '"/>');
    if (line.length > 1) out.push('<polyline class="line" points="' + line.join(" ") + '"/>');
    pts.forEach(function (p, i) { if (p) out.push('<circle class="pt" cx="' + x(i).toFixed(1) + '" cy="' + y(p[0]).toFixed(1) + '" r="2.5"/>'); });
    s.gate.forEach(function (g) {
      if (g.state !== "above_limit" || !pts[g.round_index]) return;
      var gx = x(g.round_index), gy = y(pts[g.round_index][0]);
      var lv = pts[g.round_index][0] / (1 + g.observed_pct / 100) * (1 + g.limit_pct / 100);
      var x0 = x(Math.max(0, g.round_index - DATA.config.clean_rounds));
      out.push('<line class="limit" x1="' + x0.toFixed(1) + '" x2="' + gx.toFixed(1) + '" y1="' + y(lv).toFixed(1) + '" y2="' + y(lv).toFixed(1) + '"/>');
      out.push('<path class="gate-flag" d="M' + gx.toFixed(1) + " " + (gy - 9).toFixed(1) + " l7 9 l-7 9 l-7 -9 z\"><title>" + esc("above limit: " + pct(g.observed_pct) + " (limit " + g.limit_pct + " %)") + "</title></path>");
    });
    s.detections.forEach(function (d) {
      var p = pts[d.onset_round_index]; if (!p) return;
      var cx = x(d.onset_round_index), cy = y(p[0]);
      var title = "<title>" + esc(STATUS_WORD[d.status] + " " + pct(d.delta_pct) + (d.label_at_onset ? " in " + d.label_at_onset : "")) + "</title>";
      if (d.status === "IMPROVED") out.push('<path class="mark-IMPROVED" d="M' + (cx - 7).toFixed(1) + " " + (cy - 7).toFixed(1) + " h14 l-7 12 z\">" + title + "</path>");
      else if (d.status === "ENV_SUSPECT") out.push('<rect class="mark-ENV_SUSPECT" x="' + (cx - 8).toFixed(1) + '" y="' + pad.t + '" width="16" height="' + (h - pad.t - pad.b) + '">' + title + "</rect>");
      else out.push('<circle class="mark-' + d.status + '" cx="' + cx.toFixed(1) + '" cy="' + cy.toFixed(1) + '" r="6">' + title + "</circle>");
    });
    for (var k = 0; k < n; k++) {
      var p = pts[k], r = dev.rounds[k], gate = null;
      s.gate.forEach(function (g) { if (g.round_index === k) gate = g; });
      var text = r.date + " · " + r.label + (p ? "\n" + s.title + ": " + fmt(p[0], s.unit) + " (IQR " + fmt(p[1], "") + "–" + fmt(p[2], s.unit) + ")" : "\nno value") +
        (gate ? "\ngate: " + gate.state.replace("_", " ") + (gate.observed_pct != null ? " " + pct(gate.observed_pct) : "") + (gate.reason ? " (" + gate.reason + ")" : "") : "") +
        (r.badges.length ? "\nbadges: " + r.badges.join(", ") : "") + "\ncommit " + r.commit;
      var cw = n > 1 ? (w - pad.l - pad.r) / (n - 1) : w - pad.l - pad.r;
      out.push('<rect class="hit" x="' + (x(k) - cw / 2).toFixed(1) + '" y="' + pad.t + '" width="' + cw.toFixed(1) + '" height="' + (h - pad.t - pad.b) + '" data-tip="' + esc(text) + '"/>');
    }
    out.push("</svg>");
    return out.join("");
  }

  /* ---------- views ---------- */
  function legend() {
    return '<p class="legend"><span class="l-confirmed">confirmed</span><span class="l-suspect">suspect</span><span class="l-preliminary">first hint</span><span class="l-improved">improved</span><span class="l-env">environment-suspect</span><span class="l-gate">gate flag + limit</span><span class="l-boundary">label boundary</span></p>';
  }
  function gridView() {
    var h = ['<section class="grid-view"><p class="summary">' + esc(DATA.summary.text) + "</p>"];
    if (DATA.flagged.length) {
      h.push("<h2>Flagged</h2><ul class=\"list\">");
      DATA.flagged.forEach(function (f) { h.push('<li class="above_limit"><a href="#/device/' + esc(f.device_id) + "/" + encodeURIComponent(f.metric) + '">' + esc(f.device_model + ": " + f.title) + "</a> " + esc(pct(f.observed_pct)) + " against the limit of " + esc(f.limit_pct) + " % in round " + esc(f.round_key) + "</li>"); });
      h.push("</ul>");
    }
    if (DATA.changes.length) {
      h.push("<h2>Changes</h2><ul class=\"list\">");
      DATA.changes.forEach(function (c) { h.push('<li class="' + esc(c.status) + '"><a href="#/device/' + esc(c.device_id) + "/" + encodeURIComponent(c.metric) + '">' + esc(c.device_model) + "</a> — " + esc(c.sentence) + "</li>"); });
      h.push("</ul>");
    }
    h.push("<h2>Devices</h2><div class=\"grid\">");
    DATA.devices.forEach(function (d) {
      h.push('<a class="card' + (d.reference ? "" : " non-reference") + '" href="#/device/' + esc(d.device_id) + '"><div class="card-head"><span class="platform ' + esc(d.platform) + '">' + esc(d.platform) + "</span><strong>" + esc(d.model) + "</strong>" + badges(d.badges) + "</div>" +
        '<p class="card-headline">' + esc(d.headline) + '</p><p class="card-meta">' + (d.last_round ? "last round " + esc(d.last_round.date) + " · " + esc(d.last_round.label) + " · " : "") + d.counts.rounds + " rounds · " + d.counts.open_flags + " open gate flags · " + d.counts.recent_changes + " onsets in the last session</p></a>");
    });
    h.push("</div></section>");
    return h.join("");
  }
  function deviceHeader(d) {
    var fp = d.rounds.length ? d.rounds[d.rounds.length - 1].fingerprint : {};
    var os = fp.os_version ? fp.os_version + (fp.os_build ? " (" + fp.os_build + ")" : "") : "—";
    var changed = d.fingerprint_changes.length ? " · changed at round " + d.fingerprint_changes.map(function (c) { return c.round_key + " (" + c.field + " " + c.before + " → " + c.after + ")"; }).join(", ") : "";
    var kv = ['<dl class="kv"><dt>device id</dt><dd>' + esc(d.device_id) + (d.alias ? " · alias " + esc(d.alias) : "") + "</dd><dt>OS</dt><dd>" + esc(os + changed) + "</dd>"];
    if (d.platform === "ios" && fp.network_state) kv.push("<dt>network</dt><dd>" + esc(fp.network_state) + " (recorded on each round, never refused)</dd>");
    if (fp.thermal_state) kv.push("<dt>thermal</dt><dd>" + esc(fp.thermal_state) + "</dd>");
    var ov = Object.keys(d.overrides || {});
    if (ov.length) kv.push("<dt>protocol overrides</dt><dd>" + esc(ov.map(function (k) { return k + " = " + d.overrides[k]; }).join(", ")) + "</dd>");
    kv.push("</dl>");
    return '<p class="back"><a href="#/">← all devices</a></p><h1>' + esc(d.model) + ' <span class="platform ' + esc(d.platform) + '">' + esc(d.platform) + "</span> " + badges(d.badges) + "</h1>" + kv.join("") +
      '<p class="headline">' + esc(d.headline) + "</p>" + (d.note ? '<p class="note">' + esc(d.note) + "</p>" : "");
  }
  function deviceView(d) {
    var h = [deviceHeader(d), legend(), '<div class="families">'];
    d.families.forEach(function (f) {
      h.push('<section class="family"><h2>' + esc(f.title) + '</h2><div class="charts">');
      f.series.forEach(function (s) {
        h.push('<article class="chart"><h3>' + esc(s.title) + ' <span class="muted">' + esc(s.unit) + "</span></h3>" + chartSvg(d, s, false) +
          '<p class="sentence ' + esc(s.status || "") + '">' + esc(s.sentence) + ' <a class="open" href="#/device/' + esc(d.device_id) + "/" + encodeURIComponent(s.key) + '">open</a></p></article>');
      });
      h.push("</div></section>");
    });
    h.push("</div><h2>Rounds</h2><table><tr><th>date</th><th>label</th><th>status</th><th>badges</th><th>commit</th><th>OS</th><th>thermal / battery / network</th><th>gate</th><th>artifacts</th></tr>");
    d.rounds.forEach(function (r) {
      var fp = r.fingerprint, g = r.gate_summary;
      h.push('<tr class="' + esc(r.status) + '"><td>' + esc(r.date) + (r.session ? ' <span class="muted">' + r.session.index + "/" + r.session.size + "</span>" : "") + "</td><td>" + esc(r.label) + "</td><td>" + esc(r.status) + (r.failure_reason ? " — " + esc(r.failure_reason) : "") + "</td><td>" + badges(r.badges) + "</td><td>" + esc(r.commit) + (r.dirty ? "*" : "") + "</td><td>" + esc(fp.os_version || "") + "</td><td>" +
        esc((fp.thermal_state || (fp.battery_temp_c != null ? fp.battery_temp_c + " °C" : "—")) + " / " + (fp.battery_level != null ? (fp.battery_level <= 1 ? Math.round(fp.battery_level * 100) : fp.battery_level) + " %" : "—") + " / " + (fp.network_state || "—")) + "</td><td>" +
        (g.above_limit ? g.above_limit + " above limit" : g.within_limit ? "within limits" : g.inactive ? "inactive" : "—") + "</td><td>" + r.artifacts.map(function (a) { return '<span title="' + esc(a.path) + '">' + esc(a.kind) + "</span>"; }).join(", ") + "</td></tr>");
    });
    h.push("</table>");
    var changes = d.detections.filter(function (x) { return x.status !== "PRELIMINARY"; });
    h.push("<h2>What changed</h2>");
    if (!changes.length) h.push('<p class="muted">Nothing beyond first hints on this device.</p>');
    else { h.push('<ul class="list">'); changes.forEach(function (x) { var s = seriesOf(d, x.metric); h.push('<li class="' + esc(x.status) + '"><a href="#/device/' + esc(d.device_id) + "/" + encodeURIComponent(x.metric) + '">' + esc(s ? s.title : x.metric) + "</a>: " + esc(STATUS_WORD[x.status]) + " " + esc(pct(x.delta_pct)) + (x.label_at_onset ? " in " + esc(x.label_at_onset) : "") + " (round " + esc(x.onset_round_key) + ")</li>"); }); h.push("</ul>"); }
    return h.join("");
  }
  function metricView(d, s) {
    var note = DATA.catalogue[s.metric] || (s.family === "section" ? DATA.catalogue["section.*"] : null);
    var h = ['<p class="back"><a href="#/device/' + esc(d.device_id) + '">← ' + esc(d.model) + "</a></p><h1>" + esc(s.title) + ' <span class="muted">' + esc(s.unit) + "</span></h1>",
      '<p class="headline ' + esc(s.status || "") + '">' + esc(s.sentence) + "</p>", legend(), '<article class="chart">' + chartSvg(d, s, true) + "</article>"];
    h.push("<h2>Per-round distributions</h2><table><tr><th>round</th><th>label</th><th>median</th><th>IQR</th><th>gate</th></tr>");
    d.rounds.forEach(function (r, i) {
      var p = s.points[i]; if (!p) return;
      var gate = null; s.gate.forEach(function (g) { if (g.round_index === i) gate = g; });
      h.push("<tr><td>" + esc(r.date) + "</td><td>" + esc(r.label) + '</td><td class="num">' + esc(fmt(p[0], s.unit)) + '</td><td class="num">' + esc(fmt(p[1], "") + " – " + fmt(p[2], s.unit)) + "</td><td>" + (gate ? esc(gate.state.replace("_", " ") + (gate.observed_pct != null ? " " + pct(gate.observed_pct) + " vs limit " + (gate.limit_pct != null ? gate.limit_pct + " %" : "") : "") + (gate.reason ? " (" + gate.reason + ")" : "")) : "—") + "</td></tr>");
    });
    h.push("</table><h2>Detections</h2>");
    if (!s.detections.length) h.push('<p class="muted">No detection on this series.</p>');
    else {
      h.push("<table><tr><th>status</th><th>onset</th><th>label at onset</th><th>step</th><th>step-fit score</th><th>Mann-Whitney p</th></tr>");
      s.detections.forEach(function (x) { h.push("<tr><td>" + esc(STATUS_WORD[x.status]) + "</td><td>" + esc(x.onset_round_key) + "</td><td>" + esc(x.label_at_onset || "—") + '</td><td class="num">' + esc(pct(x.delta_pct)) + '</td><td class="num">' + esc(x.stepfit_score == null ? "—" : x.stepfit_score.toFixed(2)) + '</td><td class="num">' + esc(x.mwu_p == null ? "—" : x.mwu_p.toExponential(2)) + "</td></tr>"); });
      h.push("</table>");
    }
    if (note) {
      var pn = note[d.platform] || {};
      h.push("<h2>About this metric</h2><dl class=\"kv\"><dt>unit</dt><dd>" + esc(pn.unit || s.unit) + "</dd><dt>source</dt><dd>" + esc(pn.source || "") + "</dd>" + (note.note ? "<dt>platforms</dt><dd>" + esc(note.note) + "</dd>" : "") + (note.android && note.ios ? "" : "<dt>availability</dt><dd>" + esc(d.platform) + " only</dd>") + "</dl>");
    }
    return h.join("");
  }

  /* ---------- router ---------- */
  function render() {
    var hash = location.hash || "#/";
    var parts = hash.replace(/^#\/?/, "").split("/");
    var html, crumb = '<a href="#/">devices</a>';
    if (parts[0] === "device" && parts[1]) {
      var d = device(parts[1]);
      if (!d) { html = '<p class="muted">unknown device</p>'; }
      else if (parts.length > 2) {
        var key = decodeURIComponent(parts.slice(2).join("/"));
        var s = seriesOf(d, key);
        html = s ? metricView(d, s) : '<p class="muted">unknown series</p>';
        crumb += '<span>/</span><a href="#/device/' + esc(d.device_id) + '">' + esc(d.model) + "</a><span>/</span>" + esc(s ? s.title : key);
      } else { html = deviceView(d); crumb += "<span>/</span>" + esc(d.model); }
    } else html = gridView();
    view.innerHTML = html;
    crumbs.innerHTML = crumb;
    window.scrollTo(0, 0);
    tooltip.hidden = true;
  }
  view.addEventListener("mousemove", function (e) {
    var t = e.target;
    if (!(t instanceof Element) || !t.classList.contains("hit")) { tooltip.hidden = true; return; }
    tooltip.textContent = t.getAttribute("data-tip");
    tooltip.style.whiteSpace = "pre-line";
    tooltip.hidden = false;
    var x = e.clientX + 14, y = e.clientY + 14;
    if (x + 330 > window.innerWidth) x = e.clientX - 330;
    if (y + 120 > window.innerHeight) y = e.clientY - 120;
    tooltip.style.left = x + "px"; tooltip.style.top = y + "px";
  });
  view.addEventListener("mouseleave", function () { tooltip.hidden = true; });
  window.addEventListener("hashchange", render);
  render();
})();
