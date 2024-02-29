package cpw.mods.forge.serverpacklocator.secure;

import com.electronwill.nightconfig.core.EnumGetMethod;
import com.electronwill.nightconfig.core.conversion.SpecEnum;
import com.electronwill.nightconfig.core.conversion.SpecNotNull;
import cpw.mods.forge.serverpacklocator.utils.ObjectUtils;

public class SecurityConfig {

    public static final class Default {

        private static final PasswordSecurityConfig PASSWORD_SECURITY_CONFIG = ObjectUtils.make(
                new PasswordSecurityConfig(),
                c -> c.password = "!!CHANGEME_WHEN_USING_PASSWORD_MODE!!"
        );

        private static final PublicKeyPairSecurityConfig PUBLICKEY_SECURITY_CONFIG = ObjectUtils.make(
                new PublicKeyPairSecurityConfig(),
                c -> c.validateChallenges = true
        );

        public static final SecurityConfig INSTANCE = ObjectUtils.make(
                new SecurityConfig(),
                c -> {
                    c.type = SecurityType.PUBLICKEY;
                    c.password = PASSWORD_SECURITY_CONFIG;
                    c.publicKeyPair = PUBLICKEY_SECURITY_CONFIG;
                }
        );
    }

    @SpecEnum(method = EnumGetMethod.NAME_IGNORECASE)
    private SecurityType type;
    private PasswordSecurityConfig password;
    private PublicKeyPairSecurityConfig publicKeyPair;

    public SecurityType getType() {
        return type;
    }

    public PasswordSecurityConfig getPassword() {
        return password;
    }

    public PublicKeyPairSecurityConfig getPublicKeyPair() {
        return publicKeyPair;
    }

    public static class PasswordSecurityConfig {
        @SpecNotNull
        private String password;

        public String getPassword() {
            return password;
        }

    }

    public static class PublicKeyPairSecurityConfig {
        @SpecNotNull
        private boolean validateChallenges;

        public boolean isValidateChallenges() {
            return validateChallenges;
        }

    }

    public enum SecurityType {
        PASSWORD,
        PUBLICKEY
    }
}
