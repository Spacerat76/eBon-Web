# eBon Frontend

This folder contains the React (Vite + TypeScript) frontend for the eBon Expense Tracker.

Quickstart (development)

```bash
cd frontend
npm install
npm run dev
```

Build (production)

```bash
npm run build
# serve locally
npm run preview
```

Build Docker image (production)

```bash
# from repository root
docker build -t ebon-frontend:latest -f frontend/Dockerfile ./frontend
# run it (assumes backend is reachable at backend:8080)
docker run -p 8080:80 ebon-frontend:latest
```

Notes

- The provided `nginx.conf` proxies `/api/*` to `http://backend:8080`. Adjust if your backend service name differs.
- Environment variable `VITE_API_BASE_URL` can be used to override the base API URL during development.
