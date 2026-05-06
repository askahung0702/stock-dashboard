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


def quality_discount(latest_ocf, latest_fcf, positive_ocf_quarters, positive_fcf_quarters, debt_ratio, current_ratio, non_op, style):
    discount = 1.0
    notes = []
    if latest_ocf < 0:
        discount *= 0.93
        notes.append("營業現金流為負")
    if latest_fcf < 0:
        discount *= 0.94
        notes.append("自由現金流為負")
    if 0 < positive_ocf_quarters <= 1:
        discount *= 0.96
        notes.append("營業現金流季數偏少")
    if 0 < positive_fcf_quarters <= 1:
        discount *= 0.97
        notes.append("自由現金流季數偏少")
    if debt_ratio >= 70:
        discount *= 0.88
        notes.append("負債比偏高")
    elif debt_ratio >= 60:
        discount *= 0.93
        notes.append("負債比偏高")
    if 0 < current_ratio < 1.2:
        discount *= 0.95
        notes.append("流動比偏低")
    if non_op > 35:
        discount *= 0.90
        notes.append("非營業依賴偏高")
    elif non_op > 25:
        discount *= 0.95
        notes.append("非營業依賴偏高")
    return clamp(discount, 0.45, 1.0), notes


def quality_pe_cap(style, risk_count, debt_ratio, non_op, latest_ocf, latest_fcf):
    cap = 36 if style == "growth" else 30 if style == "stable" else 32
    if risk_count >= 5:
        cap = min(cap, 20)
    elif risk_count >= 3:
        cap = min(cap, 24)
    elif risk_count >= 2:
        cap = min(cap, 28)
    if debt_ratio >= 70:
        cap = min(cap, 22)
    elif debt_ratio >= 60:
        cap = min(cap, 26)
    if non_op > 35:
        cap = min(cap, 22)
    elif non_op > 25:
        cap = min(cap, 26)
    if latest_ocf < 0 and latest_fcf < 0:
        cap = min(cap, 24)
    return clamp(cap, 14, 40)


def normalize_industry(industry):
    text = str(industry or "").strip()
    if not text:
        return "其他"
    if text.startswith("櫃") and len(text) > 1:
        text = text[1:]
    return text


def median(sorted_values):
    size = len(sorted_values)
    if size == 0:
        return 0.0
    mid = size // 2
    if size % 2:
        return sorted_values[mid]
    return (sorted_values[mid - 1] + sorted_values[mid]) / 2


def trimmed_mean(sorted_values):
    if not sorted_values:
        return 0.0
    trim = max(0, len(sorted_values) // 10)
    subset = sorted_values[trim : len(sorted_values) - trim] if trim else sorted_values
    if not subset:
        subset = sorted_values
    return sum(subset) / len(subset)


def build_peer_stats(rows, cache_entries):
    grouped_pe = {}
    grouped_pb = {}
    grouped_roe = {}
    labels = {}
    for row in rows or []:
        code = str(row.get("code") or "")
        cache = cache_entries.get(code) or {}
        industry = row.get("industry") or cache.get("industry")
        industry_key = peer_industry_key(industry)
        refined_key = peer_refined_key(industry, row.get("primaryTheme"))
        trailing_pe = number(row.get("trailingPe"))
        if trailing_pe <= 0:
            trailing_eps = number(cache.get("trailingFourQuarterEps"))
            price = number(row.get("price"))
            trailing_pe = price / trailing_eps if trailing_eps > 0 else 0
        if 3 <= trailing_pe <= 80:
            add_peer_metric(grouped_pe, labels, industry_key, peer_industry_label(industry), trailing_pe)
            if refined_key != industry_key:
                add_peer_metric(grouped_pe, labels, refined_key, peer_refined_label(industry, row.get("primaryTheme")), trailing_pe)

        price = number(row.get("price"))
        book_value = number(cache.get("bookValue"))
        if price > 0 and book_value > 0:
            pb = price / book_value
            if 0.2 <= pb <= 10:
                add_peer_metric(grouped_pb, labels, industry_key, peer_industry_label(industry), pb)
                if refined_key != industry_key:
                    add_peer_metric(grouped_pb, labels, refined_key, peer_refined_label(industry, row.get("primaryTheme")), pb)
        roe = number(cache.get("returnOnEquityPct"))
        if 0 < roe <= 80:
            add_peer_metric(grouped_roe, labels, industry_key, peer_industry_label(industry), roe)
            if refined_key != industry_key:
                add_peer_metric(grouped_roe, labels, refined_key, peer_refined_label(industry, row.get("primaryTheme")), roe)

    stats = {}
    for industry, values in grouped_pe.items():
        values = sorted(values)
        if len(values) < 5:
            continue
        med = median(values)
        trim = trimmed_mean(values)
        anchor = clamp(med * 0.65 + trim * 0.35, 6, 45)
        pb_values = sorted(grouped_pb.get(industry) or [])
        median_pb = median(pb_values) if len(pb_values) >= 5 else 0
        trimmed_pb = trimmed_mean(pb_values) if len(pb_values) >= 5 else 0
        anchor_pb = clamp(median_pb * 0.70 + trimmed_pb * 0.30, 0.4, 5.5) if len(pb_values) >= 5 else 0
        roe_values = sorted(grouped_roe.get(industry) or [])
        median_roe = median(roe_values) if len(roe_values) >= 5 else 0
        stats[industry] = {
            "count": len(values),
            "median": med,
            "trimmed": trim,
            "anchor": anchor,
            "pb_count": len(pb_values),
            "median_pb": median_pb,
            "trimmed_pb": trimmed_pb,
            "anchor_pb": anchor_pb,
            "median_roe": median_roe,
            "label": labels.get(industry, industry),
        }
    return stats


def normalize_theme(theme):
    text = str(theme or "").strip()
    return "" if text in ("", "一般", "其他") else text


def peer_industry_key(industry):
    return "I:" + normalize_industry(industry)


def peer_refined_key(industry, theme):
    normalized_industry = normalize_industry(industry)
    normalized_theme = normalize_theme(theme)
    return "I:" + normalized_industry if not normalized_theme else "T:" + normalized_industry + "|" + normalized_theme


def peer_industry_label(industry):
    return normalize_industry(industry)


def peer_refined_label(industry, theme):
    normalized_industry = normalize_industry(industry)
    normalized_theme = normalize_theme(theme)
    return normalized_industry if not normalized_theme else normalized_industry + "/" + normalized_theme


def add_peer_metric(grouped, labels, key, label, value):
    grouped.setdefault(key, []).append(value)
    labels[key] = label


def select_peer_stats(peer_stats, row, cache):
    if not peer_stats:
        return None
    industry = row.get("industry") or cache.get("industry")
    refined = peer_stats.get(peer_refined_key(industry, row.get("primaryTheme")))
    if refined and refined.get("count", 0) >= 5:
        return refined
    return peer_stats.get(peer_industry_key(industry))


def apply_peer_comparison(row, cache, peer_stats, base_price, low_price, high_price, confidence, fair_value_eps):
    current_price = number(row.get("price"))
    trailing_eps = number(cache.get("trailingFourQuarterEps"))
    if current_price <= 0 or trailing_eps <= 0 or base_price <= 0:
        return base_price, low_price, high_price, confidence, ""

    stats = select_peer_stats(peer_stats, row, cache)
    if not stats or stats["count"] < 5 or stats["anchor"] <= 0:
        return base_price, low_price, high_price, confidence, ""

    earnings_base = fair_value_eps if fair_value_eps > 0 else trailing_eps
    peer_pe_value = earnings_base * stats["anchor"]
    peer_pb_value = 0
    book_value = number(cache.get("bookValue"))
    if book_value > 0 and stats.get("pb_count", 0) >= 5 and stats.get("anchor_pb", 0) > 0:
        roe_factor = 1.0
        roe = number(cache.get("returnOnEquityPct"))
        median_roe = number(stats.get("median_roe"))
        if roe > 0 and median_roe > 0:
            roe_factor = clamp(roe / median_roe, 0.75, 1.25)
        peer_pb_value = book_value * stats["anchor_pb"] * roe_factor
    peer_value = peer_pe_value * 0.70 + peer_pb_value * 0.30 if peer_pb_value > 0 else peer_pe_value
    if peer_value <= 0:
        return base_price, low_price, high_price, confidence, ""

    divergence = max(base_price / peer_value, peer_value / base_price)
    peer_weight = 0.45 if stats["count"] >= 20 else 0.38 if stats["count"] >= 10 else 0.30
    industry_text = str(row.get("industry") or cache.get("industry") or "")
    growth_like = fair_value_style(
        industry_text,
        number(cache.get("latestQuarterEpsYoYPct")),
        number(cache.get("averageThreeMonthRevenueYoY")),
        number(row.get("peg")),
        number(cache.get("returnOnEquityPct")),
        number(cache.get("bookValue")),
    ) == "growth"
    if growth_like and divergence < 2.5:
        peer_weight = min(peer_weight, 0.32)
    if divergence >= 2.5:
        peer_weight += 0.15
    if number(row.get("dataConfidence")) < 70:
        peer_weight -= 0.08
    peer_weight = clamp(peer_weight, 0.22, 0.62)

    weak_quality = (
        number(cache.get("latestOperatingCashFlow")) < 0
        or number(cache.get("latestFreeCashFlow")) < 0
        or number(cache.get("debtRatioPct")) >= 60
        or number(cache.get("nonOperatingRatioPct")) > 25
    )
    adjusted_base = base_price * (1 - peer_weight) + peer_value * peer_weight
    latest_eps_yoy = number(cache.get("latestQuarterEpsYoYPct"))
    if base_price > peer_value * 3:
        upper_multiple = 2.8 if latest_eps_yoy >= 80 else 2.4 if latest_eps_yoy >= 30 else 2.0
        adjusted_base = min(adjusted_base, peer_value * upper_multiple)
    elif peer_value > base_price * 3:
        adjusted_base = max(adjusted_base, peer_value * 0.45)
    asset_cap_applied = False
    if weak_quality and peer_pb_value > 0 and adjusted_base > peer_pb_value * 2.8:
        adjusted_base = peer_pb_value * 2.8
        asset_cap_applied = True

    band_pct = 0.16
    if base_price > 0 and low_price > 0 and high_price > 0:
        band_pct = max((base_price - low_price) / base_price, (high_price - base_price) / base_price)
    band_pct = clamp(band_pct, 0.08, 0.24)
    conservative = min(adjusted_base * (1 - band_pct), peer_value * 0.95)
    conservative = clamp(conservative, adjusted_base * 0.68, adjusted_base)
    bull = max(adjusted_base, adjusted_base * (1 + band_pct))
    if weak_quality:
        bull = min(bull, adjusted_base * 1.12)
    elif growth_like and latest_eps_yoy >= 30:
        bull = max(bull, min(base_price, adjusted_base * 1.22))
    low_price = clamp(conservative, 0, adjusted_base)
    high_price = max(adjusted_base, bull)
    confidence += 3 if stats["count"] >= 10 else 1
    if divergence >= 3:
        confidence -= 4
    confidence = clamp(confidence, 35, 92)
    note = (
        f"；同業比較({stats.get('label', '')})：有效樣本 {stats['count']} 檔，PE中位 {fmt(stats['median'])}倍、"
        f"修剪平均 {fmt(stats['trimmed'])}倍，估值EPS估值 {fmt(peer_pe_value)}"
    )
    if peer_pb_value > 0:
        note += f"，PB中位 {fmt(stats['median_pb'])}倍、PB/ROE估值 {fmt(peer_pb_value)}"
    if asset_cap_applied:
        note += "，品質風險套用PB/ROE天花板"
    note += f"，納入權重 {fmt(peer_weight * 100)}%；三情境：保守 {fmt(low_price)} / 基準 {fmt(adjusted_base)} / 樂觀 {fmt(high_price)}"
    return adjusted_base, low_price, high_price, confidence, note


def apply_backtest_confidence(row, confidence):
    cohort = str(row.get("backtestCohort") or "")
    if not cohort or cohort == "N/A":
        return confidence, ""
    adjustment = 0.0
    notes = []
    expected = number(row.get("expectedReturnScore"))
    winrate = number(row.get("winratePriorityScore"))
    drawdown = number(row.get("maxDrawdownPenalty"))
    if expected >= 60:
        adjustment += 3
        notes.append("報酬回測佳")
    elif 0 < expected < 45:
        adjustment -= 3
        notes.append("報酬回測偏弱")
    if winrate >= 60:
        adjustment += 2
        notes.append("勝率回測佳")
    elif 0 < winrate < 45:
        adjustment -= 2
        notes.append("勝率回測偏弱")
    if drawdown >= 10:
        adjustment -= 3
        notes.append("回測回撤偏大")
    if not notes:
        return confidence, ""
    confidence = clamp(confidence + adjustment, 35, 92)
    note = f"；績效校準({cohort})：" + "、".join(notes) + f"，信心調整 {signed(adjustment)} 分"
    return confidence, note


def recalculate_row(row, cache, regime_discount, peer_stats=None):
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
    latest_ocf = number(cache.get("latestOperatingCashFlow"))
    latest_fcf = number(cache.get("latestFreeCashFlow"))
    positive_ocf_quarters = int(number(cache.get("positiveOperatingCashFlowQuarters")))
    positive_fcf_quarters = int(number(cache.get("positiveFreeCashFlowQuarters")))
    debt_ratio = number(cache.get("debtRatioPct"))
    current_ratio = number(cache.get("currentRatio"))
    financial_quality = number(row.get("financialQualityScore"))
    valuation_score = number(row.get("valuationScore"))
    peg = number(row.get("peg"))
    data_confidence = number(row.get("dataConfidence"))
    selection_qualified = bool(row.get("selectionQualified"))

    industry = row.get("industry") or cache.get("industry")
    style = fair_value_style(industry, latest_eps_yoy, avg_3m_revenue_yoy, peg, roe, book_value)
    quality_disc, discount_notes = quality_discount(
        latest_ocf,
        latest_fcf,
        positive_ocf_quarters,
        positive_fcf_quarters,
        debt_ratio,
        current_ratio,
        non_op,
        style,
    )
    pe_cap_limit = quality_pe_cap(style, len(discount_notes), debt_ratio, non_op, latest_ocf, latest_fcf)
    peer_weight = 0.50 if style == "growth" else 0.40 if style == "stable" else 0.40
    peg_weight = 0.45 if style == "growth" else 0.20 if style == "stable" else 0.25
    pb_weight = 0.40 if style == "stable" else 0.25

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
        pe_floor = 8
        pe_cap = min(36, pe_cap_limit)
        target_pe = clamp(peer_average_pe * peer_factor * regime_discount * quality_disc, pe_floor, pe_cap)
        core_values.append(fair_value_eps * target_pe)
        core_weights.append(peer_weight)
        method_notes.append(f"同業PE {fmt(target_pe)}倍")

    if fair_value_eps > 0 and latest_eps_yoy > 0:
        target_peg = 0.95 if style == "growth" else 0.8 if style == "stable" else 0.7
        if financial_quality >= 15:
            target_peg += 0.05
        peg_floor = 0.6
        peg_cap = 1.05
        target_peg = clamp(target_peg * regime_discount * quality_disc, peg_floor, peg_cap)
        pe_floor = 10
        pe_cap = min(40, pe_cap_limit + 2)
        target_pe = clamp(latest_eps_yoy * target_peg, pe_floor, pe_cap)
        core_values.append(fair_value_eps * target_pe)
        core_weights.append(peg_weight)
        method_notes.append(f"PEG 推估 {fmt(target_pe)}倍")

    if book_value > 0 and roe > 0:
        required_return = 11 if financial_quality >= 15 else 12 if financial_quality >= 10 else 14
        justified_pb = clamp(roe / required_return, 0.6, 3.8) * regime_discount * quality_disc
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
        recovery_factor = clamp(recovery_factor * regime_discount * quality_disc, 0.72, 1.08)
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
    if discount_notes:
        confidence -= min(14, len(discount_notes) * 3)
    if pe_cap_limit < 32:
        support_notes.append(f"品質風險限制PE上限至 {fmt(pe_cap_limit)}倍")
    confidence = clamp(confidence, 35, 92)
    band_pct = max(8, min(24, 22 - confidence * 0.12 + (3 - len(core_values)) * 2.5))
    low_price = min(base_price * (1 - band_pct / 100), min_value * 0.98)
    high_price = max(base_price * (1 + band_pct / 100), max_value * 1.02)
    low_price = clamp(low_price, 0, high_price)
    base_price = clamp(base_price, low_price, high_price)
    base_price, low_price, high_price, confidence, peer_note = apply_peer_comparison(
        row, cache, peer_stats, base_price, low_price, high_price, confidence, fair_value_eps
    )
    confidence, backtest_note = apply_backtest_confidence(row, confidence)
    gap_pct = (base_price - current_price) * 100 / current_price

    method = (
        "復甦期參考估值"
        if fair_value_eps <= 0 and method_notes and "復甦期市場定價" in method_notes[0]
        else "成長混合估值"
        if style == "growth"
        else "品質資產混合估值"
        if style == "stable"
        else "均衡混合估值"
    )
    if peer_note and "同業比較" not in method:
        method += "+同業比較"
    support_text = "" if not support_notes else "；" + "、".join(support_notes)
    discount_text = "" if not discount_notes else "；折價：" + "、".join(discount_notes)
    reason = (
        f"估值EPS {fmt(fair_value_eps)}（近四季 {fmt(trailing_eps)}×40% + "
        f"近兩季年化 {fmt(two_quarter_annualized_eps)}×60%）；以 "
        f"{'、'.join(method_notes)} 綜合估算{support_text}{discount_text}，合理價中位 {fmt(base_price)}，"
        f"相對現價 {signed(gap_pct)}%，信心 {fmt(confidence)} 分{peer_note}{backtest_note}"
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
    rows = payload.get("rows", [])
    peer_stats = build_peer_stats(rows, cache_entries)
    updated = 0
    skipped = 0
    for row in rows:
        cache = cache_entries.get(str(row.get("code"))) or {}
        if cache and recalculate_row(row, cache, discount, peer_stats):
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
    rows = latest_snapshot.get("rows", [])
    peer_stats = build_peer_stats(rows, cache_entries)
    updated = 0
    skipped = 0
    for row in rows:
        cache = cache_entries.get(str(row.get("code"))) or {}
        if cache and recalculate_row(row, cache, discount, peer_stats):
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
            decoded_rows = [(code, json.loads(row_json)) for code, row_json in rows]
            peer_stats = build_peer_stats([row for _, row in decoded_rows], cache_entries)
            for code, row in decoded_rows:
                cache = cache_entries.get(str(code)) or {}
                if cache and recalculate_row(row, cache, 0.94, peer_stats):
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
            decoded_rows = [(code, json.loads(row_json)) for code, row_json in rows]
            peer_stats = build_peer_stats([row for _, row in decoded_rows], cache_entries)
            for code, row in decoded_rows:
                cache = cache_entries.get(str(code)) or {}
                if cache and recalculate_row(row, cache, 0.94, peer_stats):
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
