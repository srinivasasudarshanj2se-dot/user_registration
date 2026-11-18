variable "project_id" {
  description = "Existing Google Cloud project ID with billing enabled."
  type        = string
}

variable "region" {
  description = "Region for Cloud Run, Cloud SQL, and Artifact Registry."
  type        = string
  default     = "asia-south1"
}

variable "resource_prefix" {
  description = "Prefix used for Cloud Run services, Secret Manager secrets, and the runtime service account."
  type        = string
  default     = "user-registration-tf"

  validation {
    condition     = length(var.resource_prefix) <= 21 && can(regex("^[a-z][a-z0-9-]*$", var.resource_prefix))
    error_message = "resource_prefix must start with a lowercase letter, contain only lowercase letters, digits, or hyphens, and be at most 21 characters."
  }
}

variable "artifact_registry_repository" {
  description = "Artifact Registry Docker repository name."
  type        = string
  default     = "user-registration-tf"
}

variable "api_image" {
  description = "Fully qualified API image URI, including a version tag."
  type        = string
}

variable "ui_image" {
  description = "Fully qualified Angular UI image URI, including a version tag."
  type        = string
}

variable "gateway_image" {
  description = "Fully qualified gateway image URI, including a version tag."
  type        = string
}

variable "database_instance_name" {
  description = "Cloud SQL instance name."
  type        = string
  default     = "user-registration-tf-mysql"
}

variable "database_name" {
  description = "Application database name."
  type        = string
  default     = "user_registration"
}

variable "database_user" {
  description = "Application database username."
  type        = string
  default     = "registration_app"
}

variable "database_tier" {
  description = "Cloud SQL machine tier. The default is suitable only for low-cost development."
  type        = string
  default     = "db-f1-micro"
}

variable "database_storage_gb" {
  description = "Initial Cloud SQL SSD storage size in GiB."
  type        = number
  default     = 10
}

variable "database_deletion_protection" {
  description = "Protect the Cloud SQL instance from Terraform deletion."
  type        = bool
  default     = true
}

variable "jwt_issuer" {
  description = "JWT issuer configured by the API."
  type        = string
  default     = "user-registration"
}

variable "cors_allowed_origin" {
  description = "Allowed browser origin. Set to the gateway URL or a custom application origin after the first apply."
  type        = string
  default     = "*"
}

variable "cloud_run_max_instances" {
  description = "Maximum autoscaled instance count for each Cloud Run service."
  type        = number
  default     = 5
}

variable "labels" {
  description = "Labels applied to supported resources."
  type        = map(string)
  default = {
    application = "user-registration"
    managed-by  = "terraform"
  }
}
