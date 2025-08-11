package com.valar.basestrategy.service;

import com.valar.basestrategy.entities.indicators.RegimeDetector;
import com.valar.basestrategy.entities.indicators.RegimeDetector.Regime;
import com.valar.basestrategy.state.minute.IndexState;
import com.valar.basestrategy.utils.DayIterator;
import com.valar.basestrategy.utils.RegimeWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class RegimeService {

    private final DayIterator dayIter;
    private final RegimeDetector detector;
    private final Deque<Double> atrQ = new ArrayDeque<>(10);
    private final Deque<Double> adxQ = new ArrayDeque<>(10);
    private final Deque<Double> retQ = new ArrayDeque<>(10);
    private final Deque<Double> bRetQ= new ArrayDeque<>(10);
    private Double prevClose = null;
    private LocalDate currentDay = null;

    private final Path debugPath;
    private final BufferedWriter dbg;

    public RegimeService(DayIterator iter, RegimeDetector detector) throws IOException {
        this.dayIter = iter; this.detector = detector;
        this.debugPath = Paths.get("Outputs/RegimeDebug.log");
        Files.createDirectories(debugPath.getParent());
        this.dbg = Files.newBufferedWriter(debugPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        dbg.write("[INIT] " + dayIter.info()); dbg.newLine(); dbg.flush();
    }

    /** Call once at startup with the first minute date to pre-fill the 10-day window. */
    public void preWarm(LocalDate firstMinuteDate) {
        ensureWindowReady(firstMinuteDate);
        try {
            dbg.write(String.format("[PREWARM] minute=%s atrQ=%d adxQ=%d retQ=%d%n",
                    firstMinuteDate, atrQ.size(), adxQ.size(), retQ.size()));
            dbg.flush();
        } catch (IOException ignore) {}
    }

    private void ensureWindowReady(LocalDate minuteDate) {
        List<DayIterator.Row> prior = dayIter.advanceAllToPreviousOf(minuteDate);
        // If we didn't get enough history (e.g., iterator was at the end), rewind and try again
        if (prior.isEmpty() && atrQ.size() < 10) {
            dayIter.rewind();
            prior = dayIter.advanceAllToPreviousOf(minuteDate);
        }
        for (DayIterator.Row r : prior) {
            // If ATR looks like points (tiny), convert to %
            double atr = r.atr14;
            if (atr > 0 && atr < 5 && r.close > 0) atr = (atr / r.close) * 100.0;
            // If ADX is on 0..1 scale, convert to 0..100
            double adx = (r.adx14 <= 3.0 ? r.adx14 * 100.0 : r.adx14);

            push(atrQ, atr);
            push(adxQ, adx);
            if (prevClose != null) {
                double rr = (r.close - prevClose) / (prevClose == 0.0 ? 1.0 : prevClose);
                push(retQ, rr);
            }
            prevClose = r.close;
        }
        try {
            dbg.write(String.format("[FILL] %s prior=%d atrQ=%d adxQ=%d retQ=%d lastATR=%.6f lastADX=%.6f%n",
                    minuteDate, prior.size(), atrQ.size(), adxQ.size(), retQ.size(),
                    atrQ.isEmpty()?Double.NaN:atrQ.getLast(),
                    adxQ.isEmpty()?Double.NaN:adxQ.getLast()));
            dbg.flush();
        } catch (IOException ignore) {}
    }

    /** Call once at the first minute of each trading day. */
    public void onMinute(LocalDate minuteDate, IndexState state, RegimeWriter out) throws IOException {
        if (currentDay != null && minuteDate.equals(currentDay)) return;

        ensureWindowReady(minuteDate);

        Regime regime = null;
        if (detector.warmupReady(atrQ, adxQ)) {
            regime = detector.compute(atrQ, adxQ, retQ, bRetQ);
        }

        try {
            dbg.write(String.format("[DAY] %s regime=%s%n", minuteDate, regime==null?"Null":regime.name()));
            dbg.flush();
        } catch (IOException ignore) {}

        state.setTodayRegime(regime);
        out.append(minuteDate.toString(), regime==null ? -1 : regime.code, regime==null ? "Null" : regime.label);
        currentDay = minuteDate;
    }

    private static void push(Deque<Double> q, double v){ if (q.size()==10) q.removeFirst(); q.addLast(v); }
}