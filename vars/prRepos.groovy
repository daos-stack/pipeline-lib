// vars/prRepos.groovy

/**
 * prRepos.groovy
 *
 * prRepos variable
 */

/**
 * Method to return the list of PR-repos:
 */

String call() {
    return prRepos(parseStageInfo()['target'])
}

String call(String distro) {
    String repos = ""
    if (params.CI_PR_REPOS) {
        println("CI_PR_REPOS: ${params.CI_PR_REPOS}")
        repos = params.CI_PR_REPOS
    } else {
        println('CI_PR_REPOS is not set')
        // TODO: add parameter support for per-distro CI_PR_REPOS
        if (distro.startsWith('el7') || distro.startsWith('centos7')) {
            repos = cachedCommitPragma('PR-repos-el7')
        } else if (distro.startsWith('el8') || distro.startsWith('centos8') ||
                   distro.startsWith('rocky8') || distro.startsWith('almalinux8') ||
                   distro.startsWith('rhel8')) {
            repos = cachedCommitPragma('PR-repos-el8')
        } else if (distro.startsWith('leap15')) {
            repos = cachedCommitPragma('PR-repos-leap15')
        } else if (distro.startsWith('ubuntu20')) {
            repos = cachedCommitPragma('PR-repos-ubuntu20')
        } else {
           error 'prRepos not implemented for ' + distro
        }
    }
    return [repos,
            cachedCommitPragma('PR-repos')].join(' ').trim()
}
