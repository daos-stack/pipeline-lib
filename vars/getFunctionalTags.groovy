/* groovylint-disable DuplicateStringLiteral, VariableName */
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
    /* groovylint-disable-next-line UnnecessaryGetter */
    String pragma_suffix = kwargs.get('pragma_suffix', getPragmaSuffix())
    /* groovylint-disable-next-line UnnecessaryGetter */
    String stage_tags = kwargs.get('stage_tags', getFunctionalStageTags())
    String default_tags = kwargs.get('default_tags', 'pr')
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
    // Builds started from a commit should first use any commit pragma 'Test-tag*:' tags if defined
    requested_tags = requested_tags ?: commitPragma('Test-tag' + pragma_suffix, commitPragma('Test-tag', ''))

    // Builds started from a commit should finally use the default tags for the stage
    requested_tags = requested_tags ?: default_tags

    // Append any commit pragma 'Features:' tags if defined
    String features = commitPragma('Features', '')
    if (features) {
        // Features extend the standard testing tags to include tests run in pr, daily and weekly builds
        // that test the specified feature.
        // We should eventually not need to filter by pr, daily, weekly when all tests are tagged appropriately.
        for (feature in features.split(' ')) {
            requested_tags += ' pr,' + feature + ' daily_regression,' + feature + ' full_regression,' + feature
        }
    }
    if (requested_tags) {
        requested_tags = requested_tags.trim()
    }

    // Add any Skip-list tags to each requested tag
    /* groovylint-disable-next-line UnnecessaryGetter */
    List skipped = getSkippedTests()
    if (skipped) {
        String tags = ''
        for (group in requested_tags.split(' ')) {
            tags += group + ',-' + skipped.join(',-') + ' '
        }
        requested_tags = tags.trim()
    }

    // Add the stage tags to each requested tag
    String tags = ''
    for (group in requested_tags.split(' ')) {
        /* groovylint-disable-next-line ConfusingTernary */
        tags += group + (group != '+' ? ',' + stage_tags : '') + ' '
    }

    return tags.trim()
}

// Unit testing
/* Not working as https://groovy-lang.org/structure.html describes
import org.codehaus.groovy.runtime.InvokerHelper

class Main extends Script {

    def run() {
        println('Testing')
    }
    static void main(String[] args) {
        InvokerHelper.runScript(Main, args)
    }

}
*/
/* Uncomment to get testing
String getPragmaSuffix() {
    return '-vm'
}

String getFunctionalStageTags() {
    return 'vm'
}

Boolean startedByUser() {
    return false
}

Boolean startedByUpstream() {
    return false
}

Boolean startedByTimer() {
    return false
}

String commitPragma(String pragma, String defaultValue) {
    return 'datamover'
    return ''
}

List getSkippedTests() {
    return ['skipped1', 'skipped2']
}

println(call([:]))
*/
