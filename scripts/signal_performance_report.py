#!/usr/bin/env python3
"""
Signal Performance Report
-------------------------
Build a tag/mode performance report from the staged SQLite snapshots.

Outputs:
- history/signal_snapshot_detail_YYYYMMDD.csv
- history/signal_forward_returns_YYYYMMDD.csv
- history/signal_performance_summary_YYYYMMDD.csv
- history/signal_performance_by_date_YYYYMMDD.csv
- web/performance/signal_performance_latest.json
"""

import argparse
import csv
import json
import re
import sqlite3
import statistics
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HISTORY = ROOT / "history"
WEB_PERFORMANCE = ROOT / "web" / "performance"
DB_PATH = HISTORY / "stock_history_db.sqlite"
HORIZONS = (1, 3, 5, 10, 20, 40)
STAGE_LABELS = {
    "intraday-close": "14:00",
    "close": "17:00",
    "official-chip": "20:15",
    "full": "23:00",
}
STAGE_ORDER = {
    "intraday-close": 1,
    "close": 2,
    "official-chip": 3,
    "full": 4,
}
CANONICAL_PRICE_STAGE_ORDER = ("full", "official-chip", "close", "intraday-close")


def n(row, *keys, default=0.0):
    for key in keys:
        value = row.get(key)
        if value in (None, ""):
            continue
        try:
            return float(value)
        except (TypeError, ValueError):
            continue
    return default


def s(row, *keys, default=""):
    for key in keys:
        value = row.get(key)
        if value not in (None, ""):
            return str(value)
    return default


def b(row, *keys):
    for key in keys:
        value = row.get(key)
        if isinstance(value, bool):
            return value
        if isinstance(value, (int, float)):
            return value != 0
        if isinstance(value, str):
            return value.strip().lower() in ("true", "1", "yes", "y")
    return False


def pct(base, future):
    if base <= 0 or future <= 0:
        return None
    return (future - base) * 100.0 / base


def round_metric(value, digits=2):
    if value is None:
        return ""
    return round(value, digits)


def split_tags(text):
    if not text:
        return []
    parts = re.split(r"[、,;|/]+", str(text))
    tags = []
    seen = set()
    for part in parts:
        tag = part.strip()
        if not tag or tag in seen:
            continue
        seen.add(tag)
        tags.append(tag)
    return tags


def has_confidence_data(row):
    return n(row, "dataConfidence", "data_confidence") > 0 or bool(
        s(row, "dataConfidenceReason", "data_confidence_reason")
    )


def is_prelaunch_mode(row):
    price = n(row, "price", "currentPrice", "current_price")
    ma20 = n(row, "movingAverage20", "ma20")
    ma60 = n(row, "movingAverage60", "ma60")
    ret20 = n(row, "return20DayPct", "return_20d_pct")
    ret60 = n(row, "return60DayPct", "return_60d_pct")
    vol = n(row, "volumeRatio", "volume_ratio")
    rsi = n(row, "rsi14")
    dd60 = n(row, "drawdownFromHigh60Pct", "drawdown_from_high60_pct")
    score = n(row, "score", "selectionScore", "selection_score")
    buy = n(row, "buyPointScore", "buy_point_score")
    fin = n(row, "financialQualityScore", "financial_quality_score")
    chips = n(row, "chipsScore", "chips_score")
    foreign = n(row, "latestForeignNetLots", "latest_foreign_net_lots")
    structure = s(row, "structureLabel", "structure_label")
    action = s(row, "postCloseAction", "post_close_action")
    return (
        price > 0 and ma20 > 0 and ma60 > 0 and price >= ma20 and ma20 >= ma60
        and structure == "整理待確認"
        and "優先研究" in action
        and score >= 70
        and buy >= 85
        and fin >= 14
        and chips >= 18
        and foreign > 0
        and 3 <= ret20 <= 15
        and ret60 >= 20
        and 0.8 <= vol <= 1.8
        and 45 <= rsi <= 60
        and -16 <= dd60 <= -8
    )


def is_high_win_mode(row):
    selection_qualified = b(row, "selectionQualified", "selection_qualified")
    confidence_ready = has_confidence_data(row)
    data_confidence = n(row, "dataConfidence", "data_confidence")
    financial_quality = n(row, "financialQualityScore", "financial_quality_score")
    selection_score = n(row, "score", "selectionScore", "selection_score")
    buy_point = n(row, "buyPointScore", "buy_point_score")
    structure_score = n(row, "structureScore", "structure_score")
    risk_reward = n(row, "riskRewardScore", "risk_reward_score")
    volume_ratio = n(row, "volumeRatio", "volume_ratio")
    drawdown = n(row, "drawdownFromHigh60Pct", "drawdown_from_high60_pct")
    return20 = n(row, "return20DayPct", "return_20d_pct")
    rsi = n(row, "rsi14")
    news_risk = n(row, "newsRiskScore", "news_risk_score")
    price = n(row, "price", "currentPrice", "current_price")
    ma20 = n(row, "movingAverage20", "ma20")
    structure = s(row, "structureLabel", "structure_label")
    excluded = (
        not selection_qualified
        or not confidence_ready
        or financial_quality < 12
        or data_confidence < 70
        or news_risk > 65
        or volume_ratio > 2.8
        or volume_ratio < 0.8
        or return20 > 25
        or rsi >= 75
        or structure == "追高風險"
        or (price > 0 and ma20 > 0 and price < ma20)
        or drawdown < -12
    )
    if excluded:
        return False
    return (
        data_confidence >= 75
        and financial_quality >= 12
        and selection_score >= 75
        and buy_point >= 78
        and structure_score >= 70
        and risk_reward >= 45
        and 0.9 <= volume_ratio <= 2.2
        and -8 <= drawdown <= 0
        and 50 <= rsi <= 68
        and news_risk <= 60
    )


def is_structure_edge_mode(row):
    regime = s(row, "marketRegime", "market_regime")
    bear = "空頭" in regime
    panic = "恐慌" in regime
    quality = n(row, "qualityScore", "quality_score")
    buy = n(row, "buyPointScore", "buy_point_score")
    selection = n(row, "score", "selectionScore", "selection_score")
    financial_quality = n(row, "financialQualityScore", "financial_quality_score")
    volume_ratio = n(row, "volumeRatio", "volume_ratio")
    return20 = n(row, "return20DayPct", "return_20d_pct")
    return60 = n(row, "return60DayPct", "return_60d_pct")
    rsi = n(row, "rsi14")
    news_risk = n(row, "newsRiskScore", "news_risk_score")
    structure = s(row, "structureLabel", "structure_label")
    event_direction = s(row, "eventDirection", "event_direction")
    return (
        b(row, "selectionQualified", "selection_qualified")
        and not panic
        and quality >= (75 if bear else 70)
        and buy >= (82 if bear else 78)
        and selection >= 72
        and financial_quality >= 14
        and 0.8 <= volume_ratio <= 2.5
        and return20 > 0
        and return60 > 0
        and (rsi <= 0 or rsi < 78)
        and news_risk < 60
        and "負向" not in event_direction
        and structure not in ("追高風險", "結構未完成")
    )


CATALYST_KEYWORDS = (
    "AI", "CoWoS", "HPC", "伺服器", "散熱", "矽光", "CPO", "機器人", "車用",
    "軍工", "航太", "電力", "重電", "半導體", "先進封裝", "漲價", "轉單",
)


def has_catalyst_keyword(text):
    source = str(text or "").lower()
    return any(keyword.lower() in source for keyword in CATALYST_KEYWORDS)


def has_turnaround_signal(row):
    label = s(row, "turnaroundLabel", "turnaround_label")
    return label in ("業績成長", "業績翻轉", "轉虧為盈", "高品質翻轉") or n(
        row, "turnaroundScore", "turnaround_score"
    ) >= 55


def is_catalyst_growth(row):
    revenue_strong = n(row, "revenueScore", "revenue_score") >= 20
    chips_strong = n(row, "chipsScore", "chips_score") >= 24
    strategy_ready = b(row, "selectionQualified", "selection_qualified") and n(
        row, "score", "selectionScore", "selection_score"
    ) >= 65
    buy = n(row, "buyPointScore", "buy_point_score")
    entry_healthy = 65 <= buy <= 92
    fund_flow_positive = (
        n(row, "latestInstitutionalNetRatioPct", "latest_institutional_net_ratio_pct") > 0
        or n(row, "fiveDayInstitutionalNetRatioPct", "five_day_institutional_net_ratio_pct") > 0
        or n(row, "brokerNetLots", "broker_net_lots") > 0
        or n(row, "brokerNetRatioPct", "broker_net_ratio_pct") > 0
    )
    catalyst_news = (
        has_catalyst_keyword(s(row, "newsSummary", "news_summary"))
        or has_catalyst_keyword(s(row, "buyPointReason", "buy_point_reason"))
        or has_catalyst_keyword(s(row, "scoreReason", "score_reason"))
    )
    return (
        revenue_strong
        and chips_strong
        and strategy_ready
        and entry_healthy
        and has_turnaround_signal(row)
        and fund_flow_positive
        and catalyst_news
    )


def signal_tags(row):
    tags = []
    for tag in split_tags(s(row, "launchTags", "launch_tags")):
        tags.append(("launch", tag, tag))
    if is_prelaunch_mode(row) and not any(label == "起漲前夜" for _, label, _ in tags):
        tags.append(("launch", "起漲前夜", "起漲前夜"))
    if is_high_win_mode(row):
        tags.append(("mode", "highWinMode", "highWinMode"))
    if is_structure_edge_mode(row):
        tags.append(("mode", "波段優勢", "波段優勢"))
    if is_catalyst_growth(row):
        tags.append(("mode", "催化成長", "催化成長"))
    if b(row, "likely"):
        tags.append(("flag", "Likely", "Likely"))
    if b(row, "selectionQualified", "selection_qualified"):
        tags.append(("flag", "觀察門檻", "觀察門檻"))
    category = s(row, "postCloseCategory", "post_close_category")
    if category:
        tags.append(("category", category, category))
    signal_type = s(row, "signalType", "signal_type")
    if signal_type:
        tags.append(("signal", signal_type, signal_type))

    result = []
    seen = set()
    for tag_type, label, display in tags:
        key = f"{tag_type}:{label}"
        if key in seen:
            continue
        seen.add(key)
        result.append((tag_type, label, display, key))
    return result


def load_snapshots(db_path, start_date=None, end_date=None):
    where = []
    params = []
    if start_date:
        where.append("trade_date >= ?")
        params.append(start_date)
    if end_date:
        where.append("trade_date <= ?")
        params.append(end_date)
    sql = (
        "select trade_date, stage, code, name, market, industry, score, "
        "selection_score, price, volume_ratio, likely, row_json, updated_at "
        "from daily_stock_analysis"
    )
    where.append("stage in ('intraday-close','close','official-chip','full')")
    if where:
        sql += " where " + " and ".join(where)
    sql += " order by trade_date, stage, sort_order, code"

    rows = []
    with sqlite3.connect(db_path) as con:
        for db_row in con.execute(sql, params):
            (
                trade_date,
                stage,
                code,
                name,
                market,
                industry,
                score,
                selection_score,
                price,
                volume_ratio,
                likely,
                row_json,
                updated_at,
            ) = db_row
            try:
                payload = json.loads(row_json)
            except json.JSONDecodeError:
                payload = {}
            payload.setdefault("date", trade_date)
            payload.setdefault("snapshotStage", stage)
            payload.setdefault("code", code)
            payload.setdefault("name", name)
            payload.setdefault("market", market)
            payload.setdefault("industry", industry)
            payload.setdefault("score", score)
            payload.setdefault("selectionScore", selection_score)
            payload.setdefault("price", price)
            payload.setdefault("currentPrice", price)
            payload.setdefault("volumeRatio", volume_ratio)
            payload.setdefault("likely", bool(likely))
            payload["_tradeDate"] = trade_date
            payload["_stage"] = stage
            payload["_updatedAt"] = updated_at
            rows.append(payload)
    return rows


def build_canonical_prices(rows):
    by_date_code_stage = defaultdict(dict)
    for row in rows:
        date = s(row, "_tradeDate", "date")
        code = s(row, "code")
        stage = s(row, "_stage", "snapshotStage")
        price = n(row, "price", "currentPrice", "current_price")
        if date and code and stage and price > 0:
            by_date_code_stage[(date, code)][stage] = price

    prices = {}
    dates = set()
    codes = set()
    for (date, code), stage_prices in by_date_code_stage.items():
        for stage in CANONICAL_PRICE_STAGE_ORDER:
            if stage in stage_prices:
                prices[(date, code)] = stage_prices[stage]
                dates.add(date)
                codes.add(code)
                break
    return sorted(dates), prices, codes


def enrich_forward(event, date_index_by_date, dates, canonical_prices):
    date = event["signal_date"]
    code = event["code"]
    base_price = event["signal_price"]
    idx = date_index_by_date.get(date)
    for horizon in HORIZONS:
        prefix = f"{horizon}d"
        if idx is None or idx + horizon >= len(dates):
            event[f"{prefix}_date"] = ""
            event[f"{prefix}_price"] = ""
            event[f"{prefix}_return_pct"] = ""
            event[f"{prefix}_max_drawdown_pct"] = ""
            continue
        future_date = dates[idx + horizon]
        future_price = canonical_prices.get((future_date, code), 0.0)
        ret = pct(base_price, future_price)
        path_returns = []
        for step_idx in range(idx + 1, idx + horizon + 1):
            step_price = canonical_prices.get((dates[step_idx], code), 0.0)
            step_ret = pct(base_price, step_price)
            if step_ret is not None:
                path_returns.append(step_ret)
        event[f"{prefix}_date"] = future_date
        event[f"{prefix}_price"] = round_metric(future_price)
        event[f"{prefix}_return_pct"] = round_metric(ret)
        event[f"{prefix}_max_drawdown_pct"] = round_metric(min(0.0, min(path_returns)) if path_returns else None)


def summarize_values(values):
    if not values:
        return {"count": 0, "win_rate_pct": "", "avg_return_pct": "", "median_return_pct": ""}
    wins = sum(1 for value in values if value > 0)
    return {
        "count": len(values),
        "win_rate_pct": round_metric(wins * 100.0 / len(values)),
        "avg_return_pct": round_metric(sum(values) / len(values)),
        "median_return_pct": round_metric(statistics.median(values)),
    }


def build_summary(events, include_date=False):
    grouped = defaultdict(lambda: {"event_count": 0, "returns": defaultdict(list), "drawdowns": defaultdict(list)})
    for event in events:
        base_key = (
            event["signal_date"] if include_date else "",
            event["stage"],
            event["stage_label"],
            event["tag_type"],
            event["tag"],
            event["tag_key"],
        )
        all_key = (
            event["signal_date"] if include_date else "",
            "ALL",
            "全部階段",
            event["tag_type"],
            event["tag"],
            event["tag_key"],
        )
        for key in (base_key, all_key):
            bucket = grouped[key]
            bucket["event_count"] += 1
            for horizon in HORIZONS:
                ret = event.get(f"{horizon}d_return_pct")
                dd = event.get(f"{horizon}d_max_drawdown_pct")
                if ret not in ("", None):
                    bucket["returns"][horizon].append(float(ret))
                if dd not in ("", None):
                    bucket["drawdowns"][horizon].append(float(dd))

    rows = []
    for key, bucket in grouped.items():
        signal_date, stage, stage_label, tag_type, tag, tag_key = key
        row = {
            "signal_date": signal_date,
            "stage": stage,
            "stage_label": stage_label,
            "tag_type": tag_type,
            "tag": tag,
            "tag_key": tag_key,
            "event_count": bucket["event_count"],
        }
        max_count = 0
        for horizon in HORIZONS:
            stats = summarize_values(bucket["returns"][horizon])
            max_count = max(max_count, stats["count"])
            row[f"{horizon}d_count"] = stats["count"]
            row[f"{horizon}d_win_rate_pct"] = stats["win_rate_pct"]
            row[f"{horizon}d_avg_return_pct"] = stats["avg_return_pct"]
            row[f"{horizon}d_median_return_pct"] = stats["median_return_pct"]
            dds = bucket["drawdowns"][horizon]
            row[f"{horizon}d_avg_max_drawdown_pct"] = round_metric(sum(dds) / len(dds)) if dds else ""
            row[f"{horizon}d_worst_max_drawdown_pct"] = round_metric(min(dds)) if dds else ""
        row["sample_count_max"] = max_count
        rows.append(row)

    rows.sort(
        key=lambda row: (
            row.get("signal_date") or "",
            STAGE_ORDER.get(row["stage"], 99) if row["stage"] != "ALL" else 0,
            -int(row["sample_count_max"]),
            row["tag_type"],
            row["tag"],
        )
    )
    return rows


def fieldnames_snapshot():
    return [
        "signal_date", "stage", "stage_label", "code", "name", "market", "industry",
        "price", "score", "selection_score", "buy_point_score", "post_close_category",
        "post_close_action", "signal_type", "launch_tags", "signal_tags", "tag_count",
    ]


def fieldnames_events():
    names = [
        "signal_date", "stage", "stage_label", "tag_type", "tag", "tag_key", "code",
        "name", "market", "industry", "signal_price", "score", "selection_score",
        "buy_point_score", "post_close_category", "post_close_action", "signal_type",
    ]
    for horizon in HORIZONS:
        names.extend(
            [
                f"{horizon}d_date",
                f"{horizon}d_price",
                f"{horizon}d_return_pct",
                f"{horizon}d_max_drawdown_pct",
            ]
        )
    return names


def fieldnames_summary(include_date=False):
    names = []
    if include_date:
        names.append("signal_date")
    names.extend(["stage", "stage_label", "tag_type", "tag", "tag_key", "event_count", "sample_count_max"])
    for horizon in HORIZONS:
        names.extend(
            [
                f"{horizon}d_count",
                f"{horizon}d_win_rate_pct",
                f"{horizon}d_avg_return_pct",
                f"{horizon}d_median_return_pct",
                f"{horizon}d_avg_max_drawdown_pct",
                f"{horizon}d_worst_max_drawdown_pct",
            ]
        )
    return names


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


def frontend_payload(summary_rows, by_date_rows, snapshot_rows, events, dates, start_date, end_date):
    latest_snapshot_date = max((row["signal_date"] for row in snapshot_rows), default=end_date)
    latest_snapshot_rows = [row for row in snapshot_rows if row["signal_date"] == latest_snapshot_date]
    latest_stage_counts = defaultdict(int)
    for row in latest_snapshot_rows:
        latest_stage_counts[row["stage_label"]] += 1

    top_summary = sorted(
        summary_rows,
        key=lambda row: (
            row["stage"] != "ALL",
            -int(row.get("20d_count") or row.get("10d_count") or row.get("sample_count_max") or 0),
            -(float(row.get("10d_avg_return_pct") or row.get("3d_avg_return_pct") or 0)),
        ),
    )[:300]

    return {
        "generated_at": __import__("datetime").datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "snapshot_start": start_date,
        "snapshot_end": end_date,
        "price_dates": dates,
        "horizons": list(HORIZONS),
        "snapshot_rows": len(snapshot_rows),
        "signal_events": len(events),
        "latest_snapshot_date": latest_snapshot_date,
        "latest_stage_counts": dict(sorted(latest_stage_counts.items())),
        "summary": top_summary,
        "by_date": by_date_rows[-600:],
        "latest_signals": latest_snapshot_rows[:1000],
    }


def build_report(args):
    rows = load_snapshots(args.db, args.start_date, args.end_date)
    if not rows:
        raise SystemExit("No daily_stock_analysis rows found.")

    dates, canonical_prices, _ = build_canonical_prices(rows)
    if not dates:
        raise SystemExit("No canonical price dates found.")
    date_index = {date: idx for idx, date in enumerate(dates)}

    snapshot_rows = []
    events = []
    for row in rows:
        date = s(row, "_tradeDate", "date")
        stage = s(row, "_stage", "snapshotStage")
        code = s(row, "code")
        price = n(row, "price", "currentPrice", "current_price")
        tags = signal_tags(row)
        tag_labels = [tag[1] for tag in tags]
        snapshot = {
            "signal_date": date,
            "stage": stage,
            "stage_label": STAGE_LABELS.get(stage, stage or "未標示"),
            "code": code,
            "name": s(row, "name"),
            "market": s(row, "market"),
            "industry": s(row, "industry"),
            "price": round_metric(price),
            "score": round_metric(n(row, "score")),
            "selection_score": round_metric(n(row, "selectionScore", "selection_score")),
            "buy_point_score": round_metric(n(row, "buyPointScore", "buy_point_score")),
            "post_close_category": s(row, "postCloseCategory", "post_close_category"),
            "post_close_action": s(row, "postCloseAction", "post_close_action"),
            "signal_type": s(row, "signalType", "signal_type"),
            "launch_tags": s(row, "launchTags", "launch_tags"),
            "signal_tags": "、".join(tag_labels),
            "tag_count": len(tags),
        }
        snapshot_rows.append(snapshot)
        if price <= 0:
            continue
        for tag_type, tag, _display, tag_key in tags:
            event = {
                "signal_date": date,
                "stage": stage,
                "stage_label": STAGE_LABELS.get(stage, stage or "未標示"),
                "tag_type": tag_type,
                "tag": tag,
                "tag_key": tag_key,
                "code": code,
                "name": s(row, "name"),
                "market": s(row, "market"),
                "industry": s(row, "industry"),
                "signal_price": round_metric(price),
                "score": round_metric(n(row, "score")),
                "selection_score": round_metric(n(row, "selectionScore", "selection_score")),
                "buy_point_score": round_metric(n(row, "buyPointScore", "buy_point_score")),
                "post_close_category": snapshot["post_close_category"],
                "post_close_action": snapshot["post_close_action"],
                "signal_type": snapshot["signal_type"],
            }
            enrich_forward(event, date_index, dates, canonical_prices)
            events.append(event)

    end_date = args.end_date or max(dates)
    start_date = args.start_date or min(dates)
    summary_rows = build_summary(events, include_date=False)
    by_date_rows = build_summary(events, include_date=True)
    payload = frontend_payload(summary_rows, by_date_rows, snapshot_rows, events, dates, start_date, end_date)

    suffix = end_date
    write_csv(HISTORY / f"signal_snapshot_detail_{suffix}.csv", fieldnames_snapshot(), snapshot_rows)
    write_csv(HISTORY / f"signal_forward_returns_{suffix}.csv", fieldnames_events(), events)
    write_csv(HISTORY / f"signal_performance_summary_{suffix}.csv", fieldnames_summary(False), summary_rows)
    write_csv(HISTORY / f"signal_performance_by_date_{suffix}.csv", fieldnames_summary(True), by_date_rows)
    write_json(WEB_PERFORMANCE / f"signal_performance_{suffix}.json", payload)
    write_json(WEB_PERFORMANCE / "signal_performance_latest.json", payload)

    print(f"[OK] Snapshot detail -> history/signal_snapshot_detail_{suffix}.csv ({len(snapshot_rows)} rows)")
    print(f"[OK] Forward returns -> history/signal_forward_returns_{suffix}.csv ({len(events)} rows)")
    print(f"[OK] Summary         -> history/signal_performance_summary_{suffix}.csv ({len(summary_rows)} rows)")
    print(f"[OK] By date         -> history/signal_performance_by_date_{suffix}.csv ({len(by_date_rows)} rows)")
    print("[OK] Web JSON        -> web/performance/signal_performance_latest.json")


def main():
    parser = argparse.ArgumentParser(description="Build signal/tag performance report.")
    parser.add_argument("--db", default=str(DB_PATH), help="SQLite database path.")
    parser.add_argument("--start-date", help="YYYYMMDD start date.")
    parser.add_argument("--end-date", help="YYYYMMDD end date.")
    args = parser.parse_args()
    build_report(args)


if __name__ == "__main__":
    main()
