package cpw.mods.forge.serverpacklocator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import cpw.mods.forge.serverpacklocator.utils.SyncType;

import java.util.List;

public record ServerManifest(List<DirectoryServerData> directories) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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
                                      SyncType syncType) {
    }

    public record FileData(String relativePath, long size, String checksum) {
    }
}
