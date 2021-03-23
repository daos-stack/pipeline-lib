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
   * config['cachebust'] Whether to set CB0 and CACHEBUST args.
   *                     CB0 will be set weekly and should force entire rebuild
   *                     CACHEBUST should be unique and should force updates.
   *                     Defaults to true.
   *
   * config['deps_build'] Whether to build the daos dependencies.
   *
   * config['repo_type'] Type of repo to add.  Default 'local'
   *
   * Repositories URLs are looked up via Jenkins environment variables.
   * There are two environment variables that are put together to create
   * a path to a repo.
   *
   * The first envronment variable is "REPOSITORY_URL" which is a common
   * base URL that is used for all accesses to the repository.
   *
   * The rest of the URL for each specific repo is in additional environment
   * varables with a name format of "DAOS_STACK_$(distro}${mod}_${type}_REPO".
   *
   * The ${mod} currently is "_DOCKER" for repositories that are for use
   * with dockerfiles, and "" for generic repositories.
   * DOCKER repositories are for both replacing the distro built in
   * repositories and adding locally built packages.
   *
   * The ${distro} is currently one of "EL_7", "EL_8", "LEAP_15", and
   * "UBUNTU_20_04".
   *
   * The ${type} is "LOCAL", "DEV", "STABLE", or "RELEASE".  This is passed
   * in lower case in the "repo_type" parameter above.
   *
   * "LOCAL" type contains only locally built packages.  This is being phased
   * out for all but Ubuntu.  It is a single repository.
   *
   * The other types are group repositories that combine both locally built
   * package repositories with other repositories.
   *
   * The "DEV" type is group for experimental configurations for special PRs.
   *
   * The "STABLE" type is a group with locally build packages and also can
   * contain other repositories.  It should not contain the default distro
   * provided repositories.  It may also contain locally built signed
   * release packages.
   *
   * The "RELEASE" type contains locally built signed released packages.
   *
   * Getting better repositories for Ubuntu is a work in progress.
   * For Ubuntu repository groups, that will need to be a URL that
   * can be used to download a list file containing the needed repositories.
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
    String repo_type = 'LOCAL'
    String repo_alias = ''
    String repo_mod = ''

    if (config.containsKey('cachebust')) {
      cachebust = config['cachebust']
    }
    if (config.containsKey('add_repos')) {
      add_repos = config['add_repos']
    }
    if (config.containsKey('deps_build')) {
      deps_build = config['deps_build']
    }
    if (config.containsKey('repo_type')) {
      repo_type = config['repo_type'].toString().toUpperCase()
      if (repo_type != 'LOCAL') {
        repo_mod = '_DOCKER'
      }
    }
    Map stage_info = parseStageInfo(config)

    ret_str = " --build-arg NOBUILD=1 " +
              " --build-arg UID=" + getuid() +
              " --build-arg JENKINS_URL=$env.JENKINS_URL"
    if (cachebust) {
      Calendar current_time = Calendar.getInstance()
      ret_str += " --build-arg CACHEBUST=${currentBuild.startTimeInMillis}"
      ret_str += " --build-arg CB0=" + current_time.get(Calendar.WEEK_OF_YEAR)
    }

    String repo_base = env.REPOSITORY_URL
    // No repo_base, no repos to add.
    if (add_repos && repo_base) {
      String repo_name = null
      String repo_arg = ''
      if (stage_info['target'] == 'centos7') {
        repo_alias = 'EL_7'
        repo_arg = 'EL7'
      }
      if (stage_info['target'] == 'centos8') {
        repo_alias = 'EL_8'
        repo_arg = 'EL8'
      }
      if (stage_info['target'] == 'leap15') {
        repo_alias = 'LEAP_15'
        repo_arg = 'LEAP'
        // Backwards compatibilty for LOCAL
        if (repo_type == 'LOCAL') {
         if (env.DAOS_STACK_LEAP_15_GROUP_REPO) {
            ret_str += ' --build-arg REPO_GROUP_LEAP15=' +
                       env.DAOS_STACK_LEAP_15_GROUP_REPO
          }
        }
      }
      if (stage_info['target'] == 'ubuntu20.04') {
        // Ubuntu repos usage not yet implemented
        // When it is implemented it will probably be similar to above.
        // And the URLS for will be for installing a list of repos.
        // Details still to be worked out.
        repo_alias = 'UBUNTU_20_04'
        repo_arg = 'REPO_UBUNTU'
      }
      repo_name = env["DAOS_STACK_${repo_alias}${repo_mod}_${repo_type}_REPO"]
      // Only add the build args if a repo was found.
      if (repo_name) {
        ret_str += " --build-arg REPO_${repo_arg}=" + repo_name
        ret_str += ' --build-arg REPO_URL=' + env.REPOSITORY_URL
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
