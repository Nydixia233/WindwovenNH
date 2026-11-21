package io.github.windwovennh.cloth;

/** Continuous, outward-normal mesh using the renderer's already-bound cloak atlas. */
public final class ClothSurface {
    private static final float HALF_THICKNESS = 0.03125F;
    private static final float UV_X = 1.0F / 64;
    private static final float UV_Y = 1.0F / 32;
    private final float[] mids = new float[WovenCloth.CAPACITY * 3];
    private final float[] facing = new float[mids.length];
    private final float[] side = new float[mids.length];
    private final float[] rim = new float[3];
    private Density density;

    public boolean prepare(WovenCloth cloth, float delta) {
        if (!cloth.sample(delta, mids)) return false;
        density = cloth.density();
        int w = density.columns;
        int h = density.rows;
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                int p = (r * w + c) * 3;
                float x = mids[p];
                float y = mids[p + 1];
                float z = mids[p + 2];
                // Contact points are on the inside of the fabric. Move the center and both faces behind them.
                mids[p] = -x;
                mids[p + 1] = y;
                mids[p + 2] = -z - HALF_THICKNESS;
            }
        }
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                int p = (r * w + c) * 3;
                int a = (r * w + Math.max(0, c - 1)) * 3, b = (r * w + Math.min(w - 1, c + 1)) * 3;
                int u = (Math.max(0, r - 1) * w + c) * 3, d = (Math.min(h - 1, r + 1) * w + c) * 3;
                float sx = mids[a] - mids[b], sy = mids[a + 1] - mids[b + 1], sz = mids[a + 2] - mids[b + 2];
                float dx = mids[d] - mids[u], dy = mids[d + 1] - mids[u + 1], dz = mids[d + 2] - mids[u + 2];
                unit(side, p, sx, sy, sz, 1, 0, 0);
                unit(facing, p, sy * dz - sz * dy, sz * dx - sx * dz, sx * dy - sy * dx, 0, 0, 1);
            }
        }
        return true;
    }

    private static void unit(float[] out, int i, float x, float y, float z, float fx, float fy, float fz) {
        float norm = x * x + y * y + z * z;
        if (norm < 1.0E-10F) {
            out[i] = fx;
            out[i + 1] = fy;
            out[i + 2] = fz;
        } else {
            float scale = 1.0F / (float) Math.sqrt(norm);
            out[i] = x * scale;
            out[i + 1] = y * scale;
            out[i + 2] = z * scale;
        }
    }

    public void writeQuads(VertexOut out) {
        int w = density.columns, h = density.rows;
        // ModelBox's 10x16x1 cloak unwrap: each face has a distinct normalized rectangle.
        for (int r = 0; r < h - 1; r++) {
            float v = (1 + r * 16.0F / (h - 1)) * UV_Y;
            float vNext = (1 + (r + 1) * 16.0F / (h - 1)) * UV_Y;
            for (int c = 0; c < w - 1; c++) {
                float a = c * 10.0F / (w - 1), b = (c + 1) * 10.0F / (w - 1);
                int tl = (r * w + c) * 3, tr = tl + 3, bl = tl + w * 3, br = bl + 3;
                put(out, tl, (11 - a) * UV_X, v, -1, facing, -1);
                put(out, tr, (11 - b) * UV_X, v, -1, facing, -1);
                put(out, br, (11 - b) * UV_X, vNext, -1, facing, -1);
                put(out, bl, (11 - a) * UV_X, vNext, -1, facing, -1);
                put(out, tr, (12 + b) * UV_X, v, 1, facing, 1);
                put(out, tl, (12 + a) * UV_X, v, 1, facing, 1);
                put(out, bl, (12 + a) * UV_X, vNext, 1, facing, 1);
                put(out, br, (12 + b) * UV_X, vNext, 1, facing, 1);
            }
            int right = r * w * 3, rightNext = right + w * 3;
            int left = right + (w - 1) * 3, leftNext = left + w * 3;
            put(out, right, 12 * UV_X, v, 1, side, 1);
            put(out, right, 11 * UV_X, v, -1, side, 1);
            put(out, rightNext, 11 * UV_X, vNext, -1, side, 1);
            put(out, rightNext, 12 * UV_X, vNext, 1, side, 1);
            put(out, left, UV_X, v, -1, side, -1);
            put(out, left, 0, v, 1, side, -1);
            put(out, leftNext, 0, vNext, 1, side, -1);
            put(out, leftNext, UV_X, vNext, -1, side, -1);
        }
        for (int c = 0; c < w - 1; c++) {
            float a = c * 10.0F / (w - 1), b = (c + 1) * 10.0F / (w - 1);
            int left = c * 3, right = left + 3;
            capNormal(left, right, 1);
            put(out, left, (11 - a) * UV_X, UV_Y, -1, rim, 1);
            put(out, right, (11 - b) * UV_X, UV_Y, -1, rim, 1);
            put(out, right, (11 - b) * UV_X, 0, 1, rim, 1);
            put(out, left, (11 - a) * UV_X, 0, 1, rim, 1);
            left += (h - 1) * w * 3;
            right += (h - 1) * w * 3;
            capNormal(left, right, -1);
            put(out, left, (21 - a) * UV_X, 0, 1, rim, 1);
            put(out, right, (21 - b) * UV_X, 0, 1, rim, 1);
            put(out, right, (21 - b) * UV_X, UV_Y, -1, rim, 1);
            put(out, left, (21 - a) * UV_X, UV_Y, -1, rim, 1);
        }
    }

    private void capNormal(int a, int b, int orientation) {
        float x = mids[b] - mids[a], y = mids[b + 1] - mids[a + 1], z = mids[b + 2] - mids[a + 2];
        float nx = facing[a] + facing[b], ny = facing[a + 1] + facing[b + 1], nz = facing[a + 2] + facing[b + 2];
        unit(rim, 0, orientation * (y * nz - z * ny), orientation * (z * nx - x * nz),
            orientation * (x * ny - y * nx), 0, orientation, 0);
    }

    private void put(VertexOut out, int i, float u, float v, int face, float[] normal, int sign) {
        float offset = HALF_THICKNESS * face;
        int n = normal == rim ? 0 : i;
        out.point(mids[i] + facing[i] * offset, mids[i + 1] + facing[i + 1] * offset,
            mids[i + 2] + facing[i + 2] * offset, u, v,
            normal[n] * sign, normal[n + 1] * sign, normal[n + 2] * sign);
    }

    public interface VertexOut {
        void point(float x, float y, float z, float u, float v, float nx, float ny, float nz);
    }
}
