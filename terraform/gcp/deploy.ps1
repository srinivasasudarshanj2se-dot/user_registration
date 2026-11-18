param(
  [Parameter(Mandatory = $true)]
  [ValidatePattern('^[a-z][a-z0-9-]{4,28}[a-z0-9]$')]
  [string] $ProjectId,

  [string] $Region = 'asia-south1',
  [string] $ResourcePrefix = 'user-registration-tf',
  [string] $ArtifactRepository = 'user-registration-tf',
  [string] $Tag = (Get-Date -Format 'yyyyMMddHHmmss')
)

$ErrorActionPreference = 'Stop'
$terraformDirectory = $PSScriptRoot
$cloudBuildConfig = Join-Path $terraformDirectory 'cloudbuild-images.yaml'
$tagPattern = '^[a-z0-9][a-z0-9._-]{0,127}$'

if ($Tag -notmatch $tagPattern) {
  throw 'Tag must contain only lowercase letters, digits, periods, underscores, or hyphens.'
}

foreach ($tool in @('terraform', 'gcloud')) {
  if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
    throw "Required command '$tool' was not found on PATH."
  }
}

& gcloud auth print-access-token *> $null
if ($LASTEXITCODE -ne 0) {
  throw 'Authenticate with gcloud auth login before running this script.'
}

& gcloud auth application-default print-access-token *> $null
if ($LASTEXITCODE -ne 0) {
  throw 'Configure Terraform credentials with gcloud auth application-default login.'
}

$repository = "$Region-docker.pkg.dev/$ProjectId/$ArtifactRepository"
$apiImage = "$repository/user-registration-api:$Tag"
$uiImage = "$repository/user-registration-ui:$Tag"
$gatewayImage = "$repository/user-registration-gateway:$Tag"
$baseVariables = @(
  "-var=project_id=$ProjectId"
  "-var=region=$Region"
  "-var=resource_prefix=$ResourcePrefix"
  "-var=artifact_registry_repository=$ArtifactRepository"
  "-var=api_image=$apiImage"
  "-var=ui_image=$uiImage"
  "-var=gateway_image=$gatewayImage"
)

& terraform "-chdir=$terraformDirectory" init
if ($LASTEXITCODE -ne 0) {
  throw 'Terraform initialization failed.'
}

& terraform "-chdir=$terraformDirectory" apply -auto-approve `
  "-target=google_project_service.required" `
  "-target=google_artifact_registry_repository.application" `
  @baseVariables
if ($LASTEXITCODE -ne 0) {
  throw 'Terraform Artifact Registry bootstrap failed.'
}

& gcloud builds submit . `
  "--project=$ProjectId" `
  "--config=$cloudBuildConfig" `
  "--substitutions=_REGION=$Region,_REPOSITORY=$ArtifactRepository,_TAG=$Tag"
if ($LASTEXITCODE -ne 0) {
  throw 'Cloud Build quality gates or container image build failed.'
}

& terraform "-chdir=$terraformDirectory" apply -auto-approve `
  @baseVariables `
  '-var=cors_allowed_origin=*'
if ($LASTEXITCODE -ne 0) {
  throw 'Terraform Cloud SQL or Cloud Run provisioning failed.'
}

$gatewayUrl = (& terraform "-chdir=$terraformDirectory" output -raw gateway_url).Trim()
if ($LASTEXITCODE -ne 0 -or -not $gatewayUrl.StartsWith('https://')) {
  throw 'Could not retrieve the deployed HTTPS gateway URL.'
}

& terraform "-chdir=$terraformDirectory" apply -auto-approve `
  @baseVariables `
  "-var=cors_allowed_origin=$gatewayUrl"
if ($LASTEXITCODE -ne 0) {
  throw 'Terraform could not restrict API CORS to the gateway origin.'
}

Write-Output ''
Write-Output 'Deployment complete. Test URLs:'
Write-Output "Application:  $gatewayUrl"
Write-Output "Login:        $gatewayUrl/login"
Write-Output "Dashboard:    $gatewayUrl/dashboard"
Write-Output "Live calls:   $gatewayUrl/calls (administrator account required)"
Write-Output "Health:       $gatewayUrl/actuator/health"
Write-Output "Swagger UI:   $gatewayUrl/swagger-ui.html"
Write-Output "OpenAPI JSON: $gatewayUrl/v3/api-docs"
