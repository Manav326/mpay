'use client';

import { useState } from 'react';
import { ArrowRight, CheckCircle2, ChevronDown, Menu, ShieldCheck, Smartphone, UserRound, WalletCards, X, Zap } from 'lucide-react';

function Brand(){return <a className="landing-brand" href="/"><img src="/mpay-logo.png" alt="mPay"/><span>mPay</span></a>}

export default function LandingPage(){
  const [open,setOpen]=useState(false);
  return <main className="landing">
    <header className="landing-nav">
      <Brand/>
      <nav className="landing-links"><a href="#about">About mPay</a><a href="#how">How it works</a><a href="#security">Security</a></nav>
      <div className="landing-account">
        <button className="account-trigger" aria-label="Open account menu" onClick={()=>setOpen(v=>!v)}><UserRound size={19}/><ChevronDown size={14}/></button>
        {open&&<div className="account-menu"><div className="account-menu-title">mPay account</div><a href="/login"><UserRound size={16}/> User login</a><a href="/signup"><Zap size={16}/> Create account</a><a href="/admin/login"><ShieldCheck size={16}/> Admin / Manager</a></div>}
      </div>
    </header>

    <section className="hero" id="about">
      <div className="hero-copy">
        <div className="hero-kicker"><span className="pulse-dot"/> Digital payments & everyday services</div>
        <h1>One simple place for <span>everyday payments.</span></h1>
        <p>mPay brings mobile recharge, wallet management and convenient digital services into one clean, secure experience — built for customers and the businesses that serve them.</p>
        <div className="hero-actions"><a className="landing-primary" href="/signup">Get started <ArrowRight size={17}/></a><a className="landing-secondary" href="#how">Explore mPay</a></div>
        <div className="trust-row"><span><CheckCircle2 size={15}/> Clear transaction history</span><span><ShieldCheck size={15}/> Role-based access</span><span><WalletCards size={15}/> Wallet controls</span></div>
      </div>
      <div className="hero-visual" aria-hidden="true">
        <div className="orb orb-a"/><div className="orb orb-b"/>
        <div className="phone-mock">
          <div className="phone-top"><span>mPay</span><span>•••</span></div>
          <div className="phone-balance"><small>Available balance</small><strong>₹ 8,420.00</strong><span>Ready for your next payment</span></div>
          <div className="phone-actions"><div><Smartphone size={18}/><span>Recharge</span></div><div><WalletCards size={18}/><span>Wallet</span></div><div><Zap size={18}/><span>Services</span></div></div>
          <div className="phone-card"><div><span>Recent recharge</span><b>₹299</b></div><small>Mobile • Successful</small></div>
          <div className="scan-line"/>
        </div>
        <div className="float-card float-one"><CheckCircle2 size={17}/><div><b>Payment tracked</b><span>Clear status & history</span></div></div>
        <div className="float-card float-two"><ShieldCheck size={17}/><div><b>Secure by design</b><span>Protected account access</span></div></div>
      </div>
    </section>

    <section className="landing-section" id="how"><div className="section-heading"><span>WHAT WE DO</span><h2>Designed around the moments that matter.</h2><p>mPay keeps the everyday payment journey straightforward, transparent and easy to understand.</p></div>
      <div className="feature-grid">
        <article><div className="feature-icon"><Smartphone/></div><h3>Mobile recharge</h3><p>Detect operators, discover plans and keep every recharge attempt visible from start to finish.</p></article>
        <article><div className="feature-icon"><WalletCards/></div><h3>Wallet-first payments</h3><p>See available and reserved funds separately, with a clear ledger for money added, spent or withdrawn.</p></article>
        <article><div className="feature-icon"><ShieldCheck/></div><h3>Responsible access</h3><p>Customers, managers and administrators receive role-appropriate access and operational visibility.</p></article>
      </div>
    </section>

    <section className="landing-story" id="security"><div className="story-panel"><div><span>BUILT FOR TRUST</span><h2>Simple on the surface.<br/><em>Thoughtful underneath.</em></h2></div><div className="story-points"><p><b>01</b> Transaction states remain visible instead of hiding pending or failed activity.</p><p><b>02</b> Wallet availability and reserved amounts are presented separately for clarity.</p><p><b>03</b> Operational portals use role-based controls and a read-only customer view where appropriate.</p></div></div></section>

    <footer className="landing-footer"><Brand/><span>© {new Date().getFullYear()} mPay. Digital payments, made clearer.</span><div><a href="/privacy-policy">Privacy Policy</a><a href="/delete-account">Delete account</a><a href="/admin/login">Admin portal</a><a href="/login">Login</a></div></footer>
  </main>;
}