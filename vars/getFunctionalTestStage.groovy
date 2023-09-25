// vars/getFunctionalTestStage.groovy

/**
 * getFunctionalTestStage.groovy
 *
 * Get a functional test stage in scripted syntax.
 *
 * @param kwargs Map containing the following optional arguments (empty strings yield defaults):
 *      name            functional test stage name
 *      pragma_suffix   functional test stage commit pragma suffix 
 *      label           functional test stage default cluster label
 *      next_version    next daos package version
 *      stage_tags      functional test stage tags always used and combined with all other tags
 *      timer_tags      functional test stage tags to use when the stage is started by a timer
 *      default_tags    launch.py tags argument to use when no parameter or commit pragma exist
 *      default_nvme    launch.py --nvme argument to use when no parameter or commit pragma exist
 *      provider        launch.py --provider argument to use
 *      distro          functional test stage distro (VM)
 *      job_status      Map of status for each stage in the job/build
 * @return a scripted stage to run in a pipeline
 */
Map call(Map kwargs = [:]) {
    String name = kwargs.get('name')
    String pragma_suffix = kwargs.get('pragma_suffix')
    String label = kwargs.get('label')
    String next_version = kwargs.get('next_version', null)
    String stage_tags = kwargs.get('stage_tags')
    String timer_tags = kwargs.get('timer_tags')
    String default_tags = kwargs.get('default_tags')
    String default_nvme = kwargs.get('default_nvme')
    String provider = kwargs.get('provider', '')
    String distro = kwargs.get('distro')
    Map job_status = kwargs.get('job_status', [:])

    return {
        stage("${name}") {
            label = cachedCommitPragma("Test-label-${pragma_suffix}", label)

            // Get the tags for thge stage. Use the timer_tags if the build has been started by a
            // timer. Otherwise use either the build parameter, commit pragma, or default tags. All
            // tags are comnbined with the stage tags to ensure only tests that 'fit' the cluster
            // will be run.
            if (startedByTimer() && timer_tags) {
                default_tags = timer_tags
            }
            String tags = getFunctionalTags(
                pragma_suffix: pragma_suffix, stage_tags: stage_tags, default_tags: default_tags)

            // Setup the arguments for the skipStage() groovy script to directly call the correct
            // skip stage logic. The stage name is no longer required to be defined in skipStage().
            Map skip_config = ['tags': tags]
            if (kwargs['distro']) {
                skip_config['distro'] = kwargs['distro']
            } else if (pragma_suffix.startsWith('hw-')) {
                skip_config['hw_size'] = pragma_suffix.replace('hw-', '')
            }

            echo '[getFunctionalTestStage] Parameters:'
            echo "[getFunctionalTestStage]   name:          ${name}"
            echo "[getFunctionalTestStage]   pragma_suffix: ${pragma_suffix}"
            echo "[getFunctionalTestStage]   label:         ${label}"
            echo "[getFunctionalTestStage]   next_version:  ${next_version}"
            echo "[getFunctionalTestStage]   stage_tags:    ${stage_tags}"
            echo "[getFunctionalTestStage]   timer_tags:    ${timer_tags}"
            echo "[getFunctionalTestStage]   default_tags:  ${default_tags}"
            echo "[getFunctionalTestStage]   tags:          ${tags}"
            echo "[getFunctionalTestStage]   default_nvme:  ${default_nvme}"
            echo "[getFunctionalTestStage]   provider:      ${provider}"
            echo "[getFunctionalTestStage]   distro:        ${distro}"
            echo "[getFunctionalTestStage]   job_status:    ${job_status}"
            echo "[getFunctionalTestStage]   skip_config:   ${skip_config}"
            echo "[getFunctionalTestStage] Start stage ${name}"

            // if (skipStage(skip_config)) {
            //     echo "[${name}] Stage skipped by skipStage(${skip_config})"
            // } else {
            //     node(label) {
            //         try {
            //             echo "[${name}] Running functionalTest() on ${label} with tags=${tags}"
            //             result = functionalTest(
            //                 inst_repos: daosRepos(),
            //                 inst_rpms: functionalPackages(1, next_version, 'tests-internal'),
            //                 test_tag: tags,
            //                 ftest_arg: getFunctionalArgs(
            //                     pragma_suffix: pragma_suffix,
            //                     default_nvme: default_nvme,
            //                     provider: provider),
            //                 test_function: 'runTestFunctionalV2')
            //             jobStatusUpdate(job_status, name, result)
            //             // jobStatusUpdate(
            //             //     job_status,
            //             //     name,
            //             //     functionalTest(
            //             //         inst_repos: daosRepos(),
            //             //         inst_rpms: functionalPackages(1, next_version, 'tests-internal'),
            //             //         test_tag: tags,
            //             //         ftest_arg: getFunctionalArgs(
            //             //             pragma_suffix: pragma_suffix,
            //             //             default_nvme: default_nvme,
            //             //             provider: provider),
            //             //         test_function: 'runTestFunctionalV2'))
            //         } finally {
            //             echo "[${name}] Running functionalTestPostV2()"
            //             functionalTestPostV2()
            //             jobStatusUpdate(job_status, name)
            //         }
            //     }
            // }
            echo "[${name}] Job status: ${job_status}"
        }
    }
}
