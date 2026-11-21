package io.github.windwovennh.cloth;

import java.nio.file.Paths;
import java.util.List;

/** Exercise both reobfuscated solvers in isolated loaders; no Minecraft client required. */
public final class RenameParity {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected the legacy and renamed production JARs");
        List<MotionCases> cases = MotionCases.all();
        cases.addAll(MotionCases.stationaryTurns());
        long samples = 0;
        try (CalibrationLab.Baseline old = new CalibrationLab.Baseline(Paths.get(args[0]));
            CalibrationLab.Baseline next = new CalibrationLab.Baseline(Paths.get(args[1]))) {
            for (MotionCases motion : cases) for (Density grid : Density.values()) for (float t : new float[] { .5F, 1F }) {
                float[][] before = old.replay(motion, grid, t), after = next.replay(motion, grid, t);
                if (before == null || after == null) throw new AssertionError("Invalid run: " + motion.name + " " + grid);
                for (int frame = 0; frame < before.length; frame++) {
                    for (int c = 0; c < before[frame].length; c++) {
                        if (Float.floatToRawIntBits(before[frame][c]) != Float.floatToRawIntBits(after[frame][c])) {
                            throw new AssertionError(motion.name + " " + grid + " tick=" + frame + " subTick=" + t + " coordinate=" + c);
                        }
                        samples++;
                    }
                }
            }
        }
        System.out.println("PASS: " + samples + " bit-identical coordinates across " + cases.size()
            + " cases, both LODs and both tick/interpolated samples");
    }
}
