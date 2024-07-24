package cyansraiding.cyanssimpleraiding;

import org.bukkit.plugin.java.JavaPlugin;
public final class CyansSimpleRaiding extends JavaPlugin {

    // Assume your BlockHealthListener class is here
    private final BlockHealthListener blockHealthListener = new BlockHealthListener();

    @Override
    public void onEnable() {
        blockHealthListener.setDataFolder(getDataFolder());
        blockHealthListener.loadBlockData();
        getServer().getPluginManager().registerEvents(blockHealthListener, this);


        this.getCommand("csradmin").setExecutor(new CsrAdminCommand(blockHealthListener));
        getCommand("csr").setExecutor(new CsrCommands(blockHealthListener));

        getLogger().info("CSR Enabled");
    }

    @Override
    public void onDisable() {
        // Save your data
        blockHealthListener.saveBlockData();

        getLogger().info("CSR Shutting down...");
    }
}