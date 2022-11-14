data "aws_cloudwatch_log_group" "ipv_callback_lambda_log_group" {
  count = var.use_localstack ? 0 : 1
  name  = replace("/aws/lambda/${var.environment}-ipv-callback-lambda", ".", "")
}

data "aws_cloudwatch_log_group" "spot_response_lambda_log_group" {
  count = var.use_localstack ? 0 : 1
  name  = replace("/aws/lambda/${var.environment}-spot-response-lambda", ".", "")
}

data "aws_cloudwatch_log_group" "processing_identity_lambda_log_group" {
  count = var.use_localstack ? 0 : 1
  name  = replace("/aws/lambda/${var.environment}-processing-identity-lambda", ".", "")
}

resource "aws_cloudwatch_log_metric_filter" "ipv_callback_metric_filter" {
  count          = var.use_localstack ? 0 : 1
  name           = replace("${var.environment}-ipv-callback-p1-errors", ".", "")
  pattern        = "{($.level = \"ERROR\")}"
  log_group_name = data.aws_cloudwatch_log_group.ipv_callback_lambda_log_group[0].name

  metric_transformation {
    name      = replace("${var.environment}-ipv-handback-error-count", ".", "")
    namespace = "LambdaErrorsNamespace"
    value     = "1"
  }
}

resource "aws_cloudwatch_log_metric_filter" "spot_response_metric_filter" {
  count          = var.use_localstack ? 0 : 1
  name           = replace("${var.environment}-spot-response-p1-errors", ".", "")
  pattern        = "{($.level = \"ERROR\")}"
  log_group_name = data.aws_cloudwatch_log_group.spot_response_lambda_log_group[0].name

  metric_transformation {
    name      = replace("${var.environment}-ipv-handback-error-count", ".", "")
    namespace = "LambdaErrorsNamespace"
    value     = "1"
  }
}

resource "aws_cloudwatch_log_metric_filter" "processing_identity_metric_filter" {
  count          = var.use_localstack ? 0 : 1
  name           = replace("${var.environment}-processing-identity-p1-errors", ".", "")
  pattern        = "{($.level = \"ERROR\")}"
  log_group_name = data.aws_cloudwatch_log_group.processing_identity_lambda_log_group[0].name

  metric_transformation {
    name      = replace("${var.environment}-ipv-handback-error-count", ".", "")
    namespace = "LambdaErrorsNamespace"
    value     = "1"
  }
}

resource "aws_cloudwatch_metric_alarm" "ipv_handback_p1_cloudwatch_alarm" {
  count               = var.use_localstack ? 0 : 1
  alarm_name          = replace("${var.environment}-P1-ipv-handback-alarm", ".", "")
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "1"
  metric_name         = aws_cloudwatch_log_metric_filter.processing_identity_metric_filter[0].metric_transformation[0].name
  namespace           = aws_cloudwatch_log_metric_filter.processing_identity_metric_filter[0].metric_transformation[0].namespace
  period              = var.ipv_p1_alarm_error_time_period
  statistic           = "Sum"
  threshold           = var.ipv_p1_alarm_error_threshold
  alarm_description   = "${var.ipv_p1_alarm_error_threshold} or more IPV handback errors have occurred in ${var.environment}.ACCOUNT: ${data.aws_iam_account_alias.current.account_alias}"
  alarm_actions       = [var.environment == "production" ? data.aws_sns_topic.slack_events.arn : data.aws_sns_topic.slack_events.arn]
}