package cpw.mods.forge.serverpacklocator.server;

import com.electronwill.nightconfig.core.EnumGetMethod;
import com.electronwill.nightconfig.core.conversion.Conversion;
import com.electronwill.nightconfig.core.conversion.SpecEnum;
import com.electronwill.nightconfig.core.conversion.SpecIntInRange;
import com.electronwill.nightconfig.core.conversion.SpecNotNull;
import cpw.mods.forge.serverpacklocator.secure.SecurityConfig;
import cpw.mods.forge.serverpacklocator.utils.ObjectUtils;
import cpw.mods.forge.serverpacklocator.utils.SyncType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ServerConfig {

    public static final class Default {

        private static final DirectoryServerContent DIRECTORY_SERVER_CONTENT = ObjectUtils.make(
                new DirectoryServerContent(),
                c -> c.path = "servermods"
        );

        private static final ExposedServerContent EXPOSED_SERVER_CONTENT = ObjectUtils.make(
                new ExposedServerContent(),
                c -> {
                    c.syncType = SyncType.LOADED_SERVER;
                    c.name = "servermods";
                    c.directory = DIRECTORY_SERVER_CONTENT;
                }
        );

        private static final Server SERVER = ObjectUtils.make(
                new Server(),
                c -> {
                    c.port = 8080;
                    c.exposedServerContent.add(EXPOSED_SERVER_CONTENT);
                }
        );

        public static final ServerConfig INSTANCE = ObjectUtils.make(
                new ServerConfig(),
                c -> {
                    c.server = SERVER;
                    c.security = SecurityConfig.Default.INSTANCE;
                }
        );
    }



    @SpecNotNull
    private Server server;
    @SpecNotNull
    private SecurityConfig security;

    public Server getServer() {
        return server;
    }

    public SecurityConfig getSecurity() {
        return security;
    }

    public static class Server {



        @SpecIntInRange(min = 0, max = 65535)
        private int port;
        @SpecNotNull
        private List<ExposedServerContent> exposedServerContent = new ArrayList<>();

        public int getPort() {
            return port;
        }

        public List<ExposedServerContent> getExposedServerContent() {
            return exposedServerContent;
        }
    }

    public static class ExposedServerContent {

        @SpecEnum(method = EnumGetMethod.NAME_IGNORECASE)
        private SyncType syncType;
        @SpecNotNull
        private String name;
        @SpecNotNull
        private DirectoryServerContent directory;

        public String getName() {
            return name;
        }

        public SyncType getSyncType() {
            return syncType;
        }

        public DirectoryServerContent getDirectory() {
            return directory;
        }

    }

    public static class DirectoryServerContent {

        @SpecNotNull
        private String path;
        private String targetPath;

        public String getPath() {
            return path;
        }

        public String getTargetPath() {
            return targetPath != null ? targetPath : path;
        }
    }
}
