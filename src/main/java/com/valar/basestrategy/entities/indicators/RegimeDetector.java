package com.valar.basestrategy.entities.indicators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public class RegimeDetector {

    public static enum Regime {
        CALM_SIDEWAYS_UNCORR(0,"Calm, Sideways, Uncorrelated"),
        VOL_SIDEWAYS_UNCORR (1,"Volatile, Sideways, Uncorrelated"),
        CALM_TREND_UNCORR   (2,"Calm Trend, Uncorrelated"),
        VOL_TREND_UNCORR    (3,"Volatile Trend, Uncorrelated"),
        CALM_SIDEWAYS_CORR  (4,"Calm, Sideways, Correlated"),
        VOL_SIDEWAYS_CORR   (5,"Volatile, Sideways, Correlated"),
        CALM_TREND_CORR     (6,"Calm Trend, Correlated"),
        VOL_TREND_CORR      (7,"Volatile Trend, Correlated");
        public final int code; public final String label;
        Regime(int c, String l){ code=c; label=l; }
        public static Regime from(int code){ return values()[code]; }
    }

    private final double atrMult, adxThr, corrThr;

    //defaults: atrMult=1.30, adxThr=25, corrThr=0.50
    public RegimeDetector(){ this(1.30, 25.0, 0.50); }

    public RegimeDetector(double atrMult, double adxThr, double corrThr) {
        this.atrMult=atrMult; this.adxThr=adxThr; this.corrThr=corrThr;
    }

    public boolean warmupReady(Deque<Double> atrQ, Deque<Double> adxQ){
        return atrQ.size() == 10 && adxQ.size() == 10;
    }

    public Regime compute(Deque<Double> atrQ, Deque<Double> adxQ,
                          Deque<Double> retQ, Deque<Double> bRetQ) {
        double atrToday = atrQ.getLast();
        double medianAtr = median(atrQ);
        int V = atrToday > atrMult * medianAtr ? 1 : 0;
        int T = adxQ.getLast() > adxThr ? 1 : 0;

        int C = 0;
        if (retQ.size()==10 && bRetQ.size()==10) {
            Double rho = pearson(retQ, bRetQ);
            if (rho != null && Math.abs(rho) > corrThr) C = 1;
        }
        return Regime.from(4*C + 2*T + V);
    }

    private static double median(Deque<Double> q){
        List<Double> tmp = new ArrayList<Double>(q);
        Collections.sort(tmp);
        int n = tmp.size();
        if (n==0) return 0.0;
        if ((n & 1) == 1) return tmp.get(n/2);
        return 0.5*(tmp.get(n/2-1)+tmp.get(n/2));
    }

    private static Double pearson(Deque<Double> a, Deque<Double> b){
        int n=a.size(); if (n==0 || n!=b.size()) return null;
        double sumA=0,sumB=0; for (double v:a) sumA+=v; for (double v:b) sumB+=v;
        double ma=sumA/n, mb=sumB/n;
        double num=0, da=0, db=0;
        Iterator<Double> ia = a.iterator();
        Iterator<Double> ib = b.iterator();
        while(ia.hasNext()){
            double x=ia.next()-ma, y=ib.next()-mb;
            num+=x*y; da+=x*x; db+=y*y;
        }
        if (da==0 || db==0) return null;
        return num/Math.sqrt(da*db);
    }
}