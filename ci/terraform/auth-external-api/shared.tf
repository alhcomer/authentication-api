
data "terraform_remote_state" "shared" {
  backend = "s3"
  config = {
    bucket                      = var.shared_state_bucket
    key                         = "${var.environment}-shared-terraform.tfstate"
    role_arn                    = var.deployer_role_arn
    region                      = var.aws_region
    endpoint                    = null
    iam_endpoint                = null
    sts_endpoint                = null
    skip_credentials_validation = false
    skip_metadata_api_check     = false
    force_path_style            = false
  }
}


locals {
  authentication_vpc_arn                    = data.terraform_remote_state.shared.outputs.authentication_vpc_arn
  authentication_security_group_id          = data.terraform_remote_state.shared.outputs.authentication_security_group_id
  authentication_subnet_ids                 = data.terraform_remote_state.shared.outputs.authentication_subnet_ids
  audit_signing_key_arn                     = data.terraform_remote_state.shared.outputs.audit_signing_key_arn
  events_topic_encryption_key_arn           = data.terraform_remote_state.shared.outputs.events_topic_encryption_key_arn
  lambda_code_signing_configuration_arn     = data.terraform_remote_state.shared.outputs.lambda_code_signing_configuration_arn
  lambda_env_vars_encryption_kms_key_arn    = data.terraform_remote_state.shared.outputs.lambda_env_vars_encryption_kms_key_arn
  auth_code_store_signing_configuration_arn = data.terraform_remote_state.shared.outputs.auth_code_store_signing_configuration_arn
  access_token_store_signing_key_arn        = data.terraform_remote_state.shared.outputs.access_token_store_signing_key_arn
}
