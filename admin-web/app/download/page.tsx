import type { Metadata } from 'next';
import Link from 'next/link';
import AndroidDownloadCard from '../components/AndroidDownloadCard';
import styles from './DownloadPage.module.css';

export const metadata: Metadata = {
  title: 'Download mPay for Android',
  description: 'Get the official signed mPay Android app. Check the latest release, version details and SHA-256 checksum.',
  alternates: { canonical: '/download' },
};

export default function DownloadPage() {
  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <Link href="/" className={styles.backLink}>← Back to mPay</Link>
        <span className={styles.brand}>mPay <span>OFFICIAL APP</span></span>
      </header>
      <section className={styles.intro}>
        <span className={styles.kicker}>OFFICIAL ANDROID RELEASES</span>
        <h1>Take mPay with you.</h1>
        <p>Download the official signed Android app for mobile recharge, wallet activity and everyday services. This page always points to the latest stable release.</p>
      </section>
      <AndroidDownloadCard />
      <section className={styles.install}>
        <div>
          <span className={styles.kicker}>GETTING STARTED</span>
          <h2>Install the official mPay app</h2>
          <p>Use the APK above for a direct Android installation. The AAB attached to the release is intended for Google Play publishing and is not the customer install file.</p>
        </div>
        <ol>
          <li><span>01</span><div><strong>Download the APK</strong><p>Use the official download button on this page.</p></div></li>
          <li><span>02</span><div><strong>Open the downloaded file</strong><p>Android may ask you to allow installation from the browser or file manager you used.</p></div></li>
          <li><span>03</span><div><strong>Review and install</strong><p>Confirm the package is mPay, then open the app and sign in or create an account.</p></div></li>
        </ol>
        <div className={styles.securityNote}>
          <strong>Verify your download</strong>
          <p>Each release includes a SHA-256 checksum and versioned release notes. Use the checksum link beside the download details to verify the APK before installing it.</p>
        </div>
      </section>
      <footer className={styles.footer}>
        <Link href="/">mPay home</Link>
        <Link href="/privacy-policy">Privacy policy</Link>
        <Link href="/delete-account">Account deletion</Link>
      </footer>
    </main>
  );
}