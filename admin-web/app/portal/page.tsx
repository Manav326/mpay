'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  ArrowRight, Car, CheckCircle2, Clock3, History, Home, LogOut, Menu,
  Copy, ReceiptText, RefreshCw, Smartphone, UserRound, WalletCards, X
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
type RentalQuote = {
  carId: string; carName: string; driverName: string; pickup: string; drop: string;
  startDate: string; endDate: string; days: number; pricePerDay: number; total: number;
};
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
  const [view, setView] = useState<'home'|'recharge'|'wallet'|'history'|'marketplace'|'rental'|'bookings'|'account'>('home');
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
  const [rentalQuote, setRentalQuote] = useState<RentalQuote>();
  const [rentalForm, setRentalForm] = useState({ pickup: '', drop: '', startDate: '', endDate: '' });
  const [rentalSearch, setRentalSearch] = useState({ location: '', startDate: '', endDate: '' });
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');

  const money = (n: any) => '₹' + Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 });
  const walletSignedAmount = (item: WalletItem) => {
    const amount = Math.abs(Number(item.amount || 0));
    return String(item.type || '').toUpperCase() === 'DEBIT' ? -amount : amount;
  };
  const walletAmountClass = (item: WalletItem) =>
    String(item.type || '').toUpperCase() === 'DEBIT' ? 'amount-debit' : 'amount-credit';
  const walletAmountLabel = (item: WalletItem) => {
    const amount = Math.abs(Number(item.amount || 0));
    return (walletSignedAmount(item) > 0 ? '+' : '-') + money(amount);
  };
  async function copyText(value: string, successMessage = 'Copied to clipboard.') {
    try {
      await navigator.clipboard.writeText(value);
      setNotice(successMessage);
    } catch {
      setNotice('Unable to copy. Please copy the reference manually.');
    }
  }
  const bookingShareText = (b: RentalBooking) =>
    [
      'mPay Car Rental Booking',
      'Booking ID: ' + b.bookingId,
      'Car: ' + b.carName,
      'From: ' + b.pickup,
      'To: ' + b.drop,
      'Start: ' + new Date(b.startDate).toLocaleString('en-IN'),
      'End: ' + new Date(b.endDate).toLocaleString('en-IN'),
      'Amount: ' + money(b.total),
      'Status: ' + String(b.status || 'UNKNOWN').toUpperCase()
    ].join(' | ');
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

  async function loadRentalData(startDate = '', endDate = '', location = '') {
    try {
      const params = new URLSearchParams();
      if (startDate) params.set('startDate', startDate);
      if (endDate) params.set('endDate', endDate);
      if (location.trim()) params.set('location', location.trim());
      const carsPath = '/api/v1/car-rental/cars' + (params.toString() ? '?' + params.toString() : '');
      const [available, existing] = await Promise.all([
        api<any>(carsPath),
        api<any>('/api/v1/car-rental/bookings?page=0&size=25')
      ]);
      setCars(available?.items || available || []);
      setBookings(existing?.items || existing?.content || existing || []);
    } catch (e: any) {
      setNotice(e.message || 'Unable to load rental inventory.');
    }
  }

  function searchRentalCars() {
    const location = rentalSearch.location.trim();
    const hasLocation = Boolean(location);
    const hasDates = Boolean(rentalSearch.startDate && rentalSearch.endDate);
    if (!hasLocation && !hasDates) {
      setNotice('Enter a city/pickup area or select both rental dates to search.');
      return;
    }
    if ((rentalSearch.startDate && !rentalSearch.endDate) || (!rentalSearch.startDate && rentalSearch.endDate)) {
      setNotice('Select both the start and end date & time.');
      return;
    }
    if (hasDates && new Date(rentalSearch.endDate).getTime() <= new Date(rentalSearch.startDate).getTime()) {
      setNotice('End date & time must be after the start date & time.');
      return;
    }
    setSelectedCar(undefined);
    setRentalQuote(undefined);
    setRentalForm(form => ({
      ...form,
      startDate: rentalSearch.startDate || form.startDate,
      endDate: rentalSearch.endDate || form.endDate
    }));
    loadRentalData(rentalSearch.startDate, rentalSearch.endDate, location);
  }

  function clearRentalSearch() {
    setRentalSearch({ location: '', startDate: '', endDate: '' });
    setSelectedCar(undefined);
    setRentalQuote(undefined);
    loadRentalData();
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

  async function checkRentalFare() {
    if (!selectedCar || !rentalForm.pickup || !rentalForm.startDate || !rentalForm.endDate) {
      setNotice('Select a car, pickup location and valid rental dates.');
      return;
    }
    setBusy(true); setNotice(''); setRentalQuote(undefined);
    try {
      const quote = await api<RentalQuote>('/api/v1/car-rental/bookings/quote', {
        method: 'POST',
        body: JSON.stringify({
          carId: selectedCar.id,
          pickupLocation: rentalForm.pickup,
          dropLocation: rentalForm.drop || rentalForm.pickup,
          startDate: rentalForm.startDate,
          endDate: rentalForm.endDate
        })
      });
      setRentalQuote(quote);
    } catch (e: any) {
      setNotice(e.message || 'Unable to calculate rental fare.');
    } finally { setBusy(false); }
  }

  async function bookCar() {
    if (!rentalQuote || !selectedCar) {
      setNotice('Check the fare before confirming the booking.');
      return;
    }
    setBusy(true); setNotice('');
    try {
      const result = await api<RentalBooking>('/api/v1/car-rental/bookings', {
        method: 'POST',
        body: JSON.stringify({
          clientRequestId: crypto.randomUUID(),
          carId: selectedCar.id,
          pickupLocation: rentalQuote.pickup,
          dropLocation: rentalQuote.drop,
          startDate: rentalQuote.startDate,
          endDate: rentalQuote.endDate
        })
      });
      setBookings(b => [result, ...b]);
      setNotice('Booking confirmed. Your booking status is shown below.');
      setSelectedCar(undefined);
      setRentalQuote(undefined);
      await loadRentalData();
    } catch (e: any) {
      setNotice(e.message || 'Car rental booking could not be submitted.');
    } finally { setBusy(false); }
  }

  const menu = [
    ['home','Home',Home], ['recharge','Recharge',Smartphone], ['wallet','Wallet',WalletCards],
    ['history','History',History], ['marketplace','Marketplace',Car], ['bookings','My Bookings',Clock3], ['account','Account',UserRound]
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
          onClick={() => { setView(key); setDrawer(false); if (key === 'history') loadHistory(); if (key === 'marketplace' || key === 'bookings') loadRentalData(); }}>
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
           view === 'marketplace' ? 'Marketplace' : view === 'rental' ? 'Marketplace · Car Rental' : view === 'bookings' ? 'My Bookings' : 'Your account'}
        </h1></div>
        <div className="portal-avatar">{(me?.name || 'U').charAt(0).toUpperCase()}</div>
      </header>

      {notice && <div className="portal-notice">{notice}<button onClick={() => setNotice('')}><X size={14}/></button></div>}

      {view === 'home' && <section className="portal-content">
        <div className="portal-hero-card"><div><span>AVAILABLE TO SPEND</span><strong>{money(wallet?.availableBalance)}</strong>
          <p>Manage recharges, wallet activity and your everyday mobility services from one place.</p></div>
          <button className="landing-primary" onClick={() => setView('recharge')}>Recharge now <ArrowRight size={16}/></button>
        </div>
        <div className="portal-quick-actions">
          <button onClick={() => setView('recharge')}><Smartphone/><span>Mobile Recharge</span></button>
          <button onClick={() => setView('recharge')}><WalletCards/><span>Add Money</span></button>
          <button onClick={() => { setView('bookings'); loadRentalData(); }}><Clock3/><span>My Bookings</span></button>
        </div>
        <section className="home-marketplace">
          <div className="home-section-label">Marketplace</div>
          <button className="home-marketplace-card" onClick={() => { setView('rental'); loadRentalData(); }}>
            <div className="home-marketplace-icon"><Car size={27}/></div>
            <div className="home-marketplace-copy">
              <span>CHAUFFEUR-DRIVEN MOBILITY</span>
              <b>Car Rental</b>
              <p>Choose a chauffeur-driven car, set your trip time and book directly from Home.</p>
            </div>
            <ArrowRight size={19}/>
          </button>
        </section>
        <button className="home-recharge-history" onClick={() => { setView('history'); loadHistory(); }}>
          <div className="home-recharge-history-icon"><History size={22}/></div>
          <div>
            <span>TRANSACTION HISTORY</span>
            <b>Recharge History</b>
            <p>View your submitted, pending and completed mobile recharges.</p>
          </div>
          <ArrowRight size={18}/>
        </button>
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
            <div className="history-row" key={String(x.id || i)}>
              <div><ReceiptText size={18}/><b>{x.description || x.type || 'Wallet transaction'}</b><small>{x.referenceId || '—'} · {x.createdAt ? new Date(x.createdAt).toLocaleString('en-IN') : '—'}</small></div>
              <strong className={walletAmountClass(x)}>{walletAmountLabel(x)}</strong>
              <div className="history-actions">{status(x.status)}{x.referenceId && <button className="copy-btn" title="Copy transaction reference" onClick={() => copyText(String(x.referenceId), 'Transaction reference copied.')}><Copy size={14}/><span>Copy</span></button>}</div>
            </div>)}</div>
          : <div className="empty-state">No wallet transactions were returned.</div>}
        </div>
      </section>}

      {view === 'history' && <section className="portal-content">
        <div className="portal-panel"><div className="panel-head"><div><h2>Recharge history</h2><p>Track submitted, pending, successful and failed recharges.</p></div>
          <button className="landing-secondary" onClick={loadHistory}><RefreshCw size={15}/> Refresh</button></div>
          {recharges.length ? <div className="history-list">{recharges.map((x,i) =>
            <div className="history-row" key={String(x.transactionId || i)}>
              <div><ReceiptText size={18}/><b>{x.mobileNumber || 'Recharge'} · {x.operator || '—'}</b>
              <small>{x.planDescription || 'Plan'} · {x.transactionId || 'No reference'} · {x.createdAt ? new Date(x.createdAt).toLocaleString('en-IN') : '—'}</small></div>
              <strong>{money(x.amount)}</strong>
              <div className="history-actions">{status(x.status)}{x.transactionId && <button className="copy-btn" title="Copy transaction reference" onClick={() => copyText(String(x.transactionId), 'Transaction reference copied.')}><Copy size={14}/><span>Copy</span></button>}</div>
            </div>)}</div>
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



      {view === 'marketplace' && <section className="portal-content">
        <div className="portal-panel"><div className="panel-head"><div><h2>Marketplace</h2><p>Explore mPay service categories.</p></div><Car size={28}/></div>
          <button className="rental-car selected" onClick={() => { setView('rental'); loadRentalData(); }}>
            <div className="rental-car-icon"><Car size={26}/></div><b>Car Rental</b><span>NEW · Chauffeur-driven cars</span><strong>Open marketplace</strong>
          </button>
        </div>
      </section>}

      {view === 'bookings' && <section className="portal-content">
        <div className="portal-panel"><div className="panel-head"><div><h2>My Bookings</h2><p>Booked cars, chauffeur details, trip timing and payment status.</p></div><button className="landing-secondary" onClick={() => loadRentalData()}><RefreshCw size={15}/> Refresh</button></div>
          {bookings.length ? <div className="history-list">{bookings.map(b =>
            <div className="history-row" key={b.bookingId}><div><Car size={18}/><b>{b.carName}</b>
              <small>{b.bookingId} · {b.pickup} → {b.drop} · {new Date(b.startDate).toLocaleString('en-IN')} to {new Date(b.endDate).toLocaleString('en-IN')}</small>
            </div><strong>{money(b.total)}</strong><div className="history-actions">{status(b.status)}<span>{new Date(b.createdAt || b.startDate).toLocaleString('en-IN')}</span><button className="copy-btn" title="Copy booking details" onClick={() => copyText(bookingShareText(b), 'Booking details copied.')}><Copy size={14}/><span>Copy</span></button></div></div>
          )}</div> : <div className="empty-state">No rental bookings yet.</div>}
        </div>
      </section>}

      {view === 'rental' && <section className="portal-content">
        <div className="portal-panel"><div className="panel-head"><div><h2>Choose a car</h2><p>Set your trip details, review the fare and submit a booking request.</p></div><Car size={28}/></div>
          <div className="rental-search-card">
            <div>
              <b>Find available cars</b>
              <span>Search by city or pickup area, and optionally narrow results to a rental date & time.</span>
            </div>
            <div className="rental-search-grid">
              <label>City or pickup area<input value={rentalSearch.location} placeholder="e.g. Patna, Airport Road" onChange={e => setRentalSearch({...rentalSearch, location:e.target.value})}/></label>
              <label>From<input type="datetime-local" value={rentalSearch.startDate} min={new Date().toISOString().slice(0,16)} onChange={e => setRentalSearch({...rentalSearch,startDate:e.target.value})}/></label>
              <label>To<input type="datetime-local" value={rentalSearch.endDate} min={rentalSearch.startDate || new Date().toISOString().slice(0,16)} onChange={e => setRentalSearch({...rentalSearch,endDate:e.target.value})}/></label>
            </div>
            <div className="rental-search-actions">
              <button className="landing-secondary" onClick={clearRentalSearch}>Clear</button>
              <button className="landing-primary" onClick={searchRentalCars}>Find cars <ArrowRight size={16}/></button>
            </div>
            <small>Location matching is based on the vehicle city and pickup address. Availability remains enforced by the backend.</small>
          </div>
          <div className="rental-form">
            <input placeholder="Pickup location for booking" value={rentalForm.pickup} onChange={e => setRentalForm({...rentalForm,pickup:e.target.value})}/>
            <input placeholder="Drop location (optional)" value={rentalForm.drop} onChange={e => setRentalForm({...rentalForm,drop:e.target.value})}/>
            <label>Start date & time<input type="datetime-local" value={rentalForm.startDate} min={new Date().toISOString().slice(0,16)} onChange={e => setRentalForm({...rentalForm,startDate:e.target.value})}/></label>
            <label>End date & time<input type="datetime-local" value={rentalForm.endDate} min={rentalForm.startDate} onChange={e => setRentalForm({...rentalForm,endDate:e.target.value})}/></label>
          </div>
          {cars.length ? <div className="rental-car-grid">{cars.map(car =>
            <button key={car.id} className={'rental-car ' + (selectedCar?.id === car.id ? 'selected' : '')} onClick={() => {
              setSelectedCar(car);
              setRentalQuote(undefined);
              setRentalForm(form => ({
                ...form,
                startDate: rentalSearch.startDate || form.startDate,
                endDate: rentalSearch.endDate || form.endDate
              }));
            }}>
              <div className="rental-car-icon"><Car size={26}/></div><b>{car.name}</b><span>{car.category} · {car.seats} seats · {car.transmission}</span><strong>{money(car.pricePerDay)} / day</strong>
            </button>)}</div> : <div className="rental-empty-state">
              <div className="rental-empty-icon"><Car size={28}/></div>
              <b>{rentalSearch.location || rentalSearch.startDate || rentalSearch.endDate ? 'No cars match this search' : 'No cars available right now'}</b>
              <span>{rentalSearch.location || rentalSearch.startDate || rentalSearch.endDate ? 'Try a different city or pickup area, or choose another rental window.' : 'There are no approved chauffeur-driven cars available for your account at the moment. New vehicles will appear here as soon as they are approved.'}</span>
              <button className="landing-secondary" onClick={loadRentalData}><RefreshCw size={15}/> Check again</button>
            </div>}
          {selectedCar && <div className="rental-summary">
            <div><span>Selected</span><b>{selectedCar.name}</b></div>
            <div><span>Billing</span><b>{rentalQuote ? rentalQuote.days + ' day' + (rentalQuote.days > 1 ? 's' : '') : 'Check fare'}</b></div>
            <div><span>Total</span><strong>{rentalQuote ? money(rentalQuote.total) : '—'}</strong></div>
            {!rentalQuote
              ? <button className="landing-primary" disabled={busy} onClick={checkRentalFare}>{busy ? 'Calculating…' : 'Check fare'} <ArrowRight size={16}/></button>
              : <button className="landing-primary" disabled={busy} onClick={bookCar}>{busy ? 'Confirming…' : 'Confirm booking'} <ArrowRight size={16}/></button>}
            <p className="rental-pricing-note">Price is per 24-hour day. Any partial day is charged as one full day; time is used for duration and availability.</p>
          </div>}
        </div>
        <div className="portal-panel"><div className="panel-head"><div><h2>My bookings</h2><p>Your rental booking status and references.</p></div><Clock3 size={22}/></div>
          {bookings.length ? <div className="history-list">{bookings.map(b =>
            <div className="history-row" key={b.bookingId}><div><Car size={18}/><b>{b.carName}</b><small>{b.bookingId} · {b.pickup} → {b.drop} · {b.startDate} to {b.endDate}</small></div><strong>{money(b.total)}</strong><div className="history-actions">{status(b.status)}<button className="copy-btn" title="Copy booking details" onClick={() => copyText(bookingShareText(b), 'Booking details copied.')}><Copy size={14}/><span>Copy</span></button>{b.status === 'CONFIRMED' && new Date(b.startDate).getTime() > Date.now() && <button className="text-danger-btn" disabled={busy} onClick={async()=>{if(!confirm('Cancel this booking and refund the wallet amount?')) return; setBusy(true); try { await api('/api/v1/car-rental/bookings/'+encodeURIComponent(b.bookingId)+'/cancel',{method:'POST'}); setNotice('Booking cancelled and the wallet amount was refunded.'); await loadRentalData(); const w=await api('/api/v1/wallet'); setWallet(w); } catch(e:any){ setNotice(e.message || 'Unable to cancel booking.'); } finally { setBusy(false); }}}>Cancel</button>}</div></div>)}</div>
          : <div className="empty-state">No rental bookings yet.</div>}
        </div>
      </section>}
    </main>
  </div>;
}
