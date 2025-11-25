/* groovylint-disable DuplicateStringLiteral, NestedBlockDepth, ParameterName, VariableName */
// vars/parseStageInfo.groovy

/**
 * Method to get a MAP of values based on environment variables that
 * are known to the stage.
 *
 * The map may have following members if there is a default value for them.
 *
 * result['compiler']      Known compilers are 'gcc', 'icc', clang, and 'covc'.
 *                         Default is 'gcc'
 *
 * result['target']        Known targets are 'centos7','centos8','el8', 'leap15',
 *                         'ubuntu18.04', 'ubuntu20.04'.  Default is 'centos7'
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
        result['distro_version'] = '7'
    } else {
        if (env.STAGE_NAME.contains('Hardware')) {
            res = hwDistroTarget2()
            result['target'] = res[0] + res[1]
            result['distro_version'] = res[1]
        } else if (stage_name.contains('CentOS 8.3.2011')) {
            result['target'] = 'centos8.3'
            result['distro_version'] = cachedCommitPragma('EL8.3-version', '8.3')
            new_ci_target = cachedCommitPragma('EL8.3-target', result['target'])
        } else if (stage_name.contains('CentOS 8')) {
            result['target'] = 'centos8'
            result['distro_version'] = cachedCommitPragma('EL8-version', '8')
            new_ci_target = cachedCommitPragma('EL8-target', result['target'])
        } else if (stage_name.contains('EL 8.4')) {
            result['target'] = 'el8.4'
            result['distro_version'] = cachedCommitPragma('EL8.4-version', '8.4')
            new_ci_target = cachedCommitPragma('EL8.4-target', result['target'])
        } else if (stage_name.contains('EL 8.6')) {
            result['target'] = 'el8.6'
            result['distro_version'] = cachedCommitPragma('EL8.6-version', '8.6')
            new_ci_target = cachedCommitPragma('EL8.6-target', result['target'])
        } else if (stage_name.contains('EL 8.8')) {
            result['target'] = 'el8.8'
            result['distro_version'] = cachedCommitPragma('EL8.8-version', '8.8')
            new_ci_target = cachedCommitPragma('EL8.8-target', result['target'])
        } else if (stage_name.contains('EL 8')) {
            result['target'] = 'el8'
            result['distro_version'] = cachedCommitPragma('EL8-version',
                                                          distroVersion(result['target']))
            new_ci_target = cachedCommitPragma('EL8-target', result['target'])
        } else if (stage_name.contains('EL 9.7')) {
            result['target'] = 'el9.7'
            result['distro_version'] = cachedCommitPragma('EL9.7-version', '9.7')
            new_ci_target = cachedCommitPragma('EL9.7-target', result['target'])
        } else if (stage_name.contains('EL 9')) {
            result['target'] = 'el9'
            result['distro_version'] = cachedCommitPragma('EL9-version',
                                                          distroVersion(result['target']))
            new_ci_target = cachedCommitPragma('EL9-target', result['target'])
        } else if (stage_name.contains('Leap 15.3')) {
            result['target'] = 'leap15'
            result['distro_version'] = cachedCommitPragma('LEAP15-version', '15.3')
            new_ci_target = cachedCommitPragma('LEAP15-target', result['target'])
        } else if (stage_name.contains('Leap 15.4')) {
            result['target'] = 'leap15'
            result['distro_version'] = cachedCommitPragma('LEAP15-version', '15.4')
            new_ci_target = cachedCommitPragma('LEAP15-target', result['target'])
        } else if (stage_name.contains('Leap 15.5')) {
            result['target'] = 'leap15'
            result['distro_version'] = cachedCommitPragma('LEAP15-version', '15.5')
            new_ci_target = cachedCommitPragma('LEAP15-target', result['target'])
        } else if (stage_name.contains('Leap 15.6')) {
            result['target'] = 'leap15'
            // Until a mock opensuse-leap-15.6-x86-64.cfg is available provision with 15.5
            result['distro_version'] = cachedCommitPragma('LEAP15-version', '15.5')
            new_ci_target = cachedCommitPragma('LEAP15-target', result['target'])
        } else if (stage_name.contains('Leap 15')) {
            result['target'] = 'leap15'
            result['distro_version'] = cachedCommitPragma('LEAP15-version',
                                                          distroVersion(result['target']))
            new_ci_target = cachedCommitPragma('LEAP15-target', result['target'])
        } else if (stage_name.contains('Ubuntu 18')) {
            result['target'] = 'ubuntu18.04'
            result['distro_version'] = cachedCommitPragma('UBUNTU18-version', '18.04')
            new_ci_target = cachedCommitPragma('UBUNTU18-target', result['target'])
        } else if (stage_name.contains('Ubuntu 20.04')) {
            result['target'] = 'ubuntu20.04'
            result['distro_version'] = cachedCommitPragma('UBUNTU20-version', '20.04')
            new_ci_target = cachedCommitPragma('UBUNTU20-target', result['target'])
        } else if (stage_name.contains('Ubuntu 20')) {
            // TODO: below needs to change to ubuntu20 at some point in the future when
            //       all other pipelines are ready for it
            result['target'] = 'ubuntu20.04'
            result['distro_version'] = cachedCommitPragma('UBUNTU20-version',
                                                          distroVersion(result['target']))
            new_ci_target = cachedCommitPragma('UBUNTU20-target', result['target'])
        } else {
            // also for: if (stage_name.contains('CentOS 7')) {
            echo "Could not determine target in ${stage_name}, defaulting to EL8"
            result['target'] = 'el8'
            result['distro_version'] = cachedCommitPragma('EL8-version',
                                                          distroVersion(result['target']))
            new_ci_target = cachedCommitPragma('EL8-target', result['target'])
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
    } else if (result['ci_target'].startsWith('leap')) {
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
    String ftest_arg_repeat = ''
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
        functional_args = getFunctionalArgs(kwargs)
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
