// vars/dockerBuildArgs.groovy

  /**
   * dockerBuildArgs step method
   *
   * @param config Map of parameters passed
   *
   * config['qb']      Whether to generate Quick-build args
   *
   * config['add_repos'] Whether to add yum repos to image.
   *
   * config['cachebust'] Whether to add unique id to refresh cache.
   *
   * config['deps_build'] Whether to build the daos dependencies.
   */

// The docker agent setup and the provisionNodes step need to know the
// UID that the build agent is running under.
int getuid() {
    return sh(label: 'getuid()',
              script: "id -u",
              returnStdout: true).trim()
}

String call(Map config = [:]) {
    Boolean cachebust = true
    Boolean add_repos = true
    Boolean deps_build = false
    if (config.containsKey('cachebust')) {
      cachebust = config['cachebust']
    }
    if (config.containsKey('add_repos')) {
      add_repos = config['add_repos']
    }
    if (config.containsKey('deps_build')) {
      deps_build = config['deps_build']
    }

    ret_str = " --build-arg NOBUILD=1 " +
              " --build-arg UID=" + getuid() +
              " --build-arg JENKINS_URL=$env.JENKINS_URL"
    if (cachebust) {
      ret_str += " --build-arg CACHEBUST=${currentBuild.startTimeInMillis}"
    }

    if (add_repos) {
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
      ret_str += ' --build-arg QUICKBUILD=true --build-arg DAOS_DEPS_BUILD=no'
    } else {
        if (deps_build) {
            ret_str += ' --build-arg DAOS_DEPS_BUILD=yes --build-arg DAOS_BUILD=no'
        } else {
            ret_str += ' --build-arg DAOS_DEPS_BUILD=no'
        }
    }
    ret_str += ' '
    return ret_str
}
