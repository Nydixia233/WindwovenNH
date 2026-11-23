package io.github.windwovennh.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.FloatBuffer;

import io.github.windwovennh.cloth.CapeFrame;
import net.minecraft.client.model.ModelBiped;
import org.apache.logging.log4j.LogManager;

/** Resolves the optional EvenMoBends attachment API only if its cape path runs. */
final class BendsAttachment {
    private static final String ROOT = "net.gobbob.mobends.";
    private static final String BODY = "body";
    private static boolean failed;

    static boolean available(ModelBiped model) {
        if (failed) return false;
        Links api = Links.INSTANCE;
        if (api == null || !api.modelType.isInstance(model)) return false;
        try {
            // If the renderer delegates its cape to another provider, leave that path alone.
            return !(boolean) api.delegated.invokeExact();
        } catch (Throwable failure) {
            return fallback(failure);
        }
    }

    static boolean captureRoot(ModelBiped model, CapeFrame out) {
        if (failed) return false;
        Links api = Links.INSTANCE;
        if (api == null || !api.modelType.isInstance(model)) return false;
        try {
            Object angles = (Object) api.rotation.invokeExact(model);
            float x = (float) api.x.invokeExact(angles), y = (float) api.y.invokeExact(angles), z = (float) api.z.invokeExact(angles);
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return false;
            // Convert the root through the biped flip (X and Y); the cape mesh flip cancels its draw rotation.
            out.setRoot(x, y, z);
            return true;
        } catch (Throwable failure) {
            return fallback(failure);
        }
    }

    static boolean frame(ModelBiped model, float scale, CapeFrame root, CapeFrame out, FloatBuffer matrix) {
        Links api = Links.INSTANCE;
        try {
            Object geometry = (Object) api.geometry.invokeExact(model);
            if (geometry == null) return false;
            Object transform = (Object) api.transform.invokeExact(geometry, BODY, scale);
            matrix.clear();
            FloatBuffer written = (FloatBuffer) api.write.invokeExact(transform, matrix);
            // ClothSurface flips X/Z, and the cape attachment flips them back. Use the native bone basis.
            return out.fromBone(root, written.get(0), written.get(1), written.get(2),
                written.get(4), written.get(5), written.get(6), written.get(8), written.get(9), written.get(10));
        } catch (Throwable failure) {
            return fallback(failure);
        }
    }

    private static boolean fallback(Throwable failure) {
        if (failure instanceof Error && !(failure instanceof LinkageError)) throw (Error) failure;
        if (!failed) LogManager.getLogger("WindwovenNH").warn("Could not use the EvenMoBends cape attachment; keeping its cape.", failure);
        failed = true;
        return false;
    }

    private static final class Links {
        private static final Links INSTANCE = resolve();
        final Class<?> modelType;
        final MethodHandle geometry, transform, write, delegated, rotation, x, y, z;

        Links() throws ReflectiveOperationException {
            ClassLoader loader = BendsAttachment.class.getClassLoader();
            modelType = Class.forName(ROOT + "client.model.entity.ModelBendsPlayer", false, loader);
            Class<?> geometryType = Class.forName(ROOT + "client.animation.BendsGeometry", false, loader);
            Class<?> provider = Class.forName(ROOT + "compat.WaveyCapesCompat", false, loader);
            Class<?> angles = Class.forName(ROOT + "util.SmoothVector3f", false, loader);
            Class<?> matrix = Class.forName("org.joml.Matrix4f", false, loader);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            geometry = lookup.findVirtual(modelType, "geometry", MethodType.methodType(geometryType))
                .asType(MethodType.methodType(Object.class, ModelBiped.class));
            transform = lookup.findVirtual(geometryType, "transform", MethodType.methodType(matrix, String.class, float.class))
                .asType(MethodType.methodType(Object.class, Object.class, String.class, float.class));
            write = lookup.findVirtual(matrix, "get", MethodType.methodType(FloatBuffer.class, FloatBuffer.class))
                .asType(MethodType.methodType(FloatBuffer.class, Object.class, FloatBuffer.class));
            delegated = lookup.findStatic(provider, "drawsCape", MethodType.methodType(boolean.class));
            rotation = lookup.findVirtual(modelType, "rootRotation", MethodType.methodType(angles))
                .asType(MethodType.methodType(Object.class, ModelBiped.class));
            x = component(lookup, angles, "getX");
            y = component(lookup, angles, "getY");
            z = component(lookup, angles, "getZ");
        }

        private static MethodHandle component(MethodHandles.Lookup lookup, Class<?> type, String name) throws ReflectiveOperationException {
            return lookup.findVirtual(type, name, MethodType.methodType(float.class))
                .asType(MethodType.methodType(float.class, Object.class));
        }

        private static Links resolve() {
            try {
                return new Links();
            } catch (ReflectiveOperationException | LinkageError | SecurityException failure) {
                fallback(failure);
                return null;
            }
        }
    }
}
