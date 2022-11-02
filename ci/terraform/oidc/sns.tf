data "aws_sns_topic" "pagerduty_p1_alerts" {
  count = var.environment == "production" ? 1 : 0
  name  = "${var.environment}-pagerduty-p1-alerts"
}

data "aws_sns_topic" "slack_events" {
  name = "${var.environment}-slack-events"
}