"use client";

import { CheckCircle2, LibraryBig } from "lucide-react";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

export const LIBRARY_KEY = "mpay-store-library-v2";

function readLibrary(): string[] {
  try {
    const value = window.localStorage.getItem(LIBRARY_KEY);
    const parsed = value ? JSON.parse(value) : [];
    return Array.isArray(parsed) ? parsed.filter((id): id is string => typeof id === "string") : [];
  } catch {
    return [];
  }
}

export function isInLibrary(slug: string) {
  return readLibrary().includes(slug);
}

export function addToLibrary(slug: string) {
  const current = readLibrary();
  if (current.includes(slug)) return current;
  const next = [...current, slug];
  window.localStorage.setItem(LIBRARY_KEY, JSON.stringify(next));
  window.dispatchEvent(new CustomEvent("mpay-library-updated"));
  return next;
}

export default function LibraryEntitlementButton({
  slug,
  price,
}: {
  slug: string;
  price: number;
}) {
  const router = useRouter();
  const [inLibrary, setInLibrary] = useState(false);

  useEffect(() => {
    setInLibrary(isInLibrary(slug));
  }, [slug]);

  function handleClick() {
    addToLibrary(slug);
    setInLibrary(true);
  }

  if (inLibrary) {
    return (
      <button type="button" className="store-primary" onClick={() => router.push("/library")}>
        <CheckCircle2 size={16} />
        In your library
      </button>
    );
  }

  return (
    <button type="button" className="store-secondary" onClick={handleClick}>
      <LibraryBig size={16} />
      {price === 0 ? "Add free to Library" : `Purchase · ₹${price}`}
    </button>
  );
}
