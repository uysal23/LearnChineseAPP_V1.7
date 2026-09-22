# Konuşma alıştırması: cihaz üzerinde ses tanıma (sürüm 1.5)

## Karar
Önceki sürümlerde (1.3–1.4) bir "telaffuz değerlendirmesi" (0–100 puan, alt ölçümler, 3 aşamalı
sesli sınav) vardı ve puan çevrimiçi bir Azure sunucusundan ya da takılabilir bir yerel motordan
geliyordu. Araştırmaya rağmen (bkz. eski `docs/YEREL_MOTOR.md`, bu sürümde kaldırıldı) telefonda
çalışan, doğrulanmış bir Mandarin **telaffuz puanlama** motoru bulunamadı.

Bu sürümde talimatla **karar 3** uygulandı:
- Sınav geçme şartlarından ses eşleştirme/telaffuz puanı tamamen **kaldırıldı**. Sınav yeniden
  **2 aşamalıdır**: Aşama 1 kelime (%90), Aşama 2 gramer/boşluk doldurma (%85). Önceki sürümdeki
  3. aşama, `ST.pr`, `PronunciationTask/VoiceAttempt/PronunciationAssessment` veri modeli, Azure
  sunucusu (`server/server.js`) ve ilgili tüm ekranlar **silindi**.
- Ses tanıma yalnızca **alıştırmalar için** var: her sahnede "🗣 Konuşma alıştırması" (10 kelime +
  10 cümle, karışık). Cihazın kendi (Android `SpeechRecognizer`, çevrimdışı tercih edilir) konuşma
  tanımasını kullanır, söylediğini **"duyulan metin"** olarak gösterir ve hedef metinle karakter
  karakter eşleştirir (✔/✖). **Hiçbir puan, yüzde ya da 0–100 skoru üretilmez veya gösterilmez.**
  Bu alıştırma ilerlemeye/sınava girmez, tamamen isteğe bağlı kendi kendine çalışmadır.

## Neden ses eşleştirmesi bir "telaffuz puanı" değil
Ses tanımanın döndürdüğü metin, hedef metne ne kadar yakınsa o kadar "doğru söylenmiş" demek
**değildir** — tanıyıcı hata yapmış olabilir, tonu hiç değerlendirmez, gürültüde yanlış duyabilir.
Bu yüzden arayüzde bunu her zaman "puanı değildir" notuyla birlikte, yalnızca bilgilendirici bir
"ne duyuldu" göstergesi olarak sunuyoruz; ✔/✖ işaretleri de bir "doğruluk yüzdesi" değil, yalnızca
o karakterin duyulan metinde bulunup bulunmadığını gösterir.

## Teknik
- **Motor:** `android.speech.SpeechRecognizer` + `RecognizerIntent`, dil `zh-CN`. Önce
  `EXTRA_PREFER_OFFLINE=true` ile **cihaz üzerinde (çevrimdışı)** dener. Telefonda Çince
  çevrimdışı paket kurulu değilse (hata `ERROR_LANGUAGE_NOT_SUPPORTED`/`UNAVAILABLE`),
  kullanıcıya hata göstermeden **kendisi otomatik olarak** aynı isteği çevrimiçi tanımayla
  bir kez daha dener (`MainActivity.AsrBridge.beginListening`). Yalnızca bu ikinci deneme de
  başarısız olursa (ya da hata başka bir nedenden kaynaklanıyorsa) kullanıcıya Türkçe hata
  gösterilir. **1.5 sürümünde bu geri düşüş yoktu ve çevrimdışı paketi olmayan telefonlarda
  ses tanıma tamamen çalışmıyordu — 1.6'da düzeltildi.**
  Köprü: `AndroidASR` (`isAvailable/hasPermission/requestPermission/start/stop/cancel`) →
  JS `window.__asr(evt, payload)` (`partial`/`final`/`error`).
- **İzinler:** `RECORD_AUDIO` + `INTERNET`. İnternet izni yalnızca yukarıdaki çevrimiçi geri
  düşüş içindir (Android'in kendi konuşma tanıma servisi bu durumda sesi kendi çevrimiçi
  altyapısına gönderir — Google uygulamasındaki sesli arama/yazma ile aynı mekanizma).
  Uygulama kendisi hâlâ hiçbir sunucuya bağlanmaz, ses dosyası oluşturmaz ya da saklamaz.
- **Yayın (claude.ai) sürümü / tarayıcı:** `AndroidASR` yok, bu yüzden konuşma alıştırması
  ekranında net bir "yalnızca APK'da, cihaz üzerinde çalışır" açıklaması gösterilir; uygulamanın
  geri kalanı normal çalışır.
- **Eşleştirme:** basit LCS (en uzun ortak alt dizi) ile hedef metindeki her karakterin duyulan
  metinde bulunup bulunmadığı işaretlenir (`matchChars`). Bu bir dizgi karşılaştırmasıdır, ses
  analizi değildir ve öyle sunulmaz.
- **Gizlilik:** Uygulama ses kaydını **kendisi hiçbir zaman diske yazmaz ya da bir sunucuya
  göndermez** — sistemin konuşma tanıma servisi ses akışını işler, uygulamaya yalnızca metin
  döner. Çevrimdışı paket kuruluysa bu işlem cihazdan çıkmaz; kurulu değilse (yukarıya bakın)
  ses, telefonun kendi konuşma tanıma servisi tarafından çevrimiçi işlenir. Mikrofon yalnızca
  kullanıcı "Söyle" düğmesine basınca açılır; arka plana geçince ya da ekran değişince
  `cancel()` ile kapanır. İlk kullanımda bu ayrımı açıklayan Türkçe bir izin metni gösterilir.
- **Hata/izin durumları:** ASR yok / cihaz desteklemiyor / izin reddedildi / Çince paketi eksik /
  konuşma algılanmadı — hepsi ayrı, anlaşılır Türkçe mesajlarla gösterilir; hiçbiri diğer
  bölümleri (kelime/gramer alıştırmaları, sınav, kart, senaryo oynatma) etkilemez.
- **İzinler/manifest:** yalnızca `RECORD_AUDIO`. **İnternet izni kaldırıldı** — uygulama artık uçtan
  uca çevrimdışıdır (TTS de zaten cihazın kurulu Çince sesini kullanıyordu).

## Test
`tests/asr.test.js` — Chromium (Playwright), sahte `AndroidASR` ile: 2 aşamalı sınavın 3. aşama
şartı olmadan tamamlanması, ASR yokken/izin reddinde diğer bölümlerin çalışması, "duyulan metin"
ekranında puan/yüzde **gösterilmediğinin** doğrulanması, hata kodlarının Türkçe açıklanması,
mikrofonun yalnızca kullanıcı eylemiyle açılıp arka planda kapanması, 115 sahnenin hepsinde
10+10 görev üretimi. **20/20 geçti.** Çevrimdışı→çevrimiçi otomatik geri düşüş mantığı Java
tarafında yaşadığı (`MainActivity.AsrBridge`) için bu testlerle **kapsanmıyor**; gerçek Android
cihazda, hem çevrimdışı paketi olan hem olmayan bir telefonda **denenmedi**. 1.5 sürümünde
gerçek bir kullanıcı, çevrimdışı paketi olmayan telefonunda tam olarak bu hatayı almış ve
bildirmişti; 1.6'daki düzeltme bu geri bildirime dayanıyor ama aynı telefonda yeniden
doğrulanmadı.

## Sürüm geçmişi
- **1.5:** İlk sürüm. `EXTRA_PREFER_OFFLINE=true` zorunluydu, `INTERNET` izni yoktu. Çevrimdışı
  Çince paketi olmayan telefonlarda ses tanıma tamamen çalışmıyordu (bildirilen hata).
- **1.6:** Çevrimdışı önceliklendirilip başarısız olursa sessizce çevrimiçiye düşülüyor;
  `INTERNET` izni eklendi. Görsel: karakterlere kalın "çizgi film" anahat filtresi eklendi;
  konuşmayan ama sahneyle ilgili karakterler (ör. Pip) artık ilk anıldıkları satırdan itibaren
  sahnede kalıyor; sahne kapasitesi 3'ten 4'e çıkarıldı; kimin sahneden çıkarılacağı artık
  "en son ne zaman konuştu" yerine "sırası yakında mı geliyor" mantığıyla seçiliyor.
