package io.github.windwovennh;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class LegacyConfigChecks {
    private static final String OLD = "trailcloth.cfg";

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("windwovennh-config-test-");
        Path old = dir.resolve(OLD);
        Path current = dir.resolve("windwovennh.cfg");
        try {
            expect(!LegacyConfig.prepare(current, OLD) && !Files.exists(current), "fresh launch uses Forge defaults");
            byte[] legacy = bytes("# previous settings\r\ncloth {\r\n    B:enabled=false\r\n    S:quality=FULL\r\n}\r\n");
            Files.write(old, legacy);
            expect(LegacyConfig.prepare(current, OLD), "copy legacy once");
            expect(Arrays.equals(legacy, Files.readAllBytes(current)), "exact config bytes, including comments");
            expect(Arrays.equals(legacy, Files.readAllBytes(old)), "legacy remains untouched");
            byte[] preferred = bytes("cloth {\n    B:enabled=true\n    S:quality=BALANCED\n}\n");
            Files.write(current, preferred);
            expect(!LegacyConfig.prepare(current, OLD), "new config is authoritative on subsequent launches");
            expect(Arrays.equals(preferred, Files.readAllBytes(current)), "no clobber");
            expect(Arrays.equals(legacy, Files.readAllBytes(old)), "old config still usable after rollback");
            Files.delete(current);
            Files.delete(old);
            Files.createDirectory(old);
            expectIOException(current);
            expect(!Files.exists(current) && Files.isDirectory(old), "legacy read failure leaves both paths alone");
            Files.delete(old);
            Files.write(old, legacy);
            Files.createDirectory(current);
            expectIOException(current);
            expect(Files.isDirectory(current) && Arrays.equals(legacy, Files.readAllBytes(old)), "unexpected target is not overwritten");
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.cfg.tmp")) {
                expect(!files.iterator().hasNext(), "no temporary config leaked");
            }
        } finally {
            Files.deleteIfExists(current);
            Files.deleteIfExists(old);
            Files.deleteIfExists(dir);
        }
        System.out.println("PASS: legacy config copy, precedence, retry and rollback");
    }

    private static void expectIOException(Path target) throws Exception {
        try {
            LegacyConfig.prepare(target, OLD);
            throw new AssertionError("Unsafe path was accepted");
        } catch (IOException expected) {
            // Production leaves the cape unmodified and reports a warning.
        }
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static void expect(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
