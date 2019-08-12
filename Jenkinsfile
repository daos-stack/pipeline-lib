#!/usr/bin/env groovy
// Copyright (c) 2018 Intel Corporation
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

@Library(value="pipeline-lib@corci-734") _

pipeline {
    agent none

    stages {
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
                    product: 'zzz_pl-' + env.BRANCH_NAME + '_' + env.BUILD_ID,
                    format: 'yum',
                    maturity: 'test',
                    tech: 'el7',
                    repo_dir: 'artifact/artifacts/centos7',
                    download_dir: 'artifact/download',
                    test: true)
            }
            post {
                unsuccessful {
                    daosStackNotifyStatus description: env.STAGE_NAME,
                                          context: 'test/' + env.STAGE_NAME,
                                          status: 'FAILURE'
                }
                success {
                    daosStackNotifyStatus description: env.STAGE_NAME,
                                          context: 'test/' + env.STAGE_NAME,
                                          status: 'SUCCESS'
                }
            }
        }
    }
}
