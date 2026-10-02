export type StudySubject = "Geography" | "History" | "Economics";

export type NcertStudyBook = {
  slug: string;
  title: string;
  classLevel: 9 | 10 | 11 | 12;
  subject: StudySubject;
  author: "NCERT";
  pdfUrl: string;
  portalUrl: string;
  excerpt: string;
  cover: string;
  note?: string;
};

const portalBase = "https://ncert.nic.in/textbook.php";

export const ncertStudyBooks: NcertStudyBook[] = [
  { slug:"ncert-class-9-geography", title:"Contemporary India-I", classLevel:9, subject:"Geography", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/iess1ps.pdf", portalUrl:`${portalBase}?iess1=1-5`, cover:"ncert9geo", excerpt:"NCERT Geography for Class IX.", note:"Class IX is in a textbook transition; this keeps the established NCERT subject textbook accessible." },
  { slug:"ncert-class-9-history", title:"India and the Contemporary World-I", classLevel:9, subject:"History", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/iess3ps.pdf", portalUrl:`${portalBase}?iess3=ps-5`, cover:"ncert9history", excerpt:"NCERT History for Class IX.", note:"Class IX is in a textbook transition; this keeps the established NCERT subject textbook accessible." },
  { slug:"ncert-class-9-economics", title:"Economics", classLevel:9, subject:"Economics", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/iess2ps.pdf", portalUrl:`${portalBase}?iess2=1-5`, cover:"ncert9economics", excerpt:"NCERT Economics for Class IX.", note:"Class IX is in a textbook transition; this keeps the established NCERT subject textbook accessible." },

  { slug:"ncert-class-10-geography", title:"Contemporary India-II", classLevel:10, subject:"Geography", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/jess1ps.pdf", portalUrl:`${portalBase}?jess1=1-7`, cover:"ncert10geo", excerpt:"NCERT Geography for Class X." },
  { slug:"ncert-class-10-history", title:"India and the Contemporary World-II", classLevel:10, subject:"History", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/jess3ps.pdf", portalUrl:`${portalBase}?jess3=3-3`, cover:"ncert10history", excerpt:"NCERT History for Class X." },
  { slug:"ncert-class-10-economics", title:"Understanding Economic Development", classLevel:10, subject:"Economics", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/jess2ps.pdf", portalUrl:`${portalBase}?jess2=ps-5`, cover:"ncert10economics", excerpt:"NCERT Economics for Class X." },

  { slug:"ncert-class-11-geography-physical", title:"Fundamentals of Physical Geography", classLevel:11, subject:"Geography", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/kegy2ps.pdf", portalUrl:`${portalBase}?kegy2=ps-14`, cover:"ncert11geo", excerpt:"NCERT Geography for Class XI." },
  { slug:"ncert-class-11-geography-india", title:"India: Physical Environment", classLevel:11, subject:"Geography", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/kegy1ps.pdf", portalUrl:`${portalBase}?kegy1=ps-6`, cover:"ncert11india", excerpt:"NCERT Geography for Class XI." },
  { slug:"ncert-class-11-history", title:"Themes in World History", classLevel:11, subject:"History", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/kehs1ps.pdf", portalUrl:`${portalBase}?kehs1=1-11`, cover:"ncert11history", excerpt:"NCERT History for Class XI.", },
  { slug:"ncert-class-11-economics-statistics", title:"Statistics for Economics", classLevel:11, subject:"Economics", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/kest1ps.pdf", portalUrl:`${portalBase}?kest1=ps-8`, cover:"ncert11economics", excerpt:"NCERT Economics for Class XI." },
  { slug:"ncert-class-11-economics-development", title:"Indian Economic Development", classLevel:11, subject:"Economics", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/keec1ps.pdf", portalUrl:`${portalBase}?keec1=gl-5`, cover:"ncert11economics", excerpt:"NCERT Economics for Class XI." },

  { slug:"ncert-class-12-geography-human", title:"Fundamentals of Human Geography", classLevel:12, subject:"Geography", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/legy1ps.pdf", portalUrl:`${portalBase}?legy1=7-8`, cover:"ncert12geo", excerpt:"NCERT Geography for Class XII." },
  { slug:"ncert-class-12-geography-india", title:"India - People And Economy", classLevel:12, subject:"Geography", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/legy2ps.pdf", portalUrl:`${portalBase}?legy2=0-9`, cover:"ncert12india", excerpt:"NCERT Geography for Class XII." },
  { slug:"ncert-class-12-history-1", title:"Themes in Indian History-I", classLevel:12, subject:"History", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/lehs1ps.pdf", portalUrl:`${portalBase}?lehs1=3-4`, cover:"ncert12history", excerpt:"NCERT History for Class XII." },
  { slug:"ncert-class-12-history-2", title:"Themes in Indian History-II", classLevel:12, subject:"History", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/lehs2ps.pdf", portalUrl:`${portalBase}?lehs2=0-8`, cover:"ncert12history", excerpt:"NCERT History for Class XII." },
  { slug:"ncert-class-12-history-3", title:"Themes in Indian History-III", classLevel:12, subject:"History", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/lehs3ps.pdf", portalUrl:`${portalBase}?lehs3=0-8`, cover:"ncert12history", excerpt:"NCERT History for Class XII." },
  { slug:"ncert-class-12-economics-micro", title:"Introductory Microeconomics", classLevel:12, subject:"Economics", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/leec2ps.pdf", portalUrl:`${portalBase}?leec2=ps-6`, cover:"ncert12economics", excerpt:"NCERT Economics for Class XII." },
  { slug:"ncert-class-12-economics-macro", title:"Introductory Macroeconomics", classLevel:12, subject:"Economics", author:"NCERT", pdfUrl:"https://ncert.nic.in/textbook/pdf/leec1ps.pdf", portalUrl:`${portalBase}?leec1=ps-6`, cover:"ncert12economics", excerpt:"NCERT Economics for Class XII." },
];

export const ncertStudyBooksBySlug = Object.fromEntries(
  ncertStudyBooks.map((book) => [book.slug, book]),
) as Record<string, NcertStudyBook>;
