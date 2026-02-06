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
 *      ftest_arg       String of launch.py arguments to use when running the functional tests
 *      stage_rpms      String of additional packages to install when running functional tests
 */
Map call(Map kwargs = [:]) {
    Map result = [:]
    String launchNvme = getFunctionalNvme(kwargs)
    String launchProvider = getFunctionalProvider(kwargs)
    String launchRepeat = getFunctionalRepeat(kwargs)

    // Include any additional rpms required for the provider
    if (launchProvider.contains('ucx')) {
        result['stage_rpms'] = 'mercury-ucx'
    } else {
        result['stage_rpms'] = 'mercury-libfabric'
    }

    result['ftest_arg'] = [launchNvme, launchProvider, launchRepeat].join(' ').trim()
    return result
}
