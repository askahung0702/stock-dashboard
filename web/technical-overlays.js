/* Price-chart overlays. Periods are observations, never assumed trading sessions. */
(function (root) {
  'use strict';
  const positive = value => typeof value === 'number' && Number.isFinite(value) && value > 0;
  function history(rows, asof) {
    const dates = new Map();
    for (const row of rows || []) {
      if (typeof row.date !== 'string' || !/^\d{8}$/.test(row.date) ||
          (asof && row.date > asof) || !positive(row.price)) continue;
      dates.set(row.date, row);
    }
    return [...dates.values()].sort((a, b) => a.date.localeCompare(b.date));
  }
  function bollinger(prices, period = 20, deviations = 2) {
    if (!Number.isInteger(period) || period < 5 || period > 100 ||
        !Number.isFinite(deviations) || deviations < .5 || deviations > 4) {
      throw new Error('布林通道期間需為 5～100，標準差倍數需為 0.5～4。');
    }
    const middle = prices.map(() => null), upper = prices.map(() => null), lower = prices.map(() => null);
    for (let i = period - 1; i < prices.length; i++) {
      const window = prices.slice(i - period + 1, i + 1);
      if (!window.every(positive)) continue;
      const mean = window.reduce((sum, value) => sum + value, 0) / period;
      const variance = window.reduce((sum, value) => sum + (value - mean) ** 2, 0) / period;
      const width = Math.sqrt(Math.max(0, variance)) * deviations;
      middle[i] = mean; upper[i] = mean + width; lower[i] = mean - width;
    }
    return {middle, upper, lower};
  }
  function anchors(rows, lookback = 60) {
    const start = Math.max(0, rows.length - lookback);
    let low = start, high = start;
    for (let i = start; i < rows.length; i++) {
      if (rows[i].price < rows[low].price) low = i;
      if (rows[i].price > rows[high].price) high = i;
    }
    const first = Math.min(low, high), last = Math.max(low, high);
    return {start:rows[first]?.date || '', end:rows[last]?.date || ''};
  }
  function fibonacci(rows, start, end) {
    const first = rows.findIndex(row => row.date === start), last = rows.findIndex(row => row.date === end);
    if (first < 0 || last < 0 || first >= last) throw new Error('Fibonacci 起點日期須早於終點日期。');
    const from = rows[first], to = rows[last];
    if (!positive(from.price) || !positive(to.price) || from.price === to.price) {
      throw new Error('Fibonacci 起終點需為不同的有效價格。');
    }
    return {start:from, end:to, direction:to.price > from.price ? '上漲回撤' : '下跌反彈',
      levels:[0, .236, .382, .5, .618, .786, 1].map(ratio => {
        const price = to.price + (from.price - to.price) * ratio;
        // References cover the selected leg and following prices, including a leg ending today.
        return {ratio, price, values:rows.map((row, i) => i >= first ? price : null)};
      })};
  }
  root.TechnicalOverlays = {history, bollinger, anchors, fibonacci};
  if (typeof module !== 'undefined') module.exports = root.TechnicalOverlays;
})(typeof globalThis !== 'undefined' ? globalThis : this);
