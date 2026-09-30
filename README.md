# Cam RTC

Prototype kamera Android → WebRTC → OBS, video saja. Tidak ada izin mikrofon, akun, cloud, database, atau relay media.

## Status
Source prototipe tersedia. Belum diuji pada HP/OBS fisik atau diukur CPU/RAM/suhu. Browser Source digunakan pada versi pertama; plugin OBS native dan USB belum dibuat. Hardware encoding dicoba oleh WebRTC, tetapi tidak dijamin pada setiap perangkat/codec.

## PC
Install Node.js 22+, lalu:
```sh
cd receiver
npm install
npm start
```
Server menampilkan IP LAN, kode pairing acak dan URL OBS. Izinkan TCP 8787 di firewall hanya pada jaringan privat. Gunakan Wi-Fi yang sama, tanpa AP/client isolation. Media WebRTC juga membutuhkan UDP antarperangkat; TCP 8787 hanya signaling. Jangan expose port ke internet.

Di OBS tambah Browser Source dengan URL yang dicetak (localhost), ukuran 1280×720, FPS 30. Matikan "Shutdown source when not visible" bila ingin koneksi tetap aktif. Hanya satu receiver per sesi; tutup preview browser sebelum membuka OBS.

## Android
Buka folder `android` di Android Studio (JDK 17, SDK 35), sync Gradle, jalankan app pada HP Android 8+. Tidak ada Gradle wrapper binary dalam repo; Android Studio dapat menggunakan Gradle 8.9 lokal.
Masukkan IP PC dan kode pairing, pilih kualitas, tekan Mulai, izinkan kamera. Buka receiver OBS terlebih dulu atau sesudahnya. Tombol Ganti kamera dan Preview tersedia saat streaming.

Default 720p/30, opsi hemat 480p/24 dan 1080p/30. Resolusi aktual mengikuti kamera/negosiasi. Streaming berhenti ketika app masuk background atau layar terkunci. Preview off mengurangi render lokal; layar tetap menyala saat streaming.

## Pemeriksaan
```sh
cd receiver
npm test
```
CI memeriksa Node dan membangun APK debug. APK debug hanya untuk pengujian. Download artifact dari tab Actions setelah build hijau.
Uji fisik: koneksi, pergantian kamera, preview off, stop/start, cabut Wi-Fi, receiver ditutup/dibuka ulang, background, penolakan izin. Catat CPU/RAM PC, suhu/baterai HP dan latency selama 10 menit di tiap preset. Belum ada auto reconnect: setelah jaringan terputus tekan Stop lalu Mulai.

## Desain
Android Camera2 + WebRTC EGL encoder factory → video langsung ke OBS. Node hanya meneruskan SDP/ICE. Receiver tidak meminta kamera/mikrofon dan tidak memutar audio. Pairing melindungi signaling dengan token acak; server HTTP lokal belum mengenkripsi signaling, sehingga gunakan jaringan terpercaya.

Referensi: https://github.com/webrtc-sdk/android dan https://obsproject.com/kb/browser-source
