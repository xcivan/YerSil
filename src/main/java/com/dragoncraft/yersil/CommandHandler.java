package com.dragoncraft.yersil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * "/yersil" ve "/yersilsure" komutlarini yoneten sinif.
 * <p>
 * Tek yetki dugumu (permission node) kullanilir: yersil.komut
 */
public class CommandHandler implements CommandExecutor {

    private final Main plugin;
    private final ClearManager clearManager;
    private final MiniMessage miniMessage;

    private static final String PERMISSION = "yersil.komut";

    public CommandHandler(Main plugin, ClearManager clearManager) {
        this.plugin = plugin;
        this.clearManager = clearManager;
        this.miniMessage = clearManager.getMiniMessage();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Tek permission kontrolu - tum komutlar icin gecerli.
        if (!sender.hasPermission(PERMISSION)) {
            sendMessage(sender, getMsg("mesajlar.yetki-yok", "<gold>[DragonCraft]</gold> <aqua>Yetkiniz yok!</aqua>"));
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "yersil" -> {
                // "/yersil reload" -> config.yml'i yeniden yukler. Argumansiz "/yersil" -> anlik temizlik.
                if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    handleReloadCommand(sender);
                } else {
                    handleClearCommand(sender);
                }
            }
            case "yersilsure" -> handleSetPeriodCommand(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** "/yersil reload" -> config.yml dosyasini diskten yeniden yukler ve zamanlayiciyi tazeler. */
    private void handleReloadCommand(CommandSender sender) {
        clearManager.reloadFromDisk();

        String template = getMsg(
                "mesajlar.config-yenilendi",
                "<gold>[DragonCraft]</gold> <aqua>Config basariyla yeniden yuklendi!</aqua>"
        );
        sendMessage(sender, template);
    }

    /** "/yersil" -> Anlik temizlik, zamanlayici sifirlama ve bilgilendirme. */
    private void handleClearCommand(CommandSender sender) {
        int cleared = clearManager.clearGroundItems();
        clearManager.resetTimer();

        // Komutu calistiran kisiye (oyunculuysa) Action Bar uzerinden bilgi ver.
        if (sender instanceof Player player) {
            Component actionBarMsg = miniMessage.deserialize(getMsg(
                    "mesajlar.manuel-temizlik-actionbar",
                    "<gold>[DragonCraft]</gold> <aqua>Esyalar temizlendi!</aqua>"
            ));
            player.sendActionBar(actionBarMsg);
        }

        // Sunucuya (chat) manuel temizlik bilgisi yayinla.
        String chatTemplate = getMsg(
                "mesajlar.manuel-temizlik-chat",
                "<gold>[DragonCraft]</gold> <aqua>{oyuncu} tarafindan {adet} esya temizlendi.</aqua>"
        );
        Component chatMsg = miniMessage.deserialize(
                chatTemplate
                        .replace("{oyuncu}", sender.getName())
                        .replace("{adet}", String.valueOf(cleared))
        );
        Bukkit.broadcast(chatMsg);
    }

    /** "/yersilsure <saniye>" -> Periyodu runtime'da gunceller. */
    private void handleSetPeriodCommand(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendMessage(sender, getMsg(
                    "mesajlar.hatali-kullanim",
                    "<gold>[DragonCraft]</gold> <aqua>Kullanim: /yersilsure <saniye></aqua>"
            ));
            return;
        }

        int newPeriod;
        try {
            newPeriod = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            sendMessage(sender, getMsg(
                    "mesajlar.hatali-sayi",
                    "<gold>[DragonCraft]</gold> <aqua>Gecerli bir sayi giriniz!</aqua>"
            ));
            return;
        }

        if (newPeriod <= 0) {
            sendMessage(sender, getMsg(
                    "mesajlar.hatali-sayi",
                    "<gold>[DragonCraft]</gold> <aqua>Gecerli bir sayi giriniz!</aqua>"
            ));
            return;
        }

        clearManager.updatePeriod(newPeriod);

        String template = getMsg(
                "mesajlar.sure-guncellendi",
                "<gold>[DragonCraft]</gold> <aqua>Periyot {saniye} saniye olarak guncellendi!</aqua>"
        );
        sendMessage(sender, template.replace("{saniye}", String.valueOf(newPeriod)));
    }

    /** config.yml uzerinden mesaj sablonu okur, bulunamazsa varsayilan degeri kullanir. */
    private String getMsg(String path, String def) {
        return plugin.getConfig().getString(path, def);
    }

    /** Gonderen oyuncu ise chat'e, konsol ise duz metin olarak mesaji iletir. */
    private void sendMessage(CommandSender sender, String rawMiniMessage) {
        sender.sendMessage(miniMessage.deserialize(rawMiniMessage));
    }
}
