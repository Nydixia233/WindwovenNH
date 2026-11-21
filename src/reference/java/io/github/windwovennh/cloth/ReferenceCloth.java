package io.github.windwovennh.cloth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Offline, double-precision cloth: area-lumped mass, compliant fiber constraints, bend energy and face drag. */
final class ReferenceCloth {
    static final double GRAVITY = 31.36;
    private static final double WIDTH = .625, MASS_PER_AREA = .16;
    private static final double WARP = 680, WEFT = 620, SHEAR = 180, BENDING = .006;
    private static final double AIR_NORMAL = .25, AIR_TANGENT = .018;
    // The fraction of vertical free-stream velocity reaching the sheet outside the near-body wake.
    private static final double OPEN_VERTICAL = .39;
    private final int width, rows, n, substeps, iterations;
    private final double sx, sy, dt, dt2;
    private final double[] x, y, z, oldX, oldY, oldZ, fx, fy, fz, inv;
    private final int[] face;
    private final Link[] links;
    private final Bend[] bends;
    private final int seed;
    private double bx, by, bz, yaw, shoulder, squash, seconds;

    ReferenceCloth(int width, int rows, int substeps, int iterations, int seed) {
        this.width = width;
        this.rows = rows;
        this.substeps = substeps;
        this.iterations = iterations;
        this.seed = seed;
        this.n = width * rows;
        sx = WIDTH / (width - 1);
        sy = 1.0 / (rows - 1);
        dt = .05 / substeps;
        dt2 = dt * dt;
        x = new double[n]; y = new double[n]; z = new double[n];
        oldX = new double[n]; oldY = new double[n]; oldZ = new double[n];
        fx = new double[n]; fy = new double[n]; fz = new double[n]; inv = new double[n];
        face = new int[(width - 1) * (rows - 1) * 6];
        int f = 0;
        for (int r = 0; r < rows - 1; r++) for (int c = 0; c < width - 1; c++) {
            int a = r * width + c, b = a + 1, d = a + width, e = d + 1;
            if (((r + c) & 1) == 0) { face[f++] = a; face[f++] = b; face[f++] = e; face[f++] = a; face[f++] = e; face[f++] = d; }
            else { face[f++] = a; face[f++] = b; face[f++] = d; face[f++] = b; face[f++] = e; face[f++] = d; }
        }
        for (int site : face) inv[site] += MASS_PER_AREA * sx * sy / 6;
        for (int i = width; i < n; i++) inv[i] = 1 / inv[i];
        Arrays.fill(inv, 0, width, 0);
        List<Link> l = new ArrayList<>(); List<Bend> b = new ArrayList<>();
        double diag = Math.hypot(sx, sy);
        for (int r = 0; r < rows; r++) for (int c = 0; c < width; c++) {
            int a = r * width + c;
            if (r > 0 && c < width - 1) l.add(new Link(a, a + 1, sx, WEFT * sy / sx));
            if (r < rows - 1) {
                l.add(new Link(a, a + width, sy, WARP * sx / sy));
                if (c < width - 1) l.add(new Link(a, a + width + 1, diag, SHEAR * sx * sy / (diag * diag)));
                if (c > 0) l.add(new Link(a, a + width - 1, diag, SHEAR * sx * sy / (diag * diag)));
            }
            if (r < rows - 2) b.add(new Bend(a, a + width, a + 2 * width, BENDING * sx / (sy * sy * sy)));
            if (r > 0 && c < width - 2) b.add(new Bend(a, a + 1, a + 2, BENDING * sy / (sx * sx * sx)));
        }
        links = l.toArray(new Link[0]); bends = b.toArray(new Bend[0]);
    }

    void initialize(MotionCases.Input input) {
        bx = by = bz = yaw = seconds = 0;
        shoulder = clearance(input); squash = input.crouch ? 1 : 0;
        for (int i = 0; i < n; i++) {
            double v = (i / width) * sy;
            x[i] = oldX[i] = (i % width) * sx - WIDTH * .5;
            y[i] = oldY[i] = v;
            z[i] = oldZ[i] = BodyContact.CLOAK_ORIGIN_REAR + back(v) + v * (.035 + .029 * v);
        }
    }

    void tick(MotionCases.Input in) {
        double dx = in.dx / substeps, dy = -in.up / substeps, dz = -in.dz / substeps;
        double targetYaw = Math.toRadians(in.yaw), targetShoulder = clearance(in), targetSquash = in.crouch ? 1 : 0;
        double da = (targetYaw - yaw) / substeps, ds = (targetShoulder - shoulder) / substeps;
        double dp = (targetSquash - squash) / substeps;
        double medium = in.wet ? .36 : 1, g = in.wet ? 12 : GRAVITY;
        double damp = Math.exp(-(in.wet ? 15 : 1.7) * dt);
        double bodySpeed = Math.sqrt(in.dx * in.dx + in.up * in.up + in.dz * in.dz) * 20;
        double flutter = Math.min(1.4, Math.max(0, bodySpeed - 3.0) * .055) * medium;
        for (int sub = 0; sub < substeps; sub++) {
            double ca = Math.cos(yaw), sa = Math.sin(yaw);
            // Small one-way body wake: slightly disturbed/deflected air at the back, no fluid solver.
            double gust = flutter * (.64 * Math.sin(seconds * 13 + seed * .31) + .36 * Math.sin(seconds * 23.7));
            double wake = Math.max(0, -in.up * 20) * .105 * (in.wet ? .65 : 1) + .12;
            double wx = ca * gust + sa * wake;
            // The back partly shelters cloth from vertical flow; surrounding air is not forced to follow the player.
            double wy = .31 * flutter * Math.cos(seconds * 17 + seed * .17);
            double wz = -sa * gust + ca * wake;
            accumulateAir(wx, wy, wz, medium, -20 * in.up, ca, sa);
            bx += dx; by += dy; bz += dz;
            yaw += da; shoulder += ds; squash += dp; seconds += dt;
            ca = Math.cos(yaw); sa = Math.sin(yaw);
            for (int i = 0; i < width; i++) {
                double a = i * sx - WIDTH * .5;
                oldX[i] = x[i]; oldY[i] = y[i]; oldZ[i] = z[i];
                double rear = BodyContact.CLOAK_ORIGIN_REAR + shoulder;
                x[i] = bx + ca * a + sa * rear; y[i] = by; z[i] = bz - sa * a + ca * rear;
            }
            for (int i = width; i < n; i++) {
                double a = x[i], b = y[i], c = z[i];
                // Damping represents coupling to the moving attachment, not drag against an absolute frame.
                // Air drag was already integrated separately above.
                x[i] = a + dx + (a - oldX[i] - dx) * damp + dt * fx[i] * inv[i];
                y[i] = b + dy + (b - oldY[i] - dy) * damp + dt * fy[i] * inv[i] + g * dt2;
                z[i] = c + dz + (c - oldZ[i] - dz) * damp + dt * fz[i] * inv[i];
                oldX[i] = a; oldY[i] = b; oldZ[i] = c;
            }
            for (Link s : links) s.lambda = 0;
            for (Bend s : bends) s.lx = s.ly = s.lz = 0;
            for (int pass = 0; pass < iterations; pass++) {
                if ((pass & 1) == 0) {
                    for (Link s : links) s.apply();
                    for (Bend s : bends) s.apply();
                } else {
                    for (int i = links.length - 1; i >= 0; i--) links[i].apply();
                    for (int i = bends.length - 1; i >= 0; i--) bends[i].apply();
                }
                constrain(ca, sa);
            }
        }
    }

    private void accumulateAir(double wx, double wy, double wz, double medium, double bodyVertical, double ca, double sa) {
        Arrays.fill(fx, 0); Arrays.fill(fy, 0); Arrays.fill(fz, 0);
        final double speedScale = 1 / (3 * dt);
        for (int p = 0; p < face.length; p += 3) {
            int a = face[p], b = face[p + 1], c = face[p + 2];
            double ax = x[b] - x[a], ay = y[b] - y[a], az = z[b] - z[a];
            double bx1 = x[c] - x[a], by1 = y[c] - y[a], bz1 = z[c] - z[a];
            double nx = ay * bz1 - az * by1, ny = az * bx1 - ax * bz1, nz = ax * by1 - ay * bx1;
            double twiceArea = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (twiceArea < 1e-12) continue;
            nx /= twiceArea; ny /= twiceArea; nz /= twiceArea;
            double u = wx - ((x[a] - oldX[a]) + (x[b] - oldX[b]) + (x[c] - oldX[c])) * speedScale;
            double down = (y[a] + y[b] + y[c]) / 3 - by;
            double exposure = .45 + .55 * unit(down / .78);
            double rear = sa * ((x[a] + x[b] + x[c]) / 3 - bx) + ca * ((z[a] + z[b] + z[c]) / 3 - bz);
            rear -= BodyContact.CLOAK_ORIGIN_REAR;
            double open = medium < 1 ? 0 : unit((rear - back(down) - .18) / .49);
            open *= open * (3 - 2 * open);
            // Near the torso, air partly moves with the body; outside its wake, more of the incoming
            // flow can strike the face of a billowed panel. No target lift angle or down-speed switch.
            double verticalShare = .18 * exposure + (OPEN_VERTICAL - .18 * exposure) * open;
            double v = wy + bodyVertical * (1 - verticalShare) - ((y[a] - oldY[a]) + (y[b] - oldY[b]) + (y[c] - oldY[c])) * speedScale;
            double w = wz - ((z[a] - oldZ[a]) + (z[b] - oldZ[b]) + (z[c] - oldZ[c])) * speedScale;
            double normal = u * nx + v * ny + w * nz;
            u -= normal * nx; v -= normal * ny; w -= normal * nz;
            double tangent = Math.sqrt(u * u + v * v + w * w);
            // Impulse of bounded quadratic pressure; normal sign is invariant under opposite triangle winding.
            double kn = AIR_NORMAL * medium * Math.abs(normal), kt = AIR_TANGENT * medium * tangent;
            double scale = twiceArea * dt / 6;
            double fn = scale * kn * normal / (1 + dt * kn / MASS_PER_AREA);
            double ft = scale * kt / (1 + dt * kt / MASS_PER_AREA);
            double xx = nx * fn + u * ft, yy = ny * fn + v * ft, zz = nz * fn + w * ft;
            fx[a] += xx; fy[a] += yy; fz[a] += zz;
            fx[b] += xx; fy[b] += yy; fz[b] += zz;
            fx[c] += xx; fy[c] += yy; fz[c] += zz;
        }
    }

    private void constrain(double c, double s) {
        double headTop = BodyContact.HEAD_TOP + BodyContact.HEAD_CROUCH * squash;
        double headBottom = BodyContact.HEAD_BOTTOM;
        for (int i = width; i < n; i++) {
            // One-sided long warp strain guard. Only the seam's own row is pinned.
            int pin = i % width;
            double dx = x[i] - x[pin], dy = y[i] - y[pin], dz = z[i] - z[pin];
            double max = (i / width) * sy * 1.06;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq > max * max) {
                double a = max / Math.sqrt(sq);
                x[i] = x[pin] + dx * a; y[i] = y[pin] + dy * a; z[i] = z[pin] + dz * a;
            }
            double down = y[i] - by;
            double rear = s * (x[i] - bx) + c * (z[i] - bz) - BodyContact.CLOAK_ORIGIN_REAR;
            double gap = back(down) - rear;
            if (gap > 0) { x[i] += s * gap; z[i] += c * gap; rear += gap; }
            if (down <= headTop || down >= headBottom || rear >= BodyContact.HEAD_REAR) continue;
            double side = c * (x[i] - bx) - s * (z[i] - bz);
            if (Math.abs(side) >= BodyContact.HEAD_HALF_WIDTH) continue;
            double below = headBottom - down, above = down - headTop, behind = BodyContact.HEAD_REAR - rear;
            // Project to the closer of the rear face and the top/bottom edge, not to a global height cap.
            if (below <= behind && below <= above) y[i] = by + headBottom;
            else if (above <= behind) y[i] = by + headTop;
            else { x[i] += s * behind; z[i] += c * behind; }
        }
    }

    private static double unit(double a) { return Math.max(0, Math.min(1, a)); }

    private double back(double down) { return shoulder + squash * .48 * Math.max(0, Math.min(.71, down)); }
    private static double clearance(MotionCases.Input in) { return (in.armor ? .096 : .021) + (in.crouch ? .025 : 0); }

    float[] sample(int columns, int height) {
        if ((width - 1) % (columns - 1) != 0 || (rows - 1) % (height - 1) != 0) throw new IllegalArgumentException("non-matching reference topology");
        float[] result = new float[3 * columns * height];
        double c = Math.cos(yaw), s = Math.sin(yaw);
        int a = 0;
        for (int r = 0; r < height; r++) for (int j = 0; j < columns; j++) {
            int i = r * (rows - 1) / (height - 1) * width + j * (width - 1) / (columns - 1);
            double dx = x[i] - bx, dz = z[i] - bz;
            result[a++] = (float)(c * dx - s * dz); result[a++] = (float)(y[i] - by); result[a++] = (float)(s * dx + c * dz - BodyContact.CLOAK_ORIGIN_REAR);
        }
        return result;
    }

    private final class Link {
        final int a, b;
        final double rest, alpha, reciprocal;
        double lambda;
        Link(int a, int b, double rest, double k) {
            this.a = a; this.b = b; this.rest = rest;
            alpha = 1 / (dt2 * k); reciprocal = 1 / (inv[a] + inv[b] + alpha);
        }
        void apply() {
            double dx = x[b] - x[a], dy = y[b] - y[a], dz = z[b] - z[a];
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-9) return;
            double change = (-len + rest - alpha * lambda) * reciprocal;
            lambda += change;
            double t = change / len, u = t * inv[a], v = t * inv[b];
            x[a] -= u * dx; y[a] -= u * dy; z[a] -= u * dz;
            x[b] += v * dx; y[b] += v * dy; z[b] += v * dz;
        }
    }

    private final class Bend {
        final int a, b, c;
        final double alpha, reciprocal;
        double lx, ly, lz;
        Bend(int a, int b, int c, double k) {
            this.a = a; this.b = b; this.c = c;
            alpha = 1 / (dt2 * k); reciprocal = 1 / (inv[a] + 4 * inv[b] + inv[c] + alpha);
        }
        void apply() {
            double u = (-x[a] + 2 * x[b] - x[c] - alpha * lx) * reciprocal;
            double v = (-y[a] + 2 * y[b] - y[c] - alpha * ly) * reciprocal;
            double w = (-z[a] + 2 * z[b] - z[c] - alpha * lz) * reciprocal;
            lx += u; ly += v; lz += w;
            double m = inv[a], q = -2 * inv[b], r = inv[c];
            x[a] += m * u; y[a] += m * v; z[a] += m * w;
            x[b] += q * u; y[b] += q * v; z[b] += q * w;
            x[c] += r * u; y[c] += r * v; z[c] += r * w;
        }
    }
}
