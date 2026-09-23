'use client';

import { useEffect, useState } from 'react';
import { CarFront, ChevronRight, ShieldCheck, X } from 'lucide-react';
import { approveRentalVehicle, approveRentalVendor, getRentalAdminVendorVehicles, getRentalAdminVendors, getRentalAdminVehicleUnavailability, rejectRentalVendor, rejectRentalVehicle } from '@/lib/api';
import { RentalAdminVendor, RentalAdminVehicleUnavailability } from '@/lib/types';

const INR = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const dateTime = (v: string) => new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(v));

export default function RentalVendorReview() {
  const [vendors, setVendors] = useState<RentalAdminVendor[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<RentalAdminVendor | null>(null);
  const [vehicles, setVehicles] = useState<any[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [vehicleRejectReason, setVehicleRejectReason] = useState('');
  const [unavailability, setUnavailability] = useState<RentalAdminVehicleUnavailability[]>([]);

  async function refresh() {
    setLoading(true);
    setError('');
    try {
      const [vendorResult, blackoutResult] = await Promise.allSettled([
        getRentalAdminVendors(),
        getRentalAdminVehicleUnavailability(),
      ]);
      if (vendorResult.status === 'fulfilled') setVendors(vendorResult.value);
      if (blackoutResult.status === 'fulfilled') setUnavailability(blackoutResult.value);
      if (vendorResult.status === 'rejected' && blackoutResult.status === 'rejected') setError('Unable to load rental review data.');
    } catch (err:any) {
      setError(err.message || 'Unable to load rental review data.');
    } finally { setLoading(false); }
  }
  useEffect(() => { refresh().catch(() => setLoading(false)); }, []);

  async function openVendor(v: RentalAdminVendor) {
    setSelected(v);
    setBusy('vendor-view-' + v.vendorId);
    setError('');
    try { setVehicles(await getRentalAdminVendorVehicles(v.vendorId)); }
    catch (err:any) { setError(err.message || 'Unable to load vendor vehicles.'); setVehicles([]); }
    finally { setBusy(null); }
  }

  async function approveVendor(v: RentalAdminVendor) {
    setBusy('vendor-' + v.vendorId);
    try { await approveRentalVendor(v.vendorId); await refresh(); if (selected?.vendorId === v.vendorId) setSelected({ ...v, status: 'VERIFIED', rejectionReason: null }); }
    catch (err:any) { setError(err.message || 'Unable to approve vendor.'); }
    finally { setBusy(null); }
  }

  async function rejectVendor(v: RentalAdminVendor) {
    if (!rejectReason.trim()) return;
    setBusy('vendor-' + v.vendorId);
    try { await rejectRentalVendor(v.vendorId, rejectReason); setRejectReason(''); await refresh(); if (selected?.vendorId === v.vendorId) setSelected({ ...v, status: 'REJECTED', rejectionReason: rejectReason }); }
    catch (err:any) { setError(err.message || 'Unable to reject vendor.'); }
    finally { setBusy(null); }
  }

  async function approveVehicle(id: string) {
    setBusy('vehicle-' + id);
    try { await approveRentalVehicle(id); if (selected) setVehicles(await getRentalAdminVendorVehicles(selected.vendorId)); }
    catch (err:any) { setError(err.message || 'Unable to approve vehicle.'); }
    finally { setBusy(null); }
  }
  async function rejectVehicle(id: string) {
    const reason = vehicleRejectReasons[id]?.trim() || '';
    if (!reason) return;
    setBusy('vehicle-' + id);
    try { await rejectRentalVehicle(id, reason); setVehicleRejectReasons(current=>({...current,[id]:''})); if (selected) setVehicles(await getRentalAdminVendorVehicles(selected.vendorId)); }
    catch (err:any) { setError(err.message || 'Unable to reject vehicle.'); }
    finally { setBusy(null); }
  }

  return <section className="panel" style={{ marginTop: 16 }}>
    <div className="panel-head wrap">
      <div><h2>Rental vendor applications</h2><p>Review the vendor data submitted from the customer app. Only approved vehicles enter the marketplace.</p></div>
      <button className="secondary" onClick={() => refresh()}>Refresh</button>
    </div>
    {loading ? <div className="loading">Loading rental applications…</div> :
      visibleVendors.length === 0 ? <div className="empty-state">No rental vendor applications submitted yet.</div> :
      <div className="vendor-list">{visibleVendors.map(v =>
        <div className="vendor-row" key={v.vendorId} style={{ alignItems: 'flex-start' }}>
          <div className="vendor-icon"><CarFront size={18}/></div>
          <div className="vendor-main" style={{ flex: 1 }}>
            <b>{v.fullName}{v.businessName ? ' · ' + v.businessName : ''}</b>
            <span>{v.vendorType} · {v.mobile || 'No mobile'} · {v.email || 'No email'}</span>
            <span>{v.city}, {v.state} · Submitted {dateTime(v.submittedAt)}</span>
            <span>Vehicles: {v.vehicleCount} · PAN: {v.panNumber || 'Not provided'} · Status: {v.status}</span>
            {v.rejectionReason && <span>Review note: {v.rejectionReason}</span>}
          </div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
            <button className="secondary" onClick={() => openVendor(v)}>View submission <ChevronRight size={15}/></button>
            {(v.status === 'PENDING' || v.status === 'REJECTED') && <button className="primary" disabled={busy === 'vendor-' + v.vendorId} onClick={() => approveVendor(v)}>Approve</button>}
            {v.status === 'PENDING' && <>
              <input value={selected?.vendorId === v.vendorId ? rejectReason : ''} onChange={e => { setSelected(v); setRejectReason(e.target.value); }} placeholder="Reason to reject" style={{ maxWidth: 180 }}/>
              <button className="secondary" disabled={busy === 'vendor-' + v.vendorId || !rejectReason.trim()} onClick={() => rejectVendor(v)}>Reject</button>
            </>}
          </div>
        </div>
      )}</div>
    }

    <section className="drawer-section">
      <div className="drawer-section-title">
        <div>
          <h3>Vendor vehicle off-market periods</h3>
          <p>Active periods requested by verified vendors are excluded from customer availability search.</p>
        </div>
      </div>
      {unavailability.length === 0 ? (
        <div className="empty-state">No active vehicle off-market periods.</div>
      ) : (
        <div className="vendor-list">
          {unavailability.map(row =>
            <div className="vendor-row" key={row.id}>
              <div className="vendor-icon"><CarFront size={18}/></div>
              <div className="vendor-main">
                <b>{row.carName}</b>
                <span>{dateTime(row.startDate)} → {dateTime(row.endDate)}</span>
                <span>Reason: {row.reasonLabel}</span>
                {row.reasonNote && <span>Vendor note: {row.reasonNote}</span>}
                <span>Status: {row.status}</span>
              </div>
            </div>
          )}
        </div>
      )}
    </section>

    {selected && <div className="drawer-overlay" onClick={() => setSelected(null)}>
      <aside className="user-drawer user-drawer-wide" onClick={e => e.stopPropagation()}>
        <div className="drawer-head">
          <div><div className="eyebrow">Rental vendor submission</div><h2>{selected.fullName}</h2><div className="drawer-subtitle"><span>{selected.status}</span><span>{selected.vendorType}</span></div></div>
          <button className="icon-btn" onClick={() => setSelected(null)}><X/></button>
        </div>

        <section className="detail-grid detail-grid-3">
          <div><small>Business / fleet</small><b>{selected.businessName || '—'}</b></div>
          <div><small>Mobile</small><b>{selected.mobile || '—'}</b></div>
          <div><small>Email</small><b>{selected.email || '—'}</b></div>
          <div><small>Address</small><b>{selected.address}</b></div>
          <div><small>Location</small><b>{selected.city}, {selected.state} {selected.pinCode}</b></div>
          <div><small>PAN</small><b>{selected.panNumber || '—'}</b></div>
          <div><small>Payout UPI</small><b>{selected.payoutUpiId || '—'}</b></div>
          <div><small>Bank account</small><b>{selected.bankAccountNumber || '—'}</b></div>
          <div><small>IFSC</small><b>{selected.bankIfsc || '—'}</b></div>
          <div><small>Submitted</small><b>{dateTime(selected.submittedAt)}</b></div>
        </section>

        <section className="drawer-section">
          <div className="drawer-section-title"><div><h3>Submitted vehicles</h3><p>Vehicles remain unavailable to customers until approved.</p></div></div>
          {vehicles.length === 0 ? <div className="empty-state">No vehicles submitted by this vendor yet.</div> :
            <div className="vendor-list">{vehicles.map(c =>
              <div className="vendor-row" key={c.id}>
                <div className="vendor-icon"><CarFront size={18}/></div>
                <div className="vendor-main">
                  <b>{c.name}</b>
                  <span>{c.make || ''} {c.model || ''} · {c.category} · {c.seats} seats · {c.transmission}</span>
                  <span>{c.city || '—'} · {INR.format(Number(c.pricePerDay))}/day · Driver: {c.driverName}</span>
                  <span>Status: {c.approvalStatus || 'PENDING_REVIEW'}</span>
                </div>
                {c.approvalStatus !== 'APPROVED' && <>
                  <input value={vehicleRejectReason} onChange={e => setVehicleRejectReason(e.target.value)} placeholder="Reason to reject" style={{ maxWidth: 160 }}/>
                  <button className="secondary" disabled={busy === 'vehicle-' + c.id || !vehicleRejectReason.trim()} onClick={() => rejectVehicle(c.id)}>Reject</button>
                  <button className="primary" disabled={busy === 'vehicle-' + c.id} onClick={() => approveVehicle(c.id)}>Approve vehicle</button>
                </>}
              </div>
            )}</div>}
        </section>
        <div className="drawer-note"><ShieldCheck size={15}/> Approval changes the rental state only; unapproved vehicles are not visible to customers.</div>
      </aside>
    </div>}
  </section>;
}
