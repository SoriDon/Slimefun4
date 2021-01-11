package io.github.thebusybiscuit.slimefun4.implementation.items.gps;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.cscorelib2.chat.ChatColors;
import io.github.thebusybiscuit.cscorelib2.item.CustomItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunPlugin;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.papermc.lib.PaperLib;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.Slimefun.Lists.RecipeType;
import me.mrCookieSlime.Slimefun.Objects.Category;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.SlimefunItemStack;

/**
 * The {@link ElevatorPlate} is a quick way of teleportation.
 * You can place multiple {@link ElevatorPlate ElevatorPlates} along the y axis
 * to teleport between them.
 *
 * @author TheBusyBiscuit
 * @author Walshy
 */
public class ElevatorPlate extends SimpleSlimefunItem<BlockUseHandler> {

    /**
     * This is our key for storing the floor name.
     */
    private static final String DATA_KEY = "floor";
    private static final int GUI_SIZE = 27;

    /**
     * This is our {@link Set} of currently teleporting {@link Player Players}.
     * It is used to prevent them from triggering the {@link ElevatorPlate} they land on.
     */
    private final Set<UUID> users = new HashSet<>();

    @ParametersAreNonnullByDefault
    public ElevatorPlate(Category category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe, ItemStack recipeOutput) {
        super(category, item, recipeType, recipe, recipeOutput);

        addItemHandler(onPlace());
    }

    @Nonnull
    private BlockPlaceHandler onPlace() {
        return new BlockPlaceHandler(false) {

            @Override
            public void onPlayerPlace(BlockPlaceEvent e) {
                Block b = e.getBlock();
                BlockStorage.addBlockInfo(b, DATA_KEY, ChatColor.WHITE + "Floor #0");
                BlockStorage.addBlockInfo(b, "owner", e.getPlayer().getUniqueId().toString());
            }
        };
    }

    @Nonnull
    @Override
    public BlockUseHandler getItemHandler() {
        return e -> {
            Block b = e.getClickedBlock().get();

            if (BlockStorage.getLocationInfo(b.getLocation(), "owner").equals(e.getPlayer().getUniqueId().toString())) {
                openEditor(e.getPlayer(), b);
            }
        };
    }

    @Nonnull
    public List<Block> getFloors(@Nonnull Block b) {
        List<Block> floors = new LinkedList<>();

        for (int y = b.getWorld().getMaxHeight(); y > 0; y--) {
            if (y == b.getY()) {
                floors.add(b);
                continue;
            }

            Block block = b.getWorld().getBlockAt(b.getX(), y, b.getZ());

            if (block.getType() == getItem().getType() && BlockStorage.check(block, getId())) {
                floors.add(block);
            }
        }

        return floors;
    }

    @ParametersAreNonnullByDefault
    public void openInterface(Player p, Block b) {
        if (users.remove(p.getUniqueId())) {
            return;
        }

        List<Block> floors = getFloors(b);

        if (floors.size() < 2) {
            SlimefunPlugin.getLocalization().sendMessage(p, "machines.ELEVATOR.no-destinations", true);
        } else {
            openFloorSelector(b, floors, p, 1);
        }
    }

    @ParametersAreNonnullByDefault
    private void openFloorSelector(Block b, List<Block> floors, Player p, int page) {
        ChestMenu menu = new ChestMenu(SlimefunPlugin.getLocalization().getMessage(p, "machines.ELEVATOR.pick-a-floor"));
        menu.setEmptySlotsClickable(false);
        int pages = 1 + (floors.size() / GUI_SIZE);
        int idx = page == 1 ? 0 : GUI_SIZE * (page - 1);

        for (int i = 0; i < Math.min(GUI_SIZE, floors.size() - idx); i++) {
            Block block = floors.get(idx + i);
            String floor = ChatColors.color(BlockStorage.getLocationInfo(block.getLocation(), DATA_KEY));

            if (block.getY() == b.getY()) {
                menu.addItem(i, new CustomItem(
                    Material.COMPASS,
                    ChatColor.GRAY + "> " + (floors.size() - i) + ". " + ChatColor.BLACK + floor,
                    SlimefunPlugin.getLocalization().getMessage(p, "machines.ELEVATOR.current-floor") + ' ' + ChatColor.WHITE + floor
                ), (player, i1, itemStack, clickAction) -> false);
            } else {
                menu.addItem(i, new CustomItem(
                    Material.PAPER,
                    ChatColor.GRAY + "> " + (floors.size() - i) + ". " + ChatColor.BLACK + floor,
                    SlimefunPlugin.getLocalization().getMessage(p, "machines.ELEVATOR.click-to-teleport") + ' ' + ChatColor.WHITE + floor
                ), (player, slot, itemStack, clickAction) -> {
                    teleport(player, floor, block);
                    return false;
                });
            }
        }

        // 0 index so size is the first slot of the last row.
        for (int i = GUI_SIZE; i < GUI_SIZE + 9; i++) {
            if (i == GUI_SIZE + 2 && pages > 1 && page != 1) {
                menu.addItem(i, ChestMenuUtils.getPreviousButton(p, page, pages), (player, i1, itemStack, clickAction) -> {
                    openFloorSelector(b, floors, p, page - 1);
                    return false;
                });
            } else if (i == GUI_SIZE + 6 && pages > 1 && page != pages) {
                menu.addItem(i, ChestMenuUtils.getNextButton(p, page, pages), (player, i1, itemStack, clickAction) -> {
                    openFloorSelector(b, floors, p, page + 1);
                    return false;
                });
            } else {
                menu.addItem(i, ChestMenuUtils.getBackground(), (player, i1, itemStack, clickAction) -> false);
            }
        }

        menu.open(p);
    }

    @ParametersAreNonnullByDefault
    private void teleport(Player player, String floorName, Block target) {
        SlimefunPlugin.runSync(() -> {
            users.add(player.getUniqueId());

            float yaw = player.getEyeLocation().getYaw() + 180;

            if (yaw > 180) {
                yaw = -180 + (yaw - 180);
            }

            Location destination = new Location(player.getWorld(), target.getX() + 0.5, target.getY() + 0.4, target.getZ() + 0.5, yaw, player.getEyeLocation().getPitch());

            PaperLib.teleportAsync(player, destination).thenAccept(teleported -> {
                if (teleported.booleanValue()) {
                    player.sendTitle(ChatColor.WHITE + ChatColors.color(floorName), null, 20, 60, 20);
                }
            });
        });
    }

    @ParametersAreNonnullByDefault
    public void openEditor(Player p, Block b) {
        ChestMenu menu = new ChestMenu("Elevator Settings");

        menu.addItem(4, new CustomItem(Material.NAME_TAG, "&7Floor Name &e(Click to edit)", "", ChatColor.WHITE + ChatColors.color(BlockStorage.getLocationInfo(b.getLocation(), DATA_KEY))));
        menu.addMenuClickHandler(4, (pl, slot, item, action) -> {
            pl.closeInventory();
            pl.sendMessage("");
            SlimefunPlugin.getLocalization().sendMessage(p, "machines.ELEVATOR.enter-name");
            pl.sendMessage("");

            ChatUtils.awaitInput(pl, message -> {
                BlockStorage.addBlockInfo(b, DATA_KEY, message.replace(ChatColor.COLOR_CHAR, '&'));

                pl.sendMessage("");
                SlimefunPlugin.getLocalization().sendMessage(p, "machines.ELEVATOR.named", msg -> msg.replace("%floor%", message));
                pl.sendMessage("");

                openEditor(pl, b);
            });

            return false;
        });

        menu.open(p);
    }

}
