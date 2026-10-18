# Docker and Kubernetes

The root `docker-compose.yaml` runs MySQL, the Spring Boot API, and the Angular UI. The UI's Nginx container serves the SPA and proxies `/api` and `/actuator` to the API, so the browser uses one origin.

## Docker Compose (local)

Requirements: Docker Engine/Desktop with Compose v2. From the repository root:

```powershell
Copy-Item .env.example .env
# Edit .env and replace every local example password and JWT_SECRET.
docker compose up --build
```

Open `http://localhost:4200`. The API is also published at `http://localhost:8081`; MySQL remains private to the Compose network. The MySQL named volume keeps local database data across container restarts.

Stop the containers with `Ctrl+C`, or from another terminal run:

```powershell
docker compose down
```

To also delete the local database volume and its data:

```powershell
docker compose down --volumes
```

The `.env` values are for local development only. Never commit `.env` or reuse the sample credentials in a shared or production environment.

## Kubernetes

The manifests create a namespace, MySQL `StatefulSet` with a persistent volume claim, API and UI deployments/services, and an optional NGINX Ingress. Create the namespace and a Kubernetes Secret before applying the application manifests. Do not commit credentials in YAML.

1. Build the images from the repository root:

   ```powershell
   docker build -t user-registration-api:local -f Dockerfile .
   docker build -t user-registration-ui:local -f angular-ui/Dockerfile ./angular-ui
   ```

   For a local cluster such as Minikube, load the images into the cluster if it does not share the local Docker daemon:

   ```powershell
   minikube image load user-registration-api:local
   minikube image load user-registration-ui:local
   ```

   For a remote cluster, push the images to a registry and update `image` in `kubernetes/api.yaml` and `kubernetes/ui.yaml`.

2. Create the namespace and secrets. Supply strong, unique values:

   ```powershell
   kubectl apply -f kubernetes/namespace.yaml
   $jwtSecret = [Convert]::ToBase64String([byte[]](1..48 | ForEach-Object { Get-Random -Maximum 256 }))
   kubectl create secret generic user-registration-secrets `
     --namespace user-registration `
     --from-literal=db-username=registration_app `
     --from-literal=db-password='<strong-database-password>' `
     --from-literal=mysql-root-password='<different-strong-root-password>' `
     --from-literal=jwt-secret=$jwtSecret
   ```

   `jwt-secret` must be base64 text that decodes to at least 32 bytes. The application decodes it before signing tokens. For production, use your cluster's external secret manager rather than storing manually created Kubernetes Secrets in source control.

3. Apply the database, API, and UI:

   ```powershell
   kubectl apply -f kubernetes/configmap.yaml
   kubectl apply -f kubernetes/mysql.yaml
   kubectl apply -f kubernetes/api.yaml
   kubectl apply -f kubernetes/ui.yaml
   kubectl rollout status statefulset/mysql -n user-registration
   kubectl rollout status deployment/user-registration-api -n user-registration
   kubectl rollout status deployment/user-registration-ui -n user-registration
   ```

4. Access the UI. To use port-forward without an Ingress controller:

   ```powershell
   kubectl port-forward -n user-registration service/ui 4200:80
   ```

   Browse to `http://localhost:4200`. Alternatively, install an NGINX Ingress controller, apply `kubernetes/ingress.yaml`, and map `user-registration.local` to the ingress controller address in the local hosts file.

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
- The UI container proxies application and actuator routes to the internal `api` service. Only the UI needs public ingress for browser use.
- The demo H2 profile is for local development; Compose and Kubernetes use MySQL.
- These manifests are a starting point, not a production platform baseline. Configure TLS, an ingress controller, network policies, backup/restore, secret rotation, monitoring, storage sizing, and resource values for the target environment.
