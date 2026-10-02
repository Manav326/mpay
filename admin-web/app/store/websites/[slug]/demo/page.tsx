import { ArrowRight, CalendarDays, ExternalLink, ShoppingBag, Utensils } from "lucide-react";
import Link from "next/link";
import StoreShell from "../../../StoreShell";
import { websites } from "../../../data";
export async function generateStaticParams(){return websites.map(w=>({slug:w.slug}));}
export default async function DemoLanding({params}:{params:Promise<{slug:string}>}){
 const {slug}=await params; const w=websites.find(x=>x.slug===slug)??websites[0];
 const next=w.theme==="luma"?"menu":w.theme==="estate"?"properties":w.theme==="clinic"?"doctors":w.theme==="vertex"?"case-studies":"shop";
 const kind=w.theme==="luma"?"demo-luma":w.theme==="estate"?"demo-estate":w.theme==="clinic"?"demo-clinic":w.theme==="vertex"?"demo-vertex":"demo-atelier";
 return <StoreShell><main className={`demo-shell ${w.theme}`}>
  <div className="demo-topbar"><Link href={`/websites/${w.slug}`}>← Exit demo</Link><span>{`${w.name} · live demo`}</span><Link href={`/websites/${w.slug}/demo/${next}`}>Open a route <ArrowRight size={14}/></Link></div>
  <div className={`demo-site ${kind}`}>
   <div className="demo-nav"><b>{w.name.toUpperCase()}</b><span>{w.pages[1]}</span><span>{w.pages[2]}</span><span>{w.pages[3]}</span><button>{w.theme==="estate"||w.theme==="clinic"?<CalendarDays size={14}/>:w.theme==="atelier"?<ShoppingBag size={14}/>:w.theme==="luma"?<Utensils size={14}/>:<ExternalLink size={14}/>} {w.pages[3]}</button></div>
   <div className="demo-page-block"><small>{w.category}</small><h1>{w.tagline}</h1><p>This landing route is deliberately complete enough to sell the finished site. Use the top navigation, then open deeper routes to explore the information architecture.</p><Link href={`/websites/${w.slug}/demo/${next}`} className="store-primary">Explore the next route <ArrowRight size={15}/></Link></div>
  </div>
 </main></StoreShell>
}
