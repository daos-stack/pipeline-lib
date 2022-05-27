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

String get_build_params_tags(String param_key) {
  // Get the tags defined by the build parameter entry for this stage
  if (param_key == 'tcp' && params.TestTagTCP && params.TestTagTCP != '') {
    return params.TestTagTCP
  }
  if (param_key == 'ucx' && params.TestTagUCX && params.TestTagUCX != '') {
    return params.TestTagUCX
  }
  if (params.TestTag && params.TestTag != '') {
    return params.TestTag
  }
  return ''
}

String get_commit_pragma_tags(String pragma_suffix) {
  // Get the test tags defined in the commit message
  String pragma_tag

  // Use the tags defined by the stage-specific 'Test-tag-<stage>:' commit message pragma.  If those
  // are not specified use the tags defined by the general 'Test-tag:' commit message pragma.
  pragma_tag = commitPragma("Test-tag" + pragma_suffix, commitPragma("Test-tag", null))
  if (pragma_tag) {
    return pragma_tag
  }

  // If neither of the 'Test-tag*:' commit message pragmas are specified, use the 'Features:'
  // commit message pragma to define the tags to use.
  String features = commitPragma("Features", null)
  if (features) {
    // Features extend the standard pr testing tags to include tests run in daily or weekly builds
    // that test the specified feature.
    pragma_tag = 'pr'
    for (feature in features.split(' ')) {
      pragma_tag += ' daily_regression,' + feature
      // DAOS-6468 Eventually add this when there are no failures in the full_regression set
      // pragma_tag += ' full_regression,' + feature
    }
  }
  return pragma_tag
}

def call(Map config = [:]) {

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
    } else if (stage_name.contains('EL 8.4')) {
      result['target'] = 'el8.4'
      result['distro_version'] = cachedCommitPragma('EL8.4-version', '8.4')
      new_ci_target = cachedCommitPragma('EL8.4-target', result['target'])
    } else if (stage_name.contains('CentOS 8')) {
      result['target'] = 'centos8'
      result['distro_version'] = cachedCommitPragma('EL8-version', '8')
      new_ci_target = cachedCommitPragma('EL8-target', result['target'])
    } else if (stage_name.contains('EL 8')) {
      result['target'] = 'el8'
      result['distro_version'] = cachedCommitPragma('EL8-version', '8')
      new_ci_target = cachedCommitPragma('EL8-target', result['target'])
    } else if (stage_name.contains('Leap 15')) {
      result['target'] = 'leap15'
      result['distro_version'] = cachedCommitPragma('LEAP15-version', '15.3')
      new_ci_target = cachedCommitPragma('LEAP15-target', result['target'])
    } else if (stage_name.contains('Ubuntu 18')) {
      result['target'] = 'ubuntu18.04'
      result['distro_version'] = cachedCommitPragma('UBUNTU18-version', '18.04')
      new_ci_target = cachedCommitPragma('UBUNTU18-target', result['target'])
    } else if (stage_name.contains('Ubuntu 20')) {
      result['target'] = 'ubuntu20.04'
      result['distro_version'] = cachedCommitPragma('UBUNTU20-version', '20.04')
      new_ci_target = cachedCommitPragma('UBUNTU20-target', result['target'])
    } else {
      // also for: if (stage_name.contains('CentOS 7')) {
      echo "Could not determine target in ${stage_name}, defaulting to EL7"
      result['target'] = 'centos7'
      result['distro_version'] = cachedCommitPragma('EL7-version', '7')
      new_ci_target = cachedCommitPragma('EL7-target', result['target'])
    }
  }
  new_ci_target = paramsValue('CI_' +
                              result['target'].toString().toUpperCase() +
                              '_TARGET', new_ci_target)
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
    result['log_to_file'] = result['target'] + '-' +
                            result['compiler']
    if (result['build_type']) {
      result['log_to_file'] += '-' + result['build_type']
    }
    result['log_to_file'] += '-build.log'
  }

  // Unless otherwise specified, all tests will only use one node.
  result['node_count'] = 1

  String cluster_size = ''
  String ftest_arg_nvme = ''
  String ftest_arg_repeat = ''
  String ftest_arg_provider = ''
  String param_key = ''
  if (stage_name.contains('Functional')) {
    result['test'] = 'Functional'
    result['node_count'] = 9
    cluster_size = '-hw'
    result['pragma_suffix'] = '-vm'
    if (stage_name.contains('Hardware')) {
      cluster_size = 'hw,large'
      result['pragma_suffix'] = '-hw-large'
      ftest_arg_nvme = 'auto:-3DNAND'
      if (stage_name.contains('Small')) {
        result['node_count'] = 3
        cluster_size = 'hw,small'
        result['pragma_suffix'] = '-hw-small'
      } else if (stage_name.contains('Medium')) {
        result['node_count'] = 5
        cluster_size = 'hw,medium'
        result['pragma_suffix'] = '-hw-medium'
      }
      if (stage_name.contains('TCP')) {
        ftest_arg_provider = 'ofi+tcp'
        param_key = 'tcp'
      } else if (stage_name.contains('UCX')) {
        ftest_arg_provider = 'ucx+dc_x'
        param_key = 'ucx'
      }
    }
    if (stage_name.contains('with Valgrind')) {
      result['pragma_suffix'] = '-valgrind'
      result['with_valgrind'] = 'memcheck'
      config['test_tag'] = 'memcheck'
    }

    // Determine which tests tags to use
    String tag
    if (startedByUser()) {
      // Test tags defined by the build parameters override all other tags
      tag = get_build_params_tags(param_key)
    }
    if (!tag && startedByTimer()) {
      // Stage defined tags take precedence in timed builds
      tag = config['test_tag']
      if (!tag) {
        // Otherwise use the default timed build tags
        tag = "pr daily_regression"
        if (env.BRANCH_NAME.startsWith("weekly-testing") && !param_key) {
          tag = "full_regression"
        }
      }
    } else if (!tag) {
      // Tags defined by commit pragmas have priority in user PRs
      tag = get_commit_pragma_tags(result['pragma_suffix'])
      if (!tag) {
        // Followed by stage defined tags
        tag = config['test_tag']
        if (!tag) {
          // Otherwise use the default PR tag
          tag = "pr"
        }
      }
    }
    if (tag) {
      tag = tag.trim()
    }

    // Highest repeat ftest argument priority is TestRepeat parameter
    if (params.TestRepeat && params.TestRepeat != '') {
      ftest_arg_repeat = params.TestRepeat
    } else {
      // Next highest priority is a stage specific Test-repeat-* then the general Test-repeat
      String repeat = cachedCommitPragma(
        'Test-repeat' + result['pragma_suffix'], cachedCommitPragma('Test-repeat', null))
      if (repeat) {
        ftest_arg_repeat = repeat
      }
    }

    // Highest provider ftest argument priority is TestProvider parameter
    if (params.TestProvider && params.TestProvider != '') {
      ftest_arg_provider = params.TestProvider
    } else {
      // Next highest priority is a stage specific Test-provider-* then the general Test-provider
      String provider = cachedCommitPragma(
        'Test-provider' + result['pragma_suffix'], cachedCommitPragma('Test-provider', null))
      if (provider) {
        ftest_arg_provider = provider
      }
    }

    // Assemble the ftest args
    result['ftest_arg'] = ''
    if (ftest_arg_nvme) {
      result['ftest_arg'] += ' --nvme=' + ftest_arg_nvme
    }
    if (ftest_arg_repeat) {
      result['ftest_arg'] += ' --repeat=' + ftest_arg_repeat
    }
    if (ftest_arg_provider) {
      result['ftest_arg'] += ' --provider=' + "'" + ftest_arg_provider + "'"
    }
    if (result['ftest_tag']) {
      result['ftest_tag'] = result['ftest_tag'].trim()
    }

    // if (stage_name.contains('Functional'))
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
  } else {
    result['NLT'] = false
  }

  if (stage_name.contains('Unit Test') &&
    stage_name.contains('memcheck')) {
    result['with_valgrind'] = 'memcheck'
  }

  return result
}
