/* groovylint-disable DuplicateStringLiteral, VariableName */
// vars/daosRepos.groovy

/**
 * daosRepos.groovy
 *
 * daosRepos variable
 */

/**
 * Method to return the DAOS repos
 */

String daos_repo() {
    String target_branch = env.CHANGE_TARGET ? env.CHANGE_TARGET : env.BRANCH_NAME

    if (target_branch =~ testBranchRE()) {
        return ''
    }

    /* groovylint-disable-next-line IfStatementCouldBeTernary */
    if (rpmTestVersion() == '') {
        return "daos@${env.BRANCH_NAME}:${env.BUILD_NUMBER}"
    }

    return ''
}

String call() {
    return daosRepos(hwDistroTarget())
}

String call(String distro) {
    String pr_repos = prRepos(distro)

    if (!prReposContains(distro, 'daos')) {
        pr_repos += ' ' + daos_repo()
    }

    return pr_repos
}
