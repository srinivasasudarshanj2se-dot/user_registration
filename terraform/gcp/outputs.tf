output "gateway_url" {
  description = "Public application entry point."
  value       = google_cloud_run_v2_service.gateway.uri
}

output "dashboard_url" {
  description = "Dashboard route at the public application entry point."
  value       = "${google_cloud_run_v2_service.gateway.uri}/dashboard"
}

output "gateway_health_url" {
  description = "Gateway liveness endpoint."
  value       = "${google_cloud_run_v2_service.gateway.uri}/gateway-health"
}

output "api_health_url" {
  description = "Backend API health endpoint through the gateway."
  value       = "${google_cloud_run_v2_service.gateway.uri}/actuator/health"
}

output "swagger_ui_url" {
  description = "Swagger UI through the application gateway."
  value       = "${google_cloud_run_v2_service.gateway.uri}/swagger-ui.html"
}

output "openapi_url" {
  description = "OpenAPI JSON through the application gateway."
  value       = "${google_cloud_run_v2_service.gateway.uri}/v3/api-docs"
}

output "calls_dashboard_url" {
  description = "Administrator-only real-time customer call dashboard."
  value       = "${google_cloud_run_v2_service.gateway.uri}/calls"
}

output "call_events_topic" {
  description = "Pub/Sub topic that triggers the call processor."
  value       = google_pubsub_topic.call_events.name
}

output "call_events_bigquery_table" {
  description = "BigQuery table populated by the call processor."
  value       = "${var.project_id}.${google_bigquery_dataset.call_events.dataset_id}.${google_bigquery_table.call_events.table_id}"
}

output "api_url" {
  description = "Public backend API URL. Application endpoints still require JWT authentication where configured."
  value       = google_cloud_run_v2_service.api.uri
}

output "ui_url" {
  description = "Public UI service URL."
  value       = google_cloud_run_v2_service.ui.uri
}

output "cloud_sql_instance_connection_name" {
  description = "Cloud SQL connection name for application integrations."
  value       = google_sql_database_instance.application.connection_name
}

output "artifact_registry_repository" {
  description = "Docker repository for the application images."
  value       = google_artifact_registry_repository.application.name
}
