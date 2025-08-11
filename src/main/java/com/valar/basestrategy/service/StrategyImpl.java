package com.valar.basestrategy.service;

import com.valar.basestrategy.entities.Ohlc;
import com.valar.basestrategy.entities.TradeEntity;
import com.valar.basestrategy.state.minute.IndexState;
import com.valar.basestrategy.state.minute.State;
import com.valar.basestrategy.tradeAndDayMetrics.DayMetric;
import com.valar.basestrategy.utils.DayIterator;
import com.valar.basestrategy.utils.KeyValues;
import com.valar.basestrategy.utils.RegimeWriter;
import org.ta4j.core.Bar;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.lang.Math.max;

public class StrategyImpl {
    private int tradeId;
    private float dayMaxProfit, dayMaxProfitPercent;
    public KeyValues kv;
    public boolean dayExited;
    private int unSquaredTrades;
    private final List<TradeEntity> tradeEntities = new ArrayList<>();
    private final State indexState;
    private final List<Map<String, DayMetric>> dayMetricsMapList;
    private double dayAtrPercent, dayAtrPercentage;
    private boolean dayATRConditionSatisfied, candlePeriodBelongsToDay;
    private final Map<Integer, IndexState> indexStateMap;
    private final Map<String, Double> dayAtrMap, dayAtrMapPercentage;
    private int parserAtLastTrade;
    private String lastAtrCheckeAtDate = "";
    private String prevDate = null;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm");
    private static final DateTimeFormatter DAY_FMT_MIN = DateTimeFormatter.ofPattern("dd-MM-yy");

    // regime
    private final RegimeService regimeSvc;
    private final RegimeWriter regimeWriter;

    public StrategyImpl(
            boolean candlePeriodBelongsToDay,
            Map<Integer, IndexState> indexStateMap,
            KeyValues kv,
            Map<String, Double> dayAtrMap,
            Map<String, Double> dayAtrMapPercentage,
            State indexState,
            Map<String, DayMetric> dayMetricsMap,
            Map<String, DayMetric> stockDayMetricsMap
    ) {
        this.indexStateMap = indexStateMap;
        this.kv = kv;
        this.indexState = indexState;
        this.dayAtrMap = dayAtrMap;
        this.dayAtrMapPercentage = dayAtrMapPercentage;
        this.dayMetricsMapList = new ArrayList<>(Arrays.asList(dayMetricsMap, stockDayMetricsMap));
        this.candlePeriodBelongsToDay = candlePeriodBelongsToDay;

        try {
            Path outCsv = Paths.get("Outputs/RegimeByDay.csv");
            DayIterator dayIter = new DayIterator();
            this.regimeSvc = new RegimeService(dayIter, new com.valar.basestrategy.entities.indicators.RegimeDetector());
            this.regimeWriter = new RegimeWriter(outCsv.toString());

            // Pre-warm the 10-day window using the very first minute's date
            LocalDate firstMinuteDate = LocalDate.parse(indexState.ohlc.date, DAY_FMT_MIN);
            regimeSvc.preWarm(firstMinuteDate);
        } catch (Exception e) {
            throw new RuntimeException("Regime init failed", e);
        }
    }

    public void setUnSquaredTrades(int unSquaredTrades) { this.unSquaredTrades = unSquaredTrades; }

    public void iterate(int mins) {
        String currDate = indexState.ohlc.date;

        // compute regime exactly once per new day
        if (prevDate == null || !prevDate.equals(currDate)) {
            try {
                regimeSvc.onMinute(LocalDate.parse(currDate, DAY_FMT_MIN), (IndexState) indexState, regimeWriter);
            } catch (Exception e) {
                throw new RuntimeException("Regime onMinute failed for " + currDate, e);
            }
        }

        // pivots on day change
        if (prevDate != null && !prevDate.equals(currDate)) {
            float high = indexState.ohlc.prevDayHigh;
            float low = indexState.ohlc.prevDayLow;
            float close = indexState.ohlc.lastDayClose;
            ((IndexState)indexState).computePivots(high, low, close);
            indexState.pivotsInitialized = true;
        }
        prevDate = currDate;

        if ((mins >= kv.startTime || candlePeriodBelongsToDay)
                && !lastAtrCheckeAtDate.equals(indexState.ohlc.date)) {
            lastAtrCheckeAtDate = indexState.ohlc.date;
            if (!dayAtrMap.containsKey(indexState.ohlc.date)) {
                if (!kv.positional) dayExited = true;
                return;
            }
            dayAtrPercent = dayAtrMap.get(indexState.ohlc.date);
            dayAtrPercentage = dayAtrMapPercentage.get(indexState.ohlc.date);
            dayATRConditionSatisfied = dayAtrPercent >= 0
                    && (dayAtrPercent >= kv.atrFrom && dayAtrPercent <= kv.atrTo);
            if (!dayATRConditionSatisfied && !kv.positional) { dayExited = true; return; }
        }

        if (mins >= kv.startTime || candlePeriodBelongsToDay) {
            checkForExitsInEnteredTrades();

            boolean entryOk = indexState.ohlc.mins >= kv.startTime
                    && indexState.ohlc.mins <= kv.cutOffTime
                    && (kv.maxOverlap == 0 || unSquaredTrades < kv.maxOverlap)
                    && indexState.parser - parserAtLastTrade >= kv.tradeGap;

            if (dayATRConditionSatisfied && entryOk && indexState.pivotsInitialized) {
                if (mins >= kv.startTime && kv.rsiPeriod != 0 && kv.positional) {
                    runOptionalLogic(entryOk);
                }
            }
        }
    }

    private void runOptionalLogic(boolean entryConditionSatisfied) {
        Ohlc bar = indexState.ohlc;
        int curIdx = indexState.parser;
        indexState.loadIndicators(kv.emaPeriod, kv.rsiPeriod);

        double emaVal = indexState.getEmaVal(kv.emaPeriod);
        double rsiVal = indexState.getRsiVal(kv.rsiPeriod);

        float high10 = Float.NEGATIVE_INFINITY, low10 = Float.POSITIVE_INFINITY;
        for (int i = Math.max(0, curIdx - 10); i < curIdx; ++i) {
            Bar o = indexState.series.getBar(i);
            high10 = Math.max(high10, o.getHighPrice().floatValue());
            low10 = Math.min(low10, o.getLowPrice().floatValue());
        }

        if (kv.usePivots) {
            if (bar.high>bar.prevDayHigh && rsiVal > kv.rsiLong && bar.close > emaVal  && kv.tradeType.equals("l")) {
                TradeEntity t = new TradeEntity(tradeId, 0, 0, kv, (IndexState) indexState, indexStateMap);
                t.setTrade(emaVal,rsiVal,(indexState.pivotsInitialized ? ((float) ((IndexState)indexState).pp) : Float.NaN),
                        bar.prevDayHigh,bar.prevDayLow, bar.high,bar.low,high10,low10);
                t.setStopLoss(((IndexState)indexState).nearestBelow(bar.close,low10));
                t.setTarget(((IndexState)indexState).nearestAbove(bar.close, high10));
                if (kv.isUseRegime() && hasSetRegimeSetter(t))
                    t.setRegime(((IndexState)indexState).getTodayRegime()==null ? "Null" : ((IndexState)indexState).getTodayRegime().name());
                tradeEntities.add(t);
                tradeId++;
                parserAtLastTrade = indexState.parser;
            }
            if (bar.low<bar.prevDayLow && rsiVal < kv.rsiShort && bar.close < emaVal &&  kv.tradeType.equals("s")) {
                TradeEntity t = new TradeEntity(tradeId, 0, 0, kv, (IndexState) indexState, indexStateMap);
                t.setTrade(emaVal,rsiVal,(indexState.pivotsInitialized ? ((float) ((IndexState)indexState).pp) : Float.NaN),
                        bar.prevDayHigh,bar.prevDayLow, bar.high,bar.low,high10,low10);
                t.setStopLoss(((IndexState)indexState).nearestAbove(bar.close, high10));
                t.setTarget(((IndexState)indexState).nearestBelow(bar.close,low10));
                if (kv.isUseRegime() && hasSetRegimeSetter(t))
                    t.setRegime(((IndexState)indexState).getTodayRegime()==null ? "Null" : ((IndexState)indexState).getTodayRegime().name());
                tradeEntities.add(t);
                tradeId++;
                parserAtLastTrade = indexState.parser;
            }
        }
    }

    public void checkForExitsInEnteredTrades() {
        Ohlc bar = indexState.ohlc;
        LocalDateTime barDateTime = LocalDateTime.parse(bar.date + " " + bar.time, DTF);

        float totalProfitPercent = 0, totalProfit = 0;
        unSquaredTrades = 0;

        for (TradeEntity tradeEntity : tradeEntities) {
            if (tradeEntity.tradeSquared) continue;

            char lOrS = tradeEntity.tradeAttribs.get(0).lOrS;
            boolean forceExit = (barDateTime.getDayOfWeek() == DayOfWeek.THURSDAY && bar.mins >= (15 * 60 + 15));
            boolean hitSL = false, hitTarget = false;

            if (lOrS == 'l') {
                hitSL = (bar.low <= tradeEntity.stopLoss);
                hitTarget = (bar.high >= tradeEntity.target);
            } else if (lOrS == 's') {
                hitSL = (bar.high >= tradeEntity.stopLoss);
                hitTarget = (bar.low <= tradeEntity.target);
            }

            if (hitSL) { tradeEntity.exit("StopLoss", "SL-hit"); onTradeExit(bar.date, tradeEntity); }
            else if (hitTarget) { tradeEntity.exit("Target", "TP-hit"); onTradeExit(bar.date, tradeEntity); }
            else if (forceExit) { tradeEntity.exit("ForceExit", "TimeExit"); onTradeExit(bar.date, tradeEntity); }

            if (!tradeEntity.tradeSquared) unSquaredTrades++;
            totalProfitPercent += tradeEntity.getTotalProfitPercent();
            totalProfit += tradeEntity.getTotalProfit();
        }
        dayMaxProfit = Math.max(dayMaxProfit, totalProfit);
        dayMaxProfitPercent = Math.max(dayMaxProfitPercent, totalProfitPercent);
    }

    public void onTradeExit(String date, TradeEntity tradeEntity) {
        for (Map<String, DayMetric> dayMetricsMap : dayMetricsMapList) {
            DayMetric dm = dayMetricsMap.get(date);
            if (dm == null) {
                dm = new DayMetric(date, kv.costPercent, tradeEntity.indexCloseAtEntry, kv.sno);
                dayMetricsMap.put(date, dm);
            }
            dm.updateMetric(tradeEntity.overAllTradeMetric, dayMaxProfit, dayMaxProfitPercent);
        }
    }

    private static boolean hasSetRegimeSetter(TradeEntity t) {
        try { t.getClass().getMethod("setRegime", String.class); return true; }
        catch (NoSuchMethodException e) { return false; }
    }
}