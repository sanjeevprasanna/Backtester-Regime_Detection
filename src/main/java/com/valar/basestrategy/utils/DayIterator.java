package com.valar.basestrategy.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import static com.valar.basestrategy.application.PropertiesReader.properties;

public class DayIterator {

    public static final class Row {
        public final LocalDate date;
        public final double open, high, low, close, volume;
        public final double atr14, adx14;
        public Row(LocalDate date, double open, double high, double low, double close,
                   double volume, double atr14, double adx14) {
            this.date = date; this.open = open; this.high = high; this.low = low;
            this.close = close; this.volume = volume; this.atr14 = atr14; this.adx14 = adx14;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private int idx = 0;
    private LocalDate firstDate = null, lastDate = null;
    private int parsedCount = 0, skippedCount = 0;

    private static final DateTimeFormatter FMT_DD_MM_YY = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final DateTimeFormatter FMT_ISO      = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FMT_DD_SL_YY = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter FMT_DD_MMM_YY= DateTimeFormatter.ofPattern("dd-MMM-yy");

    public DayIterator() throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(properties.getProperty("regimeDayPath")))) {
            String first = br.readLine();
            if (first == null) throw new IOException("Empty day CSV: " +properties.getProperty("regimeDayPath"));

            boolean hasHeader = looksLikeHeader(first);
            int[] cols;
            if (hasHeader) cols = mapHeader(first);
            else {
                cols = defaultCols();
                pushLine(first, cols);
            }
            String ln;
            while ((ln = br.readLine()) != null) pushLine(ln, cols);
        }
        rows.sort(Comparator.comparing(r -> r.date));
        if (!rows.isEmpty()) {
            firstDate = rows.get(0).date;
            lastDate  = rows.get(rows.size()-1).date;
        }
    }

    public List<Row> advanceAllToPreviousOf(LocalDate minuteDate) {
        List<Row> out = new ArrayList<>();
        while (idx < rows.size() && rows.get(idx).date.isBefore(minuteDate)) {
            out.add(rows.get(idx));
            idx++;
        }
        return out;
    }


    public void rewind() { idx = 0; }

    public String info() {
        return "DayIterator{rows=" + rows.size()
                + ", first=" + firstDate
                + ", last=" + lastDate
                + ", parsed=" + parsedCount
                + ", skipped=" + skippedCount
                + ", idx=" + idx + "}";
    }

    // --- helpers ---
    private void pushLine(String ln, int[] cols) {
        if (ln == null || ln.isEmpty()) return;
        String[] t = ln.split(",", -1);
        if (t.length == 0) return;
        LocalDate d = parseDateFlexible(get(t, cols[0]));
        if (d == null) { skippedCount++; return; }

        double o = parseD(get(t, cols[1]));
        double h = parseD(get(t, cols[2]));
        double l = parseD(get(t, cols[3]));
        double c = parseD(get(t, cols[4]));
        double v = parseD(get(t, cols[5]));
        double atr = parseD(get(t, cols[6]));
        double adx = parseD(get(t, cols[7]));

        rows.add(new Row(d, o, h, l, c, v, atr, adx));
        parsedCount++;
    }

    private static String get(String[] t, int i) {
        return (i >= 0 && i < t.length) ? t[i].trim() : "";
    }
    private static double parseD(String s) {
        if (s==null||s.isEmpty()) return 0.0;
        try { return Double.parseDouble(s); } catch(Exception e){ return 0.0; }
    }

    private static LocalDate parseDateFlexible(String s) {
        if (s == null) return null;
        s = s.trim();
        try { return LocalDate.parse(s, FMT_DD_MM_YY); } catch (DateTimeParseException ignore) {}
        try { return LocalDate.parse(s, FMT_ISO); }      catch (DateTimeParseException ignore) {}
        try { return LocalDate.parse(s, FMT_DD_SL_YY); } catch (DateTimeParseException ignore) {}
        try { return LocalDate.parse(s, FMT_DD_MMM_YY);} catch (DateTimeParseException ignore) {}
        return null;
    }

    private static boolean looksLikeHeader(String firstLine) {
        String l = firstLine.toLowerCase(Locale.ROOT);
        return l.contains("date") || l.contains("open") || l.contains("close") || l.contains("atr") || l.contains("adx");
    }

    private static int[] mapHeader(String header) {
        String[] h = header.split(",", -1);
        Map<String,Integer> pos = new HashMap<>();
        for (int i=0;i<h.length;i++) pos.put(h[i].trim().toLowerCase(Locale.ROOT), i);

        int date = find(pos, "date", "day", "timestamp");
        int open = find(pos, "open", "o");
        int high = find(pos, "high", "h");
        int low  = find(pos, "low", "l");
        int close= find(pos, "close", "c", "adj close", "adj_close");
        int vol  = find(pos, "volume", "vol", "v");
        int atr  = find(pos, "atr", "atr14", "atr%", "atr_percent", "avg true range");
        int adx  = find(pos, "adx", "adx14", "average directional index");

        int[] def = defaultCols();
        return new int[]{
                date>=0?date:def[0],
                open>=0?open:def[1],
                high>=0?high:def[2],
                low >=0?low :def[3],
                close>=0?close:def[4],
                vol >=0?vol :def[5],
                atr >=0?atr :def[6],
                adx >=0?adx :def[7],
        };
    }

    private static int find(Map<String,Integer> m, String... keys){
        for (String k: keys) { Integer v = m.get(k); if (v != null) return v; }
        return -1;
    }

    private static int[] defaultCols(){ return new int[]{0,1,2,3,4,5,6,7}; }
}