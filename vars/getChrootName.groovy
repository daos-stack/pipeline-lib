// vars/getChrootName.groovy

/**
 * getChrootName.groovy
 *
 * getChrootName variable
 */


/**
 * Method to return the chroot name for a distro
 */
String call(String distro) {

    return[ 'centos7':    'centos+epel-7-x86_64',
            'el8':        'rocky+epel-8-x86_64',
            'leap15':     'opensuse-leap-15.5-x86_64',
            'ubuntu2004': ''][distro]

}
