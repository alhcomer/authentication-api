cloudwatch_log_retention = 5
lambda_min_concurrency   = 25

notify_template_map = {
  VERIFY_EMAIL_TEMPLATE_ID                 = "09f29c9a-3f34-4a56-88f5-8197aede7f85,bda5cfb3-3d91-407e-90cc-b690c1fa8bf9"
  PASSWORD_RESET_CONFIRMATION_TEMPLATE_ID  = "c5a6a8d6-0a45-4496-bec8-37167fc6ecaa,4afbd99d-7745-4c9e-9caf-a1c054b74998"
  ACCOUNT_CREATED_CONFIRMATION_TEMPLATE_ID = "99580afe-9d3f-4ed1-816d-3b583a7b9167,08e0027d-a087-41f1-a5ae-7c862732ed99"
  RESET_PASSWORD_WITH_CODE_TEMPLATE_ID     = "4f76b165-8935-49fe-8ba8-8ca62a1fe723,ed08fced-e960-4261-8b28-12cb2907cbdf"
  EMAIL_UPDATED_TEMPLATE_ID                = "22aac1ce-38c7-45f5-97b2-26ac1a54a235,17540bf2-5d77-4ac2-be34-ba89c728c60b"
  DELETE_ACCOUNT_TEMPLATE_ID               = "1540bdda-fdff-4297-b627-92102e061bfa,9a212a1d-5bfc-4e7f-80fa-033d3ae03a1c"
  PHONE_NUMBER_UPDATED_TEMPLATE_ID         = "8907d080-e69c-42c6-8cf5-54ca1558a2e4,d12aaa12-1590-4d3d-b75e-e513d299b1b6"
  PASSWORD_UPDATED_TEMPLATE_ID             = "ebf3730c-0769-462b-ab39-7d9a7439bac1,435cf040-2dfc-4d1c-838d-2f349c8d11f1"
  VERIFY_PHONE_NUMBER_TEMPLATE_ID          = "8babad52-0e2e-443a-8a5a-c296dc1696cc,4bbc0a5c-833a-490e-89c6-5e286a030ac6"
  MFA_SMS_TEMPLATE_ID                      = "31e48dbf-6db6-4864-9710-081b72746698,044a2369-420c-4518-85ca-3fe1b9a93244"
  AM_VERIFY_EMAIL_TEMPLATE_ID              = "98fdb807-d0d8-41c8-a1d7-7d0abff06b3c"
  AM_VERIFY_PHONE_NUMBER_TEMPLATE_ID       = "b1b3935d-ffdc-4853-a3cd-a9fce09dbff5"
}

logging_endpoint_arns = [
  "arn:aws:logs:eu-west-2:885513274347:destination:csls_cw_logs_destination_prodpython"
]
