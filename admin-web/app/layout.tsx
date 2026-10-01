import './globals.css';
import './admin/admin-typography.css';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'mPay',
  description: 'mPay — Secure, Simple, Smart.',
  icons: {
    icon: '/branding/mpay-logo.png',
    apple: '/branding/mpay-logo.png',
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return <html lang="en"><body>{children}</body></html>;
}
