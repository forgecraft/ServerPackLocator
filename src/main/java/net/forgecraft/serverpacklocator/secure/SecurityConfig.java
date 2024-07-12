package net.forgecraft.serverpacklocator.secure;

import com.electronwill.nightconfig.core.EnumGetMethod;
import com.electronwill.nightconfig.core.conversion.SpecEnum;
import com.electronwill.nightconfig.core.conversion.SpecNotNull;
import net.forgecraft.serverpacklocator.utils.ObjectUtils;

public class SecurityConfig {

    public static final class Default {

        private static final PasswordSecurityConfig PASSWORD_SECURITY_CONFIG = ObjectUtils.make(
                new PasswordSecurityConfig(),
                c -> c.password = "!!CHANGEME_WHEN_USING_PASSWORD_MODE!!"
        );

        public static final SecurityConfig INSTANCE = ObjectUtils.make(
                new SecurityConfig(),
                c -> {
                    c.type = SecurityType.PUBLICKEY;
                    c.password = PASSWORD_SECURITY_CONFIG;
                }
        );
    }

    @SpecEnum(method = EnumGetMethod.NAME_IGNORECASE)
    private SecurityType type;
    private PasswordSecurityConfig password;

    public SecurityType getType() {
        return type;
    }

    public PasswordSecurityConfig getPassword() {
        return password;
    }

    public static class PasswordSecurityConfig {
        @SpecNotNull
        private String password;

        public String getPassword() {
            return password;
        }

    }

    public enum SecurityType {
        PASSWORD,
        PUBLICKEY
    }
}
