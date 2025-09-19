package net.forgecraft.serverpacklocator.server;

import net.forgecraft.serverpacklocator.ConfigException;
import net.forgecraft.serverpacklocator.SidedPackHandler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

public class ServerSidedPackHandler extends SidedPackHandler<ServerConfig> {
    private final ServerFileManager serverFileManager;

    public ServerSidedPackHandler(Path gameDir, Path configPath) throws ConfigException {
        super(gameDir, configPath);

        serverFileManager = new ServerFileManager(
                this
        );

        var port = getConfig().getServer().getPort();

        SimpleHttpServer.run(port, securityManager, serverFileManager);
    }

    @Override
    protected ServerConfig createDefaultConfiguration() {
        return ServerConfig.Default.INSTANCE;
    }

    @Override
    protected Supplier<ServerConfig> getConfigurationConstructor() {
        return ServerConfig::new;
    }

    @Override
    public List<File> getModFolders() {
        return getConfig().getServer()
                .getExposedServerContent()
                .stream()
                .filter(e -> e.getSyncType().loadOnServer())
                .map(c -> getGameDir().resolve(c.getDirectory().getPath()).toFile())
                .toList();
    }

    public ServerFileManager getFileManager() {
        return serverFileManager;
    }
}
