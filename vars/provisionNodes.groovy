/* groovylint-disable DuplicateNumberLiteral, DuplicateStringLiteral, VariableName */
// vars/provisionNodes.groovy
/*
 * Copyright 2020-2024 Intel Corporation
 * Copyright 2025 Hewlett Packard Enterprise Development LP
 */

/**
 * provisionNodes.groovy
 *
 * provisionNodes variable
 */

/**
 * Method to provision a set of nodes
 *
 * @param config Map of parameters passed.
 *
 * config['arch']       Architecture to use.  Default 'x86_64'
 * config['distro']     Distribution to use.  Default 'el7'
 * config['NODELIST']   Comma separated list of nodes available.
 * config['node_count'] Optional lower number of nodes to provision.
 * config['pool']       Optional pool from which to get image (i.e. test)
 * config['profile']    Profile to use.  Default 'daos_ci'.
 * config['power_only'] Only power cycle the nodes, do not provision.
 * config['timeout']    Timeout in minutes.  Default 30.
 * config['inst_repos'] DAOS stack repos that should be configured.
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
   Prefix "suse":    Anything that is compatible with "Suse Linux Enterprise"
                     as far as this environment cares.
   Prefix "sles":    Specifically the SUSE Linux Enterprise Server.
                     May be used in the future as SUSE free development
                     licenses do currently allow them to be used for
                     CI automation, and SUSE also has offered site licenses.
   Prefix "leap":    Specifically OpenSUSE leap operating system builds of
                     Suse Linux Enterprise Server.
*/
/* groovylint-disable-next-line MethodSize */
Map call(Map config = [:]) {
    long startDate = System.currentTimeMillis()
    String nodeString = config['NODELIST']
    List node_list = config['NODELIST'].split(',')
    int node_max_cnt = node_list.size()
    int node_cnt = node_max_cnt
    Map new_config = config

    // Parameter overrides commit pragma
    if (params.CI_PROVISIONING_POOL) {
        if (params.CI_PROVISIONING_POOL == 'default') {
            new_config['pool'] = ''
        } else {
            new_config['pool'] = params.CI_PROVISIONING_POOL
        }
    } else {
        // Provisioning-pool: commit pragma overrides caller
        new_config['pool'] = cachedCommitPragma('Provisioning-pool', new_config['pool'])
    }

    if (config['node_count']) {
        // Matrix builds pass requested node count as a string
        int rq_count = config['node_count'] as int
        new_config['node_count'] = rq_count
        if (rq_count < node_cnt) {
            // take is blacklisted by Jenkins.
            //new_list = node_list.take(config['clients'])
            List new_list = []
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
        println 'Jenkins not configured for CI provisioning.'
        return node_cnt
    }

    String distro_type
    String distro = config.get('distro', 'el9')
    if (distro.startsWith('centos') || distro.startsWith('el') ||
        distro.startsWith('rocky') || distro.startsWith('almalinux') ||
        distro.startsWith('rhel')) {
        distro_type = 'el' + distro.split('\\.')[0][-1]
    } else if (distro.startsWith('sles') || distro.startsWith('leap') ||
               distro.startsWith('opensuse')) {
        distro_type = 'suse'
    } else {
        distro_type = distro
    }

    String inst_rpms = config.get('inst_rpms', '')
    String inst_repos = config.get('inst_repos', '')

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
        return provisionNodesV1(config)
    }

    String cleanup_logs = 'NODESTRING=' + nodeString + ' ' +
                        'ci/provisioning/log_cleanup.sh'

    new_config['pre_reboot'] = cleanup_logs
    new_config['post_reboot'] = cleanup_logs

    String provision_script = 'set -ex\n'
    String config_power_only = config['power_only'] ? 'true' : 'false'
    provision_script += 'DISTRO='
    switch (distro_type) {
    case 'el7':
            provision_script += 'EL_7'
            break
    case 'el8':
            provision_script += 'EL_8'
            break
    case 'el9':
            provision_script += 'EL_9'
            break
    case 'suse':
            provision_script += 'LEAP_15'
            break
    case 'fedora':
            provision_script += 'fedora'
            break
    case 'ubuntu':
            provision_script += 'UBUNTU_20_04'
            break
    default:
            error "Unsupported distro type: ${distro_type}/distro: ${distro}"
    }
    String https_proxy = ''
    if (env.DAOS_HTTPS_PROXY) {
        https_proxy = "${env.DAOS_HTTPS_PROXY}"
    } else if (env.HTTPS_PROXY) {
        https_proxy = "${env.HTTPS_PROXY}"
    }
    provision_script += ' ' +
                      'NODESTRING=' + nodeString + ' ' +
                      'CONFIG_POWER_ONLY=' + config_power_only + ' ' +
                      'INST_REPOS="' + inst_repos.trim() + '" ' +
                      'INST_RPMS="' + inst_rpms + '" ' +
                      'GPG_KEY_URLS="' + gpg_key_urls.join(' ') + '" ' +
                      // https://issues.jenkins.io/browse/JENKINS-55819
                      'CI_RPM_TEST_VERSION="' + (params.CI_RPM_TEST_VERSION ?: '') + '" ' +
                      'CI_PR_REPOS="' + (params.CI_PR_REPOS ?: '') + '" ' +
                      'HTTPS_PROXY="' + https_proxy + '" ' +
                      'ci/provisioning/post_provision_config.sh'
    new_config['post_restore'] = provision_script
    try {
        int rc = provisionNodesSystem(new_config)
        if (rc != 0) {
            stepResult name: env.STAGE_NAME, context: 'test', result: 'FAILURE'
            error 'One or more nodes failed post-provision configuration!'
        }
    } catch (java.lang.NoSuchMethodError e) {
        error('Could not find a provisionNodesSystem step in' +
                ' a shared groovy library')
    }
    int runTime = durationSeconds(startDate)
    return ['provision_time': runTime]
}
