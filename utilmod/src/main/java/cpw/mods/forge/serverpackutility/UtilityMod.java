//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package cpw.mods.forge.serverpackutility;

import com.mojang.authlib.GameProfile;
import cpw.mods.forge.serverpacklocator.ModAccessor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("serverpacklocatorutility")
public class UtilityMod {

    private static final Logger LOGGER = LogManager.getLogger();

    public UtilityMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onServerStart);
    }

    private void onServerStart(ServerStartedEvent startedEvent) {
        try {
            ModAccessor.setIsWhiteListed((id) -> startedEvent.getServer().submit(() -> {
                return startedEvent.getServer().getPlayerList().getWhiteList().isWhiteListed(new GameProfile(id, "")); //Name does not matter
            }));
            ModAccessor.setIsWhiteListEnabled(() -> startedEvent.getServer().submit(() -> startedEvent.getServer().getPlayerList().isUsingWhitelist()));
        } catch (Throwable error) {
            LOGGER.error("Failed to setup Blackboard!", error);
        }
    }
}
