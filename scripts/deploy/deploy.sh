#!/usr/bin/env bash
set -euo pipefail

# Green House production deploy script
#
# Usage:
#   ./scripts/deploy/deploy.sh <tag>
#   ./scripts/deploy/deploy.sh <backend-tag> <frontend-tag>
#
# Example:
#   ./scripts/deploy/deploy.sh 20260710-a1b2c3d
#   ./scripts/deploy/deploy.sh backend-20260710-a1b2c3d frontend-20260710-a1b2c3d
#
# Required:
#   - kubectl context must point to mini-pc k3s cluster
#   - GHCR imagePullSecret must already exist in green-house namespace
#   - backend/frontend Deployments must already exist

NAMESPACE="${NAMESPACE:-green-house}"

BACKEND_DEPLOYMENT="${BACKEND_DEPLOYMENT:-green-house-backend}"
FRONTEND_DEPLOYMENT="${FRONTEND_DEPLOYMENT:-green-house-frontend}"

BACKEND_CONTAINER="${BACKEND_CONTAINER:-backend}"
FRONTEND_CONTAINER="${FRONTEND_CONTAINER:-frontend}"

GHCR_OWNER="${GHCR_OWNER:-styughjvbn}"
BACKEND_IMAGE="${BACKEND_IMAGE:-ghcr.io/${GHCR_OWNER}/green-house-backend}"
FRONTEND_IMAGE="${FRONTEND_IMAGE:-ghcr.io/${GHCR_OWNER}/green-house-frontend}"

APP_URL="${APP_URL:-https://green-house.sjw-project.site}"
BACKEND_HEALTH_PATH="${BACKEND_HEALTH_PATH:-/api/dashboard/summary}"

ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-180s}"

if [[ $# -eq 1 ]]; then
  BACKEND_TAG="$1"
  FRONTEND_TAG="$1"
elif [[ $# -eq 2 ]]; then
  BACKEND_TAG="$1"
  FRONTEND_TAG="$2"
else
  echo "Usage:"
  echo "  $0 <tag>"
  echo "  $0 <backend-tag> <frontend-tag>"
  exit 1
fi

BACKEND_FULL_IMAGE="${BACKEND_IMAGE}:${BACKEND_TAG}"
FRONTEND_FULL_IMAGE="${FRONTEND_IMAGE}:${FRONTEND_TAG}"

log() {
  echo
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

fail() {
  echo
  echo "[ERROR] $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Command not found: $1"
}

check_context() {
  log "Checking kubectl context"
  kubectl config current-context
  kubectl get namespace "${NAMESPACE}" >/dev/null
}

check_image_pull_secret() {
  log "Checking GHCR imagePullSecret"
  if ! kubectl -n "${NAMESPACE}" get secret ghcr-secret >/dev/null 2>&1; then
    echo "[WARN] ghcr-secret not found in namespace '${NAMESPACE}'."
    echo "       Private GHCR image pull may fail unless another imagePullSecret is configured."
  fi
}

check_deployments() {
  log "Checking deployments"
  kubectl -n "${NAMESPACE}" get deployment "${BACKEND_DEPLOYMENT}" >/dev/null
  kubectl -n "${NAMESPACE}" get deployment "${FRONTEND_DEPLOYMENT}" >/dev/null
}

show_current_images() {
  log "Current images"

  echo "Backend:"
  kubectl -n "${NAMESPACE}" get deployment "${BACKEND_DEPLOYMENT}" \
    -o jsonpath="{.spec.template.spec.containers[?(@.name=='${BACKEND_CONTAINER}')].image}"
  echo

  echo "Frontend:"
  kubectl -n "${NAMESPACE}" get deployment "${FRONTEND_DEPLOYMENT}" \
    -o jsonpath="{.spec.template.spec.containers[?(@.name=='${FRONTEND_CONTAINER}')].image}"
  echo
}

wait_rollout_or_rollback() {
  local deployment="$1"
  local component="$2"

  log "Waiting rollout: ${deployment}"

  if kubectl -n "${NAMESPACE}" rollout status "deployment/${deployment}" --timeout="${ROLLOUT_TIMEOUT}"; then
    log "${component} rollout succeeded"
    return 0
  fi

  echo
  echo "[ERROR] ${component} rollout failed. Rolling back..." >&2

  kubectl -n "${NAMESPACE}" rollout undo "deployment/${deployment}" || true
  kubectl -n "${NAMESPACE}" rollout status "deployment/${deployment}" --timeout="${ROLLOUT_TIMEOUT}" || true

  echo
  echo "[ERROR] ${component} deployment failed and rollback was attempted." >&2
  return 1
}

deploy_backend() {
  log "Deploying backend: ${BACKEND_FULL_IMAGE}"

  kubectl -n "${NAMESPACE}" set image "deployment/${BACKEND_DEPLOYMENT}" \
    "${BACKEND_CONTAINER}=${BACKEND_FULL_IMAGE}"

  wait_rollout_or_rollback "${BACKEND_DEPLOYMENT}" "Backend"
}

deploy_frontend() {
  log "Deploying frontend: ${FRONTEND_FULL_IMAGE}"

  kubectl -n "${NAMESPACE}" set image "deployment/${FRONTEND_DEPLOYMENT}" \
    "${FRONTEND_CONTAINER}=${FRONTEND_FULL_IMAGE}"

  wait_rollout_or_rollback "${FRONTEND_DEPLOYMENT}" "Frontend"
}

check_pods() {
  log "Pods"
  kubectl -n "${NAMESPACE}" get pods -o wide
}

check_backend_inside_cluster() {
  log "Checking backend health inside cluster"

  kubectl -n "${NAMESPACE}" run curl-test \
    --rm -i \
    --image=curlimages/curl \
    --restart=Never \
    --command -- sh -c \
    "curl -fsS http://${BACKEND_DEPLOYMENT}:8080/actuator/health"
}

check_external() {
  log "Checking external frontend"

  status_code="$(
    curl -k -sS -o /dev/null -w "%{http_code}" "${APP_URL}"
  )"

  echo "Frontend status: ${status_code}"

  case "${status_code}" in
    200|301|302|307|308)
      log "External frontend check passed"
      ;;
    *)
      fail "External frontend check failed. status=${status_code}"
      ;;
  esac
}

print_summary() {
  log "Deploy summary"

  echo "Namespace: ${NAMESPACE}"
  echo "Backend deployment: ${BACKEND_DEPLOYMENT}"
  echo "Frontend deployment: ${FRONTEND_DEPLOYMENT}"
  echo "Backend image: ${BACKEND_FULL_IMAGE}"
  echo "Frontend image: ${FRONTEND_FULL_IMAGE}"
  echo "App URL: ${APP_URL}"

  echo
  echo "Rollback commands:"
  echo "  kubectl -n ${NAMESPACE} rollout undo deployment/${BACKEND_DEPLOYMENT}"
  echo "  kubectl -n ${NAMESPACE} rollout undo deployment/${FRONTEND_DEPLOYMENT}"
}

main() {
  require_command kubectl
  require_command curl

  check_context
  check_image_pull_secret
  check_deployments
  show_current_images

  echo
  echo "New backend image:  ${BACKEND_FULL_IMAGE}"
  echo "New frontend image: ${FRONTEND_FULL_IMAGE}"
  echo

  if [[ "${SKIP_CONFIRM:-false}" != "true" ]]; then
    read -r -p "Deploy to production? [y/N] " answer
    case "${answer}" in
      y|Y|yes|YES) ;;
      *) echo "Cancelled."; exit 0 ;;
    esac
  fi

  deploy_backend
  deploy_frontend

  check_pods
  check_backend_inside_cluster
  check_external
  print_summary

  log "Deploy completed"
}

main "$@"