package stock.btc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class BtcHistoryStore {

    private static final String HISTORY_DIRECTORY_NAME = "history";
    private static final String DATABASE_FILE_NAME = "btc_market_history.json";
    private static final long VERSION = 1L;

    public void upsertSnapshot(String date, JSONObject snapshot) throws Exception {
        File historyDirectory = ensureHistoryDirectory();
        JSONObject root = loadRoot(historyDirectory);
        JSONObject snapshots = snapshotsObject(root);
        snapshots.put(date, snapshot);
        root.put("version", Long.valueOf(VERSION));
        root.put("updatedDate", date == null ? "" : date);
        writeRoot(historyDirectory, root);
    }

    public Map<String, JSONObject> loadSnapshots() throws Exception {
        File historyDirectory = ensureHistoryDirectory();
        JSONObject root = loadRoot(historyDirectory);
        JSONObject snapshots = snapshotsObject(root);
        Map<String, JSONObject> result = new HashMap<String, JSONObject>();
        for (Object key : snapshots.keySet()) {
            if (key == null) {
                continue;
            }
            Object value = snapshots.get(key);
            if (value instanceof JSONObject) {
                result.put(key.toString(), (JSONObject) value);
            }
        }
        return result;
    }

    public List<String> sortedDates(Map<String, JSONObject> snapshots) {
        List<String> dates = new ArrayList<String>(snapshots.keySet());
        Collections.sort(dates);
        return dates;
    }

    private JSONObject loadRoot(File historyDirectory) throws Exception {
        File file = dbFile(historyDirectory);
        if (!file.exists() || file.length() == 0L) {
            return emptyRoot();
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            Object parsed = new JSONParser().parse(reader);
            if (parsed instanceof JSONObject) {
                JSONObject root = (JSONObject) parsed;
                snapshotsObject(root);
                return root;
            }
        } catch (Exception ignored) {
            return emptyRoot();
        } finally {
            reader.close();
        }
        return emptyRoot();
    }

    private void writeRoot(File historyDirectory, JSONObject root) throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dbFile(historyDirectory)), "UTF-8"));
        try {
            writer.write(root.toJSONString());
        } finally {
            writer.close();
        }
    }

    private JSONObject emptyRoot() {
        JSONObject root = new JSONObject();
        root.put("version", Long.valueOf(VERSION));
        root.put("updatedDate", "");
        root.put("snapshots", new JSONObject());
        return root;
    }

    private JSONObject snapshotsObject(JSONObject root) {
        Object current = root.get("snapshots");
        if (current instanceof JSONObject) {
            return (JSONObject) current;
        }
        JSONObject snapshots = new JSONObject();
        root.put("snapshots", snapshots);
        return snapshots;
    }

    private File ensureHistoryDirectory() {
        File dir = new File(HISTORY_DIRECTORY_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File dbFile(File historyDirectory) {
        return new File(historyDirectory, DATABASE_FILE_NAME);
    }
}
