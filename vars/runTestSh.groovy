// vars/runTestSh.groovy
  /**
   * runTestSh method
   *
   * @param config Map of parameters passed
   *
   * config['mode']         Mode 'normal' or 'memcheck' 
   *                        Default is 'normal'
   *
   */

def call(Map config = [:]) {
  script {
      if (quickbuild()) {
          // TODO: these should be gotten from the Requires: of RPMs
          qb_inst_rpms = " spdk-tools mercury boost-devel"
      }
  }
  provisionNodes NODELIST: env.NODELIST,
                 node_count: 1,
                 profile: 'daos_ci',
                 distro: 'el7',
                 snapshot: true,
                 inst_repos: el7_component_repos + ' ' +
                             component_repos(),
                 inst_rpms: 'gotestsum openmpi3 ' +
                            'hwloc-devel argobots ' +
                            'fuse3-libs fuse3 ' +
                            'libisa-l-devel libpmem ' +
                            'libpmemobj protobuf-c ' +
                            'spdk-devel libfabric-devel '+
                            'pmix numactl-devel ' +
                            'libipmctl-devel' +
                            qb_inst_rpms
  if (config['mode'] == 'memcheck') {
    timeout(time:60, unit:'MINUTES') {
      runTest stashes: [ 'centos7-gcc-tests',
                       'centos7-gcc-install',
                       'centos7-gcc-build-vars' ],
            script: "SSH_KEY_ARGS=${env.SSH_KEY_ARGS} " +
                    "NODELIST=${env.NODELIST} " +
                    'ci/unit/test_main.sh memcheck'
    }
  } else if(config['mode'] == 'normal') {
    timeout(time:60, unit:'MINUTES') {
      runTest stashes: [ 'centos7-gcc-tests',
                       'centos7-gcc-install',
                       'centos7-gcc-build-vars' ],
            script: "SSH_KEY_ARGS=${env.SSH_KEY_ARGS} " +
                    "NODELIST=${env.NODELIST} " +
                    'ci/unit/test_main.sh',
            junit_files: 'test_results/*.xml'
    }
  }
}
