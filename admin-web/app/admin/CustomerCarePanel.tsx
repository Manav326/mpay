'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  BellRing,
  CheckCircle2,
  ChevronRight,
  Clock3,
  FileText,
  MessageSquareText,
  PhoneCall,
  PhoneOff,
  RefreshCw,
  Search,
  ShieldCheck,
  UserRound,
  XCircle,
} from 'lucide-react';
import {
  addSupportCaseNote,
  createVoiceCall,
  declineCustomerCareRequest,
  getCustomerCallbackAccess,
  getCustomerCareCustomer,
  getCustomerCareRequests,
  startCustomerCareCall,
  updateCustomerCallbackAccess,
  updateSupportCase,
} from '@/lib/api';
import { SupportCallRequest, SupportCase, SupportCustomer, SupportInteraction, SupportNote } from '@/lib/types';
import { VoiceCallWidget } from './VoiceCallPanel';

type Props = {
  canManageSupport: boolean;
  canCallCustomer: boolean;
  canManageCallAccess: boolean;
};

const dateTime = (value: string) =>
  new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));

const relativeStatus = (status: string) => {
  const normalized = status.toUpperCase();
  if (normalized === 'ACTIVE' || normalized === 'OPEN') return 'active';
  if (normalized === 'RESOLVED' || normalized === 'COMPLETED') return 'approved';
  if (normalized === 'DECLINED' || normalized === 'CANCELLED' || normalized === 'EXPIRED') return 'blocked';
  return 'pending';
};

function duration(seconds?: number | null) {
  if (seconds == null) return '—';
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  return minutes + ':' + String(remainder).padStart(2, '0');
}

export default function CustomerCarePanel({ canManageSupport, canCallCustomer, canManageCallAccess }: Props) {
  const [requests, setRequests] = useState<SupportCallRequest[]>([]);
  const [requestsLoading, setRequestsLoading] = useState(true);
  const [selected, setSelected] = useState<SupportCustomer | null>(null);
  const [selectedLoading, setSelectedLoading] = useState(false);
  const [customerQuery, setCustomerQuery] = useState('');
  const [notice, setNotice] = useState('');
  const [busyKey, setBusyKey] = useState('');
  const [activeCallId, setActiveCallId] = useState<string | null>(null);
  const [activeCallName, setActiveCallName] = useState('');

  async function loadRequests() {
    setRequestsLoading(true);
    try {
      setRequests(await getCustomerCareRequests());
    } catch (error: any) {
      setNotice(error?.message || 'Unable to load customer care requests.');
    } finally {
      setRequestsLoading(false);
    }
  }

  async function openCustomer(publicId: string) {
    if (!publicId) return;
    setSelectedLoading(true);
    setNotice('');
    try {
      setSelected(await getCustomerCareCustomer(publicId));
    } catch (error: any) {
      setNotice(error?.message || 'Unable to load the customer support profile.');
    } finally {
      setSelectedLoading(false);
    }
  }

  useEffect(() => {
    void loadRequests();
    const timer = window.setInterval(() => { void loadRequests(); }, 7000);
    return () => window.clearInterval(timer);
  }, []);

  const visibleRequests = useMemo(() => {
    const q = customerQuery.trim().toLowerCase();
    if (!q) return requests;
    return requests.filter(item =>
      [item.customerName, item.customerMobile, item.customerPublicId, item.reason]
        .some(value => String(value || '').toLowerCase().includes(q))
    );
  }, [requests, customerQuery]);

  async function callRequest(item: SupportCallRequest) {
    if (!canManageSupport || !canCallCustomer || busyKey) return;
    setBusyKey('call:' + item.requestId);
    try {
      const call = await startCustomerCareCall(item.requestId);
      setActiveCallId(call.callId);
      setActiveCallName(item.customerName || item.customerMobile || 'mPay customer');
      setNotice('Customer callback started. The customer must accept before audio connects.');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to start the customer callback.');
    } finally {
      setBusyKey('');
    }
  }

  async function directCall() {
    if (!selected || !canCallCustomer || busyKey) return;
    setBusyKey('direct-call');
    try {
      const call = await createVoiceCall(selected.customerPublicId);
      setActiveCallId(call.callId);
      setActiveCallName(selected.customerName || selected.mobile || 'mPay customer');
      setNotice('Voice support call started.');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to start the customer call.');
    } finally {
      setBusyKey('');
    }
  }

  async function declineRequest(item: SupportCallRequest) {
    if (!canManageSupport || busyKey) return;
    const note = window.prompt('Optional reason for declining this callback request:') || '';
    setBusyKey('decline:' + item.requestId);
    try {
      await declineCustomerCareRequest(item.requestId, note);
      setNotice('Callback request declined.');
      await loadRequests();
      if (selected?.customerPublicId === item.customerPublicId) await openCustomer(item.customerPublicId || '');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to decline the callback request.');
    } finally {
      setBusyKey('');
    }
  }

  async function toggleCallbackAccess() {
    if (!selected || !canManageCallAccess) return;
    setBusyKey('callback-access');
    try {
      const current = await getCustomerCallbackAccess(selected.customerPublicId);
      const next = await updateCustomerCallbackAccess(selected.customerPublicId, !current.enabled);
      setSelected(value => value ? { ...value, callbackRequestEnabled: next.enabled } : value);
      setNotice(next.enabled ? 'Customer callback requests enabled.' : 'Customer callback requests disabled.');
      await loadRequests();
    } catch (error: any) {
      setNotice(error?.message || 'Unable to update customer callback permission.');
    } finally {
      setBusyKey('');
    }
  }

  async function saveCaseStatus(caseItem: SupportCase, status: string) {
    if (!canManageSupport || busyKey) return;
    setBusyKey('case:' + caseItem.caseId);
    try {
      const updated = await updateSupportCase(caseItem.caseId, status, caseItem.resolutionCode || undefined, caseItem.resolutionNote || undefined);
      setSelected(value => value ? {
        ...value,
        openCases: value.openCases.map(item => item.caseId === updated.caseId ? updated : item),
      } : value);
      setNotice('Support case updated.');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to update support case.');
    } finally {
      setBusyKey('');
    }
  }

  async function addCaseNote(caseId: string) {
    if (!canManageSupport || busyKey) return;
    const note = window.prompt('Internal support note:');
    if (!note?.trim()) return;
    setBusyKey('note:' + caseId);
    try {
      const saved = await addSupportCaseNote(caseId, note.trim(), 'INTERNAL');
      setSelected(value => value ? { ...value, notes: [saved, ...value.notes] } : value);
      setNotice('Internal support note added.');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to add the support note.');
    } finally {
      setBusyKey('');
    }
  }

  return (
    <div className="content">
      <section className="panel">
        <div className="panel-head wrap">
          <div>
            <div className="eyebrow">Support operations</div>
            <h2>Customer Care</h2>
            <p>One workspace for callback requests, support cases, voice history and agent notes.</p>
          </div>
          <button className="secondary compact" onClick={() => void loadRequests()} disabled={requestsLoading}>
            <RefreshCw size={14} /> {requestsLoading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>

        <div className="metric-grid" style={{ marginTop: 16 }}>
          <div className="metric-card">
            <div className="metric-head"><span>Pending callbacks</span><div className="metric-icon"><BellRing size={17} /></div></div>
            <strong>{requests.length}</strong>
            <small>Waiting for support staff</small>
          </div>
          <div className="metric-card">
            <div className="metric-head"><span>Calling access</span><div className="metric-icon"><PhoneCall size={17} /></div></div>
            <strong>{canCallCustomer ? 'Enabled' : 'Restricted'}</strong>
            <small>Based on your staff permission</small>
          </div>
          <div className="metric-card">
            <div className="metric-head"><span>Customer request control</span><div className="metric-icon"><ShieldCheck size={17} /></div></div>
            <strong>{canManageCallAccess ? 'Admin' : 'Locked'}</strong>
            <small>Only Admin can grant callback permission</small>
          </div>
        </div>
      </section>

      {notice && <div className="admin-notice"><span>{notice}</span><button onClick={() => setNotice('')}>Dismiss</button></div>}

      <div className="split">
        <section className="panel">
          <div className="panel-head wrap">
            <div>
              <h2>Customer requests a call</h2>
              <p>A request never starts audio. Staff explicitly choose when to call.</p>
            </div>
          </div>
          <div className="filters" style={{ marginBottom: 12 }}>
            <Search size={15} />
            <input
              value={customerQuery}
              onChange={event => setCustomerQuery(event.target.value)}
              placeholder="Search customer, mobile or ID"
            />
          </div>

          <div style={{ display: 'grid', gap: 10 }}>
            {requestsLoading && requests.length === 0 ? (
              <div className="empty-state">Loading callback requests…</div>
            ) : visibleRequests.length === 0 ? (
              <div className="empty-state">No pending callback requests.</div>
            ) : visibleRequests.map(item => (
              <div key={item.requestId} className="detail-card" style={{ cursor: 'pointer' }} onClick={() => item.customerPublicId && void openCustomer(item.customerPublicId)}>
                <div className="detail-top">
                  <div>
                    <b>{item.customerName || 'mPay customer'}</b>
                    <span>{item.customerMobile || '—'} · requested {dateTime(item.requestedAt)}</span>
                  </div>
                  <span className={'status ' + relativeStatus(item.status)}>{item.status}</span>
                </div>
                {item.reason && <p style={{ margin: '8px 0 0', color: '#64748b' }}>{item.reason}</p>}
                <div className="detail-row">
                  <span>Request expires</span>
                  <strong>{dateTime(item.expiresAt)}</strong>
                </div>
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 10 }}>
                  <button
                    className="secondary compact"
                    onClick={event => { event.stopPropagation(); if (item.customerPublicId) void openCustomer(item.customerPublicId); }}
                  >
                    Open
                  </button>
                  {canManageSupport && (
                    <button
                      className="secondary compact"
                      disabled={!canCallCustomer || !!busyKey}
                      onClick={event => { event.stopPropagation(); void callRequest(item); }}
                    >
                      <PhoneCall size={14} /> {busyKey === 'call:' + item.requestId ? 'Starting…' : 'Call customer'}
                    </button>
                  )}
                  {canManageSupport && (
                    <button
                      className="secondary compact"
                      disabled={!!busyKey}
                      onClick={event => { event.stopPropagation(); void declineRequest(item); }}
                    >
                      <PhoneOff size={14} /> Decline
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="panel">
          <div className="panel-head wrap">
            <div>
              <h2>Customer lookup</h2>
              <p>Open the full Customer 360 support view using the mPay customer ID.</p>
            </div>
          </div>
          <div className="form" style={{ display: 'flex', gap: 8 }}>
            <input
              value={customerQuery}
              onChange={event => setCustomerQuery(event.target.value)}
              placeholder="Customer public ID"
            />
            <button className="primary" onClick={() => void openCustomer(customerQuery.trim())} disabled={selectedLoading || !customerQuery.trim()}>
              {selectedLoading ? 'Opening…' : 'Open'}
            </button>
          </div>
          <div style={{ marginTop: 16, padding: 14, borderRadius: 16, background: '#fff8e7', color: '#475569' }}>
            <b style={{ display: 'block', color: '#172033', marginBottom: 4 }}>Permission model</b>
            <span>SUPPORT_VIEW reads support history. SUPPORT_MANAGE handles requests and cases. CALL_CUSTOMER starts voice calls. MANAGE_CALL_ACCESS alone can grant a customer permission to request a callback.</span>
          </div>
        </section>
      </div>

      {selected && (
        <section className="panel">
          <div className="panel-head wrap">
            <div>
              <div className="eyebrow">Customer 360 · Support</div>
              <h2>{selected.customerName || 'mPay customer'}</h2>
              <p>{selected.mobile} · {selected.customerPublicId}</p>
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
              {canManageCallAccess && (
                <button className={selected.callbackRequestEnabled ? 'status-toggle on' : 'status-toggle off'} disabled={!!busyKey} onClick={() => void toggleCallbackAccess()}>
                  <ShieldCheck size={14} /> Callback {selected.callbackRequestEnabled ? 'enabled' : 'disabled'}
                </button>
              )}
              {canCallCustomer && !selected.pendingRequest && (
                <button className="primary" disabled={!!busyKey || !!activeCallId} onClick={() => void directCall()}>
                  <PhoneCall size={14} /> {busyKey === 'direct-call' ? 'Starting…' : activeCallId ? 'Call active' : 'Call customer'}
                </button>
              )}
              <button className="secondary compact" onClick={() => void openCustomer(selected.customerPublicId)}><RefreshCw size={14} /> Refresh</button>
            </div>
          </div>

          <div className="detail-grid detail-grid-3">
            <div><small>Callback permission</small><b>{selected.callbackRequestEnabled ? 'Enabled' : 'Disabled'}</b></div>
            <div><small>Pending request</small><b>{selected.pendingRequest ? selected.pendingRequest.status : 'None'}</b></div>
            <div><small>Open cases</small><b>{selected.openCases.length}</b></div>
            <div><small>Voice interactions</small><b>{selected.interactions.filter(item => item.channel === 'VOICE').length}</b></div>
            <div><small>Total support interactions</small><b>{selected.interactions.length}</b></div>
            <div><small>Internal notes</small><b>{selected.notes.filter(note => note.visibility === 'INTERNAL').length}</b></div>
          </div>

          {selected.pendingRequest && (
            <section className="drawer-section">
              <div className="drawer-section-title">
                <div><h3>Pending callback request</h3><p>{selected.pendingRequest.reason || 'Customer requested a call from mPay support.'}</p></div>
                <BellRing size={17}/>
              </div>
              <div className="detail-card">
                <div className="detail-row"><span>Requested</span><strong>{dateTime(selected.pendingRequest.requestedAt)}</strong></div>
                <div className="detail-row"><span>Expires</span><strong>{dateTime(selected.pendingRequest.expiresAt)}</strong></div>
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 10 }}>
                  {canManageSupport && <button className="secondary compact" disabled={!canCallCustomer || !!busyKey || !!activeCallId} onClick={() => void callRequest(selected.pendingRequest!)}><PhoneCall size={14}/> Call customer</button>}
                  {canManageSupport && <button className="secondary compact" disabled={!!busyKey} onClick={() => void declineRequest(selected.pendingRequest!)}><PhoneOff size={14}/> Decline request</button>}
                </div>
              </div>
            </section>
          )}

          <section className="drawer-section">
            <div className="drawer-section-title">
              <div><h3>Support cases</h3><p>Business problems remain separate from individual calls or messages.</p></div>
              <FileText size={17}/>
            </div>
            {selected.openCases.length === 0 ? <div className="empty-state">No support cases yet.</div> : (
              <div style={{ display: 'grid', gap: 10 }}>
                {selected.openCases.map(caseItem => (
                  <div className="detail-card" key={caseItem.caseId}>
                    <div className="detail-top">
                      <div>
                        <b>{caseItem.subject}</b>
                        <span>{caseItem.category} · {caseItem.caseId} · updated {dateTime(caseItem.updatedAt)}</span>
                      </div>
                      <span className={'status ' + relativeStatus(caseItem.status)}>{caseItem.status}</span>
                    </div>
                    <div className="detail-row"><span>Priority</span><strong>{caseItem.priority}</strong></div>
                    <div className="detail-row"><span>Source</span><strong>{caseItem.source}</strong></div>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 10 }}>
                      <select
                        value={caseItem.status}
                        disabled={!canManageSupport || busyKey === 'case:' + caseItem.caseId}
                        onChange={event => void saveCaseStatus(caseItem, event.target.value)}
                        style={{ flex: 1 }}
                      >
                        <option value="OPEN">Open</option>
                        <option value="RESOLVED">Resolved</option>
                        <option value="CLOSED">Closed</option>
                      </select>
                      {canManageSupport && <button className="secondary compact" disabled={!!busyKey} onClick={() => void addCaseNote(caseItem.caseId)}><MessageSquareText size={14}/> Add note</button>}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="drawer-section">
            <div className="drawer-section-title">
              <div><h3>Support timeline</h3><p>Calls and notes stay together so the next agent sees the previous context.</p></div>
              <Clock3 size={17}/>
            </div>
            <SupportTimeline interactions={selected.interactions} notes={selected.notes}/>
          </section>
        </section>
      )}

      {activeCallId && (
        <VoiceCallWidget
          callId={activeCallId}
          customerName={activeCallName}
          onClosed={() => {
            setActiveCallId(null);
            setActiveCallName('');
            if (selected) void openCustomer(selected.customerPublicId);
            void loadRequests();
          }}
        />
      )}
    </div>
  );
}

function SupportTimeline({ interactions, notes }: { interactions: SupportInteraction[]; notes: SupportNote[] }) {
  const events = useMemo(() => [
    ...interactions.map(item => ({ kind: 'interaction' as const, at: item.startedAt, item })),
    ...notes.map(note => ({ kind: 'note' as const, at: note.createdAt, item: note })),
  ].sort((a, b) => new Date(b.at).getTime() - new Date(a.at).getTime()), [interactions, notes]);

  if (events.length === 0) return <div className="empty-state">No support activity recorded yet.</div>;

  return <div style={{ display: 'grid', gap: 8 }}>
    {events.map((event, index) => event.kind === 'interaction' ? (
      <div className="detail-card" key={event.item.interactionId}>
        <div className="detail-top">
          <div>
            <b><PhoneCall size={14}/> {event.item.channel === 'VOICE' ? 'Voice call' : event.item.channel}</b>
            <span>{event.item.actorName || 'mPay Support'} · {dateTime(event.item.startedAt)}</span>
          </div>
          <span className={'status ' + relativeStatus(event.item.status)}>{event.item.status}</span>
        </div>
        <div className="detail-row"><span>Direction</span><strong>{event.item.direction}</strong></div>
        <div className="detail-row"><span>Talk time</span><strong>{duration(event.item.durationSeconds)}</strong></div>
        <div className="detail-row"><span>Outcome</span><strong>{event.item.outcome || 'Not recorded'}</strong></div>
        {event.item.voiceCallId && <div className="detail-row"><span>Call ID</span><strong className="mono">{event.item.voiceCallId}</strong></div>}
      </div>
    ) : (
      <div className="detail-card" key={'note-' + event.item.id}>
        <div className="detail-top">
          <div>
            <b><MessageSquareText size={14}/> {event.item.visibility === 'INTERNAL' ? 'Internal note' : 'Customer-visible note'}</b>
            <span>{event.item.authorName || 'Support'} · {dateTime(event.item.createdAt)}</span>
          </div>
          <span className="status pending">{event.item.visibility}</span>
        </div>
        <p style={{ margin: '10px 0 0', color: '#475569', whiteSpace: 'pre-wrap' }}>{event.item.note}</p>
      </div>
    ))}
  </div>
}
