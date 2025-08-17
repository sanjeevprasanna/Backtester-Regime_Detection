package com.valar.basestrategy.entities.indicators;

import java.util.Deque;
import java.util.Iterator;

public class RegimeDetector {

    public static final class Regime {
        public final int code;
        public final String label;
        public Regime(int code, String label){ this.code = code; this.label = label; }
        public String name() { return label; } // compat shim
    }

    private final int windowN;
    private final double volThreshold;
    private final double corrThreshold;

    public RegimeDetector(int windowN, double volThreshold, double corrThreshold) {
        if (windowN <= 1) throw new IllegalArgumentException("windowN must be > 1");
        this.windowN = windowN; this.volThreshold = volThreshold; this.corrThreshold = corrThreshold;
    }

    public int windowN() { return windowN; }

    public static double dailyReturn(double prev, double curr) {
        if (prev > 0.0 && curr > 0.0) return Math.log(curr / prev);
        return (curr - prev) / (prev == 0.0 ? 1.0 : prev);
    }

    // V from std(returns)>vol_Threshold
    // T from P_last > SMA_N(P)
    // C from |corr(BN,NF)| > θ_cor(threshold)

    public Regime compute(Deque<Double> priceQ, Deque<Double> retQForVol, Deque<Double> corrA, Deque<Double> corrB) {
        if (priceQ == null || retQForVol == null || priceQ.size() < windowN || retQForVol.size() < windowN) return null;

        double sigma = std(retQForVol);
        int V = sigma > volThreshold ? 1 : 0;

        double pLast = priceQ.getLast();
        double ma = mean(priceQ);
        int T = pLast > ma ? 1 : 0;

        int C = 0;
        if (corrA != null && corrB != null && corrA.size() >= windowN && corrB.size() >= windowN && corrA.size() == corrB.size()) {
            Double rho = pearson(corrA, corrB);
            if (rho != null && Math.abs(rho) > corrThreshold) C = 1;
        }

        int code = 4*C + 2*T + V;
        String label = labelFor(code);
        return new Regime(code, label);
    }

    private static double mean(Deque<Double> q){
        double sum=0.0;
        for (double val:q) sum+=val;
        return sum/q.size();
    }
    private static double std(Deque<Double> q){
        double avg=mean(q), sum=0.0;
        for (double val:q){
            double dif=val-avg;
            sum+=dif*dif;
        }
        return Math.sqrt(sum/q.size());
    }
    private static Double pearson(Deque<Double> a, Deque<Double> b) {
        if (a.size()!=b.size() || a.isEmpty()) return null;
        double meanOfa=mean(a), meanOfb=mean(b), num=0.0, da=0.0, db=0.0;
        Iterator<Double> iterOfa=a.iterator(), iterOfb=b.iterator();
        while (iterOfa.hasNext()) {
            double diffOfa=iterOfa.next()-meanOfa, diffOfb=iterOfb.next()-meanOfb;
            num+=diffOfa*diffOfb;
            da+=diffOfa*diffOfa;
            db+=diffOfb*diffOfb;
        }
        double den=Math.sqrt(da)*Math.sqrt(db);
        return den==0.0?0.0:num/den;
    }
    private static String labelFor(int code) {
        switch (code) {
            case 0: return "Calm-NonTrending-Uncorrelated";
            case 1: return "Volatile-NonTrending-Uncorrelated";
            case 2: return "Calm-Trending-Uncorrelated";
            case 3: return "Volatile-Trending-Uncorrelated";
            case 4: return "Calm-NonTrending-Correlated";
            case 5: return "Volatile-NonTrending-Correlated";
            case 6: return "Calm-Trending-Correlated";
            case 7: return "Volatile-Trending-Correlated";
            default: return "Unknown";
        }
    }
}