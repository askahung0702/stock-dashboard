#!/usr/bin/env python3
"""
Early Breakout Forward Return Tracker
------------------------------------
Validate how "早期起漲" / "強勢續攻" candidates perform after each snapshot date.
"""

import argparse
import csv
import json
import statistics
from bisect import bisect_left
from datetime import datetime, timedelta
from pathlib import Path

import early_breakout_screener as screener


OUTPUT_PREFIX = "early_breakout_forward_returns"
TRACK_WEEKS = (1, 2, 3, 4)


def parse_date(date_str):
    return datetime.strptime(date_str, "%Y%m%d").date()


def format_metric(value, digits=2):
    if value is None:
        return ""
    return round(value, digits)


def first_available_on_or_after(sorted_dates, target):
    idx = bisect_left(sorted_dates, target)
    if idx >= len(sorted_dates):
        return None
    return sorted_dates[idx]


def latest_condition_file():
    files = screener.common_condition_files()
    if not files:
        raise FileNotFoundError("No early_breakout_common_conditions_*.csv found in history/")
    return files[-1][2]


def load_condition_stats():
    _, rows = screener.load_common_condition_file(latest_condition_file())
    normalized = []
    for row in rows.values():
        item = dict(row)
        item["key"] = item.get("condition_key", "")
        item["label"] = item.get("condition_label", "")
        item["hit_rate_pct"] = screener.fv(item, "hit_rate_pct", 0.0)
        normalized.append(item)
    return sorted(
        normalized,
        key=lambda item: (
            item.get("rule_role") != "guard",
            -screener.fv(item, "hit_rate_pct", 0.0),
            item.get("condition_key", ""),
        ),
    )


def screen_snapshot_rows(snapshot_rows, condition_stats, daily_series_by_code):
    working_rows = []
    for row in snapshot_rows:
        item = dict(row)
        screener.hydrate_alt_metrics(item, daily_series_by_code)
        working_rows.append(item)
    screened_2060 = screener.screen_candidates(
        working_rows, condition_stats, strict_mode=True, variant=screener.VARIANT_2060
    )
    screened_1854 = screener.screen_candidates(
        working_rows, condition_stats, strict_mode=True, variant=screener.VARIANT_1854
    )
    return screener.merge_screened_variants(screened_2060, screened_1854)


def enrich_forward_returns(detail_row, snapshot_date, base_price, code, rows_by_date, available_dates):
    snapshot_dt = parse_date(snapshot_date)
    for weeks in TRACK_WEEKS:
        label = f"{weeks}w"
        target_dt = snapshot_dt + timedelta(days=weeks * 7)
        actual_dt = first_available_on_or_after(available_dates, target_dt)
        detail_row[f"forward_{label}_target_date"] = target_dt.strftime("%Y%m%d")
        detail_row[f"forward_{label}_date"] = actual_dt.strftime("%Y%m%d") if actual_dt else ""
        detail_row[f"forward_{label}_delay_days"] = (actual_dt - target_dt).days if actual_dt else ""
        if not actual_dt:
            detail_row[f"forward_{label}_price"] = ""
            detail_row[f"forward_{label}_return_pct"] = ""
            continue
        future_row = rows_by_date.get(actual_dt.strftime("%Y%m%d"), {}).get(code)
        future_price = screener.fv(future_row or {}, "current_price", 0.0)
        detail_row[f"forward_{label}_price"] = format_metric(future_price)
        detail_row[f"forward_{label}_return_pct"] = (
            format_metric(screener.pct(base_price, future_price))
            if base_price > 0 and future_price > 0
            else ""
        )
    return detail_row


def iter_group_keys(detail_row):
    if detail_row.get("in_screen_2060") == "Y":
        yield ("ma2060", detail_row.get("screen_style_2060") or "unknown", "all")
    if detail_row.get("focus_candidate_2060") == "Y":
        yield ("ma2060", detail_row.get("screen_style_2060") or "unknown", "focus")
    if detail_row.get("in_screen_1854") == "Y":
        yield ("ma1854", detail_row.get("screen_style_1854") or "unknown", "all")
    if detail_row.get("focus_candidate_1854") == "Y":
        yield ("ma1854", detail_row.get("screen_style_1854") or "unknown", "focus")


def summarize_returns(values):
    if not values:
        return {
            "count": 0,
            "avg": "",
            "median": "",
            "win_rate_pct": "",
        }
    wins = sum(1 for value in values if value > 0)
    return {
        "count": len(values),
        "avg": format_metric(sum(values) / len(values)),
        "median": format_metric(statistics.median(values)),
        "win_rate_pct": format_metric(wins * 100.0 / len(values)),
    }


def build_summary_rows(detail_rows, include_snapshot_date=False):
    grouped = {}
    for row in detail_rows:
        for variant, style, scope in iter_group_keys(row):
            key = (row["snapshot_date"], variant, style, scope) if include_snapshot_date else (variant, style, scope)
            bucket = grouped.setdefault(key, {f"{weeks}w": [] for weeks in TRACK_WEEKS})
            for weeks in TRACK_WEEKS:
                value = row.get(f"forward_{weeks}w_return_pct")
                if value in ("", None):
                    continue
                bucket[f"{weeks}w"].append(float(value))

    summary_rows = []
    for key, bucket in sorted(grouped.items()):
        if include_snapshot_date:
            snapshot_date, variant, style, scope = key
        else:
            snapshot_date = ""
            variant, style, scope = key
        summary = {
            "snapshot_date": snapshot_date,
            "variant": variant,
            "variant_label": "MA18/54" if variant == "ma1854" else "MA20/60",
            "screen_style": style,
            "screen_style_label": "強勢續攻" if style == "continuation" else "早期起漲",
            "scope": scope,
            "scope_label": "Focus" if scope == "focus" else "全部候選",
        }
        max_count = 0
        for weeks in TRACK_WEEKS:
            result = summarize_returns(bucket[f"{weeks}w"])
            max_count = max(max_count, result["count"])
            summary[f"{weeks}w_count"] = result["count"]
            summary[f"{weeks}w_avg_return_pct"] = result["avg"]
            summary[f"{weeks}w_median_return_pct"] = result["median"]
            summary[f"{weeks}w_win_rate_pct"] = result["win_rate_pct"]
        summary["sample_count_max"] = max_count
        summary_rows.append(summary)
    summary_rows.sort(
        key=lambda item: (
            item["snapshot_date"] or "99999999",
            item["variant"] != "ma2060",
            item["screen_style"] != "early",
            item["scope"] != "all",
        )
    )
    return summary_rows


def detail_fieldnames():
    names = [
        "snapshot_date",
        "rank",
        "screen_grade",
        "focus_candidate",
        "screen_score",
        "selection_score",
        "buy_point_score",
        "code",
        "name",
        "market",
        "industry",
        "current_price",
        "in_screen_2060",
        "focus_candidate_2060",
        "screen_style_2060",
        "screen_style_2060_label",
        "in_screen_1854",
        "focus_candidate_1854",
        "screen_style_1854",
        "screen_style_1854_label",
        "primary_theme",
        "signal_type",
        "post_close_category",
        "structure_label",
        "turnaround_label",
        "matched_conditions",
        "screen_reason",
    ]
    for weeks in TRACK_WEEKS:
        label = f"{weeks}w"
        names.extend(
            [
                f"forward_{label}_target_date",
                f"forward_{label}_date",
                f"forward_{label}_delay_days",
                f"forward_{label}_price",
                f"forward_{label}_return_pct",
            ]
        )
    return names


def summary_fieldnames(include_snapshot_date=False):
    names = []
    if include_snapshot_date:
        names.append("snapshot_date")
    names.extend(
        [
            "variant",
            "variant_label",
            "screen_style",
            "screen_style_label",
            "scope",
            "scope_label",
            "sample_count_max",
        ]
    )
    for weeks in TRACK_WEEKS:
        label = f"{weeks}w"
        names.extend(
            [
                f"{label}_count",
                f"{label}_avg_return_pct",
                f"{label}_median_return_pct",
                f"{label}_win_rate_pct",
            ]
        )
    return names


def write_csv(path, fieldnames, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def build_frontend_summary(summary_rows, snapshot_dates, detail_rows):
    groups = {}
    for row in summary_rows:
        key = f"{row['variant']}_{row['screen_style']}_{row['scope']}"
        groups[key] = {
            "variant": row["variant"],
            "variant_label": row["variant_label"],
            "screen_style": row["screen_style"],
            "screen_style_label": row["screen_style_label"],
            "scope": row["scope"],
            "scope_label": row["scope_label"],
            "sample_count_max": row["sample_count_max"],
        }
        for weeks in TRACK_WEEKS:
            label = f"{weeks}w"
            groups[key][label] = {
                "count": row.get(f"{label}_count", 0),
                "avg_return_pct": row.get(f"{label}_avg_return_pct", ""),
                "median_return_pct": row.get(f"{label}_median_return_pct", ""),
                "win_rate_pct": row.get(f"{label}_win_rate_pct", ""),
            }

    return {
        "snapshot_start": snapshot_dates[0],
        "snapshot_end": snapshot_dates[-1],
        "snapshot_count": len(snapshot_dates),
        "candidate_rows": len(detail_rows),
        "weeks": list(TRACK_WEEKS),
        "latest_snapshot_pending": snapshot_dates[-1],
        "groups": groups,
    }


def write_json(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)


def main():
    parser = argparse.ArgumentParser(description="Track forward returns for early-breakout snapshots.")
    parser.add_argument("--start-date", help="Snapshot start date in YYYYMMDD.")
    parser.add_argument("--end-date", help="Snapshot end date in YYYYMMDD. Defaults to latest history date.")
    args = parser.parse_args()

    condition_stats = load_condition_stats()
    history_dates, rows_by_date, _ = screener.load_history_snapshots()
    _, daily_series_by_code = screener.load_daily_price_series()
    if not history_dates:
        raise SystemExit("No history snapshots found.")

    available_dates = [parse_date(date_str) for date_str in history_dates]
    start_date = args.start_date or history_dates[0]
    end_date = args.end_date or history_dates[-1]
    snapshot_dates = [date for date in history_dates if start_date <= date <= end_date]
    if not snapshot_dates:
        raise SystemExit("No snapshot dates matched the requested range.")

    detail_rows = []
    for snapshot_date in snapshot_dates:
        snapshot_rows = list(rows_by_date[snapshot_date].values())
        screened_rows = screen_snapshot_rows(snapshot_rows, condition_stats, daily_series_by_code)
        for row in screened_rows:
            detail = dict(row)
            detail["snapshot_date"] = snapshot_date
            enrich_forward_returns(
                detail,
                snapshot_date,
                screener.fv(row, "current_price", 0.0),
                screener.sv(row, "code"),
                rows_by_date,
                available_dates,
            )
            detail_rows.append(detail)

    overall_summary = build_summary_rows(detail_rows, include_snapshot_date=False)
    by_date_summary = build_summary_rows(detail_rows, include_snapshot_date=True)
    frontend_summary = build_frontend_summary(overall_summary, snapshot_dates, detail_rows)

    output_suffix = end_date
    detail_path = Path(screener.HISTORY) / f"{OUTPUT_PREFIX}_detail_{output_suffix}.csv"
    summary_path = Path(screener.HISTORY) / f"{OUTPUT_PREFIX}_summary_{output_suffix}.csv"
    by_date_path = Path(screener.HISTORY) / f"{OUTPUT_PREFIX}_by_date_{output_suffix}.csv"
    web_summary_path = Path(screener.WEB_REPORTS) / f"{OUTPUT_PREFIX}_{output_suffix}.json"
    web_latest_summary_path = Path(screener.WEB_REPORTS) / f"{OUTPUT_PREFIX}_latest.json"

    write_csv(detail_path, detail_fieldnames(), detail_rows)
    write_csv(summary_path, summary_fieldnames(include_snapshot_date=False), overall_summary)
    write_csv(by_date_path, summary_fieldnames(include_snapshot_date=True), by_date_summary)
    write_json(web_summary_path, frontend_summary)
    write_json(web_latest_summary_path, frontend_summary)

    print(f"[OK] Detail rows      -> {detail_path}")
    print(f"[OK] Overall summary  -> {summary_path}")
    print(f"[OK] By-date summary  -> {by_date_path}")
    print(f"[OK] Web summary      -> {web_summary_path}")
    print(f"[OK] Latest web sum.  -> {web_latest_summary_path}")
    print(f"[OK] Snapshot dates   -> {snapshot_dates[0]} to {snapshot_dates[-1]} ({len(snapshot_dates)} dates)")
    print(f"[OK] Candidate rows   -> {len(detail_rows)}")


if __name__ == "__main__":
    main()
