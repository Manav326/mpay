'use client';

import { useEffect, useState } from 'react';
import { AlertCircle, CheckCircle2, Download, LoaderCircle, ShieldCheck, Smartphone } from 'lucide-react';
import styles from './AndroidDownloadCard.module.css';

type ReleaseMetadata = {
  available: boolean;
  version?: string;
  sizeBytes?: number;
  publishedAt?: string;
  message?: string;
};

type DownloadStatus = 'loading' | 'ready' | 'unavailable' | 'downloading' | 'complete' | 'error';

function formatBytes(bytes?: number) {
  if (!bytes || !Number.isFinite(bytes) || bytes <= 0) return 'Size shown when available';
  const mb = bytes / (1024 * 1024);
  return mb >= 100 ? `${Math.round(mb)} MB` : `${mb.toFixed(1)} MB`;
}

export default function AndroidDownloadCard() {
  const [metadata, setMetadata] = useState<ReleaseMetadata | null>(null);
  const [status, setStatus] = useState<DownloadStatus>('loading');
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    fetch('/api/download/android/metadata', { cache: 'no-store', signal: controller.signal })
      .then(async response => {
        if (!response.ok) throw new Error('Release information is temporarily unavailable.');
        return response.json() as Promise<ReleaseMetadata>;
      })
      .then(release => {
        setMetadata(release);
        setStatus(release.available ? 'ready' : 'unavailable');
      })
      .catch(reason => {
        if (controller.signal.aborted) return;
        setMetadata({ available: false });
        setStatus('unavailable');
        setError(reason instanceof Error ? reason.message : 'Release information is temporarily unavailable.');
      });
    return () => controller.abort();
  }, []);

  async function downloadApk() {
    if (status === 'downloading' || !metadata?.available) return;
    setStatus('downloading');
    setProgress(0);
    setError('');

    try {
      const response = await fetch('/api/download/android', { cache: 'no-store' });
      if (!response.ok || !response.body) {
        throw new Error('The Android download is not available right now. Please try again.');
      }

      const totalBytes = Number(response.headers.get('content-length')) || metadata.sizeBytes || 0;
      const reader = response.body.getReader();
      const chunks: Uint8Array[] = [];
      let receivedBytes = 0;

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        if (value) {
          chunks.push(value);
          receivedBytes += value.byteLength;
          if (totalBytes > 0) {
            setProgress(Math.min(99, Math.round((receivedBytes / totalBytes) * 100)));
          }
        }
      }

      if (receivedBytes === 0) throw new Error('The downloaded file was empty. Please try again.');

      const blobParts = chunks.map(chunk => {
        const copy = new Uint8Array(chunk.byteLength);
        copy.set(chunk);
        return copy.buffer;
      });
      const apk = new Blob(blobParts, { type: 'application/vnd.android.package-archive' });
      const objectUrl = URL.createObjectURL(apk);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = 'mpay-android.apk';
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);

      setProgress(100);
      setStatus('complete');
    } catch (reason) {
      setStatus('error');
      setError(reason instanceof Error ? reason.message : 'Download failed. Please try again.');
    }
  }

  const isDownloading = status === 'downloading';
  const isReady = status === 'ready' || status === 'complete' || status === 'error';
  const label = isDownloading
    ? 'Downloading mPay…'
    : status === 'complete'
      ? 'Download again'
      : status === 'error'
        ? 'Try again'
        : 'Download for Android';

  return (
    <section className={styles.section} aria-labelledby="android-download-title">
      <div className={styles.card}>
        <div className={styles.identity}>
          <div className={styles.appIcon}><img src="/branding/mpay-logo.png" alt="mPay" /></div>
          <div className={styles.identityCopy}>
            <span className={styles.eyebrow}>THE mPay APP</span>
            <h2 id="android-download-title">Your payments, right in your pocket.</h2>
            <p>Get the mPay Android app for a smoother way to recharge and manage your wallet.</p>
            <div className={styles.trust}><ShieldCheck size={15} /> Official mPay release · Secure signed APK</div>
          </div>
        </div>

        <div className={styles.downloadPanel}>
          <div className={styles.releaseLine}>
            <span className={styles.platform}><Smartphone size={14} /> Android</span>
            <span>{metadata?.version ? `Version ${metadata.version}` : 'Latest version'}</span>
            <span>{formatBytes(metadata?.sizeBytes)}</span>
          </div>

          <button
            className={styles.downloadButton}
            type="button"
            onClick={() => void downloadApk()}
            disabled={!isReady || isDownloading || !metadata?.available}
          >
            {isDownloading ? <LoaderCircle className={styles.spinner} size={18} /> : status === 'complete' ? <CheckCircle2 size={18} /> : <Download size={18} />}
            {status === 'loading' ? 'Checking latest release…' : status === 'unavailable' ? 'Android app coming soon' : label}
          </button>

          {isDownloading || status === 'complete' ? (
            <div className={styles.progressArea} aria-live="polite">
              <div className={styles.progressMeta}>
                <span>{status === 'complete' ? 'Download started' : 'Downloading APK'}</span>
                <strong>{progress}%</strong>
              </div>
              <div className={styles.progressTrack} role="progressbar" aria-label="Android APK download progress" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress}>
                <span style={{ width: `${progress}%` }} />
              </div>
            </div>
          ) : status === 'error' ? (
            <p className={styles.messageError}><AlertCircle size={14} /> {error || 'Download failed. Please try again.'}</p>
          ) : status === 'unavailable' ? (
            <p className={styles.message}>{error || metadata?.message || 'The first signed Android release is being prepared.'}</p>
          ) : (
            <p className={styles.message}>The latest signed APK is checked automatically before download.</p>
          )}
        </div>
      </div>
    </section>
  );
}
