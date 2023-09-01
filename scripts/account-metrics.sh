#!/usr/bin/env bash

set -eu

export AWS_REGION=eu-west-2
export ENVIRONMENT_NAME=build
export GDS_AWS_ACCOUNT=digital-identity-dev

while getopts "pi" opt; do
  case ${opt} in
    p)
        ENVIRONMENT_NAME=production
        GDS_AWS_ACCOUNT=digital-identity-prod
        echo "Using production environment..."
        shift
      ;;
    i)
        ENVIRONMENT_NAME=integration
        GDS_AWS_ACCOUNT=digital-identity-dev
        echo "Using integration environment..."
        shift
      ;;
    *)
        usage
        exit 1
      ;;
  esac
done

if [ $# -eq 0 ]
  then
    echo "Usage: account-metrics.sh [environment flag] TandCversion"
    exit 1
fi

printf "\nChecking account metrics:  %s, filter on terms and conditions version = %s \n" "${ENVIRONMENT_NAME}" "$1"

gds aws ${GDS_AWS_ACCOUNT} \
    aws dynamodb scan \
      --table-name "${ENVIRONMENT_NAME}-user-profile" \
      --filter-expression "accountVerified = :acv AND attribute_exists(termsAndConditions.version) AND termsAndConditions.version = :vtc" \
      --expression-attribute-values '{":acv":{"N":"1"},":vtc":{"S":"'$1'"} }' \
      --select "COUNT"



