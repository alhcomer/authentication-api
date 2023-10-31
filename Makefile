.DEFAULT_GOAL := help

NOW = $(shell date -u +"%Y-%m-%dT%H:%M:%SZ")

.PHONY: help
help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

_READ_SECRETS_SCRIPTS_IN_MODULES=$(shell find ci/terraform -type f -name read_secrets.sh)
.PHONY: update_read_secrets_scripts
update_read_secrets_scripts: ## Update read_secrets.sh in all terraform modules from scripts/read_secrets__main.sh
update_read_secrets_scripts: $(_READ_SECRETS_SCRIPTS_IN_MODULES)

$(_READ_SECRETS_SCRIPTS_IN_MODULES): scripts/read_secrets__main.sh
	@cp -pvf $< $@


.PHONY: .check-env
.check-env:
	$(if ${DEPLOY_ENV},,$(error Must pass DEPLOY_ENV=<name>))
	$(if ${MAKEFILE_ENV_TARGET},,$(error Must set MAKEFILE_ENV_TARGET))
	$(if ${IN_TERRAFORM_SHELL},$(error Must not use this makefile while in a terraform shell),)
	@./scripts/validate_aws_credentials.sh


.PHONY: sandpit
sandpit: ## Deploy to sandpit
	$(if ${DEPLOY_ENV},,$(eval export DEPLOY_ENV=sandpit))
	$(eval export MAKEFILE_ENV_TARGET=sandpit)
	$(eval export AWS_ACCOUNT=gds-digital-identity-dev)
	@true


sandpit%: sandpit
	$(eval export DEPLOY_ENV=$@)
	@true

.PHONY: build
build:
	./gradlew build buildZip -x test -x spotlessCheck

.tf-envars: .check-env
	$(info Loading secrets for ${DEPLOY_ENV})
	$(foreach var,$(shell scripts/tf_secrets.sh ${DEPLOY_ENV}),$(eval export $(var)))
	@true

terraform-init-%: .check-env
	$(eval COMPONENT?=$(patsubst terraform-init-%,%, $@))
	@cd ci/terraform/$(COMPONENT) && \
	rm -rf ./.terraform/ && \
	terraform init -backend-config=${DEPLOY_ENV}.hcl

terraform-plan-%: .check-env .tf-envars terraform-init-%
	$(eval COMPONENT?=$(patsubst terraform-plan-%,%, $@))
	@cd ci/terraform/$(COMPONENT) && \
	terraform plan -var-file=${DEPLOY_ENV}.tfvars -out=/tmp/$(NOW)-${DEPLOY_ENV}-$(COMPONENT).tfplan

terraform-apply-%: .check-env .tf-envars terraform-plan-%
	$(eval COMPONENT?=$(patsubst terraform-apply-%,%, $@))
	@test -f /tmp/$(NOW)-${DEPLOY_ENV}-$(patsubst terraform-apply-%,%, $@).tfplan || exit 0
	@cd ci/terraform/$(COMPONENT) && \
	terraform apply -auto-approve /tmp/$(NOW)-${DEPLOY_ENV}-$(patsubst terraform-apply-%,%, $@).tfplan

terraform-console-%: .check-env .tf-envars terraform-init-%
	$(eval COMPONENT?=$(patsubst terraform-console-%,%, $@))
	@cd ci/terraform/$(COMPONENT) && \
	terraform console -var-file=${DEPLOY_ENV}.tfvars

terraform-shell-%: .check-env .tf-envars terraform-init-%
	$(eval COMPONENT?=$(patsubst terraform-shell-%,%, $@))
	$(eval export IN_TERRAFORM_SHELL=true)
	@cd ci/terraform/$(COMPONENT) && \
	echo "Starting shell in $(COMPONENT) for environment ${DEPLOY_ENV}" && \
	$$SHELL -i
