#!/bin/sh
set -eu

if [ -z "${APP_DB_USER:-}" ] || [ -z "${APP_DB_PASSWORD:-}" ]; then
  echo "APP_DB_USER and APP_DB_PASSWORD are required" >&2
  exit 1
fi

if [ "$APP_DB_PASSWORD" = "$POSTGRES_PASSWORD" ]; then
  echo "The application and administration database passwords must be different" >&2
  exit 1
fi

if [ "$APP_DB_USER" = "$POSTGRES_USER" ]; then
  echo "The application and administration database role names must be different" >&2
  exit 1
fi

psql --set=ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=app_user="$APP_DB_USER" \
  --set=app_password="$APP_DB_PASSWORD" <<'EOSQL'
SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT',
  :'app_user',
  :'app_password'
)
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'app_user') \gexec

SELECT format(
  'ALTER ROLE %I NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT',
  :'app_user'
) \gexec

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE :"DBNAME" TO :"app_user";
GRANT USAGE, CREATE ON SCHEMA public TO :"app_user";
EOSQL
