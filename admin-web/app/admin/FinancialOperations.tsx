'use client';

import { useEffect, useState } from 'react';
import { ArrowDownLeft, ArrowUpRight, ChevronLeft, ChevronRight, History, RefreshCw, ReceiptText, WalletCards } from 'lucide-react';
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
const statusClass = (value?: string) =>
  'status ' + String(value || 'UNKNOWN').toLowerCase().replace(/[^a-z0-9]+/g, '-');
const pageSize = 25;

type Tab = 'recharges' | 'withdrawals' | 'ledger';

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

  return (
    <div className="content">
      {notice && (
        <div className="admin-notice">
          <span>{notice}</span>
          <button onClick={() => setNotice('')}>Dismiss</button>
        </div>
      )}

      <section className="panel">
        <div className="panel-head wrap">
          <div>
            <h2>Financial operations</h2>
            <p>Operational visibility across recharge, withdrawals and the wallet ledger. Money state changes remain owned by their provider workflows.</p>
          </div>
          <div className="filters">
            <button className={tab === 'recharges' ? 'secondary active-tab' : 'secondary'} onClick={() => switchTab('recharges')}>
              <ReceiptText size={15} /> Recharges
            </button>
            <button className={tab === 'withdrawals' ? 'secondary active-tab' : 'secondary'} onClick={() => switchTab('withdrawals')}>
              <ArrowDownLeft size={15} /> Withdrawals
            </button>
            <button className={tab === 'ledger' ? 'secondary active-tab' : 'secondary'} onClick={() => switchTab('ledger')}>
              <History size={15} /> Wallet ledger
            </button>
          </div>
        </div>

        <div className="filters admin-finance-filters">
          {tab !== 'ledger' ? (
            <>
              <select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
                <option>ALL</option>
                <option>PENDING</option>
                <option>PROCESSING</option>
                <option>SUCCESS</option>
                <option>FAILED</option>
                <option>CANCELLED</option>
                <option>REFUNDED</option>
              </select>
              <select value={provider} onChange={e => { setProvider(e.target.value); setPage(0); }}>
                <option>ALL</option>
                <option>MOCK</option>
                <option>RAZORPAY</option>
                <option>PAYU</option>
                <option>WAY2API</option>
              </select>
            </>
          ) : (
            <select value={referenceType} onChange={e => { setReferenceType(e.target.value); setPage(0); }}>
              <option value="ALL">All ledger types</option>
              <option value="ADD_MONEY">Add money</option>
              <option value="RECHARGE">Recharge</option>
              <option value="WITHDRAWAL">Withdrawal</option>
              <option value="RENTAL_PAYMENT">Rental payment</option>
              <option value="RENTAL_REFUND">Rental refund</option>
              <option value="RENTAL_PAYOUT">Rental payout</option>
            </select>
          )}
          <button className="secondary" onClick={() => load()} disabled={loading}>
            <RefreshCw size={14} /> Refresh
          </button>
          <span className="finance-result-count">{totalItems.toLocaleString('en-IN')} records</span>
        </div>

        {loading ? <div className="empty-state">Loading financial operations…</div> :
          tab === 'recharges' ? <RechargeTable items={recharges} canRefresh={canRefreshRecharge} busy={busyRecharge} onRefresh={refreshRecharge}/> :
          tab === 'withdrawals' ? <WithdrawalTable items={withdrawals}/> :
          <LedgerTable items={ledger}/>}
        
        <div className="panel-head finance-pager">
          <span>Page {page + 1}</span>
          <div className="filters">
            <button className="secondary" disabled={page === 0 || loading} onClick={() => setPage(p => Math.max(0, p - 1))}><ChevronLeft size={15}/> Previous</button>
            <button className="secondary" disabled={!hasNext || loading} onClick={() => setPage(p => p + 1)}>Next <ChevronRight size={15}/></button>
          </div>
        </div>
      </section>
    </div>
  );
}

function RechargeTable({
  items, canRefresh, busy, onRefresh,
}: {
  items: AdminFinancialRechargeOperation[];
  canRefresh: boolean;
  busy: string | null;
  onRefresh: (id: string) => void;
}) {
  if (!items.length) return <div className="empty-state">No recharge operations match the selected filters.</div>;
  return <div className="table-wrap"><table><thead><tr>
    <th>Date</th><th>Customer</th><th>Recharge</th><th>Money flow</th><th>Provider</th><th>Status</th><th>Action</th>
  </tr></thead><tbody>
    {items.map(item => {
      const refreshable = ['PENDING', 'PROCESSING'].includes(item.status.toUpperCase());
      return <tr key={item.transactionId}>
        <td><b>{dateTime(item.createdAt)}</b><span className="mono">{item.transactionId}</span></td>
        <td><b>{item.userName}</b><span>{item.userMobile} · {item.userPublicId}</span></td>
        <td><b>{item.operator} · {item.mobileNumber}</b><span>{item.circle}</span></td>
        <td><b>{INR.format(item.amount)}</b><span>Wallet debit {INR.format(item.walletDebitAmount)}</span><span>Client {INR.format(item.clientCommission)} · Company {INR.format(item.companyCommission)}</span></td>
        <td>{item.provider || '—'}<span>{item.providerReference || item.providerOrderId || 'No provider ref'}</span></td>
        <td><span className={statusClass(item.status)}>{item.status}</span>{item.message && <span>{item.message}</span>}</td>
        <td>{canRefresh && refreshable ? <button className="secondary" disabled={busy === item.transactionId} onClick={() => onRefresh(item.transactionId)}>{busy === item.transactionId ? 'Refreshing…' : 'Refresh status'}</button> : <span>—</span>}</td>
      </tr>;
    })}
  </tbody></table></div>;
}

function WithdrawalTable({ items }: { items: AdminFinancialWithdrawalOperation[] }) {
  if (!items.length) return <div className="empty-state">No withdrawal operations match the selected filters.</div>;
  return <div className="table-wrap"><table><thead><tr>
    <th>Date</th><th>Customer</th><th>Amount</th><th>UPI</th><th>Provider</th><th>Status</th><th>References</th>
  </tr></thead><tbody>
    {items.map(item => <tr key={item.withdrawalId}>
      <td><b>{dateTime(item.createdAt)}</b><span className="mono">{item.withdrawalId}</span></td>
      <td><b>{item.userName}</b><span>{item.userMobile} · {item.userPublicId}</span></td>
      <td><b className="red-amount">{INR.format(item.amount)}</b></td>
      <td><b>{item.upiId}</b></td>
      <td>{item.provider || '—'}<span>{item.providerStatus || 'No provider status'}</span></td>
      <td><span className={statusClass(item.status)}>{item.status}</span>{item.failureReason && <span>{item.failureReason}</span>}</td>
      <td><span className="mono">{item.providerReference || item.walletLedgerRef || '—'}</span></td>
    </tr>)}
  </tbody></table></div>;
}

function LedgerTable({ items }: { items: AdminFinancialWalletOperation[] }) {
  if (!items.length) return <div className="empty-state">No wallet ledger records match the selected filter.</div>;
  return <div className="table-wrap"><table><thead><tr>
    <th>Date</th><th>Customer</th><th>Flow</th><th>Amount</th><th>Reference</th><th>Description</th>
  </tr></thead><tbody>
    {items.map(item => {
      const type = item.type.toUpperCase();
      const credit = type === 'CREDIT';
      return <tr key={item.id}>
        <td><b>{dateTime(item.createdAt)}</b><span className={statusClass(item.status)}>{item.status}</span></td>
        <td><b>{item.userName}</b><span>{item.userMobile} · {item.userPublicId}</span></td>
        <td><span className={'flow-pill ' + (credit ? 'flow-credit' : 'flow-debit')}>{credit ? <ArrowUpRight size={13}/> : <ArrowDownLeft size={13}/>} {item.referenceType || type}</span></td>
        <td><b className={credit ? 'green' : 'red-amount'}>{credit ? '+' : '-'}{INR.format(item.amount)}</b></td>
        <td><span className="mono">{item.referenceId || item.externalRef}</span></td>
        <td>{item.description || '—'}</td>
      </tr>;
    })}
  </tbody></table></div>;
}
