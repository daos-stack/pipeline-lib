#!/usr/bin/env groovy
/* Copyright (C) 2019-2020 Intel Corporation
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

pipeline {
    agent { label 'lightweight' }
    environment {
        SSH_KEY_FILE='ci_key'
        SSH_KEY_ARGS="-i$SSH_KEY_FILE"
        CLUSH_ARGS="-o$SSH_KEY_ARGS"
        BUILDARGS = dockerBuildArgs(cachebust: False, add_repos: False)
    }

    options {
        ansiColor('xterm')
    }

    stages {
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
                    dockerfile {
                        filename 'docker/Dockerfile.centos.7'
                        label 'docker_runner'
                        additionalBuildArgs  '--build-arg UID=$(id -u)'
                    }
                }
                steps {
                    runTest script: '''set -ex
                                       rm -f *.xml
                                       echo "<failure bla bla bla/>" > \
                                         pipeline-test-failure.xml''',
                        junit_files: "*.xml non-exist*.xml",
                        failure_artifacts: env.STAGE_NAME
                }
                // runTest handles SCM notification via stepResult
            } // stage('grep JUnit results tests failure case')
            stage('grep JUnit results tests error case') {
                agent {
                    dockerfile {
                        filename 'docker/Dockerfile.centos.7'
                        label 'docker_runner'
                        additionalBuildArgs  '--build-arg UID=$(id -u)'
                    }
                }
                steps {
                    runTest script: '''set -ex
                                       rm -f *.xml
                                       echo "<error bla bla bla/>" > \
                                         pipeline-test-error.xml''',
                        junit_files: "*.xml non-exist*.xml",
                        failure_artifacts: env.STAGE_NAME
                }
                // runTest handles SCM notification via stepResult
            } // stage('grep JUnit results tests error case')
            stage('publishToRepository tests') {
                // currently broken.
                when {
                    beforeAgent true
                    expression { false }
                }
                agent {
                    dockerfile {
                        filename 'docker/Dockerfile.centos.7'
                        label 'docker_runner'
                        additionalBuildArgs  '$BUILDARGS'
                    }
                }
                steps {
                    // Populate an artifact directory
                    copyArtifacts projectName: '/daos-stack/libfabric/master',
                                  filter: 'artifacts/centos7/**',
                                  target: 'artifact'
                    publishToRepository(
                        product: 'zzz_pl-' + env.BRANCH_NAME + '_' +
                                  env.BUILD_ID,
                        format: 'yum',
                        maturity: 'test',
                        tech: 'el-7',
                        repo_dir: 'artifact/artifacts/centos7',
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
            } //stage('publishToRepository tests')
            stage('provisionNodes with release/0.9 Repo') {
                when {
                    beforeAgent true
                    expression { env.NO_CI_TESTING != "true" }
                }
                agent {
                    label 'ci_vm1'
                }
                steps {
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
                                   distro: 'el7',
                                   profile: 'daos_ci',
                                   node_count: 1,
                                   snapshot: true,
                                   inst_repos: "daos@master"
                    runTest script: '''NODE=${NODELIST%%,*}
                                       ssh $SSH_KEY_ARGS jenkins@$NODE "set -ex
                                       yum --disablerepo=\\* \
                                           --enablerepo=build\\* makecache"''',
                            junit_files: null,
                            failure_artifacts: env.STAGE_NAME
                }
                // runTest handles SCM notification via stepResult
            } // stage('provisionNodes with master Repo')
            stage('provisionNodes with slurm EL7') {
                when {
                    beforeAgent true
                    expression { env.NO_CI_TESTING != "true" }
                }
                agent {
                    label 'ci_vm1'
                }
                steps {
                    provisionNodes NODELIST: env.NODELIST,
                                   distro: 'el7',
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
                }
                // runTest handles SCM notification via stepResult
            } //stage('provisionNodes with slurm EL7')
            stage('provisionNodes with slurm Leap15') {
                when {
                    beforeAgent true
                    allOf {
                        expression { env.NO_CI_TESTING != "true" }
                    }
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
                }
                // runTest handles SCM notification via stepResult
            } //stage('provisionNodes_with_slurm_leap15')
          } // parallel
        } // stage('Test')
    }
}
