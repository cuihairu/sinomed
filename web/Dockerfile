# 构建层：Node.js 24 + pnpm 10
FROM node:24-alpine AS build
WORKDIR /build
RUN npm install -g pnpm@10
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# 运行层：非 root nginx（nginx-unprivileged，uid 101，监听 8080）
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
