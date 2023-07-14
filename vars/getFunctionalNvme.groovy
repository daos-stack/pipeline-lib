// vars/getFunctionalNvme.groovy

/**
 * getFunctionalNvme.groovy
 *
 * Get the launch.py --nvme argument for this functional test stage.
 *
 * @param kwargs Map containing the following optional arguments:
 *      pragma_suffix   commit pragma suffix for this stage, e.g. '-hw-large'
 *      default_nvme    launch.py --nvme argument to use when no parameter or commit pragma exist
 * @return String value of the launch.py --nvme argument
 */
Map call(Map kwargs = [:]) {
    String pragma_suffix = kwargs.get('pragma_suffix', getPragmaSuffix())
    String default_nvme = kwargs.get('default_nvme', '')

    // Get the launch.py --nvme argument from either the build parameters or commit pragmas
    String launch_nvme = params.TestNvme ?: cachedCommitPragma(
        'Test-nvme' + pragma_suffix, cachedCommitPragma('Test-nvme', default_nvme))

    if (launch_nvme) {
        launch_nvme = '--nvme=' + launch_nvme
    }
    return launch_nvme
}
