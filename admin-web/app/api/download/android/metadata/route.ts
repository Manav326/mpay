import { NextResponse } from 'next/server';

export const runtime = 'nodejs';

const RELEASES_URL = 'https://api.github.com/repos/Manav326/mpay/releases/latest';

type ReleaseAsset = { name?: string; size?: number; browser_download_url?: string };

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
    const assets: ReleaseAsset[] = Array.isArray(release.assets) ? release.assets : [];
    const apk = assets.find(asset => asset.name === 'mpay-android.apk');
    const checksum = assets.find(asset => asset.name === 'mpay-android.apk.sha256');
    const tag = String(release.tag_name || '');
    const versionMatch = tag.match(/^mpay-android-v(.+?)-build(\d+)-[0-9a-f]{12}$/i);
    const legacyVersionMatch = tag.match(/^mpay-android-v(.+)-[0-9a-f]{12}$/i);
    const version = versionMatch?.[1] || legacyVersionMatch?.[1] || tag.replace(/^mpay-android-v/i, '') || 'Latest';
    const buildNumber = versionMatch?.[2] || undefined;
    if (
      release.draft || release.prerelease || !apk?.browser_download_url ||
      !Number.isFinite(Number(apk.size)) || Number(apk.size) <= 0 || !checksum?.browser_download_url
    ) {
      return NextResponse.json(
        { available: false, message: 'The next signed Android release is being prepared.' },
        { status: 200, headers: { 'Cache-Control': 'public, max-age=60, stale-while-revalidate=120' } },
      );
    }
    return NextResponse.json(
      {
        available: true, version, buildNumber, sizeBytes: Number(apk.size),
        publishedAt: release.published_at || null,
        releaseUrl: release.html_url || null,
        checksumUrl: checksum.browser_download_url,
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