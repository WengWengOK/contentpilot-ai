#!/bin/bash
set -e
set -u

# Create additional databases for dependent services (e.g. Langfuse)
# The default contentops database is already created via POSTGRES_DB.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE langfuse;
    GRANT ALL PRIVILEGES ON DATABASE langfuse TO $POSTGRES_USER;
EOSQL
