# LinNan Encrypted - Deployment Guide

## Backend Production Deployment on Render

### Prerequisites

- Render account (https://render.com)
- GitHub account with this repository

### Step 1: Create Database on Render

1. Go to [Render Dashboard](https://dashboard.render.com)
2. Click **New +** → **PostgreSQL**
3. Configure:
   - **Name**: `linnan-encrypted-db`
   - **Database**: `linnan_encrypted`
   - **User**: `linnan_user`
   - **Plan**: Free (for testing) or Starter+ (for production)
   - **Region**: Oregon (or your preferred region)
4. Click **Create Database**
5. Copy the **External Database URL** (starts with `postgres://`)

### Step 2: Deploy Backend Service

1. In Render Dashboard, click **New +** → **Web Service**
2. Connect GitHub repository:
   - Search for `linnan-encrypted`
   - Click **Connect**
3. Configure Web Service:
   - **Name**: `linnan-encrypted-backend`
   - **Environment**: Node
   - **Region**: Oregon (same as database)
   - **Branch**: main
   - **Build Command**: `npm install && npm run build`
   - **Start Command**: `npm start`
   - **Plan**: Free (for testing) or Starter (for production)
4. Click **Create Web Service**

### Step 3: Set Environment Variables

In the Web Service settings:

1. Go to **Environment**
2. Add the following variables:

```
NODE_ENV=production
PORT=3000
JWT_SECRET=(generate with: openssl rand -base64 32)
CLIENT_URL=https://<your-render-domain>.onrender.com
DATABASE_URL=(copy from database External URL)
```

**Generate JWT_SECRET locally:**
```bash
openssl rand -base64 32
```

Or use an online generator: https://www.random.org/strings/

### Step 4: Run Prisma Migrations

After deployment, run migrations:

1. In Render Web Service dashboard, go to **Shell**
2. Run:
```bash
npm run prisma:generate
npx prisma migrate deploy
```

Or set up automatic migration via **On-deploy Job** in service settings

### Step 5: Verify Deployment

Once deployed:

1. Visit `https://<service-name>.onrender.com/health`
   - Should return: `{"status":"ok","timestamp":"..."}`

2. Test API:
```bash
# Health check
curl https://<service-name>.onrender.com/health

# Register user
curl -X POST https://<service-name>.onrender.com/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@example.com","password":"test123","publicKey":"test-key"}'

# Login
curl -X POST https://<service-name>.onrender.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test123"}'
```

### Step 6: Update Android App

Once backend is deployed:

1. Get the Render service URL: `https://<service-name>.onrender.com`
2. Update Android app `BuildConfig` or configuration with:
   - `BASE_URL = "https://<service-name>.onrender.com/api"`
   - `WS_URL = "wss://<service-name>.onrender.com"`

## Production Checklist

- [ ] Database created on Render
- [ ] Web Service deployed
- [ ] Environment variables set (JWT_SECRET, DATABASE_URL)
- [ ] Health check responds
- [ ] Prisma migrations applied
- [ ] Register endpoint working
- [ ] Login endpoint working
- [ ] WebSocket connection working
- [ ] Android app updated with production URLs

## Troubleshooting

### Service won't deploy
- Check build logs in Render dashboard
- Ensure `package.json` has all dependencies
- Verify Node version compatibility

### Database connection fails
- Verify `DATABASE_URL` is correct
- Check database is running on Render
- Ensure IP whitelist allows connections (or disable for development)

### Health check fails
- SSH into service: `npm start` should work
- Check logs for port binding issues

### Migrations fail
- Run manually via Render shell:
  ```bash
  npx prisma migrate deploy
  ```

## Security Notes

- Change `JWT_SECRET` to a strong random string
- Enable CORS for your Android app domain
- Use HTTPS only in production
- Never commit `.env` files
- Rotate JWT secrets periodically
- Monitor database for unauthorized access
