FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
# nginx.conf is rendered from this template at start-up; only API_UPSTREAM is substituted.
COPY nginx.conf /etc/nginx/templates/default.conf.template
ENV NGINX_ENVSUBST_FILTER=^API_UPSTREAM$
ENV API_UPSTREAM=http://dashboard:8080
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
