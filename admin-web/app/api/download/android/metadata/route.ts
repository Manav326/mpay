import { NextResponse } from 'next/server';

export const runtime = 'nodejs';

const RELEASES_URL = 'https://api.github.com/repos/Manav326/mpay/releases/latest';

export async function GET() {
  try {
    const response = await fetch(RELEASES_URL, {
      headers: {
        Accept: 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
        'User-Agent': 'mPay-Web-Android-Release-Check',
      },
      next: { revalidate: 300 },
    });

    if (!response.ok) {
      return NextResponse.json(
        { available: false, message: 'The latest Android release could not be checked right now.' },
        { status: 200, headers: { 'Cache-Control': 'public, max-age=60, stale-while-revalidate=120' } },
      );
    }

    const release = await response.json();
    const asset = Array.isArray(release.assets)
      ? release.assets.find((item: { name?: string }) => item.name === 'mpay-android.apk')
      : undefined;

    if (release.draft || release.prerelease || !asset || !Number.isFinite(Number(asset.size)) || Number(asset.size) <= 0) {
      return NextResponse.json(
        { available: false, message: 'The first signed Android release is being prepared.' },
        { status: 200, headers: { 'Cache-Control': 'public, max-age=60, stale-while-revalidate=120' } },
      );
    }

    const tag = String(release.tag_name || '');
    const versionMatch = tag.match(/^mpay-android-v(.+)-[0-9a-f]{12}$/i);
    const version = versionMatch?.[1] || tag.replace(/^mpay-android-v/i, '') || 'Latest';

    return NextResponse.json(
      {
        available: true,
        version,
        sizeBytes: Number(asset.size),
        publishedAt: release.published_at || null,
      },
      { headers: { 'Cache-Control': 'public, max-age=300, stale-while-revalidate=600' } },
    );
  } catch {
    return NextResponse.json(
      { available: false, message: 'Release information is temporarily unavailable.' },
      { status: 200, headers: { 'Cache-Control': 'public, max-age=60, stale-while-revalidate=120' } },
    );
  }
}
