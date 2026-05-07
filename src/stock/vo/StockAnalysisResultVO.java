package stock.vo;

import java.util.ArrayList;
import java.util.List;

public class StockAnalysisResultVO {

    private TaiwanStockVO stock;
    private List<MonthlyRevenueVO> revenues = new ArrayList<MonthlyRevenueVO>();
    private List<InstitutionalTradingDailyVO> institutionalDaily = new ArrayList<InstitutionalTradingDailyVO>();
    private BrokerTradingSummaryVO brokerSummary;
    private double latestRevenueYoY;
    private double averageThreeMonthRevenueYoY;
    private double accumulatedRevenueYoY;
    private int positiveRevenueMonths;
    private long latestInstitutionalNetLots;
    private double latestInstitutionalNetRatioPct;
    private long fiveDayInstitutionalNetLots;
    private double fiveDayInstitutionalNetRatioPct;
    private long latestForeignNetLots;
    private long brokerNetLots;
    private double brokerNetRatioPct;
    private String marginDataDate = "";
    private long marginBalance;
    private long previousMarginBalance;
    private long marginBalanceDelta;
    private long marginBuy;
    private long marginSell;
    private long marginCashRepay;
    private long marginLimit;
    private double marginUsagePct;
    private long shortBalance;
    private long previousShortBalance;
    private long shortBalanceDelta;
    private double shortMarginRatioPct;
    private double shortUsagePct;
    private String marginTradingNote = "";
    private double currentPrice;
    private double trailingFourQuarterEps;
    private double fairValueEps;
    private double trailingPe;
    private double latestQuarterEps;
    private double latestQuarterEpsYoYPct;
    private int positiveEpsQuarters;
    private long latestOperatingCashFlow;
    private long latestFreeCashFlow;
    private int positiveOperatingCashFlowQuarters;
    private int positiveFreeCashFlowQuarters;
    private double movingAverage18;
    private double movingAverage20;
    private double movingAverage54;
    private double movingAverage60;
    private double movingAverage120;
    private double return18DayPct;
    private double return20DayPct;
    private double return54DayPct;
    private double return60DayPct;
    private double volumeRatio;
    private double averageLots20;
    private double averageTradeValue20Billion;
    private double volatility20Pct;
    private double atr20;
    private double drawdownFromHigh60Pct;
    private String industry;
    private double peerAveragePe;
    private double grossMarginPct;
    private double operatingMarginPct;
    private double returnOnAssetsPct;
    private double returnOnEquityPct;
    private double bookValue;
    private double debtRatioPct;
    private double currentRatio;
    private double nonOperatingRatioPct;
    private double liquidityScore;
    private double eventRiskPenalty;
    private double rsi14;
    private double stochasticK;
    private double stochasticD;
    private double ma20Slope;
    private double epsAccelerationPct;
    private double peg;
    private double revenueScore;
    private double chipsScore;
    private double valuationScore;
    private double technicalScore;
    private double financialQualityScore;
    private double valuationIndustryPercentile;
    private double financialQualityIndustryPercentile;
    private double grossMarginIndustryPercentile;
    private double operatingMarginIndustryPercentile;
    private double roaIndustryPercentile;
    private double roeIndustryPercentile;
    private double pegIndustryPercentile;
    private double relativePeIndustryPercentile;
    private double nonOperatingIndustryPercentile;
    private double rawScore;
    private double selectionScore;
    private double momentumScore;
    private double qualityScore;
    private double sectorScore;
    private double themeScore;
    private double trendPersistenceScore;
    private int trendPersistenceDays;
    private double newsScore;
    private double newsRiskScore;
    private double relativeStrengthScore;
    private double industryReturnStrength;
    private double industryVolumeStrength;
    private double industryFlowStrength;
    private String eventDirection;
    private double eventConfidence;
    private int eventFreshnessDays;
    private String eventTypeSummary;
    private double structureScore;
    private String structureLabel;
    private double riskRewardScore;
    private double riskRewardRatio;
    private double turnaroundScore;
    private double revenueGrowthSignalScore;
    private double earningsTurnaroundSignalScore;
    private double profitabilityTurnaroundSignalScore;
    private double oneOffRiskScore;
    private double suggestedStopPrice;
    private double suggestedStopPct;
    private double suggestedTrailingStopPrice;
    private double suggestedTargetPrice;
    private double fairValueLow;
    private double fairValueBase;
    private double fairValueHigh;
    private double fairValueConfidence;
    private double upsidePotentialPct;
    private double sellSignalScore;
    private String sellSignalLabel;
    private boolean reducePositionSize;
    private double buyPointScore;
    private double dataConfidence;
    private boolean selectionQualified;
    private double score;
    private String analysisNote;
    private String companySummary;
    private String recentNewsBrief;
    private String transformationHint;
    private String practicalAdvice;
    private double adviceConfidence;
    private String fairValueMethod;
    private String fairValueReason;
    private String marketRegime;
    private String snapshotStage;
    private boolean techReady;
    private boolean marketReady;
    private boolean institutionalReady;
    private boolean brokerReady;
    private boolean financialReady;
    private boolean newsReady;
    private String analysisVersion;
    private String sourceUpdatedAt;
    private String primaryTheme;
    private String themeTags;
    private String launchTags;
    private String newsSummary;
    private String newsDigest;
    private String newsSourceSummary;
    private String latestNewsPublishedHint;
    private double newsSourceCredibilityScore;
    private double newsFreshnessScore;
    private int newsSourceCount;
    private int newsOfficialSourceCount;
    private int newsMediaSourceCount;
    private double themeReferenceScore;
    private String themeReferenceTheme;
    private String themeReferenceIndustry;
    private String themeReferenceReason;
    private double marketThemeReferenceScore;
    private String marketThemeReferenceTheme;
    private String marketThemeReferenceReason;
    private String scoreReason;
    private String revenueReason;
    private String chipsReason;
    private String valuationReason;
    private String technicalReason;
    private String financialQualityReason;
    private String liquidityReason;
    private String eventRiskReason;
    private String eligibilityReason;
    private String buyPointLabel;
    private String buyPointReason;
    private String dataConfidenceReason;
    private String turnaroundLabel;
    private String turnaroundReason;
    private String signalType;
    private int signalHorizonDays;
    private String entryRule;
    private String exitRule;
    private String validationMode;
    private boolean hardExclude;
    private String hardExcludeReason;
    private String dataQualityGrade;
    private int coreConditionCount;
    private double winratePriorityScore;
    private double expectedReturnScore;
    private double maxDrawdownPenalty;
    private String backtestCohort;
    private double postClosePriorityScore;
    private String postCloseCategory;
    private String postCloseAction;
    private String postCloseReason;

    public TaiwanStockVO getStock() {
        return stock;
    }

    public void setStock(TaiwanStockVO stock) {
        this.stock = stock;
    }

    public List<MonthlyRevenueVO> getRevenues() {
        return revenues;
    }

    public void setRevenues(List<MonthlyRevenueVO> revenues) {
        this.revenues = revenues;
    }

    public List<InstitutionalTradingDailyVO> getInstitutionalDaily() {
        return institutionalDaily;
    }

    public void setInstitutionalDaily(List<InstitutionalTradingDailyVO> institutionalDaily) {
        this.institutionalDaily = institutionalDaily;
    }

    public BrokerTradingSummaryVO getBrokerSummary() {
        return brokerSummary;
    }

    public void setBrokerSummary(BrokerTradingSummaryVO brokerSummary) {
        this.brokerSummary = brokerSummary;
    }

    public double getLatestRevenueYoY() {
        return latestRevenueYoY;
    }

    public void setLatestRevenueYoY(double latestRevenueYoY) {
        this.latestRevenueYoY = latestRevenueYoY;
    }

    public double getAverageThreeMonthRevenueYoY() {
        return averageThreeMonthRevenueYoY;
    }

    public void setAverageThreeMonthRevenueYoY(double averageThreeMonthRevenueYoY) {
        this.averageThreeMonthRevenueYoY = averageThreeMonthRevenueYoY;
    }

    public double getAccumulatedRevenueYoY() {
        return accumulatedRevenueYoY;
    }

    public void setAccumulatedRevenueYoY(double accumulatedRevenueYoY) {
        this.accumulatedRevenueYoY = accumulatedRevenueYoY;
    }

    public int getPositiveRevenueMonths() {
        return positiveRevenueMonths;
    }

    public void setPositiveRevenueMonths(int positiveRevenueMonths) {
        this.positiveRevenueMonths = positiveRevenueMonths;
    }

    public long getLatestInstitutionalNetLots() {
        return latestInstitutionalNetLots;
    }

    public void setLatestInstitutionalNetLots(long latestInstitutionalNetLots) {
        this.latestInstitutionalNetLots = latestInstitutionalNetLots;
    }

    public double getLatestInstitutionalNetRatioPct() {
        return latestInstitutionalNetRatioPct;
    }

    public void setLatestInstitutionalNetRatioPct(double latestInstitutionalNetRatioPct) {
        this.latestInstitutionalNetRatioPct = latestInstitutionalNetRatioPct;
    }

    public long getFiveDayInstitutionalNetLots() {
        return fiveDayInstitutionalNetLots;
    }

    public void setFiveDayInstitutionalNetLots(long fiveDayInstitutionalNetLots) {
        this.fiveDayInstitutionalNetLots = fiveDayInstitutionalNetLots;
    }

    public double getFiveDayInstitutionalNetRatioPct() {
        return fiveDayInstitutionalNetRatioPct;
    }

    public void setFiveDayInstitutionalNetRatioPct(double fiveDayInstitutionalNetRatioPct) {
        this.fiveDayInstitutionalNetRatioPct = fiveDayInstitutionalNetRatioPct;
    }

    public long getLatestForeignNetLots() {
        return latestForeignNetLots;
    }

    public void setLatestForeignNetLots(long latestForeignNetLots) {
        this.latestForeignNetLots = latestForeignNetLots;
    }

    public long getBrokerNetLots() {
        return brokerNetLots;
    }

    public void setBrokerNetLots(long brokerNetLots) {
        this.brokerNetLots = brokerNetLots;
    }

    public double getBrokerNetRatioPct() {
        return brokerNetRatioPct;
    }

    public void setBrokerNetRatioPct(double brokerNetRatioPct) {
        this.brokerNetRatioPct = brokerNetRatioPct;
    }

    public String getMarginDataDate() {
        return marginDataDate;
    }

    public void setMarginDataDate(String marginDataDate) {
        this.marginDataDate = marginDataDate;
    }

    public long getMarginBalance() {
        return marginBalance;
    }

    public void setMarginBalance(long marginBalance) {
        this.marginBalance = marginBalance;
    }

    public long getPreviousMarginBalance() {
        return previousMarginBalance;
    }

    public void setPreviousMarginBalance(long previousMarginBalance) {
        this.previousMarginBalance = previousMarginBalance;
    }

    public long getMarginBalanceDelta() {
        return marginBalanceDelta;
    }

    public void setMarginBalanceDelta(long marginBalanceDelta) {
        this.marginBalanceDelta = marginBalanceDelta;
    }

    public long getMarginBuy() {
        return marginBuy;
    }

    public void setMarginBuy(long marginBuy) {
        this.marginBuy = marginBuy;
    }

    public long getMarginSell() {
        return marginSell;
    }

    public void setMarginSell(long marginSell) {
        this.marginSell = marginSell;
    }

    public long getMarginCashRepay() {
        return marginCashRepay;
    }

    public void setMarginCashRepay(long marginCashRepay) {
        this.marginCashRepay = marginCashRepay;
    }

    public long getMarginLimit() {
        return marginLimit;
    }

    public void setMarginLimit(long marginLimit) {
        this.marginLimit = marginLimit;
    }

    public double getMarginUsagePct() {
        return marginUsagePct;
    }

    public void setMarginUsagePct(double marginUsagePct) {
        this.marginUsagePct = marginUsagePct;
    }

    public long getShortBalance() {
        return shortBalance;
    }

    public void setShortBalance(long shortBalance) {
        this.shortBalance = shortBalance;
    }

    public long getPreviousShortBalance() {
        return previousShortBalance;
    }

    public void setPreviousShortBalance(long previousShortBalance) {
        this.previousShortBalance = previousShortBalance;
    }

    public long getShortBalanceDelta() {
        return shortBalanceDelta;
    }

    public void setShortBalanceDelta(long shortBalanceDelta) {
        this.shortBalanceDelta = shortBalanceDelta;
    }

    public double getShortMarginRatioPct() {
        return shortMarginRatioPct;
    }

    public void setShortMarginRatioPct(double shortMarginRatioPct) {
        this.shortMarginRatioPct = shortMarginRatioPct;
    }

    public double getShortUsagePct() {
        return shortUsagePct;
    }

    public void setShortUsagePct(double shortUsagePct) {
        this.shortUsagePct = shortUsagePct;
    }

    public String getMarginTradingNote() {
        return marginTradingNote;
    }

    public void setMarginTradingNote(String marginTradingNote) {
        this.marginTradingNote = marginTradingNote;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public double getTrailingFourQuarterEps() {
        return trailingFourQuarterEps;
    }

    public void setTrailingFourQuarterEps(double trailingFourQuarterEps) {
        this.trailingFourQuarterEps = trailingFourQuarterEps;
    }

    public double getFairValueEps() {
        return fairValueEps;
    }

    public void setFairValueEps(double fairValueEps) {
        this.fairValueEps = fairValueEps;
    }

    public double getTrailingPe() {
        return trailingPe;
    }

    public void setTrailingPe(double trailingPe) {
        this.trailingPe = trailingPe;
    }

    public double getLatestQuarterEps() {
        return latestQuarterEps;
    }

    public void setLatestQuarterEps(double latestQuarterEps) {
        this.latestQuarterEps = latestQuarterEps;
    }

    public double getLatestQuarterEpsYoYPct() {
        return latestQuarterEpsYoYPct;
    }

    public void setLatestQuarterEpsYoYPct(double latestQuarterEpsYoYPct) {
        this.latestQuarterEpsYoYPct = latestQuarterEpsYoYPct;
    }

    public int getPositiveEpsQuarters() {
        return positiveEpsQuarters;
    }

    public void setPositiveEpsQuarters(int positiveEpsQuarters) {
        this.positiveEpsQuarters = positiveEpsQuarters;
    }

    public long getLatestOperatingCashFlow() {
        return latestOperatingCashFlow;
    }

    public void setLatestOperatingCashFlow(long latestOperatingCashFlow) {
        this.latestOperatingCashFlow = latestOperatingCashFlow;
    }

    public long getLatestFreeCashFlow() {
        return latestFreeCashFlow;
    }

    public void setLatestFreeCashFlow(long latestFreeCashFlow) {
        this.latestFreeCashFlow = latestFreeCashFlow;
    }

    public int getPositiveOperatingCashFlowQuarters() {
        return positiveOperatingCashFlowQuarters;
    }

    public void setPositiveOperatingCashFlowQuarters(int positiveOperatingCashFlowQuarters) {
        this.positiveOperatingCashFlowQuarters = positiveOperatingCashFlowQuarters;
    }

    public int getPositiveFreeCashFlowQuarters() {
        return positiveFreeCashFlowQuarters;
    }

    public void setPositiveFreeCashFlowQuarters(int positiveFreeCashFlowQuarters) {
        this.positiveFreeCashFlowQuarters = positiveFreeCashFlowQuarters;
    }

    public double getMovingAverage20() {
        return movingAverage20;
    }

    public double getMovingAverage18() {
        return movingAverage18;
    }

    public void setMovingAverage18(double movingAverage18) {
        this.movingAverage18 = movingAverage18;
    }

    public void setMovingAverage20(double movingAverage20) {
        this.movingAverage20 = movingAverage20;
    }

    public double getMovingAverage54() {
        return movingAverage54;
    }

    public void setMovingAverage54(double movingAverage54) {
        this.movingAverage54 = movingAverage54;
    }

    public double getMovingAverage60() {
        return movingAverage60;
    }

    public void setMovingAverage60(double movingAverage60) {
        this.movingAverage60 = movingAverage60;
    }

    public double getMovingAverage120() {
        return movingAverage120;
    }

    public void setMovingAverage120(double movingAverage120) {
        this.movingAverage120 = movingAverage120;
    }

    public double getReturn20DayPct() {
        return return20DayPct;
    }

    public double getReturn18DayPct() {
        return return18DayPct;
    }

    public void setReturn18DayPct(double return18DayPct) {
        this.return18DayPct = return18DayPct;
    }

    public void setReturn20DayPct(double return20DayPct) {
        this.return20DayPct = return20DayPct;
    }

    public double getReturn54DayPct() {
        return return54DayPct;
    }

    public void setReturn54DayPct(double return54DayPct) {
        this.return54DayPct = return54DayPct;
    }

    public double getReturn60DayPct() {
        return return60DayPct;
    }

    public void setReturn60DayPct(double return60DayPct) {
        this.return60DayPct = return60DayPct;
    }

    public double getVolumeRatio() {
        return volumeRatio;
    }

    public void setVolumeRatio(double volumeRatio) {
        this.volumeRatio = volumeRatio;
    }

    public double getAverageLots20() {
        return averageLots20;
    }

    public void setAverageLots20(double averageLots20) {
        this.averageLots20 = averageLots20;
    }

    public double getAverageTradeValue20Billion() {
        return averageTradeValue20Billion;
    }

    public void setAverageTradeValue20Billion(double averageTradeValue20Billion) {
        this.averageTradeValue20Billion = averageTradeValue20Billion;
    }

    public double getVolatility20Pct() {
        return volatility20Pct;
    }

    public void setVolatility20Pct(double volatility20Pct) {
        this.volatility20Pct = volatility20Pct;
    }

    public double getAtr20() {
        return atr20;
    }

    public void setAtr20(double atr20) {
        this.atr20 = atr20;
    }

    public double getDrawdownFromHigh60Pct() {
        return drawdownFromHigh60Pct;
    }

    public void setDrawdownFromHigh60Pct(double drawdownFromHigh60Pct) {
        this.drawdownFromHigh60Pct = drawdownFromHigh60Pct;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public double getPeerAveragePe() {
        return peerAveragePe;
    }

    public void setPeerAveragePe(double peerAveragePe) {
        this.peerAveragePe = peerAveragePe;
    }

    public double getGrossMarginPct() {
        return grossMarginPct;
    }

    public void setGrossMarginPct(double grossMarginPct) {
        this.grossMarginPct = grossMarginPct;
    }

    public double getOperatingMarginPct() {
        return operatingMarginPct;
    }

    public void setOperatingMarginPct(double operatingMarginPct) {
        this.operatingMarginPct = operatingMarginPct;
    }

    public double getReturnOnAssetsPct() {
        return returnOnAssetsPct;
    }

    public void setReturnOnAssetsPct(double returnOnAssetsPct) {
        this.returnOnAssetsPct = returnOnAssetsPct;
    }

    public double getReturnOnEquityPct() {
        return returnOnEquityPct;
    }

    public void setReturnOnEquityPct(double returnOnEquityPct) {
        this.returnOnEquityPct = returnOnEquityPct;
    }

    public double getBookValue() {
        return bookValue;
    }

    public void setBookValue(double bookValue) {
        this.bookValue = bookValue;
    }

    public double getDebtRatioPct() {
        return debtRatioPct;
    }

    public void setDebtRatioPct(double debtRatioPct) {
        this.debtRatioPct = debtRatioPct;
    }

    public double getCurrentRatio() {
        return currentRatio;
    }

    public void setCurrentRatio(double currentRatio) {
        this.currentRatio = currentRatio;
    }

    public double getNonOperatingRatioPct() {
        return nonOperatingRatioPct;
    }

    public void setNonOperatingRatioPct(double nonOperatingRatioPct) {
        this.nonOperatingRatioPct = nonOperatingRatioPct;
    }

    public double getLiquidityScore() {
        return liquidityScore;
    }

    public void setLiquidityScore(double liquidityScore) {
        this.liquidityScore = liquidityScore;
    }

    public double getEventRiskPenalty() {
        return eventRiskPenalty;
    }

    public void setEventRiskPenalty(double eventRiskPenalty) {
        this.eventRiskPenalty = eventRiskPenalty;
    }

    public double getRsi14() {
        return rsi14;
    }

    public void setRsi14(double rsi14) {
        this.rsi14 = rsi14;
    }

    public double getStochasticK() {
        return stochasticK;
    }

    public void setStochasticK(double stochasticK) {
        this.stochasticK = stochasticK;
    }

    public double getStochasticD() {
        return stochasticD;
    }

    public void setStochasticD(double stochasticD) {
        this.stochasticD = stochasticD;
    }

    public double getMa20Slope() {
        return ma20Slope;
    }

    public void setMa20Slope(double ma20Slope) {
        this.ma20Slope = ma20Slope;
    }

    public double getEpsAccelerationPct() {
        return epsAccelerationPct;
    }

    public void setEpsAccelerationPct(double epsAccelerationPct) {
        this.epsAccelerationPct = epsAccelerationPct;
    }

    public double getPeg() {
        return peg;
    }

    public void setPeg(double peg) {
        this.peg = peg;
    }

    public double getRevenueScore() {
        return revenueScore;
    }

    public void setRevenueScore(double revenueScore) {
        this.revenueScore = revenueScore;
    }

    public double getChipsScore() {
        return chipsScore;
    }

    public void setChipsScore(double chipsScore) {
        this.chipsScore = chipsScore;
    }

    public double getValuationScore() {
        return valuationScore;
    }

    public void setValuationScore(double valuationScore) {
        this.valuationScore = valuationScore;
    }

    public double getTechnicalScore() {
        return technicalScore;
    }

    public void setTechnicalScore(double technicalScore) {
        this.technicalScore = technicalScore;
    }

    public double getFinancialQualityScore() {
        return financialQualityScore;
    }

    public void setFinancialQualityScore(double financialQualityScore) {
        this.financialQualityScore = financialQualityScore;
    }

    public double getValuationIndustryPercentile() {
        return valuationIndustryPercentile;
    }

    public void setValuationIndustryPercentile(double valuationIndustryPercentile) {
        this.valuationIndustryPercentile = valuationIndustryPercentile;
    }

    public double getFinancialQualityIndustryPercentile() {
        return financialQualityIndustryPercentile;
    }

    public void setFinancialQualityIndustryPercentile(double financialQualityIndustryPercentile) {
        this.financialQualityIndustryPercentile = financialQualityIndustryPercentile;
    }

    public double getGrossMarginIndustryPercentile() {
        return grossMarginIndustryPercentile;
    }

    public void setGrossMarginIndustryPercentile(double grossMarginIndustryPercentile) {
        this.grossMarginIndustryPercentile = grossMarginIndustryPercentile;
    }

    public double getOperatingMarginIndustryPercentile() {
        return operatingMarginIndustryPercentile;
    }

    public void setOperatingMarginIndustryPercentile(double operatingMarginIndustryPercentile) {
        this.operatingMarginIndustryPercentile = operatingMarginIndustryPercentile;
    }

    public double getRoaIndustryPercentile() {
        return roaIndustryPercentile;
    }

    public void setRoaIndustryPercentile(double roaIndustryPercentile) {
        this.roaIndustryPercentile = roaIndustryPercentile;
    }

    public double getRoeIndustryPercentile() {
        return roeIndustryPercentile;
    }

    public void setRoeIndustryPercentile(double roeIndustryPercentile) {
        this.roeIndustryPercentile = roeIndustryPercentile;
    }

    public double getPegIndustryPercentile() {
        return pegIndustryPercentile;
    }

    public void setPegIndustryPercentile(double pegIndustryPercentile) {
        this.pegIndustryPercentile = pegIndustryPercentile;
    }

    public double getRelativePeIndustryPercentile() {
        return relativePeIndustryPercentile;
    }

    public void setRelativePeIndustryPercentile(double relativePeIndustryPercentile) {
        this.relativePeIndustryPercentile = relativePeIndustryPercentile;
    }

    public double getNonOperatingIndustryPercentile() {
        return nonOperatingIndustryPercentile;
    }

    public void setNonOperatingIndustryPercentile(double nonOperatingIndustryPercentile) {
        this.nonOperatingIndustryPercentile = nonOperatingIndustryPercentile;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getRawScore() {
        return rawScore;
    }

    public void setRawScore(double rawScore) {
        this.rawScore = rawScore;
    }

    public double getSelectionScore() {
        return selectionScore;
    }

    public void setSelectionScore(double selectionScore) {
        this.selectionScore = selectionScore;
    }

    public double getMomentumScore() {
        return momentumScore;
    }

    public void setMomentumScore(double momentumScore) {
        this.momentumScore = momentumScore;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public double getSectorScore() {
        return sectorScore;
    }

    public void setSectorScore(double sectorScore) {
        this.sectorScore = sectorScore;
    }

    public double getThemeScore() {
        return themeScore;
    }

    public void setThemeScore(double themeScore) {
        this.themeScore = themeScore;
    }

    public double getTrendPersistenceScore() {
        return trendPersistenceScore;
    }

    public void setTrendPersistenceScore(double trendPersistenceScore) {
        this.trendPersistenceScore = trendPersistenceScore;
    }

    public int getTrendPersistenceDays() {
        return trendPersistenceDays;
    }

    public void setTrendPersistenceDays(int trendPersistenceDays) {
        this.trendPersistenceDays = trendPersistenceDays;
    }

    public double getNewsScore() {
        return newsScore;
    }

    public void setNewsScore(double newsScore) {
        this.newsScore = newsScore;
    }

    public double getNewsRiskScore() {
        return newsRiskScore;
    }

    public void setNewsRiskScore(double newsRiskScore) {
        this.newsRiskScore = newsRiskScore;
    }

    public double getRelativeStrengthScore() {
        return relativeStrengthScore;
    }

    public void setRelativeStrengthScore(double relativeStrengthScore) {
        this.relativeStrengthScore = relativeStrengthScore;
    }

    public double getIndustryReturnStrength() {
        return industryReturnStrength;
    }

    public void setIndustryReturnStrength(double industryReturnStrength) {
        this.industryReturnStrength = industryReturnStrength;
    }

    public double getIndustryVolumeStrength() {
        return industryVolumeStrength;
    }

    public void setIndustryVolumeStrength(double industryVolumeStrength) {
        this.industryVolumeStrength = industryVolumeStrength;
    }

    public double getIndustryFlowStrength() {
        return industryFlowStrength;
    }

    public void setIndustryFlowStrength(double industryFlowStrength) {
        this.industryFlowStrength = industryFlowStrength;
    }

    public String getEventDirection() {
        return eventDirection;
    }

    public void setEventDirection(String eventDirection) {
        this.eventDirection = eventDirection;
    }

    public double getEventConfidence() {
        return eventConfidence;
    }

    public void setEventConfidence(double eventConfidence) {
        this.eventConfidence = eventConfidence;
    }

    public int getEventFreshnessDays() {
        return eventFreshnessDays;
    }

    public void setEventFreshnessDays(int eventFreshnessDays) {
        this.eventFreshnessDays = eventFreshnessDays;
    }

    public String getEventTypeSummary() {
        return eventTypeSummary;
    }

    public void setEventTypeSummary(String eventTypeSummary) {
        this.eventTypeSummary = eventTypeSummary;
    }

    public double getStructureScore() {
        return structureScore;
    }

    public void setStructureScore(double structureScore) {
        this.structureScore = structureScore;
    }

    public String getStructureLabel() {
        return structureLabel;
    }

    public void setStructureLabel(String structureLabel) {
        this.structureLabel = structureLabel;
    }

    public double getRiskRewardScore() {
        return riskRewardScore;
    }

    public void setRiskRewardScore(double riskRewardScore) {
        this.riskRewardScore = riskRewardScore;
    }

    public double getRiskRewardRatio() {
        return riskRewardRatio;
    }

    public void setRiskRewardRatio(double riskRewardRatio) {
        this.riskRewardRatio = riskRewardRatio;
    }

    public double getTurnaroundScore() {
        return turnaroundScore;
    }

    public void setTurnaroundScore(double turnaroundScore) {
        this.turnaroundScore = turnaroundScore;
    }

    public double getRevenueGrowthSignalScore() {
        return revenueGrowthSignalScore;
    }

    public void setRevenueGrowthSignalScore(double revenueGrowthSignalScore) {
        this.revenueGrowthSignalScore = revenueGrowthSignalScore;
    }

    public double getEarningsTurnaroundSignalScore() {
        return earningsTurnaroundSignalScore;
    }

    public void setEarningsTurnaroundSignalScore(double earningsTurnaroundSignalScore) {
        this.earningsTurnaroundSignalScore = earningsTurnaroundSignalScore;
    }

    public double getProfitabilityTurnaroundSignalScore() {
        return profitabilityTurnaroundSignalScore;
    }

    public void setProfitabilityTurnaroundSignalScore(double profitabilityTurnaroundSignalScore) {
        this.profitabilityTurnaroundSignalScore = profitabilityTurnaroundSignalScore;
    }

    public double getOneOffRiskScore() {
        return oneOffRiskScore;
    }

    public void setOneOffRiskScore(double oneOffRiskScore) {
        this.oneOffRiskScore = oneOffRiskScore;
    }

    public double getSuggestedStopPrice() {
        return suggestedStopPrice;
    }

    public void setSuggestedStopPrice(double suggestedStopPrice) {
        this.suggestedStopPrice = suggestedStopPrice;
    }

    public double getSuggestedStopPct() {
        return suggestedStopPct;
    }

    public void setSuggestedStopPct(double suggestedStopPct) {
        this.suggestedStopPct = suggestedStopPct;
    }

    public double getSuggestedTrailingStopPrice() {
        return suggestedTrailingStopPrice;
    }

    public void setSuggestedTrailingStopPrice(double suggestedTrailingStopPrice) {
        this.suggestedTrailingStopPrice = suggestedTrailingStopPrice;
    }

    public double getSuggestedTargetPrice() {
        return suggestedTargetPrice;
    }

    public void setSuggestedTargetPrice(double suggestedTargetPrice) {
        this.suggestedTargetPrice = suggestedTargetPrice;
    }

    public double getFairValueLow() {
        return fairValueLow;
    }

    public void setFairValueLow(double fairValueLow) {
        this.fairValueLow = fairValueLow;
    }

    public double getFairValueBase() {
        return fairValueBase;
    }

    public void setFairValueBase(double fairValueBase) {
        this.fairValueBase = fairValueBase;
    }

    public double getFairValueHigh() {
        return fairValueHigh;
    }

    public void setFairValueHigh(double fairValueHigh) {
        this.fairValueHigh = fairValueHigh;
    }

    public double getFairValueConfidence() {
        return fairValueConfidence;
    }

    public void setFairValueConfidence(double fairValueConfidence) {
        this.fairValueConfidence = fairValueConfidence;
    }

    public double getUpsidePotentialPct() {
        return upsidePotentialPct;
    }

    public void setUpsidePotentialPct(double upsidePotentialPct) {
        this.upsidePotentialPct = upsidePotentialPct;
    }

    public double getSellSignalScore() {
        return sellSignalScore;
    }

    public void setSellSignalScore(double sellSignalScore) {
        this.sellSignalScore = sellSignalScore;
    }

    public String getSellSignalLabel() {
        return sellSignalLabel;
    }

    public void setSellSignalLabel(String sellSignalLabel) {
        this.sellSignalLabel = sellSignalLabel;
    }

    public boolean isReducePositionSize() {
        return reducePositionSize;
    }

    public void setReducePositionSize(boolean reducePositionSize) {
        this.reducePositionSize = reducePositionSize;
    }

    public double getBuyPointScore() {
        return buyPointScore;
    }

    public void setBuyPointScore(double buyPointScore) {
        this.buyPointScore = buyPointScore;
    }

    public double getDataConfidence() {
        return dataConfidence;
    }

    public void setDataConfidence(double dataConfidence) {
        this.dataConfidence = dataConfidence;
    }

    public boolean isSelectionQualified() {
        return selectionQualified;
    }

    public void setSelectionQualified(boolean selectionQualified) {
        this.selectionQualified = selectionQualified;
    }

    public String getAnalysisNote() {
        return analysisNote;
    }

    public void setAnalysisNote(String analysisNote) {
        this.analysisNote = analysisNote;
    }

    public String getCompanySummary() {
        return companySummary;
    }

    public void setCompanySummary(String companySummary) {
        this.companySummary = companySummary;
    }

    public String getRecentNewsBrief() {
        return recentNewsBrief;
    }

    public void setRecentNewsBrief(String recentNewsBrief) {
        this.recentNewsBrief = recentNewsBrief;
    }

    public String getTransformationHint() {
        return transformationHint;
    }

    public void setTransformationHint(String transformationHint) {
        this.transformationHint = transformationHint;
    }

    public String getPracticalAdvice() {
        return practicalAdvice;
    }

    public void setPracticalAdvice(String practicalAdvice) {
        this.practicalAdvice = practicalAdvice;
    }

    public double getAdviceConfidence() {
        return adviceConfidence;
    }

    public void setAdviceConfidence(double adviceConfidence) {
        this.adviceConfidence = adviceConfidence;
    }

    public String getFairValueMethod() {
        return fairValueMethod;
    }

    public void setFairValueMethod(String fairValueMethod) {
        this.fairValueMethod = fairValueMethod;
    }

    public String getFairValueReason() {
        return fairValueReason;
    }

    public void setFairValueReason(String fairValueReason) {
        this.fairValueReason = fairValueReason;
    }

    public String getMarketRegime() {
        return marketRegime;
    }

    public void setMarketRegime(String marketRegime) {
        this.marketRegime = marketRegime;
    }

    public String getSnapshotStage() {
        return snapshotStage;
    }

    public void setSnapshotStage(String snapshotStage) {
        this.snapshotStage = snapshotStage;
    }

    public boolean isTechReady() {
        return techReady;
    }

    public void setTechReady(boolean techReady) {
        this.techReady = techReady;
    }

    public boolean isMarketReady() {
        return marketReady;
    }

    public void setMarketReady(boolean marketReady) {
        this.marketReady = marketReady;
    }

    public boolean isInstitutionalReady() {
        return institutionalReady;
    }

    public void setInstitutionalReady(boolean institutionalReady) {
        this.institutionalReady = institutionalReady;
    }

    public boolean isBrokerReady() {
        return brokerReady;
    }

    public void setBrokerReady(boolean brokerReady) {
        this.brokerReady = brokerReady;
    }

    public boolean isFinancialReady() {
        return financialReady;
    }

    public void setFinancialReady(boolean financialReady) {
        this.financialReady = financialReady;
    }

    public boolean isNewsReady() {
        return newsReady;
    }

    public void setNewsReady(boolean newsReady) {
        this.newsReady = newsReady;
    }

    public String getAnalysisVersion() {
        return analysisVersion;
    }

    public void setAnalysisVersion(String analysisVersion) {
        this.analysisVersion = analysisVersion;
    }

    public String getSourceUpdatedAt() {
        return sourceUpdatedAt;
    }

    public void setSourceUpdatedAt(String sourceUpdatedAt) {
        this.sourceUpdatedAt = sourceUpdatedAt;
    }

    public String getPrimaryTheme() {
        return primaryTheme;
    }

    public void setPrimaryTheme(String primaryTheme) {
        this.primaryTheme = primaryTheme;
    }

    public String getThemeTags() {
        return themeTags;
    }

    public void setThemeTags(String themeTags) {
        this.themeTags = themeTags;
    }

    public String getLaunchTags() {
        return launchTags;
    }

    public void setLaunchTags(String launchTags) {
        this.launchTags = launchTags;
    }

    public String getNewsSummary() {
        return newsSummary;
    }

    public void setNewsSummary(String newsSummary) {
        this.newsSummary = newsSummary;
    }

    public String getNewsDigest() {
        return newsDigest;
    }

    public void setNewsDigest(String newsDigest) {
        this.newsDigest = newsDigest;
    }

    public String getNewsSourceSummary() {
        return newsSourceSummary;
    }

    public void setNewsSourceSummary(String newsSourceSummary) {
        this.newsSourceSummary = newsSourceSummary;
    }

    public String getLatestNewsPublishedHint() {
        return latestNewsPublishedHint;
    }

    public void setLatestNewsPublishedHint(String latestNewsPublishedHint) {
        this.latestNewsPublishedHint = latestNewsPublishedHint;
    }

    public double getNewsSourceCredibilityScore() {
        return newsSourceCredibilityScore;
    }

    public void setNewsSourceCredibilityScore(double newsSourceCredibilityScore) {
        this.newsSourceCredibilityScore = newsSourceCredibilityScore;
    }

    public double getNewsFreshnessScore() {
        return newsFreshnessScore;
    }

    public void setNewsFreshnessScore(double newsFreshnessScore) {
        this.newsFreshnessScore = newsFreshnessScore;
    }

    public int getNewsSourceCount() {
        return newsSourceCount;
    }

    public void setNewsSourceCount(int newsSourceCount) {
        this.newsSourceCount = newsSourceCount;
    }

    public int getNewsOfficialSourceCount() {
        return newsOfficialSourceCount;
    }

    public void setNewsOfficialSourceCount(int newsOfficialSourceCount) {
        this.newsOfficialSourceCount = newsOfficialSourceCount;
    }

    public int getNewsMediaSourceCount() {
        return newsMediaSourceCount;
    }

    public void setNewsMediaSourceCount(int newsMediaSourceCount) {
        this.newsMediaSourceCount = newsMediaSourceCount;
    }

    public double getThemeReferenceScore() {
        return themeReferenceScore;
    }

    public void setThemeReferenceScore(double themeReferenceScore) {
        this.themeReferenceScore = themeReferenceScore;
    }

    public String getThemeReferenceTheme() {
        return themeReferenceTheme;
    }

    public void setThemeReferenceTheme(String themeReferenceTheme) {
        this.themeReferenceTheme = themeReferenceTheme;
    }

    public String getThemeReferenceIndustry() {
        return themeReferenceIndustry;
    }

    public void setThemeReferenceIndustry(String themeReferenceIndustry) {
        this.themeReferenceIndustry = themeReferenceIndustry;
    }

    public String getThemeReferenceReason() {
        return themeReferenceReason;
    }

    public void setThemeReferenceReason(String themeReferenceReason) {
        this.themeReferenceReason = themeReferenceReason;
    }

    public double getMarketThemeReferenceScore() {
        return marketThemeReferenceScore;
    }

    public void setMarketThemeReferenceScore(double marketThemeReferenceScore) {
        this.marketThemeReferenceScore = marketThemeReferenceScore;
    }

    public String getMarketThemeReferenceTheme() {
        return marketThemeReferenceTheme;
    }

    public void setMarketThemeReferenceTheme(String marketThemeReferenceTheme) {
        this.marketThemeReferenceTheme = marketThemeReferenceTheme;
    }

    public String getMarketThemeReferenceReason() {
        return marketThemeReferenceReason;
    }

    public void setMarketThemeReferenceReason(String marketThemeReferenceReason) {
        this.marketThemeReferenceReason = marketThemeReferenceReason;
    }

    public String getScoreReason() {
        return scoreReason;
    }

    public void setScoreReason(String scoreReason) {
        this.scoreReason = scoreReason;
    }

    public String getRevenueReason() {
        return revenueReason;
    }

    public void setRevenueReason(String revenueReason) {
        this.revenueReason = revenueReason;
    }

    public String getChipsReason() {
        return chipsReason;
    }

    public void setChipsReason(String chipsReason) {
        this.chipsReason = chipsReason;
    }

    public String getValuationReason() {
        return valuationReason;
    }

    public void setValuationReason(String valuationReason) {
        this.valuationReason = valuationReason;
    }

    public String getTechnicalReason() {
        return technicalReason;
    }

    public void setTechnicalReason(String technicalReason) {
        this.technicalReason = technicalReason;
    }

    public String getFinancialQualityReason() {
        return financialQualityReason;
    }

    public void setFinancialQualityReason(String financialQualityReason) {
        this.financialQualityReason = financialQualityReason;
    }

    public String getLiquidityReason() {
        return liquidityReason;
    }

    public void setLiquidityReason(String liquidityReason) {
        this.liquidityReason = liquidityReason;
    }

    public String getEventRiskReason() {
        return eventRiskReason;
    }

    public void setEventRiskReason(String eventRiskReason) {
        this.eventRiskReason = eventRiskReason;
    }

    public String getEligibilityReason() {
        return eligibilityReason;
    }

    public void setEligibilityReason(String eligibilityReason) {
        this.eligibilityReason = eligibilityReason;
    }

    public String getBuyPointLabel() {
        return buyPointLabel;
    }

    public void setBuyPointLabel(String buyPointLabel) {
        this.buyPointLabel = buyPointLabel;
    }

    public String getBuyPointReason() {
        return buyPointReason;
    }

    public void setBuyPointReason(String buyPointReason) {
        this.buyPointReason = buyPointReason;
    }

    public String getDataConfidenceReason() {
        return dataConfidenceReason;
    }

    public void setDataConfidenceReason(String dataConfidenceReason) {
        this.dataConfidenceReason = dataConfidenceReason;
    }

    public String getTurnaroundLabel() {
        return turnaroundLabel;
    }

    public void setTurnaroundLabel(String turnaroundLabel) {
        this.turnaroundLabel = turnaroundLabel;
    }

    public String getTurnaroundReason() {
        return turnaroundReason;
    }

    public void setTurnaroundReason(String turnaroundReason) {
        this.turnaroundReason = turnaroundReason;
    }

    public String getSignalType() {
        return signalType;
    }

    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }

    public int getSignalHorizonDays() {
        return signalHorizonDays;
    }

    public void setSignalHorizonDays(int signalHorizonDays) {
        this.signalHorizonDays = signalHorizonDays;
    }

    public String getEntryRule() {
        return entryRule;
    }

    public void setEntryRule(String entryRule) {
        this.entryRule = entryRule;
    }

    public String getExitRule() {
        return exitRule;
    }

    public void setExitRule(String exitRule) {
        this.exitRule = exitRule;
    }

    public String getValidationMode() {
        return validationMode;
    }

    public void setValidationMode(String validationMode) {
        this.validationMode = validationMode;
    }

    public boolean isHardExclude() {
        return hardExclude;
    }

    public void setHardExclude(boolean hardExclude) {
        this.hardExclude = hardExclude;
    }

    public String getHardExcludeReason() {
        return hardExcludeReason;
    }

    public void setHardExcludeReason(String hardExcludeReason) {
        this.hardExcludeReason = hardExcludeReason;
    }

    public String getDataQualityGrade() {
        return dataQualityGrade;
    }

    public void setDataQualityGrade(String dataQualityGrade) {
        this.dataQualityGrade = dataQualityGrade;
    }

    public int getCoreConditionCount() {
        return coreConditionCount;
    }

    public void setCoreConditionCount(int coreConditionCount) {
        this.coreConditionCount = coreConditionCount;
    }

    public double getWinratePriorityScore() {
        return winratePriorityScore;
    }

    public void setWinratePriorityScore(double winratePriorityScore) {
        this.winratePriorityScore = winratePriorityScore;
    }

    public double getExpectedReturnScore() {
        return expectedReturnScore;
    }

    public void setExpectedReturnScore(double expectedReturnScore) {
        this.expectedReturnScore = expectedReturnScore;
    }

    public double getMaxDrawdownPenalty() {
        return maxDrawdownPenalty;
    }

    public void setMaxDrawdownPenalty(double maxDrawdownPenalty) {
        this.maxDrawdownPenalty = maxDrawdownPenalty;
    }

    public String getBacktestCohort() {
        return backtestCohort;
    }

    public void setBacktestCohort(String backtestCohort) {
        this.backtestCohort = backtestCohort;
    }

    public double getPostClosePriorityScore() {
        return postClosePriorityScore;
    }

    public void setPostClosePriorityScore(double postClosePriorityScore) {
        this.postClosePriorityScore = postClosePriorityScore;
    }

    public String getPostCloseCategory() {
        return postCloseCategory;
    }

    public void setPostCloseCategory(String postCloseCategory) {
        this.postCloseCategory = postCloseCategory;
    }

    public String getPostCloseAction() {
        return postCloseAction;
    }

    public void setPostCloseAction(String postCloseAction) {
        this.postCloseAction = postCloseAction;
    }

    public String getPostCloseReason() {
        return postCloseReason;
    }

    public void setPostCloseReason(String postCloseReason) {
        this.postCloseReason = postCloseReason;
    }
}
