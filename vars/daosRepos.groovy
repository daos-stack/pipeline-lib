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
    return prRepos(distro) + ' ' + daos_repo()
}