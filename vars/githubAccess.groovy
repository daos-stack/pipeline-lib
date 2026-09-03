/* groovylint-disable DuplicateStringLiteral */
// vars/githubAccess.groovy

/**
 * githubAccess.groovy
 *
 * githubAccess variable
 */

/**
 * Method to return the withCredentials() binding for GitHub access.
 *
 * Binds the GitHub user to GH_USER and the token to GH_PASS.
 */
List call() {
    return [[$class: 'UsernamePasswordMultiBinding',
            credentialsId: 'daos_jenkins_project_github_access',
            usernameVariable: 'GH_USER',
            passwordVariable: 'GH_PASS']]
}
