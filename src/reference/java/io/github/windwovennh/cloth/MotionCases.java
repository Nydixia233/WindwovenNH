package io.github.windwovennh.cloth;

import java.util.ArrayList;
import java.util.List;

/** Original, deterministic inputs. Distances are blocks, yaw degrees, time client ticks. */
final class MotionCases {
    final String name;
    final boolean heldOut;
    final int seed;
    final Input[] steps;

    private MotionCases(String name, boolean heldOut, int seed, Input[] steps) {
        this.name = name;
        this.heldOut = heldOut;
        this.seed = seed;
        this.steps = steps;
    }

    static List<MotionCases> all() {
        List<MotionCases> result = new ArrayList<>();
        result.add(sequence("stride-surge", false, 0, 136, (i, s) -> {
            if (i >= 25 && i < 93) s.forward = ramp(i, 25, 36) * (i < 60 ? .36 : .81);
            if (i >= 93 && i < 109) s.forward = .81 * (1 - ramp(i, 93, 109));
        }));
        result.add(sequence("running-hop", false, 431, 131, (i, s) -> {
            if (i >= 23 && i < 89) s.forward = .33 * ramp(i, 23, 32);
            s.up = hop(i - 52, .42);
        }));
        result.add(sequence("climb-dive", false, 815, 147, (i, s) -> {
            if (i >= 26 && i < 57) s.up = 1.17 * ramp(i, 26, 35);
            if (i >= 73 && i < 114) s.up = -1.08 * ramp(i, 73, 88);
        }));
        result.add(sequence("thread-the-turn", false, 1833, 158, (i, s) -> {
            s.forward = i < 120 ? .58 * ramp(i, 24, 37) : .58 * (1 - ramp(i, 120, 128));
            s.side = i >= 41 && i < 93 ? .40 * (i < 64 ? 1 : -1) : 0;
            s.yaw = 85 * ramp(i, 96, 104);
            s.travelYaw = 85 * ramp(i, 99, 107);
        }));
        result.add(sequence("submerged-drift", false, 235, 118, (i, s) -> {
            s.wet = true;
            if (i >= 22 && i < 80) { s.forward = .40; s.up = i < 56 ? .29 : -.64; }
        }));
        result.add(sequence("billowed-dive", false, 350, 155, (i, s) -> {
            double brake = 1 - ramp(i, 103, 114);
            s.forward = .63 * ramp(i, 26, 38) * brake;
            s.side = .22 * ramp(i, 49, 59) * brake;
            s.up = -2.40 * ramp(i, 51, 89) * brake;
            s.yaw = 48 * ramp(i, 82, 97);
            s.travelYaw = 48 * ramp(i, 85, 100);
        }));
        result.add(sequence("oblique-drop-turn", true, 721, 135, (i, s) -> {
            s.forward = i < 91 ? .57 * ramp(i, 24, 29) : 0;
            s.side = i >= 41 && i < 62 ? .43 : i >= 62 && i < 91 ? -.41 : 0;
            s.up = i >= 39 && i < 88 ? -1.24 * ramp(i, 39, 63) : 0;
            s.yaw = -110 * ramp(i, 58, 67);
            s.travelYaw = -110 * ramp(i, 61, 72);
        }));
        result.add(sequence("boost-then-brake", true, 57, 142, (i, s) -> {
            if (i >= 29 && i < 53) { s.forward = .46; s.up = .91; }
            if (i >= 53 && i < 67) { s.forward = -.19; s.up = .20 * (1 - ramp(i, 53, 67)); }
            if (i >= 70 && i < 94) s.up = .8 * ((i - 70) / 24.0) * -1.0;
            if (i >= 99) s.crouch = i < 122;
        }));
        result.add(sequence("ledge-landing", true, 934, 121, (i, s) -> {
            if (i >= 34 && i < 66) {
                s.up = -3.92 * (1.0 - Math.pow(.98, i - 33));
                s.forward = .27 * (1 - ramp(i, 59, 66));
            }
            s.armor = false;
        }));
        result.add(sequence("banked-fall", true, 1901, 151, (i, s) -> {
            double brake = 1 - ramp(i, 99, 108);
            s.forward = .49 * ramp(i, 25, 37) * brake;
            s.side = (.35 * ramp(i, 37, 47) - .69 * ramp(i, 67, 74)) * brake;
            s.up = -2.35 * ramp(i, 41, 78) * brake;
            s.yaw = -63 * ramp(i, 65, 75);
            s.travelYaw = -63 * ramp(i, 69, 81);
            s.armor = false;
        }));
        result.add(sequence("vertical-long-fall", true, 416, 137, (i, s) -> {
            if (i >= 33 && i < 91) s.up = -3.92 * (1 - Math.pow(.98, i - 32));
        }));
        result.add(sequence("standing-hop", true, 571, 116, (i, s) -> {
            s.up = hop(i - 43, .42);
            s.armor = false;
        }));
        return result;
    }

    static List<MotionCases> stationaryTurns() {
        List<MotionCases> result = new ArrayList<>();
        result.add(sequence("standing-body-turn", true, 651, 118, (i, s) -> {
            s.yaw = 360 * ramp(i, 33, 57);
        }));
        result.add(sequence("slow-turn-reverse", true, 912, 124, (i, s) -> {
            s.yaw = -180 * ramp(i, 31, 57) + 150 * ramp(i, 60, 73);
            s.armor = false;
        }));
        return result;
    }

    private interface Script { void frame(int i, Input into); }

    private static MotionCases sequence(String name, boolean test, int seed, int duration, Script script) {
        Input[] steps = new Input[duration];
        double previousYaw = 0;
        for (int i = 0; i < duration; i++) {
            Input s = new Input();
            script.frame(i, s);
            double radians = Math.toRadians(s.travelYaw);
            double c = Math.cos(radians), t = Math.sin(radians);
            s.dx = c * s.side - t * s.forward;
            s.dz = t * s.side + c * s.forward;
            s.turn = s.yaw - previousYaw;
            previousYaw = s.yaw;
            steps[i] = s;
        }
        return new MotionCases(name, test, seed, steps);
    }

    private static double ramp(double i, double start, double end) {
        return Math.max(0, Math.min(1, (i - start) / (end - start)));
    }

    private static double hop(int tick, double v) {
        if (tick < 0) return 0;
        double y = 0;
        for (int i = 0; i < tick; i++) {
            y += v;
            if (y <= 0) return 0;
            v = (v - .08) * .98;
        }
        return Math.max(-y, v);
    }

    static final class Input {
        double dx, up, dz, yaw, turn, travelYaw, side, forward;
        boolean armor = true, crouch, wet;
    }
}
