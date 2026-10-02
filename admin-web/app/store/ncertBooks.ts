export type NcertBook = {
  id: string;
  title: string;
  classLabel: "Class XI" | "Class XII";
  code: "kegy2" | "kegy1" | "legy1" | "legy2";
  chapters: string[];
};

export const ncertBooks: NcertBook[] = [
  {
    id: "class-11-fundamentals-physical-geography",
    title: "Fundamentals of Physical Geography",
    classLabel: "Class XI",
    code: "kegy2",
    chapters: [
      "Geography as a Discipline",
      "The Origin and Evolution of the Earth",
      "Interior of the Earth",
      "Distribution of Oceans and Continents",
      "Geomorphic Processes",
      "Landforms and their Evolution",
      "Composition and Structure of Atmosphere",
      "Solar Radiation, Heat Balance and Temperature",
      "Atmospheric Circulation and Weather Systems",
      "Water in the Atmosphere",
      "World Climate and Climate Change",
      "Water (Oceans)",
      "Movements of Ocean Water",
      "Biodiversity and Conservation",
    ],
  },
  {
    id: "class-11-india-physical-environment",
    title: "India: Physical Environment",
    classLabel: "Class XI",
    code: "kegy1",
    chapters: [
      "India — Location",
      "Structure and Physiography",
      "Drainage System",
      "Climate",
      "Natural Vegetation",
      "Natural Hazards and Disasters",
    ],
  },
  {
    id: "class-12-fundamentals-human-geography",
    title: "Fundamentals of Human Geography",
    classLabel: "Class XII",
    code: "legy1",
    chapters: [
      "Human Geography: Nature and Scope",
      "The World Population: Distribution, Density and Growth",
      "Human Development",
      "Primary Activities",
      "Secondary Activities",
      "Tertiary and Quaternary Activities",
      "Transport and Communication",
      "International Trade",
    ],
  },
  {
    id: "class-12-india-people-and-economy",
    title: "India: People and Economy",
    classLabel: "Class XII",
    code: "legy2",
    chapters: [
      "Population: Distribution, Density, Growth and Composition",
      "Human Settlements",
      "Land Resources and Agriculture",
      "Water Resources",
      "Mineral and Energy Resources",
      "Planning and Sustainable Development in Indian Context",
      "Transport and Communication",
      "International Trade",
      "Geographical Perspective on Selected Issues and Problems",
    ],
  },
];

export function getNcertBook(id: string) {
  return ncertBooks.find((book) => book.id === id) ?? null;
}

export function ncertChapterUrl(book: NcertBook, chapterIndex: number) {
  const chapter = String(chapterIndex + 1).padStart(2, "0");
  return `https://ncert.nic.in/textbook/pdf/${book.code}${chapter}.pdf`;
}
