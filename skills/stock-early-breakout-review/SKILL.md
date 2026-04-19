---
name: stock-early-breakout-review
description: Review monthly early-breakout and launch conditions in the stock repo, study last month's top 20 gainers for shared launch traits, compare the newest condition set with the previous review, judge whether the screen is too broad or too loose, refresh the early-breakout screener outputs, verify the front-end/dashboard shows the latest filter results, and publish the refreshed static site when requested.
---

# Stock Early Breakout Review

Use this skill for the recurring monthly "起漲條件檢視" workflow in this repo.

## When to use

- The user wants to review last month's top 20 gainers and their launch/breakout conditions.
- The user wants to compare this month's conditions with the previous review.
- The user wants to know whether the current early-breakout rule set is too broad, too narrow, or still usable.
- The user wants a stricter early-breakout rule suggestion or wants to know if rolling backtests are needed.
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
5. Splits the review sample into `identified / window_start / fallback`, highlights the shared traits of the true `identified` launch points, and evaluates whether the current screen is too broad.
6. Produces a monthly review summary with a stricter rule suggestion and a backtest recommendation.
7. Screens the latest daily snapshot with the learned rule set.
8. Updates the early-breakout web report and injects an early-breakout panel into:
   - `history_dashboard.html`
   - the latest `stock_dashboard_YYYYMMDD.html`

## Outputs to check

- Launch-point study:
  - `history/early_breakout_launch_points_{study_start}_{study_end}.csv`
- Current condition set:
  - `history/early_breakout_common_conditions_{study_start}_{study_end}.csv`
- Condition delta vs previous review:
  - `history/early_breakout_condition_changes_{study_start}_{study_end}.csv`
- Monthly review summary for front-end/dashboard:
  - `web/early_breakout/early_breakout_{screen_date}_summary.json`
  - `web/early_breakout/early_breakout_latest_summary.json`
- Current screen results:
  - `web/early_breakout/early_breakout_{screen_date}.csv`
  - `web/early_breakout/early_breakout_{screen_date}.html`
  - `web/early_breakout/early_breakout_latest.csv`
  - `web/early_breakout/early_breakout_latest.html`

## Monthly workflow

1. Choose the monthly review window.
2. Run the screener script with that window.
3. Read the `common_conditions` CSV, the `condition_changes` CSV, and the latest `_summary.json`.
4. Summarize:
   - how many of the top 20 were `identified`, `window_start`, and `fallback`
   - which traits stayed strongest in the true `identified` launch samples
   - which core conditions stayed strong
   - which conditions strengthened
   - which conditions weakened
   - whether the latest candidate list became narrower, broader, earlier, or more extended
   - whether the rule set is too broad and whether a stricter version should be discussed
   - whether rolling backtests are recommended
5. Confirm the dashboard panel, the `web/early_breakout/early_breakout_latest.html` report, and the `web/index.html` early-breakout panel were refreshed.
6. If the user wants deployment, rebuild `site/` and push to GitHub so Pages updates.

## Default operating rules

- Treat the most recent `early_breakout_common_conditions_*.csv` before the current window as the comparison baseline.
- If no previous condition file exists, report this explicitly as the first comparable monthly review.
- Keep the comparison anchored to exact windows such as `20260317 -> 20260418`.
- Treat `window_start` samples as already-rising names; do not overfit the early-breakout rule set to them.
- Put more weight on `identified` samples when judging what "真正起漲點" looked like.
- Always judge whether the current live candidate count is too broad relative to the identified sample size and the full market.
- When reporting front-end status, mention both the dashboard panel and the standalone report page.

## Reporting guidance

- Treat `launch_status = window_start` as "already rising at the start of the study window".
- Treat `launch_status = fallback` as "no clean in-window launch point; proxy only".
- When comparing with the previous review, call out absolute date ranges, not relative dates.
- If no previous condition file exists, explicitly say this is the first comparable review.
- Be explicit about whether the current rule set is `偏寬`, `中等偏寬`, `較平衡`, or `偏窄`.
- If the rule set looks broad, propose a stricter version and say that rolling backtests should be done before locking the new threshold set.

## Front-end validation

After running the script, verify:

- the dashboard contains the `早期起漲條件` panel
- `web/index.html` contains the early-breakout summary panel and tab
- the panel links to `web/early_breakout/early_breakout_latest.html`
- the linked page reflects the newest study window, candidate list, and monthly review summary
- `web/early_breakout/early_breakout_latest_summary.json` reflects the newest monthly review

## Related files

- [run_stock_analysis.bat](../../run_stock_analysis.bat)
- [scripts/build_pages_site.ps1](../../scripts/build_pages_site.ps1)
- [history_dashboard.html](../../history_dashboard.html)
