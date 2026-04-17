package stock.vo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class NewsSignalVO {

    private List<String> headlines = new ArrayList<String>();
    private List<NewsItem> items = new ArrayList<NewsItem>();
    private int headlineCount;
    private boolean hasNewsPage;
    private String summaryText = "";
    private String sourceSummary = "";
    private String latestPublishedHint = "";
    private double sourceCredibilityScore;
    private double freshnessScore;
    private int sourceCount;
    private int officialSourceCount;
    private int mediaSourceCount;

    public List<String> getHeadlines() {
        return headlines;
    }

    public void setHeadlines(List<String> headlines) {
        this.headlines = copyStrings(headlines);
        this.headlineCount = this.headlines.size();
        if (this.summaryText == null || this.summaryText.length() == 0) {
            this.summaryText = join(this.headlines, "；");
        }
    }

    public List<NewsItem> getItems() {
        return items;
    }

    public void setItems(List<NewsItem> items) {
        this.items = new ArrayList<NewsItem>();
        if (items != null) {
            for (NewsItem item : items) {
                if (item == null) {
                    continue;
                }
                this.items.add(item.copy());
            }
        }
        refreshFromItems();
    }

    public void addItem(NewsItem item) {
        if (item == null) {
            return;
        }
        items.add(item.copy());
        refreshFromItems();
    }

    public int getHeadlineCount() {
        return headlineCount;
    }

    public void setHeadlineCount(int headlineCount) {
        this.headlineCount = headlineCount;
    }

    public boolean isHasNewsPage() {
        return hasNewsPage;
    }

    public boolean getHasNewsPage() {
        return hasNewsPage;
    }

    public void setHasNewsPage(boolean hasNewsPage) {
        this.hasNewsPage = hasNewsPage;
    }

    public String getSummaryText() {
        return summaryText == null ? "" : summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = safe(summaryText);
    }

    public String getSourceSummary() {
        return sourceSummary == null ? "" : sourceSummary;
    }

    public void setSourceSummary(String sourceSummary) {
        this.sourceSummary = safe(sourceSummary);
    }

    public String getLatestPublishedHint() {
        return latestPublishedHint == null ? "" : latestPublishedHint;
    }

    public void setLatestPublishedHint(String latestPublishedHint) {
        this.latestPublishedHint = safe(latestPublishedHint);
    }

    public double getSourceCredibilityScore() {
        return sourceCredibilityScore;
    }

    public void setSourceCredibilityScore(double sourceCredibilityScore) {
        this.sourceCredibilityScore = sanitizeScore(sourceCredibilityScore);
    }

    public double getFreshnessScore() {
        return freshnessScore;
    }

    public void setFreshnessScore(double freshnessScore) {
        this.freshnessScore = sanitizeScore(freshnessScore);
    }

    public int getSourceCount() {
        return sourceCount;
    }

    public void setSourceCount(int sourceCount) {
        this.sourceCount = Math.max(0, sourceCount);
    }

    public int getOfficialSourceCount() {
        return officialSourceCount;
    }

    public void setOfficialSourceCount(int officialSourceCount) {
        this.officialSourceCount = Math.max(0, officialSourceCount);
    }

    public int getMediaSourceCount() {
        return mediaSourceCount;
    }

    public void setMediaSourceCount(int mediaSourceCount) {
        this.mediaSourceCount = Math.max(0, mediaSourceCount);
    }

    public void refreshFromItems() {
        LinkedHashSet<String> headlineSet = new LinkedHashSet<String>();
        LinkedHashMap<String, Integer> sourceCounters = new LinkedHashMap<String, Integer>();
        double credibilityTotal = 0D;
        double freshnessTotal = 0D;
        int credibilitySamples = 0;
        int freshnessSamples = 0;
        int officialCount = 0;
        int mediaCount = 0;
        String freshestHint = "";
        double freshestScore = -1D;

        for (NewsItem item : items) {
            if (item == null) {
                continue;
            }
            String title = safe(item.getTitle());
            if (title.length() > 0 && !headlineSet.contains(title)) {
                headlineSet.add(title);
            }

            String sourceName = safe(item.getSourceName());
            String sourceType = safe(item.getSourceType());
            String sourceKey = sourceType + "|" + sourceName;
            if (sourceName.length() > 0) {
                Integer current = sourceCounters.get(sourceKey);
                sourceCounters.put(sourceKey, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
            }

            if ("OFFICIAL".equalsIgnoreCase(sourceType)) {
                officialCount++;
            } else if (sourceType.length() > 0) {
                mediaCount++;
            }

            if (item.getSourceCredibilityScore() > 0D) {
                credibilityTotal += sanitizeScore(item.getSourceCredibilityScore());
                credibilitySamples++;
            }
            if (item.getFreshnessScore() > 0D) {
                double score = sanitizeScore(item.getFreshnessScore());
                freshnessTotal += score;
                freshnessSamples++;
                if (score > freshestScore && safe(item.getPublishedHint()).length() > 0) {
                    freshestScore = score;
                    freshestHint = safe(item.getPublishedHint());
                }
            } else if (freshestHint.length() == 0 && safe(item.getPublishedHint()).length() > 0) {
                freshestHint = safe(item.getPublishedHint());
            }
        }

        this.headlines = new ArrayList<String>(headlineSet);
        this.headlineCount = this.headlines.size();
        this.summaryText = join(this.headlines, "；");
        this.sourceSummary = buildSourceSummary(sourceCounters);
        this.latestPublishedHint = freshestHint;
        this.sourceCredibilityScore = credibilitySamples <= 0 ? 0D : credibilityTotal / credibilitySamples;
        this.freshnessScore = freshnessSamples <= 0 ? 0D : freshnessTotal / freshnessSamples;
        this.sourceCount = sourceCounters.size();
        this.officialSourceCount = officialCount;
        this.mediaSourceCount = mediaCount;
    }

    private List<String> copyStrings(List<String> values) {
        List<String> copied = new ArrayList<String>();
        if (values == null) {
            return copied;
        }
        for (String value : values) {
            String text = safe(value);
            if (text.length() == 0 || copied.contains(text)) {
                continue;
            }
            copied.add(text);
        }
        return copied;
    }

    private String buildSourceSummary(Map<String, Integer> sourceCounters) {
        List<String> tokens = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : sourceCounters.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            String name = parts.length >= 2 ? safe(parts[1]) : safe(entry.getKey());
            if (name.length() == 0) {
                continue;
            }
            tokens.add(name + " " + entry.getValue() + "則");
        }
        return join(tokens, "；");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String join(List<String> values, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(delimiter);
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private double sanitizeScore(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        if (value < 0D) {
            return 0D;
        }
        if (value > 100D) {
            return 100D;
        }
        return value;
    }

    public static class NewsItem {

        private String title = "";
        private String sourceType = "";
        private String sourceName = "";
        private String publishedHint = "";
        private String url = "";
        private double sourceCredibilityScore;
        private double freshnessScore;

        public NewsItem() {
        }

        public NewsItem(String title, String sourceType, String sourceName, String publishedHint, String url,
                double sourceCredibilityScore, double freshnessScore) {
            this.title = safe(title);
            this.sourceType = safe(sourceType);
            this.sourceName = safe(sourceName);
            this.publishedHint = safe(publishedHint);
            this.url = safe(url);
            this.sourceCredibilityScore = sourceCredibilityScore;
            this.freshnessScore = freshnessScore;
        }

        public NewsItem copy() {
            return new NewsItem(title, sourceType, sourceName, publishedHint, url, sourceCredibilityScore,
                    freshnessScore);
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = safe(title);
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = safe(sourceType);
        }

        public String getSourceName() {
            return sourceName;
        }

        public void setSourceName(String sourceName) {
            this.sourceName = safe(sourceName);
        }

        public String getPublishedHint() {
            return publishedHint;
        }

        public void setPublishedHint(String publishedHint) {
            this.publishedHint = safe(publishedHint);
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = safe(url);
        }

        public double getSourceCredibilityScore() {
            return sourceCredibilityScore;
        }

        public void setSourceCredibilityScore(double sourceCredibilityScore) {
            this.sourceCredibilityScore = sourceCredibilityScore;
        }

        public double getFreshnessScore() {
            return freshnessScore;
        }

        public void setFreshnessScore(double freshnessScore) {
            this.freshnessScore = freshnessScore;
        }

        private static String safe(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
