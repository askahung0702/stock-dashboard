package stock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import stock.common.HttpTextFetcher;
import stock.common.NumberParser;
import stock.vo.ShareholderConcentrationVO;
import stock.vo.ShareholderDistributionRowVO;

public class TdccShareholderDistributionService {

    private static final String TDCC_DISTRIBUTION_CSV_URL = "https://opendata.tdcc.com.tw/getOD.ashx?id=1-5";

    private final HttpTextFetcher fetcher = new HttpTextFetcher();

    public ShareholderDistributionBundle fetchLatest() throws Exception {
        String csvText = fetcher.fetchJson(TDCC_DISTRIBUTION_CSV_URL, 30000, 2);
        List<ShareholderDistributionRowVO> rows = parseCsv(csvText);
        return new ShareholderDistributionBundle(rows, summarize(rows));
    }

    private List<ShareholderDistributionRowVO> parseCsv(String csvText) {
        List<ShareholderDistributionRowVO> rows = new ArrayList<ShareholderDistributionRowVO>();
        if (csvText == null || csvText.trim().length() == 0) {
            return rows;
        }

        String[] lines = csvText.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int i = 0; i < lines.length; i++) {
            List<String> columns = splitCsvLine(lines[i]);
            if (columns.size() < 6) {
                continue;
            }
            String dataDate = normalizeDate(columns.get(0));
            String code = normalizeCode(columns.get(1));
            if (i == 0 && !NumberParser.isFourDigitStockCode(code)) {
                continue;
            }
            if (!NumberParser.isFourDigitStockCode(code) || dataDate.length() == 0) {
                continue;
            }
            int level = parseInt(columns.get(2));
            if (level <= 0) {
                continue;
            }
            rows.add(new ShareholderDistributionRowVO(dataDate, code, level, longValue(columns.get(3)),
                    longValue(columns.get(4)), doubleValue(columns.get(5))));
        }
        return rows;
    }

    private Map<String, ShareholderConcentrationVO> summarize(List<ShareholderDistributionRowVO> rows) {
        Map<String, ShareholderConcentrationVO> summaries = new HashMap<String, ShareholderConcentrationVO>();
        for (ShareholderDistributionRowVO row : rows) {
            if (!isTargetLevel(row.getHoldingLevel())) {
                continue;
            }
            String key = row.getDataDate() + "|" + row.getCode();
            ShareholderConcentrationVO summary = summaries.get(key);
            if (summary == null) {
                summary = new ShareholderConcentrationVO(row.getDataDate(), row.getCode());
                summaries.put(key, summary);
            }
            if (row.getHoldingLevel() >= 10 && row.getHoldingLevel() <= 14) {
                summary.add100To1000Lots(row.getHolders(), row.getShares(), row.getRatioPercent());
            } else if (row.getHoldingLevel() == 15) {
                summary.addOver1000Lots(row.getHolders(), row.getShares(), row.getRatioPercent());
            }
        }
        return summaries;
    }

    private boolean isTargetLevel(int level) {
        return (level >= 10 && level <= 14) || level == 15;
    }

    private String normalizeCode(String value) {
        String text = clean(value);
        if (text.length() >= 4) {
            text = text.substring(0, 4);
        }
        return text;
    }

    private String normalizeDate(String value) {
        String text = clean(value).replace("/", "").replace("-", "");
        if (text.matches("\\d{8}")) {
            return text;
        }
        return "";
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(clean(value));
        } catch (Exception ex) {
            return 0;
        }
    }

    private long longValue(String value) {
        try {
            return NumberParser.parseLong(clean(value));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private double doubleValue(String value) {
        try {
            return NumberParser.parseDouble(clean(value));
        } catch (Exception ex) {
            return 0D;
        }
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\uFEFF", "").trim();
    }

    private List<String> splitCsvLine(String line) {
        List<String> columns = new ArrayList<String>();
        if (line == null) {
            return columns;
        }
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        columns.add(current.toString());
        return columns;
    }

    public static class ShareholderDistributionBundle {
        private final List<ShareholderDistributionRowVO> rows;
        private final Map<String, ShareholderConcentrationVO> summariesByDateAndCode;

        private ShareholderDistributionBundle(List<ShareholderDistributionRowVO> rows,
                Map<String, ShareholderConcentrationVO> summariesByDateAndCode) {
            this.rows = rows;
            this.summariesByDateAndCode = summariesByDateAndCode;
        }

        public List<ShareholderDistributionRowVO> getRows() {
            return rows;
        }

        public Map<String, ShareholderConcentrationVO> getSummariesByDateAndCode() {
            return summariesByDateAndCode;
        }
    }
}
