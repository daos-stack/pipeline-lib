// vars/skipFunctionalTestStage.groovy

/**
 * skipFunctionalTestStage.groovy
 *
 * Determine if a functional test stage should be skipped and log the reason.
 *
 * @param kwargs Map containing the following optional arguments (empty strings yield defaults):
 *      tags            functional test stage tags to run
 *      pragma_suffix   functional test stage commit pragma suffix, e.g. '-hw-medium'
 *      distro          functional test stage distro (required for VM)
 *      run_by_default  whether or not the stage should run by default
 *      run_if_pr       whether or not the stage should run for PR builds
 *      run_if_landing  whether or not the stage should run for landing builds
 * @return a String reason why the stage should be skipped; empty if the stage should run
 */
Map call(Map kwargs = [:]) {
    String tags = kwargs['tags'] ?: parseStageInfo()['test_tag']
    String pragma_suffix = kwargs['pragma_suffix'] ?: 'vm'
    String size = pragma_suffix.replace('-hw-', '')
    String distro = kwargs['distro'] ?: hwDistroTarget(size)
    Boolean run_by_default = kwargs['run_by_default'] ?: true
    Boolean run_if_pr = kwargs['run_if_pr'] ?: false
    Boolean run_if_landing = kwargs['run_if_landing'] ?: false

    String target_branch = env.CHANGE_TARGET ? env.CHANGE_TARGET : env.BRANCH_NAME
    String param_size = size.replace('-', '_')
    List override_pragmas = []
    List skip_pragmas = ["Skip-build-${distro}-rpm", 'Skip-test', 'Skip-func-test']
    if (pragma_suffix.contains('-hw')) {
        // HW Functional test stage
        override_pragmas.add("Skip-func-test-hw-${size}")
        override_pragmas.add("Skip-func-hw-test-${size}")
        override_pragmas.add('Run-daily-stages')
        skip_pragmas.add('Skip-func-test-hw')
        skip_pragmas.add("Skip-func-test-hw-${size}")
        skip_pragmas.add('Skip-func-hw-test')
        skip_pragmas.add("Skip-func-hw-test-${size}")
    } else {
        // VM Functional test stage
        override_pragmas.add("Skip-func-test-vm-${distro}")
        override_pragmas.add("Skip-func-test-${distro}")
        skip_pragmas.add('Skip-func-test-vm')
        skip_pragmas.add("Skip-func-test-vm-${distro}")
        skip_pragmas.add('Skip-func-test-vm-all')
        skip_pragmas.add("Skip-func-test-${distro}")
    }

    // Skip reasons the cannot be overriden
    if (stageAlreadyPassed()) {
        echo "Skipping ${env.STAGE_NAME}: All tests passed in the previous build"
        return true
    }
    if (!testsInStage(tags)) {
        echo "Skipping ${env.STAGE_NAME}: No tests detected matching the '${tags}' tags"
        return true
    }
    if (cachedCommitPragma('Run-GHA').toLowerCase() == 'true') {
        echo "Skipping ${env.STAGE_NAME}: Run-GHA set to True"
        return true
    }
    if (!startedByTimer() && !startedByUser() && !run_if_landing) {
        echo "Skipping ${env.STAGE_NAME}: In landing builds on master/release branch"
        return true
    }

    // Conditions for overriding skipping the stage
    for (override in override_pragmas) {
        if (cachedCommitPragma(override).toLowerCase() == 'false') {
            echo "Running ${env.STAGE_NAME}: User requested run via ${override}"
            return false
        }
    }
    if (paramsValue("CI_${param_size}_TEST", true) && !run_by_default) {
        echo "Running ${env.STAGE_NAME}: As requested by CI_${param_size}_TEST parameter"
        return false
    }

    // Overridable skip reasons
    if (docOnlyChange(target_branch) && prRepos(distro) == '') {
        echo "Skipping ${env.STAGE_NAME}: Document only file changes"
        return true
    }
    if (!paramsValue("CI_${param_size}_TEST", true)) {
        echo "Skipping ${env.STAGE_NAME}: As requested by CI_${param_size}_TEST parameter"
        return true
    }
    if (env.DAOS_STACK_CI_HARDWARE_SKIP == 'true' ) {
        echo "Skipping ${env.STAGE_NAME}: As requested by DAOS_STACK_CI_HARDWARE_SKIP parameter"
        return true
    }
    for (skip_pragma in skip_pragmas) {
        if (cachedCommitPragma(skip_pragma, 'false').toLowerCase() == 'true') {
            echo "Skipping ${env.STAGE_NAME}: User requested skip via ${skip_pragma}"
            return true
        }
    }
    /* groovylint-disable-next-line UnnecessaryGetter */
    if (isPr() && !run_if_pr) {
        echo "Skipping ${env.STAGE_NAME}: For PR build (override with '${override_pragmas[0]}: false')"
        return true
    }

    // Otherwise run the stage
    return false
}
