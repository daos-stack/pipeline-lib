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

@Field static rpm_version_cache = ""

String daos_latest_version(String next_version, String repo_type='stable') {
    String v = sh(label: "Get RPM packages version",
                  script: 'repoquery --repofrompath=daos,' + env.REPOSITORY_URL +
                          env["DAOS_STACK_EL_7_" + repo_type.toUpperCase() + "_REPO"] +
                        '''/ --repoid daos --qf %{version}-%{release} --whatprovides 'daos-tests(x86-64) < ''' +
                                     next_version + '''' |
                                 rpmdev-sort | tail -1''',
                  returnStdout: true).trim()

    return v[0..<v.lastIndexOf('.')]
}

String rpm_dist(String distro) {
    if (distro.startsWith('el7') || distro.startsWith('centos7')) {
        return ".el7"
    } else if (distro.startsWith('el8') || distro.startsWith('centos8')) {
        return ".el8"
    } else if (distro.startsWith("leap15")) {
        return ".suse.lp153"
    } else {
        error("Don't know what the RPM %{dist} is for ${distro}")
    }
}

String call(String next_version) {
    return daosPackagesVersion(parseStageInfo()['target'], next_version)
}

String call(String distro, String next_version) {
    String target_branch = env.CHANGE_TARGET ? env.CHANGE_TARGET : env.BRANCH_NAME

    if (target_branch.startsWith("weekly-testing")) {
        // weekly-test just wants the latest for the branch
        if (rpm_version_cache != "" && rpm_version_cache != "locked") {
            return rpm_version_cache + rpm_dist(distro)
        }
        if (rpm_version_cache == "") {
            // no cached value and nobody's getting it
            rpm_version_cache = "locked"
            rpm_version_cache = daos_latest_version(next_version)
        } else {
            // somebody else is getting it, wait for them
            Integer i = 30
            while (rpm_version_cache == "locked" && i-- > 0) {
                sleep(10)
            }
            if (rpm_version_cache == "locked") {
                rpm_version_cache = daos_latest_version(next_version)
            }
        }
        return rpm_version_cache + rpm_dist(distro)
    }

    /* what's the query to get the highest 1.0.x package?
    if (target_branch == "weekly-testing-1.x") {
        return "release/0.9"
    }
    */
    // commit pragma has highest priority
    // TODO: this should actually be determined from the PR-repos artifacts
    String version = rpmTestVersion()
    if (version != "") {
        String dist = ""
        if (version.indexOf('-') > -1) {
            // only tack on the %{dist} if the release was specified
            dist = rpm_dist(distro)
        }
        return version + dist
    }

    // use the stash after that
    unstash distro + '-rpm-version'
    version = readFile(distro + '-rpm-version').trim()
    if (version != "") {
        return version
    }

    sh('ls -l ' + distro + '-rpm-version || true; ' +
       'cat ' + distro + '-rpm-version || true;')

    error "Don't know how to determine package version for " + distro
}
