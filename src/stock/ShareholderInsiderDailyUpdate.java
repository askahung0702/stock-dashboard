package stock;

import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import stock.TdccShareholderDistributionService.ShareholderDistributionBundle;
import stock.vo.InsiderTransferEventVO;
import stock.vo.ShareholderConcentrationVO;
import stock.vo.ShareholderDistributionRowVO;
import stock.vo.TaiwanStockVO;

public class ShareholderInsiderDailyUpdate {

    private static final String STAGE_NAME = "shareholder-insider";

    public static void main(String[] args) throws Exception {
        String date = args.length > 1 && args[1] != null && args[1].trim().length() > 0 ? args[1].trim()
                : new TaiwanStockAnalyzer().currentDateStamp();
        StockHistoryDatabase database = new StockHistoryDatabase();
        int savedRows = 0;
        String note = "";

        try {
            Set<String> stockCodes = loadStockCodes();
            ShareholderDistributionBundle bundle = new TdccShareholderDistributionService().fetchLatest();
            List<ShareholderDistributionRowVO> stockRows = filterRows(bundle.getRows(), stockCodes);
            Map<String, ShareholderConcentrationVO> stockSummaries = filterSummaries(
                    bundle.getSummariesByDateAndCode(), stockCodes);
            database.upsertShareholderDistributionRows(stockRows);
            database.upsertShareholderConcentrationRows(stockSummaries.values());
            savedRows += stockRows.size();
            note = "tdcc rows=" + stockRows.size() + ", concentration=" + stockSummaries.size();
            System.out.println("TDCC shareholder distribution saved: " + note);
        } catch (Exception ex) {
            note = "tdcc failed: " + ex.getMessage();
            System.out.println("TDCC shareholder distribution failed: " + ex.getMessage());
        }

        boolean insiderAvailable = true;
        try {
            List<InsiderTransferEventVO> insiderEvents = new MopsInsiderTransferService().fetchDaily(date);
            database.upsertInsiderTransferEvents(insiderEvents);
            savedRows += insiderEvents.size();
            note = (note.length() == 0 ? "" : note + "; ") + "insider events=" + insiderEvents.size();
        } catch (Exception ex) {
            insiderAvailable = false;
            note = (note.length() == 0 ? "" : note + "; ") + "insider failed: " + ex.getMessage();
            System.out.println("MOPS insider transfer failed: " + ex.getMessage());
        }
        String status = savedRows > 0 ? (insiderAvailable ? "completed" : "partial") : "unavailable";
        database.upsertDailyRunStatus(date, STAGE_NAME, status, savedRows, note);
        System.out.println("Shareholder/insider daily update saved: " + note);
    }

    private static Set<String> loadStockCodes() throws Exception {
        Set<String> codes = new HashSet<String>();
        List<TaiwanStockVO> stocks = new TaiwanStockMarketProvider().loadAllStocks();
        for (TaiwanStockVO stock : stocks) {
            if (stock != null && stock.getCode() != null && stock.getCode().length() > 0) {
                codes.add(stock.getCode());
            }
        }
        return codes;
    }

    private static List<ShareholderDistributionRowVO> filterRows(List<ShareholderDistributionRowVO> rows,
            Set<String> stockCodes) {
        List<ShareholderDistributionRowVO> filtered = new java.util.ArrayList<ShareholderDistributionRowVO>();
        if (rows == null || stockCodes == null || stockCodes.isEmpty()) {
            return filtered;
        }
        for (ShareholderDistributionRowVO row : rows) {
            if (row != null && stockCodes.contains(row.getCode())) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private static Map<String, ShareholderConcentrationVO> filterSummaries(
            Map<String, ShareholderConcentrationVO> summaries, Set<String> stockCodes) {
        Map<String, ShareholderConcentrationVO> filtered = new HashMap<String, ShareholderConcentrationVO>();
        if (summaries == null || stockCodes == null || stockCodes.isEmpty()) {
            return filtered;
        }
        for (Map.Entry<String, ShareholderConcentrationVO> entry : summaries.entrySet()) {
            ShareholderConcentrationVO summary = entry.getValue();
            if (summary != null && stockCodes.contains(summary.getCode())) {
                filtered.put(entry.getKey(), summary);
            }
        }
        return filtered;
    }
}
