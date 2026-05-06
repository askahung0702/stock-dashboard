#!/usr/bin/env python3
"""
Early Breakout Screener
-----------------------
1. Use historical snapshots to find the top gainers in a study window.
2. Find each winner's earliest identifiable launch point inside the window.
3. Summarize the common launch conditions.
4. Compare the current condition set with the previous review.
5. Apply the learned conditions to the latest snapshot and rank current candidates.
6. Inject a summary panel into the main dashboard HTML.
"""

import argparse
import csv
import json
import math
import re
import sys
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SNAPSHOTS = ROOT / "daily_snapshots"
HISTORY = ROOT / "history"
WEB_ROOT = ROOT / "web"
WEB_REPORTS = WEB_ROOT / "early_breakout"
STATIC_DASHBOARD_DIR = ROOT / "static" / "dashboards"
HISTORY.mkdir(exist_ok=True)
WEB_ROOT.mkdir(exist_ok=True)
WEB_REPORTS.mkdir(parents=True, exist_ok=True)

DEFAULT_ANALYSIS_TOP = 20
DEFAULT_DISPLAY_TOP = 50
DEFAULT_STUDY_LOOKBACK = 30
DEFAULT_LAUNCH_LOOKAHEAD = 10
DEFAULT_MIN_FORWARD_RETURN_PCT = 18.0
DEFAULT_MIN_SELECTION_GATE = 60.0
DEFAULT_MIN_SCREEN_SCORE = 72.0
DEFAULT_FOCUS_BUY_POINT = 75.0
DEFAULT_STRICT_MIN_REVENUE_YOY = 5.0
DEFAULT_STRICT_RETURN20_MIN = 3.0
DEFAULT_STRICT_RETURN20_MAX = 30.0
DEFAULT_STRICT_DRAWDOWN_MIN = -25.0
DEFAULT_STRICT_DRAWDOWN_MAX = -2.0
DEFAULT_CONTINUATION_RETURN20_MIN = 3.0
DEFAULT_CONTINUATION_RETURN20_MAX = 25.0
DEFAULT_CONTINUATION_RETURN60_MIN = 10.0
DEFAULT_CONTINUATION_DRAWDOWN_MIN = -6.0
DEFAULT_CONTINUATION_DRAWDOWN_MAX = 1.0
DEFAULT_CONTINUATION_NEAR_HIGH = -4.0
DEFAULT_CONTINUATION_STRONG_RETURN60 = 20.0

VARIANT_2060 = "ma2060"
VARIANT_1854 = "ma1854"
VARIANT_LABELS = {
    VARIANT_2060: "MA20/60",
    VARIANT_1854: "MA18/54",
}

DASHBOARD_PANEL_START = "<!-- EARLY_BREAKOUT_PANEL_START -->"
DASHBOARD_PANEL_END = "<!-- EARLY_BREAKOUT_PANEL_END -->"
DASHBOARD_INSERT_MARKER = "<section class=\"panel\"><div class=\"section-head\"><div><h2>收盤後高勝率候選</h2>"

CONDITIONS = [
    {
        "key": "revenue_latest_pos",
        "label": "最新月營收年增 > 0",
        "gate": "latest_revenue_yoy_pct > 0",
    },
    {
        "key": "revenue_persistent",
        "label": "近3月營收維持年增，且正成長月數 >= 2",
        "gate": "avg_3m_revenue_yoy_pct > 0 AND positive_revenue_months >= 2",
    },
    {
        "key": "selection_qualified",
        "label": "流動性/財報品質達基本門檻",
        "gate": "selection_qualified = true OR (liquidity_score >= 4 AND financial_quality_score >= 8)",
    },
    {
        "key": "above_ma20",
        "label": "股價站上 MA20",
        "gate": "current_price > ma20",
    },
    {
        "key": "ma20_gt_ma60",
        "label": "MA20 在 MA60 之上",
        "gate": "ma20 > ma60",
    },
    {
        "key": "ma60_gt_ma120",
        "label": "MA60 在 MA120 之上",
        "gate": "ma60 > ma120",
    },
    {
        "key": "return20_pos",
        "label": "20日報酬為正",
        "gate": "return_20d_pct > 0",
    },
    {
        "key": "return60_pos",
        "label": "60日報酬為正",
        "gate": "return_60d_pct > 0",
    },
    {
        "key": "healthy_drawdown",
        "label": "距 60 日高點回檔介於 -12% 到 +1%",
        "gate": "-12 <= drawdown_from_high60_pct <= 1",
    },
    {
        "key": "healthy_volume",
        "label": "量比介於 0.8 到 2.5",
        "gate": "0.8 <= volume_ratio <= 2.5",
    },
    {
        "key": "flow_support",
        "label": "法人/主力籌碼未明顯轉弱",
        "gate": "five_day_institutional_net_ratio_pct > -2 OR broker_net_ratio_pct > 0",
    },
    {
        "key": "eps_support",
        "label": "EPS/獲利有支撐",
        "gate": "positive_eps_quarters >= 2 OR latest_quarter_eps_yoy_pct > 0",
    },
    {
        "key": "valuation_not_extreme",
        "label": "估值不極端",
        "gate": "(trailing_pe <= peer_average_pe * 1.15) OR (0 < trailing_pe <= 35)",
    },
    {
        "key": "not_overheated",
        "label": "未過熱",
        "gate": "return_20d_pct <= 35 AND volume_ratio <= 3.5 AND drawdown_from_high60_pct <= 1 AND rsi14 < 78",
    },
]


HTML_TMPL = """<!DOCTYPE html>
<html lang="zh-TW">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>早期起漲篩選器 {screen_date}</title>
<style>
  body {{ font-family: "Noto Sans TC", sans-serif; background:#0f172a; color:#e2e8f0; margin:0; padding:20px }}
  h1 {{ color:#38bdf8; font-size:1.5rem; margin-bottom:6px }}
  .sub {{ color:#94a3b8; font-size:.88rem; margin-bottom:18px; line-height:1.7 }}
  .panel {{ background:#111827; border:1px solid #1f2937; border-radius:10px; padding:14px 16px; margin-bottom:18px }}
  .panel b {{ color:#7dd3fc }}
  .metric-grid {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:12px; margin-bottom:18px }}
  .metric {{ background:#111827; border:1px solid #1f2937; border-radius:10px; padding:12px 14px }}
  .metric .label {{ color:#94a3b8; font-size:.78rem }}
  .metric .value {{ color:#f8fafc; font-size:1.25rem; font-weight:700; margin-top:4px }}
  table {{ border-collapse:collapse; width:100%; font-size:.82rem }}
  th {{ background:#1e3a5f; color:#bfdbfe; padding:8px 6px; text-align:left; position:sticky; top:0 }}
  tr:nth-child(even) {{ background:#111827 }}
  tr:hover {{ background:#1e293b }}
  td {{ padding:6px 6px; border-bottom:1px solid #1f2937; white-space:nowrap }}
  .score-hi {{ color:#34d399; font-weight:700 }}
  .score-mid {{ color:#fbbf24; font-weight:700 }}
  .score-lo {{ color:#f87171; font-weight:700 }}
  .grade {{ display:inline-block; min-width:24px; text-align:center; padding:1px 6px; border-radius:8px; font-size:.74rem; font-weight:700 }}
  .grade-a {{ background:#064e3b; color:#a7f3d0 }}
  .grade-b {{ background:#78350f; color:#fde68a }}
  .grade-c {{ background:#334155; color:#cbd5e1 }}
  .tag {{ display:inline-block; padding:1px 6px; border-radius:9px; font-size:.72rem; margin:1px }}
  .tag-theme {{ background:#1d4ed8; color:#dbeafe }}
  .tag-structure {{ background:#6d28d9; color:#ede9fe }}
  .tag-signal {{ background:#374151; color:#e5e7eb }}
  .small {{ font-size:.74rem; color:#94a3b8; white-space:normal; max-width:360px }}
  .delta-up {{ color:#34d399; font-weight:700 }}
  .delta-down {{ color:#f59e0b; font-weight:700 }}
</style>
</head>
<body>
<h1>早期起漲篩選器 — {screen_date}</h1>
<p class="sub">
研究區間 {study_start} → {study_end}；以區間漲幅前 {analysis_top} 名為樣本。<br>
起漲點定義：在後續 {launch_lookahead} 個快照內，最高收盤漲幅至少 {min_forward_return_pct:.1f}%；
同時符合至少 6 個早期條件，且不能處於過熱狀態。
</p>

<div class="metric-grid">
  <div class="metric"><div class="label">研究樣本</div><div class="value">{analysis_top}</div></div>
  <div class="metric"><div class="label">已辨識起漲點</div><div class="value">{identified_count}</div></div>
  <div class="metric"><div class="label">窗起點命中</div><div class="value">{window_start_count}</div></div>
  <div class="metric"><div class="label">回退代理點</div><div class="value">{fallback_count}</div></div>
  <div class="metric"><div class="label">目前候選數</div><div class="value">{candidate_count}</div></div>
  <div class="metric"><div class="label">Focus 候選</div><div class="value">{focus_count}</div></div>
  <div class="metric"><div class="label">寬鬆度判斷</div><div class="value">{breadth_label}</div></div>
  <div class="metric"><div class="label">寬版學習</div><div class="value">{broad_candidate_count}</div></div>
</div>

<div class="panel">
  <b>這次學到的核心條件</b><br>
  {core_lines}
</div>

<div class="panel">
  <b>次要加分條件</b><br>
  {support_lines}
</div>

<div class="panel">
  <b>和前一次相比</b><br>
  {comparison_lines}
</div>

<div class="panel">
  <b>月檢視判讀</b><br>
  {review_lines}
</div>

<table>
<thead>
<tr>
  <th>#</th>
  <th>Grade</th>
  <th>代碼</th>
  <th>名稱</th>
  <th>型態</th>
  <th>價格</th>
  <th>Screen</th>
  <th>策略分</th>
  <th>買點分</th>
  <th>3M營收</th>
  <th>20日%</th>
  <th>60日%</th>
  <th>量比</th>
  <th>結構</th>
  <th>題材</th>
  <th>訊號</th>
  <th>關鍵理由</th>
</tr>
</thead>
<tbody>
{rows}
</tbody>
</table>
</body>
</html>
"""


def fv(row, col, default=0.0):
    try:
        value = row.get(col, "")
        return float(value) if value not in ("", None) else default
    except (ValueError, TypeError):
        return default


def iv(row, col, default=0):
    try:
        value = row.get(col, "")
        return int(float(value)) if value not in ("", None) else default
    except (ValueError, TypeError):
        return default


def sv(row, col, default=""):
    return row.get(col, default) or default


def pct(a, b):
    if a in (None, 0):
        return 0.0
    return (b / a - 1.0) * 100.0


def safe_html(text):
    value = "" if text is None else str(text)
    return (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )


def selection_score_of(row):
    score = fv(row, "selection_score", 0.0)
    if score > 0:
        return score
    return fv(row, "score", 0.0)


def buy_point_of(row):
    score = fv(row, "buy_point_score", 0.0)
    if score > 0:
        return score
    return selection_score_of(row)


def score_class(score):
    if score >= 82:
        return "score-hi"
    if score >= 75:
        return "score-mid"
    return "score-lo"


def grade_class(grade):
    return {
        "A": "grade-a",
        "B": "grade-b",
    }.get(grade, "grade-c")


def list_history_files():
    return sorted(
        p for p in HISTORY.glob("stock_candidates_*.csv")
        if p.stem[-8:].isdigit()
    )


def load_csv(path):
    rows = []
    with open(path, encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            row["_source_file"] = path.name
            rows.append(row)
    return rows


def load_history_snapshots():
    dates = []
    rows_by_date = {}
    series_by_code = {}

    for path in list_history_files():
        date_str = path.stem[-8:]
        rows = load_csv(path)
        dates.append(date_str)
        row_map = {}
        for row in rows:
            row["_date"] = date_str
            code = sv(row, "code")
            row_map[code] = row
            series_by_code.setdefault(code, []).append(row)
        rows_by_date[date_str] = row_map

    dates.sort()
    for code in series_by_code:
        series_by_code[code].sort(key=lambda item: sv(item, "_date"))
    return dates, rows_by_date, series_by_code


def list_daily_snapshot_files():
    return sorted(
        p for p in SNAPSHOTS.glob("stock_candidates_*.csv")
        if p.stem[-8:].isdigit()
    )


def load_daily_price_series():
    rows_by_date = {}
    series_by_code = {}
    for path in list_daily_snapshot_files():
        date_str = path.stem[-8:]
        rows = load_csv(path)
        row_map = {}
        for row in rows:
            row["_date"] = date_str
            code = sv(row, "code")
            row_map[code] = row
            series_by_code.setdefault(code, []).append(row)
        rows_by_date[date_str] = row_map
    for code in series_by_code:
        series_by_code[code].sort(key=lambda item: sv(item, "_date"))
    return rows_by_date, series_by_code


def hydrate_alt_metrics(row, series_by_code):
    if fv(row, "ma18", 0.0) > 0 and fv(row, "ma54", 0.0) > 0:
        return row
    code = sv(row, "code")
    date_str = sv(row, "_date")
    series = [item for item in series_by_code.get(code, []) if sv(item, "_date") <= date_str]
    closes = [fv(item, "current_price", 0.0) for item in series if fv(item, "current_price", 0.0) > 0]
    current_price = fv(row, "current_price", 0.0)
    if not closes or current_price <= 0:
        return row

    def avg_last(values, span):
        if len(values) < span:
            return 0.0
        return sum(values[-span:]) / span

    def return_from_days_ago(values, span, current):
        if len(values) <= span:
            return 0.0
        base = values[-(span + 1)]
        if base <= 0:
            return 0.0
        return (current - base) * 100.0 / base

    row["ma18"] = f"{avg_last(closes, 18):.6f}"
    row["ma54"] = f"{avg_last(closes, 54):.6f}"
    row["return_18d_pct"] = f"{return_from_days_ago(closes, 18, current_price):.6f}"
    row["return_54d_pct"] = f"{return_from_days_ago(closes, 54, current_price):.6f}"
    return row


def resolve_default_study_window(dates):
    if not dates:
        return None, None
    end_idx = len(dates) - 1
    start_idx = max(0, end_idx - (DEFAULT_STUDY_LOOKBACK - 1))
    return dates[start_idx], dates[end_idx]


def condition_map(row):
    trailing_eps = fv(row, "trailing_eps", 0.0)
    trailing_pe = fv(row, "trailing_pe", 0.0)
    peer_pe = fv(row, "peer_average_pe", 0.0)
    return {
        "revenue_latest_pos": fv(row, "latest_revenue_yoy_pct", 0.0) > 0,
        "revenue_persistent": fv(row, "avg_3m_revenue_yoy_pct", 0.0) > 0
        and iv(row, "positive_revenue_months", 0) >= 2,
        "selection_qualified": sv(row, "selection_qualified", "").strip().upper() == "Y"
        or (
            fv(row, "liquidity_score", 0.0) >= 4
            and fv(row, "financial_quality_score", 0.0) >= 8
        ),
        "above_ma20": fv(row, "current_price", 0.0) > fv(row, "ma20", 0.0) > 0,
        "ma20_gt_ma60": fv(row, "ma20", 0.0) > fv(row, "ma60", 0.0) > 0,
        "ma60_gt_ma120": fv(row, "ma60", 0.0) > fv(row, "ma120", 0.0) > 0,
        "return20_pos": fv(row, "return_20d_pct", 0.0) > 0,
        "return60_pos": fv(row, "return_60d_pct", 0.0) > 0,
        "healthy_drawdown": -12.0 <= fv(row, "drawdown_from_high60_pct", -999.0) <= 1.0,
        "healthy_volume": 0.8 <= fv(row, "volume_ratio", 0.0) <= 2.5,
        "flow_support": fv(row, "five_day_institutional_net_ratio_pct", 0.0) > -2
        or fv(row, "broker_net_ratio_pct", 0.0) > 0,
        "eps_support": iv(row, "positive_eps_quarters", 0) >= 2
        or fv(row, "latest_quarter_eps_yoy_pct", 0.0) > 0,
        "valuation_not_extreme": (
            trailing_eps > 0
            and peer_pe > 0
            and trailing_pe > 0
            and trailing_pe <= peer_pe * 1.15
        ) or (0 < trailing_pe <= 35),
        "not_overheated": fv(row, "return_20d_pct", 0.0) <= 35
        and fv(row, "volume_ratio", 0.0) <= 3.5
        and fv(row, "drawdown_from_high60_pct", 0.0) <= 1.0
        and fv(row, "rsi14", 0.0) < 78,
    }


def matched_condition_keys(row, include_guard=False):
    cmap = condition_map(row)
    keys = []
    for condition in CONDITIONS:
        if not include_guard and condition["key"] == "not_overheated":
            continue
        if cmap.get(condition["key"]):
            keys.append(condition["key"])
    return keys


def label_for_key(key):
    for condition in CONDITIONS:
        if condition["key"] == key:
            return condition["label"]
    return key


def gate_for_key(key):
    for condition in CONDITIONS:
        if condition["key"] == key:
            return condition["gate"]
    return ""


def strict_breakout_ready(row):
    return (
        fv(row, "avg_3m_revenue_yoy_pct", 0.0) > DEFAULT_STRICT_MIN_REVENUE_YOY
        and iv(row, "positive_revenue_months", 0) >= 2
        and fv(row, "ma20", 0.0) > fv(row, "ma60", 0.0) > 0
        and DEFAULT_STRICT_RETURN20_MIN <= fv(row, "return_20d_pct", 0.0) <= DEFAULT_STRICT_RETURN20_MAX
        and fv(row, "return_60d_pct", 0.0) > 0
        and DEFAULT_STRICT_DRAWDOWN_MIN
        <= fv(row, "drawdown_from_high60_pct", -999.0)
        <= DEFAULT_STRICT_DRAWDOWN_MAX
        and fv(row, "broker_net_ratio_pct", 0.0) > 0
    )


def strong_continuation_ready(row):
    return (
        fv(row, "avg_3m_revenue_yoy_pct", 0.0) > DEFAULT_STRICT_MIN_REVENUE_YOY
        and iv(row, "positive_revenue_months", 0) >= 2
        and fv(row, "current_price", 0.0) > fv(row, "ma20", 0.0) > fv(row, "ma60", 0.0) > 0
        and DEFAULT_CONTINUATION_RETURN20_MIN
        <= fv(row, "return_20d_pct", 0.0)
        <= DEFAULT_CONTINUATION_RETURN20_MAX
        and fv(row, "return_60d_pct", 0.0) > DEFAULT_CONTINUATION_RETURN60_MIN
        and DEFAULT_CONTINUATION_DRAWDOWN_MIN
        <= fv(row, "drawdown_from_high60_pct", -999.0)
        <= DEFAULT_CONTINUATION_DRAWDOWN_MAX
        and fv(row, "broker_net_ratio_pct", 0.0) > 0
    )


def strict_breakout_ready_1854(row):
    return (
        fv(row, "avg_3m_revenue_yoy_pct", 0.0) > DEFAULT_STRICT_MIN_REVENUE_YOY
        and iv(row, "positive_revenue_months", 0) >= 2
        and fv(row, "ma18", 0.0) > fv(row, "ma54", 0.0) > 0
        and DEFAULT_STRICT_RETURN20_MIN <= fv(row, "return_18d_pct", 0.0) <= DEFAULT_STRICT_RETURN20_MAX
        and fv(row, "return_54d_pct", 0.0) > 0
        and DEFAULT_STRICT_DRAWDOWN_MIN
        <= fv(row, "drawdown_from_high60_pct", -999.0)
        <= DEFAULT_STRICT_DRAWDOWN_MAX
        and fv(row, "broker_net_ratio_pct", 0.0) > 0
    )


def strong_continuation_ready_1854(row):
    return (
        fv(row, "avg_3m_revenue_yoy_pct", 0.0) > DEFAULT_STRICT_MIN_REVENUE_YOY
        and iv(row, "positive_revenue_months", 0) >= 2
        and fv(row, "current_price", 0.0) > fv(row, "ma18", 0.0) > fv(row, "ma54", 0.0) > 0
        and DEFAULT_CONTINUATION_RETURN20_MIN
        <= fv(row, "return_18d_pct", 0.0)
        <= DEFAULT_CONTINUATION_RETURN20_MAX
        and fv(row, "return_54d_pct", 0.0) > DEFAULT_CONTINUATION_RETURN60_MIN
        and DEFAULT_CONTINUATION_DRAWDOWN_MIN
        <= fv(row, "drawdown_from_high60_pct", -999.0)
        <= DEFAULT_CONTINUATION_DRAWDOWN_MAX
        and fv(row, "broker_net_ratio_pct", 0.0) > 0
    )


def screen_style_of(row):
    drawdown = fv(row, "drawdown_from_high60_pct", 0.0)
    return60 = fv(row, "return_60d_pct", 0.0)
    signal_type = sv(row, "signal_type")
    if strong_continuation_ready(row) and (
        drawdown > DEFAULT_CONTINUATION_NEAR_HIGH
        or return60 >= DEFAULT_CONTINUATION_STRONG_RETURN60
        or signal_type == "5-10日波段"
    ):
        return "continuation", "強勢續攻"
    return "early", "早期起漲"


def screen_style_of_1854(row):
    drawdown = fv(row, "drawdown_from_high60_pct", 0.0)
    return54 = fv(row, "return_54d_pct", 0.0)
    signal_type = sv(row, "signal_type")
    if strong_continuation_ready_1854(row) and (
        drawdown > DEFAULT_CONTINUATION_NEAR_HIGH
        or return54 >= DEFAULT_CONTINUATION_STRONG_RETURN60
        or signal_type == "5-10日波段"
    ):
        return "continuation", "強勢續攻"
    return "early", "早期起漲"


def summarize_condition_hits(rows):
    sample_size = len(rows)
    summary = []
    for condition in CONDITIONS:
        key = condition["key"]
        hit_count = sum(1 for row in rows if condition_map(row).get(key))
        hit_rate = round((hit_count * 100.0 / sample_size), 2) if sample_size else 0.0
        summary.append(
            {
                "key": key,
                "label": condition["label"],
                "gate": condition["gate"],
                "sample_size": sample_size,
                "hit_count": hit_count,
                "hit_rate_pct": hit_rate,
            }
        )
    summary.sort(key=lambda item: (-item["hit_rate_pct"], item["key"]))
    return summary


def pick_summary_labels(condition_hits, min_hit_rate=70.0, limit=4, exclude_keys=None):
    exclude = set(exclude_keys or [])
    labels = [
        f"{item['label']} {item['hit_rate_pct']:.1f}%"
        for item in condition_hits
        if item["key"] not in exclude and item["hit_rate_pct"] >= min_hit_rate
    ]
    if labels:
        return labels[:limit]
    return [
        f"{item['label']} {item['hit_rate_pct']:.1f}%"
        for item in condition_hits
        if item["key"] not in exclude
    ][:limit]


def classify_screen_breadth(candidate_count, universe_count, identified_count):
    ratio_pct = round(candidate_count * 100.0 / universe_count, 2) if universe_count else 0.0
    multiplier = round(candidate_count / max(identified_count, 1), 2)
    if ratio_pct >= 9.0 or multiplier >= 20.0:
        label = "偏寬"
        note = (
            f"目前名單 {candidate_count} 檔，占全市場 {ratio_pct:.1f}% ，約為 identified 樣本的 {multiplier:.1f} 倍，"
            "比較像品質趨勢籃子，不夠像剛起漲名單。"
        )
    elif ratio_pct >= 5.0 or multiplier >= 12.0:
        label = "中等偏寬"
        note = (
            f"目前名單 {candidate_count} 檔，占全市場 {ratio_pct:.1f}% ，約為 identified 樣本的 {multiplier:.1f} 倍，"
            "可以再收斂一些，讓候選更接近起漲初段。"
        )
    elif candidate_count <= 20 and ratio_pct <= 1.5:
        label = "偏窄"
        note = (
            f"目前名單 {candidate_count} 檔，占全市場 {ratio_pct:.1f}% ，篩選很尖，"
            "但要留意是否過度過濾而漏掉新啟動標的。"
        )
    else:
        label = "較平衡"
        note = (
            f"目前名單 {candidate_count} 檔，占全市場 {ratio_pct:.1f}% ，約為 identified 樣本的 {multiplier:.1f} 倍，"
            "整體寬鬆度在可操作範圍。"
        )
    return label, note, ratio_pct, multiplier


def build_review_summary(
    study_start,
    study_end,
    screen_date,
    analysis_top,
    launch_rows,
    condition_stats,
    screened,
    screened_1854,
    combined_screened,
    broad_screened,
    current_rows,
    previous_meta,
):
    identified_rows = [row for row in launch_rows if row["launch_status"] == "identified"]
    non_fallback_rows = [row for row in launch_rows if row["launch_status"] != "fallback"]
    identified_condition_hits = summarize_condition_hits(
        [row["_launch_row"] for row in identified_rows if row.get("_launch_row")]
    )
    broad_label, broad_note, candidate_ratio_pct, identified_multiplier = classify_screen_breadth(
        len(combined_screened), len(current_rows), len(identified_rows)
    )

    strict_latest_count = len(screened)
    strict_latest_count_1854 = len(screened_1854)
    strict_latest_count_union = len(combined_screened)
    broad_candidate_count = len(broad_screened)
    early_candidate_count = sum(1 for row in screened if row.get("screen_style") == "early")
    continuation_candidate_count = sum(
        1 for row in screened if row.get("screen_style") == "continuation"
    )
    early_candidate_count_1854 = sum(
        1 for row in screened_1854 if row.get("screen_style") == "early"
    )
    continuation_candidate_count_1854 = sum(
        1 for row in screened_1854 if row.get("screen_style") == "continuation"
    )
    early_focus_count = sum(
        1 for row in screened if row.get("screen_style") == "early" and row["focus_candidate"] == "Y"
    )
    continuation_focus_count = sum(
        1
        for row in screened
        if row.get("screen_style") == "continuation" and row["focus_candidate"] == "Y"
    )
    early_focus_count_1854 = sum(
        1 for row in screened_1854 if row.get("screen_style") == "early" and row["focus_candidate"] == "Y"
    )
    continuation_focus_count_1854 = sum(
        1
        for row in screened_1854
        if row.get("screen_style") == "continuation" and row["focus_candidate"] == "Y"
    )
    strict_non_fallback_hit = sum(
        1 for row in non_fallback_rows if strict_breakout_ready(row.get("_launch_row", {}))
    )
    strict_identified_hit = sum(
        1 for row in identified_rows if strict_breakout_ready(row.get("_launch_row", {}))
    )
    strict_non_fallback_hit_1854 = sum(
        1 for row in non_fallback_rows if strict_breakout_ready_1854(row.get("_launch_row", {}))
    )
    strict_identified_hit_1854 = sum(
        1 for row in identified_rows if strict_breakout_ready_1854(row.get("_launch_row", {}))
    )

    identified_feature_lines = pick_summary_labels(
        identified_condition_hits,
        min_hit_rate=75.0,
        exclude_keys={"selection_qualified"},
    )
    common_feature_lines = pick_summary_labels(
        condition_stats,
        min_hit_rate=70.0,
        exclude_keys={"selection_qualified"},
    )
    strict_rule_lines = [
        f"早期起漲：近3月平均營收年增 > {DEFAULT_STRICT_MIN_REVENUE_YOY:.0f}% 且正成長月 >= 2",
        f"早期起漲：MA20 > MA60，20日報酬介於 {DEFAULT_STRICT_RETURN20_MIN:.0f}% 到 {DEFAULT_STRICT_RETURN20_MAX:.0f}%",
        f"早期起漲：距60日高點回檔介於 {DEFAULT_STRICT_DRAWDOWN_MIN:.0f}% 到 {DEFAULT_STRICT_DRAWDOWN_MAX:.0f}%，broker 主力買超 > 0",
        f"強勢續攻：股價 > MA20 > MA60，20日報酬介於 {DEFAULT_CONTINUATION_RETURN20_MIN:.0f}% 到 {DEFAULT_CONTINUATION_RETURN20_MAX:.0f}%",
        f"強勢續攻：60日報酬 > {DEFAULT_CONTINUATION_RETURN60_MIN:.0f}% ，距60日高點介於 {DEFAULT_CONTINUATION_DRAWDOWN_MIN:.0f}% 到 {DEFAULT_CONTINUATION_DRAWDOWN_MAX:.0f}%",
    ]
    strict_rule_lines_1854 = [
        f"早期起漲：近3月平均營收年增 > {DEFAULT_STRICT_MIN_REVENUE_YOY:.0f}% 且正成長月 >= 2",
        f"早期起漲：MA18 > MA54，18日報酬介於 {DEFAULT_STRICT_RETURN20_MIN:.0f}% 到 {DEFAULT_STRICT_RETURN20_MAX:.0f}%",
        f"強勢續攻：股價 > MA18 > MA54，18日報酬介於 {DEFAULT_CONTINUATION_RETURN20_MIN:.0f}% 到 {DEFAULT_CONTINUATION_RETURN20_MAX:.0f}%",
        f"強勢續攻：54日報酬 > {DEFAULT_CONTINUATION_RETURN60_MIN:.0f}% ，距60日高點介於 {DEFAULT_CONTINUATION_DRAWDOWN_MIN:.0f}% 到 {DEFAULT_CONTINUATION_DRAWDOWN_MAX:.0f}%",
    ]
    previous_window = (
        f"{previous_meta.get('study_start', '')} → {previous_meta.get('study_end', '')}"
        if previous_meta
        else ""
    )
    review_lines = [
        f"研究區間 {study_start} → {study_end} 的前 {analysis_top} 大漲股中，identified {len(identified_rows)} 檔、window_start {sum(1 for row in launch_rows if row['launch_status'] == 'window_start')} 檔、fallback {sum(1 for row in launch_rows if row['launch_status'] == 'fallback')} 檔。",
        f"真正 identified 樣本最穩的共同特徵：{'；'.join(identified_feature_lines) if identified_feature_lines else '目前樣本不足。'}",
        f"寬版學習名單 {broad_candidate_count} 檔；MA20/60 strict 主名單 {len(screened)} 檔，其中早期起漲 {early_candidate_count} 檔、強勢續攻 {continuation_candidate_count} 檔；MA18/54 strict 主名單 {len(screened_1854)} 檔，其中早期起漲 {early_candidate_count_1854} 檔、強勢續攻 {continuation_candidate_count_1854} 檔；合併前端主名單 {strict_latest_count_union} 檔。判定為「{broad_label}」。{broad_note}",
        f"MA20/60 路徑：{'；'.join(strict_rule_lines)}。回頭看本次樣本，可命中 non-fallback {strict_non_fallback_hit}/{len(non_fallback_rows)}、identified {strict_identified_hit}/{max(len(identified_rows), 1)}。",
        f"MA18/54 路徑：{'；'.join(strict_rule_lines_1854)}。回頭看本次樣本，可命中 non-fallback {strict_non_fallback_hit_1854}/{len(non_fallback_rows)}、identified {strict_identified_hit_1854}/{max(len(identified_rows), 1)}。",
        "建議每月持續檢視，並做 rolling backtest，比較現行版與 strict 版在 10/20/40 日報酬、命中率與最大回撤的差異。",
    ]
    if previous_window:
        review_lines.append(f"前次可比較區間：{previous_window}。")

    return {
        "study_start": study_start,
        "study_end": study_end,
        "screen_date": screen_date,
        "analysis_top": analysis_top,
        "identified_count": len(identified_rows),
        "window_start_count": sum(1 for row in launch_rows if row["launch_status"] == "window_start"),
        "fallback_count": sum(1 for row in launch_rows if row["launch_status"] == "fallback"),
        "screen_mode": "strict",
        "candidate_count": len(screened),
        "candidate_count_1854": len(screened_1854),
        "candidate_union_count": strict_latest_count_union,
        "focus_count": sum(1 for row in screened if row["focus_candidate"] == "Y"),
        "focus_count_1854": sum(1 for row in screened_1854 if row["focus_candidate"] == "Y"),
        "focus_union_count": sum(1 for row in combined_screened if row["focus_candidate"] == "Y"),
        "early_candidate_count": early_candidate_count,
        "continuation_candidate_count": continuation_candidate_count,
        "early_candidate_count_1854": early_candidate_count_1854,
        "continuation_candidate_count_1854": continuation_candidate_count_1854,
        "early_focus_count": early_focus_count,
        "continuation_focus_count": continuation_focus_count,
        "early_focus_count_1854": early_focus_count_1854,
        "continuation_focus_count_1854": continuation_focus_count_1854,
        "broad_candidate_count": broad_candidate_count,
        "broad_focus_count": sum(1 for row in broad_screened if row["focus_candidate"] == "Y"),
        "universe_count": len(current_rows),
        "candidate_ratio_pct": candidate_ratio_pct,
        "identified_multiplier": identified_multiplier,
        "breadth_label": broad_label,
        "breadth_note": broad_note,
        "common_feature_lines": common_feature_lines,
        "identified_feature_lines": identified_feature_lines,
        "strict_rule_lines": strict_rule_lines,
        "strict_rule_lines_1854": strict_rule_lines_1854,
        "strict_latest_count": strict_latest_count,
        "strict_latest_count_1854": strict_latest_count_1854,
        "strict_latest_count_union": strict_latest_count_union,
        "strict_non_fallback_hit": strict_non_fallback_hit,
        "strict_non_fallback_total": len(non_fallback_rows),
        "strict_identified_hit": strict_identified_hit,
        "strict_identified_total": len(identified_rows),
        "strict_non_fallback_hit_1854": strict_non_fallback_hit_1854,
        "strict_identified_hit_1854": strict_identified_hit_1854,
        "backtest_recommended": True,
        "review_lines": review_lines,
        "previous_window": previous_window,
    }


def top_gainers(rows_by_date, start_date, end_date, top_n):
    start_rows = rows_by_date.get(start_date, {})
    end_rows = rows_by_date.get(end_date, {})
    ranked = []
    for code, start_row in start_rows.items():
        end_row = end_rows.get(code)
        start_price = fv(start_row, "current_price", 0.0)
        end_price = fv(end_row, "current_price", 0.0) if end_row else 0.0
        if start_price <= 0 or end_price <= 0:
            continue
        ranked.append(
            {
                "code": code,
                "name": sv(end_row or start_row, "name"),
                "market": sv(end_row or start_row, "market"),
                "industry": sv(end_row or start_row, "industry"),
                "start_price": round(start_price, 2),
                "end_price": round(end_price, 2),
                "total_return_pct": round(pct(start_price, end_price), 2),
            }
        )
    ranked.sort(key=lambda item: item["total_return_pct"], reverse=True)
    return ranked[:top_n]


def max_forward_return(series, start_idx, lookahead):
    price = fv(series[start_idx], "current_price", 0.0)
    if price <= 0:
        return 0.0
    best = -999.0
    end_idx = min(len(series), start_idx + 1 + lookahead)
    for row in series[start_idx + 1:end_idx]:
        future_price = fv(row, "current_price", 0.0)
        if future_price <= 0:
            continue
        best = max(best, pct(price, future_price))
    return best if best > -999.0 else 0.0


def fallback_reason(first_row, best_proxy):
    if first_row is None:
        return "資料不足"
    if (
        fv(first_row, "return_20d_pct", 0.0) > 20
        and fv(first_row, "ma20", 0.0) > fv(first_row, "ma60", 0.0) > 0
    ):
        return "研究窗起點已在上升段，真正起漲可能更早"
    if best_proxy is not None:
        return "研究窗內沒有乾淨起漲點，改用條件最接近的一天"
    return "研究窗內沒有找到有效代理起漲點"


def detect_launch_point(series, study_start):
    if not series:
        return None

    best_proxy = None
    best_proxy_rank = None

    for idx, row in enumerate(series[:-1]):
        forward_max = max_forward_return(series, idx, DEFAULT_LAUNCH_LOOKAHEAD)
        cmap = condition_map(row)
        non_guard_count = sum(
            1 for condition in CONDITIONS
            if condition["key"] != "not_overheated" and cmap.get(condition["key"])
        )
        proxy_rank = (
            non_guard_count,
            forward_max,
            -abs(fv(row, "drawdown_from_high60_pct", 0.0)),
        )
        if best_proxy_rank is None or proxy_rank > best_proxy_rank:
            best_proxy = row
            best_proxy_rank = proxy_rank

        if not cmap.get("not_overheated"):
            continue
        if non_guard_count < 6:
            continue
        if forward_max < DEFAULT_MIN_FORWARD_RETURN_PCT:
            continue

        status = "window_start" if sv(row, "_date") == study_start else "identified"
        return {
            "launch_row": row,
            "status": status,
            "status_reason": "研究窗內最早符合早期起漲條件的日期",
            "forward_max_return_pct": round(forward_max, 2),
            "condition_count": non_guard_count,
            "matched_keys": matched_condition_keys(row),
        }

    first_row = series[0]
    proxy = first_row if first_row is not None else best_proxy
    if proxy is None:
        return None
    proxy_idx = series.index(proxy)
    return {
        "launch_row": proxy,
        "status": "fallback",
        "status_reason": fallback_reason(first_row, best_proxy),
        "forward_max_return_pct": round(
            max_forward_return(series, proxy_idx, DEFAULT_LAUNCH_LOOKAHEAD), 2
        ),
        "condition_count": len(matched_condition_keys(proxy)),
        "matched_keys": matched_condition_keys(proxy),
    }


def build_launch_study(winners, series_by_code, study_start, study_end):
    rows = []
    for rank, winner in enumerate(winners, 1):
        series = [
            row for row in series_by_code.get(winner["code"], [])
            if study_start <= sv(row, "_date") <= study_end
        ]
        launch = detect_launch_point(series, study_start)
        if launch is None:
            continue
        row = launch["launch_row"]
        rows.append(
            {
                "rank": rank,
                "code": winner["code"],
                "name": winner["name"],
                "market": winner["market"],
                "industry": winner["industry"],
                "study_start_price": winner["start_price"],
                "study_end_price": winner["end_price"],
                "study_total_return_pct": winner["total_return_pct"],
                "launch_date": sv(row, "_date"),
                "launch_status": launch["status"],
                "launch_status_reason": launch["status_reason"],
                "launch_price": round(fv(row, "current_price", 0.0), 2),
                "launch_forward_max_return_pct": launch["forward_max_return_pct"],
                "launch_condition_count": launch["condition_count"],
                "launch_selection_score": round(selection_score_of(row), 2),
                "launch_buy_point_score": round(buy_point_of(row), 2),
                "launch_latest_revenue_yoy_pct": round(fv(row, "latest_revenue_yoy_pct", 0.0), 2),
                "launch_avg_3m_revenue_yoy_pct": round(fv(row, "avg_3m_revenue_yoy_pct", 0.0), 2),
                "launch_five_day_institutional_net_ratio_pct": round(fv(row, "five_day_institutional_net_ratio_pct", 0.0), 2),
                "launch_broker_net_ratio_pct": round(fv(row, "broker_net_ratio_pct", 0.0), 2),
                "launch_return_20d_pct": round(fv(row, "return_20d_pct", 0.0), 2),
                "launch_return_60d_pct": round(fv(row, "return_60d_pct", 0.0), 2),
                "launch_volume_ratio": round(fv(row, "volume_ratio", 0.0), 2),
                "launch_drawdown_from_high60_pct": round(fv(row, "drawdown_from_high60_pct", 0.0), 2),
                "launch_structure_label": sv(row, "structure_label"),
                "launch_primary_theme": sv(row, "primary_theme"),
                "launch_signal_type": sv(row, "signal_type"),
                "launch_turnaround_label": sv(row, "turnaround_label"),
                "launch_note": sv(row, "note"),
                "matched_conditions": " | ".join(label_for_key(key) for key in launch["matched_keys"]),
                "_launch_row": row,
            }
        )
    return rows


def derive_common_conditions(launch_rows):
    sample = [row for row in launch_rows if row["launch_status"] != "fallback"]
    if not sample:
        sample = list(launch_rows)

    summary = []
    sample_size = len(sample)
    for condition in CONDITIONS:
        key = condition["key"]
        hit_count = 0
        for row in sample:
            if condition_map(row["_launch_row"]).get(key):
                hit_count += 1
        hit_rate = round((hit_count * 100.0 / sample_size), 2) if sample_size else 0.0
        role = "guard" if key == "not_overheated" else (
            "core" if hit_rate >= 70 else "support" if hit_rate >= 50 else "reference"
        )
        summary.append(
            {
                "key": key,
                "label": condition["label"],
                "gate": condition["gate"],
                "sample_size": sample_size,
                "hit_count": hit_count,
                "hit_rate_pct": hit_rate,
                "rule_role": role,
            }
        )
    summary.sort(key=lambda item: (item["rule_role"] != "guard", -item["hit_rate_pct"], item["key"]))
    return summary


def common_condition_files():
    pattern = re.compile(r"early_breakout_common_conditions_(\d{8})_(\d{8})\.csv$")
    files = []
    for path in HISTORY.glob("early_breakout_common_conditions_*.csv"):
        match = pattern.match(path.name)
        if not match:
            continue
        files.append((match.group(1), match.group(2), path))
    files.sort(key=lambda item: (item[0], item[1]))
    return files


def load_common_condition_file(path):
    rows = {}
    meta = {}
    for row in load_csv(path):
        key = sv(row, "condition_key")
        rows[key] = row
        meta = {
            "study_start": sv(row, "study_start"),
            "study_end": sv(row, "study_end"),
            "analysis_top": sv(row, "analysis_top"),
        }
    return meta, rows


def compare_with_previous(current_rows, current_start, current_end):
    files = common_condition_files()
    current_name = f"early_breakout_common_conditions_{current_start}_{current_end}.csv"
    previous = None
    for _, _, path in files:
        if path.name == current_name:
            continue
        previous = path
    if previous is None:
        return None, []

    previous_meta, previous_rows = load_common_condition_file(previous)
    compared = []
    for row in current_rows:
        key = row["condition_key"]
        previous_row = previous_rows.get(key, {})
        previous_hit = fv(previous_row, "hit_rate_pct", 0.0)
        previous_role = sv(previous_row, "rule_role")
        delta = round(row["hit_rate_pct"] - previous_hit, 2)
        compared.append(
            {
                "condition_key": key,
                "condition_label": row["condition_label"],
                "gate": row["gate"],
                "current_hit_rate_pct": row["hit_rate_pct"],
                "previous_hit_rate_pct": previous_hit,
                "delta_hit_rate_pct": delta,
                "current_rule_role": row["rule_role"],
                "previous_rule_role": previous_role,
                "previous_study_start": previous_meta.get("study_start", ""),
                "previous_study_end": previous_meta.get("study_end", ""),
            }
        )
    compared.sort(key=lambda item: (-abs(item["delta_hit_rate_pct"]), item["condition_key"]))
    return previous_meta, compared


def build_comparison_lines(previous_meta, compared_rows):
    if not previous_meta or not compared_rows:
        return "目前沒有更早的條件檢視檔可比較。"

    gains = [row for row in compared_rows if row["delta_hit_rate_pct"] > 0.01]
    drops = [row for row in compared_rows if row["delta_hit_rate_pct"] < -0.01]
    parts = [
        f"前次研究區間 {previous_meta.get('study_start', '')} → {previous_meta.get('study_end', '')}"
    ]
    if gains:
        top = gains[:3]
        parts.append(
            "提升最多："
            + "；".join(
                f"{item['condition_label']} <span class=\"delta-up\">+{item['delta_hit_rate_pct']:.1f}%</span>"
                for item in top
            )
        )
    if drops:
        top = drops[:3]
        parts.append(
            "下降最多："
            + "；".join(
                f"{item['condition_label']} <span class=\"delta-down\">{item['delta_hit_rate_pct']:.1f}%</span>"
                for item in top
            )
        )
    if not gains and not drops:
        parts.append("和前次相比，條件命中率幾乎沒有明顯變動。")
    return "<br>".join(parts)


def find_latest_candidates(date_str=None):
    if date_str:
        main = SNAPSHOTS / f"stock_candidates_{date_str}.csv"
        if main.exists():
            return main, date_str
    files = sorted(
        p for p in SNAPSHOTS.glob("stock_candidates_2*.csv")
        if p.stem.count("_") == 2
    )
    if files:
        target = files[-1]
        return target, target.stem.split("_")[-1]
    return None, None


def best_grade(left, right):
    order = {"A": 0, "B": 1, "C": 2}
    if not left:
        return right
    if not right:
        return left
    return left if order.get(left, 9) <= order.get(right, 9) else right


def screen_candidates(rows, condition_stats, strict_mode=False, variant=VARIANT_2060):
    guard = next((item for item in condition_stats if item["rule_role"] == "guard"), None)
    core = [item for item in condition_stats if item["rule_role"] == "core"]
    support = [item for item in condition_stats if item["rule_role"] == "support"]
    rule_set = core + support
    total_weight = sum(item["hit_rate_pct"] for item in rule_set) or 1.0
    required_core = max(1, int(math.ceil(len(core) * 0.75))) if core else 0

    screened = []
    for row in rows:
        cmap = condition_map(row)
        if guard and not cmap.get(guard["key"]):
            continue
        if strict_mode:
            if variant == VARIANT_1854:
                if not (strict_breakout_ready_1854(row) or strong_continuation_ready_1854(row)):
                    continue
            elif not (strict_breakout_ready(row) or strong_continuation_ready(row)):
                continue
        if selection_score_of(row) < DEFAULT_MIN_SELECTION_GATE:
            continue

        core_match = sum(1 for item in core if cmap.get(item["key"]))
        support_match = sum(1 for item in support if cmap.get(item["key"]))
        if core and core_match < required_core:
            continue

        matched = [item for item in rule_set if cmap.get(item["key"])]
        match_weight = sum(item["hit_rate_pct"] for item in matched)
        weighted_match_pct = match_weight * 100.0 / total_weight
        screen_score = round(
            weighted_match_pct * 0.55
            + selection_score_of(row) * 0.25
            + buy_point_of(row) * 0.20,
            2,
        )
        if screen_score < DEFAULT_MIN_SCREEN_SCORE:
            continue

        if core_match == len(core) and support_match >= max(1, len(support) // 2 or 1):
            grade = "A"
        elif core_match >= max(required_core, len(core) - 1):
            grade = "B"
        else:
            grade = "C"

        focus_candidate = buy_point_of(row) >= DEFAULT_FOCUS_BUY_POINT and grade in ("A", "B")
        if variant == VARIANT_1854:
            screen_style, screen_style_label = screen_style_of_1854(row)
        else:
            screen_style, screen_style_label = screen_style_of(row)
        missing_core = [item["label"] for item in core if not cmap.get(item["key"])]
        matched_labels = [item["label"] for item in matched]
        screened.append(
            {
                "screen_grade": grade,
                "screen_style": screen_style,
                "screen_style_label": screen_style_label,
                "screen_variant": variant,
                "screen_variant_label": VARIANT_LABELS.get(variant, variant),
                "focus_candidate": "Y" if focus_candidate else "N",
                "screen_score": screen_score,
                "core_match_count": core_match,
                "required_core_count": required_core,
                "support_match_count": support_match,
                "selection_score": round(selection_score_of(row), 2),
                "buy_point_score": round(buy_point_of(row), 2),
                "code": sv(row, "code"),
                "name": sv(row, "name"),
                "market": sv(row, "market"),
                "industry": sv(row, "industry"),
                "current_price": round(fv(row, "current_price", 0.0), 2),
                "primary_theme": sv(row, "primary_theme"),
                "signal_type": sv(row, "signal_type"),
                "post_close_category": sv(row, "post_close_category"),
                "structure_label": sv(row, "structure_label"),
                "turnaround_label": sv(row, "turnaround_label"),
                "latest_revenue_yoy_pct": round(fv(row, "latest_revenue_yoy_pct", 0.0), 2),
                "avg_3m_revenue_yoy_pct": round(fv(row, "avg_3m_revenue_yoy_pct", 0.0), 2),
                "positive_revenue_months": iv(row, "positive_revenue_months", 0),
                "five_day_institutional_net_ratio_pct": round(fv(row, "five_day_institutional_net_ratio_pct", 0.0), 2),
                "broker_net_ratio_pct": round(fv(row, "broker_net_ratio_pct", 0.0), 2),
                "return_20d_pct": round(fv(row, "return_20d_pct", 0.0), 2),
                "return_60d_pct": round(fv(row, "return_60d_pct", 0.0), 2),
                "volume_ratio": round(fv(row, "volume_ratio", 0.0), 2),
                "drawdown_from_high60_pct": round(fv(row, "drawdown_from_high60_pct", 0.0), 2),
                "liquidity_score": round(fv(row, "liquidity_score", 0.0), 2),
                "financial_quality_score": round(fv(row, "financial_quality_score", 0.0), 2),
                "matched_conditions": " | ".join(matched_labels),
                "missing_core_conditions": " | ".join(missing_core),
                "screen_reason": (
                    f"核心 {core_match}/{len(core)}，加分 {support_match}/{len(support)}；"
                    f"型態 {screen_style_label}；"
                    f"結構 {sv(row, 'structure_label') or '未標示'}；"
                    f"題材 {sv(row, 'primary_theme') or '一般'}；"
                    f"訊號 {sv(row, 'signal_type') or '待確認'}"
                ),
            }
        )

    grade_order = {"A": 0, "B": 1, "C": 2}
    screened.sort(
        key=lambda item: (
            item["focus_candidate"] != "Y",
            grade_order.get(item["screen_grade"], 9),
            -item["screen_score"],
            -item["buy_point_score"],
            -item["selection_score"],
        )
    )
    for idx, row in enumerate(screened, 1):
        row["rank"] = idx
    return screened


def merge_screened_variants(primary_rows, alt_rows):
    merged = {}

    def ensure_entry(source):
        code = source["code"]
        current = merged.get(code)
        if current is None:
            current = dict(source)
            current["in_screen_2060"] = "N"
            current["focus_candidate_2060"] = "N"
            current["screen_style_2060"] = ""
            current["screen_style_2060_label"] = ""
            current["in_screen_1854"] = "N"
            current["focus_candidate_1854"] = "N"
            current["screen_style_1854"] = ""
            current["screen_style_1854_label"] = ""
            merged[code] = current
        elif source["screen_score"] > current["screen_score"]:
            keep = {
                "in_screen_2060": current.get("in_screen_2060", "N"),
                "focus_candidate_2060": current.get("focus_candidate_2060", "N"),
                "screen_style_2060": current.get("screen_style_2060", ""),
                "screen_style_2060_label": current.get("screen_style_2060_label", ""),
                "in_screen_1854": current.get("in_screen_1854", "N"),
                "focus_candidate_1854": current.get("focus_candidate_1854", "N"),
                "screen_style_1854": current.get("screen_style_1854", ""),
                "screen_style_1854_label": current.get("screen_style_1854_label", ""),
            }
            current.update(source)
            current.update(keep)
        current["screen_grade"] = best_grade(current.get("screen_grade"), source.get("screen_grade"))
        current["focus_candidate"] = (
            "Y"
            if current.get("focus_candidate") == "Y" or source.get("focus_candidate") == "Y"
            else "N"
        )
        return current

    for row in primary_rows:
        entry = ensure_entry(row)
        entry["in_screen_2060"] = "Y"
        entry["focus_candidate_2060"] = row["focus_candidate"]
        entry["screen_style_2060"] = row["screen_style"]
        entry["screen_style_2060_label"] = row["screen_style_label"]

    for row in alt_rows:
        entry = ensure_entry(row)
        entry["in_screen_1854"] = "Y"
        entry["focus_candidate_1854"] = row["focus_candidate"]
        entry["screen_style_1854"] = row["screen_style"]
        entry["screen_style_1854_label"] = row["screen_style_label"]

    rows = list(merged.values())
    grade_order = {"A": 0, "B": 1, "C": 2}
    rows.sort(
        key=lambda item: (
            item["focus_candidate"] != "Y",
            grade_order.get(item["screen_grade"], 9),
            -item["screen_score"],
            -item["buy_point_score"],
            -item["selection_score"],
        )
    )
    for idx, row in enumerate(rows, 1):
        row["rank"] = idx
    return rows


def write_csv(path, fieldnames, rows):
    with open(path, "w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            clean = dict(row)
            clean.pop("_launch_row", None)
            writer.writerow(clean)


def tag_html(text, cls):
    if not text or text in ("一般", "待確認", "—"):
        return ""
    return f'<span class="{cls}">{safe_html(text)}</span>'


def build_html_row(candidate):
    reason_items = candidate["matched_conditions"].split(" | ")
    reason_text = "；".join(reason_items[:3])
    route_tags = []
    if candidate.get("in_screen_2060") == "Y":
        route_tags.append(f"MA20/60 {candidate.get('screen_style_2060_label') or '命中'}")
    if candidate.get("in_screen_1854") == "Y":
        route_tags.append(f"MA18/54 {candidate.get('screen_style_1854_label') or '命中'}")
    route_text = " / ".join(route_tags) if route_tags else candidate["screen_style_label"]
    return f"""<tr>
  <td>{candidate['rank']}</td>
  <td><span class="grade {grade_class(candidate['screen_grade'])}">{candidate['screen_grade']}</span></td>
  <td><b>{safe_html(candidate['code'])}</b></td>
  <td>{safe_html(candidate['name'])}</td>
  <td>{safe_html(route_text)}</td>
  <td>{candidate['current_price']:,.2f}</td>
  <td class="{score_class(candidate['screen_score'])}">{candidate['screen_score']:.2f}</td>
  <td>{candidate['selection_score']:.2f}</td>
  <td>{candidate['buy_point_score']:.2f}</td>
  <td>{candidate['avg_3m_revenue_yoy_pct']:+.1f}%</td>
  <td>{candidate['return_20d_pct']:+.1f}%</td>
  <td>{candidate['return_60d_pct']:+.1f}%</td>
  <td>{candidate['volume_ratio']:.2f}</td>
  <td>{tag_html(candidate['structure_label'] or '—', 'tag tag-structure')}</td>
  <td>{tag_html(candidate['primary_theme'] or '—', 'tag tag-theme')}</td>
  <td>{tag_html(candidate['signal_type'] or '待確認', 'tag tag-signal')}</td>
  <td class="small">{safe_html(reason_text)}</td>
</tr>"""


def build_rule_lines(condition_stats, role):
    lines = []
    for item in condition_stats:
        if item["rule_role"] != role:
            continue
        lines.append(f"{safe_html(item['label'])} ({item['hit_rate_pct']:.1f}%)")
    if not lines:
        return "目前沒有符合條件的統計樣本。"
    return "<br>".join(lines)


def print_console_summary(top_display):
    print(
        f"{'#':>3}  {'Grade':<5} {'代碼':<8} {'名稱':<12} {'型態':<8} {'Screen':>7}  {'策略分':>7}  {'買點分':>7}  "
        f"{'3M%':>7}  {'20d%':>6}  {'60d%':>6}  {'結構':<8}  {'題材':<10}"
    )
    print("-" * 134)
    for row in top_display:
        name = row["name"][:10]
        style = []
        if row.get("in_screen_2060") == "Y":
            style.append(f"20/60-{(row.get('screen_style_2060_label') or '命中')[:4]}")
        if row.get("in_screen_1854") == "Y":
            style.append(f"18/54-{(row.get('screen_style_1854_label') or '命中')[:4]}")
        style = "/".join(style)[:18] or row["screen_style_label"][:6]
        structure = row["structure_label"] or "—"
        theme = row["primary_theme"] or "—"
        print(
            f"{row['rank']:>3}. {row['screen_grade']:<5} {row['code']:<8} {name:<12} {style:<8} "
            f"{row['screen_score']:>7.2f}  {row['selection_score']:>7.2f}  {row['buy_point_score']:>7.2f}  "
            f"{row['avg_3m_revenue_yoy_pct']:>+7.1f}%  {row['return_20d_pct']:>+5.1f}%  "
            f"{row['return_60d_pct']:>+5.1f}%  {structure:<8}  {theme:<10}"
        )


def dashboard_panel_html(screen_date, condition_stats, compared_rows, previous_meta, candidates, review_summary):
    top = candidates[:10]
    core = [item for item in condition_stats if item["rule_role"] == "core"][:4]
    core_line = "；".join(
        f"{item['label']} {item['hit_rate_pct']:.1f}%"
        for item in core
    ) if core else "目前尚未建立核心條件。"
    review_line = "<br>".join(
        safe_html(line) for line in review_summary.get("review_lines", [])[:3]
    ) or "目前沒有月檢視摘要。"

    compare_line = "目前沒有前次可比較。"
    if previous_meta and compared_rows:
        gains = [row for row in compared_rows if row["delta_hit_rate_pct"] > 0.01][:2]
        drops = [row for row in compared_rows if row["delta_hit_rate_pct"] < -0.01][:2]
        parts = [f"前次區間 {previous_meta.get('study_start', '')} → {previous_meta.get('study_end', '')}"]
        if gains:
            parts.append(
                "提升："
                + "；".join(
                    f"{row['condition_label']} +{row['delta_hit_rate_pct']:.1f}%"
                    for row in gains
                )
            )
        if drops:
            parts.append(
                "下降："
                + "；".join(
                    f"{row['condition_label']} {row['delta_hit_rate_pct']:.1f}%"
                    for row in drops
                )
            )
        compare_line = " | ".join(parts)

    rows_html = []
    for candidate in top:
        route_parts = []
        if candidate.get("in_screen_2060") == "Y":
            route_parts.append(f"MA20/60 {candidate.get('screen_style_2060_label') or '命中'}")
        if candidate.get("in_screen_1854") == "Y":
            route_parts.append(f"MA18/54 {candidate.get('screen_style_1854_label') or '命中'}")
        rows_html.append(
            "<tr>"
            f"<td><strong>{safe_html(candidate['code'])} {safe_html(candidate['name'])}</strong>"
            f"<div class=\"subline\">Screen {candidate['screen_score']:.2f} / 策略 {candidate['selection_score']:.2f} / 買點 {candidate['buy_point_score']:.2f}"
            + (f" / {'；'.join(route_parts)}" if route_parts else "")
            + "</div></td>"
            f"<td>{safe_html(candidate['structure_label'] or '—')}</td>"
            f"<td>{safe_html(candidate['primary_theme'] or '一般')}</td>"
            f"<td>{safe_html('Focus' if candidate['focus_candidate'] == 'Y' else candidate['screen_grade'])}</td>"
            "</tr>"
        )
    if not rows_html:
        rows_html.append("<tr><td colspan=\"4\">目前沒有符合早期起漲條件的候選股。</td></tr>")

    return (
        DASHBOARD_PANEL_START
        + "<section class=\"panel\">"
        + "<div class=\"section-head\"><div><h2>早期起漲條件</h2>"
        + "<p class=\"hint\">這塊會用最近一次起漲研究學到的條件，挑出目前較像「剛起動」而不是「已漲很久」的名單。</p>"
        + "</div>"
        + f"<a class=\"chip-button active\" href=\"web/early_breakout/early_breakout_latest.html\">查看完整早期起漲報表 {safe_html(screen_date)}</a>"
        + "</div>"
        + "<div class=\"metric-grid\">"
        + f"<article class=\"metric-card\"><div class=\"metric-label\">目前候選</div><div class=\"metric-value\">{len(candidates)}</div><div class=\"subline\">兩組 tab 合併名單</div></article>"
        + f"<article class=\"metric-card\"><div class=\"metric-label\">20/60 與 18/54</div><div class=\"metric-value\">{review_summary.get('candidate_count', 0)} / {review_summary.get('candidate_count_1854', 0)}</div><div class=\"subline\">兩組均線主名單</div></article>"
        + f"<article class=\"metric-card\"><div class=\"metric-label\">核心條件</div><div class=\"metric-value\">{len([item for item in condition_stats if item['rule_role'] == 'core'])}</div><div class=\"subline\">命中率 >= 70%</div></article>"
        + f"<article class=\"metric-card\"><div class=\"metric-label\">寬鬆度</div><div class=\"metric-value\">{safe_html(review_summary.get('breadth_label', '-'))}</div><div class=\"subline\">合併 {review_summary.get('candidate_union_count', 0)} 檔 / 寬版 {review_summary.get('broad_candidate_count', 0)} 檔</div></article>"
        + "</div>"
        + f"<div class=\"empty\" style=\"margin-bottom:14px;\"><strong>本次核心：</strong> {safe_html(core_line)}<br><strong>和前次相比：</strong> {safe_html(compare_line)}<br><strong>月檢視：</strong> {review_line}</div>"
        + "<div class=\"table-shell\"><table><thead><tr><th>股票</th><th>結構</th><th>題材</th><th>等級</th></tr></thead><tbody>"
        + "".join(rows_html)
        + "</tbody></table></div>"
        + "</section>"
        + DASHBOARD_PANEL_END
    )


def inject_panel_into_dashboard(panel_html):
    targets = [ROOT / "history_dashboard.html"]
    dashboards = sorted(list(STATIC_DASHBOARD_DIR.glob("stock_dashboard_*.html")) + list(ROOT.glob("stock_dashboard_*.html")))
    if dashboards:
        targets.append(dashboards[-1])

    for target in targets:
        if not target.exists():
            continue
        content = target.read_text(encoding="utf-8", errors="ignore")
        content = re.sub(
            re.escape(DASHBOARD_PANEL_START) + ".*?" + re.escape(DASHBOARD_PANEL_END),
            "",
            content,
            flags=re.S,
        )
        insert_idx = content.find(DASHBOARD_INSERT_MARKER)
        if insert_idx < 0:
            continue
        content = content[:insert_idx] + panel_html + content[insert_idx:]
        target.write_text(content, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--date", help="篩選用日期 YYYYMMDD，預設抓 latest daily snapshot")
    parser.add_argument("--study-start", help="研究起始 YYYYMMDD，預設取近 30 個快照")
    parser.add_argument("--study-end", help="研究結束 YYYYMMDD，預設取最新 history 快照")
    parser.add_argument("--top", type=int, default=DEFAULT_DISPLAY_TOP, help="顯示前幾名 (HTML/console)")
    parser.add_argument("--analysis-top", type=int, default=DEFAULT_ANALYSIS_TOP, help="研究樣本前幾名")
    args = parser.parse_args()

    dates, rows_by_date, series_by_code = load_history_snapshots()
    _, daily_series_by_code = load_daily_price_series()
    for code_rows in series_by_code.values():
        for row in code_rows:
            hydrate_alt_metrics(row, daily_series_by_code)
    if len(dates) < 5:
        print("[ERROR] history snapshots not enough.")
        sys.exit(1)

    default_start, default_end = resolve_default_study_window(dates)
    study_start = args.study_start or default_start
    study_end = args.study_end or default_end
    if study_start not in rows_by_date or study_end not in rows_by_date:
        print(f"[ERROR] Invalid study window: {study_start} -> {study_end}")
        sys.exit(1)
    if study_start > study_end:
        print("[ERROR] study-start must be <= study-end")
        sys.exit(1)

    winners = top_gainers(rows_by_date, study_start, study_end, args.analysis_top)
    if not winners:
        print("[ERROR] No gainers found in study window.")
        sys.exit(1)

    launch_rows = build_launch_study(winners, series_by_code, study_start, study_end)
    condition_stats = derive_common_conditions(launch_rows)

    latest_path, screen_date = find_latest_candidates(args.date or study_end)
    if latest_path is None:
        print("[ERROR] No daily snapshot found for screener.")
        sys.exit(1)

    current_rows = load_csv(latest_path)
    for row in current_rows:
        row["_date"] = screen_date
        hydrate_alt_metrics(row, daily_series_by_code)
    broad_screened = screen_candidates(current_rows, condition_stats, strict_mode=False)
    screened = screen_candidates(current_rows, condition_stats, strict_mode=True, variant=VARIANT_2060)
    screened_1854 = screen_candidates(current_rows, condition_stats, strict_mode=True, variant=VARIANT_1854)
    combined_screened = merge_screened_variants(screened, screened_1854)
    top_display = combined_screened[:args.top]

    identified_count = sum(1 for row in launch_rows if row["launch_status"] == "identified")
    window_start_count = sum(1 for row in launch_rows if row["launch_status"] == "window_start")
    fallback_count = sum(1 for row in launch_rows if row["launch_status"] == "fallback")
    focus_count = sum(1 for row in combined_screened if row["focus_candidate"] == "Y")

    common_csv = HISTORY / f"early_breakout_common_conditions_{study_start}_{study_end}.csv"
    common_rows = []
    for item in condition_stats:
        common_rows.append(
            {
                "study_start": study_start,
                "study_end": study_end,
                "analysis_top": args.analysis_top,
                "launch_lookahead_snapshots": DEFAULT_LAUNCH_LOOKAHEAD,
                "min_forward_return_pct": DEFAULT_MIN_FORWARD_RETURN_PCT,
                "condition_key": item["key"],
                "condition_label": item["label"],
                "gate": item["gate"],
                "sample_size": item["sample_size"],
                "hit_count": item["hit_count"],
                "hit_rate_pct": item["hit_rate_pct"],
                "rule_role": item["rule_role"],
            }
        )
    common_fields = [
        "study_start", "study_end", "analysis_top",
        "launch_lookahead_snapshots", "min_forward_return_pct",
        "condition_key", "condition_label", "gate",
        "sample_size", "hit_count", "hit_rate_pct", "rule_role",
    ]
    write_csv(common_csv, common_fields, common_rows)

    previous_meta, compared_rows = compare_with_previous(common_rows, study_start, study_end)
    review_summary = build_review_summary(
        study_start,
        study_end,
        screen_date,
        args.analysis_top,
        launch_rows,
        condition_stats,
        screened,
        screened_1854,
        combined_screened,
        broad_screened,
        current_rows,
        previous_meta,
    )

    print(f"[INFO] Study window: {study_start} -> {study_end}")
    print(f"[INFO] Top gainers analysed: {len(launch_rows)}")
    print(
        f"[INFO] Launch points: identified={identified_count}, window_start={window_start_count}, fallback={fallback_count}"
    )
    if previous_meta:
        print(
            f"[INFO] Previous comparison: {previous_meta.get('study_start', '')} -> {previous_meta.get('study_end', '')}"
        )
    else:
        print("[INFO] Previous comparison: none")
    print(f"[INFO] Screening snapshot: {latest_path.name}")
    print(f"[INFO] Broad screen: {len(broad_screened)}")
    print(f"[INFO] Strict screen MA20/60: {len(screened)}")
    print(f"[INFO] Strict screen MA18/54: {len(screened_1854)}")
    print(f"[INFO] Combined screen: {len(combined_screened)} (focus {focus_count})")
    print()
    print_console_summary(top_display)

    launch_csv = HISTORY / f"early_breakout_launch_points_{study_start}_{study_end}.csv"
    compare_csv = HISTORY / f"early_breakout_condition_changes_{study_start}_{study_end}.csv"
    report_csv = WEB_REPORTS / f"early_breakout_{screen_date}.csv"
    latest_csv = WEB_REPORTS / "early_breakout_latest.csv"
    report_summary_json = WEB_REPORTS / f"early_breakout_{screen_date}_summary.json"
    latest_summary_json = WEB_REPORTS / "early_breakout_latest_summary.json"
    html_path = WEB_REPORTS / f"early_breakout_{screen_date}.html"
    latest_html = WEB_REPORTS / "early_breakout_latest.html"

    launch_fields = [
        "rank", "code", "name", "market", "industry",
        "study_start_price", "study_end_price", "study_total_return_pct",
        "launch_date", "launch_status", "launch_status_reason", "launch_price",
        "launch_forward_max_return_pct", "launch_condition_count",
        "launch_selection_score", "launch_buy_point_score",
        "launch_latest_revenue_yoy_pct", "launch_avg_3m_revenue_yoy_pct",
        "launch_five_day_institutional_net_ratio_pct", "launch_broker_net_ratio_pct",
        "launch_return_20d_pct", "launch_return_60d_pct",
        "launch_volume_ratio", "launch_drawdown_from_high60_pct",
        "launch_structure_label", "launch_primary_theme",
        "launch_signal_type", "launch_turnaround_label",
        "launch_note", "matched_conditions",
    ]
    write_csv(launch_csv, launch_fields, launch_rows)

    compare_fields = [
        "condition_key", "condition_label", "gate",
        "current_hit_rate_pct", "previous_hit_rate_pct", "delta_hit_rate_pct",
        "current_rule_role", "previous_rule_role",
        "previous_study_start", "previous_study_end",
    ]
    write_csv(compare_csv, compare_fields, compared_rows)

    screen_fields = [
        "rank", "screen_grade", "focus_candidate", "screen_score",
        "screen_style", "screen_style_label",
        "in_screen_2060", "focus_candidate_2060", "screen_style_2060", "screen_style_2060_label",
        "in_screen_1854", "focus_candidate_1854", "screen_style_1854", "screen_style_1854_label",
        "core_match_count", "required_core_count", "support_match_count",
        "selection_score", "buy_point_score",
        "code", "name", "market", "industry", "current_price",
        "primary_theme", "signal_type", "post_close_category",
        "structure_label", "turnaround_label",
        "latest_revenue_yoy_pct", "avg_3m_revenue_yoy_pct", "positive_revenue_months",
        "five_day_institutional_net_ratio_pct", "broker_net_ratio_pct",
        "return_20d_pct", "return_60d_pct", "volume_ratio",
        "drawdown_from_high60_pct", "liquidity_score", "financial_quality_score",
        "matched_conditions", "missing_core_conditions", "screen_reason",
    ]
    write_csv(report_csv, screen_fields, combined_screened)
    latest_csv.write_text(report_csv.read_text(encoding="utf-8-sig"), encoding="utf-8-sig")
    report_summary_json.write_text(
        json.dumps(review_summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    latest_summary_json.write_text(
        report_summary_json.read_text(encoding="utf-8"),
        encoding="utf-8",
    )

    try:
        html_screen_date = datetime.strptime(screen_date, "%Y%m%d").strftime("%Y-%m-%d")
        html_study_start = datetime.strptime(study_start, "%Y%m%d").strftime("%Y-%m-%d")
        html_study_end = datetime.strptime(study_end, "%Y%m%d").strftime("%Y-%m-%d")
    except ValueError:
        html_screen_date = screen_date
        html_study_start = study_start
        html_study_end = study_end

    html_rows = "\n".join(build_html_row(candidate) for candidate in top_display)
    html_content = HTML_TMPL.format(
        screen_date=html_screen_date,
        study_start=html_study_start,
        study_end=html_study_end,
        analysis_top=args.analysis_top,
        launch_lookahead=DEFAULT_LAUNCH_LOOKAHEAD,
        min_forward_return_pct=DEFAULT_MIN_FORWARD_RETURN_PCT,
        identified_count=identified_count,
        window_start_count=window_start_count,
        fallback_count=fallback_count,
        candidate_count=len(combined_screened),
        focus_count=focus_count,
        breadth_label=review_summary["breadth_label"],
        broad_candidate_count=review_summary["broad_candidate_count"],
        core_lines=build_rule_lines(condition_stats, "core"),
        support_lines=build_rule_lines(condition_stats, "support"),
        comparison_lines=build_comparison_lines(previous_meta, compared_rows),
        review_lines="<br>".join(safe_html(line) for line in review_summary["review_lines"]),
        rows=html_rows,
    )
    html_path.write_text(html_content, encoding="utf-8")
    latest_html.write_text(html_content, encoding="utf-8")

    panel_html = dashboard_panel_html(
        screen_date, condition_stats, compared_rows, previous_meta, combined_screened, review_summary
    )
    inject_panel_into_dashboard(panel_html)

    print()
    print(f"[OK] Launch points CSV   -> {launch_csv}")
    print(f"[OK] Common rules CSV    -> {common_csv}")
    print(f"[OK] Condition diff CSV  -> {compare_csv}")
    print(f"[OK] Screen CSV          -> {report_csv}")
    print(f"[OK] Review summary JSON -> {report_summary_json}")
    print(f"[OK] Screen HTML         -> {html_path}")
    print("[OK] Dashboard injected  -> history_dashboard.html / latest static/dashboards/stock_dashboard_*.html")
    print("[OK] Also written        -> early_breakout_latest.csv / .html / _summary.json")


if __name__ == "__main__":
    main()
