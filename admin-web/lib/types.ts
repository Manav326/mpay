export type Role = string;
export type SortMode = 'today-high' | 'today-low' | 'month-high' | 'month-low';

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

export interface Vendor {
  id: string;
  name: string;
  category: 'CAR_RENT' | 'TRAVEL' | 'SERVICES';
  city: string;
  phone: string;
  commissionRate: number;
  active: boolean;
  createdAt: string;
}
