// vars/dockerBuildArgs.groovy

def num_proc() {
    return sh(label: "Get number of processors online",
              script: "/usr/bin/getconf _NPROCESSORS_ONLN",
              returnStdout: true)
}

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
   * config['parallel_build'] Whether to build in parallel (-j)
   *
   * config['repo_type'] Type of repo to add.  Default 'local' for compatibilty
   *                     with older dockerfiles.
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

String call(Map config = [:]) {
    Boolean cachebust = true
    Boolean add_repos = true
    Boolean deps_build = false
    Boolean parallel_build = false
    String daos_type = 'LOCAL'
    String dist_type = 'GROUP'
    String repo_alias = ''
    String repo_mod = ''
    String daos_arg = "DAOS"
    String dist_arg = 'DISTRO'

    if (config.containsKey('cachebust')) {
      cachebust = config['cachebust']
    }
    if (config.containsKey('add_repos')) {
      add_repos = config['add_repos']
    }
    if (config.containsKey('deps_build')) {
      deps_build = config['deps_build']
    }
    if (config.containsKey('parallel_build')) {
      parallel_build = config['parallel_build']
    }
    if (config.containsKey('repo_type')) {
      daos_type = config['repo_type'].toString().toUpperCase()
      if (daos_type != 'LOCAL') {
        repo_mod = '_DOCKER'
        dist_type = daos_type
      }
    }
    Map stage_info = parseStageInfo(config)

    if (config.containsKey('distro')) {
      stage_info['target'] = config['distro']
    }

    // The docker agent setup and the provisionNodes step need to know the
    // UID that the build agent is running under.
    String ret_str = " --build-arg NOBUILD=1 " +
                     " --build-arg UID=" + sh(label: 'getuid()',
                                              script: "id -u",
                                              returnStdout: true).trim() +
                     " --build-arg JENKINS_URL=$env.JENKINS_URL"
    if (cachebust) {
      Calendar current_time = Calendar.getInstance()
      ret_str += " --build-arg CACHEBUST=${currentBuild.startTimeInMillis}"
      ret_str += " --build-arg CB0=" + current_time.get(Calendar.WEEK_OF_YEAR)
    }

    // No env.REPOSITORY_URL, no repos to add.
    if (add_repos && env.REPOSITORY_URL) {
      String repo_name = null
      String repo_arg = ''
      if (stage_info['target'] == 'centos7') {
        repo_alias = 'EL_7'
        if (daos_type == 'LOCAL') {
          daos_arg = 'EL7'
        }
      } else if (stage_info['target'] == 'centos8') {
        repo_alias = 'EL_8'
        if (daos_type == 'LOCAL') {
          daos_arg = 'EL8'
        }
        // Appstream repo not working in Nexus group repos
        if (env.DAOS_STACK_EL_8_APPSTREAM_REPO) {
          ret_str += " --build-arg REPO_APPSTREAM=" +
                     env.DAOS_STACK_EL_8_APPSTREAM_REPO
        }
      } else if (stage_info['target'] == 'leap15') {
        repo_alias = 'LEAP_15'
        // Backwards compatibilty for LOCAL
        if (daos_type == 'LOCAL') {
          if (env.DAOS_STACK_LEAP_15_GROUP_REPO) {
            daos_arg = 'LOCAL_LEAP15'
            dist_arg = 'GROUP_LEAP15'
          }
        }
      } else if (stage_info['target'] == 'ubuntu20.04') {
        // Ubuntu repos curently only used for package building.
        // When fully implemented it will probably be similar to above.
        // And the URLS for will be for installing a list of repos.
        // Details still to be worked out.
        repo_alias = 'UBUNTU_20_04'
        daos_arg = repo_alias
      } else if (stage_info['target'] == 'fedora' ) {
        // Used for mock builds, and some code scanning
        repo_alias = 'FEDORA'
        daos_arg = 'FEDORA'
      }
      dist_repo = env."DAOS_STACK_${repo_alias}${repo_mod}_${dist_type}_REPO"
      daos_repo = env."DAOS_STACK_${repo_alias}_${daos_type}_REPO"

      // Only add the build args if a repo was found.
      if (dist_repo || daos_repo) {
        ret_str += ' --build-arg REPO_URL=' + env.REPOSITORY_URL
        if (daos_repo) {
          ret_str += " --build-arg REPO_${daos_arg}=" + daos_repo
        }
        if (dist_repo) {
          ret_str += " --build-arg REPO_${dist_arg}=" + dist_repo
        }
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
        if (parallel_build) {
            String procs = num_proc()
            ret_str += ' --build-arg DEPS_JOBS=' + procs.trim()
        }
    }
    ret_str += ' '
    return ret_str
}
