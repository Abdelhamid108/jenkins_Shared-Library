/**
 * Deploys an application locally on a Jenkins Agent using Docker Compose.
 * 
 * This script handles the deployment lifecycle:
 * 1. Injects secure environment variables.
 * 2. Resets the workspace to a clean state.
 * 3. Pulls the latest code.
 * 4. Updates image tags in docker-compose.yml for specific services.
 * 5. Pulls new images and restarts services.
 *
 * @param config A map containing the deployment configuration:
 * - `envFileCredential`: (String, required) Jenkins credential ID for the "Secret file" containing .env variables.
 * - `registry`:          (String, required) The Docker registry name (e.g., "abdelhameed208/graduationproject").
 * - `buildNumber`:       (String, required) The current build number (from env.BUILD_NUMBER) to use as the image tag.
 * - `servicesToUpdate`:  (String, required) A comma-separated string of service names that need updating (e.g., "backend,frontend").
 */
def call(Map config) {
    script {
        echo "🚀 Starting local deployment on this agent..."

        // Validate required parameters
        if (!config.envFileCredential || !config.registry || !config.buildNumber || !config.servicesToUpdate) {
            error "deployApp: Missing required configuration parameters."
        }

        // 1. Load the secret .env file credential
        withCredentials([
            file(credentialsId: config.envFileCredential, variable: 'SECURE_ENV_FILE')
        ]) {

            echo "🔒 Preparing .env file..."
            // SECURE_ENV_FILE is the path to the temp file on the agent provided by Jenkins
            // We copy it into the current directory as .env for docker-compose to use
            sh "cp ${SECURE_ENV_FILE} ./.env"

            echo "🔄 Resetting workspace and pulling latest code..."
            // Reset any local changes (e.g. from previous sed commands) to ensure a clean state
            sh "git reset --hard"
            sh "git pull"
            
            // Parse the string of services into a list
            def serviceList = config.servicesToUpdate.split(',').collect { it.trim() }
            
            echo "📝 Updating image tags in docker-compose.yml for: ${serviceList.join(', ')}"
            
            // Loop through only the changed services and update their tags in docker-compose.yml
            // We use sed to replace the image definition with the specific build tag
            serviceList.each { serviceName ->
                // Regex explanation:
                // s#...#...#g : Substitute command using '#' as delimiter
                // image: ${config.registry}/${serviceName}:.* : Match the image line with any existing tag
                // image: ${config.registry}/${serviceName}:${config.buildNumber} : Replace with the new build tag
                sh "sed -i 's#image: ${config.registry}/${serviceName}:.*#image: ${config.registry}/${serviceName}:${config.buildNumber}#g' docker-compose.yml"
            }
            
            echo "⬇️ Pulling new images and restarting services..."
            // docker-compose pull will only pull images that have changed/updated tags
            sh "docker-compose pull"
            
            // docker-compose up -d will recreate containers only if their configuration or image has changed
            sh "docker-compose up -d"

            echo "✅ Deployment completed successfully."
        }
    }
}
