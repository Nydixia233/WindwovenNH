package io.github.windwovennh.qa;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

/** A disposable test world, scripted actors and a loopback host/guest. Never in the production jar. */
@Mod(modid = "windwovennhfixture", name = "WindwovenNH Lab", version = "1", acceptableRemoteVersions = "*", dependencies = "before:windwovennh;before:trailcloth")
public final class ClothLab {
    private static final int ACTOR_ID = 881310;
    private static final String LOG = "[windwovennh-lab] ";
    private static final int FLIGHT_START = 80, CLIMB_END = 104, DIVE_START = 146, DIVE_END = 170;
    private static final int FLUTTER_START = 80, FLUTTER_END = 158, FLUTTER_CAPTURE_START = 98;
    private static final float FLUTTER_YAW = (float) -Math.toDegrees(Math.atan2(2.50, 2.65));
    private static final int REVIEW_STRAFE = 72, REVIEW_REVERSE = 112, REVIEW_FORWARD = 152;
    private static final int REVIEW_TURN = 180, REVIEW_TURN_END = 186, REVIEW_FORWARD_END = 213;
    private static final int REVIEW_CLIMB = 239, REVIEW_CLIMB_END = 271;
    private static final int REVIEW_DIVE = 291, REVIEW_DIVE_END = 318, REVIEW_END = 352;
    private static final int JUMP = 77, RUN_JUMP = 125, POWERED_START = 176, POWERED_END = 199;
    private static final int FREE_FALL = 219, VERTICAL_END = 284;
    private static final int COLLAR_HOP = 72, COLLAR_FALL = 111, COLLAR_BRAKE = 157, COLLAR_REST = 173, COLLAR_END = 215;
    private static final int TURN_START = 112, TURN_STOP = 136, TURN_REVERSE = 166, TURN_END = 219;
    private final Minecraft client = Minecraft.getMinecraft();
    private final Actor[] inScene = new Actor[24];
    private FrameLog recorder;
    private Properties labSettings;
    private File directory;
    private String runId, mode;
    private int[] scenes;
    private int index, phaseTicks, postLoginTicks, screenTicks, warmup, measure, port;
    private int remoteTicks, timeLimit, passes, reviewView;
    private boolean entered, listening, seenGuest, wantScreenshots, flutterReplay, reviewReplay, verticalReplay, collarReplay, turnReplay, motionReplay;
    private boolean evenMoBendsRenderer;
    private String shutter;
    private boolean measureLive, showcase, requireLegacyOnly, upgradeWorld;
    private double anchorX, anchorY, anchorZ;
    private ResourceLocation atlas;

    @Mod.EventHandler
    public void preflight(FMLPreInitializationEvent e) throws IOException {
        if (!e.getSide().isClient()) return;
        File marker = new File(client.mcDataDir, "windwovennh-lab.properties");
        if (!marker.isFile()) return;
        Properties p = new Properties();
        try (FileInputStream input = new FileInputStream(marker)) {
            p.load(input);
        }
        if (!Boolean.parseBoolean(p.getProperty("disposableInstance", "false"))) {
            throw new IOException("The fixture requires an explicitly disposable instance");
        }
        // The Prism GUI can retain stale per-instance JVM arguments. Enable counters here,
        // before either mod's pre-init and only in the explicitly marked fixture process.
        System.setProperty("windwovennh.metrics", "true");
        System.setProperty("trailcloth.metrics", "true");
        labSettings = p;
    }

    @Mod.EventHandler
    public void configure(FMLInitializationEvent e) throws Exception {
        Properties p = labSettings;
        if (p == null) return;
        recorder = new FrameLog();
        mode = p.getProperty("mode", "capture");
        showcase = "showcase".equals(mode);
        requireLegacyOnly = Boolean.parseBoolean(p.getProperty("requireLegacyOnly", "false"));
        upgradeWorld = Boolean.parseBoolean(p.getProperty("upgradeWorld", "false"));
        flutterReplay = "flutter".equals(mode);
        reviewReplay = "review".equals(mode);
        verticalReplay = "vertical".equals(mode);
        collarReplay = "collar".equals(mode);
        turnReplay = "turn".equals(mode);
        motionReplay = flutterReplay || reviewReplay || verticalReplay || collarReplay || turnReplay || "flight".equals(mode);
        if (!"capture".equals(mode) && !showcase && !"startup".equals(mode) && !motionReplay
            && !"host".equals(mode) && !"guest".equals(mode)) {
            throw new IOException("Invalid lab mode");
        }
        runId = p.getProperty("run", "trial");
        if (!runId.matches("[a-zA-Z0-9_-]{1,48}")) throw new IOException("Invalid lab run name");
        port = Integer.parseInt(p.getProperty("port", "25584"));
        warmup = Integer.parseInt(p.getProperty("warmupTicks", "240"));
        measure = Integer.parseInt(p.getProperty("sampleTicks", "360"));
        passes = Integer.parseInt(p.getProperty("passes", "1"));
        wantScreenshots = Boolean.parseBoolean(p.getProperty("screenshots", "false"));
        evenMoBendsRenderer = Boolean.parseBoolean(p.getProperty("evenMoBendsRenderer", "false"));
        directory = new File(client.mcDataDir, "windwovennh-lab");
        Files.createDirectories(directory.toPath());
        String[] requested = p.getProperty("scenes", "0,1,24").split(",");
        scenes = new int[requested.length * passes];
        for (int i = 0; i < scenes.length; i++) {
            int count = Integer.parseInt(requested[i % requested.length].trim());
            if (count != 0 && count != 1 && count != 6 && count != 24) throw new IOException("Unknown lab scene: " + count);
            scenes[i] = count;
        }
        FMLCommonHandler.instance().bus().register(this);
        System.out.println(LOG + mode + " " + runId + " armed; windwovennh=" + recorder.renamedPresent()
            + ", legacy=" + recorder.legacyPresent());
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END) return;
        if (++timeLimit == 20000) {
            System.out.println(LOG + "timeout " + runId);
            client.shutdown();
            return;
        }
        if (!entered) {
            if (client.currentScreen == null || ++screenTicks < 35) return;
            entered = true;
            client.gameSettings.pauseOnLostFocus = false;
            client.gameSettings.showCape = false;
            client.gameSettings.enableVsync = false;
            client.gameSettings.limitFramerate = 260;
            client.gameSettings.hideGUI = wantScreenshots;
            if ("startup".equals(mode)) {
                conclude("startup complete " + runId);
            } else if ("guest".equals(mode)) {
                FMLClientHandler.instance().connectToServerAtStartup("127.0.0.1", port);
            } else {
                WorldSettings world = new WorldSettings(48321903L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT);
                world.enableCommands();
                String save = upgradeWorld ? "trailcloth-sandbox" : "windwovennh-sandbox";
                if (upgradeWorld && !new File(client.mcDataDir, "saves/" + save + "/level.dat").isFile()) {
                    throw new IOException("Expected the previously saved disposable Trailcloth world");
                }
                System.out.println(LOG + "Opening disposable save: " + save);
                client.launchIntegratedServer(save, "WindwovenNH Lab (disposable)", world);
            }
            return;
        }
        if (client.theWorld == null || client.thePlayer == null) return;
        client.gameSettings.pauseOnLostFocus = false;
        if (client.currentScreen instanceof GuiIngameMenu) client.displayGuiScreen(null);
        if ("guest".equals(mode)) {
            if (!recorder.renamedPresent() || recorder.legacyPresent() || client.isIntegratedServerRunning()) {
                throw new IllegalStateException("Expected only WindwovenNH on the remote guest");
            }
            if (++remoteTicks == 60) {
                conclude("PASS: installed client spent 60 ticks in remote world");
            }
            return;
        }
        if ("host".equals(mode)) {
            updateHost();
            return;
        }
        if (client.currentScreen != null) return;
        if (atlas == null) {
            if (++postLoginTicks < 100) return;
            anchorX = Math.floor(client.thePlayer.posX) + 0.5;
            anchorZ = Math.floor(client.thePlayer.posZ) + 0.5;
            anchorY = client.theWorld.getTopSolidOrLiquidBlock((int) anchorX, (int) anchorZ);
            if (evenMoBendsRenderer) selectEvenMoBendsRenderer();
            atlas = registerSwatch();
            if (motionReplay) placeReplayActor();
            else placeActors();
        }
        if (motionReplay) {
            updateMotionReplay(inScene[0]);
            return;
        }
        final int count = scenes[index];
        focus(count);
        clearDaylight();
        if (++phaseTicks == warmup) {
            recorder.reset();
            measureLive = true;
        }
        if (wantScreenshots && (count == 1 || count == 6) && phaseTicks == warmup + measure / 2) shutter = "scene" + count;
        if (phaseTicks == warmup + measure) {
            measureLive = false;
            System.out.println(LOG + recorder.write(directory, runId, index + 1, count, client.displayWidth, client.displayHeight));
            if (++index < scenes.length) {
                placeActors();
            } else if (showcase) {
                clearActors();
                turnReplay = motionReplay = true;
                placeReplayActor();
            } else {
                if (requireLegacyOnly) {
                    recorder.assertLegacyOnly();
                    System.out.println(LOG + "PASS: both JARs loaded; only legacy rendered");
                }
                conclude("captures complete " + runId);
            }
        }
    }

    private static void selectEvenMoBendsRenderer() {
        // Galacticraft registers a more specific renderer for EntityOtherPlayerMP in GTNH.
        // Exercise the installed EvenMoBends renderer for fixture actors only; leave real players alone.
        RenderManager manager = RenderManager.instance;
        Render renderer = manager.getEntityClassRenderObject(EntityPlayer.class);
        if (renderer == null || !renderer.getClass().getName().equals("net.gobbob.mobends.client.renderer.entity.RenderBendsPlayer")) {
            throw new IllegalStateException("EvenMoBends is not the registered base player renderer");
        }
        manager.entityRenderMap.put(Actor.class, renderer);
        System.out.println(LOG + "Synthetic actors use the registered EvenMoBends renderer");
    }

    private void focus(int count) {
        double y = anchorY + (count == 1 ? 0.54 : 1.96);
        focusCamera(anchorX, y, anchorZ, y, anchorZ, 0, count == 1 ? 3 : 12);
    }

    private void placeReplayActor() {
        int height = collarReplay ? 140 : (verticalReplay || turnReplay) ? 0 : reviewReplay ? 25 : flutterReplay ? 3 : 10;
        int feature = turnReplay ? 11 : collarReplay ? 10 : verticalReplay ? 9 : reviewReplay ? 8 : flutterReplay ? 7 : 6;
        Actor actor = new Actor(client.theWorld, anchorX, anchorY + height, anchorZ + 6.6, 0, feature, atlas);
        if (!collarReplay || reviewView == 1) actor.setCurrentItemOrArmor(3, new ItemStack(Items.iron_chestplate));
        if (collarReplay && reviewView == 1) actor.setCurrentItemOrArmor(4, new ItemStack(Items.iron_helmet));
        client.theWorld.addEntityToWorld(ACTOR_ID, actor);
        inScene[0] = actor;
        phaseTicks = 0;
    }

    private void updateMotionReplay(Actor subject) throws IOException, IllegalAccessException {
        // Follow the actor in both histories so the mesh, not the camera, moves in the comparison.
        if (reviewReplay || collarReplay || turnReplay) {
            focusReviewCamera(subject);
        } else if (flutterReplay) {
            focusCamera(subject.posX - 2.50, subject.posY + 0.99, subject.posZ - 2.65,
                subject.posY + 0.99, subject.prevPosZ - 2.65, FLUTTER_YAW, 0);
        } else {
            focusCamera(subject.posX - 2.95, subject.posY + 0.91, subject.posZ - 1.65,
                subject.prevPosY + 0.91, subject.prevPosZ - 1.65, (float) -Math.toDegrees(Math.atan2(2.95, 1.65)), 0);
        }
        clearDaylight();
        int tick = subject.flightAge;
        if (tick == phaseTicks) return;
        phaseTicks = tick;
        if (tick == 60) {
            recorder.reset();
            measureLive = true;
        }
        if (wantScreenshots) {
            if (turnReplay) {
                boolean head = tick == 87 || tick == 99;
                boolean spin = tick >= TURN_START - 2 && tick <= TURN_STOP + 14;
                boolean opposite = tick >= TURN_REVERSE + 2 && tick <= TURN_REVERSE + 20;
                if (head || ((tick & 1) == 0 && (spin || opposite)) || tick == TURN_END - 5) shutter = "turn-t" + tick;
            } else if (collarReplay) {
                boolean hop = tick >= COLLAR_HOP - 1 && tick <= COLLAR_HOP + 12;
                boolean dive = tick >= COLLAR_FALL + 16 && tick <= COLLAR_REST + 12;
                if (((tick & 1) == 0 && (hop || dive)) || tick == COLLAR_END - 5) {
                    shutter = (reviewView == 0 ? "rear-t" : "side-t") + tick;
                }
            } else if (verticalReplay) {
                boolean jump = (tick >= JUMP - 2 && tick <= JUMP + 17) || (tick >= RUN_JUMP - 2 && tick <= RUN_JUMP + 17);
                boolean powered = tick >= POWERED_START - 2 && tick <= POWERED_END;
                boolean fall = tick >= FREE_FALL - 3 && tick <= VERTICAL_END - 14;
                if (jump || ((tick & 1) == 0 && (powered || fall))) shutter = "side-t" + tick;
            } else if (reviewReplay) {
                boolean reverse = tick >= REVIEW_REVERSE - 2 && tick <= REVIEW_REVERSE + 14;
                boolean turn = tick >= REVIEW_TURN - 2 && tick <= REVIEW_TURN_END + 12;
                boolean climb = tick >= REVIEW_CLIMB + 7 && tick <= REVIEW_CLIMB_END;
                boolean dive = tick >= REVIEW_DIVE + 5 && tick <= REVIEW_DIVE_END + 24;
                if (reverse || ((tick & 1) == 0 && (turn || climb || dive))) {
                    shutter = (reviewView == 0 ? "rear-t" : "side-t") + tick;
                }
            } else if (flutterReplay) {
                if (tick >= FLUTTER_CAPTURE_START && tick < FLUTTER_END && (tick & 1) == 0) shutter = "frame" + tick;
                if (tick == 76) shutter = "idle";
                if (tick == FLUTTER_END + 30) shutter = "stopped";
            } else {
                if (tick == 76) shutter = "hover";
                if (tick == FLIGHT_START + 14) shutter = "climb";
                if (tick == CLIMB_END + 4) shutter = "climb-stop";
                if (tick == DIVE_START + 14) shutter = "dive";
                if (tick == DIVE_END + 4) shutter = "dive-stop";
            }
        }
        if (tick == (turnReplay ? TURN_END : collarReplay ? COLLAR_END : verticalReplay ? VERTICAL_END : reviewReplay ? REVIEW_END : 215)) {
            measureLive = false;
            int sampleIndex = showcase ? scenes.length + (turnReplay ? 1 : 2 + reviewView) : reviewView + 1;
            System.out.println(LOG + recorder.write(directory, runId, sampleIndex, 1, client.displayWidth, client.displayHeight));
            if (showcase && turnReplay) {
                turnReplay = false;
                collarReplay = true;
                clearActors();
                placeReplayActor();
            } else if ((reviewReplay || collarReplay) && reviewView == 0) {
                reviewView = 1;
                client.theWorld.removeEntityFromWorld(ACTOR_ID);
                placeReplayActor();
            } else conclude(mode + " complete " + runId);
        }
    }

    private void clearDaylight() {
        client.theWorld.setWorldTime(4000);
        client.theWorld.setRainStrength(0);
        client.theWorld.setThunderStrength(0);
    }

    private void focusReviewCamera(Actor actor) {
        double side = turnReplay ? -2.65 : reviewView == 0 ? -1.05 : -3.10;
        double rear = turnReplay ? 2.70 : reviewView == 0 ? 3.60 : 1.45;
        double h = Math.toRadians(actor.renderYawOffset), old = Math.toRadians(actor.prevRenderYawOffset);
        float yaw = (float) Math.toDegrees(Math.atan2(side, rear));
        focusCamera(actor.posX + side * Math.cos(h) + rear * Math.sin(h), actor.posY + 1.0,
            actor.posZ + side * Math.sin(h) - rear * Math.cos(h), actor.prevPosY + 1.0,
            actor.prevPosZ + side * Math.sin(old) - rear * Math.cos(old), actor.renderYawOffset + yaw, reviewView == 0 ? 3 : 0);
        client.thePlayer.prevPosX = client.thePlayer.lastTickPosX = actor.prevPosX + side * Math.cos(old) + rear * Math.sin(old);
        client.thePlayer.prevRotationYaw = actor.prevRenderYawOffset + yaw;
    }

    private void focusCamera(double x, double y, double z, double prevY, double prevZ, float yaw, float pitch) {
        byte visibility = client.thePlayer.getDataWatcher().getWatchableObjectByte(16);
        if ((visibility & 2) == 0) client.thePlayer.getDataWatcher().updateObject(16, (byte) (visibility | 2));
        client.thePlayer.capabilities.isFlying = true;
        client.thePlayer.motionX = client.thePlayer.motionY = client.thePlayer.motionZ = 0;
        client.gameSettings.hideGUI = wantScreenshots;
        client.thePlayer.inventory.currentItem = 8;
        client.thePlayer.setPositionAndRotation(x, y, z, yaw, pitch);
        client.thePlayer.prevRotationYaw = yaw;
        client.thePlayer.prevRotationPitch = pitch;
        client.thePlayer.prevPosX = client.thePlayer.lastTickPosX = x;
        client.thePlayer.prevPosY = client.thePlayer.lastTickPosY = prevY;
        client.thePlayer.prevPosZ = client.thePlayer.lastTickPosZ = prevZ;
    }

    private void updateHost() throws Exception {
        if (recorder.productionPresent()) throw new IllegalStateException("Loopback host must not have WindwovenNH or Trailcloth");
        IntegratedServer server = client.getIntegratedServer();
        if (!listening) {
            server.setOnlineMode(false);
            server.func_147137_ag().addLanEndpoint(InetAddress.getByName("127.0.0.1"), port);
            listening = true;
            System.out.println(LOG + "READY: GTNH host has no production mod");
        }
        if (client.theWorld.playerEntities.size() > 1) {
            if (++remoteTicks >= 20 && !seenGuest) {
                seenGuest = true;
                System.out.println(LOG + "installed guest entered the host player list");
            }
        } else if (seenGuest && remoteTicks >= 20) {
            conclude("PASS: mod-free host accepted installed guest");
        }
    }

    private void conclude(String message) throws IOException {
        System.out.println(LOG + message);
        Files.write(new File(directory, runId + ".done").toPath(), message.getBytes(StandardCharsets.UTF_8));
        client.shutdown();
    }

    private void clearActors() {
        for (int i = 0; i < inScene.length; i++) {
            if (inScene[i] != null) client.theWorld.removeEntityFromWorld(ACTOR_ID + i);
            inScene[i] = null;
        }
    }

    private void placeActors() throws Exception {
        phaseTicks = 0;
        int count = scenes[index];
        clearActors();
        for (int i = 0; i < count; i++) {
            double depth = count == 1 ? 3.4 : count == 6 ? 6.6 : i < 8 ? 12.7 : i < 16 ? 33.3 : 61.6;
            double left = count == 1 ? 0 : count == 6 ? 0.96 * (i - 2.5) : (1.25 + (i / 8) * .5) * (i % 8 - 3.5);
            int surface = client.theWorld.getTopSolidOrLiquidBlock((int) Math.floor(anchorX + left), (int) (anchorZ + depth));
            boolean scriptedCheck = count == 6;
            int feature = scriptedCheck ? i : -1;
            ResourceLocation texture = feature == 2 ? null : atlas;
            Actor person = new Actor(client.theWorld, anchorX + left, surface, anchorZ + depth, i, feature, texture);
            client.theWorld.addEntityToWorld(ACTOR_ID + i, person);
            if (feature == 2) {
                ResourceLocation gt = new ResourceLocation("gregtech", "textures/DonorCape.png");
                Method set = person.getClass().getMethod("gt5u$setCape", ResourceLocation.class);
                set.invoke(person, gt);
                if (!gt.equals(person.getLocationCape()) || !person.func_152122_n()) throw new AssertionError("GT cape provider inactive");
                System.out.println(LOG + "GregTech donor cape resolved from installed mod");
            }
            if (count == 1 || (i % 3 == 0 && feature != 3)) person.setCurrentItemOrArmor(3, new ItemStack(Items.iron_chestplate));
            if (feature == 3) person.setInvisible(true);
            inScene[i] = person;
        }
        System.out.println(LOG + "scene=" + (index + 1) + " actors=" + count);
    }

    @SubscribeEvent
    public void endFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (shutter != null) {
            String file = runId + "-" + shutter + ".png";
            shutter = null;
            ScreenShotHelper.saveScreenshot(directory, file, client.displayWidth, client.displayHeight, client.getFramebuffer());
            recorder.discardInterval();
        } else if (measureLive) recorder.frame();
    }

    private ResourceLocation registerSwatch() {
        BufferedImage b = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = b.createGraphics();
        canvas.setColor(new Color(0xDBB24F));
        canvas.fillRect(0, 0, 22, 17);
        canvas.setColor(new Color(0x702A59));
        canvas.fillRect(1, 1, 10, 16);
        canvas.setColor(new Color(0x225A67));
        canvas.fillRect(12, 1, 10, 16);
        canvas.setColor(new Color(0xF9DE86));
        for (int r = 0; r < 7; r++) {
            canvas.fillRect(2 + r, 3 + r, 1, 2);
            canvas.fillRect(14 + r, 3 + r, 1, 2);
        }
        canvas.setColor(new Color(0xF2F4E9));
        canvas.fillRect(3, 12, 2, 3);
        canvas.fillRect(14, 12, 2, 3);
        canvas.dispose();
        return client.getTextureManager().getDynamicTextureLocation("windwovennh-lab-swatch", new DynamicTexture(b));
    }

    private static final class Actor extends EntityOtherPlayerMP {
        private final double baseX, baseY, baseZ;
        private final ResourceLocation swatch;
        private final int stagger, check;
        private int flightAge;
        private double replayVertical;

        Actor(WorldClient world, double x, double y, double z, int number, int check, ResourceLocation swatch) {
            super(world, new GameProfile(new UUID(0x3ca6db17a9374cf2L, 0xaeab6b85bee171f1L + number), "Weaver" + number));
            baseX = x;
            baseY = y;
            baseZ = z;
            stagger = number;
            this.check = check;
            this.swatch = swatch;
            ignoreFrustumCheck = true;
            setPositionAndRotation(x, y, z, 0, 0);
            field_71091_bM = field_71094_bP = x;
            field_71096_bN = field_71095_bQ = y;
            field_71097_bO = field_71085_bR = z;
        }

        @Override
        public boolean func_152122_n() {
            return swatch != null || super.func_152122_n();
        }

        @Override
        public ResourceLocation getLocationCape() {
            return swatch == null ? super.getLocationCape() : swatch;
        }

        @Override
        public boolean getHideCape() {
            return check == 4;
        }

        @Override
        public boolean isInsideOfMaterial(Material material) {
            return check == 5 && material == Material.water;
        }

        @Override
        public void onUpdate() {
            if (check == 11) {
                advanceTurn();
                return;
            }
            if (check == 10) {
                advanceCollar();
                return;
            }
            if (check == 9) {
                advanceVertical();
                return;
            }
            if (check == 8) {
                advanceReview();
                return;
            }
            if (check == 7) {
                int t = ++flightAge;
                int forwardTicks = Math.max(0, Math.min(t, FLUTTER_END) - FLUTTER_START);
                prevPosZ = lastTickPosZ = posZ;
                setPosition(baseX, baseY, baseZ + 0.90 * forwardTicks);
                field_71097_bO = field_71085_bR;
                field_71085_bR += (posZ - field_71085_bR) * .26;
                return;
            }
            if (check == 6) {
                int t = ++flightAge;
                int climb = Math.max(0, Math.min(t, CLIMB_END) - FLIGHT_START);
                int dive = Math.max(0, Math.min(t, DIVE_END) - DIVE_START);
                prevPosY = lastTickPosY = posY;
                setPosition(baseX, baseY + 1.4 * (climb - dive), baseZ);
                field_71096_bN = field_71095_bQ;
                field_71095_bQ += (posY - field_71095_bQ) * .26;
                return;
            }
            double t = (ticksExisted + stagger * 2) * 0.20;
            double z = baseZ + 0.52 * Math.sin(t * 0.90);
            double x = baseX + 0.29 * Math.sin(t * 1.28);
            double y = baseY + (check == 0 && ticksExisted % 100 < 30 ? 0.12 * Math.abs(Math.sin(t * 2)) : 0);
            boolean jump = check == 5 && ticksExisted % 128 == 0;
            prevPosX = lastTickPosX = posX;
            prevPosY = lastTickPosY = posY;
            prevPosZ = lastTickPosZ = posZ;
            setPosition(x, y, z + (jump ? 12 : 0));
            prevRenderYawOffset = prevRotationYaw = prevRotationYawHead = renderYawOffset;
            renderYawOffset = rotationYaw = rotationYawHead = (float) (35 * Math.sin(t * 0.8) + 12 * Math.cos(t * 1.9));
            setSneaking(check == 1 && ticksExisted % 80 >= 16);
            setSprinting(check == 0);
            field_71091_bM = field_71094_bP;
            field_71096_bN = field_71095_bQ;
            field_71097_bO = field_71085_bR;
            field_71094_bP += (x - field_71094_bP) * .26;
            field_71095_bQ += (y - field_71095_bQ) * .26;
            field_71085_bR += (z - field_71085_bR) * .26;
            prevLimbSwingAmount = limbSwingAmount;
            limbSwingAmount = 0.61F;
            limbSwing += 0.59F;
        }

        private void advanceReview() {
            int t = ++flightAge;
            float yaw = 15.0F * Math.max(0, Math.min(t, REVIEW_TURN_END) - REVIEW_TURN);
            double dx = 0, dz = 0;
            if (t > REVIEW_STRAFE && t <= REVIEW_FORWARD) {
                dx = t <= REVIEW_REVERSE ? 0.70 : -0.70;
                dz = 0.40;
            } else if (t > REVIEW_FORWARD && t <= REVIEW_FORWARD_END) {
                // Yaw leads the change of travel direction by two ticks during the fast turn.
                double angle = Math.toRadians(15.0 * Math.max(0, Math.min(t - 2, REVIEW_TURN_END) - REVIEW_TURN));
                dx = -0.78 * Math.sin(angle);
                dz = 0.78 * Math.cos(angle);
            }
            double dy = t > REVIEW_CLIMB && t <= REVIEW_CLIMB_END ? 1.4 : t > REVIEW_DIVE && t <= REVIEW_DIVE_END ? -2.4 : 0;
            moveReplay(dx, dy, dz, yaw);
        }

        private void advanceTurn() {
            int t = ++flightAge;
            // First pivot only the head. Then turn the carrier in place, including wrap at +/-180.
            float oldHead = rotationYawHead;
            float yaw = (float) (360 * ramp(t, TURN_START, TURN_STOP) - 180 * ramp(t, TURN_REVERSE, TURN_REVERSE + 12));
            yaw = (yaw + 540) % 360 - 180;
            moveReplay(0, 0, 0, yaw);
            rotationYawHead = yaw + (float) (36 * ramp(t, 75, 85) - 71 * ramp(t, 88, 98) + 35 * ramp(t, 100, 107));
            prevRotationYawHead = oldHead;
        }

        private void advanceCollar() {
            int t = ++flightAge;
            if (t == COLLAR_HOP) replayVertical = .42;
            if (t == COLLAR_FALL) replayVertical = 0;
            double brake = 1 - ramp(t, COLLAR_BRAKE, COLLAR_REST);
            double forward = .54 * ramp(t, 96, 107) * brake;
            double side = (.29 * ramp(t, 101, 112) - .67 * ramp(t, 135, 142)) * brake;
            float yaw = (float) (58 * ramp(t, 134, 145));
            double travel = Math.toRadians(58 * ramp(t, 138, 149));
            double dx = Math.cos(travel) * side - Math.sin(travel) * forward;
            double dz = Math.sin(travel) * side + Math.cos(travel) * forward;
            boolean diving = t >= COLLAR_FALL && t < COLLAR_REST;
            if (diving) replayVertical = Math.max(-2.24, (replayVertical - .077) * .985);
            double dy = diving ? replayVertical * brake : t < COLLAR_FALL ? Math.max(baseY - posY, replayVertical) : 0;
            moveReplay(dx, dy, dz, yaw);
            if (!diving && t < COLLAR_FALL) replayVertical = posY > baseY ? (replayVertical - .08) * .98 : 0;
        }

        private static double ramp(int tick, int from, int to) {
            return Math.max(0, Math.min(1, (tick - from) / (double) (to - from)));
        }

        private void advanceVertical() {
            int t = ++flightAge;
            if (t == JUMP || t == RUN_JUMP) replayVertical = 0.42;
            if (t == FREE_FALL) replayVertical = -0.08 * 0.98;
            boolean powered = t >= POWERED_START && t <= POWERED_END;
            double dy = powered ? 1.4 : Math.max(baseY - posY, replayVertical);
            double dz = t >= RUN_JUMP - 28 && t <= RUN_JUMP + 25 ? 0.32 : 0;
            moveReplay(0, dy, dz, 0);
            if (!powered && replayVertical != 0) replayVertical = posY > baseY ? (replayVertical - 0.08) * 0.98 : 0;
        }

        private void moveReplay(double dx, double dy, double dz, float yaw) {
            prevPosX = lastTickPosX = posX;
            prevPosY = lastTickPosY = posY;
            prevPosZ = lastTickPosZ = posZ;
            setPosition(posX + dx, posY + dy, posZ + dz);
            prevRenderYawOffset = prevRotationYaw = prevRotationYawHead = renderYawOffset;
            renderYawOffset = rotationYaw = rotationYawHead = yaw;
            field_71091_bM = field_71094_bP;
            field_71096_bN = field_71095_bQ;
            field_71097_bO = field_71085_bR;
            field_71094_bP += (posX - field_71094_bP) * .26;
            field_71095_bQ += (posY - field_71095_bQ) * .26;
            field_71085_bR += (posZ - field_71085_bR) * .26;
            prevLimbSwingAmount = limbSwingAmount;
            limbSwingAmount += ((dx != 0 || dz != 0 ? .55F : 0) - limbSwingAmount) * .35F;
            limbSwing += limbSwingAmount;
        }
    }
}
