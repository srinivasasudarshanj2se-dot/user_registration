FROM node:24-alpine AS build

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine

ENV API_UPSTREAM=http://api:8081
ENV NGINX_ENVSUBST_FILTER=API_UPSTREAM

COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/angular-ui/browser /usr/share/nginx/html

EXPOSE 8080
