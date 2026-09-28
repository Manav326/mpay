import type { CSSProperties } from 'react';

type MpayBrandVariant = 'landing' | 'auth' | 'sidebar' | 'legal';

type MpayBrandUnitProps = {
  variant?: MpayBrandVariant;
  className?: string;
};

export default function MpayBrandUnit({
  variant = 'landing',
  className = '',
}: MpayBrandUnitProps) {
  const classes = ['mpay-brand-unit', 'mpay-brand-' + variant, className].filter(Boolean).join(' ');

  return (
    <div className={classes} aria-label="mPay — Secure, Simple, Smart">
      <span className="mpay-brand-mark">
        <img src="/branding/mpay-logo.png" alt="mPay" />
      </span>
      <span className="mpay-brand-copy">
        <strong>mPay</strong>
        <small>Secure <i aria-hidden="true">•</i> Simple <i aria-hidden="true">•</i> Smart</small>
      </span>
    </div>
  );
}
