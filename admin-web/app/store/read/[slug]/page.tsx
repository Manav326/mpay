'use client';
import { use, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ArrowLeft, ChevronLeft, ChevronRight, Minus, Moon, Plus, Settings2, Sun } from "lucide-react";
import { books, bookText } from "../../data";
export default function Reader({params}:{params:Promise<{slug:string}>}){
 const {slug}=use(params); const b=books.find(x=>x.slug===slug)??books[0]; const paragraphs=bookText[b.slug]??bookText.gatsby;
 const [page,setPage]=useState(0),[size,setSize]=useState(19),[dark,setDark]=useState(false),[menu,setMenu]=useState(false);
 useEffect(()=>{const saved=window.localStorage.getItem(`mpay-reader-${b.slug}`);if(saved)setPage(Number(saved)||0)},[b.slug]);
 useEffect(()=>{window.localStorage.setItem(`mpay-reader-${b.slug}`,String(page))},[b.slug,page]);
 const progress=Math.round(((page+1)/paragraphs.length)*100), text=useMemo(()=>paragraphs[page]??paragraphs[0],[paragraphs,page]);
 return <main className={`reader ${dark?"reader-dark":""}`}>
  <header className="reader-toolbar"><Link href={`/books/${b.slug}`}><ArrowLeft size={17}/><span>Library</span></Link><div className="reader-title"><small>{b.title}</small><span>Chapter I</span></div><div className="reader-actions"><button onClick={()=>setSize(s=>Math.max(15,s-1))}><Minus size={16}/></button><button onClick={()=>setSize(s=>Math.min(25,s+1))}><Plus size={16}/></button><button onClick={()=>setDark(v=>!v)}>{dark?<Sun size={16}/>:<Moon size={16}/>}</button><button onClick={()=>setMenu(v=>!v)}><Settings2 size={16}/></button></div></header>
  <div className="reader-progress"><span style={{width:`${progress}%`}}/></div>
  <section className="reader-page"><div className="reader-paper"><div className="reader-eyebrow">CHAPTER I</div><h1>{b.title}</h1><div className="reader-rule"/><p style={{fontSize:size}}>{text}</p><div className="reader-number">{page+1}</div></div></section>
  <footer className="reader-bottom"><button disabled={page===0} onClick={()=>setPage(p=>Math.max(0,p-1))}><ChevronLeft size={18}/> Previous</button><span>{progress}% · {page+1} / {paragraphs.length}</span><button disabled={page===paragraphs.length-1} onClick={()=>setPage(p=>Math.min(paragraphs.length-1,p+1))}>Next <ChevronRight size={18}/></button></footer>
  {menu&&<aside className="reader-settings"><b>Reading settings</b><label>Typeface<select><option>Inter</option><option>Georgia</option><option>System</option></select></label><label>Theme<button onClick={()=>setDark(v=>!v)}>{dark?"Dark":"Light"}</button></label><small>Progress is stored locally on this device. No in-product PDF download control is exposed.</small></aside>}
 </main>
}
