# Server Pack Locator

This mod acts as a plugin to [FancyModLoader](https://github.com/neoforged/FancyModLoader) that allows loading
mods and configuration files from the Minecraft server before starting the actual game.
This will keep a pack automatically up to date, without using a launcher, and allows for mods to be kept private
by authenticating players before mods can be downloaded.

> [!CAUTION]
> When you install this mod on your client, you fully trust the server you are connecting to.
> The server you set in the configuration file of this mod will be able to send you malicious software.

## Authentication Options

### Microsoft Account

SPL can reuse the access token passed to Minecraft by launchers, and validates the user's game profile UUID
against this access token. This is the default authentication mode, and requires no additional setup on clients.

```toml
[security]
type = "PUBLICKEY"
```

### Password

You can also set a shared password, which players will have to configure on their end.
Please choose a strong password since this mode is prone to brute-force attacks and less secure.

```toml
[security]
type = "PASSWORD"

[security.password]
password = "!!CHANGEME_WHEN_USING_PASSWORD_MODE!!"
```

## Server-Side Configuration

The configuration file is expected to be found at `spl/config.toml` relative to your game directory.
A default file will be created when you run the mod for the first time.

### WebServer Port

SPL opens a port to listen for HTTP connections. You can change this port in the configuration file.

```toml
[server]
port = 8080
```

#### SSL Termination
By default, the included webserver does not expose a TLS based communication channel over HTTP.
This means that any server operator wishing to expose SPLs webserver over a secure channel must set this up themselves.
Luckily this can easily be done via a reverse proxy.

> [!WARNING]
> It is highly recommended to not expose SPLs own webserver to the public, but to apply a reverse proxy. Potentially including a Web Application Firewall, and logging of requests to trace bad-actors.

##### NGINX
Creating a reverse proxy based on NGINX is simple and you can find an example below:
```conf
server {
    listen 444 ssl;
    server_name yourdomain.com;

    ssl_certificate /opt/ssl/fullchain.pem;
    ssl_certificate_key /opt/ssl/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Exposing Content

You also configure which directories to send to the client, and whether local changes by clients will be overwritten
or not. Any directory you add here will automatically be scanned for mods on the client-side.

```toml
[[server.exposedServerContent]]
name = "servermods"
syncType = "LOADED_SERVER"

[server.exposedServerContent.directory]
path = "servermods"
        
[[server.exposedServerContent]]
name = "clientmods"
syncType = "LOADED_CLIENT"

[server.exposedServerContent.directory]
path = "clientmods"
        
[[server.exposedServerContent]]
name = "config"
syncType = "FORCED_SYNC"

[server.exposedServerContent.directory]
path = "config"

[[server.exposedServerContent]]
name = "client-kubejs"
syncType = "FORCED_SYNC"
recursive = true
removeDanglingFiles = true

[server.exposedServerContent.directory]
path = "spl/client-kubejs"
targetPath = "kubejs"
```

- `targetPath` is optional and allows you to change the directory name on the client-side.
- `recursive` is optional and allows you to copy the entire directory recursively.
- `removeDanglingFiles` is optional and allows you to tell clients to remove files that are not with-in the server content.

Available sync types:

| Sync type     | Description                                                                                        |
|---------------|----------------------------------------------------------------------------------------------------|
| LOADED_SERVER | Contains mods that should be loaded on the server-side, as well as the client-side.                |
| LOADED_CLIENT | Contains mods that should only be loaded client-side.                                              |
| INITIAL_SYNC  | Files in this directory will only by copied to the client once, but not overwritten later.         |
| FORCED_SYNC   | Files in this directory will always be copied to the client and local changes will be overwritten. |

## Client-Side Configuration

The configuration file is expected to be found at `spl/config.toml` relative to your game directory.
A default file will be created when you run the mod for the first time.

### Remote Server

The server to download packs from has to be configured on the client:

```toml
[client]
remoteServer = "http://localhost:8080/"
```

### Ignoring Server Content

The client can be configured to ignore content sent by the server as follows.

The `name` attribute must match the `name` of the servercontent section, not necessarily the name of the directory
on disk.

```toml
[client]
downloadedServerContent = [{ name = "servermods", blackListRegex=["FilenameRegExp.*", "OtherFilenameRegexp.*"]}]
```

### Auto-Connecting on Startup

The client can be configured to auto-connect to a server after initial launch.

_Note: The Vanilla launchers QuickPlay feature will take priority over this feature._

```toml
[client]
quickPlayServer = "localhost:25565" # Your servers ip (The same as what you enter in game)
```