import * as crypto from 'crypto';

/**
 * Instagram's ("Instagram API with Instagram Login") authorization-code exchange requires a
 * confidential client secret, so it cannot happen inside the Android app - that secret would
 * be trivially extractable from the APK. This module does that one exchange here, server-side,
 * then hands the Android app a short-lived, single-use opaque "handoff" id instead of the
 * access token directly in the redirect URL. The app immediately calls back to
 * GET /api/auth/instagram/token/:handoff over HTTPS to collect the real token, and the handoff
 * is deleted the moment it's read (or after HANDOFF_TTL_MS, whichever comes first).
 */

interface HandoffRecord {
  accessToken: string;
  userId?: string;
  username?: string;
  expiresIn?: number;
  createdAt: number;
}

const HANDOFF_TTL_MS = 2 * 60 * 1000; // 2 minutes - plenty for the app to make one HTTPS call.
const handoffs = new Map<string, HandoffRecord>();

function sweepExpired() {
  const now = Date.now();
  for (const [id, record] of handoffs) {
    if (now - record.createdAt > HANDOFF_TTL_MS) {
      handoffs.delete(id);
    }
  }
}

export function createHandoff(record: Omit<HandoffRecord, 'createdAt'>): string {
  sweepExpired();
  const id = crypto.randomBytes(24).toString('base64url');
  handoffs.set(id, { ...record, createdAt: Date.now() });
  return id;
}

/** Single-use: returns the record once, then deletes it. Returns null if missing/expired. */
export function consumeHandoff(id: string): HandoffRecord | null {
  sweepExpired();
  const record = handoffs.get(id);
  if (!record) return null;
  handoffs.delete(id);
  return record;
}

interface ShortLivedTokenResponse {
  access_token: string;
  user_id: string;
  permissions?: string[];
}

interface LongLivedTokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
}

interface InstagramProfile {
  id: string;
  username: string;
}

/**
 * Exchanges the OAuth `code` for an access token, then upgrades it to a long-lived token and
 * fetches the username - all server-to-server calls to graph.instagram.com, using the
 * confidential client secret from the environment. Never logged, never sent to the device.
 */
export async function exchangeInstagramCode(code: string): Promise<{
  accessToken: string;
  expiresIn?: number;
  userId?: string;
  username?: string;
}> {
  const clientId = process.env.INSTAGRAM_CLIENT_ID;
  const clientSecret = process.env.INSTAGRAM_CLIENT_SECRET;
  const redirectUri = process.env.INSTAGRAM_REDIRECT_URI;

  if (!clientId || !clientSecret || !redirectUri) {
    throw new Error(
      'INSTAGRAM_CLIENT_ID / INSTAGRAM_CLIENT_SECRET / INSTAGRAM_REDIRECT_URI is not configured on the backend.'
    );
  }

  const form = new URLSearchParams({
    client_id: clientId,
    client_secret: clientSecret,
    grant_type: 'authorization_code',
    redirect_uri: redirectUri,
    code,
  });

  const shortLivedRes = await fetch('https://api.instagram.com/oauth/access_token', {
    method: 'POST',
    body: form,
  });
  if (!shortLivedRes.ok) {
    throw new Error(`Instagram code exchange failed: ${shortLivedRes.status} ${await shortLivedRes.text()}`);
  }
  const shortLived = (await shortLivedRes.json()) as ShortLivedTokenResponse;

  const longLivedUrl = new URL('https://graph.instagram.com/access_token');
  longLivedUrl.searchParams.set('grant_type', 'ig_exchange_token');
  longLivedUrl.searchParams.set('client_secret', clientSecret);
  longLivedUrl.searchParams.set('access_token', shortLived.access_token);

  const longLivedRes = await fetch(longLivedUrl.toString());
  const longLived = longLivedRes.ok
    ? ((await longLivedRes.json()) as LongLivedTokenResponse)
    : null;

  const finalToken = longLived?.access_token ?? shortLived.access_token;
  const expiresIn = longLived?.expires_in;

  let username: string | undefined;
  try {
    const profileRes = await fetch(
      `https://graph.instagram.com/me?fields=id,username&access_token=${finalToken}`
    );
    if (profileRes.ok) {
      const profile = (await profileRes.json()) as InstagramProfile;
      username = profile.username;
    }
  } catch {
    // Non-fatal: the app still gets a working token even if the profile lookup fails.
  }

  return {
    accessToken: finalToken,
    expiresIn,
    userId: shortLived.user_id,
    username,
  };
}
