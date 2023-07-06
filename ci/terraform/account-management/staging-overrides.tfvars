internal_sector_uri    = "https://identity.staging.account.gov.uk"
lambda_max_concurrency = 3
lambda_min_concurrency = 1
endpoint_memory_size   = 1024
scaling_trigger        = 0.6

logging_endpoint_arns = [
  "arn:aws:logs:eu-west-2:885513274347:destination:csls_cw_logs_destination_prodpython"
]

common_state_bucket = "di-auth-staging-tfstate"
