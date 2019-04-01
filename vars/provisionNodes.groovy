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
  if (config['node_count']) {
    def node_list = config['NODELIST'].split(',')
    def node_cnt = node_list.size()
    if (config['node_count'] < node_cnt) {
      // take is blacklisted by Jenkins.
      //new_list = node_list.take(config['clients'])
      def new_list = []
      int ii
      for (ii = 0; ii < config['node_count']; ii++) {
        new_list.add(node_list[ii])
      }
      nodeString = new_list.join(',')
    } else if (config['node_count'] > node_cnt) {
      echo "${config['node_count']} clients requested."
      error "Only ${node_cnt} clients available!"
    }
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
    def provision_script = """set -ex
                              groupadd -g 1101 jenkins
                              useradd -b /localhome -g 1101 -u 1101 jenkins
                              mkdir -p /localhome/jenkins/.ssh
                              cat /tmp/ci_key.pub >> /localhome/jenkins/.ssh/authorized_keys
                              mv /tmp/ci_key.pub /localhome/jenkins/.ssh/id_rsa.pub
                              mv /tmp/ci_key /localhome/jenkins/.ssh/id_rsa
                              chmod 700 /localhome/jenkins/.ssh
                              chmod 600 /localhome/jenkins/.ssh/{authorized_keys,id_rsa*}
                              chown -R jenkins.jenkins /localhome/jenkins/.ssh
                              echo \\"jenkins ALL=(ALL) NOPASSWD: ALL\\" > /etc/sudoers.d/jenkins
                              yum -y install yum-utils
                              pkgs=\\"openpa libfabric mercury pmix ompi\\"
                              for ext in \\\$pkgs; do
                                  rm -f /etc/yum.repos.d/*.hpdd.intel.com_job_daos-stack_job_\\"\\\${ext}\\"_job_*.repo
                                  yum-config-manager --add-repo=${env.JENKINS_URL}job/daos-stack/job/\\"\\\${ext}\\"/job/master/lastSuccessfulBuild/artifact/artifacts/
                                  echo \\"gpgcheck = False\\" >> /etc/yum.repos.d/*.hpdd.intel.com_job_daos-stack_job_\\"\\\${ext}\\"_job_master_lastSuccessfulBuild_artifact_artifacts_.repo
                              done
                              yum -y erase \\\$pkgs
                              yum install -y epel-release
                              yum install -y CUnit fuse python34-PyYAML python34-nose            \
                                             python34-pip valgrind python34-paramiko             \
                                             python2-avocado python2-avocado-plugins-output-html \
                                             python2-avocado-plugins-varianter-yaml-to-mux       \
                                             python-debuginfo python2-aexpect libcmocka          \
                                             python-pathlib python2-numpy git                    \
                                             golang-bin \\\$pkgs"""
    def rc = 0
    rc = sh(script: 'set -x; rm -f ci_key*; ssh-keygen -N "" -f ci_key;' +
                    ' pdcp -R ssh -l root -w ' + nodeString +
                    ' ci_key* /tmp/;' +
                    ' pdsh -R ssh -l root -w ' + nodeString +
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
