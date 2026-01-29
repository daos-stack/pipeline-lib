// groovylint-disable DuplicateNumberLiteral, DuplicateStringLiteral
// groovylint-disable NestedBlockDepth, ParameterName, UnnecessaryGetter
// groovylint-disable VariableName
// vars/parseStageInfo.groovy
/*
 * Copyright 2020-2024 Intel Corporation
 * Copyright 2025-2026 Hewlett Packard Enterprise Development LP
 */

/**
 * Method to get a MAP of values based on environment variables that
 * are known to the stage.
 *
 * The map may have following members if there is a default value for them.
 *
 * result['compiler']      Known compilers are 'gcc', 'icc', clang, and 'covc'.
 *                         Default is 'gcc'
 *
 * result['target']        Known targets are 'el#','sles15', 'leap15',
 *                         'ubuntu20.04', 'ubuntu24'.  Default is 'el9'
 *
 * result['target_prefix'] Target prefix to use for the build if present.
 *                         Default is not present unless the word
 *                         TARGET_PREFIX is present in env.STAGE_NAME,
 *                         and then it will default to 'install/opt'
 *
 * This is to simplify the addition of stages to the Jenkinsfile so
 * that in many cases, only the Stage Name text needs to be updated.
 *
 * If matrix builds are implemented, this will need to be adjusted to
 * use the environment variables for the axes.
 *
 * If these config values are supplied, they must match what is documented
 * for the result described above.
 *
 * config['compiler']
 * config['target']
 * config['target_prefix']
 * @param config Map of parameters passed
 *
 * config['test_tag']          Avocado tag to test.
 *                             Default determined by this function below.
 */

String get_default_nvme() {
    // Get the default test nvme setting
    if (env.BRANCH_NAME.startsWith('feature/vos_on_blob')) {
        return startedByTimer() ? 'auto_md_on_ssd' : 'auto'
    }
    return 'auto:-3DNAND'
}

/* groovylint-disable-next-line MethodSize */
Map call(Map config = [:]) {
    Map result = [:]
    String stage_name = ''
    if (env.STAGE_NAME) {
        stage_name = env.STAGE_NAME
    }

    String new_ci_target = ''
    if (config['target']) {
        result['target'] = config['target']
    } else if (env.TARGET) {
        result['target'] = env.TARGET
        result['distro_version'] = '9'
    } else {
        if (env.STAGE_NAME.contains('Hardware')) {
            res = hwDistroTarget2()
            result['target'] = res[0] + res[1]
            result['distro_version'] = res[1]
        // Unified EL version handling for all major/minor releases
        } else if (stage_name.contains(' EL ')) {
            int elIdx = stage_name.indexOf('EL ')
            String elPart = stage_name.substring(elIdx + 3).split()[0]
            String[] parts = elPart.split('\\.')
            String majorVersion = parts[0]
            String minorVersion = parts.length > 1 ? parts[1] : null

            if (minorVersion) {
                // Point release (e.g., EL 8.6, EL 9.4, EL 10.2)
                String version = "${majorVersion}.${minorVersion}"
                result['target'] = "el${version}"
                result['distro_version'] = cachedCommitPragma("EL${majorVersion}.${minorVersion}-version", version)
                new_ci_target = cachedCommitPragma("EL${majorVersion}.${minorVersion}-target", result['target'])
            } else {
                // Major version only (e.g., EL 8, EL 9, EL 10)
                result['target'] = "el${majorVersion}"
                result['distro_version'] = cachedCommitPragma("EL${majorVersion}-version",
                                                              distroVersion(result['target']))
                new_ci_target = cachedCommitPragma("EL${majorVersion}-target", result['target'])
            }
        // Simplified Leap 15.x point release handling
        } else if (stage_name.contains('Leap 15.')) {
            int idx = stage_name.indexOf('Leap 15.')
            String leapPart = stage_name.substring(idx + 8).split()[0]
            String pointRelease = leapPart.split('\\.')[0]
            String version = "15.${pointRelease}"
            result['target'] = 'leap15'
            // Special handling for 15.6: provision with 15.5 until mock config is available
            // Leap 15.7+ doesn't exist, use sles15.7 instead
            String defaultVersion = (pointRelease == '6') ? '15.5' :
                                    (pointRelease.toInteger() >= 7) ? '15.6' : version
            result['distro_version'] = cachedCommitPragma('LEAP15-version', defaultVersion)
            new_ci_target = cachedCommitPragma('LEAP15-target', result['target'])
        } else if (stage_name.contains('Leap 15')) {
            result['target'] = 'leap15'
            result['distro_version'] = cachedCommitPragma('LEAP15-version',
                                                          distroVersion(result['target']))
            new_ci_target = cachedCommitPragma('LEAP15-target', result['target'])
        // Simplified SLES 15.x point release handling
        } else if (stage_name.contains('SLES 15.')) {
            int idx = stage_name.indexOf('SLES 15.')
            String slesPart = stage_name.substring(idx + 8).split()[0]
            String pointRelease = slesPart.split('\\.')[0]
            String version = "15.${pointRelease}"
            result['target'] = 'sles15'
            result['distro_version'] = cachedCommitPragma('SLES15-version', version)
            new_ci_target = cachedCommitPragma('SLES15-target', result['target'])
        } else if (stage_name.contains('SLES 15')) {
            result['target'] = 'sles15'
            result['distro_version'] = cachedCommitPragma('SLES15-version',
                                                          distroVersion(result['target']))
            new_ci_target = cachedCommitPragma('SLES15-target', result['target'])
        } else if (stage_name.contains('Ubuntu 20.04')) {
            result['target'] = 'ubuntu20.04'
            result['distro_version'] = cachedCommitPragma('UBUNTU20-version', '20.04')
            new_ci_target = cachedCommitPragma('UBUNTU20-target', result['target'])
        } else if (stage_name.contains('Ubuntu 24')) {
            // TODO: below needs to change to ubuntu20 at some point in the future when
            //       all other pipelines are ready for it
            result['target'] = 'ubuntu24.04'
            result['distro_version'] = cachedCommitPragma('UBUNTU24-version',
                                                          distroVersion(result['target']))
            new_ci_target = cachedCommitPragma('UBUNTU24-target', result['target'])
        } else {
            // Fallback if we can't figure things out.
            echo "Could not determine target in ${stage_name}, defaulting to EL9"
            result['target'] = 'el9'
            result['distro_version'] = cachedCommitPragma('EL9-version',
                                                          distroVersion(result['target']))
            new_ci_target = cachedCommitPragma('EL9-target', result['target'])
        }
    }
    new_ci_target = paramsValue(
        'CI_' + result['target'].toString().toUpperCase() + '_TARGET', new_ci_target)
    if (new_ci_target) {
        result['ci_target'] = new_ci_target
    } else {
        result['ci_target'] = result['target']
    }

    if (result['ci_target'].startsWith('el') ||
        result['ci_target'].startsWith('centos') ||
        result['ci_target'].startsWith('rocky') ||
        result['ci_target'].startsWith('rhel') ||
        result['ci_target'].startsWith('almalinux')) {
        result['java_pkg'] = 'java-1.8.0-openjdk'
    } else if (result['ci_target'].startsWith('ubuntu')) {
        result['java_pkg'] = 'openjdk-8-jdk'
    } else if (result['ci_target'].startsWith('leap') ||
               result['ci_target'].startsWith('sles')) {
        result['java_pkg'] = 'java-1_8_0-openjdk'
    } else {
        error 'Java package not known for ' + result['ci_target']
    }

    result['compiler'] = 'gcc'
    if (config['COMPILER']) {
        result['compiler'] = config['COMPILER']
    } else if (env.COMPILER) {
        result['compiler'] = env.COMPILER
    } else if (stage_name.contains('Clang')) {
        result['compiler'] = 'clang'
    } else if (stage_name.contains('Intel-C')) {
        result['compiler'] = 'icc'
    } else if (stage_name.contains('Bullseye')) {
        result['compiler'] = 'covc'
    }

    if (config['build_type']) {
        result['build_type'] = config['build_type']
    } else if (config['BUILD_TYPE']) {
        result['build_type'] = config['BUILD_TYPE']
    } else if (stage_name.contains('release')) {
        result['build_type'] = 'release'
    } else if (stage_name.contains('debug')) {
        result['build_type'] = 'debug'
    }

    if (config['target_prefix']) {
        result['target_prefix'] == config['target_prefix']
    } else if (stage_name.contains('TARGET_PREFIX')) {
        result['target_prefix'] = 'install/opt'
    }

    if (stage_name.contains('Coverity')) {
        result['test'] = 'coverity'
    }

    if (config['log_to_file']) {
        result['log_to_file'] = config['log_to_file']
    } else {
        result['log_to_file'] = result['target'] + '-' + result['compiler']
        if (result['build_type']) {
            result['log_to_file'] += '-' + result['build_type']
        }
        result['log_to_file'] += '-build.log'
    }

    // Unless otherwise specified, all tests will only use one node.
    result['node_count'] = 1

    String ftest_arg_nvme = ''
    String ftest_arg_provider = ''
    if (stage_name.contains('Functional')) {
        result['test'] = 'Functional'
        result['node_count'] = 9
        result['always_script'] = config.get('always_script', 'ci/functional/job_cleanup.sh')
        if (stage_name.contains('Hardware')) {
            ftest_arg_nvme = get_default_nvme()
            if (stage_name.contains('Small')) {
                result['node_count'] = 3
            } else if (stage_name.contains('Medium')) {
                result['node_count'] = 5
                if (stage_name.contains('Provider')) {
                    if (stage_name.contains('Verbs')) {
                        ftest_arg_provider = 'ofi+verbs'
                    }
                    else if (stage_name.contains('UCX')) {
                        ftest_arg_provider = 'ucx+dc_x'
                    }
                    else if (stage_name.contains('TCP')) {
                        ftest_arg_provider = 'ofi+tcp'
                    }
                }
            } else if (stage_name.contains('Hardware 24')) {
                result['node_count'] = 24
            }
        }
        if (stage_name.contains('with Valgrind')) {
            result['with_valgrind'] = 'memcheck'
            config['test_tag'] = 'memcheck'
        }
        result['pragma_suffix'] = getPragmaSuffix()

        // Get the ftest tags
        Map kwargs = [:]
        kwargs['pragma_suffix'] = result['pragma_suffix']
        kwargs['stage_tags'] = getFunctionalStageTags()
        kwargs['default_tags'] = config['test_tag']
        if (!kwargs['default_tags']) {
            if (startedByTimer() && env.BRANCH_NAME =~ branchTypeRE('weekly')) {
                kwargs['default_tags'] = 'full_regression'
            } else if (startedByTimer()) {
                kwargs['default_tags'] = 'pr daily_regression'
            } else if (env.BRANCH_NAME =~ branchTypeRE('testing')) {
                kwargs['default_tags'] = 'always_passes'
            } else {
                kwargs['default_tags'] = 'pr'
            }
        }
        result['test_tag'] = getFunctionalTags(kwargs)

        // Get the ftest arguments
        kwargs['default_nvme'] = ftest_arg_nvme
        kwargs['provider'] = ftest_arg_provider
        Map functional_args = getFunctionalArgs(kwargs)
        result['ftest_arg'] = functional_args.get('ftest_arg', '')
        result['stage_rpms'] = functional_args.get('stage_rpms', '')
    } else if (stage_name.contains('Storage')) {
        if (env.NODELIST) {
            List node_list = env.NODELIST.split(',')
            result['node_count'] = node_list.size()
        }
    } // else if (stage_name.contains('Storage'))
    if (config['test']) {
        result['test'] = config['test']
    }
    if (config['node_count']) {
        result['node_count'] = config['node_count']
    }
    if (config['pragma_suffix']) {
        result['pragma_suffix'] = config['pragma_suffix']
    }
    if (config['ftest_arg']) {
        result['ftest_arg'] = config['ftest_arg']
    }

    if (stage_name.contains('NLT')) {
        result['NLT'] = true
        result['valgrind_pattern'] = config.get('valgrind_pattern', '*memcheck.xml')
        result['always_script'] = config.get('always_script', 'ci/unit/test_nlt_post.sh')
        result['testResults'] = config.get('testResults', 'nlt-junit.xml')
        result['with_valgrind'] = 'memcheck'
    } else {
        result['NLT'] = false
        if (config['valgrind_pattern']) {
            result['valgrind_pattern'] = config['valgrind_pattern']
        }
    }
    if (stage_name.contains('Unit Test')) {
        result['testResults'] = config.get('testResults', 'test_results/*.xml')
        result['always_script'] = config.get('always_script', 'ci/unit/test_post_always.sh')
        if (stage_name.contains('memcheck')) {
            result['with_valgrind'] = 'memcheck'
        }
    }

    return result
}
