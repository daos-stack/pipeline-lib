// vars/getFunctionalTags.groovy

/**
 * getFunctionalTags.groovy
 *
 * Get the avocado test tags for the functional test stage.
 *
 * @param kwargs Map containing the following optional arguments:
 *      pragma_suffix   commit pragma suffix for this stage, e.g. '-hw-large'
 *      stage_tags      launch.py tags to use to limit functional tests to those that fit the stage
 *      default_tags    launch.py tag argument to use when no parameter or commit pragma tags exist
 * @return String of test tags to run in the stage
 */
Map call(Map kwargs = [:]) {
    String pragma_suffix = kwargs.get('pragma_suffix', getPragmaSuffix())
    String stage_tags = kwargs.get('stage_tags', getFunctionalStageTags())
    String default_tags = kwargs.get('default_tags', '')
    String requested_tags = ''

    // Define the test tags to use in this stage
    if (startedByUser() || startedByUpstream()) {
        // Builds started by the user, timer, or upstream should use the TestTag parameter
        requested_tags = params.TestTag ?: ''
    }
    if (!requested_tags && startedByTimer()) {
        // Builds started by a timer without a TestTag parameter should use the default tag
        requested_tags = default_tags
    }
    if (!requested_tags) {
        // Builds started from a commit should first use any commit pragma 'Test-tag*:' tags if defined
        requested_tags = commitPragma('Test-tag' + pragma_suffix, commitPragma('Test-tag', ''))
    }
    if (!requested_tags) {
        // Builds started from a commit should finally use the default tags for the stage
        requested_tags = default_tags
    }

    // Append any commit pragma 'Features:' tags if defined
    String features = commitPragma('Features', '')
    if (features) {
        if (!requested_tags) {
            // Default to pr for backward compatibility
            requested_tags = 'pr'
        }
        // Features extend the standard testing tags to include tests run in pr, daily, or weekly builds
        // that test the specified feature.
        // We should eventually not need to filter by pr, daily, weekly when all tests are tagged appropriately.
        for (feature in features.split(' ')) {
            requested_tags += ' pr,' + feature + ' daily_regression,' + feature + ' full_regression,' + feature
        }
    }
    if (requested_tags) {
        requested_tags = requested_tags.trim()
    }

    // Add the stage tags to each requested tag
    String tags = ''
    for (group in requested_tags.split(' ')) {
        tags += group + (group != '+' ? ',' + stage_tags : '') + ' '
    }
    return tags.trim()
}
