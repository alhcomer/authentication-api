data "aws_iam_policy_document" "bulk_user_email_metrics_dynamo_access" {
  count = local.deploy_bulk_email_users_count
  statement {
    sid    = "AllowAccessToDescribeUserProfileTable"
    effect = "Allow"

    actions = [
      "dynamodb:DescribeTable",
      "dynamodb:Scan"
    ]

    resources = [
      data.aws_dynamodb_table.user_profile_table[0].arn,
    ]
  }
}

resource "aws_iam_policy" "bulk_user_email_metrics_dynamo_access" {
  count       = local.deploy_bulk_email_users_count
  name_prefix = "bulk-user-email-metrics-dynamo-access-policy"
  description = "IAM policy for managing permissions to the Dynamo User Profile table"

  policy = data.aws_iam_policy_document.bulk_user_email_metrics_dynamo_access[0].json
}

module "bulk_user_email_metrics_update_lambda_role" {
  count  = local.deploy_bulk_email_users_count
  source = "../modules/lambda-role"

  environment = var.environment
  role_name   = "bulk-user-email-metrics-lambda-role"

  policies_to_attach = [
    aws_iam_policy.bulk_user_email_metrics_dynamo_access[0].arn,
  ]
}

resource "aws_lambda_function" "bulk_user_email_metrics_lambda" {
  count         = local.deploy_bulk_email_users_count
  function_name = "${var.environment}-bulk-user-email-metrics-publish-lambda"
  role          = module.bulk_user_email_metrics_update_lambda_role[0].arn
  handler       = "uk.gov.di.authentication.utils.lambda.BulkUserEmailMetricsPublishHandler::handleRequest"
  timeout       = 900
  memory_size   = 4096
  runtime       = "java11"
  publish       = true

  s3_bucket         = aws_s3_object.utils_release_zip.bucket
  s3_key            = aws_s3_object.utils_release_zip.key
  s3_object_version = aws_s3_object.utils_release_zip.version_id

  environment {
    variables = merge({
      ENVIRONMENT = var.environment
    })
  }

  tags = local.default_tags
}


resource "aws_cloudwatch_log_group" "bulk_user_email_metrics_lambda_log_group" {
  count = local.deploy_bulk_email_users_count

  name              = "/aws/lambda/${aws_lambda_function.bulk_user_email_metrics_lambda[0].function_name}"
  kms_key_id        = local.cloudwatch_encryption_key_arn
  retention_in_days = var.cloudwatch_log_retention

  tags = local.default_tags
}

resource "aws_cloudwatch_log_subscription_filter" "bulk_user_email_metrics_log_subscription" {
  count           = local.deploy_bulk_email_users_count > 0 ? length(var.logging_endpoint_arns) : 0
  name            = "${aws_lambda_function.bulk_user_email_metrics_lambda[0].function_name}-log-subscription-${count.index}"
  log_group_name  = aws_cloudwatch_log_group.bulk_user_email_metrics_lambda_log_group[0].name
  filter_pattern  = ""
  destination_arn = var.logging_endpoint_arns[count.index]

  lifecycle {
    create_before_destroy = false
  }
}

resource "aws_cloudwatch_event_rule" "bulk_user_email_metrics_schedule" {
  count               = local.deploy_bulk_email_users_count
  name                = "${var.environment}-bulk-user-email-metrics-publish-schedule"
  schedule_expression = "cron(0 13 ? * FRI 2049)"
}

resource "aws_cloudwatch_event_target" "bulk_user_email_metrics_schedule_target" {
  count     = local.deploy_bulk_email_users_count
  arn       = aws_lambda_function.bulk_user_email_metrics_lambda[0].arn
  rule      = aws_cloudwatch_event_rule.bulk_user_email_metrics_schedule[0].name
  target_id = aws_lambda_function.bulk_user_email_metrics_lambda[0].version
}

resource "aws_lambda_permission" "allow_cloudwatch_to_call_bulk_user_email_metrics_lambda" {
  count         = local.deploy_bulk_email_users_count
  statement_id  = "AllowExecutionFromCloudWatch"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.bulk_user_email_metrics_lambda[0].function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.bulk_user_email_metrics_schedule[0].arn
}
