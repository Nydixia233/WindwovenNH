package io.github.windwovennh.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.windwovennh.cloth.ClothSurface;
import io.github.windwovennh.cloth.Density;
import io.github.windwovennh.cloth.Flow;
import io.github.windwovennh.cloth.WovenCloth;
import io.github.windwovennh.mixin.BatchStatus;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

public final class PlayerCloths {
    private static final String METRICS_FLAG = "windwovennh.metrics";
    private static final boolean MEASURE = Boolean.getBoolean(METRICS_FLAG);
    private static PlayerCloths addon;

    // Read by a development-only fixture. Not networked, logged, or enabled by default.
    public static long tickNanos, drawNanos, stepHigh, stepLow, drawHigh, drawLow, originalDistance;

    private final Minecraft game = Minecraft.getMinecraft();
    private final Thread owner = Thread.currentThread();
    private final boolean full;
    private final IdentityHashMap<AbstractClientPlayer, Garment> lookup = new IdentityHashMap<>();
    private final ArrayList<Garment> visible = new ArrayList<>();
    private final Flow wind = new Flow();
    private final ModelSpace model = new ModelSpace();
    private final ClothSurface fabric = new ClothSurface();
    private final float[] regrid = new float[3 * WovenCloth.CAPACITY];
    private WorldClient scene;
    private int ticks;

    private PlayerCloths(boolean full) {
        this.full = full;
    }

    public static void enable(boolean full) {
        if (addon != null) return;
        PlayerCloths service = new PlayerCloths(full);
        addon = service;
        FMLCommonHandler.instance().bus().register(service);
    }

    public static long startDraw() {
        return MEASURE && addon != null && addon.owner == Thread.currentThread() ? System.nanoTime() : 0;
    }

    public static void finishDraw(long start) {
        if (start != 0) drawNanos += System.nanoTime() - start;
    }

    public static boolean replace(AbstractClientPlayer who, float partial, float scale) {
        PlayerCloths service = addon;
        return service != null && Thread.currentThread() == service.owner && service.submit(who, partial, scale);
    }

    private boolean submit(AbstractClientPlayer wearer, float part, float scale) {
        if (scene == null || scene != game.theWorld || wearer.worldObj != scene || !Float.isFinite(part)
            || scale != 0.0625F) return false;
        Density level = detail(wearer);
        if (level == null) {
            if (MEASURE) originalDistance++;
            return false;
        }
        Tessellator target = Tessellator.instance;
        if (!(target instanceof BatchStatus) || ((BatchStatus) target).windwovennh$busy()) return false;
        Garment garment = lookup.get(wearer);
        if (garment == null) {
            garment = new Garment(wearer);
            lookup.put(wearer, garment);
            visible.add(garment);
        }
        garment.drawn = ticks;
        if (!garment.ready || garment.discontinuity()) return false;
        if (garment.cloth.density() != level) garment.cloth.changeDensity(level, regrid);
        if (!model.fromLegacyCall(wearer, part) || !fabric.prepare(garment.cloth, part)) {
            garment.ready = false;
            return false;
        }
        // Retain the game's texture/light/shader state: start the same QUADS batch as a vanilla cloak face.
        target.startDrawingQuads();
        model.outputTo(target);
        try {
            fabric.writeQuads(model);
            target.draw();
        } finally {
            model.outputTo(null);
        }
        if (MEASURE) {
            if (level == Density.DETAIL) drawHigh++;
            else drawLow++;
        }
        return true;
    }

    @SubscribeEvent
    public void endTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (scene != game.theWorld) {
            scene = game.theWorld;
            lookup.clear();
            visible.clear();
            ticks = 0;
        }
        if (scene == null || game.isGamePaused() || visible.isEmpty()) return;
        long began = MEASURE ? System.nanoTime() : 0;
        ++ticks;
        for (int index = visible.size() - 1; index >= 0; --index) {
            Garment g = visible.get(index);
            AbstractClientPlayer wearer = g.player;
            int sinceDrawn = ticks - g.drawn;
            if (sinceDrawn > 22 || wearer.worldObj != scene || wearer.isDead || wearer.getHealth() <= 0
                || wearer.isInvisible() || wearer.getHideCape() || wearer.getLocationCape() == null) {
                lookup.remove(wearer);
                Garment last = visible.remove(visible.size() - 1);
                if (index < visible.size()) visible.set(index, last);
                continue;
            }
            Density desired = detail(wearer);
            if (sinceDrawn > 3 || desired == null) {
                g.ready = false;
                continue;
            }
            update(g, desired);
        }
        if (MEASURE) tickNanos += System.nanoTime() - began;
    }

    private Density detail(AbstractClientPlayer p) {
        Entity eye = game.renderViewEntity;
        return eye == null ? null : Density.forDistance(eye.getDistanceSqToEntity(p), full);
    }

    private void update(Garment g, Density density) {
        AbstractClientPlayer p = g.player;
        boolean reset = !g.ready || g.discontinuity();
        // The attachment follows torso yaw, not the head (or the observer's camera).
        float facing = p.renderYawOffset;
        if (!Double.isFinite(p.posX) || !Double.isFinite(p.posY) || !Double.isFinite(p.posZ) || !Float.isFinite(facing)) {
            g.ready = false;
            return;
        }
        wind.update(reset ? 0 : p.posX - g.x, reset ? 0 : p.posY - g.y, reset ? 0 : p.posZ - g.z, facing,
            reset ? 0 : MathHelper.wrapAngleTo180_float(facing - g.heading), p.isInsideOfMaterial(Material.water),
            p.isSneaking(), p.getCurrentArmor(2) != null);
        if (reset) g.cloth.initialize(density, wind);
        else {
            g.cloth.changeDensity(density, regrid);
            g.cloth.advance(wind);
        }
        g.x = p.posX;
        g.y = p.posY;
        g.z = p.posZ;
        g.heading = facing;
        g.age = p.ticksExisted;
        g.ready = true;
        if (MEASURE) {
            if (density == Density.DETAIL) stepHigh++;
            else stepLow++;
        }
    }

    private static final class Garment {
        final AbstractClientPlayer player;
        final WovenCloth cloth;
        int drawn, age;
        double x, y, z;
        float heading;
        boolean ready;

        Garment(AbstractClientPlayer player) {
            this.player = player;
            cloth = new WovenCloth(player.getUniqueID().hashCode());
        }

        boolean discontinuity() {
            double dx = player.posX - x, dy = player.posY - y, dz = player.posZ - z;
            return player.ticksExisted < age || dx * dx + dy * dy + dz * dz > 64;
        }
    }
}
