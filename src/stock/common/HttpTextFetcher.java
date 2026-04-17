package stock.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Random;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class HttpTextFetcher {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    private static final String HTML_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8";
    private static final String JSON_ACCEPT = "application/json,text/plain,*/*";
    private static final String ACCEPT_LANGUAGE = "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7";
    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_RETRIES = parseIntProperty("stock.fetch.maxRetries", 4);
    private static final long BASE_RETRY_WAIT_MS = parseLongProperty("stock.fetch.baseRetryWaitMs", 1500L);
    private static final long MIN_REQUEST_INTERVAL_MS = parseLongProperty("stock.fetch.minIntervalMs", 650L);
    private static final long REQUEST_JITTER_MS = parseLongProperty("stock.fetch.jitterMs", 250L);
    private static final long THROTTLE_COOLDOWN_BASE_MS = parseLongProperty("stock.fetch.throttleCooldownBaseMs",
            30000L);
    private static final long THROTTLE_COOLDOWN_MAX_MS = parseLongProperty("stock.fetch.throttleCooldownMaxMs",
            120000L);
    private static final Object REQUEST_LOCK = new Object();
    private static final Random RANDOM = new Random();

    private static long nextAllowedRequestAtMs = 0L;
    private static boolean yahooSessionPrimed = false;
    private static int consecutiveYahooThrottleFailures = 0;

    static {
        CookieHandler.setDefault(new CookieManager());
    }

    public String fetchJson(String url) throws Exception {
        return executeWithRetry(url, JSON_ACCEPT, TIMEOUT_MS, MAX_RETRIES);
    }

    public String fetchJson(String url, int timeoutMs, int maxRetries) throws Exception {
        int resolvedTimeoutMs = timeoutMs > 0 ? timeoutMs : TIMEOUT_MS;
        int resolvedMaxRetries = maxRetries > 0 ? maxRetries : 1;
        return executeWithRetry(url, JSON_ACCEPT, resolvedTimeoutMs, resolvedMaxRetries);
    }

    public String fetchPageText(String url) throws Exception {
        String html = executeWithRetry(url, HTML_ACCEPT, TIMEOUT_MS, MAX_RETRIES);
        if (containsYahooBlockPage(url, html)) {
            throw new HttpFetchException("Detected Yahoo anti-bot page. URL=[" + url + "]", 999, html);
        }
        Document document = Jsoup.parse(html, url);
        if (document.body() == null) {
            return normalize(document.text());
        }
        return normalize(document.body().text());
    }

    public String fetchPageHtml(String url) throws Exception {
        String html = executeWithRetry(url, HTML_ACCEPT, TIMEOUT_MS, MAX_RETRIES);
        if (containsYahooBlockPage(url, html)) {
            throw new HttpFetchException("Detected Yahoo anti-bot page. URL=[" + url + "]", 999, html);
        }
        return html;
    }

    private String normalize(String text) {
        return text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String executeWithRetry(String url, String accept, int timeoutMs, int maxRetries) throws Exception {
        if (isYahooUrl(url)) {
            primeYahooSessionIfNeeded();
        }

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                throttleBeforeRequest();
                String body = executeOnce(url, accept, timeoutMs);
                if (isYahooUrl(url)) {
                    markYahooSuccess();
                }
                return body;
            } catch (Exception ex) {
                lastException = ex;
                if (!isRetryable(ex) || attempt >= maxRetries) {
                    throw ex;
                }

                if (isThrottleException(ex)) {
                    resetYahooSession();
                    applyYahooThrottleCooldown(ex, attempt);
                }

                long waitMs = retryWaitMs(attempt);
                System.out.println("Retry " + attempt + "/" + maxRetries + " after fetch failure: "
                        + shortMessage(ex) + " | wait " + waitMs + " ms");
                Thread.sleep(waitMs);
            }
        }
        throw lastException == null ? new IOException("Unable to fetch URL: " + url) : lastException;
    }

    private String executeOnce(String url, String accept, int timeoutMs) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", accept);
            connection.setRequestProperty("Accept-Language", ACCEPT_LANGUAGE);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("Referer", buildReferer(url));

            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = readBody(connection, stream);

            if (status >= 200 && status < 300) {
                return body;
            }

            throw new HttpFetchException(
                    "HTTP error fetching URL. Status=" + status + ", URL=[" + url + "]", status, body);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readBody(HttpURLConnection connection, InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }

        Charset charset = resolveCharset(connection.getContentType());
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, charset));
        try {
            StringBuilder builder = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } finally {
            reader.close();
        }
    }

    private Charset resolveCharset(String contentType) {
        if (contentType != null) {
            String[] segments = contentType.split(";");
            for (String segment : segments) {
                String trimmed = segment.trim().toLowerCase(Locale.ENGLISH);
                if (trimmed.startsWith("charset=")) {
                    try {
                        return Charset.forName(segment.substring(segment.indexOf('=') + 1).trim());
                    } catch (Exception ex) {
                        return Charset.forName("UTF-8");
                    }
                }
            }
        }
        return Charset.forName("UTF-8");
    }

    private void throttleBeforeRequest() throws InterruptedException {
        long waitMs = 0L;
        synchronized (REQUEST_LOCK) {
            long now = System.currentTimeMillis();
            if (now < nextAllowedRequestAtMs) {
                waitMs = nextAllowedRequestAtMs - now;
            }
            long jitter = REQUEST_JITTER_MS <= 0L ? 0L : RANDOM.nextInt((int) REQUEST_JITTER_MS + 1);
            long scheduledAt = now + waitMs;
            nextAllowedRequestAtMs = scheduledAt + MIN_REQUEST_INTERVAL_MS + jitter;
        }
        if (waitMs > 0L) {
            Thread.sleep(waitMs);
        }
    }

    private void primeYahooSessionIfNeeded() {
        synchronized (REQUEST_LOCK) {
            if (yahooSessionPrimed) {
                return;
            }
            yahooSessionPrimed = true;
        }

        HttpURLConnection connection = null;
        try {
            throttleBeforeRequest();
            connection = (HttpURLConnection) new URL("https://tw.stock.yahoo.com/").openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", HTML_ACCEPT);
            connection.setRequestProperty("Accept-Language", ACCEPT_LANGUAGE);
            connection.setRequestProperty("Referer", "https://tw.stock.yahoo.com/");
            InputStream stream = connection.getInputStream();
            if (stream != null) {
                stream.close();
            }
        } catch (Exception ex) {
            resetYahooSession();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void resetYahooSession() {
        synchronized (REQUEST_LOCK) {
            yahooSessionPrimed = false;
        }
    }

    private void markYahooSuccess() {
        synchronized (REQUEST_LOCK) {
            consecutiveYahooThrottleFailures = 0;
        }
    }

    private void applyYahooThrottleCooldown(Exception ex, int attempt) {
        long cooldownMs;
        int throttleCount;
        synchronized (REQUEST_LOCK) {
            consecutiveYahooThrottleFailures++;
            throttleCount = consecutiveYahooThrottleFailures;
            long computed = THROTTLE_COOLDOWN_BASE_MS * (1L << Math.max(0, throttleCount - 1));
            cooldownMs = Math.min(THROTTLE_COOLDOWN_MAX_MS, computed);
            long now = System.currentTimeMillis();
            long currentFloor = Math.max(now, nextAllowedRequestAtMs);
            nextAllowedRequestAtMs = currentFloor + cooldownMs;
        }
        System.out.println("Applying Yahoo cooldown " + cooldownMs + " ms after throttle-like failure"
                + " (count=" + throttleCount + ", retry=" + attempt + "): " + shortMessage(ex));
    }

    private String buildReferer(String url) {
        try {
            URL parsed = new URL(url);
            String host = parsed.getHost();
            if (host.contains("tw.stock.yahoo.com")) {
                String path = parsed.getPath();
                String[] segments = path.split("/");
                if (segments.length >= 3 && "quote".equals(segments[1])) {
                    return parsed.getProtocol() + "://" + host + "/quote/" + segments[2];
                }
                return parsed.getProtocol() + "://" + host + "/";
            }
            if (host.contains("finance.yahoo.com") || host.contains("query1.finance.yahoo.com")) {
                return "https://finance.yahoo.com/";
            }
            return parsed.getProtocol() + "://" + host + "/";
        } catch (Exception ex) {
            return "https://tw.stock.yahoo.com/";
        }
    }

    private boolean isRetryable(Exception ex) {
        if (ex instanceof HttpFetchException) {
            HttpFetchException fetchException = (HttpFetchException) ex;
            int status = fetchException.getStatusCode();
            return status == 999 || status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
        }

        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ENGLISH);
        return ex instanceof IOException || message.contains("timed out") || message.contains("reset")
                || message.contains("unexpected end of file") || message.contains("anti-bot");
    }

    private boolean isThrottleException(Exception ex) {
        if (ex instanceof HttpFetchException) {
            int status = ((HttpFetchException) ex).getStatusCode();
            return status == 999 || status == 429 || status == 503;
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ENGLISH);
        return message.contains("anti-bot") || message.contains("captcha");
    }

    private boolean containsYahooBlockPage(String url, String body) {
        if (!isYahooUrl(url) || body == null) {
            return false;
        }
        String normalized = body.toLowerCase(Locale.ENGLISH);
        return normalized.contains("999 unable to process request") || normalized.contains("please verify you are a human")
                || normalized.contains("captcha") || normalized.contains("access denied")
                || normalized.contains("request denied");
    }

    private boolean isYahooUrl(String url) {
        return url != null && url.contains("yahoo.com");
    }

    private long retryWaitMs(int attempt) {
        long jitter = REQUEST_JITTER_MS <= 0L ? 0L : RANDOM.nextInt((int) REQUEST_JITTER_MS + 1);
        return BASE_RETRY_WAIT_MS * (1L << Math.max(0, attempt - 1)) + jitter;
    }

    private String shortMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.trim().length() == 0) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }

    private static int parseIntProperty(String key, int defaultValue) {
        try {
            String value = System.getProperty(key);
            return value == null ? defaultValue : Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static long parseLongProperty(String key, long defaultValue) {
        try {
            String value = System.getProperty(key);
            return value == null ? defaultValue : Long.parseLong(value.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static final class HttpFetchException extends IOException {

        private static final long serialVersionUID = 1L;

        private final int statusCode;

        private HttpFetchException(String message, int statusCode, String responseBody) {
            super(messageWithSnippet(message, responseBody));
            this.statusCode = statusCode;
        }

        private int getStatusCode() {
            return statusCode;
        }

        private static String messageWithSnippet(String message, String responseBody) {
            if (responseBody == null || responseBody.trim().length() == 0) {
                return message;
            }
            String normalized = responseBody.replace('\n', ' ').replace('\r', ' ').trim();
            if (normalized.length() > 140) {
                normalized = normalized.substring(0, 140) + "...";
            }
            return message + ", BodySnippet=[" + normalized + "]";
        }
    }
}
