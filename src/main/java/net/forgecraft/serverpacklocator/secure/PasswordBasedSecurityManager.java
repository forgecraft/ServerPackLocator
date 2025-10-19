package net.forgecraft.serverpacklocator.secure;

import com.google.common.hash.Hashing;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import net.forgecraft.serverpacklocator.ConfigException;
import net.forgecraft.serverpacklocator.utils.NonceUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public final class PasswordBasedSecurityManager implements IConnectionSecurityManager
{
    private static final PasswordBasedSecurityManager INSTANCE = new PasswordBasedSecurityManager();
    private static final Logger LOGGER = LogManager.getLogger();
    private String passwordHash = "";

    public static PasswordBasedSecurityManager getInstance()
    {
        return INSTANCE;
    }

    private PasswordBasedSecurityManager()
    {
    }

    @Override
    public void onClientConnectionCreation(HttpRequest.Builder requestBuilder)
    {
        requestBuilder.header("Authentication", "Basic " + passwordHash);
    }

    @Override
    public boolean onServerConnectionRequest(ChannelHandlerContext ctx, final FullHttpRequest msg)
    {
        final String authHeader = msg.headers().get("Authentication");
        if (!authHeader.startsWith("Basic "))
        {
            LOGGER.warn("User tried to login with different authentication scheme: {}", authHeader);
            return false;
        }

        final String auth = authHeader.substring(6);
        if (!auth.equals(passwordHash))
        {
            LOGGER.warn("User tried to login with wrong password: {}", auth);
            return false;
        }
        return true;
    }

    @Override
    public void initialize(SecurityConfig config) throws ConfigException
    {
        var passwordConfig = config.getPassword();
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
    public void onServerResponse(ChannelHandlerContext ctx, FullHttpRequest msg, FullHttpResponse resp) {
        //We need to set a challenge for the client to respond to
        //However we do not validate it at all in this security mode.
        final String challenge = NonceUtils.createNonce();
        resp.headers().set("Challenge", Base64.getEncoder().encodeToString(challenge.getBytes(StandardCharsets.UTF_8)));
    }

    @Nullable
    @Override
    public String getUnavailabilityReason() {
        return null;
    }
}
