import { Router, Request, Response } from 'express';
import { PrismaClient } from '@prisma/client';
import * as argon2 from 'argon2';
import * as jwt from 'jsonwebtoken';

const router = Router();
const prisma = new PrismaClient();

const JWT_SECRET = process.env.JWT_SECRET || 'secret';

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

export default router;
