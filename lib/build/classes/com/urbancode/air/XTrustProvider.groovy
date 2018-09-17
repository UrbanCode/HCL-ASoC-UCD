/*
* Licensed Materials - Property of IBM* and/or HCL**
* UrbanCode Deploy
* UrbanCode Build
* UrbanCode Release
* AnthillPro
* (c) Copyright IBM Corporation 2011, 2017. All Rights Reserved.
* (c) Copyright HCL Technologies Ltd. 2018. All Rights Reserved.
*
* U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
* GSA ADP Schedule Contract with IBM Corp.
*
* * Trademark of International Business Machines
* ** Trademark of HCL Technologies Limited
*/
package com.urbancode.air

import java.security.Provider;
import java.security.Security;

public class XTrustProvider extends Provider {

    //**********************************************************************************************
    // CLASS
    //**********************************************************************************************
    final static private long serialVersionUID = 1L;

    //----------------------------------------------------------------------------------------------
    static public void install() {
        if (Security.getProvider(XTrustProvider.class.getSimpleName()) == null) {
            Security.insertProviderAt(new XTrustProvider(), 2);
            Security.setProperty("ssl.TrustManagerFactory.algorithm", "XTrust509");
        }
    }

    //**********************************************************************************************
    // INSTANCE
    //**********************************************************************************************

    //----------------------------------------------------------------------------------------------
    public XTrustProvider() {
        super(XTrustProvider.class.getSimpleName(), 1D,
                "Basic XTrustProvider ignoring invalid certificates");

        put("TrustManagerFactory.XTrust509", XTrustManagerFactory.class.getName());
    }
}
