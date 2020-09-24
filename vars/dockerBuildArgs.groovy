// vars/dockerBuildArgs.groovy

  /**
   * dockerBuildArgs step method
   *
   * @param config Map of parameters passed
   *
   * config['qb']      Whether to generate Quick-build args
   *
   */

// The docker agent setup and the provisionNodes step need to know the
// UID that the build agent is running under.
int getuid() {
    return sh(label: 'getuid()',
              script: "id -u",
              returnStdout: true).trim()
}

String call(Map config = [:]) {
    ret_str = " --build-arg NOBUILD=1 --build-arg UID=" + getuid() +
              " --build-arg JENKINS_URL=$env.JENKINS_URL" +
              " --build-arg CACHEBUST=${currentBuild.startTimeInMillis}"

    if (env.REPOSITORY_URL) {
      ret_str += ' --build-arg REPO_URL=' + env.REPOSITORY_URL
    }
    if (env.DAOS_STACK_EL_7_LOCAL_REPO) {
      ret_str += ' --build-arg REPO_EL7=' + env.DAOS_STACK_EL_7_LOCAL_REPO
    }
    if (env.DAOS_STACK_EL_8_LOCAL_REPO) {
      ret_str += ' --build-arg REPO_EL8=' + env.DAOS_STACK_EL_8_LOCAL_REPO
    }
    if (env.DAOS_STACK_LEAP_15_LOCAL_REPO) {
      ret_str += ' --build-arg REPO_LOCAL_LEAP15=' +
                 env.DAOS_STACK_LEAP_15_LOCAL_REPO
    }
    if (env.DAOS_STACK_UBUNTU_20_04_LOCAL_REPO) {
      ret_str += ' --build-arg REPO_UBUNTU_20_04=' + env.DAOS_STACK_UBUNTU_20_04_LOCAL_REPO
    }
    if (env.DAOS_STACK_LEAP_15_GROUP_REPO) {
      ret_str += ' --build-arg REPO_GROUP_LEAP15=' +
                 env.DAOS_STACK_LEAP_15_GROUP_REPO
    }
    if (env.HTTP_PROXY) {
      ret_str += ' --build-arg HTTP_PROXY="' + env.HTTP_PROXY + '"'
                 ' --build-arg http_proxy="' + env.HTTP_PROXY + '"'
    }
    if (env.HTTPS_PROXY) {
      ret_str += ' --build-arg HTTPS_PROXY="' + env.HTTPS_PROXY + '"'
                 ' --build-arg https_proxy="' + env.HTTPS_PROXY + '"'
    }
    if (config['qb']) {
      ret_str += ' --build-arg QUICKBUILD=true'
    }
    ret_str += ' '
    return ret_str
}