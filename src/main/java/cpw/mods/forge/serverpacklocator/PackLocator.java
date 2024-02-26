package cpw.mods.forge.serverpacklocator;

import cpw.mods.forge.serverpacklocator.utils.ModUtilityUtils;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class PackLocator implements IModLocator
{
    private static final Logger LOGGER = LogManager.getLogger();
    private final SidedPackHandler<?> serverPackLocator;
    private final Map<IModFile, IModLocator> locatedByMap = new ConcurrentHashMap<>();
    private final IModLocator internalSPLLocator;
    private boolean missingUtilMod = false;


    public PackLocator()
    {
        LOGGER.info("Loading server pack locator. Version {}", getClass().getPackage().getImplementationVersion());
        this.serverPackLocator = CreateAndValidateServerPackLocator();
        this.internalSPLLocator = LaunchEnvironmentHandler.INSTANCE.getModFolderFactory().build(serverPackLocator.getSplDirectory(), "SPL-Internal");
    }

    private static SidedPackHandler<?> CreateAndValidateServerPackLocator() {
        var serverPackLocator = SidedPackLocator.buildFor(LaunchEnvironmentHandler.INSTANCE.getDist());
        if (!serverPackLocator.isValid())
        {
            LOGGER.warn("The server pack locator is not in a valid state, it will not load any mods");
        }
        return serverPackLocator;
    }

    @Override
    public List<ModFileOrException> scanMods()
    {
        boolean successfulDownload = serverPackLocator.waitForDownload();
        this.locatedByMap.clear();
        final IModFile internalMod = discoverInternalMod();

        ArrayList<ModFileOrException> finalModList = new ArrayList<>();

        if (internalMod != null) {
            finalModList.add(new ModFileOrException(internalMod, null));
        }

        if (successfulDownload) {
            List<IModLocator> locators = serverPackLocator.buildLocators();
            for (IModLocator locator : locators) {
                var scannedMods = locator.scanMods();
                for (IModLocator.ModFileOrException modFileOrException : scannedMods) {
                    finalModList.add(modFileOrException);
                    this.locatedByMap.put(modFileOrException.file(), locator);
                }
            }
        }

        ModAccessor.setStatusLine("ServerPack: " + (successfulDownload ? "loaded" : "NOT loaded"));
        return finalModList;
    }

    private IModFile discoverInternalMod() {
        if (missingUtilMod)
            return null;

        var serverPackLocatorUtilityModFileName = ModUtilityUtils.buildModUtilityFileName();
        final List<ModFileOrException> internallyLoaded = internalSPLLocator.scanMods();
        final IModFile packUtil = internallyLoaded.stream()
                .filter(moe -> moe.file() != null)
                .filter(modFile -> serverPackLocatorUtilityModFileName.equals(modFile.file().getFileName()))
                .findFirst()
                .map(ModFileOrException::file)
                .orElseThrow(() -> new RuntimeException("Something went wrong with the internal utility mod"));
        this.locatedByMap.put(packUtil, internalSPLLocator);
        return packUtil;
    }

    @Override
    public String name()
    {
        return "serverpacklocator";
    }

    @Override
    public void scanFile(final IModFile modFile, final Consumer<Path> pathConsumer)
    {
        IModLocator locator = locatedByMap.get(modFile);
        if (locator != null)
        {
            locator.scanFile(modFile, pathConsumer);
        }
    }

    @Override
    public void initArguments(final Map<String, ?> arguments)
    {
        if (serverPackLocator.isValid())
        {
            serverPackLocator.initialize();
        }

        URL url = getClass().getProtectionDomain().getCodeSource().getLocation();

        LOGGER.info("Loading server pack locator from: " + url.toString());
        URI targetURI = LamdbaExceptionUtils.uncheck(() -> new URI("file://" + LamdbaExceptionUtils.uncheck(url::toURI).getRawSchemeSpecificPart().split("!")[0].split("\\.jar")[0] + ".jar"));

        LOGGER.info("Unpacking utility mod from: " + targetURI.toString());
        try {
            final FileSystem thiszip = FileSystems.newFileSystem(Paths.get(targetURI), getClass().getClassLoader());
            final Path utilModPath = thiszip.getPath("utilmod", ModUtilityUtils.buildModUtilityFileName());
            Files.copy(utilModPath, serverPackLocator.getSplDirectory().resolve(ModUtilityUtils.buildModUtilityFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (NoSuchFileException noSuchFileException) {
            LOGGER.error("Failed to find the utility mod in the server pack locator jar. This is okay if you are running in dev!");
            this.missingUtilMod = true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize util mod.", e);
        }
    }

    @Override
    public boolean isValid(final IModFile modFile)
    {
        return locatedByMap.get(modFile) != null && locatedByMap.get(modFile).isValid(modFile);
    }
}
