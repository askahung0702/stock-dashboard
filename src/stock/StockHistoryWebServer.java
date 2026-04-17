package stock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import stock.btc.BtcApiRenderer;

public class StockHistoryWebServer {

    private static final int DEFAULT_PORT = 8788;
    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final long BTC_CACHE_TTL_MS = 60000L;

    public static void main(String[] args) throws Exception {
        String host = resolveHost(args);
        int port = resolvePort(args);
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", new DashboardHandler());
        server.createContext("/btc", new BtcPageHandler());
        server.createContext("/api/history", new HistoryJsonHandler());
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/latest", new LatestHandler());
        server.createContext("/api/btc/latest", new BtcLatestHandler());
        server.createContext("/api/stock/", new StockHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("Stock history web server started.");
        System.out.println("Dashboard:   http://" + displayHost(host) + ":" + port + "/");
        System.out.println("BTC:         http://" + displayHost(host) + ":" + port + "/btc");
        System.out.println("Old view:    http://" + displayHost(host) + ":" + port + "/old");
        System.out.println("Latest API:  http://" + displayHost(host) + ":" + port + "/api/latest");
        System.out.println("BTC API:     http://" + displayHost(host) + ":" + port + "/api/btc/latest");
        System.out.println("History API: http://" + displayHost(host) + ":" + port + "/api/history");
        System.out.println("Stock API:   http://" + displayHost(host) + ":" + port + "/api/stock/{code}");
        System.out.println("Press Ctrl+C to stop.");
    }

    private static String resolveHost(String[] args) {
        if (args != null) {
            for (String arg : args) {
                String value = safe(arg);
                if (value.length() > 0 && !value.matches("\\d+")) {
                    return value;
                }
            }
        }
        String envHost = safe(System.getenv("STOCK_SERVER_HOST"));
        return envHost.length() > 0 ? envHost : DEFAULT_HOST;
    }

    private static int resolvePort(String[] args) {
        if (args != null) {
            for (String arg : args) {
                String value = safe(arg);
                if (value.matches("\\d+")) {
                    return Integer.parseInt(value);
                }
            }
        }
        String envPort = safe(System.getenv("STOCK_SERVER_PORT"));
        if (envPort.matches("\\d+")) {
            return Integer.parseInt(envPort);
        }
        return DEFAULT_PORT;
    }

    private static String displayHost(String host) {
        return "0.0.0.0".equals(host) ? "localhost / server-ip" : host;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static StockDashboardWriter createWriter() {
        TaiwanStockAnalyzer analyzer = new TaiwanStockAnalyzer();
        return new StockDashboardWriter(analyzer.currentDateStamp(), analyzer.getLikelyThreshold(),
                analyzer.getWatchlistThreshold(), analyzer.getVolumeSurgeThreshold());
    }

    private static String readWebFile(String path) throws Exception {
        File file = new File(path);
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            reader.close();
        }
        return sb.toString();
    }

    private static void writeResponse(HttpExchange exchange, int statusCode, String contentType, byte[] body)
            throws Exception {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store, no-cache, must-revalidate");
        headers.set("Pragma", "no-cache");
        exchange.sendResponseHeaders(statusCode, body.length);
        OutputStream output = exchange.getResponseBody();
        try {
            output.write(body);
        } finally {
            output.close();
        }
    }

    private static boolean isGet(HttpExchange exchange) {
        return "GET".equalsIgnoreCase(exchange.getRequestMethod());
    }

    private static class DashboardHandler implements HttpHandler {
        public void handle(HttpExchange exchange) {
            try {
                String path = exchange.getRequestURI().getPath();
                if ("/favicon.ico".equals(path)) {
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                    return;
                }
                if (!isGet(exchange)) {
                    writeResponse(exchange, 405, "text/plain; charset=UTF-8", "Method Not Allowed".getBytes(UTF8.name()));
                    return;
                }
                if ("/old".equals(path)) {
                    String html = createWriter().renderHistoryDashboardHtml();
                    writeResponse(exchange, 200, "text/html; charset=UTF-8", html.getBytes(UTF8.name()));
                    return;
                }
                if ("/".equals(path)) {
                    String html = readWebFile("web/index.html");
                    writeResponse(exchange, 200, "text/html; charset=UTF-8", html.getBytes(UTF8.name()));
                    return;
                }
                writeResponse(exchange, 404, "text/plain; charset=UTF-8", "Not Found".getBytes(UTF8.name()));
            } catch (Exception ex) {
                try {
                    writeResponse(exchange, 500, "text/plain; charset=UTF-8", ("Server error: " + ex.getMessage()).getBytes(UTF8.name()));
                } catch (Exception ignored) {}
            }
        }
    }

    private static class BtcPageHandler implements HttpHandler {
        public void handle(HttpExchange exchange) {
            try {
                if (!isGet(exchange)) {
                    writeResponse(exchange, 405, "text/plain; charset=UTF-8", "Method Not Allowed".getBytes(UTF8.name()));
                    return;
                }
                String html = readWebFile("web/btc.html");
                writeResponse(exchange, 200, "text/html; charset=UTF-8", html.getBytes(UTF8.name()));
            } catch (Exception ex) {
                try {
                    writeResponse(exchange, 500, "text/plain; charset=UTF-8", ("Server error: " + ex.getMessage()).getBytes(UTF8.name()));
                } catch (Exception ignored) {}
            }
        }
    }

    private static class HistoryJsonHandler implements HttpHandler {
        public void handle(HttpExchange exchange) {
            try {
                if (!isGet(exchange)) {
                    writeResponse(exchange, 405, "text/plain; charset=UTF-8", "Method Not Allowed".getBytes(UTF8.name()));
                    return;
                }
                String json = createWriter().renderHistoryDataJson();
                writeResponse(exchange, 200, "application/json; charset=UTF-8", json.getBytes(UTF8.name()));
            } catch (Exception ex) {
                try {
                    writeResponse(exchange, 500, "text/plain; charset=UTF-8", ("Server error: " + ex.getMessage()).getBytes(UTF8.name()));
                } catch (Exception ignored) {}
            }
        }
    }

    private static class LatestHandler implements HttpHandler {
        public void handle(HttpExchange exchange) {
            try {
                if (!isGet(exchange)) {
                    writeResponse(exchange, 405, "text/plain; charset=UTF-8", "Method Not Allowed".getBytes(UTF8.name()));
                    return;
                }
                String json = new StockApiRenderer().renderLatestJson();
                writeResponse(exchange, 200, "application/json; charset=UTF-8", json.getBytes(UTF8.name()));
            } catch (Exception ex) {
                try {
                    writeResponse(exchange, 500, "text/plain; charset=UTF-8", ("Server error: " + ex.getMessage()).getBytes(UTF8.name()));
                } catch (Exception ignored) {}
            }
        }
    }

    private static class BtcLatestHandler implements HttpHandler {
        private static final Object CACHE_LOCK = new Object();
        private static String cachedJson = "";
        private static long cachedAtMs = 0L;

        public void handle(HttpExchange exchange) {
            try {
                if (!isGet(exchange)) {
                    writeResponse(exchange, 405, "text/plain; charset=UTF-8", "Method Not Allowed".getBytes(UTF8.name()));
                    return;
                }
                String json = cachedBtcJson();
                writeResponse(exchange, 200, "application/json; charset=UTF-8", json.getBytes(UTF8.name()));
            } catch (Exception ex) {
                try {
                    String stale = staleCachedBtcJson();
                    if (stale.length() > 0) {
                        writeResponse(exchange, 200, "application/json; charset=UTF-8", stale.getBytes(UTF8.name()));
                        return;
                    }
                    writeResponse(exchange, 500, "text/plain; charset=UTF-8", ("Server error: " + ex.getMessage()).getBytes(UTF8.name()));
                } catch (Exception ignored) {}
            }
        }

        private String cachedBtcJson() throws Exception {
            long now = System.currentTimeMillis();
            synchronized (CACHE_LOCK) {
                if (cachedJson.length() > 0 && now - cachedAtMs <= BTC_CACHE_TTL_MS) {
                    return cachedJson;
                }
            }
            String fresh = new BtcApiRenderer().renderLatestJson();
            synchronized (CACHE_LOCK) {
                cachedJson = fresh;
                cachedAtMs = now;
                return cachedJson;
            }
        }

        private String staleCachedBtcJson() {
            synchronized (CACHE_LOCK) {
                return cachedJson == null ? "" : cachedJson;
            }
        }
    }

    private static class StockHandler implements HttpHandler {
        public void handle(HttpExchange exchange) {
            try {
                if (!isGet(exchange)) {
                    writeResponse(exchange, 405, "text/plain; charset=UTF-8", "Method Not Allowed".getBytes(UTF8.name()));
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                String code = path.substring("/api/stock/".length()).trim();
                if (code.isEmpty() || !code.matches("[0-9A-Za-z]{1,10}")) {
                    writeResponse(exchange, 400, "text/plain; charset=UTF-8", "Invalid stock code".getBytes(UTF8.name()));
                    return;
                }
                String json = new StockApiRenderer().renderStockHistoryJson(code);
                writeResponse(exchange, 200, "application/json; charset=UTF-8", json.getBytes(UTF8.name()));
            } catch (Exception ex) {
                try {
                    writeResponse(exchange, 500, "text/plain; charset=UTF-8", ("Server error: " + ex.getMessage()).getBytes(UTF8.name()));
                } catch (Exception ignored) {}
            }
        }
    }

    private static class HealthHandler implements HttpHandler {
        public void handle(HttpExchange exchange) {
            try {
                if (!isGet(exchange)) {
                    writeResponse(exchange, 405, "text/plain; charset=UTF-8", "Method Not Allowed".getBytes(UTF8.name()));
                    return;
                }
                writeResponse(exchange, 200, "text/plain; charset=UTF-8", "ok".getBytes(UTF8.name()));
            } catch (Exception ex) {
                try {
                    writeResponse(exchange, 500, "text/plain; charset=UTF-8", ("Server error: " + ex.getMessage()).getBytes(UTF8.name()));
                } catch (Exception ignored) {}
            }
        }
    }
}
