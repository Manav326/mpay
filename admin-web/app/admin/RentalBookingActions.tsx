'use client';

import { useState } from 'react';
import { AlertTriangle, CalendarDays, CarFront, CheckCircle2, Clock3, MapPin, MessageSquareText, WalletCards, X } from 'lucide-react';
import { RentalAdminBooking } from '@/lib/types';

const INR = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
const dateTime = (v: string) => new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(v));

function bookingStatusMeta(value: string) {
  const status = String(value || 'UNKNOWN').toUpperCase();
  if (status === 'CONFIRMED') return { className: 'confirmed', label: 'CONFIRMED', icon: CheckCircle2 };
  if (status === 'COMPLETED') return { className: 'completed', label: 'COMPLETED', icon: CheckCircle2 };
  if (status === 'CANCELLED') return { className: 'cancelled', label: 'CANCELLED', icon: X };
  return { className: 'unknown', label: status.replace(/_/g, ' '), icon: Clock3 };
}

export default function RentalBookingActions({
  booking,
  busy,
  onComplete,
  onCancel,
}: {
  booking: RentalAdminBooking;
  busy: boolean;
  onComplete: (id: string) => void;
  onCancel: (id: string, reason: string) => void;
}) {
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [reason, setReason] = useState('');

  if (booking.status !== 'CONFIRMED') return <span className="booking-action-muted">—</span>;

  const now = Date.now();
  const hasEnded = new Date(booking.endDate).getTime() <= now;
  const startsInFuture = new Date(booking.startDate).getTime() > now;
  const meta = bookingStatusMeta(booking.status);
  const StatusIcon = meta.icon;
  const canSubmit = reason.trim().length >= 5 && !busy;

  function closeCancel() {
    if (busy) return;
    setConfirmCancel(false);
    setReason('');
  }

  return (
    <>
      <div className="booking-actions">
        {hasEnded && (
          <button className="secondary table-action" disabled={busy} onClick={() => onComplete(booking.bookingId)}>
            <CheckCircle2 size={13} /> Complete & settle
          </button>
        )}
        {startsInFuture && (
          <button className="text-danger-btn table-action" disabled={busy} onClick={() => setConfirmCancel(true)}>
            <X size={13} /> Cancel & refund
          </button>
        )}
        {!hasEnded && !startsInFuture && (
          <span className="booking-in-progress"><Clock3 size={13} /> In progress</span>
        )}
      </div>

      {confirmCancel && (
        <div className="rental-cancel-backdrop" onClick={closeCancel}>
          <section className="rental-cancel-dialog" role="dialog" aria-modal="true" aria-labelledby="rental-cancel-title" onClick={event => event.stopPropagation()}>
            <header className="rental-cancel-head">
              <div className="rental-cancel-icon"><AlertTriangle size={20} /></div>
              <div>
                <span className="eyebrow">Rental operation</span>
                <h2 id="rental-cancel-title">Cancel and refund booking</h2>
                <p>This action cancels the confirmed booking and starts the existing wallet refund workflow.</p>
              </div>
              <button className="icon-btn" onClick={closeCancel} disabled={busy} aria-label="Close"><X size={17}/></button>
            </header>

            <div className="rental-cancel-body">
              <div className="rental-cancel-booking-card">
                <div className="rental-cancel-booking-top">
                  <span className="rental-booking-status"><StatusIcon size={12} /> {meta.label}</span>
                  <span className="mono">{booking.bookingId}</span>
                </div>
                <div className="rental-cancel-car">
                  <div className="rental-cancel-car-icon"><CarFront size={17} /></div>
                  <div><b>{booking.carName}</b><span>{booking.vendorName || 'Vendor'}</span></div>
                </div>
                <div className="rental-cancel-facts">
                  <span><CalendarDays size={13} /> {dateTime(booking.startDate)} → {dateTime(booking.endDate)}</span>
                  <span><MapPin size={13} /> {booking.pickup} → {booking.drop}</span>
                  <span><WalletCards size={13} /> Refund {INR.format(booking.total)}</span>
                </div>
              </div>

              <label className="rental-cancel-reason">
                <span><MessageSquareText size={14} /> Reason for cancellation <b>Required</b></span>
                <textarea
                  value={reason}
                  onChange={event => setReason(event.target.value)}
                  maxLength={500}
                  rows={4}
                  placeholder="Enter the operational reason for cancelling this booking…"
                  autoFocus
                  disabled={busy}
                />
                <small>{reason.length}/500 · This reason is recorded with the booking cancellation.</small>
              </label>

              <div className="rental-cancel-note">
                <AlertTriangle size={14} />
                <span>The customer will receive the booking amount back through the existing rental refund workflow. No manual wallet or ledger adjustment is created.</span>
              </div>
            </div>

            <footer className="rental-cancel-footer">
              <button className="secondary" disabled={busy} onClick={closeCancel}>Keep booking</button>
              <button className="danger-confirm-btn" disabled={!canSubmit} onClick={() => { setConfirmCancel(false); const nextReason = reason.trim(); setReason(''); onCancel(booking.bookingId, nextReason); }}>
                <X size={14} /> {busy ? 'Processing…' : 'Confirm cancellation & refund'}
              </button>
            </footer>
          </section>
        </div>
      )}
    </>
  );
}
