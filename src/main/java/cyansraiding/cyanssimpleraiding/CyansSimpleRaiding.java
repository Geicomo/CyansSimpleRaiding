package cyansraiding.cyanssimpleraiding;

import org.bukkit.plugin.java.JavaPlugin;
public final class CyansSimpleRaiding extends JavaPlugin {

    // Assume your BlockHealthListener class is here
    private final BlockHealthListener BlockHealthListener = new BlockHealthListener();

    @Override
    public void onEnable() {
        BlockHealthListener.setDataFolder(getDataFolder());
        BlockHealthListener.loadBlockData();
        getServer().getPluginManager().registerEvents(BlockHealthListener, this);


        this.getCommand("trust").setExecutor(new BlockTrustCommand(BlockHealthListener));
        this.getCommand("untrust").setExecutor(new BlockTrustCommand(BlockHealthListener));
        this.getCommand("csradmin").setExecutor(new CsrAdminCommand(BlockHealthListener));

        getLogger().info("CSR Enabled");
    }

    @Override
    public void onDisable() {
        // Save your data
        BlockHealthListener.saveBlockData();

        getLogger().info("CSR Shutting down...");
    }
}