package com.chokapeak.loupgarou;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public final class LoupGarou extends JavaPlugin implements CommandExecutor, Listener {

    private boolean gameRunning = false;
    private boolean voteEnabled = false;
    private boolean maireVoteEnabled = false;

    private final Map<UUID, String> roles = new HashMap<>();
    private final Set<UUID> alivePlayers = new HashSet<>();
    
    // Votes du Tribunal et du Maire
    private final Map<UUID, UUID> votes = new HashMap<>();
    private final Map<UUID, UUID> maireVotes = new HashMap<>();
    private UUID maireUUID = null;

    private Scoreboard scoreboard;
    private Team hideTagsTeam;

    @Override
    public void onEnable() {
        Objects.requireNonNull(getCommand("lg")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        // Initialisation du Scoreboard pour masquer les pseudos
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        hideTagsTeam = scoreboard.getTeam("LG_HideTags");
        if (hideTagsTeam == null) {
            hideTagsTeam = scoreboard.registerNewTeam("LG_HideTags");
        }
        hideTagsTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);

        getLogger().info("Plugin LoupGarou v2.0 activé avec succès !");
    }

    @Override
    public void onDisable() {
        resetTags();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !player.hasPermission("loupgarou.admin")) {
            player.sendMessage(ChatColor.RED + "Tu n'as pas la permission.");
            return true;
        }

        if (args.length > 0) {
            String sub = args[0].toLowerCase();

            switch (sub) {
                case "start":
                    startGame();
                    return true;
                case "night":
                    startNight();
                    return true;
                case "day":
                    startDay();
                    return true;
                case "vote":
                    if (args.length > 1 && args[1].equalsIgnoreCase("on")) {
                        voteEnabled = true;
                        votes.clear();
                        Bukkit.broadcastMessage(ChatColor.GOLD + "⚖️ Les votes sont maintenant OUVERTS ! Faites /lg vote pour choisir.");
                    } else if (args.length > 1 && args[1].equalsIgnoreCase("off")) {
                        voteEnabled = false;
                        processAndAnnounceVotes();
                    } else {
                        if (sender instanceof Player voter) {
                            openVoteGui(voter, false);
                        }
                    }
                    return true;
                case "maire":
                    if (args.length > 1 && args[1].equalsIgnoreCase("on")) {
                        maireVoteEnabled = true;
                        maireVotes.clear();
                        Bukkit.broadcastMessage(ChatColor.GOLD + "👑 L'élection du Maire est OUVERTE ! Faites /lg maire pour voter.");
                    } else if (args.length > 1 && args[1].equalsIgnoreCase("off")) {
                        maireVoteEnabled = false;
                        processAndAnnounceMaire();
                    } else {
                        if (sender instanceof Player voter) {
                            openVoteGui(voter, true);
                        }
                    }
                    return true;
            }
        }

        sender.sendMessage(ChatColor.YELLOW + "Commandes : /lg start, /lg night, /lg day, /lg vote <on/off>, /lg maire <on/off>");
        return true;
    }

    private void startGame() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.size() < 4) {
            Bukkit.broadcastMessage(ChatColor.RED + "Il faut au moins 4 joueurs pour lancer une partie !");
            return;
        }

        gameRunning = true;
        roles.clear();
        alivePlayers.clear();
        votes.clear();
        maireVotes.clear();
        maireUUID = null;
        voteEnabled = false;
        maireVoteEnabled = false;

        Collections.shuffle(players);

        // Attribution : 2 Loups, 1 Petite Fille, 1 Chasseur, Le reste Villageois
        int total = players.size();
        int wolvesCount = 2;

        for (int i = 0; i < total; i++) {
            Player p = players.get(i);
            UUID id = p.getUniqueId();
            alivePlayers.add(id);
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            hideTagsTeam.addEntry(p.getName()); // Cache le pseudo

            String role;
            if (i < wolvesCount) {
                role = "Loup-Garou";
                ItemStack diamondSword = new ItemStack(Material.DIAMOND_SWORD);
                diamondSword.addUnsafeEnchantment(Enchantment.SHARPNESS, 1);
                p.getInventory().addItem(diamondSword);
            } else if (i == wolvesCount) {
                role = "Petite Fille";
            } else if (i == wolvesCount + 1) {
                role = "Chasseur";
            } else {
                role = "Villageois";
            }

            roles.put(id, role);
            p.sendTitle(ChatColor.DARK_RED + "TU ES", ChatColor.YELLOW + role, 10, 70, 20);
            p.sendMessage(ChatColor.GOLD + "----------------------------------------");
            p.sendMessage(ChatColor.YELLOW + "🐺 Ton rôle secret est : " + ChatColor.RED + role);
            p.sendMessage(ChatColor.GOLD + "----------------------------------------");
        }

        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "🌙 La partie de Loup-Garou commence ! Bon jeu à tous !");
    }

    private void startNight() {
        World world = Bukkit.getWorlds().get(0);
        world.setTime(13000);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.DARK_GRAY + "C'EST LA NUIT", "Allez dans une maison et fermez la porte.\nChangez de maison à chaque tour !", 10, 80, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 0.5f);

            // Effet de force pour les Loups-Garous
            if ("Loup-Garou".equals(roles.get(p.getUniqueId()))) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 0, false, false));
                p.sendMessage(ChatColor.DARK_RED + "⚡ La force des loups t'envahit pour cette nuit !");
            }
        }
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "🌙 La nuit tombe... Rentrez vous abriter !");
    }

    private void startDay() {
        World world = Bukkit.getWorlds().get(0);
        world.setTime(1000);

        // Retirer l'effet de force des loups
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removePotionEffect(PotionEffectType.STRENGTH);
            p.sendTitle(ChatColor.YELLOW + "C'EST LE JOUR", "Rasemblez-vous au centre !", 10, 60, 20);
        }

        Bukkit.broadcastMessage(ChatColor.AQUA + "☀️ Le soleil se lève ! Tout le monde doit se rassembler au milieu du village !");
    }

    private void openVoteGui(Player player, boolean isMaire) {
        if (isMaire && !maireVoteEnabled) {
            player.sendMessage(ChatColor.RED + "L'élection du maire n'est pas active.");
            return;
        }
        if (!isMaire && !voteEnabled) {
            player.sendMessage(ChatColor.RED + "Les votes du tribunal ne sont pas actifs.");
            return;
        }
        if (!alivePlayers.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Les spectateurs/morts ne peuvent pas voter.");
            return;
        }

        int size = ((alivePlayers.size() - 1) / 9 + 1) * 9;
        size = Math.max(9, Math.min(size, 54));
        String title = isMaire ? "👑 Voter pour le Maire" : "⚖️ Tribunal - Voter";

        Inventory gui = Bukkit.createInventory(null, size, title);

        for (UUID uuid : alivePlayers) {
            if (uuid.equals(player.getUniqueId()) && !isMaire) continue; // Ne pas voter pour soi au tribunal
            Player target = Bukkit.getPlayer(uuid);
            if (target != null) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                ItemMeta meta = head.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GREEN + target.getName());
                    head.setItemMeta(meta);
                }
                gui.addItem(head);
            }
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.contains("Tribunal - Voter") && !title.contains("Voter pour le Maire")) return;

        event.setCancelled();
        if (!(event.getWhoClicked() instanceof Player voter)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) return;

        String targetName = ChatColor.stripColor(meta.getDisplayName());
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) return;

        voter.closeInventory();

        if (title.contains("Maire")) {
            if (!maireVoteEnabled) {
                voter.sendMessage(ChatColor.RED + "Les votes pour le maire sont fermés.");
                return;
            }
            maireVotes.put(voter.getUniqueId(), target.getUniqueId());
            voter.sendMessage(ChatColor.GREEN + "✅ Vote pour le maire enregistré : " + target.getName());
        } else {
            if (!voteEnabled) {
                voter.sendMessage(ChatColor.RED + "Les votes du tribunal sont fermés.");
                return;
            }
            votes.put(voter.getUniqueId(), target.getUniqueId());
            voter.sendMessage(ChatColor.GREEN + "✅ Vote effectué contre " + target.getName() + ".");
        }
    }

    private void processAndAnnounceVotes() {
        if (votes.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Aucun vote enregistré.");
            return;
        }

        Map<UUID, Integer> counts = new HashMap<>();
        for (Map.Entry<UUID, UUID> entry : votes.entrySet()) {
            UUID voterId = entry.getKey();
            UUID targetId = entry.getValue();
            int weight = (voterId.equals(maireUUID)) ? 2 : 1; // Le maire compte double si 1 personne votée
            counts.merge(targetId, weight, Integer::sum);
        }

        int maxVotes = Collections.max(counts.values());
        List<UUID> topTargets = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == maxVotes) {
                topTargets.add(entry.getKey());
            }
        }

        StringBuilder sb = new StringBuilder(ChatColor.GOLD + "📊 Résultats du Tribunal :\n");
        for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                sb.append(" - ").append(p.getName()).append(" : ").append(entry.getValue()).append(" votes\n");
            }
        }
        Bukkit.broadcastMessage(sb.toString());

        if (topTargets.size() == 1) {
            Player targeted = Bukkit.getPlayer(topTargets.get(0));
            if (targeted != null) {
                Bukkit.broadcastMessage(ChatColor.RED + "⚖️ Joueur avec le plus de votes : " + targeted.getName() + " (" + maxVotes + " votes).");
            }
        } else {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "⚠️ Égalité détectée entre plusieurs joueurs ! Appliquez les règles d'égalité.");
        }
    }

    private void processAndAnnounceMaire() {
        if (maireVotes.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Aucun vote pour le maire n'a été enregistré.");
            return;
        }

        Map<UUID, Integer> counts = new HashMap<>();
        for (UUID targetId : maireVotes.values()) {
            counts.merge(targetId, 1, Integer::sum);
        }

        UUID electedUUID = Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
        Player elected = Bukkit.getPlayer(electedUUID);

        if (elected != null) {
            maireUUID = electedUUID;
            Bukkit.broadcastMessage(ChatColor.GOLD + "👑 Le nouveau Maire du village est " + elected.getName() + " ! Son vote comptera désormais double au tribunal.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        hideTagsTeam.addEntry(event.getPlayer().getName());
    }

    private void resetTags() {
        if (hideTagsTeam != null) {
            hideTagsTeam.unregister();
        }
    }
}
