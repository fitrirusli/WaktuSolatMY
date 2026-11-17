# Waktu Solat MY

© 2026 fitrirusli. App Android dan widget waktu solat Malaysia, menggunakan data terus daripada **e-Solat JAKIM**. Zon awal ialah WLY01 — Kuala Lumpur, Putrajaya.

## Pasang versi 0.1.3

1. Pindahkan `dist/WaktuSolatMY-v0.1.3-release.apk` ke telefon Android **8.0 atau lebih baharu**.
2. Buka APK dan pasang sebagai kemas kini versi lama. Bukti pertukaran kunci disertakan supaya Android boleh mengekalkan data app semasa kemas kini.
3. Buka **Waktu Solat MY**. Jika widget lama masih menggunakan susun atur lama, buang widget itu sahaja dari skrin utama dan tambah semula melalui **Tambah widget** atau menu Widget pelancar.
4. Tekan lama widget dan tarik pemegang atas/bawah untuk mengubah tinggi.

Tetapan zon dan cache app sedia ada tidak perlu dipadam. Kesesuaian tandatangan telah disahkan menggunakan alat Android; aliran pemasangan pada telefon sebenar belum diuji dalam sesi pembangunan ini.

## Kalendar Hijrah

Bahagian **Tarikh Islam hari ini** memaparkan tarikh Hijrah penuh pada halaman utama. Tekan **Kalendar ›** untuk melihat bulan Hijrah; setiap petak memaparkan hari Hijrah di atas dan tarikh Masihi di bawah. Tekan satu tarikh untuk melihat padanannya, gunakan anak panah untuk menukar bulan, atau **Hari ini** untuk kembali ke tarikh semasa. Widget yang mempunyai ruang mencukupi memaparkan tarikh Hijrah bersama tarikh Masihi.

Kalendar menggunakan pasangan tarikh sebenar daripada jadual JAKIM yang sudah dimuat turun dan boleh dibaca tanpa internet. Bulan yang hanya sebahagiannya tersedia ditandakan; tarikh di luar data tidak dianggarkan.

App boleh digunakan pada tahun-tahun akan datang. Apabila tahun Masihi bertukar, app memerlukan jadual baharu dan akan memuat turunnya daripada JAKIM apabila internet serta data tersedia. Paparan kalendar buat masa ini terhad kepada tahun jadual yang telah dimuat turun; semakan tahun hadapan lebih awal belum disediakan.

## Perubahan keluaran ini

- Tarikh Islam yang lebih jelas, kalendar bulanan Hijrah/Masihi, navigasi bulan, pilihan hari dan butang Hari ini.

- Binaan release dengan debugging dimatikan, R8 dan kunci keluaran berasingan yang dilindungi di luar projek.
- Sambungan HTTPS JAKIM diperketat: sijil sistem, tiada redirect, had saiz respons dan penolakan UTF-8 rosak.
- Backup/pemindahan data app standard Android dinyahaktifkan dengan peraturan eksplisit.
- Copyright **© 2026 fitrirusli** pada app; **© fitrirusli** pada header widget biasa/padat, tanpa menambah tinggi baris.
- **43 ujian automatik lulus** dan semakan APK binari termasuk bukti pertukaran kunci.

Butiran kawalan, bukti dan had semakan: [SECURITY.md](SECURITY.md).

## Fungsi

- Tujuh waktu harian: Imsak, Subuh, Syuruk, Zohor, Asar, Maghrib dan Isyak; kira detik ke solat fardu seterusnya dalam app.
- Carian 60 zon rasmi mengikut negeri, daerah atau kod zon.
- Jadual setahun disimpan mengikut zon/tahun untuk dibaca tanpa internet. Internet diperlukan bagi muat turun pertama dan tahun baharu.
- Tarikh Hijrah daripada data JAKIM, dengan waktu tetap `Asia/Kuala_Lumpur`.
- Widget padat memaparkan zon, solat seterusnya dan lima waktu fardu. Saiz paling pendek bertukar kepada satu baris; tarikh/sumber muncul apabila ruang mencukupi.
- Had tinggi awal 110dp dan minimum ubah saiz 40dp; saiz sebenar mengikut grid pelancar. Latar hijau mengikut kandungan dan tidak memenuhi ruang host yang kosong.
- Tiada iklan, akaun, analitik, GPS, pelayan perantara atau pustaka pihak ketiga dalam APK produksi.

Versi ini tidak menyediakan azan/notifikasi atau lokasi automatik. Semua widget berkongsi zon app. Android boleh menangguhkan kemas kini latar semasa Doze/jimat bateri; widget mempunyai butang ↻ dan memaparkan waktu harian tersimpan.

## Sumber waktu

```text
https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=year&zone=WLY01
```

Portal rasmi: <https://www.e-solat.gov.my/>. Waktu dipaparkan tanpa pengiraan, pelarasan atau pembundaran. Parser menyokong nama bulan API Bahasa Melayu seperti Mac, Mei, Ogos, Okt dan Dis, serta padanan Bahasa Inggeris. Data salah zon, tarikh tidak sah, waktu tidak tersusun atau kosong ditolak. Jadual daripada tahun lama tidak digunakan sebagai jadual hari ini.

Senarai zon dan fixture ujian diambil daripada JAKIM pada 13 November 2026. Klien HTTPS keluaran ini diuji semula secara langsung pada 16 November 2026. Ini app bebas yang menggunakan data JAKIM, bukan app rasmi JAKIM. Notis copyright merujuk kepada kod dan reka bentuk asal app.

## Binaan

Java Android asli; `minSdk 26`, `compileSdk/targetSdk 36`, AGP 8.13.2, Gradle 8.13 dan JDK 17. Untuk menyediakan alat setempat pada Windows dengan Python:

```powershell
.\build.ps1 -Setup
```

Skrip menyediakan JDK/Gradle/SDK dalam `.tools`, menerima lesen SDK bagi alat pembangunan, kemudian membina release, menjalankan lint/ujian, menandatangani APK dan memeriksa APK akhir. Binaan seterusnya menggunakan `.\build.ps1`.

Kunci peribadi tidak disertakan dalam arkib sumber. Binaan release yang serasi dengan pemasangan asal memerlukan bahan tandatangan dalam `%USERPROFILE%\.waktusolat-my\signing\` dan profil Windows yang boleh membuka kata laluan DPAPI. Jangan padam/pinda kunci tersebut untuk mengatasi ralat binaan; rujuk [SECURITY.md](SECURITY.md).

Untuk membina versi debug bagi pembangunan:

```powershell
.\build.ps1 -Development
```

Untuk menyegarkan senarai zon dan fixture terus daripada JAKIM:

```powershell
python tools/sync_reference.py
```

Untuk menyediakan arkib sumber dan hash keluaran selepas release berjaya:

```powershell
python tools/package_source.py
```

Sijil awam dan `rotation.lineage` boleh dikongsi bersama kod sumber. Fail `.p12`, `.keystore` dan `.dpapi` mesti kekal sulit. [COPYRIGHT](COPYRIGHT).
