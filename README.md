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
Server menampilkan IP LAN, URL OBS tanpa kode pairing. Izinkan TCP 8787 di firewall hanya pada jaringan privat. Gunakan Wi-Fi yang sama, tanpa AP/client isolation. Media WebRTC juga membutuhkan UDP antarperangkat; TCP 8787 hanya signaling. Jangan expose port ke internet.

Di OBS tambah Browser Source dengan URL yang dicetak (localhost), ukuran 1280×720, FPS 30. Matikan "Shutdown source when not visible" bila ingin koneksi tetap aktif. Hanya satu receiver per sesi; tutup preview browser sebelum membuka OBS.

## Android
Buka folder `android` di Android Studio (JDK 17, SDK 35), sync Gradle, jalankan app pada HP Android 8+. Tidak ada Gradle wrapper binary dalam repo; Android Studio dapat menggunakan Gradle 8.9 lokal.
Masukkan IP PC, pilih kualitas, tekan Mulai, izinkan kamera. Buka receiver OBS terlebih dulu atau sesudahnya. Tombol Ganti kamera dan Preview tersedia saat streaming.

Default 720p/30, opsi hemat 480p/24 dan 1080p/30. Resolusi aktual mengikuti kamera/negosiasi. Streaming berhenti ketika app masuk background atau layar terkunci. Preview off mengurangi render lokal; layar tetap menyala saat streaming.

## Pemeriksaan
```sh
cd receiver
npm test
```
CI memeriksa Node dan membangun APK debug. APK debug hanya untuk pengujian. Download artifact dari tab Actions setelah build hijau.
Versi 0.1.1 menambahkan izin network/Wi-Fi yang dibutuhkan WebRTC saat receiver dibuka dan mengisolasi callback SDP/ICE per negosiasi. Penyebab crash di perangkat belum dikonfirmasi lewat logcat.

Uji fisik: koneksi, pergantian kamera, preview off, stop/start, cabut Wi-Fi, receiver ditutup/dibuka ulang, background, penolakan izin. Catat CPU/RAM PC, suhu/baterai HP dan latency selama 10 menit di tiap preset. Belum ada auto reconnect: setelah jaringan terputus tekan Stop lalu Mulai.

## Desain
Android Camera2 + WebRTC EGL encoder factory → video langsung ke OBS. Node hanya meneruskan SDP/ICE. Receiver tidak meminta kamera/mikrofon dan tidak memutar audio. Tidak ada kode pairing: siapa pun di jaringan lokal yang bisa mengakses port 8787 dapat mencoba terhubung. Gunakan jaringan privat terpercaya. Server HTTP lokal belum mengenkripsi signaling.

Referensi: https://github.com/webrtc-sdk/android dan https://obsproject.com/kb/browser-source

## Kamera jaringan Windows (eksperimental, v0.2.0)
Pilih **Kamera jaringan / Windows (uji coba)** di HP, mulai dari **480p / 24 FPS**, lalu tekan **Mulai**. IP PC tidak diperlukan. HP dan PC harus berada pada LAN yang sama, tanpa client isolation. Di Windows 11 buka Settings → Bluetooth & devices → Cameras → Add a network camera → Search for cameras, lalu pilih Cam RTC. Tidak membutuhkan receiver Node, OBS, mikrofon, atau kode pairing. Windows tetap melakukan proses menambahkan perangkat kamera.

Mode ini memakai Camera2 → surface MediaCodec H.264 → RTP/RTSP TCP, ditambah layanan ONVIF device/media dan WS-Discovery. Hanya satu mode streaming aktif. Kamera belakang, tanpa preview lokal atau pergantian kamera dalam mode jaringan; aplikasi harus tetap terbuka dan layar menyala. Video mengikuti orientasi sensor kamera (rotasi/portrait belum dikoreksi pada mode ini). Stop melepas kamera, encoder, koneksi, port, dan multicast lock.

Port HP: UDP 3702 (discovery multicast 239.255.255.250), TCP 8080 (ONVIF), TCP 8554 (RTSP). Jika discovery gagal, periksa jaringan privat Windows, firewall, VPN, dan isolasi Wi-Fi. Untuk membedakan kegagalan streaming dari discovery, buka URL `rtsp://IP_HP:8554/camera` di VLC dengan transport RTP over RTSP/TCP. URL dan IP terlihat di aplikasi saat kamera aktif.

Implementasi ONVIF ini subset eksperimental, **belum tersertifikasi Profile S**. Build/unit test tidak membuktikan discovery/pairing Windows, decoding pada Vivo Y22, atau kompatibilitas OmeTV; semuanya perlu uji perangkat nyata. Windows dapat meminta operasi ONVIF tambahan: catat status operasi terakhir di HP jika penambahan gagal. Mode ini tidak menerapkan autentikasi, sesuai penggunaan LAN pribadi. Background removal OBS tidak ikut terbawa karena stream berasal langsung dari kamera HP.
