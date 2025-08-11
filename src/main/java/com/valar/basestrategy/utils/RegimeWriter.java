package com.valar.basestrategy.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class RegimeWriter implements Closeable {
    private final BufferedWriter w;

    public RegimeWriter(String path) throws IOException {
        Path p = Paths.get(path);
        Files.createDirectories(p.getParent());
        boolean exists = Files.exists(p);
        w = Files.newBufferedWriter(p, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        if (!exists) {
            w.write("date,regime_code,regime_name");
            w.newLine();
            w.flush();
        }
    }

    public synchronized void append(String dateIso, int code, String name) throws IOException {
        w.write(dateIso); w.write(",");
        w.write(Integer.toString(code)); w.write(",");
        w.write(name);
        w.newLine();
        w.flush();
    }

    @Override public void close() throws IOException { w.flush(); w.close(); }
}