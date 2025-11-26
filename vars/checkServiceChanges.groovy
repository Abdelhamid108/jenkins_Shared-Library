def call(Map params) {
    def baseBranch = params.baseBranch
    def servicePaths = params.servicePaths as List<String>
    def compareRef = params.compareRef ?: 'HEAD~1 HEAD'
    def credentialsId = params.credentialsId
    def remote = params.remote ?: 'origin'

    if (!baseBranch || !servicePaths) {
        error "checkServiceChanges: 'baseBranch' and 'servicePaths' are required parameters."
    }

    echo "checkServiceChanges: Checking for changes on branch '${baseBranch}' relative to '${compareRef}' for services: ${servicePaths.join(', ')}"

    try {
        if (credentialsId) {
             sh "git config user.email 'jenkins@example.com'"
             sh "git config user.name 'Jenkins CI'"
             withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                 sh "git fetch --tags ${remote} ${baseBranch}"
             }
        } else {
            sh "git fetch --tags ${remote} ${baseBranch}"
        }
    } catch (e) {
        echo "WARNING: checkServiceChanges: Failed to fetch '${remote}/${baseBranch}'. Error: ${e.message}"
    }

    def changedFiles = []
    try {
        def gitDiffCommand = "git diff --name-only ${compareRef}"
        echo "checkServiceChanges: Executing Git command: '${gitDiffCommand}'"
        def diffOutput = sh(returnStdout: true, script: gitDiffCommand).trim()
        
        // Trim and filter empty lines
        changedFiles = diffOutput.split('\n').collect { it.trim() }.findAll { it != '' }
        
        echo "checkServiceChanges: Detected changed files:\n${changedFiles.join('\n')}"
    } catch (e) {
        error "checkServiceChanges: Failed to get Git diff. Error: ${e.message}"
        return null
    }

    // --- NEW LOGIC: Use findAll instead of loop mutation ---
    def servicesToUpdate = servicePaths.findAll { servicePath ->
        def cleanPath = servicePath.toString().trim()
        def match = changedFiles.any { file -> file.startsWith("${cleanPath}/") }
        
        if (match) {
            echo "checkServiceChanges: Found match for service '${cleanPath}'"
        }
        return match
    }

    echo "checkServiceChanges: Identified services to update: ${servicesToUpdate.empty ? 'None' : servicesToUpdate.join(', ')}"
    return servicesToUpdate
}
