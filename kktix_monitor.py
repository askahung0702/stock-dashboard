"""
KKTIX 張清芳慈善演唱會 - Python 票券監控程式
================================================

【安裝步驟（只需做一次）】
1. 安裝 Python: https://www.python.org/downloads/
2. 開啟命令提示字元（CMD），輸入：
   pip install playwright
   playwright install chromium

【執行方式】
在 CMD 輸入:  python kktix_monitor.py

【說明】
- 程式會使用 Chromium 瀏覽器（非您目前的 Chrome）
- 需要在開啟的瀏覽器視窗中手動登入 KKTIX（首次執行）
- 找到票時發出聲音提醒，並自動嘗試選票
- 付款步驟仍需要手動完成

※ 若想沿用已登入的 Chrome 設定，請修改下方的 USE_EXISTING_CHROME = True
"""

import asyncio
import sys
import os
import re
from datetime import datetime

# ============================================================
#  設定區（可自行修改）
# ============================================================
URL             = "https://kktix.com/events/lo3w7jya/registrations/new"
TARGET_QTY      = 2       # 要購買幾張
CHECK_INTERVAL  = 3       # 每幾秒檢查一次
RELOAD_EVERY    = 30      # 每幾次重新整理頁面（0 = 不重整）
USE_EXISTING_CHROME = False  # True = 使用您已安裝的 Chrome（含登入資料）
# ============================================================


def log(msg: str, level: str = "info"):
    time_str = datetime.now().strftime("%H:%M:%S")
    prefix = {"info": "ℹ", "ok": "✅", "warn": "⚠️", "err": "❌"}.get(level, "•")
    print(f"[{time_str}] {prefix} {msg}")


def beep():
    """發出提示音"""
    if sys.platform == "win32":
        try:
            import winsound
            for freq, dur in [(880, 200), (660, 200), (880, 200), (660, 200), (880, 400)]:
                winsound.Beep(freq, dur)
            return
        except Exception:
            pass
    # 備用：系統 bell
    for _ in range(5):
        print("\a", end="", flush=True)


async def check_tickets(page) -> list[dict]:
    """抓取頁面上所有可購買的票"""
    available = []
    try:
        rows = await page.query_selector_all("tr")
        for row in rows:
            text = (await row.inner_text()).strip()
            price_match = re.search(r"TWD\$?\s*([\d,]+)", text)
            is_sold_out = "已售完" in text or "售完" in text
            if price_match and not is_sold_out and len(text) > 5:
                price = int(price_match.group(1).replace(",", ""))
                available.append({"price": price, "text": text, "row": row})

        available.sort(key=lambda x: x["price"])
    except Exception as e:
        log(f"查詢票券時發生錯誤: {e}", "err")
    return available


async def try_select_ticket(page, ticket: dict) -> str:
    """嘗試自動選票並填入數量"""
    row = ticket["row"]

    # 找數量 input
    inp = await row.query_selector("input[type='number'], input[type='text']")
    if inp:
        await inp.triple_click()
        await inp.type(str(TARGET_QTY))
        return f"已填入數量 {TARGET_QTY}"

    # 找「+」按鈕
    for selector in ["button[class*='plus']", "button[class*='add']", ".btn-plus", "button"]:
        btn = await row.query_selector(selector)
        if btn and await btn.is_enabled():
            for _ in range(TARGET_QTY):
                await btn.click()
            return f"已點擊按鈕 {TARGET_QTY} 次"

    return "⚠️ 無法自動選票，請手動操作"


async def main():
    # 確認 playwright 是否安裝
    try:
        from playwright.async_api import async_playwright
    except ImportError:
        print("❌ 尚未安裝 playwright！")
        print("請在 CMD 執行：")
        print("    pip install playwright")
        print("    playwright install chromium")
        sys.exit(1)

    print("=" * 55)
    print("  KKTIX 票券監控程式")
    print("=" * 55)
    print(f"  目標頁面：{URL}")
    print(f"  目標數量：{TARGET_QTY} 張")
    print(f"  檢查頻率：每 {CHECK_INTERVAL} 秒")
    print(f"  重整頁面：每 {RELOAD_EVERY} 次" if RELOAD_EVERY else "  重整頁面：不重整")
    print("  按 Ctrl+C 可停止監控")
    print("=" * 55)
    print()

    async with async_playwright() as p:
        # 啟動瀏覽器
        if USE_EXISTING_CHROME:
            chrome_profile = os.path.join(
                os.environ.get("LOCALAPPDATA", ""),
                "Google", "Chrome", "User Data"
            )
            log(f"使用 Chrome 設定檔: {chrome_profile}")
            try:
                browser = await p.chromium.launch_persistent_context(
                    user_data_dir=chrome_profile,
                    channel="chrome",
                    headless=False,
                    args=["--start-maximized"],
                )
                page = await browser.new_page()
            except Exception as e:
                log(f"無法使用現有 Chrome，改用 Chromium: {e}", "warn")
                USE_EXISTING_CHROME = False

        if not USE_EXISTING_CHROME:
            # 使用臨時的 Chromium（需手動登入）
            tmp_profile = os.path.join(os.path.expanduser("~"), ".kktix_profile")
            browser = await p.chromium.launch_persistent_context(
                user_data_dir=tmp_profile,
                headless=False,
                args=["--start-maximized"],
            )
            page = await browser.new_page()

        # 前往購票頁面
        log(f"開啟頁面...")
        await page.goto(URL, wait_until="domcontentloaded")
        log("頁面已載入，開始監控...")
        log("若尚未登入，請在瀏覽器視窗中手動登入 KKTIX")
        print()

        check_count = 0
        try:
            while True:
                check_count += 1

                # 定時重整
                if RELOAD_EVERY and check_count % RELOAD_EVERY == 0:
                    log(f"第 {check_count} 次，執行頁面重整...")
                    try:
                        await page.reload(wait_until="domcontentloaded")
                    except Exception:
                        pass
                    await asyncio.sleep(2)

                # 檢查票況
                tickets = await check_tickets(page)

                if tickets:
                    cheapest = tickets[0]
                    log(f"🎉 找到票！最低票價 TWD${cheapest['price']}！", "ok")
                    print()
                    print("┌─────────────────────────────────┐")
                    print(f"│  可購買的票種（共 {len(tickets)} 種）")
                    print("├─────────────────────────────────┤")
                    for t in tickets:
                        print(f"│  TWD${t['price']:,}  {t['text'][:30]}")
                    print("└─────────────────────────────────┘")
                    print()

                    # 播放提示音
                    beep()

                    # 自動選票
                    result = await try_select_ticket(page, cheapest)
                    log(f"自動操作：{result}")
                    log("⚡ 請立刻到瀏覽器確認票數，然後點「下一步」！", "ok")
                    break

                else:
                    log(f"第 {check_count} 次：全數售完，{CHECK_INTERVAL} 秒後再試...")
                    await asyncio.sleep(CHECK_INTERVAL)

        except KeyboardInterrupt:
            log("使用者停止監控", "warn")
        except Exception as e:
            log(f"發生錯誤: {e}", "err")
            raise
        finally:
            log("程式結束，瀏覽器保持開啟狀態，請手動完成購買")
            input("按 Enter 關閉程式...")


if __name__ == "__main__":
    asyncio.run(main())
