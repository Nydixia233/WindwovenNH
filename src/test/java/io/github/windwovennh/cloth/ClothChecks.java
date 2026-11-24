package io.github.windwovennh.cloth;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ClothChecks {
    private static final float[] SAMPLED = new float[WovenCloth.CAPACITY * 3];

    public static void main(String[] args) {
        qualityBounds();
        localHeadContact();
        standAndRun(Density.DETAIL);
        standAndRun(Density.ECONOMY);
        bendingAndWater();
        for (Density density : Density.values()) {
            lateralReversal(density);
            movingTurn(density);
            stationaryTurn(density);
            jumpAndLand(density);
            tiltedTorso(density);
            ledgeFall(density);
            verticalFlight(density);
            flightWakeAndWindStrength(density);
            sustainedDive(density);
            obliqueShoulderLift(density);
            collarContinuity(density);
            posedCollarTransition(density);
            gustResponse(density);
        }
        regridAndReset();
        mesh(Density.DETAIL);
        mesh(Density.ECONOMY);
        allocation();
        System.out.println("PASS: local head/chest contact, lofted descent, independent edge curl, delayed reversal, stationary torso yaw, stop/turn/water, jump/landing, climb/descent/brake, seeded aerodynamic response, LOD histories, reset, closed mesh, UVs, winding and allocation");
    }

    private static void qualityBounds() {
        expect(Density.forDistance(576, false) == Density.DETAIL, "24 inclusive");
        expect(Density.forDistance(576.01, false) == Density.ECONOMY, "24+ blocks");
        expect(Density.forDistance(2304, false) == Density.ECONOMY, "48 inclusive");
        expect(Density.forDistance(2304.001, false) == null, "48+ uses vanilla");
        expect(Density.forDistance(9980, true) == Density.DETAIL, "FULL far mesh");
        expect(Density.forDistance(Double.NaN, true) == null, "no mesh for invalid camera position");
    }

    private static void localHeadContact() {
        float[] points = {0, 0, .096F, 0, -.29F, .21F, 0, -.015F, .13F, 0, -.23F, .65F,
            .57F, -.23F, .13F, 0, -.7F, .26F};
        float[] original = points.clone();
        for (int i = 3; i < points.length; i += 3) {
            BodyContact.projectHead(points, i, BodyContact.HEAD_TOP, BodyContact.HEAD_BOTTOM, 0, .9F);
        }
        close(points[5], BodyContact.HEAD_REAR, 1e-7F, "rear contact stops cloth inside the head proxy");
        close(points[7], BodyContact.lowerLip(points[8], BodyContact.HEAD_BOTTOM), 1e-7F, "under-head contact does not pull cloth through the head");
        for (int i = 9; i < points.length; i++) {
            close(points[i], original[i], 0, "head contact leaves clear air behind, beside and above it");
        }
        BodyContact.projectHead(original, 3, BodyContact.HEAD_TOP, BodyContact.HEAD_BOTTOM, 0, .098F);
        close(original[4], BodyContact.lowerLip(original[5], BodyContact.HEAD_BOTTOM), 1e-7F, "a short link near the collar cannot reach the back of the head");
        expect(Math.abs(original[5] - original[2]) <= .09801F, "head contact does not lengthen the collar link");
    }

    private static void tiltedTorso(Density density) {
        CapeFrame root = new CapeFrame(), pose = new CapeFrame();
        root.setRoot(70, 0, 0);
        expect(pose.fromBone(root, 1.3F, 0, 0, 0, 1.1F, 0, 0, 0, .7F), "valid scaled rig basis");
        float down = pose.localY(0, 1, 0), rear = pose.localZ(0, 1, 0);
        close(pose.toY(0, down, rear), 1, 1e-6F, "tilted world down round trip");
        close(pose.toZ(0, down, rear), 0, 1e-6F, "world down is not body forward");
        expect(!pose.fromBone(root, 1, 0, 0, 1, 0, 0, 0, 0, 1), "reject singular body transforms");
        expect(!pose.fromBone(root, 1, 0, 0, 0, 1, 0, 0, 0, -1), "reject mirrored body transforms");
        expect(!pose.fromBone(root, Float.NaN, 0, 0, 0, 1, 0, 0, 0, 1), "reject non-finite body transforms");
        root.setRoot(-70, 0, 0); // Nearly horizontal, with clear air in the direction of gravity.
        Flow wind = new Flow();
        wind.update(0, 0, 0, 0, 0, false, false, false);
        wind.pose(root);
        WovenCloth cloth = new WovenCloth();
        cloth.initialize(density, wind);
        for (int tick = 0; tick < 100; tick++) cloth.advance(wind);
        expect(cloth.sample(1, SAMPLED), "finite tilted drape");
        int hem = 3 * (density.sites - (density.columns + 1) / 2);
        float localDrop = SAMPLED[hem + 1], localRear = SAMPLED[hem + 2] - wind.shoulder;
        float worldDrop = root.toY(0, localDrop, localRear);
        expect(worldDrop > .8F && localRear > .65F,
            "hem follows downward gravity instead of turning rigidly with the body: " + worldDrop + ", " + localRear);

        // Sprint-jump into a near-horizontal forward flight, then reverse and level out.
        for (int tick = 0; tick < 125; tick++) {
            float pitch = tick < 55 ? -70 + tick * (148F / 54) : 78 - 78 * (tick - 55) / 69;
            root.setRoot(pitch, 12 * (float) Math.sin(tick * .09), 18 * (float) Math.sin(tick * .17));
            wind.update(.07, tick < 8 ? .20 : tick < 55 ? .02 : -.20, tick < 90 ? .42 : -.35,
                0, 0, false, false, true);
            wind.pose(root);
            cloth.advance(wind);
            expect(cloth.sample(.5F, SAMPLED), "finite interpolated frame through jump/flight/landing");
            for (int c = 0; c < density.columns; c++) {
                close(SAMPLED[c * 3 + 1], 0, 0, "tilt keeps the shoulder seam pinned");
                close(SAMPLED[c * 3 + 2], wind.shoulder, 1e-6F, "tilt keeps chestplate shoulder clearance");
            }
            for (int i = density.columns * 3; i < density.sites * 3; i += 3) {
                expect(SAMPLED[i + 2] >= wind.shoulder - 1e-6F, "tilt keeps cloth outside the torso");
                expect(distance(i, i - density.columns * 3) * (density.rows - 1) < 1.36,
                    "tilt preserves link lengths in " + density + " at tick " + tick);
            }
        }
        System.out.printf(Locale.ROOT, "%s tilted drape: world drop %.3f blocks at 70 degrees%n", density, worldDrop);
    }

    private static void standAndRun(Density d) {
        WovenCloth cloth = new WovenCloth();
        Flow air = new Flow();
        air.update(0, 0, 0, 0, 0, false, false, false);
        cloth.initialize(d, air);
        for (int step = 0; step < 900; step++) {
            boolean chest = step > 70;
            boolean crouch = step > 105 && step < 165;
            boolean water = step > 400 && step < 640;
            double dx = (step < 640 ? 0.34 : 0.67) * Math.sin(0.18 * step);
            double dy = step % 37 < 5 ? -0.47 : 0.14 * Math.cos(step * 0.4);
            double dz = 0.65 * Math.cos(0.2 * step);
            air.update(dx, dy, dz, (step * 3) % 360, (step % 32 < 16 ? 1 : -1) * 19, water, crouch, chest);
            cloth.advance(air);
            expect(cloth.sample(0.5F, SAMPLED), "finite interpolated result");
            expect(cloth.sample(1, SAMPLED), "finite solver step");
            for (int c = 0; c < d.columns; c++) {
                int i = 3 * c;
                close(SAMPLED[i], 0.625F * (c / (float) (d.columns - 1) - 0.5F), 1e-5F, "pinned X");
                close(SAMPLED[i + 1], 0, 1e-6F, "pinned Y");
                close(SAMPLED[i + 2], air.shoulder, 1e-6F, "pinned Z");
            }
            for (int r = 1; r < d.rows; r++) {
                for (int c = 0; c < d.columns; c++) {
                    int j = (r * d.columns + c) * 3;
                    float back = air.shoulder + (crouch ? 0.48F * Math.max(0, Math.min(.71F, SAMPLED[j + 1])) : 0);
                    expect(SAMPLED[j + 2] >= back - 1e-6, "standard torso plane");
                    expect(distance(j, j - d.columns * 3) * (d.rows - 1) < 1.30, "vertical stretch limit " + d + " step " + step + " row " + r + " col " + c + " ratio " + (distance(j, j - d.columns * 3) * (d.rows - 1)));
                    if (c > 0) expect(distance(j, j - 3) * (d.columns - 1) < 0.79, "horizontal stretch limit");
                }
            }
        }
    }

    private static void bendingAndWater() {
        Flow dry = new Flow(), water = new Flow();
        WovenCloth first = new WovenCloth(), second = new WovenCloth();
        dry.update(0, 0, 0, 0, 0, false, false, false);
        water.update(0, 0, 0, 0, 0, true, false, false);
        first.initialize(Density.DETAIL, dry);
        second.initialize(Density.DETAIL, water);
        for (int i = 0; i < 25; i++) { first.advance(dry); second.advance(water); }
        // Accelerate straight for eight ticks, then turn the body through 90 degrees as the trajectory catches up.
        for (int i = -7; i <= 8; i++) {
            float yaw = 15 * Math.max(0, Math.min(6, i));
            float yawStep = i > 0 && i <= 6 ? 15 : 0;
            double travel = Math.toRadians(15 * Math.max(0, Math.min(6, i - 2)));
            double x = -.65 * Math.sin(travel), z = .65 * Math.cos(travel);
            dry.update(x, 0, z, yaw, yawStep, false, false, false);
            water.update(x, 0, z, yaw, yawStep, true, false, false);
            first.advance(dry);
            second.advance(water);
        }
        first.sample(1, SAMPLED);
        float left = depth(12, 0) - 2 * depth(10, 0) + depth(8, 0);
        float right = depth(12, 4) - 2 * depth(10, 4) + depth(8, 4);
        float spread = Math.abs(depth(12, 0) - depth(12, 4));
        float dryExcursion = depth(12, 2);
        expect(spread > 0.08F && Math.abs(left - right) > 0.005F, "different curvature at each edge");
        second.sample(1, SAMPLED);
        expect(depth(12, 2) < dryExcursion * 1.20F, "underwater turn remains bounded");
        dry.update(0, 0, 0, 0, 0, false, false, false);
        first.advance(dry);
        first.sample(1, SAMPLED);
        expect(depth(12, 2) > 0.12F, "hem has momentum after a stop");
        for (int i = 0; i < 260; i++) first.advance(dry);
        first.sample(1, SAMPLED);
        expect(depth(12, 2) < dryExcursion * 0.5F, "drag settles the hem");
        System.out.printf(Locale.ROOT, "Independent selvages: %.4f block depth spread, curvature difference %.4f%n", spread,
            Math.abs(left - right));
    }

    private static void lateralReversal(Density d) {
        Flow air = new Flow();
        float[] previous = new float[SAMPLED.length];
        int upper = center(d, (d.rows - 1) / 3), hem = center(d, d.rows - 1);
        float maxJump = 0;
        int leastLag = Integer.MAX_VALUE;
        for (int seed : new int[] { 0, 4381, 329001, -2012382 }) {
            WovenCloth cloth = new WovenCloth(seed);
            air.update(0, 0, 0, 0, 0, false, false, true);
            cloth.initialize(d, air);
            for (int i = 0; i < 150; i++) cloth.advance(air);
            air.update(.70, 0, .40, 0, 0, false, false, true);
            for (int i = 0; i < 135; i++) cloth.advance(air);
            expect(cloth.sample(1, previous) && previous[upper] < 0 && previous[hem] < -.055F, "old side has developed a trailing pose");
            air.update(-.70, 0, .40, 0, 0, false, false, true);
            int upperTick = 0, hemTick = 0;
            for (int tick = 1; tick <= 25; tick++) {
                cloth.advance(air);
                verifyPose(cloth, air, false);
                if (upperTick == 0 && SAMPLED[upper] >= 0) upperTick = tick;
                if (hemTick == 0 && SAMPLED[hem] >= 0) hemTick = tick;
                for (int node = d.columns * 3; node < d.sites * 3; node += 3) {
                    maxJump = Math.max(maxJump, Math.abs(SAMPLED[node] - previous[node]));
                }
                System.arraycopy(SAMPLED, 0, previous, 0, d.sites * 3);
            }
            expect(upperTick > 0 && hemTick > 0 && hemTick > upperTick, "the free hem changes sides after the upper cloth");
            leastLag = Math.min(leastLag, hemTick - upperTick);
        }
        expect(maxJump <= .24F, "hard lateral reversals remain below a quarter-block per tick");
        System.out.printf(Locale.ROOT, "%s strafe reversal: min hem lag %d ticks, max lateral step %.3f blocks%n", d, leastLag, maxJump);
    }

    private static void stationaryTurn(Density d) {
        int tip = center(d, d.rows - 1), row = (d.rows - 1) * d.columns * 3;
        int edge = row + (d.columns - 1) * 3;
        float lowestTwist = Float.MAX_VALUE, smallestLag = Float.MAX_VALUE, largestStep = 0;
        for (int sign : new int[] { -1, 1 }) {
            WovenCloth cloth = new WovenCloth(651);
            Flow air = new Flow();
            // A remote observer or head can change direction without moving the attachment at all.
            air.update(0, 0, 0, 0, 0, false, false, true);
            cloth.initialize(d, air);
            for (int tick = 0; tick < 160; tick++) cloth.advance(air);
            verifyPose(cloth, air, false);
            float[] rest = SAMPLED.clone();
            for (int tick = 0; tick < 12; tick++) {
                cloth.advance(air);
                verifyPose(cloth, air, false);
                close(SAMPLED[tip], rest[tip], .0001F, "still shoulders do not add a false turn");
                close(SAMPLED[edge + 2] - SAMPLED[row + 2], rest[edge + 2] - rest[row + 2], .0001F,
                    "idle or head-only input does not twist the cape");
            }
            float peakTwist = 0, peakLag = 0, sustainedTwist = 0, previous = rest[tip];
            for (int tick = 1; tick <= 84; tick++) {
                float yaw = (sign * 15 * Math.min(tick, 24) + 540) % 360 - 180;
                air.update(0, 0, 0, yaw, tick <= 24 ? sign * 15 : 0, false, false, true);
                cloth.advance(air);
                verifyPose(cloth, air, false);
                float twist = sign * (SAMPLED[edge + 2] - SAMPLED[row + 2]);
                float lag = sign * (rest[tip] - SAMPLED[tip]);
                if (tick <= 24) {
                    peakTwist = Math.max(peakTwist, twist);
                    peakLag = Math.max(peakLag, lag);
                    if (tick >= 13) sustainedTwist = Math.max(sustainedTwist, twist);
                }
                if (tick == 2) expect(lag > .008F, "the hem initially trails a stationary body turn");
                if (tick == 25) expect(Math.abs(twist) > .045F, "the turn does not reset the cloth when it stops");
                largestStep = Math.max(largestStep, Math.abs(SAMPLED[tip] - previous));
                previous = SAMPLED[tip];
            }
            expect(peakTwist > .18F && sustainedTwist > .11F && peakLag > .025F,
                "a stationary turn gives a visible twist and lag without a one-frame effect: " + d + " "
                    + peakTwist + "/" + sustainedTwist + "/" + peakLag);
            for (int i = d.columns * 3; i < d.sites * 3; i++) close(SAMPLED[i], rest[i], .006F, "the cape settles after a turn");
            lowestTwist = Math.min(peakTwist, lowestTwist);
            smallestLag = Math.min(peakLag, smallestLag);
        }
        expect(largestStep < .22F, "turning in place does not snap the hem");
        System.out.printf(Locale.ROOT, "%s stationary 360 turn: opposite edges differ by %.3f blocks in depth, hem trails by %.3f blocks%n",
            d, lowestTwist, smallestLag);
    }

    private static void movingTurn(Density d) {
        for (int sign : new int[] { -1, 1 }) {
            WovenCloth cloth = new WovenCloth();
            Flow air = new Flow();
            air.update(0, 0, .78, 0, 0, false, false, true);
            cloth.initialize(d, air);
            for (int i = 0; i < 160; i++) cloth.advance(air);
            verifyPose(cloth, air, false);
            int hem = center(d, d.rows - 1);
            float previousHemX = SAMPLED[hem];
            for (int i = 1; i <= 55; i++) {
                float yaw = sign * 15.0F * Math.min(i, 6);
                double travel = Math.toRadians(sign * 15 * Math.max(0, Math.min(i - 2, 6)));
                air.update(-.78 * Math.sin(travel), 0, .78 * Math.cos(travel), yaw, i <= 6 ? sign * 15 : 0, false, false, true);
                cloth.advance(air);
                verifyPose(cloth, air, false);
                if (i == 1) expect((SAMPLED[hem] - previousHemX) * sign < -.025F, "hem briefly retains its prior world direction");
                expect(Math.abs(SAMPLED[hem] - previousHemX) <= .18F, "hem does not snap through a running turn");
                previousHemX = SAMPLED[hem];
            }
            expect(Math.abs(SAMPLED[hem]) < .08F, "a completed turn leaves no persistent side bias");
        }
    }

    private static void jumpAndLand(Density d) {
        for (double forward : new double[] { 0, .32 }) {
            Flow air = new Flow(), flat = new Flow();
            WovenCloth cloth = new WovenCloth(), control = new WovenCloth();
            flat.update(0, 0, forward, 0, 0, false, false, true);
            cloth.initialize(d, flat);
            control.initialize(d, flat);
            for (int i = 0; i < 160; i++) { cloth.advance(flat); control.advance(flat); }
            int hem = center(d, d.rows - 1), middle = center(d, d.rows / 2);
            float[] controlPose = new float[SAMPLED.length];
            double y = 0, velocity = .42;
            float trailing = 0, descentLift = 0;
            for (int i = 1; i <= 65; i++) {
                double next = Math.max(0, y + velocity);
                double dy = next - y;
                y = next;
                velocity = y > 0 ? (velocity - .08) * .98 : 0;
                flightStep(cloth, air, dy, forward, false, false);
                control.advance(flat);
                expect(control.sample(1, controlPose), "flat flight reference is finite");
                float delta = SAMPLED[hem + 1] - controlPose[hem + 1];
                if (i <= 2) {
                    expect(delta >= 0, "the hem trails down at launch, before the decelerating climb");
                    trailing = Math.max(trailing, delta);
                }
                if (i == 3 && forward != 0) {
                    expect(delta >= .045F && SAMPLED[middle + 1] - controlPose[middle + 1] >= .025F,
                        "from a run, the cloth visibly trails down before the apex");
                }
                if (dy < 0) descentLift = Math.max(descentLift, -delta);
                if (forward == 0) expect(highestHem(d) < -.10F, "a standing hop does not flip the hem above the seam");
                if (i == 65) {
                    close(SAMPLED[hem + 1], controlPose[hem + 1], forward == 0 ? .004F : .04F, "after landing, jump lift dies away");
                    close(SAMPLED[hem + 2], controlPose[hem + 2], .04F, "no stuck shape after landing");
                }
            }
            expect(descentLift >= .016F, "normal downward jump leg has visible lift before landing");
            System.out.printf(Locale.ROOT, "%s jump (forward %.2f): downward takeoff lag %.3f, falling lift %.3f blocks%n",
                d, forward, trailing, descentLift);
        }
    }

    private static void ledgeFall(Density d) {
        Flow air = new Flow();
        WovenCloth cloth = new WovenCloth();
        air.update(0, 0, 0, 0, 0, false, false, true);
        cloth.initialize(d, air);
        for (int i = 0; i < 160; i++) cloth.advance(air);
        verifyPose(cloth, air, false);
        int hem = center(d, d.rows - 1), middle = center(d, d.rows / 2);
        float restHem = SAMPLED[hem + 1], restMid = SAMPLED[middle + 1];
        double velocity = 0;
        for (int t = 1; t <= 22; t++) {
            velocity = (velocity - .08) * .98;
            flightStep(cloth, air, velocity, 0, false, false);
            if (t == 14) {
                expect(restHem - SAMPLED[hem + 1] >= .045F && restMid - SAMPLED[middle + 1] >= .045F,
                    "the cloth gradually opens as the falling speed builds");
            }
        }
        float hemLift = restHem - SAMPLED[hem + 1], midLift = restMid - SAMPLED[middle + 1];
        expect(hemLift >= .31F && midLift >= .19F, "the middle rises with the hem instead of forming a tip hook");
        System.out.printf(Locale.ROOT, "%s free fall after 22 ticks: hem up %.3f, middle up %.3f blocks%n", d, hemLift, midLift);
    }

    private static void flightWakeAndWindStrength(Density d) {
        float still = rearAtSpeed(d, 0, false, 1);
        float hover = rearAtSpeed(d, 0, true, 1);
        float strongerHover = rearAtSpeed(d, 0, true, 1.5F);
        float slow = rearAtSpeed(d, .12, true, 1);
        float fast = rearAtSpeed(d, .65, true, 1);
        float run = rearAtSpeed(d, .28, false, 1);
        float strongerRun = rearAtSpeed(d, .28, false, 1.5F);
        expect(hover > still + .10F && hover < .45F, "hover slightly opens the cape");
        expect(strongerHover > hover + .08F, "wind strength increases the hover opening");
        expect(slow > hover + .10F && fast > slow + .10F && fast < 1.18F, "actual speed increases the bounded wake");
        expect(strongerRun > run + .08F, "wind strength increases the sprint trail");
        close(rearAtSpeed(d, 0, true, 0), rearAtSpeed(d, .65, true, 0), .002F, "zero wind removes the steady external loads");

        Flow flow = new Flow();
        WovenCloth cape = new WovenCloth();
        flow.update(0, 0, 0, 0, 0, false, true, 0F, false, true);
        cape.initialize(d, flow);
        for (int tick = 0; tick < 25; tick++) cape.advance(flow);
        expect(cape.sample(1, SAMPLED) && SAMPLED[center(d, d.rows - 1) + 1] > .85F, "gravity is still active with no wind");
        float lateralBeforeTurn = SAMPLED[center(d, d.rows - 1)];
        flow.update(0, 0, 0, 16, 16, false, false, 0F, false, true);
        cape.advance(flow);
        verifyPose(cape, flow, false);
        expect(Math.abs(SAMPLED[center(d, d.rows - 1)] - lateralBeforeTurn) > .009F, "zero wind retains turn transport");
        float verticalBeforeLaunch = SAMPLED[center(d, d.rows - 1) + 1];
        flow.update(0, .22, 0, 16, 0, false, true, 0F, false, true);
        cape.advance(flow);
        verifyPose(cape, flow, false);
        expect(SAMPLED[center(d, d.rows - 1) + 1] > verticalBeforeLaunch + .01F, "zero wind retains takeoff inertia");

        for (float strength : new float[] {1, 1.5F, 3}) {
            flow.update(0, 0, 0, 0, 0, false, true, strength, false, true);
            cape.initialize(d, flow);
            for (int i = 0; i < 200; i++) {
                float yaw = Math.max(0, Math.min(144, (i - 100) * 12));
                double angle = Math.toRadians(yaw), speed = i < 30 ? .12 : i < 100 ? .65 : i < 130 ? .25 : 0;
                flow.update(-Math.sin(angle) * speed, i < 30 ? .16 : i < 70 ? -.32 : 0,
                    Math.cos(angle) * speed, yaw, i > 100 && i <= 112 ? 12 : 0, false, i < 130, strength, false, true);
                cape.advance(flow);
                verifyPose(cape, flow, false);
                if (i < 6) expect(highestHem(d) < 0, "powered takeoff keeps the hem below the shoulder: " + strength + "/" + highestHem(d));
            }
            expect(SAMPLED[center(d, d.rows - 1) + 2] - flow.shoulder < .025F, "stopping and landing return to idle");
        }
        System.out.printf(Locale.ROOT, "%s mean hem opening: hover %.3f, slow %.3f, fast %.3f; sprint %.3f -> %.3f blocks%n",
            d, hover, slow, fast, run, strongerRun);
    }

    private static float rearAtSpeed(Density d, double speed, boolean flying, float strength) {
        Flow flow = new Flow();
        WovenCloth cape = new WovenCloth();
        flow.update(0, 0, speed, 0, 0, false, flying, strength, false, true);
        cape.initialize(d, flow);
        int hem = center(d, d.rows - 1);
        float rear = 0;
        for (int i = 0; i < 250; i++) {
            cape.advance(flow);
            if (i >= 150) {
                verifyPose(cape, flow, false);
                rear += SAMPLED[hem + 2] - flow.shoulder;
            }
        }
        return rear / 100;
    }

    private static void sustainedDive(Density d) {
        Flow air = new Flow();
        WovenCloth cloth = new WovenCloth();
        air.update(0, 0, 0, 0, 0, false, false, true);
        cloth.initialize(d, air);
        for (int i = 0; i < 150; i++) cloth.advance(air);
        verifyPose(cloth, air, false);
        int hem = center(d, d.rows - 1), middle = center(d, d.rows / 2);
        float restY = SAMPLED[hem + 1], restMid = SAMPLED[middle + 1];
        for (int i = 0; i < 100; i++) flightStep(cloth, air, -2.4, 0, false, false);
        float[] bends = new float[240];
        float lift = 0, midLift = 0;
        for (int i = 0; i < bends.length; i++) {
            flightStep(cloth, air, -2.4, 0, false, false);
            bends[i] = hemBend(d, middle, hem);
            lift += restY - SAMPLED[hem + 1];
            midLift += restMid - SAMPLED[middle + 1];
            expect(SAMPLED[hem + 1] > -1.04F, "even a sustained dive cannot loft beyond the fabric length");
        }
        Arrays.sort(bends);
        float median = (bends[119] + bends[120]) * .5F;
        expect(lift / bends.length >= .37F && midLift / bends.length >= .17F,
            "middle and hem lift during sustained air loading: " + lift / bends.length + "/" + midLift / bends.length);
        expect(median > .009F && median < .24F, "the raised cape still has a bounded curve: " + median);
        air.update(0, 0, 0, 0, 0, true, false, true);
        cloth.initialize(d, air);
        for (int i = 0; i < 160; i++) flightStep(cloth, air, 0, 0, true, false);
        float wetRest = SAMPLED[hem + 1], wetLift = 0;
        for (int i = 0; i < 240; i++) {
            flightStep(cloth, air, -1.4, 0, true, false);
            if (i >= 100) wetLift += wetRest - SAMPLED[hem + 1];
        }
        expect(wetLift / 140 < .90F * lift / bends.length, "underwater descent has weaker lift");
        System.out.printf(Locale.ROOT, "%s sustained dive: mean hem lift %.3f, middle lift %.3f, median bow %.3f, wet hem lift %.3f blocks%n",
            d, lift / bends.length, midLift / bends.length, median, wetLift / 140);
    }

    private static int center(Density d, int row) {
        return (row * d.columns + d.columns / 2) * 3;
    }

    private static void verticalFlight(Density d) {
        WovenCloth cloth = new WovenCloth();
        Flow air = new Flow();
        air.update(0, 0, 0, 0, 0, false, false, true);
        cloth.initialize(d, air);
        for (int i = 0; i < 150; i++) cloth.advance(air);
        cloth.sample(1, SAMPLED);
        int mid = (d.rows / 2 * d.columns + d.columns / 2) * 3;
        int hem = ((d.rows - 1) * d.columns + d.columns / 2) * 3;
        float restY = SAMPLED[hem + 1], restZ = SAMPLED[hem + 2];
        for (int i = 0; i < 14; i++) flightStep(cloth, air, 1.4, 0, false, false);
        cloth.sample(1, SAMPLED);
        float riseDepth = SAMPLED[hem + 2] - restZ;
        expect(Math.abs(riseDepth) < .17F && SAMPLED[hem + 1] >= restY - .045F,
            "steady climb does not propel the hem above its resting drape");
        for (int i = 0; i < 59; i++) flightStep(cloth, air, 0, 0, false, false);
        close(SAMPLED[hem + 2], restZ, .03F, "return to hover after climb");
        for (int i = 0; i < 35; i++) flightStep(cloth, air, -1.4, 0, false, false);
        float diveBend = hemBend(d, mid, hem);
        expect(diveBend > .009F && diveBend < .33F && SAMPLED[hem + 1] < restY - .28F,
            "a fast dive raises the hem with bounded curvature: " + d + " bend=" + diveBend + " lift=" + (restY - SAMPLED[hem + 1]));
        expect(SAMPLED[hem + 1] > -1.04F, "the shoulder remains attached even if a long dive lofts the hem");
        for (int i = 0; i < 4; i++) flightStep(cloth, air, 0, 0, false, false);
        for (int i = 0; i < 65; i++) flightStep(cloth, air, 0, 0, false, false);
        close(SAMPLED[hem + 1], restY, .02F, "return to hover after dive");
        close(SAMPLED[hem + 2], restZ, .025F, "no residual pressure after dive");
        for (int i = 0; i < 120; i++) {
            double dy = i % 22 < 11 ? -2.7 : 2.7;
            flightStep(cloth, air, dy, .85, i > 84, i >= 45 && i < 84);
        }
        // Do the same zero-input tick on a reused and a fresh solver: reset must clear the vertical filter too.
        air.update(0, 0, 0, 0, 0, false, false, true);
        WovenCloth fresh = new WovenCloth();
        cloth.initialize(d, air);
        fresh.initialize(d, air);
        flightStep(cloth, air, .25, 0, false, false);
        float[] restarted = SAMPLED.clone();
        flightStep(fresh, air, .25, 0, false, false);
        for (int i = 0; i < d.sites * 3; i++) close(SAMPLED[i], restarted[i], 1e-7F, "reset clears pressure and vertical velocity history");
        System.out.printf(Locale.ROOT, "%s steady ascent rear excursion: %.3f; descent bow %.3f blocks%n", d, riseDepth, diveBend);
    }

    private static void flightStep(WovenCloth cloth, Flow flow, double dy, double dz, boolean wet, boolean crouch) {
        flow.update(0, dy, dz, 0, 0, wet, crouch, true);
        cloth.advance(flow);
        verifyPose(cloth, flow, crouch);
    }

    private static void verifyPose(WovenCloth cloth, Flow flow, boolean crouch) {
        Density d = cloth.density();
        expect(cloth.sample(.5F, SAMPLED), "finite interpolated sample");
        headClearance(d, flow.squat);
        expect(cloth.sample(1, SAMPLED), "finite final sample");
        headClearance(d, flow.squat);
        for (int i = 0; i < d.columns; i++) {
            close(SAMPLED[i * 3 + 1], 0, 1e-6F, "flight seam stays pinned");
            close(SAMPLED[i * 3 + 2], flow.shoulder, 1e-6F, "flight shoulder clearance");
        }
        for (int j = d.columns * 3; j < d.sites * 3; j += 3) {
            float back = flow.shoulder + (crouch ? .48F * Math.max(0, Math.min(.71F, SAMPLED[j + 1])) : 0);
            expect(SAMPLED[j + 2] >= back - 1e-6F, "the flight shape stays behind the chestplate");
            int a = j - d.columns * 3;
            double stretch = distance(j, a) * (d.rows - 1);
            if (stretch >= 1.29F) throw new AssertionError("loft must not stretch the cloth: " + d + " node=" + j / 3
                + " stretch=" + stretch + " from(" + SAMPLED[a] + "," + SAMPLED[a+1] + "," + SAMPLED[a+2]
                + ") to(" + SAMPLED[j] + "," + SAMPLED[j+1] + "," + SAMPLED[j+2] + ") crouch=" + crouch);
        }
    }

    private static void headClearance(Density d, float squat) {
        float offset = BodyContact.HEAD_CROUCH * squat;
        float top = BodyContact.HEAD_TOP + offset, bottom = BodyContact.HEAD_BOTTOM;
        for (int i = d.columns * 3; i < d.sites * 3; i += 3) {
            expect(Math.abs(SAMPLED[i]) >= BodyContact.HEAD_HALF_WIDTH - 1e-6F
                || SAMPLED[i + 1] <= top + 1e-6F || SAMPLED[i + 1] >= BodyContact.lowerLip(SAMPLED[i + 2], bottom) - 1e-6F
                || SAMPLED[i + 2] >= BodyContact.HEAD_REAR - 1e-6F, "do not cut through the head proxy");
        }
    }

    private static float highestHem(Density d) {
        float high = -Float.MAX_VALUE;
        for (int i = (d.rows - 1) * d.columns * 3 + 1; i < d.sites * 3; i += 3) high = Math.max(high, -SAMPLED[i]);
        return high;
    }

    private static void obliqueShoulderLift(Density d) {
        Flow air = new Flow();
        WovenCloth cloth = new WovenCloth(917);
        air.update(0, 0, 0, 0, 0, false, false, true);
        cloth.initialize(d, air);
        for (int i = 0; i < 130; i++) cloth.advance(air);
        ClothSurface mesh = new ClothSurface();
        HeadMeshProbe probe = new HeadMeshProbe();
        float lastYaw = 0, peak = -1;
        int lofted = 0, slant = 0;
        double fall = 0;
        for (int i = 0; i < 153; i++) {
            double brake = Math.max(0, Math.min(1, (103 - i) / 13.0));
            double forward = i >= 20 ? .54 * brake : 0;
            double side = i < 32 ? 0 : (i < 61 ? .28 : -.37) * brake;
            fall = i >= 42 && i < 103 ? Math.max(-2.26, (fall - .076) * .985) : 0;
            float yaw = Math.max(0, Math.min(i - 62, 12)) * 4.5F;
            double rad = Math.toRadians(yaw);
            double dx = Math.cos(rad) * side - Math.sin(rad) * forward;
            double dz = Math.sin(rad) * side + Math.cos(rad) * forward;
            air.update(dx, fall * brake, dz, yaw, yaw - lastYaw, false, false, true);
            lastYaw = yaw;
            cloth.advance(air);
            verifyPose(cloth, air, false);
            float up = highestHem(d);
            if (fall < 0 && up > .10F) {
                lofted++;
                peak = Math.max(peak, up);
                int a = (d.rows - 1) * d.columns * 3, b = (d.sites - 1) * 3;
                if (Math.abs(SAMPLED[a + 1] - SAMPLED[b + 1]) > .03F
                    || Math.abs(SAMPLED[a + 2] - SAMPLED[b + 2]) > .04F) slant++;
                expect(mesh.prepare(cloth, .5F), "the lofted surface can be drawn");
                mesh.writeQuads(probe);
            }
            if (i > 148) expect(up < -.85F, "braking and rest let the hem settle back below the shoulders");
        }
        expect(lofted >= 6 && peak > .17F && peak < .96F && slant >= 3, "oblique high-speed descent can loft unevenly: "
            + d + " peak=" + peak + " frames=" + lofted + " uneven=" + slant);
        expect(probe.vertices == 0, "the mesh check saw complete quads");
        System.out.printf(Locale.ROOT, "%s oblique fall: highest hem %.3f blocks above seam; %d lift ticks, %d asymmetric%n",
            d, peak, lofted, slant);
    }

    private static void collarContinuity(Density d) {
        Flow air = new Flow();
        WovenCloth cloth = new WovenCloth(917);
        air.update(0, 0, 0, 0, 0, false, false, 1.5F, false, true);
        cloth.initialize(d, air);
        for (int tick = 0; tick < 130; tick++) cloth.advance(air);
        ClothSurface mesh = new ClothSurface();
        HeadMeshProbe helmet = new HeadMeshProbe();
        int flat = 0, lofted = 0, stride = d.columns * 3;
        double peakBend = 0, fall = 0;
        float lastYaw = 0;
        for (int tick = 0; tick <= 112; tick++) {
            double brake = Math.max(0, Math.min(1, (103 - tick) / 13.0));
            double forward = tick >= 20 ? .54 * brake : 0;
            double side = tick < 32 ? 0 : (tick < 61 ? .28 : -.37) * brake;
            fall = tick >= 42 && tick < 103 ? Math.max(-2.26, (fall - .076) * .985) : 0;
            float yaw = Math.max(0, Math.min(tick - 62, 12)) * 4.5F;
            double rad = Math.toRadians(yaw);
            air.update(Math.cos(rad) * side - Math.sin(rad) * forward, fall * brake,
                Math.sin(rad) * side + Math.cos(rad) * forward, yaw, yaw - lastYaw, false, false, 1.5F, false, true);
            lastYaw = yaw;
            cloth.advance(air);
            verifyPose(cloth, air, false);
            if (tick < 60) continue;
            if (highestHem(d) > .10F) {
                lofted++;
                expect(mesh.prepare(cloth, .5F), "lofted collar remains renderable");
                mesh.writeQuads(helmet);
            }
            expect(cloth.sample(.5F, SAMPLED), "interpolated collar is finite");
            for (int col = 0; col < d.columns; col++) {
                int flatLinks = 0;
                double previous = 0;
                for (int row = 1; row <= Math.min(5, d.rows - 1); row++) {
                    int node = row * stride + col * 3, parent = node - stride;
                    double rear = SAMPLED[node + 2] - SAMPLED[parent + 2];
                    double angle = Math.toDegrees(Math.atan2(rear, SAMPLED[node + 1] - SAMPLED[parent + 1]));
                    if (Math.abs(SAMPLED[node + 1]) < .001F && SAMPLED[node + 2] < .332F && rear > .018) {
                        if (++flatLinks == 2) flat++;
                    } else flatLinks = 0;
                    if (row >= 2 && row <= 4) {
                        double bend = Math.abs(angle - previous);
                        peakBend = Math.max(peakBend, Math.min(bend, 360 - bend));
                    }
                    previous = angle;
                }
            }
        }
        expect(lofted >= 6 && helmet.vertices == 0, "the collar test must exercise lofted render geometry");
        if (d == Density.DETAIL) {
            expect(flat == 0, "adjacent collar links must not form a rigid shelf: " + flat);
            expect(peakBend < 15, "bend must be distributed across the near collar: " + peakBend);
        }
        System.out.printf(Locale.ROOT, "%s collar at wind 1.5: %d lofted ticks; %d planar shelves; peak local bend %.1f degrees%n",
            d, lofted, flat, peakBend);
    }

    /** The upright replay never stressed the joint where the lower lip meets the rear face. */
    private static void posedCollarTransition(Density d) {
        ClothSurface mesh = new ClothSurface();
        HeadMeshProbe helmet = new HeadMeshProbe();
        Flow air = new Flow();
        CapeFrame pose = new CapeFrame();
        WovenCloth cloth = new WovenCloth(917);
        int stride = d.columns * 3;
        float[] pitches = {-66, -35, 0};
        float[] limits = d == Density.DETAIL ? new float[] {37, 30, 17} : new float[] {56, 51, 51};
        float[] partials = {0, .25F, .5F, .75F, 1};
        for (int scenario = 0; scenario < pitches.length; scenario++) {
            float pitch = pitches[scenario];
            boolean flying = pitch == 0;
            double peak = 0;
            float lift = 0;
            for (int tick = 0; tick < 230; tick++) {
                double brake = Math.max(0, Math.min(1, (148 - tick) / 18.0));
                double fall = -Math.min(2.24, .075 * Math.max(0, tick - 80)) * brake;
                double forward = tick < 64 || tick >= 148 ? 0 : flying ? .08 : .53;
                double side = tick < 110 || tick >= 148 ? 0 : -.3;
                air.update(side, fall, forward, 0, 0, false, flying, 1.5F, false, true);
                pose.setRoot((float) (pitch * Math.min(1, Math.abs(fall) * 3)), 0, (float) (side * 14));
                air.pose(pose);
                if (tick == 0) cloth.initialize(d, air);
                else cloth.advance(air);
                verifyPose(cloth, air, false);
                if (tick < 80) continue;
                for (float partial : partials) {
                    expect(cloth.sample(partial, SAMPLED), "the posed collar remains finite across the tick");
                    headClearance(d, air.squat);
                    float up = highestHem(d);
                    lift = Math.max(up, lift);
                    if (up <= .10F) continue;
                    expect(mesh.prepare(cloth, partial), "lofted posed collar remains renderable");
                    mesh.writeQuads(helmet);
                    for (int row = 1; row <= 4; row++) {
                        for (int col = 0; col < d.columns; col++) {
                            int node = row * stride + col * 3;
                            peak = Math.max(peak, warpBend(node - stride, node, node + stride));
                        }
                    }
                }
            }
            expect(lift > .15F && highestHem(d) < -.55F, "the posed cape must still lift and settle: " + lift);
            expect(helmet.vertices == 0, "each sampled posed mesh has complete quads");
            expect(peak < limits[scenario], "posed collar transition bends too sharply: " + d + " / " + pitch + " = " + peak);
            System.out.printf(Locale.ROOT, "%s posed collar (%+.0f deg): peak bend %.1f degrees, lift %.3f blocks%n",
                d, pitch, peak, lift);
        }
    }

    private static double warpBend(int before, int joint, int after) {
        double dot = 0, u2 = 0, v2 = 0;
        for (int axis = 0; axis < 3; axis++) {
            float u = SAMPLED[joint + axis] - SAMPLED[before + axis];
            float v = SAMPLED[after + axis] - SAMPLED[joint + axis];
            dot += u * v;
            u2 += u * u;
            v2 += v * v;
        }
        expect(u2 > 1e-8 && v2 > 1e-8, "the collar links must not collapse");
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot / Math.sqrt(u2 * v2)))));
    }

    /** Sample both triangles of every rendered quad against the solid of the upright test head.
     * The attachment band (roughly the bottom pixel of the head) is excluded. */
    private static final class HeadMeshProbe implements ClothSurface.VertexOut {
        final float[] quad = new float[12];
        int vertices;

        @Override public void point(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            quad[vertices++] = -x;
            quad[vertices++] = y;
            quad[vertices++] = -z;
            if (vertices < quad.length) return;
            vertices = 0;
            triangle(0, 3, 6);
            triangle(0, 6, 9);
        }

        private void triangle(int a, int b, int c) {
            if (Math.min(quad[a + 1], Math.min(quad[b + 1], quad[c + 1])) >= -.0625F
                || Math.min(quad[a + 2], Math.min(quad[b + 2], quad[c + 2])) >= .1875F) return;
            for (int j = 0; j <= 8; j++) for (int k = 0; j + k <= 8; k++) {
                float s = j * .125F, t = k * .125F, r = 1 - s - t;
                float x = r * quad[a] + s * quad[b] + t * quad[c];
                float y = r * quad[a + 1] + s * quad[b + 1] + t * quad[c + 1];
                float z = r * quad[a + 2] + s * quad[b + 2] + t * quad[c + 2];
                expect(Math.abs(x) >= .3125F || y >= -.0625F || y <= -.5625F || z >= .1875F,
                    "lofted render triangles must stay outside the upright standard helmet");
            }
        }
    }

    private static float hemBend(Density d, int mid, int hem) {
        int pin = d.columns / 2 * 3;
        float bottomY = SAMPLED[hem + 1] - SAMPLED[pin + 1], bottomZ = SAMPLED[hem + 2] - SAMPLED[pin + 2];
        float middleY = SAMPLED[mid + 1] - SAMPLED[pin + 1], middleZ = SAMPLED[mid + 2] - SAMPLED[pin + 2];
        return (float) (Math.abs(bottomY * middleZ - bottomZ * middleY) / Math.hypot(bottomY, bottomZ));
    }

    private static float depth(int r, int c) {
        return SAMPLED[(r * 5 + c) * 3 + 2];
    }

    private static void gustResponse(Density d) {
        FlutterSample idle = steadyFlutter(d, 0, 0, false);
        FlutterSample fast = steadyFlutter(d, 0, .9, false);
        FlutterSample water = steadyFlutter(d, 0, .9, true);
        expect(idle.tipRange() < .004F, "no forced idle animation");
        expect(fast.tipRange() > .012F && fast.tipRange() < .63F && fast.largestStep < .32,
            "steady airflow can excite the free end, but the solution remains bounded");
        expect(water.tipRange() < fast.tipRange() * .50F, "the medium damps steady motion underwater");
        expect(fast.leftHigh - fast.leftLow > .003F, "asymmetric airflow reaches the cloth corners");
        expect(fast.topHigh - fast.topLow < fast.tipRange() * .25F && fast.bendHigh - fast.bendLow > .02F,
            "the free end bends more than the collar instead of oscillating as a rigid unit");
        System.out.printf(Locale.ROOT, "%s steady hem height range: idle %.3f, fast %.3f, underwater %.3f blocks%n",
            d, idle.tipRange(), fast.tipRange(), water.tipRange());
    }

    private static FlutterSample steadyFlutter(Density d, double dy, double forward, boolean submerged) {
        WovenCloth cape = new WovenCloth(), other = new WovenCloth(4381);
        Flow flow = new Flow();
        flow.update(0, dy, forward, 0, 0, submerged, false, true);
        cape.initialize(d, flow);
        other.initialize(d, flow);
        FlutterSample span = new FlutterSample();
        float[] previous = new float[SAMPLED.length];
        float[] interpolated = new float[SAMPLED.length];
        float[] alternate = new float[SAMPLED.length];
        int width = d.columns, hem = (d.rows - 1) * width * 3, tip = hem + width / 2 * 3;
        int middle = (d.rows / 2 * width + width / 2) * 3, neck = ((d.rows - 1) / 6 * width + width / 2) * 3;
        float avatarGap = 0;
        for (int tick = 0; tick < 340; tick++) {
            cape.advance(flow);
            other.advance(flow);
            expect(cape.sample(1, SAMPLED) && other.sample(1, alternate), "finite motion at fixed velocity");
            if (tick >= 100) {
                float y = SAMPLED[tip + 1], z = SAMPLED[tip + 2];
                span.include(y, SAMPLED[hem + 1] - SAMPLED[hem + width * 3 - 2], SAMPLED[neck + 1],
                    SAMPLED[middle + 1] - .5F * y, Math.hypot(y - previous[tip + 1], z - previous[tip + 2]));
                avatarGap = Math.max(avatarGap, Math.abs(y - alternate[tip + 1]));
                cape.sample(.5F, interpolated);
                close(interpolated[tip + 1], .5F * (previous[tip + 1] + y), 1e-6F, "render interpolates flutter between ticks");
                for (int node = width * 3; node < d.sites * 3; node += 3) {
                    expect(SAMPLED[node + 2] >= flow.shoulder - 1e-6F, "flutter still respects armor clearance");
                    expect(distance(node, node - width * 3) * (d.rows - 1) < 1.22F, "flutter still respects strain limiting");
                }
            }
            System.arraycopy(SAMPLED, 0, previous, 0, d.sites * 3);
        }
        if (forward == .9 && !submerged) {
            expect(avatarGap > .003F, "seeded air disturbance varies between wearers");
            // Use the same steady wind before and after reset to catch retained phase or energy.
            cape.initialize(d, flow);
            WovenCloth fresh = new WovenCloth();
            fresh.initialize(d, flow);
            for (int tick = 0; tick < 35; tick++) {
                cape.advance(flow);
                fresh.advance(flow);
                cape.sample(1, SAMPLED);
                fresh.sample(1, alternate);
                close(SAMPLED[tip + 1], alternate[tip + 1], 1e-7F, "reset clears old flutter state");
            }
            flow.update(0, 0, 0, 0, 0, false, false, true);
            for (int i = 0; i < 170; i++) cape.advance(flow);
            cape.sample(1, previous);
            for (int i = 0; i < 30; i++) {
                cape.advance(flow);
                cape.sample(1, SAMPLED);
                close(SAMPLED[tip + 1], previous[tip + 1], .002F, "no idle flutter after stopping");
                close(SAMPLED[tip + 2], previous[tip + 2], .002F, "no idle billow after stopping");
            }
        }
        return span;
    }

    private static final class FlutterSample {
        float hemLow = Float.POSITIVE_INFINITY, hemHigh = Float.NEGATIVE_INFINITY;
        float leftLow = hemLow, leftHigh = hemHigh, topLow = hemLow, topHigh = hemHigh;
        float bendLow = hemLow, bendHigh = hemHigh;
        double largestStep;

        void include(float hem, float edge, float top, float bend, double step) {
            hemLow = Math.min(hemLow, hem);
            hemHigh = Math.max(hemHigh, hem);
            leftLow = Math.min(leftLow, edge);
            leftHigh = Math.max(leftHigh, edge);
            topLow = Math.min(topLow, top);
            topHigh = Math.max(topHigh, top);
            bendLow = Math.min(bendLow, bend);
            bendHigh = Math.max(bendHigh, bend);
            largestStep = Math.max(largestStep, step);
        }

        float tipRange() {
            return hemHigh - hemLow;
        }
    }

    private static void regridAndReset() {
        Flow push = new Flow();
        push.update(-.3, .19, .49, 38, -18, false, true, true);
        WovenCloth cloth = new WovenCloth();
        cloth.initialize(Density.DETAIL, push);
        for (int i = 0; i < 20; i++) cloth.advance(push);
        float[] now = new float[SAMPLED.length];
        float[] before = new float[SAMPLED.length];
        cloth.sample(1, now);
        cloth.sample(0, before);
        cloth.changeDensity(Density.ECONOMY, SAMPLED);
        float[] low = new float[SAMPLED.length];
        cloth.sample(1, low);
        compareCommonNodes(now, low);
        cloth.sample(0, low);
        compareCommonNodes(before, low);
        cloth.changeDensity(Density.DETAIL, SAMPLED);
        cloth.sample(0, SAMPLED);
        close(depth(12, 4), before[(12 * 5 + 4) * 3 + 2], 1e-6F, "resample back without snapping hem");
        cloth.initialize(Density.DETAIL, push);
        cloth.sample(1, now);
        cloth.sample(0, before);
        for (int i = 0; i < now.length; i++) close(now[i], before[i], 1e-7F, "teleport discards old velocities");
    }

    private static void compareCommonNodes(float[] high, float[] low) {
        for (int r = 0; r < 7; r++) for (int c = 0; c < 3; c++) {
            int a = (r * 2 * 5 + c * 2) * 3, b = (r * 3 + c) * 3;
            for (int k = 0; k < 3; k++) close(high[a + k], low[b + k], 1e-6F, "seam pose and interpolator at LOD switch");
        }
    }

    private static void mesh(Density density) {
        Flow rest = new Flow();
        rest.update(0, 0, 0, 0, 0, false, true, true);
        WovenCloth cloth = new WovenCloth();
        cloth.initialize(density, rest);
        for (int i = 0; i < 15; i++) cloth.advance(rest);
        ClothSurface mesh = new ClothSurface();
        expect(mesh.prepare(cloth, .5F), "build mesh");
        MeshCheck value = new MeshCheck();
        mesh.writeQuads(value);
        int w = density.columns - 1, h = density.rows - 1;
        expect(value.quads == 2 * (w * h + w + h), "no duplicated interior faces");
        for (int count : value.edgeCount.values()) expect(count == 2, "every outside mesh edge is shared");
        expect(value.firstU == 11 / 64.0F && value.firstV == 1 / 32.0F, "outside texture orientation");
        expect(!mesh.prepare(cloth, Float.NaN), "non-finite partial tick rejected");
    }

    private static void allocation() {
        java.lang.management.ThreadMXBean basic = java.lang.management.ManagementFactory.getThreadMXBean();
        if (!(basic instanceof com.sun.management.ThreadMXBean)) return;
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) basic;
        if (!mx.isThreadAllocatedMemoryEnabled()) return;
        WovenCloth cloth = new WovenCloth();
        ClothSurface mesh = new ClothSurface();
        Flow input = new Flow();
        input.update(.2, 0, .3, 21, 4, false, false, false);
        cloth.initialize(Density.DETAIL, input);
        CountSink sink = new CountSink();
        cycle(cloth, mesh, input, sink, 2000);
        cycle(cloth, mesh, input, sink, 2500);
        long task = Thread.currentThread().getId();
        long before = mx.getThreadAllocatedBytes(task);
        cycle(cloth, mesh, input, sink, 1500);
        long bytes = mx.getThreadAllocatedBytes(task) - before;
        expect(bytes <= 256, "steady-state heap allocations: " + bytes);
        System.out.println("Standalone cloth kernel: " + bytes + " B over 1500 tick+mesh iterations; sink=" + sink.vertices);
    }

    private static void cycle(WovenCloth cloth, ClothSurface mesh, Flow wind, CountSink sink, int n) {
        for (int i = 0; i < n; i++) {
            wind.update(.4 * Math.sin(i * .11), 1.4 * Math.sin(i * .09), .39, 21, 4, false, i % 80 < 40,
                1.0F, false, false);
            cloth.advance(wind);
            if (mesh.prepare(cloth, .4F)) mesh.writeQuads(sink);
        }
    }

    private static final class CountSink implements ClothSurface.VertexOut {
        int vertices;
        float value;
        @Override public void point(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            vertices++;
            value += x + z;
        }
    }

    private static final class MeshCheck implements ClothSurface.VertexOut {
        final float[][] points = new float[4][3];
        final Map<String, Integer> edgeCount = new HashMap<>();
        int count, quads;
        float firstU, firstV, xx, yy, zz;

        @Override public void point(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            expect(u >= 0 && u <= 22 / 64.0F && v >= 0 && v <= 17 / 32.0F, "UV inside supported rectangle");
            close(nx * nx + ny * ny + nz * nz, 1, .005F, "unit surface normal");
            points[count][0] = x;
            points[count][1] = y;
            points[count][2] = z;
            if (count == 0) {
                if (quads == 0) { firstU = u; firstV = v; }
                xx = nx; yy = ny; zz = nz;
            }
            if (++count != 4) return;
            float ax = points[1][0] - points[0][0], ay = points[1][1] - points[0][1], az = points[1][2] - points[0][2];
            float bx = points[2][0] - points[0][0], by = points[2][1] - points[0][1], bz = points[2][2] - points[0][2];
            expect((ay * bz - az * by) * xx + (az * bx - ax * bz) * yy + (ax * by - ay * bx) * zz > 0,
                "outward winding, quad " + quads);
            for (int i = 0; i < 4; i++) {
                String from = key(points[i]), to = key(points[(i + 1) % 4]);
                String edge = from.compareTo(to) < 0 ? from + "/" + to : to + "/" + from;
                edgeCount.merge(edge, 1, Integer::sum);
            }
            quads++;
            count = 0;
        }

        private String key(float[] xyz) {
            return Math.round(xyz[0] * 100000) + ":" + Math.round(xyz[1] * 100000) + ":" + Math.round(xyz[2] * 100000);
        }
    }

    private static double distance(int i, int j) {
        double x = SAMPLED[i] - SAMPLED[j], y = SAMPLED[i + 1] - SAMPLED[j + 1], z = SAMPLED[i + 2] - SAMPLED[j + 2];
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static void close(float found, float expected, float tolerance, String reason) {
        expect(Math.abs(found - expected) <= tolerance, reason + ": " + found + " vs " + expected);
    }

    private static void expect(boolean predicate, String reason) {
        if (!predicate) throw new AssertionError(reason);
    }
}
