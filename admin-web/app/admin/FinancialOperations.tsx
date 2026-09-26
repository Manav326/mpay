'use client';

import { useEffect, useState } from 'react';
import {
  AlertCircle,
  ArrowDownLeft,
  ArrowUpRight,
  BadgeCheck,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  CircleDollarSign,
  Clock3,
  History,
  RefreshCw,
  ReceiptText,
  Smartphone,
  WalletCards,
  XCircle,
} from 'lucide-react';
import {
  getAdminRecharges,
  getAdminWalletLedger,
  getAdminWithdrawals,
  refreshAdminRecharge,
} from '@/lib/api';
import {
  AdminFinancialRechargeOperation,
  AdminFinancialWalletOperation,
  AdminFinancialWithdrawalOperation,
} from '@/lib/types';

const INR = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 2,
});
const dateTime = (value: string) =>
  new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
const pageSize = 25;

type Tab = 'recharges' | 'withdrawals' | 'ledger';

function statusMeta(value?: string) {
  const status = String(value || 'UNKNOWN').toUpperCase();
  if (['SUCCESS', 'COMPLETED', 'PAID', 'APPROVED'].includes(status)) {
    return { label: status, tone: 'success', Icon: BadgeCheck, description: 'Operation completed successfully.' };
  }
  if (['PENDING', 'PROCESSING'].includes(status)) {
    return { label: status, tone: 'pending', Icon: Clock3, description: 'Operation is still being processed.' };
  }
  if (['FAILED', 'REJECTED', 'CANCELLED', 'BLOCKED'].includes(status)) {
    return { label: status, tone: 'danger', Icon: XCircle, description: 'Operation did not complete.' };
  }
  return { label: status.replace(/_/g, ' '), tone: 'neutral', Icon: AlertCircle, description: 'Operation state is available for review.' };
}

function StatusBlock({ value, note }: { value?: string; note?: string | null }) {
  const meta = statusMeta(value);
  const Icon = meta.Icon;
  return (
    <div className={`finance-status-block ${meta.tone}`}>
      <span className="finance-status-pill"><Icon size={12} />{meta.label}</span>
      <small>{note || meta.description}</small>
    </div>
  );
}

function CustomerCell({ name, mobile, publicId }: { name: string; mobile: string; publicId: string }) {
  return (
    <div className="finance-customer-cell">
      <div className="finance-avatar">{String(name || 'U').charAt(0).toUpperCase()}</div>
      <div>
        <b>{name || 'Unnamed customer'}</b>
        <span><Smartphone size={10} /> {mobile || 'No mobile'}</span>
        <small>{publicId || 'No public ID'}</small>
      </div>
    </div>
  );
}

function DateCell({ value, reference }: { value: string; reference: string }) {
  return (
    <div className="finance-date-cell">
      <span><CalendarDays size={11} /> {dateTime(value)}</span>
      <small>{reference}</small>
    </div>
  );
}

export default function FinancialOperations({ canRefreshRecharge }: { canRefreshRecharge: boolean }) {
  const [tab, setTab] = useState<Tab>('recharges');
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('ALL');
  const [provider, setProvider] = useState('ALL');
  const [referenceType, setReferenceType] = useState('ALL');
  const [recharges, setRecharges] = useState<AdminFinancialRechargeOperation[]>([]);
  const [withdrawals, setWithdrawals] = useState<AdminFinancialWithdrawalOperation[]>([]);
  const [ledger, setLedger] = useState<AdminFinancialWalletOperation[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(false);
  const [busyRecharge, setBusyRecharge] = useState<string | null>(null);
  const [notice, setNotice] = useState('');

  async function load() {
    setLoading(true);
    setNotice('');
    try {
      if (tab === 'recharges') {
        const result = await getAdminRecharges(page, pageSize, status, provider);
        setRecharges(result.items);
        setTotalItems(result.totalItems);
        setHasNext(result.hasNext);
      } else if (tab === 'withdrawals') {
        const result = await getAdminWithdrawals(page, pageSize, status, provider);
        setWithdrawals(result.items);
        setTotalItems(result.totalItems);
        setHasNext(result.hasNext);
      } else {
        const result = await getAdminWalletLedger(page, pageSize, referenceType);
        setLedger(result.items);
        setTotalItems(result.totalItems);
        setHasNext(result.hasNext);
      }
    } catch (error: any) {
      setNotice(error?.message || 'Unable to load financial operations.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load().catch(() => undefined);
  }, [tab, page, status, provider, referenceType]);

  function switchTab(next: Tab) {
    setTab(next);
    setPage(0);
    setStatus('ALL');
    setProvider('ALL');
    setReferenceType('ALL');
  }

  async function refreshRecharge(transactionId: string) {
    setBusyRecharge(transactionId);
    try {
      await refreshAdminRecharge(transactionId);
      setNotice('Recharge status refreshed from the authoritative recharge workflow.');
      await load();
    } catch (error: any) {
      setNotice(error?.message || 'Unable to refresh recharge status.');
    } finally {
      setBusyRecharge(null);
    }
  }

  const tabMeta = {
    recharges: { label: 'Recharges', icon: ReceiptText, hint: 'Customer recharge attempts, wallet debit and provider state.' },
    withdrawals: { label: 'Withdrawals', icon: ArrowDownLeft, hint: 'UPI withdrawals, provider state and settlement references.' },
    ledger: { label: 'Wallet ledger', icon: History, hint: 'Authoritative wallet movements across every money flow.' },
  }[tab];
  const TabIcon = tabMeta.icon;

  return (
    <div className="content financial-operations-page">
      {notice && (
        <div className="admin-notice">
          <span>{notice}</span>
          <button onClick={() => setNotice('')}>Dismiss</button>
        </div>
      )}

      <section className="panel financial-operations-panel">
        <div className="financial-workspace-head">
          <div className="financial-workspace-title">
            <div className={`financial-workspace-icon ${tab}`}><TabIcon size={18} /></div>
            <div>
              <div className="eyebrow">Money operations</div>
              <h2>{tabMeta.label}</h2>
              <p>{tabMeta.hint}</p>
            </div>
            <span className="financial-record-count">{totalItems.toLocaleString('en-IN')}</span>
          </div>

          <div className="financial-tabs" role="tablist" aria-label="Money operations">
            {(Object.keys({
              recharges: 1, withdrawals: 1, ledger: 1,
            }) as Tab[]).map(key => {
              const meta = {
                recharges: { label: 'Recharges', Icon: ReceiptText },
                withdrawals: { label: 'Withdrawals', Icon: ArrowDownLeft },
                ledger: { label: 'Wallet ledger', Icon: History },
              }[key];
              const Icon = meta.Icon;
              return (
                <button key={key} className={tab === key ? 'active' : ''} onClick={() => switchTab(key)} role="tab" aria-selected={tab === key}>
                  <Icon size={14} />{meta.label}
                </button>
              );
            })}
          </div>
        </div>

        <div className="financial-filter-row">
          <div className="financial-filter-group">
            {tab !== 'ledger' ? (
              <>
                <label>Status<select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
                  <option>ALL</option><option>PENDING</option><option>PROCESSING</option><option>SUCCESS</option><option>FAILED</option><option>CANCELLED</option><option>REFUNDED</option>
                </select></label>
                <label>Provider<select value={provider} onChange={e => { setProvider(e.target.value); setPage(0); }}>
                  <option>ALL</option><option>MOCK</option><option>RAZORPAY</option><option>PAYU</option><option>WAY2API</option>
                </select></label>
              </>
            ) : (
              <label>Ledger type<select value={referenceType} onChange={e => { setReferenceType(e.target.value); setPage(0); }}>
                <option value="ALL">All ledger types</option><option value="ADD_MONEY">Add money</option><option value="RECHARGE">Recharge</option><option value="WITHDRAWAL">Withdrawal</option><option value="RENTAL_PAYMENT">Rental payment</option><option value="RENTAL_REFUND">Rental refund</option><option value="RENTAL_PAYOUT">Rental payout</option>
              </select></label>
            )}
          </div>
          <button className="secondary financial-refresh" onClick={() => load()} disabled={loading}><RefreshCw size={14} /> Refresh</button>
        </div>

        {loading ? <div className="empty-state">Loading financial operations…</div> :
          tab === 'recharges' ? <RechargeTable items={recharges} canRefresh={canRefreshRecharge} busy={busyRecharge} onRefresh={refreshRecharge}/> :
          tab === 'withdrawals' ? <WithdrawalTable items={withdrawals}/> :
          <LedgerTable items={ledger}/>}

        <div className="financial-pager">
          <span>Page {page + 1} · {totalItems.toLocaleString('en-IN')} records</span>
          <div>
            <button className="secondary" disabled={page === 0 || loading} onClick={() => setPage(p => Math.max(0, p - 1))}><ChevronLeft size={14}/> Previous</button>
            <button className="secondary" disabled={!hasNext || loading} onClick={() => setPage(p => p + 1)}>Next <ChevronRight size={14}/></button>
          </div>
        </div>
      </section>
    </div>
  );
}

function RechargeTable({ items, canRefresh, busy, onRefresh }: {
  items: AdminFinancialRechargeOperation[];
  canRefresh: boolean;
  busy: string | null;
  onRefresh: (id: string) => void;
}) {
  if (!items.length) return <div className="empty-state">No recharge operations match the selected filters.</div>;
  return (
    <div className="financial-operation-list">
      {items.map(item => {
        const refreshable = ['PENDING', 'PROCESSING'].includes(String(item.status).toUpperCase());
        return (
          <article className="financial-operation-row recharge-row" key={item.transactionId}>
            <div className="financial-row-date"><DateCell value={item.createdAt} reference={item.transactionId} /></div>
            <CustomerCell name={item.userName} mobile={item.userMobile} publicId={item.userPublicId} />
            <div className="financial-primary-cell">
              <div className="financial-heading-line"><ReceiptText size={13} /><b>{item.operator || 'Mobile recharge'}</b></div>
              <span>{item.mobileNumber || '—'} · {item.circle || 'Circle unavailable'}</span>
            </div>
            <div className="financial-money-stack">
              <strong>{INR.format(item.amount)}</strong>
              <span>Wallet debit {INR.format(item.walletDebitAmount)}</span>
              <small>Client {INR.format(item.clientCommission)} · Company {INR.format(item.companyCommission)}</small>
            </div>
            <div className="financial-provider-cell">
              <b>{item.provider || '—'}</b>
              <span>{item.providerReference || item.providerOrderId || 'No provider reference'}</span>
            </div>
            <StatusBlock value={item.status} note={item.message} />
            <div className="financial-action-cell">
              {canRefresh && refreshable
                ? <button className="secondary" disabled={busy === item.transactionId} onClick={() => onRefresh(item.transactionId)}><RefreshCw size={12}/>{busy === item.transactionId ? 'Refreshing…' : 'Refresh'}</button>
                : <span>—</span>}
            </div>
          </article>
        );
      })}
    </div>
  );
}

function WithdrawalTable({ items }: { items: AdminFinancialWithdrawalOperation[] }) {
  if (!items.length) return <div className="empty-state">No withdrawal operations match the selected filters.</div>;
  return (
    <div className="financial-operation-list">
      {items.map(item => (
        <article className="financial-operation-row withdrawal-row" key={item.withdrawalId}>
          <div className="financial-row-date"><DateCell value={item.createdAt} reference={item.withdrawalId} /></div>
          <CustomerCell name={item.userName} mobile={item.userMobile} publicId={item.userPublicId} />
          <div className="financial-amount-focus">
            <div><ArrowDownLeft size={14}/><strong>{INR.format(item.amount)}</strong></div>
            <span>Withdrawal request</span>
          </div>
          <div className="financial-destination-cell">
            <div><CircleDollarSign size={14}/><b>{item.upiId || 'UPI not available'}</b></div>
            <span>UPI destination</span>
          </div>
          <div className="financial-provider-cell">
            <b>{item.provider || '—'}</b>
            <span>{item.providerStatus || 'Provider status unavailable'}</span>
          </div>
          <StatusBlock value={item.status} note={item.failureReason || item.providerStatus} />
          <div className="financial-reference-cell">
            <span>Provider ref</span>
            <b className="mono">{item.providerReference || '—'}</b>
            <small>Ledger {item.walletLedgerRef || '—'}</small>
          </div>
        </article>
      ))}
    </div>
  );
}

function LedgerTable({ items }: { items: AdminFinancialWalletOperation[] }) {
  if (!items.length) return <div className="empty-state">No wallet ledger records match the selected filter.</div>;
  return (
    <div className="financial-operation-list">
      {items.map(item => {
        const type = String(item.type || '').toUpperCase();
        const credit = ['CREDIT', 'ADD_MONEY', 'RENTAL_REFUND'].includes(type);
        const reference = String(item.referenceType || type || 'WALLET').replace(/_/g, ' ');
        return (
          <article className="financial-operation-row ledger-row" key={item.id}>
            <div className="financial-row-date"><DateCell value={item.createdAt} reference={String(item.externalRef || item.id)} /></div>
            <CustomerCell name={item.userName} mobile={item.userMobile} publicId={item.userPublicId} />
            <div className={`financial-flow-focus ${credit ? 'credit' : 'debit'}`}>
              <span><>{credit ? <ArrowUpRight size={14}/> : <ArrowDownLeft size={14}/>}</> {reference}</span>
              <strong>{credit ? '+' : '-'}{INR.format(item.amount)}</strong>
            </div>
            <div className="financial-reference-cell">
              <span>Reference ID</span>
              <b className="mono">{item.referenceId || item.externalRef || '—'}</b>
            </div>
            <div className="financial-description-cell">
              <b>{item.description || reference}</b>
              <span>Wallet movement</span>
            </div>
            <StatusBlock value={item.status} note={item.description} />
          </article>
        );
      })}
    </div>
  );
}
