'use client';

import Link from "next/link";
import { ArrowLeft, ChevronLeft, ChevronRight, ExternalLink, List, Maximize2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getNcertBook, ncertBooks, ncertChapterUrl } from "./ncertBooks";

export function NcertReader({ id }: { id: string }) {
  const initialBook = getNcertBook(id) ?? ncertBooks[0];
  const [bookId, setBookId] = useState(initialBook.id);
  const book = getNcertBook(bookId) ?? initialBook;
  const storageKey = `mpay-ncert-reader-${book.id}`;
  const [chapter, setChapter] = useState(0);
  const [showChapters, setShowChapters] = useState(false);

  const classBooks = ncertBooks.filter((candidate) => candidate.classLabel === book.classLabel);

  useEffect(() => {
    const saved = Number(window.localStorage.getItem(storageKey));
    if (Number.isInteger(saved) && saved >= 0 && saved < book.chapters.length) {
      setChapter(saved);
    } else {
      setChapter(0);
    }
  }, [storageKey, book.chapters.length]);

  useEffect(() => {
    window.localStorage.setItem(storageKey, String(chapter));
  }, [storageKey, chapter]);

  const pdfUrl = useMemo(() => ncertChapterUrl(book, chapter), [book, chapter]);
  const progress = Math.round(((chapter + 1) / book.chapters.length) * 100);

  function selectBook(nextId: string) {
    setBookId(nextId);
    setChapter(0);
  }

  function selectChapter(index: number) {
    setChapter(index);
    setShowChapters(false);
  }

  return (
    <main className="reader ncert-reader">
      <header className="reader-toolbar">
        <Link href="/library"><ArrowLeft size={17} /><span>Library</span></Link>
        <div className="reader-title">
          <small>{book.title}</small>
          <span>{book.classLabel} · Official NCERT</span>
        </div>
        <div className="reader-actions">
          <button type="button" onClick={() => setShowChapters((open) => !open)} aria-label="Open chapter list">
            <List size={16} />
          </button>
          <a href={pdfUrl} target="_blank" rel="noreferrer" aria-label="Open official PDF">
            <ExternalLink size={16} />
          </a>
        </div>
      </header>

      <div className="reader-progress"><span style={{ width: `${progress}%` }} /></div>

      <div className="ncert-reader-layout">
        <aside className={`ncert-chapters ${showChapters ? "open" : ""}`}>
          <div className="ncert-chapters-head">
            <span>{book.classLabel} Geography</span>
            <b>Complete official textbook set</b>
          </div>

          <label className="ncert-book-picker">
            <span>Textbook</span>
            <select value={book.id} onChange={(event) => selectBook(event.target.value)}>
              {classBooks.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>{candidate.title}</option>
              ))}
            </select>
          </label>

          {book.chapters.map((title, index) => (
            <button
              type="button"
              key={title}
              className={index === chapter ? "active" : ""}
              onClick={() => selectChapter(index)}
            >
              <small>{String(index + 1).padStart(2, "0")}</small>
              <span>{title}</span>
            </button>
          ))}
        </aside>

        <section className="ncert-reader-stage">
          <div className="ncert-reader-head">
            <div>
              <span>OFFICIAL NCERT TEXTBOOK</span>
              <h1>Chapter {chapter + 1} · {book.chapters[chapter]}</h1>
            </div>
            <a href={pdfUrl} target="_blank" rel="noreferrer" className="library-open-link">
              <Maximize2 size={14} /> Open official PDF
            </a>
          </div>

          <div className="ncert-pdf-frame">
            <iframe
              title={`${book.title} — Chapter ${chapter + 1}`}
              src={pdfUrl}
              loading="eager"
            />
          </div>
        </section>
      </div>

      <footer className="reader-bottom">
        <button type="button" disabled={chapter === 0} onClick={() => setChapter((index) => Math.max(0, index - 1))}>
          <ChevronLeft size={18} /> Previous chapter
        </button>
        <span>{progress}% · {chapter + 1} / {book.chapters.length}</span>
        <button type="button" disabled={chapter === book.chapters.length - 1} onClick={() => setChapter((index) => Math.min(book.chapters.length - 1, index + 1))}>
          Next chapter <ChevronRight size={18} /> 
        </button>
      </footer>
    </main>
  );
}
