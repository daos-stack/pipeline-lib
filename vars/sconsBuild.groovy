// vars/sconsBuild.groovy

/**
 * sconsBuild.groovy
 *
 * sconsBuild pipeline step
 *
 */


def call(Map config = [:]) {
  /**
   * sconsBuild step method
   *
   * @param config Map of parameters passed
   * @return Integer status of script
   *
   * config['build_deps'] Setting for --build-deps.  Default 'yes'.
   * config['clean'] Space separated filenames to be removed after
   *  the prebuild option.
   *  Defaults _build.external* install build'.
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
   * config['update_prereq'] Setting for --update-prereq.  Default 'all'.
   * config['USE_INSTALLED'] setting for USE_INSTALLED.  Default 'yes'.
   *  If false, a failure of the scons commands will cause this step to fail.
   */

    /* If we have to tamper with the checkout, we also need to remove
     * the potential tampering before the scm operation.
     */
    if (config['scons_local_replace'] && config['target']) {
       sh "rm -rf \"\${WORKSPACE}/${config['target']}/scons_local\""
    }

    scm_config = [withSubmodules: true]
    if (config['scm']) {
        scm_config = config['scm']
        if (config['target'] && !scm_config['checkoutDir']) {
            scm_config['checkoutDir'] = config['target']
        }
    }
    checkoutScm(scm_config)
    if (env.DAOS_JENKINS_NOTIFY_STATUS != null) {
        githubNotify credentialsId: 'daos-jenkins-commit-status',
                     description: env.STAGE_NAME,
                     context: "build" + "/" + env.STAGE_NAME,
                     status: "PENDING"
    }

    if (config['scons_local_replace'] && config['target']) {
       // replace the scons_local directory in config['target']
       // with a link to the scons_local directory.
       sh """rm -rf "\${WORKSPACE}/${config['target']}/scons_local"
             ln -s "${WORKSPACE}/scons_local" \
               "\${WORKSPACE}/${config['target']}/scons_local"
             """
    }

    set_cwd = ''
    if (config['directory']) {
        set_cwd = "cd ${config['directory']}\n"
    } else if (config['target']) {
        set_cwd = "cd ${config['target']}\n"
    }

    scons_args = ''
    sconstruct = ''
    if (config['sconstruct']) {
        sconstruct = " --sconstruct=${config['sconstruct']}"
        scons_args += sconstruct
    }
    if (config['update_prereq']) {
        scons_args += " --update-prereq=${config['update_prereq']}"
    } else {
        scons_args += ' --update-prereq=all'
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
    } else if (config['target']) {
        scons_args += " SRC_PREFIX=\${WORKSPACE}}"
    }
    if (config['REQUIRES']) {
        scons_args += " REQUIRES=${config['REQUIRES']}"
    } else if (config['target']) {
        scons_args += " REQUIRES=${config['target']}"
    }
    //scons -c is not perfect so get out the big hammer
    clean_files = "_build.external{,-Linux} install build"
    if (config['clean']) {
        clean_files = config['clean'] + ' install build'
    }
    clean_cmd = "scons -c ${sconstruct}\n"
    if (clean_files) {
        clean_cmd += "rm -rf ${clean_files}\n"
        if (config['target']) {
            clean_cmd += "rm -rf ${config['target']}/${clean_files}\n"
        }
    }

    prebuild = ''
    if (config['prebuild']) {
      prebuild = config['prebuild'] + '\n'
    }
    prefix_1 = ''

    if (config['TARGET_PREFIX']) {
      scons_args += " TARGET_PREFIX=${config['TARGET_PREFIX']}"
      if (config['target_work']) {
        target_dirs = ''
        if (config['target_dirs']) {
          target_dirs = config['target_dirs']
        } else if (config['target']) {
          target_dirs = config['target']
        }
        target_work = config['target_work']
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

    script = '''if git show -s --format=%B | grep "^Skip-build: true"; then
                    exit 0
                fi
                '''
    script += clean_cmd
    script += 'SCONS_ARGS="' + scons_args +'"\n'
    script += '''# the config cache is unreliable so always force a reconfig
                 # with "--config=force"
                 if ! scons --config=force $SCONS_ARGS; then
                     rc=\${PIPESTATUS[0]}
                     echo "$SCONS_ARGS failed"
                     cat config.log || true
                     exit \$rc
                 fi'''
    full_script = "#!/bin/bash\nset -e\n" +
                  set_cwd + prebuild + prefix_1 + script
    rc = sh(script: full_script, returnStatus: true)
    // All of this really should be done in the post section of the main
    // Jenkinsfile but it cannot due to
    // https://issues.jenkins-ci.org/browse/JENKINS-39203
    // Once that is fixed all of the below should be pushed up into the
    // Jenkinsfile post { stable/unstable/failure/etc. }

    if (env.DAOS_JENKINS_NOTIFY_STATUS == null) {
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
    return rc
}
