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
    println "TRACE: cachedCommitPragma('Skip-' + stage).toLowerCase() = " + cachedCommitPragma('Skip-' + stage).toLowerCase()
    return cachedCommitPragma('Skip-' + stage).toLowerCase() == 'false'
}

boolean is_pr() {

    env.each{entry -> println "TRACE: $entry.key: $entry.value"}

    return env.CHANGE_ID
}

boolean skip_ftest(String distro, String target_branch) {

    println "TRACE: run_default_skipped_stage('func-test-' + distro) = " + run_default_skipped_stage('func-test-' + distro)

    if (run_default_skipped_stage('func-test-' + distro)) {
        // Forced to run due to a (Skip) pragma set to false
        return false
    }
    println "TRACE: distro = " + distro
    println "TRACE: skip_stage_pragma('func-test') = " + skip_stage_pragma('func-test')
    println "TRACE: skip_stage_pragma('func-test-vm') = " + skip_stage_pragma('func-test-vm')
    println "TRACE: testsInStage() = " + testsInStage()
    println "TRACE: testsInStage() = " + testsInStage()
    println "TRACE: skip_stage_pragma('func-test-' + distro) = " + skip_stage_pragma('func-test-' + distro) 
    println "TRACE: prRepos(distro)= " + prRepos(distro)
    println "TRACE: (docOnlyChange(target_branch) && prRepos(distro) == '') = " + (docOnlyChange(target_branch) && prRepos(distro) == '')
    println "TRACE: is_pr() = " + is_pr()

    return distro == 'ubuntu20' ||
           skip_stage_pragma('func-test') ||
           skip_stage_pragma('func-test-vm') ||
           ! testsInStage() ||
           skip_stage_pragma('func-test-' + distro) ||
           (docOnlyChange(target_branch) &&
           prRepos(distro) == '') ||
           (is_pr() && distro != "el7")
}

boolean skip_ftest_valgrind(String distro, String target_branch) {

    println "TRACE: skip_stage_pragma('func-test-vm-valgrind')= " + skip_stage_pragma('func-test-vm-valgrind')
    println "TRACE: target_branch.startsWith('weekly-testing') = " + target_branch.startsWith('weekly-testing')
    println "TRACE: target_branch = " + target_branch

    return skip_ftest(distro, target_branch) ||
           skip_stage_pragma('func-test-vm-valgrind') ||
           is_pr() ||
           (! is_pr() && ! target_branch.startsWith('weekly-testing'))
}

boolean skip_ftest_hw(String size, String target_branch) {
    return env.DAOS_STACK_CI_HARDWARE_SKIP == 'true' ||
           skip_stage_pragma('func-test') ||
           skip_stage_pragma('func-hw-test-' + size) ||
           ! testsInStage() ||
           (env.BRANCH_NAME == 'master' &&
            ! (startedByTimer() || startedByUser())) ||
           (docOnlyChange(target_branch) &&
            prRepos(hwDistroTarget(size)) == '')
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

boolean skip_build_on_centos7_gcc(String target_branch) {
    return skip_stage_pragma('build-centos7-gcc') ||
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
            return (env.BRANCH_NAME != target_branch) &&
                   skip_stage_pragma('build') ||
                   rpmTestVersion() != ''
        case "Build RPM on Leap 15":
            return target_branch == 'weekly-testing' ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '') ||
                   skip_stage_pragma('build-leap15-rpm')
        case "Build DEB on Ubuntu 20.04":
            return target_branch == 'weekly-testing' ||
                   (docOnlyChange(target_branch) &&
                    prRepos('ubuntu20') == '') ||
                   skip_stage_pragma('build-ubuntu20-rpm')
        case "Build on CentOS 7":
            return skip_build_on_centos7_gcc(target_branch)
        case "Build on CentOS 7 Bullseye":
            return env.NO_CI_TESTING == 'true' ||
                   skip_stage_pragma('bullseye', 'true') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   quickFunctional()
        case "Build on CentOS 7 debug":
            return skip_stage_pragma('build-centos7-gcc-debug') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   quickBuild()
        case "Build on CentOS 7 release":
            return skip_stage_pragma('build-centos7-gcc-release') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   quickBuild()
        case "Build on CentOS 7 with Clang":
        case "Build on CentOS 7 with Clang debug":
            return env.BRANCH_NAME != target_branch ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   quickBuild()
        case "Build on Ubuntu 20.04":
            return env.BRANCH_NAME != target_branch ||
                   (docOnlyChange(target_branch) &&
                    prRepos('ubuntu20') == '') ||
                   quickBuild()
        case "Build on Leap 15 with Clang":
            return env.BRANCH_NAME != target_branch ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '') ||
                   quickBuild()
        case "Build on CentOS 8":
            return skip_stage_pragma('build-centos8-gcc-dev') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos8') == '') ||
                   quickBuild()
        case "Build on Ubuntu 20.04 with Clang":
            return target_branch == 'weekly-testing' ||
                   skip_stage_pragma('build-ubuntu-clang') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('ubuntu20') == '') ||
                   quickBuild()
        case "Build on Leap 15":
            return skip_stage_pragma('build-leap15-gcc') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '') ||
                   quickBuild()
        case "Build on Leap 15 with Intel-C and TARGET_PREFIX":
            return target_branch == 'weekly-testing' ||
                   skip_stage_pragma('build-leap15-icc') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '') ||
                   quickBuild()
        case "Unit Tests":
            return  env.NO_CI_TESTING == 'true' ||
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
            return skip_stage_pragma('unit-test-memcheck')
        case "Unit Test":
            return skip_stage_pragma('unit-test') ||
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
            return skip_stage_pragma('coverity-test') ||
                   quickFunctional() ||
                   docOnlyChange(target_branch) ||
                   skip_stage_pragma('build')
        case "Functional on CentOS 7":
            return skip_ftest('el7', target_branch)
        case "Functional on CentOS 7 with Valgrind":
            return skip_ftest_valgrind('el7', target_branch)
        case "Functional on CentOS 8":
            return skip_ftest('el8', target_branch)
        case "Functional on Leap 15":
            return skip_ftest('leap15', target_branch)
        case "Functional on Ubuntu 20.04":
            return skip_ftest('ubuntu20', target_branch)
        case "Test CentOS 7 RPMs":
            return target_branch == 'weekly-testing' ||
                   skip_stage_pragma('test') ||
                   skip_stage_pragma('test-centos-rpms') ||
                   docOnlyChange(target_branch) ||
                   quickFunctional()
        case "Scan CentOS 7 RPMs":
            return target_branch == 'weekly-testing' ||
                   skip_stage_pragma('scan-centos-rpms', 'true') ||
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
