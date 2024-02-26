package cpw.mods.forge.serverpacklocator;

import cpw.mods.forge.serverpacklocator.client.ClientSidedPackHandler;
import cpw.mods.forge.serverpacklocator.server.ServerSidedPackHandler;
import net.neoforged.api.distmarker.Dist;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

enum SidedPackLocator {
    CLIENT(ClientSidedPackHandler::new), DEDICATED_SERVER(ServerSidedPackHandler::new);
    private final Supplier<SidedPackHandler<?> > handler;

    SidedPackLocator(final Supplier<SidedPackHandler<?> > handler) {
        this.handler = handler;
    }

    public static SidedPackHandler<?> buildFor(Dist side) {
        return valueOf(side.toString()).handler.get();
    }
}
