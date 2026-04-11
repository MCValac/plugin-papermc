package io.github.mcvalac.mcbackpack.papermc;

import io.github.mcvalac.mcbackpack.api.db.IMCBackpackDB;
import io.github.mcvalac.mcbackpack.common.MCBackpackProvider;
import io.github.mcvalac.mcbackpack.common.db.mysql.MCBackpackMySQL;
import io.github.mcvalac.mcbackpack.common.db.sqlite.MCBackpackSQLite;
import io.github.mcengine.mcutil.MCUtil;
import io.github.mcengine.mcextension.commands.MCExtensionCommand;
import io.github.mcengine.mcextension.commands.MCExtensionTabCompleter;
import io.github.mcengine.mcextension.common.MCExtensionManager;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Primary PaperMC plugin entrypoint for MCBackpack.
 */
public final class MCBackpackPlugin extends JavaPlugin {

    private IMCBackpackDB database;
    private MCExtensionManager extensionManager;
    private Executor asyncExecutor;

    @Override
    public void onEnable() {
        asyncExecutor = Executors.newCachedThreadPool();

        saveDefaultConfig();
        reloadConfig();
        database = createDatabaseFromConfig();

        if (database == null) {
            getLogger().severe("Failed to initialize database. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        new MCBackpackProvider(database);

        extensionManager = new MCExtensionManager(-1);
        extensionManager.loadAllExtensions(this, asyncExecutor);
        registerExtensionCommand();
        checkForUpdates();

        getLogger().info(() -> "MCBackpack initialized");
    }

    @Override
    public void onDisable() {
        MCBackpackProvider provider = MCBackpackProvider.getProvider();
        if (provider != null) {
            provider.close();
        }

        if (extensionManager != null) {
            extensionManager.disableAllExtensions(this, asyncExecutor);
        }

        asyncExecutor = null;
        database = null;
        extensionManager = null;
    }

    private void registerExtensionCommand() {
        var command = getCommand("mcbackpack");
        if (command == null) {
            getLogger().severe("Command 'mcbackpack' is not defined in plugin.yml");
            return;
        }
        MCExtensionCommand executor = new MCExtensionCommand(this, extensionManager, asyncExecutor);
        command.setExecutor(executor);
        command.setTabCompleter(new MCExtensionTabCompleter(extensionManager));
    }

    private void checkForUpdates() {
        try {
            PluginMeta meta = getPluginMeta();
            boolean newerAvailable = MCUtil.compareVersion(
                    "github",
                    meta.getVersion(),
                    "MCValac",
                    "plugin-papermc",
                    System.getenv("GITHUB_TOKEN")
            );

            if (newerAvailable) {
                getLogger().warning("A newer MCBackpackPaperMC version is available on GitHub.");
            }
        } catch (IOException ex) {
            getLogger().warning("Version check failed: " + ex.getMessage());
        }
    }

    private IMCBackpackDB createDatabaseFromConfig() {
        String dbType = getConfig().getString("db.type", "sqlite");
        if ("mysql".equalsIgnoreCase(dbType)) {
            String host = getConfig().getString("db.mysql.host", "localhost");
            String port = getConfig().getString("db.mysql.port", "3306");
            String databaseName = getConfig().getString("db.mysql.database", "mcbackpack");
            String user = getConfig().getString("db.mysql.user", "root");
            String password = getConfig().getString("db.mysql.password", "mcbackpack");
            boolean ssl = Boolean.parseBoolean(getConfig().getString("db.mysql.ssl", "false"));

            return new MCBackpackMySQL(host, port, databaseName, user, password, ssl);
        }

        String sqlitePath = getConfig().getString("db.sqlite.path", getDataFolder().getPath());
        return new MCBackpackSQLite(sqlitePath);
    }
}
