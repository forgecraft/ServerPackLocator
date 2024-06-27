package cpw.mods.forge.serverpacklocator.client;

import com.electronwill.nightconfig.core.conversion.SpecIntInRange;
import com.electronwill.nightconfig.core.conversion.SpecNotNull;
import cpw.mods.forge.serverpacklocator.secure.SecurityConfig;
import cpw.mods.forge.serverpacklocator.secure.SecurityConfigHolder;
import cpw.mods.forge.serverpacklocator.utils.ObjectUtils;

import java.util.ArrayList;
import java.util.List;

public class ClientConfig implements SecurityConfigHolder {

    public static final class Default {

        private static final Client CLIENT = ObjectUtils.make(
                new Client(),
                c -> {
                    c.remoteServer = "http://localhost:8080/";
                }
        );

        public static final ClientConfig INSTANCE = ObjectUtils.make(
                new ClientConfig(),
                c -> {
                    c.client = CLIENT;
                    c.security = SecurityConfig.Default.INSTANCE;
                }
        );
    }


    @SpecNotNull
    private Client client;
    @SpecNotNull
    private SecurityConfig security;


    public Client getClient() {
        return client;
    }

    @Override
    public SecurityConfig getSecurity() {
        return security;
    }

    public static class Client {

        private String remoteServer;

        @SpecNotNull
        private List<DownloadedServerContent> downloadedServerContent = new ArrayList<>();

        public String getRemoteServer() {
            return remoteServer;
        }

        public List<DownloadedServerContent> getDownloadedServerContent() {
            return downloadedServerContent;
        }
    }

    public static class DownloadedServerContent {
        @SpecNotNull
        private String name;

        @SpecNotNull
        private List<String> blackListRegex;

        public String getName() {
            return name;
        }

        public List<String> getBlackListRegex() {
            return blackListRegex;
        }

    }

}
