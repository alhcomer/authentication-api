data "aws_dynamodb_table" "user_credentials_table" {
  name = "${var.environment}-user-credentials"
}

data "aws_dynamodb_table" "user_profile_table" {
  name = "${var.environment}-user-profile"
}

data "aws_dynamodb_table" "access_token_store_table" {
  name = "${var.environment}-access-token-store"
}

data "aws_iam_policy_document" "dynamo_user_write_policy_document" {
  statement {
    sid    = "AllowAccessToDynamoTables"
    effect = "Allow"

    actions = [
      "dynamodb:BatchWriteItem",
      "dynamodb:UpdateItem",
      "dynamodb:PutItem",
    ]
    resources = [
      data.aws_dynamodb_table.user_credentials_table.arn,
      data.aws_dynamodb_table.user_profile_table.arn,
      "${data.aws_dynamodb_table.user_profile_table.arn}/index/*",
      "${data.aws_dynamodb_table.user_credentials_table.arn}/index/*",
    ]
  }
}

data "aws_iam_policy_document" "dynamo_user_read_policy_document" {
  statement {
    sid    = "AllowAccessToDynamoTables"
    effect = "Allow"

    actions = [
      "dynamodb:BatchGetItem",
      "dynamodb:DescribeStream",
      "dynamodb:DescribeTable",
      "dynamodb:Get*",
      "dynamodb:Query",
      "dynamodb:Scan",
    ]
    resources = [
      data.aws_dynamodb_table.user_credentials_table.arn,
      data.aws_dynamodb_table.user_profile_table.arn,
      "${data.aws_dynamodb_table.user_profile_table.arn}/index/*",
      "${data.aws_dynamodb_table.user_credentials_table.arn}/index/*",
    ]
  }
}

data "aws_iam_policy_document" "dynamo_access_token_store_write_policy_document" {
  statement {
    sid    = "AllowAccessToDynamoTables"
    effect = "Allow"

    actions = [
      "dynamodb:BatchWriteItem",
      "dynamodb:UpdateItem",
      "dynamodb:PutItem",
    ]
    resources = [
      data.aws_dynamodb_table.access_token_store_table.arn,
      "${data.aws_dynamodb_table.access_token_store_table.arn}/index/*",
    ]
  }
}

data "aws_iam_policy_document" "dynamo_access_token_store_read_policy_document" {
  statement {
    sid    = "AllowAccessToDynamoTables"
    effect = "Allow"

    actions = [
      "dynamodb:BatchGetItem",
      "dynamodb:DescribeStream",
      "dynamodb:DescribeTable",
      "dynamodb:Get*",
      "dynamodb:Query",
      "dynamodb:Scan",
    ]
    resources = [
      data.aws_dynamodb_table.access_token_store_table.arn,
      "${data.aws_dynamodb_table.access_token_store_table.arn}/index/*",
    ]
  }
}

resource "aws_iam_policy" "dynamo_access_token_store_read_access_policy" {
  name_prefix = "dynamo-access-token-store-read-policy"
  path        = "/${var.environment}/auth-ext/"
  description = "IAM policy for managing read permissions to the Dynamo Access Token Store table"

  policy = data.aws_iam_policy_document.dynamo_access_token_store_read_policy_document.json
}

resource "aws_iam_policy" "dynamo_access_token_store_write_access_policy" {
  name_prefix = "dynamo-access-token-store-write-policy"
  path        = "/${var.environment}/auth-ext/"
  description = "IAM policy for managing write permissions to the Dynamo Access Token Store table"

  policy = data.aws_iam_policy_document.dynamo_access_token_store_write_policy_document.json
}


resource "aws_iam_policy" "dynamo_user_read_access_policy" {
  name_prefix = "dynamo-user-read-policy"
  path        = "/${var.environment}/auth-ext/"
  description = "IAM policy for managing read permissions to the Dynamo User tables"

  policy = data.aws_iam_policy_document.dynamo_user_read_policy_document.json
}

resource "aws_iam_policy" "dynamo_user_write_access_policy" {
  name_prefix = "dynamo-user-write-policy"
  path        = "/${var.environment}/auth-ext/"
  description = "IAM policy for managing write permissions to the Dynamo User tables"

  policy = data.aws_iam_policy_document.dynamo_user_write_policy_document.json
}