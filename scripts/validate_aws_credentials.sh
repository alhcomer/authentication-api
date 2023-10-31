#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

if [ "${SKIP_AWS_CREDENTIAL_VALIDATION:-}" == "true" ]; then
    exit 0
fi

if [[ -z "${AWS_ACCESS_KEY_ID:-}" || -z "${AWS_SECRET_ACCESS_KEY:-}" ]]; then
    echo "!! AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY must be set in the environment." >&2
    echo "!! Perhaps you meant to prefix the command with 'gds aws digital-identity-dev --'?" >&2
    exit 255
fi

if ! aws sts get-caller-identity >/dev/null 2>&1; then
    echo "!! Current AWS credentials are invalid. They've probably expired." >&2
    echo "!! You will probably need to start again!" >&2
    exit 255
fi

source "${SCRIPT_DIR}/common.sh"
check_aws_account_used "${AWS_ACCOUNT}"
