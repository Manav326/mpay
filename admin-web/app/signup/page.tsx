'use client';

import { FormEvent, useEffect, useState } from 'react';
import { ArrowLeft, CheckCircle2, ShieldCheck } from 'lucide-react';

const base = process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, '') || 'http://localhost:8080';

async function apiMessage(response: Response, fallback: string) {
  const body = await response.json().catch(() => null);
  return body?.message || fallback;
}

export default function Signup() {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [mobile, setMobile] = useState('');
  const [password, setPassword] = useState('');
  const [otp, setOtp] = useState('');
  const [busy, setBusy] = useState(false);
  const [otpBusy, setOtpBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [otpMode, setOtpMode] = useState(false);
  const [maskedMobile, setMaskedMobile] = useState('');
  const [verificationToken, setVerificationToken] = useState<string | null>(null);
  const [demoOtp, setDemoOtp] = useState<string | null>(null);
  const [resendAfter, setResendAfter] = useState(0);

  useEffect(() => {
    if (resendAfter <= 0) return;
    const timer = window.setTimeout(() => setResendAfter(value => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [resendAfter]);

  async function sendOtp() {
    if (mobile.length !== 10 || otpBusy || resendAfter > 0) return;
    setOtpBusy(true);
    setNotice('');
    try {
      const r = await fetch(base + '/api/v1/auth/otp/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mobile, purpose: 'REGISTRATION' })
      });
      if (!r.ok) throw new Error(await apiMessage(r, 'Unable to send OTP.'));
      const d = await r.json();
      setOtpMode(true);
      setMaskedMobile(d.maskedMobile || ('******' + mobile.slice(-4)));
      setResendAfter(Number(d.resendAfterSeconds || 60));
      setDemoOtp(d.deliveryMode?.toLowerCase() === 'mock' ? d.demoOtp || null : null);
      setNotice('OTP sent. Enter the code to verify your mobile number.');
    } catch (e: any) {
      setNotice(e.message || 'Unable to send OTP.');
    } finally {
      setOtpBusy(false);
    }
  }

  async function verifyOtp() {
    if (otp.length !== 6 || otpBusy) return;
    setOtpBusy(true);
    setNotice('');
    try {
      const r = await fetch(base + '/api/v1/auth/otp/verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mobile, otp, purpose: 'REGISTRATION' })
      });
      if (!r.ok) throw new Error(await apiMessage(r, 'Invalid OTP.'));
      const d = await r.json();
      if (!d.verificationToken) throw new Error('Mobile verification could not be completed.');
      setVerificationToken(d.verificationToken);
      setOtp('');
      setNotice('Mobile number verified successfully.');
    } catch (e: any) {
      setNotice(e.message || 'Unable to verify OTP.');
    } finally {
      setOtpBusy(false);
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setNotice('');
    try {
      const r = await fetch(base + '/api/v1/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: name || null,
          email: email || null,
          mobile,
          password,
          mobileVerificationToken: verificationToken
        })
      });
      if (!r.ok) throw new Error(await apiMessage(r, 'Registration failed.'));
      const d = await r.json();
      localStorage.setItem('mpay_token', d.accessToken);
      localStorage.setItem('mpay_refresh_token', d.refreshToken);
      window.location.href = '/portal';
    } catch (e: any) {
      setNotice(e.message || 'Unable to create account.');
    } finally {
      setBusy(false);
    }
  }

  const passwordValid = password.length >= 8;
  const emailValid = !email || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  const canCreate = !busy && name.trim().length > 0 && mobile.length === 10 && passwordValid && emailValid;

  function resetVerification() {
    setOtpMode(false);
    setVerificationToken(null);
    setOtp('');
    setDemoOtp(null);
    setResendAfter(0);
    setNotice('');
  }

  return (
    <main className="portal-auth-page">
      <section className="portal-auth-card">
        <a href="/" className="back-link"><ArrowLeft size={16} /> Back to mPay</a>
        <div className="admin-login-brand"><img src="/mpay-logo.png" alt="mPay" /><div><strong>mPay</strong><span>Create your account</span></div></div>
        <div className="admin-login-copy">
          <div className="secure-badge"><ShieldCheck size={15} /> Secure registration</div>
          <h1>Start with mPay.</h1>
          <p>Create your account and optionally verify your mobile number before you begin.</p>
        </div>
        <form className="form" onSubmit={submit}>
          <label>Name<input value={name} onChange={e => setName(e.target.value)} placeholder="Your name" autoComplete="name" /></label>
          <label>Email<input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="you@example.com" autoComplete="email" /></label>
          <label>Mobile number<input inputMode="numeric" maxLength={10} value={mobile} disabled={verificationToken !== null || otpMode} onChange={e => { const next = e.target.value.replace(/\D/g, ''); setMobile(next); if (otpMode) resetVerification(); }} placeholder="10-digit mobile number" autoComplete="tel" /></label>

          {verificationToken ? (
            <div className="alert" style={{ borderColor: '#b9ddc3', background: '#f2fbf5', color: '#21623a', display: 'flex', alignItems: 'center', gap: 8 }}>
              <CheckCircle2 size={16} />
              <span>Mobile number verified. <button type="button" className="link-btn" onClick={resetVerification}>Change</button></span>
            </div>
          ) : !otpMode ? (
            <div>
              <button type="button" className="secondary" onClick={sendOtp} disabled={mobile.length !== 10 || otpBusy}>
                {otpBusy ? 'Sending OTP…' : 'Verify mobile number'}
              </button>
              <div style={{ marginTop: 7, fontSize: 13, color: 'var(--text-secondary)' }}>Optional. You can create the account without mobile verification.</div>
            </div>
          ) : (
            <div className="form" style={{ gap: 10 }}>
              <div style={{ fontSize: 14, color: 'var(--text-secondary)' }}>OTP sent to {maskedMobile || 'your mobile number'}</div>
              {demoOtp && <div className="alert">Development OTP: <strong>{demoOtp}</strong></div>}
              <label>OTP<input inputMode="numeric" maxLength={6} value={otp} onChange={e => setOtp(e.target.value.replace(/\D/g, ''))} placeholder="6-digit OTP" autoComplete="one-time-code" /></label>
              <button type="button" className="secondary" onClick={verifyOtp} disabled={otp.length !== 6 || otpBusy}>{otpBusy ? 'Verifying…' : 'Verify OTP'}</button>
              <button type="button" className="link-btn" onClick={sendOtp} disabled={resendAfter > 0 || otpBusy}>
                {resendAfter > 0 ? 'Resend in ' + resendAfter + 's' : 'Resend OTP'}
              </button>
              <button type="button" className="link-btn" onClick={resetVerification}>Skip verification</button>
            </div>
          )}

          <label>Password<input type="password" minLength={8} value={password} onChange={e => setPassword(e.target.value)} placeholder="At least 8 characters" autoComplete="new-password" /></label>
          {notice && <div className="alert">{notice}</div>}
          <button className="primary" disabled={!canCreate}>{busy ? 'Creating account…' : 'Create account'}</button>
        </form>
        <a className="auth-alt-link" href="/login">Already have an account? Sign in</a>
      </section>
    </main>
  );
}