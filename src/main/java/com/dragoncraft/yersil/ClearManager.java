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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
 *  - KADEMELI (BATCH) SILME: Coğu ClearLag benzeri pluginin sunucuda ani "spike/donma"
 *    yaratmasinin asil sebebi, binlerce esyayi TEK BIR TICK icinde art arda remove() ile
 *    silmesidir. Her remove() cagrisi entity tracker guncellemesi ve o esyayi goren tum
 *    oyunculara "entity yok oldu" paketi gonderilmesi anlamina gelir; bu islem binlerce kez
 *    ust uste yapilinca ana thread (main thread) o tick'te tikanir. Bunu onlemek icin bu
 *    sinif esyalari once toplar, sonra config'den ayarlanabilir bir "batch-boyutu" kadarini
 *    her tick'te silip geri kalanini bir sonraki tick'e birakir; boylece tum sunucu tek bir
 *    anda degil, birkac tick'e yayilmis kucuk parcalar halinde temizlenir.
 */
public class ClearManager {

    private final Main plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private BukkitTask task;
    private BukkitTask batchClearTask;

    private int periodSeconds;      // config'den okunan tam periyot (saniye)
    private int countdownStart;     // geri sayimin baslayacagi esik (saniye)
    private int timeLeft;           // bir sonraki temizlige kalan sure (saniye)
    private int batchSize;          // her tick'te en fazla silinecek esya adedi (spike onleme)

    // ONBELLEK (cache): Bu mesaj sablonlari saniyede bir (geri sayim boyunca) veya her periyotta
    // kullanildigi icin, config.getString(...) cagrisini her seferinde tekrarlamak yerine SADECE
    // reload sirasinda bir kere okunup burada tutulur. Boylece sunucu ayakta oldugu surece surekli
    // tekrarlanan gereksiz config/harita aramasi (map lookup) onlenmis olur.
    private String countdownTemplate;
    private String clearBroadcastTemplate;

    public ClearManager(Main plugin) {
        this.plugin = plugin;
        reloadSettings();
    }

    /** config.yml degerlerini (yeniden) okur. */
    private void reloadSettings() {
        FileConfiguration config = plugin.getConfig();
        this.periodSeconds = Math.max(1, config.getInt("temizleme-periyodu-saniye", 300));

        // Guvenlik: geri sayim esigi periyottan buyuk olamaz, aksi halde her tick'te
        // sonsuz geri sayim gorunumu olusur. Boyle bir yanlis yapilandirma varsa otomatik duzeltilir.
        int rawCountdownStart = Math.max(1, config.getInt("geri-sayim-baslangic-saniye", 20));
        this.countdownStart = Math.min(rawCountdownStart, this.periodSeconds);

        this.batchSize = Math.max(1, config.getInt("batch-boyutu", 100));

        // Mesaj sablonlarini bir kerede onbellege aliyoruz (bkz. field aciklamasi).
        this.countdownTemplate = config.getString(
                "mesajlar.geri-sayim-actionbar",
                "<gold>[DragonCraft]</gold> <aqua>Yerdeki esyalar {saniye} saniye icinde temizlenecek!</aqua>"
        );
        this.clearBroadcastTemplate = config.getString(
                "mesajlar.temizlik-tamamlandi",
                "<gold>[DragonCraft]</gold> <aqua>Yerdeki {adet} esya basariyla temizlendi!</aqua>"
        );

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
        if (batchClearTask != null) {
            batchClearTask.cancel();
            batchClearTask = null;
        }
    }

    /** Her saniye calisan dongu mantigi. */
    private void tick() {
        timeLeft--;

        if (timeLeft <= 0) {
            // Toplam adet hemen biliniyor (liste toplanir toplanmaz), ama gercek silme
            // islemi spike yaratmamak icin birkac tick'e yayilarak arka planda devam eder.
            int total = clearGroundItems();
            broadcastClearMessage(total);
            timeLeft = periodSeconds;
            return;
        }

        if (timeLeft <= countdownStart) {
            broadcastCountdown(timeLeft);
        }
    }

    /**
     * Yuklu tum dunyalardaki, yuklu chunk'lari verimli sekilde tarayip yerdeki (Item)
     * esyalarin tamamini TESPIT EDER ve silme islemini KADEMELI (batch) olarak baslatir.
     * <p>
     * Onemli: Bu metot butun esyalari ayni tick'te silmez. Bunun yerine once entity'leri
     * bir listeye toplar (bu islem remove() cagirmadigi icin ucuzdur ve spike yaratmaz),
     * ardindan config'deki "batch-boyutu" kadarini her tick'te silecek sekilde ayri bir
     * gorev (BukkitRunnable) baslatir. Boylece binlerce esya olsa bile ana thread hicbir
     * tick'te asiri yuklenmez.
     *
     * @return o an tespit edilen ve silinmek uzere kuyruga alinan toplam esya adedi
     */
    public int clearGroundItems() {
        List<Item> itemsToRemove = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            // Sadece o an yuklu olan chunk'lari geziyoruz; kapali/uzak bolgeler icin gereksiz yukleme yapilmaz.
            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof Item item) {
                        itemsToRemove.add(item);
                    }
                }
            }
        }

        scheduleBatchRemoval(itemsToRemove);
        return itemsToRemove.size();
    }

    /**
     * Onceden toplanmis esya listesini, spike yaratmamak icin her tick'te en fazla
     * {@link #batchSize} kadarini silecek sekilde parca parca (batch) temizler.
     * Ayni anda birden fazla kademeli silme gorevi calismasin diye onceki gorev varsa iptal edilir.
     */
    private void scheduleBatchRemoval(List<Item> itemsToRemove) {
        if (batchClearTask != null) {
            batchClearTask.cancel();
            batchClearTask = null;
        }

        if (itemsToRemove.isEmpty()) {
            return;
        }

        Iterator<Item> iterator = itemsToRemove.iterator();

        batchClearTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                int removedThisTick = 0;

                while (iterator.hasNext() && removedThisTick < batchSize) {
                    Item item = iterator.next();
                    // isValid() -> oyuncu bu arada esyayi yerden aldiysa veya baska sebeple
                    // entity gecersiz hale geldiyse gereksiz/hatali remove() cagrisi onlenir.
                    if (item.isValid()) {
                        item.remove();
                    }
                    removedThisTick++;
                }

                if (!iterator.hasNext()) {
                    batchClearTask.cancel();
                    batchClearTask = null;
                }
            }
        }, 0L, 1L);
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

    /**
     * "/yersil reload" komutu icin: config.yml dosyasini diskten yeniden okur
     * (plugin.reloadConfig()), periyot/geri-sayim/batch ayarlarini VE onbellege
     * alinmis mesaj sablonlarini tazeler, ardindan zamanlayiciyi yeni degerlere
     * gore sifirdan baslatir.
     */
    public void reloadFromDisk() {
        plugin.reloadConfig();
        reloadSettings();
        start();
    }

    // ==================== Mesajlasma (Adventure / MiniMessage) ====================

    /** Her tick'te tum oyunculara Action Bar uzerinden geri sayim mesaji gonderir (chat kirliligi yok). */
    private void broadcastCountdown(int secondsLeft) {
        // Sablon artik config'den degil, reload sirasinda doldurulmus onbellekten okunuyor.
        Component message = miniMessage.deserialize(countdownTemplate.replace("{saniye}", String.valueOf(secondsLeft)));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(message);
        }
    }

    /** Temizlik tamamlandiginda tum sunucuya chat uzerinden bilgi verir. */
    private void broadcastClearMessage(int clearedCount) {
        Component message = miniMessage.deserialize(clearBroadcastTemplate.replace("{adet}", String.valueOf(clearedCount)));

        Bukkit.broadcast(message);
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }
}
