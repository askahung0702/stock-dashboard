package stock;

import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import stock.common.NumberParser;
import stock.vo.InsiderTransferEventVO;

public class MopsInsiderTransferService {

    private static final String MOPS_TRANSFER_AJAX_URL = System.getProperty("stock.mops.insiderTransfer.url",
            "https://mops.twse.com.tw/mops/web/ajax_t56sb21");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    public List<InsiderTransferEventVO> fetchDaily(String tradeDate) throws Exception {
        String normalizedDate = normalizeDate(tradeDate);
        List<InsiderTransferEventVO> events = new ArrayList<InsiderTransferEventVO>();
        if (normalizedDate.length() == 0) {
            return events;
        }
        String html = postForm(MOPS_TRANSFER_AJAX_URL, buildDailyPayload(normalizedDate), 20000);
        if (isSecurityBlocked(html)) {
            throw new IOException("MOPS security block page returned");
        }
        events.addAll(parseEvents(normalizedDate, html));
        return events;
    }

    private boolean isSecurityBlocked(String html) {
        String text = html == null ? "" : html;
        return text.contains("FOR SECURITY REASONS") || text.contains("\u5b89\u5168\u6027\u8003\u91cf")
                || text.contains("\u932f\u8aa4\u4ee3\u78bc");
    }

    private String buildDailyPayload(String yyyymmdd) {
        int year = Integer.parseInt(yyyymmdd.substring(0, 4)) - 1911;
        String month = yyyymmdd.substring(4, 6);
        String day = yyyymmdd.substring(6, 8);
        StringBuilder form = new StringBuilder();
        append(form, "encodeURIComponent", "1");
        append(form, "step", "1");
        append(form, "firstin", "1");
        append(form, "off", "1");
        append(form, "TYPEK", "all");
        append(form, "year", Integer.toString(year));
        append(form, "month", month);
        append(form, "day", day);
        append(form, "qry_date", Integer.toString(year) + "/" + month + "/" + day);
        return form.toString();
    }

    private List<InsiderTransferEventVO> parseEvents(String reportDate, String html) {
        List<InsiderTransferEventVO> events = new ArrayList<InsiderTransferEventVO>();
        if (html == null || html.trim().length() == 0) {
            return events;
        }
        Document document = Jsoup.parse(html);
        Elements rows = document.select("table tr");
        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 6) {
                continue;
            }
            List<String> values = new ArrayList<String>();
            for (Element cell : cells) {
                String text = cell.text().replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
                if (text.length() > 0) {
                    values.add(text);
                }
            }
            String code = firstStockCode(values);
            if (!NumberParser.isFourDigitStockCode(code)) {
                continue;
            }
            events.add(toEvent(reportDate, code, values));
        }
        return events;
    }

    @SuppressWarnings("unchecked")
    private InsiderTransferEventVO toEvent(String reportDate, String code, List<String> values) {
        JSONArray rawValues = new JSONArray();
        for (String value : values) {
            rawValues.add(value);
        }
        JSONObject raw = new JSONObject();
        raw.put("values", rawValues);

        int codeIndex = values.indexOf(code);
        String name = valueAt(values, codeIndex + 1);
        String insiderName = guessName(values, codeIndex);
        String role = guessRole(values);
        String method = guessTransferMethod(values);
        long plannedShares = guessLargestShareValue(values);
        long currentHoldingShares = guessSecondLargestShareValue(values, plannedShares);
        String[] transferDates = guessTransferDates(values);
        return new InsiderTransferEventVO(reportDate, code, name, insiderName, role, method, plannedShares,
                currentHoldingShares, transferDates[0], transferDates[1], raw);
    }

    private String firstStockCode(List<String> values) {
        for (String value : values) {
            String text = value == null ? "" : value.trim();
            if (NumberParser.isFourDigitStockCode(text)) {
                return text;
            }
        }
        return "";
    }

    private String guessName(List<String> values, int codeIndex) {
        for (int i = codeIndex + 2; i < values.size(); i++) {
            String text = values.get(i);
            if (!looksNumeric(text) && !looksDate(text) && !looksTransferMethod(text) && !looksRole(text)) {
                return text;
            }
        }
        return "";
    }

    private String guessRole(List<String> values) {
        for (String value : values) {
            if (looksRole(value)) {
                return value;
            }
        }
        return "";
    }

    private String guessTransferMethod(List<String> values) {
        for (String value : values) {
            if (looksTransferMethod(value)) {
                return value;
            }
        }
        return "";
    }

    private long guessLargestShareValue(List<String> values) {
        long max = 0L;
        for (String value : values) {
            long number = shareValue(value);
            if (number > max) {
                max = number;
            }
        }
        return max;
    }

    private long guessSecondLargestShareValue(List<String> values, long largest) {
        long second = 0L;
        for (String value : values) {
            long number = shareValue(value);
            if (number > second && number < largest) {
                second = number;
            }
        }
        return second;
    }

    private String[] guessTransferDates(List<String> values) {
        List<String> dates = new ArrayList<String>();
        for (String value : values) {
            String date = normalizeAnyDate(value);
            if (date.length() > 0 && !dates.contains(date)) {
                dates.add(date);
            }
        }
        String start = dates.size() >= 2 ? dates.get(dates.size() - 2) : "";
        String end = dates.size() >= 1 ? dates.get(dates.size() - 1) : "";
        return new String[] { start, end };
    }

    private long shareValue(String value) {
        String text = value == null ? "" : value.replace(",", "").trim();
        if (!text.matches("\\d+(\\.\\d+)?")) {
            return 0L;
        }
        try {
            double parsed = Double.parseDouble(text);
            if (parsed < 10D) {
                return 0L;
            }
            return Math.round(parsed);
        } catch (Exception ex) {
            return 0L;
        }
    }

    private boolean looksNumeric(String value) {
        String text = value == null ? "" : value.replace(",", "").trim();
        return text.matches("-?\\d+(\\.\\d+)?");
    }

    private boolean looksDate(String value) {
        return normalizeAnyDate(value).length() > 0;
    }

    private boolean looksRole(String value) {
        String text = value == null ? "" : value;
        return text.contains("\u8463") || text.contains("\u76e3") || text.contains("\u7d93\u7406\u4eba")
                || text.contains("\u5927\u80a1\u6771") || text.contains("\u6cd5\u4eba");
    }

    private boolean looksTransferMethod(String value) {
        String text = value == null ? "" : value;
        return text.contains("\u4e00\u822c\u4ea4\u6613") || text.contains("\u76e4\u5f8c\u5b9a\u50f9")
                || text.contains("\u9245\u984d") || text.contains("\u8d08\u8207")
                || text.contains("\u4fe1\u8a17") || text.contains("\u62cd\u8ce3")
                || text.contains("\u8f49\u8b93");
    }

    private String normalizeAnyDate(String value) {
        String text = value == null ? "" : value.trim();
        if (text.matches("\\d{4}/\\d{1,2}/\\d{1,2}")) {
            String[] parts = text.split("/");
            return leftPad(parts[0], 4) + leftPad(parts[1], 2) + leftPad(parts[2], 2);
        }
        if (text.matches("\\d{2,3}/\\d{1,2}/\\d{1,2}")) {
            String[] parts = text.split("/");
            int year = Integer.parseInt(parts[0]) + 1911;
            return Integer.toString(year) + leftPad(parts[1], 2) + leftPad(parts[2], 2);
        }
        if (text.matches("\\d{8}")) {
            return text;
        }
        return "";
    }

    private String normalizeDate(String date) {
        String text = date == null ? "" : date.trim().replace("/", "").replace("-", "");
        if (text.matches("\\d{8}")) {
            return text;
        }
        return LocalDate.now().format(DATE_FORMATTER);
    }

    private String valueAt(List<String> values, int index) {
        return index >= 0 && index < values.size() ? values.get(index) : "";
    }

    private String leftPad(String value, int length) {
        String text = value == null ? "" : value.trim();
        while (text.length() < length) {
            text = "0" + text;
        }
        return text;
    }

    private void append(StringBuilder form, String key, String value) {
        try {
            if (form.length() > 0) {
                form.append('&');
            }
            form.append(URLEncoder.encode(key, "UTF-8"));
            form.append('=');
            form.append(URLEncoder.encode(value == null ? "" : value, "UTF-8"));
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private String postForm(String url, String formBody, int timeoutMs) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setDoOutput(true);
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/134.0 Safari/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Referer", "https://mops.twse.com.tw/mops/web/t56sb21_q1");
            byte[] bytes = formBody.getBytes("UTF-8");
            connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
            OutputStream outputStream = connection.getOutputStream();
            try {
                outputStream.write(bytes);
            } finally {
                outputStream.close();
            }
            int status = connection.getResponseCode();
            java.io.InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) {
                return "";
            }
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(stream, "UTF-8"));
            try {
                StringBuilder builder = new StringBuilder();
                String line = null;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                if (status >= 200 && status < 300) {
                    return builder.toString();
                }
                throw new java.io.IOException("HTTP " + status + " from MOPS: " + builder.toString());
            } finally {
                reader.close();
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
