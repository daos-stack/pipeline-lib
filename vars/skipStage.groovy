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

boolean skip_build_on_landing_branch(String target_branch) {
    if (cachedCommitPragma('Run-landing-stages') == 'true' ||
        cachedCommitPragma('Run-daily-stages') == 'true') {
        return false
    }
    return env.BRANCH_NAME != target_branch ||
           quickbuild()
}

boolean is_pr() {
    if (params.CI_MORE_FUNCTIONAL_PR_TESTS) {
        return false
    }
    if (cachedCommitPragma('Run-landing-stages') == 'true') {
        return false
    }
    return env.CHANGE_ID
}

boolean skip_scan_rpms(String distro, String target_branch) {
    return target_branch == 'weekly-testing' ||
           skip_stage_pragma('scan-rpms', 'true') ||
           (distro == 'centos-7' &&
            (! paramsValue('CI_SCAN_RPMS_el7_TEST', true)) ||
            skip_stage_pragma('scan-centos-rpms')) ||
           skip_stage_pragma('scan-' + distro + '-rpms') ||
           docOnlyChange(target_branch) ||
           quickFunctional()
}

boolean skip_ftest(String distro, String target_branch) {
    // Defaults for skipped stages and pragmas to override them
    // must be checked first before parameters are checked
    // because the defaults are based on which branch
    // is being run.
    if (run_default_skipped_stage('func-test-' + distro) ||
        run_default_skipped_stage('func-test-vm-all')) {
        // Forced to run due to a (Skip) pragma set to false
        return false
    }
    // If a parameter exists to enable a build, then use it.
    // The params.CI_MORE_FUNCTIONAL_PR_TESTS allows enabling
    // tests that are not run in PRs.
    params_value = ! paramsValue('CI_FUNCTIONAL_' + distro + '_TEST', true)
    return params_value ||
           distro == 'ubuntu20' ||
           skip_stage_pragma('func-test') ||
           skip_stage_pragma('func-test-vm') ||
           skip_stage_pragma('func-test-vm-all') ||
           ! testsInStage() ||
           skip_stage_pragma('func-test-' + distro) ||
           (docOnlyChange(target_branch) &&
            prRepos(distro) == '') ||
           (is_pr() && distro != "el8")
}

boolean skip_ftest_valgrind(String distro, String target_branch) {
    if (! skip_stage_pragma('func-test-vm-valgrind', 'true')) {
        return false
    }

    return skip_ftest(distro, target_branch) ||
           is_pr() ||
           target_branch.startsWith('weekly-testing')
}

boolean skip_ftest_hw(String size, String target_branch) {
    return env.DAOS_STACK_CI_HARDWARE_SKIP == 'true' ||
           ! paramsValue('CI_' + size + '_TEST', true) ||
           skip_stage_pragma('func-test') ||
           skip_stage_pragma('func-hw-test-' + size) ||
           ! testsInStage() ||
           ((env.BRANCH_NAME == 'master' ||
             env.BRANCH_NAME.startsWith('release/')) &&
            ! (startedByTimer() || startedByUser())) ||
           cachedCommitPragma('Run-daily-stages') == 'true' ||
           (docOnlyChange(target_branch) &&
            prRepos(hwDistroTarget(size)) == '')
}

boolean skip_if_unstable() {
    if (paramsValue('CI_ALLOW_UNSTABLE_TEST', false) ||
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

boolean skip_build_on_centos_gcc(String target_branch, String version) {
    return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
           skip_stage_pragma('build-centos' + version + '-gcc') ||
           (docOnlyChange(target_branch) &&
            prRepos('centos' + version) == '') ||
           quickFunctional()
}

boolean skip_build_bullseye(String target_branch, String distro) {
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   env.NO_CI_TESTING == 'true' ||
                   skip_stage_pragma('bullseye', 'true') ||
                   (docOnlyChange(target_branch) &&
                    prRepos(distro) == '') ||
                   quickFunctional()

}

boolean call(Map config = [:]) {
    if (config['stage']) {
        return skip_stage_pragma(config['stage'], config['def_val'])
    }

    String target_branch = env.CHANGE_TARGET ? env.CHANGE_TARGET : env.BRANCH_NAME

    switch(env.STAGE_NAME) {
        case "Pre-build":
            return docOnlyChange(target_branch) ||
                   target_branch == 'weekly-testing' ||
                   rpmTestVersion() != '' ||
                   quickBuild()
        case "checkpatch":
            return skip_stage_pragma('checkpatch')
        case "Python Bandit check":
            return skip_stage_pragma('python-bandit')
        case "Build":
            // always build branch landings as we depend on lastSuccessfulBuild
            // always having RPMs in it
            return (env.BRANCH_NAME != target_branch) &&
                   skip_stage_pragma('build') ||
                   rpmTestVersion() != ''
        case "Build RPM on CentOS 7":
            return paramsValue('CI_RPM_centos7_NOBUILD', false) ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   skip_stage_pragma('build-centos7-rpm')
        case "Build RPM on CentOS 8":
            return paramsValue('CI_RPM_centos8_NOBUILD', false) ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos8') == '') ||
                   skip_stage_pragma('build-centos8-rpm')
        case "Build RPM on Leap 15":
            return paramsValue('CI_RPM_leap15_NOBUILD', false) ||
                   target_branch == 'weekly-testing' ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '') ||
                   skip_stage_pragma('build-leap15-rpm')
        case "Build DEB on Ubuntu 20.04":
            return paramsValue('CI_RPM_ubuntu20_NOBUILD', false) ||
                   target_branch == 'weekly-testing' ||
                   (docOnlyChange(target_branch) &&
                    prRepos('ubuntu20') == '') ||
                   skip_stage_pragma('build-ubuntu20-rpm')
        case "Build on CentOS 8":
            return skip_build_on_centos_gcc(target_branch, '8')
        case "Build on CentOS 7 Bullseye":
            return skip_build_bullseye(target_branch, 'centos7')
        case "Build on CentOS 8 Bullseye":
            return skip_build_bullseye(target_branch, 'centos8')
        case "Build on CentOS 7 debug":
            if (run_default_skipped_stage('build-centos7-gcc-debug')) {
                return false
            }
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   quickBuild() 
        case "Build on CentOS 8 debug":
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_stage_pragma('build-centos7-gcc-debug') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos8') == '') ||
                   quickBuild()
        case "Build on CentOS 7 release":
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_stage_pragma('build-centos7-gcc-release', 'true') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   quickBuild()
        case "Build on CentOS 8 release":
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_stage_pragma('build-centos8-gcc-release', 'true') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos8') == '') ||
                   quickBuild()
        case "Build on CentOS 7 with Clang":
        case "Build on CentOS 7 with Clang debug":
            if (run_default_skipped_stage('build-centos7-clang-debug')) {
                return false
            }
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_build_on_landing_branch(target_branch) ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '')
        case "Build on CentOS 8 with Clang":
        case "Build on CentOS 8 with Clang debug":
            if (run_default_skipped_stage('build-centos8-clang-debug')) {
                return false
            }
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_build_on_landing_branch(target_branch) ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos8') == '')
        case "Build on Ubuntu 20.04":
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_build_on_landing_branch(target_branch) ||
                   (docOnlyChange(target_branch) &&
                    prRepos('ubuntu20') == '')
        case "Build on Leap 15 with Clang":
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_build_on_landing_branch(target_branch) ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '')
        case "Build on CentOS 8":
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_stage_pragma('build-centos8-gcc-dev') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos8') == '') ||
                   quickBuild()
        case "Build on Ubuntu 20.04 with Clang":
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   target_branch == 'weekly-testing' ||
                   skip_stage_pragma('build-ubuntu-clang') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('ubuntu20') == '') ||
                   quickBuild()
        case "Build on Leap 15":
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_stage_pragma('build-leap15-gcc') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '') ||
                   quickBuild()
        case "Build on Leap 15 with Intel-C and TARGET_PREFIX":
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   target_branch == 'weekly-testing' ||
                   skip_stage_pragma('build-leap15-icc') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '') ||
                   quickBuild()
        case "Unit Tests":
            return  env.NO_CI_TESTING == 'true' ||
                    paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                    skip_stage_pragma('build') ||
                    rpmTestVersion() != '' ||
                    docOnlyChange(target_branch) ||
                    skip_build_on_centos_gcc(target_branch, '8') ||
                    skip_stage_pragma('unit-tests')
        case "NLT":
        case "NLT on CentOS 8":
            return skip_stage_pragma('nlt')
        case "Unit Test Bullseye":
        case "Unit Test Bullseye on CentOS 8":
            return skip_stage_pragma('bullseye', 'true')
        case "Unit Test with memcheck on CentOS 8":
        case "Unit Test with memcheck":
            return ! paramsValue('CI_UNIT_TEST_MEMCHECK', true) ||
                   skip_stage_pragma('unit-test-memcheck')
        case "Unit Test":
        case "Unit Test on CentOS 8":
            return ! paramsValue('CI_UNIT_TEST', true) ||
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
        case "Coverity on CentOS 8":
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   rpmTestVersion() != '' ||
                   skip_stage_pragma('coverity-test') ||
                   quickFunctional() ||
                   docOnlyChange(target_branch) ||
                   skip_stage_pragma('build')
        case "Functional on CentOS 7":
            return skip_ftest('el7', target_branch)
        case "Functional on CentOS 7 with Valgrind":
            return skip_ftest_valgrind('el7', target_branch)
        case "Functional on CentOS 8 with Valgrind":
            return skip_ftest_valgrind('el8', target_branch)
        case "Functional on CentOS 8":
            return skip_ftest('el8', target_branch)
        case "Functional on Leap 15":
            return skip_ftest('leap15', target_branch)
        case "Functional on Ubuntu 20.04":
            /* we don't do any testing on Ubuntu yet
            skip_ftest('ubuntu20', target_branch) */
            return true
        case "Fault injection testing":
        case "Fault injection testing on CentOS 8":
            return skip_stage_pragma('fault-injection-test') ||
                   quickFunctional() ||
                   docOnlyChange(target_branch) ||
                   skip_stage_pragma('func-test') ||
                   skip_stage_pragma('func-test-vm')
        case "Test CentOS 7 RPMs":
            return ! paramsValue('CI_RPMS_el7_TEST', true) ||
                   target_branch == 'weekly-testing' ||
                   skip_stage_pragma('test') ||
                   skip_stage_pragma('test-rpms') ||
                   skip_stage_pragma('test-centos-rpms') ||
                   skip_stage_pragma('test-centos-7-rpms') ||
                   docOnlyChange(target_branch) ||
                   (quickFunctional() &&
                    ! run_default_skipped_stage('test-centos-7-rpms'))
        case "Test CentOS 8.3.2011 RPMs":
            return ! paramsValue('CI_RPMS_el8.3.2011_TEST', true) ||
                   target_branch == 'weekly-testing' ||
                   skip_stage_pragma('test') ||
                   skip_stage_pragma('test-rpms') ||
                   skip_stage_pragma('test-centos-8.3-rpms') ||
                   docOnlyChange(target_branch) ||
                   (quickFunctional() &&
                    ! run_default_skipped_stage('test-centos-8.3-rpms'))
        case "Test Leap 15 RPMs":
        case "Test Leap 15.2 RPMs":
            // Skip by default as it doesn't pass with Leap15.3 due to
            // requiring a newer glibc
            return ! paramsValue('CI_RPMS_leap15_TEST', true) ||
                   skip_stage_pragma('test-leap-15-rpms', 'true')
        case "Scan CentOS 7 RPMs":
            return skip_scan_rpms('centos-7', target_branch)
        case "Scan CentOS 8 RPMs":
            return skip_scan_rpms('centos-8', target_branch)
        case "Scan Leap 15 RPMs":
            return skip_scan_rpms('leap-15', target_branch)
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
            println("Don't know how to skip stage \"${env.STAGE_NAME}\", not skipping")
    }
}
