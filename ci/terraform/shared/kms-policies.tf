# Encryption policy for user credentials table
resource "aws_iam_policy" "user_credentials_encryption_key_kms_policy" {
  name        = "${var.environment}-user-credentials-table-encryption-key-kms-policy"
  path        = "/"
  description = "IAM policy for managing KMS encryption of the user credentials table"

  policy = data.aws_iam_policy_document.user_credentials_encryption_key_policy_document.json
}

data "aws_iam_policy_document" "user_credentials_encryption_key_policy_document" {
  statement {
    sid    = "AllowAccessToUserCredentialsTableKmsEncryptionKey"
    effect = "Allow"

    actions = [
      "kms:*",
    ]
    resources = [
      aws_kms_key.user_credentials_table_encryption_key.arn
    ]
  }
}

output "user_credentials_encryption_policy" {
  value = aws_iam_policy.user_credentials_encryption_key_kms_policy.arn
}

# Encryption policy for account modifiers
/* Implemented in the following .tf files:
 reset_password.tf
 remove-account.tf
 account-recovery.tf
 verify_code.tf
 verify_mfa_code.tf

 shared_kms_policies Modules are created at the top of each of these files,
 maybe it would make more sense to declare the module elsewhere and just
 call it as is done with modules in ci/terraform/modules
*/

resource "aws_iam_policy" "account_modifiers_encryption_key_kms_policy" {
  name        = "${var.environment}-account-modifiers-table-encryption-key-kms-policy"
  path        = "/"
  description = "IAM policy for managing KMS encryption of the account modifiers table"

  policy = data.aws_iam_policy_document.account_modifiers_encryption_key_policy_document.json
}

data "aws_iam_policy_document" "account_modifiers_encryption_key_policy_document" {
  statement {
    sid    = "AllowAccessToAccountModifiersTableKmsEncryptionKey"
    effect = "Allow"

    actions = [
      "kms:*",
    ]
    resources = [
      aws_kms_key.account_modifiers_table_encryption_key.arn
    ]
  }
}

output "account_modifiers_encryption_policy_arn" {
  value = aws_iam_policy.account_modifiers_encryption_key_kms_policy.arn
}
