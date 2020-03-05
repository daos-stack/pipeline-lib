#!/usr/bin/env groovy
// Copyright (c) 2018-2020 Intel Corporation
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the
// Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
// FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
// DEALINGS IN THE SOFTWARE.

// The @library line needs to be edited in a PR for adding a test.
// Adding a test requires two PRs.  One to add the test.
// That PR should be landed with out deleting the PR branch.
// Then a second PR submitted to comment out the @Library line, and when it
// is landed, both PR branches can be deleted.

// @Library(value="pipeline-lib@my_pr_branch") _

pipeline {
    agent { label 'lightweight' }
    environment {
        BAHTTPS_PROXY = "${env.HTTP_PROXY ? '--build-arg HTTP_PROXY="' + env.HTTP_PROXY + '" --build-arg http_proxy="' + env.HTTP_PROXY + '"' : ''}"
        BAHTTP_PROXY = "${env.HTTP_PROXY ? '--build-arg HTTPS_PROXY="' + env.HTTPS_PROXY + '" --build-arg https_proxy="' + env.HTTPS_PROXY + '"' : ''}"
        UID = sh(script: "id -u", returnStdout: true)
        SSH_KEY_FILE='ci_key'
        SSH_KEY_ARGS="-i$SSH_KEY_FILE"
        CLUSH_ARGS="-o$SSH_KEY_ARGS"
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
                                       echo "<failure bla bla bla/>" > pipeline-test-failure.xml''',
                        junit_files: "*.xml non-exist*.xml",
                        failure_artifacts: env.STAGE_NAME
                }
                post {
                    unsuccessful {
                        daosStackNotifyStatus(
                            description: env.STAGE_NAME,
                            context: 'test/' + env.STAGE_NAME,
                            status: 'FAILURE')
                    }
                    success {
                        daosStackNotifyStatus(
                            description: env.STAGE_NAME,
                            context: 'test/' + env.STAGE_NAME,
                            status: 'SUCCESS')
                    }
                }
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
                                       echo "<error bla bla bla/>" > pipeline-test-error.xml''',
                        junit_files: "*.xml non-exist*.xml",
                        failure_artifacts: env.STAGE_NAME
                }
                post {
                    unsuccessful {
                        daosStackNotifyStatus(
                            description: env.STAGE_NAME,
                            context: 'test/' + env.STAGE_NAME,
                            status: 'FAILURE')
                    }
                    success {
                        daosStackNotifyStatus(
                            description: env.STAGE_NAME,
                            context: 'test/' + env.STAGE_NAME,
                            status: 'SUCCESS')
                    }
                }
            } // stage('grep JUnit results tests error case')
            stage('publishToRepository tests') {
                agent {
                    dockerfile {
                        filename 'docker/Dockerfile.centos.7'
                        label 'docker_runner'
                        additionalBuildArgs  '--build-arg UID=$(id -u)'
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
                        daosStackNotifyStatus(
                            description: env.STAGE_NAME,
                            context: 'test/' + env.STAGE_NAME,
                            status: 'FAILURE')
                    }
                    success {
                        daosStackNotifyStatus(
                            description: env.STAGE_NAME,
                            context: 'test/' + env.STAGE_NAME,
                            status: 'SUCCESS')
                    }
                }
            } //stage('publishToRepository tests')
            stage('provisionNodes_with_slurm_el7') {
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
                                   inst_rpms: "slurm slurm-example-configs" +
                                              " slurm-slurmctld slurm-slurmd" +
                                              " ipmctl"
                            runTest(
                        script: '''NODE=${NODELIST%%,*}
                                   ssh $SSH_KEY_ARGS jenkins@$NODE "set -x
                                     set -e
                                     which scontrol"''',
                        junit_files: null,
                        failure_artifacts: env.STAGE_NAME)
                }
                post {
                    unsuccessful {
                        daosStackNotifyStatus(
                            description: env.STAGE_NAME,
                            context: 'test/' + env.STAGE_NAME,
                            status: 'FAILURE')
                    }
                    success {
                        daosStackNotifyStatus(
                            description: env.STAGE_NAME,
                            context: 'test/' + env.STAGE_NAME,
                            status: 'SUCCESS')
                    }
                }
            } //stage('provisionNodes_with_slurm_el7')
            stage('provisionNodes_with_slurm_leap15') {
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
                            runTest(
                        script: '''NODE=${NODELIST%%,*}
                                   ssh $SSH_KEY_ARGS jenkins@$NODE "set -x
                                     set -e
                                     which scontrol"''',
                        junit_files: null,
                        failure_artifacts: env.STAGE_NAME)
                }
                post {
                    unsuccessful {
                        daosStackNotifyStatus(
                            description: env.STAGE_NAME,
                            context: 'test/' + env.STAGE_NAME,
                            status: 'FAILURE')
                    }
                    success {
                        daosStackNotifyStatus(
                            description: env.STAGE_NAME,
                            context: 'test/' + env.STAGE_NAME,
                            status: 'SUCCESS')
                    }
                }
            } //stage('provisionNodes_with_slurm_leap15')
          } // parallel
        } // stage('Test')
    }
}
