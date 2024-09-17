#!/usr/bin/env groovy
/* groovylint-disable DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral */
// groovylint-disable DuplicateStringLiteral, NestedBlockDepth, VariableName
/* Copyright (C) 2019-2023 Intel Corporation
 * All rights reserved.
 *
 * This file is part of the DAOS Project. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at https://img.shields.io/badge/License-Apache%202.0-blue.svg.
 * No part of the DAOS Project, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE file.
 */

// The @library line needs to be edited in a PR for adding a test.
// Adding a test requires two PRs.  One to add the test.
// That PR should be landed with out deleting the PR branch.
// Then a second PR submitted to comment out the @Library line, and when it
// is landed, both PR branches can be deleted.
//@Library(value='pipeline-lib@my_branch_name') _
@Library(value="pipeline-lib@grom72/SRE-2453") _

/* groovylint-disable-next-line CompileStatic */
job_status_internal = [:]

void job_status_write() {
    if (!env.DAOS_STACK_JOB_STATUS_DIR) {
        return
    }
    String jobName = env.JOB_NAME.replace('/', '_')
    jobName += '_' + env.BUILD_NUMBER
    String dirName = env.DAOS_STACK_JOB_STATUS_DIR + '/' + jobName + '/'

    String job_status_text = writeYaml data: job_status_internal,
                                       returnText: true

    // Need to use shell script for creating files that are not
    // in the workspace.
    sh label: "Write jenkins_job_status ${dirName}jenkins_result",
       script: """mkdir -p ${dirName}
                  echo "${job_status_text}" >> ${dirName}jenkins_result"""
}

// groovylint-disable-next-line MethodParameterTypeRequired
void job_status_update(String name=env.STAGE_NAME,
                       // groovylint-disable-next-line NoDef
                       def value=currentBuild.currentResult) {
    String key = name.replace(' ', '_')
    key = key.replaceAll('[ .]', '_')
    if (job_status_internal.containsKey(key)) {
        // groovylint-disable-next-line NoDef, VariableTypeRequired
        def myStage = job_status_internal[key]
        if (myStage in Map) {
            if (value in Map) {
                value.each { resultKey, data -> myStage[resultKey] = data }
                return
            }
            // Update result with single value
            myStage['result'] = value
            return
        }
    }
    // pass through value
    job_status_internal[key] = value
}

// groovylint-disable-next-line MethodParameterTypeRequired, NoDef
void job_step_update(def value) {
    if (value == null) {
        // groovylint-disable-next-line ParameterReassignment
        value = currentBuild.currentResult
    }
    job_status_update(env.STAGE_NAME, value)
}

// Don't define this as a type or it loses it's global scope
target_branch = env.CHANGE_TARGET ? env.CHANGE_TARGET : env.BRANCH_NAME

Void distro_version_test(String branch, String distro, String expected) {
    println("Test branch: ${branch}, distro: ${distro}, expecting: ${expected}")
    withEnv(["BRANCH_NAME=${branch}"]) {
        String dv = distroVersion(distro)
        if (dv == null || !dv.startsWith(expected)) {
            unstable("distroVersion() returned ${dv} " +
                      "instead of string starting with '${expected}'")
        }
    }
}

/* groovylint-disable-next-line CompileStatic */
pipeline {
    agent { label 'lightweight' }
    libraries {
        lib("pipeline-lib@${env.BRANCH_NAME}")
    }

    environment {
        SSH_KEY_FILE = 'ci_key'
        SSH_KEY_ARGS = "-i$SSH_KEY_FILE"
        CLUSH_ARGS = "-o$SSH_KEY_ARGS"
    }

    options {
        ansiColor('xterm')
        copyArtifactPermission("/daos-stack/pipeline-lib/${env.BRANCH_NAME}")
    }

    parameters {
        string(name: 'BuildPriority',
               /* groovylint-disable-next-line UnnecessaryGetter */
               defaultValue: getPriority(),
               description: 'Priority of this build.  DO NOT USE WITHOUT PERMISSION.')
    }

    stages {
        stage('Prepare Environment Variables') {
            parallel {
                stage('Get Commit Message') {
                    steps {
                        pragmasToEnv()
                    }
                }
                stage('Determine Release Branch') {
                    steps {
                        script {
                            env.RELEASE_BRANCH = releaseBranch()
                            echo 'Base branch == ' + env.RELEASE_BRANCH
                        }
                    }
                }
            }
        }
        stage('Cancel Previous Builds') {
            when {
                beforeAgent true
                expression { !skipStage() }
            }
            steps {
                cancelPreviousBuilds()
            }
        }
        stage('Test') {
            when {
                beforeAgent true
                expression { !skipStage() }
            }
            parallel {
                stage('daosLatestVersion() tests') {
                    steps {
                        script {
                            assert(daosLatestVersion('master', 'el8').matches(/2.7\.\d+.*/),
                                '"' + daosLatestVersion('master', 'el8') + \
                                '" <> matches(/2.7\.\d+.*/)' )
                            )
                            assert(daosLatestVersion('release/2.4', 'el8').matches(/2.[34]\.\d+.*/),
                                '"' + daosLatestVersion('release/2.4', 'el8') + \
                                '" <> matches(/2.[34]\.\d+.*/)' )
                            assert(daosLatestVersion('release/2.6', 'el8').matches(/2.[56]\.\d+.*/))
                                '"' + daosLatestVersion('release/2.6', 'el8') + \
                                '" <> matches(/2.[56]\.\d+.*/)' )
                        }
                    }
                }
                stage('distroVersion() tests') {
                    steps {
                        distro_version_test('release/2.4', 'el8', '8')
                        distro_version_test('release/2.4', 'leap15', '15')
                        distro_version_test('release/2.6', 'el8', '8')
                        distro_version_test('release/2.6', 'el9', '9')
                        distro_version_test('release/2.6', 'leap15', '15')
                        distro_version_test('master', 'el8', '8')
                        distro_version_test('master', 'el9', '9')
                        distro_version_test('master', 'leap15', '15')
                        distro_version_test('master', 'ubuntu20', '20.04')
                    }
                }
                stage('grep JUnit results tests failure case') {
                    agent {
                        docker {
                            image 'rockylinux:8'
                            label 'docker_runner'
                        }
                    }
                    steps {
                        job_step_update(
                            runTest(script: '''set -ex
                                               rm -f *.xml
                                               echo "<failure> bla bla bla</failure>" > \
                                                 pipeline-test-failure.xml''',
                                    junit_files: '*.xml non-exist*.xml',
                                    failure_artifacts: env.STAGE_NAME))
                    }
                    // runTest handles SCM notification via stepResult
                } // stage('grep JUnit results tests failure case')
                stage('grep JUnit results tests error case 1') {
                    agent {
                        docker {
                            image 'rockylinux:8'
                            label 'docker_runner'
                        }
                    }
                    steps {
                        job_step_update(
                            runTest(script: '''set -ex
                                               rm -f *.xml
                                               echo "<error bla bla bla/>" > \
                                                 pipeline-test-error.xml''',
                                    junit_files: '*.xml non-exist*.xml',
                                    failure_artifacts: env.STAGE_NAME))
                    }
                    // runTest handles SCM notification via stepResult
                } // stage('grep JUnit results tests error case 1')
                stage('grep JUnit results tests error case 2') {
                    agent {
                        docker {
                            image 'rockylinux:8'
                            label 'docker_runner'
                        }
                    }
                    steps {
                        job_step_update(
                            runTest(script: '''set -ex
                                               rm -f *.xml
                                               echo "<errorDetails>bla bla bla</errorDetails>" > \
                                                 pipeline-test-error.xml''',
                                junit_files: '*.xml non-exist*.xml',
                                failure_artifacts: env.STAGE_NAME))
                    }
                    // runTest handles SCM notification via stepResult
                } // stage('grep JUnit results tests error case 2')
                stage('publishToRepository RPM tests') {
                    when {
                        beforeAgent true
                        expression { env.NO_CI_TESTING != 'true' }
                    }
                    agent {
                        dockerfile {
                            filename 'docker/Dockerfile.el.8'
                            label 'docker_runner'
                        }
                    }
                    steps {
                        // Populate an artifact directory
                        copyArtifacts projectName: '/daos-stack/libfabric/master',
                                      filter: 'artifacts/el8/**',
                                      target: 'artifact'
                        publishToRepository(
                            product: 'zzz_pl-' + env.BRANCH_NAME + '_' +
                                      env.BUILD_ID,
                            format: 'yum',
                            maturity: 'test',
                            tech: 'el9',
                            repo_dir: 'artifact/artifacts/el8',
                            download_dir: 'artifact/download',
                            test: true)
                    }
                    post {
                        unsuccessful {
                            scmNotify description: env.STAGE_NAME,
                                      context: 'test/' + env.STAGE_NAME,
                                      status: 'FAILURE'
                        }
                        success {
                            scmNotify description: env.STAGE_NAME,
                                      context: 'test/' + env.STAGE_NAME,
                                      status: 'SUCCESS'
                        }
                    }
                } // stage('publishToRepository RPM tests')
                stage('publishToRepository DEB tests') {
                    when {
                        beforeAgent true
                        expression { env.NO_CI_TESTING != 'true' }
                    }
                    agent {
                        dockerfile {
                            filename 'docker/Dockerfile.el.8'
                            label 'docker_runner'
                            additionalBuildArgs dockerBuildArgs(cachebust: false, add_repos: false)
                        }
                    }
                    steps {
                        // Populate an artifact directory
                        copyArtifacts projectName: '/daos-stack/libfabric/master',
                                      filter: 'artifacts/ubuntu20.04/**',
                                      target: 'artifact'
                        publishToRepository(
                            product: 'zzz_pl-' + env.BRANCH_NAME + '_' +
                                      env.BUILD_ID,
                            format: 'apt',
                            maturity: 'test',
                            tech: 'ubuntu-20.04',
                            repo_dir: 'artifact/artifacts/ubuntu20.04',
                            download_dir: 'artifact/download',
                            test: true)
                    }
                    post {
                        unsuccessful {
                            scmNotify description: env.STAGE_NAME,
                                      context: 'test/' + env.STAGE_NAME,
                                      status: 'FAILURE'
                        }
                        success {
                            scmNotify description: env.STAGE_NAME,
                                      context: 'test/' + env.STAGE_NAME,
                                      status: 'SUCCESS'
                        }
                    }
                } // stage('publishToRepository DEB tests')
                stage('provisionNodes on EL 9 with master Repo') {
                    when {
                        beforeAgent true
                        expression {
                            env.NO_CI_TESTING != 'true' &&
                            cachedCommitPragma('Skip-el9-provisioning-test') != 'true' &&
                            daosLatestVersion('master') != ''
                        }
                    }
                    agent {
                        label 'ci_vm1'
                    }
                    steps {
                        job_step_update(
                            provisionNodes(NODELIST: env.NODELIST,
                                           distro: parseStageInfo()['target'],
                                           profile: 'daos_ci',
                                           node_count: 1,
                                           snapshot: true,
                                           inst_repos: prReposContains('daos') ?  prRepos() : 'daos@master'))
                        job_step_update(
                            /* groovylint-disable-next-line GStringExpressionWithinString */
                            runTest(script: '''NODE=${NODELIST%%,*}
                                               ssh $SSH_KEY_ARGS jenkins@$NODE "set -ex
                                               dnf -y makecache"''',
                                    junit_files: null,
                                    failure_artifacts: env.STAGE_NAME))
                    }
                    post {
                        unsuccessful {
                            sh label: 'Failure debug',
                               /* groovylint-disable-next-line GStringExpressionWithinString */
                               script: '''NODE=${NODELIST%%,*}
                                          ssh $SSH_KEY_ARGS jenkins@$NODE "set -eux
                                          ls -l /etc/yum.repos.d/ || true
                                          for file in /etc/yum.repos.d/*.repo; do
                                              echo \"---- \\$file ----\"
                                              cat \"\\$file\"
                                          done"'''
                        }
                    }
                    // runTest handles SCM notification via stepResult
                } // stage('provisionNodes on EL 9 with master Repo')
                stage('provisionNodes on EL 8 with slurm') {
                    when {
                        beforeAgent true
                        expression {
                            env.NO_CI_TESTING != 'true' &&
                            daosLatestVersion('master') != ''
                        }
                    }
                    agent {
                        label 'ci_vm1'
                    }
                    steps {
                        job_step_update(
                            provisionNodes(NODELIST: env.NODELIST,
                                           distro: parseStageInfo()['target'],
                                           profile: 'daos_ci',
                                           node_count: 1,
                                           snapshot: true,
                                           inst_repos: prReposContains('daos') ?  prRepos() : 'daos@master',
                                           inst_rpms: 'slurm' +
                                                      ' slurm-slurmctld slurm-slurmd' +
                                                      ' ipmctl'))
                        job_step_update(
                            /* groovylint-disable-next-line GStringExpressionWithinString */
                            runTest(script: '''NODE=${NODELIST%%,*}
                                               ssh $SSH_KEY_ARGS jenkins@$NODE "set -ex
                                               which scontrol"''',
                                    junit_files: null,
                                    failure_artifacts: env.STAGE_NAME))
                    }
                    // runTest handles SCM notification via stepResult
                } // stage('provisionNodes on EL 8 with slurm') {
                stage('provisionNodes on Leap 15 with slurm') {
                    when {
                        beforeAgent true
                        expression {
                            env.NO_CI_TESTING != 'true' &&
                            daosLatestVersion('master') != ''
                        }
                    }
                    agent {
                        label 'ci_vm1'
                    }
                    steps {
                        job_step_update(
                            provisionNodes(NODELIST: env.NODELIST,
                                           distro: parseStageInfo()['target'],
                                           profile: 'daos_ci',
                                           node_count: 1,
                                           snapshot: true,
                                           inst_repos: prReposContains('daos') ?  prRepos() : 'daos@master',
                                           inst_rpms: 'slurm ipmctl'))
                        job_step_update(
                            /* groovylint-disable-next-line GStringExpressionWithinString */
                            runTest(script: '''NODE=${NODELIST%%,*}
                                               ssh $SSH_KEY_ARGS jenkins@$NODE "set -ex
                                               which scontrol"''',
                                    junit_files: null,
                                    failure_artifacts: env.STAGE_NAME))
                    }
                    // runTest handles SCM notification via stepResult
                } // stage('provisionNodes on Leap 15 with slurm') {
                stage('Commit Pragma tests') {
                    steps {
                        script {
                            stages = ['Functional on Leap 15',
                                      'Functional on CentOS 7',
                                      'Functional on EL 8',
                                      'Functional on EL 9',
                                      'Functional Hardware Medium',
                                      'Functional Hardware Medium Verbs Provider',
                                      'Functional Hardware Medium UCX Provider',
                                      'Functional Hardware Large']
                            commits = [[pragmas: [''],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, isPr(), !isPr(), !isPr(), true, !isPr()]],
                                       [pragmas: ['Skip-test: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [true, true, true, true, true, true, true, true]],
                                       [pragmas: ['Skip-func-test: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [true, true, true, true, true, true, true, true]],
                                       [pragmas: ['Skip-func-test-vm: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [true, true, true, true, !isPr(), !isPr(), true, !isPr()]],
                                       [pragmas: ['Skip-func-test-vm-all: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [true, true, true, true, !isPr(), !isPr(), true, !isPr()]],
                                       [pragmas: ['Skip-func-test-leap15: true\n' +
                                                  'Skip-func-test-el7: true\n' +
                                                  'Skip-func-test-el8: true\n' +
                                                  'Skip-func-test-el9: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [true, true, true, true, !isPr(), !isPr(), true, !isPr()]],
                                       [pragmas: ['Skip-func-test-leap15: false\n' +
                                                  'Skip-func-test-el7: false\n' +
                                                  'Skip-func-test-el8: false\n' +
                                                  'Skip-func-test-el9: false'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [false, false, false, false, !isPr(), !isPr(), true, !isPr()]],
                                       [pragmas: ['Skip-func-test-hw: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, isPr(), true, true, true, true]],
                                       [pragmas: ['Skip-func-test-hw-medium: true\n' +
                                                  'Skip-func-test-hw-medium-verbs-provider: true\n' +
                                                  'Skip-func-test-hw-medium-ucx-provider: true\n' +
                                                  'Skip-func-test-hw-large: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, isPr(), true, true, true, true]],
                                       [pragmas: ['Skip-func-test-hw-medium: false\n' +
                                                  'Skip-func-test-hw-medium-verbs-provider: false\n' +
                                                  'Skip-func-test-hw-medium-ucx-provider: false\n' +
                                                  'Skip-func-test-hw-large: false'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, isPr(), false, false, false, false]],
                                       [pragmas: ['Skip-func-hw-test: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, isPr(), true, true, true, true]],
                                       [pragmas: ['Skip-func-hw-test-medium: true\n' +
                                                  'Skip-func-hw-test-medium-verbs-provider: true\n' +
                                                  'Skip-func-hw-test-medium-ucx-provider: true\n' +
                                                  'Skip-func-hw-test-large: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, isPr(), true, true, true, true]],
                                       [pragmas: ['Skip-func-hw-test-medium: false\n' +
                                                  'Skip-func-hw-test-medium-verbs-provider: false\n' +
                                                  'Skip-func-hw-test-medium-ucx-provider: false\n' +
                                                  'Skip-func-hw-test-large: false'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, isPr(), false, false, false, false]],
                                       [pragmas: ['Run-daily-stages: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, isPr(), false, false, false, false]],
                                       [pragmas: ['Skip-build-leap15-rpm: true\n' +
                                                  'Skip-build-el7-rpm: true\n' +
                                                  'Skip-build-el8-rpm: true\n' +
                                                  'Skip-build-el9-rpm: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [true, true, true, true, true, true, true, true]],
                                       [pragmas: ['Skip-build-leap15-rpm: false\n' +
                                                  'Skip-build-el7-rpm: false\n' +
                                                  'Skip-build-el8-rpm: false\n' +
                                                  'Skip-build-el9-rpm: false'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, isPr(), !isPr(), !isPr(), true, !isPr()]]]
                            errors = 0
                            commits.each { commit ->
                                cm = 'Test commit\n\n'
                                commit.pragmas.each { pragma ->
                                    cm += "${pragma}\n"
                                }
                                println('-------------------------')
                                println('Unit test commit message:')
                                println('')
                                println(cm)
                                actual_skips = []
                                i = 0
                                // save current value
                                env.pragmas_sav = env.pragmas
                                // assign Map to env. var to serialize it
                                env.tmp_pragmas = pragmasToEnv(cm.stripIndent())
                                stages.each { stage ->
                                    withEnv(['STAGE_NAME=' + stage,
                                             'UNIT_TEST=true',
                                             'pragmas=' + env.tmp_pragmas,
                                             'COMMIT_MESSAGE=' + cm.stripIndent()]) {
                                        actual_skips.add(skipStage(commit_msg: cm))
                                        if (actual_skips[i] != commit.skips[i]) { errors++ }
                                        i++
                                    }
                                }
                                println('')
                                println('  Result  Expect  Actual  Stage')
                                println('  ------  ------  ------  ------------------------------------------')
                                i = 0
                                stages.each { stage ->
                                    result = 'PASS'
                                    expect = 'run '
                                    actual = 'run '
                                    if (commit.skips[i]) { expect = 'skip' }
                                    if (actual_skips[i]) { actual = 'skip' }
                                    if (expect != actual) { result = 'FAIL' }
                                    println('  ' + result + '    ' + expect + '    ' + actual + '    ' + stage)
                                    i++
                                }
                                println('')
                                cachedCommitPragma(clear: true)
                                // restore actual pragmas for later stages
                                env.pragmas = env.pragmas_sav
                            }
                            assert(errors == 0)
                        }
                        /* tests for all stages:
                              1. Test-tag: datamover
                              2. Features: datamover
                              3. Test-tag: datamover
                                 Features: foobar
                              4. Features: datamover foobar
                              5. Test-tag: datamover foobar
                              Would like to add this case, but see below…
                              6. Test-tag: datamover
                                 Features: foobar
                                 Skip-list: test_to_skip:DAOS-1234
                        */
                        // lots more test cases could be cooked up, to be sure
                        script {
                            stages = [[name: 'Fake CentOS 7 Functional stage',
                                       tag: 'vm'],
                                      [name: 'Fake CentOS 7 Functional Hardware Medium stage',
                                       tag: 'hw,medium,-provider'],
                                      [name: 'Fake CentOS 7 Functional Hardware Medium Provider stage',
                                       tag: 'hw,medium,provider'],
                                      [name: 'Fake CentOS 7 Functional Hardware Large stage',
                                       tag: 'hw,large']]
                            commits = [[tags: [[tag: 'Test-tag', value: 'datamover']],
                                       tag_template: '@commits.value@,@stages.tag@'],
                                       [tags: [[tag: 'Features', value: 'datamover']],
                                        tag_template: 'pr,@stages.tag@ ' +
                                                      'pr,@commits.value@,@stages.tag@ ' +
                                                      'daily_regression,@commits.value@,@stages.tag@ ' +
                                                      'full_regression,@commits.value@,@stages.tag@'],
                                       [tags: [[tag: 'Test-tag', value: 'datamover'],
                                               [tag: 'Features', value: 'foobar']],
                                        tag_template: '@commits.value@,@stages.tag@ ' +
                                                      'pr,foobar,@stages.tag@ ' +
                                                      'daily_regression,foobar,@stages.tag@ ' +
                                                      'full_regression,foobar,@stages.tag@'],
                                       [tags: [[tag: 'Features', value: 'datamover foobar']],
                                        tag_template: 'pr,@stages.tag@ ' +
                                                      'pr,datamover,@stages.tag@ ' +
                                                      'daily_regression,datamover,@stages.tag@ ' +
                                                      'full_regression,datamover,@stages.tag@ ' +
                                                      'pr,foobar,@stages.tag@ ' +
                                                      'daily_regression,foobar,@stages.tag@ ' +
                                                      'full_regression,foobar,@stages.tag@'],
                                       [tags: [[tag: 'Test-tag', value: 'datamover foobar']],
                                        tag_template: 'datamover,@stages.tag@ foobar,@stages.tag@'],
                                    /* this one doesn't quite work due to the @commits.value@ substituion
                                       not accounting for the skip-list
                                       [tags: [[tag: 'Test-tag', value: 'datamover'],
                                               [tag: 'Features', value: 'foobar'],
                                               [tag: 'Skip-list', value: 'test_to_skip:DAOS-1234']],
                                        tag_template: '@commits.value@,@stages.tag@ ' +
                                                      'pr,foobar,-test_to_skip,@stages.tag@ ' +
                                                      'daily_regression,foobar,-test_to_skip,@stages.tag@ ' +
                                                      'full_regression,foobar,-test_to_skip,@stages.tag@'] */]
                            commits.eachWithIndex { commit, index ->
                                cm = '''\
                                        Test commit\n'''
                                commit.tags.each { tag ->
                                    cm += """\

                                        ${tag.tag}: ${tag.value}"""
                                }
                                echo 'Running test with commit #' + (index + 1) + ' with commit:\n' + cm
                                // assign Map to env. var to serialize it
                                env.tmp_pragmas = pragmasToEnv(cm.stripIndent())
                                stages.each { stage ->
                                    withEnv(['STAGE_NAME=' + stage.name,
                                             'UNIT_TEST=true',
                                             'pragmas=' + env.tmp_pragmas,
                                             'COMMIT_MESSAGE=' + cm.stripIndent()]) {
                                        cmp = commit.tag_template.replace('@commits.value@', commit.tags[0].value)
                                        cmp = cmp.replace('@stages.tag@', stage.tag)
                                        assert(parseStageInfo()['test_tag'] == cmp),
                                               parseStageInfo()['test_tag'] + ' != ' + cmp
                                    }
                                }
                            }
                        }
                        // Unit test for skipFunctionalTestStage()
                        script {
                            println('------------------------------------------------------------')
                            println('skipFunctionalTestStage() Unit Test')
                            println('------------------------------------------------------------')
                            sequences = [
                                [description: 'run_if_pr=true',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: null, run_if_pr: true],
                                 pragma: '',
                                 expect: false],
                                [description: 'run_if_pr=false',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: null, run_if_pr: false],
                                 pragma: '',
                                 /* groovylint-disable-next-line UnnecessaryGetter */
                                 expect: isPr()],
                                [description: 'Distro set',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: 'el8', run_if_pr: true],
                                 pragma: '',
                                 expect: false],
                                [description: 'Skip-test: true',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: null, run_if_pr: true],
                                 pragma: 'Skip-test: true',
                                 expect: true],
                                [description: 'Skip-func-test: true',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: null, run_if_pr: true],
                                 pragma: 'Skip-func-test: true',
                                 expect: true],
                                [description: 'Skip-func-test-hw: true',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: null, run_if_pr: true],
                                 pragma: 'Skip-func-test-hw: true',
                                 expect: true],
                                [description: 'Skip-func-test-hw-medium: true',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: null, run_if_pr: true],
                                 pragma: 'Skip-func-test-hw-medium: true',
                                 expect: true],
                                [description: 'Skip-func-test-hw: false',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: null, run_if_pr: false],
                                 pragma: 'Skip-func-test-hw: false',
                                 expect: false],
                                [description: 'Skip-func-test-hw-medium: false',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: null, run_if_pr: false],
                                 pragma: 'Skip-func-test-hw-medium: false',
                                 expect: false],
                                [description: 'Skip-func-test-hw-large: true',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: null, run_if_pr: true],
                                 pragma: 'Skip-func-test-hw-large: true',
                                 expect: false],
                                [description: 'Run-daily-stages: true',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: null, run_if_pr: false],
                                 pragma: 'Run-daily-stages: true',
                                 expect: false],
                                [description: 'Run-daily-stages: false',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: null, run_if_pr: true],
                                 pragma: 'Run-daily-stages: false',
                                 expect: true],
                                [description: 'Run-GHA: true',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: null, run_if_pr: true],
                                 pragma: 'Run-GHA: true',
                                 expect: true],
                                [description: 'Skip-build-el8-rpm: true',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: 'el8', run_if_pr: true],
                                 pragma: 'Skip-build-el8-rpm: true',
                                 expect: true],
                                [description: 'Skip-build-el9-rpm: true',
                                 kwargs: [tags: 'pr', pragma_suffix: '-hw-medium', distro: 'el8', run_if_pr: true],
                                 pragma: 'Skip-build-el9-rpm: true',
                                 expect: false],
                            ]
                            errors = 0
                            sequences.eachWithIndex { sequence, index ->
                                cachedCommitPragma(clear: true)
                                println("${index}: ${sequence['description']}")
                                commit_message = "Test commit\n\n${sequence['pragma']}\n"
                                println(commit_message)
                                env.tmp_pragmas = pragmasToEnv(commit_message.stripIndent())
                                withEnv(['STAGE_NAME=Functional Hardware Medium',
                                         'UNIT_TEST=true',
                                         'pragmas=' + env.tmp_pragmas,
                                         'COMMIT_MESSAGE=' + commit_message.stripIndent()]) {
                                    sequences[index]['actual'] = skipFunctionalTestStage(sequence['kwargs'])
                                    println("skipFunctionalTestStage() -> ${sequence['actual']}")
                                    if (sequence['expect'] != sequence['actual']) { errors++ }
                                }
                                println('------------------------------------------------------------')
                            }
                            println('')
                            println('  Result  Expect  Actual  Test')
                            println('  ------  ------  ------  ----------------------------------------------')
                            sequences.eachWithIndex { sequence, index ->
                                result = 'PASS'
                                expect = 'run '
                                actual = 'run '
                                if (sequence['expect']) { expect = 'skip' }
                                if (sequence['actual']) { actual = 'skip' }
                                if (expect != actual) { result = 'FAIL' }
                                println("  ${result}    ${expect}    ${actual}    ${index}: " +
                                        "${sequence['description']} (${sequence['kwargs']})")
                            }
                            assert(errors == 0)
                        }
                    } // steps
                } // stage ('Commit Pragma tests')
                stage('Self Unit Test') {
                    steps {
                        selfUnitTest()
                    } // steps
                } // stage ('Self Unit Test')
            } // parallel
        } // stage('Test')
        stage('DAOS Build and Test') {
            when {
                beforeAgent true
                expression {
                    currentBuild.currentResult == 'SUCCESS' && !skipStage()
                }
            }
            matrix {
                axes {
                    axis {
                        name 'TEST_BRANCH'
                        values 'master',
                               'release/2.6',
                               'weekly-testing',
                               'weekly-2.6-testing'
                    }
                }
                when {
                    beforeAgent true
                    expression {
                        // Need to pass the stage name: https://issues.jenkins.io/browse/JENKINS-69394
                        !skipStage([stage_name: 'Test Library',
                                    axes: env.TEST_BRANCH.replaceAll('/', '-')])
                    }
                }
                stages {
                    stage('Test Library') {
                        steps {
                            buildDaosJob(env.TEST_BRANCH, params.BuildPriority)
                        } // steps
                        post {
                            success {
                                script {
                                    setupDownstreamTesting.cleanup('daos-stack/daos', env.TEST_BRANCH)
                                }
                            }
                            always {
                                writeFile file: stageStatusFilename(env.STAGE_NAME,
                                                                    env.TEST_BRANCH.replaceAll('/', '-')),
                                          text: currentBuild.currentResult + '\n'
                                /* groovylint-disable-next-line LineLength */
                                archiveArtifacts artifacts: stageStatusFilename(env.STAGE_NAME,
                                                                                env.TEST_BRANCH.replaceAll('/', '-'))
                            }
                        } // post
                    } // stage('Test Library')
                } // stages
            } // matrix
        } // stage('DAOS Build and Test')
    } // stages
    post {
        always {
            job_status_update('final_status')
            job_status_write()
        }
        unsuccessful {
            notifyBrokenBranch branches: target_branch
        }
    }
} // pipeline
