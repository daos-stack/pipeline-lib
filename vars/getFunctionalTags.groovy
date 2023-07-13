// vars/getFunctionalTags.groovy

/**
 * getFunctionalTags.groovy
 *
 */

/*
 * Get the tags defined by the commit pragma.
 *
 * @param pragma_suffix commit pragma suffix for this stage, e.g. '-hw-large'
 * @return String value of the test tags defined in the commit message
 */
String get_commit_pragma_tags(String pragma_suffix) {
    // Get the test tags defined in the commit message
    String pragma_tag

    // Use the tags defined by the stage-specific 'Test-tag-<stage>:' commit message pragma.  If those
    // are not specified use the tags defined by the general 'Test-tag:' commit message pragma.
    pragma_tag = commitPragma('Test-tag' + pragma_suffix, commitPragma('Test-tag', null))
    if (pragma_tag) {
        return pragma_tag
    }

    // If neither of the 'Test-tag*:' commit message pragmas are specified, use the 'Features:'
    // commit message pragma to define the tags to use.
    String features = commitPragma('Features', null)
    if (features) {
        // Features extend the standard pr testing tags to include tests run in daily or weekly builds
        // that test the specified feature.
        pragma_tag = 'pr'
        for (feature in features.split(' ')) {
            pragma_tag += ' daily_regression,' + feature
            pragma_tag += ' full_regression,' + feature
        }
    }
    return pragma_tag
}

/*
 * Get the avocado test tags for the functional test stage.
 *
 * @param pragma_suffix commit pragma suffix for this stage, e.g. '-hw-large'
 * @param stage_tags launch.py tags to use to limit functional tests to those that fit the stage
 * @param default_tags default launch.py tags to use when no parameter or commit pragma tags exist
 * @return String of test tags to run in the stage
 */
Map call(String pragma_suffix, String stage_tags, String default_tags) {
    String requested_tags = ''
    String stage_tags = ''

    // Define the test tags to use in this stage
    if (startedByUser() || startedByTimer() || startedByUpstream()) {
        // Builds started by the user, a timer, or upstreeam should use the TestTag parameter
        requested_tags = params.TestTag ?: default_tags
    }
    else {
        // Builds started from a commit should use any commit pragma tags if defined
        requested_tags = get_commit_pragma_tags(pragma_suffix) ?: default_tags
    }
    if (requested_tags) {
        requested_tags = tags.trim()
    }
    for (group in requested_tags.split(' ')) {
        stage_tags += group + (group != '+' ? ',' + stage_tags : '') + ' '
    }

    return stage_tags.trim()
}