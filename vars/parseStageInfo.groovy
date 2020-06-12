// vars/parseStageName.groovy

/**
 * Method to get a MAP of values based on environment variables that
 * are known to the stage.
 *
 * The map will have following members:
 *
 * result['compiler']      Known compilers are 'gcc', 'icc', clang, and 'covc'.
 *                         Default is 'gcc'
 *
 * result['target']        Known targets are 'centos7','centos8','leap15',
 *                         'ubuntu18', 'ubuntu20'.  Default is 'centos7'
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
 */
def call(Map config = [:]) {

    Map result = [:]
    result['target'] = 'centos7'
    if (config['target']) {
      result['target'] = config['target']
    } else if (env.TARGET) {
      result['target'] = config['target']
    } else {
      if (env.STAGE_NAME.contains('CentOS 7')) {
        result['target'] = 'centos7'
      } else if (env.STAGE_NAME.contains('CentOS 8')) {
        result['target'] = 'centos8'
      } else if (env.STAGE_NAME.contains('Leap 15')) {
        result['target'] = 'leap15'
      } else if (env.STAGE_NAME.contains('Ubuntu 18')) {
        result['target'] = 'ubuntu18'
      } else if (env.STAGE_NAME.contains('Ubuntu 20')) {
        result['target'] = 'ubuntu20'
      }
    }

    result['compiler'] = 'gcc'
    if (env.COMPILER) {
      result['compiler'] = env.COMPILER
    } else if (env.STAGE_NAME.contains('Clang')) {
      result['compiler'] = 'clang'
    } else if (env.STAGE_NAME.contains('Intel-C')) {
      result['compiler'] = 'icc'
    } else if (env.STAGE_NAME.contains('Bullseye')) {
      result['compiler'] = 'covc'
    }

    if (config['target_prefix']) {
      result['target_prefix'] == config['target_prefix']
    } else if (env.STAGE_NAME.contains('TARGET_PREFIX')) {
      result['target_prefix'] = 'install/opt'
    }

    return result
}