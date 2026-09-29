package net.forgecraft.serverpacklocator;

import com.google.common.hash.HashCode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.forgecraft.serverpacklocator.utils.SyncType;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public record ServerManifest(List<DirectoryServerData> directories) {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(HashCode.class, new TypeAdapter<HashCode>() {
                @Override
                public void write(JsonWriter out, HashCode value) throws IOException {
                    out.value(value.toString());
                }

                @Override
                public HashCode read(JsonReader in) throws IOException {
                    return HashCode.fromString(in.nextString().toLowerCase(Locale.ROOT));
                }
            })
            .setPrettyPrinting()
            .create();

    public static ServerManifest fromString(String content) {
        try {
            return GSON.fromJson(content, ServerManifest.class);
        } catch (JsonSyntaxException e) {
            throw new RuntimeException("Failed to parse manifest received from server: " + e, e);
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public record DirectoryServerData(String name, String path, String targetPath, List<FileData> fileData,
                                      SyncType syncType, boolean shouldRemoveDanglingFiles) {
    }

    public record FileData(String relativePath, long size, HashCode checksum) {
    }
}
