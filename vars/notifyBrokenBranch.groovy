// vars/notifyBrokenBranch.groovy

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
    println("env.GIT_BRANCH: " + env.GIT_BRANCH)
    println("env['GIT_BRANCH']: " + env['GIT_BRANCH'])

    if (config['branches']) {
        branches = config['branches'].split()
    } else {
        branches = ["master"]
    }

    if (!branches.contains(env.GIT_BRANCH)) {
        return
    }

    emailextDaos body: env.GIT_BRANCH + ' is broken and you are one of the people\n' +
                       'who have committed to it since it was last successful.  Please\n' +
                       'investigate if your recent patch(es) to ' + env.GIT_BRANCH + '\n' +
                       'are responsible for breaking it.\n\n' +
                       'See ' + env.BUILD_URL + ' for more details.',
                 recipientProviders: [
                     [$class: 'DevelopersRecipientProvider'],
                     [$class: 'RequesterRecipientProvider']
                 ],
                 subject: 'Build broken on ' + env.GIT_BRANCH
                 onPR: config['onPR']

    def branch = env['GIT_BRANCH'].toUpperCase().replaceAll("-", "_")
    println("branch: " + branch)
    def watchers = env["DAOS_STACK_${branch}_WATCHER"]
    println("watchers: " + watchers)

    def nonwatchers = env["DAOS_STACK_FOOBAR_WATCHER"]
    println("nonwatchers: " + nonwatchers)

    if (watchers != "null") {
        println("emailing : " + nonwatchers)
        emailextDaos body: env.GIT_BRANCH + ' is broken.\n\n' +
                           'See ' + env.BUILD_URL + ' for more details.',
                     to: watchers
                     subject: 'Build broken on ' + env.GIT_BRANCH
                     onPR: config['onPR']
    }
    
}
