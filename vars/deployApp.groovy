/**
 * Deploys an application locally on a Jenkins Agent.
 * Assumes this script is already running on the target server.
 *
 * @param config A map containing the deployment configuration.
 * - `envFileCredential`: (String, required) Jenkins credential ID for the "Secret file" (.env).
 * - `registry`:          (String, required) The Docker registry name (e.g., "abdelhameed208/graduationproject").
 * - `buildNumber`:       (String, required) The current build number (from env.BUILD_NUMBER) for the image tag.
 * - `servicesToUpdate`:  (String, required) A comma-separated string of service names that need updating (e.g., "backend,frontend").
 */
def call(Map config) {
    script {
        echo " Starting local deployment on this agent..."

        // 1. Load the secret .env file credential
        withCredentials([
            file(credentialsId: config.envFileCredential, variable: 'SECURE_ENV_FILE')
        ]) {

            echo " Preparing .env file..."
            // SECURE_ENV_FILE is the path to the temp file on the agent
            // We just copy it into the current directory as .env
            sh "cp ${SECURE_ENV_FILE} ./.env"

            echo "Pulling latest code..."
            sh "git pull"
            
            // Parse the string of services into a list
            def serviceList = config.servicesToUpdate.split(',').collect { it.trim() }
            
            echo " Updating image tags in docker-compose.yml for: ${serviceList.join(', ')}"
            
            // Loop through only the changed services and update their tags
            serviceList.each { serviceName ->
                sh "sed -i 's#image: ${config.registry}/${serviceName}:.*#image: ${config.registry}/${serviceName}:${config.buildNumber}#g' docker-compose.yml"
            }
            
            echo " Pulling new images and restarting services..."
            // docker-compose pull will only pull images that have changed
            sh "docker-compose pull"
            
            // docker-compose up will only restart services whose images were updated
            sh "docker-compose up -d"

            echo " Deployment completed."
        }
    }
}
