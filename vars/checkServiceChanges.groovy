// vars/checkServiceChanges.groovy

/**
 * Detects which service directories have changed between two Git references.
 *
 * Main use cases:
 *   - Merge commits (compare HEAD with its first parent)
 *   - Pull Request comparisons (origin/main...HEAD)
 *   - Single commit changes (HEAD~1)
 *
 * PARAMETERS:
 *   @param baseBranch    (String, required)
 *       The main branch (e.g., "main", "development"). Used for safety/logging and fetch.
 *
 *   @param servicePaths  (List<String>, required)
 *       List of top-level folders representing each service.
 *       Example: ['backend', 'frontend', 'nginx']
 *
 *   @param compareRef    (String, optional)
 *       Git references to compare.
 *       Defaults to: "HEAD~1 HEAD"
 *       Examples:
 *         - "HEAD~1 HEAD" (merge commit or last commit)
 *         - "origin/main...HEAD" (PR comparison)
 *         - "HEAD~1"
 *
 *   @param credentialsId (String, optional)
 *       Credentials ID for git fetch (if needed).
 *
 *   @param remote        (String, optional)
 *       Git remote name (default: "origin").
 *
 * RETURNS:
 *   List<String> – names of services that changed
 *       [] → no changes
 *
 *   The function **never returns null** (more predictable for pipelines).
 */
def call(Map params) {
    def baseBranch   = params.baseBranch
    def servicePaths = params.servicePaths as List<String>
    def compareRef   = params.compareRef ?: 'HEAD~1 HEAD'  // default for merge commits
    def credentialsId = params.credentialsId
    def remote        = params.remote ?: 'origin'

    // -------------------------------
    // 1. INPUT VALIDATION
    -------------------------------
    if (!baseBranch || !servicePaths) {
        error "checkServiceChanges: 'baseBranch' and 'servicePaths' are required."
    }
    if (servicePaths.isEmpty()) {
        echo "checkServiceChanges: No services to monitor."
        return []
    }

    echo "checkServiceChanges: Comparing '${compareRef}' on branch '${baseBranch}'"
    echo "Services to inspect: ${servicePaths.join(', ')}"

    // -------------------------------
    // 2. OPTIONAL GIT FETCH
    //    (Only fetch when comparison needs remote history)
    -------------------------------
    try {
        if (!compareRef.contains("HEAD~")) { // local-only comparisons don't need fetch
            echo "checkServiceChanges: Fetching branch '${remote}/${baseBranch}'..."

            if (credentialsId) {
                sh "git config user.email 'jenkins@example.com'"
                sh "git config user.name 'Jenkins CI'"

                withCredentials([
                    usernamePassword(credentialsId: credentialsId,
                                     usernameVariable: 'GIT_USERNAME',
                                     passwordVariable: 'GIT_PASSWORD')
                ]) {
                    sh "git fetch --tags ${remote} ${baseBranch}"
                }
            } else {
                sh "git fetch --tags ${remote} ${baseBranch}"
            }
        }
    } catch (e) {
        echo "WARNING: Failed to fetch '${remote}/${baseBranch}'. Continuing anyway. Error: ${e.message}"
    }

    // -------------------------------
    // 3. RUN GIT DIFF
    -------------------------------
    def changedFiles = []
    try {
        def gitDiffCmd = "git diff --name-only '${compareRef}'"
        echo "checkServiceChanges: Running: ${gitDiffCmd}"

        def output = sh(script: gitDiffCmd, returnStdout: true).trim()

        changedFiles = output
            .split('\n')
            .collect { it.trim() }
            .findAll { it != '' }

        echo "checkServiceChanges: ${changedFiles.size()} changed files detected."
    } catch (e) {
        echo "ERROR: Failed to run git diff. Error: ${e.message}"
        return []   // safer than returning null
    }

    if (changedFiles.isEmpty()) {
        echo "checkServiceChanges: No file changes found."
    } else {
        echo "checkServiceChanges: Changed files:\n${changedFiles.join('\n')}"
    }

    // -------------------------------
    // 4. DETERMINE WHICH SERVICES CHANGED
    -------------------------------
    def servicesToUpdate = []

    for (servicePath in servicePaths) {
        def clean = servicePath.toString().trim()

        // match: service/… or service/file.ext
        def hasChanges = changedFiles.any { file ->
            file.startsWith("${clean}/")
        }

        if (hasChanges) {
            echo "checkServiceChanges: Service '${clean}' has changes."
            servicesToUpdate.add(clean)
        }
    }

    echo "checkServiceChanges: Final result → ${servicesToUpdate ?: 'No services changed'}"
    return servicesToUpdate
}
