'use client';

import { CheckCircle2, LibraryBig } from "lucide-react";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

export const PURCHASED_BOOKS_KEY = "mpay-store-purchased-books";

function readPurchased(): string[] {
  try {
    const raw = window.localStorage.getItem(PURCHASED_BOOKS_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === "string") : [];
  } catch {
    return [];
  }
}

export default function BookPurchaseButton({ slug, price }: { slug: string; price: number }) {
  const router = useRouter();
  const [purchased, setPurchased] = useState(false);

  useEffect(() => {
    setPurchased(readPurchased().includes(slug));
  }, [slug]);

  function completePurchase() {
    const current = readPurchased();
    if (!current.includes(slug)) {
      window.localStorage.setItem(PURCHASED_BOOKS_KEY, JSON.stringify([...current, slug]));
      window.dispatchEvent(new CustomEvent("mpay-library-updated"));
    }
    setPurchased(true);
  }

  if (purchased) {
    return (
      <button className="store-primary" type="button" onClick={() => router.push("/library")}>
        <CheckCircle2 size={16} /> In your library
      </button>
    );
  }

  return (
    <button className="store-secondary" type="button" onClick={completePurchase}>
      <LibraryBig size={16} /> {price === 0 ? "Add free to Library" : `Purchase · ₹${price}`}
    </button>
  );
}
