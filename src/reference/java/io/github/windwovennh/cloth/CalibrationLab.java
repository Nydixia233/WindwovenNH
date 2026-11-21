package io.github.windwovennh.cloth;

import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Standalone reference and calibration entrypoint. Neither reference meshes nor this code enter the client JAR. */
public final class CalibrationLab {
    private static final int MIN_TICK = 20;
    private static final int REF_WIDTH = 17, REF_ROWS = 49, REF_SUBSTEPS = 24, REF_PASSES = 44;
    // Fixed restart vectors: 0.2.0 plus a starting guess for the exposed vertical flow.
    private static final float[] START_DETAIL = {1.8110312F, .02F, .95F, .54F, .09F, .38F, .15566316F, 3.7F, 1.9F, .25F};
    private static final float[] START_ECONOMY = {1.7188084F, .0814968F, .95F, .54F, .09703414F, .38F, .1523128F, 3.7F, 1.9F, .8F};
    private static final float[] MIN = {1.15F, .02F, .50F, .32F, .075F, .19F, .075F, 3.7F, .85F, .22F};
    private static final float[] MAX = {2.22F, .16F, 1.01F, .55F, .20F, .63F, .22F, 5.6F, 2.1F, .85F};

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        if (args.length != 3 || !("fit".equals(args[0]) || "verify".equals(args[0]) || "turn".equals(args[0]))) {
            throw new IllegalArgumentException("Usage: CalibrationLab fit|verify|turn outputDirectory oldJar");
        }
        Path out = Paths.get(args[1]);
        Files.createDirectories(out);
        boolean fitting = "fit".equals(args[0]);
        boolean turnOnly = "turn".equals(args[0]);
        List<Reference> recordings = new ArrayList<>();
        for (MotionCases example : turnOnly ? MotionCases.stationaryTurns() : MotionCases.all()) {
            if (fitting && example.heldOut) continue; // no holdout metric or frame is fed to the optimizer
            Reference r = new Reference(example);
            recordings.add(r);
            System.out.printf("Reference %s (%s): %d ticks in %.3fs%n", example.name, example.heldOut ? "holdout" : "fit",
                example.steps.length, r.elapsed / 1.0E9);
        }
        if (fitting) {
            try (PrintWriter fit = writer(out.resolve("fitted-presets.txt"))) {
                for (Density density : Density.values()) {
                    float[] best = fit(recordings, density);
                    fit.println(density + " = " + Arrays.toString(best));
                }
            }
            return;
        }
        if (!turnOnly) convergence(recordings, out);
        try (Baseline prior = new Baseline(Paths.get(args[2]));
            PrintWriter scores = writer(out.resolve("scores.csv")); PrintWriter frames = writer(out.resolve("comparison.csv"));
            PrintWriter signals = writer(out.resolve(turnOnly ? "turn-signal.csv" : "shoulder-lift.csv"))) {
            scores.println("scenario,split,grid,old_rms,new_rms,old_hem_rms,new_hem_rms,old_hem_p95,new_hem_p95");
            frames.println("scenario,model,tick,columns,rows,row,column,x,y,z");
            signals.println(turnOnly ? "scenario,grid,model,peak_hem_lateral,peak_hem_edge_depth,peak_hem_edge_height"
                : "scenario,grid,model,peak_hem_rise,peak_tick,frames_over_0_06,during_descent");
            for (Density d : Density.values()) {
                Metric totalOld = new Metric(), totalNew = new Metric();
                for (Reference r : recordings) {
                    float[][] oldTrace = prior.replay(r.motion, d);
                    float[][] newTrace = replay(r.motion, d, null);
                    Metric old = new Metric(), next = new Metric();
                    compare(r, d, oldTrace, old); compare(r, d, newTrace, next);
                    scores.printf("%s,%s,%s,%.7f,%.7f,%.7f,%.7f,%.7f,%.7f%n", r.motion.name, r.motion.heldOut ? "holdout" : "fit",
                        d, old.rms(), next.rms(), old.hemRms(), next.hemRms(), old.p95(), next.p95());
                    float[][] target = d == Density.DETAIL ? r.high : r.low;
                    if (turnOnly) {
                        turnSignal(r.motion, d, "old", oldTrace, signals);
                        turnSignal(r.motion, d, "reference", target, signals);
                        turnSignal(r.motion, d, "runtime", newTrace, signals);
                    } else {
                        loft(r.motion, d, "old", oldTrace, signals);
                        loft(r.motion, d, "reference", target, signals);
                        loft(r.motion, d, "runtime", newTrace, signals);
                    }
                    if (r.motion.heldOut) { totalOld.merge(old); totalNew.merge(next); }
                    if (d == Density.DETAIL && (r.motion.heldOut || "billowed-dive".equals(r.motion.name)
                        || "running-hop".equals(r.motion.name) || "climb-dive".equals(r.motion.name))) {
                        export(r, oldTrace, newTrace, frames);
                    }
                }
                System.out.printf("%s held-out weighted RMS: %.4f -> %.4f blocks (%.1f%% less); hem RMS %.4f -> %.4f%n",
                    d, totalOld.rms(), totalNew.rms(), 100 * (1 - totalNew.rms()/totalOld.rms()), totalOld.hemRms(), totalNew.hemRms());
            }
        }
    }

    private static float[] fit(List<Reference> samples, Density d) {
        float[] result = (d == Density.DETAIL ? START_DETAIL : START_ECONOMY).clone();
        float[] candidate = result.clone();
        double best = objective(samples, d, result);
        float fraction = .23F;
        for (int pass = 0; pass < 14; pass++) {
            float before = (float) best;
            for (int k = 0; k < result.length; k++) {
                float start = result[k], step = fraction * (MAX[k] - MIN[k]);
                for (int sign : new int[] { -1, 1 }) {
                    System.arraycopy(result, 0, candidate, 0, result.length);
                    candidate[k] = Math.max(MIN[k], Math.min(MAX[k], start + sign * step));
                    if (candidate[k] == result[k]) continue;
                    double error = objective(samples, d, candidate);
                    if (error < best) { best = error; result[k] = candidate[k]; }
                }
            }
            if (pass % 2 == 1 || best > before * .992) fraction *= .72F;
            System.out.printf("Fit %s pass %2d error %.6f %s%n", d, pass + 1, Math.sqrt(best), Arrays.toString(result));
        }
        // A coarse sheet needs damping headroom on one-tick strafe reversals, beyond the shape loss.
        if (d == Density.ECONOMY) result[7] = Math.max(4.2F, result[7]);
        return result;
    }

    private static AeroPreset preset(float[] p) {
        return new AeroPreset(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9]);
    }

    private static double objective(List<Reference> samples, Density d, float[] values) {
        WovenCloth.Weave geometry = new WovenCloth.Weave(d, preset(values));
        Metric sum = new Metric();
        for (Reference r : samples) {
            float[][] trace = replay(r.motion, d, geometry);
            if (trace == null) return Double.POSITIVE_INFINITY;
            compare(r, d, trace, sum);
            int width = d.columns * 3;
            for (int tick = MIN_TICK; tick < trace.length; tick++) {
                float[] target = d == Density.DETAIL ? r.high[tick] : r.low[tick];
                for (int i = width; i < trace[tick].length; i += 3) {
                    // Give extra weight to the hem height where the independently simulated fabric lofts.
                    if (i >= (d.rows * 2 / 3) * width && target[i + 1] < -.06F) {
                        double dy = target[i + 1] - trace[tick][i + 1];
                        sum.square += .9 * dy * dy;
                    }
                    double jump = Math.abs(trace[tick][i] - trace[tick - 1][i]);
                    // Fade out overly abrupt reactions that two steps per tick cannot resolve cleanly.
                    if (jump > .18) sum.square += 4 * (jump - .18) * (jump - .18);
                }
            }
        }
        return sum.square / sum.weight;
    }

    private static float[][] replay(MotionCases c, Density d, WovenCloth.Weave fit) {
        WovenCloth cloth = new WovenCloth(c.seed, fit);
        Flow flow = new Flow();
        set(flow, c.steps[0]); cloth.initialize(d, flow);
        float[][] ticks = new float[c.steps.length][d.sites * 3];
        for (int i = 0; i < ticks.length; i++) {
            MotionCases.Input input = c.steps[i];
            set(flow, input); cloth.advance(flow);
            if (!cloth.sample(1, ticks[i])) return null;
        }
        return ticks;
    }

    private static void set(Flow f, MotionCases.Input s) {
        f.update(s.dx, s.up, s.dz, (float)s.yaw, (float)s.turn, s.wet, s.crouch, s.armor);
    }

    private static void compare(Reference r, Density d, float[][] trace, Metric into) {
        if (trace == null) throw new IllegalStateException("Non-finite or invalid pose: " + r.motion.name + " " + d);
        int stride = d.columns * 3;
        for (int tick = MIN_TICK; tick < trace.length; tick++) {
            float[] right = d == Density.DETAIL ? r.high[tick] : r.low[tick];
            float[] candidate = trace[tick];
            for (int i = stride; i < candidate.length; i += 3) {
                double err = squared(candidate, i, right, i);
                int row = i / stride;
                double weight = .5 + 1.5 * row / (d.rows - 1);
                into.square += err * weight; into.weight += weight;
                if (row == d.rows - 1) into.addHem(err);
            }
        }
    }

    private static void turnSignal(MotionCases c, Density d, String model, float[][] trace, PrintWriter out) {
        int left = (d.rows - 1) * d.columns * 3, right = (d.sites - 1) * 3, center = (left + right) / 2;
        double lateral = 0, depth = 0, height = 0;
        float restX = trace[MIN_TICK][center];
        for (int tick = MIN_TICK; tick < trace.length; tick++) if (c.steps[tick].turn != 0) {
            float[] p = trace[tick];
            lateral = Math.max(lateral, Math.abs(p[center] - restX));
            depth = Math.max(depth, Math.abs(p[left + 2] - p[right + 2]));
            height = Math.max(height, Math.abs(p[left + 1] - p[right + 1]));
        }
        out.printf("%s,%s,%s,%.5f,%.5f,%.5f%n", c.name, d, model, lateral, depth, height);
    }

    private static void loft(MotionCases c, Density d, String model, float[][] trace, PrintWriter out) {
        double peak = Double.NEGATIVE_INFINITY;
        int where = -1, above = 0, falling = 0;
        for (int tick = MIN_TICK; tick < trace.length; tick++) {
            double highest = Double.NEGATIVE_INFINITY;
            for (int i = (d.rows - 1) * d.columns * 3; i < trace[tick].length; i += 3) {
                highest = Math.max(highest, -trace[tick][i + 1]);
            }
            if (highest > peak) { peak = highest; where = tick; }
            if (highest > .06) { above++; if (c.steps[tick].up < 0) falling++; }
        }
        out.printf("%s,%s,%s,%.5f,%d,%d,%d%n", c.name, d, model, peak, where, above, falling);
    }

    private static double squared(float[] a, int i, float[] b, int j) {
        double x = a[i] - b[j], y = a[i + 1] - b[j + 1], z = a[i + 2] - b[j + 2];
        return x * x + y * y + z * z;
    }

    private static void convergence(List<Reference> samples, Path out) throws Exception {
        try (PrintWriter report = writer(out.resolve("convergence.csv"))) {
            report.println("case,varied_dimension,fine_width,fine_rows,fine_substeps,fine_passes,common_node_rms,hem_rms");
            for (Reference s : samples) {
                if (!"climb-dive".equals(s.motion.name) && !"banked-fall".equals(s.motion.name)) continue;
                for (int variant = 0; variant < 2; variant++) {
                    int w = variant == 0 ? 33 : REF_WIDTH, h = (w - 1) * 3 + 1, t = variant == 0 ? REF_SUBSTEPS : 48;
                    int it = variant == 0 ? 68 : REF_PASSES;
                    ReferenceCloth finer = new ReferenceCloth(w, h, t, it, s.motion.seed);
                    finer.initialize(s.motion.steps[0]);
                    Metric dist = new Metric();
                    for (int tick = 0; tick < s.motion.steps.length; tick++) {
                        finer.tick(s.motion.steps[tick]);
                        if (tick < MIN_TICK) continue;
                        float[] a = finer.sample(5, 13), b = s.high[tick];
                        for (int i = 15; i < a.length; i += 3) {
                            double e = squared(a, i, b, i); dist.square += e; dist.weight++;
                            if (i >= 12 * 15) dist.addHem(e);
                        }
                    }
                    if (!Double.isFinite(dist.rms())) throw new IllegalStateException("Unstable reference " + s.motion.name);
                    report.printf("%s,%s,%d,%d,%d,%d,%.7f,%.7f%n", s.motion.name, variant == 0 ? "space" : "time",
                        w, h, t, it, dist.rms(), dist.hemRms());
                    System.out.printf("Reference resolution sensitivity %s %s: %.4f blocks RMS%n", s.motion.name,
                        variant == 0 ? "space" : "time", dist.rms());
                }
            }
        }
    }

    private static void export(Reference ref, float[][] old, float[][] next, PrintWriter out) {
        // Common synthetic times (each 2 client ticks); the reference retains its original dense mesh for metrics,
        // and exports a 9×25 subset for the view to keep data files compact.
        for (int t = 20; t < next.length; t += 2) {
            emit(out, ref.motion.name, "old", t, 5, 13, old[t]);
            emit(out, ref.motion.name, "reference", t, 9, 25, ref.preview[t]);
            emit(out, ref.motion.name, "runtime", t, 5, 13, next[t]);
        }
    }

    private static void emit(PrintWriter p, String name, String model, int tick, int w, int h, float[] xyz) {
        for (int r = 0, i = 0; r < h; r++) for (int c = 0; c < w; c++, i += 3) {
            p.printf("%s,%s,%d,%d,%d,%d,%d,%.5f,%.5f,%.5f%n", name, model, tick, w, h, r, c, xyz[i], xyz[i + 1], xyz[i + 2]);
        }
    }

    private static PrintWriter writer(Path p) throws Exception {
        return new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8));
    }

    private static final class Reference {
        final MotionCases motion;
        final float[][] high, low, preview;
        final long elapsed;
        Reference(MotionCases c) {
            motion = c;
            high = new float[c.steps.length][]; low = new float[c.steps.length][]; preview = new float[c.steps.length][];
            long begin = System.nanoTime();
            ReferenceCloth cloth = new ReferenceCloth(REF_WIDTH, REF_ROWS, REF_SUBSTEPS, REF_PASSES, c.seed);
            cloth.initialize(c.steps[0]);
            for (int tick = 0; tick < high.length; tick++) {
                cloth.tick(c.steps[tick]);
                high[tick] = cloth.sample(5, 13); low[tick] = cloth.sample(3, 7); preview[tick] = cloth.sample(9, 25);
                for (float value : preview[tick]) if (!Float.isFinite(value) || Math.abs(value) > 2) {
                    throw new IllegalStateException("Divergent reference: " + c.name + " tick " + tick);
                }
            }
            elapsed = System.nanoTime() - begin;
        }
    }

    private static final class Metric {
        double square, weight, hemSquare;
        long hemCount;
        double[] ends = new double[1024];
        void addHem(double v) {
            if (hemCount >= ends.length) ends = Arrays.copyOf(ends, ends.length * 2);
            ends[(int)hemCount++] = Math.sqrt(v); hemSquare += v;
        }
        void merge(Metric other) {
            square += other.square; weight += other.weight; hemSquare += other.hemSquare;
            if (hemCount + other.hemCount > ends.length) ends = Arrays.copyOf(ends, (int)(hemCount + other.hemCount) * 2);
            System.arraycopy(other.ends, 0, ends, (int)hemCount, (int)other.hemCount); hemCount += other.hemCount;
        }
        double rms() { return Math.sqrt(square / weight); }
        double hemRms() { return Math.sqrt(hemSquare / hemCount); }
        double p95() { Arrays.sort(ends, 0, (int)hemCount); return ends[(int)Math.ceil(.95 * hemCount) - 1]; }
    }

    /** Use the real previous JAR, not a copied or reconstructed old solver. */
    static final class Baseline implements AutoCloseable {
        private final URLClassLoader loader;
        private final Constructor<?> ctor, flowCtor;
        private final Method initialize, update, advance, sample;
        private final Object[] densities;
        Baseline(Path jar) throws Exception {
            loader = new URLClassLoader(new URL[] { jar.toRealPath().toUri().toURL() }, null);
            String ns = loader.findResource("io/github/windwovennh/cloth/WovenCloth.class") != null
                ? "io.github.windwovennh.cloth." : "io.github.trailcloth.cloth.";
            Class<?> type = loader.loadClass(ns + "WovenCloth");
            Class<?> flowType = loader.loadClass(ns + "Flow");
            Class<?> densityType = loader.loadClass(ns + "Density");
            densities = densityType.getEnumConstants();
            ctor = type.getConstructor(int.class); flowCtor = flowType.getConstructor();
            initialize = type.getMethod("initialize", densityType, flowType);
            update = flowType.getMethod("update", double.class, double.class, double.class, float.class, float.class,
                boolean.class, boolean.class, boolean.class);
            advance = type.getMethod("advance", flowType); sample = type.getMethod("sample", float.class, float[].class);
        }
        float[][] replay(MotionCases c, Density d) throws Exception { return replay(c, d, 1F); }

        float[][] replay(MotionCases c, Density d, float fraction) throws Exception {
            Object cape = ctor.newInstance(c.seed), f = flowCtor.newInstance();
            set(f, c.steps[0]); initialize.invoke(cape, densities[d.ordinal()], f);
            float[][] trace = new float[c.steps.length][d.sites * 3];
            for (int i = 0; i < trace.length; i++) {
                set(f, c.steps[i]); advance.invoke(cape, f);
                if (!(Boolean)sample.invoke(cape, fraction, trace[i])) return null;
            }
            return trace;
        }
        private void set(Object f, MotionCases.Input s) throws Exception {
            update.invoke(f, s.dx, s.up, s.dz, (float)s.yaw, (float)s.turn, s.wet, s.crouch, s.armor);
        }
        @Override public void close() throws Exception { loader.close(); }
    }
}
