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
  const [selectedVehicle, setSelectedVehicle] = useState<any | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [vehicleRejectReasons, setVehicleRejectReasons] = useState<Record<string,string>>({});
  const [unavailability, setUnavailability] = useState<RentalAdminVehicleUnavailability[]>([]);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [query, setQuery] = useState('');
  const [error, setError] = useState('');

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

  const q = query.trim().toLowerCase();
  const visibleVendors = vendors.filter(v =>
    (statusFilter === 'ALL' || v.status.toUpperCase() === statusFilter) &&
    (!q || v.fullName.toLowerCase().includes(q) || (v.businessName || '').toLowerCase().includes(q) || (v.mobile || '').includes(q))
  );
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

    {selectedVehicle && <div className="drawer-overlay" onClick={() => setSelectedVehicle(null)}>
      <aside className="user-drawer user-drawer-wide" onClick={e => e.stopPropagation()}>
        <div className="drawer-head">
          <div>
            <div className="eyebrow">Vehicle inspection</div>
            <h2>{selectedVehicle.name}</h2>
            <div className="drawer-subtitle">
              <span>{selectedVehicle.approvalStatus || 'PENDING_REVIEW'}</span>
              <span>{selected?.fullName || 'Vendor'}</span>
            </div>
          </div>
          <button className="icon-btn" onClick={() => setSelectedVehicle(null)}><X/></button>
        </div>

        <section className="drawer-section">
          <div className="drawer-section-title"><div><h3>Vehicle photos</h3><p>All submitted vehicle photos plus the assigned driver's photo.</p></div></div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 8 }}>
            {(selectedVehicle.imageUrl || '').split('|').filter(Boolean).map((src:string, i:number) => (
              <img key={i} src={src.startsWith('http') ? src : ((process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '') + '/' + src.replace(/^\//,''))} alt={'Vehicle photo ' + (i + 1)} style={{ width: '100%', aspectRatio: '1', objectFit: 'cover', borderRadius: 12, border: '1px solid #e5e7eb' }} />
            ))}
            {selectedVehicle.driverPhotoUrl && <div style={{ position:'relative' }}>
              <img src={selectedVehicle.driverPhotoUrl.startsWith('http') ? selectedVehicle.driverPhotoUrl : ((process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '') + '/' + selectedVehicle.driverPhotoUrl.replace(/^\//,''))} alt="Driver" style={{ width:'100%', aspectRatio:'1', objectFit:'cover', borderRadius:12, border:'2px solid #1d4ed8' }} />
              <span style={{ position:'absolute', top:6, right:6, background:'#fff', borderRadius:7, padding:'3px 6px', fontSize:11, fontWeight:700 }}>DRIVER</span>
            </div>}
          </div>
        </section>

        <section className="detail-grid detail-grid-3">
          <div><small>Vehicle ID</small><b>{selectedVehicle.id}</b></div>
          <div><small>Name</small><b>{selectedVehicle.name}</b></div>
          <div><small>Category</small><b>{selectedVehicle.category}</b></div>
          <div><small>Make</small><b>{selectedVehicle.make || '—'}</b></div>
          <div><small>Model</small><b>{selectedVehicle.model || '—'}</b></div>
          <div><small>Variant</small><b>{selectedVehicle.variant || '—'}</b></div>
          <div><small>Seats</small><b>{selectedVehicle.seats}</b></div>
          <div><small>Transmission</small><b>{selectedVehicle.transmission}</b></div>
          <div><small>Fuel</small><b>{selectedVehicle.fuelType || '—'}</b></div>
          <div><small>Manufacturing year</small><b>{selectedVehicle.manufacturingYear || '—'}</b></div>
          <div><small>Registration year</small><b>{selectedVehicle.registrationYear || '—'}</b></div>
          <div><small>Registration number</small><b>{selectedVehicle.registrationNumber || '—'}</b></div>
          <div><small>Price / day</small><b>{INR.format(Number(selectedVehicle.pricePerDay))}</b></div>
          <div><small>Pickup address</small><b>{selectedVehicle.pickupAddress || '—'}</b></div>
          <div><small>City / state</small><b>{[selectedVehicle.city, selectedVehicle.state].filter(Boolean).join(', ') || '—'}</b></div>
          <div><small>Pickup latitude</small><b>{selectedVehicle.pickupLatitude ?? '—'}</b></div>
          <div><small>Pickup longitude</small><b>{selectedVehicle.pickupLongitude ?? '—'}</b></div>
          <div><small>Pickup Place ID</small><b>{selectedVehicle.pickupPlaceId || '—'}</b></div>
          <div><small>Status</small><b>{selectedVehicle.approvalStatus || 'PENDING_REVIEW'}</b></div>
          <div><small>Rejection reason</small><b>{selectedVehicle.rejectionReason || '—'}</b></div>
          <div><small>Driver name</small><b>{selectedVehicle.driverName || '—'}</b></div>
          <div><small>Driver mobile</small><b>{selectedVehicle.driverMobile || '—'}</b></div>
          <div><small>Licence number</small><b>{selectedVehicle.driverLicenseNumber || '—'}</b></div>
          <div><small>Licence expiry</small><b>{selectedVehicle.driverLicenseExpiry ? dateTime(selectedVehicle.driverLicenseExpiry) : '—'}</b></div>
          <div><small>Driver address</small><b>{selectedVehicle.driverAddress || '—'}</b></div>
        </section>

        <div className="drawer-note"><ShieldCheck size={15}/> Approval requires a verified vendor and a valid assigned driver. Review the full submission before changing status.</div>

        {selectedVehicle.approvalStatus !== 'APPROVED' && (
          <div style={{ display:'flex', gap:8, alignItems:'center', justifyContent:'flex-end', flexWrap:'wrap' }}>
            <input value={vehicleRejectReasons[selectedVehicle.id] || ''} onChange={e => setVehicleRejectReasons(current=>({...current,[selectedVehicle.id]:e.target.value}))} placeholder="Reason to reject" style={{ minWidth: 240 }} />
            <button className="secondary" disabled={busy === 'vehicle-' + selectedVehicle.id || !(vehicleRejectReasons[selectedVehicle.id] || '').trim()} onClick={async () => { await rejectVehicle(selectedVehicle.id); setSelectedVehicle(null); }}>Reject</button>
            <button className="primary" disabled={busy === 'vehicle-' + selectedVehicle.id || selected?.status.toUpperCase() !== 'VERIFIED'} onClick={async () => { await approveVehicle(selectedVehicle.id); setSelectedVehicle(null); }}>Approve vehicle</button>
          </div>
        )}
      </aside>
    </div>}

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
                <button className="secondary" onClick={() => setSelectedVehicle(c)}>Inspect all details</button>
                {c.approvalStatus !== 'APPROVED' && <>
                  <input value={vehicleRejectReasons[c.id] || ''} onChange={e => setVehicleRejectReasons(current=>({...current,[c.id]:e.target.value}))} placeholder="Reason to reject" style={{ maxWidth: 160 }}/>
                  <button className="secondary" disabled={busy === 'vehicle-' + c.id || !(vehicleRejectReasons[c.id] || '').trim()} onClick={() => rejectVehicle(c.id)}>Reject</button>
                  <button className="primary" disabled={busy === 'vehicle-' + c.id || selected.status.toUpperCase() !== 'VERIFIED'} title={selected.status.toUpperCase() === 'VERIFIED' ? 'Approve vehicle' : 'Verify the vendor before approving vehicles'} onClick={() => approveVehicle(c.id)}>Approve vehicle</button>
                </>}
              </div>
            )}</div>}
        </section>
        <div className="drawer-note"><ShieldCheck size={15}/> Vendor verification is the first gate. A vehicle can become customer-visible only after its vendor is verified, the vehicle is approved, and its driver remains valid.</div>
      </aside>
    </div>}
  </section>;
}
