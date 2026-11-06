const LATEST_APK_URL = 'https://github.com/Manav326/mpay/releases/latest/download/mpay-android.apk';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET() {
  try {
    const upstream = await fetch(LATEST_APK_URL, {
      cache: 'no-store',
      redirect: 'follow',
      headers: { 'User-Agent': 'mPay-Web-Android-Download' },
    });

    if (!upstream.ok || !upstream.body) {
      return new Response('The latest signed mPay Android APK is not available yet.', {
        status: 503,
        headers: { 'Cache-Control': 'no-store', 'Content-Type': 'text/plain; charset=utf-8' },
      });
    }

    const headers = new Headers({
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Disposition': 'attachment; filename="mpay-android.apk"',
      'Cache-Control': 'private, no-store, max-age=0',
      'X-Content-Type-Options': 'nosniff',
    });
    const contentLength = upstream.headers.get('content-length');
    if (contentLength) headers.set('Content-Length', contentLength);

    return new Response(upstream.body, { status: 200, headers });
  } catch {
    return new Response('The mPay Android download could not be reached. Please try again.', {
      status: 502,
      headers: { 'Cache-Control': 'no-store', 'Content-Type': 'text/plain; charset=utf-8' },
    });
  }
}
