package io.github.windwovennh.cloth;

/** Rear-facing proxies in cape coordinates (Y down, Z back from the cloak call's origin). */
final class BodyContact {
    // The vanilla cloak call translates two model pixels back from the biped's yaw axis.
    static final float CLOAK_ORIGIN_REAR = .125F;
    // Standard biped helmet volume, including clearance for head yaw and a small seam margin.
    static final float HEAD_HALF_WIDTH = .455F;
    static final float HEAD_REAR = .333F;
    static final float HEAD_TOP = -.585F;
    // The collar owns the narrow band at the head/neck attachment. Its pins are never projected.
    static final float HEAD_BOTTOM = 0;
    static final float HEAD_CROUCH = .023F;

    private BodyContact() {}

    static void projectHead(float[] v, int i, float top, float bottom, int parent, float span) {
        float down = v[i + 1], rear = v[i + 2];
        if (down <= top || down >= bottom || rear >= HEAD_REAR || Math.abs(v[i]) >= HEAD_HALF_WIDTH) return;
        // Keep the contact face reachable from the warp parent, especially just below the pins.
        // Otherwise alternating contact and extension can keep throwing a short collar link over the rim.
        float py = v[parent + 1], pz = v[parent + 2], px = v[parent];
        float below = bottom - py <= span ? bottom - down : Float.POSITIVE_INFINITY;
        float above = py - top <= span ? down - top : Float.POSITIVE_INFINITY;
        float behind = HEAD_REAR - pz <= span ? HEAD_REAR - rear : Float.POSITIVE_INFINITY;
        int axis;
        float plane;
        if (Math.abs(px) >= HEAD_HALF_WIDTH) {
            // A panel already outside the side of the head can keep passing around that side.
            axis = 0;
            plane = Math.copySign(HEAD_HALF_WIDTH, px);
        } else if (below <= behind && below <= above) {
            axis = 1; plane = bottom;
        } else if (above <= behind) {
            axis = 1; plane = top;
        } else {
            axis = 2; plane = HEAD_REAR;
        }
        float anchor = v[parent + axis];
        float along = Math.max(-span, Math.min(span, plane - anchor));
        v[i + axis] = along == plane - anchor ? plane : anchor + along;
        int a = (axis + 1) % 3, b = (axis + 2) % 3;
        float da = v[i + a] - v[parent + a], db = v[i + b] - v[parent + b];
        float tangent = da * da + db * db;
        float limit = Math.max(0, span * span - along * along);
        if (tangent > limit) {
            float ratio = (float) Math.sqrt(limit / tangent);
            v[i + a] = v[parent + a] + ratio * da;
            v[i + b] = v[parent + b] + ratio * db;
        }
    }

    /** Vertical free stream recovers gradually outside the near-body wake. */
    static float wakeOpening(float gap) {
        float t = Math.max(0, Math.min(1, (gap - .18F) * (1 / .49F)));
        return t * t * (3 - 2 * t);
    }
}
