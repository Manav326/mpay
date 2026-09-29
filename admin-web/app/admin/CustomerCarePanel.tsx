
'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  CheckCircle2,
  ChevronDown,
  Clock3,
  Filter,
  MessageCircle,
  RefreshCw,
  Search,
  Send,
  ShieldCheck,
  StickyNote,
  UserRound,
} from 'lucide-react';
import {
  addCustomerCareAdminMessage,
  addCustomerCareAdminNote,
  getCustomerCareAgents,
  getCustomerCareQueueSummary,
  getCustomerCareTicket,
  getCustomerCareTickets,
  updateCustomerCareAssignment,
  updateCustomerCarePriority,
  updateCustomerCareStatus,
} from '@/lib/api';
import type {
  CustomerCareAgent,
  CustomerCareQueueSummary,
  CustomerCareTicket,
  CustomerCareTicketSummary,
} from '@/lib/types';

const STATUS_OPTIONS = ['OPEN', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED'];
const PRIORITY_OPTIONS = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];
const CATEGORY_OPTIONS = ['ACCOUNT', 'WALLET', 'RECHARGE', 'WITHDRAWAL', 'RENTAL', 'PAYMENT', 'VOICE_CALL', 'OTHER'];

function statusLabel(value: string) {
  return value.replaceAll('_', ' ');
}

function relativeDate(value: string) {
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return '—';
  const seconds = Math.max(0, Math.floor((Date.now() - timestamp) / 1000));
  if (seconds < 60) return 'Just now';
  if (seconds < 3600) return Math.floor(seconds / 60) + 'm ago';
  if (seconds < 86400) return Math.floor(seconds / 3600) + 'h ago';
  return new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short' }).format(new Date(value));
}

function fullDate(value: string) {
  return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

export function CustomerCarePanel({ canManage }: { canManage: boolean }) {
  const [summary, setSummary] = useState<CustomerCareQueueSummary>();
  const [tickets, setTickets] = useState<CustomerCareTicketSummary[]>([]);
  const [agents, setAgents] = useState<CustomerCareAgent[]>([]);
  const [selected, setSelected] = useState<CustomerCareTicket>();
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('ALL');
  const [priority, setPriority] = useState('ALL');
  const [category, setCategory] = useState('ALL');
  const [assignment, setAssignment] = useState('ALL');
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [compose, setCompose] = useState('');
  const [internal, setInternal] = useState(false);
  const [notice, setNotice] = useState('');

  async function loadQueue(resetPage = false) {
    const targetPage = resetPage ? 0 : page;
    setLoading(true);
    try {
      const [queue, summaryData] = await Promise.all([
        getCustomerCareTickets(targetPage, 25, status, priority, category, assignment, query),
        getCustomerCareQueueSummary(),
      ]);
      setTickets(queue.items);
      setHasNext(queue.hasNext);
      setSummary(summaryData);
      if (resetPage) setPage(0);
    } catch (error: any) {
      setNotice(error?.message || 'Unable to load customer-care cases.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadQueue();
  }, [page, status, priority, category, assignment]);

  useEffect(() => {
    void getCustomerCareAgents().then(setAgents).catch(() => {});
  }, []);

  async function openTicket(ticketId: string) {
    setDetailLoading(true);
    try {
      setSelected(await getCustomerCareTicket(ticketId));
      setCompose('');
      setInternal(false);
      setNotice('');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to load the support case.');
    } finally {
      setDetailLoading(false);
    }
  }

  async function mutate(action: () => Promise<CustomerCareTicket>) {
    setSaving(true);
    try {
      const updated = await action();
      setSelected(updated);
      await loadQueue();
      setNotice('');
    } catch (error: any) {
      setNotice(error?.message || 'Unable to update the support case.');
    } finally {
      setSaving(false);
    }
  }

  async function sendReply() {
    const message = compose.trim();
    if (!message || !canManage || saving || !selected) return;
    const id = selected.ticketId;
    setSaving(true);
    try {
      const updated = internal
        ? await addCustomerCareAdminNote(id, message)
        : await addCustomerCareAdminMessage(id, message);
      setSelected(updated);
      setCompose('');
      setInternal(false);
      setNotice('');
      await loadQueue();
    } catch (error: any) {
      setNotice(error?.message || 'Unable to send the support message.');
    } finally {
      setSaving(false);
    }
  }

  const activeCount = useMemo(
    () => (summary?.open || 0) + (summary?.inProgress || 0) + (summary?.waitingForCustomer || 0),
    [summary]
  );

  return (
    <section className="customer-care-page">
      <div className="customer-care-head">
        <div>
          <div className="eyebrow">Support operations</div>
          <h2>Customer Care</h2>
          <p>One queue for customer issues, ownership, replies, and a clean case history.</p>
        </div>
        <button className="secondary customer-care-refresh" onClick={() => void loadQueue()} disabled={loading}>
          <RefreshCw size={14} className={loading ? 'spin' : ''} />
          Refresh
        </button>
      </div>

      {notice && <div className="customer-care-notice">{notice}</div>}

      <div className="customer-care-kpis">
        <div className="customer-care-kpi"><span>Active queue</span><strong>{activeCount}</strong><small>Open + working cases</small></div>
        <div className="customer-care-kpi"><span>My cases</span><strong>{summary?.mine ?? 0}</strong><small>Assigned to you</small></div>
        <div className="customer-care-kpi urgent"><span>Urgent</span><strong>{summary?.urgent ?? 0}</strong><small>Needs attention</small></div>
        <div className="customer-care-kpi"><span>Unassigned</span><strong>{summary?.unassigned ?? 0}</strong><small>Waiting for ownership</small></div>
      </div>

      <div className="customer-care-workspace">
        <div className="customer-care-queue">
          <div className="customer-care-filterbar">
            <label className="customer-care-search">
              <Search size={14} />
              <input
                value={query}
                onChange={event => setQuery(event.target.value)}
                onKeyDown={event => { if (event.key === 'Enter') void loadQueue(true); }}
                placeholder="Search ticket or subject"
              />
            </label>
            <CustomerCareSelect label="Status" value={status} onChange={value => { setStatus(value); setPage(0); }} options={['ALL', ...STATUS_OPTIONS]} />
            <CustomerCareSelect label="Priority" value={priority} onChange={value => { setPriority(value); setPage(0); }} options={['ALL', ...PRIORITY_OPTIONS]} />
            <CustomerCareSelect label="Category" value={category} onChange={value => { setCategory(value); setPage(0); }} options={['ALL', ...CATEGORY_OPTIONS]} />
            <CustomerCareSelect label="Owner" value={assignment} onChange={value => { setAssignment(value); setPage(0); }} options={['ALL', 'MINE', 'UNASSIGNED']} />
          </div>

          <div className="customer-care-queue-meta">
            <span><Filter size={13} /> {loading ? 'Loading cases…' : tickets.length + ' shown'}</span>
            <span>{status === 'ALL' ? 'All statuses' : statusLabel(status)}</span>
          </div>

          <div className="customer-care-ticket-list">
            {loading && !tickets.length ? (
              <div className="customer-care-empty">Loading support queue…</div>
            ) : !tickets.length ? (
              <div className="customer-care-empty">
                <MessageCircle size={20} />
                <strong>No cases match these filters</strong>
                <span>New customer cases will appear here automatically.</span>
              </div>
            ) : tickets.map(ticket => (
              <button
                key={ticket.ticketId}
                className={'customer-care-ticket-row ' + (selected?.ticketId === ticket.ticketId ? 'selected' : '')}
                onClick={() => void openTicket(ticket.ticketId)}
              >
                <div className="customer-care-ticket-main">
                  <div className="customer-care-ticket-line">
                    <strong>{ticket.subject}</strong>
                    <CustomerCarePriority value={ticket.priority} />
                  </div>
                  <span className="customer-care-ticket-id">{ticket.ticketId} · {ticket.category.replaceAll('_', ' ')}</span>
                  <div className="customer-care-ticket-customer">
                    <UserRound size={12} />
                    <span>{ticket.customerName || 'mPay customer'}</span>
                    <span>·</span>
                    <span>{ticket.customerMobile || 'Customer'}</span>
                  </div>
                </div>
                <div className="customer-care-ticket-side">
                  <CustomerCareStatus value={ticket.status} />
                  <span>{relativeDate(ticket.updatedAt)}</span>
                  <span>{ticket.assignedAgentName || 'Unassigned'}</span>
                </div>
              </button>
            ))}
          </div>

          <div className="customer-care-pagination">
            <button className="secondary" disabled={page === 0 || loading} onClick={() => setPage(p => p - 1)}>Previous</button>
            <span>Page {page + 1}</span>
            <button className="secondary" disabled={!hasNext || loading} onClick={() => setPage(p => p + 1)}>Next</button>
          </div>
        </div>

        <aside className="customer-care-detail">
          {detailLoading ? (
            <div className="customer-care-detail-empty">Loading case…</div>
          ) : !selected ? (
            <div className="customer-care-detail-empty">
              <ShieldCheck size={24} />
              <strong>Select a case</strong>
              <span>Review the conversation and action it without leaving the support workspace.</span>
            </div>
          ) : (
            <>
              <div className="customer-care-detail-head">
                <div>
                  <span className="customer-care-ticket-id">{selected.ticketId}</span>
                  <h3>{selected.subject}</h3>
                  <div className="customer-care-customer-head">
                    <span>{selected.customerName || 'mPay customer'}</span>
                    <span>·</span>
                    <span>{selected.customerMobile || 'Customer'}</span>
                  </div>
                </div>
                <div className="customer-care-detail-badges">
                  <CustomerCareStatus value={selected.status} />
                  <CustomerCarePriority value={selected.priority} />
                </div>
              </div>

              <div className="customer-care-controls">
                <CustomerCareSelect label="Status" value={selected.status} onChange={value => void mutate(() => updateCustomerCareStatus(selected.ticketId, value))} options={STATUS_OPTIONS} disabled={!canManage || saving} />
                <CustomerCareSelect label="Priority" value={selected.priority} onChange={value => void mutate(() => updateCustomerCarePriority(selected.ticketId, value))} options={PRIORITY_OPTIONS} disabled={!canManage || saving} />
                <CustomerCareSelect
                  label="Assigned to"
                  value={selected.assignedAgentPublicId || ''}
                  onChange={value => void mutate(() => updateCustomerCareAssignment(selected.ticketId, value || null))}
                  options={[
                    { value: '', label: 'Unassigned' },
                    ...agents.map(agent => ({ value: agent.publicUserId, label: (agent.name || agent.role) + ' · ' + agent.role })),
                  ]}
                  disabled={!canManage || saving}
                />
              </div>

              <div className="customer-care-timeline">
                {selected.messages.map(message => (
                  <div
                    key={message.id}
                    className={'customer-care-message ' + (message.internalNote ? 'internal' : message.authorRole === 'CLIENT' ? 'customer' : 'agent')}
                  >
                    <div className="customer-care-message-meta">
                      <span>{message.internalNote ? 'Internal note' : message.authorName || (message.authorRole === 'CLIENT' ? 'Customer' : message.authorRole)}</span>
                      <span>{fullDate(message.createdAt)}</span>
                    </div>
                    <p>{message.body}</p>
                  </div>
                ))}
              </div>

              {canManage && selected.status !== 'CLOSED' && (
                <div className="customer-care-composer">
                  <div className="customer-care-composer-toggle">
                    <button className={internal ? 'active' : ''} onClick={() => setInternal(v => !v)}>
                      {internal ? <StickyNote size={13} /> : <MessageCircle size={13} />}
                      {internal ? 'Internal note' : 'Customer reply'}
                    </button>
                    <span>{internal ? 'Only support staff can see this note.' : 'Visible to the customer.'}</span>
                  </div>
                  <textarea
                    value={compose}
                    onChange={event => setCompose(event.target.value)}
                    placeholder={internal ? 'Add an internal handoff note…' : 'Write a clear response to the customer…'}
                    maxLength={8000}
                  />
                  <div className="customer-care-compose-footer">
                    <span>{compose.length}/8000</span>
                    <button className="primary" disabled={!compose.trim() || saving} onClick={() => void sendReply()}>
                      <Send size={14} />
                      {saving ? 'Sending…' : internal ? 'Add note' : 'Send reply'}
                    </button>
                  </div>
                </div>
              )}

              <div className="customer-care-detail-foot">
                <span><Clock3 size={12} /> Updated {relativeDate(selected.updatedAt)}</span>
                <span><CheckCircle2 size={12} /> Created {fullDate(selected.createdAt)}</span>
              </div>
            </>
          )}
        </aside>
      </div>
    </section>
  );
}

function CustomerCareSelect({
  label,
  value,
  onChange,
  options,
  disabled = false,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: string[] | Array<{ value: string; label: string }>;
  disabled?: boolean;
}) {
  const normalized = options.map(option => typeof option === 'string' ? { value: option, label: statusLabel(option) } : option);
  return (
    <label className="customer-care-select">
      <span>{label}</span>
      <div>
        <select value={value} disabled={disabled} onChange={event => onChange(event.target.value)}>
          {normalized.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
        <ChevronDown size={13} />
      </div>
    </label>
  );
}

function CustomerCareStatus({ value }: { value: string }) {
  const tone = value === 'OPEN' ? 'open' : value === 'IN_PROGRESS' ? 'progress' : value === 'WAITING_FOR_CUSTOMER' ? 'waiting' : value === 'RESOLVED' ? 'resolved' : 'closed';
  return <span className={'customer-care-status ' + tone}>{statusLabel(value)}</span>;
}

function CustomerCarePriority({ value }: { value: string }) {
  return <span className={'customer-care-priority ' + value.toLowerCase()}>{value}</span>;
}
