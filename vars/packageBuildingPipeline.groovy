#!/usr/bin/groovy
/* Copyright (C) 2019 Intel Corporation
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted for any purpose (including commercial purposes)
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions, and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions, and the following disclaimer in the
 *    documentation and/or materials provided with the distribution.
 *
 * 3. In addition, redistributions of modified forms of the source or binary
 *    code must carry prominent notices stating that the original code was
 *    changed and the date of the change.
 *
 *  4. All publications or advertising materials mentioning features or use of
 *     this software are asked, but not required, to acknowledge that it was
 *     developed by Intel Corporation and credit the contributors.
 *
 * 5. Neither the name of Intel Corporation, nor the name of any Contributor
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
// To use a test branch (i.e. PR) until it lands to master
// I.e. for testing library changes
//@Library(value="pipeline-lib@your_branch") _

def call(Map pipeline_args) {
    pipeline {
        agent none

        stages {
            stage('Cancel Previous Builds') {
                when { changeRequest() }
                steps {
                    cancelPreviousBuilds()
                }
            }
            stage('Lint') {
                parallel {
                    stage('RPM Lint') {
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.centos.7'
                                label 'docker_runner'
                                args  '--group-add mock' +
                                      ' --cap-add=SYS_ADMIN' +
                                      ' --privileged=true'
                                additionalBuildArgs  '--build-arg UID=$(id -u)'
                            }
                        }
                        steps {
                            sh 'make ' +
                               pipeline_args.get('make args', '') +
                               ' rpmlint'
                        }
                    }
                    stage('Check Packaging') {
                        agent { label 'lightweight' }
                        steps {
                            checkoutScm url: 'https://github.com/daos-stack/packaging.git',
                                        checkoutDir: 'packaging-module'
                            catchError(stageResult: 'UNSTABLE', buildResult: 'SUCCESS') {
                                sh 'make ' + pipeline_args.get('make args', '') +
                                 ''' PACKAGING_CHECK_DIR=packaging-module \
                                    packaging_check'''
                            }
                        }
                        post {
                            success { noop() }
                            unsuccessful {
                                emailext body: 'Packaging out of date for ' + jobName() + '.\n' +
                                               "You should update it and submit your PR again.",
                                         recipientProviders: [
                                              [$class: 'DevelopersRecipientProvider'],
                                              [$class: 'RequesterRecipientProvider']
                                         ],
                                         subject: 'Packaging is out of date for ' + jobName()
                            }
                        }
                    } //stage('Check Packaging')
                } // parallel
            } //stage('Lint')
            stage('Build') {
                parallel {
                    stage('Build on CentOS 7') {
                        when {
                            beforeAgent true
                            expression { pipeline_args['distros'].contains('centos7')}
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.centos.7'
                                label 'docker_runner'
                                args  '--group-add mock' +
                                      ' --cap-add=SYS_ADMIN' +
                                      ' --privileged=true'
                                additionalBuildArgs '--build-arg UID=$(id -u)' +
                                                    ' --build-arg JENKINS_URL=' +
                                                    env.JENKINS_URL
                            }
                        }
                        steps {
                            sh label: "Build package",
                               script: '''rm -rf artifacts/centos7/
                                  mkdir -p artifacts/centos7/
                                  make ''' + pipeline_args.get('make args', '') + ' chrootbuild ' +
                                  pipeline_args.get('add_make_targets', '')
                        }
                        post {
                            success {
                                sh label: "Collect artifacts",
                                   script: '''(cd /var/lib/mock/epel-7-x86_64/result/ &&
                                       cp -r . $OLDPWD/artifacts/centos7/)\n''' +
                                       pipeline_args.get('add_archiving_cmds', '') +
                                      '\ncreaterepo artifacts/centos7/'
                                publishToRepository product: pipeline_args['name'],
                                                    format: 'yum',
                                                    maturity: 'stable',
                                                    tech: 'el-7',
                                                    repo_dir: 'artifacts/centos7/'
                                archiveArtifacts artifacts: pipeline_args.get('add_artifacts', ''),
                                                            allowEmptyArchive: true
                            }
                            unsuccessful {
                                sh label: "Collect artifacts",
                                   script: '''mockroot=/var/lib/mock/epel-7-x86_64
                                      artdir=$PWD/artifacts/centos7
                                      cp -af _topdir/SRPMS $artdir
                                      (cd $mockroot/result/ &&
                                       cp -r . $artdir)
                                      (if cd $mockroot/root/builddir/build/BUILD/*/; then
                                           find . -name configure -printf %h\\\\n | \
                                           while read dir; do
                                               if [ ! -f $dir/config.log ]; then
                                                   continue
                                               fi
                                               tdir="$artdir/autoconf-logs/$dir"
                                               mkdir -p $tdir
                                               cp -a $dir/config.log $tdir/
                                           done
                                       fi)'''
                            }
                            cleanup {
                                archiveArtifacts artifacts: 'artifacts/centos7/**'
                            }
                        }
                    } //stage('Build on CentOS 7')
                    stage('Build on SLES 12.3') {
                        when {
                            beforeAgent true
                            allOf {
                                environment name: 'SLES12_3_DOCKER', value: 'true'
                                expression { pipeline_args['distros'].contains('sles12.3')}
                            }
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.sles.12.3'
                                label 'docker_runner'
                                args  '--privileged=true'
                                additionalBuildArgs '--build-arg UID=$(id -u) ' +
                                                    ' --build-arg JENKINS_URL=' +
                                                    env.JENKINS_URL
                            }
                        }
                        steps {
                            sh label: "Build package",
                               script: '''rm -rf artifacts/sles12.3/
                                  mkdir -p artifacts/sles12.3/
                                  make chrootbuild'''
                        }
                        post {
                            success {
                                sh label: "Collect artifacts",
                                   script: '''mockbase=/var/tmp/build-root/home/abuild
                                      mockroot=$mockbase/rpmbuild
                                      artdir=$PWD/artifacts/sles12.3
                                      (cd $mockroot &&
                                       cp {RPMS/*,SRPMS}/* $artdir)
                                      createrepo $artdir/'''
                                publishToRepository product: pipeline_args['name'],
                                                    format: 'yum',
                                                    maturity: 'stable',
                                                    tech: 'sles-12',
                                                    repo_dir: 'artifacts/sles12.3/'
                            }
                            unsuccessful {
                                sh label: "Collect artifacts",
                                   script: '''mockbase=/var/tmp/build-root/home/abuild
                                      mockroot=$mockbase/rpmbuild
                                      artdir=$PWD/artifacts/sles12.3
                                      (if cd $mockroot/BUILD; then
                                           find . -name configure -printf %h\\\\n | \
                                           while read dir; do
                                               if [ ! -f $dir/config.log ]; then
                                                   continue
                                               fi
                                               tdir="$artdir/autoconf-logs/$dir"
                                               mkdir -p $tdir
                                               cp -a $dir/config.log $tdir/
                                           done
                                       fi)'''
                            }
                            cleanup {
                                archiveArtifacts artifacts: 'artifacts/sles12.3/**'
                            }
                        }
                    } //stage('Build on SLES 12.3')
                    stage('Build on Leap 42.3') {
                        when {
                            beforeAgent true
                            expression { pipeline_args['distros'].contains('leap42.3')}
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.leap.42.3'
                                label 'docker_runner'
                                args  '--privileged=true'
                                additionalBuildArgs '--build-arg UID=$(id -u) ' +
                                                    ' --build-arg JENKINS_URL=' +
                                                    env.JENKINS_URL
                            }
                        }
                        steps {
                            sh label: "Build package",
                               script: '''rm -rf artifacts/leap42.3/
                                  mkdir -p artifacts/leap42.3/
                                  make chrootbuild'''
                        }
                        post {
                            success {
                                sh label: "Collect artifacts",
                                   script: '''mockbase=/var/tmp/build-root/home/abuild
                                      mockroot=$mockbase/rpmbuild
                                      artdir=$PWD/artifacts/leap42.3
                                      (cd $mockroot &&
                                       cp {RPMS/*,SRPMS}/* $artdir)
                                      createrepo $artdir/'''
                                publishToRepository product: pipeline_args['name'],
                                                    format: 'yum',
                                                    maturity: 'stable',
                                                    tech: 'leap-42',
                                                    repo_dir: 'artifacts/leap42.3/'
                            }
                            unsuccessful {
                                sh label: "Collect artifacts",
                                   script: '''mockbase=/var/tmp/build-root/home/abuild
                                      mockroot=$mockbase/rpmbuild
                                      artdir=$PWD/artifacts/leap42.3
                                      (if cd $mockroot/BUILD; then
                                       find . -name configure -printf %h\\\\n | \
                                       while read dir; do
                                           if [ ! -f $dir/config.log ]; then
                                               continue
                                           fi
                                           tdir="$artdir/autoconf-logs/$dir"
                                           mkdir -p $tdir
                                           cp -a $dir/config.log $tdir/
                                           done
                                       fi)'''
                            }
                            cleanup {
                                archiveArtifacts artifacts: 'artifacts/leap42.3/**'
                            }
                        }
                    } //stage('Build on Leap 42.3')
                    stage('Build on Leap 15') {
                        when {
                            beforeAgent true
                            expression { pipeline_args['distros'].contains('leap15')}
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.leap.15'
                                label 'docker_runner'
                                args  '--privileged=true'
                                additionalBuildArgs '--build-arg UID=$(id -u) ' +
                                                    ' --build-arg JENKINS_URL=' +
                                                    env.JENKINS_URL
                            }
                        }
                        steps {
                            sh label: "Build package",
                               script: '''rm -rf artifacts/leap15/
                                  mkdir -p artifacts/leap15/
                                  make chrootbuild'''
                        }
                        post {
                            success {
                                sh label: "Collect artifacts",
                                   script: '''mockbase=/var/tmp/build-root/home/abuild
                                      mockroot=$mockbase/rpmbuild
                                      artdir=$PWD/artifacts/leap15
                                      (cd $mockroot &&
                                       cp {RPMS/*,SRPMS}/* $artdir)
                                      createrepo $artdir/'''
                                publishToRepository product: pipeline_args['name'],
                                                    format: 'yum',
                                                    maturity: 'stable',
                                                    tech: 'leap-15',
                                                    repo_dir: 'artifacts/leap15/'
                            }
                            unsuccessful {
                                sh label: "Collect artifacts",
                                   script: '''mockbase=/var/tmp/build-root/home/abuild
                                      mockroot=$mockbase/rpmbuild
                                      artdir=$PWD/artifacts/leap15
                                      (if cd $mockroot/BUILD; then
                                       find . -name configure -printf %h\\\\n | \
                                       while read dir; do
                                           if [ ! -f $dir/config.log ]; then
                                               continue
                                           fi
                                           tdir="$artdir/autoconf-logs/$dir"
                                           mkdir -p $tdir
                                           cp -a $dir/config.log $tdir/
                                           done
                                       fi)'''
                            }
                            cleanup {
                                archiveArtifacts artifacts: 'artifacts/leap15/**'
                            }
                        }
                    } //stage('Build on Leap 15')
                    stage('Build on Ubuntu 18.04') {
                        when {
                            beforeAgent true
                            expression { pipeline_args['distros'].contains('ubuntu18.04')}
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.ubuntu.18.04'
                                label 'docker_runner'
                                additionalBuildArgs '--build-arg UID=$(id -u) ' +
                                                    ' --build-arg JENKINS_URL=' +
                                                    env.JENKINS_URL +
                                                    ' --build-arg CACHEBUST=' +
                                                    currentBuild.startTimeInMillis
                            }
                        }
                        steps {
                            sh label: "Build package",
                               script: '''rm -rf artifacts/ubuntu18.04/
                                  mkdir -p artifacts/ubuntu18.04/
                                  : "${DEBEMAIL:="$env.DAOS_EMAIL"}"
                                  : "${DEBFULLNAME:="$env.DAOS_FULLNAME"}"
                                  export DEBEMAIL
                                  export DEBFULLNAME
                                  make debs'''
                        }
                        post {
                            success {
                                sh label: "Collect artifacts",
                                   script: '''ln -v \
                                       _topdir/BUILD/*{.build,.changes,.deb,.dsc,.gz,.xz} \
                                       artifacts/ubuntu18.04/
                                      pushd artifacts/ubuntu18.04/
                                        dpkg-scanpackages . /dev/null | \
                                          gzip -9c > Packages.gz
                                      popd'''
                                publishToRepository product: pipeline_args['name'],
                                                    format: 'apt',
                                                    maturity: 'stable',
                                                    tech: 'ubuntu-18.04',
                                                    repo_dir: 'artifacts/ubuntu18.04/'
                            }
                            unsuccessful {
                                sh label: "Collect artifacts",
                                   script: "cat _topdir/BUILD/*.build",
                                   returnStatus: true
                            }
                            cleanup {
                                archiveArtifacts artifacts: 'artifacts/ubuntu18.04/**'
                            }
                        }
                    } //stage('Build on Ubuntu 18.04')
                    stage('Build on Ubuntu 18.10') {
                        when {
                            beforeAgent true
                            expression { pipeline_args['distros'].contains('ubuntu18.10')}
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.ubuntu.18.10'
                                label 'docker_runner'
                                additionalBuildArgs '--build-arg UID=$(id -u) ' +
                                                    ' --build-arg JENKINS_URL=' +
                                                    env.JENKINS_URL +
                                                    ' --build-arg CACHEBUST=' +
                                                    currentBuild.startTimeInMillis
                            }
                        }
                        steps {
                            sh '''rm -rf artifacts/ubuntu18.10/
                                  mkdir -p artifacts/ubuntu18.10/
                                  mkdir -p _topdir
                                  : "${DEBEMAIL:="$env.DAOS_EMAIL"}"
                                  : "${DEBFULLNAME:="$env.DAOS_FULLNAME"}"
                                  export DEBEMAIL
                                  export DEBFULLNAME
                                  make debs'''
                        }
                        post {
                            success {
                                sh label: "Collect artifacts",
                                   script: '''ln -v \
                                       _topdir/BUILD/*{.build,.changes,.deb,.dsc,.gz,.xz} \
                                       artifacts/ubuntu18.10/
                                      pushd artifacts/ubuntu18.10/
                                        dpkg-scanpackages . /dev/null | \
                                          gzip -9c > Packages.gz
                                      popd'''
                                publishToRepository product: pipeline_args['name'],
                                                    format: 'apt',
                                                    maturity: 'stable',
                                                    tech: 'ubuntu-18.10',
                                                    repo_dir: 'artifacts/ubuntu18.10/'
                            }
                            unsuccessful {
                                sh label: "Collect artifacts",
                                   script: "cat _topdir/BUILD/*.build",
                                   returnStatus: true
                            }
                            cleanup {
                                archiveArtifacts artifacts: 'artifacts/ubuntu18.10/**'
                            }
                        }
                    } //stage('Build on Ubuntu 18.10')
                }
            } //stage('Build')
        } // stages
    } // pipeline
} // call
