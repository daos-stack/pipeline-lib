/* groovylint-disable DuplicateStringLiteral, UnnecessaryGetter, VariableName */
// vars/getFunctionalNvme.groovy

/**
 * getFunctionalNvme.groovy
 *
 * Get the launch.py --nvme argument for this functional test stage.
 *
 * @param kwargs Map containing the following optional arguments:
 *      pragma_suffix   commit pragma suffix for this stage, e.g. '-hw-large'
 *      nvme            launch.py --nvme argument to use
 *      default_nvme    launch.py --nvme argument to use when neither a nvme, nor parameter, nor
 *                      commit pragma exist
 * @return String value of the launch.py --nvme argument
 */
Map call(Map kwargs = [:]) {
    String pragma_suffix = kwargs.get('pragma_suffix', getPragmaSuffix())
    String default_nvme = kwargs.get('default_nvme', '')
    String launch_nvme = kwargs['nvme'] ?: params.TestNvme ?: cachedCommitPragma(
        'Test-nvme' + pragma_suffix, cachedCommitPragma('Test-nvme', default_nvme))

    if (launch_nvme) {
        launch_nvme = '--nvme=' + launch_nvme
    }
    return launch_nvme
}
