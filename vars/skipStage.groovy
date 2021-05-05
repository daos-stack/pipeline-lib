// vars/skipStage.groovy

/**
 * skipStage.groovy
 *
 * skipStage variable
 */

/**
 * Method to return true or false if a commit pragma says to skip a stage
 */

String skip_stage_pragma(String stage, String def_val='false') {
    return cachedCommitPragma('Skip-' + stage, def_val).toLowerCase() == 'true'
}

boolean skip_ftest(String distro) {
    return distro == 'ubuntu20' ||
           skip_stage_pragma('func-test') ||
           skip_stage_pragma('func-test-vm') ||
           ! testsInStage() ||
           skip_stage_pragma('func-test-' + distro)
}

boolean skip_ftest_hw(String size) {
    return env.DAOS_STACK_CI_HARDWARE_SKIP == 'true' ||
           skip_stage_pragma('func-test') ||
           skip_stage_pragma('func-hw-test-' + size) ||
           ! testsInStage() ||
           (env.BRANCH_NAME == 'master' &&
            ! (startedByTimer() || startedByUser()))
}

boolean skip_if_unstable() {
    if (cachedCommitPragma('Allow-unstable-test').toLowerCase() == 'true' ||
        env.BRANCH_NAME == 'master' ||
        env.BRANCH_NAME.startsWith("weekly-testing") ||
        env.BRANCH_NAME.startsWith("release/")) {
        return false
    }

    //Ok, it's a PR and the Allow pragma isn't set.  Skip if the build is
    //unstable.

    return currentBuild.currentResult == 'UNSTABLE'
}

boolean skip_build_on_centos7_gcc() {
    return skip_stage_pragma('build-centos7-gcc') ||
           quickFunctional()
}

boolean tests_in_stage(String size) {
    Map stage_info = parseStageInfo()
    return sh(label: "Get test list for ${size}",
              script: """cd src/tests/ftest
                         ./list_tests.py """ + stage_info['test_tag'],
              returnStatus: true) == 0
}

boolean call(Map config = [:]) {
    if (config['stage']) {
        return skip_stage_pragma(config['stage'], config['def_val'])
    }

    String target_branch = env.CHANGE_TARGET ? env.CHANGE_TARGET : env.BRANCH_NAME

    switch(env.STAGE_NAME) {
        case "Pre-build":
            return target_branch == 'weekly-testing'
        case "checkpatch":
            String skip = skip_stage_pragma('checkpatch')
            return skip == 'true' ||
                   (skip != 'false' &&
                    (docOnlyChange(target_branch) || quickFunctional()))
        case "Python Bandit check":
            String skip = skip_stage_pragma('python-bandit') ||
            return skip == 'true' ||
                   (skip != 'false' &&
                    (docOnlyChange(target_branch) || quickFunctional()))
        case "Build":
            // always build branch landings as we depend on lastSuccessfulBuild
            // always having RPMs in it
            return (env.BRANCH_NAME != target_branch) &&
                   skip_stage_pragma('build') ||
                   docOnlyChange(target_branch) ||
                   rpmTestVersion() != ''
        case "Build RPM on Leap 15":
            return target_branch == 'weekly-testing' ||
                   skip_stage_pragma('build-leap15-rpm')
        case "Build DEB on Ubuntu 20.04":
            return target_branch == 'weekly-testing' ||
                   skip_stage_pragma('build-ubuntu20-rpm')
        case "Build on CentOS 7":
            return skip_build_on_centos7_gcc()
        case "Build on CentOS 7 Bullseye":
            return  env.NO_CI_TESTING == 'true' ||
                    skip_stage_pragma('bullseye', 'true') ||
                    quickFunctional()
        case "Build on CentOS 7 debug":
            return skip_stage_pragma('build-centos7-gcc-debug') ||
                   quickBuild()
        case "Build on CentOS 7 release":
            return skip_stage_pragma('build-centos7-gcc-release') ||
                   quickBuild()
        case "Build on CentOS 7 with Clang":
        case "Build on CentOS 7 with Clang debug":
        case "Build on Ubuntu 20.04":
        case "Build on Leap 15 with Clang":
            return env.BRANCH_NAME != target_branch ||
                   quickBuild()
        case "Build on CentOS 8":
            return skip_stage_pragma('build-centos8-gcc-dev') ||
                   quickBuild()
        case "Build on Ubuntu 20.04 with Clang":
            return target_branch == 'weekly-testing' ||
                   skip_stage_pragma('build-ubuntu-clang') ||
                   quickBuild()
        case "Build on Leap 15":
            return skip_stage_pragma('build-leap15-gcc') ||
                   quickBuild()
        case "Build on Leap 15 with Intel-C and TARGET_PREFIX":
            return target_branch == 'weekly-testing' ||
                   skip_stage_pragma('build-leap15-icc') ||
                   quickBuild()
        case "Unit Tests":
            return  env.NO_CI_TESTING == 'true' ||
                    (skip_stage_pragma('build') &&
                     rpmTestVersion() == '') ||
                    docOnlyChange(target_branch) ||
                    skip_build_on_centos7_gcc() ||
                    skip_stage_pragma('unit-tests')
        case "NLT":
            return skip_stage_pragma('nlt')
        case "Unit Test Bullseye":
            return skip_stage_pragma('bullseye', 'true')
        case "Unit Test with memcheck":
            return skip_stage_pragma('unit-test-memcheck')
        case "Unit Test":
            return skip_stage_pragma('unit-test') ||
                   skip_stage_pragma('run_test')
        case "Test":
            return env.NO_CI_TESTING == 'true' ||
                   (skip_stage_pragma('build') &&
                    rpmTestVersion() == '') ||
                   docOnlyChange(target_branch) ||
                   skip_stage_pragma('test') ||
                   (env.BRANCH_NAME.startsWith('weekly-testing') &&
                    ! startedByTimer() &&
                    ! startedByUser()) ||
                   skip_if_unstable()
        case "Test Hardware":
            return env.NO_CI_TESTING == 'true' ||
                   skip_stage_pragma('func-test') ||
                   skip_stage_pragma('func-hw-test') ||
                   (skip_stage_pragma('build') &&
                    rpmTestVersion() == '') ||
                   docOnlyChange(target_branch) ||
                   skip_stage_pragma('test') ||
                   (env.BRANCH_NAME.startsWith('weekly-testing') &&
                    ! startedByTimer() &&
                    ! startedByUser()) ||
                   skip_if_unstable()
        case "Test on CentOS 7 [in] Vagrant":
            return skip_stage_pragma('vagrant-test', 'true') &&
                   ! env.BRANCH_NAME.startsWith('weekly-testing')
        case "Coverity on CentOS 7":
            return skip_stage_pragma('coverity-test') ||
                   quickFunctional() ||
                   skip_stage_pragma('build')
        case "Functional on CentOS 7":
            return skip_ftest('el7')
        case "Functional on CentOS 8":
            return skip_ftest('el8')
        case "Functional on Leap 15":
            return skip_ftest('leap15')
        case "Functional on Ubuntu 20.04":
            return skip_ftest('ubuntu20') 
        case "Test CentOS 7 RPMs":
            return target_branch == 'weekly-testing' ||
                   skip_stage_pragma('test') ||
                   skip_stage_pragma('test-centos-rpms') ||
                   quickFunctional()
        case "Scan CentOS 7 RPMs":
            return target_branch == 'weekly-testing' ||
                   skip_stage_pragma('scan-centos-rpms', 'true') ||
                   quickFunctional()
        case "Functional_Hardware_Small":
        case "Functional Hardware Small":
            return skip_ftest_hw('small')
        case "Functional_Hardware_Medium":
        case "Functional Hardware Medium":
            return skip_ftest_hw('medium')
        case "Functional_Hardware_Large":
        case "Functional Hardware Large":
            return skip_ftest_hw('large')
        case "Bullseye Report":
            return env.BULLSEYE == null ||
                   skip_stage_pragma('bullseye', 'true')
        default:
            error("Don't know how to skip stage \"${env.STAGE_NAME}\"")
    }
}
