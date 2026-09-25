# FamilyTree — Yol Haritası ve Mimari

> Bu doküman projenin tek referans kaynağıdır. Her faz bittiğinde güncellenir.
> **Son güncelleme:** 17 Ağustos 2026 · **Durum:** Uygulama tamamen yerel. Faz 5 (Firebase
> hesap + senkron) kaldırıldı — gerekçesi aşağıda. Kalan tek adım senin: Play Console'da
> `familytree_premium` ürününü tanımlamak.

---

## 1. Proje Nedir

`~/StudioProjects/FamilyGem` (Michele Salvador, GPL v3) uygulamasının Jetpack Compose +
Clean Architecture ile sıfırdan yeniden yazımı.

**Kaynak projenin ölçüsü:** 21.800 satır Java+Kotlin, 130 sınıf, 82 XML layout, 419 string × 34 dil.

**Kaynak projenin mimarisi:** Katmansız. `Global.gc` adlı statik bir `org.folg.gedcom.model.Gedcom`
nesnesini tüm ekranlar doğrudan mutate ediyor, her düzenlemede `filesDir/{treeId}.json`
dosyasının tamamı yeniden yazılıyor. Veritabanı, DI, repository, use case yok. MVVM
sadece 3 yerde. Navigasyon için Android'in geri yığınına paralel elle yazılmış `Memory`
breadcrumb yığını. Detay ekranları alan okuma/yazma için reflection kullanıyor. Test ~%1.

**Hedef:** Tüm özellikler korunarak modern, katmanlı, test edilebilir bir uygulama.

---

## 2. Onaylanan Kararlar

| Konu | Karar | Gerekçe / Sonuç |
|---|---|---|
| **Backend** | **Yok.** Uygulama sunucusuz; **premium (Play Billing) korunur** | Hesap yok, veri cihazdan çıkmıyor. Paylaşım `.ged` dosyası üzerinden |
| **Diyagram** | `gedcomgraph-3.12.jar` korunur, render Compose Canvas'ta yeniden yazılır | Yerleşim algoritması kanıtlanmış; sıfırdan yazmak en riskli kalem |
| **Room şeması** | Tam ilişkisel + `extensions` tablosu | Kayıpsız GEDCOM round-trip |
| **Senkron** | **Yok.** Room tek ve tek kopya | Cihazlar arası taşıma: ZIP yedeği veya GEDCOM dışa aktarımı |
| **Firebase** *(25 Eylül 2026)* | **Yalnızca Crashlytics ve Cloud Messaging** — proje `familytrees-ecf41` | Hesap, senkron ve analitik hâlâ yok; ağaç verisi cihazdan çıkmıyor. Crashlytics yalnızca release'te toplar. Push, sunucu olmadığı için herkesin katıldığı `all` konusuna konsoldan gönderilir; token hiçbir yere iletilmez. `google-services.json` API anahtarı taşıdığı için gitignored, CI `GOOGLE_SERVICES_JSON` secret'ından yazar; dosya yoksa build Firebase'siz geçer. `PRIVACY.md` buna göre güncellendi |
| **Medya** | **Sadece cihazda** | Maliyet + gizlilik. Diğer her şey gibi, medya da cihazdan çıkmıyor |
| **Diller** | İngilizce, Türkçe, Arapça | Arapça → RTL zorunlu |
| **Teslimat** | Fazlı; her faz sonunda derlenip çalışan APK | — |
| **GeoNames** | Düşüyor | familygem.app `/credentials` gerektiriyordu. Yerine: ağaçta kullanılmış yer adlarından yerel otomatik tamamlama |

### ⚠️ Karara bağlanması gereken açık konular

1. **Lisans — Faz 4'ten önce netleşmeli.**
   FamilyGem GPL v3. `gedcomgraph-3.12.jar`'ı eklediğimiz anda (Faz 4) FamilyTree kesin
   olarak **türev eser** olur → yayınlarsan kaynak kodu GPL v3 ile açmak zorundasın,
   Play Store'a kapalı kaynak yükleyemezsin.
   *Kapalı kaynak istiyorsan tek çıkış:* diyagram yerleşim algoritmasını sıfırdan yazmak
   (çok daha uzun) ve şema/algoritma benzerliklerini temizlemek.

2. **Premium doğrulaması sunucusuz tam güvenli değil.**
   Play Billing satın alması istemcide doğrulanıp uygulamanın kendi ayarlarına yazılıyor.
   Gerçek koruma sunucu gerektirir; bu uygulamanın kasten sunucusu yok. Tek seferlik bir
   satın alma için kilidin bir nezaket olduğu kabul edildi — `PlayPremiumRepository`
   başındaki not bunu açıkça söylüyor.

3. **minSdk 28.** Orijinal 21'di; Android 5–8 cihazlar kapsam dışı.

---

## 3. Fazlar

### ✅ Faz 1 — Temel *(tamamlandı, emülatörde doğrulandı)*

| Teslim | Durum |
|---|---|
| `build-logic` composite build, 7 convention plugin | ✅ |
| Version catalog (Kotlin 2.3.21 · KSP 2.3.11 · Room 2.8.4 · Hilt 2.60.1 · AGP 9.3.1) | ✅ |
| 12 modüllü yapı, bağımlılık kuralı derleme zamanında zorlanıyor | ✅ |
| `core:model` — 9 dosyada tüm domain modelleri | ✅ |
| `core:database` — 21 tablo, DAO'lar, şema JSON'u versiyon kontrolünde | ✅ *(Faz 5 kaldırılınca 20)* |
| `core:designsystem` — marka paleti, tipografi, shape, ortak composable'lar | ✅ |
| `core:common` / `core:datastore` / `core:domain` / `core:data` | ✅ |
| `feature:trees` — çalışan ağaç listesi (boş durum + kart listesi) | ✅ |
| `app` — Hilt, type-safe Navigation, edge-to-edge, TR/EN/AR | ✅ |

**Doğrulama kanıtı (emülatör, API 36):** boş durum render oldu → 2 dokunuş → Room'da tam
2 satır → 2 kart göründü. Room → repository → use case → ViewModel → Compose zinciri
uçtan uca çalışıyor. Arapça RTL doğrulandı (başlık sağda, FAB solda, metin aynalı).

---

### ✅ Faz 2 — GEDCOM + Ağaç Yönetimi *(tamamlandı, cihazda doğrulandı)*

**Yeni modül:** `core:gedcom`

| Teslim | Durum |
|---|---|
| GEDCOM tarih ayrıştırıcısı — 12 `Kind` × 9 `Format`, çift yıl, M.Ö., çok dilli ay adları | ✅ |
| Orijinal `DateTest`'in birebir portu — 63 vaka | ✅ |
| `GedcomImporter` — `.ged` → tek transaction'da Room | ✅ |
| `GedcomProjector` — Room → bellek-içi folg `Gedcom` | ✅ |
| `GedcomExporter` — Room → `.ged` | ✅ |
| Round-trip sadakat testi + sabit-nokta testi | ✅ |
| `feature:trees` — liste, yeniden adlandır, sil, dışa aktar, menü | ✅ |
| Yeni ağaç ekranı — boş ağaç / GEDCOM içe aktar (SAF) | ✅ |
| Hata bulma ve onarma — 6 denetim, 4'ü otomatik onarılabilir | ✅ |

**Test:** 15 test, 0 hata. Round-trip iki gerçek dosyayla doğrulanıyor: elle yazılmış
egzotik-tag fixture'ı ve FamilyGem'in kendi `media.ged` test dosyası.

**Cihaz doğrulaması (API 36):** `.ged` dosyası SAF ile seçildi → 3 kişi, 1 aile, 2 kuşak
içe aktarıldı; `_UID` kolonda, `_ROOT`/`_RANK`/`_APID` uzantı tablosunda korundu;
`HUSBAND`/`WIFE`/`CHILD` üyelikleri kuruldu; hata denetimi ağacı temiz raporladı.

#### Faz 2'de öğrenilen üç şey

1. **`gedcom5-java` parser'ı sandığımızdan akıllı.** `_UID` tipli alana (`Person.uid` +
   `uidTag`), `_MILT` ise bir `EventFact`'e mapleniyor; sadece `_APID`, `_RANK`, `_ROOT`
   gibi gerçekten yeri olmayanlar ham tag olarak kalıyor. Dolayısıyla "kayıpsız" olmak
   için *hem* uzantı tablosu *hem de* `uid`/`uidTag`/`rin`/`REFN` ve tüm `*Tag` yazım
   varyantları (`emailTag`, `wwwTag`, `typeTag`, `marriedNameTag`, `akaTag`, `fileTag`)
   kolon olarak saklanmak zorunda. Round-trip testi bunu tek tek ortaya çıkardı.

2. **`GedcomWriter`'ın iki null-güvenlik hatası var.** Kayıt seviyesindeki `value` null
   olunca (`0 @U1@ SUBM`) çöküyor; ve `writeString(getWwwTag(), …)` çağrılarında tag null
   ama değer doluysa yine çöküyor. Projektör bu iki durumu telafi ediyor.

3. **Sabit-nokta testi tek yönlü testin kaçırdığını yakaladı.** İçe→dışa aktarım
   testi geçerken, dışa→içe aktarım `_ROOT` tag'inin iki kez yazıldığını ortaya çıkardı
   (hem `HEAD` altına hem kök seviyeye). Tek yönlü kontrol makul görünen ama hatalı bir
   çıktıyı onaylayabiliyor.

---

### ✅ Faz 3 — Kayıt CRUD *(tamamlandı, cihazda doğrulandı)*

| Teslim | Durum |
|---|---|
| Tarih ayrıştırıcısı `core:model`'e taşındı — domain katmanı da kullanabiliyor | ✅ |
| GEDCOM tarih **üreticisi** (ayrıştırıcının tersi) + round-trip testi | ✅ |
| `PersonSummary` + 6 kritere göre sıralama + çok kelimeli AND araması | ✅ |
| `feature:person` — kişi listesi, arama, sıralama, boş durumlar | ✅ |
| Kişi editörü — ad/soyad/cinsiyet/doğum/ölüm | ✅ |
| Akraba bağlama algoritması (`AddRelativeUseCase`) | ✅ |
| Tarih editörü — 12 tür, M.Ö., çift yıl, canlı geçerlilik uyarısı | ✅ |
| `TreeShell` — adaptive navigasyon (telefonda alt çubuk, tablette rail) | ✅ |
| Profil ekranı — Bilgiler / Akrabalar / Medya sekmeleri | ✅ |
| `feature:family` — aile listesi + aile detayı | ✅ |
| **`FieldSpec` jenerik kayıt editörü — reflection yok** | ✅ |
| 44 GEDCOM olay tag'i için üç dilde etiket | ✅ |

**Cihaz doğrulaması (API 36):** Kişi listesi, profil (isimler + olaylar + notlar),
akrabalar sekmesi, aile listesi ve aile detayı doğru render oldu. `3 FEB 1715/16` çift
yılı **1716** olarak gösterildi. `_MILT` uzantı olayı "Military service" olarak
adlandırıldı. Yeni kişi eklendi; **akraba ekleme** Fatma'yı mevcut aileye çocuk olarak
bağladı (yeni aile açmadan) — algoritmanın doğru davranışı.

#### Faz 3'te yakalanan iki hata

1. **İsim gösterimi `NAME` değerini yok sayıyordu.** `PersonName.display()`
   yapılandırılmış parçaları (`GIVN`/`SURN`) tercih ediyordu; ama gerçek dosyalar çok sık
   `NAME Ayşe /Yılmaz/` yazıp sadece `SURN`'ü ayırıyor. Sonuç: kişi listede "Yılmaz"
   olarak, adı olmadan görünüyordu. GEDCOM'da `NAME` değeri birincil biçim — kural
   düzeltildi, `effectiveGiven()`/`effectiveSurname()` eksik parçaları değerden okuyor.
   *Yalnızca emülatörde bakınca görüldü; round-trip testi veriyi doğru saklıyordu, hata
   sadece gösterimdeydi.*

2. **GEDCOM grameri 3–4 haneli yıl istiyor.** Tarih üreticisinin round-trip testi
   `44 B.C.`'nin geçersiz olduğunu ortaya çıkardı; üretici artık `044` diye sıfır
   dolduruyor.

#### `FieldSpec` — reflection'ın yerine ne geçti

Orijinalin `DetailActivity`'si alanlara `getClass().getMethod("get" + name).invoke(...)`
ile erişiyordu (kodun kendi yorumu: *"TODO: this reflection is bad performance"*).
Yerine her alan için tipli bir getter/setter çifti:

```kotlin
FieldSpec(R.string.field_date, FieldKind.DATE, { it.date }, { r, v -> r.copy(date = v) })
```

Derleme zamanında kontrol ediliyor, R8 keep kuralı gerektirmiyor, çalışma zamanı maliyeti
yok. Şu an 8 kayıt tipi tanımlı (olay, isim, not, kaynak, depo, gönderen, medya, adres);
yenisini eklemek ekran yazmak değil, alan listesi yazmak demek.

---

### ✅ Faz 4 — Diyagram + Medya *(tamamlandı, cihazda doğrulandı)*

| Teslim | Durum |
|---|---|
| GPL v3 lisansı + türev eser bildirimi (`LICENSE`, `NOTICE.md`, `README.md`) | ✅ |
| `core:diagram` — JAR sarmalayıcı, 8 ayar, dp koordinatlı çıktı | ✅ |
| Ölçüm çözümü — `SubcomposeLayout`; orijinalin `delay(100)` busy-wait'i tamamen kalktı | ✅ |
| Canvas render — grup başına tek path, S-eğrileri, kesikli arka çizgiler | ✅ |
| Pan/zoom — `detectTransformGestures`, orijinalin `MoveLayout` modeli: 0.7 açılış ölçeği (sığan diyagram ekrana büyütülür), viewport/4 esnek taşma + geri yaylanma, momentumlu fırlatma, maks 5x | ✅ |
| Diyagram ayarları — 5 slider + 3 switch, doğrusal olmayan skala, aralarındaki kısıtlar | ✅ |
| PNG/PDF dışa aktarım — ayrı baskı paleti, OOM'da küçültüp yeniden deneme | ✅ |
| `core:media` — 5 aşamalı dosya çözümleme, SAF klasörleri, içe aktarma, kırpma | ✅ |
| `MediaRepository` — paylaşımlı kayıt kuralları, portre seçimi, bağlantı kısaltma | ✅ |
| Coil 3 + `MediaImage` — tipli yer tutucular (PDF/video/belge/eksik), PDF ilk sayfa önizleme | ✅ |
| `feature:media` — galeri, medya detayı, medya klasörleri, ekleme sayfası, Compose kırpıcı | ✅ |
| Portreler — kişi listesi, profil başlığı, profil medya sekmesi, diyagram kartları | ✅ |

**Cihaz doğrulaması (API 36):** Diyagram doğru çizildi (fulcrum vurgulu, 1740 evlilik bağı,
S-eğrili bağlantılar); ayarlar ekranı açıldı; PNG (695×510) ve PDF (1 sayfa) dışa aktarıldı
ve PNG gözle doğrulandı.

Medya için ayrı bir fixture kuruldu: dört `OBJE` kaydı — çıplak dosya adı, Windows yolu
(`C:\Users\anna\...pdf`), başka bir Windows yolu (`D:\Foto\eski\ayse.png`) ve hiç var
olmayan bir dosya. Sonuçlar: galeri dördünü de tipli yer tutucularla gösterdi; SAF klasörü
verilince iki fotoğraf çözüldü, klasör silinince kayboldu, yeniden eklenince **anında**
geri geldi; `_PRIM Y` portre olarak içe alındı; portreler listede, profilde ve diyagram
kartlarında göründü (kartlar üst üste binmeden); dosya seçiciyle içe aktarılan görsel
uygulama deposuna kopyalandı ve kırpma 200×260 → 127×141 olarak dosyaya yazıldı.

#### Faz 4'te öğrenilen beş şey

1. **`setMaxBitmapSize()` zorunlu bir el sıkışma, optimizasyon değil.** Motor
   `needMaxBitmapSize()` true döndürdüğünde, sınır bildirilmeden **hiç çizgi üretmiyor**.
   Çağrı atlanınca kartlar çiziliyor ama aralarında hiçbir bağlantı olmuyor — render hatası
   gibi görünen, aslında eksik çağrıdan kaynaklanan bir durum. Bu yüzden parametre
   nullable değil, varsayılanlı.

2. **JAR Java 21 bytecode'u (class file 65).** Modüller JVM 17'ye derleniyor; Kotlin
   sorun çıkarmıyor ama JDK 17'nin `javac`'ı JAR'ı okuyamıyor. `core:diagram`'da Java
   kaynağı olmadığı için sorun değil — ama oraya `.java` dosyası eklenirse çıkacak.

3. **Kayıtlara kimlik oluşturma anında atanmalı.** GEDCOM kimliği dışa aktarıma
   ertelenirse, uygulamada eklenen kişi diyagramda **boş kart** olarak görünüyor: motor
   kayıtları çapraz referans kimliğiyle adresliyor. `createPerson`/`createFamily` artık
   kimliği hemen veriyor; `RecordCreationTest` bunu kilitliyor.

4. **Önbellek anahtarı, hesaplamanın *girdilerinin tamamını* içermeli.** Medya çözümleme
   sonuçları `(ağaç, bağlantı)` ile önbelleğe alınıyordu; ama bir ekran fotoğrafı
   *compose olur olmaz* istiyor — genelde ağacın izinli klasörleri henüz yüklenmeden.
   O ilk "bulunamadı" cevabı sonraki doğru denemeyi gölgeliyordu ve fotoğraf, yarışı kimin
   kazandığına göre **bazen** görünüyordu. Emülatörde ilk denemede kayıp, yeniden
   başlatınca görünür olması tesadüf değil, teşhisin kendisiydi. Klasörler artık anahtarın
   parçası.

5. **`pointerInput` bloğu dışarıdaki değeri dondurur.** Kırpma dikdörtgeni jest bloğunda
   okunuyordu ama blok yalnızca `width/height` değişince yeniden başlıyor: her hareket,
   bloğun başladığı andaki *eski* dikdörtgene uygulanıyordu, dolayısıyla seçim parmağı
   takip etmiyordu. Doğrusu, sürükleme başındaki dikdörtgeni sabitleyip toplam yolu ona
   eklemek. Bu tür hata testte değil ancak elle sürükleyince görünüyor.

---

### ❌ Faz 5 — Firebase *(yazıldı, sonra tümüyle kaldırıldı — 17 Ağustos 2026)*

Hesap oluşturma, Firestore senkronu ve bulut paylaşımı kodlandı, testleri geçti ve
cihazda doğrulandı; ardından ürün kararıyla **tümü söküldü**. Uygulama artık sunucusuz:
hesap yok, veri cihazdan çıkmıyor.

**Kaldırılanlar:** `core:firebase` ve `feature:auth` modülleri, `SyncEngine`,
`SyncWorker`, `SyncRepository`, `AuthRepository`, `SyncRemote` portu, `RowStore`,
18 tablo × 3 olaylık 54 SQL tetikleyicisi, `sync_queue` ve `sync_control` tabloları,
`trees` tablosundaki `firestoreId` / `ownerUid` / `lastSyncedAt` sütunları,
`firestore.rules`, `google-services.json` bağlantısı ve Firebase bağımlılıklarının
tamamı. Room şeması sürüm 1 olarak yeniden üretildi — uygulama yayınlanmamıştı, migration
gerekmedi.

**Neden kaldırıldı.** Bulut senkronu kullanıcıya hesap açtırmayı, o hesabın verisini
saklamayı ve gizlilik/silme taleplerini karşılamayı gerektiriyor. Bir soyağacı uygulaması
için taşınan veri, doğrudan aile üyelerinin kişisel bilgisi. Uygulamanın **hiçbir şey
toplamaması**, toplayıp iyi korumasından daha basit ve daha savunulabilir bir konum.

**Geriye ne kaldı.** Cihazlar arası taşıma zaten çözülmüştü ve öyle kaldı: `core:backup`
ZIP arşivi (manifest + GEDCOM + medya) ve GEDCOM dışa aktarımı. Ağaç paylaşımı
`feature:share` üzerinden dosyayla yapılıyor; `TreeComparator` / `TreeMerger` gelen ağacı
yereldekiyle karşılaştırıp farkları uyguluyor — bunlar bulutla değil, çapraz referans
kimliğiyle çalıştığı için senkrondan hiç etkilenmedi.

**Ne kaybedildi.** Cihazlar arası otomatik senkron ve birden çok kişinin aynı ağacı
canlı düzenlemesi. Her ikisi de artık elle: yedek al, karşı cihazda geri yükle.

**Silinmeden önce öğrenilen ve kalıcı olan iki şey**, bir gün senkron geri gelirse:

1. **Defter tutması kendini kuyruğa sokabilir.** Her döngü sonunda ağacın `lastSyncedAt`
   sütunu yazılıyordu, bu da ağaç satırını yeniden kuyruğa düşürüyordu: kuyruk hiç
   boşalmıyor, her senkron ağacı tekrar yüklüyordu. Defter tutması kayıt dışı yapılmalı.

2. **Kısmi satır, bilmediği sütunları siler.** `INSERT OR REPLACE` tüm satırı değiştirdiği
   için, eski sürümden gelen eksik sütunlu bir kayıt geri kalanını sessizce boşaltır.
   Yazma mevcut satırla birleştirmeli.

### ✅ Faz 6 — Kalan Özellikler *(tamamlandı, cihazda doğrulandı)*

| Teslim | Durum |
|---|---|
| `core:backup` — ZIP arşivi (manifest + GEDCOM + medya), geri yükleme, 3 yedek budama | ✅ |
| `feature:backup` — yedek listesi, otomatik yedekleme anahtarı, dosyaya kopyalama | ✅ |
| `ShareGrades` — derece durum makinesi, 9 test | ✅ |
| `TreeComparator` — çapraz referans kimliğiyle kayıt düzeyi karşılaştırma | ✅ |
| `TreeMerger` — kabul edilen farkları uygulama, kimlik yeniden eşleme | ✅ |
| `feature:share` — paylaş/al, inceleme ekranı (ekle/değiştir/kaldır) | ✅ |
| `core:billing` — Play Billing 9, satın alma onayı, hesap bazlı yetki | ✅ |
| `core:notifications` — günlük doğum günü işi, kanal, kullanıcı saati | ✅ |
| `feature:settings` — tema, uzman modu, hatırlatma saati, premium kartı | ✅ |
| Üç dil — 25 modülün tamamında EN/TR/AR eksiksiz | ✅ |
| Diyagram kart uzun-bas menüsü — 9 aksiyon, kişi seçici | ✅ |
| **Play Console'da premium ürünü tanımlama** | ⬜ *(senin adımın)* |

**Cihaz doğrulaması (API 36):** Ayarlar ekranının tamamı çalıştı; premium kartı emülatörde
mağaza olmadığını dürüstçe bildirdi. Yedek alındı (1.037 bayt) ve arşiv açılıp içeriği
doğrulandı: `manifest.json` + `tree.ged` (`_PRIM Y` dahil) + `media/ayse.png`. Geri yükleme
yeni ağaç oluşturdu, fotoğrafı geri getirdi ve kök kişiyi farklı satır kimliğine (5 → 8)
doğru eşledi; kaynak ağaç dokunulmadan kaldı.

#### Kart menüsü

Uzun basış artık dokuz aksiyonlu bir sayfa açıyor: profili aç, diyagramı buraya odakla,
çocuk/eş olduğu aileler, akraba ekle, ağaçtaki birini bağla, düzenle, ailelerden ayır, sil.

Orijinalin bağlam menüsü yerine alt sayfa: aksiyonların hepsi tek satır değil — bir aile
girdisi içindeki kişileri adlandırıyor ("Ahmet Yılmaz, Mehmet Yılmaz") — ve birkaçı başka
ekrana götürüyor; sayfa bunu yüzen bir menünün yapamadığı biçimde belli ediyor.

Uygulanamayan girdiler **devre dışı değil, hiç yok**. Ailesi olmayan biri ailelerden
ayrılamaz; soluk bir seçenek göstermek kullanıcıyı nedenini çözmeye davet eder.

"Ağaçtaki birini bağla" için aranabilir bir kişi seçici eklendi (`PersonPickerRoute`);
başlangıç kişisi listeden çıkarılıyor, çünkü birini kendisiyle akraba yapmak hiç
istenmiş olamaz.

**Cihaz doğrulaması:** Ayşe'ye Ahmet mevcut kişi olarak *ebeveyn* bağlandı → yeni F2
ailesi açıldı, Ayşe çocuk, Ahmet cinsiyetine göre HUSBAND rolünde, F1 dokunulmadı.
Diyagram bunu çift kayıt işaretiyle (↔) doğru çizdi. Ardından "ailelerden ayır" her iki
üyeliği kaldırdı, boşalan F2 budandı, Ayşe adıyla ağaçta kaldı.

#### Gerçek cihazda çıkan iki hata *(16 Ağustos)*

Kullanıcı bildirdi: diyagram düzenlemelerden sonra güncellenmiyor ve gerçek cihazda tam
görünmüyor. İkisi de üç kişilik test ağacında görünmez, çünkü ikisi de ölçekle ilgili.

**1. Dev grafik katmanı hiç çizilmiyordu.** Pan/zoom, tüm diyagramı saran tek bir
`graphicsLayer`'a uygulanıyordu. Bir render node GPU'nun azami doku boyutunu — yaygın
olarak 4096 px — aştığında **sessizce hiçbir şey çizmiyor**. Beş kuşaklı bir aile doğal
boyutunda ~6300 px genişliğinde; yani özellik tam da var olma nedeni olan ağaçlarda
çalışmıyordu. Emülatörde ölçtüm: `diagramNode=6315×1415` doğruydu ama onu saran kutu
`1080×1415`'e sıkıştırılıyor, katman da onunla birlikte kırpıyordu.

Çözüm: dönüşüm artık her parçaya ayrı ayrı uygulanıyor — her kart kendi küçük katmanını
taşıyor, bağlantılar ekran boyutundaki tek bir canvas'ta `withTransform` ile çiziliyor.
Hiçbir katman ekrandan büyük olmuyor. `DiagramEngineTest`'e bunu kayda geçiren bir kanarya
testi eklendi: gerçek bir ağacın bir katmanın izin verilen boyutundan geniş olduğunu
ölçüyor, ki ileride biri "sadeleştirip" tek katmana dönmeye kalkarsa önce bunu okusun.

**2. Diyagram veritabanını dinlemiyordu.** `load()` yalnızca sekme ilk açıldığında
çalışıyordu; kişi editöründen eklenen bir çocuk ya da profilde düzeltilen bir ad, ağaç
kapatılıp açılmadan görünmüyordu. Artık kişiler, aile üyelikleri, aile olayları, portreler
ve diyagram ayarları birleşik bir "içerik parmak izi" olarak izleniyor; parmak izi
değişince yeniden çiziliyor. Parmak izi karşılaştırması, Room'un aynı satırları yeniden
yayınlamasının binlerce kişilik bir ağaçta motoru boşuna çalıştırmasını engelliyor.
İlk açılışta dönen çember görünüyor, sonraki çizimlerde mevcut diyagram ekranda kalıyor —
küçük bir düzeltme ekranı karartmıyor.

#### Faz 6'da alınan üç karar

1. **Arşiv formatı GEDCOM, veritabanı dökümü değil.** Döküm yalnızca onu yazan sürümün
   okuyabildiği bir şeydir ve formatından uzun yaşamayan bir yedek, yedek değildir.
   GEDCOM her soyağacı programında açılır; uzantı tablosu sayesinde gidiş-dönüş kayıpsız.
   Böylece bir arşiv hem geri dönüş noktası hem dışa aktarım.

2. **Geri yükleme her zaman yeni ağaç açar, asla üzerine yazmaz.** Yedeğin alındığı andan
   beri olan her şeyi yok etmek, üstelik kimse fark etmeden — ve fark edildiğinde istenen
   yedek, az önce üzerine yazılan olur.

3. **Kayıtlar çapraz referans kimliğiyle eşleşir, satır kimliğiyle değil.** Satır kimlikleri
   cihaza özeldir; adlar benzersiz değildir — üç Mehmet Yılmaz'lı bir aile sıradandır.
   Birleştirmede bunu karıştırmak, bir ailenin olaylarını sessizce başkasına takar.
   `TreeMergeTest` bunu iki taraftaki kimlikleri kasten kaydırarak kilitliyor.

#### Faz 6'da yakalanan iki şey

1. **Arşivdeki `../` girdisi.** Bir arşiv herkesten gelebilir; hazırlanmış bir girdi adı
   hedef klasörün dışına yazabilirdi. Yol artık güvenilmek yerine denetleniyor, ve test
   bunu kurcalanmış bir arşivle doğruluyor.

2. **Manifest sürümünü yazmıyordu.** kotlinx.serialization varsayılanları atlıyor, bu
   yüzden `version: 1` dosyada hiç görünmüyordu. Yalnızca varsayılandan farklıyken beliren
   bir format işareti, bir arşivin neden açılmadığını anlamaya çalışan biri için hiç yok
   demektir. `encodeDefaults` açıldı.

**Senin yapman gerekenler:** Play Console'da `familytree_premium` kimliğiyle tek seferlik
bir ürün tanımla. Ürün yokken uygulama "mağaza kullanılamıyor" diyor ve geri kalan her şey
normal çalışıyor.

#### Faz 6 kapsamının ayrıntısı

| Özellik | Kapsam |
|---|---|
| ZIP yedekleme | Kaydet/geri yükle sekmeleri, ağaç başına son 3 yedek budaması, SAF klasör doğrulaması |
| Dosya tabanlı paylaşım | ZIP oluştur + sistem paylaşım sayfası (familygem.app sunucusu yerine) |
| Compare → Process → Confirm | Grade durum makinesi (0/9/10/20/30) ile fark inceleme: Ekle / Değiştir / Sil |
| Merge algoritması | **Premium.** Otomatik eşleştirme (isim ön-eki + ≤5 yıl doğum farkı), eşleşme yayılımı (ebeveyn+eş), ID çakışma çözümü. *Orijinalde hiç testi yoktu — veri bozulması riski en yüksek yer* |
| Play Billing | Tek seferlik ürün, `users/{uid}.premium` |
| Doğum günü bildirimleri | WorkManager + AlarmManager, ~500 alarm cihaz limiti savunması |
| Uzman modu geçişleri | Kaynaklar/depolar/gönderenler/ham tag'ler/ID düzenleme |
| 3 dilin tam çevirisi + erişilebilirlik geçişi | TalkBack, 48dp dokunma hedefleri, dinamik font ölçeği |

---

## 4. Mimari Referans

### Modül yapısı

```
FamilyTree/
├── build-logic/convention/   7 convention plugin
├── app/                      Application, MainActivity, NavHost, Hilt graph
├── core/
│   ├── model/                Saf Kotlin domain modelleri (Android yok)   [JVM]
│   ├── common/               Dispatcher qualifier'ları, LoadState
│   ├── domain/               Repository *arayüzleri* + use case'ler       [JVM]
│   ├── database/             Room entity / DAO / converter / migration
│   ├── datastore/            Preferences DataStore
│   ├── data/                 Repository *implementasyonları* + mapper
│   ├── gedcom/               (Faz 2) folg interop, importer/exporter/projector
│   ├── diagram/              (Faz 4) gedcomgraph sarmalayıcı + Canvas renderer
│   ├── media/                (Faz 4) dosya çözümleme, SAF, kırpma
│   ├── backup/               (Faz 6) ZIP arşivi, karşılaştırma, birleştirme
│   ├── notifications/        (Faz 6) doğum günü hatırlatmaları
│   ├── billing/              (Faz 6) Play Billing
│   ├── designsystem/         M3 tema, token'lar, atomik composable'lar
│   └── ui/                   Domain modelini bilen paylaşılan composable'lar
└── feature/
    └── trees/ …              her ekran ailesi için bir modül
```

**Bağımlılık kuralı:** `feature:* → core:domain → core:model`.
`core:data` domain'i implemente eder ama **feature'lar onu göremez** — Hilt `:app`'te bağlar.
Bu, Clean Architecture'ın bağımlılık kuralını derleme zamanında zorlar.

### Room şeması — 20 tablo

| Grup | Tablolar |
|---|---|
| Ağaç | `trees`, `headers`, `tree_media_folders`, `tree_shares` |
| Kişi | `persons`, `person_names` |
| Aile | `families`, `family_members` |
| Olay | `events`, `addresses` |
| Kayıtlar | `notes`, `note_links`, `media`, `media_links`, `sources`, `source_citations`, `repositories`, `repository_refs`, `submitters` |
| Altyapı | `extensions` |

**Üç kritik tasarım kararı:**

1. **`family_members` tek doğruluk kaynağı.**
   GEDCOM akrabalığı iki yerde tutar (`FAM.HUSB/WIFE/CHIL` **ve** `INDI.FAMS/FAMC`) —
   ağaçların tek taraflı kopuk referans biriktirmesinin sebebi tam olarak budur.
   Tek satırda tutup dışa aktarımda iki yönü de üretmek, FamilyGem'in `findErrors` ile
   taramak zorunda kaldığı bozulma sınıfını **yapısal olarak imkânsız** kılıyor.

2. **`extensions` tablosu öz-referanslı.**
   Maplenmeyen her vendor tag'i (`_UID`, `_MILT`, `_ROOT`, `_HOME`, `_APID`…) hiyerarşisi
   ve sırasıyla korunuyor. "Kayıpsız round-trip" iddiasının dayanağı bu tablodur.

3. **Çift kimlik.** Her tabloda Room PK `id: Long` + `gedcomId: String?` (`"I12"`, `null`=inline).
   `(treeId, gedcomId)` üzerinde unique index — SQLite NULL'ları unique index'te farklı
   saydığı için sınırsız inline kayıt mümkün. Bir ID değiştiğinde FK sabit kalır;
   orijinalin 6 ayrı yerde referans güncelleyen `U.editId` mantığı gereksizleşir.

**Visitor deseni tamamen kalktı.** 15 visitor sınıfı SQL sorgusuna dönüştü —
`MediaReferences` → `SELECT COUNT(*) FROM media_links WHERE mediaId = :id`.

**Kolon adı notu:** `order` ve `primary` SQLite anahtar kelimeleri olduğu için
`position` ve `isPrimary` olarak adlandırıldı.

### Tasarım sistemi

Orijinalin görselleri GPL ve tarihi — **hiçbiri kopyalanmadı.** Sıfırdan:
orman yeşili (primary) + bronz (secondary) + terakota (tertiary), hafif sıcak nötrler.
Android 12+ dinamik renk varsayılan açık.

Cinsiyet ve ağaç-durumu renkleri `LocalGenealogyColors`'ta **ayrı tutuluyor** —
anlam taşıdıkları için (kadın/erkek kartı, tüketilmiş ağaç) duvar kâğıdı paletiyle
yeniden renklendirilmeleri okunabilirliği bozardı.

#### Uygulama ikonu ve açılış ekranı

Marka işareti: bir aile grubu ve altında uygulamanın diyagramda kullandığı bağlayıcı —
gövde, kardeş çubuğu, üç düğüm. "Aile" değil, "soyağacı" demesi için; yalnız figürler
bir rehber uygulamasının ikonu gibi duruyordu. Figürler indirilen bir SVG'den geliyor
(lisansı `NOTICE.md`'de açık madde); onunla gelen avuç çizimleri atıldı, ince kontur
oldukları için 48dp'de beyaz bir lekeye dönüşüyorlardı.

İki ölçü kararı, ikisi de ölçülerek bulundu:

- **Ön plan katmanı 66dp güvenli daireye sığdırıldı** (`ic_launcher_foreground.xml`).
  Launcher 108dp'lik katmanı 72dp'ye kırpıp üstüne istediği maskeyi uyguluyor; en dar
  maske daire. Çizim 108'lik tuvale küçük görünüyor, sebebi bu.
- **Açılış ekranı ayrı bir çizim kullanmıyor**: ikonun ön plan katmanının kendisi.
  Android açılışa 288dp tuval verip ortadaki üçte ikisini gösteriyor — maskeyle aynı
  oran — dolayısıyla ikinci bir boy gerekmiyor.

**Açılış ekranı rengi neden gece varyantı taşımıyor** *(cihazda yakalandı)*: splash
penceresini sistem, uygulama süreci daha yokken çiziyor; kaynaklar **sistemin** gece
moduna göre çözülüyor, kullanıcının uygulama içinde seçtiği temaya göre değil. İlk
denemede `@color/launch_background` (temanın `surface` rengi) kullanılmıştı; sistem koyu,
uygulama tercihi "Açık" olan telefonda splash siyah gelip beyaz uygulamaya devrediyordu.
Artık `@color/splash_background` — Pine30, gece varyantı **yok** — her kombinasyonda
doğru; geçiş kaza değil, kasıt. `launch_background` ise splash ile ilk Compose karesi
arasındaki pencere rengi olarak kalıyor ve gündüz/gece ayrımını orada koruyor.

Splash, DataStore ilk değeri verene kadar ekranda tutuluyor (`setKeepOnScreenCondition`,
1 sn. üst sınırla): tema, dil ve yazı tipi ayarlardan geldiği için bekletmezsek ilk kareler
varsayılanlarla çizilip kullanıcının gözü önünde düzeliyordu.

---

## 5. AGP 9 Notları *(Faz 1'de öğrenildi — tekrar tökezlememek için)*

AGP 9.3.1 üç kırıcı değişiklik getirdi:

1. **`CommonExtension` artık generic değil.** `CommonExtension<*, *, *, *, *, *>` yerine
   düz `CommonExtension`.
2. **Lambda DSL formları `CommonExtension`'da yok.** `defaultConfig { }` ve
   `compileOptions { }` sadece somut `ApplicationExtension`/`LibraryExtension`'da.
   Convention plugin'lerde property erişimi kullanılmalı:
   `commonExtension.defaultConfig.minSdk = 28`.
3. **Kotlin yerleşik geldi.** `org.jetbrains.kotlin.android` plugin'ini uygulamak **hata
   veriyor**; AGP Kotlin'i kendi uyguluyor ve jvmTarget'ı `compileOptions` ile
   otomatik hizalıyor. `org.jetbrains.kotlin.plugin.compose` ve `...plugin.serialization`
   ise hâlâ ayrı uygulanıyor.

Ayrıca: `Lint` DSL sınıfı `gradle-api` artifact'ında yok, `ResourcesPackaging.excludes`
read-only — ikisi de convention plugin'den çıkarıldı.

**Gradle 9:** Test bağımlılığı olup hiç testi olmayan bir modülde `test` görevi hata
veriyor. Convention plugin `failOnNoDiscoveredTests = false` ile bunu kapatıyor.

**Robolectric:** Modül SDK 37'ye derlendiği için `@Config(sdk = [36])` ile sabitlemek
gerekiyor; Robolectric 4.16.1 henüz 37 imajı taşımıyor.

---

## 6. Derleme ve Çalıştırma

```bash
./gradlew :app:assembleDebug        # derle
./gradlew :app:installDebug         # emülatöre/cihaza kur
./gradlew test                      # tüm birim testleri
./gradlew projects                  # modül ağacını gör
./gradlew :core:database:assembleDebug   # Room şemasını yeniden üret

# Şema JSON'u: core/database/schemas/…/1.json (versiyon kontrolünde tutulur)
```

Uygulama paketi: `com.familytrees.app` (debug'da `.debug` son eki).

**Yayın imzası.** `:app:assembleRelease` kök dizindeki `keystore.properties` dosyasını
arar (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Dosya yoksa derleme
imzasız APK üretir — hata vermez. Dosya da keystore da `.gitignore`'da; deponun tek sırrı
budur. Dosya `providers.fileContents` ile okunuyor: configuration cache açık olduğu için
düz bir `File.exists()` girdi sayılmaz ve keystore'u sonradan ekleyen bir derleme sessizce
**imzasız** çıkardı.

---

## 7. Doğrulama Stratejisi

| Test | Kapsam | Faz |
|---|---|---|
| ✅ **Round-trip sadakat testi** | `.ged` → Room → `.ged`, hiçbir tag/değer çifti kaybolmamalı | 2 |
| ✅ **Sabit-nokta testi** | Dışa aktarımı yeniden içe aktarmak aynı ağacı vermeli | 2 |
| ✅ `GedcomDateTest` | Orijinal `DateTest.kt`'nin birebir portu, 63 vaka | 2 |
| ✅ `TreeMaintenanceTest` | Her denetim, kasten bozulmuş ağaçla ayrı ayrı | 2 |
| Room `MigrationTestHelper` | Şema JSON'ları versiyon kontrolünde | 3+ |
| Repository testleri | In-memory Room | 3+ |
| `MergeUseCase` testleri | Orijinalde **hiç yoktu** — en yüksek veri bozulma riski | 6 |
| ✅ `DiagramEngineTest` | Room → projeksiyon → yerleşim zinciri, bitmap el sıkışması dahil | 4 |
| ✅ `RecordCreationTest` | Yeni kayıtların kimlik alması ve çakışmaması | 5 |
| ✅ `MediaResolverTest` | 5 aşamalı çözümleme sırası, önbellek geçersizleştirme | 11 |
| ✅ `MediaAttachmentTest` | Paylaşımlı kayıt kuralları, portre seçimi | 7 |
| ✅ `FileNamingTest` | Kopya numaralandırma sınır durumları | 5 |
| Compose UI testleri | Trees → NewTree → import → Diagram akışı | 5+ |
