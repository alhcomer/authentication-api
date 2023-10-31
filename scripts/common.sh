#!/bin/bash
set -euo pipefail

[[ "${BASH_SOURCE[0]}" != "${0}" ]] || {
    echo "Error: Script must be sourced, not executed"
    exit 1
}

check_aws_account_used() {
    required_account="${1}"
    account_alias=$(aws iam list-account-aliases)
    aws_exit_status=$?
    if [ $aws_exit_status -ne 0 ]; then
        echo "!! Error talking to the AWS API. Check that you're using the VPN."
        exit 1
    fi

    if ! echo "${account_alias}" | jq --arg required "${1}" -e '.AccountAliases[] | contains($required)' >/dev/null; then
        echo "!! Required AWS account is ${required_account}, but you have provided credentials for: $(echo "${account_alias}" | jq -c '.AccountAliases')"
        exit 1
    fi
}
