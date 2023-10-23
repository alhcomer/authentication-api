module "ipv_capacity_role" {
  source      = "../modules/lambda-role"
  environment = var.environment
  role_name   = "ipv-capacity-role"
  vpc_arn     = local.authentication_vpc_arn

  policies_to_attach = [
    aws_iam_policy.audit_signing_key_lambda_kms_signing_policy.arn,
    aws_iam_policy.lambda_sns_policy.arn,
    aws_iam_policy.ipv_capacity_parameter_policy.arn,
    module.oidc_txma_audit.access_policy_arn
  ]
}

module "ipv-capacity" {
  source = "../modules/endpoint-module"

  endpoint_name   = "ipv-capacity"
  path_part       = "ipv-capacity"
  endpoint_method = ["GET"]
  environment     = var.environment

  handler_environment_variables = {
    ENVIRONMENT                    = var.environment
    TXMA_AUDIT_QUEUE_URL           = module.oidc_txma_audit.queue_url
    LOCALSTACK_ENDPOINT            = var.use_localstack ? var.localstack_endpoint : null
    REDIS_KEY                      = local.redis_key
    DYNAMO_ENDPOINT                = var.use_localstack ? var.lambda_dynamo_endpoint : null
    IPV_AUTHORISATION_URI          = var.ipv_authorisation_uri
    IPV_AUTHORISATION_CALLBACK_URI = var.ipv_authorisation_callback_uri
    IPV_AUTHORISATION_CLIENT_ID    = var.ipv_authorisation_client_id
  }
  handler_function_name = "uk.gov.di.authentication.ipv.lambda.IPVCapacityHandler::handleRequest"

  create_endpoint  = true
  rest_api_id      = aws_api_gateway_rest_api.di_authentication_api.id
  root_resource_id = aws_api_gateway_rest_api.di_authentication_api.root_resource_id
  execution_arn    = aws_api_gateway_rest_api.di_authentication_api.execution_arn

  memory_size                 = lookup(var.performance_tuning, "ipv-capacity", local.default_performance_parameters).memory
  provisioned_concurrency     = lookup(var.performance_tuning, "ipv-capacity", local.default_performance_parameters).concurrency
  max_provisioned_concurrency = lookup(var.performance_tuning, "ipv-capacity", local.default_performance_parameters).max_concurrency
  scaling_trigger             = lookup(var.performance_tuning, "ipv-capacity", local.default_performance_parameters).scaling_trigger

  source_bucket           = aws_s3_bucket.source_bucket.bucket
  lambda_zip_file         = aws_s3_object.ipv_api_release_zip.key
  lambda_zip_file_version = aws_s3_object.ipv_api_release_zip.version_id
  code_signing_config_arn = local.lambda_code_signing_configuration_arn

  authentication_vpc_arn = local.authentication_vpc_arn
  security_group_ids = [
    local.authentication_security_group_id,
    local.authentication_oidc_redis_security_group_id,
  ]
  subnet_id                              = local.authentication_subnet_ids
  lambda_role_arn                        = module.ipv_capacity_role.arn
  logging_endpoint_arns                  = var.logging_endpoint_arns
  cloudwatch_key_arn                     = data.terraform_remote_state.shared.outputs.cloudwatch_encryption_key_arn
  cloudwatch_log_retention               = var.cloudwatch_log_retention
  lambda_env_vars_encryption_kms_key_arn = local.lambda_env_vars_encryption_kms_key_arn
  default_tags                           = local.default_tags

  use_localstack = var.use_localstack

  depends_on = [
    aws_api_gateway_rest_api.di_authentication_api,
    aws_api_gateway_resource.connect_resource,
    aws_api_gateway_resource.wellknown_resource,
  ]
}
