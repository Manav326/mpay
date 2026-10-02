import Link from "next/link";
import { ArrowRight, BookOpen, Clock3 } from "lucide-react";
import StoreShell from "../StoreShell";
import { books } from "../data";
import StudyCatalog from "../StudyCatalog";

export default function BooksPage() {
  return (
    <StoreShell>
      <main className="book-catalog">
        <section className="book-hero">
          <div>
            <span>THE READING ROOM</span>
            <h1>Read, study, mark, and keep your progress in one place.</h1>
            <p>
              Explore public-domain classics and a structured NCERT study library for
              Classes IX–XII, with Geography, History and Economics organized by class.
            </p>
          </div>
          <div className="reading-pill">
            <Clock3 size={16} />
            Your reading progress and markers stay on this device.
          </div>
        </section>

        <StudyCatalog />

        <section className="store-section legacy-books-section">
          <div className="store-section-head">
            <div>
              <span>CLASSICS</span>
              <h2>Public-domain reading</h2>
              <p>Complete public-domain editions with the same mPay reader and Library flow.</p>
            </div>
            <Link href="/library">
              Your library <ArrowRight size={15} />
            </Link>
          </div>

          <div className="book-grid">
            {books.map((book) => (
              <Link
                href={`/books/${book.slug}`}
                key={book.slug}
                className={`book-product book-${book.cover}`}
              >
                <div className="book-product-cover">
                  <span>{book.title}</span>
                  <small>{book.author}</small>
                </div>
                <div>
                  <span>{book.genre}</span>
                  <h3>{book.title}</h3>
                  <p>{book.excerpt}</p>
                  <div className="book-product-foot">
                    <b>₹{book.price}</b>
                    <BookOpen size={15} />
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </section>
      </main>
    </StoreShell>
  );
}
