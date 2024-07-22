package net.forgecraft.serverpacklocator.client;

import net.forgecraft.serverpacklocator.FileChecksumValidator;
import net.forgecraft.serverpacklocator.ServerManifest;
import net.forgecraft.serverpacklocator.secure.IConnectionSecurityManager;
import net.forgecraft.serverpacklocator.utils.SyncType;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MultiThreadedDownloader {
    private static final Logger LOGGER = LogManager.getLogger();

    private final ClientSidedPackHandler clientSidedPackHandler;
    private final IConnectionSecurityManager connectionSecurityManager;
    private final HttpClient httpClient;
    private final String remoteServer;

    public MultiThreadedDownloader(
            final ClientSidedPackHandler packHandler,
            final IConnectionSecurityManager connectionSecurityManager
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.clientSidedPackHandler = packHandler;
        this.connectionSecurityManager = connectionSecurityManager;
        remoteServer = clientSidedPackHandler.getConfig().getClient().getRemoteServer();
    }

    private void authenticate() throws IOException, InterruptedException {
        var progressBar = StartupNotificationManager.addProgressBar("SPL is authenticating...", 1);
        try {
            makeRequest("authenticate", false, HttpResponse.BodyHandlers.discarding());
        } finally {
            progressBar.complete();
        }
    }

    private void processChallengeString(String challengeStr) {
        LOGGER.info("Got Challenge {}", challengeStr);
        var challenge = Base64.getDecoder().decode(challengeStr);
        this.connectionSecurityManager.onAuthenticateComplete(new String(challenge, StandardCharsets.UTF_8));
    }

    private PreparedServerDownloadData downloadManifest() throws IOException, InterruptedException {
        authenticate();
        var progressBar = StartupNotificationManager.addProgressBar("Requesting server manifest...", 1);
        try {
            var response = makeRequest("servermanifest.json", true, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            response.headers().firstValue("Challenge").ifPresent(this::processChallengeString);

            var serverManifest = ServerManifest.fromString(response.body());

            // Write the file to the client system for debugging
            if (serverManifest != null) {
                try {
                    Files.writeString(clientSidedPackHandler.getGameDir().resolve("spl/servermanifest-copy.json"), serverManifest.toJson());
                } catch (IOException e) {
                    LOGGER.warn("Failed to write server manifest copy", e);
                }
            }

            LOGGER.debug("Received manifest");

            return new PreparedServerDownloadData(
                    serverManifest,
                    clientSidedPackHandler.getConfig().getClient().getDownloadedServerContent()
            );
        } finally {
            progressBar.increment();
            progressBar.complete();
        }
    }

    private <T> HttpResponse<T> makeRequest(String path, boolean authenticated, HttpResponse.BodyHandler<T> bodyHandler) throws IOException, InterruptedException {
        var requestUri = joinUrl(remoteServer, path);

        LOGGER.info("ServerPackLocator is requesting {}...", requestUri);

        var requestBuilder = HttpRequest.newBuilder(requestUri);
        this.connectionSecurityManager.onClientConnectionCreation(requestBuilder);
        if (authenticated) {
            this.connectionSecurityManager.authenticateConnection(requestBuilder);
        }
        var request = requestBuilder.build();
        var response = httpClient.send(request, bodyHandler);
        response.headers().firstValue("Challenge").ifPresent(this::processChallengeString);
        if (response.statusCode() != 200) {
            throw new IOException("Got HTTP Status Code " + response.statusCode() + " for " + requestUri);
        }
        return response;
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

    public ServerManifest download() throws IOException, InterruptedException {
        var preparedManifest = downloadManifest();

        var filesToDownload = getFilesToDownload(preparedManifest);

        if (!filesToDownload.isEmpty()) {
            // We measure this in kiB to avoid overflow
            var overallSize = filesToDownload.stream().mapToLong(FileToDownload::size).sum();
            LOGGER.info("Downloading {} server pack files ({} byte)", filesToDownload.size(), overallSize);

            var progressBar = StartupNotificationManager.addProgressBar("Downloading...", toDownloadProgress(overallSize));
            try {

                var bytesDownloaded = 0L;
                for (var fileToDownload : filesToDownload) {
                    var bytesDownloadedAtStartOfFile = bytesDownloaded;
                    progressBar.setAbsolute(toDownloadProgress(bytesDownloadedAtStartOfFile));
                    MutableLong lastRenderTick = new MutableLong(System.currentTimeMillis());
                    progressBar.label("Downloading " + fileToDownload.localFile.getName() + "...");
                    downloadFile(fileToDownload, (downloaded, total) -> {
                        progressBar.setAbsolute(toDownloadProgress(bytesDownloadedAtStartOfFile + downloaded));
                        if (System.currentTimeMillis() - lastRenderTick.getValue() >= 50L) {
                            ImmediateWindowHandler.renderTick();
                            lastRenderTick.setValue(System.currentTimeMillis());
                        }
                    });
                    // The original estimate was based on this, and the progress bar should reflect it
                    // even if the server sent a different size
                    bytesDownloaded += fileToDownload.size;
                }

            } finally {
                progressBar.complete();
            }
        }

        return preparedManifest.manifest();
    }

    // the progress bar only supports 32-bit, so we convert to kib to avoid overflow if pack is >2GB
    private int toDownloadProgress(long bytes) {
        return (int) (bytes / 1024);
    }

    private List<FileToDownload> getFilesToDownload(PreparedServerDownloadData preparedManifest) {
        var manifest = preparedManifest.manifest();

        var lookup = preparedManifest.directoryContent()
                .stream()
                .collect(Collectors.toMap(ClientConfig.DownloadedServerContent::getName, Function.identity()));

        var filesToDownload = new ArrayList<FileToDownload>();

        // Determine which files to download
        for (var directory : manifest.directories()) {
            List<Pattern> directoryBlacklist =
                    lookup.get(directory.name()) == null ? List.of() :
                            lookup.get(directory.name()).getBlackListRegex().stream()
                                    .map(Pattern::compile)
                                    .toList();

            for (var fileData : directory.fileData()) {
                if (directoryBlacklist.stream().anyMatch(p -> p.matcher(fileData.relativePath()).matches())) {
                    LOGGER.info("Skipping blacklisted file {}", fileData.relativePath());
                    continue;
                }

                var rootDir = clientSidedPackHandler.getGameDir();
                var outputDir = rootDir.resolve(directory.targetPath());
                var downloadDir = rootDir.resolve(directory.path());

                var filePath = outputDir.resolve(fileData.relativePath());
                var downloadFilePath = downloadDir.resolve(fileData.relativePath());

                final String existingChecksum = FileChecksumValidator.computeChecksumFor(filePath);
                if (Objects.equals(fileData.checksum(), existingChecksum)) {
                    LOGGER.debug("Found existing file {} - skipping", fileData.relativePath());
                    continue;
                }

                if (existingChecksum != null && !directory.syncType().forceSync()) {
                    LOGGER.warn("Found existing file {} with different checksum - file is not forced synced - skipping", fileData.relativePath());
                    continue;
                }

                var relativeUrl = rootDir.relativize(filePath).toString().replace("\\", "/");
                var relativeDownloadPath = rootDir.relativize(downloadFilePath).toString().replace("\\", "/");
                filesToDownload.add(new FileToDownload(relativeUrl, relativeDownloadPath, filePath.toFile(), fileData.size(), fileData.checksum()));
            }
        }

        // Read the directories to diff against the files we have to download vs what we have from the server
        for (var directory : manifest.directories()) {
            if (!directory.shouldRemoveDanglingFiles()) {
                continue;
            }

            var outputDir = clientSidedPackHandler.getGameDir().resolve(directory.targetPath());
            if (!Files.exists(outputDir)) {
                continue;
            }

            // Read the directory and compare against the files we have to download
            try (var stream = Files.walk(outputDir)) {
                List<Path> dirFiles = stream.filter(Files::isRegularFile).toList();

                for (var file : dirFiles) {
                    var relativePath = outputDir.relativize(file).toString().replace("\\", "/");

                    if (directory.fileData().stream().noneMatch(f -> f.relativePath().equals(relativePath))) {
                        try {
                            Files.delete(file);
                            LOGGER.info("Deleted dangling file {}", file);
                        } catch (IOException e) {
                            LOGGER.warn("Failed to delete dangling file {}", file, e);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to read directory {}", outputDir, e);
            }
        }

        return filesToDownload;
    }

    private void downloadFile(FileToDownload fileToDownload, ProgressListener progressListener) throws IOException, InterruptedException {
        var nextFile = fileToDownload.relativeDownloadPath();

        LOGGER.info("Requesting file {}", nextFile);
        var path = "files/" + URLEncoder.encode(nextFile, StandardCharsets.UTF_8).replace("+", "%20");

        var response = makeRequest(path, true, HttpResponse.BodyHandlers.ofInputStream());

        File file = fileToDownload.localFile();
        file.getParentFile().mkdirs();

        response.headers().firstValue("Challenge").ifPresent(this::processChallengeString);

        var totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(fileToDownload.size());

        try (var outputStream = new FileOutputStream(file); var download = outputStream.getChannel();
             ReadableByteChannel channel = Channels.newChannel(response.body())) {
            while ((download.transferFrom(channel, file.length(), 8192)) > 0) {
                progressListener.onProgress(file.length(), totalBytes);
            }
        } catch (IOException e) {
            // Re-wrap the IO exception to give information about which path failed
            throw new IOException("Download of " + path + " failed: " + e, e);
        }

        // Validate that the downloaded file actually matches the expected checksum
        var downloadChecksum = FileChecksumValidator.computeChecksumFor(file.toPath());
        if (!Objects.equals(downloadChecksum, fileToDownload.checksum)) {
            throw new IOException("Downloaded file has checksum " + downloadChecksum + " but expected " + fileToDownload.checksum);
        }
    }

    @FunctionalInterface
    interface ProgressListener {
        void onProgress(long downloaded, long expectedSize);
    }

    record FileToDownload(String relativeUrl, String relativeDownloadPath, File localFile, long size, String checksum) {
    }

    public record PreparedServerDownloadData(ServerManifest manifest,
                                             List<ClientConfig.DownloadedServerContent> directoryContent) {
    }
}

