'use client';

import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import {
  AlertCircle,
  BadgeCheck,
  Building2,
  CalendarDays,
  CarFront,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock3,
  CreditCard,
  Eye,
  FileCheck2,
  FileText,
  Filter,
  History,
  IndianRupee,
  Mail,
  MapPin,
  Phone,
  Search,
  ShieldCheck,
  UserCheck,
  UserRound,
  Users,
  X,
  XCircle,
} from 'lucide-react';
import {
  approveRentalVehicle,
  approveRentalVendor,
  getRentalAdminVendorVehicles,
  getRentalAdminVendors,
  getRentalAdminVehicleUnavailability,
  rejectRentalVendor,
  rejectRentalVehicle,
} from '@/lib/api';
import { RentalAdminVendor, RentalAdminVehicleUnavailability } from '@/lib/types';

const API_BASE = (process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const INR = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const dateTime = (v?: string) => v ? new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(v)) : '—';
const dateOnly = (v?: string) => v ? new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(new Date(v)) : '—';

function cleanText(value: unknown, fallback = '—') {
  const text = String(value ?? '')
    .replace(/\\+(?:r)?n/g, ' ')
    .replace(/\\+r/g, ' ')
    .replace(/\r?\n/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return text || fallback;
}

function normalizedVehicleStatus(value?: string) {
  return cleanText(value, 'PENDING_REVIEW').toUpperCase();
}

function isPendingVehicle(value?: string) {
  const status = normalizedVehicleStatus(value);
  return status === 'PENDING_REVIEW' || status === 'PENDING';
}

function statusMeta(value?: string) {
  const status = cleanText(value, 'UNKNOWN').toUpperCase();
  if (status === 'VERIFIED' || status === 'APPROVED' || status === 'ACTIVE') {
    return { label: status, className: 'approved', icon: CheckCircle2 };
  }
  if (status === 'PENDING' || status === 'PENDING_REVIEW') {
    return { label: status === 'PENDING_REVIEW' ? 'PENDING REVIEW' : 'PENDING', className: 'pending', icon: Clock3 };
  }
  if (status === 'REJECTED') return { label: 'REJECTED', className: 'rejected', icon: XCircle };
  if (status === 'CANCELLED') return { label: 'CANCELLED', className: 'cancelled', icon: AlertCircle };
  return { label: status.replace(/_/g, ' '), className: 'neutral', icon: AlertCircle };
}

function StatusBadge({ value }: { value?: string }) {
  const meta = statusMeta(value);
  const Icon = meta.icon;
  return <span className={`rental-status-badge ${meta.className}`}><Icon size={13} />{meta.label}</span>;
}

function imageUrl(value?: string | null, variant: 'thumb' | 'large' = 'thumb') {
  const raw = String(value || '').trim();
  if (!raw) return '';
  if (/^https?:\/\//i.test(raw)) return raw;
  const url = raw.startsWith('/api/') ? API_BASE + raw : API_BASE + '/api/v1/car-rental/photos/' + raw.replace(/^\/+/, '');
  return url + (url.includes('?') ? '&' : '?') + 'variant=' + variant;
}

function PhotoTile({ src, alt, label, className = '', variant = 'thumb', priority = false }: { src?: string | null; alt: string; label?: string; className?: string; variant?: 'thumb' | 'large'; priority?: boolean }) {
  const [failed, setFailed] = useState(false);
  const resolved = imageUrl(src, variant);
  return (
    <div className={`rental-photo-tile ${className}`}>
      {resolved && !failed ? <img src={resolved} alt={alt} loading={priority ? 'eager' : 'lazy'} decoding="async" fetchPriority={priority ? 'high' : 'auto'} onError={() => setFailed(true)} /> : <div className="rental-photo-fallback"><CarFront size={22} /><span>{label || 'Photo unavailable'}</span></div>}
      {label && !failed && <span className="rental-photo-label">{label}</span>}
    </div>
  );
}

function vehiclePhotos(vehicle: any): string[] {
  return String(vehicle?.imageUrl || '')
    .replace(/\\n/g, '|')
    .replace(/\r?\n/g, '|')
    .split('|')
    .map((value) => value.trim())
    .filter(Boolean)
    .slice(0, 4)
    .concat(['', '', '', ''])
    .slice(0, 4);
}

type ModalState =
  | { kind: 'vendor-detail'; vendor: RentalAdminVendor }
  | { kind: 'submissions'; vendor: RentalAdminVendor }
  | { kind: 'inspection'; vendor: RentalAdminVendor; vehicle: any }
  | null;

export default function RentalVendorReview() {
  const [vendors, setVendors] = useState<RentalAdminVendor[]>([]);
  const [unavailability, setUnavailability] = useState<RentalAdminVehicleUnavailability[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [vendorQuery, setVendorQuery] = useState('');
  const [vendorStatusFilter, setVendorStatusFilter] = useState('ALL');
  const [offMarketQuery, setOffMarketQuery] = useState('');
  const [offMarketReason, setOffMarketReason] = useState('ALL');
  const [modal, setModal] = useState<ModalState>(null);
  const [modalVehicles, setModalVehicles] = useState<any[]>([]);
  const [submissionQuery, setSubmissionQuery] = useState('');
  const [submissionStatus, setSubmissionStatus] = useState('ALL');
  const [submissionLoading, setSubmissionLoading] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [vehicleRejectReason, setVehicleRejectReason] = useState('');
  const [inspectionConfirmed, setInspectionConfirmed] = useState(false);
  const [inspectionPhotoIndex, setInspectionPhotoIndex] = useState(0);

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
      if (vendorResult.status === 'rejected') setError('Unable to load rental partner applications.');
      if (blackoutResult.status === 'rejected') setError((current) => current || 'Unable to load vehicle off-market periods.');
    } catch (err: any) {
      setError(err.message || 'Unable to load rental review data.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void refresh(); }, []);

  async function loadSubmissions(vendor: RentalAdminVendor) {
    setSubmissionLoading(true);
    setError('');
    try {
      setModalVehicles(await getRentalAdminVendorVehicles(vendor.vendorId));
    } catch (err: any) {
      setError(err.message || 'Unable to load submitted vehicles.');
      setModalVehicles([]);
    } finally {
      setSubmissionLoading(false);
    }
  }

  async function openSubmissions(vendor: RentalAdminVendor) {
    setSubmissionQuery('');
    setSubmissionStatus('ALL');
    setInspectionConfirmed(false);
    setVehicleRejectReason('');
    setInspectionPhotoIndex(0);
    setModal({ kind: 'submissions', vendor });
    await loadSubmissions(vendor);
  }

  function openVendorDetail(vendor: RentalAdminVendor) {
    setRejectReason('');
    setInspectionConfirmed(false);
    setModal({ kind: 'vendor-detail', vendor });
  }

  function inspectVehicle(vendor: RentalAdminVendor, vehicle: any) {
    setVehicleRejectReason('');
    setInspectionConfirmed(false);
    setInspectionPhotoIndex(0);
    setModal({ kind: 'inspection', vendor, vehicle });
  }

  function closeModal() {
    setModal(null);
    setInspectionConfirmed(false);
    setRejectReason('');
    setVehicleRejectReason('');
  }

  function backToSubmissions() {
    if (modal?.kind === 'inspection') {
      setVehicleRejectReason('');
      setInspectionConfirmed(false);
      setModal({ kind: 'submissions', vendor: modal.vendor });
    } else {
      closeModal();
    }
  }

  async function approveVendor(vendor: RentalAdminVendor) {
    if (!inspectionConfirmed) return;
    setBusy('vendor-approve-' + vendor.vendorId);
    try {
      await approveRentalVendor(vendor.vendorId);
      await refresh();
      if (modal?.kind === 'vendor-detail') setModal({ kind: 'vendor-detail', vendor: { ...vendor, status: 'VERIFIED', rejectionReason: null } });
    } catch (err: any) {
      setError(err.message || 'Unable to approve vendor.');
    } finally {
      setBusy(null);
    }
  }

  async function rejectVendor(vendor: RentalAdminVendor) {
    if (!inspectionConfirmed || !rejectReason.trim()) return;
    setBusy('vendor-reject-' + vendor.vendorId);
    try {
      await rejectRentalVendor(vendor.vendorId, rejectReason.trim());
      await refresh();
      if (modal?.kind === 'vendor-detail') setModal({ kind: 'vendor-detail', vendor: { ...vendor, status: 'REJECTED', rejectionReason: rejectReason.trim() } });
      setRejectReason('');
    } catch (err: any) {
      setError(err.message || 'Unable to reject vendor.');
    } finally {
      setBusy(null);
    }
  }

  async function approveVehicle(vendor: RentalAdminVendor, vehicle: any) {
    if (!inspectionConfirmed) return;
    setBusy('vehicle-approve-' + vehicle.id);
    try {
      await approveRentalVehicle(String(vehicle.id));
      const next = await getRentalAdminVendorVehicles(vendor.vendorId);
      setModal({ kind: 'submissions', vendor });
      setModalVehicles(next);
      setInspectionConfirmed(false);
      setVehicleRejectReason('');
    } catch (err: any) {
      setError(err.message || 'Unable to approve vehicle.');
    } finally {
      setBusy(null);
    }
  }

  async function rejectVehicle(vendor: RentalAdminVendor, vehicle: any) {
    if (!inspectionConfirmed || !vehicleRejectReason.trim()) return;
    setBusy('vehicle-reject-' + vehicle.id);
    try {
      await rejectRentalVehicle(String(vehicle.id), vehicleRejectReason.trim());
      const next = await getRentalAdminVendorVehicles(vendor.vendorId);
      setModal({ kind: 'submissions', vendor });
      setModalVehicles(next);
      setInspectionConfirmed(false);
      setVehicleRejectReason('');
    } catch (err: any) {
      setError(err.message || 'Unable to reject vehicle.');
    } finally {
      setBusy(null);
    }
  }

  const visibleVendors = useMemo(() => {
    const q = vendorQuery.trim().toLowerCase();
    return vendors.filter((vendor) => {
      const matchesStatus = vendorStatusFilter === 'ALL' || String(vendor.status).toUpperCase() === vendorStatusFilter;
      const haystack = [vendor.fullName, vendor.businessName, vendor.mobile, vendor.email, vendor.city, vendor.state, vendor.panNumber]
        .map((value) => cleanText(value, '').toLowerCase())
        .join(' ');
      return matchesStatus && (!q || haystack.includes(q));
    });
  }, [vendors, vendorQuery, vendorStatusFilter]);

  const visibleOffMarket = useMemo(() => {
    const q = offMarketQuery.trim().toLowerCase();
    return unavailability.filter((row) => {
      const matchesReason = offMarketReason === 'ALL' || row.reasonCode === offMarketReason;
      const haystack = [row.carName, row.vendorId, row.reasonLabel, row.reasonNote, row.carId]
        .map((value) => cleanText(value, '').toLowerCase())
        .join(' ');
      return matchesReason && (!q || haystack.includes(q));
    });
  }, [unavailability, offMarketQuery, offMarketReason]);

  const visibleVehicles = useMemo(() => {
    const q = submissionQuery.trim().toLowerCase();
    return modalVehicles.filter((vehicle) => {
      const normalizedStatus = String(vehicle.approvalStatus || 'PENDING_REVIEW').toUpperCase();
      const matchesStatus = submissionStatus === 'ALL' || normalizedStatus === submissionStatus;
      const haystack = [vehicle.name, vehicle.make, vehicle.model, vehicle.variant, vehicle.category, vehicle.registrationNumber, vehicle.city, vehicle.driverName]
        .map((value) => cleanText(value, '').toLowerCase())
        .join(' ');
      return matchesStatus && (!q || haystack.includes(q));
    });
  }, [modalVehicles, submissionQuery, submissionStatus]);

  const pendingVendors = vendors.filter((vendor) => String(vendor.status).toUpperCase() === 'PENDING').length;
  const rejectedVendors = vendors.filter((vendor) => String(vendor.status).toUpperCase() === 'REJECTED').length;
  const verifiedVendors = vendors.filter((vendor) => String(vendor.status).toUpperCase() === 'VERIFIED').length;

  return (
    <>
      <section className="rental-partners-shell">
        <div className="rental-partners-header">
          <div>
            <div className="eyebrow"><CarFront size={14} /> Rental partner operations</div>
            <h2>Rental Partners</h2>
            <p>Review partner submissions and keep off-market vehicles visible without leaving the operational workspace.</p>
          </div>
          <button className="secondary rental-refresh" onClick={() => refresh()} disabled={loading}>
            {loading ? <Clock3 size={14} /> : <CheckCircle2 size={14} />} {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>

        {error && <div className="rental-inline-error"><AlertCircle size={16} /><span>{cleanText(error)}</span><button className="icon-btn" onClick={() => setError('')}><X size={15} /></button></div>}

        <div className="rental-partner-summary">
          <div className="rental-summary-card pending"><Clock3 size={17} /><div><span>Pending reviews</span><b>{pendingVendors}</b></div></div>
          <div className="rental-summary-card approved"><BadgeCheck size={17} /><div><span>Verified partners</span><b>{verifiedVendors}</b></div></div>
          <div className="rental-summary-card rejected"><XCircle size={17} /><div><span>Rejected applications</span><b>{rejectedVendors}</b></div></div>
          <div className="rental-summary-card neutral"><CarFront size={17} /><div><span>Off-market vehicles</span><b>{visibleOffMarket.length}</b></div></div>
        </div>

        <div className="rental-partners-columns">
          <section className="rental-review-card">
            <div className="rental-review-head">
              <div className="rental-review-title">
                <div className="rental-section-icon vendors"><Users size={18} /></div>
                <div><h3>Rental vendor applications</h3><p>{visibleVendors.length} visible · {vendors.length} total</p></div>
              </div>
              <div className="rental-section-count">{visibleVendors.length}</div>
            </div>
            <div className="rental-filter-bar">
              <div className="rental-search"><Search size={14} /><input value={vendorQuery} onChange={(e) => setVendorQuery(e.target.value)} placeholder="Search vendor, mobile, business or city" /></div>
              <label className="rental-filter-select"><Filter size={13} /><select value={vendorStatusFilter} onChange={(e) => setVendorStatusFilter(e.target.value)}><option>ALL</option><option>PENDING</option><option>VERIFIED</option><option>REJECTED</option></select></label>
            </div>

            <div className="rental-scroll-area">
              {loading ? <div className="rental-empty-panel"><Clock3 size={22} /><span>Loading rental applications…</span></div> :
                visibleVendors.length === 0 ? <div className="rental-empty-panel"><Search size={22} /><span>No rental vendor applications match the selected filters.</span></div> :
                <div className="rental-vendor-list">
                  {visibleVendors.map((vendor) => (
                    <article className="rental-vendor-card" key={vendor.vendorId}>
                      <div className="rental-vendor-avatar"><Building2 size={19} /></div>
                      <div className="rental-vendor-main">
                        <div className="rental-vendor-name-row">
                          <b>{cleanText(vendor.fullName, 'Unnamed vendor')}</b>
                          <StatusBadge value={vendor.status} />
                        </div>
                        <span>{cleanText(vendor.businessName, 'Individual vendor')} · {cleanText(vendor.vendorType)}</span>
                        <div className="rental-meta-row"><span><Phone size={12} /> {cleanText(vendor.mobile, 'No mobile')}</span><span><MapPin size={12} /> {cleanText(vendor.city)}, {cleanText(vendor.state)}</span></div>
                        <div className="rental-vendor-submission-line"><span><CalendarDays size={12} /> Submitted {dateTime(vendor.submittedAt)}</span><span className="rental-approved-vehicle-count"><BadgeCheck size={12} /> {vendor.approvedVehicleCount} approved</span></div>
                        <div className="rental-vendor-submission-line"><span><Clock3 size={12} /> {vendor.pendingVehicleCount} pending review</span><span><CarFront size={12} /> {vendor.vehicleCount} total vehicle{vendor.vehicleCount === 1 ? '' : 's'}</span></div>
                      </div>
                      <div className="rental-vendor-actions">
                        <button className="secondary rental-action" onClick={() => openVendorDetail(vendor)}><Eye size={14} /> Details</button>
                        <button className="primary rental-action" disabled={vendor.vehicleCount <= 0} onClick={() => void openSubmissions(vendor)}><CarFront size={14} /> View submissions <span className="action-count">{vendor.pendingVehicleCount}</span></button>
                      </div>
                    </article>
                  ))}
                </div>}
            </div>
          </section>

          <section className="rental-review-card">
            <div className="rental-review-head">
              <div className="rental-review-title">
                <div className="rental-section-icon offmarket"><Clock3 size={18} /></div>
                <div><h3>Vendor vehicle off-market periods</h3><p>Active periods requested by verified vendors are excluded from customer availability search.</p></div>
              </div>
              <div className="rental-section-count">{visibleOffMarket.length}</div>
            </div>
            <div className="rental-filter-bar">
              <div className="rental-search"><Search size={14} /><input value={offMarketQuery} onChange={(e) => setOffMarketQuery(e.target.value)} placeholder="Search car, vendor or reason" /></div>
              <label className="rental-filter-select"><Filter size={13} /><select value={offMarketReason} onChange={(e) => setOffMarketReason(e.target.value)}><option value="ALL">All reasons</option><option value="SERVICE_MAINTENANCE">Service / maintenance</option><option value="PRIVATE_USE">Private use</option><option value="DRIVER_UNAVAILABLE">Driver unavailable</option><option value="LEGAL_DOCUMENTATION">Documentation / compliance</option><option value="PERSONAL_REASON">Personal reason</option><option value="OTHER">Other</option></select></label>
            </div>

            <div className="rental-scroll-area">
              {visibleOffMarket.length === 0 ? <div className="rental-empty-panel"><CheckCircle2 size={22} /><span>No active vehicle off-market periods match the selected filters.</span></div> :
                <div className="rental-offmarket-list">
                  {visibleOffMarket.map((row) => (
                    <article className="rental-offmarket-card" key={row.id}>
                      <div className="rental-offmarket-icon"><CarFront size={18} /></div>
                      <div className="rental-offmarket-main">
                        <div className="rental-vendor-name-row"><b>{cleanText(row.carName, 'Car')}</b><StatusBadge value={row.status} /></div>
                        <div className="rental-meta-row"><span><CalendarDays size={12} /> {dateOnly(row.startDate)} → {dateOnly(row.endDate)}</span><span>{cleanText(row.reasonLabel)}</span></div>
                        {row.reasonNote && <p>{cleanText(row.reasonNote)}</p>}
                        <small>{cleanText(vendors.find((vendor) => vendor.vendorId === row.vendorId)?.fullName, 'Vendor')} · Vehicle ID {cleanText(row.carId)}</small>
                      </div>
                    </article>
                  ))}
                </div>}
            </div>
          </section>
        </div>
      </section>

      {modal?.kind === 'vendor-detail' && (
        <div className="rental-modal-backdrop" onClick={closeModal}>
          <aside className="rental-modal rental-modal-vendor" onClick={(event) => event.stopPropagation()}>
            <ModalHeader icon={<Building2 size={18} />} eyebrow="Rental vendor submission" title={cleanText(modal.vendor.fullName, 'Unnamed vendor')} subtitle={cleanText(modal.vendor.businessName, 'Individual vendor')} onClose={closeModal} />
            <div className="rental-modal-body">
              <div className="rental-modal-status-row"><StatusBadge value={modal.vendor.status} /><span>Submitted {dateTime(modal.vendor.submittedAt)}</span></div>
              <div className="rental-info-grid">
                <InfoCard icon={<UserRound size={15} />} label="Full name" value={modal.vendor.fullName} />
                <InfoCard icon={<Building2 size={15} />} label="Business / fleet" value={modal.vendor.businessName} />
                <InfoCard icon={<Phone size={15} />} label="Mobile" value={modal.vendor.mobile} />
                <InfoCard icon={<Mail size={15} />} label="Email" value={modal.vendor.email} />
                <InfoCard icon={<MapPin size={15} />} label="Address" value={`${cleanText(modal.vendor.address)}, ${cleanText(modal.vendor.city)}, ${cleanText(modal.vendor.state)} ${cleanText(modal.vendor.pinCode)}`} />
                <InfoCard icon={<FileText size={15} />} label="Vendor type" value={modal.vendor.vendorType} />
                <InfoCard icon={<CreditCard size={15} />} label="PAN" value={modal.vendor.panNumber} />
                <InfoCard icon={<IndianRupee size={15} />} label="Primary payout" value={modal.vendor.payoutPrimaryMethod} />
                <InfoCard icon={<CreditCard size={15} />} label="Payout UPI" value={modal.vendor.payoutUpiId} />
                <InfoCard icon={<CreditCard size={15} />} label="Bank account" value={modal.vendor.bankAccountNumber} />
                <InfoCard icon={<ShieldCheck size={15} />} label="IFSC" value={modal.vendor.bankIfsc} />
                <InfoCard icon={<Building2 size={15} />} label="Bank name" value={modal.vendor.bankName} />
                <InfoCard icon={<CarFront size={15} />} label="Vehicle submissions" value={modal.vendor.vehicleCount} />
                {modal.vendor.rejectionReason && <InfoCard icon={<AlertCircle size={15} />} label="Review note" value={modal.vendor.rejectionReason} tone="danger" />}
              </div>

              {modal.vendor.status !== 'VERIFIED' && (
                <DecisionFooter
                  confirmed={inspectionConfirmed}
                  setConfirmed={setInspectionConfirmed}
                  reason={rejectReason}
                  setReason={setRejectReason}
                  approveLabel="Approve vendor"
                  rejectLabel="Reject vendor"
                  busyApprove={busy === 'vendor-approve-' + modal.vendor.vendorId}
                  busyReject={busy === 'vendor-reject-' + modal.vendor.vendorId}
                  canApprove={modal.vendor.status === 'PENDING' || modal.vendor.status === 'REJECTED'}
                  onApprove={() => void approveVendor(modal.vendor)}
                  onReject={() => void rejectVendor(modal.vendor)}
                />
              )}
            </div>
          </aside>
        </div>
      )}

      {modal?.kind === 'submissions' && (
        <div className="rental-modal-backdrop" onClick={closeModal}>
          <aside className="rental-modal rental-modal-submissions" onClick={(event) => event.stopPropagation()}>
            <ModalHeader icon={<CarFront size={18} />} eyebrow="Submitted vehicles" title={cleanText(modal.vendor.fullName, 'Vendor')} subtitle={modalVehicles.length + ' vehicles · ' + modalVehicles.filter((vehicle) => isPendingVehicle(vehicle.approvalStatus)).length + ' pending review'} onClose={closeModal} />
            <div className="rental-modal-body">
              <div className="rental-submission-toolbar">
                <div><span className="eyebrow"><FileCheck2 size={13} /> Vehicle review queue</span><p>Pending vehicles can be approved or rejected; completed reviews are view-only.</p></div>
                <div className="rental-filter-bar compact">
                  <div className="rental-search"><Search size={14} /><input value={submissionQuery} onChange={(e) => setSubmissionQuery(e.target.value)} placeholder="Search vehicle, registration or driver" /></div>
                  <label className="rental-filter-select"><Filter size={13} /><select value={submissionStatus} onChange={(e) => setSubmissionStatus(e.target.value)}><option>ALL</option><option>PENDING_REVIEW</option><option>APPROVED</option><option>REJECTED</option></select></label>
                </div>
              </div>

              <div className="rental-submission-scroll">
                {submissionLoading ? <div className="rental-empty-panel"><Clock3 size={22} /><span>Loading submitted vehicles…</span></div> :
                  visibleVehicles.length === 0 ? <div className="rental-empty-panel"><Search size={22} /><span>No submitted vehicles match the selected filters.</span></div> :
                  visibleVehicles.map((vehicle, vehicleIndex) => (
                    <article className="rental-vehicle-review-card" key={vehicle.id}>
                      <div className="rental-review-card-gallery">
                        {vehiclePhotos(vehicle).map((photo, photoIndex) => (
                          <PhotoTile
                            key={photoIndex}
                            src={photo || null}
                            alt={cleanText(vehicle.name, 'Vehicle') + ' photo ' + (photoIndex + 1)}
                            className="rental-review-card-photo"
                            priority={vehicleIndex < 2}
                          />
                        ))}
                      </div>
                      <div className="rental-vehicle-main">
                        <div className="rental-vendor-name-row"><b>{cleanText(vehicle.name, 'Vehicle')}</b><StatusBadge value={vehicle.approvalStatus} /></div>
                        <span>{cleanText([vehicle.make, vehicle.model, vehicle.variant].filter(Boolean).join(' '), 'Vehicle details')} · {cleanText(vehicle.category)}</span>
                        <div className="rental-meta-row"><span><Users size={12} /> {cleanText(vehicle.driverName, 'Driver assigned')}</span><span><MapPin size={12} /> {cleanText(vehicle.city)}, {cleanText(vehicle.state)}</span></div>
                        <div className="rental-vendor-submission-line"><span><IndianRupee size={12} /> {INR.format(Number(vehicle.pricePerDay || 0))} / day</span><span>Reg. {cleanText(vehicle.registrationNumber)}</span></div>
                      </div>
                       <button className={`${isPendingVehicle(vehicle.approvalStatus) ? 'primary' : 'secondary'} rental-action inspect-action`} onClick={() => inspectVehicle(modal.vendor, vehicle)}><Eye size={14} /> {isPendingVehicle(vehicle.approvalStatus) ? 'Inspect details' : 'See details'}</button>
                    </article>
                  ))}
              </div>
            </div>
          </aside>
        </div>
      )}

      {modal?.kind === 'inspection' && (
        <div className="rental-modal-backdrop rental-modal-inspection-backdrop" onClick={backToSubmissions}>
          <aside className="rental-modal rental-modal-inspection" onClick={(event) => event.stopPropagation()}>
            <ModalHeader icon={<FileCheck2 size={18} />} eyebrow={isPendingVehicle(modal.vehicle.approvalStatus) ? 'Vehicle inspection' : 'Vehicle details'} title={cleanText(modal.vehicle.name, 'Vehicle')} subtitle={cleanText(modal.vendor.fullName, 'Vendor') + ' · ' + cleanText(modal.vehicle.approvalStatus, 'PENDING_REVIEW')} onClose={backToSubmissions} backLabel="Back to submissions" />
            <div className="rental-modal-body">
              <div className="inspection-hero">
                <VehiclePhotoCarousel
                  photos={vehiclePhotos(modal.vehicle)}
                  activeIndex={Math.min(inspectionPhotoIndex, Math.max(vehiclePhotos(modal.vehicle).length - 1, 0))}
                  onPrev={() => setInspectionPhotoIndex((current) => {
                    const photos = vehiclePhotos(modal.vehicle);
                    return photos.length ? (current - 1 + photos.length) % photos.length : 0;
                  })}
                  onNext={() => setInspectionPhotoIndex((current) => {
                    const photos = vehiclePhotos(modal.vehicle);
                    return photos.length ? (current + 1) % photos.length : 0;
                  })}
                  onSelect={setInspectionPhotoIndex}
                />
                <div className="inspection-summary">
                  <div className="inspection-summary-topline">
                    <StatusBadge value={modal.vehicle.approvalStatus} />
                    <span><CarFront size={12} /> Vehicle submission</span>
                  </div>
                  <h3>{cleanText(modal.vehicle.name, 'Vehicle')}</h3>
                  <p>{cleanText([modal.vehicle.make, modal.vehicle.model, modal.vehicle.variant].filter(Boolean).join(' '), '—')}</p>
                  <div className="inspection-quick-grid">
                    <QuickStat icon={<IndianRupee size={14} />} label="Price / day" value={INR.format(Number(modal.vehicle.pricePerDay || 0))} />
                    <QuickStat icon={<Users size={14} />} label="Seats" value={modal.vehicle.seats} />
                    <QuickStat icon={<CarFront size={14} />} label="Transmission" value={modal.vehicle.transmission} />
                    <QuickStat icon={<BadgeCheck size={14} />} label="Fuel" value={modal.vehicle.fuelType} />
                  </div>
                  <div className="inspection-summary-meta">
                    <span><CalendarDays size={12} /> Submitted {dateTime(modal.vehicle.createdAt || modal.vehicle.submittedAt)}</span>
                    <span><FileText size={12} /> ID {cleanText(modal.vehicle.id)}</span>
                  </div>
                </div>
              </div>

              <div className="inspection-grid">
                <section className="inspection-section inspection-info-section">
                  <div className="inspection-section-title"><div><h3>Vehicle information</h3><p>Identity, pricing and pickup details.</p></div></div>
                  <div className="rental-info-grid">
                    <InfoCard label="Category" value={modal.vehicle.category} />
                    <InfoCard label="Make / model" value={[modal.vehicle.make, modal.vehicle.model].filter(Boolean).join(' ')} />
                    <InfoCard label="Variant" value={modal.vehicle.variant} />
                    <InfoCard label="Registration" value={modal.vehicle.registrationNumber} />
                    <InfoCard label="Manufacturing year" value={modal.vehicle.manufacturingYear} />
                    <InfoCard label="Registration year" value={modal.vehicle.registrationYear} />
                    <InfoCard label="Price / day" value={INR.format(Number(modal.vehicle.pricePerDay || 0))} />
                    <InfoCard label="Pickup address" value={modal.vehicle.pickupAddress} />
                    <InfoCard label="City / State" value={`${cleanText(modal.vehicle.city)}, ${cleanText(modal.vehicle.state)}`} />
                    <InfoCard label="Pickup coordinates" value={[modal.vehicle.pickupLatitude, modal.vehicle.pickupLongitude].filter(v => v !== undefined && v !== null && v !== '').join(', ')} />
                    <InfoCard label="Pickup Place ID" value={modal.vehicle.pickupPlaceId} />
                  </div>
                </section>

                <section className="inspection-section inspection-driver-section">
                  <div className="inspection-section-title"><div><h3>Assigned driver</h3><p>Driver identity and licence details.</p></div><UserCheck size={17} /></div>
                  <div className="driver-review-card">
                    <div className="driver-photo-panel">
                      <PhotoTile src={modal.vehicle.driverPhotoUrl} alt="Driver" label="Driver photo" className="driver-photo-large" />
                      <div className="driver-photo-caption"><UserRound size={13} /><span>Assigned driver</span></div>
                    </div>
                    <div className="driver-info-content">
                      <div className="driver-name-line">
                        <div>
                          <span>Assigned driver</span>
                          <b>{cleanText(modal.vehicle.driverName, 'Driver not specified')}</b>
                        </div>
                        <StatusBadge value={modal.vehicle.driverStatus || (modal.vehicle.driverLicenseExpiry && new Date(modal.vehicle.driverLicenseExpiry).getTime() >= Date.now() ? 'ACTIVE' : 'PENDING')} />
                      </div>
                      <div className="rental-info-grid driver-info-grid">
                        <InfoCard icon={<Phone size={15} />} label="Mobile" value={modal.vehicle.driverMobile} />
                        <InfoCard icon={<CreditCard size={15} />} label="Licence number" value={modal.vehicle.driverLicenseNumber} />
                        <InfoCard icon={<CalendarDays size={15} />} label="Licence expiry" value={modal.vehicle.driverLicenseExpiry ? dateTime(modal.vehicle.driverLicenseExpiry) : null} />
                        <InfoCard icon={<MapPin size={15} />} label="Driver address" value={modal.vehicle.driverAddress} />
                      </div>
                    </div>
                  </div>
                </section>
              </div>

              <ReviewTimeline vendor={modal.vendor} vehicle={modal.vehicle} />

              {isPendingVehicle(modal.vehicle.approvalStatus) && (
                <DecisionFooter
                  confirmed={inspectionConfirmed}
                  setConfirmed={setInspectionConfirmed}
                  reason={vehicleRejectReason}
                  setReason={setVehicleRejectReason}
                  approveLabel="Approve vehicle"
                  rejectLabel="Reject vehicle"
                  busyApprove={busy === 'vehicle-approve-' + modal.vehicle.id}
                  busyReject={busy === 'vehicle-reject-' + modal.vehicle.id}
                  canApprove={modal.vendor.status.toUpperCase() === 'VERIFIED'}
                  onApprove={() => void approveVehicle(modal.vendor, modal.vehicle)}
                  onReject={() => void rejectVehicle(modal.vendor, modal.vehicle)}
                  approvalHelp={modal.vendor.status.toUpperCase() !== 'VERIFIED' ? 'Verify the vendor before approving vehicles.' : undefined}
                />
              )}
            </div>
          </aside>
        </div>
      )}
    </>
  );
}

function ReviewTimeline({ vendor, vehicle }: { vendor: RentalAdminVendor; vehicle: any }) {
  const events = (() => {
    const sourceNotes = [
      ...(Array.isArray(vehicle?.reviewHistory) ? vehicle.reviewHistory : []),
      ...(Array.isArray(vehicle?.reviewNotes) ? vehicle.reviewNotes : []),
      ...(Array.isArray(vehicle?.statusHistory) ? vehicle.statusHistory : []),
      ...(Array.isArray((vendor as any)?.reviewHistory) ? (vendor as any).reviewHistory : []),
    ];
    const mapped = sourceNotes.map((entry: any, index: number) => ({
      id: String(entry?.id || index),
      at: entry?.createdAt || entry?.updatedAt || entry?.timestamp || vehicle?.updatedAt || vehicle?.createdAt || vendor?.updatedAt || vendor?.submittedAt,
      status: entry?.status || entry?.approvalStatus || entry?.type || 'REVIEW',
      note: entry?.note || entry?.message || entry?.reason || entry?.description || '',
      actor: entry?.actorName || entry?.reviewedBy || entry?.adminName || 'Admin review',
    }));

    if (vehicle?.rejectionReason) {
      mapped.push({
        id: 'vehicle-rejection',
        at: vehicle?.updatedAt || vehicle?.createdAt,
        status: 'REJECTED',
        note: vehicle.rejectionReason,
        actor: 'Admin review',
      });
    }

    if (!mapped.length) {
      mapped.push({
        id: 'submission',
        at: vehicle?.createdAt || vehicle?.submittedAt || vendor?.submittedAt,
        status: 'SUBMITTED',
        note: 'Vehicle submission received for review.',
        actor: 'System',
      });
      if (vehicle?.updatedAt && vehicle.updatedAt !== (vehicle?.createdAt || vehicle?.submittedAt)) {
        mapped.push({
          id: 'latest-state',
          at: vehicle.updatedAt,
          status: vehicle?.approvalStatus || 'PENDING_REVIEW',
          note: 'Latest vehicle review state recorded.',
          actor: 'System',
        });
      }
    }

    return mapped
      .filter(event => event.at)
      .sort((a, b) => new Date(a.at).getTime() - new Date(b.at).getTime());
  })();

  return (
    <section className="inspection-section review-timeline-section">
      <div className="inspection-section-title">
        <div><h3>Review timeline</h3><p>Chronological review events and notes. Notes are shown from the data returned by the rental workflow.</p></div>
        <History size={17} />
      </div>
      <div className="review-timeline">
        {events.map((event, index) => {
          const meta = statusMeta(event.status);
          const Icon = meta.icon;
          return (
            <article className={`review-timeline-item ${meta.className}`} key={event.id + '-' + index}>
              <div className="review-timeline-rail"><span><Icon size={12} /></span></div>
              <div className="review-timeline-content">
                <div className="review-timeline-head">
                  <StatusBadge value={event.status} />
                  <time>{dateTime(event.at)}</time>
                </div>
                <p>{cleanText(event.note || meta.label)}</p>
                <small>{cleanText(event.actor, 'Admin review')}</small>
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}

function ModalHeader({
  icon, eyebrow, title, subtitle, onClose, backLabel,
}: {
  icon: ReactNode;
  eyebrow: string;
  title: string;
  subtitle: string;
  onClose: () => void;
  backLabel?: string;
}) {
  return (
    <div className="rental-modal-header">
      <div className="rental-modal-title-wrap">
        {backLabel && <button className="icon-btn rental-back-btn" onClick={onClose} title={backLabel}><ChevronLeft size={18} /></button>}
        <div className="rental-modal-icon">{icon}</div>
        <div><div className="eyebrow">{eyebrow}</div><h2>{title}</h2><p>{subtitle}</p></div>
      </div>
      <button className="icon-btn" onClick={onClose}><X size={18} /></button>
    </div>
  );
}

function VehiclePhotoCarousel({
  photos,
  activeIndex,
  onPrev,
  onNext,
  onSelect,
}: {
  photos: string[];
  activeIndex: number;
  onPrev: () => void;
  onNext: () => void;
  onSelect: (index: number) => void;
}) {
  const safeIndex = photos.length ? Math.min(activeIndex, photos.length - 1) : 0;
  const activePhoto = photos[safeIndex];

  return (
    <div className="inspection-vehicle-gallery">
      <div className="inspection-gallery-stage">
        <PhotoTile
          src={activePhoto}
          alt={activePhoto ? `Vehicle photo ${safeIndex + 1}` : 'Vehicle'}
          label={activePhoto ? `Photo ${safeIndex + 1} of ${photos.length}` : 'Vehicle photo'}
          className="inspection-gallery-main"
          variant="large"
        />
        {photos.length > 1 && (
          <>
            <button className="inspection-gallery-nav prev" type="button" onClick={onPrev} aria-label="Previous vehicle photo"><ChevronLeft size={17} /></button>
            <button className="inspection-gallery-nav next" type="button" onClick={onNext} aria-label="Next vehicle photo"><ChevronRight size={17} /></button>
          </>
        )}
      </div>
      {photos.length > 1 && (
        <div className="inspection-gallery-thumbs">
          {photos.map((photo, index) => (
            <button
              key={photo + index}
              type="button"
              className={`inspection-gallery-thumb ${index === safeIndex ? 'active' : ''}`}
              onClick={() => onSelect(index)}
              aria-label={`Show vehicle photo ${index + 1}`}
            >
              <PhotoTile src={photo} alt={`Vehicle thumbnail ${index + 1}`} />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function InfoCard({ icon, label, value, tone = 'default' }: { icon?: ReactNode; label: string; value: unknown; tone?: 'default' | 'danger' }) {
  return <div className={`rental-info-card ${tone}`}><div className="rental-info-label">{icon}{label}</div><b>{cleanText(value)}</b></div>;
}

function QuickStat({ icon, label, value }: { icon: ReactNode; label: string; value: unknown }) {
  return <div className="inspection-quick-stat"><span>{icon}{label}</span><b>{cleanText(value)}</b></div>;
}

function DecisionFooter({
  confirmed, setConfirmed, reason, setReason, approveLabel, rejectLabel,
  busyApprove, busyReject, canApprove, onApprove, onReject, approvalHelp,
}: {
  confirmed: boolean;
  setConfirmed: (value: boolean) => void;
  reason: string;
  setReason: (value: string) => void;
  approveLabel: string;
  rejectLabel: string;
  busyApprove: boolean;
  busyReject: boolean;
  canApprove: boolean;
  onApprove: () => void;
  onReject: () => void;
  approvalHelp?: string;
}) {
  return (
    <div className="rental-decision-footer">
      <div className="rental-inspection-confirm">
        <label><input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} /><span><b>I inspected this submission</b><small>I have reviewed the submitted information before taking this action.</small></span></label>
      </div>
      <div className="rental-decision-actions">
        <div className="rental-reject-control"><input value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Reason to reject" /><button className="secondary danger-outline" disabled={!confirmed || busyReject || !reason.trim()} onClick={onReject}><XCircle size={14} />{busyReject ? 'Rejecting…' : rejectLabel}</button></div>
        <button className="primary" disabled={!confirmed || busyApprove || !canApprove} title={!canApprove ? approvalHelp : undefined} onClick={onApprove}><CheckCircle2 size={14} />{busyApprove ? 'Approving…' : approveLabel}</button>
      </div>
      {approvalHelp && <div className="rental-approval-help"><AlertCircle size={14} />{approvalHelp}</div>}
    </div>
  );
}
