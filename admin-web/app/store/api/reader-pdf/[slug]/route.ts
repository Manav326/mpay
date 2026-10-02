import { NextResponse } from "next/server";
import { getReadableItem } from "../../../readings";
import { getStudyBook } from "../../../studyBooks";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(
  request: Request,
  { params }: { params: Promise<{ slug: string }> },
) {
  const { slug } = await params;
  const item = getReadableItem(slug);

  if (!item) {
    return NextResponse.json({ error: "Reader item not found" }, { status: 404 });
  }

  try {
    const range = request.headers.get("range");
    const headers = new Headers();
    if (range) headers.set("Range", range);

    headers.set("Accept", "application/pdf,*/*");
    headers.set(
      "User-Agent",
      "Mozilla/5.0 (compatible; mPayStudyReader/1.0; +https://store.thinkwithsujeet.in)",
    );
    const referer =
      item.kind === "ncert"
        ? getStudyBook(slug)?.portalUrl ?? "https://www.ncert.nic.in/textbook.php"
        : "https://store.thinkwithsujeet.in/";
    headers.set("Referer", referer);

    const upstream = await fetch(item.pdfUrl, {
      headers,
      redirect: "follow",
      cache: "no-store",
    });

    const contentType = upstream.headers.get("content-type") ?? "";
    if (
      (!upstream.ok && upstream.status !== 206) ||
      !contentType.toLowerCase().includes("pdf")
    ) {
      return NextResponse.json(
        { error: "The official textbook source is temporarily unavailable." },
        { status: 502 },
      );
    }

    const responseHeaders = new Headers();
    responseHeaders.set("Content-Type", "application/pdf");
    responseHeaders.set("Cache-Control", "private, max-age=3600");
    responseHeaders.set(
      "Accept-Ranges",
      upstream.headers.get("accept-ranges") ?? "bytes",
    );

    for (const name of ["content-length", "content-range", "last-modified", "etag"]) {
      const value = upstream.headers.get(name);
      if (value) responseHeaders.set(name, value);
    }

    return new Response(upstream.body, {
      status: upstream.status,
      headers: responseHeaders,
    });
  } catch {
    return NextResponse.json(
      { error: "The official textbook source is temporarily unavailable." },
      { status: 502 },
    );
  }
}
