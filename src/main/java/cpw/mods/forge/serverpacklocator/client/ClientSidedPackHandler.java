package cpw.mods.forge.serverpacklocator.client;

import com.electronwill.nightconfig.core.ConfigFormat;
import cpw.mods.forge.serverpacklocator.LaunchEnvironmentHandler;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import cpw.mods.forge.serverpacklocator.secure.ConnectionSecurityManager;
import cpw.mods.forge.serverpacklocator.secure.IConnectionSecurityManager;
import net.neoforged.neoforgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
            throw new IllegalStateException("Invalid configuation file found, please delete or correct");
        }

        return ConnectionSecurityManager.getInstance().validateConfiguration(this, getConfig().getSecurity());
    }

    @Override
    protected List<IModLocator.ModFileOrException> processModList(List<IModLocator.ModFileOrException> scannedMods) {
        return scannedMods;
    }

    @Override
    public List<IModLocator> buildLocators() {
        return clientDownloader.manifest()
                .getDirectories()
                .stream()
                .filter(e -> e.getSyncType().loadOnClient())
                .peek(e -> getGameDir().resolve(e.getTargetPath()).toFile().mkdirs())
                .map(c -> LaunchEnvironmentHandler.INSTANCE.getModFolderFactory().build(getGameDir().resolve(c.getTargetPath()), "SPL-" + c.getName()))
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
