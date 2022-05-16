#!/usr/bin/env groovy
/* Copyright (C) 2019-2022 Intel Corporation
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
//@Library(value="pipeline-lib@my_branch_name") _
@Library(value="pipeline-lib@corci-1200") _

job_status_internal = [:]

def job_status_write() {
    if (!env.DAOS_STACK_JOB_STATUS_DIR) {
        return
    }
    String job_name = env.JOB_NAME.replace('/', '_')
    job_name += '_' + env.BUILD_NUMBER
    String file_name = env.DAOS_STACK_JOB_STATUS_DIR + '/' + job_name

    String job_status_text = writeYaml data: job_status_internal,
                                       returnText: true

    // Need to use shell script for creating files that are not
    // in the workspace.
    sh label: "Write jenkins_job_status ${file_name}",
       script: "echo \"${job_status_text}\" >> ${file_name}"
}

def job_status_update(String name=env.STAGE_NAME,
                      String value=currentBuild.currentResult) {
    name = name.replace(' ', '_')
    name = name.replace('.', '_')
    job_status_internal[name] = value
}

def job_step_update(value) {
    // Wrapper around a pipeline step to obtain a status.
    name = env.STAGE_NAME
    name = name.replace(' ', '_')
    name = name.replace('.', '_')
    job_status_internal[name] = value
}

pipeline {
    agent { label 'lightweight' }
    libraries {
      lib("pipeline-lib@${env.BRANCH_NAME}")
    }

    environment {
        SSH_KEY_FILE='ci_key'
        SSH_KEY_ARGS="-i$SSH_KEY_FILE"
        CLUSH_ARGS="-o$SSH_KEY_ARGS"
    }

    options {
        ansiColor('xterm')
    }

    stages {
        stage('Get Commit Message') {
            steps {
                script {
                    env.COMMIT_MESSAGE = sh(script: 'git show -s --format=%B',
                                            returnStdout: true).trim()
                }
            }
        }
        stage('Cancel Previous Builds') {
            when { changeRequest() }
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
                        // jobStatus('JUNIT_failure')
                        job_step_update(
                            runTest(script: '''set -ex
                                           rm -f *.xml
                                           echo "<failure bla bla bla/>" > \
                                             pipeline-test-failure.xml''',
                            junit_files: "*.xml non-exist*.xml",
                            failure_artifacts: env.STAGE_NAME))
                    }
                    // runTest handles SCM notification via stepResult
                } // stage('grep JUnit results tests failure case')
                stage('grep JUnit results tests error case') {
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
                            junit_files: "*.xml non-exist*.xml",
                            failure_artifacts: env.STAGE_NAME))
                    }
                    // runTest handles SCM notification via stepResult
                } // stage('grep JUnit results tests error case')
                stage('publishToRepository RPM tests') {
                    when {
                        beforeAgent true
                        expression { env.NO_CI_TESTING != "true" }
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
                        cleanup {
                            job_status_update()
                        }
                    }
                } //stage('publishToRepository RPM tests')
                stage('publishToRepository DEB tests') {
                    when {
                        beforeAgent true
                        expression { env.NO_CI_TESTING != "true" }
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
                        cleanup {
                            job_status_update()
                        }
                    }
                } //stage('publishToRepository DEB tests')
                stage('provisionNodes with release/0.9 Repo') {
                    when {
                        beforeAgent true
                        expression { env.NO_CI_TESTING != "true" }
                    }
                    agent {
                        label 'ci_vm1'
                    }
                    steps {
                        // remove the ci/ folder so that provisionNodesV1 is used
                        fileOperations([folderDeleteOperation(folderPath: 'ci')])
                        provisionNodes NODELIST: env.NODELIST,
                                       distro: 'el7',
                                       profile: 'daos_ci',
                                       node_count: '1',
                                       snapshot: true,
                                       inst_repos: "daos@release/0.9"
                        runTest script: '''NODE=${NODELIST%%,*}
                                           ssh $SSH_KEY_ARGS jenkins@$NODE "set -ex
                                           yum --disablerepo=\\* --enablerepo=build\\* makecache"''',
                                junit_files: null,
                                failure_artifacts: env.STAGE_NAME
                        job_status_update()
                    }
                    // runTest handles SCM notification via stepResult
                } //stage('provisionNodes with release/0.9 Repo')
                stage('provisionNodes with release/2.0 Repo') {
                    when {
                        beforeAgent true
                        expression { env.NO_CI_TESTING != "true" }
                    }
                    agent {
                        label 'ci_vm1'
                    }
                    steps {
                        provisionNodes NODELIST: env.NODELIST,
                                       distro: 'el8',
                                       profile: 'daos_ci',
                                       node_count: '1',
                                       snapshot: true,
                                       inst_repos: "daos@release/2.0"
                        runTest script: '''NODE=${NODELIST%%,*}
                                           ssh $SSH_KEY_ARGS jenkins@$NODE "set -ex
                                           yum --disablerepo=\\* --enablerepo=build\\* makecache"''',
                                junit_files: null,
                                failure_artifacts: env.STAGE_NAME
                        job_status_update()
                    }
                    // runTest handles SCM notification via stepResult
                } //stage('provisionNodes with release/0.9 Repo')
                stage('provisionNodes with master Repo') {
                    when {
                        beforeAgent true
                        expression { env.NO_CI_TESTING != "true" }
                    }
                    agent {
                        label 'ci_vm1'
                    }
                    steps {
                        provisionNodes NODELIST: env.NODELIST,
                                       distro: 'el8',
                                       profile: 'daos_ci',
                                       node_count: 1,
                                       snapshot: true,
                                       inst_repos: "daos@master"
                        runTest script: '''NODE=${NODELIST%%,*}
                                           ssh $SSH_KEY_ARGS jenkins@$NODE "set -ex
                                           dnf makecache"''',
                                junit_files: null,
                                failure_artifacts: env.STAGE_NAME
                        job_status_update()
                    }
                    // runTest handles SCM notification via stepResult
                } // stage('provisionNodes with master Repo')
                stage('provisionNodes with slurm EL8') {
                    when {
                        beforeAgent true
                        expression { env.NO_CI_TESTING != "true" }
                    }
                    agent {
                        label 'ci_vm1'
                    }
                    steps {
                        provisionNodes NODELIST: env.NODELIST,
                                       distro: 'el8',
                                       profile: 'daos_ci',
                                       node_count: 1,
                                       snapshot: true,
                                       inst_rpms: "slurm" +
                                                  " slurm-slurmctld slurm-slurmd" +
                                                  " ipmctl"
                        runTest script: '''NODE=${NODELIST%%,*}
                                           ssh $SSH_KEY_ARGS jenkins@$NODE "set -ex
                                           which scontrol"''',
                                junit_files: null,
                                failure_artifacts: env.STAGE_NAME
                        job_status_update()
                    }
                    // runTest handles SCM notification via stepResult
                } //stage('provisionNodes with slurm EL8')
                stage('provisionNodes with slurm Leap15') {
                    when {
                        beforeAgent true
                        expression { env.NO_CI_TESTING != "true" }
                    }
                    agent {
                        label 'ci_vm1'
                    }
                    steps {
                        provisionNodes NODELIST: env.NODELIST,
                                       distro: 'opensuse15',
                                       profile: 'daos_ci',
                                       node_count: 1,
                                       snapshot: true,
                                       inst_rpms: "slurm ipmctl"
                        runTest script: '''NODE=${NODELIST%%,*}
                                           ssh $SSH_KEY_ARGS jenkins@$NODE "set -ex
                                           which scontrol"''',
                                junit_files: null,
                                failure_artifacts: env.STAGE_NAME
                        job_status_update()
                    }
                    // runTest handles SCM notification via stepResult
                } //stage('provisionNodes_with_slurm_leap15')
                stage ('Commit Pragma tests') {
                    steps {
                        script {
                            stages = ["Functional on Leap 15",
                                      "Functional on CentOS 7",
                                      "Functional on EL 8",
                                      "Functional Hardware Small",
                                      "Functional Hardware Medium",
                                      "Functional Hardware Large"]
                            commits = [[pragmas: ['Skip-func-test-leap15: false'],
                                        skips: [false, true, false, false, false, false]],
                                       [pragmas: [''],
                                        skips: [true, true, false, false, false, false]],
                                       [pragmas: ['Skip-func-hw-test-small: true'],
                                        skips: [true, true, false, true, false, false]]]
                            commits.each { commit ->
                                cm = """\
                                        Test commit\n\n"""
                                commit.pragmas.each { pragma ->
                                    cm += """\
                                        ${pragma}\n"""
                                }
                                i = 0
                                stages.each { stage ->
                                    withEnv(['STAGE_NAME=' + stage,
                                             'UNIT_TEST=true',
                                             'COMMIT_MESSAGE=' + cm.stripIndent()]) {
                                        // Useful for debugging since Jenkins'
                                        // assert() is pretty lame
                                        //println('For stage: ' + stage + ', assert(skipStage(commit_msg: ' + cm.stripIndent() + ') == ' + commit.skips[i] + ')')
                                        assert(skipStage(commit_msg: cm.stripIndent()) == commit.skips[i])
                                        i++
                                    }
                                }
                                cachedCommitPragma(clear: true)
                            }
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
                                      [name: 'Fake CentOS 7 Functional Hardware Small stage',
                                       tag: 'hw,small'],
                                      [name: 'Fake CentOS 7 Functional Hardware Medium stage',
                                       tag: 'hw,medium'],
                                      [name: 'Fake CentOS 7 Functional Hardware Large stage',
                                       tag: 'hw,large']]
                            commits = [[tags: [[tag: "Test-tag", value: 'datamover']],
                                       tag_template: '@commits.value@,@stages.tag@'],
                                       [tags: [[tag: "Features", value: 'datamover']],
                                        tag_template: 'pr,@stages.tag@ daily_regression,@commits.value@,@stages.tag@'],
                                       [tags: [[tag: "Test-tag", value: 'datamover'],
                                               [tag: "Features", value: 'foobar']],
                                        tag_template: '@commits.value@,@stages.tag@'],
                                       [tags: [[tag: "Features", value: 'datamover foobar']],
                                        tag_template: 'pr,@stages.tag@ daily_regression,datamover,@stages.tag@ daily_regression,foobar,@stages.tag@'],
                                       [tags: [[tag: "Test-tag", value: 'datamover foobar']],
                                        tag_template: 'datamover,@stages.tag@ foobar,@stages.tag@']]
                            commits.each { commit ->
                                cm = """\
                                        Test commit\n"""
                                commit.tags.each { tag ->
                                    cm += """\

                                        ${tag.tag}: ${tag.value}"""
                                }
                                stages.each { stage ->
                                    withEnv(['STAGE_NAME=' + stage.name,
                                             'UNIT_TEST=true',
                                             'COMMIT_MESSAGE=' + cm.stripIndent()]) {
                                        cmp = commit.tag_template.replace('@commits.value@', commit.tags[0].value)
                                        cmp = cmp.replace('@stages.tag@', stage.tag)
                                        // Useful for debugging since Jenkins'
                                        // assert() is pretty lame
                                        //println('assert(' + parseStageInfo()['test_tag'] + " == ${cmp})")
                                        assert(parseStageInfo()['test_tag'] == cmp)
                                    }
                                }
                            }
                        }
                        job_status_update()
                    } // steps
                } // stage ('Commit Pragma tests') */
            } // parallel
        } // stage('Test')
    }
    post {
        always {
            job_status_update('final_status')
            job_status_write()
        }
    } // post
}
