package cpw.mods.forge.serverpacklocator;

import cpw.mods.forge.serverpacklocator.client.ClientSidedPackHandler;
import cpw.mods.forge.serverpacklocator.server.ServerSidedPackHandler;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.Environment;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class PackLocator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void findCandidates(ILaunchContext launchContext, IDiscoveryPipeline pipeline) {
        LOGGER.info("Loading server pack locator");

        var dist = Environment.get().getDist();
        var gameDir = FMLPaths.GAMEDIR.get();
        var splDirectory = gameDir.resolve("spl");
        var configFile = splDirectory.resolve("config.toml");
        LOGGER.info("Loading ServerPackLocator configuration from {}", configFile);
        SidedPackHandler<?> sidedLocator;
        try {
            sidedLocator = switch (dist) {
                case CLIENT -> new ClientSidedPackHandler(gameDir, configFile);
                case DEDICATED_SERVER -> new ServerSidedPackHandler(gameDir, configFile);
            };
        } catch (ConfigException e) {
            pipeline.addIssue(ModLoadingIssue.error("ServerPackLocator has configuration errors: " + e.getMessage() + ". Please check configuration file {0}.")
                    .withCause(e));
            return;
        }

        var reason = sidedLocator.getUnavailabilityReason();
        if (reason != null) {
            pipeline.addIssue(ModLoadingIssue.warning("ServerPackLocator is unavailable: " + reason));
            return;
        }

        LOGGER.info("Unpacking utility mod.");
        try (var in = getClass().getResourceAsStream("/SPL-utilmod.embeddedjar")) {
            if (in == null) {
                LOGGER.error("Failed to find the utility mod in the server pack locator jar. This is okay if you are running in dev!");
            } else {
                var utilityModPath = splDirectory.resolve("SPL-utilmod.jar");
                Files.copy(in, utilityModPath, StandardCopyOption.REPLACE_EXISTING);
                pipeline.addPath(utilityModPath, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize util mod.", e);
        }

        List<File> modFolders;
        try {
            modFolders = sidedLocator.getModFolders();
        } catch (Exception e) {
            String errorText = getErrorToReport(e);

            pipeline.addIssue(ModLoadingIssue.warning("ServerPackLocator failed to download mods: " + errorText)
                    .withCause(e));
            LOGGER.error("SPL failed to download the server pack.", e);
            ModAccessor.setStatusLine("FAILED to download server pack");
            return;
        }
        ModAccessor.setStatusLine("Successfully downloaded server pack");

        for (var modFolder : modFolders) {
            IModFileCandidateLocator.forFolder(modFolder, "").findCandidates(launchContext, pipeline);
        }
    }

    private static String getErrorToReport(Exception e) {
        // Unwrap some common exception types.
        Exception reportedException = e;
        if (e instanceof UncheckedIOException ioe) {
            reportedException = ioe.getCause();
        }

        if (reportedException instanceof ConnectException) {
            return "Could not connect";
        }
        return reportedException.toString();
    }

    @Override
    public String toString() {
        return "serverpacklocator";
    }
}
