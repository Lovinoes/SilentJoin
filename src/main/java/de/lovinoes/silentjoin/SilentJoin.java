package de.lovinoes.silentjoin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

public final class SilentJoin extends JavaPlugin implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final String RENAMED_KEY = "multiplayer.player.joined.renamed";
    private static final String RELOAD_PERMISSION = "silentjoin.reload";
    private record Settings(boolean enabled, String join, String joinRenamed, String quit) {}
    private volatile Settings settings = new Settings(true, "", "", "");
    private final Object reloadLock = new Object();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            settings = loadSettings();
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().log(Level.SEVERE, "Could not read config.yml, using built-in defaults", e);
        }

        if (getServer().getPluginManager().getPermission(RELOAD_PERMISSION) == null) {
            getServer().getPluginManager().addPermission(new Permission(
                    RELOAD_PERMISSION, "Allows reloading the SilentJoin config", PermissionDefault.OP));
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(buildCommand(), "Reload the SilentJoin config"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        final Settings current = settings;
        if (!current.enabled()) {
            return;
        }

        final Component oldName = renamedOldName(event.joinMessage());
        final String template = oldName != null ? current.joinRenamed() : current.join();
        event.joinMessage(render(template, event.getPlayer(), oldName));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        final Settings current = settings;
        if (!current.enabled()) {
            return;
        }

        event.quitMessage(render(current.quit(), event.getPlayer(), null));
    }

    private static Component renamedOldName(Component message) {
        if (message instanceof TranslatableComponent translatable && RENAMED_KEY.equals(translatable.key())) {
            return translatable.arguments().size() >= 2
                    ? translatable.arguments().get(1).asComponent()
                    : Component.empty();
        }
        return null;
    }

    private static Component render(String template, Player player, Component oldName) {
        if (template.isBlank()) {
            return null;
        }
        return MINI.deserialize(
                template,
                Placeholder.component("player", player.displayName()),
                Placeholder.unparsed("name", player.getName()),
                Placeholder.component("old_name", oldName != null ? oldName : Component.empty()));
    }

    private Settings loadSettings() throws IOException, InvalidConfigurationException {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File(getDataFolder(), "config.yml"));
        return new Settings(
                yaml.getBoolean("enabled", true),
                yaml.getString("join.message", ""),
                yaml.getString("join.renamed-message", ""),
                yaml.getString("quit.message", ""));
    }

    private void reload(CommandSender sender) {
        boolean success = false;
        synchronized (reloadLock) {
            saveDefaultConfig();
            try {
                settings = loadSettings();
                success = true;
            } catch (IOException | InvalidConfigurationException e) {
                getLogger().log(Level.SEVERE, "Could not reload config.yml, keeping previous settings", e);
            }
        }

        if (success) {
            sender.sendMessage(Component.text("SilentJoin config reloaded.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(
                    "Could not read config.yml, keeping the previous settings. See the console for details.",
                    NamedTextColor.RED));
        }
    }

    private LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("silentjoin")
                .requires(source -> source.getSender().hasPermission(RELOAD_PERMISSION))
                .executes(context -> {
                    context.getSource().getSender()
                            .sendMessage(Component.text("Usage: /silentjoin reload", NamedTextColor.GRAY));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reload")
                        .executes(context -> {
                            reload(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
