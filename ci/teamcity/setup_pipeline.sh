#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

export SERVICE_NAME="${SERVICE_NAME:-ocpp-service}"

export TEAMCITY_PARENT_PROJECT_ID="${TEAMCITY_PARENT_PROJECT_ID:-Amy}"
export TEAMCITY_PROJECT_ID="${TEAMCITY_PROJECT_ID:-Amy_OcppService}"
export TEAMCITY_PROJECT_NAME="${TEAMCITY_PROJECT_NAME:-ocpp-service}"
export TEAMCITY_BUILD_TYPE_ID="${TEAMCITY_BUILD_TYPE_ID:-Amy_OcppService_Build}"
export TEAMCITY_VCS_ROOT_ID="${TEAMCITY_VCS_ROOT_ID:-Amy_HttpsGithubComAmolsurjuseOcppServiceRefsHeadsMain}"

export GIT_URL="${GIT_URL:-${OCPP_SERVICE_GIT_URL:-https://github.com/amolsurjuse/ocpp-service.git}}"
export GIT_BRANCH="${GIT_BRANCH:-${OCPP_SERVICE_GIT_BRANCH:-main}}"
export DOCKER_IMAGE="${DOCKER_IMAGE:-${OCPP_SERVICE_DOCKER_IMAGE:-amolsurjuse/ocpp-service}}"
export DOCKERFILE_PATH="${DOCKERFILE_PATH:-Dockerfile}"
export POM_PATH="${POM_PATH:-pom.xml}"
export MAVEN_GOALS="${MAVEN_GOALS:-clean package}"
export MAVEN_RUNNER_ARGS="${MAVEN_RUNNER_ARGS:--Dspring.profiles.active=test -Docpp.websocket.enabled=false -Dspring.main.lazy-initialization=true}"
export K8S_BRANCH="${K8S_BRANCH:-develop}"
export DEPLOY_VERSION_FILE="${DEPLOY_VERSION_FILE:-charts/config/services/ocpp-service/us/version/dev-version.yaml}"
export DOCKER_USERNAME="${DOCKER_USERNAME:-amolsurjuse}"

exec "${SCRIPT_DIR}/setup_spring_boot_pipeline_common.sh"
