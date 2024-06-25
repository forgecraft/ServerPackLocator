package cpw.mods.forge.serverpacklocator;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.conversion.ObjectConverter;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.json.JsonFormat;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.google.gson.Gson;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public abstract class SidedPackHandler<TConfig> {
    private final Path gameDir;
    private final Path splDirectory;
    private final Path configFilePath;
    private final TConfig packConfig;
    private final boolean isValid;

    protected SidedPackHandler() {
        this.gameDir = LaunchEnvironmentHandler.INSTANCE.getGameDir();
        this.splDirectory = this.gameDir.resolve("spl");
        this.configFilePath = this.splDirectory.resolve("config.toml");

        var config = FileConfig
                .builder(this.configFilePath)
                .onFileNotFound(this::handleMissing)
                .build();
        config.load();
        config.close();

        var objectConverter = new ObjectConverter();
        this.packConfig = objectConverter.toObject(config, getConfigurationConstructor());
        this.isValid = validateConfig();
    }

    protected final boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) throws IOException {
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

    protected abstract boolean validateConfig();

    public Path getConfigFilePath() {

        return configFilePath;
    }

    public TConfig getConfig() {
        return packConfig;
    }

    public Path getGameDir() {
        return gameDir;
    }

    public Path getSplDirectory() {
        return splDirectory;
    }

    protected boolean isValid() {
        return isValid;
    }

    public abstract void initialize();

    protected abstract boolean waitForDownload();

    public abstract List<File> getModFolders();

}
