import Link from "next/link";
import { ArrowLeft, BookOpen, Download, ExternalLink, ShieldCheck } from "lucide-react";
import StoreShell from "../../StoreShell";
import LibraryEntitlementButton from "../../LibraryEntitlementButton";
import { getReadableItem, readableItems } from "../../readings";

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

              {study ? (
                <a
                  href={`${item.pdfUrl}?download=1`}
                  className="store-secondary"
                  title="Download the complete book from the mPay Store content bundle"
                >
                  <Download size={16} />
                  Download complete book
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
                  ? "This free NCERT textbook is served from the Store content bundle. Read it inside mPay, download the complete book, and keep your study markers locally."
                  : "This is a public-domain reading edition. The complete source PDF is available through the mPay reader."
                }
              </span>
            </div>

            <a
              className="source-link"
              href={item.sourceUrl}
              target="_blank"
              rel="noreferrer"
            >
              {study ? "Official NCERT textbook portal" : "View public-domain source"}{" "}
              <ExternalLink size={13} />
            </a>
          </div>
        </section>
      </main>
    </StoreShell>
  );
}
