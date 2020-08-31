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
// To test changes to this pipeline you need push changes to this file to
// a PR and then to modify the
//@Library(value="pipeline-lib@your_branch") _ line the Jenkinsfile for a
//project to point to the above branch.  Then build and test as usual

def call(Map pipeline_args) {
    if (!pipeline_args) {
        pipeline_args = [:]
    }

    if (pipeline_args['distros']) {
        distros = pipeline_args['distros']
    } else {
        distros = ['centos7', 'leap15', 'ubuntu_rolling']
    }

    if (pipeline_args['name']) {
        package_name = pipeline_args['name']
    } else {
        package_name = jobName()
    }

    if (pipeline_args['publish_branch']) {
        publish_branch = pipeline_args['publish_branch']
    } else {
        publish_branch = 'master'
    }

    pipeline {
        agent { label 'lightweight' }

        /* DO NOT enable this without going and selecting builds in
         * all RPM build projects that should be marked Keep-forever
         * as we have builds in Jenkins which are not in Nexus, that
         * want to keep.
         * options { buildDiscarder(logRotator(numToKeepStr: '10')) }
         */

        environment {
            QUICKBUILD = sh(script: "git show -s --format=%B | grep \"^Quick-build: true\"",
                            returnStatus: true)
            PACKAGING_BRANCH = commitPragma('Packaging-branch', 'master')
        }
        stages {
            stage('Cancel Previous Builds') {
                when { changeRequest() }
                steps {
                    cancelPreviousBuilds()
                }
            }
            stage('Lint') {
                when {
                    beforeAgent true
                    allOf {
                        expression { return env.QUICKBUILD == '1' }
                    }
                }
                parallel {
                    stage('RPM Lint') {
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.mockbuild'
                                label 'docker_runner'
                                args  '--group-add mock' +
                                      ' --cap-add=SYS_ADMIN' +
                                      ' --privileged=true'
                                additionalBuildArgs dockerBuildArgs()
                            }
                        }
                        steps {
                            sh label: "SPEC file linting check",
                               script: 'make ' +
                                       pipeline_args.get('make args', '') +
                                       ' rpmlint',
                               returnStatus: !pipeline_args.get('rpmlint_check',
                                                                 true)
                        }
                    }
                    stage('SPEC file tests') {
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.mockbuild'
                                label 'docker_runner'
                                args  '--group-add mock' +
                                      ' --cap-add=SYS_ADMIN' +
                                      ' --privileged=true'
                                additionalBuildArgs dockerBuildArgs()
                            }
                        }
                        steps {
                            sh label: "SPEC file sanity check",
                               script: '[ "$(make ' +
                                       pipeline_args.get('make args', '') +
                                       ' CHROOT_NAME=epel-7-x86_64 show_sources)" != "" ]'
                        }
                        post {
                            unsuccessful {
                                sh label: "Diagnose SPEC file sanity check failure",
                                   script: '''set +x
                                              eval args=($(make ''' +
                                              pipeline_args.get('make args', '') + ' ' +
                                           '''show_common_rpm_args 2>/dev/null))
                                              vars=$(CHROOT_NAME="epel-7-x86_64" spectool --debug "${args[@]}" $(make ''' +
                                              pipeline_args.get('make args', '') + ' ' +
                                           '''show_spec 2>/dev/null) 2>&1 | sed -e 's/: /=/' -e 's/ /_/g')
                                              eval $vars
                                              cat $stderr_filename
                                              echo "in:"
                                              cat -n $temp_spec_filename'''
                            }
                        }
                    }
                    stage('Check Packaging') {
                        agent { label 'lightweight' }
                        steps {
                            checkoutScm url: 'https://github.com/daos-stack/packaging.git',
                                        checkoutDir: 'packaging-module',
                                        branch: env.PACKAGING_BRANCH
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
                    stage('Coverity') {
                        when {
                            beforeAgent true
                            allOf {
                                expression { pipeline_args.get('coverity','') != '' }
                            }
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.coverity'
                                label 'docker_runner'
                                args '--privileged=true'
                                additionalBuildArgs dockerBuildArgs()
                            }
                        }
                        steps {
                            coverityToolDownload tool_path: './cov_analysis',
                                            project: pipeline_args['coverity']
                            sh label: "Coverity",
                               script: '''set -e
                                          if [ ! -e ./cov_analysis/bin ]; then
                                              exit
                                          fi
                                          PATH+=:${WORKSPACE}/cov_analysis/bin
                                          make clean
                                          cov-build -dir cov-int make'''
                        }
                        post {
                            success {
                                sh label: "Collect Coverity Success artifacts",
                                   script: '''mkdir -p coverity
                                              rm -f coverity/*
                                              if [ -e cov-int ]; then
                                                  tar czf coverity/coverity.tgz cov-int
                                              fi'''
                            }
                            unsuccessful {
                                sh label: "Collect Coverity Fail artifacts",
                                   script: '''mkdir -p coverity
                                              rm -f coverity/*
                                              if [ -f cov-int/build-log.txt ]; then
                                                mv cov-int/build-log.txt coverity/cov-build-log.txt
                                              fi'''
                            }
                            cleanup {
                                archiveArtifacts artifacts: 'coverity/*',
                                allowEmptyArchive: true
                            }
                        }
                    } //stage('Build on CentOS 7')
                    stage('Build on CentOS 7') {
                        when {
                            beforeAgent true
                            allOf {
                                expression { distros.contains('centos7') }
                            }
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.mockbuild'
                                label 'docker_runner'
                                args  '--group-add mock' +
                                      ' --cap-add=SYS_ADMIN' +
                                      ' --privileged=true'
                                additionalBuildArgs dockerBuildArgs()
                            }
                        }
                        steps {
                            sh label: "Build package",
                               script: '''rm -rf artifacts/centos7/
                                          mkdir -p artifacts/centos7/
                                          make CHROOT_NAME="epel-7-x86_64" ''' +
                                       pipeline_args.get('make args', '') + ' chrootbuild ' +
                                       pipeline_args.get('add_make_targets', '')
                        }
                        post {
                            success {
                                sh label: "Collect artifacts",
                                   script: '''(cd /var/lib/mock/epel-7-x86_64/result/ &&
                                              cp -r . $OLDPWD/artifacts/centos7/)\n''' +
                                              pipeline_args.get('add_archiving_cmds', '').replace("<distro>", "centos7") +
                                             '\ncreaterepo artifacts/centos7/'
                                publishToRepository product: package_name,
                                                    format: 'yum',
                                                    maturity: 'stable',
                                                    tech: 'el-7',
                                                    repo_dir: 'artifacts/centos7/',
                                                    publish_branch: publish_branch
                                archiveArtifacts artifacts: pipeline_args.get('add_artifacts',
                                                                              'no-optional-artifacts-to-archive'),
                                                            allowEmptyArchive: true
                            }
                            unsuccessful {
                                sh label: "Build Log",
                                   script: '''mockroot=/var/lib/mock/epel-7-x86_64
                                              ls -l $mockroot/result/
                                              cat $mockroot/result/{root,build}.log
                                              artdir=$PWD/artifacts/centos7
                                              cp -af _topdir/SRPMS $artdir
                                              (cd $mockroot/result/ &&
                                               cp -r . $artdir)'''
                            }
                            always {
                                sh label: "Collect config.log(s)",
                                   script: '''(if cd /var/lib/mock/epel-7-x86_64/root/builddir/build/BUILD/*/; then
                                                   find . -name configure -printf %h\\\\n | \
                                                   while read dir; do
                                                       if [ ! -f $dir/config.log ]; then
                                                           continue
                                                       fi
                                                       tdir="$OLDPWD/artifacts/centos7/autoconf-logs/$dir"
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
                    stage('Build on CentOS 8') {
                        when {
                            beforeAgent true
                            allOf {
                                expression { distros.contains('centos8') }
                            }
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.mockbuild'
                                label 'docker_runner'
                                args  '--group-add mock' +
                                      ' --cap-add=SYS_ADMIN' +
                                      ' --privileged=true'
                                additionalBuildArgs dockerBuildArgs()
                            }
                        }
                        steps {
                            sh label: "Build package",
                               script: '''rm -rf artifacts/centos8/
                                          mkdir -p artifacts/centos8/
                                          make CHROOT_NAME="epel-8-x86_64" ''' +
                                       pipeline_args.get('make args', '') + ' chrootbuild ' +
                                       pipeline_args.get('add_make_targets', '')
                        }
                        post {
                            success {
                                sh label: "Collect artifacts",
                                   script: '''(cd /var/lib/mock/epel-8-x86_64/result/ &&
                                              cp -r . $OLDPWD/artifacts/centos8/)\n''' +
                                              pipeline_args.get('add_archiving_cmds', '').replace("<distro>", "centos8") +
                                             '\ncreaterepo artifacts/centos8/'
                                publishToRepository product: package_name,
                                                    format: 'yum',
                                                    maturity: 'stable',
                                                    tech: 'el-8',
                                                    repo_dir: 'artifacts/centos8/',
                                                    publish_branch: publish_branch
                                archiveArtifacts artifacts: pipeline_args.get('add_artifacts',
                                                                              'no-optional-artifacts-to-archive'),
                                                            allowEmptyArchive: true
                            }
                            unsuccessful {
                                sh label: "Build Log",
                                   script: '''mockroot=/var/lib/mock/epel-8-x86_64
                                              ls -l $mockroot/result/
                                              cat $mockroot/result/{root,build}.log
                                              artdir=$PWD/artifacts/centos8
                                              cp -af _topdir/SRPMS $artdir
                                              (cd $mockroot/result/ &&
                                               cp -r . $artdir)'''
                            }
                            always {
                                sh label: "Collect config.log(s)",
                                   script: '''(if cd /var/lib/mock/epel-8-x86_64/root/builddir/build/BUILD/*/; then
                                                   find . -name configure -printf %h\\\\n | \
                                                   while read dir; do
                                                       if [ ! -f $dir/config.log ]; then
                                                           continue
                                                       fi
                                                       tdir="$OLDPWD/artifacts/centos8/autoconf-logs/$dir"
                                                       mkdir -p $tdir
                                                       cp -a $dir/config.log $tdir/
                                                   done
                                               fi)'''
                            }
                            cleanup {
                                archiveArtifacts artifacts: 'artifacts/centos8/**'
                            }
                        }
                    } //stage('Build on CentOS 8')
                    stage('Build on SLES 12.3') {
                        when {
                            beforeAgent true
                            allOf {
                                environment name: 'SLES12_3_DOCKER', value: 'true'
                                expression { false }
                                expression { distros.contains('sles12.3') }
                                expression { return env.QUICKBUILD == '1' }
                            }
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.mockbuild'
                                label 'docker_runner'
                                args  '--group-add mock' +
                                      ' --cap-add=SYS_ADMIN' +
                                      ' --privileged=true'
                                additionalBuildArgs dockerBuildArgs()
                            }
                        }
                        steps {
                            sh label: "Build package",
                               script: '''rm -rf artifacts/sles12.3/
                                          mkdir -p artifacts/sles12.3/
                                          make CHROOT_NAME="suse-12.3-x86_64" ''' +
                                       pipeline_args.get('make args', '') + ' chrootbuild ' +
                                       pipeline_args.get('add_make_targets', '')
                        }
                        post {
                            success {
                                sh label: "Collect artifacts",
                                   script: '''(cd /var/lib/mock/sles-12.3-x86_64/result/ &&
                                              cp -r . $OLDPWD/artifacts/sles12.3/)\n''' +
                                              pipeline_args.get('add_archiving_cmds', '').replace("<distro>", "sles12.3") +
                                             '\ncreaterepo artifacts/sles12.3/'
                                publishToRepository product: package_name,
                                                    format: 'yum',
                                                    maturity: 'stable',
                                                    tech: 'sles-12',
                                                    repo_dir: 'artifacts/sles12.3/',
                                                    publish_branch: publish_branch
                                archiveArtifacts artifacts: pipeline_args.get('add_artifacts',
                                                                              'no-optional-artifacts-to-archive'),
                                                            allowEmptyArchive: true
                            }
                            unsuccessful {
                                sh label: "Build Log",
                                   script: '''mockroot=/var/lib/mock/sles-12.3-x86_64
                                              ls -l $mockroot/result/
                                              cat $mockroot/result/{root,build}.log
                                              artdir=$PWD/artifacts/sles12.3
                                              cp -af _topdir/SRPMS $artdir
                                              (cd $mockroot/result/ &&
                                               cp -r . $artdir)'''
                            }
                            always {
                                sh label: "Collect config.log(s)",
                                   script: '''(if cd /var/lib/mock/sles-12.3-x86_64/root/builddir/build/BUILD/*/; then
                                                   find . -name configure -printf %h\\\\n | \
                                                   while read dir; do
                                                       if [ ! -f $dir/config.log ]; then
                                                           continue
                                                       fi
                                                       tdir="$OLDPWD/artifacts/sles12.3/autoconf-logs/$dir"
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
                            allOf {
                                expression { false }
                                expression { distros.contains('leap42.3') }
                                expression { return env.QUICKBUILD == '1' }
                            }
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.mockbuild'
                                label 'docker_runner'
                                args  '--group-add mock' +
                                      ' --cap-add=SYS_ADMIN' +
                                      ' --privileged=true'
                                additionalBuildArgs dockerBuildArgs()
                            }
                        }
                        steps {
                            sh label: "Build package",
                               script: '''rm -rf artifacts/leap42.3/
                                          mkdir -p artifacts/leap42.3/
                                          make CHROOT_NAME="opensuse-leap-42.3-x86_64" ''' +
                                       pipeline_args.get('make args', '') + ' chrootbuild ' +
                                       pipeline_args.get('add_make_targets', '')
                        }
                        post {
                            success {
                                sh label: "Collect artifacts",
                                   script: '''(cd /var/lib/mock/opensuse-leap-42.3-x86_64/result/ &&
                                              cp -r . $OLDPWD/artifacts/leap42.3/)\n''' +
                                              pipeline_args.get('add_archiving_cmds', '').replace("<distro>", "leap42.3") +
                                             '\ncreaterepo artifacts/leap42.3/'
                                publishToRepository product: package_name,
                                                    format: 'yum',
                                                    maturity: 'stable',
                                                    tech: 'leap-42',
                                                    repo_dir: 'artifacts/leap42.3/',
                                                    publish_branch: publish_branch
                                archiveArtifacts artifacts: pipeline_args.get('add_artifacts',
                                                                              'no-optional-artifacts-to-archive'),
                                                            allowEmptyArchive: true
                            }
                            unsuccessful {
                                sh label: "Build Log",
                                   script: '''mockroot=/var/lib/mock/opensuse-leap-42.3-x86_64
                                              ls -l $mockroot/result/
                                              cat $mockroot/result/{root,build}.log
                                              artdir=$PWD/artifacts/leap42.3
                                              cp -af _topdir/SRPMS $artdir
                                              (cd $mockroot/result/ &&
                                               cp -r . $artdir)'''
                            }
                            always {
                                sh label: "Collect config.log(s)",
                                   script: '''(if cd /var/lib/mock/opensuse-leap-42.3-x86_64/root/builddir/build/BUILD/*/; then
                                                   find . -name configure -printf %h\\\\n | \
                                                   while read dir; do
                                                       if [ ! -f $dir/config.log ]; then
                                                           continue
                                                       fi
                                                       tdir="$OLDPWD/artifacts/leap42.3/autoconf-logs/$dir"
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
                            allOf {
                                expression { distros.contains('leap15') }
                                expression { return env.QUICKBUILD == '1' }
                            }
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.mockbuild'
                                label 'docker_runner'
                                args  '--group-add mock' +
                                      ' --cap-add=SYS_ADMIN' +
                                      ' --privileged=true'
                                additionalBuildArgs dockerBuildArgs()
                            }
                        }
                        steps {
                            sh label: "Build package",
                               script: '''rm -rf artifacts/leap15/
                                          mkdir -p artifacts/leap15/
                                          make CHROOT_NAME="opensuse-leap-15.2-x86_64" ''' +
                                       pipeline_args.get('make args', '') + ' chrootbuild ' +
                                       pipeline_args.get('add_make_targets', '')
                        }
                        post {
                            success {
                                sh label: "Collect artifacts",
                                   script: '''(cd /var/lib/mock/opensuse-leap-15.2-x86_64/result/ &&
                                              cp -r . $OLDPWD/artifacts/leap15/)\n''' +
                                              pipeline_args.get('add_archiving_cmds', '').replace("<distro>", "leap15") +
                                             '\ncreaterepo artifacts/leap15/'
                                publishToRepository product: package_name,
                                                    format: 'yum',
                                                    maturity: 'stable',
                                                    tech: 'leap-15',
                                                    repo_dir: 'artifacts/leap15/',
                                                    publish_branch: publish_branch
                                archiveArtifacts artifacts: pipeline_args.get('add_artifacts',
                                                                              'no-optional-artifacts-to-archive'),
                                                            allowEmptyArchive: true
                            }
                            unsuccessful {
                                sh label: "Build Log",
                                   script: '''mockroot=/var/lib/mock/opensuse-leap-15.2-x86_64
                                              ls -l $mockroot/result/
                                              cat $mockroot/result/{root,build}.log
                                              artdir=$PWD/artifacts/leap15
                                              cp -af _topdir/SRPMS $artdir
                                              (cd $mockroot/result/ &&
                                               cp -r . $artdir)'''
                            }
                            always {
                                sh label: "Collect config.log(s)",
                                   script: '''(if cd /var/lib/mock/opensuse-leap-15.2-x86_64/root/builddir/build/BUILD/*/; then
                                                   find . -name configure -printf %h\\\\n | \
                                                   while read dir; do
                                                       if [ ! -f $dir/config.log ]; then
                                                           continue
                                                       fi
                                                       tdir="$OLDPWD/artifacts/leap15/autoconf-logs/$dir"
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
                    stage('Build on Ubuntu 20.04') {
                        when {
                            beforeAgent true
                            allOf {
                                expression { distros.contains('ubuntu20.04') }
                                expression { return env.QUICKBUILD == '1' }
                                expression { env.DAOS_STACK_REPO_PUB_KEY != null }
                                expression { env.DAOS_STACK_REPO_SUPPORT != null }
                                expression { env.DAOS_STACK_REPO_UBUNTU_20_04_LIST != null}
                             }
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.ubuntu.20.04'
                                label 'docker_runner'
                                args '--privileged=true'
                                additionalBuildArgs dockerBuildArgs()
                            }
                        }
                        steps {
                            sh label: "Build package",
                               script: '''rm -rf artifacts/ubuntu20.04/
                                          mkdir -p artifacts/ubuntu20.04/
                                          : "${DEBEMAIL:="$env.DAOS_EMAIL"}"
                                          : "${DEBFULLNAME:="$env.DAOS_FULLNAME"}"
                                          export DEBEMAIL
                                          export DEBFULLNAME
                                          make ''' + pipeline_args.get('make args', '') + ' chrootbuild ' +
                                          pipeline_args.get('add_make_targets', '')
                        }
                        post {
                            success {
                                sh label: "Collect artifacts",
                                   script: '''cp -v \
                                                /var/cache/pbuilder/result/*{.buildinfo,.changes,.deb,.dsc,.xz} \
                                                artifacts/ubuntu20.04/
                                              cp -v \
                                                _topdir/BUILD/*.orig.tar.* \
                                                artifacts/ubuntu20.04
                                              pushd artifacts/ubuntu20.04/
                                                dpkg-scanpackages . /dev/null | \
                                                  gzip -9c > Packages.gz
                                              popd'''
                                publishToRepository product: package_name,
                                                    format: 'apt',
                                                    maturity: 'stable',
                                                    tech: 'ubuntu-20.04',
                                                    repo_dir: 'artifacts/ubuntu20.04/',
                                                    publish_branch: publish_branch
                            }
                            unsuccessful {
                                sh label: "Collect artifacts",
                                   script: "cat /var/cache/pbuilder/result/*.buildinfo",
                                   returnStatus: true
                            }
                            cleanup {
                                archiveArtifacts artifacts: 'artifacts/ubuntu20.04/**'
                            }
                        }
                    } //stage('Build on Ubuntu 20.04')
                    stage('Build on Ubuntu rolling') {
                        when {
                            beforeAgent true
                            allOf {
                                expression { distros.contains('ubuntu_rolling') }
                                expression { return env.QUICKBUILD == '1' }
                            }
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.ubuntu.rolling'
                                label 'docker_runner'
                                args '--privileged=true'
                                additionalBuildArgs dockerBuildArgs()
                            }
                        }
                        steps {
                            sh label: "Build package",
                               script: '''rm -rf artifacts/ubuntu_rolling/
                                          mkdir -p artifacts/ubuntu_rolling/
                                          mkdir -p _topdir
                                          : "${DEBEMAIL:="$env.DAOS_EMAIL"}"
                                          : "${DEBFULLNAME:="$env.DAOS_FULLNAME"}"
                                          export DEBEMAIL
                                          export DEBFULLNAME
                                          make ''' + pipeline_args.get('make args', '') + ' chrootbuild ' +
                                          pipeline_args.get('add_make_targets', '')
                        }
                        post {
                            success {
                                sh label: "Collect artifacts",
                                   script: '''cp -v \
                                                /var/cache/pbuilder/result/*{.buildinfo,.changes,.deb,.dsc,.xz} \
                                                artifacts/ubuntu_rolling/
                                              cp -v \
                                                _topdir/BUILD/*.orig.tar.* \
                                                artifacts/ubuntu_rolling
                                              pushd artifacts/ubuntu_rolling/
                                                dpkg-scanpackages . /dev/null | \
                                                  gzip -9c > Packages.gz
                                              popd'''
                                publishToRepository product: package_name,
                                                    format: 'apt',
                                                    maturity: 'stable',
                                                    tech: 'ubuntu-rolling',
                                                    repo_dir: 'artifacts/ubuntu_rolling/',
                                                    publish_branch: publish_branch
                            }
                            unsuccessful {
                                sh label: "Collect artifacts",
                                   script: "cat /var/cache/pbuilder/result/*.buildinfo",
                                   returnStatus: true
                            }
                            cleanup {
                                archiveArtifacts artifacts: 'artifacts/ubuntu_rolling/**'
                            }
                        }
                    } //stage('Build on Ubuntu rolling')
                } // parallel
            } //stage('Build')
            stage('Test') {
                parallel {
                    stage('Test on CentOS 7') {
                        when {
                            beforeAgent true
                            allOf {
                                expression { distros.contains('centos7') }
                            }
                        }
                        agent {
                            dockerfile {
                                filename 'packaging/Dockerfile.centos.7'
                                label 'docker_runner'
                                args  ' --cap-add=SYS_ADMIN' +
                                      ' --privileged=true' +
                                      ' -u 0'
                                additionalBuildArgs dockerBuildArgs()
                            }
                        }
                        steps {
                            sh label: "Test",
                               script: 'make ' + pipeline_args.get('make args', '') + ' test'
                        }
                    } // stage('Test on CentOS 7')
                } // parallel
            } // stage('Test')
        } // stages
    } // pipeline
} // call
