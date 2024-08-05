package net.forgecraft.serverpacklocator.client;

import com.electronwill.nightconfig.core.conversion.SpecNotNull;
import java.util.ArrayList;
import java.util.List;
import net.forgecraft.serverpacklocator.secure.SecurityConfig;
import net.forgecraft.serverpacklocator.secure.SecurityConfigHolder;
import net.forgecraft.serverpacklocator.utils.ObjectUtils;

public class ClientConfig implements SecurityConfigHolder {

    public static final class Default {

        private static final Client CLIENT = ObjectUtils.make(
                new Client(),
                c -> {
                    c.remoteServer = "http://localhost:8080/";
                    c.quickPlayServer = "";
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

        @SpecNotNull
        private String quickPlayServer;

        public String getRemoteServer() {
            return remoteServer;
        }

        public List<DownloadedServerContent> getDownloadedServerContent() {
            return downloadedServerContent;
        }

        public String getQuickPlayServer() {
            return quickPlayServer;
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
