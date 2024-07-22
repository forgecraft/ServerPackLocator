package net.forgecraft.serverpacklocator.server;

import net.forgecraft.serverpacklocator.FileChecksumValidator;
import net.forgecraft.serverpacklocator.ServerManifest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class ServerFileManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private final ServerSidedPackHandler serverSidedPackHandler;
    private final ServerManifest manifest;
    private final Set<String> exposedFiles;

    ServerFileManager(ServerSidedPackHandler packHandler, final List<ServerConfig.ExposedServerContent> exposedContent) {
        this.serverSidedPackHandler = packHandler;
        try {
            this.manifest = generateManifest(packHandler, exposedContent);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate the server manifest.", e);
        }
        this.exposedFiles = getExposedFiles(manifest);
    }

    public ServerManifest getManifest() {
        return manifest;
    }

    byte[] findFile(final String fileName) {
        if (!exposedFiles.contains(fileName)) {
            LOGGER.warn("Attempt to access non-exposed file {}", fileName);
            return null;
        }

        try {
            return Files.readAllBytes(serverSidedPackHandler.getGameDir().resolve(fileName));
        } catch (IOException e) {
            LOGGER.warn("Failed to read file {}", fileName);
            return null;
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

            var fileData = new ArrayList<ServerManifest.FileData>(files.size());
            for (var relativePath : files) {
                var fullPath = contentPath.resolve(relativePath);
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

        return new ServerManifest(directories);
    }

    private static Set<String> getExposedFiles(final ServerManifest manifest) {
        final ConcurrentHashMap<String, Boolean> exposedFiles = new ConcurrentHashMap<>();

        for (ServerManifest.DirectoryServerData directory : manifest.directories()) {
            for (ServerManifest.FileData file : directory.fileData()) {
                final String fileName = directory.path() + "/" + file.relativePath();
                exposedFiles.put(fileName, true);
            }
        }

        return exposedFiles.keySet();
    }
}
