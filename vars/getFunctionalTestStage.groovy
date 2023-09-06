// vars/getFunctionalTestStage.groovy

/**
 * getFunctionalTestStage.groovy
 *
 * Get a functional test stage in scripted syntax.
 *
 * @param kwargs Map containing the following optional arguments (empty strings yield defaults):
 *      label           label identifying which cluster will run the functional test stage
 *      name            functional test stage name
 *      next_version    next daos package version
 *      tags            launch.py tags argument to use
 *      nvme            launch.py --nvme argument to use when no parameter or commit pragma exist
 *      provider        launch.py --provider argument to use
 * @return Map values that define a scripted stage to run in a pipeline
 */
Map call(Map kwargs = [:]) {
    String label = kwargs.get('label', 'ci_nvme9')
    String name = kwargs.get('name', 'Functional Hardware Large')
    String next_version = kwargs.get('next_version', '1000')
    String tags = kwargs.get('tags', 'pr')
    String tags = kwargs.get('nvme', 'auto')
    String tags = kwargs.get('provider', 'ofi+verbs;ofi_rxm')

    return {
        stage("${name}") {
            if (skipStage()) {
                println("The ${name} stage has been skipped by skipStage()")
            } else {
                node(label) {
                    try {
                        job_step_update(
                            functionalTest(
                                inst_repos: daosRepos(),
                                inst_rpms: functionalPackages(1, next_version, 'tests-internal'),
                                test_tag: getFunctionalTags(default_tags: tags),
                                ftest_arg: getFunctionalArgs(default_nvme: nvme, provider: provider),
                                test_function: 'runTestFunctionalV2'))
                    } finally {
                        functionalTestPostV2()
                        job_status_update()
                    }
                }
            }
        }
    }
}
