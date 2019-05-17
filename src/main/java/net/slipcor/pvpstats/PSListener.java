package net.slipcor.pvpstats;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * Listener class
 *
 * @author slipcor
 */

public class PSListener implements Listener {
    private final PVPStats plugin;

    private final Debug DEBUG = new Debug(3);

    private final Map<String, String> lastKill = new HashMap<>();
    private final Map<String, BukkitTask> killTask = new HashMap<>();

    public PSListener(final PVPStats instance) {
        this.plugin = instance;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        if (event.getPlayer().isOp() && plugin.getUpdater() != null) {
            plugin.getUpdater().message(event.getPlayer());

        }
        PSMySQL.initiatePlayer(event.getPlayer(), plugin.dbTable);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        if (plugin.getConfig().getBoolean("resetkillstreakonquit")) {
            PVPData.setStreak(event.getPlayer().getName(), 0);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        if (event.getEntity() == null || plugin.ignoresWorld(event.getEntity().getWorld().getName())) {
            return;
        }

        DEBUG.i("Player killed!", event.getEntity());

        if (event.getEntity().getKiller() == null) {
            DEBUG.i("Killer is null", event.getEntity());
            if (plugin.getConfig().getBoolean("countregulardeaths")) {
                DEBUG.i("Kill will be counted", event.getEntity());
                PSMySQL.AkilledB(null, event.getEntity());
            }
            return;
        }

        final Player attacker = event.getEntity().getKiller();
        final Player player = event.getEntity();

        if (plugin.getConfig().getBoolean("checkabuse")) {
            DEBUG.i("- checking abuse", event.getEntity());
            if (lastKill.containsKey(attacker.getName()) && lastKill.get(attacker.getName()).equals(player.getName())) {
                DEBUG.i("> OUT!", event.getEntity());
                return; // no logging!
            }

            lastKill.put(attacker.getName(), player.getName());
            int abusesec = plugin.getConfig().getInt("abuseseconds");
            if (abusesec > 0) {
                class RemoveLater implements Runnable {

                    @Override
                    public void run() {
                        lastKill.remove(attacker.getName());
                        killTask.remove(attacker.getName());
                    }

                }
                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, new RemoveLater(), abusesec * 20L);

                if (killTask.containsKey(attacker.getName())) {
                    killTask.get(attacker.getName()).cancel();
                }

                killTask.put(attacker.getName(), task);
            }
        }
        // here we go, PVP!
        DEBUG.i("Counting kill by " + attacker.getName(), event.getEntity());
        PSMySQL.AkilledB(attacker, player);
    }
}