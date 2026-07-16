# YerSil

Sunucunuz için geliştirilmiş, performans odaklı ve modern **ClearLag alternatifi** Minecraft eklentisi.
Paper/Spigot **1.21.4** API'si ve native **Kyori Adventure** (MiniMessage) mesajlaşma sistemi ile yazılmıştır.

![Java](https://img.shields.io/badge/Java-21-orange)
![Paper](https://img.shields.io/badge/Paper-1.21.4-blue)
![License](https://img.shields.io/badge/license-MIT-green)

## ✨ Özellikler

- ⏱️ **Otomatik Temizlik**: `config.yml` üzerinden ayarlanabilir periyotta (varsayılan 300 saniye) yerdeki eşyaları temizler.
- 📢 **Action Bar Geri Sayımı**: Temizliğe 20 saniye kala her saniye azalan geri sayım, sohbeti kirletmeden Action Bar üzerinden gösterilir.
- 🎨 **DragonCraft Teması**: Gold (`#FFA800`) ve Aqua (`#55FFFF`) renk paleti, tamamen MiniMessage formatında.
- 🚀 **Performans Odaklı**: Yerdeki eşyalar taranırken sadece yüklü chunk'lar gezilir, gereksiz nesne üretimi yapılmaz.
- 🔧 **Runtime Komutları**: Süreyi veya anlık temizliği sunucuyu yeniden başlatmadan yönetebilirsiniz.

## 📋 Komutlar

| Komut                     | Açıklama                                                                 | Yetki           |
|---------------------------|---------------------------------------------------------------------------|-----------------|
| `/yersil`                 | Yerdeki tüm eşyaları anında temizler ve zamanlayıcıyı sıfırlar.           | `yersil.komut`  |
| `/yersilsure <saniye>`    | Temizleme periyodunu günceller, `config.yml`'e kaydeder. (`/yersilsüre` alias'ı da kullanılabilir) | `yersil.komut`  |

## ⚙️ Kurulum

1. [Releases](../../releases) sayfasından ya da kendi derlediğiniz `YerSil-1.0.0.jar` dosyasını indirin.
2. Sunucunuzun `plugins/` klasörüne atın.
3. Sunucuyu başlatın/reload edin — `plugins/YerSil/config.yml` otomatik oluşturulacaktır.
4. Gerekirse `config.yml` üzerinden süre ve mesaj şablonlarını özelleştirin.

## 📁 Proje Yapısı

```
YerSil/
├── pom.xml
├── LICENSE
├── README.md
├── .gitignore
└── src/main/
    ├── resources/
    │   ├── plugin.yml
    │   └── config.yml
    └── java/com/dragoncraft/yersil/
        ├── Main.java
        ├── ClearManager.java
        └── CommandHandler.java
```

## 📄 Lisans

Bu proje [MIT Lisansı](LICENSE) ile lisanslanmıştır.
