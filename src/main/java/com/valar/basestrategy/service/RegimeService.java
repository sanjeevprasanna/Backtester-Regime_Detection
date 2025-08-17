package com.valar.basestrategy.service;

import com.valar.basestrategy.entities.indicators.RegimeDetector;
import com.valar.basestrategy.entities.indicators.RegimeDetector.Regime;
import com.valar.basestrategy.state.minute.IndexState;
import com.valar.basestrategy.utils.DayIterator;
import com.valar.basestrategy.utils.DayIterator.Row;
import com.valar.basestrategy.utils.RegimeWriter;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;

import static com.valar.basestrategy.application.PropertiesReader.properties;

public class RegimeService {

    private final DayIterator bnIter;     // BankNifty (main)
    private final DayIterator nfIter;     // NIFTY (benchmark)
    private final RegimeDetector detector;

    // Windows
    private final Deque<Double> priceQ;   //to calc T
    private final Deque<Double> retQ;     // to calc V
    private final Deque<Double> corrRetQ; // to calcC
    private final Deque<Double> benchRetQ;// to calc C

    private Double prevCloseBN = null;
    private Double prevCloseNF = null;

    private final boolean debug = Boolean.parseBoolean(properties.getProperty("regimeDebug", "false"));

    public RegimeService(DayIterator bnIter, DayIterator nfIter, RegimeDetector detector) {
        this.bnIter = bnIter;
        this.nfIter = nfIter;
        this.detector = detector;
        int n = detector.windowN();
        this.priceQ   = new ArrayDeque<>(n);
        this.retQ     = new ArrayDeque<>(n);
        this.corrRetQ = new ArrayDeque<>(n);
        this.benchRetQ= new ArrayDeque<>(n);
    }

    // Computes regime at the start of each matched day using "previous N days".

    public void writeAllHistory(RegimeWriter out) {
        LocalDate dBN = bnIter.peekNextDate();
        LocalDate dNF = nfIter.peekNextDate();

        while (dBN != null && dNF != null) {
            int cmp = dBN.compareTo(dNF);

            if (cmp < 0) { // BN earlier → advance BF cursor
                Row rBN = bnIter.poll();
                pushPrice(rBN.close);
                if (prevCloseBN != null) pushRet(RegimeDetector.dailyReturn(prevCloseBN, rBN.close));
                prevCloseBN = rBN.close;
                dBN = bnIter.peekNextDate();

            } else if (cmp > 0) { // NF earlier → advance NF cursor
                Row rNF = nfIter.poll();
                prevCloseNF = rNF.close;
                dNF = nfIter.peekNextDate();

            } else { // matched date
                Row rBN = bnIter.poll();
                Row rNF = nfIter.poll();

                // compute from previous N days (today NOT yet in window)
                int n = detector.windowN();
                Regime regime = (priceQ.size() >= n && retQ.size() >= n)
                        ? detector.compute(priceQ, retQ, corrRetQ, benchRetQ)
                        : null;

                try {
                    if (regime == null) {
                        out.append(rBN.date.toString(), -1, "Null", -1, -1, -1, -1);
                        if (debug) System.out.println("[Regime SOD] " + rBN.date + " -> Null");
                    } else {
                        int code = regime.code;
                        int V =  code % 2;
                        int T = (code / 2) % 2;
                        int C = (code / 4) % 2;
                        out.append(rBN.date.toString(), code, regime.label, C, T, V, code);
                        if (debug) System.out.println("[Regime SOD] " + rBN.date + " -> code=" + code + " C="+C+" T="+T+" V="+V);
                    }
                } catch (Exception ignore) {}

                // add today's data for tomorrow's regime calc
                pushPrice(rBN.close);
                Double rrBN = (prevCloseBN == null) ? null : RegimeDetector.dailyReturn(prevCloseBN, rBN.close);
                if (rrBN != null) pushRet(rrBN);
                prevCloseBN = rBN.close;

                Double rrNF = (prevCloseNF == null) ? null : RegimeDetector.dailyReturn(prevCloseNF, rNF.close);
                prevCloseNF = rNF.close;
                if (rrBN != null && rrNF != null) { pushCorrBN(rrBN); pushBenchNF(rrNF); }

                dBN = bnIter.peekNextDate();
                dNF = nfIter.peekNextDate();
            }
        }
        if (debug) System.out.println("[Regime] writeAllHistory finished.");
    }
    private void pushPrice(double v) {
        if (priceQ.size()==detector.windowN()) priceQ.removeFirst();
        priceQ.addLast(v);
    }
    private void pushRet(double v) {
        if (retQ.size()==detector.windowN()) retQ.removeFirst();
        retQ.addLast(v);
    }
    private void pushCorrBN(double v){
        if (corrRetQ.size()==detector.windowN()) corrRetQ.removeFirst();
        corrRetQ.addLast(v);
    }
    private void pushBenchNF(double v){
        if (benchRetQ.size()==detector.windowN()) benchRetQ.removeFirst();
        benchRetQ.addLast(v);
    }
}