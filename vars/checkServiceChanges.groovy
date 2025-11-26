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
        echo "WARNING: checkServiceChanges: Failed to fetch. Error: ${e.message}"
    }

    def changedFiles = []
    try {
        def diffOutput = sh(returnStdout: true, script: "git diff --name-only ${compareRef}").trim()
        // Primitive split and clean
        def rawFiles = diffOutput.split('\n')
        for (int i = 0; i < rawFiles.length; i++) {
            def f = rawFiles[i].trim()
            if (f != "") {
                changedFiles.add(f)
            }
        }
        echo "checkServiceChanges: Detected changed files: ${changedFiles}"
    } catch (e) {
        error "checkServiceChanges: Failed to get Git diff. Error: ${e.message}"
        return null
    }

    // --- PRIMITIVE LOOP LOGIC (No Closures) ---
    def servicesToUpdate = []
    
    for (int i = 0; i < servicePaths.size(); i++) {
        def svc = servicePaths[i].toString().trim()
        boolean match = false
        
        for (int j = 0; j < changedFiles.size(); j++) {
            def file = changedFiles[j]
            if (file.startsWith(svc + "/")) {
                match = true
                break // Found a match, stop checking files for this service
            }
        }
        
        if (match) {
            echo "checkServiceChanges: Found match for service '${svc}'"
            servicesToUpdate.add(svc)
        }
    }

    echo "checkServiceChanges: Identified services to update: ${servicesToUpdate}"
    return servicesToUpdate
}
