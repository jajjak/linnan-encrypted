import dotenv from 'dotenv';
import { createServer } from 'http';
import express from 'express';
import { startWebSocket } from './websocket';
import authRoutes from './routes/auth';
import messageRoutes from './routes/messages';
import userRoutes from './routes/users';
import { authMiddleware } from './middleware/auth';
import cors from 'cors';
import helmet from 'helmet';
import 'express-async-errors';

dotenv.config();

const app = express();
const port = process.env.PORT || 3000;

app.use(helmet());
app.use(cors({
  origin: process.env.CLIENT_URL || 'http://localhost',
  credentials: true,
}));
app.use(express.json());

app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

app.use('/api/auth', authRoutes);
app.use('/api/messages', authMiddleware, messageRoutes);
app.use('/api/users', authMiddleware, userRoutes);

app.use((err: any, req: express.Request, res: express.Response, next: express.NextFunction) => {
  console.error('Error:', err);
  res.status(err.status || 500).json({ error: err.message || 'Internal server error' });
});

const httpServer = createServer(app);

startWebSocket(httpServer);

httpServer.listen(port, () => {
  console.log(`Server running on port ${port}`);
  console.log(`WebSocket server running on ws://localhost:${port}`);
});

process.on('SIGTERM', () => {
  httpServer.close(() => {
    console.log('Server closed');
    process.exit(0);
  });
});
