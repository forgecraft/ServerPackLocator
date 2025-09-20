package net.forgecraft.serverpacklocator;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.MoreFiles;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileChecksumValidator {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    public static HashCode computeChecksumFor(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return MoreFiles.asByteSource(file).hash(Hashing.sha256());
        } catch (IOException e) {
            LOGGER.warn("Failed to compute hash for {}", file, e);
            return null;
        }
    }
}
