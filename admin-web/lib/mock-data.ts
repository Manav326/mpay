import { DashboardSummary, UserDetail, UserSummary, Vendor } from './types';

export const dashboardMock: DashboardSummary = {
  todayVolume: 142890,
  todayCommission: 1428.9,
  monthlyVolume: 2864200,
  monthlyCommission: 28642,
  activeClients: 482,
  successfulRecharges: 98.4,
  totalUsers: 497,
  chart: [
    { label: '10 Sep', volume: 312000, commission: 3120 },
    { label: '11 Sep', volume: 428000, commission: 4280 },
    { label: '12 Sep', volume: 376000, commission: 3760 },
    { label: '13 Sep', volume: 441000, commission: 4410 },
    { label: '14 Sep', volume: 526000, commission: 5260 },
    { label: '15 Sep', volume: 781200, commission: 7812 },
  ],
};

const names = ['Manav Gupta', 'Amit Kumar', 'Priya Singh', 'Rahul Verma', 'Neha Sharma', 'Rohit Singh', 'Ankit Raj', 'Pooja Kumari'];
const roles: UserSummary['role'][] = ['CLIENT','CLIENT','CLIENT','CLIENT','MANAGER','CLIENT','CLIENT','ADMIN'];

export const usersMock: UserSummary[] = Array.from({ length: 24 }, (_, i) => {
  const role = roles[i % roles.length];
  const today = 700 + ((i * 431) % 22000);
  const month = today * (6 + (i % 4));
  return {
    id: `u-${i + 1}`,
    publicUserId: `MPAY-${10001 + i}`,
    name: names[i % names.length],
    mobile: `98${String(70000000 + i * 137).slice(0, 8)}`,
    email: `${names[i % names.length].toLowerCase().replace(/ /g, '.')}@example.com`,
    role,
    accountType: role === 'ADMIN' ? 'Admin' : role === 'MANAGER' ? 'Manager' : 'Client',
    todayEarnings: today,
    monthEarnings: month,
    todayVolume: today * 100,
    monthVolume: month * 100,
    walletBalance: 3500 + i * 517,
    joinedAt: new Date(Date.now() - (30 + i) * 86400000).toISOString(),
    profileUpdatedAt: new Date(Date.now() - i * 8640000).toISOString(),
    status: i === 9 ? 'BLOCKED' : 'ACTIVE',
  };
});

export function getUserDetail(id: string): UserDetail {
  const base = usersMock.find(u => u.id === id) ?? usersMock[0];
  return {
    ...base,
    rechargeCount: 46,
    addMoneyTotal: 19200,
    withdrawalTotal: 5400,
    commissionRate: base.role === 'CLIENT' ? 1 : base.role === 'MANAGER' ? 1.5 : 2,
    balance: base.walletBalance,
    availableBalance: base.walletBalance,
    reservedBalance: 0,
    profileImageUrl: null,
    profileImageVersion: null,
    latestRecharge: {
      mobile: '7070107483', operator: 'AIRTEL', amount: 200, commission: 2, status: 'SUCCESS',
      createdAt: new Date().toISOString(), transactionId: 'TXN-5FBD2A19',
    },
    recentWalletEntries: [
      { id: 'w1', type: 'RECHARGE', amount: 198, createdAt: new Date().toISOString(), reference: 'TXN-5FBD2A19' },
      { id: 'w2', type: 'ADD_MONEY', amount: 1000, createdAt: new Date(Date.now()-86400000).toISOString(), reference: 'PAY-8D02' },
      { id: 'w3', type: 'WITHDRAWAL', amount: 500, createdAt: new Date(Date.now()-2*86400000).toISOString(), reference: 'WD-33C1' },
    ],
  };
}


// Kept for demo-mode API compatibility; the production admin portal uses the real rental review workflow.
export const vendorsMock: Vendor[] = [];
