# Docker and Kubernetes

The root `docker-compose.yaml` runs MySQL, the Spring Boot API, the Angular UI, and a Spring WebFlux gateway. The gateway uses WebClient to route `/api`, `/actuator`, and API documentation paths to the API, and all other paths to the SPA. The browser uses one origin. Authentication and role authorization remain in the Spring Boot API; the gateway does not issue or validate JWTs.

For provisioning Google Cloud Run, Cloud SQL, Secret Manager, and the API/UI/gateway services with Terraform, see [`terraform/gcp/README.md`](../terraform/gcp/README.md). That Terraform configuration does not create a GKE cluster; the GKE deployment instructions below remain separate.

## Docker Compose (local)

Requirements: Docker Engine/Desktop with Compose v2. From the repository root:

```powershell
Copy-Item .env.example .env
# Edit .env and replace every local example password and JWT_SECRET.
docker compose up --build
```

Open `http://localhost:4200`. The gateway is the only published application endpoint; the API, UI, and MySQL are private to the Compose network. The MySQL named volume keeps local database data across container restarts.

Stop the containers with `Ctrl+C`, or from another terminal run:

```powershell
docker compose down
```

To also delete the local database volume and its data:

```powershell
docker compose down --volumes
```

The `.env` values are for local development only. Never commit `.env` or reuse the sample credentials in a shared or production environment.

## Google Cloud Run

Cloud Run runs the API and UI as separate public services. The deployed API and GKE API share the `user-registration-mysql` Cloud SQL for MySQL instance in `asia-south1`; both use the Cloud SQL Java Connector and credentials stored in Secret Manager/Kubernetes Secrets. The instance uses the `db-f1-micro` tier, 10 GiB SSD, and daily backups. Do not use the demo H2 profile for a persistent deployment.

The current test deployment is available at `https://user-registration-gateway-z56jr6utaq-el.a.run.app` (Cloud Run) and `http://8.231.121.126` (GKE). Both UIs write to the same Cloud SQL database.

For live API URLs, CRUD endpoint details, request examples, and the Flyway SQL schema, see the [API and SQL testing guide](../docs/API_AND_SQL_TESTING.md).

Prerequisites: Docker, `gcloud`, and a Google Cloud project with billing enabled. Set these PowerShell variables to your own values. Use a new image tag for each build so deployments pick up the pushed images:

```powershell
$PROJECT_ID = "<your-project-id>"
$REGION = "<cloud-run-region>"
$INSTANCE_CONNECTION_NAME = "<project-id>:<region>:<cloud-sql-instance>"
$TAG = Get-Date -Format "yyyyMMddHHmmss"
$RUNTIME_SA = "user-registration-runtime@$PROJECT_ID.iam.gserviceaccount.com"
gcloud config set project $PROJECT_ID
gcloud services enable run.googleapis.com artifactregistry.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com
gcloud sql instances create user-registration-mysql --database-version=MYSQL_8_0 --region=$REGION --tier=db-f1-micro --edition=enterprise --storage-size=10 --storage-type=SSD --availability-type=zonal --backup-start-time=18:00
gcloud sql databases create user_registration --instance=user-registration-mysql
gcloud artifacts repositories create user-registration --repository-format=docker --location=$REGION
gcloud auth configure-docker "$REGION-docker.pkg.dev"
gcloud iam service-accounts create user-registration-runtime
gcloud projects add-iam-policy-binding $PROJECT_ID --member="serviceAccount:$RUNTIME_SA" --role=roles/cloudsql.client
```

Create Secret Manager secrets for the database username/password and a base64-encoded JWT key that decodes to at least 32 bytes. Grant the Cloud Run runtime service account `roles/cloudsql.client` and `roles/secretmanager.secretAccessor` on the relevant resources. For example, generate a key and create secrets interactively:

```powershell
$randomBytes = [byte[]]::new(48)
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
$jwtSecret = [Convert]::ToBase64String($randomBytes)
$dbUsername = Read-Host "Cloud SQL username"
$dbPassword = Read-Host "Cloud SQL password"
function New-GcpSecret($name, $value) {
  $file = Join-Path $env:TEMP ([Guid]::NewGuid().ToString())
  try {
    [IO.File]::WriteAllText($file, $value, [Text.UTF8Encoding]::new($false))
    gcloud secrets create $name --data-file=$file
  } finally {
    Remove-Item -LiteralPath $file -Force -ErrorAction SilentlyContinue
  }
}
New-GcpSecret "jwt-secret" $jwtSecret
New-GcpSecret "db-username" $dbUsername
New-GcpSecret "db-password" $dbPassword
gcloud sql users create $dbUsername --instance=user-registration-mysql --password=$dbPassword
gcloud secrets add-iam-policy-binding db-username --member="serviceAccount:$RUNTIME_SA" --role=roles/secretmanager.secretAccessor
gcloud secrets add-iam-policy-binding db-password --member="serviceAccount:$RUNTIME_SA" --role=roles/secretmanager.secretAccessor
gcloud secrets add-iam-policy-binding jwt-secret --member="serviceAccount:$RUNTIME_SA" --role=roles/secretmanager.secretAccessor
```

Build and push the API, UI, and gateway images:

```powershell
$API_IMAGE = "$REGION-docker.pkg.dev/$PROJECT_ID/user-registration/user-registration-api:$TAG"
$UI_IMAGE = "$REGION-docker.pkg.dev/$PROJECT_ID/user-registration/user-registration-ui:$TAG"
$GATEWAY_IMAGE = "$REGION-docker.pkg.dev/$PROJECT_ID/user-registration/user-registration-gateway:$TAG"
docker build -t $API_IMAGE -f Dockerfile .
docker push $API_IMAGE
docker build -t $UI_IMAGE -f angular-ui/Dockerfile ./angular-ui
docker push $UI_IMAGE
docker build -t $GATEWAY_IMAGE -f gateway/Dockerfile .
docker push $GATEWAY_IMAGE
```

Deploy the API, then configure the UI proxy to use its Cloud Run URL:

```powershell
$DB_URL = "jdbc:mysql:///user_registration?cloudSqlInstance=$INSTANCE_CONNECTION_NAME&socketFactory=com.google.cloud.sql.mysql.SocketFactory&connectTimeout=30000&socketTimeout=30000"
gcloud run deploy user-registration-api `
  --image $API_IMAGE --region $REGION --allow-unauthenticated --port 8080 `
  --service-account $RUNTIME_SA `
  --add-cloudsql-instances $INSTANCE_CONNECTION_NAME `
  --remove-env-vars SPRING_PROFILES_ACTIVE `
  --set-env-vars "DB_URL=$DB_URL,JWT_ISSUER=user-registration" `
  --set-secrets "DB_USERNAME=db-username:latest,DB_PASSWORD=db-password:latest,JWT_SECRET=jwt-secret:latest"
$API_URL = gcloud run services describe user-registration-api --region $REGION --format="value(status.url)"
gcloud run deploy user-registration-ui `
  --image $UI_IMAGE --region $REGION --allow-unauthenticated --port 8080 `
  --set-env-vars "API_UPSTREAM=$API_URL"
$UI_URL = gcloud run services describe user-registration-ui --region $REGION --format="value(status.url)"
```

To expose one URL for the application, deploy the gateway with the API and UI service URLs as upstreams:

```powershell
$GATEWAY_IMAGE = "$REGION-docker.pkg.dev/$PROJECT_ID/user-registration/user-registration-gateway:$TAG"
gcloud run deploy user-registration-gateway `
  --image $GATEWAY_IMAGE --region $REGION --allow-unauthenticated --port 8080 `
  --set-env-vars "API_UPSTREAM=$API_URL,UI_UPSTREAM=$UI_URL"
$GATEWAY_URL = gcloud run services describe user-registration-gateway --region $REGION --format="value(status.url)"
gcloud run services update user-registration-api --region $REGION `
  --update-env-vars "SECURITY_CORS_ALLOWED_ORIGIN=$GATEWAY_URL"
$GATEWAY_URL
```

Use the gateway URL for the app, `/swagger-ui.html` for API testing, and `/actuator/health` for the backend health endpoint. Cloud Run sets the `PORT` environment variable to `8080`. Replace `latest` secret versions with pinned versions for controlled production rollouts.

## Kubernetes

The manifests support a local MySQL `StatefulSet` with a persistent volume claim, API and UI deployments/services, and an optional NGINX Ingress. For GKE with Cloud SQL, use the Cloud SQL configuration and Workload Identity service account below instead of the MySQL `StatefulSet`. Create the namespace and a Kubernetes Secret before applying the application manifests. Do not commit credentials in YAML.

1. Build the images from the repository root:

   ```powershell
   docker build -t user-registration-api:local -f Dockerfile .
   docker build -t user-registration-ui:local -f angular-ui/Dockerfile ./angular-ui
   docker build -t user-registration-gateway:local -f gateway/Dockerfile .
   ```

   To build and push with Cloud Build when Docker is unavailable, create the Artifact Registry repository and use:

   ```powershell
   $API_IMAGE = "$REGION-docker.pkg.dev/$PROJECT_ID/user-registration/user-registration-api:$TAG"
   $UI_IMAGE = "$REGION-docker.pkg.dev/$PROJECT_ID/user-registration/user-registration-ui:$TAG"
   $GATEWAY_IMAGE = "$REGION-docker.pkg.dev/$PROJECT_ID/user-registration/user-registration-gateway:$TAG"
   gcloud builds submit . --project=$PROJECT_ID --tag=$API_IMAGE
   gcloud builds submit angular-ui --project=$PROJECT_ID --tag=$UI_IMAGE
   gcloud builds submit . --project=$PROJECT_ID --config gateway/cloudbuild.yaml --substitutions "_IMAGE=$GATEWAY_IMAGE"
   ```

   For a local cluster such as Minikube, load the images into the cluster if it does not share the local Docker daemon:

   ```powershell
   minikube image load user-registration-api:local
   minikube image load user-registration-ui:local
   minikube image load user-registration-gateway:local
   ```

   For GKE, push the images to Artifact Registry, then set the deployment images before applying the manifests:

   ```powershell
   $PROJECT_ID = "<your-project-id>"
   $REGION = "<gke-region>"
   $CLUSTER = "<your-cluster-name>"
   $TAG = Get-Date -Format "yyyyMMddHHmmss"
   gcloud config set project $PROJECT_ID
   gcloud services enable container.googleapis.com artifactregistry.googleapis.com
   gcloud artifacts repositories create user-registration --repository-format=docker --location=$REGION
   gcloud auth configure-docker "$REGION-docker.pkg.dev"
   $API_IMAGE = "$REGION-docker.pkg.dev/$PROJECT_ID/user-registration/user-registration-api:$TAG"
   $UI_IMAGE = "$REGION-docker.pkg.dev/$PROJECT_ID/user-registration/user-registration-ui:$TAG"
   $GATEWAY_IMAGE = "$REGION-docker.pkg.dev/$PROJECT_ID/user-registration/user-registration-gateway:$TAG"
   docker build -t $API_IMAGE -f Dockerfile .
   docker push $API_IMAGE
   docker build -t $UI_IMAGE -f angular-ui/Dockerfile ./angular-ui
   docker push $UI_IMAGE
   docker build -t $GATEWAY_IMAGE -f gateway/Dockerfile .
   docker push $GATEWAY_IMAGE
   gcloud container clusters get-credentials $CLUSTER --region $REGION --project $PROJECT_ID
   ```

2. Create the namespace and secrets. Supply strong, unique values:

   ```powershell
   kubectl apply -f kubernetes/namespace.yaml
   $randomBytes = [byte[]]::new(48)
   $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
   try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
   $jwtSecret = [Convert]::ToBase64String($randomBytes)
   kubectl create secret generic user-registration-secrets `
     --namespace user-registration `
     --from-literal=db-username=registration_app `
     --from-literal=db-password='<strong-database-password>' `
     --from-literal=mysql-root-password='<different-strong-root-password>' `
     --from-literal=jwt-secret=$jwtSecret
   ```

   `jwt-secret` must be base64 text that decodes to at least 32 bytes. The application decodes it before signing tokens. For production, use your cluster's external secret manager rather than storing manually created Kubernetes Secrets in source control.

3. Apply the database, API, and UI. The manifests use the `:local` images built for local clusters. For GKE, set the Artifact Registry images after applying:

   ```powershell
   kubectl apply -f kubernetes/configmap.yaml
   kubectl apply -f kubernetes/api-service-account.yaml
   kubectl apply -f kubernetes/mysql.yaml
   kubectl apply -f kubernetes/api.yaml
   kubectl apply -f kubernetes/ui.yaml
   kubectl apply -f kubernetes/gateway.yaml
   if ($API_IMAGE -and $UI_IMAGE) {
    kubectl set image deployment/user-registration-api api=$API_IMAGE -n user-registration
    kubectl set image deployment/user-registration-ui ui=$UI_IMAGE -n user-registration
    kubectl set image deployment/user-registration-gateway gateway=$GATEWAY_IMAGE -n user-registration
   }
   kubectl rollout status statefulset/mysql -n user-registration
   kubectl rollout status deployment/user-registration-api -n user-registration
   kubectl rollout status deployment/user-registration-ui -n user-registration
   kubectl rollout status deployment/user-registration-gateway -n user-registration
   ```

   For GKE with Cloud SQL, create a GKE Workload Identity-linked Google service account with `roles/cloudsql.client`, grant it `roles/iam.workloadIdentityUser` for the `user-registration/user-registration-api` Kubernetes service account, and grant it Secret Manager access. Replace the project-specific values in `gke-cloudsql-configmap.yaml` and `gke-cloudsql-service-account.yaml` for your project. Set `SECURITY_CORS_ALLOWED_ORIGIN` in the selected ConfigMap to the gateway's public IP or DNS origin. Apply those two files instead of `configmap.yaml`, apply `api.yaml`, `ui.yaml`, and `gateway.yaml`, and **skip `mysql.yaml`**. Populate `user-registration-secrets` from Secret Manager before applying the API deployment, and ensure `SPRING_PROFILES_ACTIVE` is not set to `demo`. Apply `public-gateway-service.yaml` to expose the gateway as the single public entry point; retrieve its external address with `kubectl get service user-registration-public -n user-registration`. Restart the API deployment after changing its ConfigMap.

4. Access the UI. To use port-forward without an Ingress controller:

   ```powershell
   kubectl port-forward -n user-registration service/gateway 4200:8080
   ```

   Browse to `http://localhost:4200`. For an NGINX Ingress deployment, set `SECURITY_CORS_ALLOWED_ORIGIN` in the relevant ConfigMap to the exact browser origin (including scheme), then apply `kubernetes/ingress.yaml` and configure DNS for its host. The default origin is for the local port-forward flow.

5. Inspect or remove the deployment:

   ```powershell
   kubectl get pods,services,pvc -n user-registration
   kubectl delete namespace user-registration
   ```

   Deleting the namespace also deletes its PVC and database data. Persistent-volume reclaim behavior depends on the cluster's storage class.

## Operational notes

- MySQL must have a default `ReadWriteOnce` storage class available for its 10 GiB claim.
- The API runs with two replicas; Flyway migration locking coordinates application startup against the shared database.
- Kubernetes probes use the backend health endpoint and UI HTTP endpoint.
- The gateway routes API and documentation paths to the internal `api` service and all other paths to the internal UI service. Only the gateway needs public ingress. JWT authentication and role authorization remain enforced by the API.
- The gateway forwards request and response bodies reactively with Spring WebClient, filters hop-by-hop headers, and returns `502 Bad Gateway` when it cannot connect to an upstream.
- The demo H2 profile is for local development; Compose and Kubernetes use MySQL.
- These manifests are a starting point, not a production platform baseline. Configure TLS, an ingress controller, network policies, backup/restore, secret rotation, monitoring, storage sizing, and resource values for the target environment.

## Cloud Build CI/CD

The repository root contains a Cloud Build deployment pipeline and a separate pull-request quality pipeline. Both run JUnit/JaCoCo and Angular/Karma tests with the configured 90% line-coverage gates. The deployment build cannot push or deploy unless all quality gates pass; it then pushes commit-tagged images to Artifact Registry, updates the GKE workloads, waits for rollouts, and prints the public URLs. GKE uses the existing Cloud SQL instance through Workload Identity.

### One-time setup

This repository's current GCP project is configured for the `asia-south1` `user-registration` GKE cluster, the `user-registration` Artifact Registry repository, and these Secret Manager secret IDs: `user-registration-sql-username`, `user-registration-sql-password`, and `jwt-secret`. Review/override the substitutions in `cloudbuild.yaml` if the project resources differ. Never put secret values in Cloud Build substitutions or repository files.

Enable the Cloud Build API, then grant the dedicated deployer service account only the permissions the pipeline needs:

```powershell
$PROJECT_ID = gcloud config get-value project
$BUILD_SA = "user-registration-deployer@$PROJECT_ID.iam.gserviceaccount.com"
gcloud services enable cloudbuild.googleapis.com --project $PROJECT_ID

gcloud projects add-iam-policy-binding $PROJECT_ID `
  --member="serviceAccount:$BUILD_SA" `
  --role="roles/cloudbuild.builds.builder"
gcloud artifacts repositories add-iam-policy-binding user-registration `
  --location=asia-south1 --project=$PROJECT_ID `
  --member="serviceAccount:$BUILD_SA" `
  --role="roles/artifactregistry.writer"

gcloud secrets add-iam-policy-binding user-registration-sql-username `
  --project=$PROJECT_ID --member="serviceAccount:$BUILD_SA" `
  --role="roles/secretmanager.secretAccessor"
gcloud secrets add-iam-policy-binding user-registration-sql-password `
  --project=$PROJECT_ID --member="serviceAccount:$BUILD_SA" `
  --role="roles/secretmanager.secretAccessor"
gcloud secrets add-iam-policy-binding jwt-secret `
  --project=$PROJECT_ID --member="serviceAccount:$BUILD_SA" `
  --role="roles/secretmanager.secretAccessor"
```

The deployer account must also have `roles/container.developer` and `roles/logging.logWriter`; the existing project binding supplies these in the current project. Its Cloud Build execution permission should be limited to this trusted repository. The API Kubernetes service account `user-registration/user-registration-api` must retain its Workload Identity binding to `user-registration-gke@$PROJECT_ID.iam.gserviceaccount.com`, and that GSA needs `roles/cloudsql.client`. The current GKE cluster and GSA already have those bindings.

Connect the GitHub repository to Cloud Build once in Google Cloud Console (Cloud Build > Repositories > 2nd gen > Connect repository), install/authorize the Cloud Build GitHub App for `srinivasasudarshanj2se-dot/user_registration`, and then create quality/deploy triggers:

```powershell
$PROJECT_ID = gcloud config get-value project
$BUILD_SA = "projects/$PROJECT_ID/serviceAccounts/user-registration-deployer@$PROJECT_ID.iam.gserviceaccount.com"
gcloud builds triggers create github `
  --project=$PROJECT_ID --region=asia-south1 `
  --name=user-registration-pr-quality `
  --repo-owner=srinivasasudarshanj2se-dot --repo-name=user_registration `
  --pull-request-pattern="^main$" `
  --build-config=cloudbuild-pr.yaml `
  --service-account=$BUILD_SA
gcloud builds triggers create github `
  --project=$PROJECT_ID --region=asia-south1 `
  --name=user-registration-main-deploy `
  --repo-owner=srinivasasudarshanj2se-dot --repo-name=user_registration `
  --branch-pattern="^main$" `
  --build-config=cloudbuild.yaml `
  --service-account=$BUILD_SA
```

Protect `main` in GitHub and require the Cloud Build PR quality check before merging. Only pushes to `main` use the deployment config. The PR config deliberately has no secrets, image push, cluster access, or deploy step.

To run the same quality-gated deployment once from a local checkout, after the IAM setup:

```powershell
$PROJECT_ID = gcloud config get-value project
$BUILD_SA = "projects/$PROJECT_ID/serviceAccounts/user-registration-deployer@$PROJECT_ID.iam.gserviceaccount.com"
gcloud builds submit . --project=$PROJECT_ID --region=asia-south1 `
  --config=cloudbuild.yaml --service-account=$BUILD_SA
```

The build requires the Git working tree's source and uses Cloud Build Secret Manager bindings; `gcloud builds submit` does not print or accept secret values. Check the build log and rollout status before sharing the URL emitted by the successful deploy step. The cluster's public LoadBalancer serves HTTP; configure HTTPS/TLS and DNS before production use.
