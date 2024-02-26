package cpw.mods.forge.serverpacklocator.server;

import cpw.mods.forge.serverpacklocator.LaunchEnvironmentHandler;
import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import cpw.mods.forge.serverpacklocator.secure.ConnectionSecurityManager;
import net.neoforged.neoforgespi.locating.IModLocator;

import java.util.List;
import java.util.function.Supplier;

public class ServerSidedPackHandler extends SidedPackHandler<ServerConfig>
{
    private ServerFileManager serverFileManager;

    @Override
    protected ServerConfig createDefaultConfiguration() {
        return ServerConfig.Default.INSTANCE;
    }

    @Override
    protected Supplier<ServerConfig> getConfigurationConstructor() {
        return ServerConfig::new;
    }

    @Override
    protected boolean validateConfig() {
        return ConnectionSecurityManager.getInstance().validateConfiguration(this, getConfig().getSecurity());
    }

    @Override
    protected boolean waitForDownload() {
        return true;
    }

    @Override
    public List<IModLocator> buildLocators() {
        return getConfig().getServer()
                .getExposedServerContent()
                .stream()
                .filter(e -> e.getSyncType().loadOnServer())
                .peek(e -> getGameDir().resolve(e.getDirectory().getPath()).toFile().mkdirs())
                .map(c -> LaunchEnvironmentHandler.INSTANCE.getModFolderFactory().build(getGameDir().resolve(c.getDirectory().getPath()), "SPL-" + c.getName()))
                .toList();
    }

    @Override
    protected List<IModLocator.ModFileOrException> processModList(List<IModLocator.ModFileOrException> scannedMods) {
        return scannedMods;
    }

    @Override
    public void initialize() {
        serverFileManager = new ServerFileManager(
                this,
                getConfig().getServer().getExposedServerContent()
        );
        SimpleHttpServer.run(this);
    }

    public ServerFileManager getFileManager() {
        return serverFileManager;
    }
}
