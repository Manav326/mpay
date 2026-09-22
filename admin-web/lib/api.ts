import { dashboardMock, getUserDetail, usersMock, vendorsMock } from './mock-data';
import { DashboardSummary, RechargeHistoryResponse, Role, SortMode, UserDetail, UserSummary, Vendor, WalletHistoryResponse, WithdrawalHistoryResponse, RentalAdminVendor, RentalAdminVehicleUnavailability } from './types';

const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, '') || 'http://localhost:8080';
const demo = process.env.NEXT_PUBLIC_ADMIN_DEMO_MODE === 'true';

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('mpay_admin_token') : null;
  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(init?.headers || {}) },
  });
  if (!response.ok) throw new Error((await response.text()) || `Request failed (${response.status})`);
  return response.json();
}

export async function getPortalRoles(): Promise<string[]> {
  if (demo) return ['ADMIN', 'MANAGER'];
  const result = await api<{ roles: string[] }>('/api/v1/auth/portal-roles');
  return result.roles;
}

export async function login(mobile: string, password: string, role: string) {
  if (demo) {
    const upper = role.toUpperCase();

    if (upper !== 'ADMIN' && upper !== 'MANAGER') {
      throw new Error('This role is not enabled for the portal.');
    }

    return {
      accessToken: `demo-${upper.toLowerCase()}`,
      refreshToken: `demo-refresh-${upper.toLowerCase()}`,
      role: upper as Role,
      userId: `demo-${upper.toLowerCase()}`,
      name: upper === 'ADMIN' ? 'mPay Admin' : 'Demo Manager',
      permissions:
        upper === 'ADMIN'
          ? [
              'PORTAL_LOGIN',
              'VIEW_DASHBOARD',
              'VIEW_USERS',
              'VIEW_USER_DETAIL',
              'MANAGE_VENDORS',
              'MANAGE_COMMISSION_RATES',
            ]
          : [
              'PORTAL_LOGIN',
              'VIEW_DASHBOARD',
              'VIEW_USERS',
              'VIEW_USER_DETAIL',
            ],
    };
  }

  const endpoint =
    role.toUpperCase() === 'ADMIN'
      ? '/api/v1/auth/admin-login'
      : role.toUpperCase() === 'MANAGER'
        ? '/api/v1/auth/manager-login'
        : '/api/v1/auth/portal-login';

  const body = endpoint.endsWith('/portal-login')
    ? {
        mobile,
        password,
        portalRole: role.toUpperCase(),
      }
    : {
        mobile,
        password,
      };

  const result = await api<{
    accessToken: string;
    refreshToken: string;
    role: Role;
    userId: number | string;
    name?: string;
    permissions?: string[];
  }>(endpoint, {
    method: 'POST',
    body: JSON.stringify(body),
  });

  if (result.role.toUpperCase() !== role.toUpperCase()) {
    throw new Error(
      `Selected role is ${role}, but this account is ${result.role}.`,
    );
  }

  return {
    ...result,
    userId: String(result.userId),
    name: result.name ?? result.role,
    permissions: result.permissions ?? [],
  };
}

export async function requestPasswordReset(mobile: string) {
  if (demo) return { message: 'Demo OTP flow is enabled. Use the same backend OTP flow in development.' };
  return api<{ message: string }>('/api/v1/auth/forgot-password', { method: 'POST', body: JSON.stringify({ mobile }) });
}

export async function resetPassword(mobile: string, otp: string, newPassword: string) {
  if (demo) return { message: 'Password reset accepted in demo mode.' };
  return api<{ message: string }>('/api/v1/auth/reset-password', { method: 'POST', body: JSON.stringify({ mobile, otp, newPassword }) });
}

export async function getVisibleRoles(): Promise<string[]> {
  if (demo) return ['ADMIN','MANAGER','CLIENT'];
  const result = await api<{ roles: string[] }>('/api/v1/admin/visible-roles');
  return result.roles;
}

export async function getDashboard(): Promise<DashboardSummary> {
  if (demo) return dashboardMock;
  return api('/api/v1/admin/dashboard');
}

export async function getUsers(role: Role | 'ALL' = 'ALL', sort: SortMode = 'today-high'): Promise<UserSummary[]> {
  if (demo) {
    let list = role === 'ALL' ? [...usersMock] : usersMock.filter(u => u.role === role);
    list.sort((a,b) => {
      const key = sort.startsWith('today') ? 'todayEarnings' : 'monthEarnings';
      return sort.endsWith('high') ? b[key] - a[key] : a[key] - b[key];
    });
    return list;
  }
  return api(`/api/v1/admin/users?role=${role}&sort=${sort}`);
}

export async function getUserDetailById(id: string): Promise<UserDetail> {
  if (demo) return getUserDetail(id);

  const result = await api<{
    summary: UserSummary;
    rechargeCount: number;
    addMoneyTotal: number;
    withdrawalTotal: number;
    commissionRate: number;
    balance: number;
    availableBalance: number;
    reservedBalance: number;
    profileImageUrl?: string | null;
    profileImageVersion?: number | null;
    latestRecharge?: UserDetail['latestRecharge'];
    recentWalletEntries: UserDetail['recentWalletEntries'];
  }>(`/api/v1/admin/users/${encodeURIComponent(id)}`);

  return {
    ...result.summary,
    rechargeCount: result.rechargeCount,
    addMoneyTotal: result.addMoneyTotal,
    withdrawalTotal: result.withdrawalTotal,
    commissionRate: result.commissionRate,
    balance: result.balance,
    availableBalance: result.availableBalance,
    reservedBalance: result.reservedBalance,
    profileImageUrl: result.profileImageUrl,
    profileImageVersion: result.profileImageVersion,
    latestRecharge: result.latestRecharge,
    recentWalletEntries: result.recentWalletEntries,
  };
}

export async function getUserRechargeHistory(id: string, page = 0, size = 25): Promise<RechargeHistoryResponse> {
  if (demo) {
    const detail = getUserDetail(id);
    const item = detail.latestRecharge
      ? {
          transactionId: detail.latestRecharge.transactionId,
          clientRequestId: detail.latestRecharge.transactionId,
          mobileNumber: detail.latestRecharge.mobile,
          operator: detail.latestRecharge.operator,
          circle: '—',
          planId: '—',
          planDescription: null,
          planValidity: null,
          amount: detail.latestRecharge.amount,
          walletDebitAmount: Math.max(detail.latestRecharge.amount - detail.latestRecharge.commission, 0),
          status: detail.latestRecharge.status,
          provider: 'DEMO',
          providerReference: null,
          providerOrderId: null,
          walletLedgerRef: null,
          completedAt: detail.latestRecharge.createdAt,
          clientCommission: detail.latestRecharge.commission,
          companyCommission: 0,
          message: null,
          createdAt: detail.latestRecharge.createdAt,
          updatedAt: detail.latestRecharge.createdAt,
        }
      : null;
    const items = item ? [item] : [];
    return { items, page: 0, size, totalItems: items.length, totalPages: items.length ? 1 : 0, hasNext: false, fromDate: '1970-01-01', toDate: new Date().toISOString().slice(0, 10) };
  }
  return api('/api/v1/admin/users/' + encodeURIComponent(id) + '/recharges?page=' + page + '&size=' + size);
}

export async function getUserWalletHistory(id: string, page = 0, size = 25): Promise<WalletHistoryResponse> {
  if (demo) {
    const detail = getUserDetail(id);
    const items = detail.recentWalletEntries.map((entry, index) => ({
      id: index + 1,
      type: entry.type === 'RECHARGE' ? 'DEBIT' : entry.type === 'WITHDRAWAL' ? 'WITHDRAW' : 'CREDIT',
      amount: entry.amount,
      status: 'POSTED',
      referenceType: entry.type,
      referenceId: entry.reference,
      externalRef: entry.reference,
      description: entry.type.replace('_', ' '),
      createdAt: entry.createdAt,
      mobileNumber: null,
      operator: null,
      circle: null,
    }));
    return { items, page: 0, size, totalItems: items.length, totalPages: items.length ? 1 : 0, hasNext: false, fromDate: '1970-01-01', toDate: new Date().toISOString().slice(0, 10) };
  }
  return api('/api/v1/admin/users/' + encodeURIComponent(id) + '/wallet-history?page=' + page + '&size=' + size);
}

export async function getUserProfileImage(id: string): Promise<string | null> {
  if (demo) return null;
  const token = typeof window !== 'undefined' ? localStorage.getItem('mpay_admin_token') : null;
  const response = await fetch(baseUrl + '/api/v1/admin/users/' + encodeURIComponent(id) + '/profile-image', {
    headers: token ? { Authorization: 'Bearer ' + token } : {},
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error((await response.text()) || 'Profile image request failed (' + response.status + ')');
  const blob = await response.blob();
  return URL.createObjectURL(blob);
}

export async function getVendors(): Promise<Vendor[]> {
  if (demo) return [...vendorsMock];
  return api('/api/v1/admin/vendors');
}

export async function createVendor(input: Omit<Vendor, 'id' | 'createdAt'>): Promise<Vendor> {
  if (demo) return { ...input, id: `v-${Date.now()}`, createdAt: new Date().toISOString() };
  return api('/api/v1/admin/vendors', { method: 'POST', body: JSON.stringify(input) });
}


export async function getUserWithdrawalHistory(id: string, page = 0, size = 25): Promise<WithdrawalHistoryResponse> {
  return api('/api/v1/admin/users/' + encodeURIComponent(id) + '/withdrawals?page=' + page + '&size=' + size);
}

export async function getRentalAdminVehicleUnavailability(): Promise<RentalAdminVehicleUnavailability[]> {
  return api('/api/v1/car-rental/admin/vehicle-unavailability');
}

export async function getRentalAdminVendors(): Promise<RentalAdminVendor[]> {
  return api('/api/v1/car-rental/admin/vendors');
}
export async function getRentalAdminVendorVehicles(vendorId: string): Promise<any[]> {
  return api('/api/v1/car-rental/admin/vendors/' + encodeURIComponent(vendorId) + '/vehicles');
}
export async function approveRentalVendor(vendorId: string): Promise<unknown> {
  return api('/api/v1/car-rental/admin/vendors/' + encodeURIComponent(vendorId) + '/approve', { method: 'POST' });
}
export async function rejectRentalVendor(vendorId: string, reason: string): Promise<unknown> {
  return api('/api/v1/car-rental/admin/vendors/' + encodeURIComponent(vendorId) + '/reject', { method: 'POST', body: JSON.stringify({ reason }) });
}
export async function approveRentalVehicle(carId: string): Promise<unknown> {
  return api('/api/v1/car-rental/admin/vehicles/' + encodeURIComponent(carId) + '/approve', { method: 'POST' });
}

export async function rejectRentalVehicle(carId: string, reason: string): Promise<unknown> {
  return api('/api/v1/car-rental/admin/vehicles/' + encodeURIComponent(carId) + '/reject', { method: 'POST', body: JSON.stringify({ reason }) });
}
