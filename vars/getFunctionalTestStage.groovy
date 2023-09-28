// vars/getFunctionalTestStage.groovy

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

/**
 * getFunctionalTestStage.groovy
 *
 * Get a functional test stage in scripted syntax.
 *
 * @param kwargs Map containing the following optional arguments (empty strings yield defaults):
 *      name            functional test stage name
 *      pragma_suffix   functional test stage commit pragma suffix, e.g. '-hw-medium'
 *      label           functional test stage default cluster label
 *      next_version    next daos package version
 *      stage_tags      functional test stage tags always used and combined with all other tags
 *      default_tags    launch.py tags argument to use when no parameter or commit pragma exist
 *      default_nvme    launch.py --nvme argument to use when no parameter or commit pragma exist
 *      provider        launch.py --provider argument to use
 *      distro          functional test stage distro (VM)
 *      job_status      Map of status for each stage in the job/build
 * @return a scripted stage to run in a pipeline
 */
Map call(Map kwargs = [:]) {
    String name = kwargs.get('name', 'Unknown Functional Test Stage')
    String pragma_suffix = kwargs.get('pragma_suffix')
    String label = kwargs.get('label')
    String next_version = kwargs.get('next_version', null)
    String stage_tags = kwargs.get('stage_tags')
    String default_tags = kwargs.get('default_tags')
    String default_nvme = kwargs.get('default_nvme')
    String provider = kwargs.get('provider', '')
    String distro = kwargs.get('distro')
    Map job_status = kwargs.get('job_status', [:])

    return {
        stage("${name}") {
            // Get the tags for thge stage. Use either the build parameter, commit pragma, or
            // default tags. All tags are combined with the stage tags to ensure only tests that
            // 'fit' the cluster will be run.
            String tags = getFunctionalTags(
                pragma_suffix: pragma_suffix, stage_tags: stage_tags, default_tags: default_tags)

            // Setup the arguments for the skipStage() groovy script to directly call the correct
            // skip stage logic. The stage name is no longer required to be defined in skipStage().
            Map skip_config = ['tags': tags]
            if (pragma_suffix.startsWith('-hw-')) {
                // With this param set skip_ftest_hw() will be called by skipStage()
                skip_config['hw_size'] = pragma_suffix.replace('-hw-', '')
            } else if (kwargs['distro']) {
                // With this param set skip_ftest() will be called by skipStage()
                skip_config['distro'] = kwargs['distro']
            }

            echo "[${name}] Start with ${skip_config}"
            echo "[${name}] Check out from version control"
            checkoutScm(cleanAfterCheckout: false)
            if (skipStage(skip_config)) {
                echo "[${name}] Stage skipped by skipStage(${skip_config})"
                Utils.markStageSkippedForConditional("${name}")
            } else {
                node(cachedCommitPragma("Test-label${pragma_suffix}", label)) {
                    // Ensure access to any branch provisioning scripts exist
                    echo "[${name}] Check out from version control"
                    checkoutScm(cleanAfterCheckout: false)
                    try {
                        echo "[${name}] Running functionalTest() on ${label} with tags=${tags}"
                        jobStatusUpdate(
                            job_status,
                            name,
                            functionalTest(
                                inst_repos: daosRepos(distro),
                                inst_rpms: functionalPackages(1, next_version, 'tests-internal'),
                                test_tag: tags,
                                ftest_arg: getFunctionalArgs(
                                    pragma_suffix: pragma_suffix,
                                    default_nvme: default_nvme,
                                    provider: provider),
                                test_function: 'runTestFunctionalV2'))
                    } finally {
                        echo "[${name}] Running functionalTestPostV2()"
                        functionalTestPostV2()
                        jobStatusUpdate(job_status, name)
                    }
                }
            }
            echo "[${name}] Finished with ${job_status}"
        }
    }
}
