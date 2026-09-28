import { Router, Request, Response } from 'express';
import { PrismaClient } from '@prisma/client';
import * as argon2 from 'argon2';
import * as jwt from 'jsonwebtoken';
import { consumeHandoff, createHandoff, exchangeInstagramCode } from '../services/instagramOAuth';

const router = Router();
const prisma = new PrismaClient();

const JWT_SECRET = process.env.JWT_SECRET || 'secret';
const APP_REDIRECT_SCHEME = process.env.APP_REDIRECT_SCHEME || 'com.linnan.encrypted';

interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  publicKey: string;
}

interface LoginRequest {
  username: string;
  password: string;
}

router.post('/register', async (req: Request<{}, {}, RegisterRequest>, res: Response) => {
  const { username, email, password, publicKey } = req.body;

  if (!username || !email || !password || !publicKey) {
    res.status(400).json({ error: 'Missing required fields' });
    return;
  }

  const hashedPassword = await argon2.hash(password);

  const user = await prisma.user.create({
    data: {
      username,
      email,
      password: hashedPassword,
      publicKey,
    },
  });

  const token = jwt.sign({ userId: user.id, username: user.username }, JWT_SECRET);

  res.status(201).json({
    id: user.id,
    username: user.username,
    email: user.email,
    publicKey: user.publicKey,
    token,
  });
});

router.post('/login', async (req: Request<{}, {}, LoginRequest>, res: Response) => {
  const { username, password } = req.body;

  if (!username || !password) {
    res.status(400).json({ error: 'Missing username or password' });
    return;
  }

  const user = await prisma.user.findUnique({
    where: { username },
  });

  if (!user) {
    res.status(401).json({ error: 'Invalid username or password' });
    return;
  }

  const validPassword = await argon2.verify(user.password, password);

  if (!validPassword) {
    res.status(401).json({ error: 'Invalid username or password' });
    return;
  }

  const token = jwt.sign({ userId: user.id, username: user.username }, JWT_SECRET);

  res.json({
    id: user.id,
    username: user.username,
    email: user.email,
    publicKey: user.publicKey,
    token,
  });
});

/**
 * Meta redirects the user's browser here after they approve (or deny) the login. This is the
 * one hop that needs the confidential client secret, which is why it happens here and not in
 * the app - see src/services/instagramOAuth.ts for why.
 */
router.get('/instagram/callback', async (req: Request, res: Response) => {
  const { code, error, error_description } = req.query as Record<string, string | undefined>;

  if (error) {
    const redirect = new URL(`${APP_REDIRECT_SCHEME}://oauth/instagram/callback`);
    redirect.searchParams.set('error', error);
    if (error_description) redirect.searchParams.set('error_description', error_description);
    res.redirect(redirect.toString());
    return;
  }

  if (!code) {
    res.status(400).json({ error: 'Missing code' });
    return;
  }

  try {
    const result = await exchangeInstagramCode(code);
    const handoff = createHandoff({
      accessToken: result.accessToken,
      userId: result.userId,
      username: result.username,
      expiresIn: result.expiresIn,
    });

    const redirect = new URL(`${APP_REDIRECT_SCHEME}://oauth/instagram/callback`);
    redirect.searchParams.set('handoff', handoff);
    res.redirect(redirect.toString());
  } catch (err) {
    console.error('Instagram code exchange failed:', err);
    const redirect = new URL(`${APP_REDIRECT_SCHEME}://oauth/instagram/callback`);
    redirect.searchParams.set('error', 'exchange_failed');
    res.redirect(redirect.toString());
  }
});

/**
 * The app calls this immediately after being redirected back, over plain HTTPS, to collect the
 * access token the callback above stashed. Single-use: the second call for the same id gets 404.
 */
router.get('/instagram/token/:handoff', (req: Request, res: Response) => {
  const handoffId = Array.isArray(req.params.handoff) ? req.params.handoff[0] : req.params.handoff;
  const record = consumeHandoff(handoffId);
  if (!record) {
    res.status(404).json({ error: 'Handoff not found or already used' });
    return;
  }
  res.json({
    accessToken: record.accessToken,
    userId: record.userId,
    username: record.username,
    expiresIn: record.expiresIn,
  });
});

export default router;
