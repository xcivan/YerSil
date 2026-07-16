package com.dragoncraft.yersil;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * YerSil - DragonCraft sunucusu icin performans odakli ClearLag alternatifi.
 * <p>
 * Plugin'in ana giris noktasi. Config yuklemesi, ClearManager (zamanlayici / temizleme mantigi)
 * ve CommandHandler (komut yonetimi) baglantilarini burada kurulur.
 */
public final class Main extends JavaPlugin {

    private ClearManager clearManager;

    @Override
    public void onEnable() {
        // config.yml dosyasi yoksa jar icindeki varsayilanini diske kopyalar.
        saveDefaultConfig();

        // Zamanlayici / temizleme mantigini yoneten sinifi baslatiyoruz.
        this.clearManager = new ClearManager(this);

        // Komutlari tek bir CommandHandler uzerinden yonetiyoruz.
        CommandHandler commandHandler = new CommandHandler(this, clearManager);
        this.getCommand("yersil").setExecutor(commandHandler);
        this.getCommand("yersilsure").setExecutor(commandHandler);

        // Zamanlayiciyi (BukkitRunnable tabanli) calistiriyoruz.
        clearManager.start();

        getLogger().info("YerSil basariyla etkinlestirildi! (DragonCraft ClearLag Alternatifi)");
    }

    @Override
    public void onDisable() {
        // Sunucu kapanirken/plugin devre disi birakilirken calisan gorevi guvenli sekilde durduruyoruz.
        if (clearManager != null) {
            clearManager.stop();
        }
        getLogger().info("YerSil devre disi birakildi.");
    }

    public ClearManager getClearManager() {
        return clearManager;
    }
}
