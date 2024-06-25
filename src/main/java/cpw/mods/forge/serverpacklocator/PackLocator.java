package cpw.mods.forge.serverpacklocator;

import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class PackLocator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void findCandidates(ILaunchContext launchContext, IDiscoveryPipeline pipeline) {
        LOGGER.info("Loading server pack locator");
        var serverPackLocator = SidedPackLocator.buildFor(LaunchEnvironmentHandler.INSTANCE.getDist());
        if (!serverPackLocator.isValid()) {
            LOGGER.warn("The server pack locator is not in a valid state, it will not load any mods");
        } else {
            serverPackLocator.initialize();
        }

        LOGGER.info("Unpacking utility mod.");
        try (var in = getClass().getResourceAsStream("/SPL-utilmod.embeddedjar")) {
            if (in == null) {
                LOGGER.error("Failed to find the utility mod in the server pack locator jar. This is okay if you are running in dev!");
            } else {
                var utilityModPath = serverPackLocator.getSplDirectory().resolve("SPL-utilmod.jar");
                Files.copy(in, utilityModPath, StandardCopyOption.REPLACE_EXISTING);
                pipeline.addPath(utilityModPath, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize util mod.", e);
        }

        boolean successfulDownload = serverPackLocator.waitForDownload();

        if (!successfulDownload) {
            pipeline.addIssue(ModLoadingIssue.error("Failed to download mods."));
            return;
        } else {
            ModAccessor.setStatusLine("FAILED to download server packs");
        }

        ModAccessor.setStatusLine("Successfully downloaded server packs");
        for (var modFolder : serverPackLocator.getModFolders()) {
            IModFileCandidateLocator.forFolder(modFolder, "").findCandidates(launchContext, pipeline);
        }
    }

    @Override
    public String toString() {
        return "serverpacklocator";
    }
}
