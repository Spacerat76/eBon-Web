#!/usr/bin/env bash
set -euo pipefail

if [ -S /var/run/docker.sock ]; then
  docker_gid="$(stat -c '%g' /var/run/docker.sock)"
  docker_group="$(getent group "${docker_gid}" | cut -d: -f1 || true)"

  if [ -z "${docker_group}" ]; then
    docker_group="docker-host"
    groupadd -g "${docker_gid}" "${docker_group}"
  fi

  usermod -aG "${docker_group}" vscode
fi

mkdir -p /workspace/backend/target /workspace/frontend/node_modules
chown -R vscode:vscode /workspace/backend/target /workspace/frontend/node_modules

exec sleep infinity
