"use client";

import Link from "next/link";
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Download,
  Eraser,
  Hand,
  Highlighter,
  List,
  Minus,
  Plus,
  RotateCcw,
  Undo2,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { PDFDocumentProxy, PDFPageProxy } from "pdfjs-dist";
import type { ReadableItem } from "./readings";
import { studyBooks } from "./studyBooks";

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
  const [markerEnabled, setMarkerEnabled] = useState(false);
  const [pageDirection, setPageDirection] = useState<"next" | "previous">("next");
  const touchStartRef = useRef<{ x: number; y: number } | null>(null);
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
        const pdfjs = await import("pdfjs-dist");
        pdfjs.GlobalWorkerOptions.workerSrc = new URL(
          "pdfjs-dist/build/pdf.worker.min.mjs",
          import.meta.url,
        ).toString();

        const pdfUrl =
          item.kind === "ncert"
            ? item.pdfUrl
            : `/store/api/reader-pdf/${encodeURIComponent(item.slug)}`;

        const task = pdfjs.getDocument({
          url: pdfUrl,
          withCredentials: false,
          disableAutoFetch: false,
          disableStream: false,
          disableRange: false,
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
            "The complete book is not currently installed in the Store content bundle. Please contact Store support.",
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
        canvas,
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

  function goToPage(target: number, direction: "next" | "previous") {
    if (!numPages) return;
    const nextPage = Math.max(1, Math.min(numPages, target));
    if (nextPage === pageNumber) return;
    setPageDirection(direction);
    setPageNumber(nextPage);
  }

  function handlePageTouchStart(event: React.TouchEvent<HTMLDivElement>) {
    if (markerEnabled) return;
    const touch = event.changedTouches[0];
    if (!touch) return;
    touchStartRef.current = { x: touch.clientX, y: touch.clientY };
  }

  function handlePageTouchEnd(event: React.TouchEvent<HTMLDivElement>) {
    if (markerEnabled) {
      touchStartRef.current = null;
      return;
    }

    const start = touchStartRef.current;
    touchStartRef.current = null;
    const touch = event.changedTouches[0];
    if (!start || !touch || !numPages) return;

    const dx = touch.clientX - start.x;
    const dy = touch.clientY - start.y;

    if (Math.abs(dx) < 48 || Math.abs(dx) <= Math.abs(dy)) return;

    if (dx < 0 && pageNumber < numPages) {
      goToPage(pageNumber + 1, "next");
    } else if (dx > 0 && pageNumber > 1) {
      goToPage(pageNumber - 1, "previous");
    }
  }

  function beginMarker(event: React.PointerEvent<HTMLCanvasElement>) {
    if (!markerEnabled) return;
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
        const exportDocument = await PDFDocument.create();
        const markedPages = Object.keys(markers)
          .map(Number)
          .filter((page) => (markers[page]?.length ?? 0) > 0)
          .sort((a, b) => a - b);

        for (const sourcePage of markedPages) {
          const exportPage = exportDocument.getPages()[sourcePage - 1];
          if (!exportPage) continue;

          const width = exportPage.getWidth();
          const height = exportPage.getHeight();

          for (const stroke of markers[sourcePage] ?? []) {
            for (let pointIndex = 1; pointIndex < stroke.points.length; pointIndex += 1) {
              const startPoint = stroke.points[pointIndex - 1];
              const endPoint = stroke.points[pointIndex];

              exportPage.drawLine({
                start: {
                  x: startPoint.x * width,
                  y: height - startPoint.y * height,
                },
                end: {
                  x: endPoint.x * width,
                  y: height - endPoint.y * height,
                },
                thickness: Math.max(6, Math.min(18, width * 0.018)),
                color: rgb(
                  ...(MARKER[stroke.color].pdf as [number, number, number])
                ),
                opacity: 0.42,
              });
            }
          }
        }

        const output = await exportDocument.save();
        const blob = new Blob([output], { type: "application/pdf" });
        const url = URL.createObjectURL(blob);
        const anchor = window.document.createElement("a");
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
      const sourceDocument = await PDFDocument.load(bytes);
      const pages = sourceDocument.getPages();

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

      const output = await sourceDocument.save();
      const blob = new Blob([output], { type: "application/pdf" });
      const url = URL.createObjectURL(blob);
      const anchor = window.document.createElement("a");

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
            className={markerEnabled ? "active marker-toggle" : "marker-toggle"}
            onClick={() => setMarkerEnabled((enabled) => !enabled)}
            title={markerEnabled ? "Turn marker off and enable reading/scrolling" : "Turn marker on"}
            aria-pressed={markerEnabled}
          >
            <Hand size={15} />
          </button>
          <button
            type="button"
            className={markerColor === "yellow" && markerEnabled ? "active marker-yellow" : "marker-yellow"}
            onClick={() => {
              setMarkerColor("yellow");
              setMarkerEnabled(true);
            }}
            title="Yellow marker"
            aria-pressed={markerColor === "yellow" && markerEnabled}
          >
            <Highlighter size={15} />
          </button>
          <button
            type="button"
            className={markerColor === "green" && markerEnabled ? "active marker-green" : "marker-green"}
            onClick={() => {
              setMarkerColor("green");
              setMarkerEnabled(true);
            }}
            title="Light green marker"
            aria-pressed={markerColor === "green" && markerEnabled}
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
          {item.kind === "ncert" ? (
            <>
              <a
                className="reader-download-link"
                href={`${item.pdfUrl}?download=1`}
                title="Download the complete textbook PDF"
                aria-label="Download complete textbook PDF"
              >
                <Download size={15} />
                <span>Book PDF</span>
              </a>
              <button
                type="button"
                onClick={downloadMarkedPdf}
                disabled={!hasMarks || exporting}
                title="Download the complete textbook with your saved markers"
              >
                <Download size={15} />
                <span>{exporting ? "Exporting…" : "Marked book"}</span>
              </button>
            </>
          ) : (
            <button
              type="button"
              onClick={downloadMarkedPdf}
              disabled={!hasMarks || exporting}
              title="Download the marked PDF"
            >
              <Download size={15} />
              <span>{exporting ? "Exporting…" : "Marked PDF"}</span>
            </button>
          )}
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

          <div
            className="study-page-shell"
            ref={pageWrapRef}
            onTouchStart={handlePageTouchStart}
            onTouchEnd={handlePageTouchEnd}
          >
            <div
              key={`${item.slug}-page-${pageNumber}-${pageDirection}`}
              className={`study-page-canvas page-transition-${pageDirection}`}
            >
              <canvas ref={pageCanvasRef} />
              <canvas
                ref={markerCanvasRef}
                className={markerEnabled ? "study-marker-layer active" : "study-marker-layer"}
                onPointerDown={beginMarker}
                onPointerMove={moveMarker}
                onPointerUp={endMarker}
                onPointerCancel={endMarker}
              />
            </div>
          </div>

          <div className="study-reader-mode">
            <span className={markerEnabled ? "active" : ""}>
              {markerEnabled ? "MARK MODE" : "READ MODE"}
            </span>
            <b>
              {markerEnabled
                ? "Swipe/stroke on the page to mark. Tap the marker button to return to normal scrolling."
                : "Scroll normally. Turn on the marker only when you want to highlight something."}
            </b>
          </div>

          <div className="study-page-controls">
            <button
              type="button"
              disabled={pageNumber <= 1}
              onClick={() => goToPage(pageNumber - 1, "previous")}
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
              onClick={() => goToPage(pageNumber + 1, "next")}
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
