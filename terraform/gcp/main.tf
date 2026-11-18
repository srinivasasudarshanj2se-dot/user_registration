locals {
  required_apis = toset([
    "artifactregistry.googleapis.com",
    "bigquery.googleapis.com",
    "cloudbuild.googleapis.com",
    "cloudfunctions.googleapis.com",
    "eventarc.googleapis.com",
    "iam.googleapis.com",
    "pubsub.googleapis.com",
    "run.googleapis.com",
    "secretmanager.googleapis.com",
    "sqladmin.googleapis.com",
    "storage.googleapis.com",
  ])
}

resource "google_project_service" "required" {
  for_each = local.required_apis

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_artifact_registry_repository" "application" {
  project       = var.project_id
  location      = var.region
  repository_id = var.artifact_registry_repository
  description   = "Container images for the user registration application"
  format        = "DOCKER"
  labels        = var.labels

  depends_on = [google_project_service.required]
}

resource "google_pubsub_topic" "call_events" {
  project = var.project_id
  name    = "${var.resource_prefix}-call-events"
  labels  = var.labels

  depends_on = [google_project_service.required]
}

resource "google_bigquery_dataset" "call_events" {
  project                    = var.project_id
  dataset_id                 = "${replace(var.resource_prefix, "-", "_")}_calls"
  friendly_name              = "Customer call events"
  description                = "Processed customer call events for reporting."
  location                   = var.region
  delete_contents_on_destroy = false
  labels                     = var.labels

  depends_on = [google_project_service.required]
}

resource "google_bigquery_table" "call_events" {
  project             = var.project_id
  dataset_id          = google_bigquery_dataset.call_events.dataset_id
  table_id            = "call_events"
  deletion_protection = true
  schema = jsonencode([
    { name = "call_id", type = "STRING", mode = "REQUIRED" },
    { name = "caller_number", type = "STRING", mode = "REQUIRED" },
    { name = "direction", type = "STRING", mode = "REQUIRED" },
    { name = "status", type = "STRING", mode = "REQUIRED" },
    { name = "agent_name", type = "STRING", mode = "NULLABLE" },
    { name = "started_at", type = "TIMESTAMP", mode = "REQUIRED" },
    { name = "ended_at", type = "TIMESTAMP", mode = "NULLABLE" },
  ])
  time_partitioning {
    type  = "DAY"
    field = "started_at"
  }
  clustering = ["status", "direction"]
}

data "archive_file" "call_processor" {
  type        = "zip"
  source_dir  = "${path.module}/../../functions/call-processor"
  output_path = "${path.module}/.terraform/call-processor.zip"
}

resource "google_storage_bucket" "function_source" {
  project                     = var.project_id
  name                        = "${var.resource_prefix}-${substr(sha256(var.project_id), 0, 8)}-fn-src"
  location                    = var.region
  uniform_bucket_level_access = true
  force_destroy               = true
  labels                      = var.labels
  depends_on                  = [google_project_service.required]
}

resource "google_storage_bucket_object" "call_processor" {
  bucket       = google_storage_bucket.function_source.name
  name         = "call-processor-${data.archive_file.call_processor.output_sha256}.zip"
  source       = data.archive_file.call_processor.output_path
  content_type = "application/zip"
}

resource "google_sql_database_instance" "application" {
  project             = var.project_id
  name                = var.database_instance_name
  region              = var.region
  database_version    = "MYSQL_8_0"
  deletion_protection = var.database_deletion_protection

  settings {
    tier              = var.database_tier
    availability_type = "ZONAL"
    disk_type         = "PD_SSD"
    disk_size         = var.database_storage_gb
    disk_autoresize   = true
    user_labels       = var.labels

    backup_configuration {
      enabled                        = true
      start_time                     = "18:00"
      point_in_time_recovery_enabled = true
      binary_log_enabled             = true
    }

    ip_configuration {
      ipv4_enabled = true
    }
  }

  depends_on = [google_project_service.required]
}

resource "google_sql_database" "application" {
  project  = var.project_id
  name     = var.database_name
  instance = google_sql_database_instance.application.name
}

resource "random_password" "database" {
  length  = 32
  special = false
}

resource "random_id" "jwt_secret" {
  byte_length = 48
}

resource "google_sql_user" "application" {
  project  = var.project_id
  instance = google_sql_database_instance.application.name
  name     = var.database_user
  password = random_password.database.result
}

resource "google_secret_manager_secret" "database_username" {
  project   = var.project_id
  secret_id = "${var.resource_prefix}-db-username"
  labels    = var.labels

  replication {
    auto {}
  }

  depends_on = [google_project_service.required]
}

resource "google_secret_manager_secret" "database_password" {
  project   = var.project_id
  secret_id = "${var.resource_prefix}-db-password"
  labels    = var.labels

  replication {
    auto {}
  }

  depends_on = [google_project_service.required]
}

resource "google_secret_manager_secret" "jwt_secret" {
  project   = var.project_id
  secret_id = "${var.resource_prefix}-jwt-secret"
  labels    = var.labels

  replication {
    auto {}
  }

  depends_on = [google_project_service.required]
}

resource "google_secret_manager_secret_version" "database_username" {
  secret      = google_secret_manager_secret.database_username.id
  secret_data = var.database_user
}

resource "google_secret_manager_secret_version" "database_password" {
  secret      = google_secret_manager_secret.database_password.id
  secret_data = random_password.database.result
}

resource "google_secret_manager_secret_version" "jwt_secret" {
  secret      = google_secret_manager_secret.jwt_secret.id
  secret_data = random_id.jwt_secret.b64_std
}

resource "google_service_account" "runtime" {
  project      = var.project_id
  account_id   = "${var.resource_prefix}-runtime"
  display_name = "User Registration Cloud Run runtime"

  depends_on = [google_project_service.required]
}

resource "google_service_account" "call_processor" {
  project      = var.project_id
  account_id   = "${var.resource_prefix}-calls"
  display_name = "Customer call event processor"

  depends_on = [google_project_service.required]
}

resource "google_project_iam_member" "cloud_sql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_project_iam_member" "call_processor_cloud_sql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.call_processor.email}"
}

resource "google_project_iam_member" "call_processor_eventarc_receiver" {
  project = var.project_id
  role    = "roles/eventarc.eventReceiver"
  member  = "serviceAccount:${google_service_account.call_processor.email}"
}

resource "google_pubsub_topic_iam_member" "api_publisher" {
  project = var.project_id
  topic   = google_pubsub_topic.call_events.name
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_bigquery_dataset_iam_member" "call_processor_writer" {
  project    = var.project_id
  dataset_id = google_bigquery_dataset.call_events.dataset_id
  role       = "roles/bigquery.dataEditor"
  member     = "serviceAccount:${google_service_account.call_processor.email}"
}

resource "google_secret_manager_secret_iam_member" "call_processor_database_access" {
  for_each = {
    database_username = google_secret_manager_secret.database_username.secret_id
    database_password = google_secret_manager_secret.database_password.secret_id
  }

  project   = var.project_id
  secret_id = each.value
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.call_processor.email}"
}

resource "google_cloudfunctions2_function" "call_processor" {
  project     = var.project_id
  name        = "${var.resource_prefix}-call-processor"
  location    = var.region
  description = "Consumes customer call events and writes to Cloud SQL and BigQuery."
  labels      = var.labels

  build_config {
    runtime     = "python312"
    entry_point = "process_call"
    source {
      storage_source {
        bucket = google_storage_bucket.function_source.name
        object = google_storage_bucket_object.call_processor.name
      }
    }
  }

  service_config {
    max_instance_count               = 5
    min_instance_count               = 0
    available_memory                 = "512M"
    timeout_seconds                  = 60
    service_account_email            = google_service_account.call_processor.email
    all_traffic_on_latest_revision   = true
    ingress_settings                 = "ALLOW_ALL"
    environment_variables = {
      INSTANCE_CONNECTION_NAME = google_sql_database_instance.application.connection_name
      DB_NAME                  = google_sql_database.application.name
      BIGQUERY_TABLE           = "${var.project_id}.${google_bigquery_dataset.call_events.dataset_id}.${google_bigquery_table.call_events.table_id}"
    }
    secret_environment_variables {
      key        = "DB_USER"
      project_id = var.project_id
      secret     = google_secret_manager_secret.database_username.secret_id
      version    = google_secret_manager_secret_version.database_username.version
    }
    secret_environment_variables {
      key        = "DB_PASSWORD"
      project_id = var.project_id
      secret     = google_secret_manager_secret.database_password.secret_id
      version    = google_secret_manager_secret_version.database_password.version
    }
  }

  event_trigger {
    trigger_region        = var.region
    event_type            = "google.cloud.pubsub.topic.v1.messagePublished"
    pubsub_topic          = google_pubsub_topic.call_events.id
    retry_policy          = "RETRY_POLICY_RETRY"
    service_account_email = google_service_account.call_processor.email
  }

  depends_on = [
    google_project_service.required,
    google_project_iam_member.call_processor_cloud_sql_client,
    google_project_iam_member.call_processor_eventarc_receiver,
    google_bigquery_dataset_iam_member.call_processor_writer,
    google_secret_manager_secret_iam_member.call_processor_database_access,
    google_sql_database.application,
    google_sql_user.application,
  ]
}

resource "google_secret_manager_secret_iam_member" "runtime_access" {
  for_each = {
    database_username = google_secret_manager_secret.database_username.id
    database_password = google_secret_manager_secret.database_password.id
    jwt_secret        = google_secret_manager_secret.jwt_secret.id
  }

  project   = var.project_id
  secret_id = each.value
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_cloud_run_v2_service" "api" {
  project             = var.project_id
  name                = "${var.resource_prefix}-api"
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = false
  labels              = var.labels

  template {
    service_account = google_service_account.runtime.email

    scaling {
      max_instance_count = var.cloud_run_max_instances
    }

    volumes {
      name = "cloudsql"

      cloud_sql_instance {
        instances = [google_sql_database_instance.application.connection_name]
      }
    }

    containers {
      image = var.api_image

      ports {
        container_port = 8080
      }

      env {
        name  = "DB_URL"
        value = "jdbc:mysql:///${var.database_name}?cloudSqlInstance=${google_sql_database_instance.application.connection_name}&socketFactory=com.google.cloud.sql.mysql.SocketFactory&connectTimeout=30000&socketTimeout=30000"
      }

      env {
        name  = "JWT_ISSUER"
        value = var.jwt_issuer
      }

      env {
        name  = "GCP_PROJECT_ID"
        value = var.project_id
      }

      env {
        name  = "CALL_EVENTS_TOPIC"
        value = google_pubsub_topic.call_events.name
      }

      env {
        name  = "SECURITY_CORS_ALLOWED_ORIGIN"
        value = var.cors_allowed_origin
      }

      env {
        name = "DB_USERNAME"

        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.database_username.secret_id
            version = google_secret_manager_secret_version.database_username.version
          }
        }
      }

      env {
        name = "DB_PASSWORD"

        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.database_password.secret_id
            version = google_secret_manager_secret_version.database_password.version
          }
        }
      }

      env {
        name = "JWT_SECRET"

        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.jwt_secret.secret_id
            version = google_secret_manager_secret_version.jwt_secret.version
          }
        }
      }

      volume_mounts {
        name       = "cloudsql"
        mount_path = "/cloudsql"
      }

      startup_probe {
        initial_delay_seconds = 10
        timeout_seconds       = 3
        period_seconds        = 10
        failure_threshold     = 30

        http_get {
          path = "/actuator/health"
          port = 8080
        }
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
      }
    }
  }

  depends_on = [
    google_project_service.required,
    google_sql_database.application,
    google_sql_user.application,
    google_project_iam_member.cloud_sql_client,
    google_secret_manager_secret_iam_member.runtime_access,
    google_pubsub_topic_iam_member.api_publisher,
  ]
}

resource "google_cloud_run_v2_service" "ui" {
  project             = var.project_id
  name                = "${var.resource_prefix}-ui"
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = false
  labels              = var.labels

  template {
    scaling {
      max_instance_count = var.cloud_run_max_instances
    }

    containers {
      image = var.ui_image

      ports {
        container_port = 8080
      }

      env {
        name  = "API_UPSTREAM"
        value = google_cloud_run_v2_service.api.uri
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
      }
    }
  }

  depends_on = [google_project_service.required]
}

resource "google_cloud_run_v2_service" "gateway" {
  project             = var.project_id
  name                = "${var.resource_prefix}-gateway"
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = false
  labels              = var.labels

  template {
    scaling {
      max_instance_count = var.cloud_run_max_instances
    }

    containers {
      image = var.gateway_image

      ports {
        container_port = 8080
      }

      env {
        name  = "API_UPSTREAM"
        value = google_cloud_run_v2_service.api.uri
      }

      env {
        name  = "UI_UPSTREAM"
        value = google_cloud_run_v2_service.ui.uri
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
      }
    }
  }

  depends_on = [google_project_service.required]
}

resource "google_cloud_run_v2_service_iam_member" "public" {
  for_each = {
    api     = google_cloud_run_v2_service.api.name
    ui      = google_cloud_run_v2_service.ui.name
    gateway = google_cloud_run_v2_service.gateway.name
  }

  project  = var.project_id
  location = var.region
  name     = each.value
  role     = "roles/run.invoker"
  member   = "allUsers"
}
