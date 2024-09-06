package cyansraiding.cyanssimpleraiding;

import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.block.Action;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CsrCommands implements CommandExecutor, Listener {

    private final BlockHealthListener blockHealthListener;
    private final Map<UUID, Boolean> abandonMode = new HashMap<>();

    public CsrCommands(BlockHealthListener blockHealthListener, JavaPlugin plugin) {
        this.blockHealthListener = blockHealthListener;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        // Check the main command
        if (command.getName().equalsIgnoreCase("csr")) {
            if (args.length > 0) {
                switch (args[0].toLowerCase()) {
                    case "-h":
                    case "-help":
                        sendHelpMessage(player);
                        break;

                    case "-t":
                    case "trust":
                        handleTrustCommand(player, args); // Placeholder for trust command
                        break;

                    case "-unt":
                    case "untrust":
                        handleUntrustCommand(player, args); // Placeholder for untrust command
                        break;

                    case "-tg":
                    case "toggle":
                        toggleNotificationPreference(player); // Placeholder for notification toggle command
                        break;

                    case "abandon":
                        handleAbandonCommand(player);
                        break;

                    default:
                        player.sendMessage("[§9§lCSR§r§f] Unknown command. Use /csr -h for help.");
                        break;
                }
            } else {
                player.sendMessage("[§9§lCSR§r§f] Invalid usage. Use /csr help for help.");
            }
        }

        return true; // Return true if a valid command was executed
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("[§9§lCSR§r§f] Here is the help information for Cyan's Simple Raiding plugin:");
        player.sendMessage("§6/csr help§r - Shows this help message.");
        player.sendMessage("§6/csr trust <playername>§r - Trust a player with your containers.");
        player.sendMessage("§6/csr untrust <playername>§r - Untrust a player from your containers.");
        player.sendMessage("§6/csr toggle §r - Toggle how you want to display health notifications.");
        player.sendMessage("§6/csr abandon§r - Unclaim a container you own.");
        player.sendMessage("§6/admin§r - Allows admins to bypass block permissions.");
    }

    private void handleAbandonCommand(Player player) {
        // Enable abandon mode for the player
        abandonMode.put(player.getUniqueId(), true);
        player.sendMessage("[§9§lCSR§r§f] Right click the container you wish to abandon.");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Check if the player is in abandon mode
        if (abandonMode.getOrDefault(player.getUniqueId(), false)) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                Block block = event.getClickedBlock();

                // Check if the block is a container (chest, barrel, etc.)
                if (block.getState() instanceof Container) {
                    String locKey = blockHealthListener.locationKey(block.getLocation());
                    UUID ownerId = blockHealthListener.getBlockOwner(locKey);

                    if (ownerId != null && ownerId.equals(player.getUniqueId())) {
                        // Unclaim the container
                        blockHealthListener.unclaimBlock(locKey);
                        player.sendMessage("[§9§lCSR§r§f] You have made this container public.");
                    } else {
                        player.sendMessage("[§9§lCSR§r§f] You do not own this container.");
                    }

                    // Exit abandon mode after one interaction
                    abandonMode.put(player.getUniqueId(), false);
                    event.setCancelled(true); // Prevent the normal container interaction
                }
            }
        }
    }


    private void handleTrustCommand(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("Usage: /csr trust <playername>");
            return;
        }

        Player targetPlayer = player.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            player.sendMessage("[§9§lCSR§r§f] Player not found.");
            return;
        }

        boolean success = blockHealthListener.addPlayerToGlobalTrustList(player, targetPlayer.getUniqueId());
        if (success) {
            player.sendMessage(String.format("[§9§lCSR§r§f] You have trusted %s with your containers.", targetPlayer.getName()));
        } else {
            player.sendMessage(String.format("[§9§lCSR§r§f] %s is already trusted.", targetPlayer.getName()));
        }
    }

    private void handleUntrustCommand(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("Usage: /csr untrust <playername>");
            return;
        }

        Player targetPlayer = player.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            player.sendMessage("[§9§lCSR§r§f] Player not found.");
            return;
        }

        boolean success = blockHealthListener.removePlayerFromGlobalTrustList(player, targetPlayer.getUniqueId());
        if (success) {
            player.sendMessage(String.format("[§9§lCSR§r§f] You have untrusted %s with your containers.", targetPlayer.getName()));
        } else {
            player.sendMessage(String.format("[§9§lCSR§r§f] %s was not trusted.", targetPlayer.getName()));
        }
    }

    private void toggleNotificationPreference(Player player) {
        blockHealthListener.toggleNotificationPreference(player);
        BlockHealthListener.NotificationType currentType = blockHealthListener.getNotificationPreference(player);
        player.sendMessage(String.format("[§9§lCSR§r§f] Notification method set to: %s.", currentType.name()));
    }
}
