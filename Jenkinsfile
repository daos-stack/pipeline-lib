#!/usr/bin/env groovy
/* groovylint-disable DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral */
// groovylint-disable DuplicateStringLiteral, NestedBlockDepth, VariableName
/* Copyright 2019-2024 Intel Corporation
 * Copyright 2025-2026 Hewlett Packard Enterprise Development LP
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

Void distro_version_test(String branch, String distro, String expected) {
    println("Test branch: ${branch}, distro: ${distro}, expecting: ${expected}")
    withEnv(["BRANCH_NAME=${branch}"]) {
        String dv = distroVersion(distro)
        if (dv == null || !dv.startsWith(expected)) {
            unstable("distroVersion() returned ${dv} " +
                      "instead of string starting with '${expected}'")
        }
    }
    return
}

/* groovylint-disable-next-line CompileStatic */
pipeline {
    agent { label 'lightweight' }

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
                stage('JUnit Tests') {
                    agent {
                        label 'JUnit_jdk_tests'
                    }
                    steps {
                        sh '''
                        set -euxo pipefail
                        rm -rf ${HOME}/.gradle
                        ./gradle-init.sh

                        python3 -m venv mitm-env
                        source mitm-env/bin/activate
                        pip install mitmproxy

                        if ! command -v mitmdump >/dev/null 2>&1; then
                            echo "mitmdump is not installed on this agent" >&2
                            exit 1
                        fi

                        mitmdump --listen-host 127.0.0.1 --listen-port 8080 --set ssl_insecure=true -v > mitmproxy.log 2>&1 &
                        MITM_PID=$!
                        trap 'kill ${MITM_PID} >/dev/null 2>&1 || true; wait ${MITM_PID} >/dev/null 2>&1 || true' EXIT

                        MITM_CA="${HOME}/.mitmproxy/mitmproxy-ca-cert.pem"
                        if [[ ! -f "${MITM_CA}" ]]; then
                            echo "mitmproxy CA certificate not found at ${MITM_CA}" >&2
                            exit 1
                        fi
                        rm -f mitmproxy-truststore.jks
                        keytool -importcert -noprompt -alias mitmproxy \
                            -file "${MITM_CA}" -keystore mitmproxy-truststore.jks \
                            -storepass changeit

                        JAVA_TOOL_OPTIONS="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=8080 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=${WORKSPACE}/mitmproxy-truststore.jks -Djavax.net.ssl.trustStorePassword=changeit" \
                            ./gradle spotlessCheck test --debug

                        echo
                        cat mitmproxy.log
                        '''
                    }
                    post {
                        always {
                            junit 'build/test-results/test/*.xml'
                            archiveArtifacts artifacts: 'mitmproxy.log', allowEmptyArchive: true
                        }
                    }
                }
                stage('daosLatestVersion() tests') {
                    steps {
                        script {
                            assert(daosLatestVersion('master', 'el8').matches(/2.9\.\d+.*/))
                            assert(daosLatestVersion('release/2.4', 'el8').matches(/2.[34]\.\d+.*/))
                            assert(daosLatestVersion('release/2.6', 'el8').matches(/2.[56]\.\d+.*/))
                            assert(daosLatestVersion('release/2.8', 'el8').matches(/2.[78]\.\d+.*/))
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
                        distro_version_test('release/2.8', 'el8', '8')
                        distro_version_test('release/2.8', 'el9', '9')
                        distro_version_test('release/2.8', 'leap15', '15')
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
                        /* disabled until https://daosio.atlassian.net/browse/SRE-3161 is fixed
                        expression { env.NO_CI_TESTING != 'true' } */
                        expression { false }
                    }
                    agent {
                        dockerfile {
                            filename 'docker/Dockerfile.el.8'
                            label 'docker_runner'
                            additionalBuildArgs dockerBuildArgs()
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
                        /* disabled until https://daosio.atlassian.net/browse/SRE-3161 is fixed
                        expression { env.NO_CI_TESTING != 'true' } */
                        expression { false }
                    }
                    agent {
                        dockerfile {
                            filename 'docker/Dockerfile.el.8'
                            label 'docker_runner'
                            additionalBuildArgs dockerBuildArgs(cachebust: false)
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
                        /* disabled until https://daosio.atlassian.net/browse/SRE-3162 is fixed
                        expression {
                            env.NO_CI_TESTING != 'true' &&
                            cachedCommitPragma('Skip-el9-provisioning-test') != 'true' &&
                            daosLatestVersion('master') != ''
                        }
                        */
                        expression { false }
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
                        /* disabled until https://daosio.atlassian.net/browse/SRE-3162 is fixed
                        expression {
                            env.NO_CI_TESTING != 'true' &&
                            daosLatestVersion('master') != ''
                        }
                        */
                        expression { false }
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
                        /* disabled until https://daosio.atlassian.net/browse/SRE-3162 is fixed
                        expression {
                            env.NO_CI_TESTING != 'true' &&
                            daosLatestVersion('master') != ''
                        }
                        */
                        expression { false }
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
