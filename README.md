# Jenkins Shared Library

This repository contains a Jenkins Shared Library designed to streamline the CI/CD pipeline for a microservices architecture. It provides reusable steps for detecting changes in specific services and deploying applications using Docker Compose.

## 📂 Directory Structure

```
.
├── vars
│   ├── checkServiceChanges.groovy  # Logic to detect changed microservices
│   └── deployApp.groovy            # Logic to deploy applications via Docker Compose
└── README.md                       # Documentation
```

## 🚀 Usage

### 1. Configure Global Shared Library
In your Jenkins Dashboard:
1. Go to **Manage Jenkins** -> **Configure System**.
2. Scroll to **Global Pipeline Libraries**.
3. Add a new library:
    - **Name**: `jenkins-shared-library` (or your preferred name)
    - **Default version**: `main`
    - **Retrieval method**: Modern SCM
    - **Source Code Management**: Git (provide this repo URL and credentials)

### 2. Import in Pipeline
At the top of your `Jenkinsfile`:
```groovy
@Library('jenkins-shared-library') _
```

---

## 🛠️ Available Steps

### `checkServiceChanges`

Determines which services have been modified between two Git references. This is crucial for Monorepos to avoid rebuilding unchanged services.

#### Parameters
| Parameter       | Type         | Required | Description                                                         |
| :-------------- | :----------- | :------- | :------------------------------------------------------------------ |
| `baseBranch`    | String       | Yes      | The target branch to merge into (e.g., `main`).                     |
| `servicePaths`  | List<String> | Yes      | List of directory paths to check (e.g., `['backend', 'frontend']`). |
| `compareRef`    | String       | No       | Git range/ref to compare. Default: `HEAD~1 HEAD`.                   |
| `credentialsId` | String       | No       | Jenkins Credentials ID for Git auth.                                |
| `remote`        | String       | No       | Git remote name. Default: `origin`.                                 |

#### Example
```groovy
stage('Check Changes') {
    steps {
        script {
            def changedServices = checkServiceChanges(
                baseBranch: 'main',
                servicePaths: ['backend', 'frontend', 'auth-service'],
                credentialsId: 'github-credentials'
            )
            
            // Store in env var for later stages
            env.CHANGED_SERVICES = changedServices.join(',')
        }
    }
}
```

---

### `deployApp`

Deploys the application on the agent (typically a remote server) using `docker-compose`. It handles environment variable injection, code updates, and service restarts.

#### Parameters
| Parameter           | Type   | Required | Description                                                 |
| :------------------ | :----- | :------- | :---------------------------------------------------------- |
| `envFileCredential` | String | Yes      | Jenkins 'Secret file' credential ID containing `.env` vars. |
| `registry`          | String | Yes      | Docker registry (e.g., `user/repo`).                        |
| `buildNumber`       | String | Yes      | Build tag to deploy (usually `env.BUILD_NUMBER`).           |
| `servicesToUpdate`  | String | Yes      | Comma-separated list of services to update.                 |

#### Example
```groovy
stage('Deploy') {
    steps {
        deployApp(
            envFileCredential: 'prod-env-file',
            registry: 'my-docker-registry/app',
            buildNumber: env.BUILD_NUMBER,
            servicesToUpdate: env.CHANGED_SERVICES // e.g., "backend,frontend"
        )
    }
}
```

## ⚠️ Important Notes
- **Git Reset**: The `deployApp` step performs a `git reset --hard` to ensure the deployment directory is clean before pulling changes. **Any local uncommitted changes on the deployment server will be lost.**
- **Docker Compose**: The script assumes `docker-compose.yml` exists in the root of the repository on the deployment server.