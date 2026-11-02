#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if [ -f ./room.env ]; then
  set -a
  . ./room.env
  set +a
fi
: "${ROOM_ADMIN_PASSWORD:?Set ROOM_ADMIN_PASSWORD in room.env}"
: "${ROOM_ENROLLMENT_KEY:?Set ROOM_ENROLLMENT_KEY in room.env}"
exec java -jar room-server.jar "$@"
