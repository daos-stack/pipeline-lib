/* groovylint-disable DuplicateStringLiteral, VariableName */
// vars/buildRpm.groovy
/*
 * Copyright 2020-2024 Intel Corporation
 * Copyright 2025 Hewlett Packard Enterprise Development LP
 */

  /**
   * buildRpm step method
   *
   * @param config Map of parameters passed
   *
   * config['build_script'] Script to build RPMs.  Default 'ci/rpm/build.sh'.
   *
   * config['chroot_name']  Name of chroot, such as 'epel-7-x86_64'.
   *                        Default based on parsing env.STAGE_NAME.
   *
   * config['context']      Context name for SCM to identify the specific
   *                        stage to update status for.
   *                        Default is 'build/' + env.STAGE_NAME.
   *
   *  Important:
   *     The SCM status checking for passing may expect a specific name.
   *
   *     Matrix stages must override this setting to include matrix axes
   *     names to ensure a unique name is generated.
   *
   *     Or the default name has to be changed in a way that is compatible
   *     with a future Matrix implementation.
   *
   * config['description']  Description to report for SCM status.
   *                        Default env.STAGE_NAME.
   *
   * config['flow_name']    Flow name to use for looking up the log URL
   *                        for reporting to the SCM.
   *                        Default is to use env.STAGE_NAME.
   *
   * config['target']       Target distribution, such as 'centos7', 'el8,
   *                        'leap15'.
   *                        Default based on parsing environment variables.
   *
   * config['Unstable']     Convert build error to unstable.
   *                        default false.
   */

Map call(Map config = [:]) {
    long startDate = System.currentTimeMillis()
    String context = config.get('context', 'build/' + env.STAGE_NAME)
    String description = config.get('description', env.STAGE_NAME)
    String build_script = config.get('build_script', 'ci/rpm/build.sh')

    Map stage_info = parseStageInfo(config)

    scmNotify description: description,
              context: context,
              status: 'PENDING'

    checkoutScm withSubmodules: true

    String env_vars = ''
    env_vars = ' TARGET=' + stage_info['target'] +
               ' DISTRO_VERSION=' + stage_info['distro_version']
    if (config['chroot_name']) {
        env_vars = ' CHROOT_NAME=' + config['chroot_name']
    }

    String https_proxy = ''
    if (env.DAOS_HTTPS_PROXY) {
        https_proxy = "${env.DAOS_HTTPS_PROXY}"
    }
    if (https_proxy) {
        env_vars += ' HTTPS_PROXY=' + https_proxy
    }

    String error_stage_result = 'FAILURE'
    String error_build_result = 'FAILURE'
    if (config['unstable']) {
        error_stage_result = 'UNSTABLE'
        error_build_result = 'SUCCESS'
    }
    Map runData = ['result': 'FAILURE']
    catchError(stageResult: error_stage_result,
               buildResult: error_build_result) {
        // flow_name used as the label for this step to allow log lookup.
        sh(label: config.get('flow_name', env.STAGE_NAME),
           script: "${env_vars} " + build_script)
        runData['result'] = 'SUCCESS'
    }
    int runTime = durationSeconds(startDate)
    runData['buildrpm_time'] = runTime
    return runData
}
