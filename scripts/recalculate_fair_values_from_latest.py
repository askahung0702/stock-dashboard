import json
import sqlite3
import csv
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LATEST_PATHS = [
    ROOT / "web" / "data" / "latest.json",
    ROOT / "site" / "web" / "data" / "latest.json",
]
CACHE_PATH = ROOT / "history" / "low_frequency_cache.json"
HISTORY_JSON_PATH = ROOT / "history" / "stock_history_db.json"
SQLITE_PATH = ROOT / "history" / "stock_history_db.sqlite"


def number(value, default=0.0):
    if value is None:
        return default
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def clamp(value, low, high):
    return max(low, min(high, value))


def fmt(value):
    return f"{value:.2f}"


def signed(value):
    return ("+" if value > 0 else "") + fmt(value)


def contains_any(text, *keywords):
    text = text or ""
    return any(keyword in text for keyword in keywords)


def fair_value_style(industry, latest_eps_yoy, avg_3m_revenue_yoy, peg, roe, book_value):
    if (
        contains_any(industry, "半導體", "電子零組件", "電腦及週邊", "電腦週邊", "光電", "通信網路", "通訊網路", "其他電子")
        or latest_eps_yoy >= 20
        or avg_3m_revenue_yoy >= 12
        or (peg > 0 and peg <= 1.2)
    ):
        return "growth"
    if contains_any(industry, "鋼鐵", "塑膠", "玻璃", "造紙", "橡膠", "航運", "油電燃氣", "化學", "紡織"):
        return "cyclical"
    if book_value > 0 and roe >= 8:
        return "stable"
    return "balanced"


def market_discount(payload):
    regime = str((payload.get("marketAdvisor") or {}).get("regime") or payload.get("marketRegime") or "")
    label = str(payload.get("marketRegimeLabel") or "")
    if regime == "BEAR_CORRECTION" or "空頭修正" in label:
        return 0.94
    if regime == "PANIC_SELLOFF" or "恐慌" in label:
        return 0.88
    if regime == "RANGE_BOUND" or "區間" in label:
        return 0.97
    return 1.0


def confidence_score(core_count, data_confidence, financial_quality, valuation_score, selection_qualified, support_notes, non_op, latest_eps_yoy):
    confidence = (
        40
        + min(24, core_count * 8)
        + min(18, data_confidence * 0.18)
        + min(12, financial_quality * 0.55)
        + min(8, valuation_score * 0.35)
        + (4 if selection_qualified else 0)
    )
    if support_notes:
        confidence += 3
    if non_op > 25:
        confidence -= 6
    if latest_eps_yoy < -10:
        confidence -= 5
    return clamp(confidence, 35, 92)


def recalculate_row(row, cache, regime_discount):
    current_price = number(row.get("price"))
    if current_price <= 0:
        return False

    trailing_eps = number(cache.get("trailingFourQuarterEps"))
    latest_eps = number(cache.get("latestQuarterEps"))
    previous_eps = number(cache.get("previousQuarterEps"))
    two_quarter_annualized_eps = (latest_eps + previous_eps) * 2
    fair_value_eps = trailing_eps * 0.40 + two_quarter_annualized_eps * 0.60

    peer_average_pe = number(cache.get("peerAveragePe"))
    latest_eps_yoy = number(cache.get("latestQuarterEpsYoYPct"))
    avg_3m_revenue_yoy = number(cache.get("averageThreeMonthRevenueYoY"))
    roe = number(cache.get("returnOnEquityPct"))
    book_value = number(cache.get("bookValue"))
    non_op = number(cache.get("nonOperatingRatioPct"))
    financial_quality = number(row.get("financialQualityScore"))
    valuation_score = number(row.get("valuationScore"))
    peg = number(row.get("peg"))
    data_confidence = number(row.get("dataConfidence"))
    selection_qualified = bool(row.get("selectionQualified"))

    style = fair_value_style(row.get("industry") or cache.get("industry"), latest_eps_yoy, avg_3m_revenue_yoy, peg, roe, book_value)
    peer_weight = 0.50 if style == "growth" else 0.40 if style == "stable" else 0.55 if style == "cyclical" else 0.40
    peg_weight = 0.45 if style == "growth" else 0.20 if style == "stable" else 0.10 if style == "cyclical" else 0.25
    pb_weight = 0.40 if style == "stable" else 0.35 if style == "cyclical" else 0.25

    core_values = []
    core_weights = []
    method_notes = []
    support_notes = []

    if fair_value_eps > 0 and peer_average_pe > 0:
        peer_factor = 1.0
        if latest_eps_yoy >= 25:
            peer_factor += 0.08
        elif latest_eps_yoy < 0:
            peer_factor -= 0.08
        if financial_quality >= 15:
            peer_factor += 0.05
        elif financial_quality < 8:
            peer_factor -= 0.05
        if non_op > 25:
            peer_factor -= 0.06
        target_pe = clamp(peer_average_pe * peer_factor * regime_discount, 8, 36)
        core_values.append(fair_value_eps * target_pe)
        core_weights.append(peer_weight)
        method_notes.append(f"同業PE {fmt(target_pe)}倍")

    if fair_value_eps > 0 and latest_eps_yoy > 0:
        target_peg = 0.95 if style == "growth" else 0.8 if style == "stable" else 0.7
        if financial_quality >= 15:
            target_peg += 0.05
        target_peg = clamp(target_peg * regime_discount, 0.6, 1.05)
        target_pe = clamp(latest_eps_yoy * target_peg, 10, 40)
        core_values.append(fair_value_eps * target_pe)
        core_weights.append(peg_weight)
        method_notes.append(f"PEG 推估 {fmt(target_pe)}倍")

    if book_value > 0 and roe > 0:
        required_return = 11 if financial_quality >= 15 else 12 if financial_quality >= 10 else 14
        justified_pb = clamp(roe / required_return, 0.6, 3.8) * regime_discount
        if non_op > 25:
            justified_pb *= 0.94
        pb_value = book_value * justified_pb
        recovery_priced = fair_value_eps <= 0 and (latest_eps_yoy > 0 or avg_3m_revenue_yoy > 0)
        if (style == "growth" and core_values) or (recovery_priced and pb_value < current_price * 0.6):
            support_notes.append(f"PB/ROE {fmt(justified_pb)}倍僅作資產面輔助")
        else:
            core_values.append(pb_value)
            core_weights.append(pb_weight)
            method_notes.append(f"PB/ROE {fmt(justified_pb)}倍")

    if not core_values and fair_value_eps <= 0 and (latest_eps_yoy > 0 or avg_3m_revenue_yoy > 0):
        recovery_factor = 0.92
        if latest_eps_yoy > 0:
            recovery_factor += min(0.05, latest_eps_yoy / 1000)
        if avg_3m_revenue_yoy > 0:
            recovery_factor += min(0.06, avg_3m_revenue_yoy * 0.004)
        if financial_quality >= 12:
            recovery_factor += 0.04
        elif financial_quality < 8:
            recovery_factor -= 0.08
        if 0 < roe < 2:
            recovery_factor -= 0.06
        if non_op > 25:
            recovery_factor -= 0.04
        recovery_factor = clamp(recovery_factor * regime_discount, 0.72, 1.08)
        core_values.append(current_price * recovery_factor)
        core_weights.append(0.65)
        method_notes.append(f"復甦期市場定價 {fmt(recovery_factor)}倍")

    if not core_values:
        return False

    weight_sum = sum(core_weights)
    base_price = sum(v * w for v, w in zip(core_values, core_weights)) / weight_sum
    min_value = min(core_values)
    max_value = max(core_values)
    confidence = confidence_score(
        len(core_values),
        data_confidence,
        financial_quality,
        valuation_score,
        selection_qualified,
        support_notes,
        non_op,
        latest_eps_yoy,
    )
    band_pct = max(8, min(24, 22 - confidence * 0.12 + (3 - len(core_values)) * 2.5))
    low_price = min(base_price * (1 - band_pct / 100), min_value * 0.98)
    high_price = max(base_price * (1 + band_pct / 100), max_value * 1.02)
    low_price = clamp(low_price, 0, high_price)
    base_price = clamp(base_price, low_price, high_price)
    gap_pct = (base_price - current_price) * 100 / current_price

    method = (
        "復甦期參考估值"
        if fair_value_eps <= 0 and method_notes and "復甦期市場定價" in method_notes[0]
        else "成長混合估值"
        if style == "growth"
        else "品質資產混合估值"
        if style == "stable"
        else "循環股混合估值"
        if style == "cyclical"
        else "均衡混合估值"
    )
    support_text = "" if not support_notes else "；" + "、".join(support_notes)
    reason = (
        f"估值EPS {fmt(fair_value_eps)}（近四季 {fmt(trailing_eps)}×40% + "
        f"近兩季年化 {fmt(two_quarter_annualized_eps)}×60%）；以 "
        f"{'、'.join(method_notes)} 綜合估算{support_text}，合理價中位 {fmt(base_price)}，"
        f"相對現價 {signed(gap_pct)}%，信心 {fmt(confidence)} 分"
    )

    row["fairValueLow"] = low_price
    row["fairValueBase"] = base_price
    row["fairValueHigh"] = high_price
    row["fairValueConfidence"] = confidence
    row["fairValueMethod"] = method
    row["fairValueReason"] = reason
    return True


def update_latest(path, cache_entries):
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    discount = market_discount(payload)
    updated = 0
    skipped = 0
    for row in payload.get("rows", []):
        cache = cache_entries.get(str(row.get("code"))) or {}
        if cache and recalculate_row(row, cache, discount):
            updated += 1
        else:
            skipped += 1
    path.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return updated, skipped


def update_history_json(cache_entries):
    if not HISTORY_JSON_PATH.exists():
        return 0, 0, ""
    payload = json.loads(HISTORY_JSON_PATH.read_text(encoding="utf-8-sig"))
    snapshots = payload.get("snapshots") or {}
    if not snapshots:
        return 0, 0, ""
    latest_date = max(snapshots.keys())
    latest_snapshot = snapshots.get(latest_date) or {}
    discount = market_discount({"marketRegimeLabel": "空頭修正"})
    updated = 0
    skipped = 0
    for row in latest_snapshot.get("rows", []):
        cache = cache_entries.get(str(row.get("code"))) or {}
        if cache and recalculate_row(row, cache, discount):
            updated += 1
        else:
            skipped += 1
    HISTORY_JSON_PATH.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return updated, skipped, latest_date


def update_sqlite(cache_entries):
    if not SQLITE_PATH.exists():
        return []
    results = []
    con = sqlite3.connect(str(SQLITE_PATH))
    try:
        cur = con.cursor()
        latest_snapshot_date = cur.execute("select max(snapshot_date) from snapshot_rows").fetchone()[0]
        if latest_snapshot_date:
            updated = 0
            skipped = 0
            rows = cur.execute(
                "select code, row_json from snapshot_rows where snapshot_date = ?", (latest_snapshot_date,)
            ).fetchall()
            for code, row_json in rows:
                row = json.loads(row_json)
                cache = cache_entries.get(str(code)) or {}
                if cache and recalculate_row(row, cache, 0.94):
                    cur.execute(
                        "update snapshot_rows set row_json = ? where snapshot_date = ? and code = ?",
                        (json.dumps(row, ensure_ascii=False, separators=(",", ":")), latest_snapshot_date, code),
                    )
                    updated += 1
                else:
                    skipped += 1
            results.append((f"sqlite snapshot_rows {latest_snapshot_date}", updated, skipped))

        latest_full_date = cur.execute(
            "select max(trade_date) from daily_stock_analysis where stage = 'full'"
        ).fetchone()[0]
        if latest_full_date:
            updated = 0
            skipped = 0
            rows = cur.execute(
                "select code, row_json from daily_stock_analysis where trade_date = ? and stage = 'full'",
                (latest_full_date,),
            ).fetchall()
            for code, row_json in rows:
                row = json.loads(row_json)
                cache = cache_entries.get(str(code)) or {}
                if cache and recalculate_row(row, cache, 0.94):
                    cur.execute(
                        "update daily_stock_analysis set row_json = ? where trade_date = ? and stage = 'full' and code = ?",
                        (json.dumps(row, ensure_ascii=False, separators=(",", ":")), latest_full_date, code),
                    )
                    updated += 1
                else:
                    skipped += 1
            results.append((f"sqlite daily_stock_analysis/full {latest_full_date}", updated, skipped))
        con.commit()
    finally:
        con.close()
    return results


def update_latest_csv_reports(latest_rows_by_code):
    candidates = []
    latest_json = json.loads((ROOT / "web" / "data" / "latest.json").read_text(encoding="utf-8-sig"))
    latest_date = str(latest_json.get("date") or "")
    prev_date = str(latest_json.get("prevDate") or "")
    if latest_date:
        candidates.append(ROOT / "history" / f"stock_candidates_{latest_date}.csv")
    if prev_date:
        candidates.extend((ROOT / "daily_snapshots").glob(f"stock_candidates*_{prev_date}.csv"))

    fields = {
        "fair_value_low": "fairValueLow",
        "fair_value_base": "fairValueBase",
        "fair_value_high": "fairValueHigh",
        "fair_value_confidence": "fairValueConfidence",
        "fair_value_method": "fairValueMethod",
        "fair_value_reason": "fairValueReason",
    }
    results = []
    for path in sorted(set(candidates)):
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8-sig")
        rows = list(csv.DictReader(text.splitlines()))
        if not rows:
            continue
        fieldnames = list(rows[0].keys())
        if not all(name in fieldnames for name in fields):
            continue
        updated = 0
        for row in rows:
            latest = latest_rows_by_code.get(str(row.get("code") or ""))
            if not latest:
                continue
            for csv_name, json_name in fields.items():
                value = latest.get(json_name)
                if isinstance(value, float):
                    row[csv_name] = fmt(value)
                else:
                    row[csv_name] = "" if value is None else str(value)
            updated += 1
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(rows)
        results.append((path, updated))
    return results


def main():
    cache_payload = json.loads(CACHE_PATH.read_text(encoding="utf-8-sig"))
    cache_entries = cache_payload.get("entries", {})
    for path in LATEST_PATHS:
        if not path.exists():
            continue
        updated, skipped = update_latest(path, cache_entries)
        print(f"{path}: updated={updated} skipped={skipped}")
    updated, skipped, date = update_history_json(cache_entries)
    if date:
        print(f"{HISTORY_JSON_PATH} snapshot {date}: updated={updated} skipped={skipped}")
    for label, updated, skipped in update_sqlite(cache_entries):
        print(f"{label}: updated={updated} skipped={skipped}")
    latest_payload = json.loads((ROOT / "web" / "data" / "latest.json").read_text(encoding="utf-8-sig"))
    latest_rows_by_code = {str(row.get("code")): row for row in latest_payload.get("rows", [])}
    for path, updated in update_latest_csv_reports(latest_rows_by_code):
        print(f"{path}: csv fair values updated={updated}")


if __name__ == "__main__":
    main()
