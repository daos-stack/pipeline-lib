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

// Determine if this is a restarted job and if it succeeded on the
// previous run.
boolean already_passed(String stage_name = env.STAGE_NAME, String postfix='') {
    // Lookup to see if restarted
    /* groovylint-disable-next-line UnnecessaryGetter */
    if (!currentBuild.getBuildCauses().any { cause ->
        cause._class == 'org.jenkinsci.plugins.pipeline.modeldefinition.' +
                        'causes.RestartDeclarativePipelineCause' }) {
        return false
    }

    // Make sure a previous check has been removed.
    String status_file = stageStatusFilename(stage_name, postfix)
    if (fileExists(status_file)) {
        fileOperations([fileDeleteOperation(includes: status_file)])
    }

    String old_build = "${env.BUILD_NUMBER.toInteger() - 1}"

    // First try looking up using copyArtifacts, which requires
    // that the Jenkinsfile give permission for the copy.
    try {
        copyArtifacts projectName: env.JOB_NAME,
                      optional: false,
                      filter: status_file,
                      selector: specific(old_build)
        try {
            String stage_status = readFile(file: status_file).trim()
            if (stage_status == 'SUCCESS') {
                return true
            }
            println('Previous run this stage ended with status ' +
                    "'${stage_status}', so re-running")
        } catch (java.nio.file.NoSuchFileException e) {
            // This should not ever fail, so just collecting diagnostics
            // if the code ever gets here.
            println("readFile failed! ${e}")
            sh label: 'Diagnostic for readFile failure',
               script: 'ls -l *.status',
               returnStatus: true
            if (fileExists(status_file)) {
                println("fileExists found ${status_file}")
            }
        }
    } catch (hudson.AbortException e) {
        println("Informational: copyArtifact could not get artifact ${e}")

        // Try using httpRequests, which does not require a modified
        // Jenkinsfile, but makes assumptions on where Jenkins is
        // currently storing the artifacts for a job.
        // This is for transitioning until all Jenkinsfiles are allowing
        // artifacts to be copied and then can be removed.
        String my_build = "/${env.BUILD_NUMBER}/"
        String prev_build = "/${old_build}/"
        String old_job = env.BUILD_URL.replace(my_build, prev_build)
        String art_url = old_job + 'artifact/' + status_file

        /* groovylint-disable-next-line NoDef, VariableTypeRequired */
        def response = httpRequest(url: art_url,
                                   acceptType: 'TEXT_PLAIN',
                                   httpMode: 'GET',
                                   validResponseCodes: '100:599')

        if (response.status == 200 && response.content == 'SUCCESS') {
            return true
        }
    }
    return false
}

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
    return already_passed() ||
           target_branch == 'weekly-testing' ||
           skip_stage_pragma('scan-rpms', 'true') ||
           (distro == 'centos-7' &&
            (!paramsValue('CI_SCAN_RPMS_el7_TEST', true)) ||
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
    if (already_passed()) {
        return true
    }
    if (run_default_skipped_stage('func-test-' + distro) ||
        run_default_skipped_stage('func-test-vm-all')) {
        // Forced to run due to a (Skip) pragma set to false
        return false
    }
    // If a parameter exists to enable a build, then use it.
    // The params.CI_MORE_FUNCTIONAL_PR_TESTS allows enabling
    // tests that are not run in PRs.
    return !paramsValue('CI_FUNCTIONAL_' + distro + '_TEST', true) ||
           distro == 'ubuntu20' ||
           skip_stage_pragma('func-test') ||
           skip_stage_pragma('func-test-vm') ||
           skip_stage_pragma('func-test-vm-all') ||
           !testsInStage() ||
           skip_stage_pragma('func-test-' + distro) ||
           (docOnlyChange(target_branch) &&
            prRepos(distro) == '') ||
           /* groovylint-disable-next-line UnnecessaryGetter */
           (isPr() && distro != 'el8')
}

boolean skip_ftest_valgrind(String distro, String target_branch) {
    // Check if the default for skipping this stage been overriden
    // otherwise always skip this stage (DAOS-10585)
    return already_passed() ||
           !run_default_skipped_stage('func-test-vm-valgrind') ||
           !paramsValue('CI_FUNCTIONAL_' + distro + '_VALGRIND_TEST', false) ||
           skip_ftest(distro, target_branch) ||
           /* groovylint-disable-next-line UnnecessaryGetter */
           isPr() ||
           target_branch.startsWith('weekly-testing')
}

boolean skip_ftest_hw(String size, String target_branch) {
    return already_passed() ||
           env.DAOS_STACK_CI_HARDWARE_SKIP == 'true' ||
           !paramsValue('CI_' + size.replace('-', '_') + '_TEST', true) ||
           skip_stage_pragma('func-test') ||
           skip_stage_pragma('func-hw-test-' + size) ||
           !testsInStage() ||
           ((env.BRANCH_NAME == 'master' ||
             env.BRANCH_NAME.startsWith('release/')) &&
            !(startedByTimer() || startedByUser())) ||
           cachedCommitPragma('Run-daily-stages') == 'true' ||
           (docOnlyChange(target_branch) &&
            prRepos(hwDistroTarget(size)) == '')
}

boolean skip_if_unstable() {
    if (paramsValue('CI_ALLOW_UNSTABLE_TEST', false) ||
        cachedCommitPragma('Allow-unstable-test').toLowerCase() == 'true' ||
        env.BRANCH_NAME == 'master' ||
        env.BRANCH_NAME.matches(testBranchRE()) ||
        env.BRANCH_NAME.startsWith('release/')) {
        return false
    }

    // Ok, it's a PR and the Allow pragma isn't set.  Skip if the build is
    // unstable.

    return currentBuild.currentResult == 'UNSTABLE'
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
    if (config['stage']) {
        return skip_stage_pragma(config['stage'], config['def_val'])
    }

    if (already_passed(config['stage_name'] ?: env.STAGE_NAME,
                       config['axes'] ?: '')) {
        return true
    }

    String target_branch = env.CHANGE_TARGET ? env.CHANGE_TARGET : env.BRANCH_NAME

    switch (env.STAGE_NAME) {
        case 'Cancel Previous Builds':
            return cachedCommitPragma('Cancel-prev-build') == 'false' ||
                   /* groovylint-disable-next-line UnnecessaryGetter */
                   (!isPr() && !startedByUpstream())
        case 'Pre-build':
            return docOnlyChange(target_branch) ||
                   target_branch == 'weekly-testing' ||
                   rpmTestVersion() != '' ||
                   quickBuild()
        case 'checkpatch':
            return skip_stage_pragma('checkpatch')
        case 'Python Bandit check':
            return skip_stage_pragma('python-bandit')
        case 'Build':
            // always build branch landings as we depend on lastSuccessfulBuild
            // always having RPMs in it
            return (env.BRANCH_NAME != target_branch) &&
                   skip_stage_pragma('build') ||
                   rpmTestVersion() != '' ||
                   (quickFunctional() &&
                    cachedCommitPragma('PR-repos').trim().contains('daos@'))
        case 'Build RPM on CentOS 7':
            return paramsValue('CI_RPM_centos7_NOBUILD', false) ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                    prRepos('centos7').contains('daos@') ||
                   skip_stage_pragma('build-centos7-rpm')
        case 'Build RPM on EL 8':
        case 'Build RPM on CentOS 8':
            return paramsValue('CI_RPM_el8_NOBUILD', false) ||
                   (docOnlyChange(target_branch) &&
                    prRepos('el8') == '') ||
                   prRepos('el8').contains('daos@') ||
                   skip_stage_pragma('build-el8-rpm')
        case 'Build RPM on Leap 15':
        case 'Build RPM on Leap 15.4':
            return paramsValue('CI_RPM_leap15_NOBUILD', false) ||
                   target_branch == 'weekly-testing' ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '') ||
                   prRepos('leap15').contains('daos@') ||
                   skip_stage_pragma('build-leap15-rpm')
        case 'Build DEB on Ubuntu 20.04':
            return paramsValue('CI_RPM_ubuntu20_NOBUILD', false) ||
                   target_branch == 'weekly-testing' ||
                   (docOnlyChange(target_branch) &&
                    prRepos('ubuntu20') == '') ||
                   prRepos('ubuntu20').contains('daos@') ||
                   skip_stage_pragma('build-ubuntu20-rpm')
        case 'Build on CentOS 8':
        case 'Build on EL 8':
            return skip_build_on_el_gcc(target_branch, '8')
        case 'Build on CentOS 7 Bullseye':
            return skip_build_bullseye(target_branch, 'centos7')
        case 'Build on CentOS 8 Bullseye':
        case 'Build on EL 8 Bullseye':
            return skip_build_bullseye(target_branch, 'el8')
        case 'Build on CentOS 7 debug':
            if (run_default_skipped_stage('build-centos7-gcc-debug')) {
                return false
            }
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   quickBuild()
        case 'Build on CentOS 8 debug':
        case 'Build on EL 8 debug':
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_stage_pragma('build-centos7-gcc-debug') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('el8') == '') ||
                   quickBuild()
        case 'Build on CentOS 7 release':
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_stage_pragma('build-centos7-gcc-release', 'true') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   quickBuild()
        case 'Build on CentOS 7':
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_stage_pragma('build-centos7-gcc', 'false') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '') ||
                   quickFunctional()
        case 'Build on CentOS 8 release':
        case 'Build on EL 8 release':
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_stage_pragma('build-el8-gcc-release', 'true') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('el8') == '') ||
                   quickBuild()
        case 'Build on CentOS 7 with Clang':
        case 'Build on CentOS 7 with Clang debug':
            if (run_default_skipped_stage('build-centos7-clang-debug')) {
                return false
            }
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_build_on_landing_branch(target_branch) ||
                   (docOnlyChange(target_branch) &&
                    prRepos('centos7') == '')
        case 'Build on CentOS 8 with Clang':
        case 'Build on CentOS 8 with Clang debug':
        case 'Build on EL 8 with Clang':
        case 'Build on EL 8 with Clang debug':
            if (run_default_skipped_stage('build-el8-clang-debug')) {
                return false
            }
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_build_on_landing_branch(target_branch) ||
                   (docOnlyChange(target_branch) &&
                    prRepos('el8') == '')
        case 'Build on Ubuntu 20.04':
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_build_on_landing_branch(target_branch) ||
                   (docOnlyChange(target_branch) &&
                    prRepos('ubuntu20') == '')
        case 'Build on Leap 15 with Clang':
        case 'Build on Leap 15.4 with Clang':
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_build_on_landing_branch(target_branch) ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '')
        /* groovylint-disable-next-line DuplicateCaseStatement */
        case 'Build on CentOS 8':
        case 'Build on EL 8':
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_stage_pragma('build-el8-gcc-dev') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('el8') == '') ||
                   quickBuild()
        case 'Build on Ubuntu 20.04 with Clang':
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   target_branch == 'weekly-testing' ||
                   skip_stage_pragma('build-ubuntu-clang') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('ubuntu20') == '') ||
                   quickBuild()
        case 'Build on Leap 15':
        case 'Build on Leap 15.4':
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   skip_stage_pragma('build-leap15-gcc') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '') ||
                   quickBuild()
        case 'Build on Leap 15 with Intel-C and TARGET_PREFIX':
        case 'Build on Leap 15.4 with Intel-C and TARGET_PREFIX':
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   target_branch == 'weekly-testing' ||
                   skip_stage_pragma('build-leap15-icc') ||
                   (docOnlyChange(target_branch) &&
                    prRepos('leap15') == '') ||
                   quickBuild()
        case 'Unit Tests':
            return  env.NO_CI_TESTING == 'true' ||
                    paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                    skip_stage_pragma('build') ||
                    rpmTestVersion() != '' ||
                    docOnlyChange(target_branch) ||
                    skip_build_on_el_gcc(target_branch, '8') ||
                    skip_stage_pragma('unit-tests')
        case 'NLT':
        case 'NLT on CentOS 8':
        case 'NLT on EL 8':
            return skip_stage_pragma('nlt') ||
                   quickBuild() ||
                   already_passed()
        case 'Unit Test Bullseye':
        case 'Unit Test Bullseye on CentOS 8':
        case 'Unit Test Bullseye on EL 8':
            return skip_stage_pragma('bullseye', 'true') ||
                   already_passed()
        case 'Unit Test with memcheck on CentOS 8':
        case 'Unit Test with memcheck on EL 8':
        case 'Unit Test with memcheck':
            return !paramsValue('CI_UNIT_TEST_MEMCHECK', true) ||
                   skip_stage_pragma('unit-test-memcheck') ||
                   already_passed()
        case 'Unit Test':
        case 'Unit Test on CentOS 8':
        case 'Unit Test on EL 8':
            return !paramsValue('CI_UNIT_TEST', true) ||
                   skip_stage_pragma('unit-test') ||
                   skip_stage_pragma('run_test') ||
                   already_passed()
        case 'Test':
            return env.NO_CI_TESTING == 'true' ||
                   (skip_stage_pragma('build') &&
                    rpmTestVersion() == '') ||
                   skip_stage_pragma('test') ||
                   (env.BRANCH_NAME.matches(testBranchRE()) &&
                    !startedByTimer() &&
                    !startedByUpstream() &&
                    !startedByUser()) ||
                   skip_if_unstable()
        case 'Test on CentOS 7 [in] Vagrant':
            return skip_stage_pragma('vagrant-test', 'true') &&
                   !env.BRANCH_NAME.startsWith('weekly-testing') ||
                   already_passed()
        case 'Coverity on CentOS 7':
        case 'Coverity on CentOS 8':
        case 'Coverity on EL 8':
            return paramsValue('CI_BUILD_PACKAGES_ONLY', false) ||
                   rpmTestVersion() != '' ||
                   skip_stage_pragma('coverity-test', 'true') ||
                   quickFunctional() ||
                   docOnlyChange(target_branch) ||
                   skip_stage_pragma('build')
        case 'Functional on CentOS 7':
            return skip_ftest('el7', target_branch)
        case 'Functional on CentOS 7 with Valgrind':
            return skip_ftest_valgrind('el7', target_branch)
        case 'Functional on CentOS 8 with Valgrind':
        case 'Functional on EL 8 with Valgrind':
            return skip_ftest_valgrind('el8', target_branch)
        case 'Functional on CentOS 8':
        case 'Functional on EL 8':
            return skip_ftest('el8', target_branch)
        case 'Functional on Leap 15':
        case 'Functional on Leap 15.4':
            return skip_ftest('leap15', target_branch)
        case 'Functional on Ubuntu 20.04':
            /* we don't do any testing on Ubuntu yet
            skip_ftest('ubuntu20', target_branch) */
            return true
        case 'Fault injection testing':
        case 'Fault injection testing on CentOS 8':
        case 'Fault injection testing on EL 8':
            return skip_stage_pragma('fault-injection-test') ||
                   !paramsValue('CI_FI_el8_TEST', true) ||
                   quickFunctional() ||
                   docOnlyChange(target_branch) ||
                   skip_stage_pragma('func-test') ||
                   skip_stage_pragma('func-test-vm') ||
                   already_passed()
        case 'Test CentOS 7 RPMs':
            return !paramsValue('CI_RPMS_el7_TEST', true) ||
                   target_branch == 'weekly-testing' ||
                   skip_stage_pragma('test') ||
                   skip_stage_pragma('test-rpms') ||
                   skip_stage_pragma('test-centos-rpms') ||
                   skip_stage_pragma('test-centos-7-rpms') ||
                   docOnlyChange(target_branch) ||
                   (quickFunctional() &&
                    !paramsValue('CI_RPMS_el7_TEST', true) &&
                    !run_default_skipped_stage('test-centos-7-rpms')) ||
                   already_passed()
        case 'Test CentOS 8.3.2011 RPMs':
            return !paramsValue('CI_RPMS_centos8.3.2011_TEST', true) ||
                   target_branch == 'weekly-testing' ||
                   skip_stage_pragma('test') ||
                   skip_stage_pragma('test-rpms') ||
                   skip_stage_pragma('test-centos-8.3-rpms') ||
                   docOnlyChange(target_branch) ||
                   (quickFunctional() &&
                    !paramsValue('CI_RPMS_el8_3_2011_TEST', true) &&
                    !run_default_skipped_stage('test-centos-8.3-rpms')) ||
                   already_passed()
        case 'Test CentOS 8.4.2105 RPMs':
        case 'Test EL 8.4 RPMs':
            return !paramsValue('CI_RPMS_el8.4.2105_TEST', true) ||
                   target_branch == 'weekly-testing' ||
                   skip_stage_pragma('test') ||
                   skip_stage_pragma('test-rpms') ||
                   skip_stage_pragma('test-el-8.4-rpms') ||
                   docOnlyChange(target_branch) ||
                   (quickFunctional() &&
                    !paramsValue('CI_RPMS_el8_4_TEST', true) &&
                    !run_default_skipped_stage('test-el-8.4-rpms')) ||
                   already_passed()
        case 'Test CentOS 8.5.2111 RPMs':
        case 'Test EL 8.5 RPMs':
            return !paramsValue('CI_RPMS_el8.5.2111_TEST', true) ||
                   target_branch == 'weekly-testing' ||
                   skip_stage_pragma('test') ||
                   skip_stage_pragma('test-rpms') ||
                   skip_stage_pragma('test-el-8.5-rpms') ||
                   docOnlyChange(target_branch) ||
                   (quickFunctional() &&
                    !paramsValue('CI_RPMS_el8_5_TEST', true) &&
                    !run_default_skipped_stage('test-el-8.5-rpms')) ||
                   already_passed()
        case 'Test Leap 15 RPMs':
        case 'Test Leap 15.2 RPMs':
            // Skip by default as it doesn't pass with Leap15.3 due to
            // requiring a newer glibc
            return !paramsValue('CI_RPMS_leap15_TEST', true) ||
                   skip_stage_pragma('test-leap-15-rpms', 'true') ||
                   already_passed()
        case 'Scan CentOS 7 RPMs':
            return skip_scan_rpms('centos-7', target_branch)
        case 'Scan CentOS 8 RPMs':
        case 'Scan EL 8 RPMs':
            return skip_scan_rpms('el-8', target_branch)
        case 'Scan Leap 15 RPMs':
        case 'Scan Leap 15.4 RPMs':
            return skip_scan_rpms('leap-15', target_branch)
        case 'Test Hardware':
            return env.NO_CI_TESTING == 'true' ||
                   skip_stage_pragma('func-test') ||
                   skip_stage_pragma('func-hw-test') ||
                   (skip_stage_pragma('build') &&
                    rpmTestVersion() == '') ||
                   skip_stage_pragma('test') ||
                   (env.BRANCH_NAME.matches(testBranchRE()) &&
                    !startedByTimer() &&
                    !startedByUpstream() &&
                    !startedByUser()) ||
                   skip_if_unstable()
        case 'Functional_Hardware_Small':
        case 'Functional Hardware Small':
            return skip_ftest_hw('small', target_branch)
        case 'Functional_Hardware_Medium':
        case 'Functional Hardware Medium':
            return skip_ftest_hw('medium', target_branch)
        case 'Functional Hardware Medium Verbs Provider':
            return skip_ftest_hw('medium-verbs-provider', target_branch)
        case 'Functional Hardware Medium UCX Provider':
            return skip_ftest_hw('medium-ucx-provider', target_branch)
        case 'Functional_Hardware_Large':
        case 'Functional Hardware Large':
            return skip_ftest_hw('large', target_branch)
        case 'Functional_Hardware_24':
        case 'Functional Hardware 24':
            return skip_ftest_hw('24', target_branch)
        case 'Bullseye Report':
        case 'Bullseye Report on CentOS 8':
        case 'Bullseye Report on EL 8':
            return env.BULLSEYE == null ||
                   skip_stage_pragma('bullseye', 'true')
        case 'DAOS Build and Test':
            return skip_stage_pragma('daos-build-and-test')
        default:
            println("Don't know how to skip stage \"${env.STAGE_NAME}\", not skipping")
    }
}
