package stock;

import java.util.ArrayList;
import java.util.List;

import stock.vo.BalanceSheetRecordVO;
import stock.vo.CashFlowRecordVO;
import stock.vo.EpsRecordVO;
import stock.vo.IncomeStatementRecordVO;
import stock.vo.MonthlyRevenueVO;

public class FinancialDataBundle {

    private final String sourceName;
    private final List<MonthlyRevenueVO> revenues;
    private final List<EpsRecordVO> epsRecords;
    private final List<IncomeStatementRecordVO> incomeRecords;
    private final List<BalanceSheetRecordVO> balanceRecords;
    private final List<CashFlowRecordVO> cashFlowRecords;

    public FinancialDataBundle(String sourceName, List<MonthlyRevenueVO> revenues, List<EpsRecordVO> epsRecords,
            List<IncomeStatementRecordVO> incomeRecords, List<BalanceSheetRecordVO> balanceRecords,
            List<CashFlowRecordVO> cashFlowRecords) {
        this.sourceName = sourceName == null ? "" : sourceName;
        this.revenues = safeList(revenues);
        this.epsRecords = safeList(epsRecords);
        this.incomeRecords = safeList(incomeRecords);
        this.balanceRecords = safeList(balanceRecords);
        this.cashFlowRecords = safeList(cashFlowRecords);
    }

    public static FinancialDataBundle empty(String sourceName) {
        return new FinancialDataBundle(sourceName, null, null, null, null, null);
    }

    public String getSourceName() {
        return sourceName;
    }

    public List<MonthlyRevenueVO> getRevenues() {
        return revenues;
    }

    public List<EpsRecordVO> getEpsRecords() {
        return epsRecords;
    }

    public List<IncomeStatementRecordVO> getIncomeRecords() {
        return incomeRecords;
    }

    public List<BalanceSheetRecordVO> getBalanceRecords() {
        return balanceRecords;
    }

    public List<CashFlowRecordVO> getCashFlowRecords() {
        return cashFlowRecords;
    }

    public boolean hasRevenueData() {
        return !revenues.isEmpty();
    }

    public boolean hasFinancialData() {
        return !epsRecords.isEmpty() || !incomeRecords.isEmpty() || !balanceRecords.isEmpty()
                || !cashFlowRecords.isEmpty();
    }

    public String latestRevenuePeriod() {
        return revenues.isEmpty() ? "" : safeText(revenues.get(0).getPeriod());
    }

    public String latestFinancialPeriod() {
        if (!epsRecords.isEmpty()) {
            return safeText(epsRecords.get(0).getPeriod());
        }
        if (!incomeRecords.isEmpty()) {
            return safeText(incomeRecords.get(0).getPeriod());
        }
        if (!balanceRecords.isEmpty()) {
            return safeText(balanceRecords.get(0).getPeriod());
        }
        if (!cashFlowRecords.isEmpty()) {
            return safeText(cashFlowRecords.get(0).getPeriod());
        }
        return "";
    }

    private static <T> List<T> safeList(List<T> source) {
        return source == null ? new ArrayList<T>() : source;
    }

    private static String safeText(String text) {
        return text == null ? "" : text.trim();
    }
}
