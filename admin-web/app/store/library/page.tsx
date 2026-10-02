'use client';

import Link from "next/link";
import { ArrowRight, BookOpen, LibraryBig, Sparkles } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import StoreShell from "../StoreShell";
import { books, websites } from "../data";
import { ncertStudyBooks } from "../ncertStudyBooks";
import { PURCHASED_BOOKS_KEY } from "../BookPurchaseButton";

function readPurchased(): string[] {
 try { const raw=window.localStorage.getItem(PURCHASED_BOOKS_KEY); const parsed=raw?JSON.parse(raw):[]; return Array.isArray(parsed)?parsed.filter((x):x is string=>typeof x==="string"):[]; }
 catch { return []; }
}

export default function Library(){
 const [purchased,setPurchased]=useState<string[]>([]);
 useEffect(()=>{const sync=()=>setPurchased(readPurchased());sync();window.addEventListener("mpay-library-updated",sync);window.addEventListener("storage",sync);return()=>{window.removeEventListener("mpay-library-updated",sync);window.removeEventListener("storage",sync);};},[]);
 const purchasedBooks=useMemo(()=>books.filter(b=>purchased.includes(b.slug)),[purchased]);
 const purchasedStudy=useMemo(()=>ncertStudyBooks.filter(b=>purchased.includes(b.slug)),[purchased]);
 const total=purchasedBooks.length+purchasedStudy.length;
 return <StoreShell><main className="library-page">
  <section className="library-hero"><span>YOUR SHELF</span><h1>Library</h1><p>Your purchased books and free NCERT study books live together here. In this Store demo, entitlements are stored on this device.</p></section>

  <section className="library-section">
   <div className="store-section-head"><div><span>NCERT STUDY</span><h2>Classes IX–XII</h2><p>{purchasedStudy.length ? purchasedStudy.length + " NCERT textbook" + (purchasedStudy.length===1 ? "" : "s") + " in your study shelf." : "Add any NCERT textbook free from the Reading Room."}</p></div><Link href="/books">Browse study library <ArrowRight size={15}/></Link></div>
   {purchasedStudy.length ? <div className="library-books">{purchasedStudy.map(b=><div key={b.slug} className="library-book-row"><div className={"library-book-mini book-"+b.cover}>{b.subject}</div><div><b>{b.title}</b><span>{"Class "+b.classLevel+" · "+b.subject+" · Free NCERT access"}</span></div><div className="library-book-actions"><Link href={"/read/ncert-study/"+b.slug} className="library-open-link"><BookOpen size={14}/> Read & mark</Link></div></div>)}</div> : <div className="library-empty"><LibraryBig size={19}/><div><b>No NCERT textbooks added yet</b><span>Choose Class 9, 10, 11 or 12 and a subject in the NCERT Study Library.</span></div><Link href="/books" className="store-primary">Browse</Link></div>}
  </section>

  <section className="library-section">
   <div className="store-section-head"><div><span>OTHER BOOKS</span><h2>Purchased reading</h2><p>{purchasedBooks.length ? purchasedBooks.length + " purchased title" + (purchasedBooks.length===1 ? "" : "s") + "." : "No paid titles yet."}</p></div><Link href="/books">Browse books <ArrowRight size={15}/></Link></div>
   {purchasedBooks.length ? <div className="library-books">{purchasedBooks.map(b=><div key={b.slug} className="library-book-row"><div className={"library-book-mini book-"+b.cover}>{b.title}</div><div><b>{b.title}</b><span>Ready to continue reading</span></div><div className="library-book-actions"><Link href={"/read/"+b.slug} className="library-open-link"><BookOpen size={14}/> Read</Link></div></div>)}</div> : <div className="library-empty"><BookOpen size={19}/><div><b>Your purchased shelf is empty</b><span>Purchase a public-domain title from the reading room and it will appear here.</span></div><Link href="/books" className="store-primary">Browse books</Link></div>}
  </section>

  <section className="library-section"><div className="store-section-head"><div><span>WEBSITES</span><h2>Website purchases</h2><p>{total+" book entitlements are currently in this Store demo."}</p></div><Link href="/websites">Explore <ArrowRight size={15}/></Link></div><div className="library-websites">{websites.slice(0,3).map(w=><div key={w.slug} className="library-website"><Sparkles size={16}/><div><b>{w.name}</b><span>Demo access · customisation-ready</span></div><button type="button">Purchase</button></div>)}</div></section>
 </main></StoreShell>
}