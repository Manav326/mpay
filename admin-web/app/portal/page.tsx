'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  ArrowRight, Banknote, CalendarDays, Camera, Car, CarFront, Check, CheckCircle2, ChevronLeft,
  ChevronRight, CircleDollarSign, Clock3, Copy, Edit3, Eye, History, Home, LogOut, Menu,
  Plus, ReceiptText, RefreshCw, Save, Smartphone, Trash2, Upload, UserRound, WalletCards, X
} from 'lucide-react';

const base = process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, '') || 'http://localhost:8080';

type Wallet = { balance: number; availableBalance: number; reservedBalance: number };
type Me = {
  userId?: number; publicUserId: string; mobile: string; name?: string; email?: string; profileImageUrl?: string | null;
  profileImageVersion?: number | null; role: string; commissionRate?: number; createdAt?: string; profileUpdatedAt?: string | null;
};
type RechargeItem = {
  transactionId?: string; clientRequestId?: string; mobileNumber?: string; operator?: string; circle?: string;
  amount?: number; walletDebitAmount?: number; status?: string; createdAt?: string; updatedAt?: string;
  planDescription?: string | null; planValidity?: string | null; provider?: string; providerReference?: string | null;
  message?: string | null; completedAt?: string | null; clientCommission?: number;
};
type WalletItem = {
  id?: string | number; type?: string; amount?: number; status?: string; referenceId?: string;
  referenceType?: string; externalRef?: string; description?: string; createdAt?: string; provider?: string | null;
  mobileNumber?: string | null; operator?: string | null; circle?: string | null;
};
type WithdrawalItem = {
  withdrawalId: string; clientRequestId?: string; amount: number; upiId: string; provider: string; status: string;
  providerReference?: string; providerStatus?: string; failureReason?: string; walletLedgerRef?: string; message?: string | null;
  createdAt?: string; updatedAt?: string; completedAt?: string;
};
type RentalCar = {
  id: string; name: string; category: string; seats: number; transmission: string; fuelType?: string;
  registrationYear?: number; city?: string; pickupAddress?: string; imageUrl?: string; pricePerDay: number;
  driverName: string; driverMobile?: string; driverRating?: number; approvalStatus?: string; rejectionReason?: string;
  make?: string; model?: string; variant?: string; manufacturingYear?: number; registrationNumber?: string;
  state?: string; driverLicenseNumber?: string; driverLicenseExpiry?: string; driverAddress?: string;
};
type RentalQuote = {
  carId: string; carName: string; driverName: string; pickup: string; drop: string;
  startDate: string; endDate: string; days: number; pricePerDay: number; total: number;
};
type RentalBooking = {
  bookingId: string; carName: string; driverName?: string; driverMobile?: string; pickup: string; drop: string;
  startDate: string; endDate: string; total: number; paymentMethod?: string; status: string; createdAt?: string;
};
type RentalVendor = {
  vendorId?: string | null; status: string; vendorType?: string | null; fullName?: string | null; businessName?: string | null;
  city?: string | null; state?: string | null; vehicleCount?: number; address?: string | null; pinCode?: string | null;
  panNumber?: string | null; payoutUpiId?: string | null; bankAccountNumber?: string | null; bankIfsc?: string | null;
  bankName?: string | null; payoutPrimaryMethod?: string | null; rejectionReason?: string | null; submittedAt?: string | null;
};
type RentalVendorEarningsPeriod = {
  grossAmount: number; platformFeeAmount: number; vendorNetAmount: number; bookingCount: number; completedBookingCount: number;
};
type RentalVendorEarnings = {
  today: RentalVendorEarningsPeriod; monthly: RentalVendorEarningsPeriod; upcomingBookingCount: number;
};
type RentalPayout = {
  payoutId: string; bookingId: string; carId: string; carName: string; grossAmount: number;
  platformFeePercent: number; platformFeeAmount: number; vendorNetAmount: number; status: string; createdAt: string; paidAt?: string | null;
};
type VehicleUnavailability = {
  id: string; carId: string; startDate: string; endDate: string; reasonCode: string; reasonLabel: string;
  reasonNote?: string | null; status: string; createdAt: string;
};
type CalendarDay = { date: string; status: string; bookingId?: string | null; reasonCode?: string | null; reasonLabel?: string | null };

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

async function apiUpload<T = any>(path: string, method: 'PUT' | 'POST', formData: FormData): Promise<T> {
  const token = localStorage.getItem('mpay_token');
  const r = await fetch(base + path, {
    method,
    body: formData,
    headers: token ? { Authorization: 'Bearer ' + token } : {}
  });
  if (!r.ok) throw new Error((await r.text()) || 'Upload failed');
  return r.json();
}

const money = (n: any) => '₹' + Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 });
const dt = (v?: string) => v ? new Date(v).toLocaleString('en-IN') : '—';
const date = (v?: string) => v ? new Date(v).toLocaleDateString('en-IN') : '—';
const isoNow = () => new Date().toISOString().slice(0, 16);

function statusClass(value?: string) {
  return 'status-pill status-' + String(value || 'UNKNOWN').toLowerCase().replace(/[^a-z0-9]+/g, '-');
}

function imageFromCar(car?: RentalCar, slot = 0) {
  const raw = String(car?.imageUrl || '');
  const values = raw.split(',').map(x => x.trim()).filter(Boolean);
  const value = values[slot] || '';
  if (!value) return '';
  if (/^https?:\/\//i.test(value)) return value;
  return base + '/api/v1/car-rental/photos/' + value.replace(/^\/+/, '');
}

function bookingShareText(b: RentalBooking) {
  return [
    'mPay Car Rental Booking',
    'Booking ID: ' + b.bookingId,
    'Car: ' + b.carName,
    'Driver: ' + (b.driverName || '—') + (b.driverMobile ? ' (' + b.driverMobile + ')' : ''),
    'From: ' + b.pickup,
    'To: ' + b.drop,
    'Start: ' + dt(b.startDate),
    'End: ' + dt(b.endDate),
    'Amount: ' + money(b.total),
    'Payment: ' + (b.paymentMethod || 'WALLET'),
    'Status: ' + String(b.status || 'UNKNOWN').toUpperCase()
  ].join(' | ');
}

export default function Portal() {
  const [view, setView] = useState<'home'|'recharge'|'wallet'|'history'|'marketplace'|'rental'|'bookings'|'account'>('home');
  const [drawer, setDrawer] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [wallet, setWallet] = useState<Wallet>();
  const [me, setMe] = useState<Me>();
  const [profileImage, setProfileImage] = useState('');
  const [profileForm, setProfileForm] = useState({ name: '', email: '' });
  const [editingProfile, setEditingProfile] = useState(false);

  const [mobile, setMobile] = useState('');
  const [operator, setOperator] = useState<any>();
  const [operatorName, setOperatorName] = useState('');
  const [operatorCircle, setOperatorCircle] = useState('');
  const [plans, setPlans] = useState<any[]>([]);
  const [recharges, setRecharges] = useState<RechargeItem[]>([]);
  const [historyKind, setHistoryKind] = useState('');
  const [historyFrom, setHistoryFrom] = useState('');
  const [historyTo, setHistoryTo] = useState('');
  const [rechargeFunding, setRechargeFunding] = useState<'WALLET'|'RAZORPAY'|'PAYU'>('WALLET');

  const [walletHistory, setWalletHistory] = useState<WalletItem[]>([]);
  const [selectedWalletItem, setSelectedWalletItem] = useState<WalletItem>();
  const [selectedRechargeDetail, setSelectedRechargeDetail] = useState<any>();
  const [commissionSummary, setCommissionSummary] = useState<any>();
  const [withdrawals, setWithdrawals] = useState<WithdrawalItem[]>([]);
  const [addMoneyAmount, setAddMoneyAmount] = useState('');
  const [addMoneyProvider, setAddMoneyProvider] = useState<'mock'|'razorpay'|'payu'>('mock');
  const [withdrawAmount, setWithdrawAmount] = useState('');
  const [withdrawProvider, setWithdrawProvider] = useState<'mock'|'razorpay'|'payu'>('mock');
  const [withdrawUpi, setWithdrawUpi] = useState('');

  const [cars, setCars] = useState<RentalCar[]>([]);
  const [bookings, setBookings] = useState<RentalBooking[]>([]);
  const [bookingStatusFilter, setBookingStatusFilter] = useState('ALL');
  const [selectedCar, setSelectedCar] = useState<RentalCar>();
  const [rentalQuote, setRentalQuote] = useState<RentalQuote>();
  const [rentalForm, setRentalForm] = useState({ pickup: '', drop: '' });
  const [rentalSearch, setRentalSearch] = useState({ location: '', startDate: '', endDate: '' });
  const [rentalDetails, setRentalDetails] = useState<RentalCar>();

  const [vendor, setVendor] = useState<RentalVendor>();
  const [vendorPayouts, setVendorPayouts] = useState<RentalPayout[]>([]);
  const [vendorEarnings, setVendorEarnings] = useState<RentalVendorEarnings>();
  const [vendorVehicles, setVendorVehicles] = useState<RentalCar[]>([]);
  const [selectedVendorVehicle, setSelectedVendorVehicle] = useState<RentalCar>();
  const [vehicleUnavailability, setVehicleUnavailability] = useState<VehicleUnavailability[]>([]);
  const [vehicleCalendar, setVehicleCalendar] = useState<CalendarDay[]>([]);
  const [calendarMonth, setCalendarMonth] = useState(new Date().toISOString().slice(0, 7));
  const [showVendorForm, setShowVendorForm] = useState(false);
  const [showVehicleForm, setShowVehicleForm] = useState(false);
  const [vehicleEditId, setVehicleEditId] = useState('');
  const [vendorForm, setVendorForm] = useState({
    vendorType: 'INDIVIDUAL', fullName: '', businessName: '', address: '', city: '', state: '', pinCode: '',
    panNumber: '', payoutUpiId: '', bankAccountNumber: '', bankIfsc: '', bankName: '', payoutPrimaryMethod: ''
  });
  const defaultVehicle = {
    name:'', category:'SEDAN', seats:4, transmission:'AUTOMATIC', fuelType:'PETROL',
    manufacturingYear:new Date().getFullYear(), registrationYear:new Date().getFullYear(), registrationNumber:'',
    make:'', model:'', variant:'', pickupAddress:'', city:'', state:'', pricePerDay:'',
    driver:{fullName:'',mobile:'',licenseNumber:'',licenseExpiry:'',address:''}
  };
  const [vehicleForm, setVehicleForm] = useState<any>(defaultVehicle);
  const [unavailabilityForm, setUnavailabilityForm] = useState({ reasonCode:'SERVICE_MAINTENANCE', reasonNote:'', startDate:'', endDate:'' });

  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');

  const walletSigned = (item: WalletItem) => {
    const amount = Math.abs(Number(item.amount || 0));
    const negative = ['DEBIT', 'WITHDRAW'].includes(String(item.type || '').toUpperCase()) || String(item.referenceType || '').toUpperCase() === 'RENTAL_PAYMENT';
    return negative ? -amount : amount;
  };
  const walletAmountClass = (item: WalletItem) => walletSigned(item) < 0 ? 'amount-debit' : 'amount-credit';
  const walletAmountLabel = (item: WalletItem) => (walletSigned(item) < 0 ? '-' : '+') + money(Math.abs(Number(item.amount || 0)));

  async function loadHistory() {
    try {
      const rechargeParams = new URLSearchParams({ page:'0', size:'25' });
      if (historyFrom) rechargeParams.set('from', historyFrom);
      if (historyTo) rechargeParams.set('to', historyTo);
      const walletParams = new URLSearchParams({ page:'0', size:'25' });
      if (historyKind) walletParams.set('kind', historyKind);
      if (historyFrom) walletParams.set('from', historyFrom);
      if (historyTo) walletParams.set('to', historyTo);
      const [r, w] = await Promise.all([
        api<any>('/api/v1/recharge/history?' + rechargeParams.toString()),
        api<any>('/api/v1/wallet/history?' + walletParams.toString())
      ]);
      setRecharges(r?.items || r?.content || r || []);
      setWalletHistory(w?.items || w?.content || w || []);
    } catch (e:any) {
      setNotice(e.message || 'Unable to load transaction history.');
    }
  }

  async function loadCommissionSummary() {
    try { setCommissionSummary(await api<any>('/api/v1/recharge/commission-summary')); } catch {}
  }

  async function loadWithdrawals() {
    try {
      const data = await api<any>('/api/v1/wallet/withdrawals?page=0&size=25');
      setWithdrawals(data?.items || data?.content || data || []);
    } catch (e:any) {
      setNotice(e.message || 'Unable to load withdrawal history.');
    }
  }

  async function loadProfileImage() {
    const token = localStorage.getItem('mpay_token');
    if (!token) return;
    try {
      const r = await fetch(base + '/api/v1/profile/image?v=' + Date.now(), { headers: { Authorization: 'Bearer ' + token } });
      if (!r.ok) { setProfileImage(''); return; }
      const url = URL.createObjectURL(await r.blob());
      setProfileImage(current => {
        if (current.startsWith('blob:')) URL.revokeObjectURL(current);
        return url;
      });
    } catch { setProfileImage(''); }
  }

  async function loadAccountData() {
    try {
      const p = await api<Me>('/api/v1/profile');
      setMe(p);
      setProfileForm({ name: p?.name || '', email: p?.email || '' });
      await loadProfileImage();
    } catch (e:any) {
      setNotice(e.message || 'Unable to load profile.');
    }
    try {
      const v = await api<RentalVendor>('/api/v1/car-rental/vendor');
      setVendor(v);
      if (!v?.vendorId) {
        setVendorVehicles([]); setVendorPayouts([]); setVendorEarnings(undefined);
        return;
      }
      const results = await Promise.allSettled([
        api<any>('/api/v1/car-rental/vendor/vehicles'),
        api<any>('/api/v1/car-rental/vendor/payouts'),
        String(v.status || '').toUpperCase() === 'VERIFIED'
          ? api<RentalVendorEarnings>('/api/v1/car-rental/vendor/earnings')
          : Promise.resolve(undefined)
      ]);
      if (results[0].status === 'fulfilled') setVendorVehicles(results[0].value?.items || results[0].value || []);
      if (results[1].status === 'fulfilled') setVendorPayouts(results[1].value?.items || results[1].value || []);
      if (results[2].status === 'fulfilled') setVendorEarnings(results[2].value as RentalVendorEarnings | undefined);
    } catch {
      setVendor(undefined);
      setVendorVehicles([]);
      setVendorPayouts([]);
      setVendorEarnings(undefined);
    }
  }

  async function openWalletItem(item: WalletItem) {
    setSelectedWalletItem(item);
    setSelectedRechargeDetail(undefined);
    if (String(item.referenceType || '').toUpperCase() !== 'RECHARGE' || !item.referenceId) return;
    try {
      setSelectedRechargeDetail(await api<any>('/api/v1/recharge/' + encodeURIComponent(String(item.referenceId))));
    } catch (e:any) {
      setNotice(e.message || 'Unable to load recharge transaction details.');
    }
  }

  async function refreshWallet() {
    const w = await api<Wallet>('/api/v1/wallet');
    setWallet(w);
    return w;
  }

  async function saveProfile() {
    setBusy(true);
    try {
      const p = await api<Me>('/api/v1/profile', {
        method:'PATCH',
        body:JSON.stringify({ name:profileForm.name.trim() || null, email:profileForm.email.trim() || null })
      });
      setMe(p);
      setEditingProfile(false);
      setNotice('Profile updated.');
    } catch(e:any) { setNotice(e.message || 'Unable to update profile.'); }
    finally { setBusy(false); }
  }

  async function uploadProfileImage(file: File) {
    setBusy(true);
    try {
      const fd = new FormData();
      fd.append('image', file);
      const p = await apiUpload<Me>('/api/v1/profile/image', 'PUT', fd);
      setMe(p);
      await loadProfileImage();
      setNotice('Profile photo updated.');
    } catch(e:any) { setNotice(e.message || 'Unable to upload profile photo.'); }
    finally { setBusy(false); }
  }

  async function removeProfileImage() {
    setBusy(true);
    try {
      const p = await api<Me>('/api/v1/profile/image', { method:'DELETE' });
      setMe(p);
      setProfileImage('');
      setNotice('Profile photo removed.');
    } catch(e:any) { setNotice(e.message || 'Unable to remove profile photo.'); }
    finally { setBusy(false); }
  }

  async function ensureScript(src:string) {
    const existing = document.querySelector('script[data-mpay-provider="' + src + '"]') as HTMLScriptElement | null;
    if (existing) return;
    await new Promise<void>((resolve,reject)=>{
      const s=document.createElement('script');
      s.src=src; s.async=true; s.dataset.mpayProvider=src;
      s.onload=()=>resolve(); s.onerror=()=>reject(new Error('Unable to load payment gateway.'));
      document.head.appendChild(s);
    });
  }

  async function launchRazorpay(order:any, purpose:'wallet'|'recharge') {
    await ensureScript('https://checkout.razorpay.com/v1/checkout.js');
    const RazorpayCtor=(window as any).Razorpay;
    if (!RazorpayCtor) throw new Error('Razorpay checkout is unavailable.');
    const rzp = new RazorpayCtor({
      key:order.keyId, amount:Math.round(Number(order.amount)*100), currency:order.currency || 'INR',
      name:'mPay', description:purpose === 'recharge' ? 'mPay mobile recharge' : 'mPay wallet top-up', order_id:order.orderId,
      prefill:{ name:me?.name || '', email:me?.email || '', contact:me?.mobile || '' },
      handler:async(response:any)=>{
        try {
          const verified=await api<any>('/api/v1/payments/verify',{method:'POST',body:JSON.stringify({
            provider:'razorpay', paymentId:response.razorpay_payment_id, orderId:response.razorpay_order_id, signature:response.razorpay_signature
          })});
          await refreshWallet();
          await loadHistory();
          setNotice(purpose === 'recharge'
            ? ('Payment verified. Recharge status: ' + String(verified.rechargeStatus || verified.status || 'submitted') + '.')
            : 'Payment verified and wallet updated.');
        } catch(e:any) { setNotice(e.message || 'Payment verification failed.'); }
      },
      modal:{ ondismiss:()=>setNotice('Payment window closed.') }
    });
    rzp.open();
  }

  async function launchPayU(order:any, purpose:'wallet'|'recharge') {
    const p=order.checkoutParams || {};
    const form=document.createElement('form');
    form.method='POST';
    form.action=String(p.pgAction || (String(p.isProduction)==='true' ? 'https://secure.payu.in/_payment' : 'https://test.payu.in/_payment'));
    form.target='_blank';
    const fields:any = {
      key:order.keyId, txnid:order.orderId, amount:Number(order.amount).toFixed(2), productinfo:p.productInfo || 'mPay wallet',
      firstname:p.firstName || me?.name || 'mPay', email:p.email || me?.email || ((me?.mobile || '') + '@mpay.local'),
      phone:p.phone || me?.mobile || '', surl:p.surl, furl:p.furl, hash:p.paymentHash
    };
    Object.entries(fields).forEach(([k,v])=>{
      if(v !== undefined && v !== null && String(v).trim() !== ''){
        const input=document.createElement('input'); input.type='hidden'; input.name=k; input.value=String(v); form.appendChild(input);
      }
    });
    document.body.appendChild(form); form.submit(); form.remove();
    setNotice('PayU checkout opened in a new tab. mPay will verify the payment while the checkout is completed.');
    const orderId=order.orderId;
    for(let i=0;i<24;i++){
      await new Promise(r=>setTimeout(r,5000));
      try {
        const verified=await api<any>('/api/v1/payments/verify',{method:'POST',body:JSON.stringify({provider:'payu',orderId})});
        if(verified.status==='CAPTURED' || verified.transactionId || verified.rechargeStatus){
          await refreshWallet();
          await loadHistory();
          setNotice(purpose === 'recharge'
            ? ('PayU payment verified. Recharge status: ' + String(verified.rechargeStatus || verified.status || 'submitted') + '.')
            : 'PayU payment verified and wallet updated.');
          return;
        }
      } catch {}
    }
    setNotice('PayU checkout did not complete within the verification window. Refresh Wallet/History after returning.');
  }

  async function addMoney() {
    const amount=Number(addMoneyAmount);
    if (!(amount >= 1 && amount <= 100000)) { setNotice('Enter a wallet top-up amount between ₹1 and ₹1,00,000.'); return; }
    setBusy(true); setNotice('');
    try {
      const order=await api<any>('/api/v1/payments/orders',{method:'POST',body:JSON.stringify({
        amount, provider:addMoneyProvider, clientRequestId:crypto.randomUUID(), purpose:'ADD_MONEY'
      })});
      if(addMoneyProvider==='mock'){
        const result=await api<any>('/api/v1/payments/verify',{method:'POST',body:JSON.stringify({provider:'mock',orderId:order.orderId})});
        setWallet({balance:result.balance,availableBalance:result.availableBalance,reservedBalance:wallet?.reservedBalance || 0});
        setAddMoneyAmount('');
        setNotice('Mock wallet top-up completed.');
        await loadHistory();
      } else if(addMoneyProvider==='razorpay') {
        await launchRazorpay(order,'wallet');
      } else {
        await launchPayU(order,'wallet');
      }
    } catch(e:any) { setNotice(e.message || 'Unable to start wallet top-up.'); }
    finally { setBusy(false); }
  }

  async function withdrawMoney() {
    const amount=Number(withdrawAmount);
    const upi=withdrawUpi.trim();
    if (!(amount >= 1)) { setNotice('Enter a withdrawal amount of at least ₹1.'); return; }
    if (!/^[^\s@]+@[^\s@]+$/.test(upi)) { setNotice('Enter a valid UPI ID. UPI ID is required for every withdrawal.'); return; }
    setBusy(true); setNotice('');
    try {
      const result=await api<WithdrawalItem>('/api/v1/wallet/withdraw',{method:'POST',body:JSON.stringify({
        amount, provider:withdrawProvider, upiId:upi, clientRequestId:crypto.randomUUID()
      })});
      await refreshWallet();
      setWithdrawals(x=>[result,...x]);
      setWithdrawAmount('');
      setNotice(result.message || ('Withdrawal ' + String(result.status || '').toLowerCase() + ' for ' + upi + '.'));
      await loadHistory();
      await loadWithdrawals();
    } catch(e:any) { setNotice(e.message || 'Unable to withdraw money.'); }
    finally { setBusy(false); }
  }

  async function detect() {
    setBusy(true); setNotice(''); setPlans([]);
    try {
      const d=await api<any>('/api/v1/recharge/operator',{method:'POST',body:JSON.stringify({mobileNumber:mobile})});
      setOperator(d);
      setOperatorName(d.operator || '');
      setOperatorCircle(d.circle || '');
      if(d.providerOperator && d.circle){
        const q=await api<any>(
          '/api/v1/recharge/plans?mobile='+encodeURIComponent(mobile)+
          '&operator='+encodeURIComponent(d.operator)+
          '&circle='+encodeURIComponent(d.circle)+
          '&providerOperator='+encodeURIComponent(d.providerOperator)+
          '&providerCircle='+encodeURIComponent(d.providerCircle || '')
        );
        setPlans(q?.plans || q || []);
      }
    } catch(e:any) { setNotice(e.message || 'Unable to detect operator or plans.'); }
    finally { setBusy(false); }
  }

  async function recharge(plan:any) {
    const rechargeOperator=operatorName.trim();
    const rechargeCircle=operatorCircle.trim();
    const planId=String(plan.id || plan.planId || plan.amount);
    if (!rechargeOperator || !rechargeCircle) { setNotice('Operator and circle are required for recharge.'); return; }
    setBusy(true); setNotice('');
    try {
      const clientRequestId=crypto.randomUUID();
      if(rechargeFunding==='WALLET'){
        const result=await api<any>('/api/v1/recharge',{method:'POST',body:JSON.stringify({
          mobileNumber:mobile, operator:rechargeOperator, circle:rechargeCircle, planId, clientRequestId
        })});
        await refreshWallet();
        await loadHistory();
        setNotice('Recharge request submitted. Status: ' + String(result.status || 'PENDING') + '.');
        setView('history');
      } else {
        const provider=rechargeFunding.toLowerCase();
        const order=await api<any>('/api/v1/payments/orders',{method:'POST',body:JSON.stringify({
          amount:Number(plan.amount), provider, clientRequestId, purpose:'RECHARGE',
          rechargeMobileNumber:mobile, rechargeOperator:rechargeOperator, rechargeCircle:rechargeCircle, rechargePlanId:planId
        })});
        if(rechargeFunding==='RAZORPAY') await launchRazorpay(order,'recharge');
        else await launchPayU(order,'recharge');
        setView('history');
      }
    } catch(e:any) { setNotice(e.message || 'Recharge could not be submitted.'); }
    finally { setBusy(false); }
  }

  async function loadRentalData(startDate='',endDate='',location='') {
    try {
      const params=new URLSearchParams();
      if(startDate) params.set('startDate',startDate);
      if(endDate) params.set('endDate',endDate);
      if(location.trim()) params.set('location',location.trim());
      const carsPath='/api/v1/car-rental/cars' + (params.toString() ? '?' + params.toString() : '');
      const [available,existing]=await Promise.all([
        api<any>(carsPath),
        api<any>('/api/v1/car-rental/bookings?page=0&size=25')
      ]);
      setCars(available?.items || available || []);
      setBookings(existing?.items || existing?.content || existing || []);
    } catch(e:any) { setNotice(e.message || 'Unable to load rental inventory.'); }
  }

  function refreshRentalData() {
    void loadRentalData();
  }

  function searchRentalCars() {
    const location=rentalSearch.location.trim();
    const hasLocation=Boolean(location);
    const hasDates=Boolean(rentalSearch.startDate && rentalSearch.endDate);
    if(!hasLocation && !hasDates){setNotice('Enter a city/pickup area or select both rental dates to search.');return;}
    if((rentalSearch.startDate && !rentalSearch.endDate)||(!rentalSearch.startDate&&rentalSearch.endDate)){setNotice('Select both the start and end date & time.');return;}
    if(hasDates && new Date(rentalSearch.endDate).getTime() <= new Date(rentalSearch.startDate).getTime()){setNotice('End date & time must be after the start date & time.');return;}
    setSelectedCar(undefined); setRentalQuote(undefined);
    loadRentalData(rentalSearch.startDate,rentalSearch.endDate,location);
  }

  function clearRentalSearch() {
    setRentalSearch({location:'',startDate:'',endDate:''});
    setSelectedCar(undefined); setRentalQuote(undefined); loadRentalData();
  }

  async function checkRentalFare() {
    const startDate = rentalSearch.startDate;
    const endDate = rentalSearch.endDate;
    if(!selectedCar || !rentalForm.pickup || !startDate || !endDate){setNotice('Choose From and To date & time in the rental search, then enter your pickup location.');return;}
    if(new Date(endDate).getTime() <= new Date(startDate).getTime()){setNotice('End date & time must be after the start date & time.');return;}
    setBusy(true); setNotice(''); setRentalQuote(undefined);
    try {
      const q=await api<RentalQuote>('/api/v1/car-rental/bookings/quote',{method:'POST',body:JSON.stringify({
        carId:selectedCar.id,pickupLocation:rentalForm.pickup.trim(),dropLocation:(rentalForm.drop || rentalForm.pickup).trim(),
        startDate,endDate
      })});
      setRentalQuote(q);
    } catch(e:any){setNotice(e.message || 'Unable to calculate rental fare.');}
    finally{setBusy(false);}
  }

  async function bookCar() {
    if(!rentalQuote || !selectedCar){setNotice('Check the fare before confirming the booking.');return;}
    setBusy(true); setNotice('');
    try {
      const result=await api<RentalBooking>('/api/v1/car-rental/bookings',{method:'POST',body:JSON.stringify({
        clientRequestId:crypto.randomUUID(),carId:selectedCar.id,pickupLocation:rentalQuote.pickup,
        dropLocation:rentalQuote.drop,startDate:rentalQuote.startDate,endDate:rentalQuote.endDate,paymentMethod:'WALLET'
      })});
      setBookings(b=>[result,...b]);
      await refreshWallet(); await loadRentalData();
      setNotice('Booking confirmed. Payment is from your wallet.');
      setSelectedCar(undefined); setRentalQuote(undefined);
      setView('bookings');
    } catch(e:any){setNotice(e.message || 'Car rental booking could not be submitted.');}
    finally{setBusy(false);}
  }

  async function cancelBooking(bookingId:string) {
    if(!confirm('Cancel this booking and refund the wallet amount?')) return;
    setBusy(true);
    try {
      await api('/api/v1/car-rental/bookings/'+encodeURIComponent(bookingId)+'/cancel',{method:'POST'});
      await refreshWallet(); await loadRentalData();
      setNotice('Booking cancelled and the wallet amount was refunded.');
    } catch(e:any){setNotice(e.message || 'Unable to cancel booking.');}
    finally{setBusy(false);}
  }

  async function saveVendor() {
    setBusy(true);
    try {
      const method = vendor?.vendorId ? 'PUT' : 'POST';
      const v=await api<RentalVendor>('/api/v1/car-rental/vendor',{method,body:JSON.stringify(vendorForm)});
      setVendor(v); setShowVendorForm(false);
      setNotice(vendor?.vendorId ? 'Vendor profile updated.' : 'Vendor application submitted for admin verification.');
      await loadAccountData();
    } catch(e:any){setNotice(e.message || 'Unable to save vendor profile.');}
    finally{setBusy(false);}
  }

  function resetVendorForm(v: RentalVendor) {
    setVendorForm({
      vendorType: v.vendorType || 'INDIVIDUAL', fullName: v.fullName || '', businessName: v.businessName || '', address: v.address || '',
      city: v.city || '', state: v.state || '', pinCode: v.pinCode || '', panNumber: v.panNumber || '',
      payoutUpiId: v.payoutUpiId || '', bankAccountNumber: v.bankAccountNumber || '', bankIfsc: v.bankIfsc || '',
      bankName: v.bankName || '', payoutPrimaryMethod: v.payoutPrimaryMethod || ''
    });
    setShowVendorForm(true);
  }

  function resetVehicleForm(car?:RentalCar) {
    if(!car){
      setVehicleEditId('');
      setVehicleForm({...defaultVehicle,driver:{...defaultVehicle.driver}});
    } else {
      setVehicleEditId(String(car.id));
      setVehicleForm({
        name:car.name || '', category:car.category || 'SEDAN', seats:car.seats || 4, transmission:car.transmission || 'AUTOMATIC',
        fuelType:car.fuelType || 'PETROL', manufacturingYear:car.manufacturingYear || new Date().getFullYear(),
        registrationYear:car.registrationYear || new Date().getFullYear(), registrationNumber:car.registrationNumber || '',
        make:car.make || '', model:car.model || '', variant:car.variant || '', pickupAddress:car.pickupAddress || '',
        city:car.city || '', state:car.state || '', pricePerDay:car.pricePerDay || '',
        driver:{fullName:car.driverName || '',mobile:car.driverMobile || '',licenseNumber:car.driverLicenseNumber || '',
          licenseExpiry:car.driverLicenseExpiry ? String(car.driverLicenseExpiry).slice(0,16) : '',address:car.driverAddress || ''}
      });
    }
    setShowVehicleForm(true);
  }

  async function saveVehicle() {
    setBusy(true);
    try {
      const body={
        ...vehicleForm,
        seats:Number(vehicleForm.seats),
        manufacturingYear:Number(vehicleForm.manufacturingYear),
        registrationYear:Number(vehicleForm.registrationYear),
        pricePerDay:Number(vehicleForm.pricePerDay),
        driver:{...vehicleForm.driver,licenseExpiry:vehicleForm.driver.licenseExpiry}
      };
      const saved=await api<RentalCar>(
        vehicleEditId ? '/api/v1/car-rental/vendor/vehicles/'+encodeURIComponent(vehicleEditId) : '/api/v1/car-rental/vendor/vehicles',
        {method:vehicleEditId?'PUT':'POST',body:JSON.stringify(body)}
      );
      setVendorVehicles(v=>vehicleEditId ? v.map(x=>x.id===saved.id?saved:x) : [saved,...v]);
      setShowVehicleForm(false); setSelectedVendorVehicle(saved);
      setNotice(vehicleEditId ? 'Vehicle resubmitted for admin review.' : 'Vehicle submitted for admin review.');
    } catch(e:any){setNotice(e.message || 'Unable to save vehicle.');}
    finally{setBusy(false);}
  }

  async function uploadVehicleSlot(carId:string,slot:number,file:File) {
    setBusy(true);
    try {
      const fd=new FormData(); fd.append('photo',file);
      const saved=await apiUpload<RentalCar>('/api/v1/car-rental/vendor/vehicles/'+encodeURIComponent(carId)+'/photos/'+slot,'PUT',fd);
      setVendorVehicles(v=>v.map(x=>x.id===saved.id?saved:x));
      setSelectedVendorVehicle(saved);
      setNotice('Vehicle photo uploaded.');
    } catch(e:any){setNotice(e.message || 'Unable to upload vehicle photo.');}
    finally{setBusy(false);}
  }

  async function loadVehicleUnavailability(carId:string) {
    try {
      const list=await api<any>('/api/v1/car-rental/vendor/vehicles/'+encodeURIComponent(carId)+'/unavailability');
      setVehicleUnavailability(list || []);
    } catch(e:any){setNotice(e.message || 'Unable to load vehicle availability.');}
  }

  async function loadVehicleCalendar(carId:string,ym=calendarMonth) {
    const [year,month]=ym.split('-').map(Number);
    try {
      const d=await api<any>('/api/v1/car-rental/vendor/vehicles/'+encodeURIComponent(carId)+'/calendar?year='+year+'&month='+month);
      setVehicleCalendar(d?.days || []);
    } catch(e:any){setNotice(e.message || 'Unable to load vehicle calendar.');}
  }

  async function takeVehicleOffMarket(carId:string) {
    if(!unavailabilityForm.startDate || !unavailabilityForm.endDate){setNotice('Select the off-market start and end dates.');return;}
    setBusy(true);
    try {
      await api('/api/v1/car-rental/vendor/vehicles/'+encodeURIComponent(carId)+'/unavailability',{method:'POST',body:JSON.stringify({
        reasonCode:unavailabilityForm.reasonCode,reasonNote:unavailabilityForm.reasonNote || null,
        startDate:unavailabilityForm.startDate,endDate:unavailabilityForm.endDate
      })});
      await loadVehicleUnavailability(carId); await loadVehicleCalendar(carId);
      setNotice('Vehicle off-market period saved.');
    } catch(e:any){setNotice(e.message || 'Unable to save off-market period.');}
    finally{setBusy(false);}
  }

  async function restoreOffMarket(carId:string,id:string) {
    setBusy(true);
    try {
      await api('/api/v1/car-rental/vendor/vehicles/'+encodeURIComponent(carId)+'/unavailability/'+encodeURIComponent(id)+'/restore',{method:'POST'});
      await loadVehicleUnavailability(carId); await loadVehicleCalendar(carId);
      setNotice('Vehicle restored to market.');
    } catch(e:any){setNotice(e.message || 'Unable to restore vehicle.');}
    finally{setBusy(false);}
  }

  useEffect(()=>{
    if(!localStorage.getItem('mpay_token')){window.location.href='/login';return;}
    Promise.all([api<Me>('/api/v1/me'),api<Wallet>('/api/v1/wallet')])
      .then(([a,b])=>{setMe(a);setWallet(b);setProfileForm({name:a?.name || '',email:a?.email || ''});})
      .catch(()=>{localStorage.removeItem('mpay_token');window.location.href='/login';});
    loadHistory(); loadWithdrawals(); loadCommissionSummary(); loadRentalData(); loadAccountData();
  },[]);

  useEffect(()=>{ if(view==='history'||view==='wallet') { loadHistory(); loadWithdrawals(); loadCommissionSummary(); } },[view,historyKind,historyFrom,historyTo]);

  useEffect(()=>{
    if(selectedVendorVehicle){
      loadVehicleUnavailability(selectedVendorVehicle.id);
      loadVehicleCalendar(selectedVendorVehicle.id);
    }
  },[selectedVendorVehicle?.id]);

  useEffect(()=>{
    if(selectedVendorVehicle) loadVehicleCalendar(selectedVendorVehicle.id,calendarMonth);
  },[calendarMonth]);

  async function copyText(value:string,message='Copied to clipboard.') {
    try { await navigator.clipboard.writeText(value); setNotice(message); }
    catch { setNotice('Unable to copy. Please copy the reference manually.'); }
  }

  async function chooseContact() {
    const contacts=(navigator as any).contacts;
    if(!contacts?.select){setNotice('Contact picker is not available in this browser. Enter the number manually.');return;}
    try {
      const selected=await contacts.select(['tel'],{multiple:false});
      const tel=selected?.[0]?.tel?.[0] || '';
      if(tel) setMobile(String(tel).replace(/\D/g,'').slice(-10));
    } catch {}
  }

  function logout() {
    localStorage.removeItem('mpay_token');
    localStorage.removeItem('mpay_refresh_token');
    window.location.href='/';
  }

  const menu = [
    ['home','Home',Home], ['recharge','Recharge',Smartphone], ['wallet','Wallet',WalletCards],
    ['history','History',History], ['marketplace','Marketplace',Car], ['bookings','My Bookings',Clock3], ['account','Account',UserRound]
  ] as const;

  const filteredBookings=bookings.filter(b=>bookingStatusFilter==='ALL'||String(b.status||'').toUpperCase()===bookingStatusFilter);
  const bookingStatuses=['ALL',...Array.from(new Set(bookings.map(b=>String(b.status||'').toUpperCase()).filter(Boolean)))];

  const walletFilters=[
    {key:'',label:'All'}, {key:'ADD_MONEY',label:'Add money'}, {key:'WITHDRAWN',label:'Withdrawals'}, {key:'RECHARGE',label:'Recharges'}, {key:'RENTAL',label:'Rental'}
  ];

  return <div className="portal-shell">
    <aside className={'portal-sidebar ' + (drawer ? 'open ' : '') + (sidebarCollapsed ? 'collapsed' : '')}>
      <div className="portal-side-head"><a className="landing-brand" href="/"><img src="/mpay-logo.png" alt="mPay"/><span>mPay</span></a><div className="portal-side-controls"><button className="icon-btn sidebar-collapse-btn" title={sidebarCollapsed?'Expand navigation':'Collapse navigation'} onClick={()=>setSidebarCollapsed(v=>!v)}>{sidebarCollapsed?<ChevronRight size={17}/>:<ChevronLeft size={17}/>}</button><button className="icon-btn mobile-only" onClick={()=>setDrawer(false)}><X size={18}/></button></div></div>
      <div className="portal-welcome"><span>Signed in as</span><b>{me?.name || 'mPay user'}</b><small>{me?.mobile || ''}</small></div>
      <nav>{menu.map(([key,label,Icon])=>
        <button key={key} className={view===key?'portal-nav active':'portal-nav'} onClick={()=>{setView(key);setDrawer(false);}}>
          <Icon size={18}/><span className="portal-nav-label">{label}</span>
        </button>)}</nav>
      <button className="portal-nav portal-logout" onClick={logout}><LogOut size={18}/><span className="portal-nav-label">Logout</span></button>
    </aside>

    <main className="portal-main">
      <header className="portal-topbar">
        <button className="icon-btn mobile-only" onClick={()=>setDrawer(true)}><Menu size={19}/></button>
        <div><span>mPay personal workspace</span><h1>{
          view==='home'?'Good to see you.':view==='recharge'?'Mobile recharge':view==='wallet'?'Your wallet':
          view==='history'?'Transaction history':view==='marketplace'?'Marketplace':view==='rental'?'Marketplace · Car Rental':
          view==='bookings'?'My Bookings':'Your account'
        }</h1></div>
        <div className="portal-avatar">{profileImage ? <img src={profileImage} alt="Profile"/> : (me?.name || 'U').charAt(0).toUpperCase()}</div>
      </header>

      {notice && <div className="portal-notice">{notice}<button onClick={()=>setNotice('')}><X size={14}/></button></div>}

      {view==='home' && <section className="portal-content">
        <div className="portal-hero-card"><div><span>AVAILABLE TO SPEND</span><strong>{money(wallet?.availableBalance)}</strong><p>Manage recharges, wallet activity and your chauffeur-driven mobility services from one place.</p></div><button className="landing-primary" onClick={()=>setView('recharge')}>Recharge now <ArrowRight size={16}/></button></div>
        <div className="portal-quick-actions">
          <button onClick={()=>setView('recharge')}><Smartphone/><span>Mobile Recharge</span></button>
          <button onClick={()=>setView('wallet')}><WalletCards/><span>Add Money</span></button>
          <button onClick={()=>{setView('bookings');loadRentalData();}}><Clock3/><span>My Bookings</span></button>
        </div>
        <div className="home-earnings-strip">
          <div><span>Today's earnings</span><b>{money(commissionSummary?.daily?.commission)}</b><small>{commissionSummary?.daily?.successfulRechargeCount || 0} successful recharges</small></div>
          <div><span>This month</span><b>{money(commissionSummary?.monthly?.commission)}</b><small>Recharge volume {money(commissionSummary?.monthly?.successfulRechargeAmount)}</small></div>
          <button onClick={()=>setView('wallet')}><CircleDollarSign size={18}/><span>Wallet earnings</span><ArrowRight size={15}/></button>
        </div>
        <section className="home-marketplace"><div className="home-section-label">Marketplace</div><button className="home-marketplace-card" onClick={()=>{setView('rental');loadRentalData();}}>
          <div className="home-marketplace-icon"><Car size={27}/></div><div className="home-marketplace-copy"><span>CHAUFFEUR-DRIVEN MOBILITY</span><b>Car Rental</b><p>Choose a chauffeur-driven car, set your trip time and book directly from Home.</p></div><ArrowRight size={19}/>
        </button></section>
        <button className="home-recharge-history" onClick={()=>{setView('history');loadHistory();}}>
          <div className="home-recharge-history-icon"><History size={22}/></div><div><span>TRANSACTION HISTORY</span><b>Recharge History</b><p>View submitted, pending, completed and failed recharge activity.</p></div><ArrowRight size={18}/>
        </button>
      </section>}

      {view==='recharge' && <section className="portal-content"><div className="portal-panel">
        <div className="panel-head"><div><h2>Recharge a mobile</h2><p>Detect the operator, edit the detected operator if required, load plans and choose how to fund the recharge.</p></div></div>
        <div className="recharge-web-form">
          <input inputMode="numeric" maxLength={10} value={mobile} onChange={e=>setMobile(e.target.value.replace(/\D/g,''))} placeholder="10-digit mobile number"/>
          <button className="landing-secondary" disabled={busy} onClick={chooseContact}><Smartphone size={15}/> Contacts</button>
          <button className="primary" disabled={busy || mobile.length!==10} onClick={detect}>{busy?'Checking…':'Find plans'}</button>
        </div>
        {operator && <div className="operator-result editable-operator">
          <CheckCircle2 size={18}/><div className="operator-fields"><label>Operator<input value={operatorName} onChange={e=>setOperatorName(e.target.value)}/></label><label>Circle<input value={operatorCircle} onChange={e=>setOperatorCircle(e.target.value)}/></label><span>{operator.type || 'Mobile'} · provider {operator.providerOperator || '—'}</span></div>
        </div>}
        {plans.length>0 && <div className="funding-picker"><span>Recharge payment</span><button className={rechargeFunding==='WALLET'?'selected':''} onClick={()=>setRechargeFunding('WALLET')}>Wallet</button><button className={rechargeFunding==='RAZORPAY'?'selected':''} onClick={()=>setRechargeFunding('RAZORPAY')}>Razorpay</button><button className={rechargeFunding==='PAYU'?'selected':''} onClick={()=>setRechargeFunding('PAYU')}>PayU</button></div>}
        {plans.length>0 && <div className="web-plan-grid">{plans.map((p:any)=>
          <div className="web-plan" key={p.id || p.planId || p.amount}><div><strong>{money(p.amount)}</strong><span>{p.validity || 'Plan'}</span></div><p>{p.description || 'Recharge plan'}</p><button className="landing-secondary" disabled={busy} onClick={()=>recharge(p)}>{rechargeFunding==='WALLET'?'Use wallet':'Pay with '+rechargeFunding} <ArrowRight size={14}/></button></div>)}</div>}
        {operator && !plans.length && !busy && <div className="empty-state">No plans were returned for this number.</div>}
      </div></section>}

      {view==='wallet' && <section className="portal-content">
        <div className="wallet-grid"><div className="wallet-big"><span>Total balance</span><strong>{money(wallet?.balance)}</strong></div><div><span>Available</span><b>{money(wallet?.availableBalance)}</b></div><div><span>Reserved</span><b>{money(wallet?.reservedBalance)}</b></div></div>

        <div className="portal-panel"><div className="panel-head"><div><h2>Add Money</h2><p>Use the same test-mode providers available in Android: Mock, Razorpay and PayU.</p></div><CircleDollarSign size={22}/></div>
          <div className="funding-picker"><span>Provider</span><button className={addMoneyProvider==='mock'?'selected':''} onClick={()=>setAddMoneyProvider('mock')}>Mock</button><button className={addMoneyProvider==='razorpay'?'selected':''} onClick={()=>setAddMoneyProvider('razorpay')}>Razorpay</button><button className={addMoneyProvider==='payu'?'selected':''} onClick={()=>setAddMoneyProvider('payu')}>PayU</button></div>
          <div className="money-action-row"><input inputMode="decimal" value={addMoneyAmount} onChange={e=>setAddMoneyAmount(e.target.value.replace(/[^0-9.]/g,''))} placeholder="Amount (₹1 to ₹1,00,000)"/><button className="landing-primary" disabled={busy} onClick={addMoney}>{busy?'Processing…':'Add Money'} <ArrowRight size={16}/></button></div>
        </div>

        <div className="portal-panel"><div className="panel-head"><div><h2>Withdraw to UPI</h2><p>UPI ID is mandatory for every withdrawal and is retained in withdrawal history.</p></div><Banknote size={22}/></div>
          <div className="money-action-grid"><label>Amount<input inputMode="decimal" value={withdrawAmount} onChange={e=>setWithdrawAmount(e.target.value.replace(/[^0-9.]/g,''))} placeholder="Amount"/></label><label>UPI ID<input value={withdrawUpi} onChange={e=>setWithdrawUpi(e.target.value)} placeholder="name@upi"/></label></div>
          <div className="funding-picker"><span>Provider</span><button className={withdrawProvider==='mock'?'selected':''} onClick={()=>setWithdrawProvider('mock')}>Mock</button><button className={withdrawProvider==='razorpay'?'selected':''} onClick={()=>setWithdrawProvider('razorpay')}>Razorpay</button><button className={withdrawProvider==='payu'?'selected':''} onClick={()=>setWithdrawProvider('payu')}>PayU</button></div>
          <button className="landing-primary" disabled={busy} onClick={withdrawMoney}>{busy?'Processing…':'Withdraw money'} <ArrowRight size={16}/></button>
          {withdrawals.length>0 && <div className="history-list compact-list">{withdrawals.map(w=><div className="history-row" key={w.withdrawalId}><div><ReceiptText size={18}/><b>{money(w.amount)} → {w.upiId}</b><small>{w.withdrawalId} · {w.provider} · {dt(w.createdAt)}</small></div><div className="history-actions"><span className={statusClass(w.status)}>{String(w.status).toUpperCase()}</span>{w.providerReference && <button className="copy-btn" onClick={()=>copyText(w.providerReference || '')}><Copy size={14}/><span>Copy</span></button>}</div></div>)}</div>}
        </div>

        <div className="portal-panel"><div className="panel-head"><div><h2>Earnings & recharge summary</h2><p>Android wallet earnings summary for daily and monthly periods.</p></div><CircleDollarSign size={22}/></div>
          <div className="wallet-grid compact-wallet"><div className="wallet-big"><span>Commission %</span><strong>{commissionSummary?.commissionPercent != null ? Number(commissionSummary.commissionPercent).toFixed(2)+'%' : '—'}</strong></div><div><span>Today</span><b>{money(commissionSummary?.daily?.commission)}</b><small>{commissionSummary?.daily?.successfulRechargeCount || 0} successful recharges</small></div><div><span>This month</span><b>{money(commissionSummary?.monthly?.commission)}</b><small>{commissionSummary?.monthly?.successfulRechargeCount || 0} successful recharges</small></div></div>
        </div>

        <div className="portal-panel"><div className="panel-head"><div><h2>Wallet ledger</h2><p>Balance movements, recharge debits, rental debits/refunds and gateway funding.</p></div><button className="landing-secondary" onClick={()=>{loadHistory();refreshWallet();}}><RefreshCw size={15}/> Refresh</button></div>
          <div className="history-date-filters"><label>From<input type="date" value={historyFrom} onChange={e=>setHistoryFrom(e.target.value)}/></label><label>To<input type="date" value={historyTo} onChange={e=>setHistoryTo(e.target.value)}/></label><button className="landing-secondary" onClick={()=>{setHistoryFrom('');setHistoryTo('');setHistoryKind('');}}>Clear</button></div>
          <div className="funding-picker history-filter-picker"><span>Kind</span>{walletFilters.map(f=><button key={f.key} className={historyKind===f.key?'selected':''} onClick={()=>setHistoryKind(f.key)}>{f.label}</button>)}</div>
          {walletHistory.length ? <div className="history-list">{walletHistory.map((x,i)=><div className="history-row" key={String(x.id || i)}>
            <div><ReceiptText size={18}/><b>{x.description || x.referenceType || x.type || 'Wallet transaction'}</b><small>{x.referenceId || '—'} · {dt(x.createdAt)}{x.provider ? ' · '+x.provider : ''}</small></div>
            <strong className={walletAmountClass(x)}>{walletAmountLabel(x)}</strong>
            <div className="history-actions"><span className={statusClass(x.status)}>{String(x.status || 'UNKNOWN').toUpperCase()}</span><button className="copy-btn" onClick={()=>openWalletItem(x)}><Eye size={14}/><span>Details</span></button>{x.referenceId && <button className="copy-btn" onClick={()=>copyText(String(x.referenceId),'Transaction reference copied.')}><Copy size={14}/><span>Copy</span></button>}</div>
          </div>)}</div> : <div className="empty-state">No wallet transactions were returned.</div>}
        </div>
      </section>}

      {view==='history' && <section className="portal-content">
        <div className="portal-panel"><div className="panel-head"><div><h2>Recharge history</h2><p>Track submitted, pending, successful and failed recharges.</p></div><button className="landing-secondary" onClick={loadHistory}><RefreshCw size={15}/> Refresh</button></div>
          <div className="history-date-filters"><label>From<input type="date" value={historyFrom} onChange={e=>setHistoryFrom(e.target.value)}/></label><label>To<input type="date" value={historyTo} onChange={e=>setHistoryTo(e.target.value)}/></label><button className="landing-secondary" onClick={()=>{setHistoryFrom('');setHistoryTo('');setHistoryKind('');}}>Clear filters</button></div>
          {recharges.length ? <div className="history-list">{recharges.map((x,i)=><div className="history-row" key={String(x.transactionId || i)}>
            <div><ReceiptText size={18}/><b>{x.mobileNumber || 'Recharge'} · {x.operator || '—'}</b><small>{x.planDescription || 'Plan'} · {x.transactionId || 'No reference'} · {dt(x.createdAt)}{x.provider ? ' · '+x.provider : ''}</small></div>
            <strong>{money(x.amount)}</strong>
            <div className="history-actions"><span className={statusClass(x.status)}>{String(x.status || 'UNKNOWN').toUpperCase()}</span>{x.transactionId && <button className="copy-btn" onClick={()=>copyText(x.transactionId || '','Transaction reference copied.')}><Copy size={14}/><span>Copy</span></button>}</div>
          </div>)}</div> : <div className="empty-state"><History size={22}/><b>No recharge history yet</b><span>Your completed and pending recharges will appear here.</span></div>}
        </div>
      </section>}

      {view==='marketplace' && <section className="portal-content"><div className="portal-panel"><div className="panel-head"><div><h2>Marketplace</h2><p>Explore mPay service categories.</p></div><Car size={28}/></div>
        <button className="rental-car selected" onClick={()=>{setView('rental');loadRentalData();}}><div className="rental-car-icon"><Car size={26}/></div><b>Car Rental</b><span>NEW · Chauffeur-driven cars</span><strong>Open marketplace</strong></button>
      </div></section>}

      {view==='rental' && <section className="portal-content">
        <div className="portal-panel"><div className="panel-head"><div><h2>Choose a car</h2><p>Filter by city/pickup area and date & time, inspect the vehicle, review the fare and book from your wallet.</p></div><CarFront size={28}/></div>
          <div className="rental-search-card"><div><b>Find available cars</b><span>Availability is enforced by the backend using the selected rental window.</span></div>
            <div className="rental-search-grid"><label>City or pickup area<input value={rentalSearch.location} placeholder="e.g. Patna, Airport Road" onChange={e=>setRentalSearch({...rentalSearch,location:e.target.value})}/></label>
              <label>From<input type="datetime-local" value={rentalSearch.startDate} min={isoNow()} onChange={e=>setRentalSearch({...rentalSearch,startDate:e.target.value})}/></label>
              <label>To<input type="datetime-local" value={rentalSearch.endDate} min={rentalSearch.startDate || isoNow()} onChange={e=>setRentalSearch({...rentalSearch,endDate:e.target.value})}/></label>
            </div>
            <div className="rental-search-actions"><button className="landing-secondary" onClick={clearRentalSearch}>Clear</button><button className="landing-primary" onClick={searchRentalCars}>Find cars <ArrowRight size={16}/></button></div>
          </div>
          <div className="rental-form"><input placeholder="Pickup location for booking" value={rentalForm.pickup} onChange={e=>setRentalForm({...rentalForm,pickup:e.target.value})}/><input placeholder="Drop location (optional)" value={rentalForm.drop} onChange={e=>setRentalForm({...rentalForm,drop:e.target.value})}/><label>Start date & time<input type="datetime-local" value={rentalForm.startDate} min={isoNow()} onChange={e=>setRentalForm({...rentalForm,startDate:e.target.value})}/></label><label>End date & time<input type="datetime-local" value={rentalForm.endDate} min={rentalForm.startDate} onChange={e=>setRentalForm({...rentalForm,endDate:e.target.value})}/></label></div>
          {cars.length ? <div className="rental-car-grid">{cars.map(car=><div className={'rental-car '+(selectedCar?.id===car.id?'selected':'')} key={car.id}>
            <button className="rental-car-main" onClick={()=>{setSelectedCar(car);setRentalQuote(undefined);setRentalForm(x=>({...x,startDate:rentalSearch.startDate||x.startDate,endDate:rentalSearch.endDate||x.endDate}));}}><div className="rental-car-icon">{imageFromCar(car)?<img src={imageFromCar(car)} alt={car.name}/>:<Car size={26}/>}</div><b>{car.name}</b><span>{car.category} · {car.seats} seats · {car.transmission}</span><strong>{money(car.pricePerDay)} / day</strong></button><button className="copy-btn" onClick={()=>setRentalDetails(car)}><Eye size={14}/><span>Details</span></button>
          </div>)}</div> : <div className="rental-empty-state"><div className="rental-empty-icon"><Car size={28}/></div><b>{rentalSearch.location||rentalSearch.startDate||rentalSearch.endDate?'No cars match this search':'No cars available right now'}</b><span>{rentalSearch.location||rentalSearch.startDate||rentalSearch.endDate?'Try a different city, pickup area or rental window.':'There are no approved chauffeur-driven cars available for your account at the moment.'}</span><button className="landing-secondary" onClick={refreshRentalData}><RefreshCw size={15}/> Check again</button></div>}
          {selectedCar && <div className="rental-summary"><div><span>Selected</span><b>{selectedCar.name}</b></div><div><span>Billing</span><b>{rentalQuote ? rentalQuote.days+' day'+(rentalQuote.days>1?'s':''):'Check fare'}</b></div><div><span>Total</span><strong>{rentalQuote ? money(rentalQuote.total):'—'}</strong></div>
            {!rentalQuote?<button className="landing-primary" disabled={busy} onClick={checkRentalFare}>{busy?'Calculating…':'Check fare'} <ArrowRight size={16}/></button>:<button className="landing-primary" disabled={busy || Number(wallet?.availableBalance || 0)<Number(rentalQuote.total || 0)} onClick={bookCar}>{busy?'Confirming…':'Confirm booking'} <ArrowRight size={16}/></button>}
            <p className="rental-pricing-note">Price is per 24-hour day. Any partial day is charged as one full day; time is used for duration and availability. Payment is from your wallet.</p>
          </div>}
        </div>
      </section>}

      {view==='bookings' && <section className="portal-content"><div className="portal-panel"><div className="panel-head"><div><h2>My Bookings</h2><p>Booked cars, chauffeur details, trip timing, wallet payment, status and cancellation.</p></div><button className="landing-secondary" onClick={refreshRentalData}><RefreshCw size={15}/> Refresh</button></div>
        <div className="funding-picker">{bookingStatuses.map(s=><button key={s} className={bookingStatusFilter===s?'selected':''} onClick={()=>setBookingStatusFilter(s)}>{s==='ALL'?'All':s.replace(/_/g,' ')}</button>)}</div>
        {filteredBookings.length ? <div className="history-list">{filteredBookings.map(b=><div className="history-row" key={b.bookingId}><div><Car size={18}/><b>{b.carName}</b><small>{b.bookingId} · {b.pickup} → {b.drop} · {dt(b.startDate)} to {dt(b.endDate)} · Driver {b.driverName || '—'}</small></div><strong>{money(b.total)}</strong><div className="history-actions"><span className={statusClass(b.status)}>{String(b.status).toUpperCase()}</span><span>{b.paymentMethod || 'WALLET'}</span><button className="copy-btn" onClick={()=>copyText(bookingShareText(b),'Booking details copied.')}><Copy size={14}/><span>Copy</span></button>{String(b.status).toUpperCase()==='CONFIRMED' && new Date(b.startDate).getTime()>Date.now() && <button className="text-danger-btn" disabled={busy} onClick={()=>cancelBooking(b.bookingId)}>Cancel</button>}</div></div>)}</div> : <div className="empty-state">No bookings match the selected status.</div>}
      </div></section>}

      {view==='account' && <section className="portal-content">
        <div className="portal-panel account-panel">
          {profileImage ? <img className="account-profile-image" src={profileImage} alt="Profile"/> : <div className="account-large-avatar">{(me?.name || 'U').charAt(0).toUpperCase()}</div>}
          <h2>{me?.name || 'mPay user'}</h2><p>{me?.publicUserId}</p>
          <div className="account-facts">
            <div><span>Mobile</span><b>{me?.mobile}</b></div><div><span>Email</span><b>{me?.email || 'Not provided'}</b></div>
            <div><span>Account type</span><b>{me?.role || 'CLIENT'}</b></div><div><span>Joined</span><b>{date(me?.createdAt)}</b></div>
            <div><span>Last profile update</span><b>{dt(me?.profileUpdatedAt || undefined)}</b></div><div><span>Commission</span><b>{me?.commissionRate != null ? Number(me.commissionRate).toFixed(2)+'%' : '—'}</b></div>
          </div>
          <div className="account-actions"><button className="landing-secondary" onClick={()=>setEditingProfile(v=>!v)}><Edit3 size={15}/> {editingProfile?'Close':'Edit profile'}</button>
            <label className="landing-secondary upload-label"><Upload size={15}/> Photo<input type="file" accept="image/*" hidden onChange={e=>e.target.files?.[0] && uploadProfileImage(e.target.files[0])}/></label>
            {profileImage && <button className="text-danger-btn" onClick={removeProfileImage}><Trash2 size={14}/> Remove photo</button>}
          </div>
          {editingProfile && <div className="profile-edit-form"><input value={profileForm.name} placeholder="Full name" onChange={e=>setProfileForm({...profileForm,name:e.target.value})}/><input value={profileForm.email} placeholder="Email" onChange={e=>setProfileForm({...profileForm,email:e.target.value})}/><button className="landing-primary" disabled={busy} onClick={saveProfile}><Save size={15}/> Save profile</button></div>}
        </div>

        <div className="portal-panel"><div className="panel-head"><div><h2>Rental Vendor</h2><p>Onboarding, submitted status, fleet management, photos, availability, calendar and payout history.</p></div><CarFront size={24}/></div>
          {vendor && !vendor.vendorId && <div className="vendor-cta"><div><b>Become a rental partner</b><span>Submit your profile for admin review.</span></div><button className="landing-primary" onClick={()=>setShowVendorForm(true)}><Plus size={16}/> Become a Vendor</button></div>}
          {vendor?.vendorId && <><div className="vendor-summary-grid"><div><span>Status</span><b className={statusClass(vendor.status)}>{String(vendor.status).toUpperCase()}</b></div><div><span>Vehicles</span><b>{vendor.vehicleCount ?? vendorVehicles.length}</b></div><div><span>City</span><b>{vendor.city || '—'}</b></div><div><span>Vendor type</span><b>{vendor.vendorType || '—'}</b></div>{vendor.rejectionReason && <div className="vendor-rejection"><span>Review note</span><b>{vendor.rejectionReason}</b></div>}</div>
            <div className="vendor-actions">{(String(vendor.status||'').toUpperCase()==='REJECTED'||String(vendor.status||'').toUpperCase()==='PENDING') && <button className="landing-secondary" onClick={()=>{setVendorForm({
              vendorType:vendor.vendorType||'INDIVIDUAL',fullName:vendor.fullName||'',businessName:vendor.businessName||'',address:vendor.address||'',
              city:vendor.city||'',state:vendor.state||'',pinCode:vendor.pinCode||'',panNumber:vendor.panNumber||'',payoutUpiId:vendor.payoutUpiId||'',
              bankAccountNumber:vendor.bankAccountNumber||'',bankIfsc:vendor.bankIfsc||''});setShowVendorForm(true);}}><Edit3 size={15}/> {String(vendor.status).toUpperCase()==='REJECTED'?'Resubmit':'Edit application'}</button>}
              <button className="landing-secondary" onClick={loadAccountData}><RefreshCw size={15}/> Refresh vendor</button>
            </div>
          </>}
          {showVendorForm && <div className="vendor-form"><div className="vendor-section-head"><b>Vendor application</b><button className="icon-btn" onClick={()=>setShowVendorForm(false)}><X size={16}/></button></div>
            <div className="vendor-form-grid">{Object.keys(vendorForm).map(k=><label key={k}>{k.replace(/([A-Z])/g,' $1').replace(/^./,m=>m.toUpperCase())}<input value={(vendorForm as any)[k]} onChange={e=>setVendorForm({...vendorForm,[k]:e.target.value})}/></label>)}</div>
            <div className="form-actions"><button className="landing-secondary" onClick={()=>setShowVendorForm(false)}>Cancel</button><button className="landing-primary" disabled={busy} onClick={saveVendor}>Submit for review</button></div>
          </div>}

          {vendor?.vendorId && <div className="vendor-dashboard">
            <div className="vendor-section-head"><b>Fleet</b><button className="landing-secondary" onClick={()=>{setSelectedVendorVehicle(undefined);resetVehicleForm();}}><Plus size={14}/> Add vehicle</button></div>
            {vendorVehicles.length ? <div className="vendor-vehicle-grid">{vendorVehicles.map(car=><div className="vendor-vehicle-card" key={car.id}>
              <div className="vendor-vehicle-image">{imageFromCar(car)?<img src={imageFromCar(car)} alt={car.name}/>:<Car size={26}/>}</div>
              <div className="vendor-vehicle-main"><b>{car.name}</b><span>{car.make || ''} {car.model || ''} · {car.category} · {car.seats} seats</span><small>{money(car.pricePerDay)} / day · Driver {car.driverName || '—'}</small><div className="vendor-card-status"><span className={statusClass(car.approvalStatus)}>{String(car.approvalStatus || 'PENDING').toUpperCase()}</span></div></div>
              <div className="vendor-card-actions"><button className="icon-btn" title="Details" onClick={()=>setSelectedVendorVehicle(car)}><Eye size={16}/></button><button className="icon-btn" title="Edit/resubmit" onClick={()=>resetVehicleForm(car)}><Edit3 size={16}/></button></div>
            </div>)}</div> : <div className="empty-state">No vehicles submitted yet.</div>}

            {selectedVendorVehicle && <div className="vehicle-detail-panel">
              <div className="panel-head"><div><h3>{selectedVendorVehicle.name}</h3><p>{selectedVendorVehicle.make || '—'} {selectedVendorVehicle.model || ''} · Driver {selectedVendorVehicle.driverName || '—'}</p></div><button className="icon-btn" onClick={()=>setSelectedVendorVehicle(undefined)}><X size={17}/></button></div>
              <div className="vehicle-gallery">{[0,1,2,3].map(slot=>{const src=imageFromCar(selectedVendorVehicle,slot);return <div className="vehicle-gallery-slot" key={slot}>{src?<img src={src} alt={'Vehicle '+(slot+1)}/>:<span>Photo {slot+1}</span>}<label className="upload-photo-btn"><Camera size={14}/> Upload<input type="file" accept="image/*" hidden onChange={e=>e.target.files?.[0] && uploadVehicleSlot(selectedVendorVehicle.id,slot,e.target.files[0])}/></label></div>;})}</div>
              <div className="detail-grid-web"><span>Approval <b>{selectedVendorVehicle.approvalStatus || '—'}</b></span><span>Registration <b>{selectedVendorVehicle.registrationNumber || '—'}</b></span><span>Fuel <b>{selectedVendorVehicle.fuelType || '—'}</b></span><span>Price/day <b>{money(selectedVendorVehicle.pricePerDay)}</b></span><span>Pickup <b>{selectedVendorVehicle.pickupAddress || '—'}</b></span><span>City/State <b>{selectedVendorVehicle.city || '—'} / {selectedVendorVehicle.state || '—'}</b></span><span>Driver licence <b>{selectedVendorVehicle.driverLicenseNumber || '—'}</b></span><span>Licence expiry <b>{date(selectedVendorVehicle.driverLicenseExpiry)}</b></span></div>
              {selectedVendorVehicle.rejectionReason && <div className="vendor-rejection">{selectedVendorVehicle.rejectionReason}</div>}
              <div className="unavailability-box"><div className="vendor-section-head"><b>Availability controls</b><CalendarDays size={18}/></div>
                <div className="availability-form"><select value={unavailabilityForm.reasonCode} onChange={e=>setUnavailabilityForm({...unavailabilityForm,reasonCode:e.target.value})}><option value="SERVICE_MAINTENANCE">Service / maintenance</option><option value="PRIVATE_USE">Private use</option><option value="DRIVER_UNAVAILABLE">Driver unavailable</option><option value="LEGAL_DOCUMENTATION">Documentation / compliance</option><option value="PERSONAL_REASON">Personal reason</option><option value="OTHER">Other</option></select><input value={unavailabilityForm.reasonNote} placeholder="Reason note" onChange={e=>setUnavailabilityForm({...unavailabilityForm,reasonNote:e.target.value})}/><input type="date" value={unavailabilityForm.startDate} onChange={e=>setUnavailabilityForm({...unavailabilityForm,startDate:e.target.value})}/><input type="date" value={unavailabilityForm.endDate} onChange={e=>setUnavailabilityForm({...unavailabilityForm,endDate:e.target.value})}/><button className="landing-secondary" onClick={()=>takeVehicleOffMarket(selectedVendorVehicle.id)}>Take off market</button></div>
                {vehicleUnavailability.length ? <div className="history-list compact-list">{vehicleUnavailability.map(u=><div className="history-row" key={u.id}><div><b>{u.reasonLabel}</b><small>{u.startDate} → {u.endDate}{u.reasonNote?' · '+u.reasonNote:''}</small></div><div className="history-actions"><span className={statusClass(u.status)}>{String(u.status).toUpperCase()}</span>{String(u.status).toUpperCase()==='ACTIVE' && <button className="text-danger-btn" onClick={()=>restoreOffMarket(selectedVendorVehicle.id,u.id)}>Restore</button>}</div></div>)}</div> : <div className="empty-state">No active off-market periods.</div>}
              </div>
              <div className="calendar-box"><div className="vendor-section-head"><b>Vehicle calendar</b><div className="calendar-nav"><button className="icon-btn" onClick={()=>{const d=new Date(calendarMonth+'-01');d.setMonth(d.getMonth()-1);setCalendarMonth(d.toISOString().slice(0,7));}}><ChevronLeft size={15}/></button><b>{calendarMonth}</b><button className="icon-btn" onClick={()=>{const d=new Date(calendarMonth+'-01');d.setMonth(d.getMonth()+1);setCalendarMonth(d.toISOString().slice(0,7));}}><ChevronRight size={15}/></button></div></div><div className="calendar-grid">{vehicleCalendar.map(d=><div className={'calendar-day calendar-'+String(d.status||'UNKNOWN').toLowerCase()} key={d.date}><b>{new Date(d.date).getDate()}</b><span>{d.reasonLabel || d.status || '—'}</span></div>)}</div></div>
            </div>}

            {showVehicleForm && <div className="vehicle-form"><div className="vendor-section-head"><b>{vehicleEditId?'Resubmit vehicle':'Submit vehicle for review'}</b><button className="icon-btn" onClick={()=>setShowVehicleForm(false)}><X size={16}/></button></div>
              <div className="vehicle-form-grid">{Object.keys(vehicleForm).filter(k=>k!=='driver').map(k=><label key={k}>{k.replace(/([A-Z])/g,' $1').replace(/^./,m=>m.toUpperCase())}<input value={(vehicleForm as any)[k]} onChange={e=>setVehicleForm({...vehicleForm,[k]:e.target.value})}/></label>)}
                {Object.keys(vehicleForm.driver).map(k=><label key={k}>Driver {k.replace(/([A-Z])/g,' $1')}<input type={k==='licenseExpiry'?'datetime-local':'text'} value={vehicleForm.driver[k]} onChange={e=>setVehicleForm({...vehicleForm,driver:{...vehicleForm.driver,[k]:e.target.value}})}/></label>)}
              </div><div className="form-actions"><button className="landing-secondary" onClick={()=>setShowVehicleForm(false)}>Cancel</button><button className="landing-primary" disabled={busy} onClick={saveVehicle}>Save vehicle</button></div>
            </div>}

            <div className="vendor-section-head payouts-head"><b>Payout history</b><Banknote size={18}/></div>
            {vendorPayouts.length ? <div className="history-list compact-list">{vendorPayouts.map(p=><div className="history-row" key={p.payoutId}><div><b>{p.carName}</b><small>{p.bookingId} · {dt(p.createdAt)} · Platform fee {Number(p.platformFeePercent).toFixed(2)}%</small></div><strong className="amount-credit">{money(p.vendorNetAmount)}</strong><span className={statusClass(p.status)}>{String(p.status).toUpperCase()}</span></div>)}</div> : <div className="empty-state">No vendor payouts yet.</div>}
          </div>}
        </div>
      </section>}

      {selectedWalletItem && <div className="modal-backdrop" onClick={()=>setSelectedWalletItem(undefined)}><div className="portal-modal small-modal" onClick={e=>e.stopPropagation()}><div className="panel-head"><div><h2>Wallet transaction</h2><p>{selectedWalletItem.referenceType || selectedWalletItem.type || 'Transaction'}</p></div><button className="icon-btn" onClick={()=>setSelectedWalletItem(undefined)}><X size={17}/></button></div><div className="detail-grid-web"><span>Amount <b className={walletAmountClass(selectedWalletItem)}>{walletAmountLabel(selectedWalletItem)}</b></span><span>Status <b>{selectedWalletItem.status || '—'}</b></span><span>Reference type <b>{selectedWalletItem.referenceType || '—'}</b></span><span>Reference ID <b>{selectedWalletItem.referenceId || '—'}</b></span><span>Provider <b>{selectedWalletItem.provider || '—'}</b></span><span>Created <b>{dt(selectedWalletItem.createdAt)}</b></span><span>Mobile <b>{selectedWalletItem.mobileNumber || '—'}</b></span><span>Operator <b>{selectedWalletItem.operator || '—'}</b></span><span>Circle <b>{selectedWalletItem.circle || '—'}</b></span><span>Description <b>{selectedWalletItem.description || '—'}</b></span></div>
          {selectedRechargeDetail && <div className="recharge-detail-box"><h3>Recharge details</h3><div className="detail-grid-web"><span>Transaction <b>{selectedRechargeDetail.transactionId || '—'}</b></span><span>Plan <b>{selectedRechargeDetail.planDescription || selectedRechargeDetail.planId || '—'}</b></span><span>Recharge status <b>{selectedRechargeDetail.status || '—'}</b></span><span>Provider <b>{selectedRechargeDetail.provider || '—'}</b></span><span>Mobile <b>{selectedRechargeDetail.mobileNumber || '—'}</b></span><span>Operator / circle <b>{(selectedRechargeDetail.operator || '—') + ' / ' + (selectedRechargeDetail.circle || '—')}</b></span><span>Wallet debit <b>{money(selectedRechargeDetail.walletDebitAmount)}</b></span><span>Provider reference <b>{selectedRechargeDetail.providerReference || '—'}</b></span><span>Message <b>{selectedRechargeDetail.message || '—'}</b></span></div></div>}</div></div>}

      {rentalDetails && <div className="modal-backdrop" onClick={()=>setRentalDetails(undefined)}><div className="portal-modal" onClick={e=>e.stopPropagation()}><div className="panel-head"><div><h2>{rentalDetails.name}</h2><p>{rentalDetails.category} · {rentalDetails.seats} seats · {rentalDetails.transmission}</p></div><button className="icon-btn" onClick={()=>setRentalDetails(undefined)}><X size={17}/></button></div>
        <div className="vehicle-gallery">{[0,1,2,3].map(slot=>{const src=imageFromCar(rentalDetails,slot);return <div className="vehicle-gallery-slot" key={slot}>{src?<img src={src} alt={'Vehicle '+(slot+1)}/>:<span>Photo {slot+1}</span>}</div>;})}</div>
        <div className="detail-grid-web"><span>Make / model <b>{[rentalDetails.make,rentalDetails.model,rentalDetails.variant].filter(Boolean).join(' ')||'—'}</b></span><span>Fuel <b>{rentalDetails.fuelType||'—'}</b></span><span>Manufacturing year <b>{rentalDetails.manufacturingYear||'—'}</b></span><span>Registration <b>{rentalDetails.registrationNumber||'—'}</b></span><span>Pickup <b>{rentalDetails.pickupAddress||'—'}</b></span><span>City / State <b>{rentalDetails.city||'—'} / {rentalDetails.state||'—'}</b></span><span>Driver <b>{rentalDetails.driverName||'—'} {rentalDetails.driverMobile||''}</b></span><span>Licence expiry <b>{date(rentalDetails.driverLicenseExpiry)}</b></span></div>
      </div></div>}
    </main>
  </div>;
}
