import Link from "next/link";
import { ArrowRight, BookOpen, CheckCircle2, Globe2, Layers3, Sparkles } from "lucide-react";
import StoreShell from "./StoreShell";
import { books, websites } from "./data";
export default function StoreHome(){
 return <StoreShell>
  <main className="store-home">
   <section className="store-hero">
    <div className="store-hero-copy">
     <div className="store-kicker"><span className="store-live-dot"/> mPay digital marketplace</div>
     <h1>Beautiful digital products, <em>ready to launch.</em></h1>
     <p>Premium multi-page websites you can demo before you buy, alongside a calm reading library designed for the way people actually read.</p>
     <div className="store-hero-actions"><Link href="/websites" className="store-primary">Explore websites <ArrowRight size={17}/></Link><Link href="/books" className="store-secondary">Browse books</Link></div>
     <div className="store-proof"><span><CheckCircle2 size={15}/> Real live demos</span><span><Layers3 size={15}/> Multi-route products</span><span><BookOpen size={15}/> Kindle-style reading</span></div>
    </div>
    <div className="store-hero-orbit"><div className="orbit orbit-one"/><div className="orbit orbit-two"/><div className="hero-window"><div className="hero-window-bar"><span/><span/><span/></div><div className="hero-window-body"><div className="mini-brand">LUMA</div><b>Tables worth<br/>remembering.</b><span>Reserve your evening.</span><button>View menu</button></div></div><div className="hero-float hero-float-a">5 live website experiences</div><div className="hero-float hero-float-b">Read · save · continue</div></div>
   </section>
   <section className="store-section"><div className="store-section-head"><div><span>FEATURED WEBSITES</span><h2>Five sellable experiences, not static mockups.</h2><p>Every template has its own routes, information architecture and demo journey.</p></div><Link href="/websites">View all <ArrowRight size={15}/></Link></div>
    <div className="website-grid">{websites.map(w=><Link key={w.slug} href={`/websites/${w.slug}`} className={`website-card ${w.theme}`}><div className="website-preview"><div className="preview-browser"><i/><i/><i/></div><div className="preview-content"><small>{w.category}</small><strong>{w.name}</strong><span>{w.tagline}</span><div className="preview-lines"><i/><i/><i/></div></div></div><div className="website-card-copy"><div><span>{w.category}</span><h3>{w.name}</h3><p>{w.tagline}</p></div><strong>₹{w.price.toLocaleString("en-IN")}</strong></div></Link>)}</div>
   </section>
   <section className="store-book-feature"><div className="book-feature-copy"><span>THE READING ROOM</span><h2>A quieter part of mPay.</h2><p>Three public-domain classics are seeded into the first library experience. Purchase state, reading progress and reader controls are already designed for expansion.</p><Link href="/books" className="store-secondary">Enter the library <BookOpen size={16}/></Link></div><div className="book-stack">{books.map((b,i)=><Link href={`/books/${b.slug}`} key={b.slug} className={`book-cover book-${b.cover}`} style={{transform:`translateX(${i*38}px) rotate(${(i-1)*4}deg)`}}><span>{b.title}</span><small>{b.author}</small></Link>)}</div></section>
   <section className="store-value-grid"><article><Globe2/><h3>Real routes</h3><p>Move from landing page to menus, properties, appointments, carts and contact journeys.</p></article><article><Sparkles/><h3>Premium presentation</h3><p>mPay's warm visual system frames products without flattening their individual identities.</p></article><article><BookOpen/><h3>Reading that feels owned</h3><p>Library access and reading progress are first-class experiences, not a raw PDF link.</p></article></section>
  </main>
 </StoreShell>;
}
