/**
 * Coerces app-served plant image URLs to same-origin paths.
 *
 * When `planted.storage.public-base-url` (or an old override) embeds e.g.
 * `http://192.168.0.10:8080/images/...` in JSON, the browser would load that host
 * directly and can break on another subnet. Paths under `/images/` are always
 * served via the Next dev proxy (or production edge) on the page origin.
 *
 * External URLs (S3, iNaturalist, GBIF, etc.) are returned unchanged — they do
 * not use the `/images/` path prefix used by this app for local/Spring files.
 */
export function plantImageSrc(url: string | null | undefined): string {
  if (url == null) return "";
  const t = url.trim();
  if (t.startsWith("/")) return t;
  try {
    const u = new URL(t);
    if (u.pathname.startsWith("/images/")) {
      return `${u.pathname}${u.search}`;
    }
  } catch {
    /* ignore malformed */
  }
  return t;
}
