package cyansraiding.cyanssimpleraiding;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CsrAdminCommand implements CommandExecutor {

    private final BlockHealthListener BlockHealthListener;

    public CsrAdminCommand(cyansraiding.cyanssimpleraiding.BlockHealthListener BlockHealthListener) {
        this.BlockHealthListener = BlockHealthListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        if (player.hasPermission("cyansraiding.csradmin")) {
            BlockHealthListener.toggleCsrAdmin(player);
        } else {
            player.sendMessage("[§9§lCSR§r§f] You do not have the permissions to use this command.");
        }
        return true;
    }
}
