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
        echo "[${env.STAGE_NAME}] Skipping stage due to all tests passing in the previous build"
        return true
    }
    if (!testsInStage(tags)) {
        echo "[${env.STAGE_NAME}] Skipping stage due to detecting no tests matching the '${tags}' tags"
        return true
    }
    if (cachedCommitPragma('Run-GHA').toLowerCase() == 'true') {
        echo "[${env.STAGE_NAME}] Skipping stage - Run-GHA set to True"
        return true
    }
    Boolean started_by_timer = startedByTimer()
    Boolean started_by_user = startedByUser()
    Boolean started_by_upstream = startedByUpstream()
    if (!started_by_timer && !started_by_user && !run_if_landing) {
        echo "[${env.STAGE_NAME}] Skipping stage in landing build [startedByTimer=${started_by_timer}, startedByUser=${started_by_user}, run_if_landing=${run_if_landing}, started_by_upstream=${started_by_upstream}]"
        return true
    }

    // Conditions for overriding skipping the stage
    for (override in override_pragmas) {
        if (cachedCommitPragma(override).toLowerCase() == 'false') {
            echo "[${env.STAGE_NAME}] Running stage due to ${override} commit pragma"
            return false
        }
    }
    if (paramsValue("CI_${param_size}_TEST", true) && !run_by_default) {
        echo "[${env.STAGE_NAME}] Running stage due to CI_${param_size}_TEST parameter"
        return false
    }

    // Overridable skip reasons
    if (docOnlyChange(target_branch) && prRepos(distro) == '') {
        echo "[${env.STAGE_NAME}] Skipping stage due to document only file changes"
        return true
    }
    if (!paramsValue("CI_${param_size}_TEST", true)) {
        echo "[${env.STAGE_NAME}] Skipping stage due to CI_${param_size}_TEST parameter"
        return true
    }
    if (env.DAOS_STACK_CI_HARDWARE_SKIP == 'true' ) {
        echo "[${env.STAGE_NAME}] Skipping stage due to DAOS_STACK_CI_HARDWARE_SKIP parameter"
        return true
    }
    for (skip_pragma in skip_pragmas) {
        if (cachedCommitPragma(skip_pragma, 'false').toLowerCase() == 'true') {
            echo "[${env.STAGE_NAME}] Skipping stage due to ${skip_pragma} commit pragma"
            return true
        }
    }
    /* groovylint-disable-next-line UnnecessaryGetter */
    if (isPr() && !run_if_pr) {
        echo "[${env.STAGE_NAME}] Skipping stage in PR build (override with '${override_pragmas[0]}: false')"
        return true
    }

    // Otherwise run the stage
    echo "[${env.STAGE_NAME}] Running the stage"
    return false
}
