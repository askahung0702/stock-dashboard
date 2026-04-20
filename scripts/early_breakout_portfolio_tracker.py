#!/usr/bin/env python3
"""
Early Breakout Portfolio Tracker
--------------------------------
Take the current early-breakout / continuation candidate list as a fixed buy snapshot
and track daily profit rates for that cohort over time.
"""

import csv
import json
import statistics
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import early_breakout_screener as screener


SNAPSHOT_PREFIX = "early_breakout_portfolio_snapshot"
DETAIL_PREFIX = "early_breakout_portfolio_daily_detail"
SUMMARY_PREFIX = "early_breakout_portfolio_daily_summary"
WEB_PREFIX = "early_breakout_portfolio"


def parse_date(text):
    return datetime.strptime(text, "%Y%m%d").date()


def format_metric(value, digits=2):
    if value is None:
        return ""
    return round(value, digits)


def load_latest_breakout_rows():
    path = screener.WEB_REPORTS / "early_breakout_latest.csv"
    rows = screener.load_csv(path)
    for row in rows:
        row["_source_file"] = path.name
    return rows


def load_latest_screen_date():
    summary_path = screener.WEB_REPORTS / "early_breakout_latest_summary.json"
    if summary_path.exists():
        with open(summary_path, encoding="utf-8") as handle:
            payload = json.load(handle)
        return payload.get("screen_date") or payload.get("study_end") or ""
    rows = load_latest_breakout_rows()
    if rows and rows[0].get("_source_file", "").endswith(".csv"):
        source = rows[0]["_source_file"]
        digits = "".join(ch for ch in source if ch.isdigit())
        if len(digits) >= 8:
            return digits[-8:]
    return ""


def portfolio_snapshot_path(snapshot_date):
    return screener.HISTORY / f"{SNAPSHOT_PREFIX}_{snapshot_date}.csv"


def portfolio_detail_path(snapshot_date):
    return screener.HISTORY / f"{DETAIL_PREFIX}_{snapshot_date}.csv"


def portfolio_summary_path(snapshot_date):
    return screener.HISTORY / f"{SUMMARY_PREFIX}_{snapshot_date}.csv"


def list_snapshot_files():
    return sorted(
        path for path in screener.HISTORY.glob(f"{SNAPSHOT_PREFIX}_*.csv")
        if path.stem[-8:].isdigit()
    )


def build_snapshot_rows(snapshot_date, rows):
    snapshot_rows = []
    for row in rows:
        if row.get("in_screen_2060") != "Y":
            continue
        style = row.get("screen_style_2060") or row.get("screen_style") or ""
        if style not in ("early", "continuation"):
            continue
        snapshot_rows.append(
            {
                "snapshot_date": snapshot_date,
                "group_key": style,
                "group_label": "早期起漲" if style == "early" else "強勢續攻",
                "focus_candidate": row.get("focus_candidate_2060") or row.get("focus_candidate") or "N",
                "rank": row.get("rank", ""),
                "screen_grade": row.get("screen_grade", ""),
                "screen_score": row.get("screen_score", ""),
                "selection_score": row.get("selection_score", ""),
                "buy_point_score": row.get("buy_point_score", ""),
                "code": row.get("code", ""),
                "name": row.get("name", ""),
                "market": row.get("market", ""),
                "industry": row.get("industry", ""),
                "buy_price": row.get("current_price", ""),
                "primary_theme": row.get("primary_theme", ""),
                "signal_type": row.get("signal_type", ""),
                "post_close_category": row.get("post_close_category", ""),
                "structure_label": row.get("structure_label", ""),
                "turnaround_label": row.get("turnaround_label", ""),
                "screen_reason": row.get("screen_reason", ""),
            }
        )
    snapshot_rows.sort(
        key=lambda item: (
            item["group_key"] != "early",
            item.get("focus_candidate") != "Y",
            float(item.get("screen_score") or 0) * -1,
        )
    )
    return snapshot_rows


def write_csv(path, fieldnames, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def write_json(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)


def summarize_returns(values):
    if not values:
        return {"stock_count": 0, "avg_return_pct": "", "median_return_pct": "", "win_rate_pct": ""}
    wins = sum(1 for value in values if value > 0)
    return {
        "stock_count": len(values),
        "avg_return_pct": format_metric(sum(values) / len(values)),
        "median_return_pct": format_metric(statistics.median(values)),
        "win_rate_pct": format_metric(wins * 100.0 / len(values)),
    }


def snapshot_fieldnames():
    return [
        "snapshot_date",
        "group_key",
        "group_label",
        "focus_candidate",
        "rank",
        "screen_grade",
        "screen_score",
        "selection_score",
        "buy_point_score",
        "code",
        "name",
        "market",
        "industry",
        "buy_price",
        "primary_theme",
        "signal_type",
        "post_close_category",
        "structure_label",
        "turnaround_label",
        "screen_reason",
    ]


def detail_fieldnames():
    return [
        "snapshot_date",
        "track_date",
        "days_since_buy",
        "group_key",
        "group_label",
        "focus_candidate",
        "code",
        "name",
        "buy_price",
        "close_price",
        "return_pct",
        "screen_grade",
        "screen_score",
        "buy_point_score",
        "primary_theme",
        "signal_type",
        "structure_label",
    ]


def summary_fieldnames():
    return [
        "snapshot_date",
        "track_date",
        "days_since_buy",
        "group_key",
        "group_label",
        "scope",
        "scope_label",
        "stock_count",
        "avg_return_pct",
        "median_return_pct",
        "win_rate_pct",
    ]


def load_rows_by_date():
    dates, rows_by_date, _ = screener.load_history_snapshots()
    return dates, rows_by_date


def scope_groups(snapshot_rows):
    all_rows = list(snapshot_rows)
    groups = {
        ("all", "全部名單"): all_rows,
        ("early", "早期起漲"): [row for row in all_rows if row["group_key"] == "early"],
        ("continuation", "強勢續攻"): [row for row in all_rows if row["group_key"] == "continuation"],
        ("early_focus", "早期起漲 Focus"): [row for row in all_rows if row["group_key"] == "early" and row["focus_candidate"] == "Y"],
        ("continuation_focus", "強勢續攻 Focus"): [row for row in all_rows if row["group_key"] == "continuation" and row["focus_candidate"] == "Y"],
    }
    return {key: rows for key, rows in groups.items() if rows}


def build_tracking_outputs(snapshot_path, history_dates, rows_by_date):
    snapshot_rows = screener.load_csv(snapshot_path)
    if not snapshot_rows:
        return [], [], {}
    snapshot_date = snapshot_rows[0]["snapshot_date"]
    valid_dates = [date for date in history_dates if date >= snapshot_date]
    buy_dt = parse_date(snapshot_date)

    detail_rows = []
    by_scope_and_date = defaultdict(list)
    groups = scope_groups(snapshot_rows)

    for track_date in valid_dates:
        track_dt = parse_date(track_date)
        days_since_buy = (track_dt - buy_dt).days
        date_rows = rows_by_date.get(track_date, {})
        for item in snapshot_rows:
            code = item["code"]
            price_row = date_rows.get(code)
            close_price = screener.fv(price_row or {}, "current_price", 0.0)
            buy_price = screener.fv(item, "buy_price", 0.0)
            if buy_price <= 0 or close_price <= 0:
                continue
            daily = {
                "snapshot_date": snapshot_date,
                "track_date": track_date,
                "days_since_buy": days_since_buy,
                "group_key": item["group_key"],
                "group_label": item["group_label"],
                "focus_candidate": item["focus_candidate"],
                "code": code,
                "name": item["name"],
                "buy_price": format_metric(buy_price),
                "close_price": format_metric(close_price),
                "return_pct": format_metric(screener.pct(buy_price, close_price)),
                "screen_grade": item["screen_grade"],
                "screen_score": item["screen_score"],
                "buy_point_score": item["buy_point_score"],
                "primary_theme": item["primary_theme"],
                "signal_type": item["signal_type"],
                "structure_label": item["structure_label"],
            }
            detail_rows.append(daily)

        for (scope_key, scope_label), members in groups.items():
            returns = []
            for item in members:
                price_row = date_rows.get(item["code"])
                close_price = screener.fv(price_row or {}, "current_price", 0.0)
                buy_price = screener.fv(item, "buy_price", 0.0)
                if buy_price <= 0 or close_price <= 0:
                    continue
                returns.append(screener.pct(buy_price, close_price))
            if returns:
                by_scope_and_date[(track_date, scope_key, scope_label)] = returns

    summary_rows = []
    for (track_date, scope_key, scope_label), returns in sorted(by_scope_and_date.items()):
        track_dt = parse_date(track_date)
        metrics = summarize_returns(returns)
        summary_rows.append(
            {
                "snapshot_date": snapshot_date,
                "track_date": track_date,
                "days_since_buy": (track_dt - buy_dt).days,
                "group_key": scope_key,
                "group_label": scope_label,
                "scope": "focus" if "focus" in scope_key else "all",
                "scope_label": "Focus" if "focus" in scope_key else "全部名單",
                **metrics,
            }
        )

    latest_summary = {
        "snapshot_date": snapshot_date,
        "latest_track_date": summary_rows[-1]["track_date"] if summary_rows else snapshot_date,
        "stock_count": len(snapshot_rows),
        "groups": {},
    }
    for row in summary_rows:
        latest_summary["groups"].setdefault(row["group_key"], []).append(
            {
                "track_date": row["track_date"],
                "days_since_buy": row["days_since_buy"],
                "stock_count": row["stock_count"],
                "avg_return_pct": row["avg_return_pct"],
                "median_return_pct": row["median_return_pct"],
                "win_rate_pct": row["win_rate_pct"],
            }
        )
    return snapshot_rows, detail_rows, summary_rows, latest_summary


def build_html(snapshot_rows, summary_rows, latest_summary):
    snapshot_date = latest_summary.get("snapshot_date", "")
    latest_track_date = latest_summary.get("latest_track_date", snapshot_date)
    latest_by_group = {}
    for row in summary_rows:
        latest_by_group[row["group_key"]] = row

    cards = []
    for group_key in ("all", "early", "continuation", "early_focus", "continuation_focus"):
        row = latest_by_group.get(group_key)
        if not row:
            continue
        cards.append(
            f"""
            <div class="card">
              <div class="label">{row['group_label']}</div>
              <div class="value">{row['avg_return_pct']}%</div>
              <div class="meta">中位數 {row['median_return_pct']}% ｜ 勝率 {row['win_rate_pct']}% ｜ 樣本 {row['stock_count']}</div>
            </div>
            """
        )

    latest_rows = [row for row in summary_rows if row["track_date"] == latest_track_date]
    latest_rows.sort(key=lambda item: item["group_key"])
    table_rows = "\n".join(
        f"<tr><td>{row['group_label']}</td><td>{row['track_date']}</td><td>{row['days_since_buy']}</td><td>{row['stock_count']}</td><td>{row['avg_return_pct']}%</td><td>{row['median_return_pct']}%</td><td>{row['win_rate_pct']}%</td></tr>"
        for row in latest_rows
    )

    return f"""<!DOCTYPE html>
<html lang="zh-TW">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>起漲投組追蹤 {snapshot_date}</title>
<style>
body {{ font-family: "Noto Sans TC", sans-serif; background:#0f172a; color:#e2e8f0; margin:0; padding:20px; }}
h1 {{ color:#93c5fd; margin:0 0 8px; font-size:1.5rem; }}
.sub {{ color:#94a3b8; margin-bottom:18px; line-height:1.6; }}
.grid {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(220px,1fr)); gap:12px; margin-bottom:18px; }}
.card {{ background:#111827; border:1px solid #1f2937; border-radius:12px; padding:14px; }}
.label {{ color:#94a3b8; font-size:.82rem; }}
.value {{ color:#f8fafc; font-size:1.4rem; font-weight:700; margin-top:6px; }}
.meta {{ color:#cbd5e1; font-size:.8rem; margin-top:6px; }}
table {{ width:100%; border-collapse:collapse; background:#111827; border:1px solid #1f2937; }}
th,td {{ padding:10px 8px; border-bottom:1px solid #1f2937; text-align:left; }}
th {{ color:#bfdbfe; background:#1e3a5f; }}
</style>
</head>
<body>
  <h1>起漲投組追蹤</h1>
  <div class="sub">買進快照日 {snapshot_date}，最新追蹤日 {latest_track_date}。這裡把當天的 `早期起漲 / 強勢續攻` 視為固定買進組合，之後每天追蹤同一批股票的報酬。</div>
  <div class="grid">{''.join(cards)}</div>
  <table>
    <thead>
      <tr><th>投組</th><th>追蹤日</th><th>持有天數</th><th>股票數</th><th>平均報酬</th><th>中位數</th><th>勝率</th></tr>
    </thead>
    <tbody>{table_rows}</tbody>
  </table>
</body>
</html>"""


def write_portfolio_snapshot():
    snapshot_date = load_latest_screen_date()
    if not snapshot_date:
        raise SystemExit("Cannot determine latest breakout snapshot date.")
    rows = load_latest_breakout_rows()
    snapshot_rows = build_snapshot_rows(snapshot_date, rows)
    if not snapshot_rows:
        raise SystemExit("No MA20/60 early-breakout / continuation candidates found in latest csv.")
    path = portfolio_snapshot_path(snapshot_date)
    write_csv(path, snapshot_fieldnames(), snapshot_rows)
    return path, snapshot_rows


def main():
    snapshot_path, snapshot_rows = write_portfolio_snapshot()
    history_dates, rows_by_date = load_rows_by_date()

    latest_json = None
    latest_html = None
    latest_snapshot_rows = snapshot_rows
    latest_summary_rows = []
    for path in list_snapshot_files():
        snapshot_date = path.stem[-8:]
        cohort_rows, detail_rows, summary_rows, latest_summary = build_tracking_outputs(path, history_dates, rows_by_date)
        write_csv(portfolio_detail_path(snapshot_date), detail_fieldnames(), detail_rows)
        write_csv(portfolio_summary_path(snapshot_date), summary_fieldnames(), summary_rows)

        json_path = screener.WEB_REPORTS / f"{WEB_PREFIX}_{snapshot_date}.json"
        html_path = screener.WEB_REPORTS / f"{WEB_PREFIX}_{snapshot_date}.html"
        write_json(json_path, latest_summary)
        with open(html_path, "w", encoding="utf-8") as handle:
            handle.write(build_html(cohort_rows, summary_rows, latest_summary))

        latest_json = latest_summary
        latest_html = html_path
        latest_snapshot_rows = cohort_rows
        latest_summary_rows = summary_rows

    if latest_json is not None:
        write_json(screener.WEB_REPORTS / f"{WEB_PREFIX}_latest.json", latest_json)
        latest_html_alias = screener.WEB_REPORTS / f"{WEB_PREFIX}_latest.html"
        with open(latest_html_alias, "w", encoding="utf-8") as handle:
            handle.write(build_html(latest_snapshot_rows, latest_summary_rows, latest_json))

    print(f"[OK] Snapshot saved     -> {snapshot_path}")
    print(f"[OK] Snapshot members   -> {len(snapshot_rows)}")
    if latest_json is not None:
        latest_date = latest_json.get("snapshot_date", "")
        print(f"[OK] Latest tracking    -> {screener.WEB_REPORTS / f'{WEB_PREFIX}_{latest_date}.json'}")
        print(f"[OK] Latest tracking UI -> {latest_html}")


if __name__ == "__main__":
    main()
