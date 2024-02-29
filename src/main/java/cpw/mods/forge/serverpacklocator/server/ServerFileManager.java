package cpw.mods.forge.serverpacklocator.server;

import cpw.mods.forge.serverpacklocator.FileChecksumValidator;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ServerFileManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private final ServerSidedPackHandler serverSidedPackHandler;
    private final ServerManifest manifest;
    private final Set<String> exposedFiles;

    ServerFileManager(ServerSidedPackHandler packHandler, final List<ServerConfig.ExposedServerContent> exposedContent) {
        this.serverSidedPackHandler = packHandler;
        this.manifest = GenerateManifest(packHandler, exposedContent);
        this.exposedFiles = CalculateExposedFiles(manifest);
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

    private static ServerManifest GenerateManifest(final ServerSidedPackHandler serverSidedPackHandler, final List<ServerConfig.ExposedServerContent> exposedContent) {
        LOGGER.debug("Generating manifest");
        final ServerManifest.ServerManifestBuilder builder = new ServerManifest.ServerManifestBuilder();

        final Path serverPath = serverSidedPackHandler.getGameDir();
        exposedContent.forEach((content) -> {
            final Path contentPath = serverPath.resolve(content.getDirectory().getPath());

            contentPath.toFile().getParentFile().mkdirs();

            final List<Path> files = new ArrayList<>();
            try (Stream<Path> walk = Files.list(contentPath)) {
                walk
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        files.add(contentPath.relativize(path));
                    });
            } catch (IOException e) {
                LOGGER.error("Failed to walk directory {} for content selection", contentPath, e);
                return;
            }

            if (!files.isEmpty()) {
                builder.withDirectory(dirBuilder -> {
                    dirBuilder.setName(content.getName());
                    dirBuilder.setPath(content.getDirectory().getPath());
                    dirBuilder.setTargetPath(content.getDirectory().getTargetPath());
                    dirBuilder.setSyncType(content.getSyncType());

                    files.forEach(relativePath -> {
                        final Path fullPath = contentPath.resolve(relativePath);
                        dirBuilder.withFileData(fileBuilder -> {
                            fileBuilder.setFileName(relativePath.toString());
                            fileBuilder.setChecksum(FileChecksumValidator.computeChecksumFor(fullPath));
                        });
                    });
                });
            } else {
                LOGGER.warn("No files found in directory {} for content selection", contentPath);
            }
        });

        return builder.createServerManifest();
    }

    private static Set<String> CalculateExposedFiles(final ServerManifest manifest) {
        final ConcurrentHashMap<String, Boolean> exposedFiles = new ConcurrentHashMap<>();

        for (ServerManifest.DirectoryServerData directory : manifest.getDirectories()) {
            for (ServerManifest.FileData file : directory.getFileData()) {
                final String fileName = directory.getPath() + "/" + file.getFileName();
                exposedFiles.put(fileName, true);
            }
        }

        return exposedFiles.keySet();
    }
}
