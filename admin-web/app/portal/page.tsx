'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { useWebCapabilities } from '../../lib/webCapabilities';
import {
  ArrowRight, Banknote, CalendarDays, Camera, Car, CarFront, Check, CheckCircle2, ChevronLeft, LockKeyhole, Landmark, MapPin,
  ChevronRight, CircleDollarSign, Clock3, Copy, Edit3, Eye, FileText, History, Home, LogOut, Menu,
  Plus, ReceiptText, RefreshCw, Save, Send, Settings, ShieldCheck, Smartphone, Sparkles, Trash2, Upload, UserRound, WalletCards, X
} from 'lucide-react';

const base = process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, '') || 'http://localhost:8080';

type Wallet = { balance: number; availableBalance: number; reservedBalance: number };
type Me = {
  userId?: number; publicUserId: string; mobile: string; name?: string; email?: string; profileImageUrl?: string | null;
  profileImageVersion?: number | null; role: string; commissionRate?: number; createdAt?: string; profileUpdatedAt?: string | null;
};
type RechargeItem = {
  transactionId?: string; clientRequestId?: string; mobileNumber?: string; recipientName?: string | null; operator?: string; circle?: string;
  planId?: string; amount?: number; walletDebitAmount?: number; status?: string; createdAt?: string; updatedAt?: string; completedAt?: string | null;
  planDescription?: string | null; planValidity?: string | null; provider?: string; providerReference?: string | null;
  providerOrderId?: string | null; walletLedgerRef?: string | null; message?: string | null; clientCommission?: number;
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
  bookingId: string; carName: string; driverName?: string; driverMobile?: string; driverPhotoUrl?: string; carImageUrl?: string;
  pickup: string; drop: string; startDate: string; endDate: string; total: number; paymentMethod?: string; status: string; createdAt?: string;
  pickupLatitude?: number | null; pickupLongitude?: number | null; pickupPlaceId?: string | null;
  dropLatitude?: number | null; dropLongitude?: number | null; dropPlaceId?: string | null;
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

const indianStatesAndUt = [
  'Andhra Pradesh','Arunachal Pradesh','Assam','Bihar','Chhattisgarh','Goa','Gujarat','Haryana','Himachal Pradesh',
  'Himachal Pradesh','Jharkhand','Karnataka','Kerala','Madhya Pradesh','Maharashtra','Manipur','Meghalaya','Mizoram',
  'Nagaland','Odisha','Punjab','Rajasthan','Sikkim','Tamil Nadu','Telangana','Tripura','Uttar Pradesh','Uttarakhand',
  'West Bengal','Andaman and Nicobar Islands','Chandigarh','Dadra and Nagar Haveli and Daman and Diu','Delhi',
  'Jammu and Kashmir','Ladakh','Lakshadweep','Puducherry'
].filter((v,i,a)=>a.indexOf(v)===i);

const sanitizeVehicleAlphaNumeric = (value:string,maxLength:number) =>
  value.replace(/[^A-Za-z0-9 ]/g,'').slice(0,maxLength);
const sanitizeVehicleText = (value:string,maxLength:number=120) =>
  value.replace(/[^A-Za-z0-9 .&'()\-]/g,'').slice(0,maxLength);
const sanitizeRegistration = (value:string) =>
  value.toUpperCase().replace(/[^A-Z0-9 -]/g,'').slice(0,32);
const sanitizeLicense = (value:string) =>
  value.toUpperCase().replace(/[^A-Z0-9 -]/g,'').slice(0,64);
const sanitizeDecimal = (value:string) => {
  const cleaned=value.replace(/[^0-9.]/g,'');
  const dot=cleaned.indexOf('.');
  return dot<0 ? cleaned.slice(0,9) : cleaned.slice(0,dot+1)+cleaned.slice(dot+1).replace(/\D/g,'').slice(0,2);
};
const normalizeIndianMobile = (value:string) => value.replace(/\D/g,'').slice(0,10);
const rentalOffMarketReasons = [
  ['SERVICE_MAINTENANCE','Service / maintenance'],
  ['PRIVATE_USE','Private use'],
  ['DRIVER_UNAVAILABLE','Driver unavailable'],
  ['LEGAL_DOCUMENTATION','Documentation / compliance'],
  ['PERSONAL_REASON','Personal reason'],
  ['OTHER','Other']
] as const;

function statusClass(value?: string) {
  return 'status-pill status-' + String(value || 'UNKNOWN').toLowerCase().replace(/[^a-z0-9]+/g, '-');
}

function imageFromCar(car?: Pick<RentalCar, 'imageUrl'>, slot = 0, variant: 'thumb' | 'large' = 'thumb') {
  const raw = String(car?.imageUrl || '');
  const values = raw.replace(/\\n/g, '|').split(/[|,]/).map(x => x.trim()).filter(Boolean);
  const value = values[slot] || '';
  if (!value) return '';
  if (/^https?:\/\//i.test(value)) return value;
  const url = value.startsWith('/api/')
    ? base + value
    : base + '/api/v1/car-rental/photos/' + value.replace(/^\/+/, '');
  return url + (url.includes('?') ? '&' : '?') + 'variant=' + variant;
}

function vehiclePhotoSlots(car?: Pick<RentalCar, 'imageUrl'>): string[] {
  const raw = String(car?.imageUrl || '');
  return raw.replace(/\\n/g, '|').split(/[|,]/).map(x => x.trim()).filter(Boolean).slice(0, 4).concat(['', '', '', '']).slice(0, 4);
}

function VehicleFourPhotoGallery({
  car,
  priority = false,
  className = '',
}: {
  car?: Pick<RentalCar, 'imageUrl' | 'name'>;
  priority?: boolean;
  className?: string;
}) {
  const photos = vehiclePhotoSlots(car);
  const main = imageFromCar(car, 0, 'thumb');
  return (
    <div className={`vehicle-card-gallery ${className}`}>
      <div className="vehicle-card-gallery-main">
        {main ? (
          <img
            src={main}
            alt={car?.name || 'Vehicle'}
            loading={priority ? 'eager' : 'lazy'}
            decoding="async"
            fetchPriority={priority ? 'high' : 'auto'}
          />
        ) : <Car size={30} />}
      </div>
      <div className="vehicle-card-gallery-thumbs">
        {[1, 2, 3].map(slot => {
          const src = imageFromCar(car, slot, 'thumb');
          return (
            <div className="vehicle-card-gallery-thumb" key={slot}>
              {src ? (
                <img
                  src={src}
                  alt={`${car?.name || 'Vehicle'} photo ${slot + 1}`}
                  loading={priority ? 'eager' : 'lazy'}
                  decoding="async"
                  fetchPriority="auto"
                />
              ) : <span>Photo {slot + 1}</span>}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function bookingShareText(b: RentalBooking) {
  return [
    'mPay Car Rental Booking',
    'Booking ID: ' + b.bookingId,
    'Car: ' + b.carName,
    'From: ' + b.pickup,
    'To: ' + b.drop,
    'Start: ' + b.startDate,
    'End: ' + b.endDate,
    'Amount: ' + money(b.total),
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
    ...(item.recipientName ? ['Contact name: ' + item.recipientName] : []),
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
            {item.recipientName && <b className="home-recharge-recipient">{item.recipientName}</b>}
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

function RechargeHistoryWebCard({ item, onCopy }: { item: RechargeItem; onCopy: (text:string, message?:string)=>void }) {
  const rawStatus = String(item.status || 'UNKNOWN').toUpperCase();
  const status = rawStatus === 'RESERVED' ? 'PENDING' : rawStatus;
  const copyTextValue = [
    'Recharge history',
    'Amount: ' + money(item.amount),
    'Wallet debit: ' + money(item.walletDebitAmount),
    'Mobile: ' + (item.mobileNumber || '—'),
    ...(item.recipientName ? ['Contact name: ' + item.recipientName] : []),
    'Operator: ' + webOperatorLabel(item.operator),
    'Circle: ' + (item.circle || '—'),
    'Plan ID: ' + (item.planId || '—'),
    ...(item.planDescription ? ['Plan: ' + item.planDescription] : []),
    ...(item.planValidity ? ['Validity: ' + item.planValidity] : []),
    'Transaction ID: ' + (item.transactionId || '—'),
    'Client Request ID: ' + (item.clientRequestId || '—'),
    ...(item.providerReference ? ['Provider reference: ' + item.providerReference] : []),
    ...(item.providerOrderId ? ['Provider order ID: ' + item.providerOrderId] : []),
    ...(item.walletLedgerRef ? ['Wallet ledger reference: ' + item.walletLedgerRef] : []),
    'Provider: ' + (item.provider || '—'),
    'Status: ' + status,
    ...(item.message ? ['Message: ' + item.message] : []),
    'Date & time: ' + dt(item.completedAt || item.createdAt),
    ...(status === 'SUCCESS' ? ['Commission earned: ' + money(item.clientCommission)] : [])
  ].join('\n');
  return (
    <article className="recharge-history-card">
      <div className="recharge-history-card-top">
        <div className="recharge-history-card-main">
          <strong>{money(item.amount)}</strong>
          <span>{webOperatorLabel(item.operator)} · {item.mobileNumber || '—'}</span>
          {item.recipientName && <b className="recharge-history-recipient">{item.recipientName}</b>}
          {item.planDescription && <b>{item.planDescription}</b>}
          {item.planValidity && <small>{item.planValidity}</small>}
        </div>
        <div className="recharge-history-card-actions">
          <span className={statusClass(status)}>{status}</span>
          <button className="copy-btn" onClick={()=>onCopy(copyTextValue,'Recharge details copied.')} title="Copy all recharge data">
            <Copy size={14}/><span>Copy</span>
          </button>
        </div>
      </div>
      <div className="recharge-history-divider"/>
      <div className="recharge-history-card-meta">
        <div>
          <span>{status === 'PENDING' || status === 'PROCESSING' ? 'Reserved' : 'Wallet debit'}</span>
          <b>{money(item.walletDebitAmount)}</b>
        </div>
        <div><span>Transaction ID</span><b>{item.transactionId || '—'}</b></div>
        <div><span>Reference</span><b>{item.clientRequestId || '—'}</b></div>
        {item.providerReference && <div><span>Provider ref</span><b>{item.providerReference}</b></div>}
      </div>
      <div className="recharge-history-card-footer">
        <span><CalendarDays size={14}/>{dt(item.completedAt || item.createdAt)}</span>
        <div>
          {status === 'SUCCESS' && <b className="amount-credit">Commission earned: {money(item.clientCommission)}</b>}
          <span>{item.provider || '—'}</span>
        </div>
      </div>
      {item.message && <p className="recharge-history-message">{item.message}</p>}
    </article>
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

function MpayServiceShowcase({ view }: { view: string }) {
  const active = view === 'wallet' ? 'secure' : view === 'recharge' ? 'simple' : view.startsWith('rental') || view === 'bookings' ? 'smart' : 'ecosystem';

  return (
    <section className="mpay-service-showcase" aria-label="mPay Secure Simple Smart">
      <div className="mpay-story-rail" aria-hidden="true">
        <span className="rail-line rail-line-main" />
        <span className="rail-line rail-line-lower" />
        <span className="rail-pulse rail-pulse-one" />
        <span className="rail-pulse rail-pulse-two" />
        <span className="rail-pulse rail-pulse-three" />
      </div>

      <div className={'mpay-story-word story-secure ' + (active === 'secure' ? 'is-page-active' : '')}>
        <span className="story-word-icon"><LockKeyhole size={14} strokeWidth={2.1}/></span>
        <strong>Secure</strong>
      </div>

      <div className="mpay-finance-flow" aria-hidden="true">
        <div className="flow-icon flow-money"><Banknote size={17}/></div>
        <span className="flow-arrow arrow-money" />
        <div className="flow-icon flow-wallet"><WalletCards size={18}/></div>
        <span className="flow-arrow arrow-wallet" />
        <div className="flow-icon flow-bank"><Landmark size={17}/></div>
        <span className="flow-tag">UPI / BANK</span>
      </div>

      <div className="mpay-brand-mark" aria-hidden="true">
        <strong>mPay</strong>
        <span>Secure · Simple · Smart</span>
      </div>

      <div className={'mpay-story-word story-simple ' + (active === 'simple' ? 'is-page-active' : '')}>
        <span className="story-word-icon"><Sparkles size={14} strokeWidth={2}/></span>
        <strong>Simple</strong>
      </div>

      <div className="mpay-recharge-flow" aria-hidden="true">
        <div className="flow-icon flow-phone"><Smartphone size={17}/></div>
        <span className="flow-arrow arrow-recharge" />
        <div className="flow-icon flow-check"><CheckCircle2 size={17}/></div>
        <span className="flow-tag">RECHARGE</span>
      </div>

      <div className={'mpay-story-word story-smart ' + (active === 'smart' ? 'is-page-active' : '')}>
        <span className="story-word-icon"><Sparkles size={14} strokeWidth={2}/></span>
        <strong>Smart</strong>
      </div>

      <div className="mpay-rental-flow" aria-hidden="true">
        <div className="flow-icon flow-location"><MapPin size={16}/></div>
        <span className="flow-arrow arrow-rental" />
        <div className="flow-icon flow-car"><CarFront size={19}/></div>
        <span className="flow-tag">WITH DRIVER</span>
      </div>

      <div className="mpay-showcase-aura aura-one" />
      <div className="mpay-showcase-aura aura-two" />
      <div className="mpay-showcase-aura aura-three" />
    </section>
  );
}

function WalletBalanceHero({
  wallet,
  loading,
  onRefreshBalance,
  onAddMoney,
  onWithdraw
}: {
  wallet?: Wallet;
  loading: boolean;
  onRefreshBalance: () => void;
  onAddMoney: () => void;
  onWithdraw: () => void;
}) {
  return (
    <section className="wallet-parity-hero">
      <div className="wallet-parity-hero-main">
        <div className="wallet-parity-hero-top">
          <div className="wallet-parity-label"><WalletCards size={17}/><span>AVAILABLE BALANCE</span></div>
          <button
            className="wallet-parity-refresh"
            onClick={onRefreshBalance}
            disabled={loading}
            title="Refresh balance"
          >
            <RefreshCw size={15}/>
          </button>
        </div>
        <strong>{loading ? 'Loading…' : money(wallet?.availableBalance)}</strong>
        <div className="wallet-parity-breakdown">
          <span>Total <b>{money(wallet?.balance)}</b></span>
          <span>Reserved <b>{money(wallet?.reservedBalance)}</b></span>
        </div>
        {Number(wallet?.reservedBalance || 0) > 0 &&
          <p>{money(wallet?.reservedBalance)} reserved in pending transactions.</p>
        }
      </div>
      <div className="wallet-parity-actions">
        <button className="wallet-primary-btn" onClick={onAddMoney}>
          <Plus size={16}/> Add Money
        </button>
        <button className="wallet-outline-btn" onClick={onWithdraw}>
          <Send size={16}/> Withdraw to UPI
        </button>
      </div>
    </section>
  );
}

export default function Portal() {
  const webCapabilities = useWebCapabilities();
  const [view, setView] = useState<'home'|'recharge'|'wallet'|'history'|'marketplace'|'rental'|'rental-booking'|'bookings'|'account'>('home');
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
  const [historyKind, setHistoryKind] = useState('ALL');
  const [historyFilter, setHistoryFilter] = useState<'TODAY'|'LAST_7_DAYS'|'THIS_MONTH'|'CUSTOM'>('TODAY');
  const [historyFrom, setHistoryFrom] = useState(localDate());
  const [historyTo, setHistoryTo] = useState(localDate());
  const [historyPage, setHistoryPage] = useState(0);
  const [historyTotalItems, setHistoryTotalItems] = useState(0);
  const [historyTotalPages, setHistoryTotalPages] = useState(0);
  const [historyPageSize, setHistoryPageSize] = useState(20);
  const [historyRefreshing, setHistoryRefreshing] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
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
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteConfirmation, setDeleteConfirmation] = useState('');
  const [pendingProfileImageFile, setPendingProfileImageFile] = useState<File|null>(null);
  const [pendingProfileImagePreview, setPendingProfileImagePreview] = useState('');
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
  const [rentalBookingCar, setRentalBookingCar] = useState<RentalCar>();
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
  const [calendarCarId, setCalendarCarId] = useState('');
  const [accountSection, setAccountSection] = useState<'profile'|'vendor'|'vehicle'>('profile');
  const [showVendorForm, setShowVendorForm] = useState(false);
  const [showVehicleForm, setShowVehicleForm] = useState(false);
  const [vehicleEditId, setVehicleEditId] = useState('');
  const [vendorSubmitAttempted, setVendorSubmitAttempted] = useState(false);
  const [vehicleSubmitAttempted, setVehicleSubmitAttempted] = useState(false);
  const [vehiclePhotoUrls, setVehiclePhotoUrls] = useState<string[]>(['','','','']);
  const [offMarketOpenId, setOffMarketOpenId] = useState('');
  const [vehiclePhotoFiles, setVehiclePhotoFiles] = useState<(File|null)[]>([null,null,null,null]);
  const [vehiclePhotoPreviews, setVehiclePhotoPreviews] = useState<string[]>(['','','','']);
  const [driverPhotoFile, setDriverPhotoFile] = useState<File|null>(null);
  const [driverPhotoPreview, setDriverPhotoPreview] = useState('');

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

  async function loadHistory(
    page = 0,
    overrides?: { status?: string; from?: string; to?: string; size?: number }
  ) {
    const targetPage = Math.max(0, page);
    const status = overrides?.status ?? historyKind;
    const from = overrides?.from ?? historyFrom;
    const to = overrides?.to ?? historyTo;
    const size = overrides?.size ?? historyPageSize;
    if (from && to && to < from) {
      setNotice('The history end date must be on or after the start date.');
      return;
    }
    setHistoryLoading(targetPage !== 0);
    setHistoryRefreshing(targetPage === 0);
    try {
      const params = new URLSearchParams({
        page: String(targetPage),
        size: String(size),
        from,
        to
      });
      if (status && status !== 'ALL') params.set('status', status);
      const r = await api<any>('/api/v1/recharge/history?' + params.toString());
      const items = r?.items || r?.content || r || [];
      setRecharges(items);
      setHistoryPage(Number(r?.page ?? targetPage));
      setHistoryTotalItems(Number(r?.totalItems ?? items.length));
      setHistoryTotalPages(Number(r?.totalPages ?? (items.length ? 1 : 0)));
      setHistoryKind(status || 'ALL');
      setNotice('');
    } catch (e:any) {
      setNotice(e.message || 'Recharge history could not be loaded.');
    } finally {
      setHistoryLoading(false);
      setHistoryRefreshing(false);
    }
  }

  function applyHistoryRange(filter:'TODAY'|'LAST_7_DAYS'|'THIS_MONTH'|'CUSTOM') {
    const today = new Date();
    const todayValue = localDate(today);
    if (filter === 'TODAY') {
      setHistoryFilter('TODAY');
      setHistoryFrom(todayValue);
      setHistoryTo(todayValue);
      setHistoryPage(0);
      return;
    }
    if (filter === 'LAST_7_DAYS') {
      const fromDate = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 6);
      setHistoryFilter('LAST_7_DAYS');
      setHistoryFrom(localDate(fromDate));
      setHistoryTo(todayValue);
      setHistoryPage(0);
      return;
    }
    if (filter === 'THIS_MONTH') {
      const fromDate = new Date(today.getFullYear(), today.getMonth(), 1);
      setHistoryFilter('THIS_MONTH');
      setHistoryFrom(localDate(fromDate));
      setHistoryTo(todayValue);
      setHistoryPage(0);
      return;
    }
    setHistoryFilter('CUSTOM');
    setHistoryPage(0);
  }

  function changeHistoryStatus(status:string) {
    const normalized = String(status || 'ALL').trim().toUpperCase() || 'ALL';
    setHistoryKind(normalized);
    setHistoryPage(0);
  }

  function changeHistoryPageSize(size:number) {
    const normalized = [10,20,50].includes(size) ? size : 20;
    setHistoryPageSize(normalized);
    setHistoryPage(0);
    void loadHistory(0, { size: normalized });
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
      let p = await api<Me>('/api/v1/profile', {
        method:'PATCH',
        body:JSON.stringify({ name:profileForm.name.trim() || null, email:profileForm.email.trim() || null })
      });
      let photoError = '';
      if (pendingProfileImageFile) {
        try {
          const fd = new FormData();
          fd.append('image', pendingProfileImageFile);
          p = await apiUpload<Me>('/api/v1/profile/image', 'PUT', fd);
          await loadProfileImage();
        } catch (e:any) {
          photoError = e.message || 'Profile photo could not be uploaded.';
        }
      }
      setMe(p);
      setPendingProfileImageFile(null);
      if (pendingProfileImagePreview.startsWith('blob:')) URL.revokeObjectURL(pendingProfileImagePreview);
      setPendingProfileImagePreview('');
      setEditingProfile(false);
      setNotice(photoError ? 'Profile updated, but the profile photo could not be uploaded.' : 'Profile updated.');
    } catch(e:any) {
      setNotice(e.message || 'Unable to update profile.');
    } finally { setBusy(false); }
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
      setPendingProfileImageFile(null);
      if (pendingProfileImagePreview.startsWith('blob:')) URL.revokeObjectURL(pendingProfileImagePreview);
      setPendingProfileImagePreview('');
      setNotice('Profile photo removed.');
    } catch(e:any) { setNotice(e.message || 'Unable to remove profile photo.'); }
    finally { setBusy(false); }
  }

  function chooseProfileImage(file: File|null) {
    if (!file) return;
    if (!['image/jpeg','image/png','image/webp'].includes(file.type)) {
      setNotice('Please select a JPG, PNG or WebP image.');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setNotice('Profile photo must be 5 MB or smaller.');
      return;
    }
    if (pendingProfileImagePreview.startsWith('blob:')) URL.revokeObjectURL(pendingProfileImagePreview);
    setPendingProfileImageFile(file);
    setPendingProfileImagePreview(URL.createObjectURL(file));
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
          if (purpose === 'wallet') setHomeActionModal(null);
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
          if (purpose === 'wallet') setHomeActionModal(null);
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

  async function addMoney(): Promise<boolean> {
    const amount=Number(addMoneyAmount);
    if (!(amount >= 1 && amount <= 50000)) { setNotice('Enter a wallet top-up amount between ₹1 and ₹50,000.'); return false; }
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
    } catch(e:any) { setNotice(e.message || 'Unable to start wallet top-up.'); return false; }
    finally { setBusy(false); }
    return addMoneyProvider === 'mock';
  }

  async function withdrawMoney(): Promise<boolean> {
    const amount=Number(withdrawAmount);
    const upi=withdrawUpi.trim();
    const availableBalance = Number(wallet?.availableBalance || 0);
    if (!(amount >= 1) || amount > availableBalance) {
      setNotice('Enter a valid withdrawal amount of at least ₹1 and no more than the available balance.');
      return false;
    }
    if (!/^[A-Za-z0-9]+@[A-Za-z]+$/.test(upi)) { setNotice('Enter a valid UPI ID.'); return false; }
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
    } catch(e:any) { setNotice(e.message || 'Unable to withdraw money.'); return false; }
    finally { setBusy(false); }
    return true;
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
    setSelectedCar(undefined);
    setRentalQuote(undefined);
    setRentalForm({pickup:'',drop:''});
    loadRentalCars();
  }

  function openRentalBooking(car: RentalCar, startDate = '', endDate = '') {
    setRentalDetails(undefined);
    setSelectedCar(car);
    setRentalBookingCar(car);
    setRentalSearch(s => ({...s, startDate, endDate}));
    setRentalForm({pickup: car.pickupAddress || '', drop: ''});
    setRentalQuote(undefined);
    setView('rental-booking');
  }

  function openRentalDetails(car: RentalCar) {
    setRentalDetails(car);
  }

  function clearRentalBookingQuote() {
    setRentalQuote(undefined);
  }

  async function checkRentalFareForBooking() {
    const car = rentalBookingCar;
    const startDate = rentalSearch.startDate;
    const endDate = rentalSearch.endDate;
    const pickup = rentalForm.pickup.trim();
    const drop = rentalForm.drop.trim();
    if(!car || !pickup || !drop || !startDate || !endDate){
      setNotice('Enter pickup, drop, start date & time, and end date & time before checking the fare.');
      return;
    }
    const startMs = new Date(startDate).getTime();
    const endMs = new Date(endDate).getTime();
    if(!Number.isFinite(startMs) || !Number.isFinite(endMs) || endMs <= startMs || startMs < Date.now()){
      setNotice('Choose a future start date/time and an end date/time later than the start.');
      return;
    }
    setBusy(true); setNotice(''); setRentalQuote(undefined);
    try {
      const q=await api<RentalQuote>('/api/v1/car-rental/bookings/quote',{method:'POST',body:JSON.stringify({
        carId:car.id,pickupLocation:pickup,dropLocation:drop,startDate,endDate
      })});
      setRentalQuote(q);
    } catch(e:any) {
      setNotice(e.message || 'Unable to calculate rental fare.');
    } finally {
      setBusy(false);
    }
  }

  async function confirmRentalBooking() {
    const car = rentalBookingCar;
    const q = rentalQuote;
    if(!car || !q){setNotice('Check the fare before confirming the booking.');return;}
    const available = Number(wallet?.availableBalance || 0);
    if(wallet == null){setNotice('Wallet balance is unavailable. Refresh the wallet and try again.');return;}
    if(available < Number(q.total || 0)){setNotice('Not enough available balance. Add money to your wallet to continue.');return;}
    setBusy(true); setNotice('');
    try {
      const result=await api<RentalBooking>('/api/v1/car-rental/bookings',{method:'POST',body:JSON.stringify({
        clientRequestId:crypto.randomUUID(),
        carId:car.id,
        pickupLocation:q.pickup,
        dropLocation:q.drop,
        startDate:q.startDate,
        endDate:q.endDate,
        paymentMethod:'WALLET'
      })});
      setBookings(b=>[result,...b.filter(x=>x.bookingId!==result.bookingId)]);
      await Promise.all([refreshWallet(), loadBookings()]);
      setRentalBookingCar(undefined);
      setSelectedCar(undefined);
      setRentalQuote(undefined);
      setRentalForm({pickup:'',drop:''});
      setNotice('Booking confirmed. The amount was deducted from your available wallet balance.');
      setView('bookings');
    } catch(e:any) {
      setNotice(e.message || 'Car rental booking could not be completed.');
    } finally {
      setBusy(false);
    }
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
    if(!confirm('This will cancel the rental booking and refund the wallet amount. Continue?')) return;
    setBusy(true);
    try {
      await api('/api/v1/car-rental/bookings/'+encodeURIComponent(bookingId)+'/cancel',{method:'POST'});
      await Promise.all([refreshWallet(), loadBookings()]);
      setNotice('Booking cancelled and the wallet amount was refunded.');
    } catch(e:any){setNotice(e.message || 'Unable to cancel booking.');}
    finally{setBusy(false);}
  }

  function validateVendorForm() {
    const primary = String(vendorForm.payoutPrimaryMethod || '').toUpperCase();
    if (!vendorForm.fullName.trim()) return 'Full name is required.';
    if (!vendorForm.address.trim()) return 'Address is required.';
    if (!vendorForm.city.trim()) return 'City is required.';
    if (!vendorForm.state.trim()) return 'State is required.';
    if (!vendorForm.pinCode.trim()) return 'PIN code is required.';
    if (primary === 'UPI' && !vendorForm.payoutUpiId.trim()) return 'Payout UPI is required when UPI is selected as primary.';
    if (primary === 'BANK' && (!vendorForm.bankAccountNumber.trim() || !vendorForm.bankIfsc.trim())) return 'Bank account and IFSC are required when Bank is selected as primary.';
    return '';
  }

  async function saveVendor() {
    setVendorSubmitAttempted(true);
    const validation = validateVendorForm();
    if (validation) {
      setNotice(validation);
      return;
    }
    setBusy(true);
    try {
      const verifiedProfileEdit = String(vendor?.status || '').toUpperCase() === 'VERIFIED' && showVendorForm;
      const method = verifiedProfileEdit ? 'PUT' : 'POST';
      const v=await api<RentalVendor>('/api/v1/car-rental/vendor',{method,body:JSON.stringify({
        vendorType: vendorForm.vendorType,
        fullName: vendorForm.fullName.trim(),
        businessName: vendorForm.businessName.trim() || null,
        address: vendorForm.address.trim(),
        city: vendorForm.city.trim(),
        state: vendorForm.state.trim(),
        pinCode: vendorForm.pinCode.trim(),
        panNumber: vendorForm.panNumber.trim() || null,
        payoutUpiId: vendorForm.payoutUpiId.trim() || null,
        bankAccountNumber: vendorForm.bankAccountNumber.trim() || null,
        bankIfsc: vendorForm.bankIfsc.trim() || null,
        bankName: vendorForm.bankName.trim() || null,
        payoutPrimaryMethod: vendorForm.payoutPrimaryMethod.trim() || null
      })});
      setVendor(v);
      setShowVendorForm(false);
      setVendorSubmitAttempted(false);
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
    setVendorSubmitAttempted(false);
    setShowVendorForm(true);
  }

  function openVendorOnboarding() {
    setVendorForm({
      vendorType: vendor?.vendorType || 'INDIVIDUAL', fullName: vendor?.fullName || '', businessName: vendor?.businessName || '',
      address: vendor?.address || '', city: vendor?.city || '', state: vendor?.state || '', pinCode: vendor?.pinCode || '',
      panNumber: vendor?.panNumber || '', payoutUpiId: vendor?.payoutUpiId || '', bankAccountNumber: vendor?.bankAccountNumber || '',
      bankIfsc: vendor?.bankIfsc || '', bankName: vendor?.bankName || '', payoutPrimaryMethod: vendor?.payoutPrimaryMethod || ''
    });
    setVendorSubmitAttempted(false);
    setAccountSection('vendor');
  }

  function rawVehiclePhotos(car?:RentalCar) {
    return String(car?.imageUrl || '').replace(/\\n/g,'|').split('|').map(x=>x.trim()).filter(Boolean).slice(0,4).concat(['','','','']).slice(0,4);
  }

  function resetVehicleForm(car?:RentalCar) {
    const photos = rawVehiclePhotos(car);
    setVehicleEditId(car ? String(car.id) : '');
    setVehicleSubmitAttempted(false);
    setVehicleForm({
      ...(car ? {
        name:car.name || '', category:car.category || 'Sedan', seats:car.seats || 5, transmission:car.transmission || 'Automatic',
        fuelType:car.fuelType || 'Petrol', manufacturingYear:car.manufacturingYear || new Date().getFullYear(),
        registrationYear:car.registrationYear || new Date().getFullYear(), registrationNumber:car.registrationNumber || '',
        make:car.make || '', model:car.model || '', variant:car.variant || '', pickupAddress:car.pickupAddress || '',
        city:car.city || '', state:car.state || '', pricePerDay:car.pricePerDay || '',
        driver:{fullName:car.driverName || '',mobile:car.driverMobile || '',licenseNumber:car.driverLicenseNumber || '',
          licenseExpiry:car.driverLicenseExpiry ? String(car.driverLicenseExpiry).slice(0,10) : '',address:car.driverAddress || ''}
      } : {
        ...defaultVehicle,
        category:'Sedan', seats:5, transmission:'Automatic', fuelType:'Petrol',
        driver:{...defaultVehicle.driver,licenseExpiry:''}
      })
    });
    setVehiclePhotoUrls(photos);
    setVehiclePhotoFiles([null,null,null,null]);
    setVehiclePhotoPreviews(photos.map(x=>x ? (x.startsWith('http') ? x : base + '/api/v1/car-rental/photos/' + x.replace(/^\/+/,'')) : ''));
    setDriverPhotoFile(null);
    setDriverPhotoPreview(car?.driverPhotoUrl ? (car.driverPhotoUrl.startsWith('http') ? car.driverPhotoUrl : base + car.driverPhotoUrl) : '');
    setShowVehicleForm(true);
    setAccountSection('vehicle');
  }

  function setVehiclePhotoFile(slot:number, file:File|null) {
    setVehiclePhotoFiles(current => {
      const next=[...current];
      next[slot]=file;
      return next;
    });
    setVehiclePhotoPreviews(current => {
      const next=[...current];
      next[slot]=file ? URL.createObjectURL(file) : '';
      return next;
    });
    if(file) {
      setVehiclePhotoUrls(current => {
        const next=[...current];
        next[slot]='';
        return next;
      });
    }
  }

  function setVehiclePhotoUrl(slot:number, value:string) {
    setVehiclePhotoUrls(current => {
      const next=[...current];
      next[slot]=value;
      return next;
    });
    if(value.trim()) {
      setVehiclePhotoFiles(current => {
        const next=[...current];
        next[slot]=null;
        return next;
      });
      setVehiclePhotoPreviews(current => {
        const next=[...current];
        next[slot]=value;
        return next;
      });
    }
  }

  function handleVehiclePhotoSelection(slot:number, file:File|null) {
    if(!file) return;
    if(!['image/jpeg','image/png','image/webp'].includes(file.type)){
      setNotice('Please select a JPG, PNG or WebP vehicle photo.');
      return;
    }
    if(file.size > 5 * 1024 * 1024){
      setNotice('Vehicle photo must be 5 MB or smaller.');
      return;
    }
    setVehiclePhotoFile(slot,file);
  }

  function handleDriverPhotoSelection(file:File|null) {
    if(!file) return;
    if(!['image/jpeg','image/png','image/webp'].includes(file.type)){
      setNotice('Please select a JPG, PNG or WebP driver photo.');
      return;
    }
    if(file.size > 5 * 1024 * 1024){
      setNotice('Driver photo must be 5 MB or smaller.');
      return;
    }
    const preview=URL.createObjectURL(file);
    setDriverPhotoFile(file);
    setDriverPhotoPreview(preview);
  }

  useEffect(() => () => {
    vehiclePhotoPreviews.forEach(value => { if(value.startsWith('blob:')) URL.revokeObjectURL(value); });
    if(driverPhotoPreview.startsWith('blob:')) URL.revokeObjectURL(driverPhotoPreview);
  }, [vehiclePhotoPreviews, driverPhotoPreview]);

  function validateVehicleForm() {
    const currentYear=new Date().getFullYear();
    const earliestYear=currentYear-20;
    const manufacturing=Number(vehicleForm.manufacturingYear);
    const registration=Number(vehicleForm.registrationYear);
    const price=Number(vehicleForm.pricePerDay);
    const mobile=normalizeIndianMobile(vehicleForm.driver.mobile);
    const licenseExpiry=vehicleForm.driver.licenseExpiry ? new Date(vehicleForm.driver.licenseExpiry+'T00:00:00') : null;
    const photosComplete=vehiclePhotoUrls.every((url,slot)=>Boolean(url.trim() || vehiclePhotoFiles[slot]));
    if(!vehicleForm.name.trim()) return 'Vehicle name is required.';
    if(!vehicleForm.make.trim()) return 'Make is required.';
    if(!vehicleForm.model.trim()) return 'Model is required.';
    if(!vehicleForm.registrationNumber.trim()) return 'Registration number is required.';
    if(!vehicleForm.city.trim()) return 'City is required.';
    if(!vehicleForm.state.trim()) return 'State is required.';
    if(!Number.isInteger(manufacturing) || manufacturing < earliestYear || manufacturing > currentYear) return 'Manufacturing year must be within the last 20 years.';
    if(!Number.isInteger(registration) || registration < manufacturing || registration > currentYear) return 'Registration year cannot be before manufacture year or after the current year.';
    if(!/^\d+(\.\d{1,2})?$/.test(String(vehicleForm.pricePerDay).trim()) || !(price>0)) return 'Enter a valid positive price with up to 2 decimals.';
    if(!(Number(vehicleForm.seats) >= 2 && Number(vehicleForm.seats) <= 8)) return 'Seats must be between 2 and 8.';
    if(!vehicleForm.driver.fullName.trim()) return 'Driver name is required.';
    if(!/^\\d{10}$/.test(mobile)) return 'Driver mobile must contain exactly 10 digits.';
    if(!vehicleForm.driver.licenseNumber.trim()) return 'Driving licence number is required.';
    if(!licenseExpiry || licenseExpiry <= new Date(new Date().toDateString())) return 'Licence expiry must be a future date.';
    if(!photosComplete) return 'Front, side, rear and interior vehicle photos are required.';
    return '';
  }

  async function saveVehicle() {
    setVehicleSubmitAttempted(true);
    const validation=validateVehicleForm();
    if(validation){setNotice(validation);return;}
    setBusy(true);
    try {
      const rawImages=vehiclePhotoUrls.map((url,slot)=>vehiclePhotoFiles[slot] ? '' : url.trim()).join('|');
      const body={
        name:vehicleForm.name.trim(),
        category:vehicleForm.category,
        seats:Number(vehicleForm.seats),
        transmission:vehicleForm.transmission,
        fuelType:vehicleForm.fuelType,
        manufacturingYear:Number(vehicleForm.manufacturingYear),
        registrationYear:Number(vehicleForm.registrationYear),
        registrationNumber:sanitizeRegistration(vehicleForm.registrationNumber.trim()),
        make:sanitizeVehicleAlphaNumeric(vehicleForm.make.trim(),80),
        model:sanitizeVehicleAlphaNumeric(vehicleForm.model.trim(),80),
        variant:sanitizeVehicleAlphaNumeric(vehicleForm.variant.trim(),80) || null,
        pickupAddress:vehicleForm.pickupAddress.trim(),
        city:sanitizeVehicleAlphaNumeric(vehicleForm.city.trim(),100),
        state:vehicleForm.state,
        pricePerDay:Number(vehicleForm.pricePerDay),
        pickupLocation:null,
        imageUrl:rawImages || null,
        driver:{
          fullName:sanitizeVehicleAlphaNumeric(vehicleForm.driver.fullName.trim(),120),
          mobile:normalizeIndianMobile(vehicleForm.driver.mobile),
          licenseNumber:sanitizeLicense(vehicleForm.driver.licenseNumber.trim()),
          licenseExpiry:vehicleForm.driver.licenseExpiry,
          address:vehicleForm.driver.address.trim() || null
        }
      };
      const saved=await api<RentalCar>(
        vehicleEditId ? '/api/v1/car-rental/vendor/vehicles/'+encodeURIComponent(vehicleEditId) : '/api/v1/car-rental/vendor/vehicles',
        {method:vehicleEditId?'PUT':'POST',body:JSON.stringify(body)}
      );
      let latest=saved;
      for(let slot=0;slot<vehiclePhotoFiles.length;slot++){
        const file=vehiclePhotoFiles[slot];
        if(file) {
          const fd=new FormData(); fd.append('photo',file);
          latest=await apiUpload<RentalCar>('/api/v1/car-rental/vendor/vehicles/'+encodeURIComponent(saved.id)+'/photos/'+slot,'PUT',fd);
        }
      }
      if(driverPhotoFile && latest.driverId){
        const fd=new FormData(); fd.append('photo',driverPhotoFile);
        latest=await apiUpload<RentalCar>('/api/v1/car-rental/vendor/drivers/'+encodeURIComponent(latest.driverId)+'/photo','PUT',fd);
      }
      setVendorVehicles(v=>vehicleEditId ? v.map(x=>x.id===latest.id?latest:x) : [latest,...v]);
      setSelectedVendorVehicle(undefined);
      setShowVehicleForm(false);
      setVehicleSubmitAttempted(false);
      setAccountSection('vendor');
      setNotice(vehicleEditId ? 'Vehicle resubmitted for admin review.' : 'Vehicle submitted for admin review.');
      await loadAccountData();
    } catch(e:any){
      setNotice(e.message || 'Vehicle was saved, but one or more photos could not be uploaded.');
      await loadAccountData();
    } finally{setBusy(false);}
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
      setVehicleUnavailability(current => [...current.filter(item => item.carId !== carId), ...(list || [])]);
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
    if(view==='account') {
      setAccountSection('profile');
    }
  },[view]);

  useEffect(()=>{
    if(view==='home'){
      void Promise.all([refreshWallet(), loadLatestRecharge(), loadCommissionSummary()]);
    } else if(view==='history'){
      void loadHistory();
    } else if(view==='wallet'){
      void Promise.all([refreshWallet(), loadWalletHistory(0), loadWithdrawals(0), loadCommissionSummary()]);
    } else if(view==='rental'){
      void loadRentalCars(rentalSearch.startDate,rentalSearch.endDate,rentalSearch.location);
    } else if(view==='rental-booking'){
      void refreshWallet();
    } else if(view==='bookings'){
      void loadBookings();
    } else if(view==='account'){
      void loadAccountData();
    }
  },[view]);

  useEffect(()=>{
    if(view==='history'){
      void loadHistory(0);
    }
  },[view, historyKind, historyFrom, historyTo]);

  useEffect(()=>{
    if(selectedVendorVehicle){
      loadVehicleUnavailability(selectedVendorVehicle.id);
      loadVehicleCalendar(selectedVendorVehicle.id);
    }
  },[selectedVendorVehicle?.id]);

  useEffect(()=>{
    if(selectedVendorVehicle) loadVehicleCalendar(selectedVendorVehicle.id,calendarMonth);
  },[calendarMonth]);

  useEffect(()=>{
    if(view==='account' && accountSection==='vendor' && vendorVerified && vendorVehicles.length){
      vendorVehicles.forEach(car => { void loadVehicleUnavailability(car.id); });
    }
  },[view, accountSection, String(vendor?.status || '').toUpperCase(), vendorVehicles.map(car=>car.id).join('|')]);

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
        <button className="icon-btn mobile-only portal-mobile-menu" onClick={()=>setDrawer(true)}><Menu size={19}/></button>
        <div className="portal-topbar-copy"><span>mPay personal workspace</span><h1>{
          view==='home'?'Good to see you.':view==='recharge'?'Mobile recharge':view==='wallet'?'Your wallet':
          view==='history'?'Transaction history':view==='marketplace'?'Marketplace':view==='rental'?'Marketplace · Car Rental':
          view==='rental-booking'?'Book with driver':view==='bookings'?'My Bookings':'Your account'
        }</h1></div>
        <MpayServiceShowcase view={view} />
        <div className="portal-avatar portal-topbar-avatar">{pendingProfileImagePreview ? <img src={pendingProfileImagePreview} alt="Profile"/> : profileImage ? <img src={profileImage} alt="Profile"/> : (me?.name || 'U').charAt(0).toUpperCase()}</div>
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

        <WalletBalanceHero
          wallet={wallet}
          loading={busy}
          onRefreshBalance={()=>void refreshWallet()}
          onAddMoney={()=>setHomeActionModal('add')}
          onWithdraw={()=>setHomeActionModal('withdraw')}
        />

        <section className="portal-home-section android-home-section">
          <div className="portal-home-section-head">
            <div><h2>Quick Actions</h2></div>
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
            <div><h2>Marketplace</h2></div>
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

        <section className="android-home-section android-home-earnings-section">
          <div className="android-home-section-title">
            <h2>Today’s recharge earnings</h2>
            <button onClick={()=>loadCommissionSummary()} disabled={busy}><RefreshCw size={15}/></button>
          </div>
          <HomeEarningsPeriod period={commissionSummary?.daily} isToday={true}/>
        </section>

        <section className="android-home-section android-home-earnings-section">
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
                <button className="wallet-primary-wide" disabled={busy || !(Number(addMoneyAmount)>=1 && Number(addMoneyAmount)<=50000)} onClick={async()=>{if(await addMoney())setHomeActionModal(null);}}>
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
                <button className="wallet-primary-wide" disabled={busy || !(Number(withdrawAmount)>=1 && Number(withdrawAmount)<=Number(wallet?.availableBalance||0)) || !/^[A-Za-z0-9]+@[A-Za-z]+$/.test(withdrawUpi.trim())} onClick={async()=>{if(await withdrawMoney())setHomeActionModal(null);}}>
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
        </div>

        <WalletBalanceHero
          wallet={wallet}
          loading={busy}
          onRefreshBalance={()=>void refreshWallet()}
          onAddMoney={()=>document.getElementById('wallet-add-money')?.scrollIntoView({behavior:'smooth',block:'center'})}
          onWithdraw={()=>document.getElementById('wallet-withdraw')?.scrollIntoView({behavior:'smooth',block:'center'})}
        />

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

      {view==='history' && <section className="portal-content recharge-history-page">
        <div className="portal-panel recharge-history-panel">
          <div className="panel-head recharge-history-head">
            <div>
              <span className="recharge-history-eyebrow">TRANSACTIONS</span>
              <h2>Recharge history</h2>
              <p>Review every recharge with the same status, date and pagination controls available in the Android app.</p>
            </div>
            <button className="landing-secondary recharge-refresh-button" onClick={()=>void loadHistory(0)} disabled={historyRefreshing}>
              <RefreshCw size={15} className={historyRefreshing ? 'spin' : ''}/> {historyRefreshing ? 'Refreshing…' : 'Refresh'}
            </button>
          </div>

          <div className="recharge-history-filters">
            <div className="recharge-history-filter-group">
              <span className="recharge-history-filter-label">Date range</span>
              <div className="recharge-range-segmented">
                {([
                  ['TODAY','Today'],
                  ['LAST_7_DAYS','7 days'],
                  ['THIS_MONTH','Month'],
                  ['CUSTOM','Custom']
                ] as const).map(([value,label]) => (
                  <button
                    key={value}
                    className={historyFilter===value ? 'selected' : ''}
                    onClick={()=>applyHistoryRange(value)}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>

            <div className="recharge-history-range-row">
              <label>
                <span>From</span>
                <input
                  type="date"
                  max={localDate()}
                  value={historyFrom}
                  onChange={e=>{
                    setHistoryFilter('CUSTOM');
                    setHistoryFrom(e.target.value);
                    setHistoryPage(0);
                  }}
                />
              </label>
              <span className="recharge-range-arrow">→</span>
              <label>
                <span>To</span>
                <input
                  type="date"
                  max={localDate()}
                  min={historyFrom || undefined}
                  value={historyTo}
                  onChange={e=>{
                    setHistoryFilter('CUSTOM');
                    setHistoryTo(e.target.value);
                    setHistoryPage(0);
                  }}
                />
              </label>
            </div>

            <div className="recharge-history-filter-group">
              <span className="recharge-history-filter-label">Status</span>
              <div className="recharge-status-chips">
                {([
                  ['ALL','All'],
                  ['SUCCESS','Success'],
                  ['PENDING','Pending'],
                  ['FAILED','Failed']
                ] as const).map(([value,label]) => (
                  <button
                    key={value}
                    className={historyKind===value ? 'selected status-'+value.toLowerCase() : 'status-'+value.toLowerCase()}
                    onClick={()=>changeHistoryStatus(value)}
                  >
                    <span className="recharge-status-dot"/> {label}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="recharge-history-summary">
            <span>
              {historyFrom && historyTo
                ? new Date(historyFrom + 'T00:00:00').toLocaleDateString('en-IN',{day:'2-digit',month:'short',year:'numeric'}) +
                  ' → ' +
                  new Date(historyTo + 'T00:00:00').toLocaleDateString('en-IN',{day:'2-digit',month:'short',year:'numeric'})
                : 'Selected period'}
            </span>
            <b>{historyTotalItems} transaction{historyTotalItems===1?'':'s'}</b>
          </div>

          {notice && (
            <div className="recharge-history-notice">
              <span><ShieldCheck size={16}/>{notice}</span>
            </div>
          )}

          {historyLoading && recharges.length === 0 ? (
            <div className="recharge-history-loading">
              <div className="recharge-history-skeleton"/><div className="recharge-history-skeleton"/><div className="recharge-history-skeleton"/>
            </div>
          ) : !recharges.length ? (
            <div className="recharge-history-empty">
              <span className="recharge-history-empty-icon"><History size={24}/></span>
              <b>No recharge transactions</b>
              <span>There are no recharge records for the selected period and status.</span>
            </div>
          ) : (
            <div className="recharge-history-list">
              {recharges.map((item,i) => <RechargeHistoryWebCard key={String(item.transactionId || item.clientRequestId || i)} item={item} onCopy={copyText}/>)}
              {historyLoading && <div className="recharge-history-inline-loading"><span className="wallet-spinner"/></div>}
            </div>
          )}

          <WebHistoryPagination
            page={historyPage}
            totalItems={historyTotalItems}
            totalPages={historyTotalPages}
            pageSize={historyPageSize}
            onPageSizeChange={changeHistoryPageSize}
            onPrevious={()=>void loadHistory(Math.max(0,historyPage-1))}
            onNext={()=>void loadHistory(historyPage+1)}
          />
        </div>
      </section>}

      {view==='marketplace' && <section className="portal-content"><div className="portal-panel"><div className="panel-head"><div><h2>Marketplace</h2><p>Explore mPay service categories.</p></div><Car size={28}/></div>
        <button className="rental-car selected" onClick={()=>setView('rental')}><div className="rental-car-icon"><Car size={26}/></div><b>Car Rental</b><span>NEW · Chauffeur-driven cars</span><strong>Open marketplace</strong></button>
      </div></section>}

      {view==='rental' && <section className="portal-content rental-marketplace-page">
        <div className="portal-panel rental-marketplace-panel">
          <div className="panel-head rental-marketplace-head">
            <div>
              <span className="rental-eyebrow">MOBILITY</span>
              <h2>Car Rental Marketplace</h2>
              <p>Find chauffeur-driven cars by place and availability.</p>
            </div>
            <span className="rental-head-icon"><CarFront size={24}/></span>
          </div>

          <section className="rental-search-card rental-android-card">
            <div className="rental-search-heading">
              <div>
                <b>Search availability</b>
                <span>Use a pickup area, a future time window, or both.</span>
              </div>
              {(rentalSearch.startDate || rentalSearch.endDate || rentalSearch.location) && <span className="search-state-chip">Filters active</span>}
            </div>
            <div className="rental-search-grid">
              <label>City / pickup area
                <div className="rental-field-shell"><MapPin size={16}/><input value={rentalSearch.location} placeholder="Patna, Airport Road…" onChange={e=>setRentalSearch({...rentalSearch,location:e.target.value})}/></div>
              </label>
              <label>From
                <div className="rental-field-shell"><CalendarDays size={16}/><input type="datetime-local" value={rentalSearch.startDate} min={isoNow()} onChange={e=>setRentalSearch({...rentalSearch,startDate:e.target.value})}/></div>
              </label>
              <label>To
                <div className="rental-field-shell"><CalendarDays size={16}/><input type="datetime-local" value={rentalSearch.endDate} min={rentalSearch.startDate || isoNow()} onChange={e=>setRentalSearch({...rentalSearch,endDate:e.target.value})}/></div>
              </label>
            </div>
            {((rentalSearch.startDate && !rentalSearch.endDate) || (!rentalSearch.startDate && rentalSearch.endDate) ||
              (rentalSearch.startDate && rentalSearch.endDate && new Date(rentalSearch.endDate).getTime() <= new Date(rentalSearch.startDate).getTime()) ||
              (rentalSearch.startDate && new Date(rentalSearch.startDate).getTime() < Date.now())) &&
              <div className="rental-inline-error">Choose both dates and times, with an end later than the start and a future start.</div>}
            {!rentalSearch.location.trim() && !rentalSearch.startDate && !rentalSearch.endDate &&
              <div className="rental-inline-help">Use a place, a time window, or both.</div>}
            <div className="rental-search-actions">
              <button className="landing-secondary" onClick={clearRentalSearch}>Clear</button>
              <button className="landing-primary" onClick={searchRentalCars}>Find cars <ArrowRight size={16}/></button>
            </div>
          </section>

          {cars.length ? (
            <section className="rental-results-section">
              <div className="rental-results-head">
                <div><span>AVAILABLE VEHICLES</span><b>{cars.length} car{cars.length===1?'':'s'} found</b></div>
                <small>Tap a vehicle to inspect details and book with its chauffeur.</small>
              </div>
              <div className="rental-car-grid rental-android-grid">
                {cars.map((car, carIndex)=>(
                  <article className="rental-market-card" key={car.id}>
                    <button className="rental-market-card-main" onClick={()=>openRentalDetails(car)}>
                      <div className="rental-market-image">
                        <VehicleFourPhotoGallery car={car} priority={carIndex < 2} />
                        <span>{car.category}</span>
                      </div>
                      <div className="rental-market-copy">
                        <div className="rental-market-title-row">
                          <div><b>{car.name}</b><small>{[car.make,car.model,car.variant].filter(Boolean).join(' ') || car.category}</small></div>
                          <strong>{money(car.pricePerDay)}<em>/day</em></strong>
                        </div>
                        <div className="rental-market-specs">
                          <span>{car.seats} seats</span><span>{car.transmission}</span><span>{car.fuelType || 'Fuel —'}</span>
                        </div>
                        <div className="rental-market-driver">
                          <span className="rental-driver-avatar">{car.driverPhotoUrl ? <img src={car.driverPhotoUrl.startsWith('http') ? car.driverPhotoUrl : base + car.driverPhotoUrl} alt=""/> : <UserRound size={15}/>}</span>
                          <span><b>{car.driverName}</b><small>Chauffeur</small></span>
                          {car.driverRating != null && <span className="rental-driver-rating">★ {Number(car.driverRating).toFixed(1)}</span>}
                        </div>
                      </div>
                    </button>
                    <button className="rental-market-details-button" onClick={()=>openRentalDetails(car)}><Eye size={14}/> View details</button>
                  </article>
                ))}
              </div>
            </section>
          ) : (
            <div className="rental-empty-state rental-android-empty">
              <div className="rental-empty-icon"><Car size={28}/></div>
              <b>{rentalSearch.location || rentalSearch.startDate || rentalSearch.endDate ? 'No cars match these filters' : 'No cars available right now'}</b>
              <span>{rentalSearch.location || rentalSearch.startDate || rentalSearch.endDate ? 'Try a broader pickup area or availability window.' : 'Approved chauffeur-driven vehicles will appear here.'}</span>
              {(rentalSearch.location || rentalSearch.startDate || rentalSearch.endDate) && <button className="landing-secondary" onClick={clearRentalSearch}>Clear filters</button>}
            </div>
          )}
        </div>
      </section>}

      {view==='rental-booking' && rentalBookingCar && <section className="portal-content rental-booking-page">
        <div className="rental-booking-layout">
          <div className="portal-panel rental-booking-panel">
            <div className="panel-head rental-booking-head">
              <div>
                <span className="rental-eyebrow">BOOKING</span>
                <h2>Book with driver</h2>
                <p>Your payment will come from the available wallet balance.</p>
              </div>
              <button className="landing-secondary" onClick={()=>{setRentalBookingCar(undefined);setRentalQuote(undefined);setView('rental');}} disabled={busy}><ChevronLeft size={15}/> Back</button>
            </div>

            <article className="rental-booking-car-card">
              <div className="rental-booking-car-image">
                <VehicleFourPhotoGallery car={rentalBookingCar} priority />              </div>
              <div className="rental-booking-car-copy">
                <div className="rental-booking-car-title"><div><b>{rentalBookingCar.name}</b><small>{[rentalBookingCar.make,rentalBookingCar.model,rentalBookingCar.variant].filter(Boolean).join(' ') || rentalBookingCar.category}</small></div><strong>{money(rentalBookingCar.pricePerDay)}<em>/day</em></strong></div>
                <div className="rental-booking-car-driver">
                  <span className="rental-driver-avatar">{rentalBookingCar.driverPhotoUrl ? <img src={rentalBookingCar.driverPhotoUrl.startsWith('http') ? rentalBookingCar.driverPhotoUrl : base + rentalBookingCar.driverPhotoUrl} alt=""/> : <UserRound size={15}/>}</span>
                  <span><b>{rentalBookingCar.driverName}</b><small>{rentalBookingCar.driverMobile || 'Chauffeur'}</small></span>
                  {rentalBookingCar.driverRating != null && <span className="rental-driver-rating">★ {Number(rentalBookingCar.driverRating).toFixed(1)}</span>}
                </div>
              </div>
            </article>

            <div className="rental-booking-section">
              <div className="rental-booking-section-head"><span>1</span><div><b>Trip details</b><small>Enter the exact pickup and drop locations.</small></div></div>
              <div className="rental-booking-form-grid">
                <label>Pickup location
                  <div className="rental-input-with-icon"><MapPin size={16}/><input value={rentalForm.pickup} onChange={e=>{setRentalForm({...rentalForm,pickup:e.target.value});clearRentalBookingQuote();}} placeholder="Enter pickup location"/></div>
                </label>
                <label>Drop location
                  <div className="rental-input-with-icon"><MapPin size={16}/><input value={rentalForm.drop} onChange={e=>{setRentalForm({...rentalForm,drop:e.target.value});clearRentalBookingQuote();}} placeholder="Enter drop location"/></div>
                </label>
              </div>
              <div className="rental-mapless-note"><MapPin size={14}/><span>Enter locations manually for now. Map/Places selection can be added when the maps key is enabled.</span></div>
            </div>

            <div className="rental-booking-section">
              <div className="rental-booking-section-head"><span>2</span><div><b>Availability window</b><small>Time controls vehicle availability; billing is per 24-hour day.</small></div></div>
              <div className="rental-booking-form-grid">
                <label>Start date & time
                  <div className="rental-input-with-icon"><CalendarDays size={16}/><input type="datetime-local" min={isoNow()} value={rentalSearch.startDate} onChange={e=>{setRentalSearch({...rentalSearch,startDate:e.target.value});clearRentalBookingQuote();}}/></div>
                </label>
                <label>End date & time
                  <div className="rental-input-with-icon"><CalendarDays size={16}/><input type="datetime-local" min={rentalSearch.startDate || isoNow()} value={rentalSearch.endDate} onChange={e=>{setRentalSearch({...rentalSearch,endDate:e.target.value});clearRentalBookingQuote();}}/></div>
                </label>
              </div>
              {(!rentalSearch.startDate || !rentalSearch.endDate || new Date(rentalSearch.startDate).getTime() < Date.now() || new Date(rentalSearch.endDate).getTime() <= new Date(rentalSearch.startDate).getTime()) ?
                <div className="rental-inline-error">Choose a future start and an end date/time later than the start.</div> :
                <div className="rental-inline-help">Pricing is per day (24 hours). Any partial day is charged as one full day; time also controls availability.</div>}
            </div>

            {!rentalQuote ? (
              <div className="rental-booking-cta">
                <button className="landing-primary rental-wide-action" disabled={busy || !rentalForm.pickup.trim() || !rentalForm.drop.trim() || !rentalSearch.startDate || !rentalSearch.endDate || new Date(rentalSearch.endDate).getTime() <= new Date(rentalSearch.startDate).getTime() || new Date(rentalSearch.startDate).getTime() < Date.now()} onClick={checkRentalFareForBooking}>
                  {busy ? 'Calculating…' : 'Check fare'} <ArrowRight size={16}/>
                </button>
              </div>
            ) : (
              <section className="rental-fare-card">
                <div className="rental-fare-head"><div><span>FARE SUMMARY</span><b>{rentalQuote.days} day{rentalQuote.days===1?'':'s'} × {money(rentalQuote.pricePerDay)}</b></div><strong>{money(rentalQuote.total)}</strong></div>
                <div className="rental-fare-divider"/>
                <div className="rental-fare-line"><span>Available balance</span><b className={wallet == null ? 'rental-wallet-unknown' : Number(wallet.availableBalance) < Number(rentalQuote.total) ? 'amount-debit' : 'amount-credit'}>{wallet == null ? 'Unavailable' : money(wallet.availableBalance)}</b></div>
                <div className="rental-payment-method"><WalletCards size={15}/> Payment method: <b>Wallet</b></div>

                {wallet == null ? (
                  <div className="rental-wallet-warning"><b>Wallet balance unavailable</b><span>We cannot safely confirm this booking until the latest available wallet balance is loaded.</span><button className="landing-secondary" onClick={refreshWallet}>Refresh wallet</button></div>
                ) : Number(wallet.availableBalance) < Number(rentalQuote.total) ? (
                  <div className="rental-wallet-warning danger"><b>Not enough available balance</b><span>Add {money(Math.max(0,Number(rentalQuote.total)-Number(wallet.availableBalance)))} to complete this booking.</span><button className="landing-secondary" onClick={()=>setHomeActionModal('add')}>Add money</button></div>
                ) : (
                  <>
                    <div className="rental-confirm-note">{money(rentalQuote.total)} will be deducted from your available wallet balance when you confirm.</div>
                    <button className="landing-primary rental-wide-action" disabled={busy} onClick={confirmRentalBooking}>{busy ? 'Confirming…' : 'Confirm booking'} <ArrowRight size={16}/></button>
                  </>
                )}
                <button className="rental-recheck" disabled={busy} onClick={clearRentalBookingQuote}>Recheck fare</button>
              </section>
            )}
          </div>
        </div>
      </section>}

      {view==='bookings' && <section className="portal-content rental-bookings-page">
        <div className="portal-panel rental-bookings-panel">
          <div className="panel-head rental-bookings-head">
            <div><span className="rental-eyebrow">TRIPS</span><h2>My Bookings</h2><p>Your chauffeur-driven rental bookings.</p></div>
            <button className="landing-secondary" onClick={refreshBookings} disabled={busy}><RefreshCw size={15}/> Refresh</button>
          </div>
          <div className="rental-booking-status-filters">
            {bookingStatuses.map(s=><button key={s} className={bookingStatusFilter===s?'selected':''} onClick={()=>setBookingStatusFilter(s)}>{s==='ALL'?'All':s.replace(/_/g,' ')}</button>)}
          </div>
          {bookings.length === 0 ? (
            <div className="rental-bookings-empty"><span><Car size={25}/></span><b>No rental bookings yet</b><small>Confirmed chauffeur-driven rentals will appear here.</small></div>
          ) : filteredBookings.length === 0 ? (
            <div className="rental-bookings-empty"><span><History size={25}/></span><b>No matching bookings</b><small>Try another status filter.</small></div>
          ) : (
            <div className="rental-bookings-grid">
              {filteredBookings.map(b=>{
                const rawStatus=String(b.status||'UNKNOWN').toUpperCase();
                const rideCompleted=rawStatus==='CONFIRMED' && Number.isFinite(new Date(b.endDate).getTime()) && new Date(b.endDate).getTime() < Date.now();
                const label=rideCompleted ? 'Ride completed' : rawStatus.replace(/_/g,' ').replace(/\b\w/g,m=>m.toUpperCase());
                const displayStatus=rideCompleted ? 'COMPLETED' : rawStatus;
                const credit=rawStatus==='CANCELLED' || rawStatus==='REFUNDED';
                const canCancel=rawStatus==='CONFIRMED' && new Date(b.startDate).getTime() > Date.now();
                return <article className="rental-booking-card" key={b.bookingId}>
                  <div className="rental-booking-card-image">
                    <VehicleFourPhotoGallery
                       car={{ name: b.carName, imageUrl: b.carImageUrl }}
                       priority={false}
                     />
                  </div>
                  <div className="rental-booking-card-body">
                    <div className="rental-booking-card-head">
                      <div><b>{b.carName}</b><small>Booking {b.bookingId}</small></div>
                      <span className={'rental-booking-status '+String(displayStatus).toLowerCase()}>{label}</span>
                    </div>
                    <div className="rental-booking-info-grid">
                      <div><span>Driver</span><b>{b.driverName || '—'}</b>{b.driverMobile && <small>{b.driverMobile}</small>}</div>
                      <div><span>Payment</span><b>{b.paymentMethod || 'WALLET'}</b></div>
                      <div><span>Trip</span><b>{b.pickup} → {b.drop}</b></div>
                      <div className="align-right"><span>Total</span><strong className={credit ? 'amount-credit' : 'amount-debit'}>{money(b.total)}</strong></div>
                    </div>
                    <div className="rental-booking-dates"><CalendarDays size={14}/><span>{dt(b.startDate)} → {dt(b.endDate)}</span></div>
                    <div className="rental-booking-created">Booked {dt(b.createdAt)}</div>
                    <div className="rental-booking-actions">
                      <button className="copy-btn" onClick={()=>copyText(bookingShareText(b),'Booking details copied.')}><Copy size={14}/><span>Copy</span></button>
                      {canCancel && <button className="text-danger-btn" disabled={busy} onClick={()=>cancelBooking(b.bookingId)}>Cancel booking</button>}
                    </div>
                  </div>
                </article>;
              })}
            </div>
          )}
        </div>
      </section>}

      {view==='account' && <section className="portal-content account-android-parity">
        {accountSection==='profile' && <div className="account-android-stack">
          <section className="portal-panel account-profile-hero">
            <div className="account-profile-avatar-wrap">
              <div className="account-profile-avatar">
                {profileImage ? <img src={profileImage} alt="Profile"/> : (me?.name || 'U').charAt(0).toUpperCase()}
              </div>
              <span className="account-profile-camera"><Camera size={13}/></span>
            </div>
            <div className="account-profile-copy">
              <span className="account-eyebrow">PROFILE</span>
              <h2>{me?.name || 'Your name'}</h2>
              <p>{me?.email || 'Add an email address'}</p>
              <b>{me?.mobile || '—'}</b>
            </div>
            <button className="landing-secondary" onClick={()=>setEditingProfile(true)}><Edit3 size={15}/> Edit</button>
          </section>

          <section className="portal-panel account-details-card">
            <div className="account-section-heading">
              <div><h3>Account details</h3><p>Permanent account information and activity</p></div>
              <FileText size={18}/>
            </div>
            <div className="account-detail-grid">
              <div><span>Account ID</span><b>{me?.publicUserId || '—'}</b></div>
              <div><span>Account type</span><b>{me?.role === 'CLIENT' ? 'Client' : (me?.role || '—')}</b></div>
              <div><span>Commission rate</span><b>{me?.commissionRate != null ? Number(me.commissionRate).toFixed(2)+'%' : '—'}</b></div>
              <div><span>Joined</span><b>{dt(me?.createdAt)}</b></div>
              <div><span>Last profile update</span><b>{dt(me?.profileUpdatedAt || me?.createdAt)}</b></div>
            </div>
          </section>

          <section className={'account-vendor-entry-card '+(vendorVerified?'verified':'')}>
            <div className="account-vendor-entry-icon"><CarFront size={24}/></div>
            <div className="account-vendor-entry-copy">
              <span className="account-eyebrow">RENTAL</span>
              <h3>{
                vendorVerified ? 'Rental Vendor Dashboard' :
                vendorStatus==='REJECTED' ? 'Rental Vendor Application' :
                vendorStatus==='PENDING' ? 'Vendor Application · Pending Verification' :
                'Become a Vendor'
              }</h3>
              <p>{
                vendorVerified ? 'Business workspace: manage cars, availability, payouts and rental operations.' :
                vendorStatus==='REJECTED' ? 'Review the rejection note and resubmit your vendor details.' :
                vendorStatus==='PENDING' ? 'Your application is submitted and awaiting admin verification.' :
                'List your chauffeur-driven car and manage it through the mPay marketplace.'
              }</p>
              {vendor?.vendorId && <span className={statusClass(vendor.status)+' account-vendor-status'}>{vendorStatus || '—'}</span>}
            </div>
            <button className={vendorVerified?'account-vendor-open':'landing-primary'} onClick={()=>{
              if(vendorVerified) setAccountSection('vendor');
              else openVendorOnboarding();
            }}>
              {vendorVerified ? 'Open Vendor Studio' : vendorStatus==='PENDING' ? 'View application' : vendorStatus==='REJECTED' ? 'Review & resubmit' : 'Become a Vendor'}
              <ChevronRight size={16}/>
            </button>
          </section>

          {vendorStatus==='REJECTED' && vendor?.rejectionReason && <div className="account-review-note"><b>Admin note</b><span>{vendor.rejectionReason}</span></div>}

          <section className="portal-panel account-settings-card">
            <div className="account-section-heading"><div><h3>Settings & policies</h3><p>Manage your account, privacy and session</p></div><Settings size={18}/></div>
            <button className="account-action-row" onClick={()=>setEditingProfile(true)}>
              <span><Settings size={17}/></span><div><b>Account settings</b><small>Update your name, email and profile photo</small></div><ChevronRight size={16}/>
            </button>
            <button className="account-action-row" onClick={()=>window.open('/privacy-policy','_blank','noopener,noreferrer')}>
              <span><FileText size={17}/></span><div><b>Privacy Policy</b><small>How mPay collects and uses your information</small></div><ChevronRight size={16}/>
            </button>
            <button className="account-action-row danger" onClick={()=>{setDeletePassword('');setDeleteConfirmation('');setShowDeleteDialog(true);}}>
              <span><Trash2 size={17}/></span><div><b>Delete account</b><small>Permanently delete your account and associated personal data</small></div><ChevronRight size={16}/>
            </button>
          </section>

          <button className="account-logout-button" onClick={logout}><LogOut size={17}/> Logout</button>
        </div>}

        {accountSection==='vendor' && !vendorVerified && <section className="portal-panel vendor-onboarding-page">
          <div className="vendor-page-header">
            <button className="icon-btn" onClick={()=>setAccountSection('profile')}><ChevronLeft size={18}/></button>
            <div><span className="account-eyebrow">RENTAL PARTNER</span><h2>Become a rental partner</h2><p>{vendorStatus==='REJECTED' ? 'Let’s fix the reviewed details and resubmit.' : 'A simple profile is all we need to get your fleet reviewed.'}</p></div>
          </div>

          {vendorStatus==='PENDING' ? (
            <div className="vendor-pending-card">
              <span><CheckCircle2 size={24}/></span>
              <div><h3>You’re almost there</h3><p>Your vendor profile is with admin for verification.</p><small>Once verified, you can add vehicles and manage their availability.</small></div>
            </div>
          ) : <>
            <div className="vendor-onboarding-intro">
              <div><b>Start earning from your car</b><span>List chauffeur-driven vehicles, choose when they are available, and track bookings from one place.</span></div>
              <div className="vendor-step-chips"><span>Profile</span><span>Admin review</span><span>Add vehicles</span></div>
            </div>

            {vendorStatus==='REJECTED' && vendor?.rejectionReason && <div className="vendor-rejection-card"><b>Admin note</b><span>{vendor.rejectionReason}</span></div>}

            <section className="vendor-form-card vendor-dark-type">
              <div className="vendor-form-section-head"><div><b>Partner type</b><span>Choose the identity used for your vendor application.</span></div></div>
              <div className="vendor-type-pills">
                {(['INDIVIDUAL','BUSINESS'] as const).map(type=><button key={type} className={vendorForm.vendorType===type?'selected':''} onClick={()=>setVendorForm({...vendorForm,vendorType:type})}><b>{type==='INDIVIDUAL'?'Individual':'Business'}</b></button>)}
              </div>
            </section>

            <section className="vendor-form-card">
              <div className="vendor-form-section-head"><div><b>Your profile</b><span>Identity and base marketplace location</span></div><UserRound size={18}/></div>
              <div className="vendor-form-grid">
                <label>Full name<input value={vendorForm.fullName} onChange={e=>setVendorForm({...vendorForm,fullName:e.target.value.slice(0,120)})}/>{vendorSubmitAttempted&&!vendorForm.fullName.trim()&&<small className="field-error">Full name is required</small>}</label>
                <label>Fleet / business name <em>(optional)</em><input value={vendorForm.businessName} onChange={e=>setVendorForm({...vendorForm,businessName:e.target.value.slice(0,120)})}/></label>
                <label className="vendor-field-wide">Address<input value={vendorForm.address} onChange={e=>setVendorForm({...vendorForm,address:e.target.value.slice(0,300)})}/>{vendorSubmitAttempted&&!vendorForm.address.trim()&&<small className="field-error">Address is required</small>}</label>
                <label>City<input value={vendorForm.city} onChange={e=>setVendorForm({...vendorForm,city:e.target.value.slice(0,100)})}/>{vendorSubmitAttempted&&!vendorForm.city.trim()&&<small className="field-error">City is required</small>}</label>
                <label>State<input value={vendorForm.state} onChange={e=>setVendorForm({...vendorForm,state:e.target.value.slice(0,100)})}/>{vendorSubmitAttempted&&!vendorForm.state.trim()&&<small className="field-error">State is required</small>}</label>
                <label>PIN code<input inputMode="numeric" value={vendorForm.pinCode} onChange={e=>setVendorForm({...vendorForm,pinCode:e.target.value.replace(/\D/g,'').slice(0,10)})}/>{vendorSubmitAttempted&&!vendorForm.pinCode.trim()&&<small className="field-error">PIN code is required</small>}</label>
                <label>PAN <em>(optional)</em><input value={vendorForm.panNumber} onChange={e=>setVendorForm({...vendorForm,panNumber:e.target.value.toUpperCase().slice(0,20)})}/></label>
              </div>
            </section>

            <section className="vendor-form-card">
              <div className="vendor-form-section-head"><div><b>Payout details</b><span>Add UPI or bank details and choose which one is primary for payouts.</span></div><Banknote size={18}/></div>
              <div className="vendor-form-grid">
                <label>Payout UPI <em>(optional)</em><input value={vendorForm.payoutUpiId} onChange={e=>setVendorForm({...vendorForm,payoutUpiId:e.target.value.slice(0,120)})}/></label>
                <label>Bank name<input value={vendorForm.bankName} onChange={e=>setVendorForm({...vendorForm,bankName:e.target.value.slice(0,120)})}/></label>
                <label>Bank account<input value={vendorForm.bankAccountNumber} onChange={e=>setVendorForm({...vendorForm,bankAccountNumber:e.target.value.replace(/\D/g,'').slice(0,30)})}/></label>
                <label>Bank IFSC<input value={vendorForm.bankIfsc} onChange={e=>setVendorForm({...vendorForm,bankIfsc:e.target.value.toUpperCase().slice(0,20)})}/></label>
              </div>
              <div className="vendor-primary-payout">
                <span>Primary payout method</span>
                <button disabled={!vendorForm.payoutUpiId.trim()} className={vendorForm.payoutPrimaryMethod==='UPI'?'selected':''} onClick={()=>setVendorForm({...vendorForm,payoutPrimaryMethod:'UPI'})}>UPI</button>
                <button disabled={!vendorForm.bankAccountNumber.trim()||!vendorForm.bankIfsc.trim()} className={vendorForm.payoutPrimaryMethod==='BANK'?'selected':''} onClick={()=>setVendorForm({...vendorForm,payoutPrimaryMethod:'BANK'})}>Bank</button>
              </div>
              {vendorSubmitAttempted && validateVendorForm() && <div className="vendor-form-error">{validateVendorForm()}</div>}
            </section>

            <div className="vendor-form-actions">
              <button className="landing-secondary" onClick={()=>setAccountSection('profile')} disabled={busy}>Cancel</button>
              <button className="landing-primary" disabled={busy} onClick={saveVendor}>
                {busy ? 'Submitting…' : vendorStatus==='REJECTED' ? 'Resubmit for verification' : 'Start vendor verification'}
              </button>
            </div>
          </>}
        </section>}

        {accountSection==='vendor' && vendorVerified && <section className="portal-panel vendor-studio-page">
          <div className="vendor-studio-hero">
            <button className="icon-btn vendor-back-dark" onClick={()=>setAccountSection('profile')}><ChevronLeft size={18}/></button>
            <div className="vendor-studio-hero-copy"><span>MOBILITY PARTNER</span><h2>Vendor Studio</h2><p>Fleet, payouts, earnings and availability</p></div>
          </div>

          <section className="vendor-business-card">
            <div className="vendor-business-head">
              <div className="vendor-business-icon"><UserRound size={20}/></div>
              <div><h3>Business profile</h3><p>Business identity & marketplace status</p></div>
              <span className="status-pill status-success">VERIFIED</span>
              <button className="landing-secondary compact" onClick={()=>resetVendorForm(vendor!)}><Edit3 size={14}/> Edit</button>
            </div>
            <div className="vendor-business-body">
              <div><span>Owner</span><b>{vendor?.fullName || '—'}</b>{vendor?.businessName&&<small>{vendor.businessName}</small>}</div>
              <div className="vendor-vehicle-count"><b>{vendor?.vehicleCount ?? vendorVehicles.length}</b><span>Vehicles</span></div>
              <div className="vendor-location-line"><MapPin size={15}/>{[vendor?.city,vendor?.state].filter(Boolean).join(', ')||'Location unavailable'}</div>
            </div>
            <div className="vendor-payout-summary">
              <b>Payout details</b>
              <div>
                <span>Bank</span><strong>{vendor?.bankName||'—'}</strong>
                <span>Account</span><strong>{vendor?.bankAccountNumber||'—'}</strong>
                <span>IFSC</span><strong>{vendor?.bankIfsc||'—'}</strong>
                <span>UPI</span><strong>{vendor?.payoutUpiId||'—'}</strong>
                <span>Primary</span><strong>{vendor?.payoutPrimaryMethod||'—'}</strong>
              </div>
            </div>
          </section>

          <section className="vendor-earnings-section">
            <div className="vendor-section-title">
              <div><h3>Rental earnings</h3><p>Rental payout overview — separate from recharge commission earnings.</p></div>
            </div>
            {!vendorEarnings ? (
              <div className="vendor-loading-card">Loading payout summary…<div className="home-earnings-progress"><i/></div></div>
            ) : (
              <div className="vendor-earnings-periods">
                <article className="vendor-earnings-period">
                  <h4>Today</h4>
                  <div className="vendor-earnings-metrics">
                    <div><span>Gross</span><b>{money(vendorEarnings.today.grossAmount)}</b></div>
                    <div><span>Platform fee</span><b className="amount-debit">{money(vendorEarnings.today.platformFeeAmount)}</b></div>
                    <div><span>Net earning</span><b className="amount-credit">{money(vendorEarnings.today.vendorNetAmount)}</b></div>
                  </div>
                  <div className="vendor-earnings-foot"><span>Completed today</span><b>{vendorEarnings.today.completedBookingCount}</b></div>
                  <div className="vendor-earnings-foot"><span>Upcoming confirmed bookings</span><b>{vendorEarnings.upcomingBookingCount}</b></div>
                </article>
                <article className="vendor-earnings-period">
                  <h4>This month</h4>
                  <div className="vendor-earnings-metrics">
                    <div><span>Gross</span><b>{money(vendorEarnings.monthly.grossAmount)}</b></div>
                    <div><span>Platform fee</span><b className="amount-debit">{money(vendorEarnings.monthly.platformFeeAmount)}</b></div>
                    <div><span>Net earning</span><b className="amount-credit">{money(vendorEarnings.monthly.vendorNetAmount)}</b></div>
                  </div>
                  <div className="vendor-earnings-foot"><span>Bookings this month</span><b>{vendorEarnings.monthly.bookingCount}</b></div>
                </article>
              </div>
            )}
          </section>

          <section className="vendor-vehicles-section">
            <div className="vendor-section-title">
              <div><h3>My vehicles</h3><p>See exactly when each vehicle is booked, off market or available.</p></div>
              <button className="landing-secondary compact" onClick={()=>loadAccountData()} disabled={busy}><RefreshCw size={14}/> Refresh</button>
            </div>

            {vendorVehicles.length===0 ? (
              <div className="vendor-no-vehicle">
                <Car size={25}/><b>No vehicle added yet</b>
                <span>Add your first chauffeur-driven car to begin the admin review process.</span>
                <button className="landing-primary" onClick={()=>resetVehicleForm()}><Plus size={15}/> Add vehicle</button>
              </div>
            ) : (
              <div className="vendor-vehicle-grid-android">
                {vendorVehicles.map((car, carIndex)=>{
                  const status=String(car.approvalStatus||'PENDING_REVIEW').toUpperCase();
                  const blackouts=vehicleUnavailability.filter(u=>u.carId===car.id);
                  const today=localDate();
                  const activeBlackout=blackouts.find(u=>u.startDate<=today && u.endDate>=today);
                  const scheduledBlackout=blackouts.find(u=>u.startDate>today);
                  const displayedBlackout=activeBlackout||scheduledBlackout;
                  const statusTone=status==='APPROVED'?'success':status==='REJECTED'?'failed':'pending';
                  const secondaryLabel=activeBlackout?'OFF MARKET':scheduledBlackout?'SCHEDULED':'';

                  return (
                    <article className="vendor-vehicle-android-card" key={car.id} onClick={()=>setSelectedVendorVehicle(car)}>
                      <div className="vendor-vehicle-gallery-main">
                        <VehicleFourPhotoGallery car={car} priority={carIndex < 2} />
                        <span className="vendor-vehicle-category">{car.category}</span>
                      </div>
                      <div className="vendor-vehicle-card-content">
                        <h4>{car.name}</h4>
                        <p>{car.category} • {car.seats} seats</p>
                        <div className="vendor-driver-inline">
                          <span>{car.driverName}</span>
                          <small>{car.driverMobile||'—'}</small>
                          {car.driverPhotoUrl&&<img src={car.driverPhotoUrl.startsWith('http')?car.driverPhotoUrl:base+car.driverPhotoUrl} alt=""/>}
                        </div>
                        <div className="vendor-vehicle-badges">
                          <span className={'status-pill status-'+statusTone}>{status.replace(/_/g,' ')}</span>
                          {secondaryLabel&&<span className="vendor-secondary-badge">{secondaryLabel}</span>}
                        </div>
                        <b className="vendor-price">{money(car.pricePerDay)}/day</b>
                        <small className="vendor-fuel-note">Fuel expense paid by client</small>
                        <small className="vendor-spec-line">{car.transmission} • {car.fuelType||'Fuel'}</small>
                        {displayedBlackout&&<span className="vendor-blackout-period">{displayedBlackout.startDate} → {displayedBlackout.endDate}</span>}

                        <div className="vendor-vehicle-actions" onClick={e=>e.stopPropagation()}>
                          <button className="landing-secondary compact" onClick={()=>{
                            setSelectedVendorVehicle(car);
                            setCalendarCarId(car.id);
                            setCalendarMonth(localYearMonth());
                          }}>Calendar</button>

                          {displayedBlackout ? (
                            <button className="landing-secondary compact success" disabled={busy} onClick={()=>restoreOffMarket(car.id,displayedBlackout.id)}>Restore</button>
                          ) : status==='APPROVED' ? (
                            <button className="landing-primary compact" disabled={busy} onClick={()=>{
                              const tomorrow=localDate(new Date(Date.now()+86400000));
                              setUnavailabilityForm({reasonCode:'SERVICE_MAINTENANCE',reasonNote:'',startDate:tomorrow,endDate:tomorrow});
                              setSelectedVendorVehicle(car);
                              setOffMarketOpenId(car.id);
                            }}>Off market</button>
                          ) : (
                            <button className="landing-secondary compact" onClick={()=>resetVehicleForm(car)}>
                              {status==='REJECTED'?'Correct & resubmit':'Edit details'}
                            </button>
                          )}
                        </div>

                        {status!=='APPROVED'||activeBlackout ? (
                          <button className="landing-secondary vendor-edit-full" onClick={e=>{e.stopPropagation();resetVehicleForm(car)}}>
                            {status==='REJECTED'?'Correct & resubmit':status==='APPROVED'?'Edit details (off market)':'Edit details'}
                          </button>
                        ) : (
                          <small className="vendor-edit-lock">Approved and on market — editing is available only while this vehicle is off market.</small>
                        )}

                        {car.rejectionReason&&<small className="vendor-review-note">Review: {car.rejectionReason}</small>}
                      </div>
                    </article>
                  );
                })}
              </div>
            )}

            {vendorVehicles.length>0 && <button className="landing-secondary vendor-add-another" onClick={()=>resetVehicleForm()}><Plus size={15}/> Add another vehicle</button>}
          </section>

          <section className="vendor-payouts-section">
            <div className="vendor-section-title"><div><h3>Payout history</h3></div><Banknote size={18}/></div>
            {vendorPayouts.length ? (
              <div className="vendor-payout-list">
                {vendorPayouts.map(p=>(
                  <div className="vendor-payout-row" key={p.payoutId}>
                    <div><b>{p.carName}</b><span>{p.bookingId} · {dt(p.createdAt)} · Platform fee {Number(p.platformFeePercent).toFixed(2)}%</span></div>
                    <strong className="amount-credit">{money(p.vendorNetAmount)}</strong>
                    <span className={statusClass(p.status)}>{String(p.status).toUpperCase()}</span>
                  </div>
                ))}
              </div>
            ) : <div className="vendor-no-payouts">No vendor payouts yet.</div>}
          </section>
        </section>}

        {accountSection==='vehicle' && <section className="portal-panel vehicle-onboarding-page">
          <div className="vehicle-page-header">
            <button className="icon-btn" onClick={()=>{setAccountSection('vendor');setVehicleSubmitAttempted(false)}} disabled={busy}><ChevronLeft size={18}/></button>
            <div><span className="account-eyebrow">FLEET</span><h2>{vehicleEditId?'Correct vehicle details':'Add your vehicle'}</h2><p>{vehicleEditId?'Update the rejected details and resubmit for review.':'Two quick sections: vehicle details first, then the assigned driver.'}</p></div>
          </div>

          <section className="vehicle-form-card">
            <div className="vehicle-form-step-head"><span>1</span><div><b>Vehicle details</b><small>Identity, specifications and daily price</small></div></div>
            <div className="vehicle-form-grid-web">
              <label>Vehicle name<input value={vehicleForm.name} onChange={e=>setVehicleForm({...vehicleForm,name:sanitizeVehicleAlphaNumeric(e.target.value,120)})}/>{vehicleSubmitAttempted&&!vehicleForm.name.trim()&&<small className="field-error">Vehicle name is required</small>}</label>
              <label>Make<input value={vehicleForm.make} onChange={e=>setVehicleForm({...vehicleForm,make:sanitizeVehicleAlphaNumeric(e.target.value,80)})}/>{vehicleSubmitAttempted&&!vehicleForm.make.trim()&&<small className="field-error">Make is required</small>}</label>
              <label>Model<input value={vehicleForm.model} onChange={e=>setVehicleForm({...vehicleForm,model:sanitizeVehicleAlphaNumeric(e.target.value,80)})}/>{vehicleSubmitAttempted&&!vehicleForm.model.trim()&&<small className="field-error">Model is required</small>}</label>
              <label>Variant <em>(optional)</em><input value={vehicleForm.variant} onChange={e=>setVehicleForm({...vehicleForm,variant:sanitizeVehicleAlphaNumeric(e.target.value,80)})}/></label>
              <label>Category<select value={vehicleForm.category} onChange={e=>setVehicleForm({...vehicleForm,category:e.target.value})}>{['Sedan','SUV','Hatchback','MUV','Luxury','Other'].map(x=><option key={x}>{x}</option>)}</select></label>
              <label>Seats<select value={String(vehicleForm.seats)} onChange={e=>setVehicleForm({...vehicleForm,seats:Number(e.target.value)})}>{Array.from({length:7},(_,i)=>i+2).map(x=><option key={x}>{x}</option>)}</select></label>
              <label>Transmission<select value={vehicleForm.transmission} onChange={e=>setVehicleForm({...vehicleForm,transmission:e.target.value})}>{['Automatic','Manual'].map(x=><option key={x}>{x}</option>)}</select></label>
              <label>Fuel type<select value={vehicleForm.fuelType} onChange={e=>setVehicleForm({...vehicleForm,fuelType:e.target.value})}>{['Petrol','Diesel','CNG','Electric','Hybrid','Other'].map(x=><option key={x}>{x}</option>)}</select></label>
              <label>Manufacturing year<select value={String(vehicleForm.manufacturingYear||'')} onChange={e=>{const year=Number(e.target.value);setVehicleForm(v=>({...v,manufacturingYear:year,registrationYear:Number(v.registrationYear)<year?year:v.registrationYear}));}}>
                <option value="">Select</option>{Array.from({length:21},(_,i)=>new Date().getFullYear()-20+i).map(x=><option key={x}>{x}</option>)}</select></label>
              <label>Registration year<select value={String(vehicleForm.registrationYear||'')} onChange={e=>setVehicleForm({...vehicleForm,registrationYear:Number(e.target.value)})}>
                <option value="">Select</option>{Array.from({length:Math.max(1,new Date().getFullYear()-Number(vehicleForm.manufacturingYear||new Date().getFullYear()-20)+1)},(_,i)=>Number(vehicleForm.manufacturingYear||new Date().getFullYear()-20)+i).map(x=><option key={x}>{x}</option>)}</select></label>
              <label>Registration number<input value={vehicleForm.registrationNumber} onChange={e=>setVehicleForm({...vehicleForm,registrationNumber:sanitizeRegistration(e.target.value)})}/>{vehicleSubmitAttempted&&!vehicleForm.registrationNumber.trim()&&<small className="field-error">Registration number is required</small>}</label>
              <label>City<input value={vehicleForm.city} onChange={e=>setVehicleForm({...vehicleForm,city:sanitizeVehicleAlphaNumeric(e.target.value,100)})}/>{vehicleSubmitAttempted&&!vehicleForm.city.trim()&&<small className="field-error">City is required</small>}</label>
              <label>State<select value={vehicleForm.state} onChange={e=>setVehicleForm({...vehicleForm,state:e.target.value})}><option value="">Select state</option>{indianStatesAndUt.map(x=><option key={x}>{x}</option>)}</select>{vehicleSubmitAttempted&&!vehicleForm.state.trim()&&<small className="field-error">State is required</small>}</label>
              <label>Price per day (₹)<input inputMode="decimal" value={vehicleForm.pricePerDay} onChange={e=>setVehicleForm({...vehicleForm,pricePerDay:sanitizeDecimal(e.target.value)})}/>{vehicleSubmitAttempted && validateVehicleForm().includes('price')&&<small className="field-error">Enter a valid positive price with up to 2 decimals</small>}<small>Price charged per 24-hour rental day.</small></label>
            </div>
            <div className="vehicle-form-helper">Vehicle age is limited to 20 years; registration year cannot be before manufacture year.</div>
          </section>

          <section className="vehicle-form-card">
            <div className="vehicle-form-step-head"><span>2</span><div><b>Vehicle photos</b><small>All four photos are mandatory</small></div></div>
            <p className="vehicle-form-description">Use an image URL or a photo from your device for each slot. Device photos must be JPG, PNG or WebP and 5 MB or smaller.</p>
            <div className="vehicle-photo-form-grid">
              {['Front photo','Side photo','Rear photo','Interior photo'].map((label,slot)=>{
                const selectedFile=vehiclePhotoFiles[slot];
                const preview=vehiclePhotoPreviews[slot];
                const rawUrl=vehiclePhotoUrls[slot];
                const complete=Boolean(selectedFile || rawUrl.trim());
                return <div className={'vehicle-photo-form-card '+(complete?'complete':'incomplete')} key={label}>
                  <div className="vehicle-photo-preview">{preview ? <img src={preview} alt={label}/> : <Car size={24}/>}</div>
                  <b>{label}</b>
                  {selectedFile ? <><small>Photo selected from device</small><button type="button" className="landing-secondary compact" onClick={()=>{setVehiclePhotoFiles(v=>{const n=[...v];n[slot]=null;return n});setVehiclePhotoPreviews(v=>{const n=[...v];n[slot]='';return n})}}>Use image URL instead</button></> :
                  <><input value={rawUrl} placeholder={label+' image URL'} onChange={e=>setVehiclePhotoUrl(slot,e.target.value)}/><label className="photo-file-button landing-secondary compact"><Camera size={13}/> Choose from device<input type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={e=>handleVehiclePhotoSelection(slot,e.target.files?.[0]||null)}/></label></>}
                  {vehicleSubmitAttempted&&!complete&&<small className="field-error">This photo is required</small>}
                </div>
              })}
            </div>
            {vehicleSubmitAttempted && validateVehicleForm().includes('photos') && <div className="vendor-form-error">Front, side, rear and interior vehicle photos are required.</div>}
          </section>

          <section className="vehicle-form-card">
            <div className="vehicle-form-step-head"><span>3</span><div><b>Driver details</b><small>The chauffeur assigned to this vehicle</small></div></div>
            <div className="driver-form-photo-row">
              <div className="driver-photo-preview">{driverPhotoPreview?<img src={driverPhotoPreview} alt="Driver"/>:<UserRound size={25}/>}</div>
              <div><b>Driver photo</b><small>Passport-style square photo</small><label className="photo-file-button landing-secondary compact"><Camera size={13}/> {driverPhotoFile?'Change':'Add'}<input type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={e=>handleDriverPhotoSelection(e.target.files?.[0]||null)}/></label></div>
            </div>
            <div className="vehicle-form-grid-web">
              <label>Driver full name<input value={vehicleForm.driver.fullName} onChange={e=>setVehicleForm({...vehicleForm,driver:{...vehicleForm.driver,fullName:sanitizeVehicleAlphaNumeric(e.target.value,120)}})}/>{vehicleSubmitAttempted&&!vehicleForm.driver.fullName.trim()&&<small className="field-error">Driver name is required</small>}</label>
              <label>Driver mobile<input inputMode="numeric" value={vehicleForm.driver.mobile} onChange={e=>setVehicleForm({...vehicleForm,driver:{...vehicleForm.driver,mobile:normalizeIndianMobile(e.target.value)}})}/>{vehicleSubmitAttempted&&!/^\d{10}$/.test(normalizeIndianMobile(vehicleForm.driver.mobile))&&<small className="field-error">Enter exactly 10 digits</small>}</label>
              <label>Driving licence no.<input value={vehicleForm.driver.licenseNumber} onChange={e=>setVehicleForm({...vehicleForm,driver:{...vehicleForm.driver,licenseNumber:sanitizeLicense(e.target.value)}})}/>{vehicleSubmitAttempted&&!vehicleForm.driver.licenseNumber.trim()&&<small className="field-error">Driving licence number is required</small>}</label>
              <label>Licence expiry<input type="date" min={localDate(new Date(Date.now()+86400000))} value={vehicleForm.driver.licenseExpiry} onChange={e=>setVehicleForm({...vehicleForm,driver:{...vehicleForm.driver,licenseExpiry:e.target.value}})}/>{vehicleSubmitAttempted && (!vehicleForm.driver.licenseExpiry || new Date(vehicleForm.driver.licenseExpiry+'T00:00:00')<=new Date(new Date().toDateString()))&&<small className="field-error">Licence expiry must be a future date</small>}</label>
              <label className="vendor-field-wide">Driver address <em>(optional)</em><input value={vehicleForm.driver.address} onChange={e=>setVehicleForm({...vehicleForm,driver:{...vehicleForm.driver,address:e.target.value.slice(0,300)}})}/></label>
            </div>
          </section>

          <div className="vehicle-form-note">All vehicle details and the assigned chauffeur are reviewed before the car appears in the customer marketplace.</div>
          {notice && <div className="vendor-form-error">{notice}</div>}
          <div className="vendor-form-actions">
            <button className="landing-secondary" onClick={()=>{setAccountSection('vendor');setVehicleSubmitAttempted(false)}} disabled={busy}>Cancel</button>
            <button className="landing-primary" disabled={busy} onClick={saveVehicle}>{busy ? 'Submitting…' : vehicleEditId ? 'Resubmit vehicle for review' : 'Submit vehicle for review'}</button>
          </div>
        </section>}

        {editingProfile && <div className="modal-backdrop" onClick={()=>!busy&&setEditingProfile(false)}>
          <div className="portal-modal small-modal profile-edit-modal" onClick={e=>e.stopPropagation()}>
            <div className="panel-head"><div><h2>Edit profile</h2><p>Update your name, email and profile photo.</p></div><button className="icon-btn" onClick={()=>!busy&&setEditingProfile(false)}><X size={17}/></button></div>
            <div className="profile-edit-preview">
              <div className="account-profile-avatar">
                {pendingProfileImagePreview ? <img src={pendingProfileImagePreview} alt="Profile"/> : profileImage ? <img src={profileImage} alt="Profile"/> : (me?.name || 'U').charAt(0).toUpperCase()}
              </div>
              <div><label className="photo-file-button landing-secondary compact"><Camera size={13}/> {pendingProfileImageFile?'Change':'Change photo'}<input type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={e=>chooseProfileImage(e.target.files?.[0]||null)}/></label>{profileImage&&<button className="text-danger-btn" onClick={removeProfileImage} disabled={busy}>Remove</button>}</div>
            </div>
            <label className="account-modal-field">Full name<input value={profileForm.name} onChange={e=>setProfileForm({...profileForm,name:e.target.value.slice(0,120)})}/></label>
            <label className="account-modal-field">Email<input type="email" value={profileForm.email} onChange={e=>setProfileForm({...profileForm,email:e.target.value.slice(0,254)})}/></label>
            <div className="form-actions"><button className="landing-secondary" onClick={()=>{
              if(pendingProfileImagePreview.startsWith('blob:')) URL.revokeObjectURL(pendingProfileImagePreview);
              setPendingProfileImageFile(null);
              setPendingProfileImagePreview('');
              setEditingProfile(false);
            }} disabled={busy}>Cancel</button><button className="landing-primary" onClick={async()=>{await saveProfile();setEditingProfile(false)}} disabled={busy}>{busy?'Saving…':'Save'}</button></div>
          </div>
        </div>}

        {showDeleteDialog && <div className="modal-backdrop" onClick={()=>!deletingAccount&&setShowDeleteDialog(false)}>
          <div className="portal-modal small-modal account-delete-modal" onClick={e=>e.stopPropagation()}>
            <div className="panel-head"><div><h2>Delete mPay account</h2><p>This permanently signs you out and removes or redacts personal account data. Financial records required for reconciliation or legal compliance may be retained in redacted form.</p></div><button className="icon-btn" onClick={()=>!deletingAccount&&setShowDeleteDialog(false)}><X size={17}/></button></div>
            <label className="account-modal-field">Current password<input type="password" value={deletePassword} onChange={e=>setDeletePassword(e.target.value)}/></label>
            <label className="account-modal-field">Type DELETE to confirm<input value={deleteConfirmation} onChange={e=>setDeleteConfirmation(e.target.value.toUpperCase().slice(0,6))}/></label>
            <div className="form-actions"><button className="landing-secondary" onClick={()=>setShowDeleteDialog(false)} disabled={deletingAccount}>Cancel</button><button className="text-danger-btn delete-confirm-button" onClick={deleteAccount} disabled={deletingAccount||!deletePassword.trim()||deleteConfirmation!=='DELETE'}>{deletingAccount?'Deleting account…':'Delete account'}</button></div>
          </div>
        </div>}

        {showVendorForm && vendorVerified && <div className="modal-backdrop" onClick={()=>!busy&&setShowVendorForm(false)}>
          <div className="portal-modal vendor-profile-edit-modal" onClick={e=>e.stopPropagation()}>
            <div className="panel-head"><div><h2>Edit vendor profile</h2><p>Update identity and payout preferences without leaving Vendor Studio.</p></div><button className="icon-btn" onClick={()=>!busy&&setShowVendorForm(false)}><X size={17}/></button></div>
            <div className="vendor-form-grid">
              <label>Full name<input value={vendorForm.fullName} onChange={e=>setVendorForm({...vendorForm,fullName:e.target.value.slice(0,120)})}/></label>
              <label>Business name<input value={vendorForm.businessName} onChange={e=>setVendorForm({...vendorForm,businessName:e.target.value.slice(0,120)})}/></label>
              <label className="vendor-field-wide">Address<input value={vendorForm.address} onChange={e=>setVendorForm({...vendorForm,address:e.target.value.slice(0,300)})}/></label>
              <label>City<input value={vendorForm.city} onChange={e=>setVendorForm({...vendorForm,city:e.target.value.slice(0,100)})}/></label>
              <label>State<input value={vendorForm.state} onChange={e=>setVendorForm({...vendorForm,state:e.target.value.slice(0,100)})}/></label>
              <label>PIN<input value={vendorForm.pinCode} onChange={e=>setVendorForm({...vendorForm,pinCode:e.target.value.replace(/\D/g,'').slice(0,10)})}/></label>
              <label>PAN<input value={vendorForm.panNumber} onChange={e=>setVendorForm({...vendorForm,panNumber:e.target.value.toUpperCase().slice(0,20)})}/></label>
              <label>Bank name<input value={vendorForm.bankName} onChange={e=>setVendorForm({...vendorForm,bankName:e.target.value.slice(0,120)})}/></label>
              <label>Bank account<input value={vendorForm.bankAccountNumber} onChange={e=>setVendorForm({...vendorForm,bankAccountNumber:e.target.value.replace(/\D/g,'').slice(0,30)})}/></label>
              <label>IFSC<input value={vendorForm.bankIfsc} onChange={e=>setVendorForm({...vendorForm,bankIfsc:e.target.value.toUpperCase().slice(0,20)})}/></label>
              <label>UPI ID<input value={vendorForm.payoutUpiId} onChange={e=>setVendorForm({...vendorForm,payoutUpiId:e.target.value.slice(0,120)})}/></label>
            </div>
            <div className="vendor-primary-payout"><span>Primary payout method</span><button disabled={!vendorForm.payoutUpiId.trim()} className={vendorForm.payoutPrimaryMethod==='UPI'?'selected':''} onClick={()=>setVendorForm({...vendorForm,payoutPrimaryMethod:'UPI'})}>UPI</button><button disabled={!vendorForm.bankAccountNumber.trim()||!vendorForm.bankIfsc.trim()} className={vendorForm.payoutPrimaryMethod==='BANK'?'selected':''} onClick={()=>setVendorForm({...vendorForm,payoutPrimaryMethod:'BANK'})}>Bank</button></div>
            {vendorSubmitAttempted&&validateVendorForm()&&<div className="vendor-form-error">{validateVendorForm()}</div>}
            <div className="form-actions"><button className="landing-secondary" onClick={()=>setShowVendorForm(false)} disabled={busy}>Cancel</button><button className="landing-primary" onClick={saveVendor} disabled={busy}>{busy?'Saving…':'Save changes'}</button></div>
          </div>
        </div>}

        {selectedVendorVehicle && accountSection==='vendor' && <div className="modal-backdrop" onClick={()=>setSelectedVendorVehicle(undefined)}>
          <div className="portal-modal vendor-vehicle-details-modal" onClick={e=>e.stopPropagation()}>
            <div className="panel-head"><div><span className="account-eyebrow">VEHICLE DETAILS</span><h2>{selectedVendorVehicle.name}</h2><p>{[selectedVendorVehicle.make,selectedVendorVehicle.model,selectedVendorVehicle.variant].filter(Boolean).join(' ')||selectedVendorVehicle.category}</p></div><button className="icon-btn" onClick={()=>setSelectedVendorVehicle(undefined)}><X size={17}/></button></div>
            <div className="vehicle-gallery">{[0,1,2,3].map(slot=>{const src=imageFromCar(selectedVendorVehicle,slot,'large');return <div className="vehicle-gallery-slot" key={slot}>{src?<img src={src} alt={'Vehicle '+(slot+1)} loading="eager" decoding="async" fetchPriority="high"/>:<span>Photo {slot+1}</span>}</div>})}</div>
            <div className="vendor-detail-status-row"><span className={statusClass(selectedVendorVehicle.approvalStatus)}>{String(selectedVendorVehicle.approvalStatus||'PENDING').toUpperCase()}</span>{selectedVendorVehicle.rejectionReason&&<span className="vendor-review-note">{selectedVendorVehicle.rejectionReason}</span>}</div>
            <div className="detail-grid-web"><span>Make / model <b>{[selectedVendorVehicle.make,selectedVendorVehicle.model,selectedVendorVehicle.variant].filter(Boolean).join(' ')||'—'}</b></span><span>Category / seats <b>{selectedVendorVehicle.category} / {selectedVendorVehicle.seats}</b></span><span>Transmission / fuel <b>{selectedVendorVehicle.transmission} / {selectedVendorVehicle.fuelType||'—'}</b></span><span>Manufacturing year <b>{selectedVendorVehicle.manufacturingYear||'—'}</b></span><span>Registration year <b>{selectedVendorVehicle.registrationYear||'—'}</b></span><span>Price per day <b>{money(selectedVendorVehicle.pricePerDay)}</b></span><span>Registration number <b>{selectedVendorVehicle.registrationNumber||'—'}</b></span><span>Pickup address <b>{selectedVendorVehicle.pickupAddress||'—'}</b></span><span>City / state <b>{selectedVendorVehicle.city||'—'} / {selectedVendorVehicle.state||'—'}</b></span></div>
            <div className="driver-profile-card"><div className="driver-profile-photo">{selectedVendorVehicle.driverPhotoUrl?<img src={selectedVendorVehicle.driverPhotoUrl.startsWith('http')?selectedVendorVehicle.driverPhotoUrl:base+selectedVendorVehicle.driverPhotoUrl} alt="Driver"/>:<UserRound size={22}/>}</div><div><b>{selectedVendorVehicle.driverName||'Driver'}</b><span>{selectedVendorVehicle.driverMobile||'Mobile not provided'}</span><small>{selectedVendorVehicle.driverLicenseNumber||'Licence not provided'}</small><small>{selectedVendorVehicle.driverLicenseExpiry?date(selectedVendorVehicle.driverLicenseExpiry):'Licence expiry not provided'}</small></div></div>
            <div className="form-actions"><button className="landing-secondary" onClick={()=>setSelectedVendorVehicle(undefined)}>Close details</button>{String(selectedVendorVehicle.approvalStatus||'').toUpperCase()!=='APPROVED' || vendorVehicles.some(x=>x.id===selectedVendorVehicle.id && vehicleUnavailability.some(u=>u.carId===x.id && u.startDate<=localDate() && u.endDate>=localDate())) ? <button className="landing-primary" onClick={()=>{const car=selectedVendorVehicle;setSelectedVendorVehicle(undefined);resetVehicleForm(car)}}>Edit details</button>:null}</div>
          </div>
        </div>}

        {offMarketOpenId && selectedVendorVehicle && <div className="modal-backdrop" onClick={()=>!busy&&setOffMarketOpenId('')}>
          <div className="portal-modal small-modal" onClick={e=>e.stopPropagation()}>
            <div className="panel-head"><div><h2>Take {selectedVendorVehicle.name} off market</h2><p>Customers will not see this vehicle for the selected period. The reason is stored for admin visibility.</p></div><button className="icon-btn" onClick={()=>!busy&&setOffMarketOpenId('')}><X size={17}/></button></div>
            <div className="vehicle-form-grid-web">
              <label>From<input type="date" min={localDate()} value={unavailabilityForm.startDate} onChange={e=>setUnavailabilityForm({...unavailabilityForm,startDate:e.target.value})}/></label>
              <label>To<input type="date" min={localDate()} value={unavailabilityForm.endDate} onChange={e=>setUnavailabilityForm({...unavailabilityForm,endDate:e.target.value})}/></label>
              <label className="vendor-field-wide">Reason<select value={unavailabilityForm.reasonCode} onChange={e=>setUnavailabilityForm({...unavailabilityForm,reasonCode:e.target.value})}>{rentalOffMarketReasons.map(([code,label])=><option value={code} key={code}>{label}</option>)}</select></label>
              <label className="vendor-field-wide">Optional note for admin<input value={unavailabilityForm.reasonNote} onChange={e=>setUnavailabilityForm({...unavailabilityForm,reasonNote:e.target.value.slice(0,300)})}/></label>
            </div>
            {unavailabilityForm.startDate&&unavailabilityForm.endDate&&unavailabilityForm.endDate<unavailabilityForm.startDate&&<div className="vendor-form-error">Choose a current/future period with the end date on or after the start date.</div>}
            <div className="form-actions"><button className="landing-secondary" onClick={()=>setOffMarketOpenId('')} disabled={busy}>Cancel</button><button className="landing-primary" onClick={()=>{if(!unavailabilityForm.startDate||!unavailabilityForm.endDate||unavailabilityForm.endDate<unavailabilityForm.startDate){setNotice('Choose a valid current/future period.');return;}takeVehicleOffMarket(selectedVendorVehicle.id).then(()=>setOffMarketOpenId(''))}} disabled={busy||!unavailabilityForm.startDate||!unavailabilityForm.endDate||unavailabilityForm.endDate<unavailabilityForm.startDate}>{busy?'Saving…':'Keep off market'}</button></div>
          </div>
        </div>}

        {calendarCarId && <div className="modal-backdrop" onClick={()=>setCalendarCarId('')}>
          <div className="portal-modal vendor-calendar-modal" onClick={e=>e.stopPropagation()}>
            <div className="panel-head"><div><h2>{vendorVehicles.find(c=>c.id===calendarCarId)?.name||'Vehicle calendar'}</h2><p>{new Date(Number(calendarMonth.slice(0,4)),Number(calendarMonth.slice(5,7))-1,1).toLocaleDateString('en-IN',{month:'long',year:'numeric'})}</p></div><div className="calendar-nav"><button className="icon-btn" onClick={()=>{const[y,m]=calendarMonth.split('-').map(Number);setCalendarMonth(localYearMonth(new Date(y,m-2,1)))}}><ChevronLeft size={15}/></button><button className="icon-btn" onClick={()=>{const[y,m]=calendarMonth.split('-').map(Number);setCalendarMonth(localYearMonth(new Date(y,m,1)))}}><ChevronRight size={15}/></button><button className="icon-btn" onClick={()=>setCalendarCarId('')}><X size={16}/></button></div></div>
            {vehicleCalendar.length ? <div className="vendor-calendar-grid"><div className="vendor-calendar-week">{['M','T','W','T','F','S','S'].map((x,i)=><span key={i}>{x}</span>)}</div>{Array.from({length:new Date(Number(calendarMonth.slice(0,4)),Number(calendarMonth.slice(5,7)),0).getDate()},(_,i)=>i+1).map(day=>{
              const dateValue=calendarMonth+'-'+pad2(day); const d=vehicleCalendar.find(x=>x.date===dateValue); const past=dateValue<localDate(); const st=past?'PAST':String(d?.status||'AVAILABLE').toUpperCase();
              return <span key={dateValue} className={'calendar-cell calendar-'+st.toLowerCase()}><b>{day}</b></span>;
            })}</div> : <div className="vendor-loading-card">Loading calendar…<div className="home-earnings-progress"><i/></div></div>}
            <div className="vendor-calendar-legend"><span className="booked">Booked</span><span className="off">Off market</span><span className="available">Available</span><span className="past">Past</span></div>
          </div>
        </div>}
      </section>}      {selectedWalletItem && <div className="modal-backdrop" onClick={()=>setSelectedWalletItem(undefined)}><div className="portal-modal small-modal" onClick={e=>e.stopPropagation()}><div className="panel-head"><div><h2>Wallet transaction</h2><p>{selectedWalletItem.referenceType || selectedWalletItem.type || 'Transaction'}</p></div><button className="icon-btn" onClick={()=>setSelectedWalletItem(undefined)}><X size={17}/></button></div><div className="detail-grid-web"><span>Amount <b className={walletAmountClass(selectedWalletItem)}>{walletAmountLabel(selectedWalletItem)}</b></span><span>Status <b>{selectedWalletItem.status || '—'}</b></span><span>Reference type <b>{selectedWalletItem.referenceType || '—'}</b></span><span>Reference ID <b>{selectedWalletItem.referenceId || '—'}</b></span><span>Provider <b>{selectedWalletItem.provider || '—'}</b></span><span>Created <b>{dt(selectedWalletItem.createdAt)}</b></span><span>Mobile <b>{selectedWalletItem.mobileNumber || '—'}</b></span><span>Operator <b>{selectedWalletItem.operator || '—'}</b></span><span>Circle <b>{selectedWalletItem.circle || '—'}</b></span><span>Description <b>{selectedWalletItem.description || '—'}</b></span></div>
          {selectedRechargeDetail && <div className="recharge-detail-box"><h3>Recharge details</h3><div className="detail-grid-web"><span>Transaction <b>{selectedRechargeDetail.transactionId || '—'}</b></span><span>Plan <b>{selectedRechargeDetail.planDescription || selectedRechargeDetail.planId || '—'}</b></span><span>Recharge status <b>{selectedRechargeDetail.status || '—'}</b></span><span>Provider <b>{selectedRechargeDetail.provider || '—'}</b></span><span>Mobile <b>{selectedRechargeDetail.mobileNumber || '—'}</b></span><span>Operator / circle <b>{(selectedRechargeDetail.operator || '—') + ' / ' + (selectedRechargeDetail.circle || '—')}</b></span><span>Wallet debit <b>{money(selectedRechargeDetail.walletDebitAmount)}</b></span><span>Provider reference <b>{selectedRechargeDetail.providerReference || '—'}</b></span><span>Message <b>{selectedRechargeDetail.message || '—'}</b></span></div></div>}          {selectedWithdrawalDetail && <div className="recharge-detail-box"><h3>Withdrawal details</h3><div className="detail-grid-web"><span>Withdrawal <b>{selectedWithdrawalDetail.withdrawalId || '—'}</b></span><span>Amount <b className="amount-debit">{money(selectedWithdrawalDetail.amount)}</b></span><span>UPI ID <b>{selectedWithdrawalDetail.upiId || '—'}</b></span><span>Status <b>{selectedWithdrawalDetail.status || '—'}</b></span><span>Provider <b>{selectedWithdrawalDetail.provider || '—'}</b></span><span>Provider status <b>{selectedWithdrawalDetail.providerStatus || '—'}</b></span><span>Provider reference <b>{selectedWithdrawalDetail.providerReference || '—'}</b></span><span>Wallet ledger <b>{selectedWithdrawalDetail.walletLedgerRef || '—'}</b></span><span>Failure reason <b>{selectedWithdrawalDetail.failureReason || '—'}</b></span><span>Created <b>{dt(selectedWithdrawalDetail.createdAt)}</b></span><span>Completed <b>{dt(selectedWithdrawalDetail.completedAt)}</b></span></div></div>}</div></div>}

      {rentalDetails && (() => {
        const hasDateInput = Boolean(rentalSearch.startDate || rentalSearch.endDate);
        const startMs = rentalSearch.startDate ? new Date(rentalSearch.startDate).getTime() : NaN;
        const endMs = rentalSearch.endDate ? new Date(rentalSearch.endDate).getTime() : NaN;
        const validWindow = Number.isFinite(startMs) && Number.isFinite(endMs) &&
          endMs > startMs && startMs >= Date.now();
        const bookEnabled = !hasDateInput || validWindow;
        return <div className="modal-backdrop" onClick={()=>setRentalDetails(undefined)}>
          <div className="portal-modal rental-details-modal" onClick={e=>e.stopPropagation()}>
            <div className="panel-head rental-details-head">
              <div><span className="rental-eyebrow">VEHICLE DETAILS</span><h2>{rentalDetails.name}</h2><p>{rentalDetails.category} · {rentalDetails.seats} seats · {rentalDetails.transmission}</p></div>
              <button className="icon-btn" onClick={()=>setRentalDetails(undefined)}><X size={17}/></button>
            </div>

            <div className="vehicle-gallery rental-public-gallery">
              {[0,1,2,3].map(slot=>{
                const src=imageFromCar(rentalDetails,slot,'large');
                return <div className="vehicle-gallery-slot" key={slot}>{src?<img src={src} alt={rentalDetails.name + ' ' + (slot+1)} loading="eager" decoding="async" fetchPriority="high"/>:<span>Photo {slot+1}</span>}</div>;
              })}
            </div>

            <div className="rental-details-hero">
              <div>
                <b>{rentalDetails.name}</b>
                <span>{[rentalDetails.make,rentalDetails.model,rentalDetails.variant].filter(Boolean).join(' ') || rentalDetails.category}</span>
              </div>
              <strong>{money(rentalDetails.pricePerDay)}<em>/day</em></strong>
            </div>

            <div className="rental-detail-section">
              <div className="rental-detail-section-title"><CarFront size={17}/><b>Vehicle</b></div>
              <div className="rental-detail-facts">
                <span><small>Seats</small><b>{rentalDetails.seats}</b></span>
                <span><small>Transmission</small><b>{rentalDetails.transmission}</b></span>
                <span><small>Fuel</small><b>{rentalDetails.fuelType || '—'}</b></span>
                <span><small>Location</small><b>{[rentalDetails.city,rentalDetails.state].filter(Boolean).join(', ') || '—'}</b></span>
              </div>
            </div>

            <div className="rental-public-driver-card">
              <span className="rental-driver-avatar large">{rentalDetails.driverPhotoUrl ? <img src={rentalDetails.driverPhotoUrl.startsWith('http') ? rentalDetails.driverPhotoUrl : base + rentalDetails.driverPhotoUrl} alt={rentalDetails.driverName}/> : <UserRound size={20}/>}</span>
              <div><small>Chauffeur</small><b>{rentalDetails.driverName}</b>{rentalDetails.driverMobile && <span>{rentalDetails.driverMobile}</span>}{rentalDetails.driverRating != null && <span className="rental-driver-rating">★ {Number(rentalDetails.driverRating).toFixed(1)}</span>}</div>
            </div>

            {!bookEnabled && <div className="rental-inline-error">Complete a valid future availability window before booking.</div>}
            <div className="rental-details-actions">
              <button className="landing-secondary" onClick={()=>setRentalDetails(undefined)}>Close</button>
              <button className="landing-primary" disabled={!bookEnabled} onClick={()=>openRentalBooking(rentalDetails,rentalSearch.startDate,rentalSearch.endDate)}>Book with driver <ChevronRight size={16}/></button>
            </div>
          </div>
        </div>;
      })()}
    </main>
  </div>;
}
