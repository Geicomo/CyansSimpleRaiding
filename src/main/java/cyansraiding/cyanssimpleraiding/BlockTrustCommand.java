package cyansraiding.cyanssimpleraiding;

import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.util.BlockIterator;

public class BlockTrustCommand implements CommandExecutor {
    private final BlockHealthListener BlockHealthListener;

    public BlockTrustCommand(BlockHealthListener BlockHealthListener) {
        this.BlockHealthListener = BlockHealthListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

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

        // Example: Determine the chest block the player is looking at.
        Block chestBlock = getTargetChest(player);

        if (chestBlock == null) {
            player.sendMessage("[§9§lCSR§r§f] You are not looking at a chest.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("trust")) {
            BlockHealthListener.addPlayerToBlockTrustList(player, targetPlayer.getUniqueId(), chestBlock);
        } else if (command.getName().equalsIgnoreCase("untrust")) {
            BlockHealthListener.removePlayerFromBlockTrustList(player, targetPlayer.getUniqueId(), chestBlock);
        }

        return true;
    }

    private Block getTargetChest(Player player) {
        BlockIterator iterator = new BlockIterator(player, 5); // 5 blocks deep
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (block.getState() instanceof Container) {
                return block;
            }
        }
        return null;
    }
}

