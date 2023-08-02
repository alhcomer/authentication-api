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
  redis_key                             = "session"
  lambda_code_signing_configuration_arn = data.terraform_remote_state.shared.outputs.lambda_code_signing_configuration_arn
  authentication_vpc_arn                = data.terraform_remote_state.shared.outputs.authentication_vpc_arn
  authentication_subnet_ids             = data.terraform_remote_state.shared.outputs.authentication_subnet_ids
  authentication_security_group_id      = data.terraform_remote_state.shared.outputs.authentication_security_group_id
}
