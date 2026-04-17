# GitHub Pages Deploy Notes

This repository is prepared to publish a static stock dashboard with GitHub Pages.

## What gets deployed

- `history_dashboard.html` is used as the preferred homepage when it exists.
- If `history_dashboard.html` is missing, the newest `stock_dashboard_YYYYMMDD.html` file becomes `index.html`.
- All dated dashboard pages are copied into `site/daily/`.

## Workflow

- Workflow file: `.github/workflows/deploy-pages.yml`
- Site build script: `scripts/build_pages_site.ps1`

## Important note about private repositories

GitHub Pages for private repositories is not available on GitHub Free personal accounts.
If your account is on GitHub Free, you will need either:

1. A public repository on GitHub Pages, or
2. A paid GitHub plan that supports Pages for private repositories.

## After creating the repository

1. Push this project to `main`.
2. In GitHub repo settings, open `Pages`.
3. Set `Source` to `GitHub Actions`.
4. Run the `Deploy Stock Dashboard` workflow if it does not start automatically.
