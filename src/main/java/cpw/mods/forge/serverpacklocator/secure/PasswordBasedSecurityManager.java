package cpw.mods.forge.serverpacklocator.secure;

import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.function.Function;

public final class PasswordBasedSecurityManager implements IConnectionSecurityManager<SecurityConfig.PasswordSecurityConfig>
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
    public void onClientConnectionCreation(final URLConnection connection)
    {
        connection.setRequestProperty("Authentication", "Basic " + passwordHash);
    }

    @Override
    public void onAuthenticateComplete(String challengeString) {

    }

    @Override
    public void authenticateConnection(URLConnection connection) {

    }

    @Override
    public boolean onServerConnectionRequest(ChannelHandlerContext ctx, final FullHttpRequest msg)
    {
        final String authHeader = msg.headers().get("Authentication");
        if (!authHeader.startsWith("Basic "))
        {
            LOGGER.warn("User tried to login with different authentication scheme: " + authHeader);
            return false;
        }

        final String auth = authHeader.substring(6);
        if (!auth.equals(passwordHash))
        {
            LOGGER.warn("User tried to login with wrong password: " + auth);
            return false;
        }
        return true;
    }

    @Override
    public Function<SecurityConfig, SecurityConfig.PasswordSecurityConfig> getConfigurationExtractor(SidedPackHandler<?> handler) {
        return config -> {
            final SecurityConfig.PasswordSecurityConfig passwordConfig = config.getPassword();
            if (passwordConfig == null) {
                LOGGER.fatal("Invalid configuration file {} found. Could not locate server password security configuration. " +
                        "Repair or delete this file to continue", handler.getConfigFilePath());
                throw new IllegalStateException("Invalid configuration file found, please delete or correct");
            }
            return passwordConfig;
        };
    }

    @Override
    public boolean validateConfiguration(SidedPackHandler<?> handler, SecurityConfig.PasswordSecurityConfig passwordSecurityConfig) {
        final String password = passwordSecurityConfig.getPassword();
        if (password.isEmpty()) {
            LOGGER.fatal("Invalid configuration file {} found. Could not locate server password. " +
                    "Repair or delete this file to continue", handler.getConfigFilePath());
            return false;
        }

        return true;
    }

    @Override
    public void initialize(final SecurityConfig.PasswordSecurityConfig config)
    {
        final String password = config.getPassword();
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(password.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(Integer.toHexString(b & 0xff));
            }
            this.passwordHash = sb.toString().toUpperCase(Locale.ROOT);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("Missing MD5 hashing algorithm", e);
        }
    }

    @Override
    public void onServerResponse(ChannelHandlerContext ctx, FullHttpRequest msg, FullHttpResponse resp) {

    }
}
