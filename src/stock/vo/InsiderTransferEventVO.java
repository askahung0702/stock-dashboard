package stock.vo;

import org.json.simple.JSONObject;

public class InsiderTransferEventVO {

    private final String reportDate;
    private final String code;
    private final String name;
    private final String insiderName;
    private final String insiderRole;
    private final String transferMethod;
    private final long plannedTransferShares;
    private final long currentHoldingShares;
    private final String transferStartDate;
    private final String transferEndDate;
    private final JSONObject rawJson;

    public InsiderTransferEventVO(String reportDate, String code, String name, String insiderName, String insiderRole,
            String transferMethod, long plannedTransferShares, long currentHoldingShares, String transferStartDate,
            String transferEndDate, JSONObject rawJson) {
        this.reportDate = reportDate == null ? "" : reportDate;
        this.code = code == null ? "" : code;
        this.name = name == null ? "" : name;
        this.insiderName = insiderName == null ? "" : insiderName;
        this.insiderRole = insiderRole == null ? "" : insiderRole;
        this.transferMethod = transferMethod == null ? "" : transferMethod;
        this.plannedTransferShares = plannedTransferShares;
        this.currentHoldingShares = currentHoldingShares;
        this.transferStartDate = transferStartDate == null ? "" : transferStartDate;
        this.transferEndDate = transferEndDate == null ? "" : transferEndDate;
        this.rawJson = rawJson == null ? new JSONObject() : rawJson;
    }

    public String getReportDate() {
        return reportDate;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getInsiderName() {
        return insiderName;
    }

    public String getInsiderRole() {
        return insiderRole;
    }

    public String getTransferMethod() {
        return transferMethod;
    }

    public long getPlannedTransferShares() {
        return plannedTransferShares;
    }

    public long getCurrentHoldingShares() {
        return currentHoldingShares;
    }

    public String getTransferStartDate() {
        return transferStartDate;
    }

    public String getTransferEndDate() {
        return transferEndDate;
    }

    public JSONObject getRawJson() {
        return rawJson;
    }

    public String stableKey() {
        return reportDate + "|" + code + "|" + insiderName + "|" + insiderRole + "|" + transferMethod + "|"
                + plannedTransferShares + "|" + transferStartDate + "|" + transferEndDate + "|"
                + rawJson.toJSONString().hashCode();
    }
}
