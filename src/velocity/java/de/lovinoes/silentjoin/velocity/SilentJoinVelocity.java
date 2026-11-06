package de.lovinoes.silentjoin.velocity;

import com.google.inject.Inject;
import com.mojang.brigadier.Command;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class SilentJoinVelocity {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final String RELOAD_PERMISSION = "silentjoin.proxy.reload";
    private record Settings(boolean enabled, String join, String serverSwitch, String quit) {}

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path configFile;
    private final Map<Player, String> servers = new IdentityHashMap<>();
    private volatile Settings settings = new Settings(true, "", "", "");

    @Inject
    public SilentJoinVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.configFile = dataDirectory.resolve("config.yml");
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        reloadSettings();

        final BrigadierCommand command = buildCommand();
        final CommandManager commands = proxy.getCommandManager();
        commands.register(commands.metaBuilder(command).plugin(this).build(), command);
    }

    @Subscribe
    public void onServerConnect(ServerPostConnectEvent event) {
        final Player player = event.getPlayer();
        final String server = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse(null);
        if (server == null) {
            return;
        }

        synchronized (servers) {
            if (!player.isActive()) {
                return;
            }

            final String previous = servers.put(player, server);
            final Settings current = settings;
            if (!current.enabled()) {
                return;
            }

            if (previous == null) {
                broadcast(current.join(), player, server, "");
            } else {
                broadcast(current.serverSwitch(), player, server, previous);
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        final Player player = event.getPlayer();

        synchronized (servers) {
            final String server = servers.remove(player);
            final Settings current = settings;
            if (server != null && current.enabled()) {
                broadcast(current.quit(), player, server, "");
            }
        }
    }

    private void broadcast(String template, Player player, String server, String previousServer) {
        if (template.isBlank()) {
            return;
        }
        proxy.sendMessage(MINI.deserialize(
                template,
                Placeholder.unparsed("player", player.getUsername()),
                Placeholder.unparsed("name", player.getUsername()),
                Placeholder.unparsed("server", server),
                Placeholder.unparsed("previous_server", previousServer)));
    }

    private synchronized boolean reloadSettings() {
        try {
            saveDefaultConfig();
            final ConfigurationNode config = YamlConfigurationLoader.builder().path(configFile).build().load();
            settings = new Settings(
                    config.node("enabled").getBoolean(true),
                    config.node("join", "message").getString(""),
                    config.node("switch", "message").getString(""),
                    config.node("quit", "message").getString(""));
            return true;
        } catch (IOException e) {
            logger.error("Could not read config.yml", e);
            return false;
        }
    }

    private void saveDefaultConfig() throws IOException {
        if (Files.exists(configFile)) {
            return;
        }

        Files.createDirectories(configFile.getParent());
        try (InputStream defaults = getClass().getResourceAsStream("/velocity-config.yml")) {
            if (defaults == null) {
                throw new IOException("velocity-config.yml is missing from the plugin jar");
            }
            Files.copy(defaults, configFile);
        }
    }

    private void reload(CommandSource source) {
        if (reloadSettings()) {
            source.sendMessage(Component.text("SilentJoin proxy config reloaded.", NamedTextColor.GREEN));
        } else {
            source.sendMessage(Component.text(
                    "Could not read config.yml, keeping the previous settings. See the console for details.",
                    NamedTextColor.RED));
        }
    }

    private BrigadierCommand buildCommand() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("silentjoinproxy")
                .requires(source -> source.hasPermission(RELOAD_PERMISSION))
                .executes(context -> {
                    context.getSource()
                            .sendMessage(Component.text("Usage: /silentjoinproxy reload", NamedTextColor.GRAY));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand.literalArgumentBuilder("reload")
                        .executes(context -> {
                            reload(context.getSource());
                            return Command.SINGLE_SUCCESS;
                        })));
    }
}
