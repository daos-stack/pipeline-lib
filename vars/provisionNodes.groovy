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
  Map new_config = config
  if (config['node_count']) {
    // Matrix builds pass requested node count as a string
    def rq_count = config['node_count'] as int
    new_config['node_count'] = rq_count
    if (rq_count < node_cnt) {
      // take is blacklisted by Jenkins.
      //new_list = node_list.take(config['clients'])
      def new_list = []
      int ii
      for (ii = 0; ii < rq_count; ii++) {
        new_list.add(node_list[ii])
      }
      nodeString = new_list.join(',')
      node_cnt = rq_count
    } else if (rq_count > node_cnt) {
      echo "${rq_count} clients requested."
      error "Only ${node_cnt} clients available!"
    }
  }

  if (env.NO_CI_PROVISIONING != null) {
      println "Jenkins not configured for CI provisioning."
      return node_cnt
  }

  def distro = config.get('distro', 'el7')
  def inst_rpms = config.get('inst_rpms', '')
  def inst_repos = config.get('inst_repos','')

  def repository_g = ''
  def repository_l = ''
  def distro_type = 'el'
  if (distro.startsWith("sles") || distro.startsWith("leap") ||
      distro.startsWith("opensuse")) {
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
    } else if (distro.startsWith("sles15")) {
        if (env.DAOS_STACK_SLES_15_GROUP_REPO != null) {
            repository_g = env.REPOSITORY_URL +
                env.DAOS_STACK_SLES_15_GROUP_REPO
        }
        if (env.DAOS_STACK_SLES_15_LOCAL_REPO != null) {
            repository_l = env.REPOSITORY_URL +
                env.DAOS_STACK_SLES_15_LOCAL_REPO
        }
    }  else if (distro.startsWith("leap15") ||
                distro.startsWith("opensuse15")) {
        if (env.DAOS_STACK_LEAP_15_GROUP_REPO != null) {
            repository_g = env.REPOSITORY_URL +
                env.DAOS_STACK_LEAP_15_GROUP_REPO
        }
        if (env.DAOS_STACK_LEAP_15_LOCAL_REPO != null) {
            repository_l = env.REPOSITORY_URL +
                env.DAOS_STACK_LEAP_15_LOCAL_REPO
        }
    }
  }

  def cleanup_logs = 'clush -B -l root -w ' + nodeString +
                     '''      --connect_timeout 30 -S
                      if ls -lh /tmp/*.log 2>/dev/null; then
                          rm -f /tmp/daos.log /tmp/server.log
                      fi
                      if ls -lh /localhome/jenkins/.spdk* 2>/dev/null; then
                          rm -f /localhome/jenkins/.spdk*
                      fi
                      if [ -d /var/tmp/daos_testing/ ]; then
                          ls -lh /var/tmp/daos_testing/
                          rm -rf /var/tmp/daos_testing/
                      fi
                      if [ -d /tmp/Functional_*/ ]; then
                          rm -rf /tmp/Functional_*
                      fi'''
  new_config['pre_reboot'] = cleanup_logs
  new_config['post_reboot'] = cleanup_logs

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
  provision_script += '\nclush -B -l root -w ' + nodeString + ' -c' +
                    ''' ci_key* --dest=/tmp/
                        clush -B -S -l root -w ''' + nodeString +
                    ''' "set -ex
                         env > /root/last_run-env.txt
                         my_uid=''' + env.UID + '''
                         if ! grep \\":\\$my_uid:\\" /etc/group; then
                           groupadd -g \\$my_uid jenkins
                         fi
                         mkdir -p /localhome
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
                         chown -R jenkins.jenkins /localhome/jenkins/
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
    if (config['power_only']) {
      // Since we don't have CORCI-711 yet, erase things we know could have
      // been put on the node previously
      provision_script += '''\nrm -f /etc/yum.repos.d/*.hpdd.intel.com_job_daos-stack_job_*_job_*.repo
                            yum -y erase fio fuse ior-hpc mpich-autoload''' +
                            ' ompi argobots cart daos daos-client dpdk ' +
                            ' fuse-libs libisa-l libpmemobj mercury mpich' +
                            ' openpa pmix protobuf-c spdk libfabric libpmem' +
                            ' libpmemblk munge-libs munge slurm' +
                            ' slurm-example-configs slurmctld slurm-slurmmd'
    }
    provision_script += '\nyum -y install yum-utils ed nfs-utils'
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
                          '''\nyum-config-manager --add-repo=''' + env.JENKINS_URL + '''job/daos-stack/job/\\\${repo}/job/\\\${branch//\\//%252F}/\\\${build_number}/artifact/artifacts/centos7/
                             pname=\\\$(ls /etc/yum.repos.d/*.hpdd.intel.com_job_daos-stack_job_\\\${repo}_job_\\\${branch//\\//%252F}_\\\${build_number}_artifact_artifacts_centos7_.repo)
                             if [ "\\\$pname" != "\\\${pname//%252F/_}" ]; then
                                 mv "\\\$pname" "\\\${pname//%252F/_}"
                             fi
                             pname="\\\${pname//%252F/_}"
                             sed -i -e '/^\\[/s/%252F/_/g' -e '\\\$s/^\\\$/gpgcheck = False/' "\\\$pname"
                             cat "\\\$pname"
                         done'''
    }
    if (inst_rpms) {
      provision_script += '''\nyum -y erase ''' + inst_rpms
    }
    gpg_key_urls.each { gpg_url ->
      provision_script += '\nrpm --import ' + gpg_url
    }
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
                                                 golang-bin ipmctl ndctl'''
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
    if (repository_g != '') {
      provision_script += '\nzypper --non-interactive ar -f ' +
                           repository_g + ' daos-stack-group-repo'
      provision_script += '\nzypper --non-interactive mr ' +
                          '--gpgcheck-allow-unsigned-repo ' +
                          'daos-stack-group-repo'
      // Group repo currently needs this key.
      provision_script += '\nrpm --import "https://download.opensuse.org/' +
                          'repositories/science:/HPC/openSUSE_Leap_15.1/' +
                          'repodata/repomd.xml.key"'
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
                          '''\n    zypper --non-interactive ar --gpgcheck-allow-unsigned -f ''' + env.JENKINS_URL + '''job/daos-stack/job/\\\${repo}/job/\\\${branch//\\//%252F}/\\\${build_number}/artifact/artifacts/leap15/ \\\$repo
                               done'''
    }
    provision_script += '\nzypper --non-interactive' +
                        ' --gpg-auto-import-keys --no-gpg-checks ref'
    provision_script += '\nzypper --non-interactive in' +
                        ' ed nfs-client ipmctl ndctl sudo '
    if (inst_rpms) {
      provision_script += inst_rpms
    }
  } else {
    error("Don't know how to handle repos for distro: \"" + distro + "\"")
  }
  provision_script += '''\nsync; sync"
                         exit ${PIPESTATUS[0]}'''
  new_config['post_restore'] = provision_script

  try {
    def rc = provisionNodesSystem(new_config)
    if (rc != 0) {
      stepResult name: env.STAGE_NAME, context: "test", result: "FAILURE"
      error "One or more nodes failed post-provision configuration!"
    }
  } catch (java.lang.NoSuchMethodError e) {
    error('Could not find a provisionNodesSystem step in' +
                ' a shared groovy library')
  }
}
