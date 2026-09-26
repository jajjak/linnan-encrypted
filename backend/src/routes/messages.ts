import { Router } from 'express';
import { PrismaClient } from '@prisma/client';
import { AuthRequest } from '../middleware/auth';

const router = Router();
const prisma = new PrismaClient();

router.get('/conversations', async (req: AuthRequest, res) => {
  if (!req.userId) {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }

  const conversations = await prisma.message.findMany({
    where: {
      OR: [
        { senderId: req.userId },
        { receiverId: req.userId },
      ],
    },
    include: {
      sender: { select: { id: true, username: true } },
      receiver: { select: { id: true, username: true } },
    },
    orderBy: { createdAt: 'desc' },
    take: 50,
  });

  res.json(conversations);
});

router.get('/:userId', async (req: AuthRequest, res) => {
  if (!req.userId) {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }

  const { userId } = req.params;

  const messages = await prisma.message.findMany({
    where: {
      OR: [
        { senderId: req.userId, receiverId: userId },
        { senderId: userId, receiverId: req.userId },
      ],
    },
    include: {
      sender: { select: { id: true, username: true } },
      receiver: { select: { id: true, username: true } },
    },
    orderBy: { createdAt: 'asc' },
  });

  await prisma.message.updateMany({
    where: {
      receiverId: req.userId,
      senderId: userId,
      isRead: false,
    },
    data: { isRead: true },
  });

  res.json(messages);
});

router.post('/', async (req: AuthRequest, res) => {
  if (!req.userId) {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }

  const { receiverId, encryptedContent, iv, salt } = req.body;

  if (!receiverId || !encryptedContent || !iv || !salt) {
    res.status(400).json({ error: 'Missing required fields' });
    return;
  }

  const message = await prisma.message.create({
    data: {
      senderId: req.userId,
      receiverId,
      encryptedContent,
      iv,
      salt,
    },
    include: {
      sender: { select: { id: true, username: true } },
      receiver: { select: { id: true, username: true } },
    },
  });

  res.status(201).json(message);
});

export default router;
