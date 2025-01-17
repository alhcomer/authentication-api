internal_sector_uri = "https://identity.build.account.gov.uk"

lambda_max_concurrency = 0
lambda_min_concurrency = 1
endpoint_memory_size   = 1536
scaling_trigger        = 0.6

logging_endpoint_arns = [
  "arn:aws:logs:eu-west-2:885513274347:destination:csls_cw_logs_destination_prodpython"
]

blocked_email_duration                    = 30
otp_code_ttl_duration                     = 120
email_acct_creation_otp_code_ttl_duration = 60
