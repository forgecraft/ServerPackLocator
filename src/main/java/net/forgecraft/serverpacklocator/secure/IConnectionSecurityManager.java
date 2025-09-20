package net.forgecraft.serverpacklocator.secure;

import net.forgecraft.serverpacklocator.ConfigException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import javax.annotation.Nullable;
import java.net.http.HttpRequest;

public interface IConnectionSecurityManager
{
    void onClientConnectionCreation(HttpRequest.Builder requestBuilder);

    default void onAuthenticateComplete(String challengeString) {
    }

    default void authenticateConnection(HttpRequest.Builder requestBuilder) {
    }

    boolean onServerConnectionRequest(ChannelHandlerContext ctx, FullHttpRequest msg);

    void onServerResponse(ChannelHandlerContext ctx, FullHttpRequest msg, HttpResponse resp);

    static IConnectionSecurityManager create(final SecurityConfig config) throws ConfigException {
        return switch (config.getType()) {
            case PASSWORD -> new PasswordBasedSecurityManager(config);
            case PUBLICKEY -> new ProfileKeyPairBasedSecurityManager();
            case null -> throw new ConfigException("No securityType is set.");
        };
    }

    @Nullable
    String getUnavailabilityReason();
}
