'use client';

import { useState } from 'react';
import { ShieldCheck, X } from 'lucide-react';
import { RentalAdminBooking } from '@/lib/types';

const INR = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
const dateTime = (v: string) => new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(v));

export default function RentalBookingActions({
  booking,
  busy,
  onComplete,
  onCancel,
}: {
  booking: RentalAdminBooking;
  busy: boolean;
  onComplete: (id: string) => void;
  onCancel: (id: string) => void;
}) {
  const [confirmCancel, setConfirmCancel] = useState(false);
  if (booking.status !== 'CONFIRMED') return <span>—</span>;

  const now = Date.now();
  const hasEnded = new Date(booking.endDate).getTime() <= now;
  const startsInFuture = new Date(booking.startDate).getTime() > now;

  return (
    <>
      <div className="booking-actions">
        {hasEnded && (
          <button className="secondary table-action" disabled={busy} onClick={() => onComplete(booking.bookingId)}>
            Complete & settle
          </button>
        )}
        {startsInFuture && (
          <button className="text-danger-btn table-action" disabled={busy} onClick={() => setConfirmCancel(true)}>
            Cancel & refund
          </button>
        )}
        {!hasEnded && !startsInFuture && <span>In progress</span>}
      </div>

      {confirmCancel && (
        <div className="drawer-overlay" onClick={() => setConfirmCancel(false)}>
          <aside className="user-drawer" onClick={event => event.stopPropagation()}>
            <div className="drawer-head">
              <div>
                <div className="eyebrow">Rental operation</div>
                <h2>Cancel and refund booking?</h2>
              </div>
              <button className="icon-btn" onClick={() => setConfirmCancel(false)}><X size={16}/></button>
            </div>
            <div className="detail-card">
              <div className="detail-top"><span className="status confirmed">CONFIRMED</span><span className="mono">{booking.bookingId}</span></div>
              <b>{booking.carName}</b>
              <div className="detail-row"><span>Customer</span><strong>{booking.userName || booking.userId}</strong></div>
              <div className="detail-row"><span>Rental window</span><strong>{dateTime(booking.startDate)} → {dateTime(booking.endDate)}</strong></div>
              <div className="detail-row"><span>Wallet refund</span><strong className="green">{INR.format(booking.total)}</strong></div>
            </div>
            <div className="drawer-note"><ShieldCheck size={15}/> This uses the existing rental refund workflow. No manual wallet or ledger adjustment is created.</div>
            <div className="drawer-actions">
              <button className="secondary" disabled={busy} onClick={() => setConfirmCancel(false)}>Keep booking</button>
              <button className="text-danger-btn" disabled={busy} onClick={() => { setConfirmCancel(false); onCancel(booking.bookingId); }}>
                {busy ? 'Processing…' : 'Confirm cancellation & refund'}
              </button>
            </div>
          </aside>
        </div>
      )}
    </>
  );
}
