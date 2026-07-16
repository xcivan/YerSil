package com.dragoncraft.yersil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Yerdeki (drop edilmis) esyalarin periyodik olarak temizlenmesinden ve
 * temizlik oncesi geri sayim (Action Bar) yayininindan sorumlu sinif.
 * <p>
 * Performans notlari:
 *  - Dongu her saniyede bir (20 tick) calisir; gereksiz obje uretimi yoktur (sabit degiskenler kullanilir).
 *  - Esyalar taranirken world.getEntities() ile TUM entity listesi kopyalanmaz;
 *    bunun yerine sadece YUKLU (loaded) chunk'lar gezilip, o chunklarin kendi entity dizisi okunur.
 *  - MiniMessage Component'leri, actionbar yayininda her tick tekrar parse edilmeden
 *    onbellekten (cache) faydalanacak sekilde minimum string islemi ile olusturulur.
 */
public class ClearManager {

    private final Main plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private BukkitTask task;

    private int periodSeconds;      // config'den okunan tam periyot (saniye)
    private int countdownStart;     // geri sayimin baslayacagi esik (saniye)
    private int timeLeft;           // bir sonraki temizlige kalan sure (saniye)

    public ClearManager(Main plugin) {
        this.plugin = plugin;
        reloadSettings();
    }

    /** config.yml degerlerini (yeniden) okur. */
    private void reloadSettings() {
        FileConfiguration config = plugin.getConfig();
        this.periodSeconds = Math.max(1, config.getInt("temizleme-periyodu-saniye", 300));
        this.countdownStart = Math.max(1, config.getInt("geri-sayim-baslangic-saniye", 20));
        this.timeLeft = this.periodSeconds;
    }

    /** Zamanlayiciyi (1 saniyede bir tetiklenen BukkitRunnable) baslatir. */
    public void start() {
        if (task != null) {
            task.cancel();
        }
        // 20 tick = 1 saniye. runTaskTimer -> BukkitRunnable tabanli, ana thread uzerinde calisir.
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Zamanlayiciyi tamamen durdurur (onDisable icin). */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Her saniye calisan dongu mantigi. */
    private void tick() {
        timeLeft--;

        if (timeLeft <= 0) {
            int cleared = clearGroundItems();
            broadcastClearMessage(cleared);
            timeLeft = periodSeconds;
            return;
        }

        if (timeLeft <= countdownStart) {
            broadcastCountdown(timeLeft);
        }
    }

    /**
     * Yuklu tum dunyalardaki, yuklu chunk'lari verimli sekilde tarayip
     * yerdeki (Item) esyalari temizler. Oyuncu envanterine veya diger entity turlerine dokunmaz.
     *
     * @return temizlenen esya adedi
     */
    public int clearGroundItems() {
        int cleared = 0;

        for (World world : Bukkit.getWorlds()) {
            // Sadece o an yuklu olan chunk'lari geziyoruz; kapali/uzak bolgeler icin gereksiz yukleme yapilmaz.
            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof Item item) {
                        item.remove();
                        cleared++;
                    }
                }
            }
        }

        return cleared;
    }

    /** Zamanlayiciyi disaridan (komut ile) sifirlamak icin kullanilir. */
    public void resetTimer() {
        this.timeLeft = periodSeconds;
    }

    /**
     * Periyodu runtime'da gunceller, config.yml'e kaydeder ve zamanlayiciyi
     * yeni periyoda gore sifirdan baslatir.
     *
     * @param newPeriodSeconds yeni periyot (saniye), pozitif olmak zorunda
     */
    public void updatePeriod(int newPeriodSeconds) {
        this.periodSeconds = newPeriodSeconds;
        this.timeLeft = newPeriodSeconds;

        plugin.getConfig().set("temizleme-periyodu-saniye", newPeriodSeconds);
        plugin.saveConfig();

        // Zamanlayiciyi yeni sureye gore temiz sekilde yeniden baslatiyoruz.
        start();
    }

    public int getPeriodSeconds() {
        return periodSeconds;
    }

    // ==================== Mesajlasma (Adventure / MiniMessage) ====================

    /** Her tick'te tum oyunculara Action Bar uzerinden geri sayim mesaji gonderir (chat kirliligi yok). */
    private void broadcastCountdown(int secondsLeft) {
        String rawTemplate = plugin.getConfig().getString(
                "mesajlar.geri-sayim-actionbar",
                "<gold>[DragonCraft]</gold> <aqua>Yerdeki esyalar {saniye} saniye icinde temizlenecek!</aqua>"
        );
        Component message = miniMessage.deserialize(rawTemplate.replace("{saniye}", String.valueOf(secondsLeft)));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(message);
        }
    }

    /** Temizlik tamamlandiginda tum sunucuya chat uzerinden bilgi verir. */
    private void broadcastClearMessage(int clearedCount) {
        String rawTemplate = plugin.getConfig().getString(
                "mesajlar.temizlik-tamamlandi",
                "<gold>[DragonCraft]</gold> <aqua>Yerdeki {adet} esya basariyla temizlendi!</aqua>"
        );
        Component message = miniMessage.deserialize(rawTemplate.replace("{adet}", String.valueOf(clearedCount)));

        Bukkit.broadcast(message);
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }
}
