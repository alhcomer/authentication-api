custom_doc_app_claim_enabled       = true
doc_app_api_enabled                = true
ipv_capacity_allowed               = true
ipv_api_enabled                    = true
doc_app_authorisation_client_id    = "authOrchestratorDocApp"
doc_app_authorisation_uri          = "https://www.review-b.staging.account.gov.uk/dca/oauth2/authorize"
doc_app_backend_uri                = "https://api-backend-api.review-b.staging.account.gov.uk"
doc_app_domain                     = "https://api.review-b.staging.account.gov.uk"
doc_app_authorisation_callback_uri = "https://oidc.staging.account.gov.uk/doc-app-callback"
doc_app_encryption_key_id          = "ca6d5930-77a6-41a4-8192-125df996c084"
doc_app_cri_data_endpoint          = "userinfo"
doc_app_jwks_endpoint              = "https://api-backend-api.review-b.staging.account.gov.uk/.well-known/jwks.json"
ipv_authorisation_client_id        = "authOrchestrator"
ipv_authorisation_uri              = "https://identity.staging.account.gov.uk/oauth2/authorize"
ipv_authorisation_callback_uri     = "https://oidc.staging.account.gov.uk/ipv-callback"
ipv_audience                       = "https://identity.staging.account.gov.uk"
ipv_backend_uri                    = "https://api.identity.staging.account.gov.uk"
internal_sector_uri                = "https://identity.staging.account.gov.uk"
spot_enabled                       = true
language_cy_enabled                = true
extended_feature_flags_enabled     = true
test_clients_enabled               = "true"
ipv_no_session_response_enabled    = true
doc_app_cri_data_v2_endpoint       = "userinfo/v2"
doc_app_use_cri_data_v2_endpoint   = true
doc_app_decouple_enabled           = false
orch_client_id                     = "orchestrationAuth"
auth_audience                      = "https://auth.staging.account.gov.uk"

ipv_auth_public_encryption_key = <<-EOT
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyB5V0Tc9KEV5/zGUHLu0
ZVX0xbDhCyaNwWjJILV0pJE+HmAUc8Azc42MY9mAm0D3LYF8PcWsBa1cIgJF6z7j
LoM43PR/BZafvYeW7GwIun+pugSQO5ljKzUId42ydh0ynwEXJEoMQd3p4e/EF4Ut
yGCV108TgoqDvD50dtqNOw1wBsfbq4rUaRTxhpJLIo8tujmGpf1YVWymQEk+FlyN
LlZL4UE/eEyp+qztIsVXJfyhcC/ezrr5e0FnZ1U0iJavhdmBqmIaLi3SjNawNdEQ
RWDJd2Fit4x9bFIqpZKqc1pGLu39UEaHLzRgi0hVDQhG5A7LpErOMjWquS2lmkwa
3wIDAQAB
-----END PUBLIC KEY-----
EOT

auth_frontend_public_encryption_key = <<-EOT
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzZGTSE8TLLtQjdmD6SiF
SKbfv63JPCV+acPLQc4MjAKK7yT/QhERkemky+oPBIqCJgUq1gmOzdCAje/QEFlD
qwry65oEaUBlWmGlNTPBnUzy/d6mYMfZObsr+yI1HszZE193ABAwtPttCFhFZWov
+rF2Oc9dmiAKXuT0whbOXaj1+751w5qJpsMWgHj91at9gdOZ31huoxnLkuAK/rus
wEBMjmuOzy5osorLg9RCJQVN91Bp932vQS7hXirDpfBhCuQfYQMjFXv4MhCKnk42
pi0FWWzbnn9UcbdcS/Sl5UeuTyCQ+MrunV/XGjIrPMWaFUIQomX1+pCMHkthbQ0J
AQIDAQAB
-----END PUBLIC KEY-----
EOT

performance_tuning = {
  register = {
    memory          = 512
    concurrency     = 0
    max_concurrency = 0
    scaling_trigger = 0
  }

  update = {
    memory          = 512
    concurrency     = 0
    max_concurrency = 0
    scaling_trigger = 0
  }

  reset-password = {
    memory          = 1024
    concurrency     = 2
    max_concurrency = 10
    scaling_trigger = 0.5
  }

  reset-password-request = {
    memory          = 1024
    concurrency     = 2
    max_concurrency = 10
    scaling_trigger = 0.5
  }
}
lambda_max_concurrency = 10
lambda_min_concurrency = 3
endpoint_memory_size   = 1536
scaling_trigger        = 0.6

logging_endpoint_arns = [
  "arn:aws:logs:eu-west-2:885513274347:destination:csls_cw_logs_destination_prodpython"
]

shared_state_bucket = "di-auth-staging-tfstate"
