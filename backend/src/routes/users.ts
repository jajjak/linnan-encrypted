import { Router } from 'express';
import { PrismaClient } from '@prisma/client';
import { AuthRequest } from '../middleware/auth';

const router = Router();
const prisma = new PrismaClient();

router.get('/search', async (req: AuthRequest, res) => {
  const { q } = req.query;

  if (!q || typeof q !== 'string') {
    res.status(400).json({ error: 'Missing query parameter' });
    return;
  }

  const users = await prisma.user.findMany({
    where: {
      username: {
        contains: q,
        mode: 'insensitive',
      },
    },
    select: {
      id: true,
      username: true,
      publicKey: true,
    },
    take: 20,
  });

  res.json(users);
});

router.get('/profile', async (req: AuthRequest, res) => {
  if (!req.userId) {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }

  const user = await prisma.user.findUnique({
    where: { id: req.userId },
    select: {
      id: true,
      username: true,
      email: true,
      publicKey: true,
      createdAt: true,
    },
  });

  if (!user) {
    res.status(404).json({ error: 'User not found' });
    return;
  }

  res.json(user);
});

router.get('/:userId', async (req: AuthRequest, res) => {
  const { userId } = req.params;

  const user = await prisma.user.findUnique({
    where: { id: userId },
    select: {
      id: true,
      username: true,
      publicKey: true,
      createdAt: true,
    },
  });

  if (!user) {
    res.status(404).json({ error: 'User not found' });
    return;
  }

  res.json(user);
});

router.post('/contacts', async (req: AuthRequest, res) => {
  if (!req.userId) {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }

  const { contactId } = req.body;

  if (!contactId) {
    res.status(400).json({ error: 'Missing contactId' });
    return;
  }

  const contact = await prisma.contact.create({
    data: {
      fromId: req.userId,
      toId: contactId,
    },
  });

  res.status(201).json(contact);
});

export default router;
