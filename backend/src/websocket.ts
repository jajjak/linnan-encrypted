import { Server as HTTPServer } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import * as jwt from 'jsonwebtoken';

const JWT_SECRET = process.env.JWT_SECRET || 'secret';

interface AuthenticatedWebSocket extends WebSocket {
  userId?: string;
  username?: string;
}

const userConnections = new Map<string, Set<AuthenticatedWebSocket>>();

export function startWebSocket(httpServer: HTTPServer) {
  const wss = new WebSocketServer({ server: httpServer });

  wss.on('connection', (ws: AuthenticatedWebSocket, req) => {
    const token = new URL(`http://localhost${req.url || ''}`).searchParams.get('token');

    if (!token) {
      ws.close(4001, 'Unauthorized');
      return;
    }

    try {
      const decoded: any = jwt.verify(token, JWT_SECRET);
      ws.userId = decoded.userId;
      ws.username = decoded.username;

      if (!userConnections.has(ws.userId)) {
        userConnections.set(ws.userId, new Set());
      }
      userConnections.get(ws.userId)!.add(ws);

      console.log(`User ${ws.username} connected`);

      ws.on('message', (data) => {
        try {
          const message = JSON.parse(data.toString());
          handleWebSocketMessage(ws, message);
        } catch (error) {
          ws.send(JSON.stringify({ error: 'Invalid message format' }));
        }
      });

      ws.on('close', () => {
        const connections = userConnections.get(ws.userId!);
        if (connections) {
          connections.delete(ws);
          if (connections.size === 0) {
            userConnections.delete(ws.userId!);
          }
        }
        console.log(`User ${ws.username} disconnected`);
      });

      ws.on('error', (error) => {
        console.error('WebSocket error:', error);
      });

      ws.send(JSON.stringify({ type: 'connected', userId: ws.userId }));
    } catch (error) {
      ws.close(4001, 'Unauthorized');
    }
  });

  console.log('WebSocket server started');
}

function handleWebSocketMessage(ws: AuthenticatedWebSocket, message: any) {
  const { type, receiverId, content } = message;

  if (type === 'message') {
    const receiverConnections = userConnections.get(receiverId);
    if (receiverConnections) {
      const payload = {
        type: 'message',
        senderId: ws.userId,
        senderUsername: ws.username,
        content,
        timestamp: new Date().toISOString(),
      };
      receiverConnections.forEach((conn) => {
        if (conn.readyState === 1) {
          conn.send(JSON.stringify(payload));
        }
      });
    }
  }

  if (type === 'typing') {
    const receiverConnections = userConnections.get(receiverId);
    if (receiverConnections) {
      const payload = {
        type: 'typing',
        senderId: ws.userId,
        senderUsername: ws.username,
      };
      receiverConnections.forEach((conn) => {
        if (conn.readyState === 1) {
          conn.send(JSON.stringify(payload));
        }
      });
    }
  }
}
