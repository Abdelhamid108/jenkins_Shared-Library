/**
 * Checks for changes in specific service directories between two Git references.
 *
 * This step is useful in a Monorepo setup to determine which microservices have been modified
 * and need to be rebuilt or redeployed.
 *
 * @param params A map containing the following keys:
 *  - baseBranch (String): The target branch to merge into (e.g., 'main' or 'develop').
 *  - servicePaths (List<String>): A list of directory paths to check for changes (e.g., ['backend', 'frontend']).
 *  - compareRef (String, optional): The git range or reference to compare. Defaults to 'HEAD~1 HEAD'.
 *  - credentialsId (String, optional): Jenkins Credentials ID for Git authentication if required.
 *  - remote (String, optional): The name of the git remote. Defaults to 'origin'.
 *
 * @return List<String> A list of service paths that have detected changes. Returns an empty list if no changes found.
 */
def call(Map params) {
    def baseBranch = params.baseBranch
    def servicePaths = params.servicePaths as List<String>
    def compareRef = params.compareRef ?: 'HEAD~1 HEAD'
    def credentialsId = params.credentialsId
    def remote = params.remote ?: 'origin'

    if (!baseBranch || !servicePaths) {
        error "checkServiceChanges: 'baseBranch' and 'servicePaths' are required parameters."
    }

    echo "checkServiceChanges: Checking for changes on branch '${baseBranch}' relative to '${compareRef}'"

    try {
        // Configure git user for fetch operations if not already set globally on the agent
        // This is often required by Jenkins git plugins or scripts
        sh "git config user.email 'jenkins@example.com' || true"
        sh "git config user.name 'Jenkins CI' || true"

        if (credentialsId) {
            withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                // Fetch tags and the target branch to ensure we have the necessary history for comparison
                sh "git fetch --tags ${remote} ${baseBranch}"
            }
        } else {
            sh "git fetch --tags ${remote} ${baseBranch}"
        }
    } catch (e) {
        echo "WARNING: checkServiceChanges: Failed to fetch. Proceeding with local history. Error: ${e.message}"
    }

    def changedFiles = []
    try {
        // Get the list of changed files using git diff
        def diffOutput = sh(returnStdout: true, script: "git diff --name-only ${compareRef}").trim()
        
        if (diffOutput) {
            changedFiles = diffOutput.split('\n').collect { it.trim() }
        }
        echo "checkServiceChanges: Detected changed files: ${changedFiles}"
    } catch (e) {
        error "checkServiceChanges: Failed to get Git diff. Error: ${e.message}"
        return []
    }

    def servicesToUpdate = []

    // Iterate through each service path to check if any of the changed files belong to it
    servicePaths.each { svc ->
        def servicePath = svc.toString().trim()
        
        // Check if any changed file starts with the service path
        boolean isChanged = changedFiles.any { file ->
            file.startsWith("${servicePath}/")
        }

        if (isChanged) {
            echo "checkServiceChanges: Found changes in service '${servicePath}'"
            servicesToUpdate.add(servicePath)
        }
    }

    echo "checkServiceChanges: Identified services to update: ${servicesToUpdate}"
    return servicesToUpdate
}
