package io.github.windwovennh.qa;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/** Tick mesh measurements in a disposable fixture; no instrumentation in the production render loop. */
final class WindTrace {
    private final Field service, garments, cloth, shoulder, squat;
    private final Method density, sample, lowerLip;
    private final Field columns, rows;
    private final float[] xyz = new float[65 * 3];
    private final StringBuilder output = new StringBuilder(
        "tick,stage,mid_rear,hem_rear,hem_height,max_vertical_link_ratio,contact_violations\n");
    private int samples;

    WindTrace() throws ReflectiveOperationException {
        String root = "io.github.windwovennh.";
        Class<?> wearer = Class.forName(root + "client.PlayerCloths");
        Class<?> solver = Class.forName(root + "cloth.WovenCloth");
        service = field(wearer, "addon");
        garments = field(wearer, "lookup");
        cloth = field(Class.forName(root + "client.PlayerCloths$Garment"), "cloth");
        shoulder = field(solver, "shoulder");
        squat = field(solver, "squat");
        density = solver.getMethod("density");
        columns = density.getReturnType().getField("columns");
        rows = density.getReturnType().getField("rows");
        sample = solver.getMethod("sample", float.class, float[].class);
        lowerLip = roundedHeadContact(root);
    }

    void capture(int tick, String stage, Object actor) throws IOException {
        try {
            Object active = service.get(null);
            Object g = active == null ? null : ((Map<?, ?>) garments.get(active)).get(actor);
            if (g == null) return;
            Object fabric = cloth.get(g);
            if (!(Boolean) sample.invoke(fabric, 1F, xyz)) throw new IOException("Invalid cape sample");
            Object grid = density.invoke(fabric);
            int cols = columns.getInt(grid), height = rows.getInt(grid), stride = cols * 3;
            float seam = shoulder.getFloat(fabric), crouch = squat.getFloat(fabric);
            int middle = height / 2 * stride + cols / 2 * 3, hem = (height - 1) * stride + cols / 2 * 3;
            double maxStretch = 0;
            int intersections = 0;
            for (int i = stride; i < height * stride; i += 3) {
                float x = xyz[i], y = xyz[i + 1], z = xyz[i + 2];
                float back = seam + crouch * (.48F * Math.max(0, Math.min(.71F, y)));
                if (z < back - 1E-5F || (Math.abs(x) < .455F - 1E-5F && y > -.585F + .023F * crouch + 1E-5F
                    && y < -1E-5F && z < .333F - 1E-5F
                    && (lowerLip == null || y < (float) lowerLip.invoke(null, z, 0F) - 1E-5F))) intersections++;
                double dx = x - xyz[i - stride], dy = y - xyz[i - stride + 1], dz = z - xyz[i - stride + 2];
                maxStretch = Math.max(maxStretch, Math.sqrt(dx * dx + dy * dy + dz * dz) * (height - 1));
            }
            output.append(tick).append(',').append(stage).append(',').append(xyz[middle + 2] - seam)
                .append(',').append(xyz[hem + 2] - seam).append(',').append(-xyz[hem + 1])
                .append(',').append(maxStretch).append(',').append(intersections).append('\n');
            samples++;
        } catch (ReflectiveOperationException e) {
            throw new IOException("Could not sample test cape", e);
        }
    }

    void finish(File directory, String run) throws IOException {
        if (samples < 250) throw new IOException("Too few cape geometry samples: " + samples);
        Files.write(new File(directory, run + "-geometry.csv").toPath(), output.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Method roundedHeadContact(String root) throws ReflectiveOperationException {
        try {
            Method lip = Class.forName(root + "cloth.BodyContact").getDeclaredMethod("lowerLip", float.class, float.class);
            lip.setAccessible(true);
            return lip;
        } catch (NoSuchMethodException olderBuild) {
            // Comparisons against pre-rounding builds still use the old flat lower boundary.
            return null;
        }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
