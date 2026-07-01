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
 *      other_packages  space-separated string of additional RPM packages to install
 *      node_count      number of nodes to provision and use for the stage; overrides the count
 *                      that would otherwise be inferred from the stage name by parseStageInfo()
 *      runStage        whether or not to run the stage; overrides skipFunctionalTestStage()
 *      run_if_pr       whether or not the stage should run for PR builds
 *      run_if_landing  whether or not the stage should run for landing builds
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
    String nvme = kwargs.get('nvme')
    String default_nvme = kwargs.get('default_nvme')
    String provider = kwargs.get('provider', '')
    String distro = kwargs.get('distro', null)
    String image_version = kwargs.get('image_version', null)
    String rpm_distro = kwargs.get('rpm_distro', null)
    String base_branch = kwargs.get('base_branch')
    String other_packages = kwargs.get('other_packages', '')
    Integer node_count = kwargs.get('node_count') as Integer
    Boolean run_if_pr = kwargs.get('run_if_pr', false)
    Boolean run_if_landing = kwargs.get('run_if_landing', false)

    // Temporarily use a String to allow determing if the value was unset, but when used pass the
    // value in as a Boolean. This allows stages that have not yet been converted to use runStage
    // to be skipped by the existing skipFunctionalTestStage logic, while new stages can use
    // runStage directly. Once all stages have been converted to use runStage, this should be
    // changed to a Boolean.
    String runStage = kwargs.get('runStage', 'undefined').toString()

    Map job_status = kwargs.get('job_status', [:])

    String tags = getFunctionalTags(
        pragma_suffix: pragma_suffix, stage_tags: stage_tags, default_tags: default_tags)

    // Backwards compatibility: if runStage is not specified, use skipFunctionalTestStage() to
    // determine if the stage should be run.
    if (runStage == 'undefined') {
        runStage = !skipFunctionalTestStage(
            tags: tags,
            pragma_suffix: pragma_suffix,
            distro: distro,
            run_if_pr: run_if_pr,
            run_if_landing: run_if_landing).toString()
    }

    // Avoid extra processing by skipping early if the stage is not to be run.
    if (runStage == false) {
        println("[${name}] Stage skipped by runStage=false")
        Utils.markStageSkippedForConditional("${name}")
        return
    }

    Map functionalTestArgs = [
        image_version: image_version,
        inst_repos: daosRepos(distro),
        inst_rpms: functionalPackages(
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
        test_function: 'runTestFunctionalV2']
    if (node_count != null) {
        functionalTestArgs['node_count'] = node_count
    }

    return scriptedTestStage(
        name: name,
        pragmaSuffix: pragma_suffix,
        label: label,
        testBranch: base_branch,
        jobStatus: job_status,
        functionalTestArgs: functionalTestArgs,
        )
}
