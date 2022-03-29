// vars/repoFileUrl.groovy

  /**
   * repoFileUrl step method
   *
   * @param Value to return if the commit pragma is not set
   *
   */

def call(String def_val) {
    String PR = cachedCommitPragma(pragma: 'Repo-files-PR')
    if (PR != "") {
        String build_number
        String branch
        if (PR.contains(":")) {
            (branch, build_number) = PR.split(":")
        } else {
            build_number = "lastSuccessfulBuild"
            branch = PR
        }
        return env.JENKINS_URL + 'job/daos-do/job/repo-files/job/' + branch +
               '/' + build_number + '/artifact/'
    }

    return def_val
}
