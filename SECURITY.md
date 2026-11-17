# Semakan keselamatan — Waktu Solat MY 0.1.3

Disemak pada 16 November 2026. © 2026 fitrirusli.

## Kawalan dalam keluaran ini

- APK keluaran ialah binaan **release**, dengan debugging/JNI debugging dimatikan dan kod/sumber dikecilkan menggunakan R8. Pemeriksa membaca APK akhir, bukan hanya tetapan Gradle.
- Hanya tiga kebenaran: `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`. Tiada kebenaran SMS, kenalan, gambar, lokasi, kamera, mikrofon atau accessibility.
- Klien jadual membina URL HTTPS tetap ke `www.e-solat.gov.my`. Kod zon disahkan sebelum sambungan dibuka. Pengalihan HTTP tidak diikuti; jawapan selain HTTP 200 ditolak.
- Pengesahan sijil dan hostname menggunakan mekanisme HTTPS biasa. Konfigurasi APK hanya mempercayai CA sistem, menolak HTTP biasa dan tidak menyediakan debug trust overrides. Tiada `trust-all` atau sijil peribadi dibundel.
- Saiz respons dihadkan kepada 2 MiB walaupun Content-Length tiada atau palsu. UTF-8 rosak ditolak. Masa sambungan 15 saat dan masa menunggu bacaan 20 saat.
- Data JAKIM disahkan mengikut zon, tarikh dan susunan waktu sebelum cache diganti. Sambungan/data gagal menggunakan jadual sah yang sudah tersimpan.
- Tetapan dan jadual menggunakan `MODE_PRIVATE`. Backup cloud dan pemindahan data standard Android dinyahaktifkan melalui manifest dan peraturan pengecualian semua domain app.
- Receiver tidak dieksport. JobService hanya boleh diikat oleh sistem melalui `BIND_JOB_SERVICE`. PendingIntent menggunakan sasaran eksplisit dan `FLAG_IMMUTABLE`.
- Tiada pelayan perantara, iklan, analitik, akaun, muat turun kod boleh laksana atau pustaka pihak ketiga dalam `releaseRuntimeClasspath`.

Pelayan JAKIM tetap menerima permintaan jadual, kod zon, alamat IP rangkaian dan User-Agent app. Ini tidak menjanjikan anonimiti rangkaian.

## Kunci tandatangan dan kemas kini

Kunci keluaran baharu ialah RSA 4096-bit, identiti `fitrirusli`. Cap jari sijil SHA-256:

```text
e9405bae23743269dad2918627e1c0d954318cb51bf4d66ef2eac8a05160a512
```

APK mengandungi bukti pertukaran daripada kunci asal versi 0.1.0/0.1.1. API 28–36 menggunakan kunci keluaran baharu dengan v3; API 26–27 menggunakan tandatangan v2 asal untuk keserasian kemas kini Android 8. Keupayaan mengambil alih data pemasangan dibenarkan bagi kemas kini, manakala rollback kepada kunci lama dinyahaktifkan dalam lineage. Sokongan tandatangan dan lineage diperiksa menggunakan `apksigner`; pemasangan sebenar pada telefon pengguna belum diuji dalam sesi ini.

Kunci peribadi **tidak dimasukkan** ke dalam APK, arkib sumber atau Git. Lokasi setempat:

```text
%USERPROFILE%\.waktusolat-my\signing\
```

Folder hanya memberi akses kepada akaun Windows semasa dan SYSTEM. Keystore PKCS12 menggunakan kata laluan rawak yang disimpan melalui Windows DPAPI; tiada kata laluan ditulis ke kod, log atau argumen proses. Sijil awam dan bukti lineage dalam folder projek `signing/` boleh dikongsi.

Kemas kini masa hadapan memerlukan kunci asal. Simpan sandaran kunci secara selamat; fail kata laluan DPAPI terikat pada akaun/profil Windows ini. Sebelum menukar komputer atau memadam profil Windows, pindahkan kunci dan kata laluan melalui kaedah sandaran sulit yang sesuai. Jangan kongsikan folder kunci bersama APK.

## Bukti pengesahan

- `assembleRelease`, `lintRelease`, `testReleaseUnitTest`: **43 ujian lulus**.
- 13 ujian jadual rasmi dan sempadan waktu; 14 ujian komponen Android API 26/35 termasuk widget berubah saiz, copyright, kalendar, pilihan tarikh, sempadan data dan keperluan jadual tahun baharu; 8 ujian klien rangkaian; 8 ujian padanan kalendar Hijrah berdasarkan keseluruhan 365 hari data rasmi.
- Kalendar menggunakan data JAKIM tersimpan yang sama. Tiada permission, pelayan atau pustaka runtime baharu; browsing kalendar tidak menghantar permintaan bagi setiap tarikh.
- Ujian HTTPS langsung menggunakan kelas klien yang sama berjaya mengambil jadual WLY01 daripada JAKIM, dengan pengesahan sijil biasa dan redirect dimatikan.
- `tools/verify_release.py` memeriksa APK binari: debugging, permissions, exports, HTTPS trust, backup exclusions, zip alignment, sijil v2/v3, bukti kemas kini dan ketiadaan fail kunci peribadi.
- Laporan boleh dibaca mesin: `dist/WaktuSolatMY-v0.1.3-release.security.json`. Hash fail dalam `dist/SHA256SUMS.txt`.

Semakan ini ialah semakan kod dan pengesahan automatik setempat. Ia tidak merangkumi pentest bebas, ujian forensik telefon, atau pengesahan keselamatan pelayan JAKIM/firmware Android. Ujian reboot, Doze, rangkaian dan pelancar pada telefon sebenar masih diperlukan. Tiada sijil keselamatan pihak ketiga didakwa.

Rujukan: [debuggable](https://developer.android.com/privacy-and-security/risks/android-debuggable), [konfigurasi rangkaian](https://developer.android.com/privacy-and-security/security-config), [backup](https://developer.android.com/identity/data/autobackup), [tandatangan dan pertukaran kunci](https://developer.android.com/tools/apksigner).
