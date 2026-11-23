package io.github.windwovennh.cloth;

/**
 * A small rectangular cloth: lateral X, down Y, and behind the back Z, in blocks.
 * Verlet prediction followed by equal-mass position projection; the shoulder seam has infinite mass.
 * Grid topology is immutable and shared. Only coordinates and time history belong to each player.
 */
public final class WovenCloth {
    public static final float WIDTH = 0.625F;
    public static final int CAPACITY = 65;
    private static final float H = 0.025F;
    private static final float H2 = H * H;
    private static final float INVERSE_H = 1 / H;
    private static final Weave HIGH = new Weave(Density.DETAIL, AeroPreset.DETAIL);
    private static final Weave LOW = new Weave(Density.ECONOMY, AeroPreset.ECONOMY);

    private final float[] position = new float[CAPACITY * 3];
    private final float[] verlet = new float[CAPACITY * 3];
    private final float[] lastTick = new float[CAPACITY * 3];
    private final int seed;
    private final Weave fitWeave;
    private Weave weave;
    private float shoulder;
    private float squat;
    private float aX, aY, aZ; // filtered player-relative air in the current body frame
    private float hemWindX, hemWindY, hemWindZ;
    private double time;
    private float gustX, gustY;
    private CapeFrame lastPose, relativePose;
    private boolean posed;

    public WovenCloth() {
        this(0);
    }

    public WovenCloth(int seed) {
        this(seed, null);
    }

    // Only the standalone fitter supplies a custom weave; production shares the two baked topologies.
    WovenCloth(int seed, Weave fitted) {
        this.seed = seed;
        this.fitWeave = fitted;
    }

    public Density density() {
        return weave.density;
    }

    public void initialize(Density density, Flow flow) {
        weave = geometry(density);
        aX = aY = aZ = hemWindX = hemWindY = hemWindZ = gustX = gustY = 0;
        time = 0;
        posed = flow.frame != null;
        if (posed) {
            ensurePose();
            lastPose.copy(flow.frame);
        }
        shoulder = flow.shoulder;
        squat = flow.squat;
        int width = density.columns;
        float heightStep = 1.0F / (density.rows - 1);
        for (int site = 0; site < density.sites; site++) {
            int k = site * 3;
            float down = site / width * heightStep;
            position[k] = weave.restX[site % width];
            position[k + 1] = down * (1.0F - 0.031F * squat);
            position[k + 2] = contact(position[k + 1]) + down * (0.046F + 0.024F * down);
        }
        int n = density.sites * 3;
        System.arraycopy(position, 0, verlet, 0, n);
        System.arraycopy(position, 0, lastTick, 0, n);
    }

    /** Resample both time histories on a shared caller-owned workspace; preserve motion on LOD changes. */
    public void changeDensity(Density density, float[] work) {
        if (density == weave.density) return;
        Density before = weave.density;
        transfer(before, density, position, work);
        transfer(before, density, verlet, work);
        transfer(before, density, lastTick, work);
        weave = geometry(density);
        projectContacts(position);
        projectContacts(lastTick);
    }

    private static void transfer(Density from, Density to, float[] data, float[] scratch) {
        for (int r = 0; r < to.rows; r++) {
            float y = (float) r * (from.rows - 1) / (to.rows - 1);
            int i = Math.min((int) y, from.rows - 2);
            float ty = y - i;
            for (int c = 0; c < to.columns; c++) {
                float x = (float) c * (from.columns - 1) / (to.columns - 1);
                int j = Math.min((int) x, from.columns - 2);
                float tx = x - j;
                int start = (i * from.columns + j) * 3;
                int target = (r * to.columns + c) * 3;
                for (int axis = 0; axis < 3; axis++) {
                    int p = start + axis;
                    int q = p + from.columns * 3;
                    scratch[target + axis] = mix(mix(data[p], data[p + 3], tx),
                        mix(data[q], data[q + 3], tx), ty);
                }
            }
        }
        System.arraycopy(scratch, 0, data, 0, to.sites * 3);
    }

    /** One simulation tick, with independent render interpolation history. */
    public void advance(Flow flow) {
        Density d = weave.density;
        // Move all histories together when the shoulder clearance changes (e.g. putting armor on).
        float slide = flow.shoulder - shoulder;
        if (slide != 0) {
            for (int k = 2; k < d.sites * 3; k += 3) {
                position[k] += slide;
                verlet[k] += slide;
            }
        }
        shoulder = flow.shoulder;
        squat = flow.squat;
        contactAll();
        System.arraycopy(position, 0, lastTick, 0, d.sites * 3);
        carryYaw(flow);
        carryPose(flow.frame);
        AeroPreset model = weave.air;
        float dx = (flow.airX - aX) * model.follow;
        float dy = (flow.airY - aY) * model.follow;
        float dz = (flow.airZ - aZ) * model.follow;
        aX += dx; aY += dy; aZ += dz;
        // The free end samples the slower wake, instead of following a one-tick reversal of the incoming wind.
        hemWindX = mix(hemWindX, aX, .26F);
        hemWindY = mix(hemWindY, aY, .30F);
        hemWindZ = mix(hemWindZ, aZ, .30F);
        float kick = 20 * model.inertia * (flow.submerged ? .45F : 1);
        // A rapid change of attachment speed can unload the sheet; don't amplify gravity in Y.
        float verticalKick = 20 * Math.min(1, model.inertia) * (flow.submerged ? .45F : 1);
        float inertiaX = capped(dx * kick, 27), inertiaY = capped(dy * verticalKick, 62), inertiaZ = capped(dz * kick, 35);
        flowGust(flow.submerged);
        for (int sub = 0; sub < 2; sub++) {
            predict(flow.submerged, inertiaX, inertiaY, inertiaZ, flow.frame);
            for (int pass = 0; pass < 7; pass++) {
                // Alternate directions so the two selvages do not acquire opposite numerical biases.
                if ((pass & 1) == 0) {
                    for (Seam seam : weave.seams) project(seam);
                } else {
                    for (int i = weave.seams.length - 1; i >= 0; i--) project(weave.seams[i]);
                }
                contactAll();
            }
            for (int trim = 0; trim < 3; trim++) {
                limitExtension();
                limitWidth();
                contactAll();
            }
        }
    }

    private void ensurePose() {
        if (lastPose == null) {
            lastPose = new CapeFrame();
            relativePose = new CapeFrame();
        }
    }

    private void carryPose(CapeFrame next) {
        if (next == null && !posed) return;
        if (next != null && posed && !next.same(lastPose)) {
            relativePose.fromPrevious(next, lastPose);
            Density d = weave.density;
            for (int r = 1; r < d.rows; r++) {
                float weight = weave.fractionDown[r];
                for (int c = 0; c < d.columns; c++) {
                    int i = (r * d.columns + c) * 3;
                    float pivotX = weave.restX[c];
                    carryPoseNode(position, i, pivotX, weight);
                    carryPoseNode(verlet, i, pivotX, weight);
                }
            }
        }
        if (next != null) {
            ensurePose();
            lastPose.copy(next);
        }
        // Changing renderer is not a physical body rotation.
        posed = next != null;
    }

    private void carryPoseNode(float[] v, int i, float pivotX, float weight) {
        float x = v[i] - pivotX, y = v[i + 1], z = v[i + 2] - shoulder;
        v[i] += (relativePose.toX(x, y, z) - x) * weight;
        v[i + 1] += (relativePose.toY(x, y, z) - y) * weight;
        v[i + 2] += (relativePose.toZ(x, y, z) - z) * weight;
    }

    private void carryYaw(Flow flow) {
        float turn = flow.yawStep;
        if (turn == 0) return;
        float sin = (float) Math.sin(turn), cos = (float) Math.cos(turn);
        float oldX = aX;
        aX = cos * oldX - sin * aZ;
        aZ = sin * oldX + cos * aZ;
        oldX = hemWindX;
        hemWindX = cos * oldX - sin * hemWindZ;
        hemWindZ = sin * oldX + cos * hemWindZ;
        // At low travel speed the pins orbit the torso, even when the hem is hanging straight down.
        // Fade back to the already calibrated turn transport as translation becomes dominant.
        float travel2 = Math.max(aX * aX + aY * aY + aZ * aZ,
            flow.airX * flow.airX + flow.airY * flow.airY + flow.airZ * flow.airZ);
        float bodyPivot = Math.max(0, 1 - travel2 * (1F / 16));
        bodyPivot *= bodyPivot;
        float quietYaw = weave.density == Density.DETAIL ? 1.02F : .72F;
        float limit = mix(.42F, .32F, bodyPivot);
        float retained = capped(mix(weave.air.yaw, quietYaw, bodyPivot) * turn, limit);
        if (flow.submerged) retained *= mix(.42F, .29F, bodyPivot);
        carryTurn((float) Math.sin(retained), (float) Math.cos(retained) - 1.0F, bodyPivot);
    }

    private void flowGust(boolean submerged) {
        // Ambient disturbance is a velocity in the wake, not a prescribed animation or a vertex offset.
        time += .05;
        float speedSquared = aX * aX + aY * aY + aZ * aZ;
        if (speedSquared < 9) { gustX = gustY = 0; return; }
        float strength = Math.min(1.4F, ((float) Math.sqrt(speedSquared) - 3) * .055F) * (submerged ? .36F : 1);
        double phase = (seed & 0xffff) * .31;
        gustX = strength * (float) (.64 * Math.sin(time * 13 + phase) + .36 * Math.sin(time * 23.7));
        gustY = .31F * strength * (float) Math.cos(time * 17 + (seed & 0xffff) * .17);
    }

    private void carryTurn(float sin, float cosMinusOne, float bodyPivot) {
        Density d = weave.density;
        float anchorShare = 1 - bodyPivot;
        float pivotZ = mix(shoulder, -BodyContact.CLOAK_ORIGIN_REAR, bodyPivot);
        int i = d.columns * 3;
        for (int r = 1; r < d.rows; r++) {
            float drop = weave.fractionDown[r];
            float s = sin * drop, c = 1.0F + cosMinusOne * drop;
            for (int j = 0; j < d.columns; j++, i += 3) {
                float pivotX = anchorShare * weave.restX[j];
                // Rotate both histories; don't invent a velocity pulse or move the pinned row.
                rotateNode(position, i, pivotX, pivotZ, s, c);
                rotateNode(verlet, i, pivotX, pivotZ, s, c);
            }
        }
    }

    private static void rotateNode(float[] data, int i, float px, float pz, float sin, float cos) {
        float x = data[i] - px, z = data[i + 2] - pz;
        data[i] = px + cos * x - sin * z;
        data[i + 2] = pz + sin * x + cos * z;
    }

    private void predict(boolean submerged, float inertiaX, float inertiaY, float inertiaZ, CapeFrame pose) {
        Density d = weave.density;
        AeroPreset model = weave.air;
        int stride = d.columns * 3, across = stride - 3;
        float medium = submerged ? .36F : 1;
        float normalDrag = model.normal * medium;
        float tangentDrag = model.tangent * medium;
        float retention = submerged ? weave.wetRetention : weave.retention;
        float gravity = submerged ? 12.0F : 31.36F;
        float gravityX = pose == null ? 0 : pose.localX(0, gravity, 0);
        float gravityY = pose == null ? gravity : pose.localY(0, gravity, 0);
        float gravityZ = pose == null ? 0 : pose.localZ(0, gravity, 0);
        float shieldedWind = capped(model.verticalAir * aY, 4.4F);
        float openWind = capped(model.openAir * hemWindY, 21);
        float wake = .12F + model.wake * (submerged ? .65F : 1) * Math.min(24, Math.max(-aY, 0));
        for (int row = 1; row < d.rows; row++) {
            float drop = weave.fractionDown[row];
            float rx = mix(aX, hemWindX, drop) + gustX;
            float rz = mix(aZ, hemWindZ, drop * .68F) + wake;
            int start = row * stride;
            int previous = start - stride;
            int last = start + across;
            int up = previous + (d.columns / 2) * 3;
            int center = up + stride;
            int down = row + 1 == d.rows ? center : center + stride;
            float downAtCenter = position[center + 1];
            float exposure = .45F + .55F * Math.max(0, Math.min(1, downAtCenter * (1F / .78F)));
            // The open-air wake is not a water model; retain the sheltered, damped response there.
            float opening = submerged ? 0 : BodyContact.wakeOpening(position[center + 2] - contact(downAtCenter));
            float ry = mix(shieldedWind * exposure, openWind, opening) + gustY;
            // The flow history is in upright yaw space; move its drag input into the posed torso frame.
            if (pose != null) {
                rz -= wake;
                float x = pose.localX(rx, ry, rz), y = pose.localY(rx, ry, rz);
                rz = pose.localZ(rx, ry, rz) + wake;
                rx = x; ry = y;
            }
            // The average spanning direction and centerline tangent give one pressure patch per row.
            float sx = position[last] - position[start], sy = position[last + 1] - position[start + 1];
            float sz = position[last + 2] - position[start + 2];
            float tx = position[down] - position[up], ty = position[down + 1] - position[up + 1];
            float tz = position[down + 2] - position[up + 2];
            float nx = sy * tz - sz * ty, ny = sz * tx - sx * tz, nz = sx * ty - sy * tx;
            float sq = nx * nx + ny * ny + nz * nz;
            if (sq > 1.0E-8F) {
                float f = 1 / (float) Math.sqrt(sq);
                nx *= f; ny *= f; nz *= f;
            } else { nx = ny = 0; nz = 1; }
            float normalWind = rx * nx + ry * ny + rz * nz;
            float tx0 = rx - normalWind * nx, ty0 = ry - normalWind * ny, tz0 = rz - normalWind * nz;
            // Share tangent speed across the row, but damp each node relative to its own velocity.
            float kt = tangentDrag * (float) Math.sqrt(tx0 * tx0 + ty0 * ty0 + tz0 * tz0);
            float tangent = H2 * kt / (1 + H * kt);
            float ax0, ay0, az0;
            if (pose == null) {
                ax0 = H2 * inertiaX * (1 - .65F * drop);
                ay0 = H2 * (gravity + inertiaY);
                az0 = H2 * inertiaZ;
            } else {
                float lateral = inertiaX * (1 - .65F * drop);
                ax0 = H2 * (gravityX + pose.localX(lateral, inertiaY, inertiaZ));
                ay0 = H2 * (gravityY + pose.localY(lateral, inertiaY, inertiaZ));
                az0 = H2 * (gravityZ + pose.localZ(lateral, inertiaY, inertiaZ));
            }
            for (int i = start; i < start + stride; i += 3) {
                float x = position[i], y = position[i + 1], z = position[i + 2];
                float vx = x - verlet[i], vy = y - verlet[i + 1], vz = z - verlet[i + 2];
                float vn = capped(normalWind - (vx * nx + vy * ny + vz * nz) * INVERSE_H, 15);
                float kd = normalDrag * Math.abs(vn);
                float n = H2 * kd * vn / (1 + H * kd) - tangent * vn;
                position[i] = x + vx * retention + ax0 + nx * n + tangent * (rx - vx * INVERSE_H);
                position[i + 1] = y + vy * retention + ay0 + ny * n + tangent * (ry - vy * INVERSE_H);
                position[i + 2] = z + vz * retention + az0 + nz * n + tangent * (rz - vz * INVERSE_H);
                verlet[i] = x; verlet[i + 1] = y; verlet[i + 2] = z;
            }
        }
    }

    private static float capped(float value, float max) { return Math.max(-max, Math.min(max, value)); }

    private void project(Seam seam) {
        int a = seam.a, b = seam.b;
        float x = position[b] - position[a];
        float y = position[b + 1] - position[a + 1];
        float z = position[b + 2] - position[a + 2];
        float sq = x * x + y * y + z * z;
        if (sq < 1.0E-10F) return;
        float correction = (1.0F - seam.rest / (float) Math.sqrt(sq)) * seam.relaxation;
        x *= correction;
        y *= correction;
        z *= correction;
        if (!seam.pinned) {
            position[a] += x;
            position[a + 1] += y;
            position[a + 2] += z;
        }
        position[b] -= x;
        position[b + 1] -= y;
        position[b + 2] -= z;
    }

    // A one-sided strain limiter stops violent accelerations from lengthening the cloth.
    // Work from the shoulders down so a corrected node cannot drag its pinned parent again.
    private void limitExtension() {
        Density d = weave.density;
        int stride = d.columns * 3;
        float length = 1.18F / (d.rows - 1);
        float limit = length * length;
        for (int b = stride; b < d.sites * 3; b += 3) {
            int a = b - stride;
            float x = position[b] - position[a], y = position[b + 1] - position[a + 1];
            float z = position[b + 2] - position[a + 2];
            float stretched = x * x + y * y + z * z;
            if (stretched > limit) {
                float t = length / (float) Math.sqrt(stretched);
                position[b] = position[a] + t * x;
                position[b + 1] = position[a + 1] + t * y;
                position[b + 2] = position[a + 2] + t * z;
            }
        }
    }

    private void limitWidth() {
        Density d = weave.density;
        int stride = d.columns * 3;
        float budget = WIDTH * 1.16F / (d.columns - 1), square = budget * budget;
        for (int row = stride; row < d.sites * 3; row += stride) {
            for (int j = row + 3; j < row + stride; j += 3) {
                int i = j - 3;
                float x = position[j] - position[i], y = position[j + 1] - position[i + 1], z = position[j + 2] - position[i + 2];
                float len = x * x + y * y + z * z;
                if (len > square) {
                    float t = budget / (float) Math.sqrt(len);
                    position[j] = position[i] + t * x;
                    position[j + 1] = position[i + 1] + t * y;
                    position[j + 2] = position[i + 2] + t * z;
                }
            }
        }
    }

    private float contact(float y) {
        // Conservative torso ramp for a crouching biped; deliberately no block/cloth self-collision.
        return shoulder + squat * (0.48F * Math.max(0, Math.min(0.71F, y)));
    }

    private void contactAll() { projectContacts(position); }

    private void projectContacts(float[] vertices) {
        int stride = weave.density.columns * 3, end = weave.density.sites * 3;
        float span = 1.18F / (weave.density.rows - 1);
        float top = BodyContact.HEAD_TOP + BodyContact.HEAD_CROUCH * squat;
        for (int i = stride; i < end; i += 3) {
            BodyContact.projectHead(vertices, i, top, BodyContact.HEAD_BOTTOM, i - stride, span);
            float minimum = contact(vertices[i + 1]);
            if (vertices[i + 2] < minimum) vertices[i + 2] = minimum;
        }
    }

    public boolean sample(float delta, float[] out) {
        if (!Float.isFinite(delta)) return false;
        float f = Math.max(0, Math.min(1, delta));
        int end = weave.density.sites * 3;
        for (int i = 0; i < end; i++) {
            float current = position[i];
            if (!Float.isFinite(current) || Math.abs(current) > 2.0F) return false;
            out[i] = mix(lastTick[i], current, f);
        }
        if (f > 0 && f < 1) projectContacts(out);
        return true;
    }

    private static float mix(float a, float b, float t) {
        return a + t * (b - a);
    }

    private Weave geometry(Density density) {
        if (fitWeave != null && fitWeave.density == density) return fitWeave;
        return density == Density.DETAIL ? HIGH : LOW;
    }

    static final class Weave {
        final Density density;
        final AeroPreset air;
        final float retention, wetRetention;
        final float[] restX, fractionDown;
        final Seam[] seams;

        Weave(Density d, AeroPreset model) {
            density = d;
            air = model;
            retention = (float) Math.exp(-H * model.damping);
            wetRetention = (float) Math.exp(-H * (model.damping + 13.3F));
            int w = d.columns, h = d.rows;
            restX = new float[w];
            fractionDown = new float[h];
            float sx = WIDTH / (w - 1), sy = 1.0F / (h - 1);
            float diagonal = (float) Math.hypot(sx, sy);
            for (int c = 0; c < w; c++) {
                restX[c] = sx * c - WIDTH * .5F;
            }
            for (int r = 0; r < h; r++) fractionDown[r] = sy * r;
            java.util.ArrayList<Seam> list = new java.util.ArrayList<>();
            for (int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) {
                    int i = r * w + c;
                    boolean pin = r == 0;
                    if (r + 1 < h) put(list, i, i + w, sy, 1.0F, pin);
                    if (r > 0 && c + 1 < w) put(list, i, i + 1, sx, 0.96F, false);
                    if (r + 1 < h && c + 1 < w) put(list, i, i + w + 1, diagonal, 0.70F, pin);
                    if (r + 1 < h && c > 0) put(list, i, i + w - 1, diagonal, 0.70F, pin);
                    if (r + 2 < h) put(list, i, i + 2 * w, 2 * sy, 0.19F * model.bend, pin);
                    if (r > 0 && c + 2 < w) put(list, i, i + 2, 2 * sx, 0.15F * model.bend, false);
                }
            }
            seams = list.toArray(new Seam[0]);
        }

        private static void put(java.util.List<Seam> list, int a, int b, float length, float stiffness,
            boolean pinned) {
            list.add(new Seam(3 * a, 3 * b, length, pinned ? stiffness : stiffness * 0.5F, pinned));
        }
    }

    private static final class Seam {
        final int a, b;
        final float rest, relaxation;
        final boolean pinned;

        Seam(int a, int b, float rest, float relaxation, boolean pinned) {
            this.a = a;
            this.b = b;
            this.rest = rest;
            this.relaxation = relaxation;
            this.pinned = pinned;
        }
    }
}
