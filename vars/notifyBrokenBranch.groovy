// vars/notifyBrokenBranch.groovy
/* groovylint-disable VariableName */

/**
 * notifyBrokenBranch.groovy
 *
 * email responsible parties when a branch is broken.
 *
 * config['branches']   List of branches to notify for.  Default "master"
 * config['onPR']       Send e-mail when called from a PR.  Default false
 *
 */

def call(Map config = [:]) {

    String branches
    if (config['branches']) {
        branches = config['branches'].split()
    } else {
        branches = ["master"]
    }

    // Needed this as a work around that env['GIT_BRANCH'] is blacklisted
    // inside of pipeline-lib
    String git_branch = env.GIT_BRANCH

    if (!branches.contains(git_branch)) {
        return
    }

    emailextDaos body: git_branch + ' is broken and you are one of the people\n' +
                       'who have committed to it since it was last successful.  Please\n' +
                       'investigate if your recent patch(es) to ' + git_branch + '\n' +
                       'are responsible for breaking it.\n\n' +
                       'See ' + env.BUILD_URL + ' for more details.',
                 recipientProviders: [
                     [$class: 'DevelopersRecipientProvider'],
                     [$class: 'RequesterRecipientProvider']
                 ],
                 subject: 'Build broken on ' + git_branch,
                 onPR: config['onPR']

    String branch = git_branch.toUpperCase().replaceAll("-", "_")
    // This will need to be implemented in trusted-pipe-line lib eventually
    // as checking if environment variables exist is blacklisted in the
    // groovy sandbox.
    // for now we only have DAOS_STACK_MASTER_WATCHER
    // def watchers = env["DAOS_STACK_${branch}_WATCHER"]
    if (branch == 'MASTER') {
        String watchers = env.DAOS_STACK_MASTER_WATCHER
        if (watchers != "null") {
            emailextDaos body: git_branch + ' is broken.\n\n' +
                               'See ' + env.BUILD_URL + ' for more details.',
                         to: watchers,
                         subject: 'Build broken on ' + git_branch,
                         onPR: config['onPR']
        }
    }
}
