'use client';

import Link from "next/link";
import { ArrowRight, BookOpen, ExternalLink, LibraryBig, Sparkles } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import StoreShell from "../StoreShell";
import { books, websites } from "../data";
import { PURCHASED_BOOKS_KEY } from "../BookPurchaseButton";

function readPurchased(): string[] {
  try {
    const raw = window.localStorage.getItem(PURCHASED_BOOKS_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === "string") : [];
  } catch {
    return [];
  }
}

export default function Library(){
 const [purchased,setPurchased]=useState<string[]>([]);

 useEffect(()=>{
  const sync=()=>setPurchased(readPurchased());
  sync();
  window.addEventListener("mpay-library-updated",sync);
  window.addEventListener("storage",sync);
  return ()=>{window.removeEventListener("mpay-library-updated",sync);window.removeEventListener("storage",sync);};
 },[]);

 const purchasedBooks=useMemo(()=>books.filter(b=>purchased.includes(b.slug)),[purchased]);

 return <StoreShell><main className="library-page">
  <section className="library-hero">
   <span>YOUR SHELF</span><h1>Library</h1>
   <p>Your purchased books and free library additions live here. Book entitlements are stored on this device in this Store demo.</p>
  </section>

  <section className="library-section">
   <div className="store-section-head">
    <div><span>PURCHASED</span><h2>Your books</h2><p>{purchasedBooks.length ? `${purchasedBooks.length} book entitlement${purchasedBooks.length===1?"":"s"} in your library.` : "Purchase a book to add it here."}</p></div>
    <Link href="/books">Browse books <ArrowRight size={15}/></Link>
   </div>

   {purchasedBooks.length ? <div className="library-books">
    {purchasedBooks.map(b=>{
      const official=b.kind==="official";
      return <div key={b.slug} className="library-book-row">
       <div className={`library-book-mini book-${b.cover}`}>{b.title}</div>
       <div><b>{b.title}</b><span>{official ? "Complete official NCERT set" : "Ready to continue reading"}</span></div>
       <div className="library-book-actions">
        {official ? <Link href={`/read/ncert/${b.slug === "ncert-geography-class-11" ? "class-11-fundamentals-physical-geography" : "class-12-fundamentals-human-geography"}`} className="library-open-link"><BookOpen size={14}/> Read complete set</Link> : <Link href={`/read/${b.slug}`} className="library-open-link"><BookOpen size={14}/> Read</Link>}
       </div>
      </div>;
    })}
   </div> : <div className="library-empty"><LibraryBig size={19}/><div><b>Your purchased shelf is empty</b><span>Choose a book from the catalogue and it will appear here immediately.</span></div><Link href="/books" className="store-primary">Browse books</Link></div>}
  </section>

  <section className="library-section">
   <div className="store-section-head"><div><span>WEBsites</span><h2>Website purchases</h2></div><Link href="/websites">Explore <ArrowRight size={15}/></Link></div>
   <div className="library-websites">{websites.slice(0,3).map(w=><div key={w.slug} className="library-website"><Sparkles size={16}/><div><b>{w.name}</b><span>Demo access · customisation-ready</span></div><button type="button">Purchase</button></div>)}</div>
  </section>
 </main></StoreShell>
}
