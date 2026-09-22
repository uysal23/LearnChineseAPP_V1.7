# 柳溪镇 · Çince — APK oluşturma (sürüm 1.5)

Uygulama `app/src/main/assets/index.html` içindedir ve **tamamen çevrimdışı** çalışır (internet izni yok).

## APK
**GitHub:** bu klasörün *içindekileri* deponun kök dizinine yükle → **Actions → Build APK → Run workflow** → `liuxi-cince-apk` içindeki `app-debug.apk`.
**Android Studio:** klasörü aç → Build → Build APK(s).

## Ses
- **Okuma (TTS):** Ayarlar → Genel yönetim → Metin okuma → Google TTS → Çince (zh-CN) ses verisini indir.
- **Konuşma alıştırması (ses tanıma):** cihazın kendi konuşma tanımasını kullanır. Çevrimdışı çalışması için Ayarlar → Google → Ses → Çevrimdışı konuşma tanıma'dan Çince'yi indirmen gerekebilir. Bu **puan vermez**, yalnızca "ne duyuldu"yu gösterir; sınav/ilerleme bundan etkilenmez.

## Sınav
İki aşamalı: 1) kelime (%90), 2) gramer/boşluk doldurma (%85). Sonraki sahne ikisi de geçilince açılır.

Ayrıntı: `docs/KONUSMA.md` · testler: `tests/asr.test.js`.
