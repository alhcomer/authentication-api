
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
  redis_key                                   = "session"
  authentication_vpc_arn                      = data.terraform_remote_state.shared.outputs.authentication_vpc_arn
  authentication_security_group_id            = data.terraform_remote_state.shared.outputs.authentication_security_group_id
  authentication_egress_security_group_id     = data.terraform_remote_state.shared.outputs.authentication_egress_security_group_id
  authentication_oidc_redis_security_group_id = data.terraform_remote_state.shared.outputs.authentication_oidc_redis_security_group_id
  authentication_subnet_ids                   = data.terraform_remote_state.shared.outputs.authentication_subnet_ids
  id_token_signing_key_alias_name             = data.terraform_remote_state.shared.outputs.id_token_signing_key_alias_name
  id_token_signing_key_arn                    = data.terraform_remote_state.shared.outputs.id_token_signing_key_arn
  ipv_token_auth_key_alias_name               = data.terraform_remote_state.shared.outputs.ipv_token_auth_signing_key_alias_name
  ipv_token_auth_signing_key_arn              = data.terraform_remote_state.shared.outputs.ipv_token_auth_signing_key_arn
  orch_to_auth_signing_key_alias_name         = data.terraform_remote_state.shared.outputs.orch_to_auth_signing_key_alias_name
  orch_to_auth_signing_key_arn                = data.terraform_remote_state.shared.outputs.orch_to_auth_signing_key_arn
  doc_app_auth_key_alias_name                 = data.terraform_remote_state.shared.outputs.doc_app_auth_signing_key_alias_name
  doc_app_auth_signing_key_arn                = data.terraform_remote_state.shared.outputs.doc_app_auth_signing_key_arn
  audit_signing_key_alias_name                = data.terraform_remote_state.shared.outputs.audit_signing_key_alias_name
  audit_signing_key_arn                       = data.terraform_remote_state.shared.outputs.audit_signing_key_arn
  sms_bucket_name                             = data.terraform_remote_state.shared.outputs.sms_bucket_name
  lambda_env_vars_encryption_kms_key_arn      = data.terraform_remote_state.shared.outputs.lambda_env_vars_encryption_kms_key_arn
  events_topic_encryption_key_arn             = data.terraform_remote_state.shared.outputs.events_topic_encryption_key_arn
  lambda_parameter_encryption_key_id          = data.terraform_remote_state.shared.outputs.lambda_parameter_encryption_key_id
  lambda_parameter_encryption_alias_id        = data.terraform_remote_state.shared.outputs.lambda_parameter_encryption_alias_id
  redis_ssm_parameter_policy                  = data.terraform_remote_state.shared.outputs.redis_ssm_parameter_policy
  pepper_ssm_parameter_policy                 = data.terraform_remote_state.shared.outputs.pepper_ssm_parameter_policy
  lambda_code_signing_configuration_arn       = data.terraform_remote_state.shared.outputs.lambda_code_signing_configuration_arn
}
