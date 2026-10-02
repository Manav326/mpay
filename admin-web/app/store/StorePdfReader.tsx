"use client";

import Link from "next/link";
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Download,
  Eraser,
  Highlighter,
  List,
  Minus,
  Plus,
  RotateCcw,
  Undo2,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  getDocument,
  GlobalWorkerOptions,
  type PDFDocumentProxy,
  type PDFPageProxy,
} from "pdfjs-dist";
import type { ReadableItem } from "./readings";
import { studyBooks } from "./studyBooks";

GlobalWorkerOptions.workerSrc = new URL(
  "pdfjs-dist/build/pdf.worker.min.mjs",
  import.meta.url,
).toString();

type MarkerColor = "yellow" | "green";

type Point = { x: number; y: number };

type MarkerStroke = {
  color: MarkerColor;
  points: Point[];
};

const MARKER = {
  yellow: { label: "Yellow", ui: "#f5d34f", pdf: [0.96, 0.78, 0.11] },
  green: { label: "Light green", ui: "#9adf8a", pdf: [0.46, 0.82, 0.39] },
} as const;

function markerKey(slug: string) {
  return `mpay-reader-marks-v1:${slug}`;
}

function progressKey(slug: string) {
  return `mpay-reader-progress-v1:${slug}`;
}

function readMarkers(slug: string): Record<number, MarkerStroke[]> {
  try {
    const raw = window.localStorage.getItem(markerKey(slug));
    const parsed = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

function readProgress(slug: string) {
  try {
    const value = Number(window.localStorage.getItem(progressKey(slug)));
    return Number.isInteger(value) && value > 0 ? value : 1;
  } catch {
    return 1;
  }
}

function pagePoint(event: React.PointerEvent<HTMLCanvasElement>): Point {
  const rect = event.currentTarget.getBoundingClientRect();
  return {
    x: Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)),
    y: Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height)),
  };
}

export default function StorePdfReader({ item }: { item: ReadableItem }) {
  const [pdf, setPdf] = useState<PDFDocumentProxy | null>(null);
  const [pageNumber, setPageNumber] = useState(() => readProgress(item.slug));
  const [numPages, setNumPages] = useState(0);
  const [zoom, setZoom] = useState(1);
  const [markerColor, setMarkerColor] = useState<MarkerColor>("yellow");
  const [markers, setMarkers] = useState<Record<number, MarkerStroke[]>>(() =>
    readMarkers(item.slug),
  );
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState("");
  const [showShelf, setShowShelf] = useState(false);
  const [pageWidth, setPageWidth] = useState(760);

  const stageRef = useRef<HTMLDivElement>(null);
  const pageWrapRef = useRef<HTMLDivElement>(null);
  const pageCanvasRef = useRef<HTMLCanvasElement>(null);
  const markerCanvasRef = useRef<HTMLCanvasElement>(null);
  const draftRef = useRef<Point[] | null>(null);
  const renderTaskRef = useRef<{ cancel: () => void } | null>(null);
  const pdfRef = useRef<PDFDocumentProxy | null>(null);

  useEffect(() => {
    try {
      window.localStorage.setItem(markerKey(item.slug), JSON.stringify(markers));
    } catch {
      // Local storage is a convenience, not a hard dependency.
    }
  }, [item.slug, markers]);

  useEffect(() => {
    try {
      window.localStorage.setItem(progressKey(item.slug), String(pageNumber));
    } catch {
      // Local storage is a convenience, not a hard dependency.
    }
  }, [item.slug, pageNumber]);

  useEffect(() => {
    const node = stageRef.current;
    if (!node) return;

    const update = () => {
      setPageWidth(Math.max(320, Math.min(900, node.clientWidth - 32)));
    };

    update();
    const observer = new ResizeObserver(update);
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    let disposed = false;
    const load = async () => {
      setLoading(true);
      setError("");
      try {
        const task = getDocument({
          url: `/store/api/reader-pdf/${encodeURIComponent(item.slug)}`,
          withCredentials: false,
          disableAutoFetch: false,
          disableStream: false,
        });
        const loaded = await task.promise;
        if (disposed) {
          await loaded.destroy();
          return;
        }
        pdfRef.current = loaded;
        setPdf(loaded);
        setNumPages(loaded.numPages);
        setPageNumber((current) => Math.min(Math.max(1, current), loaded.numPages));
      } catch {
        if (!disposed) {
          setError(
            "The official textbook could not be loaded right now. Please retry, or use the NCERT source link.",
          );
        }
      } finally {
        if (!disposed) setLoading(false);
      }
    };

    void load();

    return () => {
      disposed = true;
      renderTaskRef.current?.cancel();
      renderTaskRef.current = null;
      const currentPdf = pdfRef.current;
      pdfRef.current = null;
      if (currentPdf) void currentPdf.destroy();
    };
    // The reader instance is tied to one book; reload only when the book changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item.slug]);

  const pagePercent = numPages ? Math.round((pageNumber / numPages) * 100) : 0;

  const redrawMarkers = useMemo(
    () => () => {
      const canvas = markerCanvasRef.current;
      if (!canvas) return;

      const ctx = canvas.getContext("2d");
      if (!ctx) return;

      const width = canvas.clientWidth;
      const height = canvas.clientHeight;
      const ratio = window.devicePixelRatio || 1;

      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      ctx.clearRect(0, 0, width, height);

      const strokes = markers[pageNumber] ?? [];

      for (const stroke of strokes) {
        if (stroke.points.length < 2) continue;
        ctx.save();
        ctx.globalAlpha = 0.34;
        ctx.strokeStyle = MARKER[stroke.color].ui;
        ctx.lineWidth = 19;
        ctx.lineCap = "round";
        ctx.lineJoin = "round";
        ctx.beginPath();

        stroke.points.forEach((point, index) => {
          const x = point.x * width;
          const y = point.y * height;
          if (index === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        });

        ctx.stroke();
        ctx.restore();
      }
    },
    [markers, pageNumber],
  );

  useEffect(() => {
    redrawMarkers();
  }, [redrawMarkers, pageWidth, zoom]);

  useEffect(() => {
    if (!pdf || !pageCanvasRef.current || !markerCanvasRef.current || !pageWrapRef.current) {
      return;
    }

    let cancelled = false;

    const render = async () => {
      renderTaskRef.current?.cancel();

      const page: PDFPageProxy = await pdf.getPage(pageNumber);
      if (cancelled) return;

      const initialViewport = page.getViewport({ scale: 1 });
      const fitScale = pageWidth / initialViewport.width;
      const scale = Math.max(0.6, Math.min(2, fitScale * zoom));
      const viewport = page.getViewport({ scale });

      const ratio = window.devicePixelRatio || 1;
      const canvas = pageCanvasRef.current;
      const markerCanvas = markerCanvasRef.current;

      canvas.width = Math.floor(viewport.width * ratio);
      canvas.height = Math.floor(viewport.height * ratio);
      canvas.style.width = `${viewport.width}px`;
      canvas.style.height = `${viewport.height}px`;

      markerCanvas.width = Math.floor(viewport.width * ratio);
      markerCanvas.height = Math.floor(viewport.height * ratio);
      markerCanvas.style.width = `${viewport.width}px`;
      markerCanvas.style.height = `${viewport.height}px`;

      const ctx = canvas.getContext("2d", { alpha: false });
      if (!ctx) return;

      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);

      const task = page.render({
        canvasContext: ctx,
        viewport,
      });

      renderTaskRef.current = task;

      try {
        await task.promise;
        if (!cancelled) redrawMarkers();
      } catch {
        // Cancellation is expected when the user changes page or zoom.
      } finally {
        if (renderTaskRef.current === task) renderTaskRef.current = null;
      }
    };

    void render();

    return () => {
      cancelled = true;
      renderTaskRef.current?.cancel();
      renderTaskRef.current = null;
    };
  }, [pdf, pageNumber, pageWidth, zoom, redrawMarkers]);

  function beginMarker(event: React.PointerEvent<HTMLCanvasElement>) {
    event.currentTarget.setPointerCapture(event.pointerId);
    draftRef.current = [pagePoint(event)];
    drawDraft(draftRef.current);
  }

  function moveMarker(event: React.PointerEvent<HTMLCanvasElement>) {
    if (!draftRef.current) return;
    draftRef.current = [...draftRef.current, pagePoint(event)];
    drawDraft(draftRef.current);
  }

  function endMarker() {
    const points = draftRef.current;
    draftRef.current = null;

    if (!points || points.length < 2) {
      redrawMarkers();
      return;
    }

    setMarkers((current) => ({
      ...current,
      [pageNumber]: [
        ...(current[pageNumber] ?? []),
        { color: markerColor, points },
      ],
    }));
  }

  function drawDraft(points: Point[] | null) {
    const canvas = markerCanvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const width = canvas.clientWidth;
    const height = canvas.clientHeight;
    const ratio = window.devicePixelRatio || 1;

    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.clearRect(0, 0, width, height);

    const allStrokes = [...(markers[pageNumber] ?? [])];
    if (points && points.length > 1) {
      allStrokes.push({ color: markerColor, points });
    }

    for (const stroke of allStrokes) {
      if (stroke.points.length < 2) continue;

      ctx.save();
      ctx.globalAlpha = 0.34;
      ctx.strokeStyle = MARKER[stroke.color].ui;
      ctx.lineWidth = 19;
      ctx.lineCap = "round";
      ctx.lineJoin = "round";
      ctx.beginPath();

      stroke.points.forEach((point, index) => {
        const x = point.x * width;
        const y = point.y * height;
        if (index === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });

      ctx.stroke();
      ctx.restore();
    }
  }

  function undoLast() {
    setMarkers((current) => {
      const strokes = [...(current[pageNumber] ?? [])];
      strokes.pop();

      return { ...current, [pageNumber]: strokes };
    });
  }

  function clearPage() {
    setMarkers((current) => ({ ...current, [pageNumber]: [] }));
  }

  function clearAll() {
    setMarkers({});
  }

  async function downloadMarkedPdf() {
    if (exporting || !hasMarks) return;

    setExporting(true);
    setError("");

    try {
      const { PDFDocument, rgb } = await import("pdf-lib");

      if (item.kind === "ncert") {
        const document = await PDFDocument.create();
        const markedPages = Object.keys(markers)
          .map(Number)
          .filter((page) => (markers[page]?.length ?? 0) > 0)
          .sort((a, b) => a - b);

        for (const sourcePage of markedPages) {
          const exportPage = document.addPage([612, 792]);
          const left = 46;
          const top = 84;
          const width = 520;
          const height = 620;

          exportPage.drawText("mPay Study Marks", {
            x: left,
            y: 744,
            size: 18,
          });
          exportPage.drawText(item.title, {
            x: left,
            y: 718,
            size: 11,
          });
          exportPage.drawText(
            `Class ${item.classLevel} · ${item.subject} · NCERT · Source page ${sourcePage}`,
            {
              x: left,
              y: 700,
              size: 9,
            },
          );
          exportPage.drawText(
            "This PDF contains your saved marker positions only; the textbook remains available from the official NCERT source.",
            {
              x: left,
              y: 682,
              size: 7.5,
              color: rgb(0.35, 0.32, 0.29),
            },
          );

          exportPage.drawRectangle({
            x: left,
            y: 44,
            width,
            height,
            borderWidth: 1,
            borderColor: rgb(0.82, 0.78, 0.71),
          });

          for (const stroke of markers[sourcePage] ?? []) {
            for (let pointIndex = 1; pointIndex < stroke.points.length; pointIndex += 1) {
              const startPoint = stroke.points[pointIndex - 1];
              const endPoint = stroke.points[pointIndex];

              exportPage.drawLine({
                start: {
                  x: left + startPoint.x * width,
                  y: 44 + (1 - startPoint.y) * height,
                },
                end: {
                  x: left + endPoint.x * width,
                  y: 44 + (1 - endPoint.y) * height,
                },
                thickness: 9,
                color: rgb(
                  ...(MARKER[stroke.color].pdf as [number, number, number])
                ),
                opacity: 0.42,
              });
            }
          }
        }

        const output = await document.save();
        const blob = new Blob([output], { type: "application/pdf" });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = `${item.title.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}-marks.pdf`;
        anchor.click();
        window.setTimeout(() => URL.revokeObjectURL(url), 1000);
        return;
      }

      const response = await fetch(
        `/store/api/reader-pdf/${encodeURIComponent(item.slug)}`,
        { cache: "no-store" },
      );

      if (!response.ok) throw new Error("Unable to fetch the source PDF.");

      const bytes = await response.arrayBuffer();
      const document = await PDFDocument.load(bytes);
      const pages = document.getPages();

      pages.forEach((page, index) => {
        const strokes = markers[index + 1] ?? [];
        if (!strokes.length) return;

        const width = page.getWidth();
        const height = page.getHeight();

        for (const stroke of strokes) {
          for (let pointIndex = 1; pointIndex < stroke.points.length; pointIndex += 1) {
            const startPoint = stroke.points[pointIndex - 1];
            const endPoint = stroke.points[pointIndex];

            page.drawLine({
              start: {
                x: startPoint.x * width,
                y: height - startPoint.y * height,
              },
              end: {
                x: endPoint.x * width,
                y: height - endPoint.y * height,
              },
              thickness: 15,
              color: rgb(
                ...(MARKER[stroke.color].pdf as [number, number, number])
              ),
              opacity: 0.34,
            });
          }
        }
      });

      const output = await document.save();
      const blob = new Blob([output], { type: "application/pdf" });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");

      anchor.href = url;
      anchor.download = `${item.title.replace(/[^a-z0-9]+/gi, "-").toLowerCase()}-marked.pdf`;
      anchor.click();

      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch {
      setError(
        item.kind === "ncert"
          ? "Your marks could not be exported. The saved annotations are safe; please retry."
          : "The marked copy could not be generated. Your saved markers are safe; please retry the export.",
      );
    } finally {
      setExporting(false);
    }
  }

  const progress = numPages ? `${pageNumber} / ${numPages}` : "Loading…";
  const hasMarks = Object.values(markers).some((value) => value.length > 0);

  return (
    <main className="reader study-reader">
      <header className="reader-toolbar">
        <div className="reader-toolbar-left">
          <Link href="/library">
            <ArrowLeft size={17} />
            <span>Library</span>
          </Link>
          <button
            type="button"
            className={showShelf ? "active" : ""}
            onClick={() => setShowShelf((open) => !open)}
            aria-label="Open study shelf"
          >
            <List size={16} />
            <span>Study shelf</span>
          </button>
        </div>

        <div className="reader-title">
          <small>{item.title}</small>
          <span>
            {item.kind === "ncert"
              ? `Class ${item.classLevel} · ${item.subject} · NCERT`
              : "Public-domain reading"}
          </span>
        </div>

        <div className="reader-actions">
          <button
            type="button"
            className={markerColor === "yellow" ? "active marker-yellow" : "marker-yellow"}
            onClick={() => setMarkerColor("yellow")}
            title="Yellow marker"
          >
            <Highlighter size={15} />
          </button>
          <button
            type="button"
            className={markerColor === "green" ? "active marker-green" : "marker-green"}
            onClick={() => setMarkerColor("green")}
            title="Light green marker"
          >
            <span />
          </button>
          <button type="button" onClick={undoLast} disabled={!markers[pageNumber]?.length} title="Undo">
            <Undo2 size={15} />
          </button>
          <button type="button" onClick={clearPage} disabled={!markers[pageNumber]?.length} title="Clear page">
            <Eraser size={15} />
          </button>
          <button type="button" onClick={() => setZoom((value) => Math.max(0.75, value - 0.1))} title="Zoom out">
            <Minus size={15} />
          </button>
          <button type="button" onClick={() => setZoom((value) => Math.min(1.6, value + 0.1))} title="Zoom in">
            <Plus size={15} />
          </button>
          <button type="button" onClick={downloadMarkedPdf} disabled={!hasMarks || exporting} title={item.kind === "ncert" ? "Download marker positions PDF" : "Download marked PDF"}>
            <Download size={15} />
            <span>{exporting ? "Exporting…" : item.kind === "ncert" ? "Marks PDF" : "Marked PDF"}</span>
          </button>
        </div>
      </header>

      <div className="study-reader-meta">
        <div>
          <span>READ + MARK</span>
          <b>Use yellow or light green to mark important lines. Marks stay on this device.</b>
        </div>
        <a href={item.sourceUrl} target="_blank" rel="noreferrer">
          Official source
        </a>
      </div>

      <div className="reader-progress">
        <span style={{ width: `${numPages ? (pageNumber / numPages) * 100 : 0}%` }} />
      </div>

      <div className="study-reader-body">
        {showShelf ? (
          <aside className="study-book-drawer open">
            <div className="study-drawer-head">
              <span>YOUR STUDY SHELF</span>
              <b>Classes IX–XII</b>
            </div>
            {[9, 10, 11, 12].map((level) => (
              <div key={level} className="study-drawer-group">
                <small>Class {level}</small>
                {studyBooks
                  .filter((book) => book.classLevel === level)
                  .map((book) => (
                    <Link
                      key={book.slug}
                      href={`/read/${book.slug}`}
                      className={book.slug === item.slug ? "active" : ""}
                    >
                      {book.subject}
                      <span>{book.title}</span>
                    </Link>
                  ))}
              </div>
            ))}
            <Link href="/books#ncert-study" className="store-primary drawer-browse">
              Browse all textbooks
            </Link>
          </aside>
        ) : null}

        <section className="study-reader-stage" ref={stageRef}>
          {item.curriculumNote ? (
            <div className="study-reader-note">{item.curriculumNote}</div>
          ) : null}

          {error ? <div className="study-error">{error}</div> : null}

          {loading ? (
            <div className="study-loading">
              Loading the official {item.kind === "ncert" ? "NCERT" : "book"} PDF…
            </div>
          ) : null}

          <div className="study-page-shell" ref={pageWrapRef}>
            <div className="study-page-canvas">
              <canvas ref={pageCanvasRef} />
              <canvas
                ref={markerCanvasRef}
                className="study-marker-layer"
                onPointerDown={beginMarker}
                onPointerMove={moveMarker}
                onPointerUp={endMarker}
                onPointerCancel={endMarker}
              />
            </div>
          </div>

          <div className="study-page-controls">
            <button
              type="button"
              disabled={pageNumber <= 1}
              onClick={() => setPageNumber((value) => Math.max(1, value - 1))}
            >
              <ChevronLeft size={17} />
              Previous
            </button>

            <label>
              Page
              <input
                value={pageNumber}
                onChange={(event) =>
                  setPageNumber(
                    Math.max(1, Math.min(numPages || 1, Number(event.target.value) || 1)),
                  )
                }
                inputMode="numeric"
              />
              / {numPages || "—"}
            </label>

            <button
              type="button"
              disabled={!numPages || pageNumber >= numPages}
              onClick={() => setPageNumber((value) => Math.min(numPages, value + 1))}
            >
              Next
              <ChevronRight size={17} />
            </button>

            <button type="button" onClick={clearAll} disabled={!hasMarks}>
              <RotateCcw size={15} />
              Clear all marks
            </button>

            <span className="study-page-count">{progress} · {pagePercent}%</span>
          </div>
        </section>
      </div>
    </main>
  );
}
