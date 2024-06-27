package cpw.mods.forge.serverpacklocator.client;

import cpw.mods.forge.serverpacklocator.FileChecksumValidator;
import cpw.mods.forge.serverpacklocator.LaunchEnvironmentHandler;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import cpw.mods.forge.serverpacklocator.secure.IConnectionSecurityManager;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MultiThreadedDownloader {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Executor executor;
    private final ClientSidedPackHandler clientSidedPackHandler;
    private final IConnectionSecurityManager connectionSecurityManager;
    private final HttpClient httpClient;
    private final String remoteServer;

    public MultiThreadedDownloader(
            final ClientSidedPackHandler packHandler,
            final IConnectionSecurityManager connectionSecurityManager
    ) {
        this.executor = Executors.newFixedThreadPool(
                Math.min(Math.max(1, Runtime.getRuntime().availableProcessors() - 2), packHandler.getConfig().getClient().getThreadCount())
        );
        this.httpClient = HttpClient.newBuilder().executor(executor).build();
        this.clientSidedPackHandler = packHandler;
        this.connectionSecurityManager = connectionSecurityManager;
        remoteServer = clientSidedPackHandler.getConfig().getClient().getRemoteServer();
    }

    private CompletableFuture<Void> authenticate() {
        LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Authenticating to: " + remoteServer);

        return makeRequest("authenticate", false, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    var challenge = response.headers().firstValue("Challenge").orElse(null);
                    if (challenge == null) {
                        throw new RuntimeException("Did not receive a challenge from the server.");
                    }
                    processChallengeString(challenge);
                    LOGGER.debug("Received challenge");
                });
    }

    private void processChallengeString(String challengeStr) {
        LOGGER.info("Got Challenge {}", challengeStr);
        var challenge = Base64.getDecoder().decode(challengeStr);
        this.connectionSecurityManager.onAuthenticateComplete(new String(challenge, StandardCharsets.UTF_8));
    }

    private CompletableFuture<PreparedServerDownloadData> downloadManifest() {
        return authenticate().thenCompose(ignored -> {
            LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Requesting server manifest...");

            return makeRequest("servermanifest.json", true, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }).thenApply(response -> {
            response.headers().firstValue("Challenge").ifPresent(this::processChallengeString);

            var serverManifest = ServerManifest.fromString(response.body());
            LOGGER.debug("Received manifest");

            return new PreparedServerDownloadData(
                    serverManifest,
                    clientSidedPackHandler.getConfig().getClient().getDownloadedServerContent()
            );
        });
    }

    private CompletableFuture<Void> downloadFile(final ServerManifest.DirectoryServerData nextDirectory, final ServerManifest.FileData next) {
        var rootDir = clientSidedPackHandler.getGameDir();
        var outputDir = rootDir.resolve(nextDirectory.getPath());
        var filePath = outputDir.resolve(next.getFileName());
        final String existingChecksum = FileChecksumValidator.computeChecksumFor(filePath);
        if (Objects.equals(next.getChecksum(), existingChecksum)) {
            LOGGER.debug("Found existing file {} - skipping", next.getFileName());
            return CompletableFuture.completedFuture(null);
        }

        if (existingChecksum != null && !nextDirectory.getSyncType().forceSync()) {
            LOGGER.warn("Found existing file {} with different checksum - file is not forced synced - skipping", next.getFileName());
            return CompletableFuture.completedFuture(null);
        }

        final String nextFile = rootDir.relativize(filePath).toString().replace("\\", "/");
        LOGGER.info("Requesting file {}", nextFile);
        LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Downloading " + nextFile);
        var path = "files/" + URLEncoder.encode(nextFile, StandardCharsets.UTF_8).replace("+", "%20");

        return makeRequest(path, true, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> {
                    File file = filePath.toFile();
                    file.getParentFile().mkdirs();

                    response.headers().firstValue("Challenge").ifPresent(this::processChallengeString);

                    var totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(0L);
                    long time = System.nanoTime(), between, length;
                    int percent;

                    try (var outputStream = new FileOutputStream(file); var download = outputStream.getChannel();
                         ReadableByteChannel channel = Channels.newChannel(response.body())) {
                        while (download.transferFrom(channel, file.length(), 8192) > 0) {
                            between = System.nanoTime() - time;

                            if (between < 1000000000) continue;

                            length = file.length();

                            percent = (int) ((double) length / ((double) totalBytes == 0.0 ? 1.0 : (double) totalBytes) * 100.0);

                            LOGGER.info("Downloaded {}% of {}", percent, nextFile);
                            LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Downloaded " + percent + "% of " + nextFile);

                            time = System.nanoTime();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Download of " + path + " failed: " + e, e);
                    }
                    return null;
                });
    }

    private <T> CompletableFuture<HttpResponse<T>> makeRequest(String path, boolean authenticated, HttpResponse.BodyHandler<T> bodyHandler) {
        var requestUri = joinUrl(remoteServer, path);

        LOGGER.info("ServerPackLocator is requesting {}...", requestUri);
        StartupNotificationManager.addModMessage("SPL is requesting " + requestUri);

        var requestBuilder = HttpRequest.newBuilder(requestUri);
        this.connectionSecurityManager.onClientConnectionCreation(requestBuilder);
        if (authenticated) {
            this.connectionSecurityManager.authenticateConnection(requestBuilder);
        }
        var request = requestBuilder.build();
        return httpClient.sendAsync(request, bodyHandler)
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Got HTTP Status Code " + response.statusCode() + " for " + requestUri);
                    }
                    return response;
                });
    }

    private static URI joinUrl(String baseUrl, String path) {
        // Join base url and path while avoiding double-slashes
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return URI.create(baseUrl + path);
    }

    public void download() {
        var progressBar = StartupNotificationManager.addProgressBar("Downloading server pack", 0);

        CompletableFuture<Void> downloadTask = downloadManifest()
                .thenCompose(preparedManifest -> {
                    final ServerManifest manifest = preparedManifest.manifest();
                    final List<CompletableFuture<Void>> downloads = new ArrayList<>();

                    final Map<String, ClientConfig.DownloadedServerContent> lookup = preparedManifest.directoryContent()
                            .stream()
                            .collect(Collectors.toMap(ClientConfig.DownloadedServerContent::getName, Function.identity()));

                    if (manifest.getDirectories() != null) {
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
                                    downloads.add(downloadFile(directory, fileData));
                                });
                            }
                        });
                    }

                    return CompletableFuture.allOf(downloads.toArray(new CompletableFuture[0]));
                });

        try {
            while (true) {
                try {
                    downloadTask.get(50, TimeUnit.MILLISECONDS);
                    break;
                } catch (TimeoutException ignored) {
                    // Not done yet! Update progress UI
                    ImmediateWindowHandler.renderTick();
                }
            }
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw new UncheckedIOException(ioException);
            } else if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            } else {
                throw new RuntimeException(e.getCause());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            progressBar.complete();
        }
    }

    public ServerManifest manifest() {
        return downloadManifest().join().manifest();
    }

    public record PreparedServerDownloadData(ServerManifest manifest,
                                             List<ClientConfig.DownloadedServerContent> directoryContent) {
    }
}

