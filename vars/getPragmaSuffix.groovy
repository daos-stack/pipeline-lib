/* groovylint-disable VariableName */
// vars/getPragmaSuffix.groovy

/**
 * getPragmaSuffix.groovy
 *
 * Get the commit pragma suffix for this functional test stage
 *
 * @return String value containing the commit pragma suffix for this functional test stage
 */
String call() {
    String stage_name = env.STAGE_NAME ?: ''
    String pragma_suffix = ''

    if (stage_name.contains('Functional')) {
        pragma_suffix = '-vm'
        if (stage_name.contains('Hardware')) {
            pragma_suffix = '-hw-large'
            if (stage_name.contains('Small')) {
                pragma_suffix = '-hw-small'
            } else if (stage_name.contains('Medium')) {
                pragma_suffix = '-hw-medium'
                if (stage_name.contains('Provider')) {
                    if (stage_name.contains('Verbs')) {
                        pragma_suffix += '-verbs-provider'
                    }
                    else if (stage_name.contains('UCX')) {
                        pragma_suffix += '-ucx-provider'
                    }
                    else if (stage_name.contains('TCP')) {
                        pragma_suffix += '-tcp-provider'
                    }
                }
            } else if (stage_name.contains('Hardware 24')) {
                pragma_suffix = '-hw-24'
            }
        }
        if (stage_name.contains('with Valgrind')) {
            pragma_suffix = '-valgrind'
        }
    }
    return pragma_suffix
}
