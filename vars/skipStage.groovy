/* groovylint-disable DuplicateStringLiteral, ParameterName, VariableName */
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
           quickBuild()
}

boolean skip_scan_rpms(String distro, String target_branch) {
    return stageAlreadyPassed() ||
           target_branch =~ branchTypeRE('weekly') ||
           rpmTestVersion() != '' ||
           skip_stage_pragma('scan-rpms') ||
           skip_stage_pragma('scan-' + distro + '-rpms') ||
           (distro == 'centos7' &&
            (!paramsValue('CI_SCAN_RPMS_el7_TEST', true)) ||
            skip_stage_pragma('scan-centos-rpms')) ||
           docOnlyChange(target_branch) ||
           quickFunctional()
}

boolean skip_ftest(String distro, String target_branch, String tags) {
    // Skip the functional vm test stage if it has already passed or
    // there are no tests matching the tags for the stage
    if (stageAlreadyPassed() || !testsInStage(tags) ||
        cachedCommitPragma('Run-GHA').toLowerCase() == 'true') {
        return true
    }
    // Run the functional vm test stage if explicitly requested by the user
    if (run_default_skipped_stage('func-test-' + distro) ||
        run_default_skipped_stage('func-test-vm-all')) {
        // Forced to run due to a (Skip) pragma set to false
        return false
    }
    // If a parameter exists to enable a build, then use it.
    // The params.CI_MORE_FUNCTIONAL_PR_TESTS allows enabling
    // tests that are not run in PRs.
    return !paramsValue('CI_FUNCTIONAL_' + distro + '_TEST', true) ||
           distro in ['ubuntu20'] ||
           skip_stage_pragma('build-' + distro + '-rpm') ||
           skip_stage_pragma('test') ||
           skip_stage_pragma('func-test') ||
           skip_stage_pragma('func-test-vm') ||
           skip_stage_pragma('func-test-vm-all') ||
           skip_stage_pragma('func-test-' + distro) ||
           (docOnlyChange(target_branch) &&
            prRepos(distro) == '') ||
           /* groovylint-disable-next-line UnnecessaryGetter */
           (isPr() && !(distro in ['el8']))
}

boolean skip_ftest_valgrind(String distro, String target_branch, String tags) {
    // Check if the default for skipping this stage been overriden
    // otherwise always skip this stage (DAOS-10585)
    return stageAlreadyPassed() ||
           !run_default_skipped_stage('func-test-vm-valgrind') ||
           !paramsValue('CI_FUNCTIONAL_' + distro + '_VALGRIND_TEST', false) ||
           (skip_ftest(distro, target_branch, tags) &&
            cachedCommitPragma('Run-GHA').toLowerCase() != 'true') ||
           /* groovylint-disable-next-line UnnecessaryGetter */
           isPr() ||
           target_branch =~ branchTypeRE('weekly')
}

boolean skip_ftest_hw(String size, String target_branch, String tags) {
    // Skip the functional hardware test stage if it has already passed or
    // there are no tests matching the tags for the stage
    if (stageAlreadyPassed() || !testsInStage(tags) ||
        cachedCommitPragma('Run-GHA').toLowerCase() == 'true') {
        return true
    }
    // Run the functional hardware test stage if explicitly requested by the user
    if (run_default_skipped_stage('func-hw-test-' + size) ||
        run_default_skipped_stage('func-test-hw-' + size) ||
        cachedCommitPragma('Run-daily-stages').toLowerCase() == 'true') {
        // Forced to run due to a (Skip) pragma set to false
        return false
    }
    String distro = (hwDistroTarget(size) =~ /([a-z]+)(\d+)(\.\d+)?/)[0][1..2].join('')
    return !paramsValue('CI_' + size.replace('-', '_') + '_TEST', true) ||
           env.DAOS_STACK_CI_HARDWARE_SKIP == 'true' ||
           skip_stage_pragma('build-' + distro + '-rpm') ||
           skip_stage_pragma('test') ||
           skip_stage_pragma('func-test') ||
           skip_stage_pragma('func-test-hw') ||
           skip_stage_pragma('func-test-hw-' + size) ||
           skip_stage_pragma('func-hw-test') ||
           skip_stage_pragma('func-hw-test-' + size) ||
           ((env.BRANCH_NAME == 'master' ||
             env.BRANCH_NAME =~ branchTypeRE('release')) &&
            !(startedByTimer() || startedByUser())) ||
           (docOnlyChange(target_branch) &&
            prRepos(distro) == '') ||
           /* groovylint-disable-next-line UnnecessaryGetter */
           (isPr() && size == 'medium-ucx-provider')
}

boolean skip_if_unstable() {
    if (paramsValue('CI_ALLOW_UNSTABLE_TEST', false) ||
        cachedCommitPragma('Allow-unstable-test').toLowerCase() == 'true' ||
        env.BRANCH_NAME == 'master' ||
        env.BRANCH_NAME =~ branchTypeRE('testing') ||
        env.BRANCH_NAME =~ branchTypeRE('release')) {
        return false
    }

    // Ok, it's a PR and the Allow pragma isn't set.  Skip if the build is
    // unstable or worse.
    return currentBuild.resultIsWorseOrEqualTo('UNSTABLE')
}

boolean skip_build_on_el_gcc(String target_branch, String version) {
    return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
           skip_stage_pragma('build-el' + version + '-gcc') ||
           (docOnlyChange(target_branch) &&
            prRepos('el' + version) == '') ||
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

/* groovylint-disable-next-line MethodSize */
boolean call(Map config = [:]) {
    stageMessage('Determining if the stage should be skipped')
    if (config['stage']) {
        if(skip_stage_pragma(config['stage'], config['def_val'])) {
            stageMessage('Skipping the stage')
            return true
        } else {
            stageMessage('Running the stage')
            return false
        }
    }

    if (stageAlreadyPassed(stage_name: config['stage_name'], postfix: config['axes'])) {
        stageMessage('Skipping an already passed stage')
        return true
    }

    String target_branch = env.CHANGE_TARGET ? env.CHANGE_TARGET : env.BRANCH_NAME
    String tags = config['tags'] ?: parseStageInfo()['test_tag']
    Boolean skip_stage = false

    switch (env.STAGE_NAME) {
        case 'Cancel Previous Builds':
            skip_stage = cachedCommitPragma('Cancel-prev-build') == 'false' ||
                         /* groovylint-disable-next-line UnnecessaryGetter */
                         (!isPr() && !startedByUpstream())
        case 'Check Packaging':
            skip_stage = skip_stage_pragma('packaging-check')
        case 'Lint':
            skip_stage = quickBuild()
        case 'Pre-build':
            skip_stage = docOnlyChange(target_branch) ||
                         target_branch =~ branchTypeRE('weekly') ||
                         rpmTestVersion() != '' ||
                         quickBuild()
        case 'checkpatch':
            skip_stage = skip_stage_pragma('checkpatch')
        case 'Python Bandit check':
            skip_stage = skip_stage_pragma('python-bandit')
        case 'Build':
            // always build branch landings as we depend on lastSuccessfulBuild
            // always having RPMs in it
            skip_stage = (env.BRANCH_NAME != target_branch) &&
                         skip_stage_pragma('build') ||
                         rpmTestVersion() != '' ||
                         (quickFunctional() && prReposContains(null, jobName()))
        case 'Build RPM on CentOS 7':
            skip_stage = paramsValue('CI_RPM_centos7_NOBUILD', false) ||
                         (docOnlyChange(target_branch) && prRepos('centos7') == '') ||
                         prReposContains('centos7', jobName()) ||
                         skip_stage_pragma('build-centos7-rpm')
        case 'Build RPM on EL 8':
        case 'Build RPM on EL 8.5':
        case 'Build RPM on CentOS 8':
            skip_stage = paramsValue('CI_RPM_el8_NOBUILD', false) ||
                         (docOnlyChange(target_branch) && prRepos('el8') == '') ||
                         prReposContains('el8', jobName()) ||
                         skip_stage_pragma('build-el8-rpm')
        case 'Build RPM on EL 9':
            skip_stage = paramsValue('CI_RPM_el9_NOBUILD', false) ||
                         (docOnlyChange(target_branch) && prRepos('el9') == '') ||
                         prReposContains('el9', jobName()) ||
                         skip_stage_pragma('build-el9-rpm')
        case 'Build RPM on Leap 15':
        case 'Build RPM on Leap 15.4':
        case 'Build RPM on Leap 15.5':
            skip_stage = paramsValue('CI_RPM_leap15_NOBUILD', false) ||
                         target_branch =~ branchTypeRE('weekly') ||
                         (docOnlyChange(target_branch) && prRepos('leap15') == '') ||
                         prReposContains('leap15', jobName()) ||
                         skip_stage_pragma('build-leap15-rpm')
        case 'Build DEB on Ubuntu 20.04':
            skip_stage = paramsValue('CI_RPM_ubuntu20_NOBUILD', false) ||
                         target_branch =~ branchTypeRE('weekly') ||
                         (docOnlyChange(target_branch) && prRepos('ubuntu20') == '') ||
                         prReposContains('ubuntu20', jobName()) ||
                         skip_stage_pragma('build-ubuntu20-rpm')
        case 'Build on CentOS 8':
        case 'Build on EL 8':
        case 'Build on EL 8.8':
            skip_stage = skip_build_on_el_gcc(target_branch, '8')
        case 'Build on CentOS 7 Bullseye':
            skip_stage = skip_build_bullseye(target_branch, 'centos7')
        case 'Build on CentOS 8 Bullseye':
        case 'Build on EL 8 Bullseye':
        case 'Build on EL 8.8 Bullseye':
            skip_stage = skip_build_bullseye(target_branch, 'el8')
        case 'Build on CentOS 7 debug':
            if (run_default_skipped_stage('build-centos7-gcc-debug')) {
                skip_stage = false
            } else{
                skip_stage = paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                             (docOnlyChange(target_branch) && prRepos('centos7') == '') ||
                             quickBuild()
            }
        case 'Build on CentOS 8 debug':
        case 'Build on EL 8 debug':
        case 'Build on EL 8.8 debug':
            skip_stage = paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                         skip_stage_pragma('build-centos7-gcc-debug') ||
                         (docOnlyChange(target_branch) && prRepos('el8') == '') ||
                         quickBuild()
        case 'Build on CentOS 7 release':
            skip_stage = paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                         skip_stage_pragma('build-centos7-gcc-release', 'true') ||
                         (docOnlyChange(target_branch) && prRepos('centos7') == '') ||
                         quickBuild()
        case 'Build on CentOS 7':
            skip_stage = paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                         skip_stage_pragma('build-centos7-gcc', 'false') ||
                         (docOnlyChange(target_branch) && prRepos('centos7') == '') ||
                         quickFunctional()
        case 'Build on CentOS 8 release':
        case 'Build on EL 8 release':
        case 'Build on EL 8.8 release':
            skip_stage = paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                         skip_stage_pragma('build-el8-gcc-release', 'true') ||
                         (docOnlyChange(target_branch) && prRepos('el8') == '') ||
                         quickBuild()
        case 'Build on CentOS 7 with Clang':
        case 'Build on CentOS 7 with Clang debug':
            if (run_default_skipped_stage('build-centos7-clang-debug')) {
                skip_stage = false
            } else {
                skip_stage = paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                             skip_build_on_landing_branch(target_branch) ||
                             (docOnlyChange(target_branch) && prRepos('centos7') == '')
            }
        case 'Build on CentOS 8 with Clang':
        case 'Build on CentOS 8 with Clang debug':
        case 'Build on EL 8 with Clang':
        case 'Build on EL 8.8 with Clang':
        case 'Build on EL 8 with Clang debug':
        case 'Build on EL 8.8 with Clang debug':
            if (run_default_skipped_stage('build-el8-clang-debug')) {
                skip_stage = false
            } else {
                skip_stage = paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                             skip_build_on_landing_branch(target_branch) ||
                             (docOnlyChange(target_branch) &&  prRepos('el8') == '')
            }
        case 'Build on Ubuntu 20.04':
            skip_stage = paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                         skip_build_on_landing_branch(target_branch) ||
                         (docOnlyChange(target_branch) && prRepos('ubuntu20') == '')
        case 'Build on Leap 15 with Clang':
        case 'Build on Leap 15.4 with Clang':
        case 'Build on Leap 15.5 with Clang':
            skip_stage = paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                         skip_build_on_landing_branch(target_branch) ||
                         (docOnlyChange(target_branch) && prRepos('leap15') == '')
        /* groovylint-disable-next-line DuplicateCaseStatement */
        case 'Build on CentOS 8':
        case 'Build on EL 8':
            skip_stage = paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                         skip_stage_pragma('build-el8-gcc-dev') ||
                         (docOnlyChange(target_branch) &&
                         prRepos('el8') == '') ||
                         quickBuild()
        case 'Build on Ubuntu 20.04 with Clang':
            skip_stage = paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                         target_branch =~ branchTypeRE('weekly') ||
                         skip_stage_pragma('build-ubuntu-clang') ||
                         (docOnlyChange(target_branch) && prRepos('ubuntu20') == '') ||
                         quickBuild()
        case 'Build on Leap 15':
        case 'Build on Leap 15.4':
        case 'Build on Leap 15.5':
            skip_stage = paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                         skip_stage_pragma('build-leap15-gcc') ||
                         (docOnlyChange(target_branch) && prRepos('leap15') == '') ||
                         quickBuild()
        case 'Build on Leap 15 with Intel-C and TARGET_PREFIX':
        case 'Build on Leap 15.4 with Intel-C and TARGET_PREFIX':
        case 'Build on Leap 15.5 with Intel-C and TARGET_PREFIX':
            skip_stage = paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                         target_branch =~ branchTypeRE('weekly') ||
                         skip_stage_pragma('build-leap15-icc') ||
                         (docOnlyChange(target_branch) && prRepos('leap15') == '') ||
                         quickBuild()
        case 'Unit Tests':
            skip_stage =  env.NO_CI_TESTING == 'true' ||
                          paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                          skip_stage_pragma('build') ||
                          rpmTestVersion() != '' ||
                          docOnlyChange(target_branch) ||
                          skip_build_on_el_gcc(target_branch, '8') ||
                          skip_stage_pragma('unit-tests')
        case 'NLT':
        case 'NLT on CentOS 8':
        case 'NLT on EL 8':
        case 'NLT on EL 8.8':
            skip_stage = skip_stage_pragma('nlt') ||
                         quickBuild() ||
                         stageAlreadyPassed()
        case 'Unit Test Bullseye':
        case 'Unit Test Bullseye on CentOS 8':
        case 'Unit Test Bullseye on EL 8':
        case 'Unit Test Bullseye on EL 8.8':
            skip_stage = skip_stage_pragma('bullseye', 'true') ||
                         stageAlreadyPassed()
        case 'Unit Test bdev with memcheck on EL 8':
        case 'Unit Test bdev with memcheck on EL 8.8':
        case 'Unit Test with memcheck on CentOS 8':
        case 'Unit Test with memcheck on EL 8':
        case 'Unit Test with memcheck on EL 8.8':
        case 'Unit Test with memcheck':
            skip_stage = !paramsValue('CI_UNIT_TEST_MEMCHECK', true) ||
                         skip_stage_pragma('unit-test-memcheck') ||
                         stageAlreadyPassed()
        case 'Unit Test':
        case 'Unit Test on CentOS 8':
        case 'Unit Test on EL 8':
        case 'Unit Test on EL 8.8':
        case 'Unit Test bdev on EL 8':
        case 'Unit Test bdev on EL 8.8':
            skip_stage = !paramsValue('CI_UNIT_TEST', true) ||
                         skip_stage_pragma('unit-test') ||
                         skip_stage_pragma('run_test') ||
                         stageAlreadyPassed()
        case 'Test':
            skip_stage = env.NO_CI_TESTING == 'true' ||
                         (skip_stage_pragma('build') && rpmTestVersion() == '') ||
                         skip_stage_pragma('test') ||
                         (env.BRANCH_NAME =~ branchTypeRE('testing') && !startedByTimer() && !startedByUpstream() && !startedByUser()) ||
                         skip_if_unstable()
        case 'Test on CentOS 7 [in] Vagrant':
            skip_stage = skip_stage_pragma('vagrant-test', 'true') &&
                         !env.BRANCH_NAME =~ branchTypeRE('weekly') ||
                         stageAlreadyPassed()
        case 'Coverity on CentOS 7':
        case 'Coverity on CentOS 8':
        case 'Coverity on EL 8':
        case 'Coverity on EL 8.8':
        case 'Coverity':
            skip_stage = paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                         rpmTestVersion() != '' ||
                         skip_stage_pragma('coverity-test', 'true') ||
                         quickFunctional() ||
                         docOnlyChange(target_branch) ||
                         skip_stage_pragma('build')
        case 'Functional on CentOS 7':
            skip_stage = skip_ftest('el7', target_branch, tags)
        case 'Functional on CentOS 7 with Valgrind':
            skip_stage = skip_ftest_valgrind('el7', target_branch, tags)
        case 'Functional on CentOS 8 with Valgrind':
        case 'Functional on EL 8 with Valgrind':
        case 'Functional on EL 8.8 with Valgrind':
            skip_stage = skip_ftest_valgrind('el8', target_branch, tags)
        case 'Functional on CentOS 8':
        case 'Functional on EL 8':
        case 'Functional on EL 8.8':
            skip_stage = skip_ftest('el8', target_branch, tags)
        case 'Functional on EL 9':
            skip_stage = skip_ftest('el9', target_branch, tags)
        case 'Functional on Leap 15':
        case 'Functional on Leap 15.4':
        case 'Functional on Leap 15.5':
            skip_stage = skip_ftest('leap15', target_branch, tags)
        case 'Functional on Ubuntu 20.04':
            /* we don't do any testing on Ubuntu yet
            skip_ftest('ubuntu20', target_branch, tags) */
            skip_stage = true
        case 'Fault injection testing':
        case 'Fault injection testing on CentOS 8':
        case 'Fault injection testing on EL 8':
        case 'Fault injection testing on EL 8.8':
            skip_stage = skip_stage_pragma('fault-injection-test') ||
                         !paramsValue('CI_FI_el8_TEST', true) ||
                         quickFunctional() ||
                         docOnlyChange(target_branch) ||
                         skip_stage_pragma('func-test') ||
                         skip_stage_pragma('func-test-vm') ||
                         stageAlreadyPassed()
        case 'Test CentOS 7 RPMs':
            skip_stage = !paramsValue('CI_RPMS_el7_TEST', true) ||
                         target_branch =~ branchTypeRE('weekly') ||
                         skip_stage_pragma('test') ||
                         skip_stage_pragma('test-rpms') ||
                         skip_stage_pragma('test-centos-rpms') ||
                         skip_stage_pragma('test-centos-7-rpms') ||
                         docOnlyChange(target_branch) ||
                         (quickFunctional() && !paramsValue('CI_RPMS_el7_TEST', true) && !run_default_skipped_stage('test-centos-7-rpms')) ||
                         stageAlreadyPassed()
        case 'Test CentOS 8.3.2011 RPMs':
            skip_stage = !paramsValue('CI_RPMS_centos8.3.2011_TEST', true) ||
                         target_branch =~ branchTypeRE('weekly') ||
                         skip_stage_pragma('test') ||
                         skip_stage_pragma('test-rpms') ||
                         skip_stage_pragma('test-centos-8.3-rpms') ||
                         docOnlyChange(target_branch) ||
                         (quickFunctional() && !paramsValue('CI_RPMS_el8_3_2011_TEST', true) && !run_default_skipped_stage('test-centos-8.3-rpms')) ||
                         stageAlreadyPassed()
        case 'Test CentOS 8.4.2105 RPMs':
        case 'Test EL 8.4 RPMs':
            skip_stage = !paramsValue('CI_RPMS_el8.4.2105_TEST', true) ||
                         target_branch =~ branchTypeRE('weekly') ||
                         skip_stage_pragma('test') ||
                         skip_stage_pragma('test-rpms') ||
                         skip_stage_pragma('test-el-8.4-rpms') ||
                         docOnlyChange(target_branch) ||
                         (quickFunctional() && !paramsValue('CI_RPMS_el8_4_TEST', true) && !run_default_skipped_stage('test-el-8.4-rpms')) ||
                         stageAlreadyPassed()
        case 'Test CentOS 8.5.2111 RPMs':
        case 'Test EL 8.5 RPMs':
            skip_stage = !paramsValue('CI_RPMS_el8.5.2111_TEST', true) ||
                         target_branch =~ branchTypeRE('weekly') ||
                         skip_stage_pragma('test') ||
                         skip_stage_pragma('test-rpms') ||
                         skip_stage_pragma('test-el-8.5-rpms') ||
                         docOnlyChange(target_branch) ||
                         (quickFunctional() && !paramsValue('CI_RPMS_el8_5_TEST', true) && !run_default_skipped_stage('test-el-8.5-rpms')) ||
                         stageAlreadyPassed()
        case 'Test EL 8.6 RPMs':
        case 'Test RPMs on EL 8.6':
            skip_stage = !paramsValue('CI_RPMS_el8.6_TEST', true) ||
                         target_branch =~ branchTypeRE('weekly') ||
                         skip_stage_pragma('test') ||
                         skip_stage_pragma('test-rpms') ||
                         skip_stage_pragma('test-el-8.6-rpms') ||
                         docOnlyChange(target_branch) ||
                         (quickFunctional() && !paramsValue('CI_RPMS_el8_6_TEST', true) && !run_default_skipped_stage('test-el-8.6-rpms')) ||
                         (rpmTestVersion() != '') ||
                         stageAlreadyPassed()
        case 'Test Leap 15 RPMs':
        case 'Test Leap 15.2 RPMs':
            // Skip by default as it doesn't pass with Leap15.3 due to
            // requiring a newer glibc
            skip_stage = !paramsValue('CI_RPMS_leap15_TEST', true) ||
                         skip_stage_pragma('test-leap-15-rpms', 'true') ||
                         stageAlreadyPassed()
        case 'Test RPMs on Leap 15.4':
            skip_stage = !paramsValue('CI_RPMS_leap15.4_TEST', true) ||
                         target_branch =~ branchTypeRE('weekly') ||
                         skip_stage_pragma('test') ||
                         skip_stage_pragma('test-rpms') ||
                         skip_stage_pragma('test-leap-15.4-rpms', 'true') ||
                         docOnlyChange(target_branch) ||
                         (quickFunctional() && !paramsValue('CI_RPMS_leap15_4_TEST', true) && !run_default_skipped_stage('test-leap-15.4-rpms')) ||
                         (rpmTestVersion() != '') || stageAlreadyPassed()
        case 'Scan CentOS 7 RPMs':
            skip_stage = skip_scan_rpms('centos7', target_branch)
        case 'Scan CentOS 8 RPMs':
        case 'Scan EL 8 RPMs':
            skip_stage = skip_scan_rpms('el8', target_branch)
        case 'Scan Leap 15 RPMs':
        case 'Scan Leap 15.4 RPMs':
        case 'Scan Leap 15.5 RPMs':
            skip_stage = skip_scan_rpms('leap15', target_branch)
        case 'Test Hardware':
            skip_stage = env.NO_CI_TESTING == 'true' ||
                         skip_stage_pragma('func-test') ||
                         skip_stage_pragma('func-hw-test') ||
                         (skip_stage_pragma('build') && rpmTestVersion() == '') ||
                         skip_stage_pragma('test') ||
                         (env.BRANCH_NAME =~ branchTypeRE('testing') && !startedByTimer() && !startedByUpstream() && !startedByUser()) ||
                         skip_if_unstable()
        case 'Functional_Hardware_Small':
        case 'Functional Hardware Small':
            skip_stage = skip_ftest_hw('small', target_branch, tags)
        case 'Functional_Hardware_Medium':
        case 'Functional Hardware Medium':
            skip_stage = skip_ftest_hw('medium', target_branch, tags)
        case 'Functional Hardware Medium TCP Provider':
            skip_stage = skip_ftest_hw('medium-tcp-provider', target_branch, tags)
        case 'Functional Hardware Medium Verbs Provider':
            skip_stage = skip_ftest_hw('medium-verbs-provider', target_branch, tags)
        case 'Functional Hardware Medium UCX Provider':
            skip_stage = skip_ftest_hw('medium-ucx-provider', target_branch, tags)
        case 'Functional_Hardware_Large':
        case 'Functional Hardware Large':
            skip_stage = skip_ftest_hw('large', target_branch, tags)
        case 'Functional_Hardware_24':
        case 'Functional Hardware 24':
            skip_stage = skip_ftest_hw('24', target_branch, tags)
        case 'Bullseye Report':
        case 'Bullseye Report on CentOS 8':
        case 'Bullseye Report on EL 8':
            skip_stage = env.BULLSEYE == null ||
                         skip_stage_pragma('bullseye', 'true')
        case 'DAOS Build and Test':
            skip_stage = skip_stage_pragma('daos-build-and-test')
        default:
            stageMessage("Don't know how to skip stage, not skipping!")
            skip_stage = false

        if(skip_stage) {
            stageMessage('Skipping the stage')
        } else {
            stageMessage('Running the stage')
        }
        return skip_stage
    }
}
