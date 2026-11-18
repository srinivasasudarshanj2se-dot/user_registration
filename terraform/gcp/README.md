# GCP provisioning and deployment with Terraform

This configuration provisions the GCP services and deploys the existing API, Angular UI, and Spring WebFlux gateway to Cloud Run:

- Artifact Registry Docker repository
- Cloud SQL for MySQL 8.0, an application database and user, SSD storage, daily backups, and point-in-time recovery
- Secret Manager secrets for the database username/password and JWT signing key
- A least-privilege Cloud Run runtime service account with Cloud SQL and secret access
- Three Cloud Run services; the gateway is the intended public entry point
- A Pub/Sub call-events topic and second-generation Python Cloud Function consumer
- A partitioned BigQuery call-events table; the function also persists processed events to Cloud SQL
- An administrator-only live-call dashboard at the gateway's `/calls` route

The GKE manifests in `kubernetes/` are a separate deployment path and are not created by this Terraform configuration.

## Requirements

- An existing GCP project with billing enabled
- Terraform 1.6 or newer
- `gcloud` and Docker authenticated to the project and Artifact Registry
- Application container images built and pushed before the full Terraform apply

## Build and push application images

First copy and edit the example variable file with your project ID and image names/tags. The images do not need to exist for the targeted bootstrap below:

```powershell
Copy-Item terraform/gcp/terraform.tfvars.example terraform/gcp/terraform.tfvars
# Edit terraform/gcp/terraform.tfvars with your project and image tag.
terraform -chdir=terraform/gcp init
terraform -chdir=terraform/gcp apply `
  -target=google_project_service.required `
  -target=google_artifact_registry_repository.application
```

The Windows PowerShell deployment script automates this process, including building the
images in Cloud Build (so Docker is not required on the workstation), provisioning Cloud SQL,
and deploying the services:

```powershell
.\terraform\gcp\deploy.ps1 -ProjectId "your-google-cloud-project"
```

Prerequisites: Terraform 1.6+, `gcloud`, Google Cloud user credentials, and permissions to
enable APIs, create resources, and manage IAM. The Cloud Build build identity needs
`roles/artifactregistry.writer` on the project/repository. The script runs backend and frontend
quality gates in Cloud Build, builds and pushes immutable tagged images, bootstraps Artifact
Registry, then applies the full Terraform configuration. It performs a second apply to restrict
API CORS to the gateway origin.

To build images manually instead, use Docker and the commands in the preceding section of this
guide; set the resulting image URIs in `terraform.tfvars` before applying.

## Initialize and apply manually

```powershell
terraform -chdir=terraform/gcp fmt -check
terraform -chdir=terraform/gcp validate
terraform -chdir=terraform/gcp plan -out=tfplan
terraform -chdir=terraform/gcp apply tfplan
terraform -chdir=terraform/gcp output
```

For the standalone/manual process, first run the targeted bootstrap shown above, build and push
the images, then run the full apply with the image URIs in `terraform.tfvars`. The first full
apply can use `cors_allowed_origin = "*"`. After it creates the gateway, set that variable to
the exact `gateway_url` output and apply again.

The Google provider uses Application Default Credentials. For a local user account, authenticate with `gcloud auth application-default login` and ensure it has permissions to enable services, create Cloud Run services, Cloud SQL, Artifact Registry, Secret Manager secrets, service accounts, IAM bindings, and random-managed resources.

The Cloud SQL instance defaults to `db-f1-micro` and 10 GiB to match the low-cost example deployment. This is a development-sized setting, not a production recommendation. Set an appropriate `database_tier`, storage, high availability, and backup retention for production. The instance has deletion protection enabled by default; set `database_deletion_protection = false` only when intentionally replacing/destroying it.

## Set the browser origin

The first apply creates the gateway and prints `gateway_url`. For a custom domain, use that domain instead. Set `cors_allowed_origin` in `terraform.tfvars` to the exact browser origin, without a trailing slash, and apply again:

```hcl
cors_allowed_origin = "https://your-gateway.example.com"
```

The example uses `*` to allow a first deployment before its Cloud Run URL is known. Replace it with the exact gateway/custom-domain origin for normal use.

## Secret and state handling

Terraform creates random database and JWT secrets, stores them in Secret Manager, and injects Secret Manager references into the API service. Secret values are also represented in Terraform state. Do not commit `terraform.tfvars`, plan files, or state. Use an access-restricted, encrypted remote GCS backend with object versioning for shared/production use, and restrict state access as carefully as Secret Manager access.

The default `user-registration-tf` resource prefix keeps these resources separate from the previously described manually deployed services. Change `resource_prefix`, database instance, and Artifact Registry names if you need a different naming scheme; existing GCP resources with the same names must be imported into Terraform state or renamed before apply.

The gateway URL is the single intended browser entry point. The API and UI Cloud Run URLs are also public because this app's gateway currently forwards requests without Cloud Run identity tokens; API authorization is enforced by Spring Security and JWT roles. Restricting direct access to those services requires adding authenticated service-to-service invocation in the gateway before changing their IAM policy.

## Customer call event pipeline

The Terraform-managed API accepts administrator-authenticated events at `POST /api/v1/calls`,
publishes JSON to the provisioned Pub/Sub topic, and returns `202 Accepted` when Pub/Sub accepts
the message. The event-driven Python Cloud Function validates each message and writes it to the
Flyway-managed `call_events` Cloud SQL table and the partitioned BigQuery table. Cloud SQL uses
the call ID as its primary key for idempotent upserts; BigQuery streaming insert IDs provide
best-effort duplicate suppression. The Angular dashboard polls the Cloud SQL-backed
`GET /api/v1/calls?limit=50` endpoint every five seconds.

Terraform outputs `calls_dashboard_url`, `call_events_topic`, and
`call_events_bigquery_table`. Run `python -m unittest` in `functions/call-processor` to test
validation and storage behavior without GCP. The form/API is a trusted test-ingestion surface,
not a provider webhook integration; integrate a telephony provider only through an adapter with
provider signature verification and replay protection. Caller numbers are sensitive data. Define
access, encryption, retention, and deletion policies for Cloud SQL and BigQuery before production
use. The function currently uses the stack's application database credentials; before production,
replace this with a dedicated database identity granted only the required `call_events` operations.
This stack is separate from the pre-existing manual Cloud Run/GKE deployments.

## Update images and destroy

Use a new immutable image tag in `terraform.tfvars` and apply to roll out an image update.
`terraform destroy` is blocked while Cloud SQL deletion protection or BigQuery table deletion
protection is enabled. To intentionally remove the stack, disable the relevant protections, apply,
review the impact, and then destroy. Destruction permanently removes database data and
Terraform-managed secrets.
