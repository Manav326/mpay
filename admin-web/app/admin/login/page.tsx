'use client';

import { FormEvent, useEffect, useState } from 'react';
import { ArrowLeft, ShieldCheck } from 'lucide-react';
import { getPortalRoles, login, requestPasswordReset, resetPassword } from '@/lib/api';

export default function AdminLoginPage() {
  const [roles,setRoles]=useState<string[]>(['ADMIN','MANAGER']);
  const [role,setRole]=useState('ADMIN');
  const [mode,setMode]=useState<'login'|'forgot'>('login');
  const [mobile,setMobile]=useState('');
  const [password,setPassword]=useState('');
  const [otp,setOtp]=useState('');
  const [newPassword,setNewPassword]=useState('');
  const [notice,setNotice]=useState('');
  const [busy,setBusy]=useState(false);
  const [resetRequested,setResetRequested]=useState(false);

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

  async function reset(e:FormEvent){
    e.preventDefault(); setBusy(true); setNotice('');
    try{
      if(!resetRequested){
        await requestPasswordReset(mobile);
        setResetRequested(true);
        setNotice('OTP requested. Enter the OTP sent to the registered mobile number.');
      } else {
        await resetPassword(mobile,otp,newPassword);
        setMode('login');
        setResetRequested(false);
        setOtp('');
        setNewPassword('');
        setNotice('Password reset successful. You can now sign in.');
      }
    }catch(err:any){setNotice(err?.message||'Unable to reset password.');}
    finally{setBusy(false);}
  }

  function backToLogin(){
    setMode('login');
    setResetRequested(false);
    setOtp('');
    setNewPassword('');
    setNotice('');
  }

  return <main className="admin-login-page">
    <div className="admin-login-glow glow-one"/><div className="admin-login-glow glow-two"/>
    <section className="admin-login-card">
      <a href="/" className="back-link"><ArrowLeft size={16}/> Back to mPay</a>
      <div className="admin-login-brand"><img src="/mpay-logo.png" alt="mPay"/><div><strong>mPay</strong><span>Secure portal access</span></div></div>
      {mode==='login' ? (
        <>
          <div className="admin-login-copy">
            <div className="secure-badge"><ShieldCheck size={15}/> Authorised access</div>
            <h1>Sign in to the admin portal</h1>
            <p>Access the role-based mPay console for administration, management and operational oversight.</p>
          </div>
          <form className="form" onSubmit={submit}>
            <label>Portal role<select value={role} onChange={e=>setRole(e.target.value)}>{roles.map(r=><option key={r}>{r}</option>)}</select></label>
            <label>Mobile number<input inputMode="numeric" maxLength={10} value={mobile} onChange={e=>setMobile(e.target.value.replace(/\D/g,''))} placeholder="10-digit mobile number" autoComplete="username"/></label>
            <label>Password<input type="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="Enter password" autoComplete="current-password"/></label>
            {notice&&<div className="alert">{notice}</div>}
            <button className="primary" disabled={busy}>{busy?'Signing in…':'Sign in securely'}</button>
            <button type="button" className="link-btn" onClick={()=>{setMode('forgot');setResetRequested(false);setNotice('');}}>Forgot password?</button>
          </form>
        </>
      ) : (
        <>
          <div className="admin-login-copy">
            <div className="secure-badge"><ShieldCheck size={15}/> Secure password recovery</div>
            <h1>Reset admin password</h1>
            <p>Use your registered mobile number and the OTP sent to you to create a new password.</p>
          </div>
          <form className="form" onSubmit={reset}>
            <label>Registered mobile<input inputMode="numeric" maxLength={10} value={mobile} onChange={e=>setMobile(e.target.value.replace(/\D/g,''))} placeholder="10-digit mobile number"/></label>
            {resetRequested&&<label>OTP<input inputMode="numeric" maxLength={6} value={otp} onChange={e=>setOtp(e.target.value.replace(/\D/g,''))} placeholder="6-digit OTP" autoComplete="one-time-code"/></label>}
            {resetRequested&&<label>New password<input type="password" value={newPassword} onChange={e=>setNewPassword(e.target.value)} placeholder="Enter new password" autoComplete="new-password"/></label>}
            {notice&&<div className="alert">{notice}</div>}
            <button className="primary" disabled={busy}>{busy?'Please wait…':resetRequested?'Reset password':'Send OTP'}</button>
            <button type="button" className="link-btn" onClick={backToLogin}>Back to sign in</button>
          </form>
          <p className="admin-login-foot">Only authorised admin or manager accounts can access the operations console.</p>
        </>
      )}
      {mode==='login' && <p className="admin-login-foot">mPay uses role-based access controls so each authorised user sees only the tools and information assigned to their role.</p>}
    </section>
  </main>;
}
