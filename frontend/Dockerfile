FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
# nginx.conf is rendered from this template at start-up; only API_UPSTREAM and NGINX_LOCAL_RESOLVERS are substituted.
COPY nginx.conf /etc/nginx/templates/default.conf.template
ENV NGINX_ENVSUBST_FILTER=^(API_UPSTREAM|NGINX_LOCAL_RESOLVERS)$
# A imagem oficial preenche NGINX_LOCAL_RESOLVERS com o DNS do container.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1
ENV API_UPSTREAM=http://dashboard:8080
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
