'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { useWebCapabilities } from '../../lib/webCapabilities';
import {
  ArrowRight, Banknote, CalendarDays, Camera, Car, CarFront, Check, CheckCircle2, ChevronLeft,
  ChevronRight, CircleDollarSign, Clock3, Copy, Edit3, Eye, History, Home, LogOut, Menu,
  Plus, ReceiptText, RefreshCw, Save, Send, ShieldCheck, Smartphone, Trash2, Upload, UserRound, WalletCards, X
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
  driverId?: string; driverName: string; driverMobile?: string; driverPhotoUrl?: string; driverRating?: number; approvalStatus?: string; rejectionReason?: string;
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
  if (r.status === 401) { localStorage.removeItem('mpay_token'); localStorage.removeItem('mpay_refresh_token'); window.location.href = '/login'; throw new Error('Your session has expired. Please sign in again.'); }
  if (!r.ok) { const text = await r.text(); let message = text || ('Request failed (' + r.status + ')'); try { const parsed = JSON.parse(text); message = parsed?.message || parsed?.error || message; } catch {} throw new Error(message); }
  return r.status === 204 ? (undefined as T) : r.json();
}

async function apiUpload<T = any>(path: string, method: 'PUT' | 'POST', formData: FormData): Promise<T> {
  const token = localStorage.getItem('mpay_token');
  const r = await fetch(base + path, {
    method,
    body: formData,
    headers: token ? { Authorization: 'Bearer ' + token } : {}
  });
  if (r.status === 401) {
    localStorage.removeItem('mpay_token');
    localStorage.removeItem('mpay_refresh_token');
    window.location.href = '/login';
    throw new Error('Your session has expired. Please sign in again.');
  }
  if (!r.ok) {
    const text = await r.text();
    let message = text || 'Upload failed';
    try {
      const parsed = JSON.parse(text);
      message = parsed?.message || parsed?.error || message;
    } catch {}
    throw new Error(message);
  }
  return r.json();
}

const money = (n: any) => '₹' + Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 });
const dt = (v?: string) => v ? new Date(v).toLocaleString('en-IN') : '—';
const date = (v?: string) => v ? new Date(v).toLocaleDateString('en-IN') : '—';
const pad2 = (value: number) => String(value).padStart(2, '0');
const localDateTimeInput = (d = new Date()) =>
  d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) + 'T' + pad2(d.getHours()) + ':' + pad2(d.getMinutes());
const isoNow = () => localDateTimeInput();
const localDate = (d = new Date()) => d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate());
const localYearMonth = (d = new Date()) => d.getFullYear() + '-' + pad2(d.getMonth() + 1);

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

function WebHistoryPagination({
  page,
  totalItems,
  totalPages,
  pageSize,
  onPageSizeChange,
  onPrevious,
  onNext
}: {
  page: number;
  totalItems: number;
  totalPages: number;
  pageSize: number;
  onPageSizeChange: (size: number) => void;
  onPrevious: () => void;
  onNext: () => void;
}) {
  if (totalItems <= 10) return null;
  const safeTotalPages = Math.max(totalPages, 1);
  const currentPage = Math.min(page + 1, safeTotalPages);
  const first = Math.min(page * pageSize + 1, totalItems);
  const last = Math.min(totalItems, (page + 1) * pageSize);
  return (
    <div className="wallet-pagination">
      <span>Showing {first}–{last} of {totalItems}</span>
      <div className="wallet-pagination-controls">
        <div className="wallet-page-sizes">
          {[10, 20, 50].map(size => (
            <button
              key={size}
              className={pageSize === size ? 'selected' : ''}
              onClick={() => onPageSizeChange(size)}
            >
              {size}
            </button>
          ))}
        </div>
        <button className="wallet-page-arrow" onClick={onPrevious} disabled={page <= 0}>
          <ChevronLeft size={15} />
        </button>
        <strong>{currentPage} / {safeTotalPages}</strong>
        <button className="wallet-page-arrow" onClick={onNext} disabled={page + 1 >= safeTotalPages}>
          <ChevronRight size={15} />
        </button>
      </div>
    </div>
  );
}

function homeRechargeStatus(item?: RechargeItem) {
  const raw = String(item?.status || 'UNKNOWN').toUpperCase();
  return raw === 'RESERVED' ? 'PENDING' : raw;
}

function webOperatorLabel(operator?: string) {
  switch (String(operator || '').toUpperCase()) {
    case 'AIRTEL': return 'Airtel';
    case 'JIO': return 'Jio';
    case 'VI':
    case 'VODAFONE':
    case 'VODAFONE IDEA': return 'Vodafone Idea (VI)';
    case 'BSNL': return 'BSNL';
    default: return operator || 'Operator';
  }
}

function HomeRecentRecharge({ item, onCopy }: { item?: RechargeItem; onCopy: (text: string, message?: string) => void }) {
  if (!item) return null;
  const status = homeRechargeStatus(item);
  const copyTextValue = [
    'Recharge history',
    'Amount: ' + money(item.amount),
    'Wallet debit: ' + money(item.walletDebitAmount),
    'Mobile: ' + (item.mobileNumber || '—'),
    'Operator: ' + webOperatorLabel(item.operator),
    'Circle: ' + (item.circle || '—'),
    'Plan: ' + (item.planDescription || item.transactionId || '—'),
    'Validity: ' + (item.planValidity || '—'),
    'Transaction ID: ' + (item.transactionId || '—'),
    'Client Request ID: ' + (item.clientRequestId || '—'),
    'Provider reference: ' + (item.providerReference || '—'),
    'Provider: ' + (item.provider || '—'),
    'Status: ' + status,
    'Message: ' + (item.message || '—'),
    'Date & time: ' + dt(item.completedAt || item.createdAt),
    ...(status === 'SUCCESS' ? ['Commission earned: ' + money(item.clientCommission)] : [])
  ].join('\n');
  return (
    <div className="home-recent-recharge-card">
      <div className="home-recharge-main">
        <div className="home-recharge-summary">
          <div>
            <strong>{money(item.amount)}</strong>
            <span>{webOperatorLabel(item.operator)} · {item.mobileNumber || '—'}</span>
            {item.planDescription && <b>{item.planDescription}</b>}
            {item.planValidity && <small>{item.planValidity}</small>}
          </div>
          <div className="home-recharge-status-area">
            <span className={'status-pill status-' + status.toLowerCase().replace(/[^a-z0-9]+/g,'-')}>{status}</span>
            <button className="copy-btn" onClick={()=>onCopy(copyTextValue,'Recharge details copied.')} title="Copy all recharge data">
              <Copy size={14}/><span>Copy</span>
            </button>
          </div>
        </div>
        <div className="home-recharge-divider"/>
        <div className="home-recharge-meta">
          <span>{status === 'PENDING' || status === 'PROCESSING' ? 'Reserved' : 'Wallet debit'} <b>{money(item.walletDebitAmount)}</b></span>
          <span>Transaction <b>{item.transactionId || '—'}</b></span>
          <span>Reference <b>{item.clientRequestId || '—'}</b></span>
          {item.providerReference && <span>Provider ref <b>{item.providerReference}</b></span>}
        </div>
        <div className="home-recharge-footer">
          <span>{dt(item.completedAt || item.createdAt)}</span>
          {status === 'SUCCESS' && <b className="amount-credit">Commission earned: {money(item.clientCommission)}</b>}
        </div>
        {item.message && <p className="home-recharge-message">{item.message}</p>}
      </div>
    </div>
  );
}

function HomeEarningsPeriod({ period, isToday }: { period?: any; isToday: boolean }) {
  if (!period) {
    return (
      <div className="home-earnings-card loading">
        <b>Earnings are loading</b>
        <span>We are refreshing the latest recharge commission summary.</span>
        <div className="home-earnings-progress"><i/></div>
      </div>
    );
  }
  return (
    <div className="home-earnings-card">
      <span className="home-earnings-period">{isToday ? 'As of ' + date(period.to) : date(period.from) + ' → ' + date(period.to)}</span>
      <div className="home-earnings-values">
        <div><small>Commission earned</small><strong className="amount-credit">{money(period.commission)}</strong><em>{period.successfulRechargeCount || 0} successful recharges</em></div>
        <div><small>Recharge volume</small><strong>{money(period.successfulRechargeAmount)}</strong></div>
      </div>
    </div>
  );
}

function homeRechargeStatus(item?: RechargeItem) {
  const raw = String(item?.status || 'UNKNOWN').toUpperCase();
  return raw === 'RESERVED' ? 'PENDING' : raw;
}

function webOperatorLabel(operator?: string) {
  switch (String(operator || '').toUpperCase()) {
    case 'AIRTEL': return 'Airtel';
    case 'JIO': return 'Jio';
    case 'VI':
    case 'VODAFONE':
    case 'VODAFONE IDEA': return 'Vodafone Idea (VI)';
    case 'BSNL': return 'BSNL';
    default: return operator || 'Operator';
  }
}

function HomeRecentRecharge({ item, onCopy }: { item?: RechargeItem; onCopy: (text: string, message?: string) => void }) {
  if (!item) return null;
  const status = homeRechargeStatus(item);
  const copyTextValue = [
    'Recharge history',
    'Amount: ' + money(item.amount),
    'Wallet debit: ' + money(item.walletDebitAmount),
    'Mobile: ' + (item.mobileNumber || '—'),
    'Operator: ' + webOperatorLabel(item.operator),
    'Circle: ' + (item.circle || '—'),
    'Plan: ' + (item.planDescription || item.transactionId || '—'),
    'Validity: ' + (item.planValidity || '—'),
    'Transaction ID: ' + (item.transactionId || '—'),
    'Client Request ID: ' + (item.clientRequestId || '—'),
    'Provider reference: ' + (item.providerReference || '—'),
    'Provider: ' + (item.provider || '—'),
    'Status: ' + status,
    'Message: ' + (item.message || '—'),
    'Date & time: ' + dt(item.completedAt || item.createdAt),
    ...(status === 'SUCCESS' ? ['Commission earned: ' + money(item.clientCommission)] : [])
  ].join('\n');
  return (
    <div className="home-recent-recharge-card">
      <div className="home-recharge-main">
        <div className="home-recharge-summary">
          <div>
            <strong>{money(item.amount)}</strong>
            <span>{webOperatorLabel(item.operator)} · {item.mobileNumber || '—'}</span>
            {item.planDescription && <b>{item.planDescription}</b>}
            {item.planValidity && <small>{item.planValidity}</small>}
          </div>
          <div className="home-recharge-status-area">
            <span className={'status-pill status-' + status.toLowerCase().replace(/[^a-z0-9]+/g,'-')}>{status}</span>
            <button className="copy-btn" onClick={()=>onCopy(copyTextValue,'Recharge details copied.')} title="Copy all recharge data">
              <Copy size={14}/><span>Copy</span>
            </button>
          </div>
        </div>
        <div className="home-recharge-divider"/>
        <div className="home-recharge-meta">
          <span>{status === 'PENDING' || status === 'PROCESSING' ? 'Reserved' : 'Wallet debit'} <b>{money(item.walletDebitAmount)}</b></span>
          <span>Transaction <b>{item.transactionId || '—'}</b></span>
          <span>Reference <b>{item.clientRequestId || '—'}</b></span>
          {item.providerReference && <span>Provider ref <b>{item.providerReference}</b></span>}
        </div>
        <div className="home-recharge-footer">
          <span>{dt(item.completedAt || item.createdAt)}</span>
          {status === 'SUCCESS' && <b className="amount-credit">Commission earned: {money(item.clientCommission)}</b>}
        </div>
        {item.message && <p className="home-recharge-message">{item.message}</p>}
      </div>
    </div>
  );
}

export default function Portal() {
  const webCapabilities = useWebCapabilities();
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
  const [historyFrom, setHistoryFrom] = useState(localDate());
  const [historyTo, setHistoryTo] = useState(localDate());
  const [rechargeFunding, setRechargeFunding] = useState<'WALLET'|'RAZORPAY'|'PAYU'>('WALLET');

  const [walletHistory, setWalletHistory] = useState<WalletItem[]>([]);
  const [walletHistoryKind, setWalletHistoryKind] = useState('');
  const [walletDateFilter, setWalletDateFilter] = useState<'TODAY'|'LAST_7_DAYS'|'THIS_MONTH'|'CUSTOM'>('TODAY');
  const [walletHistoryFrom, setWalletHistoryFrom] = useState(localDate());
  const [walletHistoryTo, setWalletHistoryTo] = useState(localDate());
  const [walletHistoryPage, setWalletHistoryPage] = useState(0);
  const [walletHistoryTotalItems, setWalletHistoryTotalItems] = useState(0);
  const [walletHistoryTotalPages, setWalletHistoryTotalPages] = useState(0);
  const [walletHistoryHasNext, setWalletHistoryHasNext] = useState(false);
  const [walletHistoryLoading, setWalletHistoryLoading] = useState(false);
  const [walletHistoryPageSize, setWalletHistoryPageSize] = useState(20);
  const [withdrawalPage, setWithdrawalPage] = useState(0);
  const [withdrawalTotalItems, setWithdrawalTotalItems] = useState(0);
  const [withdrawalTotalPages, setWithdrawalTotalPages] = useState(0);
  const [withdrawalLoading, setWithdrawalLoading] = useState(false);
  const [selectedWalletItem, setSelectedWalletItem] = useState<WalletItem>();
  const [selectedRechargeDetail, setSelectedRechargeDetail] = useState<any>();
  const [selectedWithdrawalDetail, setSelectedWithdrawalDetail] = useState<any>();
  const [deletingAccount, setDeletingAccount] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteConfirmation, setDeleteConfirmation] = useState('');
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
  const [calendarMonth, setCalendarMonth] = useState(localYearMonth());
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
  const [homeGreeting, setHomeGreeting] = useState('Good day');
  const [latestRecharge, setLatestRecharge] = useState<RechargeItem>();
  const [latestRechargeLoading, setLatestRechargeLoading] = useState(false);
  const [homeActionModal, setHomeActionModal] = useState<'add'|'withdraw'|null>(null);
  const rentalLoadSeq = useRef(0);
  const historyLoadSeq = useRef(0);

  const walletSigned = (item: WalletItem) => {
    const amount = Math.abs(Number(item.amount || 0));
    const negative = ['DEBIT', 'WITHDRAW'].includes(String(item.type || '').toUpperCase()) || String(item.referenceType || '').toUpperCase() === 'RENTAL_PAYMENT';
    return negative ? -amount : amount;
  };
  const walletAmountClass = (item: WalletItem) => walletSigned(item) < 0 ? 'amount-debit' : 'amount-credit';
  const walletAmountLabel = (item: WalletItem) => (walletSigned(item) < 0 ? '-' : '+') + money(Math.abs(Number(item.amount || 0)));

  async function loadHistory() {
    const requestSeq = ++historyLoadSeq.current;
    if (historyFrom && historyTo && historyTo < historyFrom) {
      setNotice('The history end date must be on or after the start date.');
      return;
    }
    const rechargeParams = new URLSearchParams({ page:'0', size:'25' });
    if (historyFrom) rechargeParams.set('from', historyFrom);
    if (historyTo) rechargeParams.set('to', historyTo);
    const walletParams = new URLSearchParams({ page:'0', size:'25' });
    if (historyKind) walletParams.set('kind', historyKind);
    if (historyFrom) walletParams.set('from', historyFrom);
    if (historyTo) walletParams.set('to', historyTo);

    const [rechargeResult, walletResult] = await Promise.allSettled([
      api<any>('/api/v1/recharge/history?' + rechargeParams.toString()),
      api<any>('/api/v1/wallet/history?' + walletParams.toString())
    ]);

    const messages:string[] = [];
    if (requestSeq !== historyLoadSeq.current) return;

    if (rechargeResult.status === 'fulfilled') {
      const r = rechargeResult.value;
      setRecharges(r?.items || r?.content || r || []);
    } else {
      messages.push(rechargeResult.reason?.message || 'Recharge history could not be loaded.');
    }
    if (walletResult.status === 'fulfilled') {
      const w = walletResult.value;
      setWalletHistory(w?.items || w?.content || w || []);
    } else {
      messages.push(walletResult.reason?.message || 'Wallet history could not be loaded.');
    }
    if (messages.length) setNotice(messages.join(' '));
  }

  async function loadCommissionSummary() {
    try { setCommissionSummary(await api<any>('/api/v1/recharge/commission-summary')); } catch {}
  }

  async function loadLatestRecharge() {
    setLatestRechargeLoading(true);
    try {
      const today = localDate();
      const params = new URLSearchParams({ page: '0', size: '1', from: today, to: today });
      const data = await api<any>('/api/v1/recharge/history?' + params.toString());
      const items = data?.items || data?.content || data || [];
      setLatestRecharge(items?.[0]);
    } catch (e:any) {
      setNotice(e.message || 'Unable to load the latest recharge.');
    } finally {
      setLatestRechargeLoading(false);
    }
  }

  async function loadWalletHistory(
    page = 0,
    overrides?: { kind?: string; from?: string; to?: string; size?: number }
  ) {
    const size = overrides?.size ?? walletHistoryPageSize;
    const kind = overrides?.kind ?? walletHistoryKind;
    const from = overrides?.from ?? walletHistoryFrom;
    const to = overrides?.to ?? walletHistoryTo;
    if (from && to && to < from) {
      setNotice('The wallet history end date must be on or after the start date.');
      return;
    }
    setWalletHistoryLoading(true);
    try {
      const params = new URLSearchParams({
        page: String(Math.max(0, page)),
        size: String(size),
        kind: kind || 'ALL',
        from,
        to
      });
      const data = await api<any>('/api/v1/wallet/history?' + params.toString());
      setWalletHistory(data?.items || data?.content || data || []);
      setWalletHistoryPage(Number(data?.page ?? page));
      setWalletHistoryTotalItems(Number(data?.totalItems ?? data?.items?.length ?? 0));
      setWalletHistoryTotalPages(Number(data?.totalPages ?? 0));
      setWalletHistoryHasNext(Boolean(data?.hasNext));
    } catch (e:any) {
      setNotice(e.message || 'Unable to load wallet history.');
    } finally {
      setWalletHistoryLoading(false);
    }
  }

  async function loadWithdrawals(page = 0, size = walletHistoryPageSize) {
    setWithdrawalLoading(true);
    try {
      const data = await api<any>('/api/v1/wallet/withdrawals?page=' + Math.max(0, page) + '&size=' + size);
      setWithdrawals(data?.items || data?.content || data || []);
      setWithdrawalPage(Number(data?.page ?? page));
      setWithdrawalTotalItems(Number(data?.totalItems ?? data?.items?.length ?? 0));
      setWithdrawalTotalPages(Number(data?.totalPages ?? 0));
    } catch (e:any) {
      setNotice(e.message || 'Unable to load withdrawal history.');
    } finally {
      setWithdrawalLoading(false);
    }
  }

  function selectWalletHistoryFilter(kind: string) {
    setWalletHistoryKind(kind);
    setWalletHistoryPage(0);
    void loadWalletHistory(0, { kind });
  }

  function selectWalletDatePreset(filter: 'TODAY'|'LAST_7_DAYS'|'THIS_MONTH') {
    const today = localDate();
    let from = today;
    if (filter === 'LAST_7_DAYS') {
      const d = new Date();
      d.setDate(d.getDate() - 6);
      from = localDate(d);
    } else if (filter === 'THIS_MONTH') {
      const d = new Date();
      d.setDate(1);
      from = localDate(d);
    }
    setWalletDateFilter(filter);
    setWalletHistoryFrom(from);
    setWalletHistoryTo(today);
    setWalletHistoryPage(0);
    void loadWalletHistory(0, { from, to: today });
  }

  function applyWalletCustomRange() {
    const today = localDate();
    const from = walletHistoryFrom || today;
    const to = walletHistoryTo || today;
    if (to < from) {
      setNotice('The wallet history end date must be on or after the start date.');
      return;
    }
    setWalletDateFilter('CUSTOM');
    setWalletHistoryPage(0);
    void loadWalletHistory(0, { from, to });
  }

  function changeWalletPageSize(size: number) {
    const normalized = [10, 20, 50].includes(size) ? size : 20;
    setWalletHistoryPageSize(normalized);
    setWalletHistoryPage(0);
    setWithdrawalPage(0);
    void loadWalletHistory(0, { size: normalized });
    void loadWithdrawals(0, normalized);
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
    const [profileResult, vendorResult] = await Promise.allSettled([
      api<Me>('/api/v1/profile'),
      api<RentalVendor>('/api/v1/car-rental/vendor')
    ]);

    if (profileResult.status === 'fulfilled') {
      const p = profileResult.value;
      setMe(p);
      setProfileForm({ name: p?.name || '', email: p?.email || '' });
    } else {
      setNotice(profileResult.reason?.message || 'Unable to load profile.');
    }

    if (vendorResult.status === 'rejected') {
      setVendor(undefined);
      setVendorVehicles([]);
      setVendorPayouts([]);
      setVendorEarnings(undefined);
      if (profileResult.status !== 'fulfilled') setNotice(vendorResult.reason?.message || 'Unable to load vendor workspace.');
      return;
    }

    const v = vendorResult.value;
    setVendor(v);
    if (!v?.vendorId) {
      setVendorVehicles([]);
      setVendorPayouts([]);
      setVendorEarnings(undefined);
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
  }

  async function openWalletItem(item: WalletItem) {
    setSelectedWalletItem(item);
    setSelectedRechargeDetail(undefined);
    setSelectedWithdrawalDetail(undefined);
    const referenceType = String(item.referenceType || '').toUpperCase();
    if (!item.referenceId) return;
    try {
      if (referenceType === 'RECHARGE') {
        setSelectedRechargeDetail(await api<any>('/api/v1/recharge/' + encodeURIComponent(String(item.referenceId))));
      } else if (referenceType === 'WITHDRAWAL') {
        setSelectedWithdrawalDetail(await api<any>('/api/v1/wallet/withdrawals/' + encodeURIComponent(String(item.referenceId))));
      }
    } catch (e:any) {
      setNotice(e.message || (referenceType === 'WITHDRAWAL' ? 'Unable to load withdrawal transaction details.' : 'Unable to load recharge transaction details.'));
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
  async function deleteAccount() {
    if (deletePassword.trim().length === 0 || deleteConfirmation.trim().toUpperCase() !== 'DELETE') return;
    setDeletingAccount(true);
    setNotice('');
    try {
      await api('/api/v1/account/deletion', {
        method: 'POST',
        body: JSON.stringify({ password: deletePassword, confirmation: deleteConfirmation })
      });
      localStorage.removeItem('mpay_token');
      localStorage.removeItem('mpay_refresh_token');
      window.location.href = '/';
    } catch (e:any) {
      setNotice(e.message || 'Unable to delete your account.');
    } finally {
      setDeletingAccount(false);
    }
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
          await loadWalletHistory(0);
          await loadWithdrawals(0);
          setNotice(purpose === 'recharge'
            ? ('Payment verified. Recharge status: ' + String(verified.rechargeStatus || verified.status || 'submitted') + '.')
            : 'Payment verified and wallet updated.');
        } catch(e:any) { setNotice(e.message || 'Payment verification failed.'); }
      },
      modal:{ ondismiss:()=>setNotice('Payment window closed.') }
    });
    rzp.open();
  }

  async function monitorPayUVerification(orderId:string, purpose:'wallet'|'recharge') {
    for(let i=0;i<24;i++){
      await new Promise(r=>setTimeout(r,5000));
      try {
        const verified=await api<any>('/api/v1/payments/verify',{method:'POST',body:JSON.stringify({provider:'payu',orderId})});
        if(verified.status==='CAPTURED' || verified.transactionId || verified.rechargeStatus){
          await refreshWallet();
          await loadHistory();
          await loadWalletHistory(0);
          await loadWithdrawals(0);
          setNotice(purpose === 'recharge'
            ? ('PayU payment verified. Recharge status: ' + String(verified.rechargeStatus || verified.status || 'submitted') + '.')
            : 'PayU payment verified and wallet updated.');
          return;
        }
      } catch {}
    }
    setNotice('PayU checkout did not complete within the verification window. Refresh Wallet/History after returning.');
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
    void monitorPayUVerification(orderId,purpose);
  }

  async function addMoney() {
    const amount=Number(addMoneyAmount);
    if (!(amount >= 1 && amount <= 50000)) { setNotice('Enter a wallet top-up amount between ₹1 and ₹50,000.'); return; }
    setBusy(true); setNotice('');
    try {
      const order=await api<any>('/api/v1/payments/orders',{method:'POST',body:JSON.stringify({
        amount, provider:addMoneyProvider, clientRequestId:crypto.randomUUID(), purpose:'ADD_MONEY'
      })});
      if(addMoneyProvider==='mock'){
        await api<any>('/api/v1/payments/verify',{method:'POST',body:JSON.stringify({provider:'mock',orderId:order.orderId})});
        await refreshWallet();
        setAddMoneyAmount('');
        setNotice('Mock wallet top-up completed.');
        await loadWalletHistory(0);
        await loadWithdrawals(0);
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
    const availableBalance = Number(wallet?.availableBalance || 0);
    if (!(amount >= 1) || amount > availableBalance) {
      setNotice('Enter a valid withdrawal amount of at least ₹1 and no more than the available balance.');
      return;
    }
    if (!/^[A-Za-z0-9]+@[A-Za-z]+$/.test(upi)) { setNotice('Enter a valid UPI ID.'); return; }
    setBusy(true); setNotice('');
    try {
      const result=await api<WithdrawalItem>('/api/v1/wallet/withdraw',{method:'POST',body:JSON.stringify({
        amount, provider:withdrawProvider, upiId:upi, clientRequestId:crypto.randomUUID()
      })});
      await refreshWallet();
      setWithdrawals(x=>[result,...x]);
      setWithdrawAmount('');
      setNotice(result.message || ('Withdrawal ' + String(result.status || '').toLowerCase() + ' for ' + upi + '.'));
      await loadWalletHistory(0);
      await loadWithdrawals(0);
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

  async function loadRentalCars(startDate='',endDate='',location='') {
    const requestSeq = ++rentalLoadSeq.current;
    try {
      const params=new URLSearchParams();
      if(startDate) params.set('startDate',startDate);
      if(endDate) params.set('endDate',endDate);
      if(location.trim()) params.set('location',location.trim());
      const carsPath='/api/v1/car-rental/cars' + (params.toString() ? '?' + params.toString() : '');
      const available=await api<any>(carsPath);
      if(requestSeq !== rentalLoadSeq.current) return;
      setCars(available?.items || available || []);
    } catch(e:any) {
      if(requestSeq === rentalLoadSeq.current) setNotice(e.message || 'Unable to load rental inventory.');
    }
  }

  async function loadBookings() {
    try {
      const existing=await api<any>('/api/v1/car-rental/bookings?page=0&size=25');
      setBookings(existing?.items || existing?.content || existing || []);
    } catch(e:any) {
      setNotice(e.message || 'Unable to load bookings.');
    }
  }

  async function loadRentalData(startDate='',endDate='',location='') {
    await Promise.all([loadRentalCars(startDate,endDate,location), loadBookings()]);
  }

  function refreshRentalData() {
    void loadRentalCars(rentalSearch.startDate,rentalSearch.endDate,rentalSearch.location);
  }

  function refreshBookings() {
    void loadBookings();
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
    setSelectedCar(undefined); setRentalQuote(undefined); loadRentalCars();
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
      await Promise.all([refreshWallet(), loadBookings()]);
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
      await Promise.all([refreshWallet(), loadBookings()]);
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

  async function uploadDriverPhoto(driverId:string,file:File) {
    setBusy(true);
    try {
      const fd=new FormData(); fd.append('photo',file);
      const saved=await apiUpload<RentalCar>('/api/v1/car-rental/vendor/drivers/'+encodeURIComponent(driverId)+'/photo','PUT',fd);
      setVendorVehicles(v=>v.map(x=>x.id===saved.id?saved:x));
      setSelectedVendorVehicle(saved);
      setNotice('Driver photo uploaded.');
    } catch(e:any){setNotice(e.message || 'Unable to upload driver photo.');}
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

  useEffect(() => () => { if(profileImage.startsWith('blob:')) URL.revokeObjectURL(profileImage); }, [profileImage]);

  useEffect(()=>{
    const hour = new Date().getHours();
    setHomeGreeting(hour >= 5 && hour <= 11 ? 'Good morning' : hour >= 12 && hour <= 16 ? 'Good afternoon' : hour >= 17 && hour <= 21 ? 'Good evening' : 'Good night');
    if(!localStorage.getItem('mpay_token')){window.location.href='/login';return;}
    void Promise.allSettled([
      api<Me>('/api/v1/me'),
      api<Wallet>('/api/v1/wallet'),
      api<any>('/api/v1/recharge/commission-summary')
    ]).then(([meResult,walletResult,commissionResult])=>{
      if(meResult.status !== 'fulfilled' || walletResult.status !== 'fulfilled'){
        localStorage.removeItem('mpay_token');
        localStorage.removeItem('mpay_refresh_token');
        window.location.href='/login';
        return;
      }
      const a=meResult.value;
      setMe(a);
      setWallet(walletResult.value);
      setProfileForm({name:a?.name || '',email:a?.email || ''});
      if(commissionResult.status === 'fulfilled') setCommissionSummary(commissionResult.value);
    });
    void loadProfileImage();
  },[]);

  useEffect(()=>{
    if(view==='home'){
      void Promise.all([refreshWallet(), loadLatestRecharge(), loadCommissionSummary()]);
    } else if(view==='history'){
      void loadHistory();
    } else if(view==='wallet'){
      void Promise.all([refreshWallet(), loadWalletHistory(0), loadWithdrawals(0), loadCommissionSummary()]);
    } else if(view==='rental'){
      void loadRentalCars(rentalSearch.startDate,rentalSearch.endDate,rentalSearch.location);
    } else if(view==='bookings'){
      void loadBookings();
    } else if(view==='account'){
      void loadAccountData();
    }
  },[view]);

  useEffect(()=>{
    if(view==='history'){
      void loadHistory();
    }
  },[historyKind,historyFrom,historyTo]);

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
    if (!webCapabilities.canUseContactPicker) {
      setNotice('Contact selection is available only on supported mobile browsers. Enter the number manually.');
      return;
    }
    const contacts = (navigator as Navigator & {
      contacts?: { select?: (properties: string[], options?: { multiple?: boolean }) => Promise<Array<{ tel?: string[] }>> };
    }).contacts;
    if (typeof contacts?.select !== 'function') return;
    try {
      const selected = await contacts.select(['tel'], { multiple: false });
      const tel = selected?.[0]?.tel?.[0] || '';
      const normalized = String(tel).replace(/\D/g, '').slice(-10);
      if (normalized.length === 10) {
        setMobile(normalized);
        setNotice('Mobile number selected from contacts.');
      }
    } catch {
      // User cancelled the native picker; keep the current number unchanged.
    }
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

  const vendorStatus = String(vendor?.status || '').toUpperCase();
  const vendorVerified = vendorStatus === 'VERIFIED';

  return <div className="portal-shell" data-form-factor={webCapabilities.formFactor} data-contact-picker={webCapabilities.canUseContactPicker ? 'available' : 'unavailable'}>
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

      {view==='home' && <section className="portal-content portal-home android-home-parity">
        <section className="android-home-greeting">
          <div className="android-home-greeting-copy">
            <span>{homeGreeting}</span>
            <h2>{me?.name || 'there'}</h2>
          </div>
          <div className="android-home-avatar">
            {profileImage ? <img src={profileImage} alt="Profile"/> : (me?.name || 'U').charAt(0).toUpperCase()}
          </div>
        </section>

        <section className="portal-wallet-hero android-wallet-hero">
          <div className="portal-wallet-hero-top">
            <div className="portal-wallet-label"><span className="portal-wallet-icon"><WalletCards size={17}/></span><span>MY WALLET</span></div>
            <button className="android-wallet-refresh" onClick={()=>refreshWallet()} disabled={busy} title="Refresh balance"><RefreshCw size={15}/></button>
          </div>
          <div className="portal-wallet-copy">
            <span>Available balance</span>
            <strong>{money(wallet?.availableBalance)}</strong>
          </div>
          <div className="portal-wallet-breakdown">
            <div><span>Total</span><b>{money(wallet?.balance)}</b></div>
            <div><span>Reserved</span><b>{money(wallet?.reservedBalance)}</b></div>
          </div>
          {Number(wallet?.reservedBalance || 0) > 0 && <p className="android-wallet-reserved">{money(wallet?.reservedBalance)} reserved in pending transactions</p>}
          <div className="portal-wallet-actions">
            <button className="landing-primary" onClick={()=>setHomeActionModal('add')}><Plus size={15}/> Add Money</button>
            <button className="landing-secondary" onClick={()=>setHomeActionModal('withdraw')}><Banknote size={15}/> Withdraw to UPI</button>
          </div>
        </section>

        <section className="portal-home-section android-home-section">
          <div className="portal-home-section-head">
            <div><span>QUICK ACTIONS</span><h2>Quick actions</h2></div>
          </div>
          <div className="android-home-quick-card">
            <button className="android-action-card recharge" onClick={()=>setView('recharge')}>
              <span><Smartphone size={19}/></span><b>Mobile Recharge</b>
            </button>
            <button className="android-action-card add" onClick={()=>setHomeActionModal('add')}>
              <span><Plus size={19}/></span><b>Add Money</b>
            </button>
            <button className="android-action-card bookings" onClick={()=>setView('bookings')}>
              <span><Clock3 size={19}/></span><b>My Bookings</b>
            </button>
          </div>
        </section>

        <section className="portal-home-section android-home-section">
          <div className="portal-home-section-head">
            <div><span>MARKETPLACE</span><h2>Marketplace</h2></div>
          </div>
          <button className="android-marketplace-card" onClick={()=>setView('rental')}>
            <span className="android-marketplace-icon"><Car size={21}/></span>
            <span className="android-marketplace-copy"><b>Car Rental</b><small>Chauffeur-driven cars, available directly from here.</small></span>
            <span className="android-marketplace-action">Explore</span>
          </button>
        </section>

        <button className="android-recharge-history-card" onClick={()=>{setView('history');loadHistory();}}>
          <span className="android-recharge-history-icon"><History size={19}/></span>
          <span><b>Recharge History</b><small>View your submitted and completed mobile recharges.</small></span>
          <b>View</b>
        </button>

        {(latestRechargeLoading || latestRecharge) && <section className="android-home-section">
          <div className="android-home-section-title">
            <h2>Latest recharge</h2>
            <button onClick={()=>{setView('history');loadHistory();}}>View all</button>
          </div>
          {latestRechargeLoading
            ? <div className="home-earnings-loading">Refreshing the latest recharge…</div>
            : <HomeRecentRecharge item={latestRecharge} onCopy={copyText}/>}
        </section>}

        <section className="android-home-section">
          <div className="android-home-section-title">
            <h2>Today’s recharge earnings</h2>
            <button onClick={()=>loadCommissionSummary()} disabled={busy}><RefreshCw size={15}/></button>
          </div>
          <HomeEarningsPeriod period={commissionSummary?.daily} isToday={true}/>
        </section>

        <section className="android-home-section">
          <div className="android-home-section-title">
            <h2>Monthly recharge earnings</h2>
          </div>
          <HomeEarningsPeriod period={commissionSummary?.monthly} isToday={false}/>
        </section>

        {homeActionModal && <div className="modal-backdrop" onClick={()=>!busy&&setHomeActionModal(null)}>
          <div className="portal-modal small-modal home-action-modal" onClick={e=>e.stopPropagation()}>
            <div className="panel-head">
              <div>
                <h2>{homeActionModal === 'add' ? 'Add money' : 'Withdraw to UPI'}</h2>
                <p>{homeActionModal === 'add' ? 'Choose how to fund the wallet.' : 'UPI ID is required for every withdrawal.'}</p>
              </div>
              <button className="icon-btn" onClick={()=>!busy&&setHomeActionModal(null)}><X size={17}/></button>
            </div>
            {homeActionModal === 'add' ? (
              <>
                <div className="wallet-current-balance">Current available balance <b>{money(wallet?.availableBalance)}</b></div>
                <div className="wallet-provider-picker">
                  {(['mock','razorpay','payu'] as const).map(provider => <button key={provider} className={addMoneyProvider===provider?'selected':''} onClick={()=>setAddMoneyProvider(provider)} disabled={busy}>{provider === 'mock' ? 'Mock' : provider === 'razorpay' ? 'Razorpay' : 'PayU'}</button>)}
                </div>
                <label className="wallet-field-label">Amount (INR)
                  <div className="wallet-input-shell"><span>₹</span><input inputMode="decimal" maxLength={10} value={addMoneyAmount} onChange={e=>setAddMoneyAmount(e.target.value.replace(/[^0-9.]/g,''))} placeholder="Enter amount"/></div>
                </label>
                <div className="wallet-field-help">Minimum ₹1 · Maximum ₹50,000</div>
                <button className="wallet-primary-wide" disabled={busy || !(Number(addMoneyAmount)>=1 && Number(addMoneyAmount)<=50000)} onClick={async()=>{await addMoney();if(!notice)setHomeActionModal(null);}}>
                  {busy ? 'Processing…' : 'Continue'} <ArrowRight size={15}/>
                </button>
              </>
            ) : (
              <>
                <div className="wallet-current-balance">Available to withdraw <b>{money(wallet?.availableBalance)}</b></div>
                <div className="wallet-provider-picker">
                  {(['mock','razorpay','payu'] as const).map(provider => <button key={provider} className={withdrawProvider===provider?'selected':''} onClick={()=>setWithdrawProvider(provider)} disabled={busy}>{provider === 'mock' ? 'Mock' : provider === 'razorpay' ? 'Razorpay' : 'PayU'}</button>)}
                </div>
                <div className="wallet-field-grid">
                  <label className="wallet-field-label">Amount (INR)
                    <div className="wallet-input-shell"><span>₹</span><input inputMode="decimal" maxLength={10} value={withdrawAmount} onChange={e=>setWithdrawAmount(e.target.value.replace(/[^0-9.]/g,''))} placeholder="Enter amount"/></div>
                  </label>
                  <label className="wallet-field-label">UPI ID (required)
                    <input className="wallet-text-input" maxLength={120} value={withdrawUpi} onChange={e=>setWithdrawUpi(e.target.value)} placeholder="name@upi"/>
                  </label>
                </div>
                <div className="wallet-field-help">Minimum ₹1 · Available {money(wallet?.availableBalance)}</div>
                <button className="wallet-primary-wide" disabled={busy || !(Number(withdrawAmount)>=1 && Number(withdrawAmount)<=Number(wallet?.availableBalance||0)) || !/^[A-Za-z0-9]+@[A-Za-z]+$/.test(withdrawUpi.trim())} onClick={async()=>{await withdrawMoney();if(!notice)setHomeActionModal(null);}}>
                  {busy ? 'Processing…' : 'Withdraw'} <ArrowRight size={15}/>
                </button>
              </>
            )}
          </div>
        </div>}
      </section>}

      {view==='recharge' && <section className="portal-content"><div className="portal-panel">
        <div className="panel-head"><div><h2>Recharge a mobile</h2><p>Detect the operator, edit the detected operator if required, load plans and choose how to fund the recharge.</p></div></div>
        <div className={'recharge-web-form ' + (webCapabilities.canUseContactPicker ? 'has-contact-picker' : 'no-contact-picker')}>
          <input inputMode="numeric" maxLength={10} value={mobile} onChange={e=>setMobile(e.target.value.replace(/\D/g,''))} placeholder="10-digit mobile number" aria-label="10-digit mobile number"/>
          {webCapabilities.canUseContactPicker && <button className="landing-secondary contact-picker-btn" disabled={busy} onClick={chooseContact}><Smartphone size={15}/> Contacts</button>}
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

      {view==='wallet' && <section className="portal-content portal-wallet-parity">
        <div className="wallet-parity-header">
          <div>
            <span>PERSONAL WALLET</span>
            <h2>Wallet</h2>
            <p>Balance, earnings and wallet activity</p>
          </div>
          <button className="wallet-icon-action" onClick={()=>refreshWallet()} disabled={busy} title="Refresh balance">
            <RefreshCw size={17}/>
          </button>
        </div>

        <section className="wallet-parity-hero">
          <div className="wallet-parity-hero-main">
            <div className="wallet-parity-label"><WalletCards size={17}/><span>AVAILABLE BALANCE</span></div>
            <strong>{busy ? 'Loading…' : money(wallet?.availableBalance)}</strong>
            <div className="wallet-parity-breakdown">
              <span>Total <b>{money(wallet?.balance)}</b></span>
              <span>Reserved <b>{money(wallet?.reservedBalance)}</b></span>
            </div>
            {Number(wallet?.reservedBalance || 0) > 0 &&
              <p>₹{Number(wallet?.reservedBalance || 0).toLocaleString('en-IN',{minimumFractionDigits:2})} reserved in pending transactions.</p>
            }
          </div>
          <div className="wallet-parity-actions">
            <button className="wallet-primary-btn" onClick={()=>document.getElementById('wallet-add-money')?.scrollIntoView({behavior:'smooth',block:'center'})}>
              <Plus size={16}/> Add Money
            </button>
            <button className="wallet-outline-btn" onClick={()=>{document.getElementById('wallet-withdraw')?.scrollIntoView({behavior:'smooth',block:'center'});}}>
              <Send size={16}/> Withdraw to UPI
            </button>
          </div>
        </section>

        <div className="wallet-parity-action-grid">
          <div className="portal-panel wallet-parity-card" id="wallet-add-money">
            <div className="wallet-parity-card-head">
              <div><h3>Add money</h3><p>Choose how to fund the wallet.</p></div>
              <span className="wallet-parity-card-icon"><Plus size={18}/></span>
            </div>
            <div className="wallet-current-balance">Current available balance <b>{money(wallet?.availableBalance)}</b></div>
            <div className="wallet-provider-picker">
              {(['mock','razorpay','payu'] as const).map(provider => (
                <button key={provider} className={addMoneyProvider===provider?'selected':''} onClick={()=>setAddMoneyProvider(provider)} disabled={busy}>
                  {provider === 'mock' ? 'Mock' : provider === 'razorpay' ? 'Razorpay' : 'PayU'}
                </button>
              ))}
            </div>
            <label className="wallet-field-label">Amount (INR)
              <div className="wallet-input-shell"><span>₹</span><input inputMode="decimal" maxLength={10} value={addMoneyAmount} onChange={e=>setAddMoneyAmount(e.target.value.replace(/[^0-9.]/g,''))} placeholder="Enter amount"/></div>
            </label>
            <div className="wallet-field-help">Minimum ₹1 · Maximum ₹50,000</div>
            <button className="wallet-primary-wide" disabled={busy || !(Number(addMoneyAmount) >= 1 && Number(addMoneyAmount) <= 50000)} onClick={addMoney}>
              {busy ? 'Processing…' : 'Continue'} <ArrowRight size={15}/>
            </button>
          </div>

          <div className="portal-panel wallet-parity-card" id="wallet-withdraw">
            <div className="wallet-parity-card-head">
              <div><h3>Withdraw to UPI</h3><p>UPI ID is required for every withdrawal.</p></div>
              <span className="wallet-parity-card-icon withdraw"><Send size={18}/></span>
            </div>
            <div className="wallet-current-balance">Available to withdraw <b>{money(wallet?.availableBalance)}</b></div>
            <div className="wallet-provider-picker">
              {(['mock','razorpay','payu'] as const).map(provider => (
                <button key={provider} className={withdrawProvider===provider?'selected':''} onClick={()=>setWithdrawProvider(provider)} disabled={busy}>
                  {provider === 'mock' ? 'Mock' : provider === 'razorpay' ? 'Razorpay' : 'PayU'}
                </button>
              ))}
            </div>
            <div className="wallet-field-grid">
              <label className="wallet-field-label">Amount (INR)
                <div className="wallet-input-shell"><span>₹</span><input inputMode="decimal" maxLength={10} value={withdrawAmount} onChange={e=>setWithdrawAmount(e.target.value.replace(/[^0-9.]/g,''))} placeholder="Enter amount"/></div>
              </label>
              <label className="wallet-field-label">UPI ID (required)
                <input className="wallet-text-input" maxLength={120} value={withdrawUpi} onChange={e=>setWithdrawUpi(e.target.value)} placeholder="name@upi"/>
              </label>
            </div>
            <div className="wallet-field-help">Minimum ₹1 · Available {money(wallet?.availableBalance)}</div>
            <button className="wallet-primary-wide" disabled={busy || !(Number(withdrawAmount) >= 1 && Number(withdrawAmount) <= Number(wallet?.availableBalance || 0)) || !/^[A-Za-z0-9]+@[A-Za-z]+$/.test(withdrawUpi.trim())} onClick={withdrawMoney}>
              {busy ? 'Processing…' : 'Withdraw'} <ArrowRight size={15}/>
            </button>
          </div>
        </div>

        <section className="portal-panel wallet-parity-section">
          <div className="wallet-parity-section-head">
            <div><span>EARNINGS</span><h3>Earnings</h3><p>Recharge commission and volume for the same periods shown in Android.</p></div>
            <button className="wallet-icon-action" onClick={()=>loadCommissionSummary()} disabled={busy}><RefreshCw size={16}/></button>
          </div>
          <div className="wallet-earnings-grid">
            {[
              ['Today', commissionSummary?.daily, true],
              ['This month', commissionSummary?.monthly, false]
            ].map(([title, period, isToday]: any) => (
              <div className="wallet-earning-period" key={String(title)}>
                <b className="wallet-earning-title">{title}</b>
                <span>{isToday ? 'As of ' + date(period?.to) : (date(period?.from) + ' → ' + date(period?.to))}</span>
                <div className="wallet-earning-values">
                  <div><small>Commission earned</small><strong className="amount-credit">{money(period?.commission)}</strong><em>{period?.successfulRechargeCount || 0} successful recharges</em></div>
                  <div><small>Recharge volume</small><strong>{money(period?.successfulRechargeAmount)}</strong></div>
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="portal-panel wallet-withdraw-history-section">
          <div className="wallet-parity-section-head">
            <div><span>UPI PAYOUTS</span><h3>Withdrawal history</h3><p>UPI payout requests and their current status.</p></div>
            <Send size={19} className="wallet-muted-icon"/>
          </div>
          {withdrawalLoading && withdrawals.length===0 ? (
            <div className="wallet-loading"><span className="wallet-spinner"/><span>Loading withdrawals…</span></div>
          ) : withdrawals.length===0 ? (
            <div className="wallet-empty-state"><Send size={24}/><b>No withdrawal requests yet.</b><span>Your UPI withdrawal requests will appear here.</span></div>
          ) : (
            <div className="wallet-transaction-list">
              {withdrawals.map(item => {
                const status=String(item.status||'UNKNOWN').toUpperCase();
                return <div className="wallet-transaction-row" key={item.withdrawalId}>
                  <div className="wallet-transaction-leading withdraw"><Send size={16}/></div>
                  <div className="wallet-transaction-copy">
                    <b>₹{Number(item.amount||0).toLocaleString('en-IN',{minimumFractionDigits:2})} → {item.upiId}</b>
                    <span>{String(item.provider||'').toUpperCase()} · {dt(item.createdAt)}</span>
                    <small>{item.withdrawalId}</small>
                    {item.failureReason && <small className="wallet-error-text">{item.failureReason}</small>}
                  </div>
                  <span className={'status-pill status-' + status.toLowerCase().replace(/[^a-z0-9]+/g,'-')}>{status}</span>
                </div>;
              })}
            </div>
          )}
          {withdrawalLoading && withdrawals.length>0 && <div className="wallet-inline-loading"><span className="wallet-spinner"/></div>}
          <WebHistoryPagination
            page={withdrawalPage}
            totalItems={withdrawalTotalItems}
            totalPages={withdrawalTotalPages}
            pageSize={walletHistoryPageSize}
            onPageSizeChange={changeWalletPageSize}
            onPrevious={()=>void loadWithdrawals(Math.max(0, withdrawalPage-1))}
            onNext={()=>void loadWithdrawals(withdrawalPage+1)}
          />
        </section>

        <section className="portal-panel wallet-history-parity-section">
          <div className="wallet-parity-section-head">
            <div><span>WALLET ACTIVITY</span><h3>Wallet history</h3><p>{date(walletHistoryFrom)} → {date(walletHistoryTo)}</p></div>
            <button className="wallet-icon-action" onClick={()=>{void refreshWallet();void loadWalletHistory(walletHistoryPage);}} disabled={walletHistoryLoading}><RefreshCw size={16}/></button>
          </div>

          <div className="wallet-filter-chips">
            {[
              {key:'',label:'All'},
              {key:'RECHARGE',label:'Recharge'},
              {key:'ADD_MONEY',label:'Add money'},
              {key:'WITHDRAWN',label:'Withdrawn'},
              {key:'RENTAL',label:'Rental'}
            ].map(filter => (
              <button key={filter.key} className={walletHistoryKind===filter.key?'selected':''} onClick={()=>selectWalletHistoryFilter(filter.key)}>
                {filter.label}
              </button>
            ))}
          </div>

          <div className="wallet-date-segmented">
            {[
              ['TODAY','Today'],
              ['LAST_7_DAYS','7 days'],
              ['THIS_MONTH','Month'],
              ['CUSTOM','Custom']
            ].map(([key,label]: any) => (
              <button key={key} className={walletDateFilter===key?'selected':''} onClick={()=>{
                if(key==='CUSTOM') { setWalletDateFilter('CUSTOM'); return; }
                selectWalletDatePreset(key);
              }}>
                {label}
              </button>
            ))}
          </div>

          {walletDateFilter==='CUSTOM' && <div className="wallet-custom-range">
            <label>From<input type="date" max={localDate()} value={walletHistoryFrom} onChange={e=>setWalletHistoryFrom(e.target.value)}/></label>
            <span>→</span>
            <label>To<input type="date" max={localDate()} value={walletHistoryTo} onChange={e=>setWalletHistoryTo(e.target.value)}/></label>
            <button className="wallet-outline-btn small" onClick={applyWalletCustomRange}>Apply</button>
          </div>}

          {walletHistoryLoading && walletHistory.length===0 ? (
            <div className="wallet-loading"><span className="wallet-spinner"/><span>Loading wallet activity…</span></div>
          ) : walletHistory.length===0 ? (
            <div className="wallet-empty-state"><WalletCards size={24}/><b>No wallet activity</b><span>There are no wallet transactions for the selected filters.</span></div>
          ) : (
            <div className="wallet-transaction-list">
              {walletHistory.map((item,i)=>{
                const signed=walletSigned(item);
                const isRecharge=String(item.referenceType||'').toUpperCase()==='RECHARGE';
                const isRental=String(item.referenceType||'').toUpperCase().includes('RENTAL');
                const isCredit=signed>0;
                const label=String(item.referenceType||'').toUpperCase()==='RENTAL_REFUND' ? 'Car rental refund'
                  : isRental ? 'Car rental payment'
                  : isRecharge ? 'Recharge'
                  : String(item.referenceType||'').toUpperCase()==='ADD_MONEY' ? 'Added money'
                  : ['WITHDRAWAL','WITHDRAWN'].includes(String(item.referenceType||item.type||'').toUpperCase()) ? 'Withdrawn money'
                  : item.description || item.type || 'Wallet transaction';
                return <button className="wallet-transaction-row wallet-history-row" key={String(item.id||i)} onClick={()=>openWalletItem(item)}>
                  <div className={'wallet-transaction-leading ' + (isCredit?'credit':'debit')}>{isCredit ? <Plus size={16}/> : <Send size={16}/>}</div>
                  <div className="wallet-transaction-copy">
                    <b>{label}</b>
                    {isRental && <span>{item.referenceId || 'Booking reference'}</span>}
                    {isRecharge && <span>{(item.operator || 'Operator') + ' · ' + (item.referenceId || 'Recharge reference')}</span>}
                    <small>{dt(item.createdAt)}{item.provider ? ' · ' + String(item.provider).toUpperCase() : ''}</small>
                  </div>
                  <strong className={signed>0?'amount-credit':'amount-debit'}>{signed>0?'+':'-'}{money(Math.abs(Number(item.amount||0)))}</strong>
                </button>;
              })}
            </div>
          )}

          {walletHistoryLoading && walletHistory.length>0 && <div className="wallet-inline-loading"><span className="wallet-spinner"/></div>}
          <WebHistoryPagination
            page={walletHistoryPage}
            totalItems={walletHistoryTotalItems}
            totalPages={walletHistoryTotalPages}
            pageSize={walletHistoryPageSize}
            onPageSizeChange={changeWalletPageSize}
            onPrevious={()=>void loadWalletHistory(Math.max(0,walletHistoryPage-1))}
            onNext={()=>void loadWalletHistory(walletHistoryPage+1)}
          />
        </section>
      </section>}

      {view==='history' && <section className="portal-content">
        <div className="portal-panel"><div className="panel-head"><div><h2>Recharge history</h2><p>Track submitted, pending, successful and failed recharges.</p></div><button className="landing-secondary" onClick={loadHistory}><RefreshCw size={15}/> Refresh</button></div>
          <div className="history-date-filters"><label>From<input type="date" max={localDate()} value={historyFrom} onChange={e=>setHistoryFrom(e.target.value)}/></label><label>To<input type="date" max={localDate()} min={historyFrom || undefined} value={historyTo} onChange={e=>setHistoryTo(e.target.value)}/></label><button className="landing-secondary" onClick={()=>{setHistoryFrom(localDate());setHistoryTo(localDate());setHistoryKind('');}}>Clear filters</button></div>
          {recharges.length ? <div className="history-list">{recharges.map((x,i)=><div className="history-row" key={String(x.transactionId || i)}>
            <div><ReceiptText size={18}/><b>{x.mobileNumber || 'Recharge'} · {x.operator || '—'}</b><small>{x.planDescription || 'Plan'} · {x.transactionId || 'No reference'} · {dt(x.createdAt)}{x.provider ? ' · '+x.provider : ''}</small></div>
            <strong className="amount-debit">{money(x.amount)}</strong>
            <div className="history-actions"><span className={statusClass(x.status)}>{String(x.status || 'UNKNOWN').toUpperCase()}</span>{x.transactionId && <button className="copy-btn" onClick={()=>openWalletItem({id:x.transactionId,type:'RECHARGE',amount:x.amount,status:x.status,referenceId:x.transactionId,referenceType:'RECHARGE',provider:x.provider,createdAt:x.createdAt})}><Eye size={14}/><span>Details</span></button>}{x.transactionId && <button className="copy-btn" onClick={()=>copyText(x.transactionId || '','Transaction reference copied.')}><Copy size={14}/><span>Copy</span></button>}</div>
          </div>)}</div> : <div className="empty-state"><History size={22}/><b>No recharge history yet</b><span>Your completed and pending recharges will appear here.</span></div>}
        </div>
      </section>}

      {view==='marketplace' && <section className="portal-content"><div className="portal-panel"><div className="panel-head"><div><h2>Marketplace</h2><p>Explore mPay service categories.</p></div><Car size={28}/></div>
        <button className="rental-car selected" onClick={()=>setView('rental')}><div className="rental-car-icon"><Car size={26}/></div><b>Car Rental</b><span>NEW · Chauffeur-driven cars</span><strong>Open marketplace</strong></button>
      </div></section>}

      {view==='rental' && <section className="portal-content">
        <div className="portal-panel"><div className="panel-head"><div><h2>Choose a car</h2><p>Filter by city/pickup area and date & time, inspect the vehicle, review the fare and book from your wallet.</p></div><CarFront size={28}/></div>
          <div className="rental-search-card"><div className="rental-search-heading"><div><b>1. Set your trip window</b><span>These dates control availability and are reused for the fare quote and booking.</span></div>{(rentalSearch.startDate || rentalSearch.endDate || rentalSearch.location) && <span className="search-state-chip">Filter ready</span>}</div>
            <div className="rental-search-grid"><label>City or pickup area<input value={rentalSearch.location} placeholder="e.g. Patna, Airport Road" onChange={e=>setRentalSearch({...rentalSearch,location:e.target.value})}/></label>
              <label>From<input type="datetime-local" value={rentalSearch.startDate} min={isoNow()} onChange={e=>setRentalSearch({...rentalSearch,startDate:e.target.value})}/></label>
              <label>To<input type="datetime-local" value={rentalSearch.endDate} min={rentalSearch.startDate || isoNow()} onChange={e=>setRentalSearch({...rentalSearch,endDate:e.target.value})}/></label>
            </div>
            <div className="rental-search-actions"><button className="landing-secondary" onClick={clearRentalSearch}>Clear</button><button className="landing-primary" onClick={searchRentalCars}>Find cars <ArrowRight size={16}/></button></div>
            {selectedCar && <div className="rental-trip-details"><div className="rental-trip-context"><span>2. Complete booking details</span><b>{selectedCar.name}</b><small>{rentalSearch.startDate && rentalSearch.endDate ? dt(rentalSearch.startDate)+' → '+dt(rentalSearch.endDate) : 'Choose From and To above before checking fare.'}</small></div><label>Pickup location<input placeholder="Pickup location" value={rentalForm.pickup} onChange={e=>setRentalForm({...rentalForm,pickup:e.target.value})}/></label><label>Drop location <em>(optional)</em><input placeholder="Drop location" value={rentalForm.drop} onChange={e=>setRentalForm({...rentalForm,drop:e.target.value})}/></label></div>}
          </div>
          {cars.length ? <div className="rental-car-grid">{cars.map(car=><div className={'rental-car '+(selectedCar?.id===car.id?'selected':'')} key={car.id}>
            <button className="rental-car-main" onClick={()=>{setSelectedCar(car);setRentalQuote(undefined);}}><div className="rental-car-icon">{imageFromCar(car)?<img src={imageFromCar(car)} alt={car.name}/>:<Car size={26}/>}</div><b>{car.name}</b><span>{car.category} · {car.seats} seats · {car.transmission}</span><strong>{money(car.pricePerDay)} / day</strong></button><button className="copy-btn" onClick={()=>setRentalDetails(car)}><Eye size={14}/><span>Details</span></button>
          </div>)}</div> : <div className="rental-empty-state"><div className="rental-empty-icon"><Car size={28}/></div><b>{rentalSearch.location||rentalSearch.startDate||rentalSearch.endDate?'No cars match this search':'No cars available right now'}</b><span>{rentalSearch.location||rentalSearch.startDate||rentalSearch.endDate?'Try a different city, pickup area or rental window.':'There are no approved chauffeur-driven cars available for your account at the moment.'}</span><button className="landing-secondary" onClick={refreshRentalData}><RefreshCw size={15}/> Check again</button></div>}
          {selectedCar && <div className="rental-summary"><div><span>Selected</span><b>{selectedCar.name}</b></div><div><span>Billing</span><b>{rentalQuote ? rentalQuote.days+' day'+(rentalQuote.days>1?'s':''):'Check fare'}</b></div><div><span>Total</span><strong>{rentalQuote ? money(rentalQuote.total):'—'}</strong></div>
            {!rentalQuote?<button className="landing-primary" disabled={busy} onClick={checkRentalFare}>{busy?'Calculating…':'Check fare'} <ArrowRight size={16}/></button>:<button className="landing-primary" disabled={busy || Number(wallet?.availableBalance || 0)<Number(rentalQuote.total || 0)} onClick={bookCar}>{busy?'Confirming…':'Confirm booking'} <ArrowRight size={16}/></button>}
            <p className="rental-pricing-note">Price is per 24-hour day. Any partial day is charged as one full day; time is used for duration and availability. Payment is from your wallet.</p>
          </div>}
        </div>
      </section>}

      {view==='bookings' && <section className="portal-content"><div className="portal-panel"><div className="panel-head"><div><h2>My Bookings</h2><p>Booked cars, chauffeur details, trip timing, wallet payment, status and cancellation.</p></div><button className="landing-secondary" onClick={refreshBookings}><RefreshCw size={15}/> Refresh</button></div>
        <div className="funding-picker">{bookingStatuses.map(s=><button key={s} className={bookingStatusFilter===s?'selected':''} onClick={()=>setBookingStatusFilter(s)}>{s==='ALL'?'All':s.replace(/_/g,' ')}</button>)}</div>
        {filteredBookings.length ? <div className="history-list">{filteredBookings.map(b=><div className="history-row" key={b.bookingId}><div><Car size={18}/><b>{b.carName}</b><small>{b.bookingId} · {b.pickup} → {b.drop} · {dt(b.startDate)} to {dt(b.endDate)} · Driver {b.driverName || '—'}</small></div><strong className={['CANCELLED','REFUNDED'].includes(String(b.status || '').toUpperCase()) ? 'amount-credit' : 'amount-debit'}>{money(b.total)}</strong><div className="history-actions"><span className={statusClass(b.status)}>{String(b.status).toUpperCase()}</span><span>{b.paymentMethod || 'WALLET'}</span><button className="copy-btn" onClick={()=>copyText(bookingShareText(b),'Booking details copied.')}><Copy size={14}/><span>Copy</span></button>{String(b.status).toUpperCase()==='CONFIRMED' && new Date(b.startDate).getTime()>Date.now() && <button className="text-danger-btn" disabled={busy} onClick={()=>cancelBooking(b.bookingId)}>Cancel</button>}</div></div>)}</div> : <div className="empty-state">No bookings match the selected status.</div>}
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

        <div className="portal-panel account-security-panel">
          <div className="panel-head">
            <div><h2>Account & privacy</h2><p>Manage your account lifecycle and account deletion.</p></div>
            <ShieldCheck size={22}/>
          </div>
          <div className="account-security-links">
            <a className="landing-secondary" href="/privacy-policy" target="_blank" rel="noreferrer">Privacy policy <ArrowRight size={14}/></a>
            <a className="landing-secondary" href="/delete-account" target="_blank" rel="noreferrer">Account deletion information <ArrowRight size={14}/></a>
          </div>
          <div className="danger-panel">
            <div><b>Delete your mPay account</b><span>This permanently disables the account and removes or redacts personal data. Financial records required for reconciliation are retained in redacted form.</span></div>
            <div className="danger-panel-form">
              <label>Password<input type="password" value={deletePassword} onChange={e=>setDeletePassword(e.target.value)} placeholder="Enter your password"/></label>
              <label>Type DELETE to confirm<input value={deleteConfirmation} onChange={e=>setDeleteConfirmation(e.target.value)} placeholder="DELETE" autoCapitalize="characters"/></label>
              <button className="text-danger-btn" disabled={deletingAccount || !deletePassword.trim() || deleteConfirmation.trim().toUpperCase()!=='DELETE'} onClick={deleteAccount}>{deletingAccount?'Deleting account…':'Delete account'}</button>
            </div>
          </div>
        </div>

        <div className="portal-panel vendor-hub">
          <div className="vendor-hub-hero">
            <div className="vendor-hub-icon"><CarFront size={25}/></div>
            <div className="vendor-hub-title"><span>mPAY MOBILITY PARTNER</span><h2>Vendor Studio</h2><p>Your chauffeur-driven fleet, availability, payouts and marketplace identity in one workspace.</p></div>
            {vendor?.vendorId ? <span className={statusClass(vendor.status) + ' vendor-hub-status'}>{vendorStatus}</span> : <span className="vendor-hub-status vendor-status-neutral">NOT ONBOARDED</span>}
          </div>

          {vendor && !vendor.vendorId && <div className="vendor-cta"><div><b>Become a rental partner</b><span>Submit your profile for admin review. Your vendor workspace unlocks after verification.</span></div><button className="landing-primary" onClick={()=>{resetVendorForm(vendor);setShowVendorForm(true);}}><Plus size={16}/> Become a Vendor</button></div>}

          {vendor?.vendorId && <>
            <div className="vendor-summary-grid">
              <div><span>Partner status</span><b className={statusClass(vendor.status)}>{vendorStatus}</b></div>
              <div><span>Fleet</span><b>{vendor.vehicleCount ?? vendorVehicles.length}</b></div>
              <div><span>Base location</span><b>{vendor.city || '—'}</b></div>
              <div><span>Primary payout</span><b>{vendor.payoutPrimaryMethod || '—'}</b></div>
              {vendor.rejectionReason && <div className="vendor-rejection"><span>Admin review note</span><b>{vendor.rejectionReason}</b></div>}
            </div>
            {vendorVerified && vendorEarnings && <div className="vendor-earnings-grid">
              <div><span>Today · net</span><b className="amount-credit">{money(vendorEarnings.today.vendorNetAmount)}</b><small>{vendorEarnings.today.completedBookingCount} completed</small></div>
              <div><span>Today · bookings</span><b>{vendorEarnings.today.bookingCount}</b><small>confirmed / completed</small></div>
              <div><span>This month · net</span><b className="amount-credit">{money(vendorEarnings.monthly.vendorNetAmount)}</b><small>{vendorEarnings.monthly.completedBookingCount} completed</small></div>
              <div><span>Upcoming bookings</span><b>{vendorEarnings.upcomingBookingCount}</b><small>currently confirmed</small></div>
            </div>}
            <div className="vendor-actions">
              <button className="landing-secondary" onClick={()=>resetVendorForm(vendor)}><Edit3 size={15}/> {vendorStatus==='REJECTED'?'Resubmit profile':'Edit profile'}</button>
              <button className="landing-secondary" onClick={loadAccountData}><RefreshCw size={15}/> Refresh workspace</button>
            </div>
          </>}

          {showVendorForm && <div className="vendor-form">
            <div className="vendor-section-head"><div><b>{vendor?.vendorId ? 'Edit vendor profile' : 'Vendor application'}</b><span className="vendor-form-subtitle">{vendor?.vendorId ? 'Update identity and payout preferences without leaving the workspace.' : 'Tell us about the business and preferred payout method.'}</span></div><button className="icon-btn" onClick={()=>setShowVendorForm(false)}><X size={16}/></button></div>
            <div className="vendor-form-grid">
              <label>Vendor type<select value={vendorForm.vendorType} onChange={e=>setVendorForm({...vendorForm,vendorType:e.target.value})}><option value="INDIVIDUAL">Individual</option><option value="BUSINESS">Business</option></select></label>
              <label>Full name<input value={vendorForm.fullName} onChange={e=>setVendorForm({...vendorForm,fullName:e.target.value})}/></label>
              <label>Business / fleet name<input value={vendorForm.businessName} onChange={e=>setVendorForm({...vendorForm,businessName:e.target.value})}/></label>
              <label className="vendor-field-wide">Address<input value={vendorForm.address} onChange={e=>setVendorForm({...vendorForm,address:e.target.value})}/></label>
              <label>City<input value={vendorForm.city} onChange={e=>setVendorForm({...vendorForm,city:e.target.value})}/></label>
              <label>State<input value={vendorForm.state} onChange={e=>setVendorForm({...vendorForm,state:e.target.value})}/></label>
              <label>PIN code<input value={vendorForm.pinCode} onChange={e=>setVendorForm({...vendorForm,pinCode:e.target.value})}/></label>
              <label>PAN<input value={vendorForm.panNumber} onChange={e=>setVendorForm({...vendorForm,panNumber:e.target.value})}/></label>
              <label>UPI ID<input value={vendorForm.payoutUpiId} onChange={e=>setVendorForm({...vendorForm,payoutUpiId:e.target.value})}/></label>
              <label>Bank name<input value={vendorForm.bankName} onChange={e=>setVendorForm({...vendorForm,bankName:e.target.value})}/></label>
              <label>Bank account<input value={vendorForm.bankAccountNumber} onChange={e=>setVendorForm({...vendorForm,bankAccountNumber:e.target.value})}/></label>
              <label>IFSC<input value={vendorForm.bankIfsc} onChange={e=>setVendorForm({...vendorForm,bankIfsc:e.target.value})}/></label>
              <label>Primary payout<select value={vendorForm.payoutPrimaryMethod} onChange={e=>setVendorForm({...vendorForm,payoutPrimaryMethod:e.target.value})}><option value="">Auto select</option><option value="UPI">UPI</option><option value="BANK">Bank</option></select></label>
            </div>
            <div className="form-actions"><button className="landing-secondary" onClick={()=>setShowVendorForm(false)}>Cancel</button><button className="landing-primary" disabled={busy} onClick={saveVendor}>{vendor?.vendorId ? 'Save profile' : 'Submit for review'}</button></div>
          </div>}

          {vendor?.vendorId && <div className="vendor-dashboard">
            <div className="vendor-section-head"><b>Fleet</b><button className="landing-secondary" onClick={()=>{setSelectedVendorVehicle(undefined);resetVehicleForm();}}><Plus size={14}/> Add vehicle</button></div>
            {vendorVehicles.length ? <div className="vendor-vehicle-grid">{vendorVehicles.map(car=><div className="vendor-vehicle-card" key={car.id}>
              <div className="vendor-vehicle-image">{imageFromCar(car)?<img src={imageFromCar(car)} alt={car.name}/>:<Car size={26}/>}</div>
              <div className="vendor-vehicle-main"><b>{car.name}</b><span>{car.make || ''} {car.model || ''} · {car.category} · {car.seats} seats</span><small>{money(car.pricePerDay)} / day · Driver {car.driverName || '—'}</small><div className="vendor-card-status"><span className={statusClass(car.approvalStatus)}>{String(car.approvalStatus || 'PENDING').toUpperCase()}</span></div></div>
              <div className="vendor-card-actions"><button className="icon-btn" title="Details" onClick={()=>setSelectedVendorVehicle(car)}><Eye size={16}/></button>{String(car.approvalStatus || '').toUpperCase()==='REJECTED' && <button className="icon-btn" title="Edit/resubmit" onClick={()=>resetVehicleForm(car)}><Edit3 size={16}/></button>}</div>
            </div>)}</div> : <div className="empty-state">No vehicles submitted yet.</div>}

            {selectedVendorVehicle && <div className="vehicle-detail-panel">
              <div className="panel-head"><div><h3>{selectedVendorVehicle.name}</h3><p>{selectedVendorVehicle.make || '—'} {selectedVendorVehicle.model || ''} · Driver {selectedVendorVehicle.driverName || '—'}</p></div><button className="icon-btn" onClick={()=>setSelectedVendorVehicle(undefined)}><X size={17}/></button></div>
              <div className="driver-profile-card"><div className="driver-profile-photo">{selectedVendorVehicle.driverPhotoUrl ? <img src={(selectedVendorVehicle.driverPhotoUrl.startsWith('http') ? selectedVendorVehicle.driverPhotoUrl : base + selectedVendorVehicle.driverPhotoUrl)} alt="Driver"/> : <UserRound size={22}/>}</div><div><b>{selectedVendorVehicle.driverName || 'Driver'}</b><span>{selectedVendorVehicle.driverMobile || 'Mobile not provided'}</span><small>{selectedVendorVehicle.driverLicenseNumber || 'Licence not provided'}</small></div><label className="landing-secondary upload-label"><Camera size={14}/> Driver photo<input type="file" accept="image/*" hidden onChange={e=>e.target.files?.[0] && selectedVendorVehicle.driverId && uploadDriverPhoto(selectedVendorVehicle.driverId,e.target.files[0])}/></label></div><div className="vehicle-gallery">{[0,1,2,3].map(slot=>{const src=imageFromCar(selectedVendorVehicle,slot);return <div className="vehicle-gallery-slot" key={slot}>{src?<img src={src} alt={'Vehicle '+(slot+1)}/>:<span>Photo {slot+1}</span>}<label className="upload-photo-btn"><Camera size={14}/> Upload<input type="file" accept="image/*" hidden onChange={e=>e.target.files?.[0] && uploadVehicleSlot(selectedVendorVehicle.id,slot,e.target.files[0])}/></label></div>;})}</div>
              <div className="detail-grid-web"><span>Approval <b>{selectedVendorVehicle.approvalStatus || '—'}</b></span><span>Registration <b>{selectedVendorVehicle.registrationNumber || '—'}</b></span><span>Fuel <b>{selectedVendorVehicle.fuelType || '—'}</b></span><span>Price/day <b>{money(selectedVendorVehicle.pricePerDay)}</b></span><span>Pickup <b>{selectedVendorVehicle.pickupAddress || '—'}</b></span><span>City/State <b>{selectedVendorVehicle.city || '—'} / {selectedVendorVehicle.state || '—'}</b></span><span>Driver licence <b>{selectedVendorVehicle.driverLicenseNumber || '—'}</b></span><span>Licence expiry <b>{date(selectedVendorVehicle.driverLicenseExpiry)}</b></span></div>
              {selectedVendorVehicle.rejectionReason && <div className="vendor-rejection">{selectedVendorVehicle.rejectionReason}</div>}
              <div className="unavailability-box"><div className="vendor-section-head"><div><b>Availability controls</b><span className="vendor-form-subtitle">{String(selectedVendorVehicle.approvalStatus || '').toUpperCase()==='APPROVED' ? 'Temporarily remove this approved vehicle from customer search.' : 'Available after the vehicle is approved.'}</span></div><CalendarDays size={18}/></div>
                <div className="availability-form"><select disabled={String(selectedVendorVehicle.approvalStatus || '').toUpperCase()!=='APPROVED'} value={unavailabilityForm.reasonCode} onChange={e=>setUnavailabilityForm({...unavailabilityForm,reasonCode:e.target.value})}><option value="SERVICE_MAINTENANCE">Service / maintenance</option><option value="PRIVATE_USE">Private use</option><option value="DRIVER_UNAVAILABLE">Driver unavailable</option><option value="LEGAL_DOCUMENTATION">Documentation / compliance</option><option value="PERSONAL_REASON">Personal reason</option><option value="OTHER">Other</option></select><input disabled={String(selectedVendorVehicle.approvalStatus || '').toUpperCase()!=='APPROVED'} value={unavailabilityForm.reasonNote} placeholder="Reason note" onChange={e=>setUnavailabilityForm({...unavailabilityForm,reasonNote:e.target.value})}/><input type="date" disabled={String(selectedVendorVehicle.approvalStatus || '').toUpperCase()!=='APPROVED'} value={unavailabilityForm.startDate} onChange={e=>setUnavailabilityForm({...unavailabilityForm,startDate:e.target.value})}/><input type="date" disabled={String(selectedVendorVehicle.approvalStatus || '').toUpperCase()!=='APPROVED'} value={unavailabilityForm.endDate} onChange={e=>setUnavailabilityForm({...unavailabilityForm,endDate:e.target.value})}/><button className="landing-secondary" disabled={busy || String(selectedVendorVehicle.approvalStatus || '').toUpperCase()!=='APPROVED'} onClick={()=>takeVehicleOffMarket(selectedVendorVehicle.id)}>Take off market</button></div>
                {vehicleUnavailability.length ? <div className="history-list compact-list">{vehicleUnavailability.map(u=><div className="history-row" key={u.id}><div><b>{u.reasonLabel}</b><small>{u.startDate} → {u.endDate}{u.reasonNote?' · '+u.reasonNote:''}</small></div><div className="history-actions"><span className={statusClass(u.status)}>{String(u.status).toUpperCase()}</span>{String(u.status).toUpperCase()==='ACTIVE' && <button className="text-danger-btn" onClick={()=>restoreOffMarket(selectedVendorVehicle.id,u.id)}>Restore</button>}</div></div>)}</div> : <div className="empty-state">No active off-market periods.</div>}
              </div>
              <div className="calendar-box"><div className="vendor-section-head"><b>Vehicle calendar</b><div className="calendar-nav"><button className="icon-btn" onClick={()=>{const [y,m]=calendarMonth.split('-').map(Number);setCalendarMonth(localYearMonth(new Date(y,m-2,1)));}}><ChevronLeft size={15}/></button><b>{calendarMonth}</b><button className="icon-btn" onClick={()=>{const [y,m]=calendarMonth.split('-').map(Number);setCalendarMonth(localYearMonth(new Date(y,m,1)));}}><ChevronRight size={15}/></button></div></div><div className="calendar-grid">{vehicleCalendar.map(d=><div className={'calendar-day calendar-'+String(d.status||'UNKNOWN').toLowerCase()} key={d.date}><b>{new Date(d.date).getDate()}</b><span>{d.reasonLabel || d.status || '—'}</span></div>)}</div></div>
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
          {selectedRechargeDetail && <div className="recharge-detail-box"><h3>Recharge details</h3><div className="detail-grid-web"><span>Transaction <b>{selectedRechargeDetail.transactionId || '—'}</b></span><span>Plan <b>{selectedRechargeDetail.planDescription || selectedRechargeDetail.planId || '—'}</b></span><span>Recharge status <b>{selectedRechargeDetail.status || '—'}</b></span><span>Provider <b>{selectedRechargeDetail.provider || '—'}</b></span><span>Mobile <b>{selectedRechargeDetail.mobileNumber || '—'}</b></span><span>Operator / circle <b>{(selectedRechargeDetail.operator || '—') + ' / ' + (selectedRechargeDetail.circle || '—')}</b></span><span>Wallet debit <b>{money(selectedRechargeDetail.walletDebitAmount)}</b></span><span>Provider reference <b>{selectedRechargeDetail.providerReference || '—'}</b></span><span>Message <b>{selectedRechargeDetail.message || '—'}</b></span></div></div>}          {selectedWithdrawalDetail && <div className="recharge-detail-box"><h3>Withdrawal details</h3><div className="detail-grid-web"><span>Withdrawal <b>{selectedWithdrawalDetail.withdrawalId || '—'}</b></span><span>Amount <b className="amount-debit">{money(selectedWithdrawalDetail.amount)}</b></span><span>UPI ID <b>{selectedWithdrawalDetail.upiId || '—'}</b></span><span>Status <b>{selectedWithdrawalDetail.status || '—'}</b></span><span>Provider <b>{selectedWithdrawalDetail.provider || '—'}</b></span><span>Provider status <b>{selectedWithdrawalDetail.providerStatus || '—'}</b></span><span>Provider reference <b>{selectedWithdrawalDetail.providerReference || '—'}</b></span><span>Wallet ledger <b>{selectedWithdrawalDetail.walletLedgerRef || '—'}</b></span><span>Failure reason <b>{selectedWithdrawalDetail.failureReason || '—'}</b></span><span>Created <b>{dt(selectedWithdrawalDetail.createdAt)}</b></span><span>Completed <b>{dt(selectedWithdrawalDetail.completedAt)}</b></span></div></div>}</div></div>}

      {rentalDetails && <div className="modal-backdrop" onClick={()=>setRentalDetails(undefined)}><div className="portal-modal" onClick={e=>e.stopPropagation()}><div className="panel-head"><div><h2>{rentalDetails.name}</h2><p>{rentalDetails.category} · {rentalDetails.seats} seats · {rentalDetails.transmission}</p></div><button className="icon-btn" onClick={()=>setRentalDetails(undefined)}><X size={17}/></button></div>
        <div className="vehicle-gallery">{[0,1,2,3].map(slot=>{const src=imageFromCar(rentalDetails,slot);return <div className="vehicle-gallery-slot" key={slot}>{src?<img src={src} alt={'Vehicle '+(slot+1)}/>:<span>Photo {slot+1}</span>}</div>;})}</div>
        <div className="driver-profile-card public-driver-card"><div className="driver-profile-photo">{rentalDetails.driverPhotoUrl ? <img src={(rentalDetails.driverPhotoUrl.startsWith('http') ? rentalDetails.driverPhotoUrl : base + rentalDetails.driverPhotoUrl)} alt="Chauffeur"/> : <UserRound size={22}/>}</div><div><b>{rentalDetails.driverName||'Chauffeur'}</b><span>Professional driver assigned for this vehicle</span></div></div><div className="detail-grid-web"><span>Make / model <b>{[rentalDetails.make,rentalDetails.model,rentalDetails.variant].filter(Boolean).join(' ')||'—'}</b></span><span>Fuel <b>{rentalDetails.fuelType||'—'}</b></span><span>Manufacturing year <b>{rentalDetails.manufacturingYear||'—'}</b></span><span>Pickup <b>{rentalDetails.pickupAddress||'—'}</b></span><span>City / State <b>{rentalDetails.city||'—'} / {rentalDetails.state||'—'}</b></span><span>Daily rate <b>{money(rentalDetails.pricePerDay)}</b></span></div>
      </div></div>}
    </main>
  </div>;
}
