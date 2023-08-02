data "terraform_remote_state" "shared" {
  backend = "s3"
  config = {
    bucket                      = var.shared_state_bucket
    key                         = "${var.environment}-shared-terraform.tfstate"
    role_arn                    = var.deployer_role_arn
    region                      = var.aws_region
    skip_credentials_validation = false
    skip_metadata_api_check     = false
    force_path_style            = false
  }
}

locals {
  authentication_security_group_id       = data.terraform_remote_state.shared.outputs.authentication_security_group_id
  authentication_subnet_ids              = data.terraform_remote_state.shared.outputs.authentication_subnet_ids
  lambda_iam_role_arn                    = data.terraform_remote_state.shared.outputs.lambda_iam_role_arn
  lambda_iam_role_name                   = data.terraform_remote_state.shared.outputs.lambda_iam_role_name
  audit_signing_key_alias_name           = data.terraform_remote_state.shared.outputs.audit_signing_key_alias_name
  audit_signing_key_arn                  = data.terraform_remote_state.shared.outputs.audit_signing_key_arn
  logging_endpoint_arns                  = var.logging_endpoint_arns
  cloudwatch_key_arn                     = data.terraform_remote_state.shared.outputs.cloudwatch_encryption_key_arn
  cloudwatch_log_retention               = 5
  authentication_vpc_arn                 = data.terraform_remote_state.shared.outputs.authentication_vpc_arn
  lambda_env_vars_encryption_kms_key_arn = data.terraform_remote_state.shared.outputs.lambda_env_vars_encryption_kms_key_arn
  events_topic_encryption_key_arn        = data.terraform_remote_state.shared.outputs.events_topic_encryption_key_arn
  lambda_code_signing_configuration_arn  = data.terraform_remote_state.shared.outputs.lambda_code_signing_configuration_arn
}

data "aws_sns_topic" "event_stream" {
  name = "${var.environment}-events"
}

data "aws_sns_topic" "slack_events" {
  name = "${var.environment}-slack-events"
}
