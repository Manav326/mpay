"use client";

import Link from "next/link";
import { BookOpen, Filter } from "lucide-react";
import { useMemo, useState } from "react";
import { studyBooks } from "./studyBooks";
import LibraryEntitlementButton from "./LibraryEntitlementButton";

const classes = ["all", 9, 10, 11, 12] as const;
const subjects = ["all", "Geography", "History", "Economics"] as const;

export default function StudyCatalog() {
  const [classFilter, setClassFilter] = useState<(typeof classes)[number]>("all");
  const [subjectFilter, setSubjectFilter] = useState<(typeof subjects)[number]>("all");

  const books = useMemo(
    () =>
      studyBooks.filter(
        (book) =>
          (classFilter === "all" || book.classLevel === classFilter) &&
          (subjectFilter === "all" || book.subject === subjectFilter),
      ),
    [classFilter, subjectFilter],
  );

  return (
    <section className="ncert-catalog-section" id="ncert-study">
      <div className="store-section-head">
        <div>
          <span>NCERT STUDY LIBRARY</span>
          <h2>Classes IX–XII · Geography · History · Economics</h2>
          <p>
            Free official NCERT textbook access, organized by class and subject. Add a book to your
            Library, read it inside mPay, save your markers, and export your marked copy.
          </p>
        </div>
        <Filter size={18} />
      </div>

      <div className="study-filters">
        <div className="study-filter-group">
          <span>Class</span>
          {classes.map((value) => (
            <button
              key={String(value)}
              type="button"
              className={classFilter === value ? "active" : ""}
              onClick={() => setClassFilter(value)}
            >
              {value === "all" ? "All" : `Class ${value}`}
            </button>
          ))}
        </div>

        <div className="study-filter-group">
          <span>Subject</span>
          {subjects.map((value) => (
            <button
              key={value}
              type="button"
              className={subjectFilter === value ? "active" : ""}
              onClick={() => setSubjectFilter(value)}
            >
              {value === "all" ? "All" : value}
            </button>
          ))}
        </div>
      </div>

      <div className="study-book-grid">
        {books.map((book) => (
          <article key={book.slug} className="study-book-card">
            <Link href={`/books/${book.slug}`} className={`study-book-cover book-${book.cover}`}>
              <small>CLASS {book.classLevel}</small>
              <b>{book.subject}</b>
              <strong>{book.title}</strong>
              <span>NCERT · ENGLISH</span>
            </Link>

            <div className="study-book-copy">
              <div>
                <span>{`Class ${book.classLevel} · ${book.subject}`}</span>
                <h3>{book.title}</h3>
                <p>{book.description}</p>
              </div>

              {book.curriculumNote ? <small className="study-book-note">{book.curriculumNote}</small> : null}

              <div className="study-book-actions">
                <Link href={`/books/${book.slug}`} className="library-open-link">
                  <BookOpen size={14} />
                  Details
                </Link>
                <LibraryEntitlementButton slug={book.slug} price={0} />
              </div>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
