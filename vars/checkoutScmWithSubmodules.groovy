// vars/checkoutScmWithSubmodules.groovy

import com.intel.checkoutScm

def call(Map config) {

    def c = new com.intel.checkoutScm()
    c.checkoutScmWithSubmodules()

}
