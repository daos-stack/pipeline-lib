// vars/functionalPackages.groovy

/**
 * functionalPackages.groovy
 *
 * functionalPackages variable
 */

/**
 * Method to return the list of packages to install for functional testing
 */

String call(Integer client_ver, String next_version) {
    return functionalPackages(hwDistroTarget(), client_ver, next_version)
}

String call(String distro, Integer client_ver, String next_version) {
    String daos_pkgs = getDAOSPackages(distro, next_version)
    String pkgs = " openmpi3 hwloc ndctl fio " +
                  "patchutils ior-hpc-daos-${client_ver} " +
                  "romio-tests-daos-${client_ver} " +
                  "testmpio " +
                  "mpi4py-tests " +
                  "hdf5-mpich-tests " +
                  "hdf5-openmpi3-tests " +
                  "hdf5-vol-daos-mpich-tests " +
                  "hdf5-vol-daos-openmpi3-tests " +
                  "MACSio-mpich " +
                  "MACSio-openmpi3 " +
                  "mpifileutils-mpich-daos-${client_ver} "
    if (distro.startsWith('leap15')) {
        return daos_pkgs + pkgs
    } else if (distro.startsWith('el7') || distro.startsWith('centos7')) {
        // need to exclude openmpi until we remove it from the repo
        return  "--exclude openmpi " + daos_pkgs + pkgs
    } else if (distro.startsWith('ubuntu20')) {
        return daos_pkgs + " openmpi-bin ndctl fio"
    } else {
        error 'functionalPackages not implemented for ' + distro
    }
}