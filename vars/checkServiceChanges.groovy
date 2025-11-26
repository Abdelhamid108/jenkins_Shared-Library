// vars/checkServiceChanges.groovy

/**
 * Checks for changes in specified service directories between two Git references.
 * Designed primarily for pipelines triggered by a merge commit, comparing the
 * merge commit (HEAD) with its first parent (HEAD~1) to identify newly introduced changes.
 *
 * @param params A map containing the following:
 *   - `baseBranch`: (String, required) The name of the primary integration branch (e.g., 'development', 'main').
 *                   Used for context and ensuring the correct branch is being processed.
 *   - `servicePaths`: (List<String>, required) A list of top-level directory paths for each service
 *                     to monitor (e.g., ['backend', 'frontend', 'nginx']).
 *   - `compareRef`: (String, optional) The Git reference(s) to compare.
 *                   Defaults to 'HEAD~1 HEAD', which is ideal for merge commits
 *                   (compares the merge commit with its first parent).
 *                   Other common values:
 *                   - 'origin/main...HEAD' for comparing a feature branch against 'main' in a PR.
 *                   - 'HEAD~1' for changes in the single last commit.
 *   - `credentialsId`: (String, optional) The ID of Jenkins credentials if the Git repository
 *                      requires authentication for `git fetch`.
 *   - `remote`: (String, optional) The name of the Git remote to fetch from (e.g., 'origin'). Defaults to 'origin'.
 *
 * @return A list of strings, where each string is the name of a service directory that has changed.
 *         Returns an empty list if no relevant changes are found.
 *         Returns null if the Git diff operation fails critically.
 */
def call(Map params) {
    def baseBranch = params.baseBranch
    def servicePaths = params.servicePaths as List<String>
    def compareRef = params.compareRef ?: 'HEAD~1 HEAD' // Default for merge commits: compare HEAD with its first parent
    def credentialsId = params.credentialsId
    def remote = params.remote ?: 'origin'

    // --- Input Validation ---
    if (!baseBranch || !servicePaths) {
        error "checkServiceChanges: 'baseBranch' and 'servicePaths' are required parameters."
    }
    if (servicePaths.isEmpty()) {
        echo "checkServiceChanges: 'servicePaths' is empty. No services to monitor."
        return [] // No services to check, so no changes to report.
    }

    echo "checkServiceChanges: Checking for changes on branch '${baseBranch}' relative to '${compareRef}' for services: ${servicePaths.join(', ')}"

    // --- Ensure Base Branch is Fetched (Important for accurate diffs if not done by SCM) ---
    // This step is crucial if 'compareRef' relies on a remote branch that wasn't fully
    // fetched by the initial SCM checkout (e.g., comparing 'origin/main...HEAD' on a feature branch).
    // For 'HEAD~1 HEAD' it's less critical, but good practice to ensure full history.
    try {
        if (credentialsId) {
             // Set dummy user config if git commands fail without it (some CI agents require this)
             sh "git config user.email 'jenkins@example.com'"
             sh "git config user.name 'Jenkins CI'"
             withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                 sh "git fetch --tags ${remote} ${baseBranch}" // Fetch tags too for full history
             }
        } else {
            sh "git fetch --tags ${remote} ${baseBranch}"
        }
        echo "checkServiceChanges: Successfully fetched '${remote}/${baseBranch}'."
    } catch (e) {
        // Log warning but continue, as the SCM checkout might have sufficient history
        // or the specific 'compareRef' might not need remote history (e.g., HEAD~1 HEAD).
        echo "WARNING: checkServiceChanges: Failed to fetch '${remote}/${baseBranch}'. This might affect diff accuracy. Error: ${e.message}"
    }

    // --- Get Git Diff Output ---
    def changedFiles = []
    try {
        // The core Git command to find changed files
        def gitDiffCommand = "git diff --name-only ${compareRef}"
        echo "checkServiceChanges: Executing Git command: '${gitDiffCommand}'"
        def diffOutput = sh(returnStdout: true, script: gitDiffCommand).trim()

        // FIX: Explicitly trim each file path to remove hidden whitespace and filter empty lines
        changedFiles = diffOutput.split('\n').collect { it.trim() }.findAll { it != '' }
        
        if (changedFiles.isEmpty()) {
            echo "checkServiceChanges: No files detected as changed by 'git diff'."
        } else {
            echo "checkServiceChanges: Detected changed files:\n${changedFiles.join('\n')}"
        }
    } catch (e) {
        error "checkServiceChanges: Failed to get Git diff. Ensure '${compareRef}' is a valid Git reference and repository is accessible. Error: ${e.message}"
        return null // Indicate critical failure
    }

    // --- Determine Services to Update ---
    def servicesToUpdate = []
    
    // FIX: Use standard for-loop to avoid Jenkins CPS closure scope issues
    for (servicePath in servicePaths) {
        // FIX: Ensure servicePath is a clean String without whitespace
        def cleanServicePath = servicePath.toString().trim()
        
        // Check if any of the changed files are located within the current service's directory
        // The startsWith("${servicePath}/") ensures we match files in subdirectories too.
        def match = changedFiles.any { file -> file.startsWith("${cleanServicePath}/") }
        
        if (match) {
            echo "checkServiceChanges: Found match for service '${cleanServicePath}'"
            servicesToUpdate.add(cleanServicePath)
 
    }

    echo "checkServiceChanges: Identified services to update: ${servicesToUpdate.empty ? 'None' : servicesToUpdate.join(', ')}"
    return servicesToUpdate
}
