'use client';

import { FormEvent, useEffect, useState } from 'react';
import { ArrowLeft, ShieldCheck } from 'lucide-react';
import { getPortalRoles, login } from '@/lib/api';

export default function AdminLoginPage() {
  const [roles,setRoles]=useState<string[]>(['ADMIN','MANAGER']);
  const [role,setRole]=useState('ADMIN');
  const [mobile,setMobile]=useState('');
  const [password,setPassword]=useState('');
  const [notice,setNotice]=useState('');
  const [busy,setBusy]=useState(false);

  useEffect(()=>{ getPortalRoles().then(r=>{if(r.length){setRoles(r);setRole(r[0]);}}).catch(()=>{}); },[]);

  async function submit(e:FormEvent){
    e.preventDefault(); setBusy(true); setNotice('');
    try{
      const r=await login(mobile,password,role);
      const s={token:r.accessToken,refreshToken:r.refreshToken,role:r.role,name:r.name||r.role,permissions:r.permissions||[]};
      localStorage.setItem('mpay_admin_session',JSON.stringify(s));
      localStorage.setItem('mpay_admin_token',r.accessToken);
      window.location.href='/admin';
    }catch(err:any){setNotice(err?.message||'Unable to sign in. Please check your details.');}
    finally{setBusy(false);}
  }

  return <main className="admin-login-page">
    <div className="admin-login-glow glow-one"/><div className="admin-login-glow glow-two"/>
    <section className="admin-login-card">
      <a href="/" className="back-link"><ArrowLeft size={16}/> Back to mPay</a>
      <div className="admin-login-brand"><img src="/mpay-logo.png" alt="mPay"/><div><strong>mPay</strong><span>Secure portal access</span></div></div>
      <div className="admin-login-copy"><div className="secure-badge"><ShieldCheck size={15}/> Authorised access</div><h1>Sign in to the admin portal</h1><p>Access the role-based mPay console for administration, management and operational oversight.</p></div>
      <form className="form" onSubmit={submit}>
        <label>Portal role<select value={role} onChange={e=>setRole(e.target.value)}>{roles.map(r=><option key={r}>{r}</option>)}</select></label>
        <label>Mobile number<input inputMode="numeric" value={mobile} onChange={e=>setMobile(e.target.value)} placeholder="10-digit mobile number" autoComplete="username"/></label>
        <label>Password<input type="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="Enter password" autoComplete="current-password"/></label>
        {notice&&<div className="alert">{notice}</div>}
        <button className="primary" disabled={busy}>{busy?'Signing in…':'Sign in securely'}</button>
      </form>
      <p className="admin-login-foot">mPay uses role-based access controls so each authorised user sees only the tools and information assigned to their role.</p>
    </section>
  </main>;
}
