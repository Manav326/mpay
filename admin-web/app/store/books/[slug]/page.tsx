import Link from "next/link";
import { ArrowLeft, BookOpen, Download, ExternalLink, ShieldCheck } from "lucide-react";
import StoreShell from "../../StoreShell";
import LibraryEntitlementButton from "../../LibraryEntitlementButton";
import { getReadableItem, readableItems } from "../../readings";
import { getStudyBook } from "../../studyBooks";

export function generateStaticParams() {
  return readableItems.map((item) => ({ slug: item.slug }));
}

export default async function BookDetail({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const item = getReadableItem(slug);
  if (!item) {
    return null;
  }

  const study = item.kind === "ncert";
  const studyBook = study ? getStudyBook(item.slug) : null;

  return (
    <StoreShell>
      <main className="book-detail">
        <Link href="/books" className="detail-back">
          <ArrowLeft size={15} /> Back to reading room
        </Link>

        <section className="book-detail-hero">
          <div className={`book-big-cover book-${item.cover}`}>
            <span>{item.title}</span>
            <small>
              {study
                ? `Class ${item.classLevel} · ${item.subject} · NCERT`
                : item.author}
            </small>
          </div>

          <div>
            <span>
              {study
                ? "NCERT STUDY LIBRARY"
                : "PUBLIC-DOMAIN READING"}
            </span>

            <h1>{item.title}</h1>

            <h2>
              {study
                ? `Class ${item.classLevel} · ${item.subject}`
                : item.author}
            </h2>

            <p>{item.description}</p>

            {item.curriculumNote ? (
              <div className="official-book-list">
                <strong>Curriculum note</strong>
                <span>{item.curriculumNote}</span>
              </div>
            ) : null}

            <div className="book-detail-actions">
              <Link
                href={`/read/${item.slug}`}
                className="store-primary"
              >
                <BookOpen size={16} />
                Read complete book
              </Link>

              {studyBook ? (
                <a
                  href={studyBook.pdfUrl}
                  className="store-secondary"
                  target="_blank"
                  rel="noreferrer"
                  title="Open the complete NCERT textbook PDF from the official NCERT host"
                >
                  <Download size={16} />
                  {studyBook.downloadLabel}
                </a>
              ) : null}

              <LibraryEntitlementButton
                slug={item.slug}
                price={item.price}
              />
            </div>

            <div className="reader-note">
              <ShieldCheck size={15} />
              <span>
                {study
                  ? "This is the free NCERT textbook. mPay organizes the book, opens the complete PDF inside the reader, and keeps your study progress and markers locally."
                  : "This is a public-domain reading edition. The complete source PDF is available through the mPay reader."
                }
              </span>
            </div>

            {studyBook ? (
              <div className="official-book-list">
                <strong>{studyBook.title}</strong>
                <span>
                  Class {studyBook.classLevel} · {studyBook.subject} · Published by NCERT · Free access
                </span>
                <div className="official-book-links">
                  <a href={studyBook.portalUrl} target="_blank" rel="noreferrer">
                    NCERT book page <ExternalLink size={13} />
                  </a>
                  <a href={studyBook.pdfUrl} target="_blank" rel="noreferrer">
                    Complete PDF <ExternalLink size={13} />
                  </a>
                </div>
              </div>
            ) : (
              <a
                className="source-link"
                href={item.sourceUrl}
                target="_blank"
                rel="noreferrer"
              >
                View public-domain source <ExternalLink size={13} />
              </a>
            )}
          </div>
        </section>
      </main>
    </StoreShell>
  );
}
