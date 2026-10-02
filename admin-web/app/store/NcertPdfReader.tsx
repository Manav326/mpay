'use client';

import Link from "next/link";
import { ArrowLeft, ChevronLeft, ChevronRight, Download, Eraser, Highlighter, List, Minus, Plus, RotateCcw } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { ncertStudyBooksBySlug } from "./ncertStudyBooks";
import type { NcertStudyBook } from "./ncertStudyBooks";

type Point={x:number;y:number};
type Color="yellow"|"green";
type Stroke={color:Color;points:Point[]};

const COLORS:Record<Color,{label:string;value:string;pdf:number[]}> = {
  yellow:{label:"Yellow",value:"#f6d34a",pdf:[1,0.82,0.1]},
  green:{label:"Light green",value:"#98df8a",pdf:[0.45,0.82,0.38]},
};

function keyFor(bookId:string){return `mpay-study-markers-${bookId}`;}

function readSaved(bookId:string):Record<number,Stroke[]>{
  try {
    const raw=window.localStorage.getItem(keyFor(bookId));
    const parsed=raw?JSON.parse(raw):{};
    return parsed && typeof parsed==="object" ? parsed : {};
  } catch { return {}; }
}

export default function NcertPdfReader({id}:{id:string}){
 const book=ncertStudyBooksBySlug[id] as NcertStudyBook|undefined;
 const [pdf,setPdf]=useState<any>(null);
 const [pageNumber,setPageNumber]=useState(1);
 const [numPages,setNumPages]=useState(0);
 const [zoom,setZoom]=useState(1);
 const [color,setColor]=useState<Color>("yellow");
 const [markers,setMarkers]=useState<Record<number,Stroke[]>>({});
 const [loading,setLoading]=useState(true);
 const [error,setError]=useState("");
 const [showLibrary,setShowLibrary]=useState(false);
 const [isExporting,setIsExporting]=useState(false);
 const canvasRef=useRef<HTMLCanvasElement|null>(null);
 const overlayRef=useRef<HTMLCanvasElement|null>(null);
 const pageHolderRef=useRef<HTMLDivElement|null>(null);
 const draftRef=useRef<Point[]|null>(null);

 useEffect(()=>{
   if(!book) return;
   setMarkers(readSaved(book.slug));
   setPageNumber(1);
 },[book]);

 useEffect(()=>{
   if(!book) return;
   try{window.localStorage.setItem(keyFor(book.slug),JSON.stringify(markers));}catch{}
 },[book,markers]);

 useEffect(()=>{
   let disposed=false;
   async function load(){
     if(!book) return;
     setLoading(true);setError("");
     try{
       const pdfjs:any=await import("pdfjs-dist");
       pdfjs.GlobalWorkerOptions.workerSrc="https://cdnjs.cloudflare.com/ajax/libs/pdf.js/4.10.38/pdf.worker.min.mjs";
       const document=await pdfjs.getDocument({url:book.pdfUrl}).promise;
       if(disposed) return;
       setPdf(document);
       setNumPages(document.numPages);
     }catch(e){
       if(!disposed) setError("The official NCERT PDF could not be loaded in the browser. Please retry, or use the NCERT source link.");
     }finally{
       if(!disposed) setLoading(false);
     }
   }
   load();
   return()=>{disposed=true;};
 },[book]);

 useEffect(()=>{
   let cancelled=false;
   async function render(){
     if(!pdf || !canvasRef.current || !overlayRef.current || !pageHolderRef.current) return;
     const page=await pdf.getPage(pageNumber);
     if(cancelled) return;
     const base=page.getViewport({scale:1});
     const available=Math.max(320,pageHolderRef.current.clientWidth-32);
     const scale=Math.max(.7,Math.min(zoom,available/base.width));
     const viewport=page.getViewport({scale});
     const canvas=canvasRef.current;
     const overlay=overlayRef.current;
     const ratio=window.devicePixelRatio||1;
     canvas.width=Math.floor(viewport.width*ratio);
     canvas.height=Math.floor(viewport.height*ratio);
     canvas.style.width=`${viewport.width}px`;
     canvas.style.height=`${viewport.height}px`;
     overlay.width=Math.floor(viewport.width*ratio);
     overlay.height=Math.floor(viewport.height*ratio);
     overlay.style.width=`${viewport.width}px`;
     overlay.style.height=`${viewport.height}px`;
     const ctx=canvas.getContext("2d");
     if(!ctx) return;
     ctx.setTransform(ratio,0,0,ratio,0,0);
     await page.render({canvasContext:ctx,viewport}).promise;
     if(cancelled) return;
     drawAnnotations();
   }
   render().catch(()=>{});
   function drawAnnotations(){
     const overlay=overlayRef.current;
     if(!overlay) return;
     const ctx=overlay.getContext("2d");
     if(!ctx) return;
     const ratio=window.devicePixelRatio||1;
     const width=parseFloat(overlay.style.width)||overlay.clientWidth;
     const height=parseFloat(overlay.style.height)||overlay.clientHeight;
     ctx.setTransform(ratio,0,0,ratio,0,0);
     ctx.clearRect(0,0,width,height);
     for(const stroke of markers[pageNumber]||[]){
       if(stroke.points.length<2) continue;
       ctx.save();
       ctx.globalAlpha=.35;
       ctx.strokeStyle=COLORS[stroke.color].value;
       ctx.lineWidth=18;
       ctx.lineCap="round";
       ctx.lineJoin="round";
       ctx.beginPath();
       stroke.points.forEach((p,i)=>{const x=p.x*width,y=p.y*height;i?ctx.lineTo(x,y):ctx.moveTo(x,y);});
       ctx.stroke();
       ctx.restore();
     }
   }
   return()=>{cancelled=true;};
 },[pdf,pageNumber,zoom,markers]);

 function pointFromEvent(event:React.PointerEvent<HTMLCanvasElement>){
   const rect=event.currentTarget.getBoundingClientRect();
   return {x:Math.max(0,Math.min(1,(event.clientX-rect.left)/rect.width)),y:Math.max(0,Math.min(1,(event.clientY-rect.top)/rect.height))};
 }

 function redrawDraft(points:Point[]){
   const overlay=overlayRef.current;if(!overlay)return;
   const ctx=overlay.getContext("2d");if(!ctx)return;
   const width=overlay.clientWidth,height=overlay.clientHeight;
   const ratio=window.devicePixelRatio||1;
   ctx.setTransform(ratio,0,0,ratio,0,0);
   ctx.clearRect(0,0,width,height);
   for(const stroke of markers[pageNumber]||[]){
     if(stroke.points.length<2) continue;
     ctx.save();ctx.globalAlpha=.35;ctx.strokeStyle=COLORS[stroke.color].value;ctx.lineWidth=18;ctx.lineCap="round";ctx.lineJoin="round";ctx.beginPath();
     stroke.points.forEach((p,i)=>{const x=p.x*width,y=p.y*height;i?ctx.lineTo(x,y):ctx.moveTo(x,y);});ctx.stroke();ctx.restore();
   }
   if(points.length>1){
     ctx.save();ctx.globalAlpha=.35;ctx.strokeStyle=COLORS[color].value;ctx.lineWidth=18;ctx.lineCap="round";ctx.lineJoin="round";ctx.beginPath();
     points.forEach((p,i)=>{const x=p.x*width,y=p.y*height;i?ctx.lineTo(x,y):ctx.moveTo(x,y);});ctx.stroke();ctx.restore();
   }
 }

 function pointerDown(event:React.PointerEvent<HTMLCanvasElement>){
   event.currentTarget.setPointerCapture(event.pointerId);
   draftRef.current=[pointFromEvent(event)];
   redrawDraft(draftRef.current);
 }
 function pointerMove(event:React.PointerEvent<HTMLCanvasElement>){
   if(!draftRef.current)return;
   draftRef.current=[...draftRef.current,pointFromEvent(event)];
   redrawDraft(draftRef.current);
 }
 function pointerUp(){
   const points=draftRef.current;draftRef.current=null;
   if(!points||points.length<2)return;
   setMarkers(current=>({...current,[pageNumber]:[...(current[pageNumber]||[]),{color,points}]}));
 }

 function clearPage(){
   setMarkers(current=>({...current,[pageNumber]:[]}));
 }

 async function exportMarkedPdf(){
   if(!book || Object.values(markers).every((items)=>items.length===0)) return;
   setIsExporting(true);
   try{
     const source=await fetch(book.pdfUrl,{mode:"cors"});
     if(!source.ok) throw new Error("PDF source unavailable");
     const bytes=await source.arrayBuffer();
     const {PDFDocument,rgb}=await import("pdf-lib");
     const sourceDoc=await PDFDocument.load(bytes);
     const output=await PDFDocument.create();
     const copied=await output.copyPages(sourceDoc,sourceDoc.getPageIndices());
     copied.forEach((page,index)=>{
       output.addPage(page);
       const pdfPage=output.getPages()[index];
       const width=pdfPage.getWidth(),height=pdfPage.getHeight();
       for(const stroke of markers[index+1]||[]){
         for(let i=1;i<stroke.points.length;i++){
           const a=stroke.points[i-1],b=stroke.points[i];
           pdfPage.drawLine({
             start:{x:a.x*width,y:height-a.y*height},
             end:{x:b.x*width,y:height-b.y*height},
             thickness:14,
             color:rgb(...COLORS[stroke.color].pdf as [number,number,number]),
             opacity:.34,
           });
         }
       }
     });
     const outputBytes=await output.save();
     const blob=new Blob([outputBytes],{type:"application/pdf"});
     const url=URL.createObjectURL(blob);
     const anchor=document.createElement("a");
     anchor.href=url;
     anchor.download=`${book.title.replace(/[^a-z0-9]+/gi,"-").toLowerCase()}-marked.pdf`;
     anchor.click();
     URL.revokeObjectURL(url);
   }catch{
     setError("Marked-PDF export needs the NCERT PDF to allow browser access. The reader itself can still use the official NCERT source.");
   }finally{setIsExporting(false);}
 }

 if(!book) return <main className="reader"><div className="reader-page"><div className="reader-paper"><h1>Book not found</h1><Link href="/books">Back to books</Link></div></div></main>;

 return <main className="reader study-reader">
   <header className="reader-toolbar">
     <Link href="/library"><ArrowLeft size={17}/><span>Library</span></Link>
     <div className="reader-title"><small>{book.title}</small><span>Class {book.classLevel} · {book.subject} · NCERT</span></div>
     <div className="reader-actions">
       <button type="button" onClick={()=>setShowLibrary(v=>!v)}><List size={16}/></button>
       <button type="button" onClick={()=>setZoom(z=>Math.max(.75,z-.1))}><Minus size={16}/></button>
       <button type="button" onClick={()=>setZoom(z=>Math.min(1.6,z+.1))}><Plus size={16}/></button>
       <button type="button" onClick={clearPage}><Eraser size={16}/></button>
       <button type="button" className={color==="yellow"?"active":""} onClick={()=>setColor("yellow")} title="Yellow marker"><Highlighter size={16}/></button>
       <button type="button" className={color==="green"?"active":""} onClick={()=>setColor("green")} title="Light green marker"><span className="study-color-dot green"/></button>
       <button type="button" disabled={isExporting||Object.values(markers).every((items)=>items.length===0)} onClick={exportMarkedPdf}><Download size={16}/></button>
     </div>
   </header>

   <div className="study-reader-meta">
     <div><span>STUDY READER</span><b>Highlight important lines with yellow or light green.</b></div>
     <a href={book.portalUrl} target="_blank" rel="noreferrer">Official NCERT source</a>
   </div>

   <div className="reader-progress"><span style={{width:`${numPages?Math.round(pageNumber/numPages*100):0}%`}}/></div>

   <div className="study-reader-body">
     {showLibrary && <aside className={"study-book-drawer"+(showLibrary ? " open" : "")}>
       <div className="study-drawer-head"><span>YOUR STUDY SHELF</span><b>Classes IX–XII</b></div>
       {([9,10,11,12] as const).map(level=>{
         const same=ncertStudyBooksBySlug;
         const items=Object.values(same).filter((item)=>item.classLevel===level);
         return <div key={level} className="study-drawer-group"><small>Class {level}</small>{items.map(item=><Link className={item.slug===book.slug?"active":""} key={item.slug} href={`/read/ncert-study/${item.slug}`}>{item.subject}<span>{item.title}</span></Link>)}</div>;
       })}
     </aside>}

     <section className="study-reader-stage">
       {book.note&&<div className="study-reader-note">{book.note}</div>}
       {loading&&<div className="study-loading">Loading the official NCERT PDF…</div>}
       {error&&<div className="study-error">{error}</div>}
       <div className="study-page-shell" ref={pageHolderRef}>
         <div className="study-page-canvas">
           <canvas ref={canvasRef}/>
           <canvas ref={overlayRef} className="study-marker-layer" onPointerDown={pointerDown} onPointerMove={pointerMove} onPointerUp={pointerUp} onPointerCancel={pointerUp}/>
         </div>
       </div>
       <div className="study-page-controls">
         <button type="button" disabled={pageNumber<=1} onClick={()=>setPageNumber(p=>Math.max(1,p-1))}><ChevronLeft size={17}/> Previous</button>
         <label>Page <input value={pageNumber} onChange={e=>setPageNumber(Math.max(1,Math.min(numPages||1,Number(e.target.value)||1)))} inputMode="numeric"/> / {numPages||"—"}</label>
         <button type="button" disabled={pageNumber>=numPages} onClick={()=>setPageNumber(p=>Math.min(numPages,p+1))}>Next <ChevronRight size={17}/></button>
         <button type="button" onClick={()=>setMarkers({})}><RotateCcw size={15}/> Clear all marks</button>
       </div>
     </section>
   </div>
 </main>;
}
