"use client";

import Link from "next/link";
import { ArrowRight, BookOpen, LibraryBig, Sparkles } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import StoreShell from "../StoreShell";
import { websites } from "../data";
import { books } from "../data";
import { studyBooks } from "../studyBooks";
import { LIBRARY_KEY } from "../LibraryEntitlementButton";

function readLibrary() {
  try {
    const raw = window.localStorage.getItem(LIBRARY_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((value): value is string => typeof value === "string") : [];
  } catch {
    return [];
  }
}

export default function Library() {
  const [library, setLibrary] = useState<string[]>([]);

  useEffect(() => {
    const sync = () => setLibrary(readLibrary());
    sync();
    window.addEventListener("mpay-library-updated", sync);
    window.addEventListener("storage", sync);
    return () => {
      window.removeEventListener("mpay-library-updated", sync);
      window.removeEventListener("storage", sync);
    };
  }, []);

  const studyOwned = useMemo(
    () => studyBooks.filter((book) => library.includes(book.slug)),
    [library],
  );

  const classicsOwned = useMemo(
    () => books.filter((book) => library.includes(book.slug)),
    [library],
  );

  return (
    <StoreShell>
      <main className="library-page">
        <section className="library-hero">
          <span>YOUR SHELF</span>
          <h1>Library</h1>
          <p>
            Your purchased books and free NCERT study books stay together here. Open a
            complete book, continue from your last page, add markers, and export a marked copy.
          </p>
        </section>

        <section className="library-section">
          <div className="store-section-head">
            <div>
              <span>NCERT STUDY</span>
              <h2>Classes IX–XII</h2>
              <p>
                {studyOwned.length
                  ? `${studyOwned.length} NCERT textbook${studyOwned.length === 1 ? "" : "s"} in your Library.`
                  : "Add any NCERT textbook free from the Reading Room."}
              </p>
            </div>
            <Link href="/books#ncert-study">
              Browse study library <ArrowRight size={15} />
            </Link>
          </div>

          {studyOwned.length ? (
            <div className="library-books">
              {studyOwned.map((book) => (
                <div key={book.slug} className="library-book-row">
                  <div className={`library-book-mini book-${book.cover}`}>
                    {book.subject}
                  </div>
                  <div>
                    <b>{book.title}</b>
                    <span>
                      Class {book.classLevel} · {book.subject} · Free NCERT access
                    </span>
                  </div>
                  <div className="library-book-actions">
                    <Link
                      href={`/read/${book.slug}`}
                      className="library-open-link"
                    >
                      <BookOpen size={14} />
                      Read & mark
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="library-empty">
              <LibraryBig size={19} />
              <div>
                <b>No NCERT textbooks added yet</b>
                <span>
                  Choose a class and subject in the NCERT Study Library.
                </span>
              </div>
              <Link href="/books#ncert-study" className="store-primary">
                Browse
              </Link>
            </div>
          )}
        </section>

        <section className="library-section">
          <div className="store-section-head">
            <div>
              <span>PURCHASED</span>
              <h2>Complete reading editions</h2>
              <p>
                {classicsOwned.length
                  ? `${classicsOwned.length} purchased title${classicsOwned.length === 1 ? "" : "s"}.`
                  : "No paid titles yet."}
              </p>
            </div>
            <Link href="/books">
              Browse books <ArrowRight size={15} />
            </Link>
          </div>

          {classicsOwned.length ? (
            <div className="library-books">
              {classicsOwned.map((book) => (
                <div key={book.slug} className="library-book-row">
                  <div className={`library-book-mini book-${book.cover}`}>
                    {book.title}
                  </div>
                  <div>
                    <b>{book.title}</b>
                    <span>Complete public-domain edition</span>
                  </div>
                  <div className="library-book-actions">
                    <Link
                      href={`/read/${book.slug}`}
                      className="library-open-link"
                    >
                      <BookOpen size={14} />
                      Read complete book
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="library-empty">
              <BookOpen size={19} />
              <div>
                <b>Your purchased shelf is empty</b>
                <span>
                  Purchase a public-domain title from the Reading Room to add it here.
                </span>
              </div>
              <Link href="/books" className="store-primary">
                Browse books
              </Link>
            </div>
          )}
        </section>

        <section className="library-section">
          <div className="store-section-head">
            <div>
              <span>WEBSITES</span>
              <h2>Website purchases</h2>
            </div>
            <Link href="/websites">
              Explore <ArrowRight size={15} />
            </Link>
          </div>

          <div className="library-websites">
            {websites.slice(0, 3).map((website) => (
              <div key={website.slug} className="library-website">
                <Sparkles size={16} />
                <div>
                  <b>{website.name}</b>
                  <span>Demo access · customisation-ready</span>
                </div>
                <button type="button">Purchase</button>
              </div>
            ))}
          </div>
        </section>
      </main>
    </StoreShell>
  );
}
