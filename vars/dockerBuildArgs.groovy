/* groovylint-disable DuplicateStringLiteral, VariableName */
// vars/dockerBuildArgs.groovy
/*
 * Copyright 2020-2024 Intel Corporation
 * Copyright 2025 Hewlett Packard Enterprise Development LP
 */

Integer num_proc() {
    return sh(label: 'Get number of processors online',
              script: '/usr/bin/getconf _NPROCESSORS_ONLN',
              returnStdout: true)
}

  /**
   * dockerBuildArgs step method
   *
   * @param config Map of parameters passed
   *
   * config['qb']      Whether to generate Quick-build args
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
   *
   * Getting better repositories for Ubuntu is a work in progress.
   * For Ubuntu repository groups, that will need to be a URL that
   * can be used to download a list file containing the needed repositories.
   */

String call(Map config = [:]) {
    Boolean cachebust = config.get('cachebust', true)
    Boolean deps_build = config.get('deps_build', false)
    Boolean parallel_build = config.get('parallel_build', false)

    Map stage_info = parseStageInfo(config)

    // The docker agent setup and the provisionNodes step need to know the
    // UID that the build agent is running under.
    String ret_str = ' --build-arg NOBUILD=1 ' +
                     ' --build-arg UID=' + sh(label: 'getuid()',
                                              script: 'id -u',
                                              returnStdout: true).trim() +
                     " --build-arg JENKINS_URL=$env.JENKINS_URL"
    if (cachebust) {
        /* groovylint-disable-next-line UnnecessaryGetter */
        Calendar current_time = Calendar.getInstance()
        // *NEVER* redefine CACHEBUST to some other value.  If you think something
        // in a Dockerfile needs doing less frequently than *always* consider either
        // CB0 (below) or define a new cache-bust interval
        ret_str += " --build-arg CACHEBUST=${currentBuild.startTimeInMillis}"
        ret_str += ' --build-arg CB0=' + current_time.get(Calendar.WEEK_OF_YEAR)
    }

    // pass through env. var.s
    ['DAOS_LAB_CA_FILE_URL', 'REPO_FILE_URL', 'HTTP_PROXY'].each { var ->
        if (env."$var") {
            ret_str += ' --build-arg ' + var + '="' + env."$var" + '"'
        }
    }

    if (env.REPO_FILE_URL) {
        def url = env.REPO_FILE_URL
        def DAOS_NO_PROXY = url.replaceFirst(/^https?:\/\//, '').split('/')[0].split(':')[0]
        println "DAOS_NO_PROXY: $DAOS_NO_PROXY"
        ret_str += ' --build-arg DAOS_NO_PROXY' + '="' + DAOS_NO_PROXY + '"'
    }

    String https_proxy = ''
    if (env.DAOS_HTTPS_PROXY) {
        https_proxy = env.DAOS_HTTPS_PROXY
    } else if (env.HTTPS_PROXY) {
        https_proxy = env.HTTPS_PROXY
    }
    if (https_proxy) {
        ret_str += ' --build-arg HTTPS_PROXY' + '="' + https_proxy + '"'
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

    // set BASE_DISTRO if necessary
    if (stage_info['target'] == 'leap15') {
        ret_str += ' --build-arg BASE_DISTRO=registry.opensuse.org/opensuse/leap-dnf:' + stage_info['distro_version']
    }

    ret_str += ' '
    return ret_str
}
