package io.github.windwovennh;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/** First-launch copy. Never open the legacy file through Forge's potentially writing config loader. */
final class LegacyConfig {
    private LegacyConfig() {}

    /** @return true only when this launch published the copy */
    static boolean prepare(Path current, String oldName) throws IOException {
        if (regularFileIfPresent(current)) return false;
        Path previous = current.resolveSibling(oldName);
        if (!regularFileIfPresent(previous)) return false;

        // Copy to a sibling first, so a failed read never leaves a partial authoritative config.
        Path staged = Files.createTempFile(current.toAbsolutePath().getParent(), ".windwovennh-", ".cfg.tmp");
        try {
            Files.copy(previous, staged, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(staged, current); // deliberately without REPLACE_EXISTING
            } catch (FileAlreadyExistsException race) {
                if (regularFileIfPresent(current)) return false;
                throw race;
            }
            return true;
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static boolean regularFileIfPresent(Path file) throws IOException {
        try {
            if (!Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile()) {
                throw new IOException("Not a regular config file: " + file.getFileName());
            }
            return true;
        } catch (NoSuchFileException absent) {
            return false;
        }
    }
}
