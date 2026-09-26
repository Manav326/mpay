'use client';
import { FormEvent, useEffect, useState } from 'react';
import { ArrowLeft, ShieldCheck, UserPlus } from 'lucide-react';

const base = process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, '') || 'http://localhost:8080';

async function apiMessage(response: Response, fallback: string) {
  const body = await response.json().catch(() => null);
  return body?.message || fallback;
}

export default function UserLogin() {
  const [mode, setMode] = useState<'login' | 'forgot'>('login');
  const [mobile, setMobile] = useState('');
  const [password, setPassword] = useState('');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [resetRequested, setResetRequested] = useState(false);
  const [resendAfter, setResendAfter] = useState(0);
  const [demoOtp, setDemoOtp] = useState<string | null>(null);

  useEffect(() => {
    if (resendAfter <= 0) return;
    const timer = window.setTimeout(() => setResendAfter(value => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [resendAfter]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setNotice('');
    try {
      const r = await fetch(base + '/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mobile, password })
      });
      if (!r.ok) throw new Error(await apiMessage(r, 'Login failed.'));
      const d = await r.json();
      localStorage.setItem('mpay_token', d.accessToken);
      localStorage.setItem('mpay_refresh_token', d.refreshToken);
      window.location.href = '/portal';
    } catch (e: any) {
      setNotice(e.message || 'Unable to sign in.');
    } finally {
      setBusy(false);
    }
  }

  async function requestResetOtp() {
    if (mobile.length !== 10 || busy || resendAfter > 0) return;
    setBusy(true);
    setNotice('');
    try {
      const r = await fetch(base + '/api/v1/auth/forgot-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mobile })
      });
      if (!r.ok) throw new Error(await apiMessage(r, 'Unable to request password reset.'));
      const d = await r.json();
      setResetRequested(true);
      setResendAfter(Number(d.resendAfterSeconds || 60));
      setDemoOtp(d.deliveryMode?.toLowerCase() === 'mock' ? d.demoOtp || null : null);
      setNotice('OTP sent. Enter it below to create a new password.');
    } catch (e: any) {
      setNotice(e.message || 'Unable to request password reset.');
    } finally {
      setBusy(false);
    }
  }

  async function reset(e: FormEvent) {
    e.preventDefault();
    if (!resetRequested) {
      await requestResetOtp();
      return;
    }
    setBusy(true);
    setNotice('');
    try {
      const r = await fetch(base + '/api/v1/auth/reset-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mobile, otp, newPassword })
      });
      if (!r.ok) throw new Error(await apiMessage(r, 'Unable to reset password.'));
      setMode('login');
      setResetRequested(false);
      setOtp('');
      setNewPassword('');
      setResendAfter(0);
      setDemoOtp(null);
      setNotice('Password reset successful. You can now sign in.');
    } catch (e: any) {
      setNotice(e.message || 'Unable to reset password.');
    } finally {
      setBusy(false);
    }
  }

  function switchToLogin() {
    setMode('login');
    setResetRequested(false);
    setOtp('');
    setNewPassword('');
    setResendAfter(0);
    setDemoOtp(null);
    setNotice('');
  }

  return <main className="portal-auth-page"><section className="portal-auth-card">
    <a href="/" className="back-link"><ArrowLeft size={16} /> Back to mPay</a>
    <div className="admin-login-brand"><img src="/mpay-logo.png" alt="mPay" /><div><strong>mPay</strong><span>Customer account</span></div></div>
    {mode === 'login' ? <>
      <div className="admin-login-copy"><div className="secure-badge"><ShieldCheck size={15} /> Secure sign in</div><h1>Welcome back.</h1><p>Sign in to recharge, manage your wallet and keep track of every transaction.</p></div>
      <form className="form" onSubmit={submit}>
        <label>Mobile number<input inputMode="numeric" maxLength={10} value={mobile} onChange={e => setMobile(e.target.value.replace(/\D/g, ''))} placeholder="10-digit mobile number" autoComplete="username" /></label>
        <label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="Enter password" autoComplete="current-password" /></label>
        {notice && <div className="alert">{notice}</div>}
        <button className="primary" disabled={busy || mobile.length !== 10 || !password}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
      <button type="button" className="link-btn" onClick={() => { setMode('forgot'); setNotice(''); }}>Forgot password?</button>
      <a className="auth-alt-link" href="/signup"><UserPlus size={15} /> New to mPay? Create an account</a>
    </> : <>
      <div className="admin-login-copy"><div className="secure-badge"><ShieldCheck size={15} /> Secure recovery</div><h1>Reset your password.</h1><p>We’ll send a one-time code to your registered mobile number.</p></div>
      <form className="form" onSubmit={reset}>
        <label>Registered mobile<input inputMode="numeric" maxLength={10} value={mobile} disabled={resetRequested} onChange={e => setMobile(e.target.value.replace(/\D/g, ''))} placeholder="10-digit mobile number" /></label>
        {resetRequested && demoOtp && <div className="alert">Development OTP: <strong>{demoOtp}</strong></div>}
        {resetRequested && <label>OTP<input inputMode="numeric" maxLength={6} value={otp} onChange={e => setOtp(e.target.value.replace(/\D/g, ''))} placeholder="6-digit OTP" autoComplete="one-time-code" /></label>}
        {resetRequested && <label>New password<input type="password" minLength={8} value={newPassword} onChange={e => setNewPassword(e.target.value)} placeholder="Enter new password" autoComplete="new-password" /></label>}
        {notice && <div className="alert">{notice}</div>}
        {!resetRequested ? <button className="primary" disabled={busy || mobile.length !== 10}>{busy ? 'Sending OTP…' : 'Send OTP'}</button> : <button className="primary" disabled={busy || otp.length !== 6 || newPassword.length < 8}>{busy ? 'Resetting…' : 'Reset password'}</button>}
      </form>
      {resetRequested && <button type="button" className="link-btn" onClick={requestResetOtp} disabled={busy || resendAfter > 0}>{resendAfter > 0 ? 'Resend in ' + resendAfter + 's' : 'Resend OTP'}</button>}
      <button type="button" className="link-btn" onClick={switchToLogin}>Back to sign in</button>
    </>}
  </section></main>;
}