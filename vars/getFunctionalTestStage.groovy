/* groovylint-disable VariableName */
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
 *      nvme            launch.py --nvme argument to use
 *      default_nvme    launch.py --nvme argument to use when no parameter or commit pragma exist
 *      provider        launch.py --provider argument to use
 *      distro          functional test stage distro (VM)
 *      image_version   image version to use for provisioning, e.g. el8.8, leap15.6, etc.
 *      rpm_distro      distribution to use for daos packages installed on the test nodes, e.g.
 *                      '.el9', '.suse.lp156', etc.  If not specified, it will be determined by
 *                      rpmDistValue(distro)
 *      base_branch     if specified, checkout sources from this branch before running tests
 *      inst_rpms       space-separated string of RPM packages to install on the test nodes;
 *                      exclusive of next_version, rpm_distro, and other_packages.
 *      other_packages  space-separated string of additional RPM packages to install
 *      bullseye        whether or not to use the bullseye-sepecific repo for provisioning
 *      node_count      number of nodes to provision and use for the stage; overrides the count
 *                      that would otherwise be inferred from the stage name by parseStageInfo()
 *      runStage        whether or not to run the stage; overrides skipFunctionalTestStage()
 *      run_if_pr       whether or not the stage should run for PR builds
 *      run_if_landing  whether or not the stage should run for landing builds
 *      job_status      Map of status for each stage in the job/build
 *      coverage_stash  name of stash to include code coverage results from the tests
 * @return a scripted stage to run in a pipeline
 */
/* groovylint-disable-next-line MethodSize */
Map call(Map kwargs = [:]) {
    String name = kwargs.get('name', 'Unknown Functional Test Stage')
    String pragma_suffix = kwargs.get('pragma_suffix')
    String label = kwargs.get('label')
    String next_version = kwargs.get('next_version', null)
    String stage_tags = kwargs.get('stage_tags')
    String default_tags = kwargs.get('default_tags')
    String nvme = kwargs.get('nvme')
    String default_nvme = kwargs.get('default_nvme')
    String provider = kwargs.get('provider', '')
    String distro = kwargs.get('distro', null)
    String image_version = kwargs.get('image_version', null)
    String rpm_distro = kwargs.get('rpm_distro', null)
    String base_branch = kwargs.get('base_branch')
    String inst_rpms = kwargs.get('inst_rpms', null)
    String other_packages = kwargs.get('other_packages', '')
    Boolean bullseye = kwargs.get('bullseye', false)
    Integer node_count = kwargs.get('node_count') as Integer
    Boolean run_if_pr = kwargs.get('run_if_pr', false)
    Boolean run_if_landing = kwargs.get('run_if_landing', false)

    // Temporarily use a String to allow determing if the value was unset, but when used pass the
    // value in as a Boolean. This allows stages that have not yet been converted to use runStage
    // to be skipped by the existing skipFunctionalTestStage logic, while new stages can use
    // runStage directly. Once all stages have been converted to use runStage, this should be
    // changed to a Boolean.
    final String RUNSTAGE_UNSET = 'undefined'
    String runStage = kwargs.get('runStage', RUNSTAGE_UNSET)

    Map job_status = kwargs.get('job_status', [:])
    String coverage_stash = kwargs.get('coverage_stash', '')

    return {
        stage("${name}") {
            // Get the tags for the stage. Use either the build parameter, commit pragma, or
            // default tags. All tags are combined with the stage tags to ensure only tests that
            // 'fit' the cluster will be run.
            String tags = getFunctionalTags(
                pragma_suffix: pragma_suffix, stage_tags: stage_tags, default_tags: default_tags)

            /* groovylint-disable-next-line DuplicateStringLiteral */
            if (runStage == 'false') {
                println("[${name}] Stage skipped by runStage=false")
                Utils.markStageSkippedForConditional("${name}")
                return
            } else if (runStage == RUNSTAGE_UNSET) {
                // To be removed once all stages have been converted to use runStage.
                Map skip_kwargs = [
                'tags': tags,
                'pragma_suffix': pragma_suffix,
                'distro': distro,
                'run_if_pr': run_if_pr,
                'run_if_landing': run_if_landing]
                if (skipFunctionalTestStage(skip_kwargs)) {
                    println("[${name}] Stage skipped by skipFunctionalTestStage()")
                    Utils.markStageSkippedForConditional("${name}")
                    return
                }
            } else if (!testsInStage(tags)) {
                println("[${name}] Stage skipped by no tests matching the '${tags}' tags")
                Utils.markStageSkippedForConditional("${name}")
                return
            }

            node(cachedCommitPragma("Test-label${pragma_suffix}", label)) {
                // Ensure access to any branch provisioning scripts exist
                println("[${name}] Check out '${base_branch}' from version control")
                if (base_branch) {
                    checkoutScm(
                        url: 'https://github.com/daos-stack/daos.git',
                        branch: base_branch,
                        withSubmodules: false,
                        pruneStaleBranch: true)
                } else {
                    checkoutScm(pruneStaleBranch: true)
                }

                try {
                    println("[${name}] Running functionalTest() on ${label} with tags=${tags}")
                    Map ftestConfig = [
                        image_version: image_version,
                        inst_repos: daosRepos(distro),
                        inst_rpms: inst_rpms ?: functionalPackages(
                            clientVersion: 1,
                            nextVersion: next_version,
                            addDaosPkgs: 'tests-internal',
                            rpmDistribution: rpm_distro) + ' ' + other_packages,
                        test_tag: tags,
                        ftest_arg: getFunctionalArgs(
                            pragma_suffix: pragma_suffix,
                            nvme: nvme,
                            default_nvme: default_nvme,
                            provider: provider)['ftest_arg'],
                        test_function: 'runTestFunctionalV2',
                        bullseye: bullseye,
                        coverage_stash: coverage_stash]
                    if (node_count != null) {
                        /* groovylint-disable-next-line DuplicateStringLiteral */
                        ftestConfig['node_count'] = node_count
                    }
                    jobStatusUpdate(
                        job_status,
                        name,
                        functionalTest(ftestConfig))
                } finally {
                    println("[${name}] Running functionalTestPostV2()")
                    functionalTestPostV2()
                    jobStatusUpdate(job_status, name)
                }
            }
        }
        println("[${name}] Finished with ${job_status}")
    }
}
