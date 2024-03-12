// vars/getFunctionalStageTags.groovy

/**
 * getFunctionalStageTags.groovy
 *
 * Get the avocado tags for the functional test stage used to ensure only appropriately configured
 * test run in the stage.
 *
 * @return String value of tags used to limit tests to ones that will run in this stage
 */
 String call() {
    String stage_name = env.STAGE_NAME ?: ''
    String stage_tags = ''

    if (stage_name.contains('Functional')) {
        stage_tags = 'vm'
        if (stage_name.contains('Hardware')) {
            stage_tags = 'hw,large'
            if (stage_name.contains('Small')) {
                stage_tags = 'hw,small'
            } else if (stage_name.contains('Medium')) {
                stage_tags = 'hw,medium,-provider'
                if (stage_name.contains('Provider')) {
                    stage_tags = 'hw,medium,provider'
                }
            } else if (stage_name.contains('Hardware 24')) {
                stage_tags = 'hw,24'
            }
        }
    }

    return stage_tags
 }
