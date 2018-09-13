/**
 * � Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

import com.urbancode.air.AirPluginTool
import com.urbancode.air.plugin.AppScanSaaS.SCXRestClient
import com.urbancode.air.plugin.AppScanSaaS.PresenceHelper

final def airHelper = new AirPluginTool(args[0], args[1])
final Properties props = airHelper.getStepProperties()
String presenceId = props['presenceId']

PresenceHelper presenceHelper = new PresenceHelper(new SCXRestClient(props), airHelper.isWindows)
presenceHelper.startPresence(presenceId)

println "[OK] Presence has started successfully."