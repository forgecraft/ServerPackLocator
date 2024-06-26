package cpw.mods.forge.serverpacklocator;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.conversion.ObjectConverter;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.json.JsonFormat;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.google.gson.Gson;
import cpw.mods.forge.serverpacklocator.secure.IConnectionSecurityManager;
import cpw.mods.forge.serverpacklocator.secure.SecurityConfigHolder;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public abstract class SidedPackHandler<TConfig extends SecurityConfigHolder> {
    private final Path gameDir;
    private final TConfig packConfig;
    protected final IConnectionSecurityManager securityManager;

    protected SidedPackHandler(Path gameDir, Path configPath) throws ConfigException {
        this.gameDir = gameDir;

        var config = FileConfig
                .builder(configPath)
                .onFileNotFound(this::handleMissing)
                .build();
        config.load();
        config.close();

        var objectConverter = new ObjectConverter();
        this.packConfig = objectConverter.toObject(config, getConfigurationConstructor());
        this.securityManager = IConnectionSecurityManager.create(packConfig.getSecurity());
    }

    protected final boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) {
        final File target = path.toFile();
        target.getParentFile().mkdirs();

        final TConfig configObject = createDefaultConfiguration();

        Gson gson = new Gson();
        String json = gson.toJson(configObject);

        ConfigFormat<?> jsonFormat = JsonFormat.fancyInstance();
        Config jsonConfig = jsonFormat.createParser().parse(json);

        TomlFormat.instance().createWriter().write(jsonConfig, path, WritingMode.REPLACE);

        return true;
    }

    protected abstract TConfig createDefaultConfiguration();

    protected abstract Supplier<TConfig> getConfigurationConstructor();

    public TConfig getConfig() {
        return packConfig;
    }

    public Path getGameDir() {
        return gameDir;
    }

    public abstract List<File> getModFolders();

    @Nullable
    public String getUnavailabilityReason() {
        return securityManager.getUnavailabilityReason();
    }
}
