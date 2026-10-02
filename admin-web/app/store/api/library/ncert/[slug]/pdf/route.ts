import fs from "node:fs";
import { Readable } from "node:stream";
import path from "node:path";
import { NextResponse } from "next/server";
import { getStudyBook } from "../../../../studyBooks";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const CONTENT_ROOT = "/app/store-content/ncert";

function filenameFor(book: ReturnType<typeof getStudyBook>) {
  if (!book) return "ncert-textbook.pdf";
  return `Class-${book.classLevel}-${book.title.replace(/[^a-z0-9]+/gi, "-").replace(/^-|-$/g, "")}-NCERT.pdf`;
}

function assetPathFor(slug: string) {
  const book = getStudyBook(slug);
  if (!book) return null;
  return {
    book,
    path: path.join(CONTENT_ROOT, `${slug}.pdf`),
  };
}

async function handle(request: Request, { params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const asset = assetPathFor(slug);

  if (!asset) {
    return NextResponse.json({ error: "Study book not found" }, { status: 404 });
  }

  let stat: fs.Stats;
  try {
    stat = await fs.promises.stat(asset.path);
  } catch {
    return NextResponse.json(
      {
        error: "Study book asset is not installed on this Store server.",
        slug: asset.book.slug,
        expectedPath: `/opt/mpay/store-content/ncert/${slug}.pdf`,
      },
      { status: 503 },
    );
  }

  if (!stat.isFile() || stat.size <= 0) {
    return NextResponse.json({ error: "Study book asset is invalid." }, { status: 503 });
  }

  const range = request.headers.get("range");
  const wantsDownload = new URL(request.url).searchParams.get("download") === "1";
  const filename = filenameFor(asset.book);

  const headers = new Headers({
    "Content-Type": "application/pdf",
    "Accept-Ranges": "bytes",
    "Cache-Control": "private, max-age=3600",
    "X-Content-Type-Options": "nosniff",
    "Content-Disposition": `${wantsDownload ? "attachment" : "inline"}; filename="${filename}"`,
  });

  if (request.method === "HEAD") {
    headers.set("Content-Length", String(stat.size));
    return new Response(null, { status: 200, headers });
  }

  if (!range) {
    headers.set("Content-Length", String(stat.size));
    const stream = fs.createReadStream(asset.path);
    return new Response(Readable.toWeb(stream) as ReadableStream, {
      status: 200,
      headers,
    });
  }

  const match = /^bytes=(\d*)-(\d*)$/.exec(range);
  if (!match || (match[1] === "" && match[2] === "")) {
    headers.set("Content-Range", `bytes */${stat.size}`);
    return new Response(null, { status: 416, headers });
  }

  let start = match[1] === "" ? 0 : Number(match[1]);
  let end = match[2] === "" ? stat.size - 1 : Number(match[2]);

  if (match[1] === "") {
    const suffixLength = Number(match[2]);
    if (!Number.isFinite(suffixLength) || suffixLength <= 0) {
      headers.set("Content-Range", `bytes */${stat.size}`);
      return new Response(null, { status: 416, headers });
    }
    start = Math.max(0, stat.size - suffixLength);
    end = stat.size - 1;
  }

  if (
    !Number.isFinite(start) ||
    !Number.isFinite(end) ||
    start < 0 ||
    end < start ||
    start >= stat.size
  ) {
    headers.set("Content-Range", `bytes */${stat.size}`);
    return new Response(null, { status: 416, headers });
  }

  end = Math.min(end, stat.size - 1);
  const length = end - start + 1;

  headers.set("Content-Length", String(length));
  headers.set("Content-Range", `bytes ${start}-${end}/${stat.size}`);

  const stream = fs.createReadStream(asset.path, { start, end });
  return new Response(stream as unknown as ReadableStream, {
    status: 206,
    headers,
  });
}

export async function GET(
  request: Request,
  context: { params: Promise<{ slug: string }> },
) {
  return handle(request, context);
}

export async function HEAD(
  request: Request,
  context: { params: Promise<{ slug: string }> },
) {
  return handle(request, context);
}
