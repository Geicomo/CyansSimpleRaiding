package cyansraiding.cyanssimpleraiding;

import org.bukkit.plugin.java.JavaPlugin;

public final class CyansSimpleRaiding extends JavaPlugin {

    // Assume your BlockHealthListener class is here
    private final BlockHealthListener blockHealthListener = new BlockHealthListener(this);

    @Override
    public void onEnable() {
        blockHealthListener.setDataFolder(getDataFolder());
        blockHealthListener.loadBlockData();
        getServer().getPluginManager().registerEvents(blockHealthListener, this);

        // Register command executors
        this.getCommand("csradmin").setExecutor(new CsrAdminCommand(blockHealthListener));
        this.getCommand("csr").setExecutor(new CsrCommands(blockHealthListener, this));  // Pass both BlockHealthListener and JavaPlugin instance

        // Update checker logic
        new UpdateChecker(this, 118344).getVersion(latestVersion -> {
            String currentVersion = this.getDescription().getVersion();
            if (currentVersion.equals(latestVersion)) {
                getLogger().info("No new updates available. You are using the latest version: " + currentVersion);
            } else {
                getLogger().info("A new update is available! Current version: " + currentVersion + ", Latest version: " + latestVersion);
            }
        });

        getLogger().info("Cyans Simple Raiding Enabled");
    }

    @Override
    public void onDisable() {
        // Save your data
        blockHealthListener.saveBlockData();

        getLogger().info("Cyans Simple Raiding Shutting down...");
    }
}
