package net.forgecraft.serverpacklocator.secure;

import net.forgecraft.serverpacklocator.ConfigException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;

import javax.annotation.Nullable;
import java.net.URLConnection;
import java.net.http.HttpRequest;

public interface IConnectionSecurityManager
{
    void onClientConnectionCreation(HttpRequest.Builder requestBuilder);

    default void onAuthenticateComplete(String challengeString) {
    }

    default void authenticateConnection(HttpRequest.Builder requestBuilder) {
    }

    boolean onServerConnectionRequest(ChannelHandlerContext ctx, FullHttpRequest msg);

    void initialize(SecurityConfig config) throws ConfigException;

    void onServerResponse(ChannelHandlerContext ctx, FullHttpRequest msg, FullHttpResponse resp);

    static IConnectionSecurityManager create(SecurityConfig config) throws ConfigException {
        var securityType = config.getType();
        if (securityType == null) {
            throw new ConfigException("No securityType is set.");
        }

        var securityManager = switch (securityType) {
            case PASSWORD -> PasswordBasedSecurityManager.getInstance();
            case PUBLICKEY -> ProfileKeyPairBasedSecurityManager.getInstance();
        };

        securityManager.initialize(config);
        return securityManager;
    }

    @Nullable
    String getUnavailabilityReason();
}
