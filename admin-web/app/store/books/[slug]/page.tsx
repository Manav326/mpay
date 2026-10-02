import Link from "next/link";
import { ArrowLeft, BookOpen, ExternalLink, ShieldCheck } from "lucide-react";
import StoreShell from "../../StoreShell";
import { books } from "../../data";
import BookPurchaseButton from "../../BookPurchaseButton";

export function generateStaticParams(){return books.map(b=>({slug:b.slug}));}

export default async function BookDetail({params}:{params:Promise<{slug:string}>}){
 const {slug}=await params;
 const b=books.find(x=>x.slug===slug)??books[0];

 return <StoreShell><main className="book-detail">
  <Link href="/books" className="detail-back"><ArrowLeft size={15}/> Back to books</Link>
  <section className="book-detail-hero">
   <div className={`book-big-cover book-${b.cover}`}><span>{b.title}</span><small>{b.author}</small></div>
   <div>
    <span>{b.genre}</span>
    <h1>{b.title}</h1>
    <h2>{b.author}</h2>
    <p>{b.excerpt}</p>
    {b.kind==="official" && b.officialBooks ? <div className="official-book-list">
      <strong>Included official textbooks</strong>
      {b.officialBooks.map(title=><span key={title}>{title}</span>)}
    </div> : null}
    <div className="book-detail-actions">
      {b.kind==="public-domain" ? <Link href={`/read/${b.slug}`} className="store-primary"><BookOpen size={16}/> Read sample</Link> : null}
      <BookPurchaseButton slug={b.slug} price={b.price}/>
    </div>
    <div className="reader-note">
      <ShieldCheck size={15}/>
      <span>{b.kind==="official" ? "The complete NCERT books remain hosted by NCERT. Your library stores the entitlement and opens the official textbook source." : "Reader-first delivery. The marketplace never exposes a direct download button for the book asset."}</span>
    </div>
    <a className="source-link" href={b.source} target="_blank" rel="noreferrer">
      {b.kind==="official" ? "Open official NCERT textbook portal" : "View public-domain source"} <ExternalLink size={13}/>
    </a>
   </div>
  </section>
 </main></StoreShell>
}
