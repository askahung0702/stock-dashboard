/**
 * KKTIX 票券監控腳本 v3
 * ─────────────────────────────────────────────────────────
 * 【v3 修正重點】
 *   ✅ 移除 fetch（KKTIX 票況由客戶端 JS 動態渲染，fetch 抓不到）
 *   ✅ 主偵測：找 input[type="number"] = 有數量框就代表有票
 *   ✅ 備用偵測：找沒有「已售完」文字的票價列
 *   ✅ 每 2 秒主動掃描目前 DOM（不依賴重整）
 *   ✅ MutationObserver 同時監控 DOM 變化
 *   ✅ 書籤功能：重整後一鍵重啟
 *
 * 【使用方法】
 *   1. 開啟 KKTIX 購票頁 → F12 → Console
 *   2. 複製全部內容貼上 → Enter
 *   3. 不需要重整！腳本持續監控目前頁面 DOM
 *   4. 找到票時：發聲 + 自動填數量 + 彈出提醒
 *
 * 【停止監控】Console 輸入: window._kktixStop()
 */

(function monitorKKTIX() {
    'use strict';

    // ══════════════ 設定區 ══════════════
    const TARGET_QTY   = 2;     // 要買幾張
    const DOM_CHECK_MS = 2000;  // 每幾毫秒掃描一次頁面（2000 = 2秒）
    const BEEP_TIMES   = 10;    // 找到票時發幾聲
    // ════════════════════════════════════

    let found = false;
    let scanCount = 0;

    // ── 提示音 ───────────────────────────────────────────
    function playBeep(n) {
        try {
            const ctx = new (window.AudioContext || window.webkitAudioContext)();
            for (let i = 0; i < n; i++) {
                setTimeout(() => {
                    const osc = ctx.createOscillator();
                    const g   = ctx.createGain();
                    osc.connect(g); g.connect(ctx.destination);
                    osc.frequency.value = i % 2 === 0 ? 880 : 660;
                    g.gain.setValueAtTime(0.5, ctx.currentTime);
                    g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.4);
                    osc.start(ctx.currentTime);
                    osc.stop(ctx.currentTime + 0.4);
                }, i * 500);
            }
        } catch (e) { /* 無聲模式 */ }
    }

    // ── 偵測可購買的票（三層策略）────────────────────────
    function findAvailableTickets() {
        const results = [];

        // ★ 策略1（最可靠）：頁面上有 input[type="number"] = 有數量可選 = 有票
        document.querySelectorAll('input[type="number"]').forEach(input => {
            if (input.offsetParent === null) return; // 跳過隱藏元素
            const row = input.closest('tr') ||
                        input.closest('[class*="ticket"]') ||
                        input.closest('li') ||
                        input.parentElement;
            if (!row) return;
            const text       = (row.innerText || row.textContent || '').replace(/\s+/g, ' ').trim();
            const priceMatch = text.match(/(?:TWD|NT|NTD)\$?\s*([\d,]+)/i);
            const price      = priceMatch ? parseInt(priceMatch[1].replace(/,/g, ''), 10) : 0;
            results.push({ price, text, row, input, method: 'input' });
        });

        // ★ 策略2：有「+」按鈕且無「已售完」文字的列
        if (results.length === 0) {
            document.querySelectorAll('tr, [class*="ticket-row"], [class*="ticket_row"]').forEach(row => {
                const text       = (row.innerText || row.textContent || '').replace(/\s+/g, ' ').trim();
                const sold       = text.includes('已售完') || text.includes('售完') || text.includes('Sold');
                const priceMatch = text.match(/(?:TWD|NT|NTD)\$?\s*([\d,]+)/i);
                const plusBtn    = row.querySelector('button:not([disabled])');
                if (priceMatch && !sold && plusBtn && text.length > 3) {
                    const price = parseInt(priceMatch[1].replace(/,/g, ''), 10);
                    results.push({ price, text, row, input: null, method: 'button' });
                }
            });
        }

        // ★ 策略3（備用）：有價格且無售完文字的列
        if (results.length === 0) {
            document.querySelectorAll('tr').forEach(row => {
                const text       = (row.innerText || row.textContent || '').replace(/\s+/g, ' ').trim();
                const sold       = text.includes('已售完') || text.includes('售完') || text.includes('Sold');
                const priceMatch = text.match(/(?:TWD|NT|NTD)\$?\s*([\d,]+)/i);
                // 額外確認：這列確實有意義（不是表頭、不是空列）
                const hasContent = text.length > 5 && !/^(票種|票價|Ticket|Price)$/.test(text.trim());
                if (priceMatch && !sold && hasContent) {
                    const price = parseInt(priceMatch[1].replace(/,/g, ''), 10);
                    results.push({ price, text, row, input: null, method: 'text' });
                }
            });
        }

        // 去重（同一個 row 可能被多次加入）
        const seen = new Set();
        const unique = results.filter(t => {
            if (seen.has(t.row)) return false;
            seen.add(t.row);
            return true;
        });

        return unique.sort((a, b) => a.price - b.price);
    }

    // ── 自動選票 ─────────────────────────────────────────
    function trySelectTicket(ticket) {
        // 優先用找到的 input
        const input = ticket.input ||
                      ticket.row.querySelector('input[type="number"]');
        if (input) {
            try {
                const setter = Object.getOwnPropertyDescriptor(
                    HTMLInputElement.prototype, 'value').set;
                setter.call(input, TARGET_QTY);
                ['input', 'change', 'keyup'].forEach(e =>
                    input.dispatchEvent(new Event(e, { bubbles: true })));
                return `已自動填入數量 ${TARGET_QTY} 張 ✅`;
            } catch (e) {
                input.value = TARGET_QTY;
                return `已填入數量 ${TARGET_QTY} 張`;
            }
        }

        // 找「+」按鈕連按
        const plusBtn = ticket.row.querySelector(
            'button[class*="plus"], button[class*="add"], button[class*="incr"], .btn-plus'
        ) || [...ticket.row.querySelectorAll('button')].find(
            b => !b.disabled && (b.textContent.trim() === '+' || b.getAttribute('aria-label') === '增加')
        );
        if (plusBtn) {
            for (let i = 0; i < TARGET_QTY; i++) plusBtn.click();
            return `已點擊「+」${TARGET_QTY} 次 ✅`;
        }

        return '⚠️ 無法自動選票，請手動選擇數量';
    }

    // ── 找到票後的處理 ───────────────────────────────────
    function onFound(tickets) {
        if (found) return;
        found = true;

        clearInterval(window._kktixDomTimer);
        if (window._kktixObserver) window._kktixObserver.disconnect();

        const cheapest = tickets[0];
        const time = new Date().toLocaleTimeString('zh-TW');

        console.log(
            `%c🎉 [${time}] 找到票！最低 TWD$${cheapest.price}（偵測方式：${cheapest.method}）`,
            'color:green; font-size:18px; font-weight:bold'
        );
        console.table(tickets.map(t => ({
            票價: `TWD$${t.price}`,
            偵測方式: t.method,
            摘要: t.text.slice(0, 40)
        })));

        playBeep(BEEP_TIMES);

        const autoMsg = trySelectTicket(cheapest);

        setTimeout(() => {
            alert(
                `🎉🎉 找到可購買的票！\n\n` +
                `最低票價：TWD$${cheapest.price}\n` +
                `目標數量：${TARGET_QTY} 張\n` +
                `自動操作：${autoMsg}\n\n` +
                `請確認數量後，點「下一步」完成購買！`
            );
        }, 600);
    }

    // ── 主掃描函數（每 2 秒執行）────────────────────────
    function domScan() {
        if (found) return;
        scanCount++;
        const time  = new Date().toLocaleTimeString('zh-TW');
        const spin  = '⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'[scanCount % 10];
        const tix   = findAvailableTickets();

        if (tix.length > 0) {
            onFound(tix);
        } else {
            console.log(`[${time}] ${spin} 掃描 #${scanCount}：目前無可購買票券，持續監控...`);
        }
    }

    // ── MutationObserver（KKTIX 更新 DOM 時立刻觸發）────
    window._kktixObserver = new MutationObserver(() => {
        if (found) return;
        const tix = findAvailableTickets();
        if (tix.length > 0) onFound(tix);
    });
    window._kktixObserver.observe(document.body, {
        childList: true, subtree: true, attributes: true,
        attributeFilter: ['class', 'disabled', 'style']
    });

    // ── 定時 DOM 掃描（每 2 秒主動檢查）────────────────
    window._kktixDomTimer = setInterval(domScan, DOM_CHECK_MS);

    // ── 停止函數 ─────────────────────────────────────────
    window._kktixStop = function () {
        found = true;
        clearInterval(window._kktixDomTimer);
        if (window._kktixObserver) window._kktixObserver.disconnect();
        console.log('%c⏹ 監控已停止', 'color:red; font-weight:bold');
    };

    // ── 書籤 URL（儲存腳本供重整後一鍵重啟）────────────
    try {
        localStorage.setItem('kktix_src', `(${monitorKKTIX.toString()})()`);
        const bookmarkURL = `javascript:(function(){eval(localStorage.getItem('kktix_src'))})();void 0`;
        console.log('%c╔════════════════════════════════════════════╗', 'color:#2196F3;font-weight:bold');
        console.log('%c║     KKTIX 票券監控 v3 已啟動              ║', 'color:#2196F3;font-size:14px;font-weight:bold');
        console.log('%c╚════════════════════════════════════════════╝', 'color:#2196F3;font-weight:bold');
        console.log(`  🔍 偵測策略1：找 input[type=number]（最可靠）`);
        console.log(`  🔍 偵測策略2：找有按鈕且無「已售完」的列`);
        console.log(`  🔍 偵測策略3：文字比對備用`);
        console.log(`  ⏱  每 ${DOM_CHECK_MS/1000} 秒主動掃描 DOM`);
        console.log(`  👁  MutationObserver 即時監控`);
        console.log(`  🎯 目標：最便宜的票 × ${TARGET_QTY} 張`);
        console.log(`  ⏹  停止：window._kktixStop()`);
        console.log('');
        console.log('%c【★ 書籤 URL（設定後重整頁面一鍵重啟）★】', 'color:orange;font-weight:bold;font-size:13px');
        console.log('%c' + bookmarkURL, 'color:darkorange');
    } catch (e) {
        console.log('%c🎫 KKTIX 票券監控 v3 已啟動！', 'color:#2196F3;font-size:14px;font-weight:bold');
    }

    // ── 立刻執行一次 ─────────────────────────────────────
    domScan();

})();
