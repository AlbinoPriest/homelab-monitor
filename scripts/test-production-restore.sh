#!/bin/sh
set -eu

base_url="${1:-http://127.0.0.1:8080}"
project_name="${COMPOSE_PROJECT_NAME:-homelab-monitor}"
database_name="${POSTGRES_DB:-homelab_monitor}"
admin_user="${POSTGRES_ADMIN_USER:-homelab_admin}"
sentinel_id="00000000-0000-0000-0000-000000000010"

if [ "${RESTORE_TEST_CONFIRM:-}" != "delete-ci-database" ]; then
    echo "Refusing destructive restore test without RESTORE_TEST_CONFIRM=delete-ci-database" >&2
    exit 1
fi
case "$project_name" in
    *-ci|*-audit) ;;
    *)
        echo "Refusing destructive restore test outside a *-ci or *-audit Compose project" >&2
        exit 1
        ;;
esac
work_dir="$(mktemp -d)"

cleanup() {
    docker compose exec -T postgres rm -f /tmp/homelab-monitor-ci.dump /tmp/restore.dump >/dev/null 2>&1 || true
    rm -rf "$work_dir"
}
trap cleanup EXIT

docker compose exec -T postgres psql \
    --username="$admin_user" \
    --dbname="$database_name" \
    --set=ON_ERROR_STOP=1 \
    --command="INSERT INTO monitors (
        id, name, description, type, target, port, enabled, status,
        interval_seconds, timeout_millis, failure_threshold, recovery_threshold,
        latency_warning_millis, expected_http_status, consecutive_failures,
        consecutive_successes, next_check_at, created_at, updated_at, version
    ) VALUES (
        '$sentinel_id', 'CI restore sentinel', 'Production recovery verification',
        'TCP', '127.0.0.1', 9, FALSE, 'PAUSED', 60, 1000, 2, 2,
        500, NULL, 0, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
    );"

docker compose exec -T postgres sh -c \
    'pg_dump --format=custom --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --file=/tmp/homelab-monitor-ci.dump'
docker compose cp postgres:/tmp/homelab-monitor-ci.dump "$work_dir/backup.dump"

docker compose stop frontend backend
docker compose cp "$work_dir/backup.dump" postgres:/tmp/restore.dump
docker compose exec -T postgres sh -c \
    'dropdb --force --if-exists --username="$POSTGRES_USER" "$POSTGRES_DB" && createdb --username="$POSTGRES_USER" "$POSTGRES_DB"'
# Git Bash otherwise rewrites this container path into a Windows host path.
MSYS_NO_PATHCONV=1 docker compose exec -T postgres sh /docker-entrypoint-initdb.d/10-create-app-role.sh
docker compose exec -T postgres sh -c \
    'pg_restore --exit-on-error --no-owner --no-privileges --role="$APP_DB_USER" --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" /tmp/restore.dump'
docker compose up -d backend frontend

timeout 120 sh -c "until [ \"\$(docker inspect --format='{{.State.Health.Status}}' '${project_name}-frontend-1' 2>/dev/null)\" = healthy ]; do sleep 2; done"

restored_name="$(docker compose exec -T postgres psql \
    --username="$admin_user" \
    --dbname="$database_name" \
    --tuples-only --no-align \
    --command="SELECT name FROM monitors WHERE id = '$sentinel_id';")"
test "$restored_name" = "CI restore sentinel"

sh scripts/smoke-production.sh "$base_url"
printf 'Production backup and restore checks passed\n'
