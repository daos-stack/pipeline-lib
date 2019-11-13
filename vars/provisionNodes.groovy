// vars/provisionNodes.groovy

/**
 * provisionNodes.groovy
 *
 * provisionNodes variable
 */

/**
 * Method to provision a set of nodes
 *
 *
 * @param config Map of parameters passed.
 *
 * config['arch']       Architecture to use.  Default 'x86_64'
 * config['distro']     Distribution to use.  Default 'el7'
 * config['NODELIST']   Comma separated list of nodes available.
 * config['node_count'] Optional lower number of nodes to provision.
 * config['profile']    Profile to use.  Default 'test'.
 * config['poll']       Poll time in minutes.  Default 1.
 * config['power_only'] Only power cycle the nodes, do not provision.
 * config['snapshot']   Use snapshots for VMs.  Default false.
 * config['timeout']    Timeout in minutes.  Default 30.
 * config['inst_repos']  DAOS stack repos that should be configured.
 * config['inst_rpms']  DAOS stack RPMs that should be installed.
 *  The timeout and poll parameters not used if snapshot is specified.
 *  if timeout is <= 0, then will not wait for provisioning.
 *  if reboot_only is specified, the nodes will be rebooted and the
 *  provisioning information ignored.
 */
def call(Map config = [:]) {

  def nodeString = config['NODELIST']
  def node_list = config['NODELIST'].split(',')
  def node_max_cnt = node_list.size()
  def node_cnt = node_max_cnt
  if (config['node_count']) {
    if (config['node_count'] < node_cnt) {
      // take is blacklisted by Jenkins.
      //new_list = node_list.take(config['clients'])
      def new_list = []
      int ii
      for (ii = 0; ii < config['node_count']; ii++) {
        new_list.add(node_list[ii])
      }
      nodeString = new_list.join(',')
      node_cnt = config['node_count']
    } else if (config['node_count'] > node_cnt) {
      echo "${config['node_count']} clients requested."
      error "Only ${node_cnt} clients available!"
    }
  }

  if (env.NO_CI_PROVISIONING != null) {
      println "Jenkins not configured for CI provisioning."
      return node_cnt
  }

  // corci-792 assist code.
  // corci-792 will convert the VM in a physical CI allocation to be
  // a physical node, so we want to skip the snapshot restore when
  // the only node is not a VM.
  if (config['snapshot'] && (config['NODELIST'].indexOf('vm') < 0)) {
    println "Skipping re-provisioning snapshot for physical nodes."
    return 0
  }

  checkoutScm url: 'git@gitlab.hpdd.intel.com:lab/ci-node-mgmt.git',
                                  checkoutDir: 'jenkins',
                                  credentialsId: 'daos-special-read'
  def options = ''
  def snapshot = ''
  def distro = 'el7'
  def wait_for_it = true
  def inst_rpms = ''
  def inst_repos = ''
  if (config['snapshot']) {
    options += ' --snapshot'
    wait_for_it = false
  }
  if (config['arch']) {
    options += " --arch=${config['arch']}"
  }
  if (config['distro']) {
    options += " --distro=${config['distro']}"
    distro = config['distro']
  }
  if (config['profile']) {
    options += " --profile=${config['profile']}"
  }

  if (config['inst_rpms']) {
      inst_rpms = config['inst_rpms']
  }

  if (config['inst_repos']) {
      inst_repos = config['inst_repos']
  }

  def repository_g = ''
  def repository_l = ''
  def distro_type = 'el'
  if (distro.startsWith("sles") || distro.startsWith("leap")) {
      distro_type = 'suse'
  }
  def gpg_key_urls = []
  if (env.DAOS_STACK_REPO_SUPPORT != null) {
     gpg_key_urls.add(env.DAOS_STACK_REPO_SUPPORT + 'RPM-GPG-KEY-CentOS-7')
     gpg_key_urls.add(env.DAOS_STACK_REPO_SUPPORT +
                      'RPM-GPG-KEY-CentOS-Debug-7')
     gpg_key_urls.add(env.DAOS_STACK_REPO_SUPPORT +
                      'RPM-GPG-KEY-CentOS-Testing-7')
     gpg_key_urls.add(env.DAOS_STACK_REPO_SUPPORT +
                      'RPM-GPG-KEY-EPEL-7')
     if (env.DAOS_STACK_REPO_PUB_KEY) {
       gpg_key_urls.add(env.DAOS_STACK_REPO_SUPPORT +
                        env.DAOS_STACK_REPO_PUB_KEY)
     }
  }
  if (env.REPOSITORY_URL != null) {
    if (distro.startsWith("el7")) {
        if (env.DAOS_STACK_EL_7_GROUP_REPO != null) {
            repository_g = env.REPOSITORY_URL + env.DAOS_STACK_EL_7_GROUP_REPO
        }
        if (env.DAOS_STACK_EL_7_LOCAL_REPO != null) {
            repository_l = env.REPOSITORY_URL + env.DAOS_STACK_EL_7_LOCAL_REPO
        }
    } else if (distro.startsWith("sles12")) {
        if (env.DAOS_STACK_SLES_12_GROUP_REPO != null) {
            repository_g = env.REPOSITORY_URL +
                env.DAOS_STACK_SLES_12_GROUP_REPO
        }
        if (env.DAOS_STACK_SLES_12_LOCAL_REPO != null) {
            repository_l = env.REPOSITORY_URL +
                env.DAOS_STACK_SLES_12_LOCAL_REPO
        }
    } else if (distro.startsWith("sles15")) {
        if (env.DAOS_STACK_SLES_15_GROUP_REPO != null) {
            repository_g = env.REPOSITORY_URL +
                env.DAOS_STACK_SLES_15_GROUP_REPO
        }
        if (env.DAOS_STACK_SLES_15_LOCAL_REPO != null) {
            repository_g = env.REPOSITORY_URL +
                env.DAOS_STACK_SLES_15_LOCAL_REPO
        }
    }  else if (distro.startsWith("leap15")) {
        if (env.DAOS_STACK_LEAP_15_GROUP_REPO != null) {
            repository_g = env.REPOSITORY_URL +
                env.DAOS_STACK_LEAP_15_GROUP_REPO
        }
        if (env.DAOS_STACK_LEAP_15_LOCAL_REPO != null) {
            repository_g = env.REPOSITORY_URL +
                env.DAOS_STACK_LEAP_15_LOCAL_REPO
        }
    }
  }

  sshagent (credentials: ['daos-provisioner']) {

    if (config['power_only']) {
      sh script: """./jenkins/node_powercycle.py \
                     --node=${nodeString}""",
         returnStatus: true
    } else {
      sh script: """./jenkins/node_provision_start.py \
                     --nodes=${nodeString} ${options}""",
         returnStatus: true
    }

    def woptions = ''
    if (wait_for_it) {
      if (config['timeout']) {
        if (config['timeout'] <= 0) {
          wait_for_it = false
        } else {
          woptions = " --timeout=${config['timeout']}"
        }
      }
      if (config['poll']) {
        woptions = " --poll=${config['poll']}"
      }
    }
    if (wait_for_it) {
      def rc = 0
      rc = sh script: """./jenkins/wait_for_node_ready.py \
                       --nodes=${nodeString} ${woptions}""",
              returnStatus: true
      if (rc != 0) {
        error "One or more nodes failed to provision!"
      }
    }

    // Prepare the node for daos/cart testing
    def provision_script = '''set -ex
                              rm -f ci_key*
                              ssh-keygen -N "" -f ci_key
                              cat << "EOF" > ci_key_ssh_config
host wolf-*
    CheckHostIp no
    StrictHostKeyChecking no
    UserKnownHostsFile /dev/null
    LogLevel error
EOF'''
    if (distro_type == "suse") {
        provision_script += '\nclush -B -S -l root -w ' + nodeString +
                            ' "zypper --non-interactive install sudo"'
    }
    provision_script += '\nclush -l root -w ' + nodeString + ' -c' +
                      ''' ci_key* --dest=/tmp/
                          clush -B -S -l root -w ''' + nodeString +
                      ''' "set -ex
                          my_uid=''' + env.UID + '''
                          if ! grep \\":\\$my_uid:\\" /etc/group; then
                            groupadd -g \\$my_uid jenkins
                          fi
                          if ! grep \\":\\$my_uid:\\$my_uid:\\" /etc/passwd; then
                            useradd -b /localhome -g \\$my_uid -u \\$my_uid jenkins
                          fi
                          mkdir -p /localhome/jenkins/.ssh
                          cat /tmp/ci_key.pub >> /localhome/jenkins/.ssh/authorized_keys
                          cat /tmp/ci_key.pub >> /root/.ssh/authorized_keys
                          mv /tmp/ci_key.pub /localhome/jenkins/.ssh/id_rsa.pub
                          mv /tmp/ci_key /localhome/jenkins/.ssh/id_rsa
                          mv /tmp/ci_key_ssh_config /localhome/jenkins/.ssh/config
                          chmod 700 /localhome/jenkins/.ssh
                          chmod 600 /localhome/jenkins/.ssh/{authorized_keys,id_rsa*,config}
                          chown -R jenkins.jenkins /localhome/jenkins/.ssh
                          echo \\"jenkins ALL=(ALL) NOPASSWD: ALL\\" > /etc/sudoers.d/jenkins'''
    def iterate_repos = '''for repo in ''' + inst_repos + '''; do
                               branch=\\"master\\"
                               build_number=\\"lastSuccessfulBuild\\"
                               if [[ \\\$repo = *@* ]]; then
                                   branch=\\"\\\${repo#*@}\\"
                                   repo=\\"\\\${repo%@*}\\"
                                   if [[ \\\$branch = *:* ]]; then
                                       build_number=\\"\\\${branch#*:}\\"
                                       branch=\\"\\\${branch%:*}\\"
                                   fi
                               fi'''
    if (distro.startsWith("el7")) {
       // Since we don't have CORCI-711 yet, erase things we know could have
       // been put on the node previously
      provision_script += '''\nrm -f /etc/yum.repos.d/*.hpdd.intel.com_job_daos-stack_job_*_job_*.repo
                             yum -y erase fio fuse ior-hpc mpich-autoload''' +
                            ' ompi argobots cart daos daos-client dpdk ' +
                            ' fuse-libs libisa-l libpmemobj mercury mpich' +
                            ' openpa pmix protobuf-c spdk libfabric libpmem' +
                            ' libpmemblk munge-libs munge slurm' +
                            ' slurm-example-configs slurmctld slurm-slurmmd'
      provision_script +=   '\nyum -y install yum-utils'
      if (repository_g != '') {
        def repo_file = repository_g.substring(
                            repository_g.lastIndexOf('/') + 1,
                            repository_g.length())
        if (repo_file == '') {
            error "Could not extract a repo file from ${repository_g}"
        }
        provision_script += '\nrm -f /etc/yum.repos.d/*' + repo_file + '.repo'
        provision_script += '\nyum-config-manager --add-repo=' + repository_g
      }
      if (repository_l != '') {
        def repo_file = repository_l.substring(
                            repository_l.lastIndexOf('/') + 1,
                            repository_l.length())
        if (repo_file == '') {
            error "Could not extract a repo file from ${repository_l}"
        }
        provision_script += '\nrm -f /etc/yum.repos.d/*' + repo_file + '.repo'
        provision_script += '\nyum-config-manager --add-repo=' + repository_l
        provision_script += '\necho \\"gpgcheck = False\\" >> ' +
                              '/etc/yum.repos.d/*' + repo_file + '.repo'
      }

      if (inst_repos) {
        provision_script += '\n' + iterate_repos +
                            '''\nyum-config-manager --add-repo=''' + env.JENKINS_URL + '''job/daos-stack/job/\\\${repo}/job/\\\${branch}/\\\${build_number}/artifact/artifacts/centos7/
                               echo \\"gpgcheck = False\\" >> /etc/yum.repos.d/*.hpdd.intel.com_job_daos-stack_job_\\\${repo}_job_\\\${branch}_\\\${build_number}_artifact_artifacts_centos7_.repo
                           done'''
      }
      if (inst_rpms) {
         provision_script += '''\nyum -y erase ''' + inst_rpms
      }
      gpg_key_urls.each { gpg_url ->
        provision_script += '\nrpm --import ' + gpg_url
      }
      provision_script += '\nrpm --import ' +
              'https://copr-be.cloud.fedoraproject.org/results/jhli' +
              '/ipmctl/pubkey.gpg'
      provision_script += '''\nrm -f /etc/profile.d/openmpi.sh
                               rm -f /tmp/daos_control.log
                               yum -y erase metabench mdtest simul IOR compat-openmpi16
                               yum -y install epel-release
                               if ! yum -y install CUnit fuse python36-PyYAML   \
                                                   python36-nose                \
                                                   python36-pip valgrind        \
                                                   python36-paramiko            \
                                                   python2-avocado              \
                                                   python2-avocado-plugins-output-html \
                                                   python2-avocado-plugins-varianter-yaml-to-mux \
                                                   libcmocka python-pathlib     \
                                                   python2-numpy git            \
                                                   python2-clustershell         \
                                                   golang-bin'''
      if (inst_rpms) {
         provision_script += ' ' + inst_rpms
      }
      provision_script += '''; then
                              rc=\\${PIPESTATUS[0]}
                              for file in /etc/yum.repos.d/*.repo; do
                                  echo \\"---- \\$file ----\\"
                                  cat \\$file
                              done
                              exit \\$rc
                          fi
                          if [ ! -e /usr/bin/pip3 ] &&
                             [ -e /usr/bin/pip3.6 ]; then
                              ln -s pip3.6 /usr/bin/pip3
                          fi
                          if [ ! -e /usr/bin/python3 ] &&
                             [ -e /usr/bin/python3.6 ]; then
                              ln -s python3.6 /usr/bin/python3
                          fi'''
    } else if (distro_type == "suse") {
      // Needed for sles-12.3/leap-42.3 only
      provision_script += '\nrpm --import https://download.opensuse.org/' +
                                 'repositories/science:/HPC/' +
                                 'openSUSE_Leap_42.3/repodata/repomd.xml.key'
      provision_script += '\nrpm --import https://download.opensuse.org/' +
                                 'repositories/home:/jhli/SLE_15/' +
                                 'repodata/repomd.xml.key'
      if (repository_g != '') {
        provision_script += '\nzypper --non-interactive ar -f ' +
                            repository_g + ' daos-stack-group-repo'
        provision_script += '\nzypper --non-interactive mr ' +
                            '--gpgcheck-allow-unsigned-repo ' +
                            'daos-stack-group-repo'
      }
      if (repository_l != '') {
        provision_script += '\nzypper --non-interactive ar' +
                            ' --gpgcheck-allow-unsigned -f ' +
                            repository_l + ' daos-stack-local-repo'
        provision_script += '\nzypper --non-interactive mr' +
                            ' --no-gpgcheck daos-stack-local-repo'
      }
      if (inst_repos) {
        provision_script +=   '\n' + iterate_repos +
                            '''\n    zypper --non-interactive ar --gpgcheck-allow-unsigned -f ''' + env.JENKINS_URL + '''job/daos-stack/job/\\\${repo}/job/\\\${branch}/\\\${build_number}/artifact/artifacts/sles12.3/ \\\$repo
                                 done'''
      }
      provision_script += '\nzypper --non-interactive' +
                          ' --gpg-auto-import-keys --no-gpg-checks ref'
      if (inst_rpms) {
        provision_script += '\nzypper --non-interactive in ' + inst_rpms
      }
    } else {
        error("Don't know how to handle repos for distro: \"" + distro + "\"")
    }
    provision_script += '''\nsync; sync"
                           exit ${PIPESTATUS[0]}'''
    def rc = 0
    rc = sh(script: provision_script,
            label: "Post provision configuration",
            returnStatus: true)
    if (rc != 0) {
      stepResult name: env.STAGE_NAME, context: "test", result: "FAILURE"
      error "One or more nodes failed post-provision configuration!"
    }
  } //sshagent
}
