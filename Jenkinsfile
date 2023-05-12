#!/usr/bin/env groovy
// groovylint-disable DuplicateListLiteral, DuplicateNumberLiteral,
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

String test_branch(String target) {
    return 'ci-' + JOB_NAME.replaceAll('/', '-') +
            '-' + target.replaceAll('/', '-')
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

    stages {
        stage('Get Commit Message') {
            steps {
                pragmasToEnv()
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
            parallel {
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
                            tech: 'el-8',
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
                } //stage('publishToRepository RPM tests')
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
                } //stage('publishToRepository DEB tests')
                stage('provisionNodes with release/2.2 Repo') {
                    when {
                        beforeAgent true
                        expression { env.NO_CI_TESTING != 'true' }
                    }
                    agent {
                        label 'ci_vm1'
                    }
                    steps {
                        job_step_update(
                            provisionNodes(NODELIST: env.NODELIST,
                                           distro: 'el8',
                                           profile: 'daos_ci',
                                           node_count: '1',
                                           snapshot: true,
                                           inst_repos: 'daos@release/2.2'))
                        job_step_update(
                            /* groovylint-disable-next-line GStringExpressionWithinString */
                            runTest(script: '''NODE=${NODELIST%%,*}
                                               ssh $SSH_KEY_ARGS jenkins@$NODE "set -ex
                                               yum -y --disablerepo=\\* --enablerepo=build\\* makecache"''',
                                    junit_files: null,
                                    failure_artifacts: env.STAGE_NAME))
                    }
                    // runTest handles SCM notification via stepResult
                } //stage('provisionNodes with release/0.9 Repo')
                stage('provisionNodes with master Repo') {
                    when {
                        beforeAgent true
                        expression { env.NO_CI_TESTING != 'true' }
                    }
                    agent {
                        label 'ci_vm1'
                    }
                    steps {
                        job_step_update(
                            provisionNodes(NODELIST: env.NODELIST,
                                           distro: 'el8',
                                           profile: 'daos_ci',
                                           node_count: 1,
                                           snapshot: true,
                                           inst_repos: 'daos@master'))
                        job_step_update(
                            /* groovylint-disable-next-line GStringExpressionWithinString */
                            runTest(script: '''NODE=${NODELIST%%,*}
                                               ssh $SSH_KEY_ARGS jenkins@$NODE "set -ex
                                               dnf -y makecache"''',
                                    junit_files: null,
                                    failure_artifacts: env.STAGE_NAME))
                    }
                    // runTest handles SCM notification via stepResult
                } // stage('provisionNodes with master Repo')
                stage('provisionNodes with slurm EL8') {
                    when {
                        beforeAgent true
                        expression { env.NO_CI_TESTING != 'true' }
                    }
                    agent {
                        label 'ci_vm1'
                    }
                    steps {
                        job_step_update(
                            provisionNodes(NODELIST: env.NODELIST,
                                           distro: 'el8',
                                           profile: 'daos_ci',
                                           node_count: 1,
                                           snapshot: true,
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
                } //stage('provisionNodes with slurm EL8')
                stage('provisionNodes with slurm Leap15') {
                    when {
                        beforeAgent true
                        expression { env.NO_CI_TESTING != 'true' }
                    }
                    agent {
                        label 'ci_vm1'
                    }
                    steps {
                        job_step_update(
                            provisionNodes(NODELIST: env.NODELIST,
                                           distro: 'opensuse15',
                                           profile: 'daos_ci',
                                           node_count: 1,
                                           snapshot: true,
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
                } //stage('provisionNodes_with_slurm_leap15')
                stage('Commit Pragma tests') {
                    steps {
                        script {
                            stages = ['Functional on Leap 15',
                                      'Functional on CentOS 7',
                                      'Functional on EL 8',
                                      'Functional Hardware Medium',
                                      'Functional Hardware Medium Verbs Provider',
                                      'Functional Hardware Medium UCX Provider',
                                      'Functional Hardware Large']
                            commits = [[pragmas: [''],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, !isPr(), !isPr(), isPr(), !isPr()]],
                                       [pragmas: ['Skip-test: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [true, true, true, true, true, true, true]],
                                       [pragmas: ['Skip-func-test: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [true, true, true, true, true, true, true]],
                                       [pragmas: ['Skip-func-test-vm: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [true, true, true, !isPr(), !isPr(), isPr(), !isPr()]],
                                       [pragmas: ['Skip-func-test-vm-all: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [true, true, true, !isPr(), !isPr(), isPr(), !isPr()]],
                                       [pragmas: ['Skip-func-test-el8: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), true, !isPr(), !isPr(), isPr(), !isPr()]],
                                       [pragmas: ['Skip-func-test-leap15: false'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [false, isPr(), false, !isPr(), !isPr(), true, !isPr()]],
                                       [pragmas: ['Skip-func-test-hw: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, true, true, true, true]],
                                       [pragmas: ['Skip-func-test-hw-medium: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, true, !isPr(), isPr(), !isPr()]],
                                       [pragmas: ['Skip-func-test-hw-medium-ucx-provider: false'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, !isPr(), !isPr(), !isPr(), !isPr()]],
                                       [pragmas: ['Skip-func-hw-test: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, true, true, true, true]],
                                       [pragmas: ['Skip-func-hw-test-medium: true'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, true, !isPr(), isPr(), !isPr()]],
                                       [pragmas: ['Skip-func-hw-test-medium-ucx-provider: false'],
                                        /* groovylint-disable-next-line UnnecessaryGetter */
                                        skips: [isPr(), isPr(), false, !isPr(), !isPr(), !isPr(), !isPr()]]]
                            errors = 0
                            commits.each { commit ->
                                cm = 'Test commit\n\n'
                                commit.pragmas.each { pragma ->
                                    cm += "${pragma}\n"
                                }
                                println('-------------------------')
                                println('Unit test commit message:')
                                println('')
                                println(commit)
                                println('')
                                i = 0
                                // assign Map to env. var to serialize it
                                env.tmp_pragmas = pragmasToEnv(cm.stripIndent())
                                stages.each { stage ->
                                    withEnv(['STAGE_NAME=' + stage,
                                             'UNIT_TEST=true',
                                             'pragmas=' + env.tmp_pragmas,
                                             'COMMIT_MESSAGE=' + cm.stripIndent()]) {
                                        println('  stage:                     ' + stage)
                                        println('  skipped (expect ==actual): ' +
                                                commit.skips[i] + ' == ' + skipStage(commit_msg: cm))
                                        if (skipStage(commit_msg: cm) != commit.skips[i]) {
                                            println('  status: FAIL')
                                            errors++
                                        }
                                        else {
                                            println('  status: PASS')
                                        }
                                        i++
                                    }
                                }
                                cachedCommitPragma(clear: true)
                            }
                            assert(errors != 0)
                        }
                        /* tests for all stages:
                              1. Test-tag: datamover
                              2. Features: datamover
                              3. Test-tag: datamover
                                 Features: foobar
                              4. Features: datamover foobar
                              5. Test-tag: datamover foobar
                        */
                        // lots more test cases could be cooked up, to be sure
                        script {
                            stages = [[name: 'Fake CentOS 7 Functional stage',
                                       tag: '-hw'],
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
                                                      'daily_regression,@commits.value@,@stages.tag@ ' +
                                                      'full_regression,@commits.value@,@stages.tag@'],
                                       /* groovylint-disable-next-line DuplicateMapLiteral */
                                       [tags: [[tag: 'Test-tag', value: 'datamover'],
                                               [tag: 'Features', value: 'foobar']],
                                        tag_template: '@commits.value@,@stages.tag@'],
                                       [tags: [[tag: 'Features', value: 'datamover foobar']],
                                        tag_template: 'pr,@stages.tag@ ' +
                                                      'daily_regression,datamover,@stages.tag@ ' +
                                                      'full_regression,datamover,@stages.tag@ ' +
                                                      'daily_regression,foobar,@stages.tag@ ' +
                                                      'full_regression,foobar,@stages.tag@'],
                                       [tags: [[tag: 'Test-tag', value: 'datamover foobar']],
                                        tag_template: 'datamover,@stages.tag@ foobar,@stages.tag@']]
                            commits.each { commit ->
                                cm = '''\
                                        Test commit\n'''
                                commit.tags.each { tag ->
                                    cm += """\

                                        ${tag.tag}: ${tag.value}"""
                                }
                                // assign Map to env. var to serialize it
                                env.tmp_pragmas = pragmasToEnv(cm.stripIndent())
                                stages.each { stage ->
                                    withEnv(['STAGE_NAME=' + stage.name,
                                             'UNIT_TEST=true',
                                             'pragmas=' + env.tmp_pragmas,
                                             'COMMIT_MESSAGE=' + cm.stripIndent()]) {
                                        cmp = commit.tag_template.replace('@commits.value@', commit.tags[0].value)
                                        cmp = cmp.replace('@stages.tag@', stage.tag)
                                        // Useful for debugging since Jenkins'
                                        // assert() is pretty lame
                                        // println('assert(' + parseStageInfo()['test_tag'] + " == ${cmp})")
                                        assert(parseStageInfo()['test_tag'] == cmp)
                                    }
                                }
                            }
                        }
                    } // steps
                } // stage ('Commit Pragma tests')
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
                               'release/2.2',
                               'weekly-testing',
                               'weekly-testing-2.2'
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
                            // TODO: find out what the / escape is.  I've already tried both %2F and %252F
                            //       https://issues.jenkins.io/browse/JENKINS-68857
                            // Instead, we will create a branch specifically to test on
                            withCredentials([[$class: 'UsernamePasswordMultiBinding',
                                            credentialsId: 'daos_jenkins_project_github_access',
                                            usernameVariable: 'GH_USER',
                                            passwordVariable: 'GH_PASS']]) {
                                sh label: 'Create or update test branch',
                                   script: 'branch_name=' + test_branch(env.TEST_BRANCH) + '''
                                           source_branch=origin/''' +
                                           cachedCommitPragma("Test-${env.TEST_BRANCH}-branch",
                                                              env.TEST_BRANCH) + '''
                                            # dir for checkout since all runs in the matrix share the same
                                            # workspace
                                            dir="daos-''' + env.TEST_BRANCH.replaceAll('/', '-') + '''"
                                            if cd $dir; then
                                                git remote update
                                                git fetch origin
                                            else
                                                git clone https://''' + env.GH_USER + ':' +
                                                                        env.GH_PASS +
                                                              """@github.com/daos-stack/daos.git \$dir
                                                cd \$dir
                                            fi
                                            if git checkout \$branch_name; then
                                                git rebase \$source_branch
                                            else
                                                if ! git checkout -b \$branch_name \$source_branch; then
                                                    echo "Error trying to create branch \$branch_name"
                                                    exit 1
                                                fi
                                                # if a PR...
                                                if [ -n "\$CHANGE_BRANCH" ]; then
                                                    # edit to use this PR as the pipeline-lib branch
                                                    sed -i -e '/^\\/\\/@Library/s/^\\/\\///' """ +
                                                          "-e \"/^@Library/s/'/\\\"/g\" " +
                                                          "-e '/^@Library/s/-lib@.*/-lib@" +
                                                        (env.CHANGE_BRANCH ?: '').replaceAll('\\/', '\\\\/') +
                                                        "\") _/' Jenkinsfile" + '''
                                                fi
                                                if [ -n "$(git status -s)" ]; then
                                                    git commit -m "Update pipeline-lib branch to self''' +
                                                      (cachedCommitPragma('Test-skip-build', 'true') == 'true' ? '' :
                                                               '\n\nSkip-unit-tests: true') + '''" Jenkinsfile
                                                fi
                                            fi
                                            git push -f origin $branch_name:$branch_name
                                            sleep 10'''
                                sh label: 'Delete local test branch',
                                   script: '''dir="daos-''' + env.TEST_BRANCH.replaceAll('/', '-') + '''"
                                              if ! cd $dir; then
                                                  echo "$dir does not exist"
                                                  exit 1
                                              fi
                                              git checkout origin/master
                                              if ! git branch -D ''' + test_branch(env.TEST_BRANCH) + '''; then
                                                  git status
                                                  git branch -a
                                                  exit 1
                                              fi'''
                            } // withCredentials
                            build job: 'daos-stack/daos/' + test_branch(env.TEST_BRANCH),
                                  parameters: [string(name: 'TestTag',
                                                      value: 'load_mpi test_core_files test_pool_info_query'),
                                               string(name: 'CI_RPM_TEST_VERSION',
                                                      value: cachedCommitPragma('Test-skip-build', 'true') == 'true' ?
                                                               daosLatestVersion(env.TEST_BRANCH) : ''),
                                               booleanParam(name: 'CI_UNIT_TEST', value: false),
                                               booleanParam(name: 'CI_FI_el8_TEST', value: true),
                                               booleanParam(name: 'CI_FUNCTIONAL_el7_TEST', value: true),
                                               booleanParam(name: 'CI_MORE_FUNCTIONAL_PR_TESTS', value: true),
                                               booleanParam(name: 'CI_FUNCTIONAL_el8_TEST', value: true),
                                               booleanParam(name: 'CI_FUNCTIONAL_leap15_TEST', value: true),
                                               booleanParam(name: 'CI_SCAN_RPMS_el7_TEST', value: true),
                                               booleanParam(name: 'CI_RPMS_el7_TEST', value: true),
                                               booleanParam(name: 'CI_medium_TEST', value: true),
                                               booleanParam(name: 'CI_large_TEST', value: false)
                                              ]
                        } //steps
                        post {
                            success {
                                /* groovylint-disable-next-line DuplicateMapLiteral */
                                withCredentials([[$class: 'UsernamePasswordMultiBinding',
                                                credentialsId: 'daos_jenkins_project_github_access',
                                                usernameVariable: 'GH_USER',
                                                passwordVariable: 'GH_PASS']]) {
                                    sh label: 'Delete test branch',
                                       script: 'if ! git push https://$GH_USER:$GH_PASS@github.com/daos-stack/' +
                                                  'daos.git --delete ' + test_branch(env.TEST_BRANCH) + '''; then
                                                    echo "Error trying to delete branch ''' +
                                                    test_branch(env.TEST_BRANCH) + '''"
                                                    git remote -v
                                                    exit 1
                                                fi'''
                                } // withCredentials
                            } // success
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
