// vars/getFunctionalArgs.groovy

/**
 * getFunctionalArgs.groovy
 *
 * Get a map of arguments used to run the functional tests in the stage.
 *
 * @param pragma_suffix commit pragma suffix for this stage, e.g. '-hw-large'
 * @param stage_tags launch.py tags to use to limit functional tests to those that fit the stage
 * @param default_tags default launch.py tags to use when no parameter or commit pragma tags exist
 * @param nvme default launch.py --nvme argument to use
 * @param provider default launch.py --provider argument to use
 * @return Map values to use with runTestFunctional
 */
Map call(String pragma_suffix, String stage_tags, String default_tags, String nvme,
         String provider) {
    Map result = [:]
    result['test_tag'] = getFunctionalTags(pragma_suffix, stage_tags, default_tags)
    result['ftest_arg'] = ''
    result['stage_rpms'] = ''

    // Get the launch.py --nvme argument from either the build parameters or commit pragmas
    String launch_nvme = params.TestNvme ?: cachedCommitPragma(
        'Test-nvme' + pragma_suffix, cachedCommitPragma('Test-nvme', nvme))
    if (launch_nvme) {
        result['ftest_arg'] += ' --nvme=' + launch_nvme
    }

    // Get the launch.py --provider argument from either the build parameters or commit pragmas
    String launch_provider = provider ?: params.TestProvider ?: cachedCommitPragma(
        'Test-provider' + pragma_suffix, cachedCommitPragma('Test-provider', null))
    if (launch_provider) {
        result['ftest_arg'] += ' --provider=' +  launch_provider

        // Include any additional rpms required for the provider
        if (launch_provider.contains('ucx')) {
            result['stage_rpms'] = 'mercury-ucx'
        }
    }

    // Get the launch.py --repeat argument from either the build parameters or commit pragmas
    String launch_repeat = params.TestRepeat ?: cachedCommitPragma(
        'Test-repeat' + pragma_suffix, cachedCommitPragma('Test-repeat', null))
    if (launch_repeat) {
        result['ftest_arg'] += ' --repeat=' + launch_repeat
    }

    return result
}
