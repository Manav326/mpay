export type StudySubject = "Geography" | "History" | "Economics";

export type StudyBook = {
  slug: string;
  classLevel: 9 | 10 | 11 | 12;
  subject: StudySubject;
  title: string;
  publisher: "NCERT";
  pdfUrl: string;
  portalUrl: string;
  language: "English";
  cover: string;
  description: string;
  curriculumNote?: string;
};

export const studyBooks: StudyBook[] = [
  { slug:"ncert-class-9-geography", classLevel:9, subject:"Geography", title:"Contemporary India-I", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/iess1ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?iess1=1-10", language:"English", cover:"ncert9geo", description:"Textbook in Geography for Class IX.", curriculumNote:"NCERT is transitioning Grade 9 materials in 2026; this is the official English textbook currently listed for this subject." },
  { slug:"ncert-class-9-history", classLevel:9, subject:"History", title:"India and the Contemporary World-I", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/iess3ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?iess3=ps-5", language:"English", cover:"ncert9history", description:"History textbook for Class IX.", curriculumNote:"NCERT is transitioning Grade 9 materials in 2026; this is the official English textbook currently listed for this subject." },
  { slug:"ncert-class-9-economics", classLevel:9, subject:"Economics", title:"Economics", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/iess2ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?iess2=1-5", language:"English", cover:"ncert9economics", description:"Economics textbook for Class IX.", curriculumNote:"NCERT is transitioning Grade 9 materials in 2026; this is the official English textbook currently listed for this subject." },

  { slug:"ncert-class-10-geography", classLevel:10, subject:"Geography", title:"Contemporary India-II", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/jess1ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?jess1=1-7", language:"English", cover:"ncert10geo", description:"Textbook in Geography for Class X." },
  { slug:"ncert-class-10-history", classLevel:10, subject:"History", title:"India and the Contemporary World-II", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/jess3ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?jess3=3-3", language:"English", cover:"ncert10history", description:"History textbook for Class X." },
  { slug:"ncert-class-10-economics", classLevel:10, subject:"Economics", title:"Understanding Economic Development", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/jess2ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?jess2=ps-5", language:"English", cover:"ncert10economics", description:"Economics textbook for Class X." },

  { slug:"ncert-class-11-geography-physical", classLevel:11, subject:"Geography", title:"Fundamentals of Physical Geography", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/kegy2ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?kegy2=4-14", language:"English", cover:"ncert11geo", description:"Fundamentals of Physical Geography for Class XI." },
  { slug:"ncert-class-11-geography-india", classLevel:11, subject:"Geography", title:"India: Physical Environment", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/kegy1ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?kegy1=1-6", language:"English", cover:"ncert11india", description:"India: Physical Environment for Class XI." },
  { slug:"ncert-class-11-history", classLevel:11, subject:"History", title:"Themes in World History", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/kehs1ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?kehs1=1-11", language:"English", cover:"ncert11history", description:"Themes in World History for Class XI." },
  { slug:"ncert-class-11-economics-statistics", classLevel:11, subject:"Economics", title:"Statistics for Economics", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/kest1ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?kest1=ps-8", language:"English", cover:"ncert11economics", description:"Statistics for Economics for Class XI." },
  { slug:"ncert-class-11-economics-development", classLevel:11, subject:"Economics", title:"Indian Economic Development", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/keec1ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?keec1=gl-5", language:"English", cover:"ncert11economics", description:"Indian Economic Development for Class XI." },

  { slug:"ncert-class-12-geography-human", classLevel:12, subject:"Geography", title:"Fundamentals of Human Geography", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/legy1ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?legy1=5-12", language:"English", cover:"ncert12geo", description:"Fundamentals of Human Geography for Class XII." },
  { slug:"ncert-class-12-geography-india", classLevel:12, subject:"Geography", title:"India - People And Economy", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/legy2ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?legy2=0-9", language:"English", cover:"ncert12india", description:"India - People And Economy for Class XII." },
  { slug:"ncert-class-12-history-1", classLevel:12, subject:"History", title:"Themes in Indian History-I", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/lehs1ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?lehs1=3-4", language:"English", cover:"ncert12history", description:"Themes in Indian History-I for Class XII." },
  { slug:"ncert-class-12-history-2", classLevel:12, subject:"History", title:"Themes in Indian History-II", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/lehs2ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?lehs2=0-8", language:"English", cover:"ncert12history", description:"Themes in Indian History-II for Class XII." },
  { slug:"ncert-class-12-history-3", classLevel:12, subject:"History", title:"Themes in Indian History-III", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/lehs3ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?lehs3=0-8", language:"English", cover:"ncert12history", description:"Themes in Indian History-III for Class XII." },
  { slug:"ncert-class-12-economics-micro", classLevel:12, subject:"Economics", title:"Introductory Microeconomics", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/leec2ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?leec2=ps-6", language:"English", cover:"ncert12economics", description:"Introductory Microeconomics for Class XII." },
  { slug:"ncert-class-12-economics-macro", classLevel:12, subject:"Economics", title:"Introductory Macroeconomics", publisher:"NCERT", pdfUrl:"https://www.ncert.nic.in/textbook/pdf/leec1ps.pdf", portalUrl:"https://www.ncert.nic.in/textbook.php?leec1=ps-6", language:"English", cover:"ncert12economics", description:"Introductory Macroeconomics for Class XII." },
];

export const studyBookBySlug = Object.fromEntries(
  studyBooks.map((book) => [book.slug, book]),
) as Record<string, StudyBook>;

export function getStudyBook(slug: string) {
  return studyBookBySlug[slug] ?? null;
}
