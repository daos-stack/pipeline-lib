// vars/prReposContains.groovy

/**
 * prReposContains.groovy
 *
 * prReposContains variable
 */

/**
 * Method to return True/False if a PR-repos* commit pragma contains a project
 */

Boolean call(String value) {
    return prReposContains(parseStageInfo()['target'], value)
}

Boolean call(String distro, String value) {
    return prRepos(distro).split().any { i -> i.startsWith(value + '@') }
}
