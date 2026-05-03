// vars/rpmDistValue.groovy

/**
 * rpmDistValue.groovy
 *
 * rpmDistValue variable
 */

/**
 * Method to return the %{dist} value of an OS
 */

String call(String distro) {
    if (distro.startsWith('el7') || distro.startsWith('centos7')) {
        return '.el7'
    } else if (distro.startsWith('el8') || distro.startsWith('centos8') ||
               distro.startsWith('rocky8') || distro.startsWith('almalinux8') ||
               distro.startsWith('rhel8')) {
        return '.el8'
    } else if (distro.startsWith('el9') || distro.startsWith('centos9') ||
               distro.startsWith('rocky9') || distro.startsWith('almalinux9') ||
               distro.startsWith('rhel9')) {
        return '.el9'
    } else if (distro.startsWith('leap15')) {
        return '.suse.lp' + parseStageInfo()['distro_version'].replaceAll('\\.', '')
    }
    error("Don't know what the RPM %{dist} is for ${distro}")
    return
}

