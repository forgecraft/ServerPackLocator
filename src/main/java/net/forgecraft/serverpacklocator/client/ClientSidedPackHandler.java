package net.forgecraft.serverpacklocator.client;

import net.forgecraft.serverpacklocator.ConfigException;
import net.forgecraft.serverpacklocator.SidedPackHandler;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class ClientSidedPackHandler extends SidedPackHandler<ClientConfig> {

    private final MultiThreadedDownloader clientDownloader;

    public ClientSidedPackHandler(Path gameDir, Path configPath) throws ConfigException {
        super(gameDir, configPath);

        var remoteServer = getConfig().getClient().getRemoteServer();

        if (remoteServer == null || remoteServer.isEmpty() || remoteServer.isBlank()) {
            throw new ConfigException("No remote server set.");
        }

        try {
            new URI(remoteServer);
        } catch (URISyntaxException e) {
            throw new ConfigException("Remote server is not a valid URL.");
        }

        clientDownloader = new MultiThreadedDownloader(
                this,
                securityManager
        );
    }

    @Override
    protected ClientConfig createDefaultConfiguration() {
        return ClientConfig.Default.INSTANCE;
    }

    @Override
    protected Supplier<ClientConfig> getConfigurationConstructor() {
        return ClientConfig::new;
    }

    @Override
    public List<File> getModFolders() throws InterruptedException, IOException {
        var manifest = clientDownloader.download();

        return manifest
                .directories()
                .stream()
                .filter(e -> e.syncType().loadOnClient())
                .map(c -> getGameDir().resolve(c.targetPath()).toFile())
                .toList();
    }
}
