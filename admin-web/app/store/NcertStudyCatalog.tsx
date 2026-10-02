'use client';

import Link from "next/link";
import { BookOpen, Filter } from "lucide-react";
import { useMemo, useState } from "react";
import { ncertStudyBooks } from "./ncertStudyBooks";
import BookPurchaseButton from "./BookPurchaseButton";

export default function NcertStudyCatalog(){
 const [classFilter,setClassFilter]=useState<"all"|9|10|11|12>("all");
 const [subjectFilter,setSubjectFilter]=useState<"all"|"Geography"|"History"|"Economics">("all");
 const items=useMemo(()=>ncertStudyBooks.filter(book=>(classFilter==="all"||book.classLevel===classFilter)&&(subjectFilter==="all"||book.subject===subjectFilter)),[classFilter,subjectFilter]);
 return <section className="ncert-catalog-section">
  <div className="store-section-head">
   <div><span>NCERT STUDY LIBRARY</span><h2>Classes IX–XII · Geography · History · Economics</h2><p>Free access to the official NCERT PDFs, organized by class and subject. Read them in mPay and keep your own highlights.</p></div>
   <Filter size={18}/>
  </div>
  <div className="study-filters">
   <div className="study-filter-group"><span>Class</span>{(["all",9,10,11,12] as const).map(value=><button key={String(value)} className={classFilter===value?"active":""} onClick={()=>setClassFilter(value)}>{value==="all"?"All":`Class ${value}`}</button>)}</div>
   <div className="study-filter-group"><span>Subject</span>{(["all","Geography","History","Economics"] as const).map(value=><button key={value} className={subjectFilter===value?"active":""} onClick={()=>setSubjectFilter(value)}>{value==="all"?"All":value}</button>)}</div>
  </div>
  <div className="study-book-grid">
   {items.map(book=><article key={book.slug} className={`study-book-card book-${book.cover}`}>
     <Link href={`/books/${book.slug}`} className="study-book-cover"><small>CLASS {book.classLevel}</small><b>{book.subject}</b><strong>{book.title}</strong><span>NCERT</span></Link>
     <div className="study-book-copy">
       <div><span>Class {book.classLevel} · {book.subject}</span><h3>{book.title}</h3><p>{book.excerpt}</p></div>
       <div className="study-book-actions"><Link href={`/books/${book.slug}`} className="library-open-link"><BookOpen size={14}/> Details</Link><BookPurchaseButton slug={book.slug} price={0}/></div>
     </div>
   </article>)}
  </div>
 </section>;
}
