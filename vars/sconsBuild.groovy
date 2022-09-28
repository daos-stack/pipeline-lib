/* groovylint-disable VariableName */
// vars/sconsBuild.groovy

/**
 * sconsBuild.groovy
 *
 * sconsBuild pipeline step
 *
 */

def num_proc() {
    return sh(label: "Get number of processors online",
              script: "/usr/bin/getconf _NPROCESSORS_ONLN",
              returnStdout: true)
}

def call(Map config = [:]) {
  /**
   * sconsBuild step method
   *
   * @param config Map of parameters passed
   * @return Integer status of script
   *
   * config['build_deps'] Setting for --build-deps.  Default 'yes'.
   * config['scons_exe'] Name of scons executable.  Default 'scons'.
   * config['skip_clean'] Skip cleaning the build
   * config['clean'] Space separated filenames to be removed after
   *  the prebuild option.
   *  Defaults _build.external'.
   * config['directory'] is the directory to use for the build.
   *  Defaults to config['target'] if config['target'] is specified.
   * config['no_install'] Set to true to skip install step.
   * config['prebuild'] Optional commands to run before the build.
   * config['PYTHONPATH'] Python path environment to set.
   * config['REQUIRES'] Required components to build.
   *  Defaults to config['target'] if config['target'] is specified.
   * config['returnStatus'] return a status code.  Default false.
   * config['scm'] are the parameters for the scm to use.
   * config['sconstruct'] Path to sconstruct file if specified.
   * config['scons_args'] are optional additional arguments for scons
   *  after all other options are set.
   * config['scons_local_replace'] replace scons_local in config['target']
   *  with a symlink.
   * config['SRC_PREFIX'] Directories to find the prefetched source code in.
   *  Defaults to config['target'] if config['target'] is specified.
   * config['target'] Target if only one component is being built.
   * config['target_dirs'] Space delimited list build targets or prebuilt
   *  targets to be in 'TARGET_PREFIX'.  Defaults to config['target'] if
   *  config['target'] is specified.
   * config['TARGET_PREFIX'] Alternate prefix to use in building.
   * config['target_work'] Directory in workspace containing directories
   *  symlinked from the target prefix.
   * config['USE_INSTALLED'] setting for USE_INSTALLED.  Default 'all'.
   * config['BUILD_ROOT'] setting for BUILD_ROOT.  Default 'build'.
   * config['BUILD_TYPE'] setting for BUILD_TYPE.  Default 'dev'.
   * config['COMPILER'] setting for COMPILER.
   *  Default is from parsing environment variables.
   * config['WARNING_LEVEL'] setting for WARNING_LEVEL.  Default 'error'.
   *  If false, a failure of the scons commands will cause this step to fail.
   * config['failure_artifacts'] Artifacts to link to when scons fails.
   *  Default is from parsing environment variables.
   * config['log_to_file'] Copy build output to a file.
   *  Default filename is based on parsing environment variables.
   * config['parallel_build'] Build using maximum CPUs
   * config['stash_files']  Filename containing list of test files to stash.
   * config['stash_opt'] Boolean, stash tar of /opt in preference to install/**.  Default false.
   *   If present, those files will be placed in a stash name
   *   of based on parsing the evironment variables of the
   *   <target-compiler[-build_type]-test>.  Additional stashes
   *   will be created for "install" and "build_vars" with similar
   *   prefixes.
   */

    String config_target
    if (config['target']) {
      config_target = config['target']
      config.remove('target')
    }
    Map stage_info = parseStageInfo(config)

    String tee_file = '| tee $WORKSPACE/' + stage_info['log_to_file']

    String failure_artifacts=''
    if (config['failure_artifacts']) {
        failure_artifacts = config['failure_artifacts']
    } else {
        failure_artifacts = 'config.log-' +  stage_info['target'] +
                            '-' + stage_info['compiler']
        if (stage_info['build_type']) {
            failure_artifacts += '-' + stage_info['build_type']
        }
    }

    /* If we have to tamper with the checkout, we also need to remove
     * the potential tampering before the scm operation.
     */
    if (config['scons_local_replace'] && config_target) {
       sh "rm -rf \"\${WORKSPACE}/${config_target}/scons_local\""
    }

    def scm_config = [withSubmodules: true]
    if (config['scm']) {
        scm_config = config['scm']
        if (config_target && !scm_config['checkoutDir']) {
            scm_config['checkoutDir'] = config_target
        }
    }
    checkoutScm(scm_config)
    if (env.DAOS_JENKINS_NOTIFY_STATUS != null) {
        scmNotify description: env.STAGE_NAME,
                  context: "build" + "/" + env.STAGE_NAME,
                  status: "PENDING"
    }

    if (config['scons_local_replace'] && config_target) {
       // replace the scons_local directory in config_target
       // with a link to the scons_local directory.
       sh """rm -rf "\${WORKSPACE}/${config_target}/scons_local"
             ln -s "${WORKSPACE}/scons_local" \
               "\${WORKSPACE}/${config_target}/scons_local"
             """
    }

    if (stage_info['compiler'] == 'covc') {
      httpRequest url: env.JENKINS_URL +
                       'job/daos-stack/job/tools/job/master' +
                       '/lastSuccessfulBuild/artifact/' +
                       'bullseyecoverage-linux.tar',
                  httpMode: 'GET',
                  outputFile: 'bullseye.tar'
    }

    def set_cwd = ''
    if (config['directory']) {
        set_cwd = "cd ${config['directory']}\n"
    } else if (config_target) {
        set_cwd = "cd ${config_target}\n"
    }

    // probe for traditional scons image name first
    // then try for el-8 distro provided scons image name
    String scons_exe = sh(label: 'probe for scons command',
                          script: 'command -v scons || command -v scons-3',
                          returnStdout: true).trim()

    def scons_args = ''
    if (config['parallel_build'] && config['parallel_build'] == true) {
        String procs = num_proc()
        scons_args += '-j ' + procs.trim()
    }
    def sconstruct = ''
    if (config['scons_exe']) {
        scons_exe = "${config['scons_exe']}"
    }
    if (config['sconstruct']) {
        sconstruct = " --sconstruct=${config['sconstruct']}"
        scons_args += sconstruct
    }
    if (config['build_deps']) {
        scons_args += " --build-deps=${config['build_deps']}"
    } else {
        scons_args += ' --build-deps=yes'
    }
    if (!config['no_install']) {
        scons_args += ' install'
    }
    if (config['USE_INSTALLED']) {
        scons_args += " USE_INSTALLED=${config['USE_INSTALLED']}"
    } else {
        scons_args += ' USE_INSTALLED=all'
    }
    if (config['SRC_PREFIX']) {
        scons_args += " SRC_PREFIX=${config['SRC_PREFIX']}"
    } else if (config_target) {
        scons_args += " SRC_PREFIX=\${WORKSPACE}}"
    }
    if (config['REQUIRES']) {
        scons_args += " REQUIRES=${config['REQUIRES']}"
    } else if (config_target) {
        scons_args += " REQUIRES=${config_target}"
    }
    if (config['BUILD_ROOT']) {
        scons_args += " BUILD_ROOT=${config['BUILD_ROOT']}"
    }
    if (stage_info['build_type']) {
        scons_args += " BUILD_TYPE=${stage_info['build_type']}"
    }
    if (stage_info['compiler']) {
        scons_args += " COMPILER=${stage_info['compiler']}"
    }
    if (config['WARNING_LEVEL']) {
        scons_args += " WARNING_LEVEL=${config['WARNING_LEVEL']}"
    }
    //scons -c is not perfect so get out the big hammer
    def clean_cmd = ""
    if (config['skip_clean']) {
        clean_cmd += "echo 'skipping scons -c'\n"
    } else {
        def clean_files = "_build.external"
        if (config['clean']) {
            clean_files = config['clean']
        }
        clean_files += ' install build {daos_m,daos,iof,cart-Linux}.conf'
        clean_files += ' .sconsign{,-Linux}.dblite .sconf-temp{,-Linux}'
        clean_cmd += scons_exe + " -c ${sconstruct}\n"
        clean_cmd += "rm -rf ${clean_files}\n"
        if (config_target) {
            clean_cmd += "rm -rf ${config_target}/${clean_files}\n"
        }
    }

    // Builds of Raft may fail if this directory is present in the workspace
    // from a previous run.
    // Builds may fail if bandit.xml files present in the workspace.
    sh label: 'Remove some old build files if present.',
       script:'rm -rf src/rdb/raft/CLinkedListQueue bandit.xml test.cov',
       returnStatus: true

    def prebuild = ''
    if (config['prebuild']) {
      prebuild = config['prebuild'] + '\n'
    }
    def prefix_1 = ''

    if (config['TARGET_PREFIX']) {
      scons_args += " TARGET_PREFIX=${config['TARGET_PREFIX']}"
      if (config['target_work']) {
        def target_dirs = ''
        if (config['target_dirs']) {
          target_dirs = config['target_dirs']
        } else if (config_target) {
          target_dirs = config_target
        }
        def target_work = config['target_work']
        prefix_1 =
          """for new_dir in ${target_dirs}; do
               mkdir -p "\${WORKSPACE}/${target_work}/\${new_dir}"
               rm -f "${config['TARGET_PREFIX']}/\${new_dir}"
               ln -s "\${WORKSPACE}/${target_work}/\${new_dir}" \
                 "${config['TARGET_PREFIX']}/\${new_dir}"
             done
             """
      }
    }
    if (config['scons_args']) {
        scons_args += ' ' + config['scons_args']
    }

    def script = clean_cmd
    script += 'SCONS_ARGS="' + scons_args + '"\n'

    if (config['coverity']) {
        sh 'rm -rf ./cov_analysis ./cov-int ./coverity/*.tgz'
        Map cov_config = [:]
        cov_config['project'] = config['coverity']
        cov_config['tool_path'] = './cov_analysis'
        if (coverityToolDownload(cov_config) < 0)
            return
        script += "PATH+=:${WORKSPACE}/cov_analysis/bin\n"
        scons_exe = "cov-build --dir cov-int " + scons_exe
    }

    // the config cache is unreliable so always force a reconfig
    // with "--config=force"
    script += '''if ! ''' + scons_exe + ''' --config=force $SCONS_ARGS''' +
                 tee_file + '''; then
                     rc=\${PIPESTATUS[1]}
                     echo "Trying to write to log file failed: \$rc"
                     exit \$rc
                 fi
                 rc=\${PIPESTATUS[0]}
                 if [ \$rc != 0 ]; then
                     echo "scons failed: \$rc."
                     set +x
                     echo -n "If the error is not in the output above, it might be in the config.log: "
                     echo "${JOB_URL%/job/*}/view/change-requests/job/$BRANCH_NAME/$BUILD_ID/artifact/''' +
                          failure_artifacts + '"' + '''
                     exit \$rc
                 fi'''
    def full_script = "#!/bin/bash\nset -ex\n" +
                      set_cwd + prebuild + prefix_1 + script
    int rc = 0
    rc = sh(script: full_script, label: env.STAGE_NAME, returnStatus: true)
    // All of this really should be done in the post section of the main
    // Jenkinsfile but it cannot due to
    // https://issues.jenkins-ci.org/browse/JENKINS-39203
    // Once that is fixed all of the below should be pushed up into the
    // Jenkinsfile post { stable/unstable/failure/etc. }

    if (env.DAOS_JENKINS_NOTIFY_STATUS != null) {
        if (rc != 0) {
            stepResult name: env.STAGE_NAME, context: "build",
                       result: "FAILURE"
        } else if (rc == 0) {
            stepResult name: env.STAGE_NAME, context: "build",
                       result: "SUCCESS"
        }
    }
    if (!config['returnStatus'] && (rc != 0)) {
      error "sconsBuild failed for ${full_script}"
    }
    if (config['stash_files']) {
        String target_stash = stage_info['target'] + '-' +
                              stage_info['compiler']
        if (stage_info['build_type']) {
            target_stash += '-' + stage_info['build_type']
        }
	if (config.get('stash_opt', false)) {
              sh(script: 'tar -cf opt-daos.tar /opt/daos/', label: 'Tar /opt')
              stash name: target_stash + '-opt-tar', includes: 'opt-daos.tar'	
        } else {
            stash name: target_stash + '-install',
                includes: 'install/**'
        }
	String vars_includes = '.build_vars.*'
        if (stage_info['compiler'] == 'covc') {
            vars_includes += ', test.cov'
	}
        stash name: target_stash + '-build-vars',
              includes: vars_includes
        String test_files = readFile "${env.WORKSPACE}/${config['stash_files']}"
        stash name: target_stash + '-tests',
              includes: test_files
    }
    return rc
}
