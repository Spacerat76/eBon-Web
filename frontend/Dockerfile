# Multi-stage build: build with Node, serve with nginx
FROM node:20-alpine AS builder
WORKDIR /app

# Install deps
COPY package.json ./
RUN npm install --silent

# Copy sources and build
COPY . .
RUN npm run build

# Production image
FROM nginx:stable-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
