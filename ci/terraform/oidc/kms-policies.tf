### ID Token signing key access

data "aws_iam_policy_document" "kms_policy_document" {
  statement {
    sid     = "AllowAccessToKmsSigningKey"
    effect  = "Allow"
    actions = ["kms:GetPublicKey"]
    resources = [
      local.id_token_signing_key_arn,
      aws_kms_key.id_token_signing_key_rsa.arn
    ]
  }
}

resource "aws_iam_policy" "oidc_default_id_token_public_key_kms_policy" {
  name_prefix = "id-token-kms-policy"
  path        = "/${var.environment}/oidc-default/"
  description = "IAM policy for managing ID token public signing key access"

  policy = data.aws_iam_policy_document.kms_policy_document.json
}

### Audit signing key access

data "aws_iam_policy_document" "audit_payload_kms_signing_policy_document" {
  statement {
    sid       = "AllowAccessToKmsAuditSigningKey"
    effect    = "Allow"
    actions   = ["kms:Sign", "kms:GetPublicKey", "kms:Verify"]
    resources = [local.audit_signing_key_arn]
  }
}

resource "aws_iam_policy" "audit_signing_key_lambda_kms_signing_policy" {
  name_prefix = "audit-payload-kms-signing-policy"
  path        = "/${var.environment}/oidc-default/"
  description = "IAM policy for managing KMS connection for a lambda which allows signing of audit payloads"

  policy = data.aws_iam_policy_document.audit_payload_kms_signing_policy_document.json
}

### Signing key access for OIDC/Orch API to send signed authorize payload to Authentication

data "aws_iam_policy_document" "orch_to_auth_kms_policy_document" {
  statement {
    sid    = "AllowAccessToKmsSigningKey"
    effect = "Allow"

    actions = [
      "kms:Sign",
      "kms:GetPublicKey",
    ]
    resources = [
      local.orch_to_auth_signing_key_arn
    ]
  }
}

resource "aws_iam_policy" "orch_to_auth_kms_policy" {
  name_prefix = "kms-orch-to-auth-policy"
  path        = "/${var.environment}/orch-to-auth-kms-signing/"
  description = "IAM policy for managing Orch/OIDC API's authorize endpoint KMS key access"

  policy = data.aws_iam_policy_document.orch_to_auth_kms_policy_document.json
}

### IPV Token signing key access

data "aws_iam_policy_document" "ipv_token_auth_kms_policy_document" {
  statement {
    sid    = "AllowAccessToKmsSigningKey"
    effect = "Allow"

    actions = [
      "kms:Sign",
      "kms:GetPublicKey",
    ]
    resources = [
      local.ipv_token_auth_signing_key_arn
    ]
  }
}

resource "aws_iam_policy" "ipv_token_auth_kms_policy" {
  name_prefix = "kms-ipv-token-auth-policy"
  path        = "/${var.environment}/ipv-token/"
  description = "IAM policy for managing IPV authentication token KMS key access"

  policy = data.aws_iam_policy_document.ipv_token_auth_kms_policy_document.json
}

### Doc App signing key access

data "aws_iam_policy_document" "doc_app_auth_kms_policy_document" {
  statement {
    sid    = "AllowAccessToKmsSigningKey"
    effect = "Allow"

    actions = [
      "kms:Sign",
      "kms:GetPublicKey",
    ]
    resources = [
      local.doc_app_auth_signing_key_arn
    ]
  }
}

resource "aws_iam_policy" "doc_app_auth_kms_policy" {
  name_prefix = "kms-doc-app-auth-policy"
  path        = "/${var.environment}/doc-app/"
  description = "IAM policy for managing Doc app authentication KMS key access"

  policy = data.aws_iam_policy_document.doc_app_auth_kms_policy_document.json
}

data "aws_iam_policy_document" "auth_code_dynamo_encryption_key_policy_document" {
  statement {
    sid    = "AllowAccessToAuthCodeTableKmsEncryptionKey"
    effect = "Allow"

    actions = [
      "kms:Encrypt*",
      "kms:Decrypt*",
      "kms:GetPublicKey"
    ]
    resources = [
      local.auth_code_store_signing_configuration_arn
    ]
  }
}

resource "aws_iam_policy" "auth_code_dynamo_encryption_key_kms_policy" {
  name        = "${var.environment}-auth-code-table-encryption-key-kms-policy"
  path        = "/"
  description = "IAM policy for managing KMS encryption of the auth code table"

  policy = data.aws_iam_policy_document.auth_code_dynamo_encryption_key_policy_document.json
}

data "aws_iam_policy_document" "authentication_callback_userinfo_encryption_key_policy_document" {
  statement {
    sid    = "AllowAccessToAuthCallbackUserInfoTableKmsEncryptionKey"
    effect = "Allow"

    actions = [
      "kms:Decrypt*"
    ]
    resources = [
      local.authentication_callback_userinfo_encryption_key_arn
    ]
  }
}

resource "aws_iam_policy" "authentication_callback_userinfo_encryption_key_kms_policy" {
  name        = "${var.environment}-authentication-callback-userinfo-encryption-key-kms-policy"
  path        = "/"
  description = "IAM policy for managing KMS encryption of the auth code table"

  policy = data.aws_iam_policy_document.authentication_callback_userinfo_encryption_key_policy_document.json
}