package cpw.mods.forge.serverpacklocator.client;

import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import cpw.mods.forge.serverpacklocator.secure.ConnectionSecurityManager;
import cpw.mods.forge.serverpacklocator.secure.IConnectionSecurityManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

public class ClientSidedPackHandler extends SidedPackHandler<ClientConfig> {

    private static final Logger LOGGER = LogManager.getLogger();
    private MultiThreadedDownloader clientDownloader;

    @Override
    protected ClientConfig createDefaultConfiguration() {
        return ClientConfig.Default.INSTANCE;
    }

    @Override
    protected Supplier<ClientConfig> getConfigurationConstructor() {
        return ClientConfig::new;
    }

    @Override
    protected boolean validateConfig() {
        final String remoteServer = getConfig().getClient().getRemoteServer();

        if (remoteServer == null || remoteServer.isEmpty() || remoteServer.isBlank()) {
            LOGGER.fatal("Invalid configuration file {} found. Could not locate remove server address. " +
                         "Repair or delete this file to continue", getConfigFilePath());
            throw new IllegalStateException("Invalid configuration file found, please delete or correct");
        }

        return ConnectionSecurityManager.getInstance().validateConfiguration(this, getConfig().getSecurity());
    }

    @Override
    public List<File> getModFolders() {
        return clientDownloader.manifest()
                .getDirectories()
                .stream()
                .filter(e -> e.getSyncType().loadOnClient())
                .map(c -> getGameDir().resolve(c.getTargetPath()).toFile())
                .toList();
    }

    @Override
    protected boolean waitForDownload() {
        if (!isValid()) return false;

        if (!clientDownloader.download()) {
            LOGGER.info("There was a problem with the connection, there will not be any server mods");
            return false;
        }
        return true;
    }

    @Override
    public void initialize() {
        final IConnectionSecurityManager<?> connectionSecurityManager = ConnectionSecurityManager.getInstance().initialize(this, getConfig().getSecurity());
        clientDownloader = new MultiThreadedDownloader(
                this,
                connectionSecurityManager
        );
    }
}
