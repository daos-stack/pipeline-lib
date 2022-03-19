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

    if (target_branch.startsWith("weekly-testing")) {
        return ""
    }

    if (rpmTestVersion() == '') {
        return "daos@${env.BRANCH_NAME}:${env.BUILD_NUMBER}"
    }

    return ""
}

String call() {
    return daosRepos(hwDistroTarget())
}

String call(String distro) {
    String pr_repos = prRepos(distro)

    echo "PR-repos for distro " + distro + " is " + pr_repos
    echo "PR-repos contains daos@: " + pr_repos.contains('daos@')
    if (! pr_repos.contains('daos@')) {
        pr_repos += ' ' + daos_repo()
    }

    echo "returning: " + pr_repos
    return pr_repos
}
