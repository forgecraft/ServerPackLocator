package net.forgecraft.serverpacklocator.utils;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressionUtils {
    public abstract static class CompressionMethod {
        CompressionMethod() {}

        public abstract @NotNull String name();

        public abstract @NotNull InputStream decompress(InputStream input) throws IOException;

        public abstract @NotNull OutputStream compress(OutputStream input) throws IOException;
    }

    public static class Gzip extends CompressionMethod {
        public static final CompressionMethod INSTANCE = new Gzip();
        public static final String NAME = "gzip";

        @Override
        public @NotNull String name() {
            return NAME;
        }

        @Override
        public @NotNull InputStream decompress(InputStream input) throws IOException {
            return new GZIPInputStream(input);
        }

        @Override
        public @NotNull OutputStream compress(OutputStream input) throws IOException {
            return new GZIPOutputStream(input);
        }
    }

    public static CompressionMethod methodFromName(String name) {
        return switch (name) {
            case Gzip.NAME -> Gzip.INSTANCE;
            case null, default -> null;
        };
    }
}
