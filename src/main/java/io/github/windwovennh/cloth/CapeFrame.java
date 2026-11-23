package io.github.windwovennh.cloth;

/** Orthonormal basis: cape coordinates (lateral, down the back, rear) to upright player-yaw space. */
public final class CapeFrame {
    private float xx, xy, xz, yx, yy, yz, zx, zy, zz;

    public CapeFrame() { identity(); }

    public void identity() {
        xx = yy = zz = 1;
        xy = xz = yx = yz = zx = zy = 0;
    }

    public void copy(CapeFrame m) {
        xx = m.xx; xy = m.xy; xz = m.xz;
        yx = m.yx; yy = m.yy; yz = m.yz;
        zx = m.zx; zy = m.zy; zz = m.zz;
    }

    public boolean same(CapeFrame m) {
        return xx == m.xx && xy == m.xy && xz == m.xz && yx == m.yx && yy == m.yy && yz == m.yz
            && zx == m.zx && zy == m.zy && zz == m.zz;
    }

    public void setRoot(float pitch, float yaw, float roll) {
        double x = Math.toRadians(pitch), y = Math.toRadians(yaw), z = Math.toRadians(roll);
        float a = (float) Math.cos(x), b = (float) Math.sin(x), c = (float) Math.cos(y), d = (float) Math.sin(y),
            e = (float) Math.cos(z), f = (float) Math.sin(z);
        xx = c * e; xy = -c * f; xz = d;
        yx = a * f + b * d * e; yy = a * e - b * d * f; yz = -b * c;
        zx = b * f - a * d * e; zy = b * e + a * d * f; zz = a * c;
    }

    /** Combine the root and bone rotations, discarding model scales but not their orientation. */
    public boolean fromBone(CapeFrame root, float ax, float ay, float az, float bx, float by, float bz,
        float cx, float cy, float cz) {
        float a = inverseLength(ax, ay, az);
        if (a == 0) return false;
        ax *= a; ay *= a; az *= a;
        float projection = ax * bx + ay * by + az * bz;
        bx -= projection * ax; by -= projection * ay; bz -= projection * az;
        float b = inverseLength(bx, by, bz);
        if (b == 0) return false;
        bx *= b; by *= b; bz *= b;
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float sign = cx * nx + cy * ny + cz * nz;
        if (!Float.isFinite(sign) || sign < 1e-4F) return false;
        xx = root.toX(ax, ay, az); xy = root.toX(bx, by, bz); xz = root.toX(nx, ny, nz);
        yx = root.toY(ax, ay, az); yy = root.toY(bx, by, bz); yz = root.toY(nx, ny, nz);
        zx = root.toZ(ax, ay, az); zy = root.toZ(bx, by, bz); zz = root.toZ(nx, ny, nz);
        return true;
    }

    private static float inverseLength(float x, float y, float z) {
        float q = x * x + y * y + z * z;
        return Float.isFinite(q) && q >= 1e-8F ? 1 / (float) Math.sqrt(q) : 0;
    }

    /** Change of coordinates between orientations at consecutive simulation ticks. */
    void fromPrevious(CapeFrame now, CapeFrame old) {
        xx = now.localX(old.xx, old.yx, old.zx); xy = now.localX(old.xy, old.yy, old.zy); xz = now.localX(old.xz, old.yz, old.zz);
        yx = now.localY(old.xx, old.yx, old.zx); yy = now.localY(old.xy, old.yy, old.zy); yz = now.localY(old.xz, old.yz, old.zz);
        zx = now.localZ(old.xx, old.yx, old.zx); zy = now.localZ(old.xy, old.yy, old.zy); zz = now.localZ(old.xz, old.yz, old.zz);
    }

    public float toX(float x, float y, float z) { return xx * x + xy * y + xz * z; }
    public float toY(float x, float y, float z) { return yx * x + yy * y + yz * z; }
    public float toZ(float x, float y, float z) { return zx * x + zy * y + zz * z; }

    public float localX(float x, float y, float z) { return xx * x + yx * y + zx * z; }
    public float localY(float x, float y, float z) { return xy * x + yy * y + zy * z; }
    public float localZ(float x, float y, float z) { return xz * x + yz * y + zz * z; }
}
