'use client';

import { useState } from 'react';
import { ShieldAlert, ShieldCheck } from 'lucide-react';
import { updateUserMobileVerification } from '@/lib/api';
import { UserDetail } from '@/lib/types';

export default function MobileVerificationControl({
  user,
  onChanged,
}: {
  user: UserDetail;
  onChanged: () => Promise<void>;
}) {
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState<boolean | null>(null);
  const [reason, setReason] = useState('');

  const isVerified = !!user.mobileVerified;

  async function save() {
    if (confirm === null || !reason.trim()) return;
    setBusy(true);
    try {
      await updateUserMobileVerification(user.publicUserId, confirm, reason.trim());
      await onChanged();
      setConfirm(null);
      setReason('');
    } catch (error: any) {
      window.alert(error?.message || 'Unable to update mobile verification.');
    } finally {
      setBusy(false);
    }
  }

  if (String(user.role || '').toUpperCase() !== 'CLIENT') return null;

  return (
    <>
      <div className="account-state-actions mobile-verification-control">
        <div>
          <b>{isVerified ? 'Mobile number is verified' : 'Mobile number is not verified'}</b>
          <span>
            {isVerified
              ? 'This client is currently treated as mobile-verified by verification-gated services.'
              : 'This client is not currently eligible for verification-gated services such as client-network assignment.'}
          </span>
          {user.mobileVerifiedAt && <small>Verified on {new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(user.mobileVerifiedAt))}</small>}
        </div>
        <button
          className={isVerified ? 'status-toggle off' : 'status-toggle on'}
          onClick={() => { setConfirm(!isVerified); setReason(''); }}
          disabled={busy}
        >
          {isVerified ? <ShieldAlert size={13}/> : <ShieldCheck size={13}/>}
          {isVerified ? 'Mark not verified' : 'Mark verified'}
        </button>
      </div>

      {confirm !== null && (
        <div className="status-confirm mobile-verification-confirm">
          <div>
            <b>{confirm ? 'Mark this mobile number as verified?' : 'Mark this mobile number as not verified?'}</b>
            <span>
              This is an administrative identity decision. Existing wallet, recharge and network history will remain unchanged.
            </span>
          </div>
          <label>
            Reason
            <textarea
              className="admin-textarea"
              maxLength={1000}
              rows={3}
              value={reason}
              onChange={e => setReason(e.target.value)}
              placeholder="Explain why this verification status is being changed."
            />
          </label>
          <div className="filters">
            <button className="secondary" disabled={busy} onClick={() => { setConfirm(null); setReason(''); }}>Cancel</button>
            <button
              className={confirm ? 'primary' : 'text-danger-btn'}
              disabled={busy || !reason.trim()}
              onClick={() => void save()}
            >
              {busy ? 'Saving…' : confirm ? 'Confirm verification' : 'Confirm removal'}
            </button>
          </div>
        </div>
      )}
    </>
  );
}
