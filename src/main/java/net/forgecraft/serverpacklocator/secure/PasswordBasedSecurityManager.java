package net.forgecraft.serverpacklocator.secure;

import com.google.common.hash.Hashing;
import com.mojang.logging.LogUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import net.forgecraft.serverpacklocator.ConfigException;
import org.slf4j.Logger;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class PasswordBasedSecurityManager implements IConnectionSecurityManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String passwordHash;

    public PasswordBasedSecurityManager(final SecurityConfig config) throws ConfigException {
        final SecurityConfig.PasswordSecurityConfig passwordConfig = config.getPassword();
        if (passwordConfig == null) {
            throw new ConfigException("Could not locate server password security configuration.");
        }

        final String password = passwordConfig.getPassword();
        if (password.isEmpty()) {
            throw new ConfigException("No server password is set.");
        }

        passwordHash = Hashing.sha256().hashString(password, StandardCharsets.UTF_8).toString().toUpperCase(Locale.ROOT);
    }

    @Override
    public void decorateClientRequest(final HttpRequest.Builder requestBuilder, final boolean authenticated) {
        requestBuilder.header("Authentication", "Basic " + passwordHash);
    }

    @Override
    public boolean validateServerRequest(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        final String authHeader = msg.headers().get("Authentication");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            LOGGER.warn("User tried to login with invalid authentication scheme: {}", authHeader);
            return false;
        }
        final String auth = authHeader.substring(6);
        if (!auth.equals(passwordHash)) {
            LOGGER.warn("User tried to login with wrong password: {}", auth);
            return false;
        }
        return true;
    }

    @Override
    public boolean needsAuthRequest() {
        return false;
    }
}
