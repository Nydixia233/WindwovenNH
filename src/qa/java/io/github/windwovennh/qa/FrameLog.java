package io.github.windwovennh.qa;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/** Same recorder in installed and physically uninstalled runs. */
final class FrameLog {
    private static final String HEADER = "run,scene,capes,frames,width,height,sum_ns,p50_ns,p95_ns,p99_ns,allocated_bytes,"
        + "tick_ns,draw_ns,step_high,step_low,draw_high,draw_low,far_original,draw_bends";
    private static final String[] COUNTERS = { "tickNanos", "drawNanos", "stepHigh", "stepLow", "drawHigh", "drawLow", "originalDistance" };
    private final Field[] fields = new Field[COUNTERS.length];
    private final long[] initial = new long[fields.length];
    private final long[] intervals = new long[131072];
    private final java.lang.management.ThreadMXBean jvm = ManagementFactory.getThreadMXBean();
    private final Field bends;
    private long initialBends;
    private int size;
    private long last, initialAllocation;

    private final Class<?> renamed = load("io.github.windwovennh.client.PlayerCloths");
    private final Class<?> legacy = load("io.github.trailcloth.client.PlayerCloths");

    FrameLog() throws NoSuchFieldException {
        bends = optionalField(renamed, "drawBends");
        Class<?> tracked = renamed != null ? renamed : legacy;
        if (tracked != null) for (int i = 0; i < fields.length; i++) fields[i] = tracked.getField(COUNTERS[i]);
    }

    private static Field optionalField(Class<?> type, String name) {
        if (type == null) return null;
        try {
            return type.getField(name);
        } catch (NoSuchFieldException notInOlderBuild) {
            return null;
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException notInstalled) {
            return null;
        }
    }

    void assertLegacyOnly() throws ReflectiveOperationException {
        if (renamed == null || legacy == null) throw new IllegalStateException("Expected both JARs");
        for (String counter : COUNTERS) {
            if (renamed.getField(counter).getLong(null) != 0) throw new AssertionError("Renamed hook activated alongside legacy: " + counter);
        }
        if (legacy.getField("stepHigh").getLong(null) <= 0 || legacy.getField("drawHigh").getLong(null) <= 0) {
            throw new AssertionError("Legacy renderer did not run during coexistence test");
        }
    }

    boolean renamedPresent() { return renamed != null; }
    boolean legacyPresent() { return legacy != null; }

    boolean productionPresent() {
        return fields[0] != null;
    }

    void reset() throws IllegalAccessException {
        size = 0;
        last = 0;
        for (int i = 0; i < fields.length; i++) initial[i] = fields[i] == null ? 0 : fields[i].getLong(null);
        initialBends = bends == null ? 0 : bends.getLong(null);
        initialAllocation = allocation();
    }

    void discardInterval() {
        last = 0;
    }

    void frame() {
        long current = System.nanoTime();
        if (last != 0 && size < intervals.length) intervals[size++] = current - last;
        last = current;
    }

    String write(File dir, String id, int scene, int capes, int width, int height) throws IOException, IllegalAccessException {
        long bytes = allocation();
        long total = 0;
        for (int i = 0; i < size; i++) total += intervals[i];
        Arrays.sort(intervals, 0, size);
        String row = id + ',' + scene + ',' + capes + ',' + size + ',' + width + ',' + height + ',' + total + ','
            + quantile(.50) + ',' + quantile(.95) + ',' + quantile(.99) + ','
            + (bytes < 0 ? -1 : bytes - initialAllocation);
        for (int i = 0; i < fields.length; i++) row += "," + (fields[i] == null ? 0 : fields[i].getLong(null) - initial[i]);
        row += "," + (bends == null ? 0 : bends.getLong(null) - initialBends);
        if (size < 100) throw new IOException("Too few rendered frames: " + size);
        File csv = new File(dir, id + ".csv");
        String text = (csv.exists() ? "" : HEADER + "\n") + row + "\n";
        Files.write(csv.toPath(), text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return row;
    }

    private long quantile(double q) {
        return size == 0 ? 0 : intervals[(int) Math.ceil(q * size) - 1];
    }

    private long allocation() {
        return jvm instanceof com.sun.management.ThreadMXBean
            ? ((com.sun.management.ThreadMXBean) jvm).getThreadAllocatedBytes(Thread.currentThread().getId()) : -1;
    }
}
