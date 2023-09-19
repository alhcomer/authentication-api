variable "shared_state_bucket" {
  type    = string
  default = "digital-identity-dev-tfstate"
}

variable "environment" {
  type = string
}

variable "deployer_role_arn" {
  default     = ""
  description = "The name of the AWS role to assume, leave blank when running locally"
  type        = string
}

variable "auth_ext_lambda_zip_file" {
  default     = "../../../auth-external-api/build/distributions/auth-external-api.zip"
  description = "Location of the Lambda ZIP file"
  type        = string
}

variable "aws_region" {
  default = "eu-west-2"
}

variable "enable_api_gateway_execution_request_tracing" {
  default     = false
  description = "Whether to enable capturing of requests/responses from API gateway runs (ONLY ENABLE IN NON-PROD ENVIRONMENTS)"
}

variable "txma_account_id" {
  default = ""
  type    = string
}

variable "enable_api_gateway_execution_logging" {
  default     = true
  description = "Whether to enable logging of API gateway runs"
}

variable "cloudwatch_log_retention" {
  default     = 1
  type        = number
  description = "The number of day to retain Cloudwatch logs for"
}

variable "logging_endpoint_arns" {
  type        = list(string)
  default     = []
  description = "Amazon Resource Name (ARN) for the CSLS endpoints to ship logs to"
}

variable "internal_sector_uri" {
  type    = string
  default = "undefined"
}


variable "performance_tuning" {
  type = map(object({
    memory : number,
    concurrency : number,
    max_concurrency : number,
    scaling_trigger : number,
  }))
  description = "A map of performance tuning parameters per lambda"
  default     = {}
}

variable "lambda_max_concurrency" {
  default = 0
}

variable "scaling_trigger" {
  default = 0.7
}

variable "lambda_min_concurrency" {
  default     = 1
  type        = number
  description = "The number of lambda instance to keep 'warm'"
}

variable "endpoint_memory_size" {
  default = 1024
  type    = number
}

variable "authentication_auth_callback_uri" {
  default     = ""
  type        = string
  description = "The redirect URI used by the orchestrator's auth callback lambda when calling the token endpoint. N.B. there is no actual redirect in the orchestration <-> authentication flow, but it is a part of OAuth2 formalities"
}

variable "orch_client_id" {
  default     = ""
  type        = string
  description = "The client ID used by the orchestrator in two situations: 1) when passing an authorize request to the authentication frontend - see OIDC module 2) when calling the authentication token endpoint - in this second case there is no real client registry or defined scopes, but it is a part of OAuth2 formalities."
}

variable "authentication_backend_uri" {
  default     = ""
  type        = string
  description = "The base URL for the authentication API (all endpoints). This is not used for HTTP(S) routing within the context of this module, but is used to calculate the expected 'aud(ience)' in the signed client_assertion JWT in the orchestrator's token request to authentication's token endpoint"
}

variable "orch_to_auth_public_signing_key" {
  default     = ""
  type        = string
  description = "A hardcoded value for the public key corresponding to the KMS created in the OIDC module. It is used to validate the signature of a client_assertion JWT (orch<->auth token endpoint)"
}

locals {
  default_performance_parameters = {
    memory          = var.endpoint_memory_size
    concurrency     = var.lambda_min_concurrency
    max_concurrency = var.lambda_max_concurrency
    scaling_trigger = var.scaling_trigger
  }
}
