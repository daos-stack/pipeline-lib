/* groovylint-disable DuplicateNumberLiteral, DuplicateStringLiteral, ParameterName, VariableName */
// vars/daosPackagesVersion.groovy

/**
 * daosPackagesVersion.groovy
 *
 * daosPackagesVersion variable
 */

/**
 * Method to return the version of the DAOS packages
 */

import groovy.transform.Field

/* groovylint-disable-next-line CompileStatic */
@Field static String rpm_version_cache = ''

String normalize_distro(String distro) {
    if (distro.startsWith('el8') || distro.startsWith('centos8') ||
        distro.startsWith('rocky8') || distro.startsWith('almalinux8') ||
        distro.startsWith('rhel8')) {
        return 'el8'
    }

    return distro
}

String rpm_dist(String distro) {
    if (distro.startsWith('el7') || distro.startsWith('centos7')) {
        return '.el7'
    } else if (distro.startsWith('el8') || distro.startsWith('centos8') ||
               distro.startsWith('rocky8') || distro.startsWith('almalinux8') ||
               distro.startsWith('rhel8')) {
        return '.el8'
    } else if (distro.startsWith('leap15')) {
        return '.suse.lp153'
    }
    error("Don't know what the RPM %{dist} is for ${distro}")
    return
}

String call(String next_version) {
    return daosPackagesVersion(parseStageInfo()['target'], next_version)
}

String call(String distro, String next_version) {
    String target_branch = env.CHANGE_TARGET ? env.CHANGE_TARGET : env.BRANCH_NAME
    String _distro = distro

    // build parameter (CI_RPM_TEST_VERSION) has highest priority, followed by commit pragma
    // TODO: this should actually be determined from the PR-repos artifacts
    String version = rpmTestVersion()
    if (version != '') {
        String dist = ''
        if (version.indexOf('-') > -1) {
            // only tack on the %{dist} if the release was specified
            dist = rpm_dist(_distro)
        }
        return version + dist
    }

    if (target_branch.startsWith("weekly-testing") ||
        target_branch.startsWith("provider-testing") ||
        target_branch.startsWith("soak-testing")) {
        // weekly-test just wants the latest for the branch
        if (rpm_version_cache != '' && rpm_version_cache != 'locked') {
            return rpm_version_cache + rpm_dist(_distro)
        }
        if (rpm_version_cache == '') {
            // no cached value and nobody's getting it
            rpm_version_cache = 'locked'
            rpm_version_cache = daosLatestVersion(next_version)
        } else {
            // somebody else is getting it, wait for them
            Integer i = 30
            while (rpm_version_cache == 'locked' && i-- > 0) {
                /* groovylint-disable-next-line BusyWait */
                sleep(10)
            }
            if (rpm_version_cache == 'locked') {
                rpm_version_cache = daosLatestVersion(next_version)
            }
        }
        return rpm_version_cache + rpm_dist(_distro)
    }

    // otherwise use the version in the stash
    // but trim off any point release from the distro first
    Integer dot = _distro.indexOf('.')
    if (dot > -1) {
        _distro = _distro[0..dot - 1]
    }

    String err_msg = null
    /* TODO: the stage info should tell us the name of the distro that the
     * packages to be tested were built on and stashed in
     */
    String normalized_distro = normalize_distro(_distro)
    String version_file = normalized_distro
    try {
        unstash normalized_distro + '-rpm-version'
    /* groovylint-disable-next-line CatchException */
    } catch (Exception e1) {
        // backward compatibilty
        try {
            // ugly backwards compatibility hack due to hardware distro
            // being el8 now
            if (_distro == normalized_distro && _distro == 'el8') {
                _distro = 'centos8'
            }
            unstash _distro + '-rpm-version'
            version_file = _distro
        /* groovylint-disable-next-line CatchException */
        } catch (Exception e2) {
            err_msg = "Don't know how to determine package version for " + _distro
        }
    }

    if (!err_msg) {
        version = readFile(version_file + '-rpm-version').trim()
        if (version != '') {
            return version
        }
        err_msg = 'Unable to read a version from ' + version_file + '-rpm-version'
    }

    sh('ls -l ' + version_file + '-rpm-version || true; ' +
       'cat ' + version_file + '-rpm-version || true;')

    error err_msg
    return
}
