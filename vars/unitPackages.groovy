// vars/unitPackages.groovy

/**
 * unitPackages.groovy
 *
 * unitPackages variable
 */

/**
 * Method to return the list of Unit Testing packages
 */

String call() {
    Map stage_info = parseStageInfo()
    if (stage_info['target'] == 'centos7') {
        String packages =  'gotestsum openmpi3 ' +
                           'hwloc-devel argobots ' +
                           'fuse3-libs fuse3 ' +
                           'boost-devel ' +
                           'libisa-l-devel libpmem ' +
                           'libpmemobj protobuf-c ' +
                           'spdk-devel libfabric-devel '+
                           'pmix numactl-devel ' +
                           'libipmctl-devel python36-pyxattr ' +
                           'python36-tabulate numactl ' +
                           'libyaml-devel ' +
                           'valgrind-devel patchelf'
        if (env.STAGE_NAME.contains('Bullseye') ||
            quickBuild()) {
            unstash stage_info['target'] + '-required-mercury-rpm-version'
            packages += " spdk-tools mercury-" +
                        readFile(stage_info['target'] +
                                 '-required-mercury-rpm-version').trim() +
                        " boost-devel libisa-l_crypto libfabric-debuginfo" +
                        " argobots-debuginfo protobuf-c-debuginfo"
        }
        return packages
    } else {
        error 'unitPackages not implemented for ' + stage_info['target']
    }
}