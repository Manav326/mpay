'use client';

import { useEffect, useMemo, useState } from 'react';
import { RefreshCw, Search, WalletCards, CircleDollarSign, Smartphone, ArrowDownLeft, ArrowUpRight } from 'lucide-react';
import {
  getAdminFinancialRecharges,
  getAdminFinancialWalletHistory,
  getAdminFinancialWithdrawals,
  refreshAdminRecharge,
} from '@/lib/api';
import {
  AdminFinancialRechargeOperation,
  AdminFinancialWalletOperation,
  AdminFinancialWithdrawalOperation,
} from '@/lib/types';

const INR = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
const dt = (v: string) => new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(v));

function statusClass(value?: string) {
  return 'status ' + String(value || 'UNKNOWN').toLowerCase().replace(/[^a-z0-9]+/g, '-');
}

function maskUpi(value: string) {
  const parts = value.split('@');
  if (parts.length !== 2) return '••••';
  const left = parts[0];
  return (left.length <= 2 ? '••••' : left.slice(0, 2) + '••••') + '@' + parts[1];
}

function signedAmount(item: AdminFinancialWalletOperation) {
  const raw = Math.abs(Number(item.amount || 0));
  const credit = ['CREDIT', 'REFUND'].includes(String(item.type || '').toUpperCase());
  return (credit ? '+' : '-') + INR.format(raw);
}

export default function FinancialOperations({
  canRefreshRecharge,
  onNotice,
}: {
  canRefreshRecharge: boolean;
  onNotice: (message: string) => void;
}) {
  const [tab, setTab] = useState<'recharges' | 'withdrawals' | 'wallet'>('recharges');
  const [rechargeStatus, setRechargeStatus] = useState('ALL');
  const [rechargeProvider, setRechargeProvider] = useState('ALL');
  const [withdrawalStatus, setWithdrawalStatus] = useState('ALL');
  const [withdrawalProvider, setWithdrawalProvider] = useState('ALL');
  const [walletReferenceType, setWalletReferenceType] = useState('ALL');

  const [recharges, setRecharges] = useState<AdminFinancialRechargeOperation[]>([]);
  const [withdrawals, setWithdrawals] = useState<AdminFinancialWithdrawalOperation[]>([]);
  const [walletItems, setWalletItems] = useState<AdminFinancialWalletOperation[]>([]);
  const [rechargePage, setRechargePage] = useState(0);
  const [withdrawalPage, setWithdrawalPage] = useState(0);
  const [walletPage, setWalletPage] = useState(0);
  const [rechargeHasNext, setRechargeHasNext] = useState(false);
  const [withdrawalHasNext, setWithdrawalHasNext] = useState(false);
  const [walletHasNext, setWalletHasNext] = useState(false);
  const [loading, setLoading] = useState(false);
  const [refreshingId, setRefreshingId] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    try {
      if (tab === 'recharges') {
        const result = await getAdminFinancialRecharges(rechargePage, 25, rechargeStatus, rechargeProvider);
        setRecharges(result.items);
        setRechargeHasNext(result.hasNext);
      } else if (tab === 'withdrawals') {
        const result = await getAdminFinancialWithdrawals(withdrawalPage, 25, withdrawalStatus, withdrawalProvider);
        setWithdrawals(result.items);
        setWithdrawalHasNext(result.hasNext);
      } else {
        const result = await getAdminFinancialWalletHistory(walletPage, 25, walletReferenceType);
        setWalletItems(result.items);
        setWalletHasNext(result.hasNext);
      }
    } catch (err: any) {
      onNotice(err.message || 'Unable to load financial operations.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // Filters and the active page intentionally drive this request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, rechargePage, rechargeStatus, rechargeProvider, withdrawalPage, withdrawalStatus, withdrawalProvider, walletPage, walletReferenceType]);

  useEffect(() => {
    setRechargePage(0);
  }, [rechargeStatus, rechargeProvider]);
  useEffect(() => {
    setWithdrawalPage(0);
  }, [withdrawalStatus, withdrawalProvider]);
  useEffect(() => {
    setWalletPage(0);
  }, [walletReferenceType]);

  async function refreshRecharge(id: string) {
    setRefreshingId(id);
    try {
      await refreshAdminRecharge(id);
      onNotice('Recharge status refreshed from the provider workflow.');
      await load();
    } catch (err: any) {
      onNotice(err.message || 'Unable to refresh recharge status.');
    } finally {
      setRefreshingId(null);
    }
  }

  const visibleRows = useMemo(() => tab === 'recharges' ? recharges.length : tab === 'withdrawals' ? withdrawals.length : walletItems.length, [tab, recharges.length, withdrawals.length, walletItems.length]);

  return (
    <div className="content">
      <section className="panel financial-hero">
        <div className="financial-hero-copy">
          <span className="eyebrow">Protected operations</span>
          <h2>Financial operations</h2>
          <p>One operational view for recharge execution, withdrawal outcomes and the wallet ledger. State changes remain owned by provider and wallet workflows; the portal only triggers supported operational refreshes.</p>
        </div>
        <div className="financial-hero-actions">
          <div className="financial-trust"><WalletCards size={18}/><span>Ledger-first</span></div>
          <button className="secondary" onClick={load} disabled={loading}><RefreshCw size={15} className={loading ? 'spin' : ''}/> Refresh</button>
        </div>
      </section>

      <section className="panel financial-panel">
        <div className="financial-tabs">
          <button className={tab === 'recharges' ? 'active' : ''} onClick={() => setTab('recharges')}><Smartphone size={15}/> Recharges</button>
          <button className={tab === 'withdrawals' ? 'active' : ''} onClick={() => setTab('withdrawals')}><ArrowUpRight size={15}/> Withdrawals</button>
          <button className={tab === 'wallet' ? 'active' : ''} onClick={() => setTab('wallet')}><WalletCards size={15}/> Wallet ledger</button>
        </div>

        {tab === 'recharges' && (
          <>
            <div className="panel-head wrap financial-toolbar">
              <div><h2>Recharge operations</h2><p>Monitor persisted recharge attempts and refresh unresolved provider states.</p></div>
              <div className="filters">
                <select value={rechargeStatus} onChange={e => setRechargeStatus(e.target.value)}>
                  <option>ALL</option><option>RESERVED</option><option>PENDING</option><option>SUCCESS</option><option>FAILED</option>
                </select>
                <select value={rechargeProvider} onChange={e => setRechargeProvider(e.target.value)}>
                  <option>ALL</option><option>MOCK</option><option>PAYU</option>
                </select>
              </div>
            </div>
            {loading && visibleRows === 0 ? <div className="empty-state">Loading recharge operations…</div> : recharges.length === 0 ? <div className="empty-state">No recharge operations match these filters.</div> :
              <div className="history-table-wrap"><table className="history-table financial-table"><thead><tr><th>Transaction</th><th>User</th><th>Recharge</th><th>Amounts</th><th>Provider</th><th>Status</th><th>Action</th></tr></thead><tbody>
                {recharges.map(r => <tr key={r.transactionId}>
                  <td><b className="mono">{r.transactionId}</b><span>{dt(r.createdAt)}</span></td>
                  <td><b>{r.userName}</b><span>{r.userMobile} · {r.userPublicId}</span></td>
                  <td><b>{r.operator} · {r.mobileNumber}</b><span>{r.circle}</span></td>
                  <td><b>{INR.format(r.amount)}</b><span>Wallet debit {INR.format(r.walletDebitAmount)}</span><span>Client {INR.format(r.clientCommission)} · Company {INR.format(r.companyCommission)}</span></td>
                  <td><b>{r.provider}</b><span>{r.providerReference || r.providerOrderId || 'No provider ref'}</span></td>
                  <td><span className={statusClass(r.status)}>{r.status}</span>{r.message && <span>{r.message}</span>}</td>
                  <td>{canRefreshRecharge && ['RESERVED','PENDING'].includes((r.status || '').toUpperCase()) ? <button className="secondary table-action" disabled={refreshingId === r.transactionId} onClick={() => refreshRecharge(r.transactionId)}><RefreshCw size={13}/>{refreshingId === r.transactionId ? 'Checking…' : 'Refresh status'}</button> : <span>—</span>}</td>
                </tr>)}
              </tbody></table></div>}
            <Pagination page={rechargePage} hasNext={rechargeHasNext} onPrev={() => setRechargePage(p => Math.max(0, p - 1))} onNext={() => setRechargePage(p => p + 1)} />
          </>
        )}

        {tab === 'withdrawals' && (
          <>
            <div className="panel-head wrap financial-toolbar">
              <div><h2>Withdrawal operations</h2><p>Track payout state, provider acknowledgement and reserved-fund outcomes.</p></div>
              <div className="filters">
                <select value={withdrawalStatus} onChange={e => setWithdrawalStatus(e.target.value)}>
                  <option>ALL</option><option>PENDING</option><option>PROCESSING</option><option>SUCCESS</option><option>FAILED</option><option>REVERSED</option>
                </select>
                <select value={withdrawalProvider} onChange={e => setWithdrawalProvider(e.target.value)}>
                  <option>ALL</option><option>MOCK</option><option>RAZORPAY</option><option>PAYU</option>
                </select>
              </div>
            </div>
            {loading && visibleRows === 0 ? <div className="empty-state">Loading withdrawal operations…</div> : withdrawals.length === 0 ? <div className="empty-state">No withdrawal operations match these filters.</div> :
              <div className="history-table-wrap"><table className="history-table financial-table"><thead><tr><th>Withdrawal</th><th>User</th><th>Amount</th><th>UPI</th><th>Provider</th><th>Status</th><th>Outcome</th></tr></thead><tbody>
                {withdrawals.map(w => <tr key={w.withdrawalId}>
                  <td><b className="mono">{w.withdrawalId}</b><span>{dt(w.createdAt)}</span></td>
                  <td><b>{w.userName}</b><span>{w.userMobile} · {w.userPublicId}</span></td>
                  <td><b className="red-amount">{INR.format(w.amount)}</b></td>
                  <td><b>{maskUpi(w.upiId)}</b></td>
                  <td><b>{w.provider}</b><span>{w.providerReference || w.walletLedgerRef || 'No reference'}</span></td>
                  <td><span className={statusClass(w.status)}>{w.status}</span>{w.providerStatus && <span>{w.providerStatus}</span>}</td>
                  <td>{w.failureReason || (w.completedAt ? dt(w.completedAt) : 'Processing governed by provider workflow')}</td>
                </tr>)}
              </tbody></table></div>}
            <Pagination page={withdrawalPage} hasNext={withdrawalHasNext} onPrev={() => setWithdrawalPage(p => Math.max(0, p - 1))} onNext={() => setWithdrawalPage(p => p + 1)} />
          </>
        )}

        {tab === 'wallet' && (
          <>
            <div className="panel-head wrap financial-toolbar">
              <div><h2>Wallet ledger</h2><p>Canonical credits and debits across add-money, recharge, withdrawal and rental flows.</p></div>
              <div className="filters">
                <select value={walletReferenceType} onChange={e => setWalletReferenceType(e.target.value)}>
                  <option>ALL</option><option>ADD_MONEY</option><option>RECHARGE</option><option>WITHDRAWAL</option><option>RENTAL_PAYMENT</option><option>RENTAL_PAYOUT</option>
                </select>
              </div>
            </div>
            {loading && visibleRows === 0 ? <div className="empty-state">Loading wallet ledger…</div> : walletItems.length === 0 ? <div className="empty-state">No wallet ledger entries match this filter.</div> :
              <div className="history-table-wrap"><table className="history-table financial-table"><thead><tr><th>Date</th><th>User</th><th>Flow</th><th>Amount</th><th>Status</th><th>Reference</th><th>Description</th></tr></thead><tbody>
                {walletItems.map(w => <tr key={w.id}>
                  <td>{dt(w.createdAt)}</td>
                  <td><b>{w.userName}</b><span>{w.userMobile} · {w.userPublicId}</span></td>
                  <td><b>{w.referenceType || w.type}</b><span>{w.type}</span></td>
                  <td className={String(w.type).toUpperCase() === 'CREDIT' ? 'green' : 'red-amount'}><b>{signedAmount(w)}</b></td>
                  <td><span className={statusClass(w.status)}>{w.status}</span></td>
                  <td><span className="mono">{w.referenceId || w.externalRef}</span></td>
                  <td>{w.description || '—'}</td>
                </tr>)}
              </tbody></table></div>}
            <Pagination page={walletPage} hasNext={walletHasNext} onPrev={() => setWalletPage(p => Math.max(0, p - 1))} onNext={() => setWalletPage(p => p + 1)} />
          </>
        )}
      </section>
    </div>
  );
}

function Pagination({page,hasNext,onPrev,onNext}:{page:number;hasNext:boolean;onPrev:()=>void;onNext:()=>void}) {
  return <div className="panel-head financial-pagination"><span>Page {page + 1}</span><div className="filters"><button className="secondary" disabled={page === 0} onClick={onPrev}>Previous</button><button className="secondary" disabled={!hasNext} onClick={onNext}>Next</button></div></div>;
}
