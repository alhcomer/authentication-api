output "base_url" {
  value = local.oidc_api_base_url
}

output "api_gateway_root_id" {
  value = aws_api_gateway_rest_api.di_account_management_api.id
}

output "email_queue" {
  value = aws_sqs_queue.email_queue.id
}

output "txma_audit_queue_arn" {
  value = module.acct_mgmt_txma_audit.queue_arn
}

output "txma_audit_key_arn" {
  value = module.acct_mgmt_txma_audit.kms_key_arn
}