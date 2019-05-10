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

  checkoutScm url: 'ssh://review.hpdd.intel.com:29418/exascale/jenkins',
                                  checkoutDir: 'jenkins',
                                  credentialsId: 'daos-gerrit-read'
  def options = ''
  def snapshot = ''
  def wait_for_it = true
  if (config['snapshot']) {
    options += ' --snapshot'
    wait_for_it = false
  }
  if (config['arch']) {
    options += " --arch=${config['arch']}"
  }
  if (config['distro']) {
    options += " --distro=${config['distro']}"
  }
  if (config['profile']) {
    options += " --profile=${config['profile']}"
  }

  sshagent (credentials: ['daos-provisioner']) {

    if (config['power_only']) {
      sh script: """./jenkins/test_manager/node_powercycle.py \
                     --node=${nodeString}""",
         returnStatus: true
    } else {
      sh script: """./jenkins/test_manager/node_provision_start.py \
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
      rc = sh script: """./jenkins/test_manager/wait_for_node_ready.py \
                       --nodes=${nodeString} ${woptions}""",
              returnStatus: true
      if (rc != 0) {
        error "One or more nodes failed to provision!"
      }
    }

    // Prepare the node for daos/cart testing
    def provision_script = "set -ex\n" +
                           "my_uid=" + env.UID + "\n" +
                           '''if ! grep ":\\$my_uid:" /etc/group; then
                                groupadd -g \\$my_uid jenkins
                              fi
                              if ! grep ":\\$my_uid:\\$my_uid:" /etc/passwd; then
                                useradd -b /localhome -g \\$my_uid -u \\$my_uid jenkins
                              fi
                              mkdir -p /localhome/jenkins/.ssh
                              cat /tmp/ci_key.pub >> /localhome/jenkins/.ssh/authorized_keys
                              mv /tmp/ci_key.pub /localhome/jenkins/.ssh/id_rsa.pub
                              mv /tmp/ci_key /localhome/jenkins/.ssh/id_rsa
                              chmod 700 /localhome/jenkins/.ssh
                              chmod 600 /localhome/jenkins/.ssh/{authorized_keys,id_rsa*}
                              chown -R jenkins.jenkins /localhome/jenkins/.ssh
                              echo \\"jenkins ALL=(ALL) NOPASSWD: ALL\\" > /etc/sudoers.d/jenkins
                              yum -y install epel-release
                              if ! yum -y install openmpi CUnit fuse           \
                                                  python36-PyYAML              \
                                                  python36-nose                \
                                                  python36-pip valgrind        \
                                                  python36-paramiko            \
                                                  python2-avocado              \
                                                  python2-avocado-plugins-output-html \
                                                  python2-avocado-plugins-varianter-yaml-to-mux \
                                                  python-debuginfo             \
                                                  libcmocka python-pathlib     \
                                                  python2-numpy git            \
                                                  golang-bin; then
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
                              fi
                              sync'''
    def rc = 0
    rc = sh(script: 'set -x; rm -f ci_key*; ssh-keygen -N "" -f ci_key;' +
                    ' pdcp -R ssh -l root -w ' + nodeString +
                    ' ci_key* /tmp/;' +
                    ' pdsh -R ssh -S -l root -w ' + nodeString +
                    ' "' + provision_script + '" 2>&1 | dshbak -c;' +
                    ' exit ${PIPESTATUS[0]}',
            label: "Post provision configuration",
            returnStatus: true)
    if (rc != 0) {
      stepResult name: env.STAGE_NAME, context: "test", result: "FAILURE"
      error "One or more nodes failed post-provision configuration!"
    }
  } //sshagent
}
