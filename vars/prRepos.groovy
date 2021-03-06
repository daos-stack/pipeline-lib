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
    if (distro.startsWith('el7') || distro.startsWith('centos7')) {
        repos = cachedCommitPragma('PR-repos-el7')
    } else if (distro.startsWith('el8') || distro.startsWith('centos8')) {
        repos = cachedCommitPragma('PR-repos-el8')
    } else if (distro.startsWith('leap15')) {
        repos = cachedCommitPragma('PR-repos-leap15')
    } else if (distro.startsWith('ubuntu20')) {
        repos = cachedCommitPragma('PR-repos-ubuntu20')
    } else {
       error 'prRepos not implemented for ' + distro
    }
    return [repos,
            cachedCommitPragma('PR-repos')].join(' ')
}