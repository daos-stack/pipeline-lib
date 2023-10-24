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
 *      run_if_pr       whether or not the stage should run for PR builds
 *      run_if_landing  whether or not the stage should run for landing builds
 * @return a String reason why the stage should be skipped; empty if the stage should run
 */
Map call(Map kwargs = [:]) {
    String tags = kwargs['tags'] ?: parseStageInfo()['test_tag']
    String pragma_suffix = kwargs['pragma_suffix'] ?: 'vm'
    String size = pragma_suffix.replace('-hw-', '')
    String distro = kwargs['distro'] ?: hwDistroTarget(size)
    String build_param = "CI_${size.replace('-', '_')}_TEST"
    String build_param_value = paramsValue(build_param, '').toString()
    Boolean run_if_landing = kwargs['run_if_landing'] ?: false
    Boolean run_if_pr = kwargs['run_if_pr'] ?: false
    String target_branch = env.CHANGE_TARGET ? env.CHANGE_TARGET : env.BRANCH_NAME

    echo "[${env.STAGE_NAME}] Running skipFunctionalTestStage: " +
         "tags=${tags}, pragma_suffix=${pragma_suffix}, size=${size}, distro=${distro}, " +
         "build_param=${build_param}, build_param_value=${build_param_value}, " +
         "run_if_landing=${run_if_landing}, run_if_pr=${run_if_pr}, " +
         "startedByUser()=${startedByUser()}, startedByTimer()=${startedByTimer()}, " +
         "startedByUpstream()=${startedByUpstream()}, target_branch=${target_branch}"

    // Regardless of how the stage has been started always skip a stage that has either already
    // passed or does not contain any tests match the tags.
    if (stageAlreadyPassed()) {
        echo "[${env.STAGE_NAME}] Skipping the stage due to all tests passing in the previous build"
        return true
    }
    if (!testsInStage(tags)) {
        echo "[${env.STAGE_NAME}] Skipping the stage due to detecting no tests matching the '${tags}' tags"
        return true
    }

    // If the stage has been started by the user, e.g. Build with Parameters, or a timer, or an
    // upstream build then use the stage's build parameter (check box) to determine if the stage
    // should be run or skipped.
    if (startedByUser() && (build_param_value == 'false')) {
        echo "[${env.STAGE_NAME}] Skipping the stage in user started build due to ${build_param} param"
        return true
    }
    if (startedByUser() && (build_param_value == 'true')) {
        echo "[${env.STAGE_NAME}] Running the stage in user started build due to ${build_param} param"
        return false
    }
    if (startedByTimer() && (build_param_value == 'false')) {
        echo "[${env.STAGE_NAME}] Skipping the stage in timer started build due to ${build_param} param"
        return true
    }
    if (startedByTimer() && (build_param_value == 'true')) {
        echo "[${env.STAGE_NAME}] Running the stage in timer started build due to ${build_param} param"
        return false
    }
    if (startedByUpstream() && (build_param_value == 'false')) {
        echo "[${env.STAGE_NAME}] Skipping the stage in an upstream build due to ${build_param} param"
        return true
    }
    if (startedByUpstream() && (build_param_value == 'true')) {
        echo "[${env.STAGE_NAME}] Running the stage in an upstream build due to ${build_param} param"
        return false
    }

    // If the stage is being run in a landing build use the 'run_if_landing' input to determine
    // whether the stage should be run or skipped.
    if (startedByLanding() && !run_if_landing) {
        echo "[${env.STAGE_NAME}] Skipping the stage in a landing build on master/release branch"
        return true
    }
    if (startedByLanding() && run_if_landing) {
        echo "[${env.STAGE_NAME}] Running the stage in a landing build on master/release branch"
        return false
    }

    // If the stage is being run in a build started by a commit, first use any set commit pragma to
    // determine if the stage should be skipped.
    List skip_pragmas = ["Skip-build-${distro}-rpm", 'Skip-test', 'Skip-func-test', 'Run-GHA']
    List commit_pragmas = []
    if (pragma_suffix.contains('-hw')) {
        commit_pragmas.add("Skip-func-test-hw-${size}")
        commit_pragmas.add("Skip-func-hw-test-${size}")
        commit_pragmas.add('Skip-func-test-hw')
        commit_pragmas.add('Skip-func-hw-test')
        commit_pragmas.add('Run-daily-stages')
    } else {
        commit_pragmas.add("Skip-func-test-vm-${distro}")
        commit_pragmas.add("Skip-func-test-${distro}")
        commit_pragmas.add('Skip-func-test-vm')
        commit_pragmas.add('Skip-func-test-vm-all')
    }
    for (commit_pragma in commit_pragmas + skip_pragmas) {
        String value = 'true' ? (commit_pragma.startsWith('Skip-') || (commit_pragma == 'Run-GHA')) : 'false'
        if (env.UNIT_TEST && env.UNIT_TEST == 'true') {
            echo "[${env.STAGE_NAME}] Checking if ${commit_pragma} == ${value}"
        }
        if (cachedCommitPragma(commit_pragma, '').toLowerCase() == value) {
            echo "[${env.STAGE_NAME}] Skipping the stage in commit build due to '${commit_pragma}: ${value}' commit pragma"
            return true
        }
    }

    // If the stage is being run in a build started by a commit, next use any set commit pragma to
    // determine if the stage should be run.
    for (commit_pragma in commit_pragmas) {
        String value = 'false' ? commit_pragma.startsWith('Skip-') : 'true'
        if (env.UNIT_TEST && env.UNIT_TEST == 'true') {
            echo "[${env.STAGE_NAME}] Checking if ${commit_pragma} == ${value}"
        }
        if (cachedCommitPragma(commit_pragma, '').toLowerCase() == value) {
            echo "[${env.STAGE_NAME}] Running the stage in commit build due to '${commit_pragma}: ${value}' commit pragma"
            return false
        }
    }

    // If the stage is being run in a build started by a commit with only documentation changes,
    // skip the stage.
    if (docOnlyChange(target_branch) && prRepos(distro) == '') {
        echo "[${env.STAGE_NAME}] Skipping the stage in commit build due to document only file changes"
        return true
    }

    // If the stage is being run in a build started by a commit, skip the build if
    // DAOS_STACK_CI_HARDWARE_SKIP is set.
    if (env.DAOS_STACK_CI_HARDWARE_SKIP == 'true' ) {
        echo "[${env.STAGE_NAME}] Skipping the stage in commit build due to DAOS_STACK_CI_HARDWARE_SKIP parameter"
        return true
    }

    // If the stage is being run in a PR build started by a commit, skip the stage if 'run_if_pr' is
    // not set.
    /* groovylint-disable-next-line UnnecessaryGetter */
    if (isPr() && !run_if_pr) {
        echo "[${env.STAGE_NAME}] Skipping the stage in commit PR build (override with '${commit_pragmas[0]}: false')"
        return true
    }

    // If the stage is being run in a build started by a commit skip, finally use the stage's
    // build parameter to determine if the stage should be skipped.
    if (build_param_value == 'false') {
        echo "[${env.STAGE_NAME}] Skipping the stage in commit build due to ${build_param} param"
        return true
    }

    // Otherwise run the stage
    echo "[${env.STAGE_NAME}] Running the stage in a commit build"
    return false
}
