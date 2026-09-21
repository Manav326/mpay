'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  ArrowRight, Car, CheckCircle2, Clock3, History, Home, LogOut, Menu,
  ReceiptText, RefreshCw, Smartphone, UserRound, WalletCards, X
} from 'lucide-react';

const base = process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, '') || 'http://localhost:8080';

type Wallet = { balance: number; availableBalance: number; reservedBalance: number };
type Me = { name?: string; mobile: string; email?: string; role: string; publicUserId: string };
type RechargeItem = {
  transactionId?: string; mobileNumber?: string; operator?: string; circle?: string;
  amount?: number; status?: string; createdAt?: string; updatedAt?: string;
  planDescription?: string | null; planValidity?: string | null; message?: string | null;
};
type WalletItem = {
  id?: string | number; type?: string; amount?: number; status?: string;
  referenceId?: string; description?: string; createdAt?: string;
};
type RentalCar = { id: string; name: string; category: string; seats: number; transmission: string; pricePerDay: number };
type RentalBooking = {
  bookingId: string; carName: string; pickup: string; drop: string;
  startDate: string; endDate: string; total: number; status: string; createdAt?: string;
};

async function api<T = any>(path: string, init?: RequestInit): Promise<T> {
  const token = localStorage.getItem('mpay_token');
  const r = await fetch(base + path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
      ...(init?.headers || {})
    }
  });
  if (!r.ok) throw new Error((await r.text()) || 'Request failed');
  return r.status === 204 ? (undefined as T) : r.json();
}


export default function Portal() {
  const [view, setView] = useState<'home'|'recharge'|'wallet'|'history'|'rental'|'account'>('home');
  const [drawer, setDrawer] = useState(false);
  const [wallet, setWallet] = useState<Wallet>();
  const [me, setMe] = useState<Me>();
  const [mobile, setMobile] = useState('');
  const [operator, setOperator] = useState<any>();
  const [plans, setPlans] = useState<any[]>([]);
  const [recharges, setRecharges] = useState<RechargeItem[]>([]);
  const [walletHistory, setWalletHistory] = useState<WalletItem[]>([]);
  const [cars, setCars] = useState<RentalCar[]>([]);
  const [bookings, setBookings] = useState<RentalBooking[]>([]);
  const [selectedCar, setSelectedCar] = useState<RentalCar>();
  const [rentalForm, setRentalForm] = useState({ pickup: '', drop: '', startDate: '', endDate: '' });
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');

  const money = (n: any) => '₹' + Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 });
  const days = useMemo(() => {
    if (!rentalForm.startDate || !rentalForm.endDate) return 1;
    const d = Math.ceil((new Date(rentalForm.endDate).getTime() - new Date(rentalForm.startDate).getTime()) / 86400000);
    return Math.max(1, d);
  }, [rentalForm.startDate, rentalForm.endDate]);

  async function loadHistory() {
    try {
      const [r, w] = await Promise.all([
        api<any>('/api/v1/recharge/history?page=0&size=25'),
        api<any>('/api/v1/wallet/history?page=0&size=25')
      ]);
      setRecharges(r?.items || r?.content || r || []);
      setWalletHistory(w?.items || w?.content || w || []);
    } catch (e: any) {
      setNotice(e.message || 'Unable to load transaction history.');
    }
  }

  async function loadRentalData() {
    try {
      const [available, existing] = await Promise.all([
        api<any>('/api/v1/car-rental/cars'),
        api<any>('/api/v1/car-rental/bookings?page=0&size=25')
      ]);
      setCars(available?.items || available || []);
      setBookings(existing?.items || existing?.content || existing || []);
    } catch (e: any) {
      setNotice(e.message || 'Unable to load rental inventory.');
    }
  }

  useEffect(() => {
    if (!localStorage.getItem('mpay_token')) { window.location.href = '/login'; return; }
    Promise.all([api('/api/v1/me'), api('/api/v1/wallet')])
      .then(([a, b]) => { setMe(a); setWallet(b); })
      .catch(() => { localStorage.removeItem('mpay_token'); window.location.href = '/login'; });
    loadHistory();
    loadRentalData();
  }, []);

  function logout() {
    localStorage.removeItem('mpay_token');
    localStorage.removeItem('mpay_refresh_token');
    window.location.href = '/';
  }

  async function detect() {
    setBusy(true); setNotice(''); setPlans([]);
    try {
      const d = await api('/api/v1/recharge/operator', {
        method: 'POST', body: JSON.stringify({ mobileNumber: mobile })
      });
      setOperator(d);
      if (d.providerOperator && d.circle) {
        const q = await api<any>(
          '/api/v1/recharge/plans?mobile=' + encodeURIComponent(mobile) +
          '&operator=' + encodeURIComponent(d.operator) +
          '&circle=' + encodeURIComponent(d.circle) +
          '&providerOperator=' + encodeURIComponent(d.providerOperator) +
          '&providerCircle=' + encodeURIComponent(d.providerCircle || '')
        );
        setPlans(q?.plans || q || []);
      }
    } catch (e: any) {
      setNotice(e.message || 'Unable to detect operator or plans.');
    } finally { setBusy(false); }
  }

  async function recharge(plan: any) {
    if (!confirm('Continue with ' + money(plan.amount) + ' recharge for ' + mobile + '?')) return;
    setBusy(true); setNotice('');
    try {
      await api('/api/v1/recharge', {
        method: 'POST',
        body: JSON.stringify({
          mobileNumber: mobile,
          planId: String(plan.id || plan.planId || plan.amount),
          amount: Number(plan.amount),
          clientRequestId: crypto.randomUUID()
        })
      });
      setNotice('Recharge request submitted. Check History for the final status.');
      await loadHistory();
      setView('history');
    } catch (e: any) {
      setNotice(e.message || 'Recharge could not be submitted.');
    } finally { setBusy(false); }
  }

  async function bookCar() {
    if (!selectedCar || !rentalForm.pickup || !rentalForm.startDate || !rentalForm.endDate) {
      setNotice('Select a car, pickup location and valid rental dates.');
      return;
    }
    setBusy(true); setNotice('');
    try {
      const result = await api<RentalBooking>('/api/v1/car-rental/bookings', {
        method: 'POST',
        body: JSON.stringify({
          carId: selectedCar.id,
          pickupLocation: rentalForm.pickup,
          dropLocation: rentalForm.drop || rentalForm.pickup,
          startDate: rentalForm.startDate,
          endDate: rentalForm.endDate,
        })
      });
      setBookings(b => [result, ...b]);
      setNotice('Booking request submitted. Your booking status is shown below.');
      setSelectedCar(undefined);
      await loadRentalData();
    } catch (e: any) {
      setNotice(e.message || 'Car rental booking could not be submitted.');
    } finally { setBusy(false); }
  }

  const menu = [
    ['home','Home',Home], ['recharge','Recharge',Smartphone], ['wallet','Wallet',WalletCards],
    ['history','History',History], ['rental','Car Rental',Car], ['account','Account',UserRound]
  ] as const;

  const status = (s?: string) => {
    const value = String(s || 'UNKNOWN').toUpperCase();
    return <span className={'status-pill status-' + value.toLowerCase()}>{value}</span>;
  };

  return <div className="portal-shell">
    <aside className={'portal-sidebar ' + (drawer ? 'open' : '')}>
      <div className="portal-side-head">
        <a className="landing-brand" href="/"><img src="/mpay-logo.png" alt="mPay"/><span>mPay</span></a>
        <button className="icon-btn mobile-only" onClick={() => setDrawer(false)}><X size={18}/></button>
      </div>
      <div className="portal-welcome"><span>Signed in as</span><b>{me?.name || 'mPay user'}</b><small>{me?.mobile || ''}</small></div>
      <nav>{menu.map(([key,label,Icon]) =>
        <button key={key} className={view === key ? 'portal-nav active' : 'portal-nav'}
          onClick={() => { setView(key); setDrawer(false); if (key === 'history') loadHistory(); if (key === 'rental') loadRentalData(); }}>
          <Icon size={18}/>{label}
        </button>)}</nav>
      <button className="portal-nav portal-logout" onClick={logout}><LogOut size={18}/>Logout</button>
    </aside>

    <main className="portal-main">
      <header className="portal-topbar">
        <button className="icon-btn mobile-only" onClick={() => setDrawer(true)}><Menu size={19}/></button>
        <div><span>mPay personal workspace</span><h1>
          {view === 'home' ? 'Good to see you.' : view === 'recharge' ? 'Mobile recharge' :
           view === 'wallet' ? 'Your wallet' : view === 'history' ? 'Transaction history' :
           view === 'rental' ? 'Car Rental' : 'Your account'}
        </h1></div>
        <div className="portal-avatar">{(me?.name || 'U').charAt(0).toUpperCase()}</div>
      </header>

      {notice && <div className="portal-notice">{notice}<button onClick={() => setNotice('')}><X size={14}/></button></div>}

      {view === 'home' && <section className="portal-content">
        <div className="portal-hero-card"><div><span>AVAILABLE TO SPEND</span><strong>{money(wallet?.availableBalance)}</strong>
          <p>Manage recharges, wallet activity and your everyday mobility services from one place.</p></div>
          <button className="landing-primary" onClick={() => setView('recharge')}>Recharge now <ArrowRight size={16}/></button>
        </div>
        <div className="portal-card-grid">
          <button onClick={() => setView('recharge')}><Smartphone/><b>Mobile recharge</b><span>Detect operator, compare plans and submit a recharge.</span></button>
          <button onClick={() => setView('wallet')}><WalletCards/><b>Wallet</b><span>See available, reserved and ledger balances.</span></button>
          <button onClick={() => setView('history')}><History/><b>History</b><span>Track every recharge and wallet transaction.</span></button>
          <button onClick={() => setView('rental')}><Car/><b>Car Rental</b><span>Choose a vehicle and submit a rental booking.</span></button>
        </div>
      </section>}

      {view === 'wallet' && <section className="portal-content">
        <div className="wallet-grid">
          <div className="wallet-big"><span>Total balance</span><strong>{money(wallet?.balance)}</strong></div>
          <div><span>Available</span><b>{money(wallet?.availableBalance)}</b></div>
          <div><span>Reserved</span><b>{money(wallet?.reservedBalance)}</b></div>
        </div>
        <div className="portal-panel"><div className="panel-head"><div><h2>Wallet ledger</h2><p>Authoritative balance movements recorded by mPay.</p></div>
          <button className="landing-secondary" onClick={loadHistory}><RefreshCw size={15}/> Refresh</button></div>
          {walletHistory.length ? <div className="history-list">{walletHistory.map((x,i) =>
            <div className="history-row" key={String(x.id || i)}><div><ReceiptText size={18}/><b>{x.description || x.type || 'Wallet transaction'}</b><small>{x.referenceId || '—'} · {x.createdAt ? new Date(x.createdAt).toLocaleString('en-IN') : '—'}</small></div><strong>{money(x.amount)}</strong>{status(x.status)}</div>)}</div>
          : <div className="empty-state">No wallet transactions were returned.</div>}
        </div>
      </section>}

      {view === 'history' && <section className="portal-content">
        <div className="portal-panel"><div className="panel-head"><div><h2>Recharge history</h2><p>Track submitted, pending, successful and failed recharges.</p></div>
          <button className="landing-secondary" onClick={loadHistory}><RefreshCw size={15}/> Refresh</button></div>
          {recharges.length ? <div className="history-list">{recharges.map((x,i) =>
            <div className="history-row" key={String(x.transactionId || i)}><div><ReceiptText size={18}/><b>{x.mobileNumber || 'Recharge'} · {x.operator || '—'}</b>
              <small>{x.planDescription || 'Plan'} · {x.transactionId || 'No reference'} · {x.createdAt ? new Date(x.createdAt).toLocaleString('en-IN') : '—'}</small></div>
              <strong>{money(x.amount)}</strong>{status(x.status)}</div>)}</div>
          : <div className="empty-state"><History size={22}/><b>No recharge history yet</b><span>Your completed and pending recharges will appear here.</span></div>}
        </div>
      </section>}

      {view === 'account' && <section className="portal-content"><div className="portal-panel account-panel">
        <div className="account-large-avatar">{(me?.name || 'U').charAt(0).toUpperCase()}</div><h2>{me?.name || 'mPay user'}</h2><p>{me?.publicUserId}</p>
        <div className="account-facts"><div><span>Mobile</span><b>{me?.mobile}</b></div><div><span>Email</span><b>{me?.email || 'Not provided'}</b></div><div><span>Account type</span><b>{me?.role || 'CLIENT'}</b></div></div>
      </div></section>}

      {view === 'recharge' && <section className="portal-content"><div className="portal-panel">
        <div className="panel-head"><div><h2>Recharge a mobile</h2><p>Detect the operator, load plans and submit the selected recharge.</p></div></div>
        <div className="recharge-web-form"><input inputMode="numeric" maxLength={10} value={mobile}
          onChange={e => setMobile(e.target.value.replace(/\D/g,''))} placeholder="10-digit mobile number"/>
          <button className="primary" disabled={busy || mobile.length !== 10} onClick={detect}>{busy ? 'Checking…' : 'Find plans'}</button></div>
        {operator && <div className="operator-result"><CheckCircle2 size={18}/><div><b>{operator.operator}</b><span>{operator.circle} · {operator.type || 'Mobile'}</span></div></div>}
        {plans.length > 0 && <div className="web-plan-grid">{plans.map((p:any) =>
          <div className="web-plan" key={p.id || p.planId || p.amount}><div><strong>{money(p.amount)}</strong><span>{p.validity || 'Plan'}</span></div>
            <p>{p.description || 'Recharge plan'}</p><button className="landing-secondary" disabled={busy} onClick={() => recharge(p)}>Continue <ArrowRight size={14}/></button></div>)}</div>}
        {operator && !plans.length && !busy && <div className="empty-state">No plans were returned for this number.</div>}
      </div></section>}

      {view === 'rental' && <section className="portal-content">
        <div className="portal-panel"><div className="panel-head"><div><h2>Choose a car</h2><p>Set your trip details, review the fare and submit a booking request.</p></div><Car size={28}/></div>
          <div className="rental-form">
            <input placeholder="Pickup location" value={rentalForm.pickup} onChange={e => setRentalForm({...rentalForm,pickup:e.target.value})}/>
            <input placeholder="Drop location (optional)" value={rentalForm.drop} onChange={e => setRentalForm({...rentalForm,drop:e.target.value})}/>
            <label>Start date<input type="date" value={rentalForm.startDate} onChange={e => setRentalForm({...rentalForm,startDate:e.target.value})}/></label>
            <label>End date<input type="date" value={rentalForm.endDate} min={rentalForm.startDate} onChange={e => setRentalForm({...rentalForm,endDate:e.target.value})}/></label>
          </div>
          <div className="rental-car-grid">{cars.map(car =>
            <button key={car.id} className={'rental-car ' + (selectedCar?.id === car.id ? 'selected' : '')} onClick={() => setSelectedCar(car)}>
              <div className="rental-car-icon"><Car size={26}/></div><b>{car.name}</b><span>{car.category} · {car.seats} seats · {car.transmission}</span><strong>{money(car.pricePerDay)} / day</strong>
            </button>)}</div>
          {selectedCar && <div className="rental-summary"><div><span>Selected</span><b>{selectedCar.name}</b></div><div><span>Duration</span><b>{days} day{days > 1 ? 's' : ''}</b></div><div><span>Estimated total</span><strong>{money(selectedCar.pricePerDay * days)}</strong></div>
            <button className="landing-primary" disabled={busy} onClick={bookCar}>{busy ? 'Submitting…' : 'Request booking'} <ArrowRight size={16}/></button></div>}
        </div>
        <div className="portal-panel"><div className="panel-head"><div><h2>My bookings</h2><p>Your rental booking status and references.</p></div><Clock3 size={22}/></div>
          {bookings.length ? <div className="history-list">{bookings.map(b =>
            <div className="history-row" key={b.bookingId}><div><Car size={18}/><b>{b.carName}</b><small>{b.bookingId} · {b.pickup} → {b.drop} · {b.startDate} to {b.endDate}</small></div><strong>{money(b.total)}</strong>{status(b.status)}</div>)}</div>
          : <div className="empty-state">No rental bookings yet.</div>}
        </div>
      </section>}
    </main>
  </div>;
}
