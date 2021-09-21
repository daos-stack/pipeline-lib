// vars/quickBuildDeps.groovy

  /**
   * quickBuildDeps step method
   *
   * @param distro Distribution to get the deps for
   * @param always Force return of the deps, not just when quickbuilding
   * @return List of dependencies needed when quickbuilding
   *
   */
def call(String distro, always=false) {
    String rpmspec_args = ""
    if (!always) {
        if (!quickBuild() ) {
          // Reply with an empty string if quickBuild disabled to avoid
          // discarding docker caches.
            return ""
        }
    }
    if (distro.startsWith('leap15')) {
        rpmspec_args = "--define dist\\ .suse.lp153 " +
                       "--undefine rhel " +
                       "--define suse_version\\ 1502"
    } else if (distro.startsWith('el7') || distro.startsWith('centos7')) {
        rpmspec_args = "--undefine suse_version " +
                       "--define rhel\\ 7"
    } else if (distro.startsWith('el8') || distro.startsWith('centos8')) {
        rpmspec_args = "--undefine suse_version " +
                       "--define rhel\\ 8"
    } else {
        error("Unknown distro: ${distro} in quickBuildDeps()")
    }
    return sh(label: 'Get Quickbuild dependencies',
              script: "rpmspec -q " +
                      "--srpm " +
                      rpmspec_args + ' ' +
                      "--requires utils/rpms/daos.spec " +
                      "2>/dev/null",
              returnStdout: true)
}