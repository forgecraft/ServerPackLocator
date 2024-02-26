package cpw.mods.forge.serverpacklocator.secure;

import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;

import java.net.URLConnection;
import java.util.function.Function;

public interface IConnectionSecurityManager<TConfig>
{
    void onClientConnectionCreation(URLConnection connection);

    void onAuthenticateComplete(String challengeString);

    void authenticateConnection(URLConnection connection);

    boolean onServerConnectionRequest(ChannelHandlerContext ctx, FullHttpRequest msg);

    default Function<SecurityConfig, TConfig> getConfigurationExtractor(SidedPackHandler<?> handler) {
        return null;
    }

    default boolean validateConfiguration(SidedPackHandler<?> handler, TConfig config) {
        return true;
    }

    default void initialize(TConfig config) {
        initialize();
    }

    default void initialize() {
        throw new UnsupportedOperationException("This security manager does not support initialization without parameters");
    }

    void onServerResponse(ChannelHandlerContext ctx, FullHttpRequest msg, FullHttpResponse resp);
}
