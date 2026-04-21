package stock.common;

public class NumberParser {

    public static boolean isFourDigitStockCode(String code) {
        return code != null && code.matches("\\d{4}");
    }

    public static long parseLong(String value) {
        if (value == null) {
            return 0L;
        }

        String normalized = value.trim().replace(",", "");
        if (normalized.length() == 0 || "-".equals(normalized) || "--".equals(normalized)) {
            return 0L;
        }

        return Long.parseLong(normalized);
    }

    public static double parseDouble(String value) {
        if (value == null) {
            return 0D;
        }

        String normalized = value.trim().replace(",", "").replace("%", "");
        if (normalized.length() == 0 || "-".equals(normalized) || "--".equals(normalized)) {
            return 0D;
        }

        return Double.parseDouble(normalized);
    }

    public static double parseDoubleOrNaN(String value) {
        if (value == null) {
            return Double.NaN;
        }
        String normalized = value.trim().replace(",", "").replace("%", "");
        if (normalized.length() == 0 || "-".equals(normalized) || "--".equals(normalized)) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static double ratioPercent(long numerator, long denominator) {
        if (denominator == 0L) {
            return 0D;
        }
        return (numerator * 100D) / denominator;
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
