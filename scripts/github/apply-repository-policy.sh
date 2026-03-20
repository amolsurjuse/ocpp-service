#!/usr/bin/env bash
set -euo pipefail

OWNER="${GH_OWNER:-amolsurjuse}"
REPO="${GH_REPO:-}"
TOKEN="${GITHUB_TOKEN:-}"
BRANCH="${GH_BRANCH:-main}"
PROFILE="${POLICY_PROFILE:-standard}" # small | standard | strict

if [[ -z "${REPO}" ]]; then
  echo "GH_REPO is required"
  exit 1
fi

if [[ -z "${TOKEN}" ]]; then
  echo "GITHUB_TOKEN is required"
  exit 1
fi

case "${PROFILE}" in
  small) APPROVALS=1 ;;
  standard) APPROVALS=2 ;;
  strict) APPROVALS=2 ;;
  *)
    echo "Invalid POLICY_PROFILE: ${PROFILE}"
    echo "Allowed: small | standard | strict"
    exit 1
    ;;
esac

api() {
  local method="$1"
  local path="$2"
  local data="${3:-}"

  if [[ -n "${data}" ]]; then
    curl -sS -X "${method}" \
      -H "Authorization: Bearer ${TOKEN}" \
      -H "Accept: application/vnd.github+json" \
      -H "Content-Type: application/json" \
      "https://api.github.com${path}" \
      -d "${data}"
  else
    curl -sS -X "${method}" \
      -H "Authorization: Bearer ${TOKEN}" \
      -H "Accept: application/vnd.github+json" \
      "https://api.github.com${path}"
  fi
}

echo "Applying security and analysis settings..."
api PATCH "/repos/${OWNER}/${REPO}" "$(jq -cn '
{
  security_and_analysis: {
    secret_scanning: {status: "enabled"},
    secret_scanning_push_protection: {status: "enabled"},
    dependabot_security_updates: {status: "enabled"}
  }
}')" >/dev/null

echo "Enabling Dependabot alerts and automated security fixes..."
api PUT "/repos/${OWNER}/${REPO}/vulnerability-alerts" >/dev/null
api PUT "/repos/${OWNER}/${REPO}/automated-security-fixes" >/dev/null

echo "Applying branch protection to ${BRANCH}..."
api PUT "/repos/${OWNER}/${REPO}/branches/${BRANCH}/protection" "$(jq -cn \
  --argjson approvals "${APPROVALS}" \
  '{
    required_status_checks: {
      strict: true,
      contexts: [
        "build-and-test",
        "dependency-review",
        "license-compliance",
        "gitleaks"
      ]
    },
    enforce_admins: true,
    required_pull_request_reviews: {
      dismiss_stale_reviews: true,
      require_code_owner_reviews: true,
      required_approving_review_count: $approvals,
      require_last_push_approval: true
    },
    restrictions: null,
    required_conversation_resolution: true,
    allow_force_pushes: false,
    allow_deletions: false,
    block_creations: false,
    lock_branch: false,
    allow_fork_syncing: true
  }')" >/dev/null

echo "Upserting baseline labels..."
for label in \
  "type:bug|d73a4a" \
  "type:feature|0e8a16" \
  "type:task|5319e7" \
  "priority:p0|b60205" \
  "priority:p1|d93f0b" \
  "priority:p2|fbca04" \
  "priority:p3|c2e0c6" \
  "status:triage|ededed" \
  "status:in-progress|1d76db" \
  "status:blocked|b60205" \
  "status:done|0e8a16" \
  "security|b60205" \
  "dependencies|0366d6"; do
  name="${label%%|*}"
  color="${label##*|}"
  api POST "/repos/${OWNER}/${REPO}/labels" "$(jq -cn --arg n "${name}" --arg c "${color}" '{name:$n,color:$c}')" >/dev/null || \
  api PATCH "/repos/${OWNER}/${REPO}/labels/${name}" "$(jq -cn --arg n "${name}" --arg c "${color}" '{new_name:$n,color:$c}')" >/dev/null
done

echo "Repository policy applied successfully for ${OWNER}/${REPO} (${PROFILE})."

