// ============================================================
// AnumLab - Sistem Cek Update Otomatis
// ============================================================
// Cara kerja:
// 1. Tiap app dibuka, cek rilis terbaru di GitHub Releases
// 2. Kalau cuma web bundle yang beda (fitur/bug fix biasa)
//    -> "Hot Update": unduh & pasang otomatis, tanpa install APK baru
// 3. Kalau rilis itu butuh native baru (ditandai MIN_NATIVE di
//    catatan rilis) -> tampilkan popup, arahkan unduh APK baru
//
// GANTI NILAI INI kalau nama repo GitHub berubah:
const REPO = 'syharl/AnumLab';

// Naikkan angka ini SECARA MANUAL cuma kalau kamu bikin perubahan
// native (nambah permission, plugin native baru, dsb). Untuk update
// fitur/bug fix biasa (HTML/JS/CSS saja), biarkan angka ini tetap.
const NATIVE_BUILD = 1;

async function cekUpdate() {
  try {
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`);
    if (!res.ok) return;
    const data = await res.json();

    const versiTerbaru = data.tag_name;
    const versiWebTersimpan = localStorage.getItem('anumlab_versi_web') || '';

    // Cek apakah rilis ini menandai butuh native minimum lebih baru
    const match = (data.body || '').match(/MIN_NATIVE:\s*(\d+)/);
    const minNative = match ? parseInt(match[1], 10) : 0;

    if (minNative > NATIVE_BUILD) {
      const apkAsset = (data.assets || []).find(a => a.name.endsWith('.apk'));
      tampilkanPopupUpdatePenuh(versiTerbaru, apkAsset ? apkAsset.browser_download_url : data.html_url);
      return;
    }

    if (versiTerbaru && versiTerbaru !== versiWebTersimpan) {
      const bundleAsset = (data.assets || []).find(a => a.name === 'web-bundle.zip');
      if (bundleAsset) {
        await terapkanHotUpdate(bundleAsset.browser_download_url, versiTerbaru);
      }
    }
  } catch (e) {
    console.log('Cek update gagal (biasanya karena tidak ada koneksi):', e);
  }
}

async function terapkanHotUpdate(url, versiBaru) {
  try {
    const { CapacitorUpdater } = window.Capacitor.Plugins;
    tampilkanStatus('Ada pembaruan ringan, memasang...');
    const info = await CapacitorUpdater.download({ url, version: versiBaru });
    localStorage.setItem('anumlab_versi_web', versiBaru);
    await CapacitorUpdater.set(info);
    // set() akan reload aplikasi otomatis ke versi baru
  } catch (e) {
    console.log('Hot update gagal:', e);
  }
}

function tampilkanPopupUpdatePenuh(versi, url) {
  const mauUpdate = confirm(
    `Ada update aplikasi (versi ${versi}) yang perlu dipasang ulang. Unduh sekarang?`
  );
  if (mauUpdate) {
    window.open(url, '_system');
  }
}

function tampilkanStatus(pesan) {
  const el = document.getElementById('status');
  if (el) el.innerText = pesan;
}

// Wajib dipanggil tiap start, supaya CapacitorUpdater tahu
// versi yang sedang jalan ini aman (tidak di-rollback otomatis)
window.addEventListener('load', () => {
  try {
    const { CapacitorUpdater } = window.Capacitor.Plugins;
    CapacitorUpdater.notifyAppReady();
  } catch (e) {
    // aman diabaikan kalau plugin belum siap saat load pertama
  }
  cekUpdate();
});
