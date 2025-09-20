package net.forgecraft.serverpacklocator.server;

import com.mojang.logging.LogUtils;
import net.forgecraft.serverpacklocator.FileChecksumValidator;
import net.forgecraft.serverpacklocator.ServerManifest;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ServerFileManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerSidedPackHandler serverSidedPackHandler;
    private ServerManifest manifest;

    private final Map<String, ExposedFile> exposedFilesByName;

    ServerFileManager(ServerSidedPackHandler packHandler) {
        this.serverSidedPackHandler = packHandler;

        rebuildManifest();
        this.exposedFilesByName = getExposedFiles(manifest, packHandler.getGameDir());

        createWatchService();
    }

    public ServerManifest getManifest() {
        return manifest;
    }

    @Nullable
    ExposedFile getExposedFile(final String fileName) {
        return exposedFilesByName.get(fileName);
    }

    public void rebuildManifest() {
        try {
            this.manifest = generateManifest(this.serverSidedPackHandler, serverSidedPackHandler.getConfig().getServer().getExposedServerContent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate the server manifest.", e);
        }
    }

    private static ServerManifest generateManifest(ServerSidedPackHandler serverSidedPackHandler, List<ServerConfig.ExposedServerContent> exposedContent) throws IOException {
        LOGGER.debug("Generating manifest");

        var directories = new ArrayList<ServerManifest.DirectoryServerData>();

        var serverPath = serverSidedPackHandler.getGameDir();
        for (var content : exposedContent) {
            var contentPath = serverPath.resolve(content.getDirectory().getPath());
            Files.createDirectories(contentPath);

            var files = new ArrayList<Path>();
            if (!content.isRecursive()) {
                try (Stream<Path> walk = Files.list(contentPath)) {
                    walk.filter(Files::isRegularFile)
                            .forEach(path -> files.add(contentPath.relativize(path)));
                }
            } else {
                // Recursive directory listing
                try (Stream<Path> walk = Files.walk(contentPath)) {
                    walk.filter(Files::isRegularFile)
                            .forEach(path -> files.add(contentPath.relativize(path)));
                }
            }

            if (files.isEmpty()) {
                LOGGER.warn("No files found in directory {} for content selection", contentPath);
                continue; // Skip empty directories
            }

            var blacklist = content.getDirectory().getBlacklistedFiles();
            var fileData = new ArrayList<ServerManifest.FileData>(files.size());

            // Get the systems file matcher
            FileSystem fileSystem = contentPath.getFileSystem();
            for (var relativePath : files) {
                var fullPath = contentPath.resolve(relativePath);
                var relPath = relativePath.toString();

                boolean skip = false;
                for (var blacklistedFile : blacklist) {
                    if (fileSystem.getPathMatcher("glob:" + blacklistedFile).matches(relativePath)) {
                        LOGGER.debug("Skipping blacklisted file {}", relPath);
                        skip = true;
                    }
                }

                if (skip) {
                    continue;
                }

                fileData.add(new ServerManifest.FileData(
                        relativePath.toString(),
                        Files.size(fullPath),
                        FileChecksumValidator.computeChecksumFor(fullPath)
                ));
            }

            directories.add(new ServerManifest.DirectoryServerData(
                    content.getName(),
                    content.getDirectory().getPath(),
                    content.getDirectory().getTargetPath(),
                    fileData,
                    content.getSyncType(),
                    content.shouldRemoveDanglingFiles()
            ));
        }

        // Write the manifest to the server directory
        var gameDir = FMLPaths.GAMEDIR.get();
        var splDirectory = gameDir.resolve("spl");

        if (!Files.exists(splDirectory)) {
            Files.createDirectories(splDirectory);
        }

        var manifestPath = splDirectory.resolve("manifest.json");

        ServerManifest serverManifest = new ServerManifest(directories);
        Files.writeString(manifestPath, serverManifest.toJson());

        return serverManifest;
    }

    private static Map<String, ExposedFile> getExposedFiles(final ServerManifest manifest, final Path rootDirectory) {
        return manifest.directories().stream()
                .flatMap(directory -> directory.fileData().stream()
                        .map(file -> {
                            final String name = directory.path() + "/" + file.relativePath();
                            return new ExposedFile(name, rootDirectory.resolve(name), file.size());
                        })
                )
                .collect(Collectors.toMap(ExposedFile::name, f -> f));
    }

    private void createWatchService() {
        LOGGER.info("Starting file watch service");
        Thread.ofPlatform()
                .name("SPL File Watcher")
                .daemon(true)
                .start(new ServerFileWatchService(this));
    }

    public record ExposedFile(String name, Path path, long size) {
    }
}
