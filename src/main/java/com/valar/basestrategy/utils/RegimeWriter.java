package com.valar.basestrategy.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class RegimeWriter implements Closeable {
    private final BufferedWriter w;

    public RegimeWriter(String file) throws IOException {
        Path path = Paths.get(file);
        Files.createDirectories(path.getParent());
        this.w = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        w.write("date,regime_code,regime_name,C,T,V,R");
        w.newLine();
        w.flush();
    }

    //date,code,label,C,T,V,R

    public synchronized void append(String date, int code, String label, int C, int T, int V, int R) throws IOException {
        w.write(date); w.write(",");
        w.write(Integer.toString(code)); w.write(",");
        w.write(label); w.write(",");
        w.write(Integer.toString(C)); w.write(",");
        w.write(Integer.toString(T)); w.write(",");
        w.write(Integer.toString(V)); w.write(",");
        w.write(Integer.toString(R));
        w.newLine();
        w.flush();
    }

    public synchronized void append(String date, int code, String label) throws IOException {
        append(date, code, label, -1, -1, -1, code);
    }

    @Override public void close() throws IOException { w.flush(); w.close(); }
}