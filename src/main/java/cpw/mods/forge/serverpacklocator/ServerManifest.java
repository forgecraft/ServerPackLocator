package cpw.mods.forge.serverpacklocator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cpw.mods.forge.serverpacklocator.utils.SyncType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ServerManifest {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private List<DirectoryServerData> directories;

    public static ServerManifest loadFromStream(final InputStream stream) {
        return GSON.fromJson(new InputStreamReader(stream), ServerManifest.class);
    }

    public ServerManifest() {
    }

    public ServerManifest(List<DirectoryServerData> directories) {
        this.directories = directories;
    }

    public List<DirectoryServerData> getDirectories() {
        return directories;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static class DirectoryServerData {
        private String name;
        private String path;
        private String targetPath;
        private List<FileData> fileData;
        private SyncType syncType;


        public DirectoryServerData() {
        }

        public DirectoryServerData(String name, String path, String targetPath, List<FileData> fileData, SyncType syncType) {
            this.name = name;
            this.path = path;
            this.targetPath = targetPath;
            this.fileData = fileData;
            this.syncType = syncType;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public String getTargetPath() {
            return targetPath;
        }

        public List<FileData> getFileData() {
            return fileData;
        }

        public SyncType getSyncType() {
            return syncType;
        }

    }

    public static class FileData {
        private String checksum;
        private String fileName;

        public FileData() {
        }

        public FileData(String checksum, String fileName) {
            this.checksum = checksum;
            this.fileName = fileName;
        }

        public String getChecksum() {
            return checksum;
        }

        public String getFileName() {
            return fileName;
        }

    }


    public static ServerManifest load(final Path path) {
        try (BufferedReader json = Files.newBufferedReader(path)) {
            return GSON.fromJson(json, ServerManifest.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void save(final Path path) {
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path)) {
            bufferedWriter.write(toJson());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class ServerManifestBuilder {

        private List<DirectoryServerData> directories = new ArrayList<>();

        public ServerManifestBuilder setDirectories(List<DirectoryServerData> directories) {
            this.directories = new ArrayList<>(directories);
            return this;
        }

        public ServerManifestBuilder withDirectory(Consumer<DirectoryServerDataBuilder> builderConsumer) {
            DirectoryServerDataBuilder builder = new DirectoryServerDataBuilder();
            builderConsumer.accept(builder);
            this.directories.add(builder.createDirectoryServerData());
            return this;
        }

        public ServerManifest createServerManifest() {
            return new ServerManifest(directories);
        }

    }

    public static class DirectoryServerDataBuilder {

        private String name;
        private String path;
        private String targetPath;
        private List<FileData> fileData = new ArrayList<>();
        private SyncType syncType;

        public DirectoryServerDataBuilder setName(String name) {
            this.name = name;
            return this;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public void setTargetPath(String targetPath) {
            this.targetPath = targetPath;
        }

        public void setSyncType(SyncType syncType) {
            this.syncType = syncType;
        }

        public DirectoryServerDataBuilder setFileData(List<FileData> fileData) {
            this.fileData = new ArrayList<>(fileData);
            return this;
        }

        public DirectoryServerDataBuilder withFileData(Consumer<FileDataBuilder> builderConsumer) {
            FileDataBuilder builder = new FileDataBuilder();
            builderConsumer.accept(builder);
            this.fileData.add(builder.createFileData());
            return this;
        }

        public DirectoryServerData createDirectoryServerData() {
            return new DirectoryServerData(name, path, targetPath, fileData, syncType);
        }

    }

    public static class FileDataBuilder {

        private String checksum;
        private String fileName;

        public FileDataBuilder setChecksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        public FileDataBuilder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public FileData createFileData() {
            return new FileData(checksum, fileName);
        }

    }

}
