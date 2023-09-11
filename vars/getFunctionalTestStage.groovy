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
 * @return a scripted stage to run in a pipeline
 */
Map call(Map kwargs = [:]) {
    String label = kwargs.get('label', 'ci_vm9')
    String name = kwargs.get('name', 'Functional on EL 8')
    String next_version = kwargs.get('next_version', null)
    String tags = kwargs.get('tags', '')
    String nvme = kwargs.get('nvme', '')
    String provider = kwargs.get('provider', '')
    // Map job_status = kwargs.get('job_status', [:])

    return {
        stage("${name}") {
            echo "[${name}] Start stage: label=${label}, tags=${tags}, nvme=${nvme}, provider=${provider}"
            // if (skipStage()) {
            //     echo "[${name}] Stage skipped by skipStage()"
            // } else {
            //     node(label) {
            //         try {
            //             echo "[${name}] Running functionalTest()"
            //             result = functionalTest(
            //                 inst_repos: daosRepos(),
            //                 inst_rpms: functionalPackages(1, next_version, 'tests-internal'),
            //                 test_tag: getFunctionalTags(default_tags: tags),
            //                 ftest_arg: getFunctionalArgs(default_nvme: nvme, provider: provider),
            //                 test_function: 'runTestFunctionalV2')
            //             echo "[${name}] Result: ${result}"
            //             // jobStatusUpdate(job_status, name, result)
            //             // jobStatusUpdate(
            //             //     job_status,
            //             //     name,
            //             //     functionalTest(
            //             //         inst_repos: daosRepos(),
            //             //         inst_rpms: functionalPackages(1, next_version, 'tests-internal'),
            //             //         test_tag: getFunctionalTags(default_tags: tags),
            //             //         ftest_arg: getFunctionalArgs(default_nvme: nvme, provider: provider),
            //             //         test_function: 'runTestFunctionalV2'))
            //         } finally {
            //             echo "[${name}] Running functionalTestPostV2()"
            //             functionalTestPostV2()
            //             // jobStatusUpdate(job_status, name)
            //         }
            //     }
            // }
            // echo "[${name}] Job status: ${job_status}"
            echo "[${name}] End stage"
        }
    }
}
