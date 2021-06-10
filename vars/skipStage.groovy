// vars/skipStage.groovy

/**
 * skipStage.groovy
 *
 * skipStage variable
 */

/**
 * Method to return true or false if a commit pragma says to skip a stage
 */

// Determine if a stage has been specified to skip with a commit pragma
String skip_stage_pragma(String stage, String def_val='false') {
    return cachedCommitPragma('Skip-' + stage, def_val).toLowerCase() == 'true'
}

// Determine if a stage that defaults to being skipped has been forced to run
// (i.e. due to a commit pragma)
String run_default_skipped_stage(String stage) {
    return cachedCommitPragma('Skip-' + stage).toLowerCase() == 'false'
}

boolean is_pr() {
    if (params.CI_MORE_FUNCTIONAL_PR_TESTS) {
        return false
    }
    return env.CHANGE_ID
}

boolean skip_ftest(String distro, String target_branch) {
    // Pragmas have highest priority
    if (run_default_skipped_stage('func-test-' + distro)) {
        // Forced to run due to a (Skip) pragma set to false
        return false
    }
    // If a parameter exists, it is the next priority.
    boolean ci_func_distro_test =
      params.containsKey('CI_FUNCTIONAL_' + distro + '_TEST')
    boolean params_value = false
    if (ci_func_distro_test) {
        params_value = ! params['CI_FUNCTIONAL_' + distro + '_TEST']
    }
    return params_value ||
           distro == 'ubuntu20' ||
           skip_stage_pragma('func-test') ||
           skip_stage_pragma('func-test-vm') ||
           ! testsInStage() ||
           skip_stage_pragma('func-test-' + distro) ||
           (docOnlyChange(target_branch) &&
            prRepos(distro) == '') ||
           (is_pr() && distro != "el7")
}

boolean skip_ftest_hw(String size, String target_branch) {
    boolean ci_size_test = params.containsKey('CI_' + size + '_TEST')
    boolean params_value = false
    if (ci_size_test) {
        params_value = ! params['CI_' + size + '_TEST']
    }
    return env.DAOS_STACK_CI_HARDWARE_SKIP == 'true' ||
           params_value ||
           skip_stage_pragma('func-test') ||
           skip_stage_pragma('func-hw-test-' + size) ||
           ! testsInStage() ||
           (env.BRANCH_NAME == 'master' &&
            ! (startedByTimer() || startedByUser())) ||
           (docOnlyChange(target_branch) &&
            prRepos(hwDistroTarget(size)) == '')
}

boolean skip_if_unstable() {
    if (params.CI_ALLOW_UNSTABLE_TEST) {
        println('skip if unstable - params.CI_ALLOW_UNSTABLE_TEST is true')
    } else {
        println('skip if unstable - params.CI_ALLOW_UNSTABLE_TEST is true')
    }
    if (params.CI_ALLOW_UNSTABLE_TEST ||
        cachedCommitPragma('Allow-unstable-test').toLowerCase() == 'true' ||
        env.BRANCH_NAME == 'master' ||
        env.BRANCH_NAME.startsWith("weekly-testing") ||
        env.BRANCH_NAME.startsWith("release/")) {
        return false
    }

    //Ok, it's a PR and the Allow pragma isn't set.  Skip if the build is
    //unstable.

    return currentBuild.currentResult == 'UNSTABLE'
}

boolean skip_build_on_centos7_gcc(String target_branch) {
    return params.CI_BUILD_PACKAGES_ONLY ||
           skip_stage_pragma('build-centos7-gcc') ||
           (docOnlyChange(target_branch) &&
            prRepos('centos7') == '') ||
           quickFunctional()
}

boolean call(Map config = [:]) {
    if (config['stage']) {
        return skip_stage_pragma(config['stage'], config['def_val'])
    }

    String target_branch = env.CHANGE_TARGET ? env.CHANGE_TARGET : env.BRANCH_NAME

    switch(env.STAGE_NAME) {
        case "Pre-build":
            return target_branch == 'weekly-testing' ||
                   quickBuild()
        case "checkpatch":
            String skip = skip_stage_pragma('checkpatch', '')
            return skip == 'true' ||
                   (skip != 'false' &&
                    docOnlyChange(target_branch))
        case "Python Bandit check":
            String skip = skip_stage_pragma('python-bandit', '')
            return skip == 'true' ||
                   (skip != 'false' &&
                    docOnlyChange(target_branch))
        case "Build":
            // always build branch landings as we depend on lastSuccessfulBuild
            // always having RPMs in it
            return params.CI_NOBUILD ||
                   (env.BRANCH_NAME != target_branch) &&
                   skip_stage_pragma('build') ||
                   rpmTestVersion() != ''
        case "Build RPM on CentOS 7":
            return params.CI_RPM_centos7_NOBUILD ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   skip_stage_pragma('build-centos7-rpm')
        case "Build RPM on CentOS 8":
            return params.CI_RPM_centos8_NOBUILD ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos8') == '') ||
                   skip_stage_pragma('build-centos7-rpm')
        case "Build RPM on Leap 15":
            return params.CI_RPM_leap15_NOBUILD ||
                   target_branch == 'weekly-testing' ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '') ||
                   skip_stage_pragma('build-leap15-rpm')
        case "Build DEB on Ubuntu 20.04":
            return params.CI_RPM_ubuntu20_NOBUILD ||
                   target_branch == 'weekly-testing' ||
                   (docOnlyChange(target_branch) &&
                    prRepos('ubuntu20') == '') ||
                   skip_stage_pragma('build-ubuntu20-rpm')
        case "Build on CentOS 7":
            return skip_build_on_centos7_gcc(target_branch)
        case "Build on CentOS 7 Bullseye":
            return params.CI_BUILD_PACKAGES_ONLY ||
                   env.NO_CI_TESTING == 'true' ||
                   skip_stage_pragma('bullseye', 'true') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   quickFunctional()
        case "Build on CentOS 7 debug":
            return params.CI_BUILD_PACKAGES_ONLY ||
                   skip_stage_pragma('build-centos7-gcc-debug') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   quickBuild()
        case "Build on CentOS 7 release":
            return params.CI_BUILD_PACKAGES_ONLY ||
                   skip_stage_pragma('build-centos7-gcc-release') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   quickBuild()
        case "Build on CentOS 7 with Clang":
        case "Build on CentOS 7 with Clang debug":
            return params.CI_BUILD_PACKAGES_ONLY ||
                   env.BRANCH_NAME != target_branch ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   quickBuild()
        case "Build on Ubuntu 20.04":
            return params.CI_BUILD_PACKAGES_ONLY ||
                   env.BRANCH_NAME != target_branch ||
                   (docOnlyChange(target_branch) &&
                    prRepos('ubuntu20') == '') ||
                   quickBuild()
        case "Build on Leap 15 with Clang":
            return params.CI_BUILD_PACKAGES_ONLY ||
                   env.BRANCH_NAME != target_branch ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '') ||
                   quickBuild()
        case "Build on CentOS 8":
            return params.CI_BUILD_PACKAGES_ONLY ||
                   skip_stage_pragma('build-centos8-gcc-dev') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos8') == '') ||
                   quickBuild()
        case "Build on Ubuntu 20.04 with Clang":
            return params.CI_BUILD_PACKAGES_ONLY ||
                   target_branch == 'weekly-testing' ||
                   skip_stage_pragma('build-ubuntu-clang') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('ubuntu20') == '') ||
                   quickBuild()
        case "Build on Leap 15":
            return params.CI_BUILD_PACKAGES_ONLY ||
                   skip_stage_pragma('build-leap15-gcc') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '') ||
                   quickBuild()
        case "Build on Leap 15 with Intel-C and TARGET_PREFIX":
            return params.CI_BUILD_PACKAGES_ONLY ||
                   target_branch == 'weekly-testing' ||
                   skip_stage_pragma('build-leap15-icc') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '') ||
                   quickBuild()
        case "Unit Tests":
            return  env.NO_CI_TESTING == 'true' ||
                    params.CI_BUILD_PACKAGES_ONLY ||
                    params.CI_NOBUILD ||
                    quickBuild() ||
                    skip_stage_pragma('build') ||
                    rpmTestVersion() != '' ||
                    docOnlyChange(target_branch) ||
                    skip_build_on_centos7_gcc(target_branch) ||
                    skip_stage_pragma('unit-tests')
        case "NLT":
            return skip_stage_pragma('nlt')
        case "Unit Test Bullseye":
            return skip_stage_pragma('bullseye', 'true')
        case "Unit Test with memcheck":
            return ! params.CI_UNIT_TEST_MEMCHECK ||
                   skip_stage_pragma('unit-test-memcheck')
        case "Unit Test":
            return params.CI_UNIT_TEST ||
                   skip_stage_pragma('unit-test') ||
                   skip_stage_pragma('run_test')
        case "Test":
            return env.NO_CI_TESTING == 'true' ||
                   (skip_stage_pragma('build') &&
                    rpmTestVersion() == '') ||
                   skip_stage_pragma('test') ||
                   (env.BRANCH_NAME.startsWith('weekly-testing') &&
                    ! startedByTimer() &&
                    ! startedByUser()) ||
                   skip_if_unstable()
        case "Test on CentOS 7 [in] Vagrant":
            return skip_stage_pragma('vagrant-test', 'true') &&
                   ! env.BRANCH_NAME.startsWith('weekly-testing')
        case "Coverity on CentOS 7":
            return params.CI_BUILD_PACKAGES_ONLY ||
                   params.CI_NOBUILD ||
                   skip_stage_pragma('coverity-test') ||
                   quickFunctional() ||
                   docOnlyChange(target_branch) ||
                   skip_stage_pragma('build')
        case "Functional on CentOS 7":
            return skip_ftest('el7', target_branch)
        case "Functional on CentOS 8":
            return skip_ftest('el8', target_branch)
        case "Functional on Leap 15":
            return skip_ftest('leap15', target_branch)
        case "Functional on Ubuntu 20.04":
            return skip_ftest('ubuntu20', target_branch)
        case "Test CentOS 7 RPMs":
            return ! params.CI_RPMS_el7_TEST ||
                   target_branch == 'weekly-testing' ||
                   skip_stage_pragma('test') ||
                   skip_stage_pragma('test-centos-rpms') ||
                   docOnlyChange(target_branch) ||
                   quickFunctional()
        case "Scan CentOS 7 RPMs":
            return ! params.CI_SCAN_RPMS_el7_TEST ||
                   target_branch == 'weekly-testing' ||
                   skip_stage_pragma('scan-centos-rpms') ||
                   docOnlyChange(target_branch) ||
                   quickFunctional()
        case "Test Hardware":
            return env.NO_CI_TESTING == 'true' ||
                   skip_stage_pragma('func-test') ||
                   skip_stage_pragma('func-hw-test') ||
                   (skip_stage_pragma('build') &&
                    rpmTestVersion() == '') ||
                   skip_stage_pragma('test') ||
                   (env.BRANCH_NAME.startsWith('weekly-testing') &&
                    ! startedByTimer() &&
                    ! startedByUser()) ||
                   skip_if_unstable()
        case "Functional_Hardware_Small":
        case "Functional Hardware Small":
            return skip_ftest_hw('small', target_branch)
        case "Functional_Hardware_Medium":
        case "Functional Hardware Medium":
            return skip_ftest_hw('medium', target_branch)
        case "Functional_Hardware_Large":
        case "Functional Hardware Large":
            return skip_ftest_hw('large', target_branch)
        case "Bullseye Report":
            return env.BULLSEYE == null ||
                   skip_stage_pragma('bullseye', 'true')
        default:
            error("Don't know how to skip stage \"${env.STAGE_NAME}\"")
    }
}
