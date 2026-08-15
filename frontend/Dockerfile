FROM node:24.18.0-alpine3.24@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.30.4-alpine3.24@sha256:44e36330f74d4f3a1d4e222acca9e23b401fb87811a7597024502bb759c4dd49 AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY proxy_params /etc/nginx/proxy_params
COPY --from=build /workspace/dist/ /usr/share/nginx/html/
EXPOSE 8080
