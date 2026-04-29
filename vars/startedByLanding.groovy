// vars/startedByLanding.groovy

/**
 * Determine if the build was started by landing a commit.
 *
 * @return a Boolean indicating if this build was started by landing a commit
 */
/* groovylint-disable-next-line UnusedMethodParameter */
Boolean call(Map config = [:]) {
    return (env.BRANCH_NAME == 'master' ||
            env.BRANCH_NAME =~ branchTypeRE('release') ||
            env.BRANCH_NAME =~ branchTypeRE('feature')) &&
           !(startedByTimer() || startedByUser())
}
