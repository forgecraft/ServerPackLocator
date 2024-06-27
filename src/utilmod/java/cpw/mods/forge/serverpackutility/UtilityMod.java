//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package cpw.mods.forge.serverpackutility;

import com.mojang.authlib.GameProfile;
import cpw.mods.forge.serverpacklocator.ModAccessor;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("serverpacklocatorutility")
public class UtilityMod {
    private static final Logger LOG = LoggerFactory.getLogger(UtilityMod.class);

    public UtilityMod() {
        NeoForge.EVENT_BUS.addListener(this::onServerStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStop);
    }

    private void onServerStart(ServerStartedEvent startedEvent) {
        LOG.info("Initializing ServerPackLocator utility mod...");

        var server = startedEvent.getServer();
        ModAccessor.setAllowListStrategy(uuid -> server.submit(() -> {
            if (server.getPlayerList().isUsingWhitelist()) {
                return server.getPlayerList().getWhiteList().isWhiteListed(new GameProfile(uuid, "")); //Name does not matter
            } else {
                return true;
            }
        }).join());
        ModAccessor.setLogIps(server.logIPs());
    }

    private void onServerStop(ServerStoppedEvent stoppedEvent) {
        ModAccessor.setAllowListStrategy(null);
        ModAccessor.setLogIps(true);
        LOG.info("Uninitialized ServerPackLocator utility mod.");
    }
}
