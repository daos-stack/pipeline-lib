/* groovylint-disable VariableName */
// vars/prRepos.groovy

/**
 * prRepos.groovy
 *
 * prRepos variable
 */

/**
 * Method to return the list of PR-repos:
 */

String call(String distro=null) {
    String _distro = distro ?: parseStageInfo()['target']
    String repos = ''

    if (params.CI_PR_REPOS) {
        repos = params.CI_PR_REPOS
    } else if (_distro) {
        // TODO: add parameter support for per-distro CI_PR_REPOS
        if (_distro.startsWith('el7') || _distro.startsWith('centos7')) {
            repos = cachedCommitPragma('PR-repos-el7')
        } else if (_distro.startsWith('el8') || _distro.startsWith('centos8') ||
                   _distro.startsWith('rocky8') || _distro.startsWith('almalinux8') ||
                   _distro.startsWith('rhel8')) {
            repos = cachedCommitPragma('PR-repos-el8')
        } else if (_distro.startsWith('el9')) {
            repos = cachedCommitPragma('PR-repos-el9')
        } else if (_distro.startsWith('leap15')) {
            repos = cachedCommitPragma('PR-repos-leap15')
        } else if (_distro.startsWith('ubuntu20')) {
            repos = cachedCommitPragma('PR-repos-ubuntu20')
        } else {
            error 'prRepos not implemented for ' + _distro
        }
    }
    return [repos,
            cachedCommitPragma('PR-repos')].join(' ').trim()
}
