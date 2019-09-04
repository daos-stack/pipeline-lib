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

  def repository = ''
  def distro_type = 'el'
  if (distro.startsWith("sles") || distro.startsWith("leap")) {
      distro_type = 'suse'
  }
  if (env.REPOSITORY_URL != null) {
    if (distro.startsWith("el7") && (env.DAOS_STACK_EL_7_GROUP_REPO != null)) {
        repository = env.repository_url + env.DAOS_STACK_EL_7_GROUP_REPO
    } else if (distro.startsWith("sles12") &&
               (env.DAOS_STACK_SLES_12_3_GROUP_REPO != null)) {
        repository = env.repository_url + env.DAOS_STACK_SLES_12_3_GROUP_REPO
    } else if (distro.startsWith("sles15") &&
               (env.DAOS_STACK_SLES_15_GROUP_REPO != null)) {
        repository = env.repository_url + env.DAOS_STACK_SLES_15_GROUP_REPO
    }  else if (distro.startsWith("leap15") &&
                (env.DAOS_STACK_SLES_15_GROUP_REPO != null)) {
        repository = env.repository_url + env.DAOS_STACK_LEAP_15_GROUP_REPO
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
      provision_script += '\nyum -y erase fio fuse ior-hpc mpich-autoload' +
                             ' ompi argobots cart daos daos-client dpdk ' +
                             ' fuse-libs libisa-l libpmemobj mercury mpich' +
                             ' openpa pmix protobuf-c spdk libfabric libpmem' +
                             ' libpmemblk munge-libs munge slurm' +
                             ' slurm-example-configs slurmctld slurm-slurmmd'
      provision_script +=   '\nyum -y install yum-utils'
      if (repository != '') {
        def repo_file = repository.substring(repository.lastIndexOf('/') + 1,
                                             repository.length())
        if (repo_file == '') {
            error "Could not extract a repo file from ${repository}"
        }
        provision_script += '\nrm -f /etc/yum.repos.d/*' + repo_file + '.repo'
        provision_script += '\nyum-config-manager --add-repo=' + repository
        provision_script += '\necho \\"gpgcheck = False\\" >> ' +
                              '/etc/yum.repos.d/*' + repo_file + '.repo'
      }
      if (inst_repos) {
        provision_script += '\n' + iterate_repos +
                            '''\n  rm -f /etc/yum.repos.d/*.hpdd.intel.com_job_daos-stack_job_\\\${repo}_job_*.repo
                                   yum-config-manager --add-repo=''' + env.JENKINS_URL + '''job/daos-stack/job/\\\${repo}/job/\\\${branch}/\\\${build_number}/artifact/artifacts/centos7/
                                   echo \\"gpgcheck = False\\" >> /etc/yum.repos.d/*.hpdd.intel.com_job_daos-stack_job_\\\${repo}_job_\\\${branch}_\\\${build_number}_artifact_artifacts_centos7_.repo
                               done'''
      }
      if (inst_rpms) {
         provision_script += '''\nyum -y erase ''' + inst_rpms
      }
      provision_script += '''\nrm -f /etc/profile.d/openmpi.sh
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
      if (repository != '') {
        provision_script += '\nzypper --non-interactive ar ' +
                            '--gpgcheck-allow-unsigned -f ' +
                            repository + ' daos-stack-group-repo'
      }
      if (inst_repos) {
        provision_script +=   '\n' + iterate_repos +
                            '''\n    zypper --non-interactive ar --gpgcheck-allow-unsigned -f ''' + env.JENKINS_URL + '''job/daos-stack/job/\\\${repo}/job/\\\${branch}/\\\${build_number}/artifact/artifacts/sles12.3/ \\\$repo
                                 done'''
      }
      provision_script += '\nzypper --non-interactive' +
                          ' --gpg-auto-import-keys --no-gpg-checks ref'
      if (inst_rpms) {
        provision_script += '\nzypper --non-interactive --no-gpg-checks in ' + 
                            inst_rpms
      }
    } else {
        error("Don't know how to handle repos for distro: \"" + distro + "\"")
    }
    provision_script += '''\nsync"
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
