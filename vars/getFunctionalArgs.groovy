// vars/getFunctionalArgs.groovy

/**
 * getFunctionalArgs.groovy
 *
 * Get a map of arguments used to run the functional tests in the stage.
 *
 * @param kwargs Map containing the following optional arguments:
 *      pragma_suffix   commit pragma suffix for this stage, e.g. '-hw-large'
 *      nvme            launch.py --nvme argument to use
 *      default_nvme    launch.py --nvme argument to use when no parameter or commit pragma exist
 *      provider        launch.py --provider argument to use
 * @return Map values to use with runTestFunctional:
 *      ftest_arg       String of launch.py arguments to use when running the fuctional tests
 *      stage_rpms      String of additional packages to install when running functional tests
 */
Map call(Map kwargs = [:]) {
    Map result = [:]
    String launch_nvme = getFunctionalNvme(kwargs)
    String launch_provider = getFunctionalProvider(kwargs)
    String launch_repeat = getFunctionalRepeat(kwargs)

    // Include any additional rpms required for the provider
    if (launch_provider.contains('ucx')) {
        result['stage_rpms'] = 'mercury-ucx'
    }

    result['ftest_arg'] = [launch_nvme, launch_provider, launch_repeat].join(' ').trim()
    return result
}
