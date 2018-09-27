/**
 * (c) Copyright IBM Corporation 2015.
 * (c) Copyright HCL Technologies Ltd. 2018. All Rights Reserved.
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
boolean renewKey = Boolean.parseBoolean(props['renewKey'])

PresenceHelper presenceHelper = new PresenceHelper(new SCXRestClient(props), airHelper.isWindows)
presenceHelper.startPresence(presenceId, renewKey)

println "[OK] Presence has started successfully."