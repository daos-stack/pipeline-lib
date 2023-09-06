// vars/getFunctionalTagsDefault.groovy

/**
 * getFunctionalTagsDefault.groovy
 *
 * Get the default avocado test tags for the functional test stage.
 *
 * @param kwargs Map containing the following optional arguments:
 *      tags    launch.py tag argument to use when no parameter or commit pragma tags exist
 * @return String of default test tags to use
 */
Map call(Map kwargs = [:]) {
    String tags = kwargs.get('tags', 'pr')

    if (startedByTimer() && tags == 'pr') {
        return 'pr daily_regression'
    }
    if (startedByTimer()) {
        return tags
    }
    if (tags in ['pr', 'daily_regression', 'full_regression']) {
        return tags
    }
    pr_tags = ''
    for (tag in tags.split(' ')) {
        pr_tags += " pr,${tag}"
    }
    return pr_tags.trim()
}
