'use client';
import { useState } from "react";
import Link from "next/link";
import { BookOpen, Menu, Search, ShoppingBag, Sparkles, X } from "lucide-react";
import MpayBrandUnit from "../components/MpayBrandUnit";
export default function StoreShell({children}:{children:React.ReactNode}){
 const [menu,setMenu]=useState(false);
 return <div className="store-shell">
  <header className="store-nav">
   <Link href="/" className="store-brand"><MpayBrandUnit variant="landing"/></Link>
   <nav className={`store-nav-links ${menu?"open":""}`}>
    <Link href="/websites" onClick={()=>setMenu(false)}><Sparkles size={15}/> Websites</Link>
    <Link href="/books" onClick={()=>setMenu(false)}><BookOpen size={15}/> Books</Link>
    <Link href="/library" onClick={()=>setMenu(false)}><ShoppingBag size={15}/> My Library</Link>
   </nav>
   <div className="store-nav-actions"><button className="store-icon-btn" aria-label="Search"><Search size={18}/></button><button className="store-menu-btn" onClick={()=>setMenu(v=>!v)} aria-label="Menu">{menu?<X/>:<Menu/>}</button></div>
  </header>
  {children}
  <footer className="store-footer"><div><MpayBrandUnit variant="landing"/><p>Premium digital products by mPay.</p></div><div className="store-footer-links"><Link href="/websites">Websites</Link><Link href="/books">Books</Link><Link href="/library">Library</Link></div><span>© {new Date().getFullYear()} mPay</span></footer>
 </div>;
}
