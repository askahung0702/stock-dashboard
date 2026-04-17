package stock;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

public class StockStaticApiExporter {

    public static void main(String[] args) throws Exception {
        String latestPath = args.length > 0 ? args[0] : new File("web\\data", "latest.json").getPath();
        String historyPath = args.length > 1 ? args[1] : new File("web\\data", "history.json").getPath();
        StockStaticApiExporter exporter = new StockStaticApiExporter();
        exporter.writeDefaultOutputs(latestPath, historyPath);
        System.out.println("Static latest JSON: " + new File(latestPath).getAbsolutePath());
        System.out.println("Static history JSON: " + new File(historyPath).getAbsolutePath());
    }

    public void writeDefaultOutputs(String latestPath, String historyPath) throws Exception {
        StockApiRenderer apiRenderer = new StockApiRenderer();
        writeUtf8(latestPath, apiRenderer.renderLatestJson());

        TaiwanStockAnalyzer analyzer = new TaiwanStockAnalyzer();
        StockDashboardWriter writer = new StockDashboardWriter(analyzer.currentDateStamp(), analyzer.getLikelyThreshold(),
                analyzer.getWatchlistThreshold(), analyzer.getVolumeSurgeThreshold());
        writeUtf8(historyPath, writer.renderHistoryDataJson());
    }

    private void writeUtf8(String path, String content) throws Exception {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
        try {
            writer.write(content == null ? "" : content);
        } finally {
            writer.close();
        }
    }
}
