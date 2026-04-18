---
name: stock-early-breakout-review
description: Review monthly early-breakout and launch conditions in the stock repo, compare the newest condition set with the previous review, refresh the early-breakout screener outputs, and verify the front-end/dashboard shows the latest early-breakout filter results. Use when the user wants a monthly 起漲條件檢視, wants to compare this month versus the previous window, or wants the latest early-breakout candidates exposed in the web report and dashboard.
---

# Stock Early Breakout Review

Use this skill for the recurring monthly "起漲條件檢視" workflow in this repo.

## When to use

- The user wants to review one month's launch/breakout conditions.
- The user wants to compare this month's conditions with the previous review.
- The user wants to refresh the early-breakout screener outputs.
- The user wants the front-end/dashboard to show the latest early-breakout filter results.

## Primary script

- Run [scripts/early_breakout_screener.py](../../scripts/early_breakout_screener.py).

Recommended command:

```powershell
python scripts\early_breakout_screener.py --study-start YYYYMMDD --study-end YYYYMMDD --analysis-top 20 --top 30
```

If the user does not give a window, default to the latest ~30 history snapshots.
If the user asks for the latest monthly review, prefer explicit absolute dates in the report.

## What the script does

1. Finds the top gainers inside the study window from `history/stock_candidates_YYYYMMDD.csv`.
2. Detects each winner's earliest identifiable launch point inside the window.
3. Builds the common-condition set for that review.
4. Compares the current condition set with the most recent previous `early_breakout_common_conditions_*.csv`.
5. Screens the latest daily snapshot with the learned rule set.
6. Updates the early-breakout web report and injects an early-breakout panel into:
   - `history_dashboard.html`
   - the latest `stock_dashboard_YYYYMMDD.html`

## Outputs to check

- Launch-point study:
  - `history/early_breakout_launch_points_{study_start}_{study_end}.csv`
- Current condition set:
  - `history/early_breakout_common_conditions_{study_start}_{study_end}.csv`
- Condition delta vs previous review:
  - `history/early_breakout_condition_changes_{study_start}_{study_end}.csv`
- Current screen results:
  - `web/early_breakout/early_breakout_{screen_date}.csv`
  - `web/early_breakout/early_breakout_{screen_date}.html`
  - `web/early_breakout/early_breakout_latest.csv`
  - `web/early_breakout/early_breakout_latest.html`

## Monthly workflow

1. Choose the monthly review window.
2. Run the screener script with that window.
3. Read the `common_conditions` CSV and the `condition_changes` CSV.
4. Summarize:
   - which core conditions stayed strong
   - which conditions strengthened
   - which conditions weakened
   - whether the latest candidate list became narrower, broader, earlier, or more extended
5. Confirm the dashboard panel and the `web/early_breakout/early_breakout_latest.html` report were refreshed.

## Default operating rules

- Treat the most recent `early_breakout_common_conditions_*.csv` before the current window as the comparison baseline.
- If no previous condition file exists, report this explicitly as the first comparable monthly review.
- Keep the comparison anchored to exact windows such as `20260317 -> 20260418`.
- When reporting front-end status, mention both the dashboard panel and the standalone report page.

## Reporting guidance

- Treat `launch_status = window_start` as "already rising at the start of the study window".
- Treat `launch_status = fallback` as "no clean in-window launch point; proxy only".
- When comparing with the previous review, call out absolute date ranges, not relative dates.
- If no previous condition file exists, explicitly say this is the first comparable review.

## Front-end validation

After running the script, verify:

- the dashboard contains the `早期起漲條件` panel
- the panel links to `web/early_breakout/early_breakout_latest.html`
- the linked page reflects the newest study window and candidate list

## Related files

- [run_stock_analysis.bat](../../run_stock_analysis.bat)
- [scripts/build_pages_site.ps1](../../scripts/build_pages_site.ps1)
- [history_dashboard.html](../../history_dashboard.html)
