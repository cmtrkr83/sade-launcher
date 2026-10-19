# Sade Launcher (Sade TV Launcher)

Android TV / Google TV için yazılmış **minimal, animasyonsuz, D-pad odaklı** bir launcher.
2 GB RAM'li (armeabi-v7a, Android 14 / API 34) Skyworth Google TV kutusu için optimize edildi.

---

## Özellikler (v1)

| Özellik | Açıklama |
|---|---|
| Saat + iki satır tarih | Sol tarafta saat; yanında iki satır: üstte `19 Ekim`, altta `Pazartesi` (cihaz diline göre). 20 saniyede bir güncellenir |
| Uygulama ızgarası | **Dinamik**: uygulama sayısına göre sütun/satır ve kart boyutu değişir (aşağıdaki tablo), Leanback + normal launcher uygulamaları, tek pakette tek giriş (leanback tercih edilir), Türkçe alfabetik sıralama |
| Gradient kart zemini | Her kartın zemini, uygulama ikonunun **baskın renginden** türetilir; **45°** açılı degrade olarak koyu zeminle karıştırılır (parlaklık sınırlandığı için arayüz koyu kalır) |
| Uzun basma menüsü | Karta uzun basınca menü: **Favorilere ekle/çıkar**, **Taşı / sırala**, **Uygulamayı aç**, **Uygulama bilgisi**, **Kaldır** (sistem uygulamalarında gizli). Koyu, yuvarlak köşeli panel |
| Kart taşıma / sıralama | **Taşı / sırala** → D-pad oklarıyla kartı adım adım kaydır, **OK** kaydet, **GERİ** iptal. Sıra kalıcıdır; yeni kurulan uygulamalar listenin sonuna eklenir. Menüde **Sıralamayı sıfırla (alfabetik)** seçeneği de bulunur |
| Favoriler | Favoriler üstte ayrı şeritte gösterilir; ızgarada ★ rozetiyle işaretlenir. Favori kartları, ızgara kartlarıyla **birebir aynı boyutta** ölçeklenir; şerit yüksekliği de buna göre ayarlanır. Ayarlar/kurulum değişikliklerinde liste otomatik tazelenir |
| Başlıkta ikonlu Ayarlar düğmesi | Sağ üstte dişli ikonlu düğme; odaklanınca ikon ve metin aksan rengine döner. **Uzun basma** varsayılan launcher rolünü ister |
| Bağlantı durumu | Ayarlar düğmesinin solunda: aktif ağ **ethernet ise ethernet**, değilse **Wi-Fi** simgesi; internet doğrulanmışsa **yeşil**, değilse **kırmızı** (canlı güncellenir) |
| Sistem izleyici | Bağlantı ikonunun solunda üç halka: **RAM %**, **CPU çalışma frekansı** (örn. `1,2G`) ve **/data doluluk %**. Ana ekran görünürken 2 saniyede bir tazelenir; başka uygulama açılınca sayaç durur, arka planda ölçüm yapmaz. Cihazda `/proc/stat` uygulamalara kapalı olduğu için CPU, `cpufreq/time_in_state` sayaç farkından **ortalama frekans** olarak ölçülür |
| Geri tuşu | Ana ekranda hiçbir şey yapmaz (kazara çıkış yok) |

### Dinamik ızgara

Kartlar, seçilen satır sayısı ekrana tam sığacak şekilde küçültülür (ikon, yazı ve
boşluklar da orantılı küçülür). Favoriler şeridi açıkken ızgara alanı azaldığı için
satır sayısı gerekirse bir azaltılır ve kalan uygulamalar kaydırmayla görünür.

| Uygulama sayısı | Sütun × satır | Not |
|---|---|---|
| ≤ 12 | 4 × 3 | Kartlar en büyük boyutta (200×124dp'ye kadar) |
| 13–20 | 5 × 4 | 20 karta kadar kaydırmasız |
| 21–30 | 6 × 5 | 30 karta kadar kaydırmasız |
| 31+ | 5 × 4 | Kaydırmalı (satırlar sabit, liste aşağı kayar) |


**Performans notları**

- Sıfır destek kütüphanesi: yalnızca `androidx.recyclerview`. Tema `android:Theme.Material.NoActionBar`.
- Pencere/aktivite geçiş animasyonları kapalı (`windowAnimationStyle=@null`) → anında açılır.
- Uygulama listesi, ikonlar ve **ikon renk analizi** arka plan iş parçacığında yüklenir; ikonlar 144 px'e küçültülür (bellek dostu).
- Ölçümler (hedef cihazda): **APK ~149 KB (release)**, **PSS ~39 MB**, **soğuk açılış ~0.5 sn**.

---

## Proje yapısı

```
SadeLauncher/
├─ settings.gradle, build.gradle, gradle.properties, local.properties
└─ app/
   ├─ build.gradle
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/com/sade/tvlauncher/
      │  ├─ MainActivity.java     # ekran, odak yönetimi, favoriler, uzun basma menüsü
      │  ├─ AppRepository.java    # kurulu uygulamaları toplar, ikonları küçültür, baskın rengi bulur
      │  ├─ AppGridAdapter.java   # ortak RecyclerView adaptörü (ızgara + favori şeridi)
      │  ├─ TileBackgrounds.java  # ikon renginden 45° degrade kart zemini
      │  └─ AppEntry.java         # tek uygulama kaydı (+ baskın renk)
      └─ res/
         ├─ layout/activity_main.xml, item_app.xml (boyut çalışma anında hesaplanır), item_favorite.xml
         ├─ drawable/bg_app.xml, bg_tile.xml, bg_pill.xml, bg_star.xml, bg_dialog.xml, bg_hint.xml,
         │           ic_settings.xml, ic_wifi.xml, ic_ethernet.xml, tv_banner (PNG)
         ├─ color/pill_text.xml, icon_tint.xml
         ├─ values{,-en}/strings.xml
         ├─ values/{colors,styles,themes}.xml
         └─ mipmap-*/ic_launcher.png
```

---

## Derleme

Bu makinede kullanılan araç zinciri (taşınabilir, kurulum gerektirmeyen):

| Araç | Yol |
|---|---|
| JDK 17.0.20 | `C:\Users\Turker\AppData\Local\Temp\opencode\toolchain\jdk17` |
| Android SDK (platform 34, build-tools 34.0.0) | `C:\Users\Turker\AppData\Local\Temp\opencode\toolchain\android-sdk` |
| Gradle 8.9 | `C:\Users\Turker\AppData\Local\Temp\opencode\toolchain\gradle-dist\gradle-8.9` |
| adb | `C:\Users\Turker\AppData\Local\Temp\opencode\platform-tools\adb.exe` |

PowerShell ile derleme:

```powershell
$root = "$env:LOCALAPPDATA\Temp\opencode\toolchain"
$env:JAVA_HOME = "$root\jdk17"
$env:ANDROID_HOME = "$root\android-sdk"
$env:ANDROID_SDK_ROOT = "$root\android-sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

& "$root\gradle-dist\gradle-8.9\bin\gradle.bat" -p "C:\Users\Turker\AndroidProjects\SadeLauncher" assembleRelease
```

Çıktı: `app\build\outputs\apk\release\app-release.apk`

> Android Studio ile açacaksanız `local.properties` içindeki `sdk.dir` yolu Android Studio'nun
> kendi SDK'sını göstermelidir.

Kurulum:

```powershell
& "$env:LOCALAPPDATA\Temp\opencode\platform-tools\adb.exe" install -r "app\build\outputs\apk\release\app-release.apk"
```

---

## Varsayılan launcher yapma (Google TV'de önemli)

Google TV ROM'u HOME tuşunu `com.google.android.apps.tv.launcherx`'e (LauncherX) kilitler.
Ayrıca Android 12+ ile birlikte sistem, **kullanıcı tarafından kurulan** uygulamaların
`android:priority` değerini sıfırlar; bu yüzden öncelikle öne geçmek mümkün değildir.

Çalışan yöntem: rolü vermek **ve** stok launcher + kurulum bekçisini devre dışı bırakmak.
Kurulumdan sonra stok launcher (LauncherX) ve FLauncher sahip kullanıcıdan tamamen kaldırıldı:

```powershell
$adb = "$env:LOCALAPPDATA\Temp\opencode\platform-tools\adb.exe"

# 1) HOME rolü
& $adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.sade.tvlauncher
& $adb shell cmd package set-home-activity com.sade.tvlauncher/com.sade.tvlauncher.MainActivity

# 2) Stok launcher ve setup bekçisi (setupwraith devre dışı kalır, kaldırılmaz)
& $adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
& $adb shell am force-stop com.google.android.apps.tv.launcherx

# 3) Stok launcher'ı sahip kullanıcıdan kaldır (sistem APK'sı diskte kalır, çalışmaz)
& $adb shell pm uninstall --user 0 com.google.android.apps.tv.launcherx

# 4) FLauncher'ı tamamen kaldır (kullanıcı uygulaması)
& $adb shell pm uninstall me.efesser.flauncher
```

Notlar:
- `setupwraith` devre dışı bırakılmazsa, stok launcher kapalıyken bir "kurtarma" döngüsüne girip
  HOME tuşunu kendisi kapar.
- `pm list packages` LauncherX'i listelemeye devam eder (sistem APK'sı silinemez), ancak
  `pm list packages --user 0` içinde görünmez; hiç çalışmaz ve ana ekranda yer almaz.
- Google TV ana ekranına geri dönmek için:

```powershell
& $adb shell pm install-existing --user 0 com.google.android.apps.tv.launcherx
& $adb shell pm enable --user 0 com.google.android.tungsten.setupwraith
& $adb shell cmd package set-home-activity com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity
```

- Google TV sistem güncellemesi sonrası paketler geri gelebilir; komutları tekrar çalıştırın.
- Doğrulama: `cmd role get-role-holders --user 0 android.app.role.HOME` → `com.sade.tvlauncher`
  ve `cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME`
  → `com.sade.tvlauncher/.MainActivity`.

---

## Yol haritası fikirleri (v2)

- Leanback uygulamalarında ikon yerine **banner** (16:9) gösterimi
- Son kullanılanlar şeridi (`UsageStatsManager`)
- Uygulama adına göre arama + ekran klavyesi
- Uygulama gizleme / özel sıralama
- Duvar kağıdı desteği ve hafif odak animasyonları (isteğe bağlı)
- Favorileri D-pad ile sıralama, klasörler
- İstenirse alt kısayol şeridini (Wi-Fi, Bluetooth, Görüntü, Ağ) geri ekleme —
  metinleri `strings.xml` içinde hazır duruyor.
