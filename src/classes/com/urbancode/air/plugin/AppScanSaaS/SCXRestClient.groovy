/**
 * © Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS

import java.util.Properties;

public class SCXRestClient extends RestClient {
	public SCXRestClient(Properties props) {
		super(props, true, true);
	}
	
	@Override
	protected def login() {
		scxLogin()
		assertLogin()
	} 
}
