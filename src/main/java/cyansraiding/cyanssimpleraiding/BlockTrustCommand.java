package cyansraiding.cyanssimpleraiding;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BlockTrustCommand implements CommandExecutor {
    private final BlockHealthListener blockHealthListener;

    public BlockTrustCommand(BlockHealthListener blockHealthListener) {
        this.blockHealthListener = blockHealthListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        if (args.length != 1) {
            player.sendMessage("Usage: /" + label + " <playername>");
            return true;
        }

        Player targetPlayer = player.getServer().getPlayer(args[0]);
        if (targetPlayer == null) {
            player.sendMessage("[§9§lCSR§r§f] Player not found.");
            return true;
        }

        // The command now directly trusts/untrusts a player without needing to specify a container.
        if (command.getName().equalsIgnoreCase("trust")) {
            boolean success = blockHealthListener.addPlayerToGlobalTrustList(player, targetPlayer.getUniqueId());
            if (success) {
                player.sendMessage(String.format("[§9§lCSR§r§f] You have trusted %s with your containers.", targetPlayer.getName()));
            } else {
                player.sendMessage(String.format("[§9§lCSR§r§f] %s is already trusted.", targetPlayer.getName()));
            }
        } else if (command.getName().equalsIgnoreCase("untrust")) {
            boolean success = blockHealthListener.removePlayerFromGlobalTrustList(player, targetPlayer.getUniqueId());
            if (success) {
                player.sendMessage(String.format("[§9§lCSR§r§f] You have untrusted %s with your containers.", targetPlayer.getName()));
            } else {
                player.sendMessage(String.format("[§9§lCSR§r§f] %s was not trusted.", targetPlayer.getName()));
            }
        }

        return true;
    }
}
