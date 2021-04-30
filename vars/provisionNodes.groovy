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
 * config['profile']    Profile to use.  Default 'daos_ci'.
 * config['power_only'] Only power cycle the nodes, do not provision.
 * config['timeout']    Timeout in minutes.  Default 30.
 * config['inst_repos']  DAOS stack repos that should be configured.
 * config['inst_rpms']  DAOS stack RPMs that should be installed.
 *  if timeout is <= 0, then will not wait for provisioning.
 *  if power_only is specified, the nodes will be rebooted and the
 *  provisioning information ignored.
 */
/* Distro glossary:  Not case sensitive.
   Prefix "el":      Anything that is compatible with "RedHat Enterprise Linux"
                     as far as this environment cares.
   Prefix "rhel":    Specifically the Red Hat build of Enterprise Linux.
                     Not planned to be used as RHEL free development licenses
                     do not currently allow them to be used for CI automation.
   Prefix "centos":  Specifically the CentOS build of Enterprise Linux.
                     This is the only el compatible distro we are using.

   Prefix "suse":    Anything that is compatible with "Suse Linux Enterprise"
                     as far as this environment cares.
   Prefix "sles":    Specifically the SUSE Linux Enterprise Server.
                     May be used in the future as SUSE free development
                     licenses do currently allow them to be used for
                     CI automation, and SUSE also has offered site licenses.
   Prefix "leap":    Specifically OpenSUSE leap oprating system builds of
                     Suse Linux Enterprise Server.
*/
def call(Map config = [:]) {

  String nodeString = config['NODELIST']
  List node_list = config['NODELIST'].split(',')
  int node_max_cnt = node_list.size()
  int node_cnt = node_max_cnt
  String repo_type = config.get('repo_type', 'stable')
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

  String distro_type = 'el7'
  String distro = config.get('distro', 'el7')
  if (distro.startsWith("centos8") || distro.startsWith("el8")) {
    distro_type = 'el8'
  } else if (distro.startsWith("sles") || distro.startsWith("leap") ||
      distro.startsWith("opensuse")) {
      // sles and opensuse leap can use each others binaries.
      // Currently we are only building opensuse leap binaries.
      distro_type = 'suse'
  }  else if (distro.startsWith("fedora")) {
      distro_type = 'fedora'
  }  else if (distro.startsWith("ubuntu")) {
      distro_type = 'ubuntu'
  }

  String inst_rpms = config.get('inst_rpms', '')
  String inst_repos = config.get('inst_repos','')

  List gpg_key_urls = []
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

  if (!fileExists('ci/provisioning/log_cleanup.sh') ||
      !fileExists('ci/provisioning/post_provision_config.sh')) {
      println('Falling back to old provisioning code')
      return provisionNodesV1(config)
  }

  String cleanup_logs = 'NODESTRING=' + nodeString + ' ' +
                        'ci/provisioning/log_cleanup.sh'

  new_config['pre_reboot'] = cleanup_logs
  new_config['post_reboot'] = cleanup_logs

  String provision_script = 'set -ex\n'
  String config_power_only = config['power_only'] ? 'true': 'false'
  provision_script += 'DISTRO='
  if (distro_type == "el7") {
      provision_script += 'EL_7'
  } else if (distro_type == "el8") {
      provision_script += 'EL_8'
  } else if (distro_type == 'suse') {
      provision_script += 'LEAP_15'
  } else if (distro_type == 'fedora') {
      provision_script += distro_type
  } else if (distro_type == 'ubuntu') {
      provision_script += 'UBUNTU_20_04'
  }
  provision_script += ' ' +
                      'NODESTRING=' + nodeString + ' ' +
                      'CONFIG_POWER_ONLY=' + config_power_only + ' ' +
                      'INST_REPOS="' + inst_repos + '" ' +
                      'INST_RPMS="' + inst_rpms + '" ' +
                      'GPG_KEY_URLS="' + gpg_key_urls.join(' ') + '" ' +
                      'ci/provisioning/post_provision_config.sh'
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
