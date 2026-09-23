'use client';

import { useEffect, useMemo, useState } from 'react';
import { BarChart3, CarFront, CalendarDays, ChevronLeft, ChevronRight, CircleDollarSign, Clock3, History, LayoutDashboard, LogOut, Menu, ReceiptText, ShieldCheck, Smartphone, TrendingUp, UserCog, Users, Wallet, WalletCards, X } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { completeRentalBooking, createVendor, getDashboard, getPortalRoles, getRentalAdminBookings, getRentalAdminDashboard, getUserDetailById, getUserProfileImage, getUserRechargeHistory, getUserWalletHistory, getUsers, getVendors, getVisibleRoles, login, requestPasswordReset, resetPassword } from '@/lib/api';
import RentalVendorReview from './RentalVendorReview';
import { DashboardSummary, RechargeHistoryItem, RentalAdminBooking, RentalAdminDashboard, Role, SortMode, UserDetail, UserSummary, Vendor, WalletHistoryItem } from '@/lib/types';

const INR = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
const dateTime = (v: string) => new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(v));

function Logo({ compact = false }: { compact?: boolean }) {
  return <div className="brand"><img src="/mpay-logo.png" alt="mPay"/><div><strong>mPay</strong>{!compact && <span>Admin Portal</span>}</div></div>;
}

export default function Page() {
  const [session, setSession] = useState<{token:string; refreshToken:string; role:Role; name:string; permissions:string[]} | null>(null);
  const [loginState, setLoginState] = useState<'login'|'forgot'>('login');
  const [portalRoles, setPortalRoles] = useState<string[]>(['ADMIN','MANAGER']);
  const [selectedPortalRole, setSelectedPortalRole] = useState('ADMIN');
  const [mobile, setMobile] = useState(''); const [password, setPassword] = useState(''); const [otp, setOtp] = useState(''); const [newPassword, setNewPassword] = useState('');
  const [notice, setNotice] = useState(''); const [busy, setBusy] = useState(false); const [resetRequested, setResetRequested] = useState(false); const [view, setView] = useState<'dashboard'|'users'|'vendors'|'rental'>('dashboard');
  const [dashboard, setDashboard] = useState<DashboardSummary>(); const [users, setUsers] = useState<UserSummary[]>([]); const [visibleUserRoles, setVisibleUserRoles] = useState<string[]>(['ADMIN','MANAGER','CLIENT']); const [vendors, setVendors] = useState<Vendor[]>([]);
  const [roleFilter, setRoleFilter] = useState<Role|'ALL'>('ALL'); const [sort, setSort] = useState<SortMode>('today-high'); const [selected, setSelected] = useState<UserDetail>(); const [drawer, setDrawer] = useState(false); const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [newVendor, setNewVendor] = useState({ name:'', category:'CAR_RENT' as Vendor['category'], city:'', phone:'', commissionRate:5, active:true });
  const [rentalDashboard, setRentalDashboard] = useState<RentalAdminDashboard>();
  const [rentalBookings, setRentalBookings] = useState<RentalAdminBooking[]>([]);
  const [rentalBookingPage, setRentalBookingPage] = useState(0);
  const [rentalBookingHasNext, setRentalBookingHasNext] = useState(false);
  const [rentalBookingStatus, setRentalBookingStatus] = useState('ALL');

  useEffect(()=>{
    const raw = localStorage.getItem('mpay_admin_session');
    if(raw) setSession(JSON.parse(raw));
    getPortalRoles().then(roles=>{ if(roles.length) { setPortalRoles(roles); if(!roles.includes(selectedPortalRole)) setSelectedPortalRole(roles[0]); } }).catch(()=>{});
  },[]);

  useEffect(()=>{ if(session) { getDashboard().then(setDashboard); getVisibleRoles().then(setVisibleUserRoles).catch(()=>{}); loadUsers(); if(session.permissions?.includes('MANAGE_VENDORS')) getVendors().then(setVendors); } },[session]);
  async function loadUsers(){ setUsers(await getUsers(roleFilter, sort)); }
  async function loadRental(){ try { const [summary, page] = await Promise.all([getRentalAdminDashboard(), getRentalAdminBookings(rentalBookingPage,25,rentalBookingStatus)]); setRentalDashboard(summary); setRentalBookings(page.items); setRentalBookingHasNext(page.hasNext); } catch(err:any){ setNotice(err.message||'Unable to load rental administration data.'); } }
  useEffect(()=>{ if(session) loadUsers(); },[roleFilter, sort]);
  useEffect(()=>{ if(session && view==='rental' && permissionsForSession(session).includes('MANAGE_VENDORS')) loadRental(); },[session,view,rentalBookingPage,rentalBookingStatus]);

  async function doLogin(e: React.FormEvent){ e.preventDefault(); setBusy(true); setNotice(''); try { const r = await login(mobile, password, selectedPortalRole); const s={token:r.accessToken, refreshToken:r.refreshToken, role:r.role, name:r.name||r.role, permissions:r.permissions||[]}; localStorage.setItem('mpay_admin_session', JSON.stringify(s)); localStorage.setItem('mpay_admin_token', r.accessToken); setSession(s); } catch(err:any){ setNotice(err.message||'Login failed'); } finally { setBusy(false); } }
  async function doReset(e: React.FormEvent){ e.preventDefault(); setBusy(true); try { if(!resetRequested){ await requestPasswordReset(mobile); setResetRequested(true); setNotice('OTP requested. Enter the OTP sent to the registered mobile number.'); } else { await resetPassword(mobile, otp, newPassword); setNotice('Password reset successful. You can now sign in.'); setLoginState('login'); setResetRequested(false); setOtp(''); setNewPassword(''); } } catch(err:any){ setNotice(err.message||'Reset failed'); } finally { setBusy(false); } }
  function logout(){ localStorage.removeItem('mpay_admin_session'); localStorage.removeItem('mpay_admin_token'); setSession(null); }

  if(!session) return <AuthScreen resetRequested={resetRequested} setResetRequested={setResetRequested} state={loginState} setState={setLoginState} mobile={mobile} setMobile={setMobile} password={password} setPassword={setPassword} otp={otp} setOtp={setOtp} newPassword={newPassword} setNewPassword={setNewPassword} busy={busy} notice={notice} onLogin={doLogin} onReset={doReset} portalRoles={portalRoles} selectedPortalRole={selectedPortalRole} setSelectedPortalRole={setSelectedPortalRole}/>;

  const permissions = session.permissions || [];
  function permissionsForSession(s: typeof session){ return s?.permissions || []; }
  const canVendors = permissions.includes('MANAGE_VENDORS');
  const menu = [
    ['dashboard','Dashboard',LayoutDashboard], ['users','Users',Users], ...(canVendors ? [['vendors','Vendors',CarFront] as const, ['rental','Rental Operations',CalendarDays] as const] : []),
  ] as const;

  return <div className="shell">
    <aside className={`sidebar ${drawer?'open ':''}${sidebarCollapsed?'collapsed':''}`}>
      <div className="side-top"><Logo compact/><div className="side-controls"><button className="icon-btn sidebar-collapse-btn" title={sidebarCollapsed?'Expand navigation':'Collapse navigation'} onClick={()=>setSidebarCollapsed(v=>!v)}>{sidebarCollapsed?<ChevronRight size={17}/>:<ChevronLeft size={17}/>}</button><button className="icon-btn mobile-only" onClick={()=>setDrawer(false)}><X size={19}/></button></div></div>
      <div className="portal-role"><ShieldCheck size={16}/><span>{session.role.replace('_',' ')} portal</span></div>
      <nav>{menu.map(([key,label,Icon])=><button key={key} className={view===key?'nav active':'nav'} onClick={()=>{setView(key as any);setDrawer(false)}}><Icon size={18}/><span>{label}</span></button>)}</nav>
      <div className="side-bottom"><div className="profile-mini"><div className="avatar">{session.name.charAt(0)}</div><div><b>{session.name}</b><span>{session.role}</span></div></div><button className="nav" onClick={logout}><LogOut size={18}/><span>Logout</span></button></div>
    </aside>
    <main className="main"><header className="topbar"><button className="icon-btn mobile-only" onClick={()=>setDrawer(true)}><Menu size={20}/></button><div><div className="eyebrow">mPay admin console</div><h1>{view==='dashboard'?'Company Overview':view==='users'?'Users':view==='vendors'?'Vendors & Services':'Rental Operations'}</h1></div><div className="top-actions"><span className="role-pill">{session.role}</span><div className="admin-user-chip"><div className="avatar">{session.name.charAt(0)}</div><span>{session.name}</span></div></div></header>
      {view==='dashboard' && <Dashboard data={dashboard} onNavigate={(next)=>setView(next)} canVendors={canVendors} />}
      {view==='users' && <UsersView users={users} role={session.role} visibleRoles={visibleUserRoles} roleFilter={roleFilter} setRoleFilter={setRoleFilter} sort={sort} setSort={setSort} selected={selected} setSelected={setSelected}/>} 
      {view==='vendors' && canVendors && <VendorsView vendors={vendors} newVendor={newVendor} setNewVendor={setNewVendor} onAdd={async()=>{const v=await createVendor(newVendor);setVendors(x=>[v,...x]);setNewVendor({name:'',category:'CAR_RENT',city:'',phone:'',commissionRate:5,active:true});}}/>}
      {view==='rental' && canVendors && <RentalOperations dashboard={rentalDashboard} bookings={rentalBookings} status={rentalBookingStatus} setStatus={(v)=>{setRentalBookingStatus(v);setRentalBookingPage(0)}} page={rentalBookingPage} hasNext={rentalBookingHasNext} onPrev={()=>setRentalBookingPage(p=>Math.max(0,p-1))} onNext={()=>setRentalBookingPage(p=>p+1)} onRefresh={loadRental} onComplete={async(id)=>{setBusy(true);try{await completeRentalBooking(id);setNotice('Booking completed and vendor payout settled.');await loadRental();}catch(err:any){setNotice(err.message||'Unable to complete booking.')}finally{setBusy(false)}}} busy={busy}/>} \n      {view==='vendors' && canVendors && <RentalVendorReview/>}
    </main>
  </div>
}

function AuthScreen(p:any){
  return <main className="auth-wrap"><div className="auth-card"><div className="auth-brand"><Logo/></div>{p.state==='login'?<><div className="auth-copy"><h1>Welcome to mPay Admin</h1><p>Sign in as Admin, Manager, or another enabled portal role.</p></div><form onSubmit={p.onLogin} className="form"><label>Portal role<select value={p.selectedPortalRole} onChange={e=>p.setSelectedPortalRole(e.target.value)}>{p.portalRoles.map((r:string)=><option key={r} value={r}>{r.charAt(0)+r.slice(1).toLowerCase()}</option>)}</select></label><label>Mobile number<input value={p.mobile} onChange={e=>p.setMobile(e.target.value)} placeholder="10-digit mobile number" /></label><label>Password<input type="password" value={p.password} onChange={e=>p.setPassword(e.target.value)} placeholder="Enter password" /></label>{p.notice&&<div className="alert">{p.notice}</div>}<button className="primary" disabled={p.busy}>{p.busy?'Signing in…':'Sign in'}</button><button type="button" className="link-btn" onClick={()=>{p.setState('forgot');p.notice&&p.setMobile(p.mobile)}}>Forgot password?</button></form></>:<><div className="auth-copy"><h1>Reset admin password</h1><p>Only Admin and Manager accounts can access this portal.</p></div><form onSubmit={p.onReset} className="form"><label>Registered mobile<input value={p.mobile} onChange={e=>p.setMobile(e.target.value)} placeholder="10-digit mobile number" /></label>{p.resetRequested&&<label>OTP<input value={p.otp} onChange={e=>p.setOtp(e.target.value)} placeholder="6-digit OTP" /></label>}{p.resetRequested&&<label>New password<input type="password" value={p.newPassword} onChange={e=>p.setNewPassword(e.target.value)} placeholder="New password" /></label>}{p.notice&&<div className="alert">{p.notice}</div>}<button className="primary" disabled={p.busy}>{p.busy?'Please wait…':p.resetRequested?'Reset password':'Send OTP'}</button><button type="button" className="link-btn" onClick={()=>{p.setState('login');p.setResetRequested(false)}}>Back to sign in</button></form></>}</div></main>
}

function Dashboard({data,onNavigate,canVendors}:{data?:DashboardSummary;onNavigate:(next:'dashboard'|'users'|'vendors'|'rental')=>void;canVendors:boolean}){
  if(!data) return <div className="loading">Loading dashboard…</div>;
  const cards=[['Today earnings',data.todayCommission,'Commission earned today',CircleDollarSign],['Today volume',data.todayVolume,'Successful recharge value',Wallet],['This month',data.monthlyCommission,'Commission through today',TrendingUp],['Active clients',data.activeClients,'Currently active client accounts',Users]] as const;
  return <div className="content"><section className="metric-grid">{cards.map(([title,value,sub,Icon])=><div className="metric-card" key={title}><div className="metric-head"><span>{title}</span><div className="metric-icon"><Icon size={18}/></div></div><strong>{typeof value==='number'&&title!=='Active clients'?INR.format(value):value.toLocaleString('en-IN')}</strong><small>{sub}</small></div>)}</section><div className="split"><section className="panel"><div className="panel-head"><div><h2>Company performance</h2><p>Recharge volume and commission across the current period.</p></div><button className="secondary" onClick={()=>onNavigate('users')}>View users <ChevronRight size={16}/></button></div><div className="chart-box"><ResponsiveContainer width="100%" height="100%"><BarChart data={data.chart}><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#eee7dd"/><XAxis dataKey="label" tickLine={false} axisLine={false}/><YAxis tickLine={false} axisLine={false}/><Tooltip formatter={(v:any)=>INR.format(Number(v))}/><Bar dataKey="volume" fill="#f59e0b" radius={[6,6,0,0]}/></BarChart></ResponsiveContainer></div></section><section className="panel"><div className="panel-head"><div><h2>Quick actions</h2><p>Direct links to active administration workspaces.</p></div></div><div className="quick-grid"><button className="quick" onClick={()=>onNavigate('users')}><Users size={20}/><div><b>Manage users</b><span>Hierarchy, balances and earnings</span></div></button>{canVendors && <><button className="quick" onClick={()=>onNavigate('vendors')}><CarFront size={20}/><div><b>Manage vendors</b><span>Partner directory and vendor review</span></div></button><button className="quick" onClick={()=>onNavigate('rental')}><CalendarDays size={20}/><div><b>Rental operations</b><span>Bookings, settlement and platform fees</span></div></button></>}<CalendarDays size={20}/><div><b>Rental operations</b><span>Bookings, settlement and platform fees</span></div></button></div></section></div></div>
}

function UsersView({users,role,visibleRoles,roleFilter,setRoleFilter,sort,setSort,selected,setSelected}:{users:UserSummary[];role:Role;visibleRoles:string[];roleFilter:Role|'ALL';setRoleFilter:(v:any)=>void;sort:SortMode;setSort:(v:any)=>void;selected?:UserDetail;setSelected:(v:any)=>void}){
  const allowed = ['ALL', ...visibleRoles];
  return <div className="content"><section className="panel"><div className="panel-head wrap"><div><h2>User hierarchy</h2><p>{role==='ADMIN'?'Admins can see managers and clients. Managers can see clients only.':'You can see client accounts assigned under your management.'}</p></div><div className="filters"><select value={roleFilter} onChange={e=>setRoleFilter(e.target.value)}>{allowed.map(r=><option key={r} value={r}>{r==='ALL'?'All users':r}</option>)}</select><select value={sort} onChange={e=>setSort(e.target.value)}><option value="today-high">Today: highest earnings</option><option value="today-low">Today: lowest earnings</option><option value="month-high">Month: highest earnings</option><option value="month-low">Month: lowest earnings</option></select></div></div><div className="table-wrap"><table><thead><tr><th>User</th><th>Type</th><th>Today's earnings</th><th>Monthly earnings</th><th>Wallet</th><th>Status</th><th></th></tr></thead><tbody>{users.map(u=><tr key={u.id} onClick={async()=>setSelected(await getUserDetailById(u.id))}><td><div className="user-cell"><div className="avatar light">{u.name.charAt(0)}</div><div><b>{u.name}</b><span>{u.mobile} · {u.publicUserId}</span></div></div></td><td><span className={`type-pill ${(u.role || 'UNKNOWN').toLowerCase()}`}>{u.accountType}</span></td><td>{INR.format(u.todayEarnings)}</td><td>{INR.format(u.monthEarnings)}</td><td>{INR.format(u.walletBalance)}</td><td><span className={`status ${(u.status || 'UNKNOWN').toLowerCase()}`}>{u.status}</span></td><td><ChevronRight size={18}/></td></tr>)}</tbody></table></div></section>{selected&&<UserDrawer user={selected} onClose={()=>setSelected(undefined)}/>}</div>
}

function UserDrawer({user,onClose}:{user:UserDetail;onClose:()=>void}){
  const [tab,setTab] = useState<'overview'|'recharges'|'wallet'>('overview');
  const [imageSrc,setImageSrc] = useState<string | null>(null);
  const [recharges,setRecharges] = useState<RechargeHistoryItem[]>([]);
  const [rechargePage,setRechargePage] = useState(0);
  const [rechargeHasNext,setRechargeHasNext] = useState(false);
  const [walletHistory,setWalletHistory] = useState<WalletHistoryItem[]>([]);
  const [walletPage,setWalletPage] = useState(0);
  const [walletHasNext,setWalletHasNext] = useState(false);
  const [loadingRecharges,setLoadingRecharges] = useState(true);
  const [loadingWallet,setLoadingWallet] = useState(true);

  useEffect(()=>{
    let active = true;
    getUserProfileImage(user.id).then(src=>{ if(active) setImageSrc(src); }).catch(()=>{});
    Promise.all([
      getUserRechargeHistory(user.id,0,25),
      getUserWalletHistory(user.id,0,25),
    ]).then(([rechargePageData,walletPageData])=>{
      if(!active) return;
      setRecharges(rechargePageData.items);
      setRechargePage(rechargePageData.page);
      setRechargeHasNext(rechargePageData.hasNext);
      setWalletHistory(walletPageData.items);
      setWalletPage(walletPageData.page);
      setWalletHasNext(walletPageData.hasNext);
    }).catch(()=>{}).finally(()=>{
      if(active){ setLoadingRecharges(false); setLoadingWallet(false); }
    });
    return ()=>{ active=false; };
  },[user.id]);

  useEffect(()=>()=>{ if(imageSrc?.startsWith('blob:')) URL.revokeObjectURL(imageSrc); },[imageSrc]);

  async function loadMoreRecharges(){
    const next = await getUserRechargeHistory(user.id,rechargePage + 1,25);
    setRecharges(current=>[...current,...next.items]);
    setRechargePage(next.page);
    setRechargeHasNext(next.hasNext);
  }

  async function loadMoreWallet(){
    const next = await getUserWalletHistory(user.id,walletPage + 1,25);
    setWalletHistory(current=>[...current,...next.items]);
    setWalletPage(next.page);
    setWalletHasNext(next.hasNext);
  }

  const walletWithBalances = useMemo(() => {
    let running = user.balance;
    return walletHistory.map(item => {
      const posted = (item.status || '').toUpperCase() === 'POSTED';
      if (!posted) return { item, before: null as number | null, after: null as number | null };
      const after = running;
      const reducesBalance = ['DEBIT', 'WITHDRAW'].includes((item.type || '').toUpperCase());
      const before = reducesBalance ? after + item.amount : after - item.amount;
      running = before;
      return { item, before, after };
    });
  }, [walletHistory, user.balance]);

  const statusClass = (user.status || 'UNKNOWN').toLowerCase();

  return <div className="drawer-overlay" onClick={onClose}>
    <aside className="user-drawer user-drawer-wide" onClick={e=>e.stopPropagation()}>
      <div className="drawer-head">
        <div className="drawer-user-heading">
          {imageSrc ? <img className="drawer-avatar-image" src={imageSrc} alt={user.name + ' profile'} /> : <div className="drawer-avatar">{user.name.charAt(0).toUpperCase()}</div>}
          <div>
            <div className="eyebrow">Account detail · Read only</div>
            <h2>{user.name}</h2>
            <div className="drawer-subtitle"><span>{user.accountType}</span><span>{user.publicUserId}</span><span className={"status " + statusClass}>{user.status}</span></div>
          </div>
        </div>
        <button className="icon-btn" onClick={onClose}><X/></button>
      </div>

      <section className="detail-grid detail-grid-3">
        <div className="balance-highlight"><small>Current balance</small><b>{INR.format(user.balance)}</b></div>
        <div><small>Available balance</small><b>{INR.format(user.availableBalance)}</b></div>
        <div><small>Reserved balance</small><b>{INR.format(user.reservedBalance)}</b></div>
        <div><small>Successful recharges</small><b>{user.rechargeCount.toLocaleString('en-IN')}</b></div>
        <div><small>Total added</small><b>{INR.format(user.addMoneyTotal)}</b></div>
        <div><small>Total withdrawn</small><b>{INR.format(user.withdrawalTotal)}</b></div>
      </section>

      <section className="drawer-section">
        <div className="drawer-section-title"><div><h3>Profile & account</h3><p>Information currently available to the client account.</p></div><ShieldCheck size={17}/></div>
        <div className="profile-facts">
          <div><small><Users size={14}/> Name</small><b>{user.name}</b></div>
          <div><small><Smartphone size={14}/> Mobile</small><b>{user.mobile}</b></div>
          <div><small><ReceiptText size={14}/> Email</small><b>{user.email || 'Not provided'}</b></div>
          <div><small><CircleDollarSign size={14}/> Commission</small><b>{user.commissionRate}%</b></div>
          <div><small><CalendarDays size={14}/> Joined</small><b>{dateTime(user.joinedAt)}</b></div>
          <div><small><History size={14}/> Profile updated</small><b>{user.profileUpdatedAt ? dateTime(user.profileUpdatedAt) : 'Not available'}</b></div>
        </div>
      </section>

      <div className="detail-tabs">
        <button className={tab==='overview'?'active':''} onClick={()=>setTab('overview')}><WalletCards size={15}/> Overview</button>
        <button className={tab==='recharges'?'active':''} onClick={()=>setTab('recharges')}><ReceiptText size={15}/> Recharges <span>{recharges.length}{rechargeHasNext?'+':''}</span></button>
        <button className={tab==='wallet'?'active':''} onClick={()=>setTab('wallet')}><History size={15}/> Balance history <span>{walletHistory.length}{walletHasNext?'+':''}</span></button>
      </div>

      {tab==='overview' && <section className="drawer-section">
        <div className="drawer-section-title"><div><h3>Recent activity</h3><p>Latest wallet and recharge activity for quick review.</p></div></div>
        {user.latestRecharge ? <div className="detail-card">
          <div className="detail-top"><span className={"status " + (user.latestRecharge.status || 'UNKNOWN').toLowerCase()}>{user.latestRecharge.status}</span><span>{dateTime(user.latestRecharge.createdAt)}</span></div>
          <b>{user.latestRecharge.operator} · {user.latestRecharge.mobile}</b>
          <div className="detail-row"><span>Recharge amount</span><strong>{INR.format(user.latestRecharge.amount)}</strong></div>
          <div className="detail-row"><span>Client commission</span><strong className="green">{INR.format(user.latestRecharge.commission)}</strong></div>
          <div className="detail-row"><span>Transaction ID</span><strong className="mono">{user.latestRecharge.transactionId}</strong></div>
        </div> : <div className="empty-state">No recharge activity recorded.</div>}
        <div className="activity-list">
          {user.recentWalletEntries.map(entry=><div className="activity-row" key={entry.id}><div><b>{entry.type.replace('_',' ')}</b><span>{entry.reference}</span></div><strong>{INR.format(entry.amount)}</strong></div>)}
        </div>
      </section>}

      {tab==='recharges' && <section className="drawer-section">
        <div className="drawer-section-title"><div><h3>All recharge records</h3><p>Includes successful, failed, pending and other persisted recharge attempts.</p></div></div>
        {loadingRecharges ? <div className="empty-state">Loading recharge history…</div> : recharges.length===0 ? <div className="empty-state">No recharge records found.</div> :
          <div className="history-table-wrap"><table className="history-table"><thead><tr><th>Date</th><th>Recharge</th><th>Plan</th><th>Amounts</th><th>Status</th><th>References</th></tr></thead><tbody>
            {recharges.map(r=><tr key={r.transactionId}><td>{dateTime(r.createdAt)}</td><td><b>{r.operator} · {r.mobileNumber}</b><span>{r.circle}</span></td><td><b>{INR.format(r.amount)}</b><span>{r.planDescription || r.planId}</span></td><td><b>Debit {INR.format(r.walletDebitAmount)}</b><span>Commission {INR.format(r.clientCommission)}</span><span>Company {INR.format(r.companyCommission)}</span></td><td><span className={"status " + (r.status || 'UNKNOWN').toLowerCase()}>{r.status}</span>{r.message&&<span>{r.message}</span>}</td><td><span className="mono">{r.transactionId}</span><span>{r.providerReference || r.providerOrderId || r.clientRequestId}</span></td></tr>)}
          </tbody></table></div>}
        {rechargeHasNext && <button className="secondary load-more" onClick={loadMoreRecharges}>Load more recharge records <ChevronRight size={15}/></button>}
      </section>}

      {tab==='wallet' && <section className="drawer-section">
        <div className="drawer-section-title"><div><h3>Complete balance history</h3><p>Credits, debits, withdrawals and linked recharge ledger entries.</p></div></div>
        {loadingWallet ? <div className="empty-state">Loading balance history…</div> : walletHistory.length===0 ? <div className="empty-state">No balance history found.</div> :
          <div className="history-table-wrap"><table className="history-table"><thead><tr><th>Date</th><th>Type</th><th>Amount</th><th>Balance before</th><th>Balance after</th><th>Status</th><th>Reference</th><th>Description</th></tr></thead><tbody>
            {walletWithBalances.map(({item,before,after})=><tr key={item.id}><td>{dateTime(item.createdAt)}</td><td><b>{item.referenceType || item.type}</b>{item.mobileNumber&&<span>{item.operator} · {item.mobileNumber}</span>}</td><td><b>{INR.format(item.amount)}</b></td><td>{before===null?'—':INR.format(before)}</td><td>{after===null?'—':INR.format(after)}</td><td><span className={"status " + (item.status || 'UNKNOWN').toLowerCase()}>{item.status}</span></td><td><span className="mono">{item.referenceId || item.externalRef}</span></td><td>{item.description || '—'}</td></tr>)}
          </tbody></table></div>}
        {walletHasNext && <button className="secondary load-more" onClick={loadMoreWallet}>Load more balance records <ChevronRight size={15}/></button>}
      </section>}

      <div className="drawer-note"><ShieldCheck size={15}/> This view is strictly read-only. No balance, profile, recharge, transaction or booking data can be changed from this screen.</div>
    </aside>
  </div>
}
function VendorsView({vendors,newVendor,setNewVendor,onAdd}:{vendors:Vendor[];newVendor:any;setNewVendor:(v:any)=>void;onAdd:()=>void}){
  return <div className="content"><div className="split"><section className="panel"><div className="panel-head"><div><h2>Add vendor</h2><p>These partner services will later appear as quick actions for clients.</p></div></div><div className="form vendor-form"><label>Vendor name<input value={newVendor.name} onChange={e=>setNewVendor({...newVendor,name:e.target.value})}/></label><div className="two"><label>Category<select value={newVendor.category} onChange={e=>setNewVendor({...newVendor,category:e.target.value})}><option value="CAR_RENT">Car rental</option><option value="TRAVEL">Travel</option><option value="SERVICES">Other service</option></select></label><label>City<input value={newVendor.city} onChange={e=>setNewVendor({...newVendor,city:e.target.value})}/></label></div><label>Phone<input value={newVendor.phone} onChange={e=>setNewVendor({...newVendor,phone:e.target.value})}/></label><label>Commission %<input type="number" value={newVendor.commissionRate} onChange={e=>setNewVendor({...newVendor,commissionRate:Number(e.target.value)})}/></label><button className="primary" onClick={onAdd} disabled={!newVendor.name||!newVendor.city}>Add vendor</button></div></section><section className="panel"><div className="panel-head"><div><h2>Partner directory</h2><p>Active and inactive service vendors.</p></div></div><div className="vendor-list">{vendors.map(v=><div className="vendor-row" key={v.id}><div className="vendor-icon"><CarFront size={18}/></div><div className="vendor-main"><b>{v.name}</b><span>{v.category.replace('_',' ')} · {v.city} · {v.phone}</span></div><span className={`status ${v.active?'active':'blocked'}`}>{v.active?'ACTIVE':'INACTIVE'}</span></div>)}</div></section></div></div>
}


function RentalOperations(p:{
  dashboard?: RentalAdminDashboard;
  bookings: RentalAdminBooking[];
  status: string;
  setStatus:(v:string)=>void;
  page:number;
  hasNext:boolean;
  onPrev:()=>void;
  onNext:()=>void;
  onRefresh:()=>void;
  onComplete:(id:string)=>void;
  busy:boolean;
}){
  const d=p.dashboard;
  const money=(v:number)=>INR.format(v);
  const statusClass=(s:string)=>(s||'UNKNOWN').toLowerCase().replace(/_/g,'-');
  return <div className="content">
    <section className="metric-grid">
      <div className="metric-card"><div className="metric-head"><span>Total bookings</span><div className="metric-icon"><CalendarDays size={18}/></div></div><strong>{d?.totalBookings ?? '—'}</strong><small>All persisted rental bookings</small></div>
      <div className="metric-card"><div className="metric-head"><span>Active</span><div className="metric-icon"><Clock3 size={18}/></div></div><strong>{d?.activeBookings ?? '—'}</strong><small>Currently in progress</small></div>
      <div className="metric-card"><div className="metric-head"><span>Booking value</span><div className="metric-icon"><CircleDollarSign size={18}/></div></div><strong>{d ? money(d.totalBookingValue) : '—'}</strong><small>Total rental value</small></div>
      <div className="metric-card"><div className="metric-head"><span>Platform fees</span><div className="metric-icon"><TrendingUp size={18}/></div></div><strong>{d ? money(d.totalPlatformFees) : '—'}</strong><small>Settled vendor fees</small></div>
    </section>
    <section className="panel">
      <div className="panel-head wrap">
        <div><h2>Rental bookings</h2><p>Wallet-paid bookings and their operational lifecycle.</p></div>
        <div className="filters">
          <select value={p.status} onChange={e=>p.setStatus(e.target.value)}><option>ALL</option><option>CONFIRMED</option><option>COMPLETED</option><option>CANCELLED</option></select>
          <button className="secondary" onClick={p.onRefresh}>Refresh</button>
        </div>
      </div>
      {p.bookings.length===0 ? <div className="empty-state">No rental bookings found.</div> :
      <div className="table-wrap"><table><thead><tr><th>Booking</th><th>Customer</th><th>Car / Vendor</th><th>Trip</th><th>Amount</th><th>Status</th><th>Action</th></tr></thead><tbody>
        {p.bookings.map(b=><tr key={b.bookingId}>
          <td><b className="mono">{b.bookingId}</b><span>{dateTime(b.createdAt)}</span></td>
          <td><b>{b.userName || 'Customer'}</b><span>{b.userMobile || b.userId}</span></td>
          <td><b>{b.carName}</b><span>{b.vendorName || 'Vendor'}</span></td>
          <td><b>{b.pickup}</b><span>→ {b.drop}</span><span>{dateTime(b.startDate)} → {dateTime(b.endDate)}</span></td>
          <td><b>{money(b.total)}</b><span>{b.paymentMethod} · {b.paymentStatus}</span></td>
          <td><span className={'status '+statusClass(b.status)}>{b.status}</span></td>
          <td>{b.status==='CONFIRMED' && new Date(b.endDate).getTime()<=Date.now() ? <button className="secondary" disabled={p.busy} onClick={()=>p.onComplete(b.bookingId)}>Complete & settle</button> : <span>—</span>}</td>
        </tr>)}
      </tbody></table></div>}
      <div className="panel-head"><span>Page {p.page+1}</span><div className="filters"><button className="secondary" disabled={p.page===0} onClick={p.onPrev}>Previous</button><button className="secondary" disabled={!p.hasNext} onClick={p.onNext}>Next</button></div></div>
    </section>
  </div>;
}
