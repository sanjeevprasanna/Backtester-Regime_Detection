package com.valar.basestrategy.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

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

    // (BN - dd-MM-yy; NIFTY - dd/MM/yy)
    private static final DateTimeFormatter F_SLASH = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter F_DASH  = DateTimeFormatter.ofPattern("dd-MM-yy");

    public DayIterator(String pathStr) throws IOException {
        Path p = Paths.get(pathStr);
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String ln;
            while ((ln = br.readLine()) != null) {
                if (ln.isEmpty()) continue;
                String[] t = ln.split(",", -1);
                if (t.length < 8) continue;

                LocalDate d = parseDate(t[0]); if (d == null) continue;
                double o = parseD(t[1]), h = parseD(t[2]), l = parseD(t[3]), c = parseD(t[4]);
                double v = parseD(t[5]), atr = parseD(t[6]), adx = parseD(t[7]);

                rows.add(new Row(d, o, h, l, c, v, atr, adx));
            }
        }
        rows.sort(Comparator.comparing(r -> r.date));
        if (rows.isEmpty()) {
            throw new IOException("No rows parsed from " + pathStr + " (need dd/MM/yy or dd-MM-yy in col 0).");
        }
    }

    /** Peek the next row's date (null if finished). */
    public LocalDate peekNextDate() { return (idx < rows.size()) ? rows.get(idx).date : null; }

    /** Return the next row and advance the cursor (null if finished). */
    public Row poll() { return (idx < rows.size()) ? rows.get(idx++) : null; }

    private static double parseD(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0.0; }
    }
    private static LocalDate parseDate(String s) {
        if (s == null) return null; s = s.trim();
        try { return LocalDate.parse(s, F_SLASH); } catch (DateTimeParseException ignore) {}
        try { return LocalDate.parse(s, F_DASH);  } catch (DateTimeParseException ignore) {}
        return null;
    }
}