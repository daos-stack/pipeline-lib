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
 * result['target']        Known targets are 'centos7','centos8','leap15',
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
def call(Map config = [:]) {

    Map result = [:]
    result['target'] = 'centos7'
    if (config['target']) {
      result['target'] = config['target']
    } else if (env.TARGET) {
      result['target'] = env.TARGET
    } else {
      if (env.STAGE_NAME.contains('CentOS 7')) {
        result['target'] = 'centos7'
      } else if (env.STAGE_NAME.contains('CentOS 8')) {
        result['target'] = 'centos8'
      } else if (env.STAGE_NAME.contains('Leap 15')) {
        result['target'] = 'leap15'
      } else if (env.STAGE_NAME.contains('Ubuntu 18')) {
        result['target'] = 'ubuntu18.04'
      } else if (env.STAGE_NAME.contains('Ubuntu 20')) {
        result['target'] = 'ubuntu20.04'
      } else {
        echo (
          "Could not determine target in ${env.STAGE_NAME}, defaulting to EL7")
      }
    }

    if (result['target'].startsWith('el') ||
        result['target'].startsWith('centos')) {
      result['java_pkg'] = 'java-1.8.0-openjdk'
    } else if (result['target'].startsWith('ubuntu')) {
      result['java_pkg'] = 'openjdk-8-jdk'
    } else if (result['target'].startsWith('leap')) {
      result['java_pkg'] = 'java-1_8_0-openjdk'
    } else {
      error 'Java package not known for ' + result['target']
    }

    result['compiler'] = 'gcc'
    if (config['COMPILER']) {
      result['compiler'] = config['COMPILER']
    } else if (env.COMPILER) {
      result['compiler'] = env.COMPILER
    } else if (env.STAGE_NAME.contains('Clang')) {
      result['compiler'] = 'clang'
    } else if (env.STAGE_NAME.contains('Intel-C')) {
      result['compiler'] = 'icc'
    } else if (env.STAGE_NAME.contains('Bullseye')) {
      result['compiler'] = 'covc'
    }

    if (config['build_type']) {
      result['build_type'] = config['build_type']
    } else if (config['BUILD_TYPE']) {
      result['build_type'] = config['BUILD_TYPE']
    } else if (env.STAGE_NAME.contains('release')) {
      result['build_type'] = 'release'
    } else if (env.STAGE_NAME.contains('debug')) {
      result['build_type'] = 'debug'
    }

    if (config['target_prefix']) {
      result['target_prefix'] == config['target_prefix']
    } else if (env.STAGE_NAME.contains('TARGET_PREFIX')) {
      result['target_prefix'] = 'install/opt'
    }

    if (env.STAGE_NAME.contains('Coverity')) {
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

    String cluster_size = ""
    if (env.STAGE_NAME.contains('Functional')) {
      String branch_tag = "pr"
      if (config['test_tag']) {
        // Caller told us which bucket to test from
        branch_tag = config['test_tag']
      } else if (startedByTimer()) {
        // Otherwise, if it was started by a timer, it must be the daily test
        branch_tag = "daily_regression"
      }
      result['test'] = 'Functional'
      result['node_count'] = 9
      cluster_size = '-hw'
      result['pragma_suffix'] = ''
      result['ftest_arg'] = ''
      if (env.STAGE_NAME.contains('Hardware')) {
        cluster_size = 'hw,large'
        result['pragma_suffix'] = '-hw-large'
        result['ftest_arg'] = 'auto:Optane'
        if (env.STAGE_NAME.contains('Small')) {
          result['node_count'] = 3
          cluster_size = 'hw,small'
          result['pragma_suffix'] = '-hw-small'
        } else if (env.STAGE_NAME.contains('Medium')) {
          result['node_count'] = 5
          cluster_size = 'hw,medium,ib2'
          result['pragma_suffix'] = '-hw-medium'
        }
      }
      result['test_tag'] = branch_tag + ',' + cluster_size

      for (feature in commitPragma(pragma: "Features").split(' ')) {
        // Add tests from all testing buckets
        // Duplication with branch_tag is not a problem
        result['test_tag'] += ' pr,' + feature + ',' + cluster_size
        result['test_tag'] += ' daily_regression,' + feature + ',' + cluster_size
        result['test_tag'] += ' full_regression,' + feature + ',' + cluster_size
      }
    }
    if (config['test']) {
      result['test'] = config['test']
    }
    if (config['node_count']) {
      result['node_count'] = config['node_count']
    }
    if (config['test_tag']) {
      result['test_tag'] = config['test_tag']
    }
    if (config['pragma_suffix']) {
      result['pragma_suffix'] = config['pragma_suffix']
    }
    if (config['ftest_arg']) {
      result['ftest_arg'] = config['ftest_arg']
    }

    if (env.STAGE_NAME.contains('NLT')) {
      result['NLT'] = true
    } else {
      result['NLT'] = false
    }

    if (env.STAGE_NAME.contains('Unit Test') &&
        env.STAGE_NAME.contains('memcheck')) {
        result['with_valgrind'] = 'memcheck'
    }

    return result
}
