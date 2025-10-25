package exoticatechnologies.ui.impl.shop

import exoticatechnologies.ui.ButtonHandler

class MaxBandwidthButtonHandler(val bandwidthPanel: ShipHeaderUIPlugin): ButtonHandler() {
    override fun checked() {
        bandwidthPanel.maxBandwidthButtonClicked()
    }
}
