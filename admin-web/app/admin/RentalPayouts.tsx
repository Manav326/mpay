'use client';

import { useEffect, useState } from 'react';
import { ArrowDownLeft, CheckCircle2, Clock3, RefreshCw } from 'lucide-react';
import { getRentalAdminPayouts } from '@/lib/api';
import { RentalAdminPayout } from '@/lib/types';

const INR = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
const dt = (v: string) => new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(v));

function statusClass(value?: string) {
  return 'status ' + String(value || 'UNKNOWN').toLowerCase().replace(/[^a-z0-9]+/g, '-');
}

export default function RentalPayouts({ onNotice }: { onNotice: (message: string) => void }) {
  const [items, setItems] = useState<RentalAdminPayout[]>([]);
  const [status, setStatus] = useState('ALL');
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const result = await getRentalAdminPayouts(page, 25, status);
      setItems(result.items);
      setHasNext(result.hasNext);
    } catch (err: any) {
      onNotice(err.message || 'Unable to load rental payout operations.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // The active page/filter intentionally controls the request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, status]);

  useEffect(() => setPage(0), [status]);

  return (
    <section className="panel rental-payout-panel">
      <div className="panel-head wrap">
        <div><h2>Vendor settlement ledger</h2><p>Read-only view of payout records created by the rental settlement workflow.</p></div>
        <div className="filters"><select value={status} onChange={e => setStatus(e.target.value)}><option>ALL</option><option>PENDING</option><option>PAID</option><option>FAILED</option></select><button className="secondary" disabled={loading} onClick={load}><RefreshCw size={14}/> Refresh</button></div>
      </div>
      {loading && items.length === 0 ? <div className="empty-state">Loading payout ledger…</div> :
        items.length === 0 ? <div className="empty-state">No payout records match this filter.</div> :
        <div className="history-table-wrap"><table className="history-table financial-table"><thead><tr><th>Payout</th><th>Vendor</th><th>Booking</th><th>Gross</th><th>Platform fee</th><th>Vendor net</th><th>Status</th><th>Settlement</th></tr></thead><tbody>
          {items.map((p: RentalAdminPayout) => <tr key={p.payoutId}>
            <td><b className="mono">{p.payoutId}</b><span>{dt(p.createdAt)}</span></td>
            <td><b>{p.vendorName}</b><span>Vendor ID {p.vendorId}</span></td>
            <td><span className="mono">{p.bookingId}</span></td>
            <td><b>{INR.format(p.grossAmount)}</b></td>
            <td><b>{INR.format(p.platformFeeAmount)}</b><span>{p.platformFeePercent}%</span></td>
            <td className="green"><b>{INR.format(p.vendorNetAmount)}</b></td>
            <td><span className={statusClass(p.status)}>{p.status}</span>{p.failureReason && <span>{p.failureReason}</span>}</td>
            <td>{p.paidAt ? <span><CheckCircle2 size={13}/> {dt(p.paidAt)}</span> : <span><Clock3 size={13}/> Awaiting settlement</span>}</td>
          </tr>)}
        </tbody></table></div>}
      <div className="panel-head financial-pagination"><span>Page {page + 1}</span><div className="filters"><button className="secondary" disabled={page === 0 || loading} onClick={() => setPage(p => Math.max(0, p - 1))}>Previous</button><button className="secondary" disabled={!hasNext || loading} onClick={() => setPage(p => p + 1)}>Next</button></div></div>
      <div className="drawer-note"><ArrowDownLeft size={15}/> Payout status is governed by the rental settlement service. This screen does not create or force payouts.</div>
    </section>
  );
}
