'use client';

import { useState } from 'react';
import { LockKeyhole, ShieldCheck } from 'lucide-react';
import { updateUserStatus } from '@/lib/api';
import { UserDetail } from '@/lib/types';

export default function AccountStatusControl({
  user,
  onChanged,
}: {
  user: UserDetail;
  onChanged: () => Promise<void>;
}) {
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState<boolean | null>(null);
  const isActive = String(user.status || '').toUpperCase() === 'ACTIVE';

  async function save() {
    if (confirm === null) return;
    setBusy(true);
    try {
      await updateUserStatus(user.id, confirm);
      await onChanged();
      setConfirm(null);
    } finally {
      setBusy(false);
    }
  }

  if (String(user.role || '').toUpperCase() === 'ADMIN') return null;

  return (
    <>
      <div className="account-status-controls">
        <div>
          <b>Account access</b>
          <span>{isActive ? 'The account can sign in and use enabled services.' : 'The account is blocked from normal platform use.'}</span>
        </div>
        <button
          className={isActive ? 'status-toggle off' : 'status-toggle on'}
          onClick={() => setConfirm(!isActive)}
          disabled={busy}
        >
          <LockKeyhole size={13}/>
          {isActive ? 'Block account' : 'Activate account'}
        </button>
      </div>

      {confirm !== null && (
        <div className="status-confirm">
          <div>
            <b>{confirm ? 'Activate this account?' : 'Block this account?'}</b>
            <span>
              {confirm
                ? 'The user will regain access to the platform.'
                : 'The user will no longer be able to use the platform. Existing ledger history remains unchanged.'}
            </span>
          </div>
          <div className="filters">
            <button className="secondary" disabled={busy} onClick={() => setConfirm(null)}>Cancel</button>
            <button className={confirm ? 'primary' : 'text-danger-btn'} disabled={busy} onClick={save}>
              {busy ? 'Saving…' : confirm ? 'Confirm activation' : 'Confirm block'}
            </button>
          </div>
        </div>
      )}
      <div className="drawer-note"><ShieldCheck size={15}/> Account status changes do not modify wallet balances or ledger history.</div>
    </>
  );
}
