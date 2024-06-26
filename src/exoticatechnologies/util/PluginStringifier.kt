package exoticatechnologies.util

import exoticatechnologies.modifications.upgrades.UpgradeSpecialItemPlugin
import org.apache.log4j.Logger

object PluginStringifier {
    @JvmStatic
    public fun logPlugin(plugin: UpgradeSpecialItemPlugin?, log: Logger) {
        val pluginString: String = if (plugin != null) {
            plugin.upgrade?.toString() ?: "plugin.upgrade was null"
        } else {
            "plugin was null"
        }
        log.info("CreateItemPlugin::createTooltip()\tplugin: $plugin, plugin.getUpgrade(): $pluginString")
    }

    @JvmStatic
    public fun logPlugins(upgPlugin: UpgradeSpecialItemPlugin?, newPlugin: UpgradeSpecialItemPlugin?, log: Logger) {
        val newPluginUpgradeString: String = if (newPlugin != null) {
            newPlugin.upgrade?.toString() ?: "newPlugin.upgrade was null"
        } else {
            "newPlugin was null"
        }
        val upgPluginUpgradeString: String = if (upgPlugin != null) {
            upgPlugin.upgrade?.toString() ?: "upgPlugin.upgrade was null"
        } else {
            "upgPlugin was null"
        }
        log.info("CreateItemPlugin::createTooltip()\tnewPlugin: $newPlugin, upgPlugin: $upgPlugin, newPlugin.getUpgrade(): $newPluginUpgradeString, upgPlugin.getUpgrade(): $upgPluginUpgradeString")
    }

}