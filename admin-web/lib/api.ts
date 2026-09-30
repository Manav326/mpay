import { dashboardMock, getUserDetail, usersMock } from './mock-data';
import { redirectToLogin, refreshWebSession } from './session';
import { DashboardSummary, RechargeHistoryResponse, Role, SortMode, UserDetail, UserSummary, WalletHistoryResponse, WithdrawalHistoryResponse, RentalAdminVendor, RentalAdminVehicleUnavailability, RentalAdminBookingResponse, RentalAdminDashboard, AdminFinancialRechargePageResponse, HistoryPdfAccessResponse, HistoryPdfPendingAccessResponse, AdminFinancialWithdrawalPageResponse, AdminFinancialWalletPageResponse, AdminProfile, VoiceCallResponse, VoiceCallSignalingTokenResponse, VoiceCallRoleAccess, VoiceCallUserAccess, SupportAiSettings, SupportCallRequest, SupportCase, SupportInteraction, SupportNote, SupportCustomer, CustomerCallbackAccess, CustomerSupportOverview, SupportChat, SupportMessage, SupportAccessResponse, SupportRoleAccess, SupportUserAccess, SupportQueueResponse, SupportAssignmentResponse, SupportCustomerSearchResult } from './types';

const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, '') || 'http://localhost:8080';
export function getAdminApiBaseUrl(): string { return baseUrl; }
const demo = process.env.NEXT_PUBLIC_ADMIN_DEMO_MODE === 'true';

function normalizeDisplayValue<T>(value: T): T {
  if (typeof value === 'string') {
    return value
      .replace(/\\+(?:r)?n/g, ' ')
      .replace(/\\+r/g, ' ')
      .replace(/\r?\n/g, ' ')
      .replace(/\s+/g, ' ')
      .trim() as T;
  }
  if (Array.isArray(value)) return value.map(item => normalizeDisplayValue(item)) as T;
  if (value && typeof value === 'object') {
    const copy: Record<string, unknown> = {};
    Object.entries(value as Record<string, unknown>).forEach(([key, item]) => {
      copy[key] = normalizeDisplayValue(item);
    });
    return copy as T;
  }
  return value;
}


const adminWebSession = {
  accessKey: 'mpay_admin_token',
  refreshKey: 'mpay_admin_refresh_token',
  sessionKey: 'mpay_admin_session',
  redirectPath: '/admin',
};

function clearAdminSession(): never {
  redirectToLogin(adminWebSession);
}

async function authenticatedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  let token = typeof window !== 'undefined' ? localStorage.getItem(adminWebSession.accessKey) : null;
  const buildHeaders = (authorization?: string) => {
    const headers = new Headers(init.headers || {});
    if (!headers.has('Content-Type') && !(typeof FormData !== 'undefined' && init.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json');
    }
    if (authorization) headers.set('Authorization', 'Bearer ' + authorization);
    else if (token) headers.set('Authorization', 'Bearer ' + token);
    return headers;
  };

  let response = await fetch(baseUrl + path, { ...init, headers: buildHeaders() });
  if (response.status === 401 && typeof window !== 'undefined' && !path.startsWith('/api/v1/auth/')) {
    const refreshed = await refreshWebSession(adminWebSession);
    if (refreshed === 'refreshed') {
      token = localStorage.getItem(adminWebSession.accessKey);
      response = await fetch(baseUrl + path, { ...init, headers: buildHeaders(token || undefined) });
    } else if (refreshed === 'invalid') {
      clearAdminSession();
    }
  }
  return response;
}

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  let response = await authenticatedFetch(path, init);
  if (!response.ok) {
    const text = await response.text();
    let message = text || ('Request failed (' + response.status + ')');
    try {
      const parsed = JSON.parse(text);
      message = normalizeDisplayValue(parsed?.message || parsed?.error || message);
    } catch {}
    throw new Error(String(message));
  }
  return normalizeDisplayValue(await response.json()) as T;
}

export async function getPortalRoles(): Promise<string[]> {
  if (demo) return ['ADMIN', 'MANAGER'];
  const result = await api<{ roles: string[] }>('/api/v1/auth/portal-roles');
  return result.roles;
}

export async function createPortalStaff(payload: {
  name: string;
  mobile: string;
  email?: string;
  password: string;
  role: string;
}): Promise<PortalStaff> {
  return api('/api/v1/admin/staff', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
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
              'MANAGE_USER_STATUS',
              'MANAGE_RECHARGE_OPERATIONS',
              'MANAGE_RENTAL_OPERATIONS',
              'VIEW_FINANCIAL_OPERATIONS',
              'MANAGE_HISTORY_PDF_ACCESS',
            ]
          : [
              'PORTAL_LOGIN',
              'VIEW_DASHBOARD',
              'VIEW_USERS',
              'VIEW_USER_DETAIL',
              'VIEW_FINANCIAL_OPERATIONS',
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

export async function getPendingHistoryPdfAccess(): Promise<HistoryPdfPendingAccessResponse[]> {
  if (demo) return [];
  return api('/api/v1/admin/history-pdf-access/pending');
}

export async function getUserHistoryPdfAccess(id: string): Promise<HistoryPdfAccessResponse> {
  if (demo) return { status: 'APPROVED', requestId: 1, requestReason: 'Demo access', reviewNote: 'Demo mode', requestedAt: new Date().toISOString(), reviewedAt: new Date().toISOString() };
  return api('/api/v1/admin/users/' + encodeURIComponent(id) + '/history-pdf-access');
}

export async function decideUserHistoryPdfAccess(
  id: string,
  requestId: number,
  action: 'APPROVE' | 'REJECT' | 'REVOKE',
  reviewNote?: string
): Promise<HistoryPdfAccessResponse> {
  if (demo) return { status: action === 'APPROVE' ? 'APPROVED' : action === 'REVOKE' ? 'REVOKED' : 'REJECTED', requestId, requestReason: 'Demo access', reviewNote: reviewNote || 'Demo mode', requestedAt: new Date().toISOString(), reviewedAt: new Date().toISOString() };
  return api('/api/v1/admin/users/' + encodeURIComponent(id) + '/history-pdf-access/decision', {
    method: 'POST',
    body: JSON.stringify({ requestId, action, reviewNote }),
  });
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
  const response = await authenticatedFetch('/api/v1/admin/users/' + encodeURIComponent(id) + '/profile-image');
  if (response.status === 404) return null;
  if (!response.ok) throw new Error((await response.text()) || 'Profile image request failed (' + response.status + ')');
  const blob = await response.blob();
  return URL.createObjectURL(blob);
}


export async function getUserWithdrawalHistory(id: string, page = 0, size = 25): Promise<WithdrawalHistoryResponse> {
  return api('/api/v1/admin/users/' + encodeURIComponent(id) + '/withdrawals?page=' + page + '&size=' + size);
}


export async function updateUserStatus(id: string, active: boolean): Promise<{ publicUserId: string; active: boolean; status: 'ACTIVE' | 'BLOCKED' }> {
  return api('/api/v1/admin/users/' + encodeURIComponent(id) + '/status', {
    method: 'POST',
    body: JSON.stringify({ active }),
  });
}

export async function getAdminRecharges(page = 0, size = 25, status = 'ALL', provider = 'ALL'): Promise<AdminFinancialRechargePageResponse> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status !== 'ALL') query.set('status', status);
  if (provider !== 'ALL') query.set('provider', provider);
  return api('/api/v1/admin/financial/recharges?' + query.toString());
}

export async function refreshAdminRecharge(transactionId: string): Promise<unknown> {
  return api('/api/v1/admin/financial/recharges/' + encodeURIComponent(transactionId) + '/refresh', { method: 'POST' });
}

export async function getAdminWithdrawals(page = 0, size = 25, status = 'ALL', provider = 'ALL'): Promise<AdminFinancialWithdrawalPageResponse> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status !== 'ALL') query.set('status', status);
  if (provider !== 'ALL') query.set('provider', provider);
  return api('/api/v1/admin/financial/withdrawals?' + query.toString());
}

export async function getAdminWalletLedger(page = 0, size = 25, referenceType = 'ALL'): Promise<AdminFinancialWalletPageResponse> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (referenceType !== 'ALL') query.set('referenceType', referenceType);
  return api('/api/v1/admin/financial/wallet-history?' + query.toString());
}

export async function getAdminProfile(): Promise<AdminProfile> {
  if (demo) {
    return { userId: 'demo-admin', publicUserId: 'DEMO-ADMIN', name: 'mPay Admin', email: 'admin@mpay.local', mobile: '9999999999', role: 'ADMIN' };
  }
  return api('/api/v1/profile');
}

export async function getAdminProfileImage(): Promise<string | null> {
  if (demo) return null;
  const response = await authenticatedFetch('/api/v1/profile/image');
  if (response.status === 404) return null;
  if (!response.ok) throw new Error((await response.text()) || 'Profile image request failed (' + response.status + ')');
  return URL.createObjectURL(await response.blob());
}

export async function uploadAdminProfileImage(file: File): Promise<unknown> {
  if (demo) return { ok: true };
  const form = new FormData();
  form.append('image', file, file.name);
  const response = await authenticatedFetch('/api/v1/profile/image', { method: 'PUT', body: form });
  if (!response.ok) throw new Error((await response.text()) || 'Unable to upload profile image.');
  return normalizeDisplayValue(await response.json());
}

export async function deleteAdminProfileImage(): Promise<unknown> {
  if (demo) return { ok: true };
  return api('/api/v1/profile/image', { method: 'DELETE' });
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


export async function getRentalAdminDashboard(): Promise<RentalAdminDashboard> {
  return api('/api/v1/car-rental/admin/dashboard');
}

export async function getRentalAdminBookings(page = 0, size = 25, status = 'ALL'): Promise<RentalAdminBookingResponse> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status && status !== 'ALL') query.set('status', status);
  return api('/api/v1/car-rental/admin/bookings?' + query.toString());
}

export async function completeRentalBooking(bookingId: string): Promise<unknown> {
  return api('/api/v1/car-rental/admin/bookings/' + encodeURIComponent(bookingId) + '/complete', { method: 'POST' });
}
export async function cancelRentalBooking(bookingId: string, reason: string): Promise<unknown> {
  return api('/api/v1/car-rental/admin/bookings/' + encodeURIComponent(bookingId) + '/cancel', { method: 'POST', body: JSON.stringify({ reason }) });
}

export async function getRentalAdminPayouts(page = 0, size = 25, status = 'ALL'): Promise<import('./types').RentalAdminPayoutPageResponse> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status && status !== 'ALL') query.set('status', status);
  return api('/api/v1/car-rental/admin/payouts?' + query.toString());
}


export async function getCommissionRates(): Promise<import('./types').RoleCommissionRate[]> {
  return api('/api/v1/admin/commission-roles');
}

export async function updateCommissionRate(role: string, commissionPercent: number, active: boolean): Promise<import('./types').RoleCommissionRate> {
  return api('/api/v1/admin/commission-roles/' + encodeURIComponent(role), {
    method: 'PUT',
    body: JSON.stringify({ commissionPercent, active }),
  });
}


export async function createVoiceCall(targetPublicId: string): Promise<VoiceCallResponse> {
  return api('/api/v1/calls', { method: 'POST', body: JSON.stringify({ targetPublicId }) });
}

export async function getVoiceCall(callId: string): Promise<VoiceCallResponse> {
  return api('/api/v1/calls/' + encodeURIComponent(callId));
}

export async function endVoiceCall(callId: string): Promise<VoiceCallResponse> {
  return api('/api/v1/calls/' + encodeURIComponent(callId) + '/end', { method: 'POST' });
}

export async function getVoiceCallSignalingToken(callId: string): Promise<VoiceCallSignalingTokenResponse> {
  return api('/api/v1/calls/signaling-token', {
    method: 'POST',
    body: JSON.stringify({ callId }),
  });
}

export async function getVoiceCallRoleAccess(): Promise<VoiceCallRoleAccess[]> {
  return api('/api/v1/admin/call-access/roles');
}

export async function updateVoiceCallRoleAccess(role: string, enabled: boolean): Promise<VoiceCallRoleAccess> {
  return api('/api/v1/admin/call-access/roles/' + encodeURIComponent(role), {
    method: 'PUT',
    body: JSON.stringify({ enabled }),
  });
}

export async function getVoiceCallUserAccess(): Promise<VoiceCallUserAccess[]> {
  return api('/api/v1/admin/call-access/users');
}

export async function updateVoiceCallUserAccess(publicUserId: string, mode: VoiceCallUserAccess['mode']): Promise<VoiceCallUserAccess> {
  return api('/api/v1/admin/call-access/users/' + encodeURIComponent(publicUserId), {
    method: 'PUT',
    body: JSON.stringify({ mode }),
  });
}


export async function getSupportAiSettings(): Promise<SupportAiSettings> {
  return api('/api/v1/admin/customer-care/ai');
}

export async function updateSupportAiSettings(enabled: boolean): Promise<SupportAiSettings> {
  return api('/api/v1/admin/customer-care/ai', {
    method: 'PUT',
    body: JSON.stringify({ enabled }),
  });
}


export async function searchCustomerCareCustomers(query: string): Promise<SupportCustomerSearchResult[]> {
  return api('/api/v1/admin/customer-care/customers/search?query=' + encodeURIComponent(query));
}

export async function getCustomerCareQueue(): Promise<SupportQueueResponse> {
  return api('/api/v1/admin/customer-care/queue');
}

export async function markCustomerCareChatRead(publicUserId: string): Promise<SupportChat> {
  return api('/api/v1/admin/customer-care/customers/' + encodeURIComponent(publicUserId) + '/chat/read', {
    method: 'POST',
  });
}

export async function takeSupportCaseOwnership(caseId: string): Promise<SupportAssignmentResponse> {
  return api('/api/v1/admin/customer-care/cases/' + encodeURIComponent(caseId) + '/ownership', {
    method: 'POST',
  });
}

export async function releaseSupportCaseOwnership(caseId: string): Promise<SupportAssignmentResponse> {
  return api('/api/v1/admin/customer-care/cases/' + encodeURIComponent(caseId) + '/ownership', {
    method: 'DELETE',
  });
}

export async function getCustomerCareAccess(): Promise<SupportAccessResponse> {
  return api('/api/v1/admin/customer-care/access');
}

export async function updateCustomerCareRolePermission(role: string, permission: string, enabled: boolean): Promise<SupportRoleAccess> {
  return api(
    '/api/v1/admin/customer-care/access/roles/' + encodeURIComponent(role) + '/' + encodeURIComponent(permission),
    { method: 'PUT', body: JSON.stringify({ enabled }) }
  );
}

export async function updateCustomerCareUserPermission(publicUserId: string, permission: string, mode: 'DEFAULT' | 'ALLOW' | 'DENY'): Promise<SupportUserAccess> {
  return api(
    '/api/v1/admin/customer-care/access/users/' + encodeURIComponent(publicUserId) + '/' + encodeURIComponent(permission),
    { method: 'PUT', body: JSON.stringify({ mode }) }
  );
}

export async function getCustomerCareRequests(): Promise<SupportCallRequest[]> {
  return api('/api/v1/admin/customer-care/requests');
}

export async function getCustomerCareRequest(requestId: string): Promise<SupportCallRequest> {
  return api('/api/v1/admin/customer-care/requests/' + encodeURIComponent(requestId));
}

export async function startCustomerCareCall(requestId: string): Promise<VoiceCallResponse> {
  return api('/api/v1/admin/customer-care/requests/' + encodeURIComponent(requestId) + '/call', { method: 'POST' });
}

export async function declineCustomerCareRequest(requestId: string, note?: string): Promise<SupportCallRequest> {
  return api('/api/v1/admin/customer-care/requests/' + encodeURIComponent(requestId) + '/decline', {
    method: 'POST',
    body: JSON.stringify({ note: note || null }),
  });
}

export async function getCustomerCareChat(publicUserId: string): Promise<SupportChat> {
  return api('/api/v1/admin/customer-care/customers/' + encodeURIComponent(publicUserId) + '/chat');
}

export async function sendCustomerCareChatMessage(publicUserId: string, message: string): Promise<SupportMessage> {
  return api('/api/v1/admin/customer-care/customers/' + encodeURIComponent(publicUserId) + '/chat/messages', {
    method: 'POST',
    body: JSON.stringify({ message }),
  });
}

export async function getCustomerCareCustomerContext(publicUserId: string): Promise<UserDetail> {
  return api('/api/v1/admin/customer-care/customers/' + encodeURIComponent(publicUserId) + '/context');
}

export async function getCustomerCareCustomer(publicUserId: string): Promise<SupportCustomer> {
  return api('/api/v1/admin/customer-care/customers/' + encodeURIComponent(publicUserId));
}

export async function getCustomerCallbackAccess(publicUserId: string): Promise<CustomerCallbackAccess> {
  return api('/api/v1/admin/customer-care/customers/' + encodeURIComponent(publicUserId) + '/callback-access');
}

export async function updateCustomerCallbackAccess(publicUserId: string, enabled: boolean): Promise<CustomerCallbackAccess> {
  return api('/api/v1/admin/customer-care/customers/' + encodeURIComponent(publicUserId) + '/callback-access', {
    method: 'PUT',
    body: JSON.stringify({ enabled }),
  });
}

export async function updateSupportCase(caseId: string, status: string, resolutionCode?: string, resolutionNote?: string): Promise<SupportCase> {
  return api('/api/v1/admin/customer-care/cases/' + encodeURIComponent(caseId), {
    method: 'PUT',
    body: JSON.stringify({ status, resolutionCode: resolutionCode || null, resolutionNote: resolutionNote || null }),
  });
}

export async function addSupportCaseNote(caseId: string, note: string, visibility: 'INTERNAL' | 'CUSTOMER' = 'INTERNAL'): Promise<SupportNote> {
  return api('/api/v1/admin/customer-care/cases/' + encodeURIComponent(caseId) + '/notes', {
    method: 'POST',
    body: JSON.stringify({ note, visibility }),
  });
}

export async function getCustomerSupportOverview(): Promise<CustomerSupportOverview> {
  return api('/api/v1/support/overview');
}

export async function requestCustomerSupportCall(reason?: string): Promise<SupportCallRequest> {
  return api('/api/v1/support/call-request', {
    method: 'POST',
    body: JSON.stringify({ reason: reason || null }),
  });
}

export async function cancelCustomerSupportCall(requestId: string): Promise<SupportCallRequest> {
  return api('/api/v1/support/call-request/' + encodeURIComponent(requestId) + '/cancel', {
    method: 'POST',
  });
}
