package net.forgecraft.serverpacklocator.server;

import net.forgecraft.serverpacklocator.ServerManifest;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerFileWatchService implements Runnable {
    private static final Logger LOGGER = LogManager.getLogger();

    private final ServerFileManager fileManager;
    private final WatchService watchService;

    private final List<Path> watchedPaths = new ArrayList<>();
    private final Map<Path, WatchKey> watchKeys = new ConcurrentHashMap<>();

    private boolean lockedForRebuild = false;

    public ServerFileWatchService(ServerFileManager fileManager) {
        this.fileManager = fileManager;

        try {
            this.watchService = FMLPaths.GAMEDIR.get().getFileSystem().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.rebuildWatcherPaths();
        this.registerWatchers();
    }

    @Override
    public void run() {
        while (true) {
            // Die if interrupted
            if (Thread.interrupted()) {
                return;
            }

            // Stop watching if we're rebuilding the watch list
            if (lockedForRebuild) {
                continue;
            }

            try {
                var key = watchService.take();
                if (key == null) {
                    continue;
                }

                for (var event : key.pollEvents()) {
                    var kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    var path = (Path) event.context();
                    LOGGER.info("File event: {} {}", kind, path);

                    if (!lockedForRebuild) {
                        lockedForRebuild = true;

                        // Rebuild the manifest to keep the data consistent with what clients will be sent
                        this.fileManager.rebuildManifest();

                        if (kind != StandardWatchEventKinds.ENTRY_MODIFY) {
                            // Only rebuild the watch list if the file was created or deleted
                            rebuildWatcherPaths();
                            registerWatchers();
                        }
                        lockedForRebuild = false;
                    }
                }

                key.reset();
            } catch (InterruptedException e) {
                LOGGER.error("Watcher thread interrupted", e);
                return;
            }
        }
    }

    private void registerWatchers() {
        LOGGER.info("Registering watchers");

        Map<Path, WatchKey> originalKeys = new HashMap<>(watchKeys);
        watchKeys.clear();

        List<Path> addedPaths = new ArrayList<>();
        for (var path : watchedPaths) {
            try {
                WatchKey register = path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                watchKeys.put(path, register);
                addedPaths.add(path);
            } catch (IOException e) {
                LOGGER.error("Failed to register watcher for path {}", path, e);
            }
        }

        // Close the watch keys for paths that were removed
        for (var entry : originalKeys.entrySet()) {
            if (!addedPaths.contains(entry.getKey())) {
                entry.getValue().cancel();
            }
        }

        // Clean up the original keys
        originalKeys.clear();
    }

    private void rebuildWatcherPaths() {
        watchedPaths.clear();

        LOGGER.info("Rebuilding watched paths");

        var gamePath = FMLPaths.GAMEDIR.get();
        ServerManifest manifest = fileManager.getManifest();
        for (ServerManifest.DirectoryServerData directory : manifest.directories()) {
            try (var files = Files.walk(gamePath.resolve(directory.path()))) {
                files.forEach(file -> {
                    if (Files.isDirectory(file)) {
                        watchedPaths.add(file);
                    }
                });
            } catch (IOException e) {
                LOGGER.error("Failed to walk directory {}", directory.path(), e);
            }
        }
    }
}
