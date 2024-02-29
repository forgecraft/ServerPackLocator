package cpw.mods.forge.serverpacklocator.secure;

import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.function.Function;

public final class ConnectionSecurityManager
{
    private static final ConnectionSecurityManager INSTANCE = new ConnectionSecurityManager();
    private static final Logger LOGGER = LogManager.getLogger();

    public static ConnectionSecurityManager getInstance()
    {
        return INSTANCE;
    }

    private final Map<SecurityConfig.SecurityType, IConnectionSecurityManager<?>> securityManagers;

    private ConnectionSecurityManager()
    {
        securityManagers = Map.of(
                SecurityConfig.SecurityType.PASSWORD, PasswordBasedSecurityManager.getInstance(),
                SecurityConfig.SecurityType.PUBLICKEY, ProfileKeyPairBasedSecurityManager.getInstance()
        );
    }

    public boolean validateConfiguration(final SidedPackHandler<?> handler, SecurityConfig config)
    {
        final SecurityConfig.SecurityType securityType = config.getType();
        if (securityType == null) {
            LOGGER.fatal("Invalid configuration file {} found. Could not locate security type. " +
                                 "Repair or delete this file to continue", handler.getConfigFilePath());
            throw new IllegalStateException("Invalid configuration file found, please delete or correct");
        }
        if (!securityManagers.containsKey(securityType)) {
            LOGGER.fatal("Invalid configuration file {} found. Unknown security type: {}. " +
                                 "Repair or delete this file to continue",  handler.getConfigFilePath(), securityType);
            throw new IllegalStateException("Invalid configuration file found, please delete or correct");
        }

        final IConnectionSecurityManager<?> securityManager = securityManagers.get(securityType);
        return validateConfiguration(handler, config, securityManager);
    }

    private static <TSecurityManagerConfig> boolean validateConfiguration(final SidedPackHandler<?> handler, final SecurityConfig config, final IConnectionSecurityManager<TSecurityManagerConfig> connectionSecurityManager) {
        final Function<SecurityConfig, TSecurityManagerConfig> configurationExtractor = connectionSecurityManager.getConfigurationExtractor(handler);
        if (configurationExtractor == null)
            return true;

        final TSecurityManagerConfig securityManagerConfig = configurationExtractor.apply(config);
        if (securityManagerConfig == null)
            return false;

        return connectionSecurityManager.validateConfiguration(handler, securityManagerConfig);
    }

    public IConnectionSecurityManager<?> initialize(SidedPackHandler<?> handler, final SecurityConfig config) {
        final SecurityConfig.SecurityType securityType = config.getType();
        final IConnectionSecurityManager<?> connectionSecurityManager = securityManagers.get(securityType);
        initialize(handler, config, connectionSecurityManager);
        return connectionSecurityManager;
    }

    private static <TSecurityManagerConfig> void initialize(final SidedPackHandler<?> handler, final SecurityConfig config, final IConnectionSecurityManager<TSecurityManagerConfig> connectionSecurityManager) {
        final Function<SecurityConfig, TSecurityManagerConfig> configurationExtractor = connectionSecurityManager.getConfigurationExtractor(handler);
        if (configurationExtractor == null) {
            connectionSecurityManager.initialize();
            return;
        }

        final TSecurityManagerConfig securityManagerConfig = configurationExtractor.apply(config);
        if (securityManagerConfig == null) {
            connectionSecurityManager.initialize();
        } else {
            connectionSecurityManager.initialize(securityManagerConfig);
        }
    }

}
