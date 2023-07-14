// vars/getFunctionalProvider.groovy

/**
 * getFunctionalProvider.groovy
 *
 * Get the launch.py --provider argument for this functional test stage.
 *
 * @param kwargs Map containing the following optional arguments:
 *      pragma_suffix   commit pragma suffix for this stage, e.g. '-hw-large'
 *      provider        launch.py --provider argument to use
 * @return String value of the launch.py --nvme argument
 */
Map call(Map kwargs = [:]) {
    String pragma_suffix = kwargs.get('pragma_suffix', getPragmaSuffix())
    String launch_provider = kwargs['provider'] ?: params.TestProvider ?: cachedCommitPragma(
        'Test-provider' + pragma_suffix, cachedCommitPragma('Test-provider', null))

    if (launch_provider) {
        launch_provider = '--provider=' +  launch_provider
    }
    return launch_provider
}
