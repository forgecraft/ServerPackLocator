package net.forgecraft.serverpacklocator.secure;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import net.forgecraft.serverpacklocator.ConfigException;

import javax.annotation.Nullable;
import java.net.http.HttpRequest;

public interface IConnectionSecurityManager
{
    default void decorateClientRequest(final HttpRequest.Builder requestBuilder, final boolean authenticated) {
    }

    default void handleClientResponse(final java.net.http.HttpResponse<?> response) {
    }

    boolean validateServerRequest(ChannelHandlerContext ctx, FullHttpRequest msg);

    default void decorateServerResponse(final ChannelHandlerContext ctx, final FullHttpRequest msg, final HttpResponse resp) {
    }

    static IConnectionSecurityManager create(final SecurityConfig config) throws ConfigException {
        return switch (config.getType()) {
            case NONE -> NullSecurityManager.INSTANCE;
            case PASSWORD -> new PasswordBasedSecurityManager(config);
            case PUBLICKEY -> new ProfileKeyPairBasedSecurityManager();
            case null -> throw new ConfigException("No securityType is set.");
        };
    }

    @Nullable
    default String getUnavailabilityReason() {
        return null;
    }

    boolean needsAuthRequest();
}
