// vars/getFunctionalRepeat.groovy

/**
 * getFunctionalRepeat.groovy
 *
 * Get the launch.py --repeat argument for this functional test stage.
 *
 * @param kwargs Map containing the following optional arguments:
 *      pragma_suffix   commit pragma suffix for this stage, e.g. '-hw-large'
 * @return String value of the launch.py --repeat argument
 */
Map call(Map kwargs = [:]) {
    String pragma_suffix = kwargs.get('pragma_suffix', getPragmaSuffix())
    String launch_repeat = params.TestRepeat ?: cachedCommitPragma(
        'Test-repeat' + pragma_suffix, cachedCommitPragma('Test-repeat', null))

    if (launch_repeat) {
        launch_repeat = '--repeat=' + launch_repeat
    }
    return launch_repeat
}
