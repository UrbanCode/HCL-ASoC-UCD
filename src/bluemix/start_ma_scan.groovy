/**
 * © Copyright IBM Corporation 2014.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

import java.util.Formatter.DateTime;
import java.util.concurrent.TimeUnit;

import com.urbancode.air.AirPluginTool
import com.urbancode.air.plugin.AppScanSaaS.*;

final def airHelper = new AirPluginTool(args[0], args[1])
final Properties props = airHelper.getStepProperties()

String scanId = MAScanRunner.runMAScan(props, new BluemixRestClient(props))

airHelper.setOutputProperty("ScanId", scanId)
airHelper.storeOutputProperties()
println "Exiting with success"
System.exit 0