import { books } from "./data";
import { getStudyBook, studyBooks } from "./studyBooks";

export type ReadableItem = {
  slug: string;
  title: string;
  author: string;
  kind: "ncert" | "public-domain";
  classLevel?: 9 | 10 | 11 | 12;
  subject?: "Geography" | "History" | "Economics";
  price: number;
  pdfUrl: string;
  sourceUrl: string;
  cover: string;
  description: string;
  curriculumNote?: string;
};

export function getReadableItem(slug: string): ReadableItem | null {
  const study = getStudyBook(slug);
  if (study) {
    return {
      slug: study.slug,
      title: study.title,
      author: study.publisher,
      kind: "ncert",
      classLevel: study.classLevel,
      subject: study.subject,
      price: 0,
      pdfUrl: study.pdfUrl,
      sourceUrl: study.portalUrl,
      cover: study.cover,
      description: study.description,
      curriculumNote: study.curriculumNote,
    };
  }

  const classic = books.find((book) => book.slug === slug);
  if (!classic) return null;

  return {
    slug: classic.slug,
    title: classic.title,
    author: classic.author,
    kind: "public-domain",
    price: classic.price,
    pdfUrl: classic.pdf,
    sourceUrl: classic.source,
    cover: classic.cover,
    description: classic.excerpt,
  };
}

export const readableItems = [...studyBooks, ...books];
