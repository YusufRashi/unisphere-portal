#!/bin/sh
# Runs once, when the database volume is first created.
# Creates a least-privileged login role that owns only the application schema: no superuser, cannot
# create databases or roles. The application (and Flyway) connect with this role, never as "postgres".
set -eu

psql -v ON_ERROR_STOP=1 \
     -v app_user="$APP_DB_USER" \
     -v app_password="$APP_DB_PASSWORD" \
     -v db_name="$POSTGRES_DB" \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE :"db_name" TO :"app_user";
CREATE SCHEMA unisphere AUTHORIZATION :"app_user";
ALTER ROLE :"app_user" SET search_path = unisphere;
EOSQL
