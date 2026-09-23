export type Role = string;
export type SortMode = 'today-high' | 'today-low' | 'month-high' | 'month-low';

export interface RoleCommissionRate {
  role: string;
  commissionPercent: number;
  active: boolean;
}

export interface CurrentUserProfile {
  userId: number;
  publicUserId: string;
  mobile: string;
  name?: string | null;
  email?: string | null;
  profileImageUrl?: string | null;
  profileImageVersion?: number | null;
  role: string;
  commissionRate: number;
  createdAt?: string | null;
  profileUpdatedAt?: string | null;
}

export interface AdminUserStatusResult {
  publicUserId: string;
  active: boolean;
  status: 'ACTIVE' | 'BLOCKED';
}

export interface UserSummary {
  id: string;
  publicUserId: string;
  name: string;
  mobile: string;
  email: string;
  role: Role;
  accountType: string;
  todayEarnings: number;
  monthEarnings: number;
  todayVolume: number;
  monthVolume: number;
  walletBalance: number;
  joinedAt: string;
  profileUpdatedAt: string;
  status: 'ACTIVE' | 'BLOCKED';
}

export interface UserDetail extends UserSummary {
  rechargeCount: number;
  addMoneyTotal: number;
  withdrawalTotal: number;
  commissionRate: number;
  balance: number;
  availableBalance: number;
  reservedBalance: number;
  profileImageUrl?: string | null;
  profileImageVersion?: number | null;
  latestRecharge?: {
    mobile: string;
    operator: string;
    amount: number;
    commission: number;
    status: string;
    createdAt: string;
    transactionId: string;
  };
  recentWalletEntries: Array<{
    id: string;
    type: 'RECHARGE' | 'ADD_MONEY' | 'WITHDRAWAL';
    amount: number;
    createdAt: string;
    reference: string;
  }>;
}

export interface LoginSession { token: string; refreshToken: string; role: Role; userId: string; name: string; permissions: string[]; }

export interface DashboardSummary {
  todayVolume: number;
  todayCommission: number;
  monthlyVolume: number;
  monthlyCommission: number;
  activeClients: number;
  successfulRecharges: number;
  totalUsers: number;
  chart: Array<{ label: string; volume: number; commission: number }>;
}

export interface RechargeHistoryItem {
  transactionId: string;
  clientRequestId: string;
  mobileNumber: string;
  operator: string;
  circle: string;
  planId: string;
  planDescription?: string | null;
  planValidity?: string | null;
  amount: number;
  walletDebitAmount: number;
  status: string;
  provider: string;
  providerReference?: string | null;
  providerOrderId?: string | null;
  walletLedgerRef?: string | null;
  completedAt?: string | null;
  clientCommission: number;
  companyCommission: number;
  message?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface RechargeHistoryResponse {
  items: RechargeHistoryItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
  fromDate: string;
  toDate: string;
}

export interface WalletHistoryItem {
  id: number;
  type: string;
  amount: number;
  status: string;
  referenceType?: string | null;
  referenceId?: string | null;
  externalRef: string;
  description?: string | null;
  createdAt: string;
  mobileNumber?: string | null;
  operator?: string | null;
  circle?: string | null;
}

export interface WalletHistoryResponse {
  items: WalletHistoryItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
  fromDate: string;
  toDate: string;
}

export interface WithdrawalHistoryItem {
  withdrawalId: string;
  clientRequestId: string;
  amount: number;
  upiId: string;
  provider: string;
  status: string;
  providerReference?: string | null;
  providerStatus?: string | null;
  failureReason?: string | null;
  walletLedgerRef?: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt?: string | null;
}

export interface WithdrawalHistoryResponse {
  items: WithdrawalHistoryItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
}


export interface RentalAdminVendor {
  vendorId: string;
  userId: string;
  fullName: string;
  businessName?: string | null;
  mobile?: string | null;
  email?: string | null;
  vendorType: string;
  status: string;
  address: string;
  city: string;
  state: string;
  pinCode: string;
  panNumber?: string | null;
  payoutUpiId?: string | null;
  bankAccountNumber?: string | null;
  bankIfsc?: string | null;
  vehicleCount: number;
  rejectionReason?: string | null;
  submittedAt: string;
  updatedAt: string;
}


export interface RentalAdminVehicleUnavailability {
  id: string;
  carId: string;
  carName: string;
  vendorId: string;
  startDate: string;
  endDate: string;
  reasonCode: string;
  reasonLabel: string;
  reasonNote?: string | null;
  status: string;
  createdAt: string;
}


export interface RentalAdminBooking {
  bookingId: string;
  userId: string;
  userName?: string | null;
  userMobile?: string | null;
  carId: string;
  carName: string;
  vendorName?: string | null;
  pickup: string;
  drop: string;
  startDate: string;
  endDate: string;
  total: number;
  paymentMethod: string;
  paymentStatus: string;
  walletLedgerRef?: string | null;
  status: string;
  createdAt: string;
}

export interface RentalAdminBookingResponse {
  items: RentalAdminBooking[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
}

export interface RentalAdminDashboard {
  totalBookings: number;
  confirmedBookings: number;
  activeBookings: number;
  completedBookings: number;
  cancelledBookings: number;
  totalBookingValue: number;
  totalRefunded: number;
  totalVendorPayouts: number;
  totalPlatformFees: number;
}
