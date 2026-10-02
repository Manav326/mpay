import Link from "next/link";
import { ArrowLeft, ArrowRight, BookOpen, ExternalLink, ShieldCheck } from "lucide-react";
import StoreShell from "../../StoreShell";
import { books } from "../../data";
import { ncertStudyBooksBySlug } from "../../ncertStudyBooks";
import BookPurchaseButton from "../../BookPurchaseButton";

export function generateStaticParams(){
  return [
    ...books.map(b=>({slug:b.slug})),
    ...Object.keys(ncertStudyBooksBySlug).map(slug=>({slug})),
  ];
}

export default async function BookDetail({params}:{params:Promise<{slug:string}>}){
 const {slug}=await params;
 const study=ncertStudyBooksBySlug[slug];
 if(study){
  return <StoreShell><main className="book-detail study-book-detail">
   <Link href="/books" className="detail-back"><ArrowLeft size={15}/> Back to reading room</Link>
   <section className="book-detail-hero">
    <div className={`book-big-cover book-${study.cover}`}><span>{study.title}</span><small>Class {study.classLevel} · {study.subject} · NCERT</small></div>
    <div>
      <span>NCERT STUDY LIBRARY</span><h1>{study.title}</h1><h2>Class {study.classLevel} · {study.subject}</h2>
      <p>{study.excerpt}</p>
      {study.note&&<div className="official-book-list"><strong>Textbook note</strong><span>{study.note}</span></div>}
      <div className="book-detail-actions"><BookPurchaseButton slug={study.slug} price={0}/><Link href={`/read/ncert-study/${study.slug}`} className="store-primary"><BookOpen size={16}/> Read in mPay</Link></div>
      <div className="reader-note"><ShieldCheck size={15}/><span>The textbook is read from the official NCERT PDF source. Your highlights are stored locally in mPay and can be exported as your own marked PDF.</span></div>
      <a className="source-link" href={study.portalUrl} target="_blank" rel="noreferrer">Official NCERT textbook portal <ExternalLink size={13}/></a>
    </div>
   </section>
  </main></StoreShell>;
 }
 const b=books.find(x=>x.slug===slug)??books[0];
 return <StoreShell><main className="book-detail">
  <Link href="/books" className="detail-back"><ArrowLeft size={15}/> Back to books</Link>
  <section className="book-detail-hero"><div className={`book-big-cover book-${b.cover}`}><span>{b.title}</span><small>{b.author}</small></div><div><span>{b.genre}</span><h1>{b.title}</h1><h2>{b.author}</h2><p>{b.excerpt}</p><div className="book-detail-actions"><Link href={`/read/${b.slug}`} className="store-primary"><BookOpen size={16}/> Read now</Link><BookPurchaseButton slug={b.slug} price={b.price}/></div><div className="reader-note"><ShieldCheck size={15}/><span>Reader-first delivery. The marketplace never exposes a direct download button for the book asset.</span></div><a className="source-link" href={b.source} target="_blank" rel="noreferrer">View public-domain source <ExternalLink size={13}/></a></div></section>
 </main></StoreShell>
}