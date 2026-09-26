'use client';
import { FormEvent,useState } from 'react';
import { ArrowLeft,ShieldCheck,UserPlus } from 'lucide-react';
const base=process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/,'')||'http://localhost:8080';

export default function UserLogin(){
 const [mode,setMode]=useState<'login'|'forgot'>('login');
 const [mobile,setMobile]=useState(''),[password,setPassword]=useState(''),[otp,setOtp]=useState(''),[newPassword,setNewPassword]=useState('');
 const [busy,setBusy]=useState(false),[notice,setNotice]=useState(''),[resetRequested,setResetRequested]=useState(false);

 async function submit(e:FormEvent){
  e.preventDefault();setBusy(true);setNotice('');
  try{
   const r=await fetch(base+'/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({mobile,password})});
   if(!r.ok)throw new Error((await r.text())||'Login failed');
   const d=await r.json();
   localStorage.setItem('mpay_token',d.accessToken);
   localStorage.setItem('mpay_refresh_token',d.refreshToken);
   window.location.href='/portal';
  }catch(e:any){setNotice(e.message||'Unable to sign in.')}finally{setBusy(false)}
 }

 async function reset(e:FormEvent){
  e.preventDefault();setBusy(true);setNotice('');
  try{
   if(!resetRequested){
    const r=await fetch(base+'/api/v1/auth/forgot-password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({mobile})});
    if(!r.ok)throw new Error((await r.text())||'Unable to request password reset.');
    setResetRequested(true);
    setNotice('OTP requested. Enter the OTP sent to the registered mobile number.');
   }else{
    const r=await fetch(base+'/api/v1/auth/reset-password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({mobile,otp,newPassword})});
    if(!r.ok)throw new Error((await r.text())||'Unable to reset password.');
    setMode('login');setResetRequested(false);setOtp('');setNewPassword('');
    setNotice('Password reset successful. You can now sign in.');
   }
  }catch(e:any){setNotice(e.message||'Unable to reset password.')}finally{setBusy(false)}
 }

 const switchToLogin=()=>{setMode('login');setResetRequested(false);setOtp('');setNewPassword('');setNotice('')};

 return <main className="portal-auth-page"><section className="portal-auth-card">
  <a href="/" className="back-link"><ArrowLeft size={16}/> Back to mPay</a>
  <div className="admin-login-brand"><img src="/mpay-logo.png" alt="mPay"/><div><strong>mPay</strong><span>Customer account</span></div></div>
  {mode==='login'?<>
    <div className="admin-login-copy"><div className="secure-badge"><ShieldCheck size={15}/> Secure sign in</div><h1>Welcome back.</h1><p>Sign in to recharge, manage your wallet and keep track of every transaction.</p></div>
    <form className="form" onSubmit={submit}>
      <label>Mobile number<input inputMode="numeric" maxLength={10} value={mobile} onChange={e=>setMobile(e.target.value.replace(/\D/g,''))} placeholder="10-digit mobile number" autoComplete="username"/></label>
      <label>Password<input type="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="Enter password" autoComplete="current-password"/></label>
      {notice&&<div className="alert">{notice}</div>}
      <button className="primary" disabled={busy}>{busy?'Signing in…':'Sign in'}</button>
    </form>
    <button type="button" className="link-btn" onClick={()=>{setMode('forgot');setNotice('');}}>Forgot password?</button>
    <a className="auth-alt-link" href="/signup"><UserPlus size={15}/> New to mPay? Create an account</a>
  </>:<>
    <div className="admin-login-copy"><div className="secure-badge"><ShieldCheck size={15}/> Secure sign in</div><h1>Reset your password.</h1><p>Use your registered mobile number and the OTP sent to you to create a new password.</p></div>
    <form className="form" onSubmit={reset}>
      <label>Registered mobile<input inputMode="numeric" maxLength={10} value={mobile} onChange={e=>setMobile(e.target.value.replace(/\D/g,''))} placeholder="10-digit mobile number"/></label>
      {resetRequested&&<label>OTP<input inputMode="numeric" maxLength={6} value={otp} onChange={e=>setOtp(e.target.value.replace(/\D/g,''))} placeholder="6-digit OTP" autoComplete="one-time-code"/></label>}
      {resetRequested&&<label>New password<input type="password" value={newPassword} onChange={e=>setNewPassword(e.target.value)} placeholder="Enter new password" autoComplete="new-password"/></label>}
      {notice&&<div className="alert">{notice}</div>}
      <button className="primary" disabled={busy}>{busy?'Please wait…':resetRequested?'Reset password':'Send OTP'}</button>
    </form>
    <button type="button" className="link-btn" onClick={switchToLogin}>Back to sign in</button>
  </>}
 </section></main>;
}
