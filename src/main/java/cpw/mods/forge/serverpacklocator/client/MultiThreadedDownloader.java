package cpw.mods.forge.serverpacklocator.client;

import cpw.mods.forge.serverpacklocator.FileChecksumValidator;
import cpw.mods.forge.serverpacklocator.LaunchEnvironmentHandler;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import cpw.mods.forge.serverpacklocator.secure.IConnectionSecurityManager;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MultiThreadedDownloader {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Executor executor;
    private final ClientSidedPackHandler clientSidedPackHandler;
    private final IConnectionSecurityManager<?> connectionSecurityManager;

    public MultiThreadedDownloader(
            final ClientSidedPackHandler packHandler,
            final IConnectionSecurityManager<?> connectionSecurityManager
    ) {
        this.executor = Executors.newFixedThreadPool(
                Math.min(Math.max(1, Runtime.getRuntime().availableProcessors() - 2), packHandler.getConfig().getClient().getThreadCount())
        );
        this.clientSidedPackHandler = packHandler;
        this.connectionSecurityManager = connectionSecurityManager;
    }

    private CompletableFuture<Void> authenticate() {
        return CompletableFuture.runAsync(() -> {
            try {
                var serverHost = clientSidedPackHandler.getConfig().getClient().getRemoteServer();
                var address = serverHost + "/authenticate";

                LOGGER.info("Authenticating to: " + serverHost);
                LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Authenticating to: " + serverHost);

                var url = new URL(address);
                var connection = url.openConnection();
                this.connectionSecurityManager.onClientConnectionCreation(connection);

                try (BufferedReader ignored = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    final String headerChallengeString = connection.getHeaderField("Challenge");
                    processChallengeString(headerChallengeString);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to download challenge", e);
                }
                LOGGER.debug("Received challenge");
            } catch (Exception e) {
                throw new RuntimeException("Failed to open a connection", e);
            }
        }, executor);
    }

    private void processChallengeString(String challengeStr) {
        LOGGER.info("Got Challenge {}", challengeStr);
        var challenge = Base64.getDecoder().decode(challengeStr);
        this.connectionSecurityManager.onAuthenticateComplete(new String(challenge, StandardCharsets.UTF_8));
    }

    private CompletableFuture<PreparedServerDownloadData> downloadManifest() {
        return authenticate().thenApplyAsync(v -> {
            try {
                var serverHost = clientSidedPackHandler.getConfig().getClient().getRemoteServer();
                var address = serverHost + "/servermanifest.json";

                LOGGER.info("Requesting server manifest from: " + serverHost);
                LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Requesting server manifest from: " + serverHost);

                var url = new URL(address);
                var connection = url.openConnection();
                this.connectionSecurityManager.onClientConnectionCreation(connection);
                this.connectionSecurityManager.authenticateConnection(connection);

                ServerManifest serverManifest;
                try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream())) {
                    var challengeString = connection.getHeaderField("Challenge");
                    processChallengeString(challengeString);

                    serverManifest = ServerManifest.loadFromStream(in);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to download manifest", e);
                }
                LOGGER.debug("Received manifest");

                return new PreparedServerDownloadData(
                        serverManifest,
                        clientSidedPackHandler.getConfig().getClient().getDownloadedServerContent()
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to open a connection", e);
            }
        }, executor);
    }

    private CompletableFuture<Void> downloadFile(final String server, final ServerManifest.DirectoryServerData nextDirectory , final ServerManifest.FileData next) {
        return CompletableFuture.runAsync(() -> {
            try {
                var rootDir = clientSidedPackHandler.getGameDir();
                var outputDir = rootDir.resolve(nextDirectory.getPath());
                var filePath = outputDir.resolve(next.getFileName());
                final String existingChecksum = FileChecksumValidator.computeChecksumFor(filePath);
                if (Objects.equals(next.getChecksum(), existingChecksum)) {
                    LOGGER.debug("Found existing file {} - skipping", next.getFileName());
                    return;
                }

                if (existingChecksum != null && !nextDirectory.getSyncType().forceSync()) {
                    LOGGER.warn("Found existing file {} with different checksum - file is not forced synced - skipping", next.getFileName());
                    return;
                }

                final String nextFile = rootDir.relativize(filePath).toString();
                LOGGER.info("Requesting file {}", nextFile);
                LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Requesting file "+nextFile);
                final String requestUri = server + LamdbaExceptionUtils.rethrowFunction((String f) -> URLEncoder.encode(f, StandardCharsets.UTF_8))
                        .andThen(s -> s.replaceAll("\\+", "%20"))
                        .andThen(s -> "/files/"+s)
                        .apply(nextFile);

                try
                {
                    URLConnection connection = new URL(requestUri).openConnection();
                    this.connectionSecurityManager.onClientConnectionCreation(connection);
                    this.connectionSecurityManager.authenticateConnection(connection);

                    File file = filePath.toFile();
                    file.getParentFile().mkdirs();

                    try (var outputStream = new FileOutputStream(file);
                            var download = outputStream.getChannel()) {

                        long totalBytes = connection.getContentLengthLong(), time = System.nanoTime(), between, length;
                        int percent;

                        try (ReadableByteChannel channel = Channels.newChannel(connection.getInputStream())) {

                            var challengeString = connection.getHeaderField("Challenge");
                            processChallengeString(challengeString);

                            while (download.transferFrom(channel, file.length(), 1024) > 0) {
                                between = System.nanoTime() - time;

                                if (between < 1000000000) continue;

                                length = file.length();

                                percent = (int) ((double) length / ((double) totalBytes == 0.0 ? 1.0 : (double) totalBytes) * 100.0);

                                LOGGER.info("Downloaded {}% of {}", percent, nextFile);
                                LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Downloaded " + percent + "% of " + nextFile);

                                time = System.nanoTime();
                            }
                        }
                    }
                } catch (Exception ex) {
                    throw new IllegalStateException("Failed to download file: " + nextFile, ex);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to download a file", e);
            }
        }, executor);
    }

    public boolean download() {
        CompletableFuture<Void> downloadTask = downloadManifest()
                .thenComposeAsync(preparedManifest -> {
                    final ServerManifest manifest = preparedManifest.manifest();
                    final List<CompletableFuture<Void>> downloads = new ArrayList<>();

                    final Map<String, ClientConfig.DownloadedServerContent> lookup = preparedManifest.directoryContent()
                            .stream()
                            .collect(Collectors.toMap(ClientConfig.DownloadedServerContent::getName, Function.identity()));

                    if (manifest.getDirectories() != null && manifest.getDirectories() != null) {
                        manifest.getDirectories().forEach(directory -> {
                            List<Pattern> directoryBlacklist =
                                    lookup.get(directory.getName()) == null ? List.of() :
                                    lookup.get(directory.getName()).getBlackListRegex().stream()
                                    .map(Pattern::compile)
                                    .toList();

                            if (directory.getFileData() != null) {
                                directory.getFileData().forEach(fileData -> {
                                    if (directoryBlacklist.stream().anyMatch(p -> p.matcher(fileData.getFileName()).matches())) {
                                        LOGGER.info("Skipping blacklisted file {}", fileData.getFileName());
                                        return;
                                    }
                                    downloads.add(downloadFile(clientSidedPackHandler.getConfig().getClient().getRemoteServer(), directory, fileData));
                                });
                            }
                        });
                    }

                    return CompletableFuture.allOf(downloads.toArray(new CompletableFuture[0]));
                }, executor);

        try {
            downloadTask.join();
            return true;
        } catch (Exception e) {
            LOGGER.error("Caught exception downloading mods from server", e);
            return false;
        }
    }

    public ServerManifest manifest() {
        return downloadManifest().join().manifest();
    }

    public record PreparedServerDownloadData(ServerManifest manifest, List<ClientConfig.DownloadedServerContent> directoryContent) {}
}
